# -*- coding: utf-8 -*-
"""生成剩余 14 题的逐题审核清单。

输入（只读）：
- knowledge/src/test/resources/agent-effect-eval/rule-fact-check-effect-v2-dev.json
- docs/evidence/agent-eval/2026-11-06-review-cards/审核卡.md（条款原文与已通过题）
- docs/evidence/agent-eval/2026-11-06-review-cards/检查结果.json（风险提示）

输出：审核清单-14题.md
本脚本不修改任何数据、卡片或测试资源。
"""

import hashlib
import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[4]
DATASET = ROOT / "knowledge/src/test/resources/agent-effect-eval/rule-fact-check-effect-v2-dev.json"
CARD = ROOT / "docs/evidence/agent-eval/2026-11-06-review-cards/审核卡.md"
CHECK = ROOT / "docs/evidence/agent-eval/2026-11-06-review-cards/检查结果.json"
OUT = Path(__file__).resolve().parent / "审核清单-14题.md"

data = json.loads(DATASET.read_text(encoding="utf-8"))
card = CARD.read_text(encoding="utf-8")
checks = json.loads(CHECK.read_text(encoding="utf-8"))
dataset_sha = hashlib.sha256(DATASET.read_bytes()).hexdigest()

# 已通过题号：以审核卡中的 [x] 通过 为准，不以数据集状态为准（意见尚未回写）
approved = set()
for block in re.split(r"^#### ", card, flags=re.M)[1:]:
    m = re.match(r"(RFE-\d+)", block)
    if m and "[x] 通过" in block:
        approved.add(m.group(1))

# 条款原文与页码行：来自 B03 快照审核卡
articles = {}
for m in re.finditer(r"^### 第 (\d+) 条 · (handbook-2025-article-\d+)\n(.*?)(?=^### |\Z)", card, re.M | re.S):
    art, locator, body = m.group(1), m.group(2), m.group(3)
    quotes = re.findall(r"^> ?(.*)$", body, re.M)
    page = re.search(r"PDF 物理页 (\d+)，印刷页 (\d+)", body)
    articles[locator] = {
        "article": art,
        "text": "\n".join(quotes).strip(),
        "physical": page.group(1) if page else "",
        "printed": page.group(2) if page else "",
    }

pending = [c for c in data["cases"] if c["caseId"] not in approved]
pending.sort(key=lambda c: int(c["caseId"].split("-")[1]))

assert len(pending) == 14, f"预期 14 题，实际 {len(pending)}：{[c['caseId'] for c in pending]}"

lines = []
lines.append("# 剩余 14 题逐题审核清单")
lines.append("")
lines.append("状态：**阅读辅助清单，不是签字页，不是数据集变更。**")
lines.append("")
lines.append(f"- 数据集：`rule-fact-check-effect-v2-dev.json`（`{dataset_sha}`），`datasetVersion=v2-dev`。")
lines.append("- 依据：B03 保存的 2025.9.2 条款快照；**原始 PDF 字节不在工作树**，当期适用性与实际运行时绑定未核验。")
lines.append("- 已通过 6 题（RFE-001/002/003/013/018/020）不在本清单内；其中 RFE-020 仅确认回答预期。")
lines.append("- 审核意见仍记录在 [审核卡.md](../2026-11-06-review-cards/审核卡.md)；本清单不回写数据集，也不改变任何审核状态。")
lines.append("")
lines.append("## 怎么用")
lines.append("")
lines.append("每题按四步核对，再给结论：")
lines.append("")
lines.append("1. **问题与预期是否匹配**：这道问法是否真的需要这条规则/事实来回答。")
lines.append("2. **事实与引用是否成立**：`expectedClaim` 是否被 B03 原文逐字支持，locator/页码是否正确。")
lines.append("3. **回答应包含 / 不得声称是否够用**：是否覆盖该题的主要失败模式（扩大结论、编造当期信息、越权等）。")
lines.append("4. **适用条件与缺口是否说清**：该题若考边界，是否明确“仅凭这条不能推出什么”。")
lines.append("")
lines.append("结论三选一：通过 / 需修改 / 暂无法判断；需修改时请写具体改哪一条。")
lines.append("")
lines.append("## 总览")
lines.append("")
lines.append("| # | 题号 | 问题 | 题型 | 预期处置 | 事实 | 证据 | 风险提示 |")
lines.append("|---|---|---|---|---|---:|---:|---:|")
for i, c in enumerate(pending, 1):
    risks = checks.get("riskFlags", {}).get(c["caseId"], [])
    q = c["question"].replace("|", "\\|")
    lines.append(
        f"| {i} | {c['caseId']} | {q} | `{c['caseFamily']}` | `{c['expectedDisposition']}` "
        f"| {len(c['expectedFacts'])} | {len(c['requiredEvidence'])} | {len(risks)} |"
    )
lines.append("")

for i, c in enumerate(pending, 1):
    risks = checks.get("riskFlags", {}).get(c["caseId"], [])
    locators = []
    seen = set()
    for ev in c["requiredEvidence"]:
        key = ev["locatorId"]
        if key in seen:
            continue
        seen.add(key)
        locators.append(ev)

    lines.append(f"## {i}. {c['caseId']} · {c['question']}")
    lines.append("")
    lines.append("| 项 | 值 |")
    lines.append("|---|---|")
    lines.append(f"| 题型 | `{c['caseFamily']}` |")
    lines.append(f"| 覆盖 | `{c['sourceCoverage']}` |")
    lines.append(f"| 预期处置 | `{c['expectedDisposition']}` |")
    if c["requiredEvidence"]:
        refs = "；".join(
            f"第 {ev['article']} 条 / 物理页 {ev['physicalPage']} / 印刷页 {ev['printedPage']} / `{ev['locatorId']}`"
            for ev in c["requiredEvidence"]
        )
    else:
        refs = "无（语料外或证据缺口题）"
    lines.append(f"| 引用 | {refs} |")
    lines.append("| 数据集状态 | `PENDING`（审核卡意见尚未回写） |")
    lines.append("")

    if locators:
        lines.append("**条款原文（B03 2025.9.2 快照）：**")
        lines.append("")
        for ev in locators:
            a = articles.get(ev["locatorId"])
            if a:
                lines.append(f"> {a['text']}")
                lines.append(f">")
                lines.append(f"> —— 第 {a['article']} 条，物理页 {a['physical']}，印刷页 {a['printed']}")
                lines.append("")
            else:
                lines.append(f"> （未在审核卡中找到 `{ev['locatorId']}` 的原文，需回查 B03）")
                lines.append("")
    else:
        lines.append("**条款原文：** 无。该题应识别语料外信息或证据缺口，不应引用不存在的条款。")
        lines.append("")

    lines.append("**预期事实：**")
    lines.append("")
    if c["expectedFacts"]:
        for f in c["expectedFacts"]:
            lines.append(f"- `{f['reviewItemId']}/{f['semanticFactId']}`：{f['expectedClaim']}")
    else:
        lines.append("- 无（该题不应从现有语料得出事实结论）")
    lines.append("")

    lines.append("**回答应包含：**")
    lines.append("")
    for r in c["requiredResponseElements"]:
        lines.append(f"- {r}")
    lines.append("")

    lines.append("**不得声称：**")
    lines.append("")
    for m in c["mustNotClaim"]:
        lines.append(f"- {m}")
    lines.append("")

    if risks:
        lines.append("**风险提示（关键词与字段提示，非已确认问题）：**")
        lines.append("")
        for r in risks:
            lines.append(f"- {r}")
        lines.append("")

    lines.append("**审核记录：**")
    lines.append("")
    lines.append("- [ ] 问题与预期匹配；事实及引用支持预期。")
    lines.append("- [ ] 适用条件、缺口与禁止断言合适。")
    lines.append("- [ ] 通过　- [ ] 需修改　- [ ] 暂无法判断")
    lines.append("- 审核人 / 日期：________　意见：________________")
    lines.append("")

lines.append("## 附：本清单未覆盖的已通过题")
lines.append("")
lines.append("RFE-001、RFE-002、RFE-003、RFE-013、RFE-018、RFE-020（用户 2026-11-06 确认）。")
lines.append("RFE-020 仅确认“回答预期”，`CITATION_MISMATCH` 在无引用场景下的评分语义仍待定。")
lines.append("")

OUT.write_text("\n".join(lines) + "\n", encoding="utf-8")
print(f"written: {OUT}")
print(f"pending: {len(pending)} -> {[c['caseId'] for c in pending]}")
print(f"approved: {sorted(approved)}")
