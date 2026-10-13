"""离线核对查证评估资产；不调用数据库、模型，也不自动批准人工判断。"""
import argparse
import hashlib
import json
from copy import deepcopy
from datetime import datetime, timezone
from pathlib import Path

JUDGMENTS = {"PASS", "FAIL", "UNJUDGEABLE", "ERROR"}
SEVERITIES = {"CRITICAL", "MAJOR", "MINOR", "NONE"}
STATES = {"ANSWERED", "PARTIALLY_ANSWERED", "NEED_CLARIFICATION",
          "INSUFFICIENT_EVIDENCE", "CONFLICT"}
PDF_SHA = "dd036279ca21beea40351815d21d6d49f1037448a4df45c95e1eb2f8b199f653"


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def case_verdict(rows, required_ids):
    # 缺项或重复标签属于评分失效，不能让最后一条PASS覆盖已记录的FAIL。
    ids = [r.get("id") for r in rows]
    if not required_ids or len(ids) != len(set(ids)) or set(ids) != set(required_ids):
        return "ERROR"
    for row in rows:
        rating, severity = row.get("judgment"), row.get("severity")
        if rating not in JUDGMENTS or severity not in SEVERITIES:
            return "ERROR"
        if (rating == "FAIL") != (severity != "NONE"):
            return "ERROR"
        # MINOR体验问题另记；不改变任务成功的修饰问题不能写入核心失败标签。
        if rating == "FAIL" and severity == "MINOR":
            return "ERROR"
    ratings = {r["judgment"] for r in rows}
    # 已确认的产品错误不会被其他维度的高分或未知抵消；无确认失败时才区分评估失效。
    for verdict in ["FAIL", "ERROR", "UNJUDGEABLE"]:
        if verdict in ratings:
            return verdict
    return "PASS"


def validate_cases(cases, source_articles, development_articles, development_families):
    errors = []
    ids = [c.get("caseId") for c in cases]
    if len(cases) != 16 or len(set(ids)) != 16 or None in ids:
        errors.append("报告必须有16个唯一caseId")
    for case in cases:
        cid = case.get("caseId")
        if case.get("questionFamily") in development_families or not case.get("questionFamily"):
            errors.append(f"{cid}:家族与开发集重叠或未声明")
        evidence = case.get("requiredEvidence", [])
        articles = {e.get("article") for e in evidence}
        if not articles or not articles <= source_articles:
            errors.append(f"{cid}:必要证据缺失或未覆盖")
        if articles & development_articles:
            errors.append(f"{cid}:必要条款跨开发集合")
        if not case.get("requiredFacts") or case.get("expectedStatus") not in STATES:
            errors.append(f"{cid}:参考事实或状态缺失")
        if not case.get("applicableRubricIds"):
            errors.append(f"{cid}:缺评分适用性清单")
    return errors


def validate_frozen_content(current, presented, manifest_files, expected_files):
    errors = []
    declared = [entry.get("path") for entry in manifest_files]
    if len(declared) != len(set(declared)) or set(declared) != set(expected_files):
        errors.append("冻结文件清单必须完整、唯一，不能为空或漏项")
    for entry in manifest_files:
        if expected_files.get(entry.get("path")) != entry.get("sha256"):
            errors.append("冻结文件指纹不符")
    allowed = {
        "report":{"version","status","locked","humanReferenceApproved","usableForProductQualityGate","humanReview"},
        "source":{"status","humanQualificationConfirmed","humanReview"},
        "rubric":{"version","status","locked","humanRubricApproved","humanReview"}}
    for kind in ["report","source","rubric"]:
        before, after = deepcopy(presented[kind]), deepcopy(current[kind])
        for item in [before, after]:
            for key in allowed[kind]:
                item.pop(key, None)
            if kind == "report":
                for case in item.get("cases", []):
                    case.pop("annotationStatus", None)
                    case.pop("humanReview", None)
                item.get("familyAudit", {}).pop("semanticAuditHumanConfirmed", None)
                # 资格状态可能改变包hash；原文语义由source快照检查独立保护。
                item.get("sourceSupplement", {}).pop("sha256", None)
        if before != after:
            errors.append(f"{kind}:冻结语义与实际呈审快照不一致")
    return errors


def required_case_dimensions(case, actual_status):
    required = set(case["applicableRubricIds"])
    # 实际声称部分回答时也须查独立性，不能用缺证金标准跳过关键错误。
    if actual_status == "PARTIALLY_ANSWERED" or case["expectedStatus"] == "PARTIALLY_ANSWERED":
        required.add("E07")
    return required


def audit(root, pdf):
    product = root / "docs/product"
    evidence_dir = root / "docs/evidence/rule-check-refactor/2026-10-09/c01-c02-eval-assets-01"
    paths = {"report": product / "rule-check-2025-human-reference-report.json",
             "source": product / "rule-check-2025-report-source-supplement.json",
             "rubric": product / "rule-check-2025-scoring-rubric.json"}
    data = {k: json.loads(p.read_text(encoding="utf-8-sig")) for k, p in paths.items()}
    report, source, rubric = data["report"], data["source"], data["rubric"]
    errors = []
    chunks = {c["article"]: c for c in source["chunks"]}
    if set(chunks) != {1, 3, 7, 13, 14, 15, 16, 17, 19, 20} or len(source["chunks"]) != 10:
        errors.append("补充原文条款集合或唯一性不符合本批范围")
    dev = json.loads((product / "rule-check-2025-human-reference-development.json").read_text(encoding="utf-8-sig"))
    dev_articles = {e["article"] for c in dev["records"] for e in c["sourceEvidence"]}
    errors += validate_cases(report["cases"], set(chunks), dev_articles,
                            set(report["familyAudit"]["developmentTaskFamilies"]))
    if len(dev["records"]) != 20 or not dev["humanReferenceApproved"]:
        errors.append("已确认20题开发参考状态异常")
    for key in ["sourceSupplement", "developmentReference", "originDraft"]:
        entry = report[key]
        if digest(root / entry["path"]) != entry["sha256"]:
            errors.append(f"报告输入指纹变化:{key}")
    if digest(pdf) != PDF_SHA or source["sourceFileSha256"] != PDF_SHA or report["sourceSha256"] != PDF_SHA:
        errors.append("原件或资产原件指纹不一致")
    from pypdf import PdfReader
    reader = PdfReader(pdf)
    pages = {p: reader.pages[p - 1].extract_text() for p in [76, 77]}
    names = {1:"一",3:"三",7:"七",13:"十三",14:"十四",15:"十五",16:"十六",17:"十七",19:"十九",20:"二十"}
    for article, chunk in chunks.items():
        body = chunk["content"]
        physical = 76 if article in {1, 3, 7} else 77
        expected_span = {"unit":"UTF16_CODE_UNIT", "startInclusive":0,
                         "endExclusive":len(body.encode("utf-16-le"))//2}
        if not body.startswith(f"第{names[article]}条　") or body not in pages[physical]:
            errors.append(f"第{article}条非指定物理页的原文")
        if chunk["contentChecksum"] != hashlib.sha256(body.encode("utf-8")).hexdigest() or chunk["span"] != expected_span:
            errors.append(f"第{article}条正文hash或UTF16跨度异常")
        if chunk["physicalPage"] != physical or chunk["printedPage"] != physical - 12 or pages[physical].splitlines()[0] != str(physical - 12):
            errors.append(f"第{article}条页码不一致")
        if chunk["sourceLocator"] != f"PDF物理第{physical}页／印刷第{physical-12}页，第{article}条" or chunk["locatorId"] != f"handbook-2025-article-{article}":
            errors.append(f"第{article}条定位身份不一致")
        if chunk["publicationStatus"] != "DRAFT" or chunk["visibilityScope"] != "INTERNAL_ONLY" or chunk["sharingRightsConfirmed"] or any(chunk[k] is not None for k in ["sourceId", "chunkId", "profileId"]):
            errors.append(f"第{article}条离线材料冒充已发布绑定")
    for case in report["cases"]:
        expected = {"E01","E02","E03","E04","E05","E06","E08","E09","E10"}
        if case["expectedStatus"] == "PARTIALLY_ANSWERED":
            expected.add("E07")
        if set(case["applicableRubricIds"]) != expected:
            errors.append(f"{case['caseId']}:评分适用性异常")
        if case.get("additionalRubricTriggers") != {"E07":"EXPECTED_OR_ACTUAL_PARTIALLY_ANSWERED"}:
            errors.append(f"{case['caseId']}:缺少实际部分回答触发检查")
        for e in case["requiredEvidence"]:
            chunk = chunks[e["article"]]
            if any(e[k] != chunk[k] for k in ["locatorId","physicalPage","printedPage","contentChecksum","span"]) or e["sourceFileSha256"] != PDF_SHA:
                errors.append(f"{case['caseId']}:参考证据与原文包不匹配")
    if [r["id"] for r in rubric["dimensions"]] != [f"E{i:02d}" for i in range(1, 13)] or set(rubric["judgments"]) != JUDGMENTS:
        errors.append("评分维度或四种判断不完整")
    clusters = len({c["clauseGroup"] for c in report["cases"]})
    if clusters != report["articleClusterCount"] or report["independentSampleCountClaimed"]:
        errors.append("条款簇计数或独立样本声明错误")
    request = json.loads((evidence_dir / "review-request.json").read_text(encoding="utf-8"))
    for entry in request["protectedInputs"]:
        if digest(root / entry["path"]) != entry["sha256"]:
            errors.append(f"既有批准或历史候选被改写:{entry['path']}")
    for entry in request["presentedFiles"]:
        presented_path = evidence_dir / ("presented-" + Path(entry["path"]).name)
        if digest(presented_path) != entry["sha256"]:
            errors.append(f"实际呈审快照指纹变化:{entry['path']}")
    presented_docs = {}
    for kind, path in paths.items():
        presented = evidence_dir / ("presented-" + path.name)
        # 待审时完全一致；批准后另行核对语义，不靠批准布尔值跳过。
        current = json.loads(path.read_text(encoding="utf-8"))
        before = json.loads(presented.read_text(encoding="utf-8"))
        presented_docs[kind] = before
        if not current.get("locked") and not current.get("humanQualificationConfirmed") and current != before:
            errors.append(f"待审材料漂移:{path.name}")
    freeze_path = evidence_dir / "freeze-manifest.json"
    freeze_gate = False
    if freeze_path.exists():
        freeze = json.loads(freeze_path.read_text(encoding="utf-8"))
        receipt_path = evidence_dir / "human-confirmation.json"
        receipt = json.loads(receipt_path.read_text(encoding="utf-8")) if receipt_path.exists() else {}
        freeze_gate = (freeze.get("status") == "FROZEN_WITH_ACTUAL_USER_CONFIRMATION"
            and receipt.get("authority") == "DIRECT_USER_MESSAGE"
            and set(receipt.get("confirmedDecisions", [])) == {"C01-D01", "C01-D02", "C02-D01"}
            and receipt.get("confirmationText") and report["locked"] is True and report["humanReferenceApproved"] is True
            and report["familyAudit"]["semanticAuditHumanConfirmed"] is True
            and all(c.get("annotationStatus") == "HUMAN_CONFIRMED" for c in report["cases"])
            and source["humanQualificationConfirmed"] is True and rubric["locked"] is True and rubric["humanRubricApproved"] is True)
        expected_files = {p.relative_to(root).as_posix():digest(p) for p in paths.values()}
        review_path = product / "rule-check-2025-c01-c02-review.md"
        expected_files[review_path.relative_to(root).as_posix()] = digest(review_path)
        errors += validate_frozen_content(data, presented_docs, freeze.get("files", []), expected_files)
        if (receipt.get("presentedFiles") != request["presentedFiles"]
                or not receipt_path.exists()
                or freeze.get("humanConfirmationSha256") != digest(receipt_path)
                or freeze.get("reviewRequestSha256") != digest(evidence_dir / "review-request.json")):
            errors.append("真实决定未绑定本批实际呈审文件和请求指纹")
        if digest(review_path) != digest(evidence_dir / ("presented-" + review_path.name)):
            errors.append("人工核对说明在呈审后被覆盖")
        if not freeze_gate:
            errors.append("冻结声明缺少本批真实决定或对应状态")
    elif report["locked"] or rubric["locked"] or source["humanQualificationConfirmed"]:
        errors.append("未经实际决定清单宣称已冻结/获准")
    return {"auditedAt":datetime.now(timezone.utc).isoformat(), "validationPassed":not errors,
        "freezeGatePassed":bool(freeze_gate) and not errors,"errors":errors,
        "reportCount":len(report["cases"]),"articleClusterCount":clusters,
        "newSourceClauses":len(chunks),"existingDevelopmentReferenceCount":len(dev["records"]),
        "protectedInputCount":len(request["protectedInputs"]),"productEvaluationRun":False,
        "databaseCalled":False,"providerCalled":False,"userStudyRun":False,
        "semanticIsolationAutomaticallyProven":False,
        "files":[{"path":p.relative_to(root).as_posix(),"sha256":digest(p)} for p in paths.values()]}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=Path(__file__).resolve().parents[2])
    parser.add_argument("--pdf", type=Path, default=Path("D:/alldesk/Projects/SuperAgent-zhkuagent-mvp/data/raw/2025版学生手册2025.9.2.pdf"))
    parser.add_argument("--out", type=Path, required=True)
    parser.add_argument("--require-frozen", action="store_true")
    args = parser.parse_args()
    if args.out.exists():
        raise ValueError("结果已存在，请使用新的输出路径，避免覆盖历史失败")
    result = audit(args.root.resolve(), args.pdf)
    args.out.parent.mkdir(parents=True, exist_ok=True)
    args.out.write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({k:result[k] for k in ["validationPassed","freezeGatePassed","reportCount","articleClusterCount","errors"]}, ensure_ascii=False))
    raise SystemExit(0 if result["validationPassed"] and (not args.require_frozen or result["freezeGatePassed"]) else 2)


if __name__ == "__main__":
    main()
