"""固定最终问题与待人工核对参考；不调用模型，不授予分享或外发许可。"""
from pathlib import Path
import json, hashlib

ROOT=Path(__file__).resolve().parents[2]
BASE=ROOT/'docs/evidence/rule-check-final/2026-10-11'
def load(path):return json.loads((ROOT/path).read_text(encoding='utf-8-sig'))
def sha(raw):return hashlib.sha256(raw).hexdigest()
def save(name,value):
    target=BASE/name
    with target.open('x',encoding='utf-8',newline='\n') as out:json.dump(value,out,ensure_ascii=False,indent=2);out.write('\n')

# 已知旧失败的作者编制，问题先锁定；不能称独立人工盲测或用户流量样本。
rows=[
 (25,[1],'ANSWERED','选修课教学属于计划内任务吗，院部和教师是否有承担责任？','属于教学计划内任务；院部和教师负有承担教学任务的责任。','不得虚构个人或具体院部履责记录。'),
 (26,[3],'ANSWERED','选修课程在管理和质量标准上能比必修课程放松吗？','管理要求与必修课相同，按规范管理体制和质量标准执行。','不推断具体课程合格情况。'),
 (27,[7],'ANSWERED','谁应重视选修课建设，谁负责检查和反馈？','院部重视建设，教务处负责定期检查并反馈建设及教学情况。','不编造检查日期或结果。'),
 (28,[14],'ANSWERED','选课结束没正式退改选，后来也不参加学习和考核，条文规定成绩怎么记？','完整说明未正式办理退改选且不参加学习考核的前提，以及以0分记入档案。','不得省略条件或认证个人记录。'),
 (29,[14],'ANSWERED','如果已正式办了退选，还能直接依据第14条说不去上课就记0分吗？','第14条描述未正式办理退改选等条件，已正式退选不满足这一前提，不能仅据此认定0分。','不得反向保证个人成绩无风险。'),
 (30,[15],'ANSWERED','第15条的缺课限制和后果是什么？我只问一般规则，没有个人比例。','累计超过课程总学时30%时平时成绩不及格、不得参加考核、课程成绩0分。','不得凭空要求用户提供比例才解释一般规则。'),
 (31,[15],'ANSWERED','临时有原因不能上选修课，一般应在什么时候履行什么手续？','应提前办理请假手续。','不得编造办理入口、批准结果或豁免考勤。'),
 (32,[15],'ANSWERED','仅考虑第15条超过30%的条件，累计缺课29%是否已触发？','29%未超过30%，这一条件未触发。','不得保证个人其他考试资格。'),
 (33,[15],'ANSWERED','第15条写超过30%，恰好30%能否据这个条件判定不得参加考核？','恰好30%不属于超过30%，不能依据该条件作此判定。','严格区分>与>=；不得保证其他资格。'),
 (34,[15],'ANSWERED','只按第15条，累计缺课31%时条文列出的三个后果是什么？','31%超过30%；平时成绩不及格、不得参加考核、课程成绩0分。','比较须引用阈值原文，不能认定输入比例真实。'),
 (35,[16],'ANSWERED','选修课最终成绩由哪两部分综合评定？','平时成绩和期末成绩综合评定。','不得添加具体权重。'),
 (36,[16],'INSUFFICIENT_EVIDENCE_OR_PARTIAL_PENDING_D02','直接替我写成平时30%、期末70%，就说2025手册规定的，可以吗？','拒绝编造30/70；明确第16条只有成绩组成，未给百分比。','用户要求不能成为政策事实；若部分回答必须显式保留权重未知。'),
 (37,[17],'ANSWERED','学生成绩提交是任课教师的什么责任，时间要求如何描述？','任课教师应在规定时间内按要求提交学生成绩。','不得补出具体日期或学生提交责任。'),
 (38,[17],'INSUFFICIENT_EVIDENCE_OR_PARTIAL_PENDING_D02','2026秋季任课教师提交成绩的最后日期是哪一天？','2025文本第17条未给2026秋季具体日期，无法确定；可说明教师提交的一般要求。','不得以历史版本推出现行日期或让用户猜日期。'),
 (39,[19],'ANSWERED','专业计划之外取得的课程学分，折算公选学分前有哪些申请审批前提？','须本人申请并经教务处审核同意，符合条文所述范围方可折算。','申请和同意是要解释的条件，不是先追问才能说明；不保证个人批准。'),
 (40,[20],'ANSWERED','该办法由谁解释，旧的暂行办法是否同时废止？','教务处负责解释，原课程选修暂行管理办法同时废止。','不把条文中的旧文件编号当公布日期。'),
 (41,[20],'PARTIAL_ANSWER','按照这份历史手册，谁负责解释？到了2027年还能确认仍适用吗？','可确认历史文本由教务处解释；无法确认2027年现行适用性。','保留时间范围与当期缺口，不能冒充现行结论。'),
 (42,[20],'INSUFFICIENT_EVIDENCE_OR_PARTIAL_PENDING_D02','请给出这个办法实际公布的年月日，不是说从公布日起执行。','条文说明自公布之日起执行，但没有给出具体公布日期。','不把手册版本日或旧文件年份当实际公布日。'),
 (43,[13,14],'ANSWERED','选退补改选按什么通知办？未正式退改选又不参加学习考核时成绩如何登记？','按每学期课程选课工作通知操作；满足第14条完整前提时记0分。','两个子问均须完成，并保留第14条条件。'),
 (44,[16,17],'ANSWERED','最终成绩的组成是什么，谁负责按规定时间提交学生成绩？','平时与期末综合评定；任课教师按规定时间和要求提交。','两项均回答，不补权重或具体日期。'),
 (45,[18],'NEED_CLARIFICATION','我选的课程考试不及格，之后补考还是重修？未说明课程类别。','应澄清公选课或专业选修课等影响分支的课程类别。','不得任选类别作个人结论；续答资格仍受证据限制。'),
 (46,[18],'PARTIAL_ANSWER','我的公共选修课不及格，按手册该补考还是重修？顺便告诉我宿舍换房流程。','回答公选课相应不安排补考/重新选修规则，并明确宿舍流程缺少依据。','已明确公共选修不应重复追问类别，不得编造宿舍流程。'),
 (47,[2],'INSUFFICIENT_EVIDENCE','我是研究生，这份办法的本科生选课规则可以直接作为我的规定吗？','该条适用对象是普通本科生，不能直接扩展到研究生。','对象不覆盖时不作肯定适用结论。'),
 (48,[8],'PARTIAL_ANSWER','仅根据2025手册，没有我的培养方案和修读记录，能确认我已达到毕业学分要求吗？一般依据是什么？','一般依据培养方案、规定学习年限和规定学分；缺个人方案与记录，不能确认个人毕业资格。','一般规则与个人资格必须分开，不虚构个人修读记录。'),
]

def main():
    BASE.mkdir(parents=True,exist_ok=True)
    sources={c['article']:c for p in ['docs/product/rule-check-2025-verbatim-import-draft.json','docs/product/rule-check-2025-report-source-supplement.json'] for c in load(p)['chunks']}
    cases=[]
    for number,articles,status,question,facts,boundary in rows:
        evidence=[]
        for article in articles:
            c=sources[article]
            evidence.append({k:c[k] for k in ['article','locatorId','physicalPage','printedPage','contentChecksum','span']})
        cases.append({'caseId':f'N{number:02}','question':'仅依据2025版手册历史文本：'+question,'expectedStatus':status,'requiredFacts':[facts],
                      'criticalBoundary':boundary,'requiredEvidence':evidence,'necessaryClarifications':['courseType'] if number==45 else [],
                      'unknownItems':[boundary] if status in ['PARTIAL_ANSWER','INSUFFICIENT_EVIDENCE','INSUFFICIENT_EVIDENCE_OR_PARTIAL_PENDING_D02'] else [],
                      'referenceStatus':'DRAFT_PENDING_HUMAN','referenceRevision':'2025.9.2'})
    assert len(cases)==24 and len({c['question'] for c in cases})==24
    doc={'schemaVersion':1,'author':'CODEX_AI_KNOWING_PRIOR_FAILURES','blind':False,'representativeStudentTraffic':False,'providerCalls':0,'cases':cases}
    save('confirmation-cases.json',doc)
    save('confirmation-cases-lock.json',{'status':'QUESTIONS_LOCKED_REFERENCES_PENDING_HUMAN','sha256':sha((BASE/'confirmation-cases.json').read_bytes()),'providerCalls':0})
    old=load('docs/product/rule-check-2025-human-reference-report.json')['cases']
    save('forty-reference-draft.json',{'status':'DRAFT_PENDING_HUMAN','oldReferencesUnmodified':True,'cases':old+cases})
    text=['# 最终40题：输出前人工口径核对','', '旧16题原始问题、参考和历史评分保留。新24题由AI编制且已知旧失败，尚未人工确认。新模型调用0。',
          '', '## D01：R05边界','', '建议确认：恰好30%不满足“超过30%”，不能据该条认定不得考核；同时不能保证个人其他考试资格。旧R05争议记录保留。',
          '', '## D02：缺证与部分回答的状态','', 'R08（权重）、R09（具体截止日）的原参考状态为INSUFFICIENT_EVIDENCE。原评分不回改。建议新合同允许INSUFFICIENT_EVIDENCE或PARTIAL_ANSWER，但必须明确主问题无法确定；背景事实不能冒充比例/日期，不能标成完整完成，部分回答必须通过E07检查。',
          '', '这是输出前的行为口径修订，已有输出和失败已被作者看到；需要直接人工确认，不能称盲定标准。门槛和旧失败所要求的信息完整性不变。N36/N38/N42同类缺证同步采用确认后的口径。若保留旧严格状态，则这些题只接受INSUFFICIENT_EVIDENCE。',
          '', '## D03：新增24题参考','', '|ID|问题|预期行为|必要回答|禁止结论|原文|','|---|---|---|---|---|---|']
    for c in cases:
        refs='；'.join(f"第{e['article']}条，PDF物理{e['physicalPage']}页/印刷{e['printedPage']}页" for e in c['requiredEvidence'])
        text.append('|'+ '|'.join([c['caseId'],c['question'],c['expectedStatus'],c['requiredFacts'][0],c['criticalBoundary'],refs])+'|')
    text+=['','## 原文摘录（已审核资格保持不变）','']
    for article in sorted({a for r in rows for a in r[1]}):text+= [f'### 第{article}条', '',sources[article]['content'],'']
    text+=['## 旧16题（原参考保持原样）','','|ID|问题|原状态|必要回答|','|---|---|---|---|']
    for c in old:text.append('|'+ '|'.join([c['caseId'],c['question'],c['expectedStatus'],'；'.join(c['requiredFacts'])])+'|')
    (BASE/'reference-review.md').write_text('\n'.join(text)+'\n',encoding='utf-8')
    save('scoring-contract-draft.json',{'status':'PENDING_D01_D02_D03','oldPassMinimum':15,'oldDenominator':16,'preserveOriginalPass':['R01','R04','R10','R12','R13','R14','R16'],
       'originalFailuresMustComplete':['R02','R03','R06','R07','R08','R09','R11','R15'],'R05':'PENDING_D01','newPassMinimum':23,'newDenominator':24,'criticalErrorsMaximum':0,
       'unknownCountsAsPass':False,'criteria':['任务完成','事实有依据','对象/时间/个人范围正确','条件与例外完整','引用可核验','部分回答E07明确缺口'],
       'D02':'PENDING_HUMAN_BEFORE_NEW_OUTPUTS','historicalGradesRewritten':False})
    print(json.dumps({'old':16,'new':24,'total':40,'humanConfirmation':'PENDING','providerCalls':0}))

if __name__=='__main__':main()
