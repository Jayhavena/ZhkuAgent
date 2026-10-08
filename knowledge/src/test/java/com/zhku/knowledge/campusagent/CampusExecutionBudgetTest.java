package com.zhku.knowledge.campusagent;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class CampusExecutionBudgetTest {
    @Test void firstThreeAttemptsConsumeBudgetEvenWhenNoResponseWasConfirmed() {
        var b=new CampusExecutionBudget(Duration.ofSeconds(30));
        for(int i=0;i<3;i++)b.reserveModelCall("合成问题");
        assertThat(b.attemptedCalls()).isEqualTo(3);failure(()->b.reserveModelCall("合成问题"),"MODEL_CALL_BUDGET_EXCEEDED");
        assertThat(b.attemptedCalls()).isEqualTo(3);
    }
    @Test void inputLimitIsBeforeExternalAttemptAndBudgetsArePerRun() {
        var a=new CampusExecutionBudget(Duration.ofSeconds(30));var b=new CampusExecutionBudget(Duration.ofSeconds(30));
        failure(()->a.reserveModelCall("x".repeat(12001)),"MODEL_INPUT_LIMIT");assertThat(a.attemptedCalls()).isZero();
        a.reserveModelCall("x".repeat(12000));b.reserveModelCall("问题");assertThat(a.attemptedCalls()).isEqualTo(1);assertThat(b.attemptedCalls()).isEqualTo(1);
    }
    @Test void exactDeadlineAndCancellationPreventFurtherCallsWithoutRefund() {
        var now=new AtomicLong(100);var stopped=new AtomicBoolean();
        var b=new CampusExecutionBudget(100,Duration.ofSeconds(30),now::get,stopped::get);b.reserveModelCall("问题");
        now.set(100+TimeUnit.SECONDS.toNanos(30));failure(b::checkDeadline,"RUN_TIMEOUT");failure(()->b.reserveModelCall("问题"),"RUN_TIMEOUT");
        stopped.set(true);failure(()->b.reserveModelCall("问题"),"CANCELLED");assertThat(b.attemptedCalls()).isEqualTo(1);
    }
    @Test void concurrentReservationsStillAllowOnlyThreeAttempts()throws Exception {
        var b=new CampusExecutionBudget(Duration.ofSeconds(30));var success=new AtomicInteger();
        try(var callers=Executors.newFixedThreadPool(8)) {
            var go=new CountDownLatch(1);var tasks=new ArrayList<Future<?>>();
            for(int i=0;i<16;i++)tasks.add(callers.submit(()->{go.await();try{b.reserveModelCall("问题");success.incrementAndGet();}
                catch(CampusExecutionBudget.Failure e){assertThat(e.code()).isEqualTo("MODEL_CALL_BUDGET_EXCEEDED");}return null;}));
            go.countDown();for(var f:tasks)f.get(2,TimeUnit.SECONDS);
        }assertThat(success).hasValue(3);assertThat(b.attemptedCalls()).isEqualTo(3);
    }
    @Test void deadlineAfterPolicyFailureStillHasRealTimeoutRatherThanRetrievalFailure() {
        var now=new AtomicLong();var context=com.zhku.agent.agent.runtime.AgentRunContext.create("合成问题");
        var budget=new CampusExecutionBudget(0,Duration.ofSeconds(30),now::get,context::isStopRequested);
        org.springframework.ai.chat.model.ChatModel model=p->new org.springframework.ai.chat.model.ChatResponse(List.of(
                new org.springframework.ai.chat.model.Generation(new org.springframework.ai.chat.messages.AssistantMessage("",Map.of(),List.of(
                    new org.springframework.ai.chat.messages.AssistantMessage.ToolCall("local","function","lookup_public_elective_credit_limit","{}"))))));
        var service=new CampusCreditAgentService(model,new com.zhku.knowledge.tools.CampusToolRegistry(List.of(new com.zhku.knowledge.tools.CourseCreditSummaryTool())),q->{
            now.set(TimeUnit.SECONDS.toNanos(30));throw new IllegalStateException("synthetic policy failure");},3);
        var result=service.run(context,new CampusCreditAgentService.Request("合成问题",List.of()),budget);context.publishTermination();
        assertThat(result.state()).isEqualTo(com.zhku.agent.agent.runtime.AgentRunState.CANCELLED);
        assertThat(result.stopReason()).isEqualTo(com.zhku.agent.agent.runtime.AgentStopReason.RUN_TIMEOUT);
        assertThat(budget.attemptedCalls()).isEqualTo(1);
    }
    @Test void cancelledContextCannotStartPolicyEvenWhenBudgetWasCreatedIndependently() {
        var context=com.zhku.agent.agent.runtime.AgentRunContext.create("合成问题");var policies=new AtomicInteger();
        org.springframework.ai.chat.model.ChatModel model=p->{context.requestStop(com.zhku.agent.agent.runtime.AgentStopReason.USER_REQUEST);
            return new org.springframework.ai.chat.model.ChatResponse(List.of(new org.springframework.ai.chat.model.Generation(
                new org.springframework.ai.chat.messages.AssistantMessage("",Map.of(),List.of(new org.springframework.ai.chat.messages.AssistantMessage.ToolCall(
                    "local","function","lookup_public_elective_credit_limit","{}"))))));};
        var service=new CampusCreditAgentService(model,new com.zhku.knowledge.tools.CampusToolRegistry(List.of(new com.zhku.knowledge.tools.CourseCreditSummaryTool())),q->{policies.incrementAndGet();return null;},3);
        var budget=new CampusExecutionBudget(Duration.ofSeconds(30));
        var result=service.run(context,new CampusCreditAgentService.Request("合成问题",List.of()),budget);context.publishTermination();
        assertThat(result.stopReason()).isEqualTo(com.zhku.agent.agent.runtime.AgentStopReason.USER_REQUEST);assertThat(policies).hasValue(0);
    }
    private static void failure(Runnable action,String code) {
        assertThatThrownBy(action::run).isInstanceOf(CampusExecutionBudget.Failure.class)
                .extracting(x->((CampusExecutionBudget.Failure)x).code()).isEqualTo(code);
    }
}
