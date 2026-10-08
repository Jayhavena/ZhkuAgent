package com.zhku.agent.agent;

import com.zhku.agent.agent.runtime.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class BaseAgentStepLimitTest {
    @Test
    void exhaustedStepsAreIncompleteInsteadOfSuccess() {
        var agent = new BaseAgent(Runnable::run) {
            AgentRunContext captured;
            {
                setMaxSteps(2);
            }

            protected String step(AgentRunContext context) {
                captured = context;
                return "继续";
            }
        };
        assertThat(agent.run("未完成的任务")).contains("Reached max steps (2)");
        assertThat(agent.captured.state().name()).isEqualTo("INCOMPLETE");
        assertThat(agent.captured.currentStep()).isEqualTo(2);
        assertThat(agent.captured.termination().isDone()).isTrue();
    }
}
