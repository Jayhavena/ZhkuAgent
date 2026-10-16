# T06/T07 实际 nginx 模拟验收：局部通过，退出卡点未解除

源码为独立分支 codex/t06-t07-student-http 的1e27e3c898c4b498ffde3fd07d9159003db5fc2a。没有引入T05未提交文件或新执行语义；本轮只新增验收脚本和证据，生产源码未改动。执行模式TEST_SIMULATION_NO_PROVIDER，schemaVersion=3，供应商0、数据库0。

## 实际结果与可复用部分

既有学生构建通过。复用仓库start-nginx-preview.ps1、compose.preview.yaml、nginx.conf，镜像固定为nginx@sha256:6e23479198b998e5e25921dff8455837c7636a67111a04a635cf1bb363d199dc，nginx配置检查与就绪检查通过。本会话独立项目zhku-t06-t07-nginx-20261014-01，网页53216、模拟后端17274。

http-exchanges.json保存72条实际nginx请求/响应与Cache-Control，检查缺CSRF403、跨账号404、创建202/重复键、澄清WAITING_USER、旧version409、同任务续答202、并发第二续答409、结果RESOLVED、原文200、部分答案PARTIAL、故障FAILED、取消202到STOPPED。身份仓储、领域输出与原文定位是已提交测试替身，不能据此声称真实资料或在线模型通过。

browser-dom.json保存10个实际浏览器状态：澄清、同任务续答/原文、部分与缺口、处理故障、取消受理与最终停止、换身份、实验路由返回、旧结果不再展示、退出。browser-partial.png为实际nginx页面截图。浏览器操作只使用本会话创建的页，结束后关闭该页。

## 未通过项与停止条件

原定主动时限20分钟；后续时钟读数已超过停止时点，未按时完成正常退出。本轮没有满足时限/完整资源退出验收，停止扩展，不把局部HTTP/页面通过写成整轮完成。时钟读数：开始2026-10-14 00:06:30 UTC；确认超时2026-10-14 01:42:04 UTC。

STOP文件已写入，但原运行时不再保留后台执行句柄，未生成本次browser-host-exit.json。nginx Stop调用失败：Docker Desktop Linux Engine命名管道不存在；项目检查同样失败。当前53216/17274实际连接均失败且无监听，但不能据此推断容器已删除或任务线程正常退出。详见cleanup-status.json、nginx-stop.log、cleanup-inspection.log。本轮没有重启Docker、终止其他进程或清理T05资源。

首轮构建因工作目录下证据相对路径错误未执行，修正为绝对目录后实际构建通过；早期非提升的Docker只读命令被本机管道权限拒绝，随后在已授权独立范围读取成功。最终Docker不可用是退出卡点，不能用之前启动成功掩盖。

## T05共享合同缺口：供用户同步确认

已核对实现8bfdf773258e96276c3099e73043674562345cef及回执04192db、实际验收范围。其FINISH本地73项后端、84项前端、2项临时库流程通过，但ASK、在线适配与工厂未完成，T05仍未通过。

现有定义：双方schemaVersion仍3；本会话基线不识别新原因码。具体问题：8bfdf77新增QUESTION_UNDERSTANDING_UNRESOLVED与ANSWER_COVERAGE_INCOMPLETE，两者PROCESSING_FAILURE；若现在接其结果，旧消费者会拒绝。建议增量：由用户协调后，按该明确提交的权威定义同步Java/JS原因映射及实际样例，不另定义同名语义。影响模块：TaskSnapshot消费、HTTP装配、学生结果页。对应测试：新原因FAILED/PARTIAL保留已支持答案、处理原因不伪装DATA_GAP；既有未知码仍拒绝。受影响部分保持暂停，未擅自修改或复制共享权威文件。

当前接口/页面/代理及验收脚本可复用，没有产品重定位、Agent返工或新增重构阶段。真实DB身份/证据资格、T05在线链、最新原因码同版本消费仍未验证。旧证据和旧退出缺口不改写。

唯一下一项：Docker由用户或既有环境恢复可用后，只停止并移除本会话zhku-t06-t07-nginx-20261014-01项目，确认本次退出卡点解除；随后仍需T05稳定SHA/合同确认，才能在同一源码版本执行真实HTTP及浏览器全过程。不进入T08、不push/PR/标签/历史改写。
