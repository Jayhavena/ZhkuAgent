"""离线核验政策字节、跨度和题集契约；输出聚合信息，避免打印保留正文。"""
import copy
import hashlib
import json
import subprocess
from collections import Counter
from pathlib import Path

BASE = "828c55530013dc417b03ab178f83d8867538c928"
HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[4]
SPLITS = {"development": 6, "regression": 6, "holdout": 8}
TAGS = {"complete_conditions_direct_answer", "incomplete_conditions_clarify", "cross_turn_supplement", "policy_version_change", "cohort_applicability", "policy_not_applicable", "possible_policy_conflict", "insufficient_retrieval", "no_authoritative_basis", "invalidated_citation", "unrelated_question", "tool_execution_failure"}
REQUIRED = {"caseId", "group", "category", "synthetic", "provenance", "input", "reference", "expected", "policyScope", "rubricRevision"}


def digest(data):
    return hashlib.sha256(data).hexdigest()


def git_blob(path):
    return subprocess.check_output(["git", "show", BASE + ":" + path], cwd=ROOT)


def pointer(value, path):
    for token in path.strip("/").split("/"):
        value = value[int(token)] if isinstance(value, list) else value[token]
    return value


def utf16_slice(text, start, end):
    return text.encode("utf-16-le")[start * 2:end * 2].decode("utf-16-le")


def validate(datasets, ledger):
    errors = []
    def require(ok, code):
        if not ok:
            errors.append(code)
    require(ledger.get("recordType") == "T08_S02_POLICY_LEDGER" and ledger.get("schemaVersion") == 1, "ledger_header")
    require(ledger.get("sourceCommit") == BASE, "source_identity")
    evidence = {e["evidenceId"]: e for e in ledger["evidence"]}
    require(len(evidence) == len(ledger["evidence"]) == 12, "evidence_count")
    for item in ledger["evidence"]:
        # 只向冻结Git对象核对，避免把其它工作树的未提交变更当成政策事实。
        raw = git_blob(item["sourcePath"])
        require(digest(raw) == item["sourceSha256"], "source_digest")
        value = pointer(json.loads(raw), item["sourceJsonPointer"])
        require(value == item["quote"], "source_quote")
        parent = evidence.get(item.get("parentEvidenceId"))
        content = parent["quote"] if parent else value
        require(digest(content.encode("utf-8")) == item["contentChecksum"], "content_digest")
        require(0 <= item["startInclusive"] < item["endExclusive"] <= len(content.encode("utf-16-le")) // 2, "span_bounds")
        require(utf16_slice(content, item["startInclusive"], item["endExclusive"]) == item["quote"], "utf16_span")
        require(digest(git_blob(item["semanticReviewPath"])) == item["semanticReviewSha256"], "review_digest")
        require(item["currentApplicability"] == "UNKNOWN", "applicability_boundary")
    ids = set()
    risk = Counter()
    by_group = Counter()
    by_category = Counter()
    cross_turn = 0
    for split, count in SPLITS.items():
        data = datasets[split]
        require(data.get("recordType") == "T08_S02_DATASET" and data.get("schemaVersion") == 1, "dataset_header")
        require(data.get("datasetId") == "t08-s02-20261020" and data.get("revision") == "1" and data.get("split") == split, "dataset_identity")
        require(len(data["cases"]) == count, "split_count")
        for item in data["cases"]:
            require(REQUIRED <= set(item), "required_case_fields")
            require(item["caseId"] not in ids, "unique_case_id")
            ids.add(item["caseId"])
            require(item["category"] in "ABCDEFGH" and len(item["category"]) == 1, "category")
            require(isinstance(item["synthetic"], bool), "synthetic_boolean")
            expected = item["expected"]
            rounds = 1 + len(item["input"]["supplements"])
            cross_turn += rounds > 1
            require(len(expected["taskStatus"]) == len(expected["coverage"]) == rounds, "round_cardinality")
            require(all(s in {"WAITING_USER", "RESOLVED", "STOPPED", "FAILED"} for s in expected["taskStatus"]), "task_status")
            require(all(c in {"NONE", "PARTIAL", "COMPLETE"} for c in expected["coverage"]), "coverage")
            require(all(x in {"SEARCH", "READ"} for x in expected["requiredToolTypes"]), "tool_types")
            refs = item["reference"]
            for key in ["answerPoints", "forbiddenConclusions", "necessaryConditions", "relevantEvidence", "allowedGaps"]:
                require(isinstance(refs[key], list) and all(isinstance(x, str) for x in refs[key]), "reference_arrays")
            require(set(refs["relevantEvidence"]) <= set(evidence), "known_evidence")
            require(not expected["evidenceRequired"] or bool(refs["relevantEvidence"]), "required_evidence_present")
            require(refs["reviewStatus"] in {"HISTORICAL_SOURCE_CHECKED", "SAFETY_EXPECTATION_ONLY", "PENDING_OFFICIAL_SOURCE"}, "review_status")
            require(refs["reviewStatus"] != "PENDING_OFFICIAL_SOURCE" or refs["verifiedBeforeRun"] is False, "pending_not_verified")
            require(item["policyScope"]["revision"] == "2025.9.2" and item["policyScope"]["currentApplicability"] == "UNKNOWN", "scope")
            require(item["rubricRevision"] == "t08-s02-rubric-v1", "rubric")
            require(item["provenance"]["sourceCommit"] == BASE, "case_source")
            require(item["provenance"]["exposure"] == split.upper(), "split_exposure")
            if split == "holdout":
                require(rounds <= 2, "holdout_round_budget")
                require(item["group"] == "G3" and item["synthetic"] is False and item["provenance"]["kind"] == "DESIGNED_STUDENT_QUESTION", "holdout_isolation")
            elif item["synthetic"]:
                require(item["group"] == "G4" and item["provenance"]["kind"] == "SYNTHETIC_FAULT" and "faultInjection" in item, "fault_group")
            else:
                require(item["group"] == "G2" and item["provenance"]["kind"] == "EXPOSED_DEVELOPMENT", "exposed_group")
            risk.update(item.get("riskTags", []))
            by_group[item["group"]] += 1
            by_category[item["category"]] += 1
    require(TAGS <= set(risk), "risk_tag_coverage")
    return errors, {"caseCount": len(ids), "evidenceCount": len(evidence), "splitCounts": SPLITS, "groups": dict(sorted(by_group.items())), "categories": dict(sorted(by_category.items())), "riskTags": dict(sorted(risk.items())), "crossTurnCases": cross_turn, "holdoutCategories": dict(sorted(Counter(c["category"] for c in datasets["holdout"]["cases"]).items())), "holdoutMaxRuns": max(1 + len(c["input"]["supplements"]) for c in datasets["holdout"]["cases"])}


def main():
    datasets = {s: json.loads((HERE / (s + ".json")).read_text(encoding="utf-8")) for s in SPLITS}
    ledger = json.loads((HERE.parent / "policy-ledger.json").read_text(encoding="utf-8"))
    errors, aggregate = validate(datasets, ledger)
    checks = {}
    mutations = {
        "source_digest": lambda ds, le: le["evidence"][0].update(sourceSha256="0" * 64),
        "utf16_span": lambda ds, le: le["evidence"][-1].update(startInclusive=0),
        "known_evidence": lambda ds, le: ds["development"]["cases"][0]["reference"].update(relevantEvidence=["UNKNOWN"]),
        "round_cardinality": lambda ds, le: ds["development"]["cases"][0]["expected"].update(coverage=[]),
        "holdout_isolation": lambda ds, le: ds["holdout"]["cases"][0]["provenance"].update(kind="EXPOSED_DEVELOPMENT"),
        "risk_tag_coverage": lambda ds, le: [c.update(riskTags=[]) for d in ds.values() for c in d["cases"]],
        "pending_not_verified": lambda ds, le: ds["development"]["cases"][0]["reference"].update(reviewStatus="PENDING_OFFICIAL_SOURCE"),
    }
    for code, mutate in mutations.items():
        ds, le = copy.deepcopy(datasets), copy.deepcopy(ledger)
        mutate(ds, le)
        mutated_errors, _ = validate(ds, le)
        checks[code] = code in mutated_errors
    result = {"recordType": "T08_S02_CASE_SELF_CHECK", "schemaVersion": 1, "sourceCommit": BASE, "passed": not errors and all(checks.values()), "errors": sorted(set(errors)), "negativeChecks": checks, "aggregate": aggregate, "artifactSha256": {name: digest((HERE / name).read_bytes()) for name in ["development.json", "regression.json", "holdout.json", "validate_cases.py", "README.md"]}, "ledgerSha256": digest((HERE.parent / "policy-ledger.json").read_bytes()), "externalCalls": 0, "databaseCalls": 0, "providerCalls": 0, "qualityEvaluationPerformed": False, "humanCaseSignoff": False, "strictBlindTest": False}
    (HERE / "validation-summary.json").write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n", encoding="utf-8", newline="\n")
    print(json.dumps(result, ensure_ascii=False, indent=2))
    raise SystemExit(0 if result["passed"] else 1)


if __name__ == "__main__":
    main()
