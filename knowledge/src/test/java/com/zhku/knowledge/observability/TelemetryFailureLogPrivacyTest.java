package com.zhku.knowledge.observability;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.zhku.knowledge.conversation.*;
import com.zhku.knowledge.conversation.elective.ElectiveRetakeConversationService;
import com.zhku.knowledge.conversation.publicelective.*;
import com.zhku.knowledge.telemetry.TelemetryRecorder;
import com.zhku.knowledge.web.*;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.*;

/** 用内存会话和故障旁路验证日志边界，不创建数据库或调用模型。 */
class TelemetryFailureLogPrivacyTest {
    static final String RAW = "DEVELOPMENT_SYNTHETIC_TELEMETRY_FAILURE_SECRET";
    @Test void recorderFailureLogsOnlyFixedCodeAndKeepsAnswerAndCancellation() {
        var count = new AtomicInteger();
        var broken = facade(fields -> { count.incrementAndGet(); throw new IllegalStateException(RAW, new RuntimeException(RAW)); });
        var plain = facade(null);
        var logger = (Logger) LoggerFactory.getLogger(LocalPreviewConversationFacade.class);
        var capture = new ListAppender<ILoggingEvent>(); capture.start(); logger.addAppender(capture);
        try {
            var session = broken.createConversation().sessionId();
            var answer = broken.sendMessage(session, "DEVELOPMENT_SYNTHETIC_UNKNOWN_QUESTION");
            var expected = plain.sendMessage(plain.createConversation().sessionId(), "DEVELOPMENT_SYNTHETIC_UNKNOWN_QUESTION");
            assertThat(answer).isEqualTo(expected); assertThat(count.get()).isEqualTo(1);
            assertThat(broken.cancelConversation(session).type().name()).isEqualTo("SESSION_CANCELLED");
            broken.sendMessage(session, "DEVELOPMENT_SYNTHETIC_UNKNOWN_QUESTION");
            assertThat(count.get()).isEqualTo(1);
            assertThat(capture.list).extracting(ILoggingEvent::getFormattedMessage).containsExactly("TELEMETRY_WRITE_FAILED");
            assertThat(capture.list.getFirst().getThrowableProxy()).isNull();
            assertThat(capture.list.getFirst().getArgumentArray()).isNullOrEmpty();
        } finally { logger.detachAppender(capture); capture.stop(); }
    }

    LocalPreviewConversationFacade facade(TelemetryRecorder recorder) {
        var store = new InMemoryConversationStore();
        var clock = Clock.fixed(Instant.EPOCH, ZoneOffset.UTC); var ttl = Duration.ofMinutes(5);
        var retake = new ElectiveRetakeConversationService(store, type -> Optional.empty(), clock, ttl);
        var router = new PublicElectiveConversationRouter(store, retake,
                new PublicElectiveCreditLimitService(Optional::empty), null, null, clock);
        var chain = new LocalPreviewAnswerChain(store, router, null, null, clock);
        return new LocalPreviewConversationFacade(store, chain, clock, ttl, null, null, recorder);
    }
}
