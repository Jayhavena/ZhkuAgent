"""防止漏题、未观察检索和未审核语义被折算为漂亮成绩。"""
import unittest
from pathlib import Path
import json
import build_rule_check_package03_report as report

class ReportContract(unittest.TestCase):
    def rows(self):
        return [{'caseId':f'R{i:02}','actualRecall':{'observed':False,'deliveredRecall':0,'recall':None},
            'statusMatchesReference':True,'confirmedVerdict':'UNJUDGEABLE','aiProposedVerdict':'PASS'} for i in range(1,17)]
    def test_status_matches_and_ai_proposals_do_not_make_human_passes(self):
        metrics=report.summarize(self.rows())
        self.assertEqual(metrics['statusMatchesReference'],16)
        self.assertEqual(metrics['confirmedVerdicts']['UNJUDGEABLE'],16)
        self.assertIsNone(metrics['taskPassRate']);self.assertFalse(metrics['productGatePassed'])
    def test_missing_retrieval_observations_keep_full_denominator(self):
        rows=self.rows();rows[0]['actualRecall']={'observed':True,'recall':1,'deliveredRecall':1}
        metrics=report.summarize(rows)
        self.assertEqual(metrics['observedRetrievalMacroRecall'],1)
        self.assertEqual(metrics['endToEndNecessaryEvidenceDeliveryRecall'],1/16)
        self.assertIsNone(metrics['fullSixteenRetrievalMacroRecall'])
    def test_deleting_or_duplicating_cases_invalidates_report(self):
        with self.assertRaises(ValueError):report.summarize(self.rows()[:-1])
        rows=self.rows();rows[-1]['caseId']='R01'
        with self.assertRaises(ValueError):report.summarize(rows)
    def test_no_retrieval_is_not_a_successful_empty_needed_set(self):
        result=report.recall({'retrievalCalls':0},{'requiredEvidence':[{'article':15}]},{})
        self.assertFalse(result['observed']);self.assertIsNone(result['recall']);self.assertEqual(result['deliveredRecall'],0)
    def test_random_citation_alias_is_not_quality_regression_but_identity_is_retained(self):
        left={'status':'ANSWERED','evidence':[{'citationId':'random-a','sourceId':'same-source','checksum':'same-hash'}]}
        right={'status':'ANSWERED','evidence':[{'citationId':'random-b','sourceId':'same-source','checksum':'same-hash'}]}
        self.assertEqual(report.normalize_decision(left),report.normalize_decision(right))
        right['evidence'][0]['checksum']='changed-hash'
        self.assertNotEqual(report.normalize_decision(left),report.normalize_decision(right))
    def test_retrieved_required_clause_is_not_delivered_when_answer_cites_another_clause(self):
        chunk={'chunkId':'required','sourceId':'source','content':'required text','contentChecksum':report.sha(b'required text')}
        binding={'article':14,'sourceId':'source','text':'required text','checksum':chunk['contentChecksum']}
        path={'retrievalCalls':1,'top5':[chunk],'result':{'decision':{'confirmedFacts':[
            {'evidence':[{'chunkId':'other','sourceId':'source','revision':'2025.9.2','checksum':report.sha(b'other text'),'text':'other text','startInclusive':0,'endExclusive':10}]}]}}}
        case={'requiredEvidence':[{'article':14,'contentChecksum':chunk['contentChecksum']}]}
        result=report.recall(path,case,{'required':binding,'other':{'article':13,'sourceId':'source','text':'other text','checksum':report.sha(b'other text')}})
        self.assertEqual(result['recall'],1)
        self.assertEqual(result['deliveredRecall'],0)
    def test_run_annotations_obey_frozen_required_fields_even_when_unjudgeable(self):
        root=Path(__file__).resolve().parents[2]
        final=root/report.BASE/'assessment-final/run-annotations.json'
        target=final if final.exists() else root/report.BASE/'run-annotations.json'
        rubric=json.loads((root/'docs/product/rule-check-2025-scoring-rubric.json').read_text(encoding='utf-8-sig'))
        annotation=json.loads(target.read_text(encoding='utf-8-sig'))['annotations'][0]
        self.assertFalse(set(rubric['requiredAnnotationFields'])-set(annotation))

if __name__=='__main__':unittest.main()
