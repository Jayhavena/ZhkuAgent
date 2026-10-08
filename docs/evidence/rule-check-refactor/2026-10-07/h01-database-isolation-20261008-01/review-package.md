# Task2L / H01 旧数据库测试隔离最终增量审查

仅审42项 before/*.bin → 当前差异（source-before.json/source-review.json/changes-review.diff）；巨大dirty checkout其余修改不属于本批。只读，不写文件、不跑测试/DB/模型/网络、不派agent/发其他线程消息。规范计划Task2L末尾、ledger Rulings、剩余任务H01和AGENTS凭证/临时库约定。

32个旧原演示库默认URL统一改为guarded shared getter，class static字段/数据源构造即检查；TestFlyway配置前再次guard参数。仅显式系统属性URL，精确127.0.0.1、1024..65535非54329、三个既有精确库名rulecheck/run_archive/zhkuagent_e2e，无userinfo/query/fragment/尾随；保留测试密码来源。第三名是现有verify-campus-temp-db和CampusCreditAgentLiveProviderTest的专用目标，未运行供应商。原negative literals保留。

额外发现隔离SubmissionProductionStartupProbe仅挂载一个class，继承环境ZHKU_DB_URL可能不等于父测试系统属性；新自包含guard必须在Application启动/迁移之前拒绝非精确archive目标，固定异常输出无URL/cause。不依赖没挂载的TestConfig helper；单元直接检查guard，不称已打包子进程正向启动。

连接入口清单见connection-entrypoints.json：32个旧getter；R5 reader/audit/retriever/HTTP既有更窄RuleCheckDatabaseTestTarget；ArchiveTestDatabase.dataSource/migrate及其fixtures；PreviewIdentitySchemaIntegration直接Flyway只接已guarded archive；CampusCreditAgentLiveProvider先断言target再共享Flyway；ControlledLiveModelAcceptance先ControlledWebDemoSetupCli.validateTarget后JDBC/SQL；Conversation/Submission restart probe经ArchiveTestDatabase或父fixture明确转交已校验DataSource。ProductionStartupProbe新补guard。此为源审查，不是所有旧类执行证据；任意其他遗漏请指出。

无DB RED11/10fail，兼容/探针RED14/2fail；0errors/skips，未调用代表性fixture生命周期。第一GREEN13类50PASS；最终45类444PASS=312纯单元+120专用临时库含12HTTP+12凭据配置。无卷55467/V22/连接0/实际容器和Java退出、566源码0drift/XMLhash、29计数替身runner合同。32默认站点不是32真正执行DB类，本轮仅额外12旧DB类39项执行。

生产main全部文件/真实2025DRAFT/20已确认参考/36题保护不变。未运行provider/report evaluation，未原库写入/部署/commit。默认全量Maven缺显式目标将fail closed，完整纯/DB/HTTP/provider CI分类仍F06；正向packaged probe仍属于原验收入口，本批只新增非法目标guard单测。不由测试数推断质量/收益。

请返回Strengths/Critical/Important/Minor(绝对file:line、触发条件、最小修复)/Declined to judge/Assessment。重点：setup/cleanup前所有默认入口、迁移防绕过、窄目标兼容、probe单类挂载和继承地址、无secret自由错误、test-only保护/证据计数。一轮审查，修复后最终验收不重审。
