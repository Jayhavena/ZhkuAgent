package com.zhku.knowledge.rulecheck;

import java.text.Normalizer;
import java.util.*;
import java.util.regex.Pattern;

/** 有界中文信号保护；不评价主题相关性，也不能代替真实模型的独立语义验收。 */
public final class RuleCheckApplicabilityGuard {
    private static final Pattern YEAR=Pattern.compile("(?<!\\d)(20\\d{2})(?:年|版|[./-]\\d)");
    private static final Pattern QUOTED_EXAMPLE=Pattern.compile("(^|[，,；;。])[“\"]([^”\"]+)[”\"](?:只是例句|仅作举例)(?=$|[，,；;。！？!?])");
    private static final Pattern ALTERNATIVE_GENERAL_REQUEST=Pattern.compile("(?:只问|只解释|请解释|我想了解)[^；。！？!?]*(?:一般|全日制普通本科)");
    // 年级简称只是有界对象信号；NFKC后也识别研１，不能靠主题不明碰巧拒答来冒充对象核对。
    private static final String POSTGRADUATE_YEAR="(?:(?:研|硕)[一二三1-3]|博[一二三四五六1-6])(?![0-9])";
    // 不能从“调研一下/科研一般”的词内部抽出研一；只认完整年级称谓的明确上下文。
    private static final Pattern POSTGRADUATE_MENTION=Pattern.compile("研究生|硕士|博士|(?:^|(?<=[；我是在读问给和与上为名位个人])|(?<=解释)|(?<=比较)|(?<=核对)|(?<=了解)|(?<=说明))"
        +POSTGRADUATE_YEAR+"(?=$|；|学生|同学|年级|阶段|身份|的|选修|选课|判断|应|能|必须)");
    private static final Pattern OWN_IDENTITY_EXCLUSION=Pattern.compile("^(?:我|本人)(?:不是|并非|不属于)(?:一名|一个)?(?:研究生|硕士生?|博士生?|"+POSTGRADUATE_YEAR+"|本科生|本科学生)$");
    private static final Pattern INQUIRY_EXCLUSION=Pattern.compile("^(?:我)?(?:不问|不查询|不比较|不确认|不需要(?:确认|判断|查询)|不用(?:确认|判断)|不是(?:在)?问)(.+)$");
    // 只消除完整、可识别的排除目标；不使用任意后缀，否则无标点的后续请求会被一并吞掉。
    private static final Pattern EXCLUDED_TARGET=Pattern.compile("(?:研究生|硕士生?|博士生?|"+POSTGRADUATE_YEAR+"|本科生)(?:的)?(?:选修)?(?:要求|规则|规定)?"
        +"|(?:今天|现在|目前|本学期|当期)?(?:网上)?入口(?:是否开放|的网址|网址)?"
        +"|20\\d{2}年(?:规则)?|(?:个人|我的|本人)(?:资格|修读资格|毕业资格)"
        +"|(?:这门课|某门课|该课|某课)(?:是否)?(?:实际)?(?:开班|开课)"
        +"|(?:公共选修课的|公共选修|公选的|公选)?(?:学时上限|每学期学分上限|网络公选比例|选课依据)"
        +"|(?:学生)?医保(?:报销)?|奖学金(?:申请条件)?|宿舍|住宿费|食堂|餐厅");
    private static final Pattern PERSONAL_APPLICATION=Pattern.compile("(?:我|本人|该同学|这位同学|该学生|(?<!其)他|她)(?:还能|能否|可不可以|是否可以|可以|能不能|可否)(?:再|继续|直接)?(?:选|报|毕业|申请)");
    public enum Audience { UNDERGRADUATE, POSTGRADUATE, OTHER }
    public enum TimeDemand { SNAPSHOT_RULE, CURRENT, OTHER_PERIOD }
    /** 由服务端声明审核覆盖；空集合是未知，不能从范围文案或规则ID推断全覆盖。 */
    public record Coverage(Set<Audience> audiences) {
        public Coverage {audiences=Set.copyOf(audiences);}
        public static Coverage unspecified(){return new Coverage(Set.of());}
    }
    public record Assessment(Set<Audience> requestedAudiences,TimeDemand timeDemand,
            boolean personalRecordsRequired,boolean actualOfferingRequired,
            List<String> signals,List<String> blockingReasons,List<String> evidenceGaps) {
        public Assessment {
            requestedAudiences=Set.copyOf(requestedAudiences);Objects.requireNonNull(timeDemand);
            signals=List.copyOf(signals);blockingReasons=List.copyOf(blockingReasons);evidenceGaps=List.copyOf(evidenceGaps);
        }
        public boolean blocked(){return !blockingReasons.isEmpty();}
    }
    public Assessment assess(String question,Coverage coverage,String revision) {
        // 归一化只用于风险信号；原问题、原文、引用跨度及审计正文不改写。
        String q=Normalizer.normalize(question,Normalizer.Form.NFKC).replaceAll("\\s+","");
        var audiences=EnumSet.noneOf(Audience.class);var signals=new ArrayList<String>();
        q=activeDemandText(q,signals);
        if(POSTGRADUATE_MENTION.matcher(q).find()){audiences.add(Audience.POSTGRADUATE);signals.add("POSTGRADUATE_MENTION");}
        if(has(q,"本科")){audiences.add(Audience.UNDERGRADUATE);signals.add("UNDERGRADUATE_MENTION");}
        if(has(q,"专科","成人教育","非全日制","自考","留学生","交换生")){audiences.add(Audience.OTHER);signals.add("OTHER_AUDIENCE_MENTION");}
        var blocked=new ArrayList<String>();
        if(q.isBlank())blocked.add("排除说明之后没有明确的查证请求，请补充需要查询的一般规则");
        if(!coverage.audiences().containsAll(audiences))blocked.add("请求的培养对象未被本项已审核资料覆盖；不能将一般规则直接推广至该对象");
        boolean otherPeriod=false;var years=YEAR.matcher(q);
        String snapshotYear=revision.matches("20\\d{2}.*")?revision.substring(0,4):null;
        while(years.find())if(!years.group(1).equals(snapshotYear))otherPeriod=true;
        boolean current=has(q,"今天","现在","目前","今年","本学期","本年度","当期","最新","现行","明天","后天","实时","是否有效","还有效","是否废止","何时开放","什么时候开放","入口","网址");
        if(otherPeriod){signals.add("OTHER_PERIOD_REQUEST");blocked.add("问题涉及其他年份，当前引用版本不能证明该年份的规则或实际安排");}
        if(current)signals.add("CURRENT_REQUEST");
        boolean offering=has(q,"开班","开课","停开","开设")&&has(q,"这门","某门","该课","这课","某课","某课程","实际","一定","确认","现在","今天","明天");
        boolean personal=has(q,"还缺","还差","已选","已修","剩余额度","个人资格","毕业资格","修读资格","我的课表","我的成绩","本人成绩","我的情况","我这种情况","我的学分")
            ||PERSONAL_APPLICATION.matcher(q).find()
            // “确认我已达到毕业学分要求”也是个人资格判断；缺记录不能因没有“资格”二字而丢失。
            ||q.matches("(?s).*(?:确认|判断|证明)(?:我|本人)(?:已|已经|是否|能否)?(?:达到|满足|符合|修满).*毕业.*(?:学分|要求).*")
            ||(has(q,"我","本人","个人","该学生","某同学","这位同学","他的","她的","他能","她能")
                // “我想了解成绩规则”仍是一般规则咨询；成绩/课表名词本身不证明在请求个人结果。
                &&has(q,"资格","剩余","能毕业","毕业还","能选","可以选","获批"));
        var gaps=new ArrayList<String>();
        if(offering){signals.add("ACTUAL_OFFERING_REQUEST");gaps.add("只能解释已审核的一般开班规则；缺少该课程当期实际开班决定，不能确认实际结果");}
        if(personal){signals.add("PERSONAL_RECORD_REQUEST");gaps.add("只能解释已审核的一般规则；缺少经核验的个人修读记录、适用方案或资格结果，口述信息不能证明个人结论");}
        if(current){
            // 当前系统没有实时教务证据；一般阈值可独立展示，但不能回答入口或现行有效性。
            if(!offering&&!personal)blocked.add("缺少当期安排或现行有效性的已审核依据，历史文本不能证明当前状态");
            else gaps.add("引用仅说明所列版本的一般规则；当期安排及现行适用性仍未确认");
        }
        return new Assessment(audiences,otherPeriod?TimeDemand.OTHER_PERIOD:current?TimeDemand.CURRENT:TimeDemand.SNAPSHOT_RULE,
            personal,offering,signals,blocked,gaps);
    }
    /** 只追加缺口，不增写事实、个人条件或审核权限；独立性仍交由已审核模板与决策器约束。 */
    public RuleCheckRuleTemplate constrain(RuleCheckRuleTemplate template,Assessment assessment) {
        if(assessment.evidenceGaps().isEmpty())return template;
        var gaps=new LinkedHashSet<>(template.evidenceGaps());gaps.addAll(assessment.evidenceGaps());
        return new RuleCheckRuleTemplate(template.ruleId(),template.referenceRevision(),template.reviewState(),
            template.reviewer(),template.reviewedAt(),template.facts(),template.conditions(),List.copyOf(gaps),
            template.conflicts(),template.conditionalEvidenceGaps());
    }
    private static boolean has(String q,String...markers){return Arrays.stream(markers).anyMatch(q::contains);}
    static String activeDemandText(String q,List<String> signals) {
        var quoted=QUOTED_EXAMPLE.matcher(q);var withoutExamples=new StringBuilder();
        String remaining=QUOTED_EXAMPLE.matcher(q).replaceAll("$1");
        // “只是引用”不等于排除；例句也可能被回指要求回答。没有明确另起的一般请求时保守保留。
        boolean excludeExample=ALTERNATIVE_GENERAL_REQUEST.matcher(remaining).find()
            &&!has(remaining,"这句","该句","这段","该段","例句","例子","引述","引用","上面","上述","原句","前句","它");
        while(quoted.find()) {
            quoted.appendReplacement(withoutExamples,java.util.regex.Matcher.quoteReplacement(excludeExample?quoted.group(1):quoted.group()));
            if(excludeExample)signals.add("QUOTED_EXAMPLE_EXCLUDED");
        }
        quoted.appendTail(withoutExamples);
        // 引号必须明确标记为例句才能排除；比较和仍在请求的引述保留原风险。
        var active=new ArrayList<String>();
        for(String clause:withoutExamples.toString().split("[，,；;。！？!?]|但是|但|不过|而是|同时|另外|还请|也请|也想|也问|并且")) {
            if(clause.isBlank())continue;
            if(OWN_IDENTITY_EXCLUSION.matcher(clause).matches()){signals.add("NEGATED_OWN_IDENTITY");continue;}
            var exclusion=INQUIRY_EXCLUSION.matcher(clause);
            if(exclusion.matches()&&EXCLUDED_TARGET.matcher(exclusion.group(1)).matches()){signals.add("EXPLICIT_INQUIRY_EXCLUDED");continue;}
            active.add(clause);
        }
        // 不拼接相邻子句，避免跨边界偶然组合出人称/申请词；混合需求中的活动子句仍逐项可扫描。
        return String.join("；",active);
    }
}
