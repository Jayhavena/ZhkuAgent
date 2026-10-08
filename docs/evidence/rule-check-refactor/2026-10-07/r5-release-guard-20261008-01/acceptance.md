# Task2N / A07：返回前规则/资料复查组件验收

2026-10-08。FINAL_RECHECK_COMPONENT_VERIFIED_NOT_HTTP_ASSEMBLED。

|解决的问题|实现|验证|
|---|---|---|
|决策后资料或全文审核规则变化|绑定settings/entry/原refs，fresh读并重跑确定性决策、前后目录比对|publication/version/body+hash/cut实际DB拒绝；规则scope/责任/事实/复查期间变化单元反例|
|只查输出quote漏掉必要澄清|重新核验原集合并比对全decision/scope/continuation|无输出摘录的NEED_CLARIFICATION资料撤回后拒绝；四有scope状态重放|
|复查变成重新选题/外发/审计或擅自升级|保留alias及原集合，仅reader/decide|selector/retrieval/audit不增；新未检索证据可读也不将partial升级|
|兼容record构造或伪scope被当可信产物|非缺证无binding拒绝，元数据唯一有界及完整重放比对|旧3参数/伪事实/scope/continuation/错settings/缺或重复refs反例|
|目录引用membership混淆current资格|只精确binding匹配facts/conflict|不由本方法证明source当前资格；HTTP另fresh locate/identity/Session|

有效RED14/13pure+26/2DB均0error/skip，第一GREEN5类118PASS；后加partial与预算内重复refs验证，已有实现通过，不伪称额外RED。审查I1双片段前两次43/1fail+1error是缺必填source_locator夹具问题；修正定位字段和新增多片段清理后有效RED43/2/0error，补逐refs纯正文SHA256/UTF16检查而不增加查询，覆盖未输出片段漂移。清理改造是新夹具支持，不冒称独立cleanup故障已复现。47类482项（347纯单元+123专用临时库含12HTTP+12凭据配置）、29计数替身合同；无卷55469 V22连接0/退出。最终0失败/异常/跳过，569源码/XMLhash复算，12项before/after、Controller/Security/config/Session及所有其余main/真实2025DRAFT/20参考/36题保护不变。一次审查及一次修复无重审，见review.md/review-resolution.json；所有失败报告保留。

refs<=50是distinct身份，不是总SQL数/端到端时限；decide仍逐binding再fresh核验。模板/question/条件/元数据只inflight，不存Session/回复DTO。公开record不证明原始query真实性/问题相关性，真实选题语义另C组。保守拒绝原非输出chunk变化可能增加拒答/延迟，需eval。

本批未HTTP/Bean装配，旧Controller尚未调用新gate。顺序复查不是数据/身份/网络原子提交；A06GET、A07最终当前身份/Session调用与真实HTTP矩阵、B/C及页面/用户收益G2继续OPEN。正式CLOSED/真实资料不可见不变，无原库/供应商/留出评测/部署/commit。
