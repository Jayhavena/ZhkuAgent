# 校园问答 SSE 流式输出实施计划

> **执行方式：内联执行。** 每项先完成失败测试，随后最小实现，再跑该项与 knowledge 模块回归；不创建并行 HTTP 端点。

**目标：** 在既有 `POST /zhku/local-preview/conversations/{sessionId}/messages` 上通过 `Accept: text/event-stream` 协商输出 SSE；保留无 SSE `Accept` 时原有 JSON 响应。事件使用冻结字段 `sessionId`、`revision`、`conversationStatus`、`route`、`handoffRequired`，且不实现模型 token 级流。

**架构：** `ConversationStreamProcessor` 复用 `LocalPreviewConversationFacade` 运行完整回答链，并从 `ConversationStore` 取得发送前后快照。它把 `accepted → reply → complete` 交给 `ConversationStreamWriter`；`SseConversationStreamer` 仅负责使用受限线程池调度和将 writer 适配为 `SseEmitter`，不复制路由或修改会话状态。

**技术栈：** Spring MVC `SseEmitter`、Spring Boot 条件配置、JUnit 5、AssertJ、MockMvc。

---

## 任务 1：事件数据与处理器的领域契约

**文件：**
- 新建：`knowledge/src/main/java/com/zhku/knowledge/web/streaming/ConversationStreamEvent.java`
- 新建：`knowledge/src/main/java/com/zhku/knowledge/web/streaming/ConversationStreamWriter.java`
- 新建：`knowledge/src/main/java/com/zhku/knowledge/web/streaming/ConversationStreamProcessor.java`
- 新建：`knowledge/src/test/java/com/zhku/knowledge/web/streaming/ConversationStreamProcessorTest.java`

1. 先写失败测试：用内存会话存储、真实会话门面和记录 writer，断言事件顺序为 `accepted`、`reply`、`complete`；回复事件保留答案、路由、引用、转人工字段，三个事件都含会话 ID、版本和状态。
2. 运行：`mvn -pl knowledge -Dtest=ConversationStreamProcessorTest test`，确认测试因类型不存在或行为缺失而失败。
3. 最小实现：事件 record 固定字段；处理器在发送前和会话门面返回后读取快照，`accepted` 保持业务回复字段为空，`reply`/`complete` 复用同一回复。会话未找到时仍发送字段齐全、版本/状态为空的回复事件，避免悄然伪造状态。
4. 再运行同一命令，确认通过。

## 任务 2：SSE 传输与受限后台执行

**文件：**
- 新建：`knowledge/src/main/java/com/zhku/knowledge/web/streaming/SseConversationStreamer.java`
- 新建：`knowledge/src/main/java/com/zhku/knowledge/web/streaming/ZhkuLocalPreviewStreamingConfiguration.java`
- 新建：`knowledge/src/test/java/com/zhku/knowledge/web/streaming/SseConversationStreamerTest.java`
- 修改：`knowledge/src/main/resources/application-zhku-local-preview.yml`
- 修改：`knowledge/src/test/java/com/zhku/knowledge/web/ZhkuLocalPreviewConfigurationTest.java`

1. 先写失败测试：验证 `stream` 立即返回带超时的 `SseEmitter`，并把处理任务交给注入 executor；配置测试验证只在 `zhku-local-preview` 与 `zhku.local-preview.enabled=true` 时、且 `zhku.streaming.enabled=true` 时注册 streamer。
2. 运行对应测试，确认失败。
3. 最小实现：配置以冻结四项 `zhku.streaming.*` 创建固定工作线程与有界队列，显式拒绝策略避免无限积压；关闭 SSE 时不创建 bean。适配 writer 用 `SseEmitter.event().name(...).data(...)` 输出，发送失败时完成带错误的 emitter，正常事件完成后调用 `complete()`。
4. 写入本机 Profile 的四个显式配置默认值：启用、30 秒、2 个线程、16 项队列。
5. 运行对应测试，确认通过。

## 任务 3：在既有消息端点进行内容协商

**文件：**
- 修改：`knowledge/src/main/java/com/zhku/knowledge/web/ZhkuLocalPreviewConversationController.java`
- 修改：`knowledge/src/test/java/com/zhku/knowledge/web/ZhkuLocalPreviewConversationControllerTest.java`

1. 先写失败 MockMvc 测试：同一 POST 地址带 `Accept: text/event-stream` 返回 `200` 与 SSE 内容类型；没有该 Accept 时仍返回 JSON。空消息在 SSE 请求下仍返回既有 JSON `400`。
2. 运行 controller 测试，确认 SSE 断言失败。
3. 最小实现：把原同步映射显式限制为 `application/json`，新增同地址、`produces=text/event-stream` 的映射，使用 `ObjectProvider<SseConversationStreamer>`；流能力未启用时返回既有可读的 `503` JSON。请求校验与异常映射沿用原端点，禁止创建 `/stream` 等新地址。
4. 运行 controller 测试，确认通过。

## 任务 4：回归、证据与小步提交

1. 运行 `mvn -pl knowledge test`，必要时先运行针对性测试定位问题；只修复本 SSE 改动引入的问题。
2. `docs/evidence/` 目前由 WB-3 占用：在写入本 SSE 的证据账本前，先取得该行释放或“按不同文件并行”的明确登记；不得覆盖线 A 证据。
3. 更新状态板线 B 行为“待合并”并清除 SSE 文件占用，记录测试命令与结果；精确暂存本任务文件，核对 `git status --porcelain=v1`，提交但不推送。
