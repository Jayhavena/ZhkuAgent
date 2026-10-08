package com.zhku.knowledge.controlled.demo;

import com.zhku.knowledge.controlled.ControlledPreviewApplication;
import com.zhku.knowledge.conversation.ConversationJdbcAccess;
import com.zhku.knowledge.tools.modelcall.CourseCreditModelTransport;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;

/** Windows脚本的产品Context宿主，不注册测试模型或新的业务入口。 */
public final class ControlledWebDemoHost {
    private ControlledWebDemoHost() { }
    public static void main(String[] args) {
        int exit = 1;
        try {
            if (args.length != 0) throw new IllegalArgumentException();
            int port = Integer.parseInt(System.getenv().getOrDefault("ZHKU_DEMO_BACKEND_PORT", "58123"));
            if (port != 0 && (port < 1024 || port > 65535 || port == 54329)) throw new IllegalArgumentException();
            String target = System.getenv("ZHKU_DB_URL");
            var settings = new ArrayList<String>();
            settings.addAll(java.util.List.of("--server.port=" + port, "--server.address=127.0.0.1", "--server.servlet.context-path=/api",
                    "--spring.main.banner-mode=off", "--logging.level.root=OFF", "--zhku.controlled-preview.enabled=true",
                    "--zhku.controlled-preview.conversations.enabled=true", "--zhku.campus-agent.enabled=true",
                    "--zhku.campus-agent.archive.enabled=true", "--zhku.db.username=zhkuagent", "--zhku.db.url=" + target,
                    "--zhku.rag.active-profile=dashscope"));
            // 只复用供应商元数据，不启用RAG或旧预览profile；真实工厂仍是唯一模型装配者。
            String[][] provider = {{"base-url", "https://dashscope.aliyuncs.com/compatible-mode/v1"}, {"api-key-env-var", "DASHSCOPE_API_KEY"},
                    {"chat-model", "qwen-plus"}, {"embedding-model", "text-embedding-v4"}, {"embedding-dimensions", "1024"},
                    {"timeout-seconds", "30"}, {"index-profile", "dashscope-text-embedding-v4-1024"}};
            for (var entry : provider) settings.add("--zhku.rag.profiles.dashscope." + entry[0] + "=" + entry[1]);
            var context = (ServletWebServerApplicationContext) ControlledPreviewApplication.application().run(settings.toArray(String[]::new));
            var transport = context.getBean(CourseCreditModelTransport.class);
            var conversations = context.getBean(ConversationJdbcAccess.class);
            var worker = context.getBean("controlledCampusWorker", ExecutorService.class);
            var timer = context.getBean("controlledCampusTimer", ExecutorService.class);
            try (var input = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8))) {
                System.out.println("DEMO_HOST_READY pid=" + ProcessHandle.current().pid() + " port=" + context.getWebServer().getPort());
                System.out.flush();
                String command;
                while ((command = input.readLine()) != null && !command.equals("STOP")) { }
            } finally {
                // EOF也排空Context；关闭预算由调用者约束，退出信号只在真实自有资源收敛后发出。
                context.close();
            }
            long deadline = System.nanoTime() + Duration.ofSeconds(2).toNanos();
            while ((!transport.isTerminated() || !worker.isTerminated() || !timer.isTerminated()
                    || conversations.status().liveThreads() != 0) && System.nanoTime() < deadline) TimeUnit.MILLISECONDS.sleep(10);
            var state = conversations.status();
            boolean resources = transport.isTerminated() && worker.isTerminated() && timer.isTerminated()
                    && state.closed() && !state.resourceFailure() && state.inFlight() == 0 && state.liveThreads() == 0;
            System.out.println("DEMO_HOST_CLOSED resources=" + resources);
            exit = resources ? 0 : 1;
        } catch (Exception failure) { System.err.println("DEMO_HOST_FAILED"); }
        if (exit != 0) System.exit(exit);
    }
}
