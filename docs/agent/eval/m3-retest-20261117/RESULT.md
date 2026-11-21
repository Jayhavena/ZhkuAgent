# M3 修复后最小真实复测：技术链通过，账单待核验

2026-11-17，用户明确授权“重新冻结运行包，并做已经明确授权下的真实复测”。范围沿用每批1元、一个合成问题两run、最多10次调用、无自动重试。运行源提交be762644（Schema/采集生产修复f4b72a73），重新构建controlled.jar并冻结新包3afb4f51eaa2705e09e9efc4475f8cb0c937d5996b08ae7d710c33f6bb49c9fe。370个源码文件与jar身份校验通过；问题、资料上限、模型、端点、预算与上批一致，仅换已审修复后的构建/Schema。新包、披露、jar、模板、绑定器6份私有副本逐字节核对，CheckPackage零调用/零数据库通过。旧失败包244c5d27和旧失败报告没有改写、没有复用。

## 实际执行

一次Probe、无自动重试，共8次HTTP200（6次决策+2次独立发布核验），responseModel均qwen3.8-flash。首个允许FINISH的请求携带新增配对Schema，合计4个请求包含该结构，端点均接受；这证明本批接受了Schema，不证明供应商会拒绝所有非法组合。

- 同一task：2dedee51-964f-4d4b-a5ce-58b730b8a8e6。
- 首轮d5929639-7600-424c-94f4-d58de4b2317b：SEARCH→READ→ASK_NECESSARY_CONDITION→PUBLICATION_REVIEW，4次调用，WAITING_USER/INCOMPLETE/NONE，MISSING_CONDITION、无Claim。
- 预登记补充PUBLIC后，续答6d414674-35a0-4dcf-835d-ae23c047d5e4：SEARCH→READ→FINISH_CHECK→PUBLICATION_REVIEW，4次调用，RESOLVED/SUCCEEDED/COMPLETE。
- 续答仅发布S10/RETAKE_ONLY:public-failed-no-retake，when={courseType:PUBLIC}；没有重复追问或未覆盖缺口。

实际工具观察两轮均为SEARCH SUCCEEDED/CANDIDATE及READ SUCCEEDED/VERIFIED_FACT，不能仅凭模型自报动作当作已执行。两次独立核验是同供应商不同用途的实际调用，不宣称来自另一模型。19项补充检查均通过，见execution-audit.json：已发布快照与outcome身份一致、真实动作/观察、Claim事实/条件与引用绑定、批准原文SHA及UTF-16跨度、模型正文的事实/引文范围、私有标识未外发、用途/响应与消费票据一致等。未在报告复制原文或模型正文。

## 原生Grader

新绑定器读取本批真实task-results.json，exit=0/PASS；现有Grader exit=0、regressionPassed=true，两case均PASS/EXPECTATIONS_MET。原生report.json/report.md按原字节保存。suiteId=M2、M2-CLARIFY/RESOLVE是复用冻结评分合同的技术ID，不是使用旧M2证据；evidenceDir是本批新live-1f04830822d64234bfd0b3c2844d7da7，task/run均为本批新身份。没有改评分预期以迎合输出。

## 用量与费用

北京时间供应商响应时间窗2026-11-17 08:24:15—08:24:32；输入14852、输出1074 tokens，缓存命中0。按执行前核对的[北京官方目录价](https://help.aliyun.com/zh/model-studio/qwen3-8-flash)输入0.8/输出2.7元每百万tokens，估算0.0147814元。目录价估算不等于实际账单、折扣后应付或实际支付。

费用控制台默认Preview profile仍显示“登录”，未取得已认证账单会话，无法核对Key标识及本批实际账单。actualInvoiceCny与actualCostPerSuccessfulTaskCny保持null、billingStatus=UNVERIFIED。该可访问性边界不等于用户所有浏览器都未登录，也不以推理密钥冒充账单权限；页面已关闭。后续核账不需要再次调用模型。

## 证据、资源和结论边界

原始31文件（包括Trace/请求响应/审核快照、task-results、两run outcome及消费票据）已保留本机私有live目录并归档到用户.zhkuagent/m3/evidence/<本包SHA>/m3-retest-evidence.zip；逐条摘要验证一致。这里只提交脱敏审计/用量/原生Grader报告/绑定回执/摘要，不提交受控正文、原始交换、PDF、jar、凭据。Probe resourcesClosed=true、启动器返回0，专用55549无监听且无发布该端口的残留容器。Docker就绪首次CLI超时后用较长限时只读检查恢复，随后标准DockerOnly通过；这发生在Probe之前，不是Probe重试，也未操作共享演示库。新包票据已消耗，剩余2次不可复用。

本批技术结论：修复后的一个固定任务两轮真实执行链通过，包含必要澄清、实际工具观察、证据关联和发布门禁。结合此前真实非法响应被正确停止的证据，可以分别证明该正常与特定错误路径。它不证明Agent整体可靠性，不更新旧20题成功率，也不覆盖显式首问类别、第四个缺口码、所有适用性/取消/超时故障或持久学生HTTP ONLINE完整业务。实际账单仍待补证。M3取得最小真实链路通过证据，尚不是正式发布/在线准入；当前本地代码未合main、远程CI未运行，M4集成仍需独立推进。
