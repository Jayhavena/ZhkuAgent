"""Materialize Codex's explicit offline reviews, never call a provider or change grading.
Semantic decisions below were made after reading all 21 answers and frozen cards.
Source/span checks are mechanical; semantic support remains the reviewer's decision.
"""
import hashlib, json
from pathlib import Path

HERE = Path(__file__).resolve().parent
ROOT = next(p for p in HERE.parents if (p / "knowledge/pom.xml").exists())
GOLD = ROOT / "docs/evidence/agent-eval/2026-11-17-frozen-gold-v1/rule-fact-check-effect-v1.0.json"
SOURCE = ROOT / "docs/product/rule-check-2025-verbatim-import-draft.json"
REVIEWER = "Codex（独立于答题模型的离线证据复核，非用户人工终审）"
def sha(b): return hashlib.sha256(b).hexdigest()
def read(p): return json.loads(p.read_text(encoding="utf-8-sig"))
def emit(p, value):
    with p.open("x", encoding="utf-8", newline="\n") as f:
        json.dump(value, f, ensure_ascii=False, indent=2); f.write("\n")

cards = {c["caseId"]: c for c in read(GOLD)["cases"]}
chunks = {c["contentChecksum"]: c for c in read(SOURCE)["chunks"]}
# Explicitly reviewed successful/clarification turns. All other listed cases published only failures.
DIRECT = {
    "RFE-003/ANSWER": (["hours-principle", "hours-upper-limit"], "SUPPORTED", "SUPPORTED",
        "回答同时保留16学时的原则值及32学时上限，未把原则值绝对化，满足题卡。",
        "问题条件明确，必答要素已齐，不需要额外澄清。"),
    "RFE-010/ANSWER": (["public-semester-four-credits"], "MISMATCH", "SUPPORTED",
        "每学期、公共选修课及4学分均准确，但正文未标明2025.9.2历史文本；与M1/M2同一正文判定口径，正确版本元数据不能补足漏写的限定。",
        "题目条件明确，已给出必要学分上限，无额外条件缺口。"),
    "RFE-013/ASK": ([], "SUPPORTED", "MISMATCH",
        "先询问公共/专业选修类别，未在必要条件未知时给统一能否重修结论。",
        "追问类别正确，但没有说明第18条对公共与专业两分支处理不同；与M2同一标准，漏必要解释而非分支选错。"),
    "RFE-013/RESOLVE_PUBLIC": (["public-failed-no-retake"], "SUPPORTED", "SUPPORTED",
        "同task已回复PUBLIC，答案限公共选修成绩不合格不安排重修，没有混入专业分支或承诺当前办理结果；续答沿用M2分支契约。",
        "已取得预声明PUBLIC条件，给出该分支规则，没有新增必需条件或编造办理结果。"),
}
FAILED = {"RFE-001","RFE-002","RFE-004","RFE-005","RFE-006","RFE-007","RFE-008","RFE-009","RFE-011","RFE-012","RFE-014","RFE-015","RFE-016","RFE-017","RFE-018","RFE-019","RFE-020"}
captures = sorted(HERE.glob("RFE-*/*/capture.json"))
assert len(captures) == 21
prepared, decisions, failure_reviews = [], [], []
for path in captures:
    c = read(path); case = c["turn"]["caseId"]; key = case + "/" + c["turn"]["kind"]
    answer = c["execution"]["answer"]; trace = c["execution"]["trace"]; pub = c["publishedResult"]
    claims = [x for item in pub.get("items", []) for x in item.get("claims", [])]
    capture_sha = sha(path.read_bytes()); checks = []
    for e in pub.get("evidence", []):
        source = chunks[e["checksum"]]
        # Runtime spans use Java UTF-16 indices, not Python's Unicode code point indices.
        raw = source["content"].encode("utf-16-le")
        quote = raw[e["startInclusive"]*2:e["endExclusive"]*2].decode("utf-16-le")
        assert sha(source["content"].encode("utf-8")) == e["checksum"]
        assert quote == e["quote"] and e["revision"] == "2025.9.2"
        checks.append({"evidenceId":e["id"], "revision":e["revision"], "checksum":e["checksum"],
            "article":source["article"], "spanStart":e["startInclusive"], "spanEnd":e["endExclusive"],
            "quoteMatchesSource":True, "sourceFile":str(SOURCE.relative_to(ROOT)).replace("\\","/"),
            "relations":[r for r in pub.get("evidenceRelations", []) if r["evidenceId"]==e["id"]]})
    if key in DIRECT:
        matched, scope, gap, scope_reason, gap_reason = DIRECT[key]
        assert trace["taskStatus"] in {"RESOLVED","WAITING_USER"}
        # These claims were individually read against the gold facts and the checked article quotes.
        assert {x["factId"].split(":")[-1] for x in claims} == set(matched)
        expected = {x["semanticFactId"]:x["expectedClaim"] for x in cards[case].get("expectedFacts", [])}
        for x in claims: assert x["text"] == expected[x["factId"].split(":")[-1]]
        support_reason = "已逐条核对已发布主张、冻结事实与对应原文；主张均获支持。未发表主张的ASK不凭空增添事实。覆盖遗漏由既有Grader另判。"
        mode = "INDEPENDENT_SEMANTIC_REVIEW"
    else:
        assert case in FAILED and trace["taskStatus"] in {"FAILED","STOPPED"} and not claims and not pub.get("evidence")
        assert answer.startswith("本轮模型响应未通过协议或语义核验") or answer.startswith("本轮达到执行边界")
        matched, scope, gap = [], "UNKNOWN", "UNKNOWN"
        scope_reason = "实际仅有失败/停止提示，未给实质规则答案；不能用安全错误提示证明已处理题卡的对象、条件和适用范围，保留不可判定。"
        gap_reason = "实际仅有执行失败/停止提示，未给题目所需的特定缺口/下一步说明；不将通用错误文案视为语义缺口判定通过。"
        support_reason = "实际结果无已发布规则主张；失败/停止通知与Trace相符，未凭空发布个人/当前政策事实。必答事实和引用缺失仍由既有覆盖评分保留。"
        mode = "REVIEWED_FAILURE_NOTIFICATION_NO_CLAIMS"
    def review(verdict, reason):
        return {"verification":verdict,"reviewer":REVIEWER,"reason":reason,"answerExcerpt":answer}
    annotation = {"captureSha256":capture_sha,"answerSha256":sha(answer.encode("utf-8")),
        "factsComplete":True,"matchedFacts":matched,"citationsComplete":True,
        "citationReviews":{e["id"]:"SUPPORTED" for e in pub.get("evidence", [])},
        "reviews":{"unsupportedClaims":review("SUPPORTED",support_reason),
            "scopeAndApplicability":review(scope,scope_reason),"clarificationAndGap":review(gap,gap_reason),
            "executionSafety":review("SUPPORTED","核对实际Trace/调用审计，未有代办教务操作或成功办理声明；这里只核验权限声明，FAILED/STOPPED及动作/终态不匹配仍由既有Trace Grader独立判FAIL。")}}
    prepared.append((path.parent/"annotation-reviewed.json", annotation))
    prepared.append((path.parent/"citation-checks.json", {"origin":"OFFLINE_SOURCE_SPAN_AND_INDEPENDENT_SUPPORT_REVIEW","sourceSha256":sha(SOURCE.read_bytes()),"checks":checks}))
    decisions.append({"caseId":case,"kind":c["turn"]["kind"],"mode":mode,"captureSha256":capture_sha,
        "answerSha256":annotation["answerSha256"],"matchedFacts":matched,"scope":scope,"clarificationAndGap":gap})
    if key in {"RFE-010/ANSWER","RFE-013/ASK"}:
        reason = scope_reason if case=="RFE-010" else gap_reason
        failure_reviews.append({"caseId":case,"capturePath":str(path.relative_to(HERE)).replace("\\","/"),
            "captureSha256":capture_sha,"category":"ANSWER_INCOMPLETE",
            "subtype":"HISTORICAL_QUALIFIER_MISSING" if case=="RFE-010" else "BRANCH_DIFFERENCE_EXPLANATION_MISSING",
            "defect":True,"reviewer":REVIEWER,"reason":reason,"answerExcerpt":answer})
assert len(prepared)==42
for path, value in prepared: emit(path, value)
emit(HERE/"review-decisions.json", {"reviewer":REVIEWER,"goldSha256":sha(GOLD.read_bytes()),"providerCalls":0,
    "policy":"Same M1/M2 semantic policy; failure prose does not establish scope/gap quality; metadata does not replace required historical prose.","decisions":decisions})
emit(HERE/"failure-reviews.json", failure_reviews)
print("PASS offline annotation materialization: 21 independently reviewed turns, source/span checks; providerCalls=0")
