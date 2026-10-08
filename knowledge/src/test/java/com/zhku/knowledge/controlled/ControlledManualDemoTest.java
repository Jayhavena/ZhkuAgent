package com.zhku.knowledge.controlled;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import static org.assertj.core.api.Assertions.*;

/** 演示菜单真实HTTP/真库验收；classpath导出单独计数，不当作Agent行为覆盖。 */
class ControlledManualDemoTest {
    @Test void exportTestClasspath() throws Exception {
        var directory = Path.of("target", "manual-demo"); Files.createDirectories(directory);
        String classpath = System.getProperty("surefire.test.class.path");
        assertThat(classpath).isNotBlank().contains("test-classes").contains("agent-runtime");
        Files.writeString(directory.resolve("classpath.txt"), classpath, StandardCharsets.UTF_8);
    }

    @Test @Timeout(value = 90, unit = TimeUnit.SECONDS)
    void menuUsesActualHttpAndDifferentPidsWithoutBypassingOwnershipOrVisibility() throws Exception {
        var transcript = new StringWriter();
        try (var demo = ControlledManualDemo.open(new PrintWriter(transcript, true))) {
            var firstLogin = demo.execute("1");
            assertThat(firstLogin.httpStatus()).isEqualTo(200);
            String userId = firstLogin.body().path("userId").asText();
            var result = demo.execute("2");
            assertThat(result.body().path("run").path("state").asText()).isEqualTo("SUCCEEDED");
            assertThat(result.body().path("run").path("answer").asText()).contains("3.5");
            assertThat(result.body().path("resultVisibility").asText()).isEqualTo("VISIBLE");
            demo.execute("b"); assertThat(demo.execute("1").httpStatus()).isEqualTo(200);
            for (String command : new String[] { "3", "4", "6" }) {
                var denied = demo.execute(command);
                assertThat(denied.httpStatus()).isEqualTo(404);
                assertThat(denied.body().toString()).doesNotContain("3.5", "合成学分规则");
            }
            assertThat(demo.execute("5").body().path("items")).isEmpty();
            demo.execute("m"); demo.execute("1");
            assertThat(demo.execute("3").httpStatus()).isEqualTo(403);
            assertThat(demo.execute("5").httpStatus()).isEqualTo(403);
            demo.execute("a"); demo.execute("7");
            assertThat(demo.execute("8").body().path("type").asText()).isEqualTo("NEED_CLARIFICATION");
            assertThat(demo.execute("12").httpStatus()).isEqualTo(409);
            var pending = demo.execute("10").body();
            demo.execute("14");
            var hidden = demo.execute("3").body();
            assertThat(hidden.path("run").path("state").asText()).isEqualTo("SUCCEEDED");
            assertThat(hidden.path("resultVisibility").asText()).isEqualTo("HIDDEN");
            assertThat(hidden.path("run").path("answer").isNull()).isTrue();
            var restart = demo.execute("13").body();
            assertThat(restart.path("oldPid").asLong()).isNotEqualTo(restart.path("newPid").asLong());
            assertThat(restart.path("oldCookieStatus").asInt()).isEqualTo(401);
            assertThat(demo.execute("10").httpStatus()).isEqualTo(401);
            assertThat(demo.execute("1").body().path("userId").asText()).isEqualTo(userId);
            assertThat(demo.execute("3").httpStatus()).isEqualTo(404); // 在线Run没有被自动续跑。
            assertThat(demo.execute("6").body().path("archiveStatus").asText()).isEqualTo("FINALIZED");
            assertThat(demo.execute("10").body()).isEqualTo(pending);
            assertThat(demo.execute("9").body().path("type").asText()).isEqualTo("ANSWER");
            assertThat(demo.execute("10").body().path("revision").asLong()).isEqualTo(2);
            demo.execute("7"); demo.execute("8"); demo.execute("11");
            assertThat(demo.execute("9").body().path("code").asText()).isEqualTo("SESSION_CANCELLED");
            demo.execute("7"); demo.execute("8"); demo.execute("16");
            var withdrawn = demo.execute("9").body();
            assertThat(withdrawn.path("type").asText()).isEqualTo("EVIDENCE_UNAVAILABLE");
            assertThat(withdrawn.path("answer").isNull()).isTrue();
            demo.execute("17"); demo.execute("15");
        }
        assertThat(transcript.toString()).contains("MANUAL_DEMO_CLOSED").doesNotContain("Synthetic-", "password_hash", "Set-Cookie", "X-CSRF-TOKEN");
    }

    @Test @Timeout(45)
    void quittingBeforeLoginStillStopsTheActualServiceAndAllowsAnotherFreshDemo() throws Exception {
        var transcript = new StringWriter();
        try (var demo = ControlledManualDemo.open(new PrintWriter(transcript, true))) {
            assertThat(demo.execute("5").httpStatus()).isEqualTo(401);
            assertThatThrownBy(() -> demo.execute("3")).isInstanceOf(IllegalArgumentException.class);
        }
        // close删除的只是本次合成行；第二次不会碰撞账号或遗留Session，不重置迁移历史。
        try (var next = ControlledManualDemo.open(new PrintWriter(transcript, true))) {
            assertThat(next.execute("1").httpStatus()).isEqualTo(200);
        }
        assertThat(transcript.toString()).contains("MANUAL_DEMO_CLOSED");
    }

    @Test @Timeout(45)
    void standaloneHostTreatsInputEofAsGracefulQuit() throws Exception {
        var builder = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java.exe").toString(),
                "-Dfile.encoding=UTF-8", "-cp", System.getProperty("surefire.test.class.path"), ControlledManualDemo.class.getName());
        builder.environment().remove("DASHSCOPE_API_KEY");
        // 不保存宿主的自由终端输出；exit=0须先通过宿主自身的服务/线程/连接关闭断言。
        builder.redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD);
        var process = builder.start();
        try {
            process.getOutputStream().close();
            assertThat(process.waitFor(40, TimeUnit.SECONDS)).as("入口EOF固定40秒收尾预算").isTrue();
            assertThat(process.exitValue()).isZero();
        } finally {
            if (process.isAlive()) {
                process.descendants().forEach(ProcessHandle::destroyForcibly);
                process.destroyForcibly();
                assertThat(process.waitFor(5, TimeUnit.SECONDS)).isTrue();
            }
        }
    }
}
