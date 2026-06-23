#!/usr/bin/env python3
"""
因果推断库 → Qdrant 向量数据库 全量导入脚本。

用法：
  python3 import_all_to_qdrant.py

功能：
  - 读取 ~/.hermes/skills/creative/causal-inference-library.json
  - 读取 /home/ubuntu/人间/*.html 中已提取的因果链
  - 清空并重建 causal_chains 集合
  - 用 fastembed 计算向量，上传到 Qdrant
  - 同时写入 /home/ubuntu/qdrant_data/causal_backup.json 作为普通数据库备份

数据格式：
  {
    "类型": "冲突",
    "烈度": "是不是XX？",
    "烈度评分": 0-10,
    "果": "具体可观察的结果",
    "因": ["直接原因1", "直接原因2"],
    "缘": ["辅助条件1", "辅助条件2"]
  }

烈度评分体系（从0问到10，是就停）：
  0=日常  1=应付  2=顶撞  3=吵架  4=拉黑
  5=决裂  6=打架  7=报复  8=害命  9=灭门  10=连坐

⚠️ 入库前必须给用户预览确认，不可私自导入。
"""

import json, os, re
from pathlib import Path

from fastembed import TextEmbedding
from qdrant_client import QdrantClient
from qdrant_client.models import PointStruct, VectorParams, Distance

# ── 配置 ──────────────────────────────────────────
QDRANT_HOST = "127.0.0.1"
QDRANT_PORT = 6333
COLLECTION = "causal_chains"
VECTOR_SIZE = 512  # BAAI/bge-small-zh-v1.5 实际输出512维（ONNX模型）。如果搜索异常，确认集合配置：curl -s http://127.0.0.1:6333/collections/causal_chains
EMBED_MODEL = "BAAI/bge-small-zh-v1.5"
BACKUP_PATH = "/home/ubuntu/qdrant_data/causal_backup.json"
DB_PATH = "/home/ubuntu/qdrant_data/causal_chains.db"
LIBRARY_PATH = os.path.expanduser(
    "~/.hermes/skills/creative/causal-inference-library.json"
)

# ── 连接 ──────────────────────────────────────────
client = QdrantClient(host=QDRANT_HOST, port=QDRANT_PORT)

# ── 收集所有因果链 ─────────────────────────────────
all_chains = []

# 1. 从原始因果推断库加载（新版 flat events 结构）
if os.path.exists(LIBRARY_PATH):
    with open(LIBRARY_PATH, "r") as f:
        data = json.load(f)
    for event in data.get("events", []):
        if "果" in event and "烈度评分" in event:
            all_chains.append(event)

# 2. 从人间文章提取的内嵌冲突链（备份修复：NDJSON 格式逐行读取）
if os.path.exists(BACKUP_PATH):
    with open(BACKUP_PATH, "r") as f:
        backup_lines = f.readlines()
    backup_data = []
    for line in backup_lines:
        line = line.strip()
        if line:
            try:
                backup_data.append(json.loads(line))
            except json.JSONDecodeError:
                pass  # 跳过可能有问题的旧备份行
    # 合并去重
    existing_effects = {c.get("果", "") for c in all_chains}
    for chain in backup_data:
        if isinstance(chain, dict) and chain.get("果") not in existing_effects:
            all_chains.append(chain)
    print(f"从备份加载: {len(backup_lines)} 行，其中 {len(backup_data)} 条有效，新增 {len(all_chains)} 条")

print(f"总因果链: {len(all_chains)}")

# ── 重建集合 ──────────────────────────────────────
try:
    client.delete_collection(COLLECTION)
    print(f"已删除旧集合")
except:
    pass

client.create_collection(
    collection_name=COLLECTION,
    vectors_config=VectorParams(size=VECTOR_SIZE, distance=Distance.COSINE),
)
print(f"已创建集合 {COLLECTION} ({VECTOR_SIZE}维, Cosine)")

# ── 嵌入 ──────────────────────────────────────────
print(f"加载嵌入模型 {EMBED_MODEL}...")
embedder = TextEmbedding(model_name=EMBED_MODEL)
print("模型加载完成!")

texts = []
for chain in all_chains:
    effect = chain.get("果", "")
    causes = "、".join(chain.get("因", []))
    conditions = "、".join(chain.get("缘", []))
    parts = [effect]
    if causes:
        parts.append("因：" + causes)
    if conditions:
        parts.append("缘：" + conditions)
    texts.append("。".join(parts))

print(f"正在计算 {len(texts)} 条向量的嵌入...")
embeddings = list(embedder.embed(texts))
print("嵌入完成!")

# ── 上传 ──────────────────────────────────────────
points = []
for i, (chain, emb) in enumerate(zip(all_chains, embeddings)):
    payload = {
        "类型": chain.get("类型", "冲突"),
        "烈度": chain.get("烈度", ""),
        "烈度评分": chain.get("烈度评分") or chain.get("评分", 0),
        "果": chain.get("果", ""),
        "因": chain.get("因", []),
        "缘": chain.get("缘", []),
        "source": chain.get("source", ""),
    }
    points.append(PointStruct(id=i + 1, vector=emb.tolist(), payload=payload))

batch_size = 100
for i in range(0, len(points), batch_size):
    batch = points[i : i + batch_size]
    client.upsert(collection_name=COLLECTION, points=batch)
    print(f"  上传 {min(i + batch_size, len(points))}/{len(points)}")

# ── 写入备份（NDJSON 格式：每行一条 JSON，兼容追加写入） ──────────────────────────────────────
backup_payloads = [p.payload for p in points]
with open(BACKUP_PATH, "w", encoding="utf-8") as f:
    for p in backup_payloads:
        f.write(json.dumps(p, ensure_ascii=False) + "\n")
print(f"备份已写入 {BACKUP_PATH} ({len(backup_payloads)} 行 NDJSON)")

# ── 写入 SQLite ──────────────────────────────────────
try:
    import sqlite3
    conn = sqlite3.connect(DB_PATH)
    c = conn.cursor()
    # 创建表（如果不存在）
    c.execute('''
        CREATE TABLE IF NOT EXISTS causal_chains (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            type TEXT NOT NULL DEFAULT '冲突',
            intensity_label TEXT NOT NULL,
            intensity_score INTEGER NOT NULL,
            effect TEXT NOT NULL,
            causes TEXT NOT NULL,
            conditions TEXT NOT NULL,
            source TEXT,
            created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
        )
    ''')
    c.execute('CREATE INDEX IF NOT EXISTS idx_intensity ON causal_chains(intensity_score)')
    c.execute('CREATE INDEX IF NOT EXISTS idx_type ON causal_chains(type)')
    # 清空旧数据
    c.execute('DELETE FROM causal_chains')
    # 插入新数据
    for p in backup_payloads:
        c.execute('''
            INSERT INTO causal_chains (type, intensity_label, intensity_score, effect, causes, conditions, source)
            VALUES (?, ?, ?, ?, ?, ?, ?)
        ''', (
            p.get("类型", "冲突"),
            p.get("烈度", ""),
            p.get("烈度评分", 0),
            p.get("果", ""),
            json.dumps(p.get("因", []), ensure_ascii=False),
            json.dumps(p.get("缘", []), ensure_ascii=False),
            p.get("source", "")
        ))
    conn.commit()
    conn.close()
    print(f"SQLite 已写入 {DB_PATH} ({len(backup_payloads)} 条)")
except Exception as e:
    print(f"SQLite 写入失败（不影响 Qdrant）：{e}")

# ── 验证 ──────────────────────────────────────────
info = client.get_collection(COLLECTION)
print(f"\n=== 导入完成 ===")
print(f"集合状态: {info.status}")
print(f"向量数: {info.vectors_count}")
print(f"点数: {info.points_count}")

# 烈度分布
scores = set()
for p in points:
    s = p.payload.get("烈度评分", 0)
    if isinstance(s, (int, float)):
        scores.add(int(s))
print(f"烈度: {min(scores)}-{max(scores)} 全覆盖 ✅" if len(scores) > 1 else f"烈度: {list(scores)}")
