import json
import tempfile
import unittest
from pathlib import Path

import revise


class RevisionTest(unittest.TestCase):
    def test_reviewed_changes_and_integrity(self):
        original = json.loads(revise.SOURCE.read_text(encoding='utf-8'))
        with tempfile.TemporaryDirectory() as tmp:
            dataset, manifest = revise.create(Path(tmp))
            cases = {c['caseId']: c for c in dataset['cases']}
            c17, c20 = cases['RFE-017'], cases['RFE-020']
            self.assertEqual(['schedule-order'], [f['semanticFactId'] for f in c17['expectedFacts']])
            self.assertEqual(['schedule-previous-semester'], [f['semanticFactId'] for f in c17['optionalContextFacts']])
            self.assertEqual(1, len(c17['requiredResponseElements']))
            self.assertEqual([], c20['requiredEvidence'])
            self.assertIn('CITATION_MISMATCH', c20['criticalFailureTags'])
            rule = dataset['scoringContract']['citationApplicability']
            self.assertEqual('NOT_APPLICABLE', rule['noRequiredEvidenceAndNoCitation'])
            self.assertEqual('VERIFY_EACH_CITATION', rule['citationPresent'])
            self.assertEqual('UNJUDGEABLE', rule['citationVerificationUnavailable'])
            self.assertEqual(revise.digest((Path(tmp) / manifest['datasetFile']).read_bytes()), manifest['datasetSha256'])
            for old in original['cases']:
                if old['caseId'] not in {'RFE-017', 'RFE-020'}:
                    self.assertEqual(old, cases[old['caseId']])
            self.assertEqual('DRAFT_NEEDS_HUMAN_CASE_REVIEW', dataset['datasetStatus'])


if __name__ == '__main__':
    unittest.main()
