package com.zhku.agent.agent;

import com.zhku.agent.agent.runtime.*;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import com.zhku.agent.agent.model.*;
import java.io.IOException;
import java.util.ArrayList;
import static org.assertj.core.api.Assertions.*;

class BaseAgentFailureOutputTest {
    private static class CaptureEmitter extends SseEmitter {
        ArrayList<Object> sent = new ArrayList<>();
        Throwable failure;
        int attempts;
        boolean failSend;

        public void send(Object value) throws IOException {
            attempts++;
            if (failSend)
                throw new IOException("SECRET-SEND");
            sent.add(value);
        }

        public void completeWithError(Throwable error) {
            failure = error;
        }
    }

    private static class FailedAgent extends BaseAgent {
        final CaptureEmitter emitter;
        AgentRunContext captured;

        FailedAgent(CaptureEmitter emitter) {
            super(Runnable::run);
            this.emitter = emitter;
        }

        protected SseEmitter createStreamEmitter() {
            return emitter;
        }

        protected String step(AgentRunContext context) {
            captured = context;
            throw new AgentExecutionFailure(AgentExecutionFailure.Category.MODEL_FAILURE);
        }
    }

    @Test
    void failedRunSendsOneSanitizedCategoryAndTerminates() {
        var emitter = new CaptureEmitter();
        var agent = new FailedAgent(emitter);
        agent.runStream("任务");
        assertThat(emitter.sent).containsExactly("Error: MODEL_FAILURE");
        assertThat(emitter.failure).hasMessage("MODEL_FAILURE").hasNoCause();
        assertThat(agent.captured.state()).isEqualTo(AgentRunState.FAILED);
        assertThat(agent.captured.termination().isDone()).isTrue();
    }

    @Test
    void failedErrorDeliveryIsNotRetriedRecursively() {
        var emitter = new CaptureEmitter();
        emitter.failSend = true;
        var agent = new FailedAgent(emitter);
        agent.runStream("任务");
        assertThat(emitter.attempts).isEqualTo(1);
        assertThat(emitter.failure).hasMessage("MODEL_FAILURE").hasNoCause();
        assertThat(agent.captured.termination().isDone()).isTrue();
    }
}
