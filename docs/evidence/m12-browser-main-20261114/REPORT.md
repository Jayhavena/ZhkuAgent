# M1.2 浏览器学生流程验收

2026-11-14，正式集成代码main `505a0dea86c5b88f030c6692d8a9c0df9f52898a`，工作区HEAD `1aaff47f`仅增加M1.1报告，前后端源码与被测main无差异。后端复用M1.1重建的persistent.jar（摘要见identity.json），重新启动专用身份/规则库实例；前端以student入口新构建，经专用Nginx代理58165→58160，不使用开发服务器。使用隔离实例里已有合成账号，不涉及真实学生账号。

## 浏览器实际操作

所有提交通过preview_act点击/输入页面控件完成；没有用直接HTTP脚本替代用户操作。preview_evaluate用于读取DOM和最终只读快照元数据，未通过该通道发起创建/续答操作。

1. 打开/rules，跳转登录页；输入既有合成账号登录，进入学生页。
2. 页面明确显示“工程模拟”、固定问题范围与不调用在线模型；没有手动刷新。
3. 在问题输入框键入固定问题并提交，页面先显示正在查证，随后显示必要澄清“公共选修还是专业选修”。
4. 点击PUBLIC单选及“确认并继续”，页面先显示处理中/上一轮内容，再显示已找到依据和1条公共选修结论；保留零模型声明。
5. 展开资料、点击来源，展示版本2025.9.2、字符范围0–48与校验摘要；点击“在原文中定位”，来源面板显示第18条，DOM中48字符mark元素可见。
6. 最终只读GET快照为RESOLVED/SUCCEEDED、claimCount=1、ENGINEERING_REPLAY、modelAttemptCount=0、providerResponseReceived=false、replayCoverage=HIT、toolStepCount=3。浏览器资源记录含1次originals请求。
7. 关闭原文面板，通过账号菜单退出登录；登录表单重现，旧结论不再显示，performance.timeOrigin不变，未用整页刷新实现清理。

DOM元数据只保存脱敏布尔/计数/资源文件名，不保存口令、Cookie、CSRF、continuation/original token、条文正文或账户UUID。

## 限定结论与问题

核心登录→提问→澄清→续答→结果→站内原文条款高亮通过。原文面板明确提示物理/印刷页映射未核实、无已验证安全原件地址，因此不宣称PDF原件打开/跳页通过；该项保留为原件追溯缺口，后续须核实元数据而不能靠改文案隐藏。

截图通道多次返回“Preview automation snapshot timed out after 15000ms”；备用电脑控制报告native pipe/browser access host不可用。新建预览页后DOM读取和页面控件操作正常，完成上述流程。没有截图或视频，不能称视觉布局/像素验收；工具故障与产品主流程结果分开记录。未做移动端、键盘可达性、迟到响应竞态或多浏览器矩阵，不把单次SPA正常流程当作自动化竞态覆盖。

最终已关闭所有本轮预览标签，停止专用Nginx和后端；宿主退出码0、规则临时容器回收、身份容器停止且账号卷保留。identity.json记录4个专用端口无监听。没有共享数据库写入、没有真实模型或账单调用。本次为SIMULATED学生入口验收，不证明真实Agent回答质量；M2仍需ONLINE正向装配测试和新的受控Probe授权包。
