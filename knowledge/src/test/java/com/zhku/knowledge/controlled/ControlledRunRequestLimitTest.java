package com.zhku.knowledge.controlled;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.*;

class ControlledRunRequestLimitTest {
    @Test void rejectsActualOversizedBodyEvenWithoutContentLength() throws Exception {
        var request = new MockHttpServletRequest("POST", "/api/zhku/controlled-preview/v1/runs") {
            @Override public long getContentLengthLong() { return -1; }
            @Override public int getContentLength() { return -1; }
        };
        request.setContextPath("/api"); request.setContent(new byte[16385]);
        var response = new MockHttpServletResponse(); var calls = new AtomicInteger();
        new ControlledRunRequestLimitFilter().doFilter(request, response, (r,s) -> calls.incrementAndGet());
        assertThat(response.getStatus()).isEqualTo(413);
        assertThat(response.getContentAsString()).contains("REQUEST_TOO_LARGE");
        assertThat(calls).hasValue(0);
    }
    @Test void preservesExactBodyAtLimitAndLeavesOtherRoutesAlone() throws Exception {
        var request = new MockHttpServletRequest("POST", "/api/zhku/controlled-preview/v1/runs");
        request.setContextPath("/api"); var bytes = new byte[16384]; bytes[1]=42; request.setContent(bytes);
        new ControlledRunRequestLimitFilter().doFilter(request, new MockHttpServletResponse(),
                (r,s) -> assertThat(r.getInputStream().readAllBytes()).isEqualTo(bytes));
        var other = new MockHttpServletRequest("POST", "/other"); other.setContent(new byte[17000]);
        var response = new MockHttpServletResponse(); var calls = new AtomicInteger();
        new ControlledRunRequestLimitFilter().doFilter(other,response,(r,s)->calls.incrementAndGet());
        assertThat(response.getStatus()).isEqualTo(200); assertThat(calls).hasValue(1);
    }
}
