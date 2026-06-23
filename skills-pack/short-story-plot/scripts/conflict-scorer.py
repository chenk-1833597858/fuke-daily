#!/usr/bin/env python3
"""
冲突强度评分工具 —— 调用 DeepSeek 打分+修改建议

用法：
  python3 conflict-scorer.py 场景清单.txt

输出：评分表 + 通过/不通过 + 修改建议
"""

import sys
import os
import json
import requests

SCORING_PROMPT = """你是一个冲突强度评分专家。请按以下1-10分标准对提供的场景清单进行逐场评分。

评分标准：
1=假意迎合  2=明显对抗  3=冲突升级  4=暴力冲突  5=恩断义绝
6=不相往来  7=见面眼红  8=你死我活  9=抄家灭族  10=赶尽杀绝

判定规则：
先看后果（死人了？7+）→ 再看方式（动手了？3+）→ 最后看来回（顶两句了？2+）

通过条件：
- 前两场 ≥ 3分
- 至少一场 ≥ 7分
- 终果（最后一场） ≥ 6分
- 无连续2场同分
- 总分（只计≥3分的场次）≥ 20分（普通短篇）或 ≥ 50分（追妻火葬场，由请求方指定）

整体情绪曲线检查（读者需求满足曲线）：
- 前期（前2-3场）：需求不被满足，烈度较高。读者看到"这说的就是我"，但角色还没得到她想要的。
- 中期（中间3-4场）：需求逐渐被看见，烈度开始降低。角色开始行动/离开/反击，但还没有彻底解决。
- 后期（最后1-2场）：需求被满足。烈度最低，但情绪最完整——角色走出来了，读者也跟着走出来。
- 推荐分布模式：
  * 7-8个场景：中-高-中-高-中-低-低
  * 5-6个场景：中-中-高-中-低
- 如果整体曲线不是前高后低（最高峰出现在后半段或结尾），请在修改建议中指出，并建议重排场景顺序。
- 检查需求是否在前期不被满足、后期被满足：如果不是，指出哪段断层了。
- **强制检查：每个场景的冲突真实性**——冲突的定义是两个人面对面互不相让、快干起来了。朋友的问询、家人的劝说、同事的闲聊——这些不是冲突，不算数。如果场景中没有真正的对抗（双方都在为自己的立场争），标记为"非冲突场景"并在修改建议中指出应该删掉或重写。
- **强制检查：开局是否有激烈的观念/价值观碰撞与吵架**
  - 第一场必须是一场真正的争论——双方都在为自己的立场辩论，没有人让步，没有人说"算了"结束话题
  - 不吵架，有个屁的冲突。如果第一场只是"一个人说话另一个人敷衍"或"一个人生气一个人回避"，则不合格
  - 如果不合格，必须给出修改方案：让双方各持一种价值观正面交锋，至少3-4轮攻防

输出格式（只输出以下内容，不要多余的解释）：

## 评分表
| 场景 | 名称 | 强度 | 说明 | 曲线位置 | 冲突真实性 |
|------|------|------|------|---------|-----------|
| 1 | [名称] | [分数] | [一句话理由] | [前/中/后期] | [真实/非冲突] |
...

## 验证结果
总分：[ ]
- 前两场 ≥ 3分：[是/否]
- 至少一场 ≥ 7分：[是/否]
- 终果 ≥ 6分：[是/否]
- 无连续同分：[是/否]
- 总分 ≥ 20分：[是/否]
- 开局价值观碰撞检查：[通过/不通过]
- 整体情绪曲线评估：[前高后低/最高峰在后半段]
- 读者需求满足：[前期不被满足→后期被满足/断层]
- **整体：通过 / 不通过**

## 修改建议
[如果不过，逐条说明：哪一场强度不够、原因是什么、怎么改（具体到交锋链的改法）
如果通过了，可以提优化建议：哪些场景可以再升级、冲突类型是否可以更多样]

## 曲线重排方案（如果需要）
[如果曲线不达标，给出具体的场景重排顺序，确保前高后低]

以下是待评分的场景清单：
"""


def call_deepseek(scene_text):
    api_key = os.environ.get("DEEPSEEK_API_KEY")
    if not api_key:
        print("错误：DEEPSEEK_API_KEY 环境变量未设置")
        sys.exit(1)

    headers = {
        "Authorization": f"Bearer {api_key}",
        "Content-Type": "application/json"
    }

    payload = {
        "model": "deepseek-v4-flash",
        "messages": [
            {"role": "system", "content": "你是冲突强度评分专家。严格按评分标准打分，输出评分表+验证结果+修改建议，不要多余的解释。"},
            {"role": "user", "content": SCORING_PROMPT + scene_text}
        ],
        "temperature": 0.1,
        "max_tokens": 2000
    }

    try:
        resp = requests.post(
            "https://api.deepseek.com/v1/chat/completions",
            headers=headers,
            json=payload,
            timeout=60
        )
        resp.raise_for_status()
        result = resp.json()
        return result["choices"][0]["message"]["content"]
    except requests.exceptions.Timeout:
        return "错误：请求超时（60秒），请重试"
    except requests.exceptions.RequestException as e:
        return f"错误：API 调用失败 — {e}"
    except (KeyError, json.JSONDecodeError) as e:
        return f"错误：返回格式异常 — {e}\n原始返回：{resp.text[:500]}"


if __name__ == "__main__":
    if len(sys.argv) > 1:
        with open(sys.argv[1], "r") as f:
            scenes = f.read()
    else:
        scenes = sys.stdin.read()

    if not scenes.strip():
        print("错误：请输入场景清单（文件参数或管道输入）")
        sys.exit(1)

    result = call_deepseek(scenes)
    print(result)
