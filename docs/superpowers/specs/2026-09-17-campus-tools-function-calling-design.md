# 校园工具集与 Function Calling 设计

> 状态：已由用户于 2026-09-17 确认，可进入实施计划阶段。

## 目标

在 `knowledge` 模块新增一组可由未来编排器调用的校园工具：课程学分汇总、选课冲突检测、毕业学分进度以及校历/课表样例查询。工具只能进行纯计算或读取明确标记的本地样例数据；不得连接校务系统、写数据库、保存学生数据、代办业务或确认学籍/毕业资格。

## 范围与非目标

### 本次范围

- 在 `com.zhku.knowledge.tools` 创建框架无关的工具契约、注册表和四个独立工具。
- 用稳定的工具名称、说明、输入 JSON 形状和风险标记，使线 A 的 `routing/` 编排器可以在后续将其适配为模型 Function Calling。
- 为正常结果、无数据、格式错误及关键边界编写自动化测试。
- 交付工具能力的证据账本。

### 明确不做

- 不修改 `agent/` 模块、`ToolRegistration`、`ToolCallAgent` 或任意 `pom.xml`。
- 不修改线 A 拥有的 `routing/`，不新增工具路由分支；线 A 决定何时调用工具。
- 不修改 `ZhkuLocalPreviewConfiguration`、HTTP 门面、SSE、前端、数据库迁移或现有会话存储接线。
- 不抓取、不连接真实校务、课表或校历数据源；本地样例永远以 `SAMPLE_DATA` 标识返回。

## 架构

```text
线 A 编排器（未来）
        │ 根据路由选择调用
        ▼
CampusToolRegistry ──按稳定名称查询──► CampusTool
        │                                  │
        │ 工具描述（名称/JSON 形状/风险）    ├─ CreditSummaryTool
        └──────────────────────────────────┼─ ScheduleConflictTool
                                           ├─ GraduationCreditProgressTool
                                           └─ SampleAcademicScheduleTool
                                                      │
                                           调用方参数 / 本地样例数据
```

工具层不依赖 Spring AI、模型 SDK 或 `agent` 模块。`CampusToolRegistry` 是线 B 对外唯一入口：它返回不可变的 `CampusToolDescriptor`，并按名称调用对应工具。这样未来可以增加 Spring AI/MCP 适配层，而不让领域计算依赖某个模型提供商。

## 统一调用契约

### 工具描述

`CampusToolDescriptor` 包含：

- `name`：稳定的小写 snake_case 名称；首批使用 `summarize_course_credits`、`detect_schedule_conflicts`、`assess_graduation_credit_progress`、`query_sample_academic_schedule`。
- `description`：说明输入和结果，不对真实校务数据作暗示。
- `inputSchemaJson`：供未来 Function Calling 适配器直接使用的 JSON Schema 文本。
- `accessMode`：固定为 `PURE_CALCULATION` 或 `SAMPLE_DATA_READ_ONLY`。

### 调用与结果

`CampusToolCall` 只含工具名和 JSON 参数文本。注册表先校验工具存在，再由具体工具解析自己的参数。`CampusToolResult` 统一包含工具名、`SUCCESS` / `INVALID_ARGUMENT` / `DATA_UNAVAILABLE` / `UNKNOWN_TOOL` 状态、JSON 结果文本和面向用户的边界提示。

格式错误、缺字段、未知工具和样例缺失必须返回结构化失败结果，不能将异常或堆栈交给模型。所有成功结果都追加边界提示：只基于调用参数或样例数据计算，不能替代培养方案、当期通知或人工审核。

## 工具行为

### 课程学分汇总

输入为课程标识、课程类别和非负学分列表。输出总学分及按类别的汇总。空标识、空类别、负学分或重复 JSON 字段导致 `INVALID_ARGUMENT`；该工具不判断课程是否可选、是否计入培养方案。

### 选课冲突检测

输入为课程标识及上课时段：星期、起止节次和非空正整数周次集合。仅当两门不同课程的星期相同、节次区间相交且周次集合相交时报告冲突。结果按课程标识和交集时段确定性排序；同课程的多个教学时段不当作跨课程冲突。非法节次或周次导致 `INVALID_ARGUMENT`。

### 毕业学分进度

输入为培养方案类别目标和已修类别学分。输出每个类别的已修、目标和 `max(目标 - 已修, 0)` 缺口，以及总缺口。工具只计算传入的学分目标；即使总缺口为零，也必须提示仍可能受课程性质、学制、论文、处分或其他规则影响，不能确认毕业资格。

### 校历/课表样例查询

输入为学期及可选日期或课程关键字，从不可变的本地样例目录读取记录。输出携带 `SAMPLE_DATA` 来源标记。匹配不到时返回 `DATA_UNAVAILABLE`，不得补全或猜测真实校历、教室、开课状态或个人课表。

## Function Calling 适配边界

线 B 在本交付中只提供上述描述与注册接口。线 A 可以把 `inputSchemaJson` 转换为其模型供应商的 Function Calling 定义，并把模型的工具参数传回 `CampusToolRegistry`。路由优先级、是否调用、调用次数、工具结果如何融入回答和引用白名单均归线 A；工具层不改 `routing/` 文件。

若未来需要专门的“工具分支”或更改五级短路顺序，必须先在 `SYNC-STATUS.md` 的待协商事项登记，待双方确认后再改。

## 测试与交付策略

每个工具按 TDD 实施：先写一个真实调用契约测试并观察其因类/行为缺失失败，再写最小实现，最后运行对应测试。注册表测试覆盖不可变描述、已注册查询、未知工具和失败结果；各工具测试覆盖正常路径、关键边界及错误输入。

分为四个独立提交：

1. 工具契约与注册表；
2. 学分汇总与毕业进度；
3. 选课冲突；
4. 样例校历/课表与证据账本。

本次不使用 Flyway 号段；V12–V14 继续保留给线 B 后续确有持久化结构需求的工作。
