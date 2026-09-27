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

## 2026-09-27 线 B 补充：模型工具调用最小纵切

状态：**离线契约通过，真实调用未验证**。上述初始交付记录保持原有时间口径；本节只记录新增纵切。

- 使用现有 Spring AI **1.0.9** 的 OpenAI 兼容对话模型接口，读取现有 `application-zhku-local-preview.yml` 中的激活 profile，并通过既有 `ApiKeyResolver` 从环境变量取密钥。对模型只声明 `summarize_course_credits`；`internalToolExecutionEnabled(false)` 使模型返回原生 `AssistantMessage.ToolCall` 后由服务端检查。回调本身拒绝直接执行，实际调用只经单工具 `CampusToolRegistry`。
- `CourseCreditSummaryTool` 的 schema 已补足 `courses[]` 每项必填的 `courseId`、`category`、`credits` 及非负数要求；模型参数先按计算器的同一解析契约校验，再进入注册表。每轮最多接受 **1** 个工具调用；超时取现有 profile 的 `timeout-seconds`。未知工具、非法参数、无工具调用、多调用、超时和供应商失败均为受控状态，不输出猜测的学分。
- 成功结果保留原工具的边界提示；命令行只输出工具名、状态、计算 JSON 与边界提示，不记录原始问题、身份、密钥或供应商异常原文。核对 Spring AI 1.0.9 源码发现空响应 WARN 分支会打印 Prompt，本机入口在读取问题前关闭 `org.springframework.ai` 日志；若日志实现不受控则拒绝调用。仅供本机 CLI 使用，未接页面、教务系统、选课或毕业认定。

测试先行记录：新服务测试最初因类缺失编译失败；实现后 schema 字段断言仍红；补 schema 后，非法参数仍触发注册表执行器的计数测试红（预期 0，实际 1）；改用同一解析契约预检后转绿。

| 验证 | 实际结果 |
| --- | --- |
| 离线聚焦：`CourseCreditModelCallServiceTest,CourseCreditSummaryToolTest,CampusToolRegistryTest` | **18/18 通过，0 失败，0 错误**；涵盖真实 ToolCall 结构、单工具暴露、未知/非法拒绝、无调用、供应商失败、超时、跨模型候选的次数上限、边界提示。 |
| 默认 CLI | 无 `--live` 时输出 `DISABLED`，不建立模型连接。 |
| 缺密钥 CLI | 显式 `--live` 后在配置/密钥守卫退出，输出 `CONFIG_UNAVAILABLE`；当前进程与用户级 `DASHSCOPE_API_KEY` 均不存在，未触达供应商。 |
| knowledge 全模块（离线） | **404 运行 / 8 失败 / 84 错误 / 4 跳过**，不能称全绿。当前进程及用户级 `ZHKU_DB_PASSWORD` 均缺失，数据库相关用例无法装配；此外 `GracefulDegradationTest.shouldSafelyRefuseAndClassifyModelTimeoutAsModelDegraded` 为状态板已登记的 B-4 既有红例（实际 `RETRIEVAL_MISS`，预期 `MODEL_DEGRADED`，归线 A 修复）。 |
| 真实供应商冒烟 | **未执行**：缺 `DASHSCOPE_API_KEY`。没有观察到供应商 tool-call 响应，因此不写“真实模型工具调用已验证”。 |

复现命令与输入方式见 [本机模型工具调用演示](../zhkuagent/本机模型工具调用演示.md)。本批可以表述为“供应商原生工具调用接口的离线契约和服务端受控执行已实现”；**不能表述为“真实供应商调用已验证”**。

## 2026-09-27 线 B 补充：真实 Spring AI 到本机假供应商的协议合同

状态：**本机供应商协议合同已验证；真实 DashScope tool-call 仍因缺少 DASHSCOPE_API_KEY 未验证**。本节是对上节 ChatModel 替身测试的补证，不改变上节当时的验证口径。

API 核对：先经 Context7 查 Spring AI 1.0.x 官方手动工具执行示例（可用版本为 1.0.3，未单列 1.0.9），再对照本机锁定的 **1.0.9 sources.jar** 确认选项合并、`tools` 请求字段与 `tool_calls` 响应映射；最终以本机 HTTP 合同实测为准。

先写 `CourseCreditSpringAiProtocolContractTest`，首次运行因缺少 `CourseCreditChatModelFactory` 编译失败。随后将 CLI 中原有的 `OpenAiApi` / `OpenAiChatModel` 构建逻辑原样抽至此工厂，CLI 与合同测试共用同一份 base URL 版本段处理和模型选项；没有新增供应商配置或密钥来源。

合同使用 JDK `HttpServer` 绑定 `127.0.0.1` 随机端口、假密钥和标准 OpenAI-compatible 响应。成功用例实际接收 Spring AI 1.0.9 发出的请求，核对 `tools` **恰好一个**、名称为 `summarize_course_credits`，且参数 schema 包含 `courses[]` 每项必填的 `courseId/category/credits`、类型及非负数约束；假端点回 `tool_calls` 后，受控注册表执行一次，得到 4.5 学分及原工具的资格边界提示。普通文本、未知工具、负学分参数和 HTTP 400 供应商错误四种路径均不执行获准工具，也不产生计算结果。请求只在测试内存中解析；问题与鉴权值不打印。测试沿用 CLI 的 Spring AI 日志关闭边界，避免异常响应日志泄露请求内容。

| 验证 | 实际结果 |
| --- | --- |
| 本机协议合同 + 既有工具调用聚焦 + `SpringAiAdapterContractTest` | **28/28 通过，0 失败，0 错误**（新协议合同 5 项；既有模型工具 18 项；既有适配器 5 项）。命令见演示说明；Maven `-o`，无外网/数据库依赖。 |
| knowledge 全模块离线回归 | **411 运行 / 8 失败 / 84 错误 / 4 跳过**，不能称全绿。当前仍缺 `ZHKU_DB_PASSWORD`；数据库相关测试无法装配。B-4 的 `GracefulDegradationTest.shouldSafelyRefuseAndClassifyModelTimeoutAsModelDegraded` 仍为既有红例（`RETRIEVAL_MISS` vs `MODEL_DEGRADED`，归线 A）。新协议合同 5/5 通过。 |
| 真实 DashScope 冒烟 | **未执行**：当前进程缺 `DASHSCOPE_API_KEY`；未观察到真实供应商 tool-call 响应。 |

这批只能证明 Spring AI 1.0.9 与本机假供应商之间的原生工具调用协议及受控执行，不能推断真实 DashScope 当下支持情况或调用成功率。

## 2026-09-27 真实 DashScope tool-call 冒烟补证

状态：**真实 DashScope tool-call 已验证（本次本机配置与时点）**。上节“未验证”是当时缺密钥的历史记录，本节在用户完成环境变量配置后追加，不回写旧结果。

本次从 Windows 用户级 `DASHSCOPE_API_KEY` 向当前测试进程注入密钥，未打印或记录其值。使用现有 `application-zhku-local-preview.yml` 的 `dashscope` profile（`qwen-plus`、国内站兼容端点），运行 CLI 的显式 `--live` 路径；没有替换供应商、模型或工具注册表。两次虚构课程冒烟均观察到 `tool=summarize_course_credits`、`status=SUCCESS`、`result.totalCredits=4.5` 与 `boundary` 字段；第二次用纯 ASCII 问题排除当前终端的中文管道编码干扰，类别汇总为 `required=3`、`general education=1.5`。这说明供应商实际返回了工具调用意图，Spring AI 解析后由服务端执行了已批准的纯计算工具；普通文本或客户端自行解析 JSON 不会产生该成功状态。

限制：当前 Codex 终端捕获中的中文 `boundary` 显示乱码，故只把其**字段存在**作为本次真机输出观察；边界原文由离线合同测试确认。此冒烟不证明课程归属、选课资格、毕业资格或跨时点稳定性。最近一次 knowledge 全模块回归仍是 **411 运行 / 8 失败 / 84 错误 / 4 跳过**（缺 `ZHKU_DB_PASSWORD` 与既有 B-4 红例）；本次只更新真实供应商证据，未将全模块写成通过。复现命令见演示说明。
