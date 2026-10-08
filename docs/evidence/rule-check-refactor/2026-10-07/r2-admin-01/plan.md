# R2 执行计划与白名单

用户再次授权下一步，范围为P0 Task 3。业务白名单：ZhkuLocalAdminController.java、zhku/local-admin/index.html、ZhkuLocalAdminControllerTest.java；新增CandidateResponseMappingTest.java。既有PageResourceTest复验但不改写。另同步六份文档/台账，不改学生v1 DTO、审核服务、发布器、POM或Flyway。

先修改既有MockMvc响应断言，确认真实JSON字段缺失；再用旧实现启动回环管理服务观察原文/编辑区缺失和全量渲染。随后最小扩展管理响应及页面，在既有非供应商入口跑GREEN/相关回归；用新临时库执行浏览器流程。

新辅助脚本local-browser-service.ps1只准备本批随机无卷临时库、合成审核夹具与回环服务，并finally清理；固定端口55439/18124，数据库名zhkuagent_rulecheck_e2e，先检查端口占用。每次数据库操作前刷新已配置凭证，实际使用随机临时口令，密钥不入参数/证据。源码使用当前target/classes；classpath沿用已存在manual-demo/classpath.txt，过滤已不存在的旧路径，不新增依赖或复制依赖库。SQL直接准备提取态候选，明确本轮未验证PDF上传/提取。

判定：JSON保留原文且不替换编辑/审核状态；新候选草稿默认原文但不自动保存；HTML字面显示；最多20卡；分页/筛选保留草稿和全局待审计数；审核结束片段只读；显式保存后才可通过；待审仍在时发布被锁定；合成流程终态/审核事件/学生可见资格可核对；服务和容器退出、连接0。

R2入口运行过程中不修改源码；回归结束后才启动浏览器服务，避免把持续验收的Java进程误记为未清理测试进程。G0仍未通过，本批不进入学生试点。
