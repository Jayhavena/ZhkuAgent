# 会话状态契约

## 目标

本契约为“选修课不及格能否重修”的两轮澄清链路建立纯 Java 会话模型。它不包含意图识别、
回答卡、HTTP、前端或任何存储实现。

## 合法状态与转换

| 当前状态 | pendingIntent / pendingField | 允许转换 |
| --- | --- | --- |
| ACTIVE | 同时为空 | 写入成对的待补信息、取消 |
| ACTIVE | 同时存在 | 覆盖待补信息、成功清理待补信息、取消 |
| CANCELLED | 同时为空 | 仅允许幂等取消 |

`EXPIRED` 不作为状态保存。任何操作使用 `Clock` 判断 `now >= expiresAt`；读取不应延长
有效期，写入待补信息和成功清理待补信息才由后续存储实现提供新的过期时间。

## 并发约束

每次有效状态变更递增 `revision`。存储写入必须要求调用方提供 `expectedRevision`，并在同一
原子步骤内检查版本、状态和过期时间。会话已取消时，普通写入返回 `CANCELLED` 或
`STALE_WRITE`，绝不能重新创建待补状态。

## 非目标

- 不实现 `InMemoryConversationStore`、并发容器或持久化。
- 不连接 PostgreSQL、修改 Flyway、读取资料或创建运行时目录。
- 不新增 Controller、页面、回答卡、制度结论或其他公共选修课规则。
