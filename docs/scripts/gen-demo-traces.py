# -*- coding: utf-8 -*-
"""
透明化面板演示数据生成脚本
==========================
用途：调用 /api/qa/ask 生成 5 组真实检索 trace 数据，用于演示与截图。

- 2 组 PASS（高置信 0.71 / 压线 0.78）
- 3 组 REJECT（含一个「看似制度内实则无据」的问题，展示诚实拒答）

用法（需先启动后端）：
    python docs/scripts/gen-demo-traces.py

输出：逐条打印 mode / gate / gateReason / Top3 候选分数，并把完整 JSON
保存到 docs/scripts/demo-traces-result.json。
"""
import json
import sys
import urllib.request

BASE_URL = "http://localhost:8080/api"
SESSION_ID = "demo-trace-showcase"

# (类型, 问题, 预期表现)
QUESTIONS = [
    ("制度内·命中", "学生请假的流程和审批权限是怎么规定的？", "Top1 0.708，PASS"),
    ("制度内·无据", "宿舍晚上几点断电断网？", "最高 0.534 < 0.6，REJECT（制度未规定断电时间，诚实拒答）"),
    ("制度内·命中", "学校对旷课多少学时给予什么处分？", "Top1 0.779，PASS（违纪处分办法 p15）"),
    ("制度外·拒答", "学校附近有什么好吃的？", "最高 0.425，REJECT"),
    ("制度外·拒答", "帮我写一篇毕业论文致谢", "最高 0.431，REJECT"),
]


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


def main() -> int:
    results = []
    print(f"会话: {SESSION_ID}\n" + "=" * 60)
    for kind, q, expect in QUESTIONS:
        print(f"\n[{kind}] {q}\n  预期: {expect}")
        try:
            r = ask(q).get("data") or {}
        except Exception as e:
            print(f"  ✗ 请求失败: {e}")
            return 1
        trace = r.get("trace") or {}
        gate = trace.get("gate", "-")
        reason = trace.get("gateReason", "-")
        mode = trace.get("mode", "-")
        print(f"  mode={mode}  gate={gate}")
        print(f"  结论: {reason}")
        for c in (trace.get("candidates") or [])[:3]:
            flag = "✓" if c.get("hit") else "✗"
            print(f"    {flag} {c.get('title','?')} p{c.get('pageNumber','?')}  score={c.get('score', 0):.3f}")
        if not trace.get("candidates"):
            print("    （无候选，走无向量关键词兜底或空库）")
        results.append({"kind": kind, "question": q, "trace": trace, "recordId": r.get("recordId")})

    out = "demo-traces-result.json"
    with open(out, "w", encoding="utf-8") as f:
        json.dump(results, f, ensure_ascii=False, indent=2)
    print(f"\n完整数据已保存: {out}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
