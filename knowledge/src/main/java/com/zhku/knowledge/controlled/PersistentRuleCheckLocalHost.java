package com.zhku.knowledge.controlled;

import com.zhku.knowledge.rulecheck.web.RuleCheckPreviewApplication;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/** 本机封存回放入口；身份由独立持久库管理，不创建或删除账号。 */
public final class PersistentRuleCheckLocalHost {
    private PersistentRuleCheckLocalHost() {}

    public static void main(String[] args) throws Exception {
        run(args, System::getenv, new InputStreamReader(System.in, StandardCharsets.UTF_8),
                options -> RuleCheckPreviewApplication.application().run(options));
    }

    @FunctionalInterface
    interface Starter {
        AutoCloseable start(String[] arguments) throws Exception;
    }

    static void run(String[] args, Function<String, String> environment, Reader input, Starter starter) throws Exception {
        String[] options = options(args, environment);
        // 启动后所有退出路径（包括读取异常）都释放Application拥有的资源。
        try (AutoCloseable context = starter.start(options)) {
            System.out.println("RULECHECK_HOST_READY 离线回放，不是在线模型");
            var reader = new BufferedReader(input);
            String line;
            while ((line = reader.readLine()) != null && !line.equals("STOP")) { }
        }
    }

    static String[] options(String[] args, Function<String, String> environment) {
        if (args.length != 0) throw new IllegalArgumentException("参数只由本机启动环境提供");
        var root = Path.of(required(environment, "ZHKU_RULECHECK_ROOT")).toAbsolutePath().normalize();
        int port = Integer.parseInt(required(environment, "ZHKU_RULECHECK_PORT"));
        if (port < 1024 || port > 65535) throw new IllegalArgumentException("监听端口非法");
        boolean taskReplay = environment.apply("ZHKU_RULECHECK_TASK_BATCH_DIRECTORY") != null;
        var options = new ArrayList<>(List.of(
                "--server.port=" + port, "--spring.main.banner-mode=off", "--logging.level.root=ERROR",
                "--zhku.db.url=" + required(environment, "ZHKU_DB_URL"), "--zhku.db.username=zhkuagent",
                "--zhku.rule-check-preview.enabled=true", "--zhku.rule-check-preview.initialize=true",
                "--zhku.rule-check-preview.persistent-identity.enabled=true",
                "--zhku.rule-check-preview.persistent-identity.url=" + required(environment, "ZHKU_RULECHECK_IDENTITY_DB_URL"),
                "--zhku.rule-check-preview.catalogue-mode=REVIEWED_2025_CONTROLLED",
                "--zhku.rule-check-preview.selector-mode=" + (taskReplay ? "TASK_PLAN_REPLAY" : "SEALED_REPLAY"),
                "--zhku.rule-check-preview.repository-root=" + root,
                "--zhku.rule-check-preview.profile-id=controlled-2025-local",
                "--zhku.rule-check-preview.chunking-version=handbook-2025-clause-verbatim-v1",
                "--zhku.rule-check-preview.required-scope=RULE_CHECK_PUBLIC_ELECTIVE_2025",
                "--zhku.rule-check-preview.reference-revision=2025.9.2",
                "--zhku.rule-check-preview.candidate-limit=20", "--zhku.rule-check-preview.result-limit=5"));
        if (taskReplay) {
            options.add("--zhku.rule-check-preview.task-batch-directory=" + required(environment, "ZHKU_RULECHECK_TASK_BATCH_DIRECTORY"));
            options.add("--zhku.rule-check-preview.task-package-sha256=" + required(environment, "ZHKU_RULECHECK_TASK_PACKAGE_SHA256"));
            options.add("--zhku.rule-check-preview.task-summary-sha256=" + required(environment, "ZHKU_RULECHECK_TASK_SUMMARY_SHA256"));
        }
        // URL和凭据边界仍由application()原有initializer校验，不在此绕过或复制。
        return options.toArray(String[]::new);
    }

    private static String required(Function<String, String> environment, String name) {
        String value = environment.apply(name);
        if (value == null || value.isBlank()) throw new IllegalArgumentException("缺少启动环境：" + name);
        return value;
    }
}
