"""
Extract causal conflict chains from 4 new 人间 articles and import into Qdrant.
Only processes articles NOT already imported.
"""
import json, sys, os
sys.path.insert(0, '/home/ubuntu/.hermes/hermes-agent/venv/lib/python3.12/site-packages')

from fastembed import TextEmbedding
from qdrant_client import QdrantClient, models

COLLECTION = "causal_chains"
client = QdrantClient(host="127.0.0.1", port=6333)

# ========== NEW conflict chains from 4 articles ==========
new_chains = []

# ===== 1. 父母不买房靠女儿婚姻落家 =====
new_chains.extend([
    {
        "类型": "冲突",
        "烈度": "是不是顶撞？",
        "烈度评分": 2,
        "果": "女儿带男友上门，父亲因为对方没房当众把筷子扔了打女儿耳光",
        "因": ["父母一辈子没买房到处寄人篱下", "父亲指望女儿嫁个有房的人好跟着落脚", "男友家世代务农在省城没房"],
        "缘": ["女儿从小被寄养在亲戚家受尽委屈", "亲戚家孩子落水挨打的是她", "父母连政府补贴修房的机会都放弃了"]
    },
    {
        "类型": "冲突",
        "烈度": "是不是吵架？",
        "烈度评分": 3,
        "果": "父亲在新亲戚家过元宵节，用核桃夹子砸女儿脑袋还当众辱骂",
        "因": ["父亲寄人篱下心情烦躁", "觉得女儿成绩不好让他丢脸", "母亲在旁边一句话不说"],
        "缘": ["那是亲戚刚搬的新房", "大过年的有忌讳", "亲戚看到这场景直接起身离开"]
    },
    {
        "类型": "冲突",
        "烈度": "是不是打架？",
        "烈度评分": 6,
        "果": "父亲输了钱在街上用保温杯砸女儿脸，女儿被打到呼吸急促倒地抽搐",
        "因": ["父亲觉得女儿在亲戚家不会讨好主人给他丢人了", "多年积压的憋屈在打牌输钱后彻底爆发"],
        "缘": ["母亲也在旁边骂她", "父亲以为女儿是装的还踹了一脚", "亲戚来劝才发现不对劲送急诊"]
    },
    {
        "类型": "冲突",
        "烈度": "是不是决裂？",
        "烈度评分": 5,
        "果": "父亲三次拒绝政府补贴修房的机会，老家房子彻底塌了无家可归",
        "因": ["父亲觉得女儿以后不会回老家", "想攒钱在省城买房但一直没攒够", "房价涨了后又买不起了"],
        "缘": ["亲戚劝了三次都被拒绝", "后来政府给现成的房子只要几万也不要", "女儿从此没有自己的根了"]
    },
])

# ===== 2. 我和爸爸都搞砸了铁饭碗 =====
new_chains.extend([
    {
        "类型": "冲突",
        "烈度": "是不是应付？",
        "烈度评分": 1,
        "果": "父亲把买断工龄的钱借给妹夫做生意，妹夫意外去世钱也拿不回来了",
        "因": ["父亲下岗后种苹果需要钱", "妹夫说帮他投资分红", "父亲没签任何合同就把钱给了"],
        "缘": ["父亲在煤矿干了20年买断就这点钱", "母亲生气但也没办法", "后来只能开个小卖部维持"]
    },
    {
        "类型": "冲突",
        "烈度": "是不是顶撞？",
        "烈度评分": 2,
        "果": "儿子在国企干了一年坚决辞职，父亲怎么劝都不听",
        "因": ["儿子受不了兵工厂一眼望到头的生活", "一个月一千出头的工资养不活自己", "想去上海闯一闯"],
        "缘": ["父亲当年也砸了煤矿的铁饭碗后悔了一辈子", "爷爷当年劝过父亲的话父亲现在原样说给儿子", "儿子还是走了父亲的老路"]
    },
    {
        "类型": "冲突",
        "烈度": "是不是日常？",
        "烈度评分": 0,
        "果": "煤矿井下塌方活埋了工友，父亲背着死人跑到井上才知道没气了",
        "因": ["八十年代的煤矿安全条件差", "井下一旦塌方很难逃生"],
        "缘": ["矿上后来给父亲处分", "父亲工伤后左胳膊落下残疾"]
    },
])

# ===== 3. 缺爱女孩遇上好男人 =====
new_chains.extend([
    {
        "类型": "冲突",
        "烈度": "是不是顶撞？",
        "烈度评分": 2,
        "果": "男友瞒着女友找她十三个朋友借了六万多块钱，婚礼上被发现了",
        "因": ["男友以女友手术费为由借钱", "他社交能力强大家对他印象好", "借了不还不催就不还"],
        "缘": ["女友从小缺爱极度渴望有个家", "朋友发现后建QQ群统计债主", "男友被发现后恼羞成怒"]
    },
    {
        "类型": "冲突",
        "烈度": "是不是害命？",
        "烈度评分": 8,
        "果": "花臂男上门催债，怀孕八个多月的妻子挺着肚子面对放高利贷的",
        "因": ["丈夫借了一万高利贷利滚利到五万", "他之前卖房还了四十万卡债又借网贷", "前女友跟他合伙刷爆九张信用卡"],
        "缘": ["母亲卖了唯一住房替他还债", "亲舅舅当场骂他败家子气死你妈", "妻子挺着肚子说医生说不稳"]
    },
    {
        "类型": "冲突",
        "烈度": "是不是拉黑？",
        "烈度评分": 4,
        "果": "丈夫偷了刚出生女儿收到的九千六红包，说是借给表哥了",
        "因": ["丈夫到处借钱已经走投无路", "觉得老婆的钱就是他的钱", "月子里的老婆太好说话了"],
        "缘": ["之前借的钱一分没还过", "每次都说下个月一定还", "亲戚朋友已经没人信他了"]
    },
    {
        "类型": "冲突",
        "烈度": "是不是决裂？",
        "烈度评分": 5,
        "果": "离婚后前夫跟踪纠缠，尾随到家门口说要从九楼跳下去",
        "因": ["前夫欠了三十多万走投无路", "妻子帮他填了窟窿就离婚了", "他觉得没了老婆就没了依靠"],
        "缘": ["报警后警察来了才消停", "妻子背了三十万债年薪才四万", "父母知道后骂她活该"]
    },
])

# ===== 4. 老年人0元游全是套路 =====
new_chains.extend([
    {
        "类型": "冲突",
        "烈度": "是不是应付？",
        "烈度评分": 1,
        "果": "说好的免费旅游，三天全在购物点，饭越吃越差",
        "因": ["旅行社靠购物提成赚钱", "不买东西导游就黑脸", "早上六点半就要出发赶场"],
        "缘": ["老头老太想着免费就忍了", "第一天还是肉菜不购物连肉都没了", "有人想报警被劝住了"]
    },
    {
        "类型": "冲突",
        "烈度": "是不是顶撞？",
        "烈度评分": 2,
        "果": "大妈被导游逼着购物当场翻脸，说要报警投诉",
        "因": ["导游说必须买东西不然别想回去", "大妈来的时候说好是自愿购物", "一车人都不买导游急了"],
        "缘": ["好几个团友跟着一起吵", "白发旅游达人去调解才散场", "后来导游态度好点了"]
    },
    {
        "类型": "冲突",
        "烈度": "是不是吵架？",
        "烈度评分": 3,
        "果": "老头花1680买了口航空锅高高兴兴背回来，被子女骂上当",
        "因": ["导购说这锅质保30年不粘锅不冒烟", "现场烤蛋糕爆米花免费吃", "说原价四千现价一千六是让利"],
        "缘": ["同行的说跟我买没错", "好几个老头都买了", "回家子女查了网上同款才三百"]
    },
])

print(f"New chains to import: {len(new_chains)}")

# ========== Check what's already in DB ==========
count_result = client.count(collection_name=COLLECTION, exact=True)
existing_count = count_result.count
print(f"Already in DB: {existing_count} points")

# ========== Compute embeddings ==========
print("Loading embedding model...")
embedder = TextEmbedding(model_name="BAAI/bge-small-zh-v1.5")

texts = [c["果"] + "。" + "。".join(c["因"]) + "。" + "。".join(c["缘"]) for c in new_chains]
print(f"Computing {len(texts)} embeddings...")
embeddings = list(embedder.embed(texts))

# ========== Upload ==========
points = []
for i, (chain, emb) in enumerate(zip(new_chains, embeddings)):
    pid = existing_count + i + 1
    vec = emb.tolist()
    payload = {
        "类型": chain["类型"],
        "烈度": chain["烈度"],
        "烈度评分": chain["烈度评分"],
        "果": chain["果"],
        "因": chain["因"],
        "缘": chain["缘"],
    }
    points.append(models.PointStruct(id=pid, vector=vec, payload=payload))

# Upsert in batches
batch_size = 50
for i in range(0, len(points), batch_size):
    batch = points[i:i+batch_size]
    client.upsert(collection_name=COLLECTION, points=batch)
    done = min(i + batch_size, len(points))
    print(f"  Uploaded {done}/{len(points)}")

# ========== Verify ==========
updated_count = client.count(collection_name=COLLECTION, exact=True).count
print(f"\n=== 导入完成 ===")
print(f"原有点数: {existing_count}")
print(f"新增点数: {len(new_chains)}")
print(f"现在总数: {updated_count}")
print(f"来源: 父母不买房(4) + 铁饭碗(3) + 缺爱女孩(4) + 老年人0元游(3)")

# ========== Save to backup ==========
backup_path = "/home/ubuntu/qdrant_data/causal_backup.json"
existing_backup = []
if os.path.exists(backup_path):
    with open(backup_path, 'r') as f:
        for line in f:
            line = line.strip()
            if line:
                existing_backup.append(json.loads(line))

# Append new chains to backup
with open(backup_path, 'a') as f:
    for c in new_chains:
        f.write(json.dumps(c, ensure_ascii=False) + '\n')

print(f"\n备份更新: {len(existing_backup)} → {len(existing_backup) + len(new_chains)} 条")
print("\n全部完成!")
