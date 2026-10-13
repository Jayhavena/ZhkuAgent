"""第五包冻结器合同：版本、状态链、完整性与凭证边界。"""

import hashlib
import json
import tempfile
import unittest
from pathlib import Path

import build_rule_check_package05_freeze as freeze


class FreezeContract(unittest.TestCase):
    def fixture(self):
        temporary = tempfile.TemporaryDirectory()
        root = Path(temporary.name)
        (root / "pom.xml").write_text(
            "<project xmlns='http://maven.apache.org/POM/4.0.0'><version>0.1.0-rc.1</version></project>",
            encoding="utf-8",
        )
        for module in ("agent", "agent-runtime", "knowledge"):
            path = root / module / "pom.xml"
            path.parent.mkdir(parents=True)
            path.write_text(
                "<project xmlns='http://maven.apache.org/POM/4.0.0'><parent><version>0.1.0-rc.1</version></parent><version>0.1.0-rc.1</version></project>",
                encoding="utf-8",
            )
        base = root / "docs/evidence/rule-check-refactor"
        packages = [
            (base / "2026-10-10/package-01", {"status": "DONE_FIRST_PACKAGE_ONLY", "remainingEngineeringPackages": 4, "defaultRuntimeSwitched": False, "G2Passed": False}),
            (base / "2026-10-10/package-02", {"status": "DONE_PACKAGE_02_SEALED_REPLAY_ONLY", "remainingPackages": 3, "version": "0.1.0-rc.1", "defaultCatalogue": "CLOSED", "G2Passed": False}),
            (base / "2026-10-10/package-03", {"status": "DONE_PACKAGE_03_REPORT_WITH_CONFIRMED_FAILURES", "remainingPackages": 2, "version": "0.1.0-rc.1", "defaultCatalogue": "CLOSED", "G2Passed": False, "humanCaseLevelVerdicts": {"PASS": 7, "FAIL": 8, "UNJUDGEABLE": 1}}),
            (base / "2026-10-11/package-04", {"status": "DONE_PACKAGE_04_BOUNDED_RELIABILITY", "remainingPackages": 1, "version": "0.1.0-rc.1", "defaultCatalogue": "CLOSED", "G2Passed": False, "providerSelection": {"trialDenominator": 5, "successCount": 5, "successVector": [True] * 5, "passPower5": True, "independentProviderTrials": True, "retryCount": 0}, "applicationReplay": {"trialDenominator": 5, "successCount": 5, "successVector": [True] * 5, "passPower5": True, "providerCallsInReplay": 0}}),
        ]
        for directory, closeout in packages:
            directory.mkdir(parents=True)
            artifact = directory / "artifact.txt"
            artifact.write_text(directory.name, encoding="utf-8")
            integrity = {
                "algorithm": "SHA256",
                "artifacts": [{"path": artifact.relative_to(root).as_posix(), "sha256": hashlib.sha256(artifact.read_bytes()).hexdigest(), "bytes": artifact.stat().st_size}],
            }
            (directory / "integrity.json").write_text(json.dumps(integrity), encoding="utf-8")
            if directory.name == "package-04":
                closeout["integritySha256"] = hashlib.sha256((directory / "integrity.json").read_bytes()).hexdigest()
            (directory / "closeout.json").write_text(json.dumps(closeout), encoding="utf-8")
        return temporary, root

    def test_valid_release_preserves_closed_product_gates(self):
        temporary, root = self.fixture()
        self.addCleanup(temporary.cleanup)
        result = freeze.validate_release(root)
        self.assertEqual(result["version"], "0.1.0-rc.1")
        self.assertEqual(result["remainingChain"], [4, 3, 2, 1])
        self.assertEqual(result["package03Verdicts"], {"PASS": 7, "FAIL": 8, "UNJUDGEABLE": 1})
        self.assertFalse(result["G2Passed"])
        self.assertEqual(result["defaultCatalogue"], "CLOSED")

    def test_version_counter_and_gate_drift_prevent_freeze(self):
        for mutation in ("version", "module_version", "counter", "g2", "catalogue"):
            temporary, root = self.fixture()
            try:
                p4 = root / "docs/evidence/rule-check-refactor/2026-10-11/package-04/closeout.json"
                data = json.loads(p4.read_text(encoding="utf-8"))
                if mutation == "module_version":
                    module = root / "knowledge/pom.xml"
                    module.write_text(module.read_text(encoding="utf-8").replace("0.1.0-rc.1", "0.2.0"), encoding="utf-8")
                elif mutation == "version":
                    data["version"] = "0.2.0"
                elif mutation == "counter":
                    data["remainingPackages"] = 0
                elif mutation == "g2":
                    data["G2Passed"] = True
                else:
                    data["defaultCatalogue"] = "OPEN"
                p4.write_text(json.dumps(data), encoding="utf-8")
                with self.assertRaises(ValueError, msg=mutation):
                    freeze.validate_release(root)
            finally:
                temporary.cleanup()

    def test_historical_hash_drift_prevents_freeze(self):
        temporary, root = self.fixture()
        self.addCleanup(temporary.cleanup)
        artifact = root / "docs/evidence/rule-check-refactor/2026-10-10/package-03/artifact.txt"
        artifact.write_text("changed", encoding="utf-8")
        with self.assertRaises(ValueError):
            freeze.validate_release(root)

    def test_collection_excludes_build_cache_credentials_and_package05_outputs(self):
        temporary, root = self.fixture()
        self.addCleanup(temporary.cleanup)
        allowed = root / "knowledge/src/main/java/App.java"
        allowed.parent.mkdir(parents=True)
        allowed.write_text("class App {}", encoding="utf-8")
        frontend = root / "agent/frontend/package.json"
        frontend.parent.mkdir(parents=True)
        frontend.write_text('{"scripts":{"test":"vitest"}}', encoding="utf-8")
        for relative in ("knowledge/target/x.class", "knowledge/scripts/__pycache__/x.pyc", "infra/.env", "docs/evidence/rule-check-refactor/2026-10-11/package-05/closeout.json"):
            path = root / relative
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text("forbidden", encoding="utf-8")
        paths = freeze.collect_freeze_paths(root)
        names = {path.relative_to(root).as_posix() for path in paths}
        self.assertIn("knowledge/src/main/java/App.java", names)
        self.assertIn("agent/frontend/package.json", names)
        self.assertNotIn("knowledge/target/x.class", names)
        self.assertNotIn("knowledge/scripts/__pycache__/x.pyc", names)
        self.assertNotIn("infra/.env", names)
        self.assertNotIn("docs/evidence/rule-check-refactor/2026-10-11/package-05/closeout.json", names)

    def test_sensitive_names_and_private_keys_are_rejected(self):
        temporary, root = self.fixture()
        self.addCleanup(temporary.cleanup)
        secret_file = root / "knowledge/scripts/.env.local"
        secret_file.parent.mkdir(parents=True, exist_ok=True)
        secret_file.write_text("PASSWORD=hidden", encoding="utf-8")
        self.assertNotIn(secret_file, freeze.collect_freeze_paths(root))
        key = root / "knowledge/src/main/resources/private.txt"
        key.parent.mkdir(parents=True, exist_ok=True)
        key.write_text("-----BEGIN PRIVATE KEY-----\nabc\n-----END PRIVATE KEY-----", encoding="utf-8")
        self.assertEqual(freeze.scan_credentials(root, [key])["findingCount"], 1)

    def test_secret_like_content_is_rejected_without_echoing_value(self):
        temporary, root = self.fixture()
        self.addCleanup(temporary.cleanup)
        path = root / "knowledge/src/main/resources/application.yml"
        path.parent.mkdir(parents=True)
        secret = "sk-" + "abcdefghijklmnopqrstuvwxyz123456"
        path.write_text(f"api_key: '{secret}'", encoding="utf-8")
        result = freeze.scan_credentials(root, [path])
        self.assertEqual(result["findingCount"], 1)
        self.assertEqual(result["paths"], ["knowledge/src/main/resources/application.yml"])
        self.assertNotIn("abcdefghijklmnopqrstuvwxyz", json.dumps(result))

    def test_verify_freeze_recomputes_manifest_and_closeout_identity(self):
        temporary, root = self.fixture()
        self.addCleanup(temporary.cleanup)
        package = root / "docs/evidence/rule-check-refactor/2026-10-11/package-05"
        package.mkdir(parents=True)
        artifact = root / "pom.xml"
        integrity = {
            "algorithm": "SHA256",
            "artifactCount": 1,
            "artifacts": [{"path": "pom.xml", "sha256": hashlib.sha256(artifact.read_bytes()).hexdigest(), "bytes": artifact.stat().st_size}],
        }
        integrity_path = package / "integrity.json"
        integrity_path.write_text(json.dumps(integrity), encoding="utf-8")
        closeout = {
            "status": "FROZEN_ENGINEERING_BASELINE_NOT_RELEASED_TO_STUDENTS",
            "version": "0.1.0-rc.1",
            "remainingPackages": 0,
            "defaultCatalogue": "CLOSED",
            "G2Passed": False,
            "studentPilotReleased": False,
            "artifactCount": 1,
            "integritySha256": hashlib.sha256(integrity_path.read_bytes()).hexdigest(),
        }
        (package / "closeout.json").write_text(json.dumps(closeout), encoding="utf-8")
        self.assertEqual(freeze.verify_freeze(root)["verifiedArtifacts"], 1)
        artifact.write_text("changed", encoding="utf-8")
        with self.assertRaises(ValueError):
            freeze.verify_freeze(root)


if __name__ == "__main__":
    unittest.main()
