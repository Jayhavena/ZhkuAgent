# 校园规则查证 P0 实施计划（Implementation Plan）

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking. 顺序执行，默认不使用子代理。

**Goal:** 完成安全基线与四项局部修复，获得进入单主题查证试验的条件，不宣称产品重构完成。

**Architecture:** 保留现有模块与 DTO 兼容，修复检索预算、管理员原文展示、模型输出结构和在线资料资格。事实语义、时间元数据与发布清单仍由后续 R5/R6 完成。

**Tech Stack:** 当前 JDK 21、Maven wrapper、JUnit/AssertJ、Spring/JDBC、PostgreSQL/pgvector、既有管理 HTML。禁止为这批新增产品依赖。

**Spec:** [产品范围约定](../../product/rule-check-scope.md)、[主路线图](2026-10-07-rule-check-refactor-roadmap.md) 与 [诊断报告](../../reviews/2026-10-07-refactor-design.md)。五种查证业务状态是后续目标，不修改本批ModelResponse四字段协议。

日期：2026-10-07；状态：待未来实施指令。本次只写计划，没有运行本文件测试或修改业务实现。

评估口径沿用 [Eval 协议](../../product/rule-check-eval-contract.md)：R0准备人工参考和原子rubric，P0以确定性回归为主，不要求先引入付费模型裁判或通用平台。质量、已知风险与裁判验证样本用途分开。

## Global Constraints

严格沿用主路线图 Global Constraints 和 AGENTS。R0 先于数据库写测试；专用目标为 `127.0.0.1:55439/zhkuagent_rulecheck_e2e`，若端口占用可在执行前登记另一非54329回环端口。每次批次新目录，不覆盖旧 XML/报告。涉及框架特定实现/API时按 AGENTS 使用 Context7，再用本项目版本编译验证，不凭最新文档更换依赖。

## Review Focus

1. 测试替身忽略 limit 会让 RRF 漏召回测试假通过：Task 2 替身必须截断。
2. 原文是 HTML/注入或扫描空白页：Task 3 安全显示并提示人工处理，不静默丢弃。
3. 直接构造 NaN 绕过 parser：Task 4 guard 同样拒绝，不只测 JSON。
4. 最接近的向量都不合格：Task 5 资格过滤前不得 LIMIT，合法较远条款仍应召回。
5. SQL 资格、Java资格、profile状态与索引内容漂移：Task 5 真库矩阵验证，Task 6 说明仍未完成时间映射。

## 0. 文件职责与改动白名单

所有以下新增路径是计划，不表示已有文件。开始前重新核对占用与源码哈希；发现已被其他任务修复，先复验后缩减任务，不重复改写。

| 任务 | 创建/修改 | 责任 |
| --- | --- | --- |
| Task 1 / R0 | 复核/补充 `docs/product/rule-check-scope.md`；创建 `knowledge/scripts/verify-rule-check-refactor.ps1`、`.cmd`；`scripts/tests/verify-rule-check-refactor-contract.ps1` | 范围与实际资料核验、无供应商测试入口、临时库/结果/资源边界 |
| Task 2 / R1 | 修改 `knowledge/src/main/java/com/zhku/knowledge/retrieval/HybridRetrievalService.java`；`knowledge/src/test/java/com/zhku/knowledge/retrieval/HybridRetrievalServiceTest.java` | 候选预算与最终 K 分开；保持降级 |
| Task 3 / R2 | 修改 `knowledge/src/main/java/com/zhku/knowledge/governance/web/ZhkuLocalAdminController.java`；`knowledge/src/main/resources/zhku/local-admin/index.html`；现有 `ZhkuLocalAdminControllerTest.java`、`ZhkuLocalAdminPageResourceTest.java`；必要时新增同包 `CandidateResponseMappingTest.java` | DTO 原文、对照编辑、有界 DOM与安全渲染 |
| Task 4 / R3 | 修改 `knowledge/src/main/java/com/zhku/knowledge/rag/ModelOutputParser.java`、`AnswerEvidenceGuard.java`；按直接构造行为决定是否改 `ModelResponse.java`；新增同包测试 `ModelOutputParserTest.java`、`AnswerEvidenceGuardTest.java` | 类型/范围/非空证据合同，不假装语义校验 |
| Task 5 / R4 | 修改 `knowledge/src/main/java/com/zhku/knowledge/retrieval/JdbcVectorCandidateRetriever.java`、`JdbcLexicalCandidateRetriever.java`；修改 `knowledge/src/test/java/com/zhku/knowledge/retrieval/JdbcCandidateRetrieverTest.java` 及确需迁移到新资格语义的检索夹具 | ACTIVE 与已知资料/index资格落实到读取 |
| Task 6 | 新批次验收报告、issues/metrics更新 | 汇总证据与 G1 判断；不夹带其他产品改动 |

公共 runtime、学分 loop、旧 Flyway 文件、原演示数据、POM 和前端目录迁移不在白名单。调用方兼容修复若超出清单，先把文件和理由加入本批声明，不能无说明扩张范围。

## 1. 统一验证入口和证据输出

Task 1 创建的 `.cmd` 入口接受：

```text
-Mode Unit|Database|Acceptance
-Phase <本次唯一的a-z0-9-名称>
-Tests <显式测试类名，以逗号分隔>
-Port 55439
```

批次证据写入 `docs/evidence/rule-check-refactor/2026-10-07/<Phase>/`。参数格式、选类、批次存在性、目标隔离在外发/DB操作前检查。Unit 不能启动数据库或供应商；Database 创建临时容器并强制显式 URL；Acceptance 只包含已冻结的相关集合，交互浏览器和真实模型另设人工选择，不塞入默认选类。

单元白名单起点：HybridRetrievalServiceTest、HybridCandidateFusionTest、GroundedAnswerComposerTest、RetrievalAugmentedAnswerServiceTest、RetrievalEligibilityFilterTest、ZhkuLocalAdminControllerTest、ZhkuLocalAdminPageResourceTest。新增 parser/guard/mapping 测试随任务加入。数据库起点：KnowledgeSchemaTest、JdbcCandidateRetrieverTest。其他必需类在声明真实来源与性质后加入；禁止通配符和供应商测试自动匹配。

未来创建入口后，执行形态如下。这些新入口命令本轮未执行；Task 1 必须在普通 Windows 终端验证后才能标记可用：

```powershell
.\knowledge\scripts\verify-rule-check-refactor.cmd -Mode Unit -Phase r1-red -Tests HybridRetrievalServiceTest
.\knowledge\scripts\verify-rule-check-refactor.cmd -Mode Unit -Phase r1-green -Tests HybridRetrievalServiceTest,HybridCandidateFusionTest
.\knowledge\scripts\verify-rule-check-refactor.cmd -Mode Database -Phase r4-green -Tests KnowledgeSchemaTest,JdbcCandidateRetrieverTest -Port 55439
```

该入口内部调用根 Maven wrapper，显式传 `-pl knowledge -am`、测试类集合、隔离 URL、`-Dsurefire.failIfNoSpecifiedTests=false` 和用户可用 Maven 缓存；不将密码放参数。验证 exitCode、实际 XML 选类/计数、必需类无跳过、源码前后哈希和容器/连接退出，不能只检查 exitCode=0。

## Task 1：R0 基线与安全测试入口

**Consumes：**AGENTS、当前源码、主路线图、现有隔离runner作为参考。**Produces：**范围卡、36题版本清单、隔离入口、白名单、baseline清单。问题：RC-01/09/10。

- [ ] **Step 1：记录当前基线。** 保存HEAD、白名单文件SHA-256、dirty路径、3C与browser-supplement证据链接；声明旧记录未重跑。复核已有范围约定，补实际主题/资料核验；锁定36题质量集（20开发/16报告）与独立已知风险集。标注完整/部分回答、缺条件、缺证、冲突与必要/重复追问，记录用途/采样性质/parentCaseId、原文与rubric；简单改写按家族隔离。初期人工评分，语义能力不由结构测试推断。文档已整理不等于基线或G0已完成。
- [ ] **Step 2：编写入口合同反例。** 参数为空、通配符类、LiveProvider/DashScope类、原库URL/端口、外部host、已有Phase、未知模式、未登记类必须在启动Maven/容器前拒绝。合同测试输出实际是否启动进程，不能把“随后连接失败”当安全拒绝。
- [ ] **Step 3：实现目标校验。** 在新脚本中使用同等约束的函数，先验证再构造任何连接：

```powershell
function Assert-RuleCheckDatabaseTarget([string]$JdbcUrl) {
    $matched = [regex]::Match($JdbcUrl,
        '^jdbc:postgresql://127\.0\.0\.1:(\d{4,5})/zhkuagent_rulecheck_e2e$')
    if (-not $matched.Success) { throw '仅允许专用回环临时数据库' }
    $testPort = [int]$matched.Groups[1].Value
    if ($testPort -lt 1024 -or $testPort -gt 65535 -or $testPort -eq 54329) {
        throw '拒绝原演示库或非法端口'
    }
}
```

- [ ] **Step 4：实现执行/结果/退出。** 复用既有runner的临时容器、迁移、XML汇总与finally清理模式，移除旧阶段专用BOARD/lease写入；新阶段只写自己的证据。Process/User/Machine变量重新检查，临时库使用本次隔离口令，调用环境退出时恢复；不得记录值。若资源无法退出，结果标失败并说明残留，不能报绿色。
- [ ] **Step 5：验证普通用户入口。** `.cmd` 按既有启动器模式寻找实际PowerShell7，保留退出码，不假设裸pwsh；Node解析复用 Resolve-ZhkuNode，所有新路径先 Test-Path。用合同反例验证零外发/零原库访问，再执行纯单元基线；Database模式第一次只跑KnowledgeSchemaTest。
- [ ] **Step 6：保存证据。** 正反目标、实际命令结构、工具版本、XML/计数、provider=0的验证性质、资源退出、源码哈希与范围卡。只有这些齐备才记G0通过。

**Expected：**任何不安全目标在构建连接前失败；Unit不建容器，Database只触达新库；无零测试假绿。当前尚无这些执行结果。

## Task 2：RRF 行为反例与兼容修复

**Consumes：**Task1 Unit入口。**Produces：**兼容旧方法的新候选预算重载。问题：RC-02。

- [ ] **Step 1：在现有测试类增加真实截断的反例。** 使用该类已有A/B/C、service和idsOf：

```java
@Test
void sharedSecondRankMustSurviveBeforeFinalTruncation() {
    var lexical = List.of(A, C);
    var vector = List.of(B, C);
    var outcome = service.retrieve("校园规则", 1,
            (query, limit) -> lexical.stream().limit(limit).toList(),
            (query, limit) -> vector.stream().limit(limit).toList());
    assertThat(idsOf(outcome)).containsExactly("chunk-c");
}
```

- [ ] **Step 2：运行 r1-red。** 预期为实际排名断言失败，不能拿新重载不存在导致编译失败代替该反例。保存旧源码哈希、请求limit和最终结果。
- [ ] **Step 3：最小实现。** 保留四参入口并委托五参重载；独立candidateLimit，先融合、最后截resultLimit。默认20只是首轮基线，显式重载约束candidateLimit≥resultLimit>0，修改错误注释。

```java
public RetrievalOutcome retrieve(String query, int resultLimit,
        ChunkCandidateRetriever lexical, ChunkCandidateRetriever vector) {
    return retrieve(query, Math.max(resultLimit, 20), resultLimit, lexical, vector);
}
```

- [ ] **Step 4：新增边界断言。** 显式候选2/结果1得到C；候选1/结果2及非正数拒绝；单路不可用、双路空、重复候选和稳定排序保持。既有fakeRetriever改为严格遵守limit。
- [ ] **Step 5：运行 r1-green 与相关检索单元。** 开发集比较10/20/40候选，记录召回/耗时/返回量，不调锁定留出。
- [ ] **Step 6：保存diff、RED/GREEN、指标与问题更新。** RC-02仅关闭截断缺陷，不宣称整体检索最优。

## Task 3：候选原文与安全审核界面

**Consumes：**当前KnowledgeCandidate原文与既有本机管理边界。**Produces：**CandidateResponse.rawText、原文/编辑对照与20条DOM切片。问题：RC-03。

- [ ] **Step 1：添加DTO映射断言。** 在治理web同包的映射测试使用真实record，所需imports为UUID/Instant、KnowledgeCandidate、CandidateReviewStatus和JUnit/AssertJ：

```java
@Test
void candidateResponseKeepsRawTextWithoutApprovingIt() {
    var now = Instant.parse("2026-10-07T00:00:00Z");
    var candidate = new KnowledgeCandidate(UUID.randomUUID(), UUID.randomUUID(),
            1, 1, "原文：最高修读4学分。", null, null, null,
            CandidateReviewStatus.PENDING_REVIEW, null, null, now, now);
    var response = ZhkuLocalAdminController.CandidateResponse.from(candidate);
    assertThat(response.rawText()).isEqualTo(candidate.rawText());
    assertThat(response.editedText()).isNull();
    assertThat(response.reviewStatus()).isEqualTo("PENDING_REVIEW");
}
```

- [ ] **Step 2：先记录旧JSON/网页失败。** 新accessor尚不存在时属于结构RED，另外保存旧JSON无原文字段及真实新候选空编辑区观察；不得仅凭编译失败声称用户问题已复现。
- [ ] **Step 3：DTO最小扩展与页面显示。** from复制rawText；原文只读，编辑区草稿初始化，不自动调用保存/approve。渲染方式：

```javascript
const original = document.createElement('pre')
original.textContent = candidate.rawText ?? ''
const editor = document.createElement('textarea')
editor.value = candidate.editedText ?? candidate.rawText ?? ''
```

- [ ] **Step 4：状态/分页/恶意文本验证。** 页面每次渲染20条，切页/筛选不丢待审状态；HTML作为字面文本；空白页提示人工处理；APPROVED/DISCARDED维持既有不可编辑约束。服务端仍全量返回的限制写入报告，不说后端已分页。
- [ ] **Step 5：单测与真实本机管理浏览器验收。** 真库只由专用入口准备少量合成夹具；对照原文→编辑→保存→审核→发布资格各步观察。
- [ ] **Step 6：保存JSON脱敏样例、页面观察、哈希、回归和真实/替身标识。** 不把管理原文加到学生v1响应。

## Task 4：结构、范围与空引用守卫

**Consumes：**既有ModelResponse四字段协议。**Produces：**严格解析与直接构造的守卫边界；不产生通用事实证明器。问题：RC-04结构部分。

- [ ] **Step 1：写parser行为RED。** 在rag同包的新测试中逐项检查根null/数组、数字answer、字符串uncertainty、数值citation、缺handoff、非布尔handoff、空引用项与越界分值。示例：

```java
@Test
void parserMustRejectCoercedFieldTypes() {
    String json = """
        {"answer":42,"uncertainty":"0.9",
         "citationIds":[7],"handoffRequired":false}
        """;
    assertThatThrownBy(() -> ModelOutputParser.parse(json))
            .isInstanceOf(ModelOutputFormatException.class);
}
```

- [ ] **Step 2：写guard行为RED。** 当前verify签名不变，因此下面应在旧实现中出现真实断言失败：

```java
@Test
void substantiveAnswerCannotPassWithNoCitation() {
    var approved = List.of(new ApprovedEvidence("ev-1", "上限4学分。", "原文"));
    var output = new ModelResponse("上限4学分。", 0.9, List.of(), false);
    assertThat(new AnswerEvidenceGuard().verify(output, approved)).isPresent();
}
```

- [ ] **Step 3：严格检查节点再读取。** 根对象、answer文本且非空白、uncertainty数值且有限0–1、citationIds数组且每项非空白字符串、handoffRequired布尔；非法统一ModelOutputFormatException，不能NPE/宽松默认。不擅自改变现有自报分值的语义。

在现有JSON读取后替换字段转换逻辑如下；读取本身的异常继续映射为ModelOutputFormatException，null输入也须有受控异常：

```java
if (payload == null || !payload.isObject()) {
    throw new ModelOutputFormatException("模型输出根节点必须是对象");
}
var answer = payload.get("answer");
var uncertainty = payload.get("uncertainty");
var citationIds = payload.get("citationIds");
var handoff = payload.get("handoffRequired");
if (answer == null || !answer.isTextual() || answer.textValue().isBlank()
        || uncertainty == null || !uncertainty.isNumber()
        || citationIds == null || !citationIds.isArray()
        || handoff == null || !handoff.isBoolean()) {
    throw new ModelOutputFormatException("模型输出字段缺失、为空或类型非法");
}
double score = uncertainty.doubleValue();
if (!Double.isFinite(score) || score < 0 || score > 1) {
    throw new ModelOutputFormatException("模型自报分值必须是0至1的有限数");
}
List<String> ids = new ArrayList<>();
for (var id : citationIds) {
    if (!id.isTextual() || id.textValue().isBlank()) {
        throw new ModelOutputFormatException("引用标识必须是非空白字符串");
    }
    ids.add(id.textValue());
}
return new ModelResponse(answer.textValue(), score, ids, handoff.booleanValue());
```
- [ ] **Step 4：守卫覆盖直接构造。** 空引用/空白正文/非有限分值/未知ID均拒绝；保留既有handoff和办理断言边界。若在record构造器拒绝，测试确认此时也不能下发答案。
- [ ] **Step 5：回归composer和适配器协议。** 正常四字段返回仍可解析；拒绝时稳定降级，无原文候选不调用模型。把合法引用但4→40另记为开放语义反例，不用词汇/数字集合检查关闭它。
- [ ] **Step 6：更新RC-04为PARTIAL。** 格式子项可CLOSED，事实子项仍OPEN，防止一项修复后台账误写全部完成。

## Task 5：在线profile与合法候选读取

**Consumes：**Task1临时数据库、当前V9索引关系和资格过滤器。**Produces：**在线ACTIVE约束、已知资格与内容版本一致性、精确过滤检索。问题：RC-05读取部分。

- [ ] **Step 1：记录现有夹具状态。** JdbcCandidateRetrieverTest当前注册BUILDING却进行候选读取；保持该状态先写不可在线读取断言，合法排名夹具在修复后显式转ACTIVE，不能删状态测试以求全绿。
- [ ] **Step 2：写“inactive不外发embedding”RED。** 复用既有夹具PROFILE、dataSource、filter；新增AtomicInteger import，EmbeddingClient按当前真实两方法接口实现：

```java
var embeddingCalls = new java.util.concurrent.atomic.AtomicInteger();
EmbeddingClient client = new EmbeddingClient() {
    public int dimensions() { return 3; }
    public float[] embed(String text) {
        embeddingCalls.incrementAndGet();
        return new float[] {1f, 0f, 0f};
    }
};
var retriever = new JdbcVectorCandidateRetriever(
        dataSource, filter, PROFILE, "PUBLIC_ELECTIVE", client);
assertThatThrownBy(() -> retriever.retrieve("学分上限", 1))
        .isInstanceOf(RetrieverUnavailableException.class);
assertThat(embeddingCalls.get()).isZero();
```

- [ ] **Step 3：实现online检查。** 默认线上构造查询ACTIVE和维度；词法同样约束ACTIVE。构建路径允许BUILDING不等于在线读取允许BUILDING；发现离线评测确实复用线上读取时单独登记兼容需求，不通过放宽online约束处理。
- [ ] **Step 4：资格预过滤。** 加profile ACTIVE、索引run COMPLETED、source PUBLISHED/STUDENT_VISIBLE/share确认、scope以及entry/source版本与entry/chunk校验和一致；资格检查仍在Java最终复核。当前schema没有完整时间列，不编造SQL字段。

向量查询增加如下连接/条件；保留原SELECT字段与参数绑定。requiredScope允许null，两个scope参数都以相同值绑定。该SQL只覆盖现有字段，不代表完整时效控制：

```sql
JOIN embedding_profile profile ON profile.profile_id = entry.profile_id
JOIN retrieval_index_run index_run ON index_run.id = entry.run_id
WHERE entry.profile_id = ?
  AND profile.status = 'ACTIVE'
  AND index_run.status = 'COMPLETED'
  AND index_run.profile_id = entry.profile_id
  AND source.publication_status = 'PUBLISHED'
  AND source.visibility_scope = 'STUDENT_VISIBLE'
  AND source.sharing_rights_confirmed = TRUE
  AND (CAST(? AS VARCHAR) IS NULL OR source.knowledge_scope = ?)
  AND entry.source_version = source.source_version
  AND entry.content_checksum = chunk.content_checksum
  AND entry.embedding IS NOT NULL
ORDER BY entry.embedding <=> CAST(? AS vector), entry.chunk_id
LIMIT ?
```

PostgreSQL空值参数类型由显式CAST固定；两个scope值保持一致，不能因null参数无法推导而删scope条件。词法查询使用相同已知资格，去掉embedding非空要求和向量ORDER/LIMIT，继续按既有词面评分后截断。
- [ ] **Step 5：向量排序后有限读取。** 已知资格全部过滤后才ORDER BY distance并LIMIT；参数绑定保持当前余弦排序与稳定UUID次序。词法先限制到合法集合，命中词数/UUID顺序维持原合同，不突然LIMIT词法未排序集合。
- [ ] **Step 6：真库矩阵。** BUILDING/RETIRED、草稿/内部/未确认分享/scope不符、索引未完成、版本/校验和漂移分别不可召回；不合格最近向量在前而合格较远结果仍可取得；合法精确排序不退步。维度错不外发，异常只降级对应路。
- [ ] **Step 7：兼容检索夹具。** 只修明确不符新语义的profile状态/真实校验和，不改数据断言掩盖错误；新增与修改类名单写入command.json。
- [ ] **Step 8：保存查询计划、读取数、结果和退出。** RC-05读取部分可关，完整时间映射与清单仍OPEN至R6。

## Task 6：G1收口与回退演练

**Consumes：**Tasks1–5的批次证据。**Produces：**G1报告、台账更新、源码manifest和恢复说明。

- [ ] **Step 1：确认每项证据对应当前字节。** 定向GREEN后源码有变化，至少重跑受影响的必要集合；XML旧时间/缺类/跳过/漂移均不准通过。
- [ ] **Step 2：回归必要集合。** R1/R3：hybrid、composer、RAG服务及有关适配器合同；R2：管理映射/controller/page、审核/发布状态；R4：真库retrieval/indexing/相关eval及scope权限。只有触及controlled/身份/提交装配才额外执行相应3C门禁，避免每次低影响改动重复全量大验收。
- [ ] **Step 3：输出metrics。** 每个指标含样本数/分母/资料与模型性质，未测标NOT_RUN；不算新用户收益，不把单测数当准确率。
- [ ] **Step 4：验证回退只恢复本批。** 在隔离恢复样例或明确临时目录验证原字节/新增文件清单，原工作区不做递归清理；不删新旧迁移/私有数据。记录入口关闭策略，避免恢复了旧文件就认为旧binary可继续受理。
- [ ] **Step 5：判定G1。** RC-02/03和RC-04/05的P0子项通过；RC-01/04语义/05时效/06–09仍按范围开放。建立下一阶段R5任务计划前先汇报缺口和实际新增复杂度。
- [ ] **Step 6：更新交付状态。** 标“局部修复技术验证”，不写“校园查证上线”。只有未来G2/G4通过，才能追加用户价值与有限产品完成结论。

## 2. 每个任务的标准输出

每次收口提供：RC问题→复现方式→改动文件与原因→验证命令/实际结果→证据路径→当前限制→必要下一步。保存可回退原字节与增量快照；本计划不要求提交整个dirty仓库或自动推送。

### 自审

- [x] 任务落点与当前真实路径匹配；新增路径明确标为计划。
- [x] 5类Review Focus各自有归属测试；语义与时间未被格式修复冒充完成。
- [x] 旧方法与新重载参数顺序一致，DTO字段为rawText，根协议四字段不改名。
- [x] 前置临时库与真实/替身证据性质明确，未声明执行过本计划。
- [x] G1不自动进入P1/P2，不自动迁目录或升级依赖。
