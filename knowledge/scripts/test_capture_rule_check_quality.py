import json
import tempfile
import unittest
from pathlib import Path
from capture_rule_check_quality import validate_response, admitted, BASE, PACKAGE, ENDPOINT, MODEL
from capture_rule_check_selection import sha

class QualityCaptureTest(unittest.TestCase):
    def response(self,content):
        return {'model':MODEL,'choices':[{'index':0,'finish_reason':'stop','message':{'role':'assistant','content':content}}],
                'usage':{'prompt_tokens':10,'completion_tokens':3,'total_tokens':13}}
    def test_preserves_actual_plan_without_claiming_semantic_grade(self):
        content=json.dumps({'tasks':[{'ruleId':'A15/GENERAL','action':'COMPARE_THRESHOLD','operand':'ABSENCE_PERCENT','value':'30'}]})
        record=validate_response(self.response(content))
        self.assertEqual(record['rawModelContent'],content)
        self.assertFalse(record['humanSemanticGradeProduced'])
        self.assertEqual(record['totalTokens'],13)
    def test_invalid_protocol_is_retained_for_java_rejection_not_discarded(self):
        record=validate_response(self.response('{"tasks":[],"answer":"虚构"}'))
        self.assertEqual(record['status'],'RESPONSE_RECEIVED')
        self.assertEqual(record['protocolDiagnostic'],'INVALID_RESPONSE')
    def test_truncated_or_tool_response_cannot_be_recorded_as_success(self):
        value=self.response('{}');value['choices'][0]['finish_reason']='length'
        with self.assertRaises(ValueError):validate_response(value)
        value=self.response('{}');value['choices'][0]['message']['tool_calls']=[{}]
        with self.assertRaises(ValueError):validate_response(value)
    def test_no_authorization_means_no_batch_claim_or_network(self):
        with tempfile.TemporaryDirectory(dir=Path(__file__).resolve().parents[2]/'.superpowers/sdd/2026-10-11-rule-check-quality-closeout-plan') as directory:
            root=Path(directory);(root/BASE).mkdir(parents=True)
            with self.assertRaises((ValueError,FileNotFoundError)):admitted(root,'0'*64)
            self.assertFalse((root/BASE/'provider-capture').exists())
    def test_wrong_hash_rejected_before_authorization_can_expand_budget(self):
        with tempfile.TemporaryDirectory(dir=Path(__file__).resolve().parents[2]/'.superpowers/sdd/2026-10-11-rule-check-quality-closeout-plan') as directory:
            root=Path(directory);(root/BASE).mkdir(parents=True);(root/PACKAGE).write_text('{}')
            with self.assertRaises(ValueError):admitted(root,'f'*64)
            self.assertFalse((root/BASE/'provider-capture').exists())

if __name__=='__main__':unittest.main()
