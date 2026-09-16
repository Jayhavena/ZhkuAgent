# 阶段 6：公共选修课单学期学分上限 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在现有本机会话中提供“每学期公共选修课最高不得超过 4 学分”的可追溯回答，且不影响重修链路。

**Architecture:** 以独立 PostgreSQL 证据范围承载学分规则；规则路由先处理已有重修待澄清，再分发明确的学分上限问法。阶段 5 的 HTTP、会话与回答卡协议不变。

**Tech Stack:** Java 21、Spring Boot Web、JDBC/PostgreSQL、JUnit 5、AssertJ、MockMvc、原生 DOM API。

**Spec:** `docs/superpowers/specs/2026-09-15-public-elective-credit-limit-design.md`

## Global Constraints

- 只实现单学期公共选修课学分上限，不实现网络比例、退改选、缺课、相近课程或缓考。
- 不计算个人可选余额，不承诺选课资格、课程开设或当期例外。
- 仅查询 `STUDENT_HANDBOOK/PUBLIC_ELECTIVE_CREDIT_LIMIT` 范围内已发布、学生可见、分享权确认的资料。
- `ELECTIVE_RETAKE_AFTER_FAILURE` 待澄清优先；`公选课` 继续是重修澄清回复。
- 不新增 HTTP 路由、前端框架、Flyway 或会话持久化；不提交或推送。

---

### Task 1: 独立证据、导入入口与查询器

**Files:**
- Create: `knowledge/data/curation/2025版学生手册2025.9.2-公共选修单学期学分上限审核知识片段.json`
- Create: `knowledge/src/main/java/com/zhku/knowledge/conversation/publicelective/CreditLimitEvidence.java`
- Create: `knowledge/src/main/java/com/zhku/knowledge/conversation/publicelective/CreditLimitEvidenceFinder.java`
- Create: `knowledge/src/main/java/com/zhku/knowledge/conversation/publicelective/JdbcCreditLimitEvidenceFinder.java`
- Create: `knowledge/src/main/java/com/zhku/knowledge/conversation/publicelective/CreditLimitEvidenceImportCli.java`
- Create: `knowledge/src/test/java/com/zhku/knowledge/conversation/publicelective/CreditLimitEvidenceCurationTest.java`
- Create: `knowledge/src/test/java/com/zhku/knowledge/conversation/publicelective/JdbcCreditLimitEvidenceFinderIntegrationTest.java`

**Interfaces:** Produces `Optional<CreditLimitEvidence> CreditLimitEvidenceFinder.find()`; uses `ReviewedKnowledgePublisher.publishReplacingScope`。

- [ ] **Step 1: 写失败测试**

```java
assertThat(publication.chunks()).singleElement().satisfies(chunk -> {
    assertThat(chunk.sourceLocator()).isEqualTo("PDF 第 76—77 页，第 8、10 条");
    assertThat(chunk.content()).contains("每学期公共选修课最高不得超过 4 学分");
});
assertThat(finder.find()).hasValueSatisfying(evidence ->
    assertThat(evidence.sourceLocator()).isEqualTo("PDF 第 76—77 页，第 8、10 条"));
```

集成测试同时插入未发布、非学生可见和被新版本替代的记录，断言 `find()` 不返回它们。

- [ ] **Step 2: 运行并确认失败**

Run: `.\mvnw.cmd -f knowledge\pom.xml "-Dzhku.db.password=$env:ZHKU_DB_PASSWORD" "-Dtest=CreditLimitEvidenceCurationTest,JdbcCreditLimitEvidenceFinderIntegrationTest" test`

Expected: 缺少审核 JSON、查询器或导入入口而编译失败。

- [ ] **Step 3: 创建最小审核资料**

使用新的固定 `source_id`（`f4d0b0a0-c78a-4c7a-94e0-9b2942d0b8f5`），包含唯一片段：

```json
{
  "chunk_id": "public-elective-credit-limit-001",
  "chunk_index": 7608,
  "title": "公共选修课单学期学分上限",
  "source_locator": "PDF 第 76—77 页，第 8、10 条",
  "applicable_scope": "办理公共选修课程选课的本科生；个人培养方案与当期通知仍需核对",
  "evidence_summary": "学生应以人才培养方案为选课依据，在规定学习年限内修读并取得规定学分；每学期公共选修课最高不得超过 4 学分。",
  "answer_boundary": "不得据此计算个人剩余可选学分、确认课程开设或承诺选课资格。",
  "review_status": "审核通过",
  "student_visible": true
}
```

顶层 `student_visible`、`sharing_rights_confirmed` 均为 `true`，审核状态为 `审核通过`。

- [ ] **Step 4: 实现类型、查询与导入**

```java
public record CreditLimitEvidence(UUID sourceId, String sourceTitle,
        String sourceVersion, String sourceLocator,
        String applicableScope, String content) { }

@FunctionalInterface
public interface CreditLimitEvidenceFinder {
    Optional<CreditLimitEvidence> find();
}
```

查询 SQL 必须限制 `PUBLISHED`、`STUDENT_VISIBLE`、`sharing_rights_confirmed = TRUE`、资料族 `STUDENT_HANDBOOK`、范围 `PUBLIC_ELECTIVE_CREDIT_LIMIT` 与正文固定短语 `每学期公共选修课最高不得超过 4 学分`；按 `published_at DESC, chunk_index ASC`、`LIMIT 1`。SQL 异常包装为 `IllegalStateException("无法查询已发布的公共选修课学分上限证据", cause)`。导入入口调用：

```java
publisher.publishReplacingScope(publication,
        "STUDENT_HANDBOOK", "PUBLIC_ELECTIVE_CREDIT_LIMIT");
```

- [ ] **Step 5: 通过并暂存**

Run: `.\mvnw.cmd -f knowledge\pom.xml "-Dzhku.db.password=$env:ZHKU_DB_PASSWORD" "-Dtest=CreditLimitEvidenceCurationTest,JdbcCreditLimitEvidenceFinderIntegrationTest" test`

Then: `git add knowledge/data/curation/2025版学生手册2025.9.2-公共选修单学期学分上限审核知识片段.json knowledge/src/main/java/com/zhku/knowledge/conversation/publicelective knowledge/src/test/java/com/zhku/knowledge/conversation/publicelective`

Expected: 范围隔离、版本替换与学生可见过滤通过；不提交或推送。

### Task 2: 规则服务与重修优先路由

**Files:**
- Create: `knowledge/src/main/java/com/zhku/knowledge/conversation/publicelective/CreditLimitAnswer.java`
- Create: `knowledge/src/main/java/com/zhku/knowledge/conversation/publicelective/CreditLimitReply.java`
- Create: `knowledge/src/main/java/com/zhku/knowledge/conversation/publicelective/PublicElectiveCreditLimitService.java`
- Create: `knowledge/src/main/java/com/zhku/knowledge/conversation/publicelective/PublicElectiveConversationRouter.java`
- Create: `knowledge/src/test/java/com/zhku/knowledge/conversation/publicelective/PublicElectiveCreditLimitServiceTest.java`
- Create: `knowledge/src/test/java/com/zhku/knowledge/conversation/publicelective/PublicElectiveConversationRouterTest.java`

**Interfaces:** Produces `CreditLimitReply handle(String message)` 和 `ElectiveRetakeReply route(UUID sessionId, String message)`。

- [ ] **Step 1: 写失败测试**

```java
CreditLimitReply reply = service.handle("一学期最多能选多少公共选修课学分？");
assertThat(reply.type()).isEqualTo(ElectiveRetakeReplyType.ANSWER);
assertThat(reply.answer().conclusion()).isEqualTo("每学期公共选修课最高不得超过 4 学分。");
assertThat(reply.answer().sourceLocator()).isEqualTo("PDF 第 76—77 页，第 8、10 条");
assertThat(service.handle("网络公共选修课最多能占多少学分？").type())
        .isEqualTo(ElectiveRetakeReplyType.OUT_OF_SCOPE);
```

路由测试先创建 `ELECTIVE_RETAKE_AFTER_FAILURE` 待澄清，再发送 `公选课`，断言仍返回既有“不安排重修”回答。

- [ ] **Step 2: 运行并确认失败**

Run: `.\mvnw.cmd -f knowledge\pom.xml "-Dtest=PublicElectiveCreditLimitServiceTest,PublicElectiveConversationRouterTest" test`

Expected: 规则服务、回答类型或路由不存在。

- [ ] **Step 3: 实现规则服务**

```java
public CreditLimitReply handle(String message) {
    String normalized = normalize(message);
    if (!isCreditLimitQuestion(normalized)) {
        return CreditLimitReply.outOfScope();
    }
    return evidenceFinder.find().filter(this::supportsCreditLimit)
            .map(this::answerFrom).orElseGet(CreditLimitReply::evidenceUnavailable);
}
```

`isCreditLimitQuestion` 同时要求公共选修名称、`学分` 和 `每学期` / `一学期` / `最多` / `上限` 至少一词；长度不超过 500。个人资格问法可返回规则，但 `basis` 必须声明不能据口述确认已选学分、培养方案或当期限制。`CreditLimitAnswer` 固定 7 字段：结论、依据、来源 ID、标题、版本、定位、适用范围。

- [ ] **Step 4: 实现路由**

```java
public ElectiveRetakeReply route(UUID sessionId, String message) {
    StoreResult visible = store.find(sessionId, clock);
    if (visible.outcome() == StoreResult.Outcome.FOUND
            && visible.state().pendingIntent() == PendingIntent.ELECTIVE_RETAKE_AFTER_FAILURE) {
        return electiveRetakeService.handle(sessionId, message);
    }
    if (isElectiveRetakeQuestion(message)) {
        return electiveRetakeService.handle(sessionId, message);
    }
    return creditLimitService.handle(message).toElectiveRetakeReply();
}
```

`toElectiveRetakeReply()` 是既有门面未重构前的内部适配器，必须保留 `ANSWER`、`OUT_OF_SCOPE`、`EVIDENCE_UNAVAILABLE` 和完整回答卡；过期、取消或未知会话交给既有重修服务维持会话语义。

路由器自身实现与阶段 4 完全相同的重修首问判断：消息同时包含 `选修`、`不及格` / `挂科` /
`没及格` 之一及 `重修`。不能调用 `ElectiveRetakeConversationService` 的私有辅助方法，也不能改变其既有判断结果。

- [ ] **Step 5: 通过并暂存**

Run: `.\mvnw.cmd -f knowledge\pom.xml "-Dtest=ElectiveRetakeConversationServiceTest,PublicElectiveCreditLimitServiceTest,PublicElectiveConversationRouterTest" test`

Then: `git add knowledge/src/main/java/com/zhku/knowledge/conversation/publicelective/CreditLimitAnswer.java knowledge/src/main/java/com/zhku/knowledge/conversation/publicelective/CreditLimitReply.java knowledge/src/main/java/com/zhku/knowledge/conversation/publicelective/PublicElectiveCreditLimitService.java knowledge/src/main/java/com/zhku/knowledge/conversation/publicelective/PublicElectiveConversationRouter.java knowledge/src/test/java/com/zhku/knowledge/conversation/publicelective/PublicElectiveCreditLimitServiceTest.java knowledge/src/test/java/com/zhku/knowledge/conversation/publicelective/PublicElectiveConversationRouterTest.java`

Expected: 正例、反例、资料不足和重修优先通过；不提交或推送。

### Task 3: 阶段 5 门面、配置与 HTTP 回答卡接入

**Files:**
- Modify: `knowledge/src/main/java/com/zhku/knowledge/web/LocalPreviewConversationFacade.java`
- Modify: `knowledge/src/main/java/com/zhku/knowledge/web/ZhkuLocalPreviewConfiguration.java`
- Modify: `knowledge/src/test/java/com/zhku/knowledge/web/ZhkuLocalPreviewConversationControllerTest.java`
- Modify: `knowledge/src/test/java/com/zhku/knowledge/web/ZhkuLocalPreviewConfigurationTest.java`

**Interfaces:** Consumes `PublicElectiveConversationRouter.route(UUID, String)`；既有消息 API 返回新的 `ANSWER` 卡，不增加路由。

- [ ] **Step 1: 写失败的 HTTP 与配置测试**

```java
mockMvc.perform(post("/zhku/local-preview/conversations/{sessionId}/messages", sessionId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"message\":\"一学期最多能选多少公共选修课学分？\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.type").value("ANSWER"))
        .andExpect(jsonPath("$.answer.conclusion").value("每学期公共选修课最高不得超过 4 学分。"))
        .andExpect(jsonPath("$.answer.sourceLocator").value("PDF 第 76—77 页，第 8、10 条"));
```

另断言网络比例问法返回 `OUT_OF_SCOPE` 且不含 `answer`；启用 Profile 时有 `CreditLimitEvidenceFinder`、`PublicElectiveCreditLimitService`、`PublicElectiveConversationRouter` 单例。

- [ ] **Step 2: 运行并确认失败**

Run: `.\mvnw.cmd -f knowledge\pom.xml "-Dtest=ZhkuLocalPreviewConversationControllerTest,ZhkuLocalPreviewConfigurationTest" test`

Expected: 学分问法尚未路由或新 Bean 缺失。

- [ ] **Step 3: 最小接入**

门面依赖从 `ElectiveRetakeConversationService` 改为 `PublicElectiveConversationRouter`，发送调用为：

```java
return ConversationReply.from(conversationRouter.route(sessionId, message));
```

为 `CreditLimitAnswer` 增加到现有 `ConversationAnswer` 的逐字段转换。配置新增并共享原存储与 `Clock`：

```java
JdbcCreditLimitEvidenceFinder zhkuLocalPreviewCreditLimitEvidenceFinder();
PublicElectiveCreditLimitService zhkuLocalPreviewCreditLimitService(
        JdbcCreditLimitEvidenceFinder finder);
PublicElectiveConversationRouter zhkuLocalPreviewConversationRouter(
        ConversationStore store, ElectiveRetakeConversationService retakeService,
        PublicElectiveCreditLimitService creditLimitService, Clock clock);
```

- [ ] **Step 4: 通过并暂存**

Run: `.\mvnw.cmd -f knowledge\pom.xml "-Dtest=ConversationStateTest,ConversationStoreContractTest,ElectiveRetakeConversationServiceTest,PublicElectiveCreditLimitServiceTest,PublicElectiveConversationRouterTest,ZhkuLocalPreviewConversationControllerTest,ZhkuLocalPreviewConfigurationTest" test`

Then: `git add knowledge/src/main/java/com/zhku/knowledge/web/LocalPreviewConversationFacade.java knowledge/src/main/java/com/zhku/knowledge/web/ZhkuLocalPreviewConfiguration.java knowledge/src/test/java/com/zhku/knowledge/web/ZhkuLocalPreviewConversationControllerTest.java knowledge/src/test/java/com/zhku/knowledge/web/ZhkuLocalPreviewConfigurationTest.java`

Expected: 学分回答卡、取消、超时、重修两轮及 Profile 开关不回归。

### Task 4: 页面范围、规则契约与完整验收

**Files:**
- Modify: `knowledge/src/main/resources/zhku/local-preview/index.html`
- Modify: `knowledge/src/test/java/com/zhku/knowledge/web/ZhkuLocalPreviewPageResourceTest.java`
- Create: `docs/decisions/2026-09-15-public-elective-credit-limit-contract.md`

**Interfaces:** 复用既有回答卡 JSON、`conversationApi`、sessionStorage 与安全渲染。

- [ ] **Step 1: 写失败的页面资源测试**

```java
assertThat(page).contains("一学期最多能选多少公共选修课学分？");
assertThat(page).contains("个人培养方案与当期通知仍需核对");
assertThat(page).contains("textContent");
assertThat(page).doesNotContain("innerHTML");
```

- [ ] **Step 2: 运行并确认失败**

Run: `.\mvnw.cmd -f knowledge\pom.xml -Dtest=ZhkuLocalPreviewPageResourceTest test`

Expected: 页面未声明学分上限示例或个人资格边界。

- [ ] **Step 3: 更新页面与契约**

页面范围改为“当前支持选修课不及格后的重修咨询，以及单学期公共选修课学分上限”，新增：

```html
<button class="example-question" type="button"
        data-question="一学期最多能选多少公共选修课学分？">
    一学期最多能选多少公共选修课学分？
</button>
```

保留 `renderAnswer`、`requestEpoch`、sessionStorage 和 `textContent`。契约记录结论、范围、定位、个人资格边界、重修优先、反例、失效降级与非目标。

- [ ] **Step 4: 全量自动化与页面验收**

Run: `.\mvnw.cmd -f knowledge\pom.xml "-Dzhku.db.password=$env:ZHKU_DB_PASSWORD" test`

手工验证：标准问法显示完整来源；“已选 3 学分再选 2 学分”不承诺资格；网络比例不命中；重修澄清的“公选课”仍返回重修；专用证据失效时安全降级。

- [ ] **Step 5: 暂存验收材料**

Run: `git add knowledge/src/main/resources/zhku/local-preview/index.html knowledge/src/test/java/com/zhku/knowledge/web/ZhkuLocalPreviewPageResourceTest.java docs/decisions/2026-09-15-public-elective-credit-limit-contract.md`

Then: `git diff --cached --check`

Expected: 无空白错误；不提交或推送。

## Plan Self-Review

- Task 1 覆盖独立证据、发布过滤与版本替换。
- Task 2 覆盖规则识别、资料不足、反例与重修优先。
- Task 3 覆盖 HTTP、回答卡和 Profile 边界复用。
- Task 4 覆盖页面范围、自动化与人工验收。
- 没有新增动态通知、个人资格计算、额外路由、框架、Flyway 或会话持久化。
