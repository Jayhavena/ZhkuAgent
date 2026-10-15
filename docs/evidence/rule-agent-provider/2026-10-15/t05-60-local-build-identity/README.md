# T05-60：本地构建与资产身份核对

本地构建及身份核对通过，未修改生产源码。源码提交`6dafcb021a40cb4134687694ab6c93b071e1d264`，分支codex/t05-online-evidence；主仓库仍58e1ef7，尚未合入。真实模型、数据库、Docker操作均0，旧授权剩余0。工程成果已冻结，真实在线验收未通过。

## 本轮实际构建

- 新jar SHA256：`06e24df21835744fd97184fdb85c9e9752b7cfd5945629c2ab580bc290044f3a`，43837686字节。
- 默认输出：`knowledge/target/knowledge-0.1.0-rc.1-controlled.jar`。
- 新冻结副本：`knowledge/target/eligibility-6dafcb02-06e24df21835-controlled-frozen.jar`，哈希与默认输出一致；两个文件均不入Git。
- 659个应用class逐项等于knowledge/target/classes，27个共享运行库class逐项匹配模块输出，内嵌agent-runtime jar等于模块产物；其余内嵌依赖全部记录SHA。没有class新增/缺失，实际6项生产变更由新class内容承载。
- jar未嵌入Git提交SHA；通过明确源码提交、受控构建、class/依赖哈希及实际加载来源绑定，不声称跨机器二进制可复现。

普通用户持久PATH下 WindowsPowerShell5.1→CMD→PowerShell7 的BuildOnly离线构建，退出0、未超时、资源已关闭，使用skipTests。现有启动器不打印内部捕获的Maven标准输出，build.log只有外层输出；不伪造完整构建控制台日志。202项测试是上一轮6dafcb02同源验收，本轮只核对选定源码和11份测试XML哈希，均未变，不能写成本轮重跑。

## 输入身份及如实保留的检查失误

335项生产源码/启动文件原始字节与HEAD匹配；额外14项POM、共享模块源码和wrapper等输入，其Git规范化内容与HEAD一致、工作区原始SHA与前轮相同。这14项的原始工作区换行表示与Git blob不同，详情source-identity.json；不能混称349项都逐字节等于Git blob。工作区实际无生产差异，Git偶尔显示cmd的stat缓存变化不等于内容变化。

最初程序按原始字节比较wrapper配置时断言失败，父shell未因Python退出码自动停止，随后执行了已授权的唯一一次本地BuildOnly；事后已完整核对349项输入和HEAD/工作区关系，没有重建第二次或伪造构建前快照。本轮默认jar在构建前没有完成fresh SHA采样，保留未知；前轮默认jar记录为82c25684，且该旧内容的冻结副本fresh核对未变，因此仍可按冻结SHA恢复。另一次只读merge-base误传占位ref失败，之后以真实SHA确认主仓库是工作树祖先且未合入；没有Git状态改写。

## 新jar实际运行证据

LocalBuildIdentity.java作为TEMP编译入口，通过新jar的PropertiesLauncher加载实际BOOT-INF/classes；7个关键类的加载来源均指向默认新jar。确认动作合同06、核验合同02、publicationFactIds视图与支持ID Schema、REVIEW_CONDITION_UNKNOWN分支。将旧55包交给新jar的实际validatePackage，已拒绝；将本批保存的带前缀核验响应交给实际新解析器，按REVIEW_CONDITION_UNKNOWN拒绝。只做受控合同输入验证，没有调用Provider、数据源、任务或Publisher；本地反射进程安装socket拒绝器。

实际新jar的--CheckRuntimeAssets核对15项审核运行资产，与旧清单逐项相等；外部PDF只读核对身份，没有复制或提交PDF。未执行会连接数据库的CheckRuntimeAssembly。临时编译/解包目录已回收，独立确认无本次身份探针Java残留。

WindowsPowerShell5.1普通入口的CheckEnvironment及会话UTF8中文样例通过；dockerAvailable只表示CLI存在，不查询引擎。再从同入口以旧55 SHA运行零调用CheckPackage，明确拒绝。参数名ApprovedPackageSha256只是核对指纹，不代表授权。helper运行期Java21的SecurityManager已弃用警告已保留，不是产品失败；本helper不宣称适用于未来移除该机制的JDK。

## 后续准备限制

**新jar已核对，不等于新申请入口已就绪。** Host和启动器仍指向旧55路径，旧包存在且不可覆写。直接PrepareProbe会先构建后因旧路径已存在拒绝，不能把旧包/旧授权喂给新jar。本轮不执行PrepareProbe，也不改入口路径。准备新包时必须先安排新的不覆盖路径，并对受影响源码和构建身份重新绑定；这属于下一项明确范围工作，不能跳过或宣称当前jar可直接产出新的已绑定申请包。

仍未知：真实模型是否遵守新枚举与条件键、完整P02能否同task完成续答和发布、冻结后的可靠性及学生收益。没有真实在线成功的新证据，不用本地202项或此构建核对替代。

## 保留、恢复与复核

4个旧冻结jar、旧55申请包/披露、主仓库4份既有文档改动均未变；原失败批次及关闭额度保留。默认jar已替换为本次新构建，回退时使用已记录SHA的旧冻结副本，不按相对路径猜构建。撤回本证据提交不自动删除本机新jar或恢复额度。

本目录仅提交明确证据和本地诊断程序；不含jar、class、依赖解包、整本PDF或凭证。[构建身份](build-identity.json)、[源码身份](source-identity.json)、[实际合同](runtime-contract.json)、[运行资产](runtime-assets.json)、[验收](verification.json)、[上一轮测试绑定](test-evidence-binding.json)供复核。本轮到此停止，不准备新包或开启在线批次。
