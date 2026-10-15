# T05-56 本地构建与资产身份核对

结果：LOCAL_BUILD_AND_IDENTITY_CHECK_PASSED。源码提交`796c95894611fcdcd041b79da68b5f7fe0b572ac`，分支codex/t05-online-evidence。未修改生产源码，未生成新请求包或披露申请，未启动Docker、未操作数据库、真实模型调用0。工程成果已冻结，真实在线验收未通过。

## 新构建身份

- jar SHA256：`82c25684f5993b96ddea4fa72b6bca85e25e055ab82bedac663593081f0b1541`，字节数43835415。
- 默认输出：`knowledge/target/knowledge-0.1.0-rc.1-controlled.jar`。
- 保留的本机冻结副本：`knowledge/target/clarification-796c9589-82c25684f599-controlled-frozen.jar`，SHA一致；构建产物不提交Git。
- 335项生产源码/启动文件的工作区字节与HEAD匹配；另核对根/模块POM、共享模块源码及wrapper配置等14项受Git管理构建输入。额外非Git构建输入0项，明细见build-identity.json。
- 新jar的659个应用class与knowledge/target/classes全量匹配；较旧85b95a1c只增加ReviewedCondition类，无历史类遗留或缺失。内嵌agent-runtime jar与模块产物相同，27个共享class匹配；全部内嵌依赖另存SHA列表。
- jar未嵌入Git提交SHA；身份通过明确源码提交、构建入口、class/依赖哈希及实际类加载来源绑定，不声称跨机器可复现相同二进制。

## 实际运行检查

使用新jar的PropertiesLauncher执行--CheckRuntimeAssets，15项审核运行资产存在、SHA及使用边界与旧清单一致；包含外部原PDF的身份核对，但未复制PDF。未执行会创建数据库的--CheckRuntimeAssembly。

用本目录LocalBuildIdentity.java作为临时本地反射入口，应用合同/Host类实际从新jar的BOOT-INF/classes加载，核验版本RULE_PUBLICATION_REVIEW_02、条件目录、REVIEW_INPUT_SNAPSHOT及4决策+1核验策略匹配。直接将旧51包交给新jar的validatePackage，已拒绝；新路径绑定55。未实例化Provider/DataSource或运行任务。

## 构建和测试边界

通过普通用户持久PATH的CMD启动器-BuildOnly离线构建，退出0；脚本只有在子进程退出、未超时且resourcesClosed时成功。现有启动器不打印Maven捕获stdout，故build.log可能为空；以build-process.json退出、fresh jar哈希和class/运行检查为证，不虚构完整Maven控制台日志。

本次package使用skipTests，不重新声称跑过194项。已逐项核对上一轮796c9589提交的已验收源码哈希和10份测试XML哈希，对应194项通过记录均未变。新jar包装及运行身份为本轮fresh验证；这不代表真实模型理解、新schema供应商支持、完整P02、可靠性或学生收益通过。

WindowsPowerShell5.1→CMD→PowerShell7普通入口的CheckEnvironment和会话UTF8中文样例通过，0构建/模型/数据库调用。dockerAvailable只表示CLI存在，不表示引擎可用或已启动。本轮无需Docker。

## 保留与恢复

原3个冻结jar、旧51请求包及关闭额度均未改变，剩余0；主仓库58e1ef7未合入本工作树，4份既有产品文档字节未变。默认构建jar由85b95a1c替换为本次新jar，旧二进制可从既有冻结副本恢复，恢复不恢复外发额度。

本目录只保存构建/核对证据及临时检查程序；解包依赖和编译helper在系统临时目录，不进入产品构建或Git。只提交明确证据清单。撤销本证据提交不删除已保存的本机jar；旧/新副本身份以SHA校验，不能因相对路径误用构建。

下一步可基于该冻结jar准备新的55请求包和完整外发披露，再单独请求真实批次授权。本轮不自动开始该任务或在线批次。
