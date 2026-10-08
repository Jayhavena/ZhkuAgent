# Task2O 独立增量审查

审查者：/root/r5_original_http_review，gpt-6-astra/high，fresh context；只读一次，无测试/DB/模型/网络。审查15项增量，不审其余dirty。以下按收到的结果记录。

Strengths：POST已接入实际规则/资料、fresh身份、完整Session actor与双generation，错误清理限定本轮。GET仅opaque handle，绑定Session/actor/TTL，两次当前原文核验及目录membership；生产CLOSED/空host。48 XML共498项、570源码、15项审查哈希均核对一致；新增8实际Runtime/词法/审计/原文HTTP，固定selector/embedding界限明确。

Critical：0。

Important I1：RuleCheckController.java:46，在begin/consume前未检查Session当前actor。A通过入口后暂停→B同Session登录并完成新题→A恢复建立两代，覆盖B的凭据和澄清。末端虽拒A，B已有引用404；clearOwn不能恢复被覆盖slot。身份授权后请求体限制读取、changeSessionId保留Session使此窗口存在。原afterDecision并发测试未覆盖B先完成新题的顺序。最小修复：首问和续答临界区内先requireSessionActor，再任何consume/begin；补开始前暂停→B登录完成新题→恢复A反例，B原文/澄清/登录保留。

Minor M1：review-package.md:9写55470，实际command/database均55439；改用实测记录。V22/连接归零/退出证据未见矛盾。

Declined to judge：未运行测试或外部调用；不判顺序检查为DB/身份/网络原子；不判真实发布资格、模型语义、浏览器旧响应、原件页码/G2；不扩查其他dirty或既有身份过滤器所有并发行为。

Assessment：Needs changes，修I1后完成验收；按约定一轮审查、一轮重要修复，无重审。
