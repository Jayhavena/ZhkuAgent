package com.zhku.knowledge.rulecheck;

import java.util.*;
import java.text.Normalizer;
import java.util.regex.Pattern;

/** 独立的有界问题主题核对；合法选题ID和原文引用都不能代替这个检查。 */
public final class RuleCheckTopicGuard {
    public enum Topic { OBLIGATION,CATEGORY,HOURS,ENROLLMENT,PLAN_BASIS,NETWORK_PROPORTION,
        GRADUATION_CREDITS,ONLINE_SELECTION,UNENROLLED_EXAM,SEMESTER_CREDITS,REPEAT_RESTRICTION,
        SCHEDULE,DEFERRED_EXAM,RETAKE,TEACHING_DUTY,QUALITY_REQUIREMENTS,COURSE_CONSTRUCTION,
        SELECTION_CHANGES,SELECTED_ABSENCE,ATTENDANCE,GRADE_COMPOSITION,GRADE_SUBMISSION,
        CREDIT_CONVERSION,EFFECTIVE_INTERPRETATION }
    public record Coverage(Set<Topic> topics,boolean enabled) {
        public Coverage {topics=Set.copyOf(topics);if(enabled&&topics.isEmpty())throw new IllegalArgumentException("启用主题核对须声明覆盖");}
        public static Coverage unspecified(){return new Coverage(Set.of(),false);}
        public static Coverage declared(Topic... topics){return new Coverage(Set.of(topics),true);}
    }
    public record Assessment(Set<Topic> requestedTopics,boolean evaluated,List<String> blockingReasons) {
        public Assessment {requestedTopics=Set.copyOf(requestedTopics);blockingReasons=List.copyOf(blockingReasons);}
        public boolean blocked(){return !blockingReasons.isEmpty();}
    }
    public Assessment assess(String question,Coverage coverage) {
        Objects.requireNonNull(coverage);
        // 旧目录未声明覆盖时只保留兼容，不把它描述成已通过主题核对。
        if(!coverage.enabled())return new Assessment(Set.of(),false,List.of());
        if(question==null||question.isBlank()||question.length()>2048)
            return new Assessment(Set.of(),true,List.of("问题主题尚不明确，请补充需要核对的规则"));
        String q=RuleCheckApplicabilityGuard.activeDemandText(Normalizer.normalize(question,Normalizer.Form.NFKC).replaceAll("\\s+",""),new ArrayList<>());
        // 已识别的校园非教务需求不能被同句的公选关键词掩盖；先处理排除/例句再检查。
        // 这是有限范围保护，不代表已经识别所有未知主题；目录扩展前须新增独立反例。
        boolean outsideScope=has(q,"医保","报销","奖学金","宿舍","住宿费","食堂","餐厅");
        var requested=EnumSet.noneOf(Topic.class);
        // 缺课比例的分母不等于一般授课时长；同句明确比较学时上限时仍保留两个需求。
        if(has(q,"学时","课时","授课时长")&&(!has(q,"考勤","缺课","请假")
                ||has(q,"学时上限","授课","课时","通常安排")))requested.add(Topic.HOURS);
        if(has(q,"人数","多少人","开班","不予开课")||Pattern.compile("\\d+人").matcher(q).find())requested.add(Topic.ENROLLMENT);
        if(has(q,"缓考"))requested.add(Topic.DEFERRED_EXAM);
        if(has(q,"重修"))requested.add(Topic.RETAKE);
        // 只移除完整的“类别未说明”子句或作为判断参数的类别短语；其他类别请求不依赖重修是否出现。
        var categoryClauses=new ArrayList<String>();
        for(String clause:q.split("；")) {
            if(clause.matches("(?:我)?(?:未说明|没有说明|没说|缺少)(?:课程)?类别"))continue;
            categoryClauses.add(clause.replaceAll("(?:按|根据|依据)(?:课程)?类别(?=解释|判断|核对|区分|确定)",""));
        }
        String categoryDemand=String.join("；",categoryClauses);
        if(has(categoryDemand,"类别","同一类","分类","有哪些种类","包含网络课","包括网络课","校内和网络"))requested.add(Topic.CATEGORY);
        if(has(q,"选修")&&(has(q,"学习义务","参加选修学习","参加选修课学习")||has(q,"必须","应当")&&has(q,"学习","参加")))requested.add(Topic.OBLIGATION);
        if((has(q,"选修要求")&&has(q,"先看","资料","依据"))||(has(q,"培养方案")&&has(q,"重要依据","选课依据","参考什么","先看")))requested.add(Topic.PLAN_BASIS);
        if(has(q,"网络","线上")&&has(q,"比例","一半","分母","占比"))requested.add(Topic.NETWORK_PROPORTION);
        if(has(q,"毕业")&&has(q,"学分","还缺","还差","修满"))requested.add(Topic.GRADUATION_CREDITS);
        if(has(q,"开课计划","网上选课","选课方式")||(has(q,"选课")&&has(q,"怎样","如何完成"))
                ||(has(q,"公选","选课")&&has(q,"入口","哪天","何时","日期","什么时候")))requested.add(Topic.ONLINE_SELECTION);
        if(has(q,"未经选课","没有正式选课","未选课")&&has(q,"考试","考核","成绩"))requested.add(Topic.UNENROLLED_EXAM);
        if(has(q,"学分")&&has(q,"每学期","一学期","单学期","已选","再选"))requested.add(Topic.SEMESTER_CREDITS);
        if(has(q,"相近","相同","重复修读","重复选")||Pattern.compile("已(?:经)?(?:考核)?合格").matcher(q).find())requested.add(Topic.REPEAT_RESTRICTION);
        if(has(q,"排课","课表","排课顺序","安排顺序"))requested.add(Topic.SCHEDULE);
        // 新增覆盖从已审条款推导；只识别一般规则，不替学生认定请假、学分折算或现行效力。
        if(has(q,"教学任务","教学责任"))requested.add(Topic.TEACHING_DUTY);
        if(has(q,"教学质量","质量标准","教学管理要求"))requested.add(Topic.QUALITY_REQUIREMENTS);
        if(has(q,"课程建设","建设情况","定期检查"))requested.add(Topic.COURSE_CONSTRUCTION);
        // 退选条件可能和“不参加”分处两个子句；0分/第14条及未正式退选是条件咨询，不能误当办理通知。
        boolean absence=has(q,"不参加","未参加","不去上课","不去考试")&&has(q,"已选","选课结束","退改选","退/改选","退选","改选")
            ||has(q,"0分","记0","第14条")&&has(q,"退选","退改选","退/改选","改选")
            ||has(q,"未正式退选","未正式退改选")
            ||has(q,"选课结束")&&has(q,"没正式退","未正式退");
        if(absence)requested.add(Topic.SELECTED_ABSENCE);
        if(has(q,"补选","选课工作通知")||!absence&&has(q,"退选","改选"))requested.add(Topic.SELECTION_CHANGES);
        if(has(q,"考勤","缺课","请假"))requested.add(Topic.ATTENDANCE);
        if(has(q,"平时","期末")&&has(q,"综合","比例","权重","构成","评定","百分比","各占","%")
                ||has(q,"成绩")&&has(q,"组成","构成"))requested.add(Topic.GRADE_COMPOSITION);
        if(has(q,"提交成绩","成绩提交","报送成绩","提交学生成绩")
                ||q.matches("(?s).*(?:教师|老师).*(?:提交|报送).*成绩.*"))requested.add(Topic.GRADE_SUBMISSION);
        if(has(q,"折算","计划外","社会实践","创新创业","校际交流"))requested.add(Topic.CREDIT_CONVERSION);
        if(has(q,"解释部门","解释权","谁解释","废止","生效","公布之日","何时执行")
                ||has(q,"公布","发布")&&has(q,"日期","年月日","哪天","哪一天","何时"))requested.add(Topic.EFFECTIVE_INTERPRETATION);
        // 旧单规则入口仍阻断混合需求；保留已识别主题供新路径独立答复，不能把未知需求吞掉。
        if(outsideScope)return new Assessment(requested,true,List.of("问题包含当前公共选修资料未覆盖的需求，请分开提问或补充对应依据"));
        // 有明确的混合需求不能只凭其中一项覆盖就全答；未知也不能默认对应所选目录。
        if(requested.isEmpty())return new Assessment(requested,true,List.of("尚不能独立确认问题主题与所选规则匹配，请明确需要查证的规则"));
        if(!coverage.topics().containsAll(requested))return new Assessment(requested,true,List.of("所选规则未覆盖问题的全部主题，不能以正确引用代替所问结论"));
        return new Assessment(requested,true,List.of());
    }
    private static boolean has(String q,String...markers){return Arrays.stream(markers).anyMatch(q::contains);}
}
