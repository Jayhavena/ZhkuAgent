# JDBC 会话存储设计

## 目标

为既有 `ConversationStore` 增加 PostgreSQL 实现，使会话状态可跨进程保存，同时保持当前
`InMemoryConversationStore` 的行为契约不变。本交付只提供持久化底座与选择工厂，不切换本机
预览的运行时 Bean。

## 范围与非目标

- 范围：`JdbcConversationStore`、Flyway `V12`、数据库契约测试、`ConversationStoreFactory`、证据账本。
- 不改 `ZhkuLocalPreviewConfiguration`、Controller、页面、SSE、路由器或既有迁移。
- 默认运行行为继续由既有预览配置创建内存存储；`jdbc` 仅是后续配置切换可使用的显式选择。

## 数据模型

新增 `conversation_session`：

- `session_id UUID` 为主键；
- `status`、`pending_intent`、`pending_field`、`expires_at`、`revision` 对应不可变领域快照；
- 约束待补意图与字段必须同时为空或同时非空，且取消状态不能保留待补信息；
- 为 `expires_at` 建索引，过期清理不扫描无关会话。

表不保存问题、回答、资料正文或密钥，避免把本次只需保存的会话控制状态扩大成对话内容留存。

## 并发与到期语义

`savePending`、`clearPending` 与首次取消使用带 `session_id`、`revision`、`ACTIVE` 和
`expires_at > Clock.instant()` 条件的 `UPDATE ... RETURNING`。因此校验和写入由数据库在同一语句中
完成，晚到请求不能覆盖已取消或已更新的状态。

若条件更新未命中，仓储读取当前快照并按既有优先级返回 `NOT_FOUND`、`EXPIRED`、`CANCELLED` 或
`STALE_WRITE`；重复取消直接返回当前取消快照。`clearExpired` 只删除 `expires_at <= Clock.instant()`
的记录。所有时间都来自调用者传入的 `Clock`，不读取系统时间。

## 显式选择预留

`ConversationStoreFactory` 约定 `zhku.conversation.store=jdbc` 创建 JDBC 存储；该属性缺失或为空时
创建内存存储，其他值快速失败。工厂在本交付中不注册 Spring Bean；后续配置切换必须先在同步状态板
登记并单独提交，以免与线 A 对 `ZhkuLocalPreviewConfiguration` 的修改冲突。

## 测试与验收

- 保留内存版 S01–S11 契约测试不变；
- 新增 JDBC 集成契约测试，覆盖创建、重复创建、隔离、到期、滑动过期、幂等取消、取消后写入、过期
  清理及旧 revision；
- 新增 schema 断言，验证 V12 表、约束和过期索引；
- 新增工厂测试，验证 `jdbc` 显式选择和默认内存回退；
- 测试使用现有本机 PostgreSQL 配置与随机会话 ID，不修改数据库卷或已有资料。

## 风险控制

数据库实现不得放宽领域状态约束，也不得通过“先查后写”实现乐观并发；后者会使取消请求与旧写入
之间出现会话复活窗口。运行时接线刻意后置，避免持久化底座尚未通过契约测试时改变当前可验收链路。
