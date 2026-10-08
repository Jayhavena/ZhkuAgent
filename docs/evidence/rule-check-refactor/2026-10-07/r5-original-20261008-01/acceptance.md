# R5 Task2K / A06第一段：有界原文定位验收

2026-10-08。BOUNDED_ORIGINAL_LOCATOR_VERIFIED_NOT_HTTP_ASSEMBLED。

|解决的问题|实现|验证|
|---|---|---|
|引用可能指向撤回或变化的正文|固定revision/current身份hash资格，当前一次SQL正文/元数据同读|实际DB元数据、撤回/内部/分享false合法组合；旧reader版本/正文hash/profile/cut矩阵保留|
|读取整个大段或无限TEXT元数据|SQL单片段65536数据库字符、URL/section字节超限NULL|实际65537字符chunk拒绝；URL4097/section8193 bytes不返回，正文仍正确|
|显示上下文截断摘录、跨度乱码|完整quote左右有界、UTF16跨度与代理对边界、8192 UTF8字节|quote文字/hash/span/Unicode反例与emoji上下文；超大quote无正文拒绝|
|原文地址可成为危险链接、页码映射被猜造|https精确host/no userinfo/标准端口；页码仅记录值，不造锚|javascript/data/file/http/伪host/userinfo/异常端口拒绝；原片段/页映射未知明确|
|缺元数据或异常被伪装成定位成功|明确unknown，失败无原文/cause|12组件检查；标题/条款纯文本为未来逃逸渲染数据，不称HTML净化|

有效RED31/13（12unit9失败+19DB4失败），0异常/跳过；首GREEN104有7fail+7error，游标离行后读取metadata错误，先完整读当前行修复；次104仅1error，夹具撤回仍STUDENT_VISIBLE违反V2，改同步INTERNAL，未放宽constraint；第三GREEN104通过。旧报告保留，不把错误都计为有效业务RED。分享false组合含INTERNAL，不声称单独隔离SQL分享谓词。最终32类391项（298纯单元+81真库含12HTTP+12凭据配置）、29计数替身合同；无卷55466 V22连接0/退出。0失败/异常/跳过；源码565及XMLhash复算，11项before/after，原DRAFT/20参考/36题/其余rulecheckJava/profileYAML/18迁移不变。

一次增量审查见review.md/review-resolution.json。此批内部组件无原文HTTP/Bean装配，公开VerifiedExcerpt不能当客户端授权。复用Verifier的memo仅校验本次当前SQL快照，不是第二次fresh DB或最终外发原子性；未来Session引用proof/最终身份和资料复查仍A06后段/A07。地址协议白名单不证明远端文件/版本，记录页码不证明物理/印刷页；B01真实原件映射仍OPEN。正式目录CLOSED、真实2025DRAFT/不可见不变；真实供应商/语义/浏览器/用户收益及G2未通过。
