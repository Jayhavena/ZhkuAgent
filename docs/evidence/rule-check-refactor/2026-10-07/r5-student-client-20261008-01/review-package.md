# Task3A / 学生查证v2客户端与状态增量审查

只读10白名单before→当前（source-before/source-review/changes-review.diff）及依赖现有Session factory；不要写文件、运行测试/DB/模型/网络、派agent/发其他thread消息。当前巨大dirty其余不审。规范task3a-brief/计划Task3A和decision-contract Task2O。后端/旧v1/frontend其他文件受保护未改。

新增独立v2 client：同源credentials/no-store/CSRF/auth，复用createControlledSession factory但独立实例，不混用v1 actor/submission。server reply固定revision2025.9.2/USER_DECLARED/五状态/非空facts引用/bounds与Unicode，original只opaque handle，内部context跨度/代理对/pageKind/HTTPS无userinfo/非443port校验；纯结构不证明语义或source资格，当前网络JSON先整体读取再校验，不宣称下载内存/字节流硬上限。严格条件/文本上限有可能比模板更严，真实模板绑定与字段评测后校准；临时不引入复杂响应schema库。

身份epoch+question round保护旧成功/401/原文；check开始cancelResults，错误校验先于轮次开始；Abort辅助，不宣称物理停止。当前401自己clear换代后仍保留AUTH错误；旧epoch/round 401不清新主体。me发现role/user/revision不同clear旧内容+CSRF；跨tab不立即检测，只有me/current401。login/logout串行，密码只调用参数内存不持久化；server Set-Cookie网络顺序问题不靠客户端epoch声称解决。

Workflow维护当前问题/业务结果/原文/pending，watch身份epoch flush sync立即reset，localgeneration再防迟到；澄清只当前awaited key+allowed value+server token，不猜其他条件；失败/新题/身份换代/dispose清旧内容。未知结果固定未确认，无自动重试/受理声明；UI下一批适配这些状态。原文同问题内读取可后发覆盖，new question/identity旧读丢弃，error无raw cause。尚未页面/路由/Nginx/真实浏览器装配。

初始RED17/17（可执行stub，非import错误；每项fail但部分因为stub不给正常值，没有额外调用）；GREEN17；补边界RED12/2（当前401错误变STALE、半Unicode引用；后一测试第一个assert已fail，context边界在最终green覆盖不冒称独立red）；最终全Node65=旧46+新增19，0fail/cancel/skip，Vue已有构建成功。构建当前未import新module，不冒称新页面bundle或浏览器通过；Node测试实际import新api/workflow+Vue reactive。后端Task2O48类501最终0fail保持未变，不重跑Java。正式CLOSED/真实DRAFT保护，无DB/模型/新依赖/commit部署。

重点审查epoch/round/error时机、fresh me身份变化、auth transition清理、readonly问答未知状态、result shape failclosed、Unicode spans、安全link、workflow内state可被异步覆盖/保留不当。返回Strengths/Critical/Important/Minor（绝对file:line/触发/最小修复）/Declined to judge/Assessment。一轮fresh review、重要项RED→GREEN后不重审。不得拿纯Node效果称A08–A10或G2完成。
