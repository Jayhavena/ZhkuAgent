# T05 新请求包执行记录

起点f1bac08808691de208049e6a25fe11d07fd3a53a；请求包轮起点2026-10-15 00:14:46+08:00，上限20分钟。

RED：旧诊断批次与旧执行策略拒绝测试2项2失败0错误；已登记该轮失败。
实现：新批次P01_TRANSPORT_CLOSEOUT_01、新执行策略STATE_AWARE_TRANSPORT_CLOSEOUT_01；旧批次与旧策略均须在外发前拒绝。模型、问题、schema、20/110/125秒与5次业务预算不变。
后续：本地回归→独立提交→离线构建→生成新包→零调用核对→绑定提交/jar/资产指纹→外发申请。真实授权未取得，不能执行Probe。

GREEN：新批次装配及全部8类162项本地回归通过、0失败/错误/跳过；准备独立提交后构建与生成请求包。

装配提交：3b4dca601156625c4e18a21977765291d1583ee1；普通用户PATH离线PrepareProbe及零调用CheckPackage/CheckEnvironment退出0；包5400bbf7fa1e9bb1566f0481f571584c585bb2b06481687b2075e87ddb849d6a，jar47e5fbbd35b5396ab7828aaed1b94e58df7fab1b105432e7f1a665e7d9b0db74。申请5次/6元，尚未授权；真实调用0/数据库0。

归档首次辅助检查使用Windows默认GBK读取UTF-8 JSON而失败，未产生提交；改为显式UTF-8。生成请求包的CRLF字节原样保留，差异检查仅本命令允许cr-at-eol，不修改全局Git设置或包哈希。
