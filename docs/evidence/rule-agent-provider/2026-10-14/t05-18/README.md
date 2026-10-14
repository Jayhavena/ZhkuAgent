# T05剩余任务2：在线装配与本地纵向验收

执行起点2026-10-14 10:16:48，20分钟截止10:36:48。仅本机fake HTTP、合成映射事实、本地构建/新包零调用校验；真实供应商0、数据库0。本轮完成后仍须任务3具体外发授权及真实验证，T05未完成。

| 问题 | 实际改动 | 验证 |
| --- | --- | --- |
| 在线工厂调用旧3参数Runner，未核验 | 同一Provider作为决策及核验端口传入新Runner，无额外客户端/预算 | 动态SEARCH→READ→FINISH→REVIEW，4次；真实TaskStore令牌消费后的新run续答及重复消息幂等，合计8次本地HTTP；取消不发布，端口关闭 |
| 模型/启动合同仍宣称4次决策 | publicationReviewPolicy由权威常量生成：3次决策+1次核验，总4次/45秒；动作合同REVISION升级02 | 新包拒绝旧4次决策预算、缺核验合同/旧核验提示；旧包及OnlyP01入口拒绝 |
| 新宿主可能沿用旧P01许可 | 新包固定P01及P02必要澄清/PUBLIC续答，3个run最多12次，失败停止；哈希/协议/审核资产身份核对 | 新IncludeClarification开关及P01_P02身份；新包NOT_AUTHORIZED，旧批额度不能复用 |
| 零调用包准备误依赖已安装运行目录 | 新包只绑定能力版本及源码指纹，实际运行READ后仍发送完整能力/来源指纹；不在准备阶段初始化DB | 初次PrepareProbe失败，NoSuchElementException定位目录尚未初始化；最小修复后CMD离线构建、PrepareProbe及CheckPackage均成功、资源退出，外发/DB0 |

失败保留：red-01共111项有3项预期失败，分别缺REVIEW事件、澄清未等待、取消核验阶段不可到达；工厂接线后red-02共112项有3项启动合同预期错误，工厂续答/取消已通过；green-03共112项全部通过。零调用包准备失败单独保留prepare-package.json/log与prepare-diagnostic.log，不改写为通过；prepare-package-recheck及check-package为新成功记录。最终green-05用于本轮修复后源码身份。

前端84项fresh通过；verify-factory.mjs把真实Java TaskStore的等待和续答快照输入现有前端合同，2个快照接受，验证同task/newrun、实际令牌消费、已知PUBLIC不重复追问及8次调用中的2次独立核验。脱敏副本移除originalToken/continuationToken，verification.json绑定原始文件SHA，原始输出不入Git。

新请求包：docs/evidence/rule-agent-provider/2026-10-14/t05-probe-04/request-package.json。SHA256 ca44be57b868e4ebdb8f588fc6cde8f1332d092207d9d18ed38900abee697f61。包含两种提示、动作/核验合同、3+1预算、两问题与补充PUBLIC、10条原文及审核说明、能力权威版本/源码SHA，动态候选UUID/实际READ能力指纹在受控运行时产生。当前状态NOT_AUTHORIZED；CheckPackage的ApprovedPackageSha256参数只用于零调用身份检查，不代表用户授予外发许可。

局部限制：映射资料及核验输出均为受控合成测试，不能证明自然语言质量或真实政策任务完成；本轮不验证真实模型、数据库/学生HTTP页面/用户收益。现有前端只作合同及回归，不是浏览器验收。实际新探针分支尚未运行，后续必须新授权。未调整4次/45秒/10秒/1024token预算，无自动模型重试。

恢复：局部提交父版本恢复本轮7个源码/脚本文件即可撤回，不重置他人改动或原数据。保留旧失败及原请求包；新包只作本机受控审阅，不授予公开上传许可。验收和SHA见stage-audit.json/commit-receipt.json。最后只剩任务3：获准真实探针和T05工程收口，不追加任务或补测额度。
