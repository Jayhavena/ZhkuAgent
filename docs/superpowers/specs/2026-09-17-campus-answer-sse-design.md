# 校园问答 SSE 流式输出设计

> 状态：已按用户确认的方案 A 设计；实施前提为线 A 的统一 HTTP 装配已合入主线 `7414586`。

## 目标与范围

在既有本机预览会话消息路径 `POST /zhku/local-preview/conversations/{sessionId}/messages` 上，以内容协商追加 `text/event-stream` 响应。同步 JSON 响应保留原语义；请求 `Accept: text/event-stream` 时复用同一 `LocalPreviewConversationFacade` 和五级回答链路，不另建会话 Controller 或平行问答链路。

本次是**生命周期流**，而非模型 token 流：当前受控生成接口没有 token 回调，不能把一次最终回答误称为逐 token 生成。流会发出 `accepted`、`reply`、`complete` 三个事件，使调用方能先确认服务器已接收请求，再接收最终路由回答和明确结束标识。

## 事件契约

三个事件的数据均为 JSON，字段为：`sessionId`、`revision`、`conversationStatus`、`type`、`message`、`answer`、`route`、`handoffRequired`、`citationIds`。

- `accepted`：仅表示请求已排入本机流式任务；其 `revision` 与 `conversationStatus` 为入队前读取到的会话快照，`route` 和 `type` 为 `null`，`handoffRequired` 为 `false`，`citationIds` 为空。
- `reply`：由同一会话门面执行消息后发出，携带既有 JSON 响应含义及执行后读取到的会话快照。
- `complete`：重复最后一个 `reply` 数据，作为客户端无需依赖 TCP 断开即可确定流已结束的标识。

读取快照必须通过 `ConversationStore.find(sessionId, clock)`；`FOUND` 映射领域会话状态及 revision，其他存储结果映射为 `EXPIRED`、`CANCELLED` 或 `NOT_FOUND`。并发取消可能发生在回答后、快照前，事件报告服务器观察到的最新状态，不伪称消息处理时的状态。

## 组件与执行模型

`SseConversationStreamer` 负责创建 `SseEmitter`、在有界后台执行器中发送事件、处理发送失败和完成回调。它只调用 `LocalPreviewConversationFacade.sendMessage`，不直接修改会话状态或重新执行路由。

`ZhkuLocalPreviewStreamingConfiguration` 仅在 `zhku-local-preview` Profile 与 `zhku.local-preview.enabled=true` 下装配流式执行器和 streamer；`zhku.streaming.enabled=false` 时不创建 streamer，Controller 对 SSE 请求返回既有格式的服务不可用错误。执行器必须有界并在 Spring 关闭时停止，避免慢客户端无限占用请求资源。

`ZhkuLocalPreviewConversationController` 保留同一路径的同步 JSON 方法，并增加 `produces=text/event-stream` 的方法。同步方法显式声明 `application/json`，由 HTTP `Accept` 内容协商选择，不增加 `/stream` 等平行路径。

## 配置与安全边界

仅使用并已在冻结契约下追加登记：

- `zhku.streaming.enabled`（默认 `true`）
- `zhku.streaming.timeout-millis`（默认 `30000`）
- `zhku.streaming.worker-threads`（默认 `2`）
- `zhku.streaming.queue-capacity`（默认 `16`）

配置只置于已有 `zhku-local-preview` Profile；不添加模型密钥、不访问真实校务系统、不修改数据库或 Flyway。队列拒绝、客户端断开或发送失败只终止当前 emitter，绝不取消其他会话或篡改会话状态。

## 测试与诚实边界

先写失败测试，覆盖同一路径按 `Accept` 分派 SSE、`accepted → reply → complete` 顺序、回复事件中的冻结字段、禁用 streaming 时拒绝 SSE 请求、非法消息仍返回既有 400 JSON，以及执行器配置拒绝零/负数。

验证使用真实门面和会话存储的可控测试装配；对 `SseEmitter` 的传输写入使用仅在测试侧的记录 writer，断言本项目事件顺序与 JSON 数据，而非重复测试 Spring 的 SSE 格式化实现。

本次不能表述为“模型逐 token 流式生成”或“浏览器 UI 已升级”；前者尚无底层 token 回调，后者属于 WB-1。
