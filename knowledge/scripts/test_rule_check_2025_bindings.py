"""核验原文绑定审计会拒绝内容漂移；这些是审计器检查，不是政策回答得分。"""
import copy
import importlib.util
import json
import unittest
from pathlib import Path

spec = importlib.util.spec_from_file_location("bindings", Path(__file__).with_name("audit-rule-check-2025-bindings.py"))
bindings = importlib.util.module_from_spec(spec)
spec.loader.exec_module(bindings)
ROOT = Path(__file__).resolve().parents[2]
EVIDENCE = ROOT / "docs/evidence/rule-check-refactor/2026-10-08/b01-source-binding-01"


class AuditContract(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.draft = json.loads((ROOT / "docs/product/rule-check-2025-rule-template-draft.json").read_text(encoding="utf-8-sig"))
        cls.snapshot = json.loads((EVIDENCE / "database-snapshot.json").read_text(encoding="utf-8-sig"))
        # 真正原件提取在CLI另行运行；此处为指纹/跨度拒绝路径的受控替身。
        cls.pages = {p: "\n".join(c["rawExtractedText"] for c in cls.draft["clauses"] if c["pdfPhysicalPage"] == p)
                     for p in {c["pdfPhysicalPage"] for c in cls.draft["clauses"]}}
        cls.pdf_hash = cls.draft["sourceFileSha256"]

    def run_audit(self, draft=None, snapshot=None, pages=None, pdf_hash=None):
        return bindings.audit(draft or self.draft, snapshot or self.snapshot, pages or self.pages, pdf_hash or self.pdf_hash)

    def test_snapshot_integrity_without_inventing_bindings(self):
        report = self.run_audit()
        self.assertEqual((report["sourcesCount"], report["chunksCount"], report["entriesCount"]), (6, 17, 0))
        self.assertFalse(report["runtimeBindingsActivated"])
        self.assertTrue(all(c["proposedLiteralChunk"]["chunkId"] is None for c in report["clauses"]))

    def test_wrong_pdf_rejected(self):
        with self.assertRaisesRegex(ValueError, "原件指纹"):
            self.run_audit(pdf_hash="0" * 64)

    def test_changed_quote_rejected(self):
        draft = copy.deepcopy(self.draft)
        draft["clauses"][0]["rawExtractedText"] += "可以直接毕业。"
        with self.assertRaisesRegex(ValueError, "摘录指纹"):
            self.run_audit(draft=draft)

    def test_same_hash_wrong_page_rejected(self):
        pages = copy.deepcopy(self.pages)
        pages[76] = "无相应条款"
        with self.assertRaisesRegex(ValueError, "指定页"):
            self.run_audit(pages=pages)

    def test_changed_database_body_rejected(self):
        snapshot = copy.deepcopy(self.snapshot)
        snapshot["chunks"][0]["content"] += "40学分"
        with self.assertRaisesRegex(ValueError, "正文指纹"):
            self.run_audit(snapshot=snapshot)

    def test_foreign_source_identity_rejected(self):
        snapshot = copy.deepcopy(self.snapshot)
        snapshot["chunks"][0]["source_id"] = "00000000-0000-0000-0000-000000000000"
        with self.assertRaisesRegex(ValueError, "悬空"):
            self.run_audit(snapshot=snapshot)

    def test_non_readonly_snapshot_rejected(self):
        snapshot = copy.deepcopy(self.snapshot)
        snapshot["transactionReadOnly"] = "off"
        with self.assertRaisesRegex(ValueError, "只读"):
            self.run_audit(snapshot=snapshot)

    def test_utf16_supplementary_character_boundaries(self):
        self.assertEqual(bindings.utf16_length("甲😀乙"), 4)
        self.assertTrue(bindings.verify_span("甲😀乙", 1, 3, "😀"))
        self.assertFalse(bindings.verify_span("甲😀乙", 1, 2, "😀"))
        self.assertFalse(bindings.verify_span("甲😀乙", 1, 4, "😀"))


if __name__ == "__main__":
    unittest.main()
