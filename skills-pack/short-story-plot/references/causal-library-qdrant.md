# Qdrant 向量数据库 · 因果推断库集成

## 架构概览

```
因果推断库（双存储）
├── 普通数据库（JSON 备份）
│   └─ /home/ubuntu/qdrant_data/causal_backup.json
│        └─ 178条因果链（20篇人间文章），纯文本备份，经用户确认后方可编辑
│
└── 向量数据库（Qdrant v1.18.2）
    └─ 127.0.0.1:6333  →  集合 causal_chains
         └─ 每个 point = 一条因果链，512维向量（fastembed BAAI/bge-small-zh-v1.5）
```

**当前统计（2026-06-21）：**
- 总因果链数：**178条**
- 来源文章数：**20篇** 网易人间非虚构
- 烈度分布：0-8分（日常到害命）
- 主要类型：家暴、婚姻、原生家庭、社会骗局、职场冲突

## 数据格式

每条因果链统一使用以下格式：

```json
{
  "类型": "冲突",
  "烈度": "是不是害命？",
  "烈度评分": 8,
  "果": "长期家暴终于失手打死人",
  "因": ["每次喝醉酒就打老婆", "那天因为饭菜凉了又动手", "推的时候头撞到茶几角"],
  "缘": ["邻居听到动静习惯了没人报警", "之前报警过但调解完就放回来了", "茶几是大理石面的"]
}
```

**字段说明：**
- `类型`：冲突 / 金句 / 观点（可扩展）
- `烈度`：从 「是不是日常？」问到 「是不是连坐？」，问到一个「是」就停
- `烈度评分`：0-10，对应烈度等级
- `果`：一个具体的可观察的结果
- `因`：导致这个果的直接原因（数组，可多条）
- `缘`：让因能起作用的辅助条件（数组，环境/时间/人物关系等）

**不用具体人名**，用身份称呼（"被出轨的妻子"、"欠债的人"、"施暴的丈夫"）。

## 评分体系（11级，从0问到10）

| 分 | 烈度 | 判据 |
|----|------|------|
| 0 | 是不是日常？ | 日常琐事，顺手而为 |
| 1 | 是不是应付？ | 心里不情愿但表面应付 |
| 2 | 是不是顶撞？ | 口头顶撞，直接反驳 |
| 3 | 是不是吵架？ | 激烈争吵，情绪升级 |
| 4 | 是不是拉黑？ | 删好友/拉黑/断联 |
| 5 | 是不是决裂？ | 彻底决裂，老死不相往来 |
| 6 | 是不是打架？ | 动手肢体冲突 |
| 7 | 是不是报复？ | 蓄意报复，损人利己 |
| 8 | 是不是害命？ | 危及性命，谋害他人 |
| 9 | 是不是灭门？ | 灭门之祸，牵连全家 |
| 10 | 是不是连坐？ | 一人犯罪全家遭殃，甚至牵连亲族 |

## 语义搜索（Qdrant REST API）

### 按语义搜因果链（推荐用 REST API + fastembed）

```python
import requests
from fastembed import TextEmbedding

embedder = TextEmbedding(model_name="BAAI/bge-small-zh-v1.5")
query_text = "深夜一个人等对方回家，等了一夜没人来"
query_vec = list(embedder.embed([query_text]))[0]

resp = requests.post(
    "http://127.0.0.1:6333/collections/causal_chains/points/search",
    json={
        "vector": query_vec.tolist(),
        "limit": 5,
        "with_payload": True
    }
)
hits = resp.json()["result"]
for h in hits:
    p = h["payload"]
    print(f'  [{p["烈度"]}] {p["果"]}  (相似度: {round(h["score"], 4)})')
```

### 按语义+烈度过滤

```python
resp = requests.post(
    "http://127.0.0.1:6333/collections/causal_chains/points/search",
    json={
        "vector": query_vec.tolist(),
        "limit": 5,
        "with_payload": True,
        "filter": {
            "must": [
                {"key": "烈度评分", "range": {"gte": 4, "lte": 7}}
            ]
        }
    }
)
```

### 删除指定因果链

```bash
# 按 ID 删
curl -X POST http://127.0.0.1:6333/collections/causal_chains/points/delete \
  -H "Content-Type: application/json" \
  -d '{"points": [1, 2, 3]}'

# 按条件删（如删除所有"日常"烈度的链）
curl -X POST http://127.0.0.1:6333/collections/causal_chains/points/delete \
  -H "Content-Type: application/json" \
  -d '{"filter": {"must": [{"key": "烈度评分", "range": {"lte": 2}}]}}'

# 删整个集合
curl -X DELETE http://127.0.0.1:6333/collections/causal_chains
```

## 核心工作流

### 写冲突前的搜索流程

1. **用户指定目标分**（如 "我需要一个6分的冲突"）
2. **因果分析**：倒推角色当前处境 → 最可能的果是什么
3. **语义搜索 Qdrant**：用角色处境的核心关键词搜，找语义相近的因果链
4. **筛选**：按烈度过滤（只返回目标分±2的因果链）
5. **展示给用户**：把搜到的因果链列出所有因和缘，**不代评烈度**（用户指出过\"你的评分假的很\"——只列原始因果链，让用户自己判断烈度是否正确）
6. **用户确认后**再用于写作或入库

### 入库新因果链的流程

**重要区分：**
- **从互联网/人间找到的真实冲突** → 直接导入，**不用问用户**（用户：\"以后找到的真实冲突都直接导入\"）
- **从小说/拆书提取的因果链** → 必须展示给用户预览，确认后才能入库

1. **先因果分析** — 从果出发，拆出因和缘
2. **再用「是不是XX？」从0问到10** — 确定自然烈度，不加分不减分
3. **按格式写出**：类型、烈度、烈度评分、果、因[]、缘[]
4. **敏感检查**：避开政治运动（批斗/文革/上访）、民族宗教冲突等敏感内容。发现此类内容直接跳过
5. **如果是真实冲突** → 直接入库。**如果是拆书/小说提取的** → 先展示给用户看，确认后再入库
6. **入库执行**：
   ```bash
   cd /home/ubuntu && python3 ~/.hermes/skills/creative/short-story-plot/scripts/import_all_to_qdrant.py
   ```
   此脚本会自动：清空 Qdrant 集合 → 重新嵌入所有因果链 → 上传 → 写入 JSON 备份 → **写入 SQLite 数据库**
   
   **双存储架构**：
   - 向量数据库（Qdrant）：`127.0.0.1:6333` — 语义搜索
   - 普通数据库（SQLite）：`/home/ubuntu/qdrant_data/causal_chains.db` — 结构化查询
   - JSON 备份：`/home/ubuntu/qdrant_data/causal_backup.json` — 灾难恢复

### 类型扩展

- `"类型": "冲突"` — 当前存量数据
- `"类型": "金句"` — 用户以后手动发给你保存，不做评分判断
- `"类型": "观点"` — 用户以后手动发给你保存，不做评分判断
- 类型不设上限，按需扩展

## Qdrant 服务器信息

- **地址**：`127.0.0.1:6333`（HTTP REST API）
- **gRPC**：`127.0.0.1:6334`
- **版本**：v1.18.2
- **嵌入模型**：`BAAI/bge-small-zh-v1.5`（**512维**——⚠️ 注意不是384维，fastembed 的这个 ONNX 导出版本是512维。创建集合时必须用512，否则上传时报 \"Vector dimension error: expected dim: 384, got 512\"）
- **安装方式**：GitHub releases 下载预编译二进制 `qdrant-x86_64-unknown-linux-musl`（国内用 ghproxy.net 镜像加速；直接连接 github.com 会超时）
- **配置文件**：`/home/ubuntu/qdrant_data/config.yaml`
- **启动命令**：`cd /home/ubuntu/qdrant_data && qdrant --config-path config.yaml > qdrant.log 2>&1 &`
- **数据目录**：`/home/ubuntu/qdrant_data/storage/`
- **导入脚本**：`/home/ubuntu/.hermes/skills/creative/short-story-plot/scripts/import_all_to_qdrant.py`（全量导入 + 备份写入，skill 自带）
- **备份文件**：`/home/ubuntu/qdrant_data/causal_backup.json`（NDJSON 格式，每行一条 JSON。⚠️ 注意：写入时用逐行 append 而非 json.dump，否则 json.load 会解析失败）

## 双存储架构（向量 + SQLite）

```
因果推断库（双存储）
├── 向量数据库（Qdrant）
│   └─ 语义搜索、相似度匹配
│
└── 普通数据库（SQLite）
│   └─ /home/ubuntu/qdrant_data/causal_chains.db
│      └─ 91条，结构化查询，SQL 增删改查
│
└── JSON 备份（冗余）
   └─ /home/ubuntu/qdrant_data/causal_backup.json
      └─ 从 SQLite 或 Qdrant 导出，用于灾难恢复
```

### SQLite 表结构

```sql
CREATE TABLE causal_chains (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    type TEXT NOT NULL DEFAULT '冲突',
    intensity_label TEXT NOT NULL,
    intensity_score INTEGER NOT NULL,
    effect TEXT NOT NULL,
    causes TEXT NOT NULL,  -- JSON array
    conditions TEXT NOT NULL,  -- JSON array
    source TEXT,  -- 来源：'原始库'/'人间-XX文章'/'用户提交'
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_intensity ON causal_chains(intensity_score);
CREATE INDEX idx_type ON causal_chains(type);
```

### 为什么用 SQLite

JSON 文件的问题：
- 没有索引，搜索只能全文匹配
- 格式容易损坏（之前出现过写入数组格式导致解析失败）
- 并发写入不安全

SQLite 的优势：
- 结构化查询：`SELECT * FROM causal_chains WHERE intensity_score >= 8`
- 索引：`烈度评分`、`类型` 都有索引
- 事务安全：多条同时写入不会损坏
- 备份简单：`.db` 文件直接复制即可

### 从 SQLite 查询示例

```python
import sqlite3, json
conn = sqlite3.connect('/home/ubuntu/qdrant_data/causal_chains.db')
c = conn.cursor()

# 查烈度8以上的家暴链
c.execute('''
    SELECT effect, causes, conditions FROM causal_chains
    WHERE intensity_score >= 8 AND type = '冲突'
''')
for row in c.fetchall():
    print(f'果: {row[0]}')
    print(f'因: {json.loads(row[1])}')
    print(f'缘: {json.loads(row[2])}')

conn.close()
```

### 入库流程（双存储）

1. **提取因果链** → 按格式写出
2. **敏感检查** → 跳过政治/民族/宗教敏感内容
3. **如果是真实冲突** → 直接入库（Qdrant + SQLite）
4. **如果是拆书/小说提取** → 先展示给用户，确认后再入库
5. **备份** → 从 SQLite 导出到 JSON（`causal_backup.json`）

## ⚠️ 已知陷阱

### 批量导入时的内存管理
当一次性导入大量因果链（如20篇文章×6条=120条）时，fastembed可能会占用较多内存。如果服务器内存不足（4GB），建议：
1. 分批导入，每批不超过50条
2. 每批导入后验证成功再继续
3. 如果embedding过程卡住，检查`~/.cache/fastembed/`模型缓存是否存在

### 向量维度不匹配
`BAAI/bge-small-zh-v1.5` 的 fastembed ONNX 版本输出 **512维**，不是官方说的384维。如果上传报错 `"Vector dimension error: expected dim: 384, got 512"`，说明集合创建时用了384维。解法：删除集合，用 VectorParams(size=512) 重建。

### 备份文件格式
备份文件 `/home/ubuntu/qdrant_data/causal_backup.json` 是 NDJSON（每行一条 JSON），不是标准 JSON 数组。不要用 `json.load(f)` 读取，要用 `for line in f: json.loads(line)`。之前有过写入 `json.dump` 数组格式导致读取时 `json.load` 失败的情况。写入时建议逐行 append。

### 备份文件被污染
脚本中如果有 bug（如写入了纯字符串而非 dict），备份文件会混入垃圾数据。修复方法：从 Qdrant 导出当前所有点（`scroll` API），重新写入备份文件。
