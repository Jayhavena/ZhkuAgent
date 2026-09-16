# 阶段 5 本机预览会话闭环 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在既有本机预览 Profile 中提供可刷新、可取消、可抗旧响应竞态的首期两轮制度问答页面。

**Architecture:** 新增 `LocalPreviewConversationFacade` 隔离 HTTP 与阶段 1—4 领域模型；它持有
`ConversationStore`、`Clock` 和 `ElectiveRetakeConversationService`，负责创建、已知 ID 校验及幂等取消。
Controller 仅映射 JSON，现有静态预览页使用 `sessionStorage` 和单调递增的 `requestEpoch` 调用会话接口。

**Tech Stack:** Java 21、Spring Boot Web、MockMvc、JUnit 5、AssertJ、原生浏览器 DOM API、PostgreSQL JDBC。

**Spec:** `docs/superpowers/specs/2026-09-15-local-preview-conversation-design.md`

## Global Constraints

- 只支持 `ELECTIVE_RETAKE_AFTER_FAILURE`，不添加新制度领域、RAG 或 Agent 框架。
- 所有新 Web 组件复用 `zhku-local-preview` Profile 与 `zhku.local-preview.enabled=true` 双重开关。
- 继续使用服务端 UUID、不可变状态、`Clock` 和 revision；浏览器不得直连数据库。
- 浏览器只保存 `sessionId` 与澄清提示，所有文本以 `textContent` 渲染，禁止 `innerHTML`。
- 保留 `POST /zhku/local-preview/answers` 及现有测试；不提交或推送 Git 历史。

---

### Task 1: 会话门面与受控 HTTP 契约

**Files:**
- Create: `knowledge/src/main/java/com/zhku/knowledge/web/LocalPreviewConversationFacade.java`
- Create: `knowledge/src/main/java/com/zhku/knowledge/web/ZhkuLocalPreviewConversationController.java`
- Create: `knowledge/src/test/java/com/zhku/knowledge/web/ZhkuLocalPreviewConversationControllerTest.java`

**Interfaces:**
- Consumes: `ConversationStore`, `ConversationState.active(UUID, Instant)`, `ElectiveRetakeConversationService.handle(UUID, String)`.
- Produces: `createConversation()`, `sendMessage(UUID, String)`, `cancelConversation(UUID)` and `/zhku/local-preview/conversations` HTTP routes.

- [ ] **Step 1: 写入失败的 HTTP 契约测试**

```java
mockMvc.perform(post("/zhku/local-preview/conversations"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.sessionId").isNotEmpty())
        .andExpect(jsonPath("$.expiresAt").isNotEmpty());

mockMvc.perform(post("/zhku/local-preview/conversations/{id}/messages", sessionId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"message\":\"选修课没及格，还能重修吗？\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.type").value("NEED_CLARIFICATION"));
```

同时覆盖：第二条“公选课”回复的 `ANSWER` 与来源字段、未知 UUID 的 `SESSION_INVALID`、空消息的
400 `INVALID_MESSAGE`、重复取消的 `SESSION_CANCELLED`。

- [ ] **Step 2: 运行测试确认其因类与路由不存在而失败**

Run: `./mvnw.cmd -f knowledge/pom.xml -Dtest=ZhkuLocalPreviewConversationControllerTest test`

Expected: 编译失败，提示 `ZhkuLocalPreviewConversationController` 或会话路由尚不存在。

- [ ] **Step 3: 实现最小会话门面**

```java
public LocalPreviewConversationReply sendMessage(UUID sessionId, String message) {
    StoreResult visible = store.find(sessionId, clock);
    if (visible.outcome() == StoreResult.Outcome.NOT_FOUND) {
        return LocalPreviewConversationReply.sessionInvalid();
    }
    return LocalPreviewConversationReply.from(service.handle(sessionId, message));
}
```

创建操作必须以 `ConversationState.active(UUID.randomUUID(), clock.instant().plus(TEN_MINUTES))` 调用
`store.create`；取消操作每次读取可见 revision 并条件取消，遇到 `STALE_WRITE` 时重新读取，直到返回
`CANCELLED`、`EXPIRED`、`NOT_FOUND` 或不可继续的状态。回答卡必须直接复制
`ElectiveRetakeAnswer` 的结论、依据、来源 ID、标题、版本、条款定位和适用范围。

- [ ] **Step 4: 实现 Controller 与 DTO**

```java
@PostMapping("/conversations")
public ResponseEntity<ConversationCreatedResponse> create();

@PostMapping("/conversations/{sessionId}/messages")
public ResponseEntity<?> message(@PathVariable UUID sessionId,
                                 @RequestBody ConversationMessageRequest request);

@PostMapping("/conversations/{sessionId}/cancel")
public ResponseEntity<ConversationReplyResponse> cancel(@PathVariable UUID sessionId);
```

Controller 保持 `JSON_UTF8`；只把参数格式错误和空消息映射为 400，把未预期的
`IllegalStateException` 映射为不泄露内部细节的 503。领域返回类型均使用 HTTP 200。

- [ ] **Step 5: 运行测试确认通过**

Run: `./mvnw.cmd -f knowledge/pom.xml -Dtest=ZhkuLocalPreviewConversationControllerTest test`

Expected: 创建、两轮回答、来源字段、未知 ID、取消幂等和非法消息全部通过。

- [ ] **Step 6: 暂存本任务文件，不提交或推送**

Run: `git add knowledge/src/main/java/com/zhku/knowledge/web/LocalPreviewConversationFacade.java knowledge/src/main/java/com/zhku/knowledge/web/ZhkuLocalPreviewConversationController.java knowledge/src/test/java/com/zhku/knowledge/web/ZhkuLocalPreviewConversationControllerTest.java`

Expected: 文件处于暂存区；不执行 `git commit` 或 `git push`。

### Task 2: 本机 Profile 的会话依赖装配边界

**Files:**
- Modify: `knowledge/src/main/java/com/zhku/knowledge/web/ZhkuLocalPreviewConfiguration.java`
- Modify: `knowledge/src/test/java/com/zhku/knowledge/web/ZhkuLocalPreviewConfigurationTest.java`

**Interfaces:**
- Consumes: `LocalKnowledgePublisherFactory.createDataSource()`, `JdbcElectiveRetakeEvidenceFinder`, `InMemoryConversationStore`.
- Produces: 本机 Profile 内唯一的 `Clock`、`ConversationStore`、证据查询器、领域服务与门面 Bean。

- [ ] **Step 1: 写入失败的配置边界测试**

```java
new ApplicationContextRunner()
        .withUserConfiguration(ZhkuLocalPreviewConfiguration.class)
        .run(context -> assertThat(context)
                .doesNotHaveBean(LocalPreviewConversationFacade.class)
                .doesNotHaveBean(ZhkuLocalPreviewConversationController.class));
```

另增加启用 Profile 与 `zhku.local-preview.enabled=true` 时可装配门面的测试，使用受控测试数据源，
不读取真实系统时间。

- [ ] **Step 2: 运行测试确认失败**

Run: `./mvnw.cmd -f knowledge/pom.xml -Dtest=ZhkuLocalPreviewConfigurationTest test`

Expected: 启用场景因缺少会话 Bean 而失败；默认场景仍通过。

- [ ] **Step 3: 最小配置实现**

```java
@Bean Clock zhkuLocalPreviewClock() { return Clock.systemUTC(); }

@Bean ConversationStore zhkuLocalPreviewConversationStore() {
    return new InMemoryConversationStore();
}
```

使用同一 `Clock` 装配 `JdbcElectiveRetakeEvidenceFinder`、`ElectiveRetakeConversationService`
（10 分钟 TTL）与 `LocalPreviewConversationFacade`。所有新增 Bean 保持类级现有 Profile 与属性条件，
不得加入默认 `knowledge` 应用。

- [ ] **Step 4: 运行配置测试确认通过**

Run: `./mvnw.cmd -f knowledge/pom.xml -Dtest=ZhkuLocalPreviewConfigurationTest,ZhkuLocalPreviewConversationControllerTest test`

Expected: 默认不装配，受控预览环境装配；接口契约回归通过。

- [ ] **Step 5: 暂存本任务文件，不提交或推送**

Run: `git add knowledge/src/main/java/com/zhku/knowledge/web/ZhkuLocalPreviewConfiguration.java knowledge/src/test/java/com/zhku/knowledge/web/ZhkuLocalPreviewConfigurationTest.java`

Expected: 仅阶段 5 配置与测试进入暂存区。

### Task 3: 预览页会话状态、刷新恢复与安全渲染

**Files:**
- Modify: `knowledge/src/main/resources/zhku/local-preview/index.html`
- Modify: `knowledge/src/test/java/com/zhku/knowledge/web/ZhkuLocalPreviewPageResourceTest.java`

**Interfaces:**
- Consumes: `POST /api/zhku/local-preview/conversations`, `/conversations/{id}/messages`, `/conversations/{id}/cancel`.
- Produces: 浏览器 `sessionStorage` 键、`requestEpoch`、新对话按钮与结论/依据/来源回答卡展示。

- [ ] **Step 1: 写入失败的页面约束测试**

```java
assertThat(page).contains("/api/zhku/local-preview/conversations");
assertThat(page).contains("sessionStorage");
assertThat(page).contains("requestEpoch");
assertThat(page).contains("新对话");
assertThat(page).contains("textContent");
assertThat(page).doesNotContain("innerHTML");
```

测试不应仅验证字符串；同时读取页面资源后验证页面已有发送控件、澄清提示容器、回答卡来源容器和新对话
控件均被声明，防止脚本找不到关键 DOM 节点。

- [ ] **Step 2: 运行测试确认失败**

Run: `./mvnw.cmd -f knowledge/pom.xml -Dtest=ZhkuLocalPreviewPageResourceTest test`

Expected: 缺少会话 API、sessionStorage、epoch 与新对话控件断言。

- [ ] **Step 3: 最小页面实现**

```javascript
let requestEpoch = 0;
const sessionKey = 'zhku.local-preview.session-id';

function isCurrent(epoch) { return epoch === requestEpoch; }

function renderText(element, value) {
  element.textContent = value;
}
```

首次加载无 session 时创建会话。澄清回复只保存并显示提示；`ANSWER` 用 `textContent` 分别写入结论、
依据、来源标题、版本、条款定位和适用范围，随后删除澄清提示。刷新仅恢复保存的澄清提示；发送下一条
消息始终由服务端校验 session。新对话先递增 epoch、清除界面与 sessionStorage、取消旧会话（响应不渲染），
再创建新会话。每个异步响应在改变 UI 前检查 `isCurrent(epoch)`。

- [ ] **Step 4: 运行页面测试确认通过**

Run: `./mvnw.cmd -f knowledge/pom.xml -Dtest=ZhkuLocalPreviewPageResourceTest test`

Expected: 页面绑定新会话 API，声明恢复与竞态隔离机制，仍不存在 `innerHTML`。

- [ ] **Step 5: 暂存页面与测试，不提交或推送**

Run: `git add knowledge/src/main/resources/zhku/local-preview/index.html knowledge/src/test/java/com/zhku/knowledge/web/ZhkuLocalPreviewPageResourceTest.java`

Expected: 页面改动可与 HTTP 契约独立审阅。

### Task 4: 阶段 5 验收说明与定向回归

**Files:**
- Create: `docs/decisions/2026-09-15-local-preview-conversation-contract.md`
- Modify: `knowledge/src/test/java/com/zhku/knowledge/web/ZhkuLocalPreviewApplicationTest.java`

**Interfaces:**
- Consumes: 阶段 5 HTTP 契约、页面 sessionStorage 语义、既有本机 Profile 开关。
- Produces: 可复核的验收清单及启动入口保护回归。

- [ ] **Step 1: 写入失败的启动边界测试**

```java
assertThat(ZhkuLocalPreviewApplication.class)
        .hasAnnotation(SpringBootApplication.class);
```

补充应用启动测试：没有 `zhku.local-preview.enabled=true` 时会话 Controller 不可调用；专用启动入口继续自动
附加 `zhku-local-preview` Profile。该测试必须验证运行行为或 Spring context，不只匹配源文件文本。

- [ ] **Step 2: 运行测试确认失败**

Run: `./mvnw.cmd -f knowledge/pom.xml -Dtest=ZhkuLocalPreviewApplicationTest test`

Expected: 阶段 5 Controller 尚未纳入启动边界验收时失败。

- [ ] **Step 3: 编写阶段 5 契约说明并完成必要实现调整**

文档必须固定：API 路径和返回类型、浏览器保存边界、刷新语义、旧响应忽略规则、取消与超时的 UI 行为、
安全渲染要求、7 类验收场景与明确非目标。若启动测试暴露遗漏，只调整本机 Profile 配置，不扩大普通应用。

- [ ] **Step 4: 运行完整定向验收**

Run: `./mvnw.cmd -f knowledge/pom.xml "-Dzhku.db.password=$env:ZHKU_DB_PASSWORD" "-Dtest=ConversationStateTest,ConversationStoreContractTest,ElectiveRetakeConversationServiceTest,ElectiveRetakeEvidenceCurationTest,JdbcElectiveRetakeEvidenceFinderIntegrationTest,ZhkuLocalPreviewConversationControllerTest,ZhkuLocalPreviewConfigurationTest,ZhkuLocalPreviewPageResourceTest,ZhkuLocalPreviewApplicationTest" test`

Expected: 阶段 1—5 领域、HTTP、配置、页面资源和 PostgreSQL 证据查询全部通过；若本机密码未配置，记录为
环境阻塞，不修改测试以跳过数据库验收。

- [ ] **Step 5: 执行本机页面验收**

在受控 Profile 与开关启动的本机预览页面完成：模糊首问、公共选修课回答、刷新澄清提示、取消、10 分钟
超时、新对话与延迟旧响应。逐项记录 HTTP 响应类型、回答卡来源字段和旧响应未改变 UI 的观察结果。

- [ ] **Step 6: 暂存验收材料并检查差异，不提交或推送**

Run: `git add docs/decisions/2026-09-15-local-preview-conversation-contract.md knowledge/src/test/java/com/zhku/knowledge/web/ZhkuLocalPreviewApplicationTest.java && git diff --cached --check`

Expected: 无空白错误；不执行 `git commit` 或 `git push`。

## Plan Self-Review

- 覆盖检查：任务 1 实现 HTTP 与会话门面；任务 2 锁定 Profile/开关；任务 3 实现浏览器恢复、竞态与安全渲染；
  任务 4 固化验收与回归。
- 占位检查：没有未完成占位符或模糊步骤；每个实现任务都有预期失败测试、最小实现与通过验证。
- 类型一致性：门面向 Controller 输出受控回复；配置只装配门面依赖；页面只消费 Controller 三条路由。
