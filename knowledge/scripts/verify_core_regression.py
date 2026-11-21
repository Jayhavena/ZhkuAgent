"""M4 最小回归清单及执行门禁；只输出计数，不复制测试正文或系统属性。"""
import argparse
import json
import re
import xml.etree.ElementTree as ET
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[2]


def load_contract(root=REPO_ROOT, manifest=None):
    data = json.loads((manifest or root / "knowledge/scripts/core-regression.json").read_text(encoding="utf-8-sig"))
    classes = data.get("testClasses", [])
    if (data.get("schemaVersion") != 1 or not classes or len(classes) != len(set(classes))
            or any(not isinstance(c, str) or not re.fullmatch(r"[A-Za-z][A-Za-z0-9]*Test", c) for c in classes)):
        raise ValueError("MANIFEST_INVALID")
    required = json.loads((root / data["matrixPath"]).read_text(encoding="utf-8-sig"))["cases"]
    if not required or any(c["testClass"] not in classes for c in required):
        raise ValueError("MANIFEST_OMITS_REQUIRED_CLASS")
    return {"testClasses": classes, "requiredCases": required}


def selector(contract):
    return ",".join(contract["testClasses"])


def verify_reports(contract, reports):
    files = list(reports.glob("TEST-*.xml"))
    totals, executed, rows = 0, set(), []
    for name in contract["testClasses"]:
        matches = [p for p in files if p.stem.removeprefix("TEST-").split(".")[-1] == name]
        if len(matches) != 1:
            raise ValueError("REPORT_MISSING_OR_DUPLICATE")
        try:
            suite = ET.parse(matches[0]).getroot()
            count = int(suite.attrib["tests"])
            bad = sum(int(suite.attrib.get(k, "0")) for k in ("failures", "errors", "skipped"))
        except (ET.ParseError, KeyError, ValueError, OSError):
            raise ValueError("REPORT_INVALID") from None
        cases = suite.findall("testcase")
        # 不能只相信套件总数；漏跑/跳过和单条失败都必须阻断，避免白名单假绿。
        if suite.tag != "testsuite" or count <= 0 or bad != 0 or any(
                c.find(x) is not None for c in cases for x in ("failure", "error", "skipped")):
            raise ValueError("REPORT_NOT_GREEN")
        if count != len(cases):
            raise ValueError("REPORT_COUNT_MISMATCH")
        if suite.attrib.get("name", "").split(".")[-1] != name:
            raise ValueError("REPORT_INVALID")
        for case in cases:
            # Surefire 参数化方法会附带参数签名或序号，保留方法名作固定要求匹配。
            executed.add((name, re.split(r"[\[(]", case.attrib.get("name", ""))[0]))
        totals += count
        rows.append({"class": name, "tests": count})
    for requirement in contract["requiredCases"]:
        if (requirement["testClass"], requirement["testMethod"]) not in executed:
            raise ValueError("CRITERION_NOT_EXECUTED")
    return {"passed": True, "tests": totals, "criteria": len(contract["requiredCases"]),
            "classes": rows, "scope": "OFFLINE_OR_LOOPBACK_NOT_MODEL_QUALITY"}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    operation = parser.add_mutually_exclusive_group(required=True)
    operation.add_argument("--selector", action="store_true")
    operation.add_argument("--reports", type=Path)
    parser.add_argument("--summary", type=Path)
    args = parser.parse_args()
    try:
        contract = load_contract()
        if args.selector:
            print(selector(contract))
            return 0
        summary = verify_reports(contract, args.reports)
    except (ValueError, KeyError, TypeError, OSError) as error:
        # 只披露固定门禁原因；坏 JSON/XML 或系统异常的正文可能含测试原始内容。
        reason = str(error) if isinstance(error, ValueError) and str(error).startswith(("REPORT_", "MANIFEST_", "CRITERION_")) else "CONTRACT_INVALID"
        summary = {"passed": False, "reason": reason}
    if args.summary:
        args.summary.parent.mkdir(parents=True, exist_ok=True)
        args.summary.write_text(json.dumps(summary, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(summary))
    return 0 if summary["passed"] else 1


if __name__ == "__main__":
    raise SystemExit(main())
