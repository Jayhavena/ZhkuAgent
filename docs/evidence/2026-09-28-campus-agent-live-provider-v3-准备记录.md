# 校园 Agent 真实供应商验收准备 v3（2026-09-28）

本记录接续 [v1 受控验收](2026-09-28-campus-agent-v1-验收记录.md)与 [v2 专用临时库验收](2026-09-28-campus-agent-temp-db-v2-验收记录.md)；保留 `five-level-routing-v1` 历史 2/5，不修改其预期或语料指纹。

## 已有真实供应商冒烟证据

本机 `knowledge/target/surefire-reports/TEST-com.zhku.knowledge.rag.DashScopeProviderSmokeTest.xml` 的最后修改时间为 **2026-09-28 08:20:40 UTC**。报告显示 `zhku.rag.smoke=true`、**2 运行 / 0 失败 / 0 错误 / 0 跳过**，测试名分别为真实 `qwen-plus` 对话调用及 `text-embedding-v4` 的 1024 维向量调用。测试类还要求进程环境中的 `DASHSCOPE_API_KEY`；报告不保存密钥值。本轮仅核对该本机已有报告，**没有重跑供应商调用**；冒烟通过只代表当时的接口可用，不代表校园 Agent 真实工具选择或检索质量。

## 下一项同场验收的准备状态

新增默认禁用的 `CampusCreditAgentLiveProviderTest`，并给专用临时库脚本增加显式 `-RunLiveProvider` 选项。测试只接受 `jdbc:postgresql://127.0.0.1:<端口>/zhkuagent_e2e`，拒绝演示库端口 54329；先发布审核片段，再由真实 `qwen-plus` 选择政策查询、学分纯计算，经过五级规则链与 HTTP 控制器，断言 3.5 学分、4 学分规则和来源 ID/定位。脚本默认路径仍只运行可控模型；无显式选项不会外发资料。

授权审查所需的**对外发送内容**：目标为 `https://dashscope.aliyuncs.com/compatible-mode/v1` 的 `qwen-plus`。首轮提示包含“这两门虚构公选课学分合计及每学期上限？”及虚构课程 `ART01 / 公共选修课 / 1.5 学分`、`HIS02 / 公共选修课 / 2 学分`，另含两项获准工具的名称、描述和参数模式。政策查询后，下一轮提示会加入已审核《本科生学生手册（2025版）》片段的上限结论“每学期公共选修课最高不得超过 4 学分”、资料标题及 `PDF 第 76—77 页，第 8、10 条`。策展 JSON 声明 `review_status=审核通过`、`student_visible=true`、`sharing_rights_confirmed=true`；**尚未单独取得该具体文本发往外部服务的确认**。不发送真实学生身份、成绩或教务系统数据；API 密钥仅作为供应商鉴权使用。

本轮验证：新测试的 Maven 编译成功；未启用 live 标志时，`CampusCreditAgentServiceTest` 8 项、`CampusAgentPreviewControllerTest` 2 项通过，live 测试 1 项跳过，聚焦回归合计 **11 运行 / 0 失败 / 0 错误 / 1 跳过**，因此不计为真实端到端通过。`verify-campus-temp-db.ps1` 在 Windows PowerShell 中解析错误为 0。当前 Docker daemon 未运行，专用临时库脚本本轮未执行。

真实调用被自动审批审查拒绝，原因为冒烟测试会将仓库的校园政策片段发送至外部 DashScope，而该具体载荷的外发授权尚未明确。本轮没有改走其他出口调用供应商。待外发范围得到明确确认、Docker 可用后，才能执行 live 选项并逐项记录真实通过、失败、跳过及模型工具选择；真实向量索引、同批资料的词法＋向量 RRF、当期通知优先级和真实供应商超时释放仍需分别验收。
