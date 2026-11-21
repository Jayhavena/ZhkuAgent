import copy
import json
import tempfile
import unittest
from pathlib import Path
import freeze


class FreezeTest(unittest.TestCase):
    def setUp(self):
        self.m = json.loads((freeze.OUT / 'manifest.json').read_bytes())
        self.d = json.loads((freeze.OUT / self.m['datasetFile']).read_bytes())

    def test_review_card_decisions_and_dataset_agree(self):
        rows = {}
        for line in (freeze.OUT / '审核决定卡.md').read_text(encoding='utf-8').splitlines():
            if line.startswith('| RFE-'):
                fields = [s.strip() for s in line.split('|')[1:-1]]
                self.assertNotIn(fields[0], rows)
                rows[fields[0]] = fields[1:]
        decisions = json.loads((freeze.OUT / 'review-decisions.json').read_bytes())['cases']
        self.assertEqual(20, len(rows))
        self.assertEqual(20, len(decisions))
        self.assertEqual(20, len(self.d['cases']))
        for case, decision in zip(self.d['cases'], decisions):
            self.assertEqual(case['humanReview'], decision)
            self.assertEqual('APPROVED', case['humanReviewStatus'])
            self.assertEqual([case['humanReviewStatus'], decision['reviewer'], decision['reviewedAt'], decision['basis']], rows[case['caseId']])

    def test_manifest_sources_and_freeze_identity(self):
        self.assertEqual('FROZEN', self.d['datasetStatus'])
        for key in ('datasetId', 'datasetVersion', 'caseCount', 'datasetStatus', 'frozenAt', 'reviewer', 'freezeScope', 'corpusFingerprint'):
            self.assertEqual(self.d[key], self.m[key])
        for name, sha in self.m['files'].items():
            self.assertEqual(sha, freeze.digest((freeze.OUT / name).read_bytes()), name)
        for name, sha in self.m['sources'].items():
            self.assertEqual(sha, freeze.digest((freeze.ROOT / name).read_bytes()), name)

    def test_regeneration_is_identical_and_refuses_modified_output(self):
        with tempfile.TemporaryDirectory() as tmp:
            out = Path(tmp)
            freeze.create(out)
            for name in (*self.m['files'], 'manifest.json'):
                self.assertEqual((freeze.OUT / name).read_bytes(), (out / name).read_bytes())
            (out / self.m['datasetFile']).write_text('tampered', encoding='utf-8')
            with self.assertRaises(FileExistsError):
                freeze.create(out)

    def test_expected_facts_and_evidence_still_match_b02_b03(self):
        b02 = json.loads((freeze.ROOT / 'docs/product/rule-check-2025-b02-semantic-review.json').read_bytes())
        b03 = json.loads((freeze.ROOT / 'docs/product/rule-check-2025-b03-publication-review.json').read_bytes())
        clauses = {c['locatorId']: c for c in b03['clauses']}
        rules = {r['reviewItemId']: r for r in b02['rules']}
        for case in self.d['cases']:
            for evidence in case['requiredEvidence']:
                clause = clauses[evidence['locatorId']]
                for key in ('article', 'physicalPage', 'printedPage', 'contentChecksum'):
                    self.assertEqual(evidence[key], clause[key])
                self.assertEqual(clause['contentChecksum'], freeze.digest(clause['content'].encode('utf-8')))
            for fact in case['expectedFacts'] + case.get('optionalContextFacts', []):
                original = next(f for f in rules[fact['reviewItemId']]['facts'] if f['id'] == fact['semanticFactId'])
                self.assertEqual('CONFIRMED', original['semanticReviewState'])
                self.assertEqual(fact['expectedClaim'], original['text'])

    def test_only_review_metadata_changes_from_accepted_v3(self):
        original = json.loads(freeze.SOURCE.read_bytes())
        for old, new in zip(original['cases'], self.d['cases']):
            actual = copy.deepcopy(new)
            actual.pop('humanReview')
            actual['humanReviewStatus'] = 'PENDING'
            self.assertEqual(old, actual)
        self.assertEqual(19, sum(bool(c['expectedFacts']) for c in self.d['cases']))
        self.assertEqual(19, sum(bool(c['requiredEvidence']) for c in self.d['cases']))
        state = self.d['sourceSnapshot']['runtimeState']
        self.assertFalse(state['runtimeActivated'])
        self.assertFalse(state['studentReleaseApproved'])
        self.assertEqual('UNKNOWN', state['currentAcademicYearApplicability'])


if __name__ == '__main__':
    unittest.main()
