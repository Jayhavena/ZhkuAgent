# Task2N / A07返回前复查内部组件最终增量审查

仅12项before/*.bin→当前（source-before/source-review/changes-review.diff）及真实源。巨大dirty其余改动不审。只读、不写文件、不测试/DB/模型/网络、不派agent/发线程消息。规范计划Task2N/ledger Rulings/剩余A07及decision-contract。Controller/Security/config/Session均保护，本批无HTTP/Bean装配。

ScopedResult加服务端ReleaseBinding(settings/完整审核entry/question/明确conditions/原capture refs元数据)，3参数兼容constructor仍可构造但非缺证结果无binding不得isCurrent。binding只本请求，不返回DTO/存Session；public constructor不证明真实查询来源或问题相关性，只是内部重放合同。

isCurrent：非null/revision一致；INSUFFICIENT仅scope/continuation/binding全null可直接release，没有facts/clarification/conflicts（Decision ctor已限制），不需catalogue可用。其余settings exact/refs非空且<=resultLimit<=50；原输入受限，完整entry与current approved catalogue一致；ref alias<=128/nonblank/身份/revision/hash/唯一alias和source+chunk在fresh读前验证。读取每个原ref currentSnapshot，匹配身份/版本/hash/eligible，保留原alias后deterministic decide；verifier继续全文hash/UTF16/span及current再读，不把reader返回直接称fact。重建scope/continuation并exact compare全decision/scope/continuation，最后再fullentry catalogue比对。无重选/检索/embedding/capture/audit；不能自动把partial升级，也不查新未检索证据。原检索中不输出的chunk变化也保守拒绝，拒答/性能需eval。<=50是distinct refs，不是全过程SQL调用数上限；decide仍有逐binding verifier fresh读，整体请求时限/费用门禁另后段/C。

isReferenceApproved只检当前APPROVED/精确revision目录fact及conflict sides的source/chunk/revision/hash/span/quote绑定，不看citation alias，并不证明current source资格；future GET需Session opaque handle、fresh locate、current identity与完成后Session检查。协议合法不保证问题语义/个人适用；public record不授予客户端可信输入。

Unit RED14/13assertfail、DB RED26/2assertfail，均0errors/skips；第一GREEN5类118PASS。后续添加“partial不取新证据升级”和预算内重复refs不读取验证，已有实现通过，不伪称额外RED。最终47类480=346纯单元+122真库含12HTTP+12凭据配置，29计数替身合同；无卷55469/V22/连接0/实际退出，569source0drift/XMLhash、保护不变。实际DB两新测试：决策后publication/version/body+hash/cut变化拒绝且audit不增；无输出摘录的NEED_CLARIFICATION也在来源撤回后拒绝。不是实际HTTP矩阵，单元catalogue/read变化顺序交错不是数据库事务并发原子性。

阶段FINAL_RECHECK_COMPONENT_VERIFIED_NOT_HTTP_ASSEMBLED。A07Controller最终identity/Session/recheck调用和真实HTTP矩阵/下发窗口、A06GET/页面以及B/C/G2仍OPEN。新方法目前main仅类内引用，旧Controller未调用；正式CLOSED/真实2025DRAFT不可见不变。产品doc收尾之后生成，审查不预授权扩大阶段。

请返回Strengths/Critical/Important/Minor(绝对file:line、触发、最小修复)/Declined to judge/Assessment。重点完整binding/replay/拒证、pending无quote、approved变化后置、alias与事实比较、旧构造兼容和API不误装配、currentSnapshot不假verified、source保护和证据准确性。一轮审查，修复后GREEN不重审。
