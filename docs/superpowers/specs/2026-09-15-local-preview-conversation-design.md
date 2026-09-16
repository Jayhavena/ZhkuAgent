# 阶段 5：本机预览会话闭环设计

## 目标与范围

在既有 `knowledge` 模块的本机预览 Profile 内，将首期“选修课不及格能否重修”接入 HTTP 和浏览器。
本阶段只服务 `ELECTIVE_RETAKE_AFTER_FAILURE`，复用阶段 1—4 的不可变会话状态、原子内存存储、
`Clock` 与已发布 PostgreSQL 证据查询。旧的通用资料检索接口
`POST /zhku/local-preview/answers` 保留兼容，但预览页面改用新的会话接口。

不引入 Agent 框架、数据库会话持久化、登录身份、流式协议或新的制度领域。

## 组件与边界

### 会话门面

新增仅供本机预览调用的会话门面，负责：

- 创建服务端生成的 UUID 会话，默认有效期为 10 分钟；
- 在调用领域服务前确认 session 存在，未知 ID 返回 `SESSION_INVALID`，不得由 HTTP 请求隐式创建；
- 调用 `ElectiveRetakeConversationService` 并映射其有限回复类型；
- 取消时按当前 revision 重试条件取消，直到得到 `CANCELLED`，使重复取消保持幂等；
- 将已过期、已取消、陈旧请求和证据不足转换为受控响应，不能暴露数据库异常细节。

会话门面依赖 `ConversationStore`、`Clock`、`ElectiveRetakeConversationService`；Controller 只承担
JSON 与 HTTP 状态映射，不直接访问 PostgreSQL 或 `ConcurrentHashMap`。

### HTTP 契约

所有接口沿用 `/api/zhku/local-preview` context path，并继续受 `zhku-local-preview` Profile 和
`zhku.local-preview.enabled=true` 双重限制。

| 操作 | 方法与路径 | 成功响应 | 说明 |
| --- | --- | --- | --- |
| 新对话 | `POST /conversations` | `sessionId`、`expiresAt` | 由服务端创建 UUID，不接受浏览器指定 ID。 |
| 发送消息 | `POST /conversations/{sessionId}/messages` | `type`、`message`、可选 `answer` | `answer` 仅在 `ANSWER` 时包含结论、依据、来源、版本、条款定位和适用范围。 |
| 取消对话 | `POST /conversations/{sessionId}/cancel` | `SESSION_CANCELLED` | 重复请求保持相同可见结果。 |

有效的学生可见结果类型为：`NEED_CLARIFICATION`、`ANSWER`、`EVIDENCE_UNAVAILABLE`、
`SESSION_EXPIRED`、`SESSION_CANCELLED`、`SESSION_INVALID`、`OUT_OF_SCOPE`。HTTP 200 表示请求被
领域层处理；格式错误使用 400；未预期基础设施失败使用 503 且不返回内部原因。

### 预览配置

`ZhkuLocalPreviewConfiguration` 增加：受控 `Clock`、`InMemoryConversationStore`、
`JdbcElectiveRetakeEvidenceFinder`、会话服务与门面 Bean。所有 Bean 只能在现有本机 Profile 与显式
开关同时满足时创建，默认应用不注册 Controller 或连接资料库。

## 浏览器状态与竞态

页面只在 `sessionStorage` 保存 `sessionId` 和最后一次澄清提示的展示信息；不保存回答卡、制度原文或
来源内容。刷新后可恢复“正在等待课程类型”的提示，但下一条消息必须由后端重新判断会话是否仍有效。

每次创建新对话或发送消息均递增本地 `requestEpoch`。请求返回时，只有其 epoch 仍等于当前值才允许
更新页面；新对话先使旧 epoch 失效、清除本地 session，再尽力取消旧 session，最后创建并保存新 session。
因此旧响应晚到不能覆盖新对话的提示、答案或 sessionStorage。

页面通过 `textContent` 和显式 DOM 节点构造展示结论、依据和来源；禁止 `innerHTML`。取消、超时或
`SESSION_INVALID` 会清理本地 session 与恢复提示，不伪造旧结论。

## 流程

```text
打开或新建对话
  -> POST /conversations
  -> sessionStorage 保存 sessionId
  -> POST /conversations/{id}/messages
  -> NEED_CLARIFICATION：仅保存并展示澄清提示
  -> ANSWER：展示后端回答卡，清除澄清提示

新对话
  -> requestEpoch + 1
  -> 清除旧本地状态
  -> POST /conversations/{oldId}/cancel（结果不更新当前 UI）
  -> POST /conversations
```

## 测试与验收

1. HTTP 契约测试：创建、模糊首问、课程类型回答、回答卡完整来源字段、未知 session、取消幂等、10 分钟边界。
2. 配置边界测试：默认 Profile 不注册会话 Controller、服务、存储或 JDBC 证据查询器。
3. 页面资源测试：请求使用会话 API、使用 `sessionStorage` 和 epoch、仍无 `innerHTML`。
4. 本机验收：页面完成“选修课没及格，还能重修吗？”两轮回答；刷新后恢复澄清提示；取消与超时清除会话；
   以延迟旧请求验证其响应不能污染新对话。
5. 回归：阶段 1—4 的 28 项定向测试和 PostgreSQL 证据查询继续通过。

## 非目标

- 不让浏览器直接访问 PostgreSQL、证据导入入口或通用资料治理接口；
- 不对补考资格、缓考审批、课程开设或个人重修申请作出承诺；
- 不替换或扩大旧通用资料检索接口；
- 不实现跨浏览器持久会话、账号体系或生产部署能力。
