# 真实语料检索评测集 v2：8 个合格 chunk 来源链核验

日期：2026-10-26

审计状态：**AUDIT_COMPLETE_WITH_OPEN_CURRENTNESS_AND_METADATA_GAPS**

范围：只核验 v2.0.0-rc1 当前 8 个符合检索资格的 chunk 及其 15 条正向检索金标准。没有运行三路检索对照。

## 1. 结论摘要

本轮已完成对现有 2025 版原始 PDF 身份、既有页码证据、8 个 chunk 正文校验和、来源记录和条款映射的核对。

| 检查项 | 结果 | 证据与边界 |
|---|---|---|
| 本机原始 PDF 身份 | PASS | 5,526,012 bytes；SHA-256 为 `dd036279ca21beea40351815d21d6d49f1037448a4df45c95e1eb2f8b199f653`，与原有 manifest 及 v2 source snapshot 一致 |
| 原文页码证据 | PASS（复用已有证据） | 已有来源审计和页图确认物理页 76→印刷页 64、物理页 77→印刷页 65；本轮确认两张页图及既有 binding audit 均存在 |
| 当前 8 个 chunk 的正文 checksum | PASS | 从 v2 snapshot 重新对每个 chunk.content 计算 UTF-8 SHA-256，8/8 与 contentChecksum 一致 |
| 数据库来源 URL | OPEN | 8/8 的 sourceUrl 都是 null；本轮未写回数据库。外部审计账本把 sourceId、策展记录、原始 PDF SHA 和条款位置关联起来 |
| chunk 内容类型 | 已分类 | 8 个 chunk 并非全部原文逐字摘录；其中有逐字子句，也有策展摘要。不能把摘要当成 PDF 原文引用 |
| 定位质量 | 部分 OPEN | e2dc... 跨第 8、10 条和物理页 76—77；当前单值 pageNumber=76 不能单独准确表达第 10 条的出处在物理页 77 |
| 当前学年有效性 | UNKNOWN | 官方资源页已列出 2026 版学生手册（2026-09-05）；官方 2026 附件下载请求返回验证码 HTML，本轮未绕过验证码，未取得 2026 PDF 做条款比较 |
| 生产安全边界 | PASS | 未写入生产或隔离数据库；未激活检索 profile；未调用模型或 Embedding API；未修改外部原始 PDF、其他 worktree 或 v2.0.0-rc1 文件 |

**关键判断：**可以重建“当前 8 个 chunk → 2025 版 PDF → 对应条款”的历史来源链，但不能据此证明这些条款仍是 2026 学年现行规则。数据库自身的 sourceUrl 字段也仍未补齐。故本轮不是把所有状态简单改成 PASS，而是将已证实、摘要绑定、定位待修和现行性未知分开记录。

## 2. 原始文件和官方发布证据

### 本机原件

- 文件：`2025版学生手册2025.9.2.pdf`
- 位置：`D:\alldesk\Projects\ZhkuAgent\worktrees\projects\SuperAgent-zhkuagent-mvp\data\raw\2025版学生手册2025.9.2.pdf`
- 大小：5,526,012 bytes
- SHA-256：`dd036279ca21beea40351815d21d6d49f1037448a4df45c95e1eb2f8b199f653`
- 页数：339
- 结果：与原有 manifest 和 v2 sourceSnapshot.sha256 一致。

### 官方网页

- 2025 版学生手册发布页：<https://xsc.zhku.edu.cn/info/1154/15962.htm>，页面标题为“2025版学生手册”，发布日期为 2025-09-03。
- 学生工作部资源目录：<https://xsc.zhku.edu.cn/zyxz.htm>，列出“2026-09-05 2026版学生手册”以及 2025 版。
- 2026 版发布页：<https://xsc.zhku.edu.cn/info/1154/21062.htm>。

本轮对 2026 版附件的普通下载请求返回 3,829-byte HTML 验证码页面（不是 PDF）。没有尝试绕过验证码。因此：
- 本机 2025 PDF 与本机 manifest 的字节身份已核验；
- 本机 2025 PDF 与官网在线附件的字节级一致性仍为 `NOT_VERIFIED`；
- 2025 规则对 2026 学年的适用性为 `UNKNOWN`，不能自动标为当前有效，也不能仅因有新版本就推断所有旧条款都已废止。

### 已有原文核验材料

以下材料位于外部项目 worktree，本轮只读复用，没有修改：
- `D:\alldesk\Projects\ZhkuAgent\worktrees\projects\ZhkuAgent\docs\evidence\rule-check-refactor\2026-10-07\r0-baseline-01\source-audit.md`
- 同目录 `page-76.png`、`page-77.png`
- `D:\alldesk\Projects\ZhkuAgent\worktrees\projects\ZhkuAgent\docs\evidence\rule-check-refactor\2026-10-08\b01-source-binding-02\binding-audit.json`
- `D:\alldesk\Projects\ZhkuAgent\worktrees\projects\ZhkuAgent\docs\product\rule-check-2025-source-excerpts.md`
- `D:\alldesk\Projects\ZhkuAgent\worktrees\projects\ZhkuAgent\docs\product\rule-check-2025-report-source-supplement.json`

既有 B01 记录说明：原件物理页 76、77 的页图已查看；所列 10 条原文摘录在指定页逐字找到。该证据支持 2025 原文及页码映射，但不等于对 2026 现行性作了核验。

## 3. 当前 8 个 chunk 的逐项结果

所有 8 个 chunk 的 `contentChecksum` 均已从当前 snapshot 的 `content` 重新计算并匹配。这里的“逐字子句”指 chunk 内容中存在对应的原文子句；“策展摘要”表示内容是对条款的摘要/合并，不可当作原文逐字引用。

| chunkId（短前缀） | sourceId（短前缀） | 记录定位 | 原文对应 | 核验结果与处理 |
|---|---|---|---|---|
| `06766d1e…` | `72e1a0a5…` | 物理页 77，第 18 条；`ELECTIVE_RETAKE` | 第 18 条“公共选修课不办理缓考，成绩不合格者不安排重修” | **逐字子句可绑定**。正文 hash 通过。仅能作为 2025 原文证据，当前学年有效性未知 |
| `357797d7…` | `6b2f4c18…` | 物理页 77，第 9、13 条；`PUBLIC_ELECTIVE` | 第 9 条选课/考核后果；第 13 条按每学期《课程选课工作通知》办理选课调整 | **策展摘要**，不是两条原文的逐字拼接；当前内容与已审核策展摘要一致，条款映射明确。可用于历史文本检索排名，不可冒充原文引号 |
| `55815778…` | `6b2f4c18…` | 物理页 76，第 5、6 条；`PUBLIC_ELECTIVE` | 第 5 条学时；第 6 条校内公选课开课人数 | **摘要带措辞漂移**。第 5 条事实可逐字对应；第 6 条数据库正文写“低于 30 人不开课”，原文/策展摘要写“低于 30 人不予开课”。语义近似，但非逐字；历史检索排名可用，引用时应回到原文 |
| `8760f9a1…` | `6b2f4c18…` | 物理页 77，第 14—15、18 条；`PUBLIC_ELECTIVE` | 第 14 条未办理退改选且不参加学习考核的成绩处理；第 15 条缺课超过 30% 的后果；第 18 条公共选修重修规则 | **多条款策展摘要**。第 15 条阈值后果和第 18 条公共选修句可定位；第 14 条数据库文本省略“手续”“课程”等字样，与策展摘要措辞不完全一致。可用于检索排名，但不用于严格逐字引用；若要评测逐条件忠实性，应先修正文案并复核 |
| `ac78ed6c…` | `6b2f4c18…` | 物理页 76，第 2、4 条；`PUBLIC_ELECTIVE` | 第 2 条适用对象；第 4 条专业/公共选修类别及公共选修子类 | **部分条文摘要**。第 2 条可逐字对应；第 4 条保留课程类别和公共选修课子类，但省略了“专业选修课列入教学计划要求在规定学期内修读”这一部分。对当前类别问题足够，不代表完整复述第 4 条 |
| `b2213ca7…` | `6b2f4c18…` | 物理页 77，第 11 条；`PUBLIC_ELECTIVE` | 相同/相近专业教学计划课程限制、合格课程不得重复修读 | **策展摘要**。数据库正文与策展摘要一致；原文使用“不可重复修读”，摘要写“不得重复修读”，语义一致但不是逐字引文 |
| `b3cd386d…` | `72e1a0a5…` | 物理页 77，第 18 条；`ELECTIVE_RETAKE` | 第 18 条“专业选修课成绩不合格者可申请重修学习” | **逐字子句可绑定**。正文 hash 通过。必须保留“可申请”，不能解释为一定获批 |
| `e2dc2c7a…` | `f4d0b0a0…` | 记录为物理页 76，locator 为物理页 76—77、第 8、10 条；`PUBLIC_ELECTIVE_CREDIT_LIMIT` | 第 8 条培养方案依据（物理页 76）；第 10 条每学期 4 学分上限（物理页 77） | **摘要 + 跨页定位问题**。4 学分事实由第 10 条支持，但单值 `pageNumber=76` 不能精确表达该事实位于物理页 77；当前正文还比策展摘要少“修读”二字。检索排名可测试，严格出处/页码评分必须继续关闭；新版本应记录按条款分开的 source span 或拆分 chunk |

## 4. 来源记录映射

当前 8 个合格 chunk 来自 3 个 sourceId：

| sourceId | chunk 数 | scope | 当前 sourceUrl |
|---|---:|---|---|
| `72e1a0a5-4c9e-4f86-b5fd-2fe2a3957c14` | 2 | `ELECTIVE_RETAKE` | null |
| `6b2f4c18-0d5a-4e73-9a41-7c1e8f2b6d05` | 5 | `PUBLIC_ELECTIVE` | null |
| `f4d0b0a0-c78a-4c7a-94e0-9b2942d0b8f5` | 1 | `PUBLIC_ELECTIVE_CREDIT_LIMIT` | null |

这些 sourceId 可通过既有策展 JSON 映射到对应的主题记录；原始 PDF 的本机 SHA 与 manifest 一致。本轮把这种外部证据链记录在本审计中，但**没有改写数据库 sourceUrl、sourceVersion、chunk 元数据或索引条目**。因此，“来源可追溯”在外部审计层已建立；数据库内部的直接官方 URL 字段仍未补齐。

## 5. 金标准的使用边界

### 可用于什么

当前 15 条正向用例可以用于**固定 2025.9.2 文本快照上的检索排名开发比较**，因为它们的目标 chunk 都能映射到 2025 PDF 中的相关条款，且 8 个 chunk 正文 hash 均匹配。第 8 条网络公选课比例没有合格学生可见正向证据，已经被独立放在权限排除门禁，不应重新放回 Recall@5/MRR 分母。

### 不可用于什么

- 不能把这 15 条的成绩解释成“当前学生问答正确率”或生产总体质量。
- 不能把策展摘要当成 PDF 的逐字引文。
- 不能报告严格页码/条款定位准确率；v2 的 `strictLocatorAccuracy` 继续保持关闭。
- 不能声称 2025 规则对 2026 学年仍然有效；官方目录已列出 2026 版，但其相关条款尚未与 2025 版逐条比较。

### 下一版必须处理

1. 保持 v2.0.0-rc1 原样不动；不要在这个冻结候选目录里直接改 hash 绑定文件。
2. 新建下一版评测资产时，为每个 gold 增加 `evidenceKind=VERBATIM_SUBCLAUSE|CURATED_SUMMARY` 和按条款记录的 `physicalPage`、`printedPage`、`article`、原文跨度。
3. 修正或明确记录 `55815778…`、`8760f9a1…`、`e2dc2c7a…` 的摘要/定位差异；不能把它们当作逐字引文。
4. 如要做当前规则产品质量评测，先取得 2026 版 PDF，比较第 4、5、6、8、9、10、11、13、14、15、18 条的变化，并对变化影响到的用例重新判定金标准。获取文件时使用正常官方流程；本轮没有绕过验证码。
5. 只有在明确选择“历史文本检索对照”后，才可基于该 2025 快照继续运行三路检索；运行结果仍应单独标注为历史语料开发集结果。

## 6. 复现记录

- v2 corpus snapshot SHA-256：`0724e24e4c2bf2283a08294b34cbd1c7b75c24635269ead40b1ad1656bad8838`
- v2 cases SHA-256：`27a14c37965450e675f5cbc24cbdb741e15d1b682765c1a4921a99588701f442`
- 2025 原始 PDF SHA-256：`dd036279ca21beea40351815d21d6d49f1037448a4df45c95e1eb2f8b199f653`
- 2025 原文摘录文件 SHA-256：`a5cd210045f53a24bcc3906005287049f83a0f284014eb749a0cb708616952fd`
- 2025 原文补充文件 SHA-256：`7a29472e68e0d1eb2d902484e3f887ab92d78ef833c865b6feca0f2a7ff71ebd`
- 本轮重新计算：8/8 chunk 正文 checksum 匹配。
- 本轮未运行：词法/向量/RRF 对照、模型评测、Embedding API 调用、任何数据库写入。
