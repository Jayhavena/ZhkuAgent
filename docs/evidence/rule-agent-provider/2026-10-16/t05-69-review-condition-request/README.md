T05-69 新请求包已准备，状态 NOT_AUTHORIZED，当前授权真实调用为0。

包SHA256：`3d6a515c2eeae15732540bf8fb96d87f52a6691b94a5e6131d67d8b7445999e5`。冻结jar：`73500a441c7c8e936042f6a4119d0349a29bb50d32d1ababca864c06e9f61842`。生产源码检查点：`bc5cdf4e3072689a7f54d09941745b11176887ed`；准备前HEAD：cb88e7da6d1856f63b7ce40858d06fd15f89254a。实际工作树D:/alldesk/Projects/ZhkuAgent-t05；主仓库HEAD58e1ef7未合入。

[授权申请](REQUEST-APPLICATION.md)、[完整外发披露](EXTERNAL-DATA-DISCLOSURE.md)、[全部静态内容](OUTBOUND-STATIC-CONTENT.json)、[动态字段及上限](OUTBOUND-DYNAMIC-FIELDS.json)。本次申请仅一个P02同任务两run，最多10次真实调用，人民币12元上限。历史5次/6元或其他包授权均不复用。

冻结jar直接Prepare及CheckPackage均在禁止网络的helper中执行，无构建；旧SHA、重复Prepare拒绝且包不变。普通用户持久PATH的Windows PowerShell5.1→CMD→PowerShell7入口CheckPackage、旧SHA及重复Prepare保护均通过。正式包83065字节是本机申请上限内容集合，不是单次HTTP载荷；实际每次请求仍受65536字节守卫。上一轮同输入样本7706字节、本地213项是已有证据，本轮未重新跑测试，也不保证未来动态请求都不超限。

相对65包只有reviewSystemPrompt、publicationReviewContract和buildIdentity三个顶级键不同；条件目录/能力映射、Gate及业务成功门槛均不变。17种范围文字重新从当前资产构建比对，10种能力映射源码及15运行资产不变。完整内容与变化见附件，不仅靠版本号推断。

本轮模型调用、数据库、Docker操作均0；仅核对公开价格网页，未向该网页发送项目内容。未核Docker/镜像/数据库就绪，不代表在线环境已就绪。后续必须重新核身份/价格/环境并获得本包明确授权后执行；失败归档关闭余量，不自动修复、补调用或开下一批。

恢复：保持本包未授权，不覆盖申请。若源码、jar、资产、目的地、模型、预算或提示变化，停止并重新准备独立申请；不恢复旧额度。Git不能恢复原PDF和本机依赖，应按清单核对。

工程成果已冻结，真实在线验收未通过。
