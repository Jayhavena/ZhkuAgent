# Campus Tools and Function Calling Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Provide framework-neutral, safe campus calculation and sample-data tools that a future line-A orchestrator can adapt to Function Calling.

**Architecture:** `CampusToolRegistry` owns immutable tool descriptions and delegates JSON calls to focused `CampusTool` implementations. The tools live only under `com.zhku.knowledge.tools`, return uniform structured results, and never access a campus system, database, routing class, configuration class, or `agent` module.

**Tech Stack:** Java 21 records and collections; Jackson (already supplied by `spring-boot-starter-web`); JUnit 5 and AssertJ.

**Spec:** `docs/superpowers/specs/2026-09-17-campus-tools-function-calling-design.md`

## Global Constraints

- Create and modify only `knowledge/src/main/java/com/zhku/knowledge/tools/`, `knowledge/src/test/java/com/zhku/knowledge/tools/`, the classpath sample resource, evidence, and this plan.
- Do not change `agent/`, `routing/`, `PublicElectiveConversationRouter`, configuration, controllers, pages, any `pom.xml`, or Flyway migrations.
- The only access modes are `PURE_CALCULATION` and `SAMPLE_DATA_READ_ONLY`; no network, database, file writes, credential reads, or campus-system actions.
- Tool results must say they are based only on arguments or `SAMPLE_DATA`, and must never confirm course eligibility, actual timetable facts, or graduation qualification.
- Register the four stable names exactly: `summarize_course_credits`, `detect_schedule_conflicts`, `assess_graduation_credit_progress`, `query_sample_academic_schedule`.
- Use test-first development: run every new test while the production class or behavior is missing, verify the expected RED result, then implement the smallest GREEN change.

---

### Task 1: Tool contract and registry

**Files:**

- Create: `knowledge/src/main/java/com/zhku/knowledge/tools/CampusToolAccessMode.java`
- Create: `knowledge/src/main/java/com/zhku/knowledge/tools/CampusToolStatus.java`
- Create: `knowledge/src/main/java/com/zhku/knowledge/tools/CampusToolDescriptor.java`
- Create: `knowledge/src/main/java/com/zhku/knowledge/tools/CampusToolCall.java`
- Create: `knowledge/src/main/java/com/zhku/knowledge/tools/CampusToolResult.java`
- Create: `knowledge/src/main/java/com/zhku/knowledge/tools/CampusTool.java`
- Create: `knowledge/src/main/java/com/zhku/knowledge/tools/CampusToolRegistry.java`
- Test: `knowledge/src/test/java/com/zhku/knowledge/tools/CampusToolRegistryTest.java`

**Interfaces:**

- Produces `CampusTool#descriptor(): CampusToolDescriptor` and `CampusTool#execute(String argumentsJson): CampusToolResult`.
- Produces `CampusToolRegistry#descriptors(): List<CampusToolDescriptor>`, `findDescriptor(String): Optional<CampusToolDescriptor>`, and `execute(CampusToolCall): CampusToolResult` for the future line-A adapter.
- `CampusToolResult` uses `SUCCESS`, `INVALID_ARGUMENT`, `DATA_UNAVAILABLE`, and `UNKNOWN_TOOL` only; it carries tool name, status, JSON result, and boundary message.

- [ ] **Step 1: Write the failing registry tests**

```java
@Test
void shouldExposeImmutableDescriptorsAndDelegateKnownTool() {
    CampusTool tool = new StubTool("summarize_course_credits");
    CampusToolRegistry registry = new CampusToolRegistry(List.of(tool));

    assertThat(registry.descriptors()).extracting(CampusToolDescriptor::name)
            .containsExactly("summarize_course_credits");
    assertThatThrownBy(() -> registry.descriptors().add(tool.descriptor()))
            .isInstanceOf(UnsupportedOperationException.class);
    assertThat(registry.execute(new CampusToolCall(
            "summarize_course_credits", "{}"))).isEqualTo(tool.result());
}

@Test
void shouldReturnUnknownToolWithoutCallingAnyTool() {
    CampusToolRegistry registry = new CampusToolRegistry(List.of());

    assertThat(registry.execute(new CampusToolCall("not_registered", "{}")))
            .extracting(CampusToolResult::status, CampusToolResult::toolName)
            .containsExactly(CampusToolStatus.UNKNOWN_TOOL, "not_registered");
}
```

- [ ] **Step 2: Run the test to verify RED**

Run: `./mvnw.cmd -f knowledge/pom.xml -Dtest=CampusToolRegistryTest test`

Expected: compilation failure because `CampusTool`, `CampusToolRegistry`, and the result records do not yet exist.

- [ ] **Step 3: Implement the minimal contract**

```java
public interface CampusTool {
    CampusToolDescriptor descriptor();
    CampusToolResult execute(String argumentsJson);
}

public final class CampusToolRegistry {
    private final Map<String, CampusTool> toolsByName;

    public CampusToolRegistry(Collection<? extends CampusTool> tools) {
        // 同名工具会让模型定义与实际执行脱节，注册时必须拒绝，而不是按插入顺序覆盖。
        this.toolsByName = tools.stream().collect(toUnmodifiableMap(
                tool -> tool.descriptor().name(), Function.identity()));
    }

    public CampusToolResult execute(CampusToolCall call) {
        return Optional.ofNullable(toolsByName.get(call.toolName()))
                .map(tool -> tool.execute(call.argumentsJson()))
                .orElseGet(() -> CampusToolResult.failure(
                        call.toolName(), CampusToolStatus.UNKNOWN_TOOL,
                        "未注册的校园工具", "工具未注册，不能执行或猜测结果。"));
    }
}
```

Use validating record compact constructors: names and descriptions are trimmed nonblank; schema and argument JSON are nonblank; access mode/status are non-null. Return `List.copyOf(...)` from `descriptors()` in lexical name order.

- [ ] **Step 4: Run the registry tests to verify GREEN**

Run: `./mvnw.cmd -f knowledge/pom.xml -Dtest=CampusToolRegistryTest test`

Expected: all registry tests pass, including duplicate-name rejection and unknown-tool structured failure.

- [ ] **Step 5: Commit**

```powershell
git add -- knowledge/src/main/java/com/zhku/knowledge/tools/CampusToolAccessMode.java knowledge/src/main/java/com/zhku/knowledge/tools/CampusToolStatus.java knowledge/src/main/java/com/zhku/knowledge/tools/CampusToolDescriptor.java knowledge/src/main/java/com/zhku/knowledge/tools/CampusToolCall.java knowledge/src/main/java/com/zhku/knowledge/tools/CampusToolResult.java knowledge/src/main/java/com/zhku/knowledge/tools/CampusTool.java knowledge/src/main/java/com/zhku/knowledge/tools/CampusToolRegistry.java knowledge/src/test/java/com/zhku/knowledge/tools/CampusToolRegistryTest.java
git commit -m "feat(zhku): 增加校园工具注册契约"
```

### Task 2: Course-credit summary and graduation-credit progress

**Files:**

- Create: `knowledge/src/main/java/com/zhku/knowledge/tools/ToolJsonSupport.java`
- Create: `knowledge/src/main/java/com/zhku/knowledge/tools/CourseCreditSummaryTool.java`
- Create: `knowledge/src/main/java/com/zhku/knowledge/tools/GraduationCreditProgressTool.java`
- Test: `knowledge/src/test/java/com/zhku/knowledge/tools/CourseCreditSummaryToolTest.java`
- Test: `knowledge/src/test/java/com/zhku/knowledge/tools/GraduationCreditProgressToolTest.java`

**Interfaces:**

- `CourseCreditSummaryTool` publishes descriptor name `summarize_course_credits` and accepts `{"courses":[{"courseId":"GE001","category":"GENERAL","credits":2.0}]}`.
- `GraduationCreditProgressTool` publishes descriptor name `assess_graduation_credit_progress` and accepts `{"requirements":[{"category":"GENERAL","requiredCredits":8.0}],"completedCreditsByCategory":{"GENERAL":6.0}}`.
- Both return `CampusToolResult` with `PURE_CALCULATION` descriptors and a boundary message that declines eligibility/graduation confirmation.

- [ ] **Step 1: Write the failing calculation tests**

```java
@Test
void shouldSumCreditsAndGroupByCategory() throws Exception {
    CampusToolResult result = new CourseCreditSummaryTool().execute("""
            {"courses":[
              {"courseId":"GE001","category":"GENERAL","credits":2.0},
              {"courseId":"MA101","category":"MAJOR","credits":3.0}]}
            """);

    JsonNode json = MAPPER.readTree(result.resultJson());
    assertThat(result.status()).isEqualTo(CampusToolStatus.SUCCESS);
    assertThat(json.path("totalCredits").decimalValue()).isEqualByComparingTo("5.0");
    assertThat(json.path("creditsByCategory").path("GENERAL").decimalValue())
            .isEqualByComparingTo("2.0");
}

@Test
void shouldReportGraduationCreditGapWithoutClaimingGraduationEligibility() throws Exception {
    CampusToolResult result = new GraduationCreditProgressTool().execute("""
            {"requirements":[{"category":"GENERAL","requiredCredits":8.0}],
             "completedCreditsByCategory":{"GENERAL":6.0}}
            """);

    assertThat(result.status()).isEqualTo(CampusToolStatus.SUCCESS);
    assertThat(MAPPER.readTree(result.resultJson()).path("totalRemainingCredits").decimalValue())
            .isEqualByComparingTo("2.0");
    assertThat(result.boundaryMessage()).contains("不能确认毕业资格");
}
```

Add failing cases for negative credit, blank category/course ID, a missing `courses` array, duplicate categories in requirements, and a completed credit category not present in requirements. All must return `INVALID_ARGUMENT`, never a thrown parse exception.

- [ ] **Step 2: Run the tests to verify RED**

Run: `./mvnw.cmd -f knowledge/pom.xml -Dtest=CourseCreditSummaryToolTest,GraduationCreditProgressToolTest test`

Expected: compilation failure because both calculation tools and JSON support are absent.

- [ ] **Step 3: Implement the minimal calculation tools**

```java
final class ToolJsonSupport {
    static final ObjectMapper MAPPER = new ObjectMapper(JsonFactory.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .build());

    static CampusToolResult invalidArguments(String toolName, String message) {
        return CampusToolResult.failure(toolName, CampusToolStatus.INVALID_ARGUMENT,
                jsonMessage(message), "只按传入参数计算，不能确认个人资格或学籍事实。");
    }
}
```

Parse JSON into package-private records, validate before aggregation, and use `BigDecimal` rather than `double`. Sort category keys lexically before serializing results so model-facing output remains deterministic. `GraduationCreditProgressTool` must calculate each `remainingCredits` with `required.subtract(completed).max(BigDecimal.ZERO)` and must not infer missing categories as valid data.

- [ ] **Step 4: Run the calculation tests to verify GREEN**

Run: `./mvnw.cmd -f knowledge/pom.xml -Dtest=CourseCreditSummaryToolTest,GraduationCreditProgressToolTest test`

Expected: all normal, validation, deterministic-output, and boundary-message tests pass.

- [ ] **Step 5: Commit**

```powershell
git add -- knowledge/src/main/java/com/zhku/knowledge/tools/ToolJsonSupport.java knowledge/src/main/java/com/zhku/knowledge/tools/CourseCreditSummaryTool.java knowledge/src/main/java/com/zhku/knowledge/tools/GraduationCreditProgressTool.java knowledge/src/test/java/com/zhku/knowledge/tools/CourseCreditSummaryToolTest.java knowledge/src/test/java/com/zhku/knowledge/tools/GraduationCreditProgressToolTest.java
git commit -m "feat(zhku): 增加学分计算校园工具"
```

### Task 3: Schedule-conflict detection

**Files:**

- Create: `knowledge/src/main/java/com/zhku/knowledge/tools/ScheduleConflictTool.java`
- Test: `knowledge/src/test/java/com/zhku/knowledge/tools/ScheduleConflictToolTest.java`

**Interfaces:**

- Publishes descriptor name `detect_schedule_conflicts`.
- Accepts `{"courses":[{"courseId":"CS101","meetings":[{"weekday":"MONDAY","startPeriod":1,"endPeriod":2,"weeks":[1,2]}]}]}`.
- Returns `conflicts` whose entries contain the two course IDs, weekday, overlapping start/end periods, and sorted overlapping weeks.

- [ ] **Step 1: Write the failing schedule tests**

```java
@Test
void shouldReportOnlySlotsWithDayPeriodAndWeekOverlap() throws Exception {
    CampusToolResult result = new ScheduleConflictTool().execute("""
            {"courses":[
              {"courseId":"CS101","meetings":[{"weekday":"MONDAY","startPeriod":1,"endPeriod":2,"weeks":[1,2]}]},
              {"courseId":"MA101","meetings":[{"weekday":"MONDAY","startPeriod":2,"endPeriod":3,"weeks":[2,3]}]},
              {"courseId":"EN101","meetings":[{"weekday":"MONDAY","startPeriod":1,"endPeriod":2,"weeks":[4]}]}]}
            """);

    JsonNode conflicts = MAPPER.readTree(result.resultJson()).path("conflicts");
    assertThat(result.status()).isEqualTo(CampusToolStatus.SUCCESS);
    assertThat(conflicts).hasSize(1);
    assertThat(conflicts.get(0).path("overlappingWeeks").get(0).asInt()).isEqualTo(2);
}
```

Add tests that meetings belonging to the same course are ignored, endpoint-overlapping periods conflict because periods are inclusive, different weekdays do not conflict, empty/zero week lists fail, and `startPeriod > endPeriod` fails with `INVALID_ARGUMENT`.

- [ ] **Step 2: Run the test to verify RED**

Run: `./mvnw.cmd -f knowledge/pom.xml -Dtest=ScheduleConflictToolTest test`

Expected: compilation failure because `ScheduleConflictTool` does not exist.

- [ ] **Step 3: Implement the minimal detector**

```java
private static boolean overlaps(Meeting left, Meeting right) {
    return left.weekday().equals(right.weekday())
            && left.startPeriod() <= right.endPeriod()
            && right.startPeriod() <= left.endPeriod()
            && !Collections.disjoint(left.weeks(), right.weeks());
}
```

Flatten each course meeting into an immutable value with its course ID, compare only pairs whose course IDs differ, calculate inclusive period intersection and set intersection, then sort records by `firstCourseId`, `secondCourseId`, weekday, start period, end period, and week list. Return an empty conflict array as successful rather than treating it as data unavailable.

- [ ] **Step 4: Run the schedule tests to verify GREEN**

Run: `./mvnw.cmd -f knowledge/pom.xml -Dtest=ScheduleConflictToolTest test`

Expected: all conflict, non-conflict, validation, and deterministic-sort tests pass.

- [ ] **Step 5: Commit**

```powershell
git add -- knowledge/src/main/java/com/zhku/knowledge/tools/ScheduleConflictTool.java knowledge/src/test/java/com/zhku/knowledge/tools/ScheduleConflictToolTest.java
git commit -m "feat(zhku): 增加选课冲突检测工具"
```

### Task 4: Sample academic-calendar and schedule query

**Files:**

- Create: `knowledge/src/main/java/com/zhku/knowledge/tools/SampleAcademicScheduleTool.java`
- Create: `knowledge/src/main/resources/zhku/tools/sample-academic-schedule.json`
- Test: `knowledge/src/test/java/com/zhku/knowledge/tools/SampleAcademicScheduleToolTest.java`

**Interfaces:**

- Publishes descriptor name `query_sample_academic_schedule` and access mode `SAMPLE_DATA_READ_ONLY`.
- Accepts `{"term":"SAMPLE-2026-1","date":"2026-03-02","courseKeyword":"演示"}` where date and course keyword are optional but term is required.
- Returns `source: "SAMPLE_DATA"`, matched entries, and an explicit non-authoritative boundary message.

- [ ] **Step 1: Write the failing sample-query tests**

```java
@Test
void shouldReturnOnlySampleEntriesMatchingTermAndDate() throws Exception {
    CampusToolResult result = new SampleAcademicScheduleTool().execute("""
            {"term":"SAMPLE-2026-1","date":"2026-03-02"}
            """);

    JsonNode json = MAPPER.readTree(result.resultJson());
    assertThat(result.status()).isEqualTo(CampusToolStatus.SUCCESS);
    assertThat(json.path("source").asText()).isEqualTo("SAMPLE_DATA");
    assertThat(json.path("entries")).isNotEmpty();
    assertThat(result.boundaryMessage()).contains("样例数据");
}

@Test
void shouldRefuseToInventAnUnrepresentedSchedule() {
    CampusToolResult result = new SampleAcademicScheduleTool().execute(
            "{\"term\":\"SAMPLE-2099-1\"}");

    assertThat(result.status()).isEqualTo(CampusToolStatus.DATA_UNAVAILABLE);
}
```

Add tests for blank term, malformed date, keyword filtering, immutable resource records, and the guarantee that results contain no classroom/eligibility assertion outside the fixed sample entries.

- [ ] **Step 2: Run the test to verify RED**

Run: `./mvnw.cmd -f knowledge/pom.xml -Dtest=SampleAcademicScheduleToolTest test`

Expected: compilation failure because the tool and classpath resource are missing.

- [ ] **Step 3: Add fixed sample data and the query implementation**

Create a resource with an intentionally fictional `SAMPLE-2026-1` term and entries using `CALENDAR_EVENT` / `SCHEDULE_ENTRY`. Do not use a real school date, real room, real course opening, or wording that implies official status.

```json
{
  "source": "SAMPLE_DATA",
  "entries": [
    {"term":"SAMPLE-2026-1","date":"2026-03-02","type":"CALENDAR_EVENT","title":"样例教学周开始"},
    {"term":"SAMPLE-2026-1","date":"2026-03-02","type":"SCHEDULE_ENTRY","courseId":"DEMO101","title":"演示课程"}
  ]
}
```

Load the resource once through the class loader into an immutable list. Parse query date with `LocalDate`; filter term exactly, date exactly when supplied, and course keyword case-insensitively against `courseId` and title. Return `DATA_UNAVAILABLE` when no record matches, rather than treating an empty list as an official no-class conclusion.

- [ ] **Step 4: Run the sample-query tests to verify GREEN**

Run: `./mvnw.cmd -f knowledge/pom.xml -Dtest=SampleAcademicScheduleToolTest test`

Expected: all source-label, filtering, validation, and no-invention tests pass.

- [ ] **Step 5: Commit**

```powershell
git add -- knowledge/src/main/java/com/zhku/knowledge/tools/SampleAcademicScheduleTool.java knowledge/src/main/resources/zhku/tools/sample-academic-schedule.json knowledge/src/test/java/com/zhku/knowledge/tools/SampleAcademicScheduleToolTest.java
git commit -m "feat(zhku): 增加样例校历课表查询工具"
```

### Task 5: Registry integration, evidence, and verification

**Files:**

- Modify: `knowledge/src/test/java/com/zhku/knowledge/tools/CampusToolRegistryTest.java`
- Create: `docs/evidence/2026-09-17-校园工具集与Function-Calling-证据账本.md`
- Modify: `docs/plans/agent-sync/SYNC-STATUS.md`

**Interfaces:**

- The final registry exposes exactly four stable descriptors: the three calculation tools use `PURE_CALCULATION`; only `query_sample_academic_schedule` uses `SAMPLE_DATA_READ_ONLY`.
- The evidence documents TDD RED outputs, tool safety boundaries, test commands/results, and the fact that no Function Calling provider adapter or router integration is claimed.

- [ ] **Step 1: Write the failing full-registry test**

```java
@Test
void shouldExposeExactlyTheFourApprovedCampusTools() {
    CampusToolRegistry registry = CampusToolRegistry.withDefaultCampusTools();

    assertThat(registry.descriptors()).extracting(CampusToolDescriptor::name)
            .containsExactly(
                    "assess_graduation_credit_progress",
                    "detect_schedule_conflicts",
                    "query_sample_academic_schedule",
                    "summarize_course_credits");
}
```

- [ ] **Step 2: Run the integration test to verify RED**

Run: `./mvnw.cmd -f knowledge/pom.xml -Dtest=CampusToolRegistryTest test`

Expected: failure because `withDefaultCampusTools()` is not present or does not yet include all four tools.

- [ ] **Step 3: Add the minimal default-registry factory and evidence**

Implement `CampusToolRegistry.withDefaultCampusTools()` using exactly the four approved tools, then document that the registry is an adapter-ready contract only. Do not introduce Spring AI, alter line-A routing, or claim a model executed any tool.

- [ ] **Step 4: Run focused and full verification**

Run:

```powershell
.\mvnw.cmd -f knowledge\pom.xml "-Dtest=CampusToolRegistryTest,CourseCreditSummaryToolTest,GraduationCreditProgressToolTest,ScheduleConflictToolTest,SampleAcademicScheduleToolTest" test
.\mvnw.cmd -f knowledge\pom.xml "-Dzhku.db.url=jdbc:postgresql://127.0.0.1:55432/zhkuagent_test" "-Dzhku.db.user=zhkuagent" test
```

Expected: focused tools tests pass. For the full suite, use only the previously approved isolated temporary PostgreSQL workflow (migrations plus reviewed test data); do not access port `54329` or the demonstration database.

- [ ] **Step 5: Commit and update status**

```powershell
git add -- knowledge/src/main/java/com/zhku/knowledge/tools/ knowledge/src/test/java/com/zhku/knowledge/tools/ knowledge/src/main/resources/zhku/tools/sample-academic-schedule.json docs/evidence/2026-09-17-校园工具集与Function-Calling-证据账本.md docs/plans/agent-sync/SYNC-STATUS.md
git commit -m "docs(zhku): 记录校园工具验证证据"
```

Before the commit, inspect `git status --porcelain=v1` and verify every staged path is owned by CX-1. Clear the tools file-occupancy row, set the line-B task to `待合并`, run `git for-each-ref refs/heads`, and do not push.
