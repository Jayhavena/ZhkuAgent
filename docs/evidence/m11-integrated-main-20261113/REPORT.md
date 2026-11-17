# M1.1 正式集成验收

2026-11-13，官方main已包含持久学生Task API。代码候选8c07cb64经独立只读审查发现装配断言回归，修复提交77b4a206补入CI后两项检查通过，PR #6合入main，实际被测提交为`505a0dea86c5b88f030c6692d8a9c0df9f52898a`。合并树与审查head无差异；本次从合并提交启动并重新生成persistent.jar，身份和规则实例均为本次专用，不使用共享54329演示库。

- PR：https://github.com/Jayhavena/ZhkuAgent/pull/6
- 修订head CI：https://github.com/Jayhavena/ZhkuAgent/actions/runs/43445280000 （trial-contracts、replay均SUCCESS）
- 正式main CI：https://github.com/Jayhavena/ZhkuAgent/actions/runs/43445786568 （SUCCESS）
- 先前运行报告提交30a0d933是独立文档身份，不是本次代码提交；本目录是新的合入后验收，未改写旧报告。

## 实际环境与结果

Windows本机，Java21/Maven离线缓存、Node24、Docker Linux引擎。具名实例m11-main-20261113，backend58160、identity55470、rules55471。数据库操作前重新核对Process→User→Machine凭证；启动器为隔离实例独立生成数据库凭据，凭据不入库。审核资产使用本机只读main资产根与已核对2025手册原件，不提交PDF。jar摘要/大小详见baseline.json，构建产物本身不入库。

正式版本启动后执行仓库自带`persistent-student-http.mjs initial`，14项全部通过；同实例Restart -SkipBuild后执行restart，14项全部通过。两轮都真实访问Spring HTTP、隔离PostgreSQL身份库与资料工具：注册/登录、任务能力和模拟模式、CSRF拒绝、创建幂等、WAITING_USER必要澄清、同账号原文、跨账号404、续答幂等、RESOLVED含发布事实、回放未覆盖披露、取消/已完成409边界、退出401。重启后原账号可重新登录、旧任务404，并可再完成一次全流程。

运行命令采用手册启动器，显式InstanceName/三端口/审核资产与PDF路径，默认SIMULATED；HTTP脚本使用ZHKU_STUDENT_TEST_BASE_URL=http://127.0.0.1:58160及本机tmp/m11-main-http输出目录。首次构建去掉SkipBuild，重启使用SkipBuild。不是复制旧jar回执。本轮providerCalls=0，ENGINEERING_REPLAY，真实模型/质量/费用未验证。

验收后Stop返回0，stopped.json记录hostExitCode=0；docker inspect显示专用身份容器exited，规则临时容器不存在，58160/55470/55471均无监听；身份卷保留。关停后不留运行服务。首次ready回执未在重启前复制，因此只保存实际采集的restart-ready，不重建或冒充首启ready；两轮HTTP回执分别保存。

## 原功能边界与后续

修订本机Java选类218项，217通过、1因手册条件跳过；包含旧装配、安全、Controller和原文服务。前端202项与生产构建在候选同生产树上通过，远程main CI再次执行前端和离线Java/Grader回归。没有观察到所覆盖功能回归；未全量覆盖所有历史数据库业务路径，不能据此声称所有功能均无缺陷。

M1.1的集成/重建/关键HTTP/重启完成。M1.2浏览器完整学生流程仍待本次正式main复验；M2前须补ONLINE正向装配测试并冻结新Probe范围/预算。Stop非0退出呈现、长期稳定性、HTTPS/邀请制/隐私、真实模型和实际账单仍按M11-REVIEW记录跟踪。本次不是正式产品发布或在线学生准入。
