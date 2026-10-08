package com.zhku.agent.agent.runtime;

import java.nio.file.*;
import java.util.concurrent.TimeUnit;

/** 独立JVM隔离默认holder；关闭证据来自hook读取，不以daemon进程退出代替。 */
public final class AgentRunDefaultObservationOwnerProbe {
    public static void main(String[] args) throws Exception {
        Path witness = Path.of(args[1]);
        if (args[0].equals("NONE")) {
            var context = AgentRunContext.create("无观察");
            context.start();
            long threads = Thread.getAllStackTraces().keySet().stream()
                    .filter(t -> t.getName().startsWith("agent-observation-")).count();
            if (threads != 0 || context.observationStatus().phase() != AgentRunObservationDispatcher.Phase.DISABLED)
                throw new AssertionError("无观察不应创建资源");
            Files.writeString(witness, "DISABLED/0");
            return;
        }
        var owner = AgentRunObservationDispatcher.processDefault();
        if (owner != AgentRunObservationDispatcher.processDefault()) throw new AssertionError("默认不唯一");
        try { owner.close(); throw new AssertionError("借用者不能关闭默认owner"); }
        catch (IllegalStateException expected) { }
        for (int i = 0; i < 2; i++) {
            var context = AgentRunContext.create("默认观察", e -> { });
            context.start(); context.finish(AgentRunState.SUCCEEDED);
            context.cleanupOnce(() -> { }); context.signalTermination(); context.publishTermination();
        }
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(1);
        while (owner.status().registeredCount() != 0 && System.nanoTime() < deadline) Thread.sleep(1);
        if (owner.status().registeredCount() != 0 || owner.status().liveThreadCount() != 5) throw new AssertionError("默认交付超时");
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
            while (System.nanoTime() < until) {
                var status = owner.status();
                if (status.phase() == AgentRunObservationDispatcher.OwnerPhase.CLOSED && status.liveThreadCount() == 0) {
                    try { Files.writeString(witness, "CLOSED/0/UNIQUE"); }
                    catch (Exception failure) { throw new AssertionError("关闭证据写入失败"); }
                    return;
                }
                try { Thread.sleep(1); } catch (InterruptedException ignored) { }
            }
            throw new AssertionError("默认关闭hook未收敛");
        }, "r13-default-witness"));
    }
}
