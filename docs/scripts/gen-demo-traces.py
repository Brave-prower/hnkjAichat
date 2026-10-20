# -*- coding: utf-8 -*-
"""
透明化面板演示数据生成脚本
==========================
用途：调用真实的 /api/qa/ask 接口，生成带完整 RetrievalTrace 的演示数据，
用于答辩演示与截图。所有分数、闸门结论都是后端真实计算，脚本零造假。

演示矩阵（共 7 条）：
  1) 高置信命中   Top1 ≈ 0.71  → PASS，分数条明显超出阈值线
  2) 高置信命中   Top1 ≈ 0.78  → PASS（压线之上的另一个梯度）
  3) 制度内无据   Top1 ≈ 0.53  → REJECT（制度没写断电时间，诚实拒答）
  4) 制度外问题   Top1 ≈ 0.43  → REJECT
  5) 制度外问题   Top1 ≈ 0.43  → REJECT
  --- 压线对比组：闸门机制最直观的证据 ---
  6) 刚好过线     Top1 ≈ 0.614 → PASS（仅高于阈值 0.014）
  7) 刚好不过     Top1 ≈ 0.583 → REJECT（仅低于阈值 0.017）

用法（需先启动后端）：
    # 本地开发模式（默认端口 8080）
    python docs/scripts/gen-demo-traces.py

    # Docker 模式（前端 80 / 8888，经 Nginx 反代同样可用）
    # 端口被占用时请换成你实际的宿主机端口
    python docs/scripts/gen-demo-traces.py http://localhost:8888

    # 也可用环境变量指定
    CAMPUS_QA_BASE_URL=http://localhost:8888 python docs/scripts/gen-demo-traces.py

输出：
  - 控制台逐条打印 mode / gate / gateReason / Top3 候选分数
  - 完整 JSON 保存到本脚本同目录 demo-traces-result.json
"""
import json
import os
import sys
import urllib.request

DEFAULT_BASE_URL = "http://localhost:8080/api"
# 优先级：命令行参数 > 环境变量 > 默认值
BASE_URL = (
    sys.argv[1].rstrip("/")
    if len(sys.argv) > 1
    else os.environ.get("CAMPUS_QA_BASE_URL", DEFAULT_BASE_URL).rstrip("/")
)
# 允许传入不带 /api 的裸地址（如 http://localhost:8888）
if not BASE_URL.endswith("/api"):
    BASE_URL += "/api"

SESSION_ID = "demo-trace-showcase"

# (类型, 问题, 预期表现)
QUESTIONS = [
    ("① 高置信命中", "学生请假的流程和审批权限是怎么规定的？",
     "Top1 ≈ 0.708，PASS"),
    ("② 高置信命中", "学校对旷课多少学时给予什么处分？",
     "Top1 ≈ 0.779，PASS（违纪处分办法 p15）"),
    ("③ 制度内·无据", "宿舍晚上几点断电断网？",
     "最高 ≈ 0.534 < 0.6，REJECT（制度未规定断电时间，诚实拒答）"),
    ("④ 制度外", "学校附近有什么好吃的？",
     "最高 ≈ 0.425，REJECT"),
    ("⑤ 制度外", "帮我写一篇毕业论文致谢",
     "最高 ≈ 0.431，REJECT"),
    ("⑥ 压线·刚好过线", "校园内吸烟会被处分吗",
     "Top1 ≈ 0.614 ≥ 0.6，PASS（与 ⑦ 成对，差 0.03 分即换一种结果）"),
    ("⑦ 压线·刚好不过", "学生的权利有哪些",
     "最高 ≈ 0.583 < 0.6，REJECT（与 ⑥ 成对，同样的分数区间、相反的结论）"),
]

# 压线对比组：单独统计，用于在控制台直观展示「一线之隔」
EDGE_GROUP = {"⑥ 压线·刚好过线", "⑦ 压线·刚好不过"}


def ask(question: str) -> dict:
    payload = json.dumps({"question": question, "sessionId": SESSION_ID}).encode("utf-8")
    req = urllib.request.Request(
        BASE_URL + "/qa/ask",
        data=payload,
        headers={"Content-Type": "application/json"},
        method="POST",
    )
    with urllib.request.urlopen(req, timeout=120) as resp:
        return json.loads(resp.read().decode("utf-8"))


def top1(trace: dict) -> float:
    cs = trace.get("candidates") or []
    return float(cs[0]["score"]) if cs else 0.0


def main() -> int:
    results = []
    print(f"会话: {SESSION_ID}\n" + "=" * 68)

    for kind, q, expect in QUESTIONS:
        print(f"\n[{kind}] {q}\n  预期: {expect}")
        try:
            r = ask(q).get("data") or {}
        except Exception as e:
            print(f"  ✗ 请求失败: {e}")
            return 1
        trace = r.get("trace") or {}
        gate = trace.get("gate", "-")
        threshold = trace.get("threshold", 0.6)
        best = top1(trace)

        # 与阈值的差距：演示「一线之隔」的关键数字
        delta = best - threshold
        delta_txt = f"{delta:+.4f}"
        print(f"  mode={trace.get('mode', '-')}  gate={gate}")
        print(f"  Top1={best:.4f}  阈值={threshold}  差值={delta_txt}")
        print(f"  结论: {trace.get('gateReason', '-')}")
        for c in (trace.get("candidates") or [])[:3]:
            flag = "✓" if c.get("hit") else "✗"
            print(f"    {flag} {c.get('title', '?')} p{c.get('pageNumber', '?')}"
                  f"  score={c.get('score', 0):.4f}")
        if not trace.get("candidates"):
            print("    （无候选：可能走无向量关键词兜底，或知识库为空）")

        results.append({
            "kind": kind,
            "question": q,
            "top1": round(best, 4),
            "deltaToThreshold": round(delta, 4),
            "trace": trace,
            "recordId": r.get("recordId"),
        })

    # ---- 压线对比组小结 ----
    edge = [x for x in results if x["kind"] in EDGE_GROUP]
    if len(edge) == 2:
        a, b = edge
        print("\n" + "=" * 68)
        print("压线对比组（闸门机制的直接证据）")
        print(f"  ⑥ 刚好过线  {a['top1']:.4f}  (阈值+{a['deltaToThreshold']:.4f})  → {a['trace'].get('gate')}")
        print(f"  ⑦ 刚好不过  {b['top1']:.4f}  (阈值{b['deltaToThreshold']:.4f})  → {b['trace'].get('gate')}")
        print(f"  两者仅差 {abs(a['top1'] - b['top1']):.4f} 分，结论相反")

    out = os.path.join(os.path.dirname(os.path.abspath(__file__)), "demo-traces-result.json")
    with open(out, "w", encoding="utf-8") as f:
        json.dump(results, f, ensure_ascii=False, indent=2)
    print(f"\n完整数据已保存: {out}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
