package com.zhku.knowledge.eval.chain;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * 评测链路命令行入口。
 *
 * <pre>
 * collect --evidence &lt;dir&gt; --out &lt;dir&gt; [--repo-root &lt;dir&gt;] [--bundle-id &lt;id&gt;]
 * run     --suite &lt;file&gt; --out &lt;dir&gt; [--repo-root &lt;dir&gt;]
 * </pre>
 *
 * <p>退出码：0 表示回归期望全部命中；1 表示存在未命中或执行失败。CI 直接据此失败。
 */
public final class AgentEvalChainMain {
    private AgentEvalChainMain() {}

    public static void main(String[] args) {
        try {
            System.exit(execute(args));
        } catch (IOException failure) {
            System.err.println("评测链路执行失败: " + failure.getMessage());
            System.exit(1);
        } catch (RuntimeException failure) {
            System.err.println("评测链路执行失败: " + failure);
            System.exit(1);
        }
    }

    static int execute(String[] args) throws IOException {
        if (args.length == 0 || "--help".equals(args[0])) {
            System.out.println("用法: collect --evidence <dir> --out <dir> | run --suite <file> --out <dir>");
            return args.length == 0 ? 1 : 0;
        }
        String command = args[0];
        Map<String, String> options = parse(args);
        Path repoRoot = Path.of(options.getOrDefault("repo-root",
                Path.of("").toAbsolutePath().toString()));
        return switch (command) {
            case "collect" -> collect(options, repoRoot);
            case "run" -> run(options, repoRoot);
            default -> {
                System.err.println("未知命令: " + command);
                yield 1;
            }
        };
    }

    private static int collect(Map<String, String> options, Path repoRoot) throws IOException {
        Path evidence = required(options, "evidence");
        Path out = required(options, "out");
        String bundleId = options.getOrDefault("bundle-id",
                evidence.toAbsolutePath().normalize().getFileName().toString());
        var collection = EvalEvidenceCollector.collect(evidence, bundleId, out, repoRoot);
        System.out.println("collect bundle=" + bundleId + " status=" + collection.status()
                + " runs=" + collection.runs().size() + " out=" + out.toAbsolutePath().normalize());
        collection.reasons().forEach(reason -> System.out.println("  reason=" + reason));
        return collection.status() == EvalEvidenceCollector.Status.INCOMPLETE ? 2 : 0;
    }

    private static int run(Map<String, String> options, Path repoRoot) throws IOException {
        Path suite = required(options, "suite");
        Path out = required(options, "out");
        EvalChainReport report = EvalChainRunner.run(repoRoot, suite, out);
        report.write(out);
        for (EvalChainReport.CaseRecord record : report.cases()) {
            System.out.println((record.matched() ? "[MATCH] " : "[MISMATCH] ") + record.caseId()
                    + " verdict=" + record.verdict() + " expected=" + record.expectedVerdict()
                    + " details=" + String.join(",", record.details()));
        }
        System.out.println("report: " + out.resolve("report.json").toAbsolutePath().normalize()
                + " regression=" + (report.regressionPassed() ? "PASS" : "FAIL"));
        return report.regressionPassed() ? 0 : 1;
    }

    private static Map<String, String> parse(String[] args) {
        Map<String, String> options = new HashMap<>();
        for (int index = 1; index < args.length; index++) {
            String token = args[index];
            if (!token.startsWith("--")) throw new IllegalArgumentException("未知参数: " + token);
            String key = token.substring(2);
            if (index + 1 >= args.length) throw new IllegalArgumentException("参数缺值: " + token);
            options.put(key, args[++index]);
        }
        return options;
    }

    private static Path required(Map<String, String> options, String key) {
        String value = options.get(key);
        if (value == null || value.isBlank()) throw new IllegalArgumentException("缺少 --" + key);
        return Path.of(value);
    }
}
