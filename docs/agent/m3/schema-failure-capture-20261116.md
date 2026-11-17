# M3-LIVE-01 离线修复：Issue配对与失败发布采集

基线d61cf311（2026-11-16真实失败记录）。本轮不调用模型、数据库、Docker或浏览器，不消耗新票据，不重建已冻结jar，不改写原始批次/失败报告/评分期望。新源码使旧构建不再代表当前修复；真实复验须另建冻结包并另获授权。

## 修复行为

Schema不再独立列举kind与reasonCode，而是从ReasonCode.kind生成互斥对象分支；每分支固定kind并只列对应reasonCode，字段仍闭合且必填。CONFLICT/CANCELLED等领域拒绝组合在机器Schema中也被排除。宿主解码与值构造器继续独立校验；不能假设供应商遵守Schema。没有改变领域允许的合法组合或学生HTTP DTO，也不因此放宽模型自报原因的发布权限。

Probe两轮循环抽为同一runP02Tasks方法。每次从TaskStore取得已发布终态，先落task-results.json再判P02阶段是否成功；第一轮失败时保留一个真实发布快照，不运行澄清回复或制造第二run。第二轮以同task、不同run、递增版本追加；拒绝第三run、重复run或跨task。首次文件CREATE_NEW，第二轮只更新本机已写且字节仍匹配的前缀，经同目录临时文件原子替换；既有历史文件或临时文件冲突均拒绝覆盖。假定批次唯一目录由单一Probe写入，不承诺抵御外部特权进程并发篡改。

绑定器将task-results数组视为按运行顺序写入的发布前缀。单条已发布失败绑定首轮真实runId，给出FIRST_RUN_STATE_MISMATCH/FAIL；未执行续答runId仍null。单条RESOLVED也只能算首轮提前结束，不能按成功状态猜成续答。原有两轮同task/不同run、Claim关联、模板不可改和输出拒绝覆盖守卫保留。缺少发布数组时不从唯一outcome猜测轮次或伪造发布来源，原始历史报告仍为2 NOT_EVALUABLE；本轮不倒填历史task-results。

## 离线验证与限制

新增Schema测试覆盖领域枚举全部kind/reason组合，逐对与领域配对权威一致；显式锁定CONFLICT/CANCELLED及额外字段拒绝。测试只解释本Schema使用的对象/闭合/必填/const/enum/oneOf子集，不代表完整通用JSON Schema验证器或供应商兼容性验收。

新增发布测试经过实际Provider适配器、本机合成HTTP、实际Runner/TaskStore及Probe循环：SEARCH后返回错配FINISH，2次本机HTTP、首轮FAILED先发布落盘、只有一条任务历史/快照、无续答、Provider关闭。实际Collector得到COMPLETE，现有Grader在未改的冻结两轮期望下得到1 FAIL+1 NOT_EVALUABLE、0 PASS、regressionPassed=false。这是正确识别合成失败的回归通过，不能说模型任务通过。另测两条快照增量写入及历史/临时文件防覆盖。

测试过程保留：初始Schema两例失败；宿主失败前未保存发布数组导致新增采集例NoSuchFile（m3-schema-capture-red.log，2 fail+1 error）。Python单轮绑定2例先失败，后26项通过；拟从未发布outcome推测首轮的方案未采用，改为保留不可判定的守卫测试。中途测试直接调用不可见CLI方法造成编译错误，改用既有公开EvalChainRunner；旧Runner直接返回坏JSON会走不同重试路径，采集fixture改为实际Provider本机HTTP；JSON树long/int表示差异改为序列化后比较。没有因这些测试构造问题改写生产旧业务期望。

局部最终Java 6项通过（knowledge/target/m3-schema-capture-local-v2.log），Python 26项通过（m3-pair-capture-python-green.log）。扩大回归结果另附。CI trial-contracts新增两类Java守卫及纯标准库Python绑定器测试；未push或运行远程CI。尚未验证：真实供应商对新Schema的支持/遵守、真实两轮任务成功、实际账单、断电时文件系统持久性。历史M3真实失败与M2实际账单待办不因离线修复关闭。

扩大回归：主Agent执行21类（含CI Java同选类及Provider/Factory/Runner/Store/合同/Grader/票据相邻回归），289项通过，0失败/错误/跳过，BUILD SUCCESS（knowledge/target/m3-schema-capture-regression.log）。参数为mvnw.cmd -o -pl knowledge -am、显式-Dtest、-Dsurefire.failIfNoSpecifiedTests=false、-Dzhku.rulecheck.evidenceDir=<根>/knowledge/target/m3-evidence、已核对-Dzhku.t06.handbookPdf=<本机PDF绝对路径>。部分旧Factory/装配类读取既有审核资料作离线验证；新两类测试仅合成fixture与本机HTTP。Python单独26项通过，不与Java计数混称一次套件。未运行前端、数据库、浏览器、真实供应商或远程CI；本次没有前端/迁移变更。原真实批次execution-20261116目录相对基线无差异，原始文件摘要亦单独核对保留。独立只读复核待完成，不把本地绿灯称远程集成或真实质量通过。

## 2026-11-17 独立复核结论与低风险补充

只读复核f4b72a73未发现本离线提交阻断项；独立亲跑6 Java与26 Python通过。请求配置OpenCode/opencode-go/deepseek-v4.1-flash，未取得供应商executedModel证明；不把配置身份当作实际模型执行证据。独立报告对嵌套oneOf的供应商接受度、跨文件系统/断电持久性保留限制，与本记录一致。

主Agent自行补充，不再为小修创建新审查循环：Schema缺kind、缺reasonCode及空对象负例；Python单条STOPPED首轮绑定失败、续答为空且冻结期望不变；实际本机HTTP工厂产出的WAITING_USER（含continuationToken）与RESOLVED（含Claim）快照原子追加。失败链测试不再手工写suite.runId，而是启动真实Python绑定器，再将其输出交现有EvalChainRunner；要求绑定器exit=2/FAIL、真实首轮runId、续答null、expectationsUnchanged=true，最后Grader仍为1 FAIL+1 NOT_EVALUABLE。

该组合测试需要Python标准库运行时：Windows默认python、其他平台默认python3，可用-Dzhku.test.python=<解释器可执行路径>显式指定。CI在Java测试之前通过setup-python配置3.11，不依赖隐含预装路径。子进程输出写合成临时日志，10秒有界等待、超时仅结束自有绑定器进程。仓库根定位已加耗尽保护，找不到时给明确错误。共享wire.cleanup内部断言与跨平台调度的诊断边界仍与既有测试相同，不为本次小修重写全套测试资源框架。

协议修订字符串当前保留06；合法领域动作集合未变化，包校验比较完整Schema、源码与jar摘要，不能仅以修订字符串认定Schema字节相同。供应商兼容性须在另一个新授权批次的首个预算内调用验证，若不接受即停，不能在同包下静默回退另一Schema或额外探测。没有改动任何历史真实报告、运行包或消费票据，也没有冻结/执行新真实包。

补充验证：57 Java（Schema 3、采集组合5、相邻OnlineFactory49）通过，0失败/错误/跳过，BUILD SUCCESS（knowledge/target/m3-review-gap-java.log）；27 Python通过（m3-review-gap-python.log）。仅测试/CI环境/文档相对f4b72a73变化，生产Java与绑定器实现未再改动，原真实execution-20261116目录无差异。因此保留原289项扩大验证结论，不把本轮57项说成再次跑完289项。此离线检查点完成独立复核及补充回归；不代表远程CI或真实任务质量已通过。
