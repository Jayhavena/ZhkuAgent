# T05 新候选请求包（等待明确外发许可）

核心修正f1bac08；装配提交3b4dca601156625c4e18a21977765291d1583ee1。新批次P01_TRANSPORT_CLOSEOUT_01，执行策略STATE_AWARE_TRANSPORT_CLOSEOUT_01。

包SHA256：5400bbf7fa1e9bb1566f0481f571584c585bb2b06481687b2075e87ddb849d6a；jar SHA256：47e5fbbd35b5396ab7828aaed1b94e58df7fab1b105432e7f1a665e7d9b0db74。

- 新增旧批次/策略拒绝反例先2失败，再8类162项本地回归通过、0失败/错误/跳过。
- 实现提交后，以普通Windows用户/系统PATH、明确存在的PowerShell7路径执行PrepareProbe（离线构建）及CheckPackage/CheckEnvironment，均退出0。零真实调用/数据库操作；中文编码65001。
- 实际jar内123个规则Agent class与当前target一致；源码/依赖/包/jar指纹及零调用回执见package-receipt.json。
- 请求包根文件是唯一权威包。regression目录的回归输出是本地替身材料，不能作为外发包或在线证据。
- 官方价重新核对；申请5次/6元，按公开最大输入输出预留5.736704元，不依赖请求1024输出上限强制性。详见pricing.json和REQUEST-APPLICATION.md。
- 原始日志、XML保留本机；本地回归日志本身不等于在线执行。

本轮独立审查无重要缺陷。核心deadline对任意同步阻塞不提供强制抢占；清理单独跨deadline分支未独立测试。语义核验漏需求和同模型错误相关性仍是质量边界。

外发前要用户明确批准新SHA、5次、6元和受控资料范围；失败即停、余量关闭，不自动修复或开启下一轮。未合入主仓库、未push/部署。

**工程成果已冻结，真实在线验收未通过。**

补充终端验收：Windows PowerShell5.1父进程先置GBK/936，再按AGENTS规定仅在当前进程设置三项UTF-8编码，调用实际PowerShell7启动器CheckEnvironment退出0、中文正确；未改系统/全局编码。原始回执见powershell51-encoding.log，摘要指纹见package-receipt.json。
