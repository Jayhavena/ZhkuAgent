import json
import tempfile
import unittest
from pathlib import Path
from build_rule_check_quality_review import build

class QualityReviewTest(unittest.TestCase):
    def test_incomplete_provider_batch_keeps_full_denominator_and_no_fake_grade(self):
        workspace=Path(__file__).resolve().parents[2]/'.superpowers/sdd/2026-10-11-rule-check-quality-closeout-plan'
        with tempfile.TemporaryDirectory(dir=workspace) as directory:
            root=Path(directory);base=root/'docs/evidence/rule-check-quality/2026-10-11';base.mkdir(parents=True)
            product=root/'docs/product';product.mkdir(parents=True)
            old=[{'caseId':f'R{i:02}','question':'合成问题'} for i in range(1,17)]
            new=[{'caseId':f'N{i:02}','question':'合成问题','required':'合成参考'} for i in range(1,25)]
            (product/'rule-check-2025-human-reference-report.json').write_text(json.dumps({'cases':old}),encoding='utf-8')
            (base/'confirmation-cases.json').write_text(json.dumps({'cases':new}),encoding='utf-8')
            ids=[x['caseId'] for x in old+new]+['T01','T02','T03']
            cases=[{'caseId':case,'question':'合成问题','result':{'decision':{'status':'INSUFFICIENT_EVIDENCE','confirmedFacts':[]}},
                    'actualOriginalLocations':[],'providerRecordStatus':'ERROR' if i==1 else 'NOT_EXECUTED','qualityEligible':False} for i,case in enumerate(ids)]
            actual=base/'actual.json';actual.write_text(json.dumps({'cases':cases}),encoding='utf-8')
            result=build(root,actual);self.assertEqual(result['rows'],43);self.assertIsNone(result['qualityPassed'])
            grades=json.loads((base/'human-review/human-grades.json').read_text(encoding='utf-8'))
            self.assertEqual(len(grades['cases']),43)
            self.assertTrue(all(x['humanGrade']=='PENDING' and not x['humanConfirmed'] for x in grades['cases']))

if __name__=='__main__':unittest.main()
