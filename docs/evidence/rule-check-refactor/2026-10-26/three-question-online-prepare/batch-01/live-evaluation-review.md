# ZhkuAgent 三题非预置 ONLINE 实测复核（2026-10-27）

**性质：** 本文基于本地 Live 回执、Provider Trace 与原文证据进行初步解读，不改变冻结包、评价金标准或运行代码。产品完成度仍需独立人工审核。

## 身份、调用、成本

- 授权编号：ZHKU-LIVE-3Q-20261027-01
- 冻结包 SHA-256：d4702146ec98832518401ca08f2f421c61e02fc3ff0fa95ec2e1840b70cd7681
- E02、E04（仅 PUBLIC 续答）、E13，共 3 task、4 run、16 次 Provider 请求（上限 20），无额外重试。
- 按最坏情况预留：12.8446688 元；授权硬帽 16.10 元。
- 实际 usage：输入 24913、输出 2187 token。
- 按冻结单价估算：输入 0.0199304 元 + 输出 0.0059049 元 = 0.0258353 元；实际供应商账单未核对。
- 本轮未操作真实浏览器；生产学生入口未开启；在线测试仅限受控测试 classpath HTTP 宿主。
- 被引用的仍是 2025.9.2 规则资料；不据此推断 2026 学年现行性或个人教务资格。

## 真实结果

| 场景 | 实际状态 | 已证实能力及限制 |
|---|---|---|
| E02：24 学时是否必然违规 | ONLINE / RESOLVED / COMPLETE；4 次模型请求、3 次工具步骤 | 发布第 5 条「原则 16 学时」「最多 32 学时」两条有原文支持的主张。结构体没有显式生成「24 小时并不因超过 16 就必然违规」的完整回答或现行规则提示。技术链路通过，产品语言完成度仍待人工判断 |
| E04 首轮：选修挂科能否直接重修 | ONLINE / WAITING_USER；4 次模型请求 | 正确追问公共选修或专业选修，没有擅自猜类别 |
| E04-PUBLIC 续答 | ONLINE / RESOLVED / COMPLETE；4 次模型请求 | 同一 task 新 run（版本 4），按照用户声明 PUBLIC 发布第 18 条公共选修课不安排重修；仍需核对 UI 是否充分说明历史文本与个人资格边界 |
| E13：今晚补选几点关、可否代办 | ONLINE / FAILED / NONE；4 次模型请求 | 未发布截止时间或代办完成的假主张，但因核验响应合同不合法，最终只返回处理失败，未完成应有的资料不足及能力边界说明 |

## E13 的原因

- 4 次供应商请求均返回 HTTP 200，并包含 usage。
- 第四次 PUBLICATION_REVIEW 的 finish_reason 为 stop，message.content 是可解析的 JSON，但后端返回 REVIEW_JSON_INVALID，Provider 归类 INVALID_MODEL_RESPONSE。
- 原始 review JSON 中存在 Requirement 的 dimension=UNKNOWN 与 recognition=IDENTIFIED 同时出现。
- RulePublicationContract.Requirement 明确禁止在 recognition=IDENTIFIED 时使用 UNKNOWN dimension；这个字段组合已足以说明合同不符合要求。
- 尚未单独重放反序列化器来证明它是第一个被拒字段。因此记录为具体的高置信度协议失配，不宣称已穷尽所有原因。
- 安全门禁没有发布无效结果，是积极证据；但 FAIL 不是合格的 DATA_GAP 或 SCOPE_LIMIT 终态，产品完成度不通过。

## 评测资料自身的错误

冻结 E04 necessaryEvidence 提及「第 16 条公共/专业不同处理」，而此次真实发布引用的历史原文是**第 18 条**。现有来源链审计同样指向第 18 条。应在下一份评测包中修正预期描述，不能悄悄改写已执行的冻结包；不要把它记为模型引文错误。

## 安全与收尾

- Live 回执：httpChecksPassed=true、resourcesClosed=true、browserTested=false；人工作品评价 REQUIRED_NOT_AUTOMATICALLY_PASSED。
- 一次性授权已消费，不允许用剩余 4 次额度重试本包。
- Launcher 回执：failed=false、containerRemoved=true；表示宿主执行完成与容器清理，并不代表 3 道题全部产品通过。
- 临时容器 label 下无残留、端口 55527 无监听；已跟踪 Git 修改为空；没有修改生产路由或源代码。
- 未运行后续额外模型或 Embedding API。

**结论：**真实证明了两类可答问题的在线任务流程和一次条件澄清续答；E13 真实暴露独立语义核验协议失败。下一步先离线复现和修正 E13 的输出契约及失败兜底，未经新授权不得重跑付费 Live。