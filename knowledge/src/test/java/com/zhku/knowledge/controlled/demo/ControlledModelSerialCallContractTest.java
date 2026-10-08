package com.zhku.knowledge.controlled.demo;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import com.zhku.knowledge.rag.ApiKeyResolver;
import com.zhku.knowledge.rag.RagProviderProfile;
import com.zhku.knowledge.tools.modelcall.CourseCreditChatModelFactory;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import static org.assertj.core.api.Assertions.*;

/** 本机供应商协议替身只证明请求配置及SDK边界，不能冒充真实模型收益。 */
@Timeout(5)
class ControlledModelSerialCallContractTest {
    @Test void genericRuntimeOptionsStillSendExplicitSerialToolConstraint() throws Exception {
        exchange(false);
    }
    @Test void providerMultipleCallsAreReturnedWithoutHiddenSdkExecution() throws Exception {
        exchange(true);
    }
    private void exchange(boolean multiple) throws Exception {
        var serial = new AtomicBoolean(); var requests = new AtomicInteger(); var callback = new AtomicInteger();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", request -> {
            // 不保留请求正文/头；只记录安全的选项boolean和次数。
            var body = new ObjectMapper().readTree(request.getRequestBody());
            serial.set(body.has("parallel_tool_calls") && !body.path("parallel_tool_calls").asBoolean()); requests.incrementAndGet();
            String toolCalls = multiple ? ",\"tool_calls\":[{\"id\":\"call-1\",\"type\":\"function\",\"function\":{\"name\":\"lookup\",\"arguments\":\"{}\"}},{\"id\":\"call-2\",\"type\":\"function\",\"function\":{\"name\":\"calculate\",\"arguments\":\"{}\"}}]" : "";
            String response = "{\"id\":\"synthetic\",\"object\":\"chat.completion\",\"created\":1,\"model\":\"stub\",\"choices\":[{\"index\":0,\"message\":{\"role\":\"assistant\",\"content\":\"\"" + toolCalls + "},\"finish_reason\":\"" + (multiple ? "tool_calls" : "stop") + "\"}]}";
            byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
            request.getResponseHeaders().set("Content-Type", "application/json"); request.sendResponseHeaders(200, bytes.length);
            try (var stream = request.getResponseBody()) { stream.write(bytes); }
            request.close();
        });
        server.start();
        var profile = new RagProviderProfile("local-serial", "http://127.0.0.1:" + server.getAddress().getPort() + "/v1",
                "ZHKU_LOCAL_SERIAL_KEY", "stub", "stub-embed", 1, Duration.ofSeconds(1), "stub-index");
        try (var transport = CourseCreditChatModelFactory.open(profile, new ApiKeyResolver(Map.of("ZHKU_LOCAL_SERIAL_KEY", "fake-only")))) {
            var options = ToolCallingChatOptions.builder().toolCallbacks(tool("lookup", callback), tool("calculate", callback))
                    .internalToolExecutionEnabled(false).build();
            var reply = transport.model().call(new Prompt("合成请求，不发送真实供应商", options));
            assertThat(serial.get()).as("产品模型请求必须显式禁止并行工具调用，不能依赖供应商默认值").isTrue();
            assertThat(requests.get()).isEqualTo(1);
            assertThat(callback.get()).isZero();
            assertThat(reply.getResult().getOutput().getToolCalls()).hasSize(multiple ? 2 : 0);
        } finally { server.stop(0); }
    }
    private static ToolCallback tool(String name, AtomicInteger called) {
        return new ToolCallback() {
            @Override public ToolDefinition getToolDefinition() { return ToolDefinition.builder().name(name).description("合成工具")
                    .inputSchema("{\"type\":\"object\",\"additionalProperties\":false}").build(); }
            @Override public String call(String input) { called.incrementAndGet(); return "{}"; }
        };
    }
}
