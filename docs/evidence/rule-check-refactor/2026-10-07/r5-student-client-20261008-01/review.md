# Task3A 独立增量审查

审查者 /root/r5_student_client_review，gpt-6-astra/high，fresh context，一次只读，无测试/DB/模型/网络。10项匹配source-review，保护文件均匹配before。

Strengths：epoch/round/workflow generation分层防迟到，同步清理、Unicode边界、五状态基础结构/HTTPS清楚；已有日志Node65/65及旧Vue构建通过，审查未运行测试。

Critical：0。Important：3。

- I1，rule-check.js:92：当前空/HTML401先parse失败抛BAD_RESPONSE，跳过身份清理。HTTP身份失效不应依赖正文；先确认epoch/round后按401清理，补当前空/非JSON401与旧epoch/round反例。
- I2，rule-check.js:122/146：logout开始清本地后，公开me未被identityBusy限制；同epoch旧actor读取在退出成功后迟到可重新填actor。公开me在转换期间拒绝，login内部私有读取，补logout pending→me→退出成功→me迟到反例。
- I3，rule-check.js:113/119：查证获取CSRF未传expectedRound/登记resultRequests；A前置CSRF迟到401可清已成功B新题的身份。前置CSRF也须本题round，在更新token/错误清理之前校验；独立auth的CSRF保留身份级语义，补A/B时序。

Minor：无额外必须修改。Declined：新页面/浏览器/Cookie代理/普通终端/供应商/真实语义及B/C/G2，无动态复现；只读源码/日志。

Assessment：Needs changes，补三项RED→GREEN后完成，不二次审查；不能称A08–A10或G2完成。
