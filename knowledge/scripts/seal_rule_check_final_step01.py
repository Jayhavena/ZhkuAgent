"""封存准备阶段的身份与证据；不得创建真实外发许可或产品放行记录。"""
from pathlib import Path
import json, hashlib, zipfile, datetime
from capture_rule_check_quality import admitted, FINAL_BASE

ROOT=Path(__file__).resolve().parents[2]
BASE=ROOT/FINAL_BASE
def read(p):return json.loads(p.read_text(encoding='utf-8-sig'))
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def save(name,value):
    with (BASE/name).open('x',encoding='utf-8',newline='\n') as f:json.dump(value,f,ensure_ascii=False,indent=2);f.write('\n')

def main():
    assembly=ROOT/'docs/evidence/rule-check-refactor/2026-10-07/final-step01-assembly-20261011-02'
    identity=ROOT/'docs/evidence/rule-check-refactor/2026-10-07/final-step01-identity-20261011-01'
    for folder in (assembly,identity):
        result=read(folder/'results.json')
        assert result['passed'] and result['sourceDrift']==0 and result['resourcesVerified']
    assert read(BASE/'verifier-contract.json')['passed']
    assert 'Ran 44 tests' in (BASE/'python-capture-tests.log').read_text(encoding='utf-8-sig')
    assert '\nOK' in (BASE/'python-capture-tests.log').read_text(encoding='utf-8-sig')
    for src,dest in [('final-request-package.json','request-package.json'),('system-prompt.txt','system-prompt.txt'),('final-assembly-observation.json','assembly-observation.json')]:
        with (BASE/dest).open('xb') as out:out.write((assembly/src).read_bytes())
    packet=read(BASE/'request-package.json');refs=read(BASE/'forty-reference-confirmed.json')['cases']
    expected={f'R{i:02}' for i in range(1,17)}|{f'N{i:02}' for i in range(25,49)}
    assert len(refs)==40 and {r['caseId'] for r in refs}==expected
    assert {r['caseId']:r['question'] for r in refs}=={r['caseId']:r['question'] for r in packet['questionProjection']}
    assert len(packet['requests'])==40 and not packet['localBlockedCases'] and packet['providerCalls']==0 and packet['repeatDenominator']==0
    system=(BASE/'system-prompt.txt').read_text(encoding='utf-8')
    for row in packet['requests']:
        assert row['body']['messages'][0]['content']==system
        payload=json.loads(row['body']['messages'][1]['content'])
        assert set(payload)=={'question','evidenceOptions'}
        assert all(set(o)=={'ruleId','questionScope','verifiedFacts','actions','operands'} for o in payload['evidenceOptions'])
    assert not (BASE/'external-transfer-authorization.json').exists() and not (BASE/'provider-capture').exists()
    # 使用实际包调用生产准入：尚无直接外发许可时必须拒绝，不得创建采集目录。
    denied=False
    try:admitted(ROOT,sha(BASE/'request-package.json'),FINAL_BASE)
    except FileNotFoundError as error:
        assert Path(error.filename).name=='external-transfer-authorization.json';denied=True
    assert denied and not (BASE/'provider-capture').exists()
    authorization={'status':'PREPARED_NOT_AUTHORIZED','endpoint':packet['endpoint'],'model':'qwen-plus','maxCalls':40,'retryCount':0,'maxOutputTokens':512,
        'packagePath':FINAL_BASE+'/request-package.json','packageSha256':sha(BASE/'request-package.json'),
        'payload':'旧16题、新24题及每题已核验的规则ID、范围、原文事实、允许动作和操作数；不含人工参考答案、整本PDF、个人记录、数据库身份或凭证',
        'perCallSeconds':5,'batchSeconds':300,'priorAuthorizationsReused':False}
    save('external-transfer-review.json',authorization)
    # 上批源码清单保持历史身份；新清单按当前文件重算，补齐新配置和评分输入。
    previous=ROOT/'docs/evidence/rule-check-targeted-repair/2026-10-11/source-freeze.json'
    paths={r['path'] for r in read(previous)['files']}
    paths.update(p.relative_to(ROOT).as_posix() for p in (ROOT/'knowledge/src').rglob('*') if p.is_file())
    paths.update(p.relative_to(ROOT).as_posix() for p in (ROOT/'knowledge/scripts').glob('*.py'))
    paths.update(p.relative_to(ROOT).as_posix() for p in BASE.iterdir() if p.is_file() and p.name!='step-01-state.json')
    paths.add('docs/product/rule-check-final-closeout-exit-contract.md')
    files=[{'path':p,'sha256':sha(ROOT/p)} for p in sorted(paths)]
    save('source-freeze.json',{'status':'FROZEN_BEFORE_NEW_MODEL_OUTPUTS','purpose':'FINAL40_EVAL_CANDIDATE_NOT_RELEASE','files':files,'providerCalls':0,'productQualityPassed':False,'g2Passed':False})
    archive=BASE/'step01-eval-candidate.zip'
    with zipfile.ZipFile(archive,'x',zipfile.ZIP_DEFLATED) as out:
        for row in files:out.write(ROOT/row['path'],row['path'])
        out.write(BASE/'source-freeze.json',FINAL_BASE+'/source-freeze.json')
    with zipfile.ZipFile(archive) as bundle:
        assert len(bundle.namelist())==len(files)+1
        for row in files:assert hashlib.sha256(bundle.read(row['path'])).hexdigest()==row['sha256']
        assert hashlib.sha256(bundle.read(FINAL_BASE+'/source-freeze.json')).hexdigest()==sha(BASE/'source-freeze.json')
    previous_files={r['path']:r['sha256'] for r in read(previous)['files']}
    changes=[r['path'] for r in files if r['path'] in previous_files and r['sha256']!=previous_files[r['path']]]
    receipt={'status':'STEP01_CHECKS_COMPLETE_NOT_RELEASED','recordedAt':datetime.datetime.now(datetime.timezone.utc).isoformat(),
       'oldCases':16,'newCases':24,'totalCases':40,'humanReferenceConfirmed':True,'referenceContractVersion':'FINAL40-V2',
       'requestsPrepared':40,'localBlockedCases':0,'newProviderCalls':0,'externalTransferAuthorized':False,
       'assemblyChecks':16,'strictIdentityChecks':15,'pythonCaptureChecks':44,'launcherContractChecks':42,
       'assemblyEvidence':assembly.relative_to(ROOT).as_posix(),'identityEvidence':identity.relative_to(ROOT).as_posix(),
       'resourcesClean':True,'sourceDriftInChecks':0,'currentSourceChangesFromPreviousCandidate':changes,
       'sourceFreezeSha256':sha(BASE/'source-freeze.json'),'frozenFiles':len(files),'packageSha256':sha(BASE/'request-package.json'),
       'referenceSha256':sha(BASE/'forty-reference-confirmed.json'),'scoringContractSha256':sha(BASE/'scoring-contract-confirmed.json'),
       'promptSha256':sha(BASE/'system-prompt.txt'),'archivePath':archive.relative_to(ROOT).as_posix(),'archiveSha256':sha(archive),'verifiedArchiveEntries':len(files)+1,
       'releaseApproved':False,'g2Passed':False,'historicalGradesRewritten':False,
       'testCorrection':'首次新增合成测试缺catalogue-mode导致空值，非预期业务RED；修正测试配置后通过。Python首次系统临时目录拒写，改用仓库内临时目录后44项通过。历史失败记录保留。'}
    save('step-01-receipt.json',receipt)
    print(json.dumps({k:receipt[k] for k in ['totalCases','requestsPrepared','newProviderCalls','frozenFiles','packageSha256','verifiedArchiveEntries']}))

if __name__=='__main__':main()
