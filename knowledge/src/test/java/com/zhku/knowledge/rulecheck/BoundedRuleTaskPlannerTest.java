package com.zhku.knowledge.rulecheck;

import org.junit.jupiter.api.Test;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.assertj.core.api.Assertions.*;

class BoundedRuleTaskPlannerTest {
    private final List<RuleCheckTaskPlan.Option> options=List.of(new RuleCheckTaskPlan.Option("A15/GENERAL","考勤规则",List.of("合成的已核验原文"),
        List.of(RuleCheckTaskPlan.Action.EXPLAIN_RULE,RuleCheckTaskPlan.Action.COMPARE_THRESHOLD),List.of("ABSENCE_PERCENT")));
    private final String response="{\"tasks\":[{\"ruleId\":\"A15/GENERAL\",\"action\":\"EXPLAIN_RULE\",\"operand\":\"\",\"value\":\"\"}]}";
    @Test void sendsVerifiedEvidenceAndOnlyReturnsTasks() {
        var seen=new AtomicReference<BoundedRuleSelector.Request>();
        try(var planner=new BoundedRuleTaskPlanner(request->{seen.set(request);return response;},Duration.ofSeconds(1),1)) {
            var result=planner.plan("自然问题",options);
            assertThat(result.tasks()).hasSize(1);assertThat(seen.get().userPayload()).contains("合成的已核验原文").contains("evidenceOptions");
            assertThat(planner.attempts()).isEqualTo(1);
            assertThat(planner.plan("自然问题",options)).isNull();assertThat(planner.attempts()).isEqualTo(1);
        }
    }
    @Test void malformedUnknownDuplicateAndFreeFactResponsesAreRejected() {
        for(String invalid:List.of(response+"{}",response.replace("A15/GENERAL","NOT_IN_EVIDENCE"),response.replace("EXPLAIN_RULE","INVENT_FACT"),
                response.replace("\"value\":\"\"","\"value\":\"\",\"answer\":\"个人可以毕业\""),
                response.replace("\"operand\":\"\"","\"operand\":\"\",\"operand\":\"\""),"{\"tasks\":[]}")) {
            try(var planner=new BoundedRuleTaskPlanner(r->invalid,Duration.ofSeconds(1),1)) {assertThat(planner.plan("问题",options)).isNull();}
        }
    }
    @Test void undeclaredOperandAndThresholdOutsidePercentageRangeAreRejected() {
        for(String operand:List.of("PERSONAL_ELIGIBILITY","ABSENCE_PERCENT")) {
            String value=operand.equals("ABSENCE_PERCENT")?"101":"30";
            String invalid="{\"tasks\":[{\"ruleId\":\"A15/GENERAL\",\"action\":\"COMPARE_THRESHOLD\",\"operand\":\""+operand+"\",\"value\":\""+value+"\"}]}";
            try(var planner=new BoundedRuleTaskPlanner(r->invalid,Duration.ofSeconds(1),1)) {assertThat(planner.plan("问题",options)).isNull();}
        }
    }
    @Test void undeclaredExtraTaskCannotSwallowIndependentlyValidExplanation() {
        String mixed=response.replace("]}",",{\"ruleId\":\"A15/GENERAL\",\"action\":\"MISSING_DETAIL\",\"operand\":\"AUTOMATIC_PROCESSING\",\"value\":\"\"}]}");
        try(var planner=new BoundedRuleTaskPlanner(r->mixed,Duration.ofSeconds(1),1)) {
            var plan=planner.plan("解释已核验规则",options);
            assertThat(plan).isNotNull();
            assertThat(plan.tasks()).extracting(RuleCheckTaskPlan.Task::action).containsExactly(RuleCheckTaskPlan.Action.EXPLAIN_RULE);
            assertThat(plan.rejectedTasks()).extracting(RuleCheckTaskPlan.Task::operand).containsExactly("AUTOMATIC_PROCESSING");
        }
    }
    @Test void invalidStructureCannotBeSalvagedByAddingOneValidTask() {
        for(String extra:List.of(
            "{\"ruleId\":\"A15/GENERAL\",\"action\":\"EXPLAIN_RULE\",\"operand\":\"\",\"value\":\"\",\"answer\":\"个人保证可以考试\"}",
            "{\"ruleId\":\"A15/GENERAL\",\"action\":\"INVENT_FACT\",\"operand\":\"\",\"value\":\"\"}",
            "{\"ruleId\":\"A15/GENERAL\",\"action\":\"EXPLAIN_RULE\",\"operand\":\"\",\"operand\":\"\",\"value\":\"\"}")) {
            var mixed=response.replace("]}",","+extra+"]}");
            assertThat(BoundedRuleTaskPlanner.parse(mixed,options)).isNull();
        }
    }
    @Test void timeoutConsumesAttemptWithoutAutomaticRetry() {
        try(var planner=new BoundedRuleTaskPlanner(r->{try{Thread.sleep(500);}catch(InterruptedException stop){Thread.currentThread().interrupt();}return response;},Duration.ofMillis(30),1)) {
            assertThat(planner.plan("问题",options)).isNull();assertThat(planner.attempts()).isEqualTo(1);
        }
    }
    @Test void closedAndInvalidInputNeverCallTransport() {
        var count=new java.util.concurrent.atomic.AtomicInteger();
        var planner=new BoundedRuleTaskPlanner(r->{count.incrementAndGet();return response;},Duration.ofSeconds(1),2);
        assertThat(planner.plan("",options)).isNull();planner.close();assertThat(planner.plan("问题",options)).isNull();assertThat(count).hasValue(0);
    }
}
