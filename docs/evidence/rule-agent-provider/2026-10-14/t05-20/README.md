# T05 P01零调用启动前检查

2026-10-14 12:04:35开始，主动执行上限20分钟。基线f0eed907b1c16d7db7fb4475bd1126cbea8ee628。本轮只收窄工程探针入口、检查包/源码/构建身份和范围拒绝；真实供应商调用0、数据库操作0，不执行真实P01/P02、不进入T06。提交身份见commit-receipt.json。

## 改动与依据

- RuleAgentOnlineProbeHost改为读取已经冻结的t05-19/request-package.json；限定P01_READ_REFERENCE_03、仅一个P01任务、最多4次调用。保留原每run3次决策+1次独立核验、45秒/单次10秒/1024输出token和禁止自动重试。
- start-rule-agent-probe.ps1使用同一新包；CheckPackage/Probe必须明确OnlyP01；IncludeClarification在凭证导入、Docker调用之前拒绝。CMD包装器保持原行为。
- 删除当前探针宿主的P02执行分支，P02需P01通过后另行准备并授权；原Factory、TaskStore澄清续答能力未修改，相关回归仍运行。
- 本轮没有修改READ映射、模型提示、权限、原文核验或发布器。没有修改新包或历史资料/评分。包内executionReadiness是上一轮的历史准备标记，保留其字节；当前零调用就绪状态以本次preflight-result.json为准，NOT_AUTHORIZED标记仍成立。

## 验证记录

| 验证 | 结果 | 意义 |
| --- | --- | --- |
| 旧入口RED：25项工厂测试 | 1断言失败、4预期协议拒绝异常，20通过，资源退出/源码无漂移 | 旧组合范围被接受，新P01范围及4次包被拒绝；不是环境连接或编译失败 |
| 新入口及相关后端回归 | 136/136通过，7类，sourceDrift=0，resourcesVerified=true | 同25项全部通过，READ/证据/发布/续答/取消的既有本地回归保留 |
| BuildOnly | 离线构建成功，资源退出 | 新jar对应本轮源码；不是线上能力验收 |
| 用户持久Machine+User PATH下的CMD入口 | 6项全部通过 | 不依赖Codex注入PATH；wrapper通过已存在的PowerShell7路径运行 |

六项实际CLI检查为：CheckEnvironment成功；新包CheckPackage成功（PACKAGE_CHECK_ONLY、4次上限、模型0/DB0）；Probe缺OnlyP01按指定原因拒绝；携带IncludeClarification按指定原因拒绝；旧包SHA和编辑SHA按指纹原因拒绝。没有执行任何合法Probe命令。Windows PowerShell5.1发起CMD包装器的附加检查见terminal-ps51.json；它只检查包，不操作数据库或供应商。

证据：preflight-result.json保存三个修改文件的原始SHA、请求包与jar SHA；build-identity.json记录构建身份；各case JSON保留实际退出码、输出、资源及受控拒绝文案；case-comparison.json保留RED→GREEN逐项对照。已知凭证值在写入证据前脱敏，未记录凭证。零调用结论基于明确非供应商测试类、环境密钥清除与包检查在数据库/客户端构造前返回，不是网络抓包证明。

## 当前就绪与未验证范围

新请求包仍为7bcb597074997edd9fc2ef22e5e85003df093e9eaf92b2cebc6d5a5d14a3f560；t05-19历史回执不改写。构建jar SHA见preflight-result.json。新入口已可核对这份冻结包，旧12次批次/剩余10次不能沿用。

未验证供应商连通性、模型决策、真实SQL链路、动态事实选择质量或最终实际P01内容；T05仍未通过。Docker是否可用、临时库是否可创建及本次凭证能否访问，须在获得本批许可后的真实执行前重新检查，不凭本轮成功推定。

下一步仅可申请P01新包最多4次真实外发：北京DashScope chat/completions、qwen-plus，同批准10条已审核2025历史原文/事实/条件范围、编号/集合代号、实际工具反馈、独立核验能力/来源指纹及合同；不发送参考答案/整本PDF/个人记录/owner/session/原文和续答token/数据库身份凭证。每次10秒/1024输出、run45秒、无重试，失败停止。P01须走完真实检索→READ→FINISH→独立核验→受控发布，并核对两项第四条修读事实、usage及退出状态；否则保留未达标。P01通过后才申请P02，不自动扩大此包。

## Git与恢复

本轮仅独立本地工程检查点；没有push/PR/标签/部署。暂存清单仅包含两份入口实现、一份测试和对应证据/进度说明，既有四份产品文档保留。恢复按检查点生成可审阅反向改动并重新验证，不重置工作区或恢复旧外发许可。
