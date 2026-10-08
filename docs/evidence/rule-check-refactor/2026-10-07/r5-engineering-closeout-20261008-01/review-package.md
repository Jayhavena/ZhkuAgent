# 最近工程收尾改动：一次合并审查包

只读审查，不修改文件、分支或索引，不执行数据库/模型/浏览器，不派生其他审查者。

本仓大量未提交历史工作，不能用HEAD diff当本批增量。按以下before/diff审查当前代码，生产范围为旧文案和HTTP客户端字段映射；新增Java仅test classpath，runner仅登记非供应商测试。

## 范围

1. agent/frontend/src/api/controlled.js、src/views/CampusRun.vue、tests/controlled-copy.test.mjs：before与变化见 ../r5-legacy-copy-20261008-01/source-before.json、changes-checkpoint.diff。
2. agent/frontend/src/api/rule-check.js、tests/rule-check-api.test.mjs、tests/fixtures/rule-check-answer-http.json：before见 ../r5-browser-chain-20261008-01/scope-extension.json，变化见该批changes-checkpoint.diff。HTTP字段declaredConditions严格核验后映射UI conditions；不得以自造前端夹具证明Java契约。
3. knowledge/src/test/java/com/zhku/knowledge/retrieval/RuleCheckBrowserHttpTest.java：新增宿主，当前又补实际HTTP五状态与澄清续答预检、合成第二原文、有限命令和600秒窗口。原字节见本批before/browser-host.java。特殊live批次等待浏览器，普通回归不等待。
4. knowledge/src/test/java/com/zhku/knowledge/controlled/RuleCheckLegacyBrowserHttpTest.java：新增旧v1实际应用/鉴权/派发/内容冲突与有限浏览器租约；现有纯合成Model，精确临时目标守卫，正常Context退出和本夹具UUID清理。
5. knowledge/scripts/verify-rule-check-refactor.ps1：此前已登记BrowserHttpTest，当前新增LegacyBrowserHttpTest。目标、凭证来源、供应商禁用与生命周期约束不放宽。

## 要求和重点

按 docs/superpowers/plans/2026-10-08-rule-check-r5-plan.md 的Task3、Task3C2/A09/A10，及 docs/product/rule-check-decision-contract.md。用户本次仅要求收齐工程验收，不批准真实资料发布、框架迁移或新产品功能。

- 已有任务不得继续显示未受理/正在确认；同键不同内容需明确冲突，网络未知不得自动重发。
- Java真实响应能够通过严格客户端合同；UI条件名、未知、冲突和原文不跨题/身份继承。
- 只允许明确专用临时目标，口令仅公开合成夹具，绝不复用真实密码或调用真实模型；泄露的测试UUID不是新权限。
- 宿主超时仍失败，成功宿主退出不代替浏览器观察；Test/Application资源及合成行清理可信。特别看setup失败、提前return、场景切换、第二chunk及账户清理。
- 旧v1和v2不共用权限/路径；原默认CLOSED与真实DRAFT不变。

## 当前证据及限制

v2 live05实际HTTP五状态+续答预检与原文，浏览器五状态/双方原文/撤回/退出/换账号/维护者/编辑清旧/首页/刷新已观察，显式STOP，JUnit1通过及resourcesVerified/sourceDrift=0。该1项不是浏览器断言计数。

前端本批沙箱realpath EPERM造成2个测试文件未运行，属于环境错误；同集合正常权限重跑结果见frontend-final.log。旧v1本批Docker停止未创建库/mavenStarted=false，失败记录保持；恢复后live02单独验收。普通Windows PowerShell5.1重建User/Machine PATH、UTF8会话边界与包装入口环境检查通过（不等同实际业务菜单验收），ordinary-terminal.json。

人工真实资料与供应商语义、用户收益、完整发布治理不在此验收通过范围。最近一次全后端503为历史时点，本批不重跑无新生产Java改动的整套。

请输出Critical/Important/Minor（文件/行号、触发与影响、最小修复建议），并单列Declined to judge。没有问题也明确说明审查边界；不做泛泛架构重写建议。用户对耗时敏感，请只做本包的一次集中只读审查。
