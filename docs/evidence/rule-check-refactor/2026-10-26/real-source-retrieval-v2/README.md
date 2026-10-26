# 真实语料检索评测集 v2.0.0-rc1

状态：**候选集已建立，可用于受控开发集算法对照；尚未通过独立复核，不可作为生产质量结论。**

## 1. 这版解决什么问题

v1 的五条问题已经证明真实 Embedding、向量检索和 RRF 链路可以运行，但不能给出可信的综合指标：有一个正向金标准只存在于内部/归档资料，有些目标 chunk 覆盖多条条款，学分上限 chunk 的页码/条款绑定不准确，类别澄清问题还要求三组证据却只标了不完整的 locator。

v2 保留 v1 不动，建立新的机器可校验数据集。它把检索排名、完整证据集、权限排除和知识范围边界分成不同验收项，避免把“证据不存在/不允许访问”记成普通 Recall 失败。

## 2. 文件

- `cases.json`：15 条正向检索用例 + 1 条权限排除门禁 + 2 条知识范围门禁。
- `corpus-snapshot.json`：从隔离数据库只读导出的 17 个真实 chunk/source 元数据快照。8 个合格片段包含正文；9 个不合格片段只保留 ID、资格字段和内容校验和，不复制其受限正文。
- `validate-v2.ps1`：本地 JSON、语料指纹、chunk/source/checksum/范围绑定、资格、用例完整性及文件哈希校验。
- `score-v2-results.ps1`：读取各算法的 Top 5 结果，计算 Recall@5、MRR、完整证据集通过率，并单独统计权限/范围门禁。
- `manifest.json`：数据集版本、语料指纹、文件 SHA-256、统计与已知限制。

## 3. 语料与资格

来源快照：`2025版学生手册2025.9.2.pdf`，记录的 SHA-256 为 `dd036279ca21beea40351815d21d6d49f1037448a4df45c95e1eb2f8b199f653`；对应《仲恺农业工程学院选修课程管理办法》（仲字〔2021〕65号），涉及 PDF 物理第 76—77 页、印刷第 64—65 页。

数据库快照共有 17 个 chunks：

| 资格 | 数量 | 处理 |
| --- | ---: | --- |
| PUBLISHED + STUDENT_VISIBLE + 分享权已确认 | 8 | 进入正向算法比较候选池 |
| ARCHIVED、INTERNAL_ONLY 或分享权未确认 | 9 | 不进入正向候选池；作为权限审计元数据 |

正向候选池按当前资格标记分为 `PUBLIC_ELECTIVE` 5 个、`PUBLIC_ELECTIVE_CREDIT_LIMIT` 1 个、`ELECTIVE_RETAKE` 2 个。主对照模式是 `ALL_ELIGIBLE_STUDENT_VISIBLE`；当前应用的 `PUBLIC_ELECTIVE` 范围单独作为范围门禁，不与主对照分数混合。

语料指纹（对 17 条 chunk 的 ID、source ID、内容 checksum、版本、发布状态、可见性、分享权、知识范围、页码、locator、资格标记按 chunkId 排序后计算 SHA-256）：

`4fd00ba1c8154fa7f4389cde6b64fe7276b5ce36ae05a6c87224ee9d465980f0`

该指纹证明本次评测引用的数据库快照可复核，不证明数据库 source 行与原始 PDF 之间已建立独立、机器可验证的来源链。数据库 source_url 为空，原始 PDF 不在当前仓库中，2025 版对当前学年的适用性仍为 UNKNOWN，官方线上附件字节一致性也未验证。

## 4. 用例设计

### 正向检索（15 条）

覆盖以下真实语料事实：

- 第 5 条：16 学时原则值与 32 学时上限；
- 第 6 条：校内公选课 30—150 人的开课人数描述；
- 第 4 条：专业/公共选修课类别及公共选修课子类；
- 第 10 条：每学期公共选修课 4 学分上限；
- 第 13 条：选课、退选、补选、改选遵循每学期选课工作通知；
- 第 11 条：相同/相近专业教学计划课程限制、合格课程不得重复修读；
- 第 18 条：公共选修课不安排重修/不办理缓考，专业选修课不合格可申请重修；
- 第 14—15 条：未办理退改选手续且未参加学习考核的成绩处理、缺课超过 30% 的后果；
- 多证据问题：同时召回课程类别、公共选修课分支、专业选修课分支。

每个正向用例都绑定真实的 `chunkId + sourceId + contentChecksum + knowledgeScope + pageNumber + sourceLocator`。多证据用例必须召回每个 `requiredEvidence`，不能只命中一个相关片段就算通过。

### 权限与范围门禁（3 条）

- `RSV2-AUTH-001`：网络公选课比例的正向证据只出现在当前不合格的内部/归档资料中。它不是正向召回题，不进入 Recall@5 或 MRR 分母。
- `RSV2-SCOPE-001`：在 `PUBLIC_ELECTIVE` 范围下，不能用 `PUBLIC_ELECTIVE_CREDIT_LIMIT` 范围的片段回答。
- `RSV2-SCOPE-002`：在 `PUBLIC_ELECTIVE` 范围下，不能用 `ELECTIVE_RETAKE` 范围的片段回答。

重要边界：当前向量/词法对照索引只含 8 个合格片段。因此权限门禁数据描述了必须满足的契约，但不能单凭“结果里没有不合格 ID”就声称已经通过了真实的过滤器穿透测试——不合格片段本身没有被索引。若要验证过滤器在面对不合格候选时确实拒绝输出，应使用不包含受限正文的合成资格夹具或单独的资格过滤器测试；不要将内部文本发送给 Embedding API。

## 5. 公平比较协议

- 固定数据库语料快照、chunk ID、正文 checksum 和资格规则。比较过程中不重切片、不换金标准、不扩大某一算法的候选池。
- 主模式统一为 `ALL_ELIGIBLE_STUDENT_VISIBLE`；范围受限测试另行报告。
- 统一 Top 5、每路候选上限 8；RRF 使用 `k=60`。如后续比较其他 K 或 RRF 常数，必须作为另一个实验配置记录。
- 已对隔离数据库做只读核验：词法 profile `real-source-lexical-only-v1-20261026` 有 8 条条目、0 个向量；向量 profile `dashscope-text-embedding-v4-1024` 有 8 条条目、8 个向量。两者包含完全相同的 8 个 chunk ID，集合 SHA-256 为 `a9a21f6b878ac932d5994195cd052e4efdfcf5d64b0eeb8831818c23be9855eb`。向量 profile 仍为 `BUILDING`，未激活应用 RAG。
- 对每个正向用例运行词法、向量和 RRF，保存每路排名与实际命中的 chunk ID。
- 查询向量缓存策略必须一致；每个独立问题最多生成一次查询向量，范围模式之间复用缓存。
- 结果报告应包含 Recall@5、MRR、CompleteEvidenceSetRate@5，以及单独的权限/范围门禁结果。多证据完整性不能由 MRR 代替。
- 不把该真实语料分数与合成语料的 `retrieval-golden-v1` 指标合并。

### 指标口径

- **Recall@5**：每个正向用例的必需相关 chunk ID 中，出现在 Top 5 的比例；先逐用例计算，再报告宏平均。
- **MRR**：第一个必需相关 chunk 在排名中的倒数排名；多证据用例仅作补充指标。
- **CompleteEvidenceSetRate@5**：Top 5 含齐一个用例的所有必需证据 chunk ID 才算通过。
- **ForbiddenChunkLeakCount / ScopeViolationCount**：独立门禁，不能折算进 Recall@5 或 MRR。

### 机器计分输入格式

为每个算法提供两种 scope mode 的结果：主模式每个算法必须包含 15 条正向用例及 `RSV2-AUTH-001`；配置范围模式每个算法必须包含两条 `RSV2-SCOPE` 用例。每条结果记录 `caseId` 和按排名顺序排列的 `rankedChunks`，每个命中至少包含 `chunkId`、`sourceId`、`contentChecksum`、`knowledgeScope`。输入文件还必须绑定数据集版本、语料指纹及 K=5 / 候选上限=8 / RRF k=60 的配置。

计分命令（输出文件必须不存在，避免覆盖历史结果）：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\score-v2-results.ps1 -ResultsPath .\results.json -OutputPath .\scored-results.json
```

脚本会拒绝缺失用例、重复排名、未知 chunk、校验和不匹配及不一致的实验配置；输出每个算法的宏平均 Recall@5、MRR、完整证据集通过率和独立门禁计数。计分脚本不访问数据库、不调用模型，也不会修改输入结果文件。

## 6. 为什么现在仍是候选集，而不是正式冻结集

1. v2 问题是根据已有真实 chunk 内容由 AI 起草的开发问题，不是来自学生真实日志的随机抽样，也不是盲测。
2. 目前只有 8 个合格候选 chunk。15 条正向问题覆盖这些 chunk，但部分问题属于同一 query family，统计独立性有限。
3. 尚无独立第二审阅者确认每个问题、事实、locator 与必需证据集合。
4. 数据库没有原始 PDF 文件指纹到每个 source/chunk 的完整机器绑定；部分 chunk 跨多个条款，学分上限 chunk 的 locator 已知不准确。
5. Article 9 的“未选课参加考核/成绩无效”不纳入正向评分：现有合格 chunk 使用“或”压缩关系，必须先对照原始 PDF 复核原文逻辑。
6. Article 8 的网络公选课比例不作为正向召回题：当前没有合格的学生可见正向证据。

所以 v2 **现在可以用于受控的开发集算法行为比较**，但在独立复核、原文定位绑定和更大合格语料完成前，不可宣称它是代表生产总体质量的正式测试集。

## 7. 建议的冻结门槛

- 独立复核 15 条正向用例、3 条门禁用例及每个必需证据组；
- 对照原始 PDF 校正多条款 chunk 的定位，特别是第 5/6 条、第 2/4 条、第 9/13 条、第 14—15/18 条，以及第 10 条的页码/条款绑定；
- 取得原始 PDF 或官方来源附件，并记录文件 SHA-256 与 source/chunk 的可复核绑定；
- 扩大合格语料，而不是只增加同一批 8 个 chunk 的问法；
- 独立冻结一组 query-family 不重叠的留出集，避免用开发集调参后再报告同一批分数。

运行校验：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\validate-v2.ps1
```

该脚本只读检查文件与绑定，不连接数据库、不调用模型、不修改应用代码。
