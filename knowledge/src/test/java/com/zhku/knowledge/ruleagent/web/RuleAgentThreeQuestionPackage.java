package com.zhku.knowledge.ruleagent.web;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import com.zhku.knowledge.ruleagent.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;
import java.util.concurrent.TimeUnit;

/** 只在测试classpath存在。以未提交工作区的真实字节冻结本轮，不修改旧的提交检查门禁。 */
final class RuleAgentThreeQuestionPackage {
    static final ObjectMapper JSON = new ObjectMapper().findAndRegisterModules();
    static final String PURPOSE = "THREE_NATURAL_QUESTIONS_TEST_ONLY_V1";
    static final String PLAN = "knowledge/src/test/resources/rule-agent-three-question-plan.json";
    private RuleAgentThreeQuestionPackage() {}
    static boolean e02Profile() {
        String profile = System.getProperty("zhku.three.profile", "Three");
        if (!Set.of("Three", "E02").contains(profile)) throw new IllegalArgumentException("未知测试执行配置");
        return profile.equals("E02");
    }
    static JsonNode plan(Path root) throws Exception {
        JsonNode value = JSON.readTree(Files.readAllBytes(root.resolve(e02Profile() ? RuleAgentE02Policy.PLAN : PLAN)));
        if (e02Profile()) RuleAgentE02Policy.validate(value);
        return value;
    }
    static String planHash(Path root) throws Exception { return sha(Files.readAllBytes(root.resolve(e02Profile() ? RuleAgentE02Policy.PLAN : PLAN))); }
    static String sha(byte[] data) throws Exception { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data)); }
    static String prepare(Path root, Path assets, Path batch) throws Exception {
        if (Files.exists(batch)) throw new IllegalArgumentException("拒绝覆盖或重用批次");
        ObjectNode pack = definition(root, assets, UUID.randomUUID().toString());
        Files.createDirectory(batch);
        byte[] bytes = JSON.writerWithDefaultPrettyPrinter().writeValueAsBytes(pack);
        Files.write(batch.resolve("request-package.json"), bytes, StandardOpenOption.CREATE_NEW);
        String hash = sha(bytes);
        Files.writeString(batch.resolve("request-package.sha256"), hash + "\n", StandardOpenOption.CREATE_NEW);
        return hash;
    }
    static JsonNode verify(Path root, Path assets, Path batch, String expected) throws Exception {
        byte[] bytes = Files.readAllBytes(batch.resolve("request-package.json"));
        if (expected == null || !expected.matches("[a-f0-9]{64}") || !sha(bytes).equals(expected)) throw new IllegalArgumentException("审包SHA不匹配");
        JsonNode actual = JSON.readTree(bytes);
        String id = actual.path("batchId").asText();
        if (!UUID.fromString(id).toString().equals(id) || !actual.equals(definition(root, assets, id))) throw new IllegalArgumentException("构建、源文件、预算或审核资料发生变化，须重新审包");
        return actual;
    }
    static void claim(Path root, Path batch, String hash, String authorization) throws Exception {
        if (authorization == null || !authorization.matches("[A-Za-z0-9._-]{1,128}") || authorization.equals("LOCAL_TEST_ONLY")) throw new IllegalArgumentException("缺少本批新授权编号");
        Path reservations = root.resolve("docs/evidence/rule-agent-integration/authorization-reservations");
        Files.createDirectories(reservations);
        // SHA命名且CREATE_NEW：复制目录、失败、重启都不能恢复同包额度；仅live路径调用。
        ObjectNode claim = JSON.createObjectNode().put("packageSha256", hash).put("authorizationId", authorization)
                .put("batchDirectory", batch.toRealPath().toString()).put("status", "STARTED_NONREFUNDABLE")
                .put("startedAt", Instant.now().toString());
        Files.write(reservations.resolve(hash + (e02Profile() ? ".e02-single-page-started.json" : ".three-question-started.json")), JSON.writeValueAsBytes(claim), StandardOpenOption.CREATE_NEW);
    }
    static void tariff(JsonNode plan, Clock clock) {
        LocalDate today = LocalDate.now(clock.withZone(ZoneId.of("Asia/Shanghai")));
        if (today.isBefore(LocalDate.parse(plan.path("money").path("tariffCheckedDate").asText())) || today.isAfter(LocalDate.parse(plan.path("money").path("tariffValidThrough").asText()))) throw new IllegalStateException("核价已过期或时钟无效，停止待重新审包");
        BigDecimal reserve = perCallReserve(plan).multiply(BigDecimal.valueOf(plan.path("maximumProviderCalls").asInt()));
        if (reserve.compareTo(new BigDecimal(plan.path("money").path("hardBudget").asText())) > 0 || reserve.compareTo(new BigDecimal(plan.path("money").path("worstCaseReserved").asText())) != 0) throw new IllegalStateException("人民币预算与最坏预留不一致");
    }
    static BigDecimal perCallReserve(JsonNode plan) {
        return new BigDecimal(plan.path("money").path("inputPerMillion").asText()).multiply(BigDecimal.valueOf(plan.path("maximumInputTokensPerCallConservative").asLong()))
                .add(new BigDecimal(plan.path("money").path("outputPerMillion").asText()).multiply(BigDecimal.valueOf(plan.path("money").path("maximumBilledOutputTokensPerCallConservative").asLong())))
                .divide(BigDecimal.valueOf(1000000));
    }
    private static ObjectNode definition(Path root, Path assets, String id) throws Exception {
        root = root.toRealPath(); assets = assets.toRealPath();
        var pack = JSON.createObjectNode().put("purpose", PURPOSE).put("authorizationStatus", "NOT_AUTHORIZED")
                .put("batchId", id).put("previousAuthorization", "CLOSED_NOT_REUSED").put("codeRoot", root.toString()).put("assetsRoot", assets.toString());
        JsonNode executionPlan = plan(root);
        boolean single = e02Profile();
        if (single) {
            pack.put("purpose", RuleAgentE02Policy.PURPOSE);
            pack.put("planSha256", planHash(root));
            Path quote = root.resolve(executionPlan.path("money").path("evidenceFile").asText()).normalize();
            if (!quote.startsWith(root)) throw new IllegalArgumentException("核价证据越界");
            pack.put("tariffEvidenceSha256", sha(Files.readAllBytes(quote)));
            var formal = pack.putObject("formalAssetsSha256");
            for (String name : List.of(RuleAgentE02Policy.PLAN,
                    "docs/evidence/rule-check-refactor/2026-10-26/real-question-agent-readiness/agent-e2e-eval.md",
                    "docs/product/rule-check-2025-verbatim-import-draft.json",
                    "docs/product/rule-check-2025-b02-semantic-review.json", "docs/product/rule-check-2025-b03-publication-review.json"))
                formal.put(name, sha(Files.readAllBytes(assets.resolve(name))));
        }
        pack.set("plan", executionPlan);
        var identity = pack.putObject("identity");
        identity.put("head", git(root, "rev-parse", "HEAD").trim());
        identity.put("inputStatus", git(root, "status", "--porcelain", "--untracked-files=all", "--", ".gitattributes", "AGENTS.md", ".mvn", "mvnw", "mvnw.cmd", "pom.xml", "agent-runtime", "agent", "knowledge", "scripts"));
        identity.put("sourceIdentity", "HEAD_PLUS_ACTUAL_TRACKED_AND_UNTRACKED_INPUT_BYTES_NOT_A_COMMIT");
        String files = git(root, "ls-files", "--cached", "--others", "--exclude-standard", "-z", "--", ".gitattributes", "AGENTS.md", ".mvn", "mvnw", "mvnw.cmd", "pom.xml", "agent-runtime", "agent", "knowledge", "scripts");
        var sources = identity.putObject("sourceInputsSha256");
        for (String name : Arrays.stream(files.split("\u0000")).filter(s -> !s.isBlank()).distinct().sorted().toList()) {
            Path file = root.resolve(name).normalize();
            if (!file.startsWith(root) || !Files.isRegularFile(file)) throw new IllegalArgumentException("构建输入缺失或越界");
            sources.put(name.replace('\\', '/'), sha(Files.readAllBytes(file)));
        }
        String cp = System.getProperty("surefire.test.class.path", System.getProperty("java.class.path"));
        var runtime = identity.putObject("runtimeClasspathSha256");
        for (String name : cp.split(java.util.regex.Pattern.quote(System.getProperty("path.separator")))) {
            if (name.isBlank()) continue;
            Path path = Path.of(name).toRealPath();
            if (Files.isDirectory(path)) runtime.set(path.toString(), tree(path)); else runtime.put(path.toString(), sha(Files.readAllBytes(path)));
        }
        identity.put("javaExecutableSha256", sha(Files.readAllBytes(Path.of(System.getProperty("java.home"), "bin", "java.exe"))));
        Path dist = root.resolve(e02Profile()
                ? plan(root).path("page").path("dist").asText()
                : "agent/frontend/dist-student");
        if (single) {
            // E02 计划声称必须真实浏览器：缺产物就不能生成一个“看起来完整”的包。
            if (!Files.isDirectory(dist) || !Files.isRegularFile(dist.resolve("index.html")))
                throw new IllegalArgumentException("E02页面产物缺失，拒绝生成不含浏览器证据的包");
            var distTree = tree(dist);
            if (distTree.isEmpty()) throw new IllegalArgumentException("E02页面产物为空，拒绝生成不含浏览器证据的包");
            identity.set("studentDistSha256", distTree);
        } else if (Files.isDirectory(dist)) identity.set("studentDistSha256", tree(dist));
        else identity.put("studentDist", "ABSENT_HTTP_ONLY_NO_BROWSER_SUCCESS_CLAIM");
        // 仅复用已审核资料披露与实际提示词，不沿用旧题、旧授权或旧10次额度。
        ObjectNode contract = RuleAgentOnlineProbeHost.controlledRequestDefinition(assets);
        contract.remove(List.of("cases", "purpose", "remainingBudgetRule", "maximumTaskRunsThisBatch", "ownedProcessMillis", "executionPolicyRevision", "localExchangeRecording", "executionReadiness"));
        contract.put("maximumProviderCalls", executionPlan.path("maximumProviderCalls").asInt())
                .put("maximumTaskRunsThisBatch", executionPlan.path("maximumRuns").asInt())
                .put("purpose", single ? RuleAgentE02Policy.PURPOSE : PURPOSE)
                .put("ownedProcessMillis", 660000)
                .put("executionPolicyRevision", single ? "E02_ONE_TASK_ONE_RUN_FIVE_CALLS_01" : "THREE_TASKS_FOUR_RUNS_TWENTY_CALLS_01")
                .put("remainingBudgetRule", single
                        ? "单题E02、单任务单run、最多5次调用，复用逐字PUBLIC的澄清额度为0；所有核验计费；无自动重试/续杯"
                        : "3任务固定顺序E02/E04/E13；只有E04可用逐字PUBLIC续一次；每run最多5次，整批20次；所有核验计费；无自动重试/续杯")
                .put("localExchangeRecording", "LOCAL_REQUEST_RESPONSE_AND_REVIEW_INPUT_WITH_PROVIDER_SECRET_REDACTION; NO_HEADERS_OR_LOGIN_BODY")
                .put("executionReadiness", single
                        ? "NOT_AUTHORIZED_REQUIRE_EXPLICIT_HUMAN_APPROVAL_OF_THIS_SHA_AND_4_02_CNY"
                        : "NOT_AUTHORIZED_REQUIRE_EXPLICIT_HUMAN_APPROVAL_OF_THIS_SHA_AND_16_10_CNY");
        if (single) contract.put("pageExecution", "REAL_CHROME_ON_STUDENT_RULES_PAGE_ONLY; NO_JAVA_POST; ONE_TASK_ONE_RUN");
        pack.set("modelContract", contract);
        pack.put("notSent", "expectations/reference answers/owner/session/originalToken/continuationToken/credentials/source code/full PDF/real student records; API key only in TLS Authorization");
        pack.put("scope", single
                ? "ISOLATED_TEST_CLASSPATH_E02_PAGE_ONLY; NO_PRODUCTION_OR_STUDENT_ROUTE_CHANGE; HUMAN_PRODUCT_REVIEW_REQUIRED"
                : "ISOLATED_TEST_CLASSPATH_HTTP_ONLY; NO_PRODUCTION_OR_STUDENT_ROUTE_CHANGE; HUMAN_PRODUCT_REVIEW_REQUIRED");
        return pack;
    }
    private static ObjectNode tree(Path root) throws Exception {
        var node = JSON.createObjectNode();
        try (var files = Files.walk(root)) { for (Path p : files.filter(Files::isRegularFile).sorted().toList()) node.put(root.relativize(p).toString().replace('\\', '/'), sha(Files.readAllBytes(p))); }
        return node;
    }
    private static String git(Path root, String... args) throws Exception {
        var command = new ArrayList<>(List.of("git", "-C", root.toString())); command.addAll(List.of(args));
        var p = new ProcessBuilder(command).redirectError(ProcessBuilder.Redirect.DISCARD).start();
        try { byte[] output = p.getInputStream().readAllBytes(); if (!p.waitFor(10, TimeUnit.SECONDS) || p.exitValue() != 0) throw new IllegalStateException("无法冻结源码身份"); return new String(output, StandardCharsets.UTF_8); }
        finally { if (p.isAlive()) p.destroyForcibly(); }
    }
}
