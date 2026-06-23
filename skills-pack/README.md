# 短篇小说写作技能包

## 包含内容

### 1. 技能文件 (short-story-plot/)
- SKILL.md - 主技能文档
- references/ - 参考资料
  - conflict-intensity.md - 冲突强度评分体系（含因果推断方法）
  - conflict-reference.md - 八种冲突技巧详解
  - screenplay-structure.md - 剧本因果链法
  - classic-conflicts.md - 经典影视冲突分析
  - chinese-conflicts.md - 真实中国冲突案例
  - renjian-mining.md - 人间文章采集与冲突挖掘
  - values-clash-opening.md - 价值观冲突开局模板
  - trace-fire-pursuit.md - 追妻火葬场特化指南
  - passed-cases.md - 已通过案例库
  - causal-library-qdrant.md - Qdrant向量数据库集成
- scripts/ - 脚本
  - conflict-scorer.py - 冲突评分器
  - import_all_to_qdrant.py - 批量导入脚本

### 2. 向量数据库 (qdrant/)
- config.yaml - Qdrant配置
- import_all_to_qdrant.py - 批量导入脚本
- import_new_chains.py - 新文章导入脚本
- reimport_all.py - 全量重建脚本
- causal_backup.json - 备份数据

### 3. 真实冲突案例 (人间/)
- 网易人间文章存档
- 已拆解的冲突链
- 待拆解的文章

## 使用方法

1. 安装Qdrant向量数据库
2. 运行 import_all_to_qdrant.py 导入因果链
3. 使用语义搜索查找相关冲突
4. 参考SKILL.md进行写作

## 核心原则

- 替身写作法：把自己放进角色的位置
- 因果推断：从果反推多因，穷尽所有可能
- 冲突强度：0-10分评分体系
- 第一人称"我"视角

## 作者

依观/yiguan
GitHub: chenk-1833597858
License: MIT
