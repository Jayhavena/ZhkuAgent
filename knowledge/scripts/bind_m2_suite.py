#!/usr/bin/env python3
"""M2 两轮评测套件绑定器（离线，只用 Python 标准库）。

绑定器只做两件事：

1. 把预冻结模板中的两个 ``case.runId`` 和 ``block.evidenceDir`` 替换成新证据目录的真实值，
   其余期望原样保留；
2. 在现有 Grader 之外，对 ``task-results.json`` 做结构守卫，把结果写入 ``binding.json``。

它不访问数据库、模型、浏览器或密钥，也不读写历史 suite / 报告。所有失败只登记固定枚举，
不把原始 JSON、异常正文或用户可见文案写进报告，避免私有交换内容随失败一起扩散。

注意：结构守卫是 Grader 之外的补充，不是 Grader 自身的校验。它不校验 checksum、span、
factId 或答案语义；这些仍由现有 Collector/Grader 处理，或根本未覆盖。
"""

from __future__ import annotations

import argparse
import hashlib
import json
import re
import sys
from pathlib import Path
from typing import Any

SCHEMA_VERSION = 1
SUITE_ID = "M2"
BLOCK_KIND = "EVIDENCE_BUNDLE"
TEMPLATE_EVIDENCE_PLACEHOLDER = "__REPLACE_WITH_EVIDENCE_DIR__"
CASE_CLARIFY = "M2-CLARIFY"
CASE_RESOLVE = "M2-RESOLVE"

# 预冻结契约：模板只能由本绑定器填 runId/evidenceDir，期望值必须与这里完全一致。
FIRST_EXPECTATION = {
    "taskStatus": "WAITING_USER",
    "runState": "INCOMPLETE",
    "coverage": "NONE",
    "requiredActions": [
        "SEARCH_REVIEWED_RULES",
        "READ_VERIFIED_RULE",
        "ASK_NECESSARY_CONDITION",
        "PUBLICATION_REVIEW",
    ],
    "issueReason": "MISSING_CONDITION",
    "expectClaims": False,
    "observedAction": "SEARCH_REVIEWED_RULES",
    "observationStatus": "SUCCEEDED",
    "requireEvidenceLinks": False,
    "expectRejectedUnknownTool": False,
}

SECOND_EXPECTATION = {
    "taskStatus": "RESOLVED",
    "runState": "SUCCEEDED",
    "coverage": "COMPLETE",
    "requiredActions": [
        "SEARCH_REVIEWED_RULES",
        "READ_VERIFIED_RULE",
        "FINISH_CHECK",
        "PUBLICATION_REVIEW",
    ],
    "issueReason": None,
    "expectClaims": True,
    "observedAction": "READ_VERIFIED_RULE",
    "observationStatus": "SUCCEEDED",
    "requireEvidenceLinks": True,
    "expectRejectedUnknownTool": False,
}

EXPECTED_DETAILS = ["EXPECTATIONS_MET"]
FIRST_STATUS = ("WAITING_USER", "INCOMPLETE", "NONE")
SECOND_STATUS = ("RESOLVED", "SUCCEEDED", "COMPLETE")

RUN_ID_PATTERN = re.compile(
    r"^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$"
)

REASON_NOTES = {
    "TEMPLATE_MISSING": "模板文件不存在",
    "TEMPLATE_UNREADABLE": "模板不是可读取的 UTF-8 JSON",
    "TEMPLATE_SHAPE_INVALID": "模板的块/用例结构不符合预冻结契约",
    "TEMPLATE_RUNID_NOT_NULL": "模板 case.runId 必须为 null，只能由绑定器写入",
    "TEMPLATE_EXPECTATION_DRIFT": "模板期望或 bundleCase 与预冻结契约不一致",
    "EVIDENCE_DIR_MISSING": "证据目录不存在或不是目录",
    "EVIDENCE_DIR_OUTSIDE_ROOT": "证据目录不在仓库根内，无法写入可移植的相对路径",
    "TASK_RESULTS_MISSING": "证据目录缺少 task-results.json",
    "TASK_RESULTS_UNREADABLE": "task-results.json 不是合法的 UTF-8 JSON",
    "TASK_RESULTS_NOT_ARRAY": "task-results.json 顶层必须是 run 数组",
    "RUN_COUNT_MISMATCH": "task-results.json 必须恰好包含同任务的两个 run",
    "RUN_ENTRY_INVALID": "run 缺少合法 runId/taskId 或状态字段",
    "RUN_ID_DUPLICATE": "两个 run 的 runId 重复，不能当作两轮",
    "TASK_ID_MISMATCH": "两个 run 不属于同一个 task",
    "FIRST_RUN_STATE_MISMATCH": "首轮不是 WAITING_USER/INCOMPLETE/NONE",
    "SECOND_RUN_STATE_MISMATCH": "续答不是 RESOLVED/SUCCEEDED/COMPLETE",
    "SECOND_RUN_RESULT_MISSING": "续答缺少 result",
    "SECOND_RUN_CLAIM_MISSING": "续答没有发布任何 claim",
    "SECOND_RUN_EVIDENCE_LINK_MISSING": "续答没有 claim 指向已列证据的 CLAIM 关系",
    "SUITE_TEMPLATE_DRIFT": "绑定后套件除 runId/evidenceDir 外发生变化",
    "OUTPUT_EXISTS": "输出文件已存在，拒绝覆盖",
    "OUTPUT_WRITE_FAILED": "输出文件无法写入",
}

# 这些原因表示“输入不足以判定”，而不是“证据与契约冲突”。任一 FAIL 原因优先于它们。
NOT_EVALUABLE_REASONS = frozenset({
    "TEMPLATE_MISSING",
    "TEMPLATE_UNREADABLE",
    "TEMPLATE_SHAPE_INVALID",
    "EVIDENCE_DIR_MISSING",
    "EVIDENCE_DIR_OUTSIDE_ROOT",
    "TASK_RESULTS_MISSING",
    "TASK_RESULTS_UNREADABLE",
    "TASK_RESULTS_NOT_ARRAY",
    "RUN_COUNT_MISMATCH",
    "RUN_ENTRY_INVALID",
    "OUTPUT_WRITE_FAILED",
})

EXIT_PASS = 0
EXIT_FAIL = 2
EXIT_NOT_EVALUABLE = 3
EXIT_OUTPUT_EXISTS = 4


def _sha256(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def _canonical_bytes(value: Any) -> bytes:
    return json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":")).encode("utf-8")


def manifest_sha256(manifest: dict[str, Any]) -> str:
    """binding.json 自校验：对去掉 bindingSha256 后的规范 JSON 求 SHA-256。"""
    payload = {key: value for key, value in manifest.items() if key != "bindingSha256"}
    return _sha256(_canonical_bytes(payload))


def _add(reason_list: list[str], code: str) -> None:
    if code not in reason_list:
        reason_list.append(code)


def _repo_relative(root: Path, path: Path) -> str | None:
    try:
        return path.relative_to(root).as_posix()
    except ValueError:
        return None


def _read_json(path: Path) -> tuple[Any | None, str | None, str | None]:
    """返回 (数据, 字节 SHA, 失败原因)。失败原因不含解析器正文，避免泄漏输入片段。"""
    try:
        raw = path.read_bytes()
    except OSError:
        return None, None, "MISSING"
    digest = _sha256(raw)
    try:
        text = raw.decode("utf-8")
    except UnicodeDecodeError:
        return None, digest, "UNREADABLE"
    try:
        return json.loads(text), digest, None
    except (json.JSONDecodeError, RecursionError):
        return None, digest, "UNREADABLE"


def validate_template(template: Any) -> list[str]:
    """模板守卫：只接受预冻结结构；任何期望漂移都在绑定前拦下。"""
    reasons: list[str] = []
    if not isinstance(template, dict):
        _add(reasons, "TEMPLATE_SHAPE_INVALID")
        return reasons
    if template.get("schemaVersion") != SCHEMA_VERSION:
        _add(reasons, "TEMPLATE_SHAPE_INVALID")
    if template.get("suiteId") != SUITE_ID:
        _add(reasons, "TEMPLATE_SHAPE_INVALID")
    blocks = template.get("blocks")
    if not isinstance(blocks, list) or len(blocks) != 1:
        _add(reasons, "TEMPLATE_SHAPE_INVALID")
        return reasons
    block = blocks[0]
    if not isinstance(block, dict):
        _add(reasons, "TEMPLATE_SHAPE_INVALID")
        return reasons
    if block.get("kind") != BLOCK_KIND:
        _add(reasons, "TEMPLATE_SHAPE_INVALID")
    if not isinstance(block.get("blockId"), str) or not block.get("blockId", "").strip():
        _add(reasons, "TEMPLATE_SHAPE_INVALID")
    if block.get("evidenceDir") != TEMPLATE_EVIDENCE_PLACEHOLDER:
        _add(reasons, "TEMPLATE_SHAPE_INVALID")
    bundle_case = block.get("bundleCase")
    if not isinstance(bundle_case, dict):
        _add(reasons, "TEMPLATE_SHAPE_INVALID")
    else:
        details = bundle_case.get("expectedDetails")
        if bundle_case.get("expectedVerdict") != "NOT_EVALUABLE" or \
                not isinstance(details, list) or "COLLECTION_INCOMPLETE" not in details:
            _add(reasons, "TEMPLATE_EXPECTATION_DRIFT")
    cases = block.get("cases")
    if not isinstance(cases, list) or len(cases) != 2:
        _add(reasons, "TEMPLATE_SHAPE_INVALID")
        return reasons
    ids = [case.get("caseId") if isinstance(case, dict) else None for case in cases]
    if ids != [CASE_CLARIFY, CASE_RESOLVE]:
        _add(reasons, "TEMPLATE_SHAPE_INVALID")
        return reasons
    for case, expected in zip(cases, (FIRST_EXPECTATION, SECOND_EXPECTATION)):
        if not isinstance(case, dict):
            _add(reasons, "TEMPLATE_SHAPE_INVALID")
            continue
        if "runId" not in case or case.get("runId") is not None:
            _add(reasons, "TEMPLATE_RUNID_NOT_NULL")
        if case.get("expectedVerdict") != "PASS" or case.get("expectedDetails") != EXPECTED_DETAILS:
            _add(reasons, "TEMPLATE_EXPECTATION_DRIFT")
        if case.get("expectation") != expected:
            _add(reasons, "TEMPLATE_EXPECTATION_DRIFT")
    return reasons


def _entry_identity(entry: Any) -> dict[str, str] | None:
    if not isinstance(entry, dict):
        return None
    run_id = entry.get("runId")
    task_id = entry.get("taskId")
    if not isinstance(run_id, str) or not RUN_ID_PATTERN.match(run_id):
        return None
    if not isinstance(task_id, str) or not RUN_ID_PATTERN.fullmatch(task_id):
        return None
    return {"runId": run_id, "taskId": task_id.strip().lower()}


def _entry_status(entry: Any) -> tuple[str, str, str] | None:
    if not isinstance(entry, dict):
        return None
    result = entry.get("result")
    coverage = result.get("coverage") if isinstance(result, dict) else None
    task_status = entry.get("taskStatus")
    run_state = entry.get("runState")
    if not all(isinstance(value, str) for value in (task_status, run_state, coverage)):
        return None
    return (task_status, run_state, coverage)


def _second_run_claims(second: Any) -> tuple[int | None, int | None]:
    """返回 (claim 数, 有 CLAIM 关系且关系指向已列证据的 claim 数)。"""
    if not isinstance(second, dict):
        return None, None
    result = second.get("result")
    if not isinstance(result, dict):
        return None, None
    claims: list[dict[str, Any]] = []
    items = result.get("items")
    if isinstance(items, list):
        for item in items:
            if isinstance(item, dict) and isinstance(item.get("claims"), list):
                claims.extend(claim for claim in item["claims"] if isinstance(claim, dict))
    evidence = result.get("evidence")
    relations = result.get("evidenceRelations")
    # 损坏容器不能让绑定器崩溃并丢失失败回执；按无有效关系处理，交由结构守卫拒绝。
    evidence = evidence if isinstance(evidence, list) else []
    relations = relations if isinstance(relations, list) else []
    evidence_ids = {
        evidence["id"]
        for evidence in evidence
        if isinstance(evidence, dict) and isinstance(evidence.get("id"), str)
    }
    linked_targets = {
        relation["targetId"]
        for relation in relations
        if isinstance(relation, dict)
        and relation.get("role") == "CLAIM"
        and isinstance(relation.get("targetId"), str)
        and isinstance(relation.get("evidenceId"), str)
        and relation.get("evidenceId") in evidence_ids
    }
    linked_claims = [
        claim for claim in claims
        if isinstance(claim.get("id"), str) and claim["id"] in linked_targets
    ]
    return len(claims), len(linked_claims)


def _analyse_runs(entries: list[Any], reasons: list[str]) -> tuple[str | None, str | None, dict[str, Any]]:
    """结构守卫并给出可绑定的 runId。

    只有身份可证明时才写 runId：第二个 run 必须与第一个同 task 且 runId 不同；
    无法证明时留 null，让现有 Grader 输出缺迹/不可判定，而不是伪造一个能过的值。
    """
    checks: dict[str, Any] = {
        "taskResultCount": len(entries),
        "taskIdMatches": None,
        "runIdsDistinct": None,
        "firstRoundStatusMatches": None,
        "secondRoundStatusMatches": None,
        "secondRoundClaimCount": None,
        "secondRoundLinkedClaimCount": None,
    }
    first_run: str | None = None
    second_run: str | None = None
    identities = [_entry_identity(entry) for entry in entries]
    statuses = [_entry_status(entry) for entry in entries]
    for identity, status in zip(identities, statuses):
        if identity is None or status is None:
            _add(reasons, "RUN_ENTRY_INVALID")
    if len(entries) != 2:
        _add(reasons, "RUN_COUNT_MISMATCH")

    if len(entries) == 2:
        if identities[0] is not None and identities[1] is not None:
            same_task = identities[0]["taskId"] == identities[1]["taskId"]
            distinct = identities[0]["runId"].lower() != identities[1]["runId"].lower()
            checks["taskIdMatches"] = same_task
            checks["runIdsDistinct"] = distinct
            # 首轮无论后续是否同任务都绑定：它是本批真实第一个 run，Grader 会核对状态。
            first_run = identities[0]["runId"]
            if not same_task:
                _add(reasons, "TASK_ID_MISMATCH")
            elif not distinct:
                _add(reasons, "RUN_ID_DUPLICATE")
            else:
                second_run = identities[1]["runId"]
        elif identities[0] is not None:
            # 第二个 run 身份不可读时，只能确认首轮；续答留 null。
            first_run = identities[0]["runId"]
        if statuses[0] is not None:
            checks["firstRoundStatusMatches"] = statuses[0] == FIRST_STATUS
            if not checks["firstRoundStatusMatches"]:
                _add(reasons, "FIRST_RUN_STATE_MISMATCH")
        if statuses[1] is not None:
            checks["secondRoundStatusMatches"] = statuses[1] == SECOND_STATUS
            if not checks["secondRoundStatusMatches"]:
                _add(reasons, "SECOND_RUN_STATE_MISMATCH")
        second = entries[1]
        if not isinstance(second, dict):
            pass  # 结构无效已由 RUN_ENTRY_INVALID 登记，不再派生 claim/link 噪声。
        elif not isinstance(second.get("result"), dict):
            _add(reasons, "SECOND_RUN_RESULT_MISSING")
        else:
            claim_count, linked_count = _second_run_claims(second)
            checks["secondRoundClaimCount"] = claim_count
            checks["secondRoundLinkedClaimCount"] = linked_count
            if not claim_count:
                _add(reasons, "SECOND_RUN_CLAIM_MISSING")
            if not linked_count:
                _add(reasons, "SECOND_RUN_EVIDENCE_LINK_MISSING")
    elif len(entries) == 1 and identities[0] is not None and statuses[0] is not None:
        # 已发布数组是按运行顺序写入的前缀；唯一条目只能是首轮，不能看见成功状态就猜成续答。
        # 即便首轮失败也绑定真实runId，让Grader评分失败；不存在的第二轮仍留空。
        first_run = identities[0]["runId"]
        checks["firstRoundStatusMatches"] = statuses[0] == FIRST_STATUS
        if not checks["firstRoundStatusMatches"]:
            _add(reasons, "FIRST_RUN_STATE_MISMATCH")
    return first_run, second_run, checks


def _mask_binding_fields(document: Any) -> Any:
    masked = json.loads(json.dumps(document))
    block = masked["blocks"][0]
    block["evidenceDir"] = "<EVIDENCE_DIR>"
    for case in block["cases"]:
        case["runId"] = "<RUN_ID>"
    return masked


def _build_suite(template: Any, evidence_rel: str, first_run: str | None,
                 second_run: str | None, reasons: list[str]) -> dict[str, Any] | None:
    suite = json.loads(json.dumps(template))
    block = suite["blocks"][0]
    block["evidenceDir"] = evidence_rel
    block["cases"][0]["runId"] = first_run
    block["cases"][1]["runId"] = second_run
    # 防御性自检：除这两个绑定字段外，模板内容必须逐字语义不变。
    if _mask_binding_fields(template) != _mask_binding_fields(suite):
        _add(reasons, "SUITE_TEMPLATE_DRIFT")
        return None
    return suite


def _overall_status(reasons: list[str]) -> str:
    if not reasons:
        return "PASS"
    if all(reason in NOT_EVALUABLE_REASONS for reason in reasons):
        return "NOT_EVALUABLE"
    return "FAIL"


def _exit_code(status: str) -> int:
    return {"PASS": EXIT_PASS, "FAIL": EXIT_FAIL, "NOT_EVALUABLE": EXIT_NOT_EVALUABLE}[status]


def _print_status(status: str, reasons: list[str], suite_path: Path | None) -> None:
    suffix = f" suite={suite_path}" if suite_path is not None else " suite=NOT_WRITTEN"
    print(f"binding status={status} reasons={','.join(reasons) if reasons else 'NONE'}{suffix}")
    for reason in reasons:
        print(f"  reason={reason} note={REASON_NOTES.get(reason, '未登记原因')}")


def bind(template_path: Path, evidence_path: Path, out_dir: Path, repo_root: Path) -> int:
    root = repo_root.resolve()
    template_file = template_path.resolve()
    evidence_dir = evidence_path.resolve()
    out = out_dir.resolve()
    suite_file = out / "suite.json"
    binding_file = out / "binding.json"

    # CREATE_NEW 语义：任一输出已存在就整体拒绝，不写一半、也不靠覆盖“修正”历史记录。
    if suite_file.exists() or binding_file.exists():
        print(f"binding status=ABORT reason=OUTPUT_EXISTS note={REASON_NOTES['OUTPUT_EXISTS']}", file=sys.stderr)
        return EXIT_OUTPUT_EXISTS
    try:
        out.mkdir(parents=True, exist_ok=True)
    except OSError:
        print("binding status=ABORT reason=OUTPUT_WRITE_FAILED", file=sys.stderr)
        return EXIT_NOT_EVALUABLE

    reasons: list[str] = []
    template_sha: str | None = None
    task_results_sha: str | None = None
    evidence_rel = _repo_relative(root, evidence_dir)
    template_rel = _repo_relative(root, template_file)
    first_run: str | None = None
    second_run: str | None = None
    checks: dict[str, Any] = {
        "taskResultCount": None,
        "taskIdMatches": None,
        "runIdsDistinct": None,
        "firstRoundStatusMatches": None,
        "secondRoundStatusMatches": None,
        "secondRoundClaimCount": None,
        "secondRoundLinkedClaimCount": None,
    }
    suite: dict[str, Any] | None = None
    expectations_unchanged: bool | None = None

    template, digest, failure = _read_json(template_file)
    template_sha = digest
    if failure == "MISSING":
        _add(reasons, "TEMPLATE_MISSING")
    elif failure == "UNREADABLE":
        _add(reasons, "TEMPLATE_UNREADABLE")
    else:
        for reason in validate_template(template):
            _add(reasons, reason)
    # 模板被篡改时不再从它生成套件：否则会把错误期望原样带进报告。
    template_usable = not reasons

    if template_usable:
        if evidence_rel is None:
            _add(reasons, "EVIDENCE_DIR_OUTSIDE_ROOT")
        elif not evidence_dir.is_dir():
            _add(reasons, "EVIDENCE_DIR_MISSING")
        else:
            task_results_file = evidence_dir / "task-results.json"
            entries, task_results_sha, failure = _read_json(task_results_file)
            if failure == "MISSING":
                _add(reasons, "TASK_RESULTS_MISSING")
            elif failure == "UNREADABLE":
                _add(reasons, "TASK_RESULTS_UNREADABLE")
            elif not isinstance(entries, list):
                _add(reasons, "TASK_RESULTS_NOT_ARRAY")
            else:
                first_run, second_run, checks = _analyse_runs(entries, reasons)

    if template_usable and evidence_rel is not None:
        suite = _build_suite(template, evidence_rel, first_run, second_run, reasons)
        expectations_unchanged = suite is not None

    status = _overall_status(reasons)
    suite_bytes = (
        (json.dumps(suite, ensure_ascii=False, indent=2) + "\n").encode("utf-8")
        if suite is not None else None
    )
    manifest: dict[str, Any] = {
        "schemaVersion": SCHEMA_VERSION,
        "binder": "bind_m2_suite",
        "status": status,
        "reasons": reasons,
        "reasonNotes": {reason: REASON_NOTES.get(reason, "未登记原因") for reason in reasons},
        "suiteId": SUITE_ID,
        "templatePath": template_rel if template_rel is not None else template_file.as_posix(),
        "templateSha256": template_sha,
        "evidencePath": evidence_rel,
        "taskResultsSha256": task_results_sha,
        "suiteGenerated": suite is not None,
        "outputs": {
            "suite": "suite.json" if suite is not None else None,
            "binding": "binding.json",
        },
        "cases": [
            {"caseId": CASE_CLARIFY, "runId": first_run, "bound": first_run is not None},
            {"caseId": CASE_RESOLVE, "runId": second_run, "bound": second_run is not None},
        ],
        "checks": checks,
        "expectationsUnchanged": expectations_unchanged,
        "suiteSha256": _sha256(suite_bytes) if suite_bytes is not None else None,
        # 绑定器不验证 Grader 的 checksum/span/factId；这里只声明结构守卫的职责边界。
        "graderBoundary": "STRUCTURAL_GUARD_ONLY_NOT_CHECKSUM_SPAN_FACT_VERIFICATION",
    }
    manifest["bindingSha256"] = manifest_sha256(manifest)
    binding_bytes = (json.dumps(manifest, ensure_ascii=False, indent=2) + "\n").encode("utf-8")

    created: list[Path] = []
    try:
        if suite_bytes is not None:
            with suite_file.open("xb") as handle:
                created.append(suite_file)
                handle.write(suite_bytes)
        with binding_file.open("xb") as handle:
            created.append(binding_file)
            handle.write(binding_bytes)
    except OSError as failure:
        # 只清理由本次调用创建的文件；不碰已存在的历史记录。
        for path in created:
            try:
                path.unlink()
            except OSError:
                pass
        if isinstance(failure, FileExistsError):
            print("binding status=ABORT reason=OUTPUT_EXISTS", file=sys.stderr)
            return EXIT_OUTPUT_EXISTS
        print("binding status=ABORT reason=OUTPUT_WRITE_FAILED", file=sys.stderr)
        return EXIT_NOT_EVALUABLE

    _print_status(status, reasons, suite_file if suite_bytes is not None else None)
    print(f"binding={binding_file}")
    return _exit_code(status)


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="绑定 M2 两轮评测套件（离线结构守卫）")
    parser.add_argument("--template", required=True, help="预冻结套件模板")
    parser.add_argument("--evidence", required=True, help="新证据目录（含 task-results.json）")
    parser.add_argument("--out", required=True, help="输出目录；已存在 suite.json/binding.json 时拒绝")
    parser.add_argument("--repo-root", default=".", help="仓库根，默认当前目录")
    args = parser.parse_args(argv)
    return bind(Path(args.template), Path(args.evidence), Path(args.out), Path(args.repo_root))


if __name__ == "__main__":
    raise SystemExit(main())
