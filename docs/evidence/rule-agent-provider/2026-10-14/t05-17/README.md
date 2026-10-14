# T05剩余任务1：独立核验在线适配器局部检查点

执行起点：2026-10-14 10:00:52，截止10:20:52。用户授权只完成剩余第一步。本轮不接在线工厂、不改学生端、不调用DashScope、不访问数据库。所有HTTP测试使用127.0.0.1随机端口、LOCAL_TEST_ONLY和测试占位密钥；真实外发0。

基线7570500693db94cff7a69f49c40625c474ad3557，暂存区初始为空，既有四份无关产品文档改动保留。源码范围六文件：RuleAgentProvider、RuleAgentProtocol、RuleAgentJson、RuleSemanticReviewPort、新RulePublicationProtocol和RuleAgentProviderTest。

| 问题 | 实际改动 | 验证证据 |
| --- | --- | --- |
| 只有动作决策端口，无独立核验传输 | 同一个Provider实现DecisionPort及SemanticReviewPort，复用envelope与exchange；核验使用独立SYSTEM/Input | 新增端口、隐私、解码测试；red-01新增8项均因缺核验端口失败，旧75项保持通过 |
| 两套传输可能各领取额度/调用号 | 同一客户端、allowance、attempts、inFlight和close；exchange串行化，单run最多4次 | 3次决策+1次核验调用号为1—4；批次额度设8，拒绝第5次后仍剩4，无额外HTTP |
| 核验约束重复硬编码/宽松解析 | 合同字段、枚举、容量来自既有record/enum/常量；复用严格JSON解码，校验指纹、跨度、子问题与事实ID | 坏JSON、空结果、重复键、未知字段、未知枚举、错误指纹拒绝；阶段REVIEW_JSON/REVIEW_BINDING。具体能力/条件/提议覆盖仍交Gate |
| 非法核验响应可能丢usage或被记成零 | 外层完整可解析后先记RECEIVED，再内部解码；Trace增加DECISION/PUBLICATION_REVIEW用途 | 非法结果仍记录25/12 token；缺失usage为null；日志不保存自由响应正文、凭证或任意异常文本 |
| 关闭核验被误分类为连接故障 | 已关闭端口的ExecutionException优先归CANCELLED；不返回迟到评估 | green-03实际复现预期CANCELLED却MODEL_CONNECTION_FAILURE；最小修正后关闭进行中请求测试通过 |
| 传输整理可能影响旧动作或任务状态 | 保留原动作合同和调用接口，在线工厂实现未动 | 最终109项：Provider44、Gate15、Contract7、Runner21、OnlineFactory8、TaskStore14，全部通过 |

执行证据：t05-review-adapter-red-01共83项，新增8项失败；green-02共83项，1项失败是测试把代表未知值的null误判为泄漏，修正断言不改变业务结果；green-03共109项，1项为真实取消分类缺口；green-04共109项全通过。每批sourceDrift=0、resourcesVerified=true，旧失败记录原样保留。代码写入工具曾返回失败但部分改动已落盘，核对后去除重复bindReview声明；该写入异常不作为业务测试结果。

预算边界：适配器两种用途共用每run4次及批次AtomicInteger额度，单次仍最多10秒/1024输出token；现有Runner新路径负责3次决策+1次核验和45秒deadline，传入剩余timeout。适配器不能独立宣称工厂已使用新路径，Factory仍为旧3参数Runner。没有新建第二套供应商实例或提高预算。

评估边界：测试证明HTTP/解码/审计/退出控制，核验参考为合成数据，不证明qwen-plus理解质量、真实传输/成本/超时可靠性、全目录相关性或学生收益。未进行前端、数据库或真实供应商验证；未完成新工厂装配、真实续答纵向贯通或新协议真实探针，T05未完成。

成功退出：最终109项检查通过，源码与暂存身份一致，凭证/访问令牌检查及差异自审通过后保存局部本地提交。SHA及验收身份见commit-receipt.json、stage-audit.json。原始失败XML保持字节，CDATA行尾空白仅按精确路径豁免，其余文件严格检查。未push、PR、标签或部署。

下一步只有剩余任务2：在线工厂显式装配同一端口用于决策/核验，同步新预算合同及可审阅启动配置，使用本地fake服务完成TaskStore澄清令牌续答、停止、结果与资源退出。再进入任务3准备新包、申请真实探针授权并收口。旧额度不复用，不自动外发或追加补测。

恢复：通过父提交恢复本轮六个明确文件即可撤回适配器增量，保留证据及其他工作区改动；不重置全仓库、不改写历史，不触碰原演示数据库。
