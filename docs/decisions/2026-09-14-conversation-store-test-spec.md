# 会话存储测试规范

## 目的与阶段边界

本规范是阶段 2 的可执行验收线，用于约束阶段 3 的 `InMemoryConversationStore`。阶段 2
只新增测试和规范；测试因实现尚不存在而红灯是预期状态，不能通过弱化断言、`@Disabled`
或伪造测试实现转绿。

## 固定约束

- 全部时间由 `Clock.fixed` 显式提供；禁止真实系统时间和 `Thread.sleep`。
- 有效期为 10 分钟，边界为 `now >= expiresAt`。
- 查询只读取，绝不刷新 `expiresAt`。
- 仅成功写入 pending 或成功清理 pending 才能将有效期刷新为操作时刻后 10 分钟。
- 普通写入必须匹配 `expectedRevision`；取消后的旧 revision 不能写回 pending。
- `CANCELLED` 是终态；重复取消返回同一可见快照且不刷新有效期。

## 契约用例矩阵

| 编号 | 行为 | 关键断言 |
| --- | --- | --- |
| S01 | 创建 | 首次 `CREATED`，返回新会话快照 |
| S02 | 重复创建 | `ALREADY_EXISTS`，原快照不被覆盖 |
| S03 | 隔离 | 一个 session 写 pending 不影响另一个 session |
| S04 | 读取 | 读取不续期；精确到期时返回 `EXPIRED` |
| S05 | 保存 pending | 成功后 revision 加一、有效期滑动 10 分钟 |
| S06 | 清理 pending | 成功后 pending 清空、revision 加一、有效期滑动 10 分钟 |
| S07 | 到期写入 | `now == expiresAt` 不能写入或续期 |
| S08 | 取消 | 幂等、清空 pending、不刷新有效期 |
| S09 | 取消后普通写入 | 返回 `CANCELLED`，会话不得复活 |
| S10 | 过期清理 | 只移除到期记录，返回移除数量 |
| S11 | 晚到请求 | 旧 revision 写回得到 `STALE_WRITE` 或 `CANCELLED`，最终状态仍为取消 |

## 运行与验收

阶段 2 的红灯命令：

```powershell
.\mvnw.cmd -f knowledge\pom.xml -Dtest=ConversationStoreContractTest test
```

预期失败原因是 `InMemoryConversationStore` 尚未提供。阶段 3 不得改写本套断言；其验收是该
命令与既有 `ConversationStateTest` 一并通过。

## 明确非目标

- 不实现 `InMemoryConversationStore` 或任何并发容器、锁、数据库实现。
- 不接入 HTTP、Controller、页面、回答卡或意图识别。
- 不修改 PostgreSQL、Flyway、资料文件、Docker volume 或运行配置。
