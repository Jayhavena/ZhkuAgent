"""合成40题只检查外发准入与零网络装配，不是生产授权。"""
import json, tempfile, unittest
from pathlib import Path
from capture_rule_check_quality import admitted, FINAL_BASE, ENDPOINT, MODEL
from capture_rule_check_selection import sha
TEMP_ROOT=Path(__file__).resolve().parents[2]/'.superpowers/sdd/2026-10-11-rule-check-quality-closeout-plan'

class FinalAdmissionTest(unittest.TestCase):
    def fixture(self,root):
        base=root/FINAL_BASE;base.mkdir(parents=True)
        def write(name,data):
            raw=json.dumps(data,ensure_ascii=False).encode();(base/name).write_bytes(raw);return sha(raw)
        (base/'system-prompt.txt').write_text('synthetic-system',encoding='utf-8')
        ids=[f'R{i:02}' for i in range(1,17)]+[f'N{i:02}' for i in range(25,49)]
        projection=[{'caseId':i,'question':i+' synthetic'} for i in ids]
        body=lambda q:{'model':MODEL,'max_tokens':512,'temperature':0,'stream':False,'enable_thinking':False,'response_format':{'type':'json_object'},'messages':[{'role':'system','content':'synthetic-system'},{'role':'user','content':json.dumps({'question':q,'evidenceOptions':[{'ruleId':'A13/GENERAL','questionScope':'synthetic','verifiedFacts':['synthetic'],'actions':['EXPLAIN_RULE'],'operands':[]}]})}]}
        packet={'endpoint':ENDPOINT,'model':MODEL,'requests':[{'caseId':r['caseId'],'body':body(r['question'])} for r in projection],'localBlockedCases':[],
            'maxCalls':40,'retryCount':0,'maxOutputTokens':512,'regressionDenominator':16,'confirmationDenominator':24,'repeatDenominator':0,'questionProjection':projection}
        digest=write('request-package.json',packet)
        refs=write('forty-reference-confirmed.json',{'synthetic':True});contract=write('scoring-contract-confirmed.json',{'synthetic':True})
        write('reference-confirmation.json',{'authority':'DIRECT_USER_MESSAGE','decision':'CONFIRMED','confirmedDecisions':['D01','D02','D03'],'referenceSha256':refs,'scoringContractSha256':contract,'fixtureOnly':True})
        write('external-transfer-authorization.json',{'authority':'DIRECT_USER_MESSAGE','decision':'CONFIRMED','endpoint':ENDPOINT,'model':MODEL,'maxCalls':40,'packagePath':FINAL_BASE+'/request-package.json','packageSha256':digest,'reportQueriesAllowed':True,'referenceAnswersAllowed':False,'databaseCredentialsAllowed':False,'fixtureOnly':True})
        write('source-freeze.json',{'status':'FROZEN_BEFORE_NEW_MODEL_OUTPUTS','files':[{'path':FINAL_BASE+'/system-prompt.txt','sha256':sha((base/'system-prompt.txt').read_bytes())}]})
        return base,packet,digest,write
    def test_final40_admission_does_not_claim_or_call_network(self):
        with tempfile.TemporaryDirectory(dir=TEMP_ROOT) as directory:
            root=Path(directory);base,packet,digest,write=self.fixture(root)
            self.assertEqual(len(admitted(root,digest,FINAL_BASE)['requests']),40)
            self.assertFalse((base/'provider-capture').exists())
    def test_unconfirmed_reference_rejects_even_with_matching_external_fixture(self):
        with tempfile.TemporaryDirectory(dir=TEMP_ROOT) as directory:
            root=Path(directory);base,packet,digest,write=self.fixture(root)
            write('reference-confirmation.json',{'decision':'PENDING'})
            with self.assertRaises(ValueError):admitted(root,digest,FINAL_BASE)
            self.assertFalse((base/'provider-capture').exists())
    def test_changed_prompt_rejected_even_when_package_and_authorization_hash_agree(self):
        with tempfile.TemporaryDirectory(dir=TEMP_ROOT) as directory:
            root=Path(directory);base,packet,digest,write=self.fixture(root)
            packet['requests'][0]['body']['messages'][0]['content']='changed'
            digest=write('request-package.json',packet)
            auth=json.loads((base/'external-transfer-authorization.json').read_text());auth['packageSha256']=digest;write('external-transfer-authorization.json',auth)
            with self.assertRaises(ValueError):admitted(root,digest,FINAL_BASE)
    def test_question_mismatch_and_path_escape_rejected(self):
        with tempfile.TemporaryDirectory(dir=TEMP_ROOT) as directory:
            root=Path(directory);base,packet,digest,write=self.fixture(root)
            packet['questionProjection'][0]['question']='another'
            digest=write('request-package.json',packet)
            auth=json.loads((base/'external-transfer-authorization.json').read_text());auth['packageSha256']=digest;write('external-transfer-authorization.json',auth)
            with self.assertRaises(ValueError):admitted(root,digest,FINAL_BASE)
            with self.assertRaises(ValueError):admitted(root,digest,'../outside')

if __name__=='__main__':unittest.main()
