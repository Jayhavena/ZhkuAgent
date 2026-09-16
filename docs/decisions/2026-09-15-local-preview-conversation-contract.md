# 阶段 5：本机预览会话闭环契约

## 适用范围

本契约只适用于 `zhku-local-preview` Profile 且 `zhku.local-preview.enabled=true` 的本机回环预览，
只支持“选修课不及格能否重修”的两轮咨询。它不构成学生端公开接口，也不扩展其他制度问题。

## HTTP 契约

| 操作 | 路径 | 成功结果 |
| --- | --- | --- |
| 创建 | `POST /api/zhku/local-preview/conversations` | `sessionId`、ISO-8601 `expiresAt` |
| 发送消息 | `POST /api/zhku/local-preview/conversations/{sessionId}/messages` | `NEED_CLARIFICATION`、`ANSWER` 或会话状态结果 |
| 取消 | `POST /api/zhku/local-preview/conversations/{sessionId}/cancel` | `SESSION_CANCELLED`，重复取消仍返回该结果 |

`ANSWER` 必须携带结论、依据、来源 ID、来源标题、版本、条款定位和适用范围。空消息为
`400 INVALID_MESSAGE`；未知 ID 只返回 `SESSION_INVALID`，不得由一条消息隐式创建会话；内部资料服务
不可用时只返回 `503 KNOWLEDGE_SERVICE_UNAVAILABLE`，不泄露连接细节。

## 浏览器状态与竞态规则

- `sessionStorage` 仅保存 `sessionId` 与待澄清提示，不保存回答卡或来源证据。
- 刷新后只恢复已保存的待澄清展示；下一条消息仍由服务端检查会话是否有效。
- 每次异步操作分配递增的 `requestEpoch`；只有当前编号的响应才可更新页面。
- “开始新对话”先递增编号、清除浏览器状态并取消旧会话，旧会话取消成功后才创建新会话。
- 会话超时、取消或不存在时清除浏览器保存状态，并提示用户开始新对话。
- 所有可见文本均通过 DOM `textContent` 写入；禁止使用 `innerHTML`。

## 验收场景

| 场景 | 预期 |
| --- | --- |
| 模糊首问 | 返回 `NEED_CLARIFICATION` 和课程类别追问 |
| 回答“公选课” | 返回 `ANSWER`，结论为不安排重修并显示完整来源字段 |
| 回答“专业选修课” | 返回“可申请重修学习”，不得承诺审批、开课或结果 |
| 刷新待澄清页 | 仅恢复澄清提示和会话 ID |
| 取消两次 | 两次都返回 `SESSION_CANCELLED` |
| 超过 10 分钟 | 返回 `SESSION_EXPIRED`，页面清理保存的会话 |
| 旧响应晚到 | 不得覆盖新对话的页面状态 |

## 明确非目标

不新增前端框架、数据库会话持久化、RAG/Agent 编排框架、其他制度域或学生端公开部署；不从“未安排重修”
推导补考、缓考或审批资格结论。动态事项仍必须以当期有效通知为依据。
