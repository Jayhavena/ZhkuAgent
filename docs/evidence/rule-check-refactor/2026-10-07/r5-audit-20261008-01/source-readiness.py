from pathlib import Path
import hashlib, json
from datetime import datetime, timezone
batch=Path(__file__).resolve().parent
root=batch.parents[4]
external=Path('D:/alldesk/Projects/SuperAgent-zhkuagent-mvp/data')
paths=[external/'manifests/2025版学生手册2025.9.2.json',
       external/'curation/2025版学生手册2025.9.2-公共选修候选知识片段.json',
       external/'raw/2025版学生手册2025.9.2.pdf']
assert not (batch/'source-readiness.json').exists()
hashes={str(p):hashlib.sha256(p.read_bytes()).hexdigest() for p in paths}
manifest=json.loads(paths[0].read_text(encoding='utf-8-sig'))
curation=json.loads(paths[1].read_text(encoding='utf-8-sig'))
draft=json.loads((root/'docs/product/rule-check-2025-rule-template-draft.json').read_text(encoding='utf-8-sig'))
assert hashes[str(paths[2])]==manifest['sha256'].lower()==draft['sourceFileSha256']
assert manifest['id']==curation['source_id']
result=dict(observedAt=datetime.now(timezone.utc).isoformat(), hashes=hashes,
    rawPdfBytes=paths[2].stat().st_size, manifestSourceId=manifest['id'],
    manifestRevision=manifest['source_version'], manifestReviewStatus=manifest['review_status'],
    manifestStudentVisible=manifest['student_visible'],
    curationReviewStatus=curation['review_status'], curationScope=curation['publication_scope'],
    curationStudentVisible=curation['student_visible'], curationSharingConfirmed=curation['sharing_rights_confirmed'],
    ruleReferenceRevision=draft['referenceRevision'], runtimeVersionMappingStatus='NOT_VERIFIED_NO_AUTOMATIC_ALIAS',
    runtimeSourceId=None,runtimeChunkBindingsStatus='NOT_VERIFIED',
    containerInventory={'name':'zhkuagent-postgres','observedStatus':'Exited (0) 10 days ago','startedByThisRun':False},
    databaseConnected=False, databaseQueried=False, databaseMigrated=False, onlineSourceQualification='UNKNOWN',
    studentReleaseApproved=False, ruleDefinitionReviewStatus=draft['ruleDefinitionReviewStatus'])
(batch/'source-readiness.json').write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
print('原件指纹匹配；外部资料保持本地验证/不可学生展示；原库已停止且未连接，在线资格仍未知')
