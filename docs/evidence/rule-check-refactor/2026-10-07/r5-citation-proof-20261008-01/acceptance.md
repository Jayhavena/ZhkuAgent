# Task2M / A06：服务端引用凭据组件验收

2026-10-08。SESSION_CITATION_PROOF_VERIFIED_NOT_HTTP_ASSEMBLED。

|解决的问题|实现|验证|
|---|---|---|
|客户端自报source/chunk/quote不能授权原文|内部Session/full actor/opaque handle|Session、用户、revision、角色、猜造handle与Ticket反例；未来HTTP不得接客户端excerpt|
|无限历史和过期正文恢复|单轮32引用/文本预算/开始后固定5min|精确数量、UTF16/UTF8及总字节边界；重复读不续期，空完成不可补写|
|旧慢请求覆盖或删新问题|generation publish/clearOwn/requireCurrent|新问即废旧handle；旧publish/clear不影响新轮，I/O后再次核对|
|处理期间跨过期限仍发布|校验和handle构造后再次current|19/1有效deadline RED后最小修复；过期不保留slot|

原RED18为17断言失败+1测试前置NPE，补handle存在断言后18/18有效失败/0error；首GREEN4类56PASS，新增期限反例19/1/0error。46类463项（331纯单元+120专用临时库含12HTTP+12凭据配置）、29计数替身合同；无卷55468 V22连接0/退出。最终0失败/异常/跳过，568源码/XMLhash复算、8项before/after及全部既有main/真实2025DRAFT/20确认参考/36题保护不变。一次审查见review.md/review-resolution.json；故障报告保留。

新增19项是MockHttpSession/Clock纯单元，不等于实际HTTP/资料当前资格/供应商或用户成绩。只存有限摘录正文，不能称无正文存储；公开record不是真实性证明。组件未HTTP/Bean装配；正式CLOSED和真实资料不可见不变。A06后段原文HTTP/页码、A07身份/目录/证据最终复查及B/C/G2继续OPEN，无原库/模型/留出评测/部署/commit。
