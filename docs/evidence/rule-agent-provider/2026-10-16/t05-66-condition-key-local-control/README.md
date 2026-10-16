# T05-66：t05-65原响应的条件键单变量对照

本轮仅本地验证，零真实模型调用、零数据库、零Docker、零应用重建。提示澄清保留为候选，未修改提示、条件记录、生产守卫或预算。

使用t05-65实际jar（SHA256：79a22f1c24c0a893d52a888c7e5678e4988f3e8484088ee2ead85ba89259ed70），准备前HEAD为7e59d1b317dd25783611b6e92b7e37f2138ef433。助手仅在独立临时目录编译，运行时加载实际jar中的解码器、能力目录、Gate、Runner和发布器；关键类来源见replay-result.json。Java禁止所有SocketPermission，子进程移除凭证；临时目录已删除。

对照结果：

- 原始核验响应未经改写，实际解码稳定拒绝：REVIEW_BINDING / REVIEW_CONDITION_UNKNOWN。本机Runner也落FAILED。
- 仅另建副本，将requirements[0/1].necessaryConditionIds[0]由S10/RETAKE_ONLY:courseType改为courseType。JSON差异严格限于这两个位置；需求ID、跨度、subject、CORE绑定、unresolvedDemands和inputFingerprint全部保持。原始轨迹和响应文件不修改。
- 副本通过关联校验和独立Gate，Gate返回UNRESOLVED（条件未知时的澄清判定，不能写成完整答案通过）。
- 本机受控Runner经过SEARCH→READ→ASK→PUBLICATION_REVIEW并发布WAITING_USER/INCOMPLETE；无答案主张，澄清键courseType、允许值PUBLIC/PROFESSIONAL，有1份原文证据及关联。尚未注入PUBLIC，未运行第二run。

完整事实定义来自t05-65 REVIEW_INPUT_SNAPSHOT，核验输入同时与真实REQUEST逐项比对；重新构造的SemanticInput与原输入完全相等，包括完整事实指纹产生的inputFingerprint。ASK使用该批实际提议，不替换支持事实。

替身边界：检索与原文读取是内存端口，只提供该批已读原文；模板条件/事实/范围来自历史快照，测试审核者、主体、运行ID、检索配置和覆盖夹具是本机构造。真实数据库权限、索引、fresh回读、任务仓发布一致性和网络时延未在本轮重测。原始审计文件完整保留。

Runner的execution.mode字段在源码中固定为ONLINE，不能据此声称本轮外发。决定/核验端口只是读取历史资料，providerResponseReceived=false、answerSource=VERIFIED_RULE_ONLY；两组各3次决定端口调用加1次核验端口调用，均不是真实模型调用。

本结果证明：对这份具体输入和响应，改正两个引用键足以在现有Gate及本机发布路径得到必要澄清。它不证明重复条件是模型加前缀的原因，不证明提示增强有效，不代表真实P02两run整链、可靠性或产品质量通过。未重新运行历史204/205项测试，也未将局部对照冒充完整回归。

335项生产/启动输入、实际jar、t05-65原始文件和主仓库既有4份改动的字节均保持，旧授权仍CLOSED_FAILED，不恢复或补充额度。撤回本轮仅需另建逆向文档提交，不改历史证据、不操作生产历史。

工程成果已冻结，真实在线验收未通过。
