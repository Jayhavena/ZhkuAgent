package com.zhku.knowledge.ruleagent.web;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;

/** 合成 JVM 只监听临时回环端口；不启动 Vite、模型或数据库。 */
class RuleAgentOwnedProcessLoopbackTest {
    @Test @Timeout(20) void ownedRealProcessStopsAndItsLoopbackListenerCloses(@TempDir Path temporary) throws Exception {
        Path ready = temporary.resolve("child-port.txt");
        String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        String classpath = System.getProperty("surefire.test.class.path", System.getProperty("java.class.path"));
        var builder = new ProcessBuilder(java, "-cp", classpath, ListenerChild.class.getName())
                .redirectOutput(ready.toFile()).redirectError(ProcessBuilder.Redirect.DISCARD);
        builder.environment().remove("DASHSCOPE_API_KEY");
        Process child = builder.start();
        try {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            String text = "";
            while (child.isAlive() && System.nanoTime() < deadline) {
                text = Files.readString(ready).trim();
                if (text.matches("[0-9]+")) break;
                Thread.sleep(25);
            }
            assertThat(text).as("子进程须在有界时间提供临时监听端口").matches("[0-9]+");
            int port = Integer.parseInt(text);
            try (var socket = new Socket()) { socket.connect(new InetSocketAddress("127.0.0.1", port), 500); }
            RuleAgentThreeQuestionHostTest.stopOwnedPreview(child);
            assertThat(child.isAlive()).isFalse();
            boolean closed = false;
            deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
            while (System.nanoTime() < deadline) {
                try (var socket = new Socket()) { socket.connect(new InetSocketAddress("127.0.0.1", port), 200); }
                catch (java.io.IOException expected) { closed = true; break; }
                Thread.sleep(25);
            }
            assertThat(closed).as("owned 进程退出后回环端口须停止监听").isTrue();
        } finally {
            if (child.isAlive()) { child.destroyForcibly(); child.waitFor(3, TimeUnit.SECONDS); }
        }
    }

    public static class ListenerChild {
        public static void main(String[] args) throws Exception {
            try (var server = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))) {
                System.out.println(server.getLocalPort());
                System.out.flush();
                Thread.sleep(60_000);
            }
        }
    }
}
