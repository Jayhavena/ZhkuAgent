"""整理已确认参考的来源和运行时审核缺口，不把参考确认变成生产规则审核。"""
from pathlib import Path
import datetime, hashlib, json
HERE=Path(__file__).resolve().parent; ROOT=HERE.parents[4]
source=ROOT/'docs/product/rule-check-2025-human-reference-development.json'
target=ROOT/'docs/product/rule-check-2025-rule-template-draft.json'
assert not target.exists(), '规则草案批次不可覆盖'
original=source.read_bytes(); reference=json.loads(original.decode('utf-8-sig'))
assert reference['approvedDevelopmentCount']==20 and all(r['humanReview']['decision']=='CONFIRMED' for r in reference['records'])
gaps={
    'Q02':['研究生选修规则未取得'],
    'Q05':['专业选修课缓考依据未取得'],
    'Q09':['实际当期开课结果未核实'],
    'Q13':['个人对应培养方案及可靠的已修记录未取得'],
    'Q14':['若查询具体选课时间或入口，需另查当期通知'],
    'Q16':['当期通知和入口开放状态未核实'],
    'Q18':['口述数据不能核实官方选课记录或个人资格'],
}
clauses={}; profiles=[]
for row in reference['records']:
    bindings=[]
    for e in row['humanReview']['acceptedEvidence']:
        locator=f'handbook-2025-article-{e["article"]}'
        raw=e['rawExtractedText']
        item={'locatorId':locator,'article':e['article'],'pdfPhysicalPage':e['physicalPage'],
              'printedPage':e['printedPage'],'referenceRevision':reference['referenceRevision'],
              'sourceFileSha256':reference['sourceSha256'],
              'excerptUtf8Sha256':hashlib.sha256(raw.encode('utf-8')).hexdigest(),
              'rawExtractedText':raw,'runtimeSourceId':None,'runtimeChunkId':None,
              'runtimeChunkChecksum':None,'runtimeSpan':None}
        if locator in clauses: assert clauses[locator]==item
        clauses[locator]=item; bindings.append(locator)
    profiles.append({
        'referenceCaseId':row['sourceCaseId'],'reviewId':row['reviewId'],'reviewedQuestion':row['question'],
        'purpose':'DEVELOPMENT_REFERENCE_PROFILE_NOT_QUERY_ROUTER',
        'expectedStatusForReviewedQuestion':row['humanReview']['acceptedStatus'],
        'confirmedReferencePoints':row['humanReview']['acceptedFacts'],
        'referenceReview':row['humanReview'],
        'requiredEvidenceLocators':bindings,
        'boundaryDraft':row['proposedBoundary'],
        'boundarySeparatelyConfirmed':row['humanReview'].get('acceptedBoundary'),
        'proposedRequiredConditions':([{'key':'courseType','question':'这门课属于公共选修还是专业选修？',
                                      'allowedValues':['PUBLIC','PROFESSIONAL']}] if row['reviewId']=='Q05' else []),
        'missingDependenciesDraft':gaps.get(row['reviewId'],[]),
        'factIndependenceReview':None,'scopeAndExceptionsReview':None,'conflictCompletenessReview':None,
        'ruleDefinitionReviewStatus':'DRAFT','ruleDefinitionReviewer':None,'ruleDefinitionReviewedAt':None,
        'runtimeTemplate':None,
    })
assert len(profiles)==20 and len(clauses)==10
result={'schemaVersion':1,'createdAt':datetime.datetime.now(datetime.timezone(datetime.timedelta(hours=8))).isoformat(),
        'referenceRevision':reference['referenceRevision'],'sourceFileSha256':reference['sourceSha256'],
        'referenceArtifactSha256':hashlib.sha256(original).hexdigest(),'developmentReferenceConfirmed':True,
        'ruleDefinitionReviewStatus':'DRAFT','runtimeActivated':False,'studentVisible':False,
        'sharingQualificationConfirmed':False,'currentAcademicYearApplicability':'UNKNOWN',
        'clauses':list(clauses.values()),'profiles':profiles,
        'notes':['20题确认只确认相应问题的参考要点，不能推导出任意新问题的适用条件。',
                 '证据定位按条款归并为10项；PDF文件hash、摘录hash和未来数据库片段hash不可混用。',
                 '提取文本存在换行；运行时跨度必须在实际发布片段上重新绑定，不伪造UUID或位置。',
                 '每项事实的独立性、条件与例外完整性、冲突、资料展示资格另审；此草案不注册生产Bean。',
                 'Q14的当期通知缺口只在查询具体时间/入口时触发；不能把相关但非必要材料变成无用追问。']}
target.write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
assert source.read_bytes()==original
(HERE/'template-export-verification.json').write_text(json.dumps({
    'passed':True,'referenceUnchanged':True,'profileCount':20,'clauseCount':10,'runtimeActivated':False,
    'studentVisible':False,'ruleDefinitionReviewStatus':'DRAFT','referenceSha256':hashlib.sha256(original).hexdigest(),
    'draftPath':target.relative_to(ROOT).as_posix(),'draftSha256':hashlib.sha256(target.read_bytes()).hexdigest()
},ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
print('PASS: 20 confirmed-reference profiles / 10 clause locators exported as DRAFT; no fabricated runtime identities.')
