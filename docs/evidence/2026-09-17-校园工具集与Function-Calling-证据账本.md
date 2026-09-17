# 校园工具集与 Function Calling 契约证据账本

> 状态：**已实现，待合并**。本记录覆盖线 B 的低风险校园工具、框架无关注册接口和固定样例查询；不包含路由器改造、真实校务系统接入或模型供应商 Function Calling 适配。

## 交付范围与边界

| 交付 | 结果 | 安全边界 |
| --- | --- | --- |
| 工具注册契约 | `CampusTool`、`CampusToolDescriptor`、`CampusToolRegistry` 提供稳定描述与结构化调用/结果。 | 未注册工具返回 `UNKNOWN_TOOL`，不会猜测或执行。 |
| 学分工具 | 课程学分汇总、毕业学分进度均为纯计算。 | 不能确认课程归属、选课资格或毕业资格。 |
| 冲突检测 | 对传入时段的星期、节次、教学周交集做确定性检测。 | 不能确认真实课表、教室或选课资格。 |
| 样例查询 | 查询随代码发布的虚构 `SAMPLE_DATA` 资源。 | 没有匹配记录时返回 `DATA_UNAVAILABLE`，不推断真实校历或课表。 |
| 默认清单 | 固定注册四个经批准的工具，描述顺序按工具名称稳定输出。 | 仅为后续调用适配层准备；没有模型执行工具。 |

## TDD 记录

1. `ScheduleConflictToolTest` 先因工具类不存在而编译失败；实现后覆盖同课程忽略、星期/节次/周次交集、端点重叠、空或零周次和反向节次。
2. `SampleAcademicScheduleToolTest` 先因工具类不存在而编译失败；实现后覆盖固定来源标签、日期和关键词筛选、无数据拒绝、空学期与非法日期。
3. `CampusToolRegistryTest` 先因缺少 `withDefaultCampusTools()` 而编译失败；实现后验证四个工具名称及访问模式。
4. 聚焦回归额外发现 `Map.copyOf` 不保证 `TreeMap` 的迭代顺序，已改为保序的只读映射，避免工具描述在调用适配层漂移。

## 自动化验证

| 验证 | 结果 |
| --- | --- |
| `CampusToolRegistryTest, CourseCreditSummaryToolTest, GraduationCreditProgressToolTest, ScheduleConflictToolTest, SampleAcademicScheduleToolTest` | 19 项通过，0 失败，0 错误。 |
| `knowledge` 模块全量测试 | 190 项通过，0 失败，0 错误，1 项既有条件跳过。 |
| 临时数据库隔离 | 使用无挂载、自动删除的 pgvector PostgreSQL 容器，仅监听 `127.0.0.1:55432`；先迁移至 V12、导入三份既有审核资料并设定既有测试范围，验证后已关闭删除。未访问 `54329` 演示库。 |

## 诚实表述边界

可以表述为：校园 Agent 已具备四个只读/纯计算校园工具及稳定、可供后续适配器读取的工具注册契约。

不能表述为：已接入真实校务系统、已由模型发起 Function Calling、已改造五级路由，或已完成 SSE/前端答案卡。
