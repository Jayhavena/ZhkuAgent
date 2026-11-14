# M3 当前版本最小真实复测：已准备，未授权执行

源代码/HTTP被测提交：93dc4a1c630bbbc29ba715e3a4292b0c0d418d9c。生产修复为d6d3a2f6，补充测试为a42bdab0。manifest.json绑定本轮新包、jar、模板与绑定器摘要。Prepare/CheckPackage均0次供应商调用、0次数据库操作，绑定器23项通过。无本批真实结果、Trace或Evaluation Report。

## 申请的唯一批次

- 包SHA-256：244c5d272cdd2e3bf2d39601bf1254c8a75a63ee189e6786ebfbdd3a26cdc596。
- 请求模型qwen3.8-flash，北京端点https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions。实际responseModel与供应商用量待采集，不把请求配置当执行证明。
- 一个合成问题“我这门选修课没过，能不能重修？”，第一次必须必要澄清，补充PUBLIC后同task续答；两run合计最多10次（每run4次决策+1次独立核验），不自动重试、不补跑、不第三run。单次20秒/最多1024输出token，每run110秒，进程上限235秒。
- 建议本批费用上限1元，approvedBudgetCny=null；不是沿用旧12元或M2授权，也不是供应商账户级硬限额。实际账单及每成功任务成本仍待控制台核对，不能拿估价冒充实际支付。
- 首轮不达WAITING_USER即停止，不强行补充；续答不达核验发布标准即停止。失败保留原证据和Grader失败报告，不修改冻结期望迎合输出。

## 外发范围及本机留存

最大原文范围10条/616字符，20条语义事实、2条Article4 overlay事实，配套问题/条件/规则上下文、候选别名、工具观察（状态、原因、引文、版本）、事实及when、被拒动作、剩余预算/允许动作、SYSTEM/REVIEW_SYSTEM、动作Schema/合同、独立核验合同/策略。实际只发送当次决策或核验payload，不整体发送request-package。

envelope还包含model、stream=false、enable_thinking=false、max_completion_tokens、response_format及messages。核验input包含问题、条件、已读事实/引文、能力和inputFingerprint。taskId只参与本机绑定摘要；不把账号/会话、密码/密钥、continuation/original token、整本PDF、参考答案、完整源码或本机资产清单放入模型消息；API凭据仅通过固定供应商端点的Authorization认证头使用。原始交换仅写本机私有批次，禁止提交或委派给审查模型。

可直接在本机审阅：docs/evidence/rule-agent-provider/2026-11-14/m3-minimal-93dc4a1c/request-package.json、OUTBOUND-STATIC.json、OUTBOUND-DYNAMIC.json。它们包含受控正文，未暂存。冻结jar、包、两份披露、模板与绑定器已在用户私有.zhkuagent/m3/prepared/<包SHA>/保存6份逐字节副本。不得在Check/Probe前执行package/install重建jar；如构建身份不符必须停止，不能改manifest掩盖。

## 执行与评测顺序（仅新授权后）

1. 重新CheckPackage，核对批准的完整SHA；读取本次所需凭据Process→User→Machine，不打印值。DockerOnly就绪后用独立临时库端口55549执行，仅当前包可消费一次授权。Prepare本身不授予Probe许可。
2. Probe使用start-rule-agent-probe.cmd -Probe -OnlyP02 -ApprovedPackageSha256 <以上完整SHA> -PackageRelativePath docs/evidence/rule-agent-provider/2026-11-14/m3-minimal-93dc4a1c/request-package.json -DatabasePort 55549 -HandbookPdf <manifest中已核对绝对路径>。运行后核对新live目录和用户级probe-claims/<SHA>.used，并把消费回执副本绑定到新证据目录；不能拿旧live证据充数。
3. 复用既有M2两轮技术评分合同与严格绑定器，不改模板：python knowledge/scripts/bind_m2_suite.py --template docs/agent/eval/m2-minimal-20261114/suite-template.json --evidence <本批新live目录> --out <独立新输出目录> --repo-root .。
4. 交现有EvalChainMain collect/run，使用新的报告输出目录，保留原生report.json/report.md。suiteId=M2及M2-CLARIFY/RESOLVE是复用合同的技术ID，不表示使用旧M2 run或结果。
5. 另外核对新task/run身份、工具实际观察、Claim factId/when、checksum/span、引用关系和fresh资格。Grader只验证其已有合同范围，PASS不自动证明答案语义或账单。失败即停，不扩大批次。
6. 对照本批时间窗/地域/模型/Key标识核账。当前M2-BILL仍未完成，真实账单在可读前保持UNVERIFIED/null。资源关闭后方可保存脱敏结果。

## 本批能与不能证明什么

本轮只检查新版本必要澄清、已知PUBLIC续答、实际工具观察与发布链。它不是大规模质量评测，不能提高旧20题成功率；题面不含明确类别，因此不验证首问显式类别提取的真实模型行为，也不强迫产生第四个能力缺口码。它不能证明持久HTTP ONLINE或真实供应商故障停止。首问提取当前由纯/Store/合成Factory及零模型HTTP证明工程接线；能力缺口由合成Gate/Publisher回归验证。若产品要求相应真实场景，必须另冻结、另授权，不能在本批临时加题。

本轮仅申请这个最小包。当前HTTP工程验收首启14项、重启14项通过，已停止服务；该结果与本批尚未执行的真实复测分别记录。
