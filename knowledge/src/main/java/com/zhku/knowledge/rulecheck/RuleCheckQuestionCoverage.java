package com.zhku.knowledge.rulecheck;

import java.util.*;
import static com.zhku.knowledge.rulecheck.RuleCheckTaskPlan.Action.*;

/** 有界覆盖兜底，只解释目录已声明的主题；未知表达仍交给计划或明确缺证。 */
final class RuleCheckQuestionCoverage {
    record Need(String question,Set<RuleCheckTopicGuard.Topic> topics,boolean outsideScope) {}
    private static final RuleCheckTopicGuard GUARD=new RuleCheckTopicGuard();
    private static final RuleCheckTopicGuard.Coverage ALL=new RuleCheckTopicGuard.Coverage(EnumSet.allOf(RuleCheckTopicGuard.Topic.class),true);
    static List<Need> needs(String question) {
        var result=new ArrayList<Need>();
        // 最多四个显式分句；不把“和”一律拆开，以免破坏同一规则的条件、分母或否定。
        String active=RuleCheckApplicabilityGuard.activeDemandText(java.text.Normalizer.normalize(question,java.text.Normalizer.Form.NFKC).replaceAll("\\s+",""),new ArrayList<>());
        var whole=need(active);
        // 单主题中的分句常是条件和结果，不是独立任务；拆开会把“已退选”错误降为办理通知。
        if(whole.topics().equals(Set.of(RuleCheckTopicGuard.Topic.SELECTED_ABSENCE))&&!whole.outsideScope()
                &&!active.contains("通知")&&!active.contains("补选")&&!active.contains("选退补改选"))return List.of(whole);
        String[] parts=active.split("[，,；;。！？?!]|(?:顺便|另外请|以及请)");
        if(parts.length>4)return List.of(need(question));
        for(String part:parts)if(!part.isBlank())result.add(need(part));
        return List.copyOf(result);
    }
    private static Need need(String question) {
        var result=GUARD.assess(question,ALL);
        return new Need(question,result.requestedTopics(),result.blockingReasons().stream().anyMatch(s->s.startsWith("问题包含")));
    }
    static Set<RuleCheckTopicGuard.Topic> requested(List<Need> needs) {
        var topics=EnumSet.noneOf(RuleCheckTopicGuard.Topic.class);needs.forEach(n->topics.addAll(n.topics()));return topics;
    }
    static List<String> queries(String question,List<Need> needs,boolean declared) {
        if(!declared)return List.of(question);
        var queries=needs.stream().filter(n->!n.topics().isEmpty()).map(Need::question).distinct().toList();
        return queries.isEmpty()?List.of(question):queries;
    }
    static boolean relevant(RuleCheckApplicationService.CatalogEntry entry,Set<RuleCheckTopicGuard.Topic> topics) {
        return !entry.topicCoverage().enabled()||topics.isEmpty()||!Collections.disjoint(entry.topicCoverage().topics(),topics);
    }
    static RuleCheckTaskPlan complete(String question,RuleCheckTaskPlan proposed,List<RuleCheckApplicationService.CatalogEntry> available) {
        // 未声明主题的旧目录维持原协议拒绝项语义，不能借新兜底默默抹掉未知任务。
        if(available.stream().noneMatch(e->e.topicCoverage().enabled()))return proposed;
        var wanted=requested(needs(question));
        var rejected=proposed==null?List.<RuleCheckTaskPlan.Task>of():proposed.rejectedTasks();
        var tasks=new LinkedHashSet<RuleCheckTaskPlan.Task>();
        if(proposed!=null)for(var task:proposed.tasks()) {
            if(task.action()==UNSUPPORTED){tasks.add(task);continue;}
            var entry=available.stream().filter(e->e.template().ruleId().equals(task.ruleId())).findFirst().orElse(null);
            if(entry==null||!relevant(entry,wanted)||!RuleCheckClauseOperations.accepts(task,RuleCheckClauseOperations.option(entry)))continue;
            tasks.add(normalizeDetail(question,task,entry));
        }
        // 只用本轮捕获且已审核的目录补齐独立主题，不从评分参考或未捕获原文补答案。
        for(var topic:wanted) {
            boolean covered=tasks.stream().anyMatch(t->available.stream().anyMatch(e->e.template().ruleId().equals(t.ruleId())&&e.topicCoverage().topics().contains(topic)));
            if(covered)continue;
            var entry=available.stream().filter(e->e.topicCoverage().enabled()&&e.topicCoverage().topics().contains(topic))
                .min(Comparator.comparingInt((RuleCheckApplicationService.CatalogEntry e)->e.topicCoverage().topics().size())
                    .thenComparingInt(e->e.template().evidenceGaps().size()).thenComparing(e->e.template().ruleId())).orElse(null);
            if(entry!=null&&tasks.size()+rejected.size()<4) {
                var replacement=normalizeDetail(question,new RuleCheckTaskPlan.Task(entry.template().ruleId(),EXPLAIN_RULE,"",""),entry);
                if(!rejected.contains(replacement))tasks.add(replacement);
            }
        }
        if(tasks.isEmpty()||tasks.size()>4)return null;
        // 补齐覆盖不等于已处理被拒绝的请求；保留拒绝项及总预算，才能向用户披露部分完成。
        return new RuleCheckTaskPlan(List.copyOf(tasks),rejected);
    }
    private static RuleCheckTaskPlan.Task normalizeDetail(String question,RuleCheckTaskPlan.Task task,RuleCheckApplicationService.CatalogEntry entry) {
        var option=RuleCheckClauseOperations.option(entry);
        if(option.actions().contains(EXCLUDE_CONDITION)&&option.operands().contains("FORMAL_WITHDRAWAL_COMPLETED")) {
            boolean stated=formalWithdrawalStated(question);
            // 模型的合法动作不证明“已退选”前提；反之，明确假设条件可解释本条排除，不能推断实际个人记录。
            if(task.action()==EXCLUDE_CONDITION&&!stated)return new RuleCheckTaskPlan.Task(task.ruleId(),EXPLAIN_RULE,"","");
            if(task.action()==EXPLAIN_RULE&&stated)return new RuleCheckTaskPlan.Task(task.ruleId(),EXCLUDE_CONDITION,"FORMAL_WITHDRAWAL_COMPLETED","");
        }
        if(task.action()==MISSING_DETAIL&&!asksDetail(question,task.operand()))
            return new RuleCheckTaskPlan.Task(task.ruleId(),EXPLAIN_RULE,"","");
        // 当期未知也不能吞掉用户实际询问的权重/具体日期；缺口从原文支持的操作数确定，不由模型猜。
        if(task.action()==EXPLAIN_RULE||task.action()==UNKNOWN_CURRENT)for(String operand:option.operands())
            if(option.actions().contains(MISSING_DETAIL)&&asksDetail(question,operand))return new RuleCheckTaskPlan.Task(task.ruleId(),MISSING_DETAIL,operand,"");
        return task;
    }
    private static boolean formalWithdrawalStated(String question) {
        String q=RuleCheckApplicabilityGuard.activeDemandText(question,new ArrayList<>()).replaceAll("\\s+","");
        return q.matches("(?s).*已(?:经)?正式(?:办(?:理|了))?退选.*")
            // 明确否定或询问前提时，不能把嵌入的“已正式退选”当作成立事实。
            &&!q.matches("(?s).*(?:是否已|有没有|不确定|不知道|未说明|没说明|并非已|不是已|并没有|没有已).*正式.*退选.*");
    }
    static boolean asksDetail(String question,String operand) {
        String q=RuleCheckApplicabilityGuard.activeDemandText(java.text.Normalizer.normalize(question,java.text.Normalizer.Form.NFKC),new ArrayList<>()).replaceAll("\\s+","");
        return switch(operand) {
            case "GRADE_WEIGHTS" -> q.matches("(?s).*(?:比例|权重|占比|各占|百分比|百分之).*")
                ||q.contains("%")&&(q.contains("平时")||q.contains("期末"));
            case "CURRENT_DEADLINE" -> q.matches("(?s).*(?:截止|最晚|最后(?:期限|日期)|哪(?:一)?(?:天|日)|几(?:号|日)|具体(?:日期|时间)|什么时候|何时).*");
            case "PUBLICATION_DATE" -> q.matches("(?s).*(?:公布|发布|执行|生效).*(?:哪(?:一)?(?:天|日|年|月)|几(?:号|日|月)|日期|年月日|什么时候|何时).*")
                ||q.matches("(?s).*(?:何时|什么时候).*(?:公布|发布|执行|生效).*");
            default -> false;
        };
    }
    static List<String> gaps(String question,List<Need> needs,Collection<RuleCheckApplicationService.CatalogEntry> selected) {
        var result=new LinkedHashSet<String>();
        for(var need:needs) {
            if(need.outsideScope())result.add("关于“"+need.question()+"”缺少对应的已审核依据，不能作出结论");
            for(var topic:need.topics())if(selected.stream().noneMatch(e->e.topicCoverage().topics().contains(topic)))
                result.add("关于“"+need.question()+"”的子问题尚未取得可用回答，不能把其他条文当作已经完成");
        }
        // 当前目录声明缓考和重修，未声明补考；不能把两个不同业务动作混用。
        String active=RuleCheckApplicabilityGuard.activeDemandText(question,new ArrayList<>());
        if(active.contains("补考"))result.add("所问补考安排没有对应的已审核依据；缓考规则不能代替补考规定");
        return List.copyOf(result);
    }
}
