# M3 独立评审核对与处理

评审配置：provider=opencode，model=opencode-go/deepseek-v4.1-flash；任务已completed。工具返回executedModel=null，评审者自行报告上述运行身份，不把未返回的运行字段当作额外模型证明。

原始评审：[M3 full baseline pre-freeze independent review](t3-thread://v2/eb819992-5cf0-4415-84b9-e8ffd8aa9d9c/thread:delegated-task:command%3Amcp%3Acfc276c1-13fa-4578-9c5d-7aaab1ee2f6b%3Adelegate-task%3Ab584e740-c1d0-46c8-95d4-2fdfd4fa1711)。只读评审，没有运行测试或模型。Codex独立核对代码与实际产物后作以下处置；不把评审的静态检查冒充执行验收。

| 意见 | 核对与处置 |
| --- | --- |
| B1 草包过期/缺README和全量预检 | 草包确为过期、未授权；随后R0包6378dd40…已完成三次Check、20题Assembly、36项测试及README。评审对此处状态的读取落后于实际产物。本轮代码修正再次使R0失效，保留历史文件，重新构建R1并预检，不申请草包或R0授权。不按评审建议直接提交Git。 |
| B2 宿主/审阅错误、未细分引用FAIL计入Agent缺陷 | 接受。宿主/审阅错误为HARNESS_OR_REVIEW_ERROR、defect=false；Trace确有FAILED/STOPPED单列AGENT_FAILED_OR_STOPPED。未细分引用失败defect=false，须绑定语义细分才计缺陷。报告同时列已证实缺陷与非缺陷/待归因数。运行失败不等于已证明模型根因。 |
| I1 比较遗漏批次与jar范围 | 接受。新增selection SHA、task/run数量、全eval源文件身份、jar比较；导出从实际包推导子集/全量模式，版本取冻结数据，关键身份缺失拒绝。共同10题可比较不意味着运行物完全相同。 |
| I2 缺授权证据 | 接受。运行前复制批准/预留原字节，batch-plan绑定二者SHA；Report核对字节与包身份，输出executionIdentity并哈希source。缺副本的旧M2和离线夹具明确NOT_VERIFIED，不能补造历史授权。 |
| I3 回执内细项不校验 | 接受矛盾检查。SHA绑定的包已强制全部预算；回执若重复声明单run次数、输出token、单次/run时长、题目顺序、PUBLIC回复、专用库，必须与包/计划一致。最小回执可不重复声明，M3实际回执会完整填入。 |
| I4 013跳过原因笼统 | 接受。记录实际taskStatus、追问条件/允许值，区分NO_WAITING_USER、CLARIFICATION_MISSING、WRONG_CONDITION、REPLY_NOT_ALLOWED，不消耗续答调用。直接回答时按NO_WAITING_USER保留真实终态，不猜测其质量。 |
| I5 013漂移检查 | 接受。断言唯一ASK_CLARIFICATION是013，并验证RESOLVE_PUBLIC kind。courseType来自生产澄清协议，是宿主预声明约束，不伪称题卡原字段。 |
| I6 清理遮蔽/泄漏 | 接受防御性修正。factory构造纳入try，关闭失败后仍shutdownNow/await，case-closeout保留。鉴权/连接/不可用及清理停批保留typed码。未采用110秒等待：TaskStore.close已requestStop，factory.close取消Provider，shutdownNow中断worker；25秒=单次调用20秒+5秒余量，超时则保守停批。无限DB/线程挂起应由宿主拒绝继续，不靠延长等待掩盖。新增关闭异常测试。 |
| I7 ERROR维度计数 | 接受表格修正。逐维展示总分母、NA、PASS/FAIL/UNJUDGEABLE/ERROR及已判定/适用分母，适用分母=全题-已证实NA，ERROR保留在分母中；新增各列总和20测试。 |
| I8 评价异常过于笼统 | 接受诊断区分。TurnRecord错误保留阶段CAPTURE_DECODE/CAPTURE_IDENTITY/ANNOTATION_DECODE/BOUND_EVALUATION及异常类。报告离线入口保持只读，不向原执行目录写eval-error；不保存可能夹带正文/凭证的异常message或cause。异常均ERROR，不计Agent缺陷。 |
| I9 文档/入口状态漂移 | R0已存在实际测试/预检记录，修正后另记R1；补完整命令与SHA，保留旧阶段证据，不写未执行PASS。 |
| Minor | Prepare改用显式SUBSET_10/FULL_20选择；全NA不能整题PASS；容器清理失败不覆盖原宿主错误，另记container-cleanup.json。冻结集当前严格20题，20-denominator符合此版本契约，不为假设未来扩集修改历史Grader。通用进程辅助函数、Docker入口和凭证导入脚本未单独纳入包SHA，评测入口/专用监督器/生产Java/jar/资料资产已绑定；不声称全系统环境不可变。 |

R0离线重放确认M2全部题/维结论、通过数和可评估比例一致；5处factSupport理由仅集合展示顺序不同，不能宣称报告逐字节一致。原M2证据未修改。R1仍须重新验证，并在本目录记录实际结果；无真实调用授权或执行。
