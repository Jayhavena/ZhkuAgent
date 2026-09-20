# Stage 9 Feedback Preview Wiring Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the already-defined anonymous feedback HTTP contract reachable from the local preview application without changing the conversation, answer-card, RAG, or frontend paths.

**Architecture:** Expand the preview application's component scan to include `com.zhku.knowledge.feedback`. A feedback-only preview configuration creates the JDBC repository and submission service only when the existing local-preview profile and explicit switch are active; the controller receives the same guards so an otherwise disabled preview context remains safe to start.

**Tech Stack:** Java 21, Spring Boot MVC, MockMvc, JUnit 5, JDBC/PostgreSQL through the existing feedback repository.

**Spec:** 用户 2026-09-20 阶段 9 接线指令；`docs/plans/agent-sync/SYNC-STATUS.md` 第三、八节；`docs/superpowers/plans/2026-09-16-stage-9-service-guides-feedback.md`。

## Global Constraints

- Only change `ZhkuLocalPreviewApplication`, `feedback/`, its tests, `docs/`, and the state board occupancy/own row.
- Do not change `LocalPreviewConversationFacade`, `answer/`, `rag/`, reply payloads, or any frontend resources.
- Do not add a migration; V16 is already the only stage-9 feedback migration and has no V15 foreign key.
- Preserve the frozen request and response fields; reject free text/identity fields and never return the anonymous hash.
- Do not merge to the main branch or push; use exact-path staging only.

---

### Task 1: Preview application endpoint contract

**Files:**
- Create: `knowledge/src/test/java/com/zhku/knowledge/feedback/FeedbackPreviewEndpointTest.java`
- Modify: `knowledge/src/main/java/com/zhku/knowledge/web/ZhkuLocalPreviewApplication.java`
- Modify: `knowledge/src/main/java/com/zhku/knowledge/feedback/AnonymousFeedbackController.java`
- Create: `knowledge/src/main/java/com/zhku/knowledge/feedback/FeedbackPreviewConfiguration.java`

**Consumes:** `AnonymousFeedbackController`, `AnonymousFeedbackService`, `FeedbackRepository`, existing `zhku-local-preview` profile and `zhku.local-preview.enabled` switch.

**Produces:** A reachable `POST /zhku/feedback` endpoint when preview is explicitly enabled; disabled preview contexts retain their previous startup behavior.

- [ ] **Step 1: Write the failing application-entry test**

```java
mockMvc.perform(post("/zhku/feedback").contentType(APPLICATION_JSON)
        .content(validFeedbackJson()))
    .andExpect(status().isCreated())
    .andExpect(jsonPath("$.accepted").value(true));

mockMvc.perform(post("/zhku/feedback").contentType(APPLICATION_JSON)
        .content(jsonWith("reason", "UNSAFE_FREE_TEXT_REASON")))
    .andExpect(status().isBadRequest());

mockMvc.perform(post("/zhku/feedback").contentType(APPLICATION_JSON)
        .content(jsonWith("anonymousSessionHash", "a".repeat(65))))
    .andExpect(status().isBadRequest());
```

- [ ] **Step 2: Run the test to verify RED**

Run: `mvn -pl knowledge -Dtest=FeedbackPreviewEndpointTest test`

Expected: the existing preview application returns 404 because it scans only `com.zhku.knowledge.web`.

- [ ] **Step 3: Add the smallest guarded wiring**

```java
@SpringBootApplication(scanBasePackages = {
        "com.zhku.knowledge.web", "com.zhku.knowledge.feedback"
})
class ZhkuLocalPreviewApplication { }

@Configuration(proxyBeanMethods = false)
@Profile("zhku-local-preview")
@ConditionalOnProperty(prefix = "zhku.local-preview", name = "enabled", havingValue = "true")
class FeedbackPreviewConfiguration {
    @Bean FeedbackRepository zhkuLocalPreviewFeedbackRepository() { ... }
    @Bean AnonymousFeedbackService zhkuLocalPreviewAnonymousFeedbackService(...) { ... }
}
```

The controller receives the same profile/property guard. This prevents the newly scanned controller from requiring a feedback service when preview is disabled.

- [ ] **Step 4: Run the endpoint test to verify GREEN**

Run: `mvn -pl knowledge -Dtest=FeedbackPreviewEndpointTest test`

Expected: normal request returns 201; unsupported enum and 65-character hash return 400; no reply contains `anonymousSessionHash`.

- [ ] **Step 5: Commit**

```powershell
git add -- knowledge/src/main/java/com/zhku/knowledge/web/ZhkuLocalPreviewApplication.java knowledge/src/main/java/com/zhku/knowledge/feedback/AnonymousFeedbackController.java knowledge/src/main/java/com/zhku/knowledge/feedback/FeedbackPreviewConfiguration.java knowledge/src/test/java/com/zhku/knowledge/feedback/FeedbackPreviewEndpointTest.java
git diff --cached --check
git status --porcelain=v1
git commit -m "feat(zhku): 接入预览匿名反馈端点"
```

### Task 2: Feedback operations manual and completion evidence

**Files:**
- Create: `docs/zhkuagent/仲园Agent-匿名反馈管理员操作手册.md`
- Modify: `docs/evidence/2026-09-20-阶段9-匿名反馈闭环-证据账本.md`
- Modify: `docs/plans/agent-sync/SYNC-STATUS.md`

**Consumes:** The frozen §8 API contract and Task 1 result.

**Produces:** WB-1-readable operational instructions, corrected evidence of preview reachability, and cleared occupancy/updated line-B status.

- [ ] **Step 1: Write the manual**

Include the four enum meanings, the strict receive boundary, same-day de-duplication, 90-day aggregate/delete rule, aggregate-only administrator review, explicit retention operation, and the promise that feedback never automatically creates a material candidate.

- [ ] **Step 2: Update evidence and state board by append-only contract rules**

Record the endpoint test's normal and two boundary results, note that no conversation/answer/RAG/frontend file changed, clear the Task 1 occupancy, and update only the line-B row.

- [ ] **Step 3: Run focused verification and commit**

Run: `mvn -pl knowledge "-Dtest=AnonymousFeedbackContractTest,FeedbackRetentionTest,JdbcFeedbackRepositoryTest,FeedbackAdminControllerTest,FeedbackPreviewEndpointTest" test`

Then stage only the manual, evidence, and state board with `git add -- <exact paths>`; run `git diff --cached --check` and `git status --porcelain=v1`; commit as `docs(zhku): 补匿名反馈操作手册与接线证据`.

## Self-Review

- Spec coverage: Task 1 covers only allowed preview scan/wiring plus normal and two required boundaries. Task 2 covers the user-assigned manual and the required evidence/status closure.
- Placeholder scan: No task depends on a new dependency, migration, answer-card field, or frontend change.
- Type consistency: The endpoint keeps `AnonymousFeedbackController`'s existing `POST /zhku/feedback` request/receipt; preview configuration provides its required `AnonymousFeedbackService` and `FeedbackRepository` beans.
