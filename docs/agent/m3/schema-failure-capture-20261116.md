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
