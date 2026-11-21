"""Build a report/visual from the existing graded real captures. Offline only."""
import hashlib, html, json
from pathlib import Path
H=Path(__file__).resolve().parent
R=next(p for p in H.parents if (p/"knowledge/pom.xml").exists())
def read(p):return json.loads(p.read_text(encoding="utf-8-sig"))
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def emit(p,v):
    with p.open("x",encoding="utf-8") as f:json.dump(v,f,ensure_ascii=False,indent=2);f.write("\n")
report=read(H/"report-reviewed.json");q=report["qualityReport"];audit=read(H/"audit-verification.json");breakdown=read(H/"execution-breakdown.json")
oldpath=R/"docs/evidence/rule-agent-provider/2026-11-17-frozen-m2/live-13bba9842e834136acb1e37ff87009fb/report-reviewed.json"
old=read(oldpath)["qualityReport"];common=list(old["cases"]);turns=breakdown["turns"]
assert q["denominator"]==20 and q["passed"]==1 and q["failed"]==19 and q["fullyEvaluable"]==3
priorities=[
    ("P0","结构化动作参数契约",["RFE-005","RFE-007","RFE-009","RFE-011","RFE-012","RFE-017"],
     "第三次模型决策被ACTION_PARAMETERS:ACTION_PARAMETER_INVALID拒绝；前两步已有成功检索/读取。",
     "先核对模型动作输出与宿主schema/参数映射，补字段级脱敏拒绝诊断和离线协议夹具；不能凭缺失的原始报文猜具体错误字段。",
     "该六题的对应拒绝消失，动作参数能按同一协议通过校验；再以独立获批真实包复验，不能只看HTTP200。"),
    ("P0","发布核验链路",["RFE-001","RFE-004","RFE-008","RFE-019"],
     "001/004/019的PUBLICATION_REVIEW记录FACT_NOT_RELEVANT；008的核验响应被REVIEW_JSON:REVIEW_JSON_INVALID拒绝。",
     "核对候选结论、证据关系与核验输入的映射及核验JSON协议，补可解释的输入/输出诊断。现有记录不足以区分错误结论与核验误拒，保留该根因边界。",
     "每次拒绝能定位到同run的候选主张/证据与字段，且正确候选可稳定完成发布；不放宽门禁掩盖失败。"),
    ("P1","结束与修复动作的进展",["RFE-002","RFE-006","RFE-014","RFE-015","RFE-016","RFE-018","RFE-020"],
     "002/006/014/018/020为BUDGET_EXCEEDED，015/016为NO_PROGRESS；部分FINISH_CHECK重复或被拒。",
     "检查被拒结束动作后的修复路径、重复判定与核验预留；明确具体预算触发点，优先减少无效步骤。BUDGET_EXCEEDED不是110秒超时的同义词。",
     "在原有单run5次/110秒上限内完成合法终态，或保留具体可解释的失败原因；不自动增加额度/重试。"),
    ("P1","答案与澄清的必要说明",["RFE-010","RFE-013"],
     "010正确给4学分但漏历史文本限定；013正确追问/选择PUBLIC，但ASK没解释两类课程处理不同。",
     "让发布答案携带题卡要求的历史范围，让必要澄清包含分支差异说明，继续使用既有Grader及正文判定口径。",
     "010正文明确2025.9.2历史依据；013 ASK说明分支差异且不提前统一判断，PUBLIC续答保持正确。")]
items=[]
for level,title,ids,observed,action,acceptance in priorities:
    refs=[{k:t[k] for k in ("caseId","kind","capturePath","captureSha256","providerTracePath","providerTraceSha256")} for t in turns if t["caseId"] in ids]
    items.append({"priority":level,"title":title,"affectedCases":ids,"observed":observed,"nextAction":action,"acceptance":acceptance,"evidence":refs})
assert sum(len(p[2]) for p in priorities)==19
comparison={"policy":"Descriptive paired comparison on original common ten only; one trial per batch, no causal or statistical improvement claim",
    "m2ReportSha256":sha(oldpath),"m3ReportSha256":sha(H/"report-reviewed.json"),"commonCases":common,
    "m2":{"passed":old["passed"],"denominator":10,"fullyEvaluable":old["fullyEvaluable"],"calls":42},
    "m3CommonTen":{"passed":sum(q["cases"][k]["outcome"]=="PASS" for k in common),"denominator":10,
        "fullyEvaluable":sum(q["cases"][k]["fullyEvaluable"] for k in common),"calls":sum(t["calls"] for t in turns if t["caseId"] in common)},
    "caseOutcomes":[{"caseId":k,"m2":old["cases"][k]["outcome"],"m3":q["cases"][k]["outcome"],"m2FullyEvaluable":old["cases"][k]["fullyEvaluable"],"m3FullyEvaluable":q["cases"][k]["fullyEvaluable"]} for k in common]}
emit(H/"baseline-comparison-results.json",comparison);emit(H/"repair-priorities.json",{"origin":"OBSERVED_REAL_FAILURE_LAYERS_NOT_UNPROVEN_ROOT_CAUSES","priorities":items})
lines=["# 20题开发集真实质量基线（M3）","",
    "**里程碑交付验收：PASS；Agent回答质量：FAIL。** 冻结20题已全部实际执行并进入报告；这是同一手册文档组的探索性开发集、每题一次的基线，不是独立测试集或通用能力成绩。语义标注由Codex离线独立核验，不是用户人工终审。","",
    "20任务、21run，RFE-013在实际必要追问后按预声明回复PUBLIC；79次DashScope调用，全部HTTP200且响应模型为qwen3.8-flash。无自动重试/替换/重复试验，未用26次额度已关闭、剩余授权0，临时容器已移除。标准Execute入口实际完成，未使用M2的监督器替代方案。","",
    "**1 PASS / 19 FAIL / 0 UNJUDGEABLE / 0 ERROR；通过率1/20=5%。** 已确定整题结论20/20，但全部适用维度可评估仅3/20=15%（003、010、013）。其余17题确定执行失败，适用性和澄清/缺口维度仍未知，不能以错误提示代替语义通过。","",
    "证据入口：[逐题逐维判定](report-reviewed.md)、[JSON报告](report-reviewed.json)、[授权/预算审计](audit-verification.json)、[21轮动作与错误分类](execution-breakdown.json)、[复核决定](review-decisions.json)、[机器可读修复优先级](repair-priorities.json)。[批准回执](execution-approval.json)和[永久预留](execution-reserved.json)原字节已由[batch-plan](batch-plan.json)摘要绑定；[关闭记录](batch-closeout.json)、[容器清理](container-cleanup.json)、[执行后包核对](package-postcheck.json)可回溯。","",
    "## 六维结果","","| 维度 | 适用分母 | PASS | FAIL | 未知 | ERROR | NA | 可评估比例 |","| --- | --- | --- | --- | --- | --- | --- | --- |"]
names={"retrievalRecall":"必要定位召回","factSupport":"必答事实/支持","citationIntegrity":"引用完整性","scopeAndApplicability":"范围/适用性","clarificationAndGap":"澄清/缺口","executionSafety":"执行契约/安全"}
for k,d in q["dimensions"].items():
    lines.append(f'| {names[k]} | {d["applicable"]} | {d["passed"]} | {d["failed"]} | {d["unjudgeable"]} | {d["errors"]} | {d["notApplicable"]} | {d["passed"]+d["failed"]}/{d["applicable"]} |')
lines += ["","必要定位召回只说明冻结必要定位进入真实候选观察，不证明模型正确选读、理解或发布了完整答案。020无必要定位/必答事实/引用要求，前三维为NA。事实/引用的16个FAIL来自执行失败后没有发布必需内容，不能将其写成16次事实编造或错引。4份实际发布证据的原文摘要、UTF-16片段及引用支持已核验。","",
    "## 全部20题","","| 题目 | 结果 | 全维可评估 | 真实终态 | 调用 | 观察到的问题/结论 | 证据 |","| --- | --- | --- | --- | --- | --- | --- |"]
for cid,c in q["cases"].items():
    ts=[t for t in turns if t["caseId"]==cid]
    issue="全部六维通过" if cid=="RFE-003" else "正文漏2025.9.2历史限定" if cid=="RFE-010" else "ASK漏分支差异解释；PUBLIC续答正确" if cid=="RFE-013" else "; ".join(sorted({x for t in ts for x in t["terminalIssues"]}))
    refs=" / ".join(f'[{t["kind"]}]({t["capturePath"]})' for t in ts)
    lines.append(f'| {cid} | {c["outcome"]} | {"是" if c["fullyEvaluable"] else "否"} | {"→".join(t["taskStatus"] for t in ts)} | {sum(t["calls"] for t in ts)} | {issue} | {refs} |')
lines += ["","每轮capture、annotation及其SHA见逐维报告；每题provider审计路径和SHA见execution-breakdown.json。013的ASK与续答分别复核，同task、不同run、PUBLIC链路已核验，按一题计分。","",
    "## 失败分类与证据边界","","| 要求的类别 | 已证实观察 | 解释 |","| --- | --- | --- |",
    "| 检索失败 | 0/19适用题 | 19/19必要定位召回，不据此宣称排序/选读最优 |",
    "| 条件判断错误 | 已发布结果中未证实错误分支；17题实质语义未知 | 013选PUBLIC正确，漏解释归回答遗漏，不伪造条件误判 |",
    "| 证据不足 | 未证实不当缺证据断言；个人/当期/范围外边界题未完成实质答案 | 008个人成绩、018当期截止、019当前适用性、020语料外奖学金本有预期边界；这些边界不是缺陷，但本轮也未证明Agent已正确处理 |",
    "| 引用问题 | 16题缺必需引用；4份已发表证据核验通过 | 缺引用伴随执行失败，自动分类保留待归因，不当作16次伪造/错版本；报告不把待归因计入已证实Agent引用错误排行 |",
    "| 执行异常 | 17题：10 FAILED、7 STOPPED | 6题动作参数拒绝、3题FACT_NOT_RELEVANT、1题核验JSON非法、5题BUDGET_EXCEEDED、2题NO_PROGRESS；宿主/标注ERROR=0 |",
    "| 回答要素遗漏 | 2题 | 010历史范围、013 ASK分支差异说明 |","",
    "17题全部保留终态与已知错误层。79个响应均HTTP200、最长单次5.855秒、最长run12.589秒，故本批BUDGET_EXCEEDED不能描述为110秒超时，也不能一概解释成5次调用耗尽（020用了5次，其余此类题4次）。没有原始Provider报文归档，FACT_NOT_RELEVANT不能被进一步武断归为模型捏造或核验器误判。","",
    "## 下一轮优先修复什么",""]
for item in items:
    refs="、".join(f'[{cid}]({next(t["capturePath"] for t in turns if t["caseId"]==cid)})' for cid in item["affectedCases"])
    lines += [f'### {item["priority"]} · {item["title"]}（{len(item["affectedCases"])}题）',"",f'证据：{refs}。{item["observed"]}',"",item["nextAction"],"",f'验收：{item["acceptance"]}',""]
lines += ["修复优先级先覆盖结构化动作与核验链路，再处理结束流程及必要说明。必要定位19/19已召回，现有证据支持先修消费证据到发布结果的稳定性。以上是下一轮建议，本轮没有改生产提示词/工具/发布门禁来改变成绩。","",
    "## 与M2可比较的部分","","| 指标（共同10题） | M2首批 | M3本次共同10题 |","| --- | --- | --- |",
    f'| 整题通过 | 1/10 | {comparison["m3CommonTen"]["passed"]}/10 |',
    f'| 全部适用维度可评估 | 5/10 | {comparison["m3CommonTen"]["fullyEvaluable"]}/10 |',
    f'| 调用数 | 42 | {comparison["m3CommonTen"]["calls"]} |',"",
    "共同10题整题PASS/FAIL均未变，但局部可评估比例下降；这是单次描述性观察，不能因M3全量1/20低于M2的1/10就认定模型变差。模型、两份系统提示词、工具协议/实现、资料与六维Grader一致；选择范围、批次宿主/报告实现及jar不同，详见[执行前配置比较](../baseline-comparison-r1.json)和[实际共同题比较](baseline-comparison-results.json)。唯一文档组、探索性开发集、无重复、随机标识/响应可能变化，均限制外推。","",
    "## 可复现身份与留存","",
    f'最终包SHA：`{audit["packageSha256"]}`。数据版本gold-v1.0；完整模型/提示词/动作和发布协议/资料SHA/预算/工具及Grader源码SHA/jar身份见[实际基线身份](baseline-identity.json)及[冻结请求包](../request-package.json)。',"",
    "本轮不增加重复试验：首轮已交付所有20题，且结构化失败是当前主要修复对象；剩余26次调用授权已关闭。下一轮修复后先对明确失败层限量复验，再以新包/新授权比较全量，避免未修复前继续消耗调用。","",
    "41项离线回归、监督器零调用检查、3次包核对、20题临时库Assembly的历史准备证据见上级README；本轮标准Execute、预算/授权/隔离/清理审计与离线Grader报告为不同验收层，不互相替代。实际宿主用时176.326秒（上限2550秒），输入132869 tokens、输出7266 tokens。未计算货币成本。","",
    "仓库main Worktree；分支feat/zhkuagent-v3-frontend；HEAD338c1ea184ac6240cdaaaaa5045d1c24e72dcc0a。保留现有未提交变更，未提交/推送。真实原文/回答仅本地受控留存30天，不自动进入Git/CI/公开上传。"]
with (H/"quality-report.md").open("x",encoding="utf-8") as f:f.write("\n".join(lines)+"\n")

bars=[]
for key,d in q["dimensions"].items():
    segments="".join(f'<span class="{cls}" style="width:{d[field]*5}%" title="{field}: {d[field]}">{d[field] if d[field] else ""}</span>' for cls,field in [("pass","passed"),("fail","failed"),("unknown","unjudgeable"),("na","notApplicable")] if d[field])
    bars.append(f'<div class="row"><label>{names[key]}</label><div class="bar">{segments}</div><small>{d["passed"]}/{d["applicable"]}通过</small></div>')
page='''<!doctype html><html lang="zh-CN"><head><meta charset="UTF-8"><meta name="viewport" content="width=device-width,initial-scale=1"><style>
body{font-family:var(--font-sans);color:var(--foreground);margin:0}h2{font-size:19px;margin:0 0 8px}p,small{color:var(--muted-foreground);font-size:12px}.stats{display:grid;grid-template-columns:repeat(4,1fr);gap:12px;margin:16px 0 24px}.stats strong{display:block;font-size:26px;font-variant-numeric:tabular-nums}.stats span{font-size:12px;color:var(--muted-foreground)}.row{display:grid;grid-template-columns:116px 1fr 86px;gap:8px;align-items:center;margin:12px 0;font-size:12px}.bar{height:24px;display:flex;overflow:hidden;border-radius:4px}.bar span{display:flex;align-items:center;justify-content:center;color:var(--background);font-size:12px;font-weight:600}.pass{background:var(--success)}.fail{background:var(--destructive)}.unknown{background:var(--muted-foreground)}.na{background:var(--info)}.legend{display:flex;gap:16px;font-size:12px;margin:16px 0}.legend i{display:inline-block;width:9px;height:9px;margin-right:5px}.paired{margin-top:22px;border-top:1px solid var(--border);padding-top:16px}.paired .row{grid-template-columns:116px 1fr 86px}.note{line-height:1.6}@media(max-width:440px){.row,.paired .row{grid-template-columns:88px 1fr 65px;gap:5px}.stats{gap:7px}.stats strong{font-size:23px}.legend{gap:10px}}
</style></head><body><h2>20题真实开发集基线</h2><p>里程碑交付PASS · 回答质量FAIL · 单次试验 · qwen3.8-flash</p><div class="stats"><div><strong>1 / 20</strong><span>整题通过 · 5%</span></div><div><strong>3 / 20</strong><span>全维可评估 · 15%</span></div><div><strong>17</strong><span>执行失败/停止题</span></div><div><strong>79</strong><span>实际调用 · 余26已关闭</span></div></div><div class="legend"><span><i class="pass"></i>PASS</span><span><i class="fail"></i>FAIL</span><span><i class="unknown"></i>未知</span><span><i class="na"></i>NA</span></div>'''+"".join(bars)+'''<p class="note">每条以20题为分母；右侧通过数/适用分母排除NA。16题事实与引用FAIL是执行后缺必答内容，不代表16次事实编造或错引。</p><div class="paired"><strong>共同10题：全维可评估比例</strong><div class="row"><label>M2首批</label><div class="bar"><span class="pass" style="width:50%">5</span><span class="unknown" style="width:50%">5</span></div><small>5/10</small></div><div class="row"><label>M3本次共同题</label><div class="bar"><span class="pass" style="width:20%">2</span><span class="unknown" style="width:80%">8</span></div><small>2/10</small></div><p class="note">两批共同题整题通过均为1/10。单次描述性比较，批次实现与jar不同；不能据此声称统计显著或因果变化。优先修复动作参数、发布核验和结束流程。</p></div></body></html>'''
with (H/"quality-summary.html").open("x",encoding="utf-8") as f:f.write(page)
print("PASS delivery draft: 20-case report, four evidence-bound priority groups, paired common-ten comparison, chart")
