# 学生浏览器实际观察与中断边界

10月18日21:06—21:08，主会话在实际学生构建、真实临时身份和审核资料、测试同源代理上，亲测登录、异步任务、同任务澄清续答、依据与原文、跨页退出撤回、保持终态响应后退出再释放、PARTIAL缺口展示及资料资格撤销。完整范围见 browser-observations.json，原始实际请求/状态见 browser-proxy-exchanges.json，关键节选见 selected-http-observations.json。

本轮撤销操作已真正完成：review-revoked.json 为 true；旧原文接口返回409 TASK_EXPIRED，页面撤回结果和原文，显示“依据资格或版本已变化，旧结果暂停展示；请重新查证。”截图 eligibility-withdrawn.jpg 为实际页面。此结果与旧205102批中后端故障不同。

源码身份为 ba0e80f 加唯一测试宿主修正，文件SHA和八类165项源码清单见观察JSON及 online-preflight-green-06。终态明确 ENGINEERING_REPLAY、供应商0；不能解释为模型答案质量通过。

**总批次状态：INTERRUPTED。** 夜间中断发生在两标签页关闭后、STOP与退出回执生成前。10月19日05:55恢复时，进程和工具会话均已消失，Docker引擎未启动，不能证明原宿主正常finally/自动回收。STOP于恢复后才写入，不回填历史。实际残余检查及必要清理由 recovered-after-interruption.json 单独记载；后续短租约验证正常退出，不能覆盖或改写本批中断。
