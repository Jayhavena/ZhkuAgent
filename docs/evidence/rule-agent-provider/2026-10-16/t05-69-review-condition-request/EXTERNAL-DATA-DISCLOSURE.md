T05-69 完整外发披露——尚未授权

仅对应包SHA256 `3d6a515c2eeae15732540bf8fb96d87f52a6691b94a5e6131d67d8b7445999e5`、jarSHA256 `73500a441c7c8e936042f6a4119d0349a29bb50d32d1ababca864c06e9f61842`、源码检查点bc5cdf4e3072689a7f54d09941745b11176887ed。实际工作树D:/alldesk/Projects/ZhkuAgent-t05，准备前HEAD cb88e7d；主仓库58e1ef7未合入。当前授权调用0，本轮准备不发生供应商模型、数据库或Docker操作。

接收方与目的地：阿里云百炼/DashScope华北2（北京），唯一HTTPS POST `https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions`，模型固定qwen3.8-flash，不跟随重定向。显式HTTP头为Content-Type及Authorization: Bearer本机DASHSCOPE_API_KEY；HTTP栈还生成Host、长度等传输元数据。密钥仅供该目的地鉴权，不进入请求JSON或本机日志/答复。这里披露的是未来待批准传输，本轮没有发出模型请求。

用途及输入：仅一次固定P02工程探针，首问“我这门选修课没过，能不能重修？”。首run必须真实检索、读取、必要追问提议、独立发布核验及合格WAITING_USER发布成功，才提交预登记合成补充PUBLIC。PUBLIC仅表示本次测试的公共选修输入，不是真实学籍、不含参考答案。续答必须同一task新run，重新检索、读取、核验、发布。首run不合格即停止，不提前注入PUBLIC。重复续答幂等，不能产生额外run或调用。

完整静态内容见[OUTBOUND-STATIC-CONTENT.json](OUTBOUND-STATIC-CONTENT.json)，动态字段、来源、上限及仅本机字段见[OUTBOUND-DYNAMIC-FIELDS.json](OUTBOUND-DYNAMIC-FIELDS.json)。静态附件逐字给出system/review提示、动作与核验合同/机器Schema、最多可能发送的原文片段、审核事实/when/条件、17种questionScope、证据/条件缺口和10种能力映射。附件和整份83065字节申请包只供本机审阅，不整体作为请求上传。未来精确动态请求取决于实际工具结果，不能预造为已发生的真实HTTP字节。

本次变化：相对已关闭65包，只有reviewSystemPrompt、publicationReviewContract、buildIdentity三个顶级字段不同，其中仅前两个属于模型外发文字。新增共享conditionIdForm明确necessaryConditionIds逐字复制input.conditions[].conditionId；courseType不能拼成S10/RETAKE_ONLY:courseType；同名键跨规则仍使用裸键，需求分支用各自id/subject/事实绑定区分。没有新增SemanticInput.conditions成员，没有合并条件记录，没有放宽格式或语义校验，不自动修正响应。模型是否遵循新提示仍未知。静态附件15个对应包字段已逐项一致；17种scope从当前C05/审核语义与实际拼接规则重新重建比对；能力源码原始SHA及15项资产相对65不变。

请求外壳：model、stream=false、enable_thinking=false、max_completion_tokens=1024及system/user两条messages。决策使用strict json_schema；核验使用json_object。本地JSON字段与字节检查只能证明客户端构造结果，不能证明供应商支持全部Schema或会严格执行输出限制。

决策user载荷：完整当前候选的临时集合代号、c编号、摘要/修订/已读标记；executionState合法动作、剩余证据片段/字节、run时间及核验/发布预留；原始问题、questions.id/text、用户已声明条件；已读ruleContexts规则号、questionScope、条件问题/值、evidenceGaps；已读事实id/ruleId/text/when与publicationEligible、publicationFactIds；工具观察step/status/dataLevel/reasonCode/factIds和原文quote/revision。历史观察中的旧候选数组为空，当前完整候选统一投影，不默认c1、不截断候选。最近最多2条rejectedActions只含attempt/action/reasonCode和可选publicationReason，availableConditions只含ruleId/conditionId/allowedValues，不发送模型自由理由。动态READ句柄枚举来自完整当前候选；ASK/FINISH事实名单来自实际已读事实能力登记及原始完整Fact指纹匹配；无资格事实仍可见，空名单用maxItems=0，不预置模型应选事实。模型自报完整不触发绕过核验或强制FINISH。

核验user载荷：安全SemanticInput，含协议/能力版本、inputFingerprint、questions、userConditions、已读facts(id/ruleId/text/when/quotes)、capabilities(factId/dimension/subject/allowedRoles/auxiliaryDimensions/sourceFingerprint)、审核conditions(ruleId/conditionId/question/allowedValues/factIds)，另附核验合同和剩余核验次数。task/version只参与摘要，不发送原始身份。审核条件目录参与指纹；分支只能用实际已读审核事实支撑，不能把未读事实用于排除分支。核验不接收提议者ASK/理由、相关性/完整性自评或C05的questionScope。它是同模型另一次调用，只提供上下文及职责分离，不能声称跨模型、跨供应商独立性或真实语义必然正确。

原文、权限及适用范围：仅2025.9.2本科历史已审材料；原文保持，现行适用性、个人资格和实际教务记录均未核。C05选题草案派生的questionScope确实可能在决策输入外发，完整17种文字与来源已披露；整份草案不外发。对本批传输的批准不把该草案升级为学校正式政策审核，也不赋予公开传播、跨用途共享或PDF整本外传权限。来源/用途边界、能力登记及独立发布核验均保留。

不外发：owner/session、原文访问token、原始task/run/source/chunk UUID、原始checksum、完整Fact定义及evidenceBindings、数据库URL/密码、学生私人记录、完整源码、整本PDF、整份请求包/buildIdentity/runtimeAssets、成功门槛/参考答案、历史最终答案或语义自评。capability.sourceFingerprint与inputFingerprint是受控摘要，不是原始标识、登录凭据或原文访问凭据。

本机受控留档：REQUEST/RESPONSE/PROPOSED_ACTION及REVIEW_INPUT_SNAPSHOT按task/run/attempt关联；快照保存原始完整Fact（包括checksum原大小写、source/chunk、跨度、原文、独立性及when）以重建事实身份，仅留本机，绝不加入供应商JSON。展示Evidence可能把checksum小写化，不能替代完整Fact。原字节SHA、脱敏副本及redacted标志分别记录，不记录HTTP头；按解码字段和值及有界Unicode展开脱敏，无法可靠解析的JSON正文省略并标记，不声称脱敏副本可逐字重放。快照/单次请求最多65536字节，证据最多8片段/32768字节；超容量拒绝，不裁剪分支换取通过。审计失败以AUDIT_UNAVAILABLE终止。记录器不是额外在线调用，也不增加额度。

调用、时间与费用：本申请仅一次P02，同task最多2 run、最多10次真实调用，每run最多4决策加1独立核验，单次最多20秒、run110秒，两个run最多220秒、Java外层235秒；无自动重试和第三run。每run保留核验20秒和发布5秒。满额4×20+20+5=105秒，仅余5秒给工具、本地验证/记录I/O，不能承诺四次决策都获得完整20秒。Runner会缩短或拒绝后续调用，预算失败也算本批失败。Docker就绪和清理在Java235秒之外，但整执行轮主动操作仍≤20分钟。

本轮核对[阿里云官方模型定价](https://help.aliyun.com/zh/model-studio/qwen3-8-flash)：北京输入0.8元/百万token，输出2.7元/百万token；非思考公开最大输入991808、输出131072。保守按公开最大输出预留10×(991808×0.8+131072×2.7)/百万=11.473408元，小于待申请12元。详见[pricing-and-budget.json](pricing-and-budget.json)。不依赖1024上限必被执行、不计优惠/缓存/免费额度。这是保守风险预留，不是预计账单，也不是供应商钱包自动扣款硬限额。执行前必须重新核价及确认目的地/文本非思考配置；无法确认不超过申请上限就不启动，不自动提高上限。价格或模型供应商内部实现可能变化，不能视为冻结产品质量。

全部尝试与费用记录：以task/run/attempt去重，BEGIN/RECEIVED/FINISHED和本机快照不重复计次。记录是否派发、响应头/requestId是否可得、状态、耗时、字节SHA、usage、提议动作、Provider校验码、Gate发布码和首次失败位置。派发后超时、usage缺失或供应商执行状态不明仍消耗调用额度；费用记UNKNOWN，不记0、不补调用。usage估算与实际账单分开，账单未核保持未知。目前realAttempts为空，授权为0；申请10次不等于已执行或已获额度。

成功与失败退出：首run须WAITING_USER/INCOMPLETE、run终止、有LIVE_AGENT真实响应、原文与courseType澄清关系，只列PUBLIC/PROFESSIONAL且无答案主张，SEARCH/READ/ASK/PUBLICATION_REVIEW成功，Runner产物与任务仓发布快照一致。续答须同task、不同且版本递增run，RESOLVED/SUCCEEDED、coverage COMPLETE，重新SEARCH/READ/FINISH/PUBLICATION_REVIEW成功，按审核事实发布有原文与依据关系的公共选修解释，无残留追问或缺口；本机验收factId不发送。不能用跨批次阶段、测试数或一段核验替代这条链路。

任一身份、权限、条件、时间、容量、协议、输入关联、独立核验、发布、归档、幂等或资源退出不合格即失败停止。归档所有实际尝试和未知费用，关闭剩余额度，不自动修复、重试、补额度或下一批。只清理本次自有随机容器/进程；不能按进程名结束用户任务。后续实际执行若任务仓快照/清理行数未能取得，应准确记未知而非填通过。

本地与在线边界：自动CheckPackage绑定包定义、实际jar、335项生产/启动输入、15运行资产；本机身份回执另核14项POM/共享模块/wrapper等附加构建输入和class字节。不能声称后者全部已由现有自动门禁覆盖。批准后执行前应再次核对receipt、jar/源码/资产、价格、凭证、Docker引擎/本地镜像及空闲回环端口。仅专用随机容器临时库zhkuagent_rulecheck_e2e，拒绝原演示端口54329。每次凭证操作前重新检查本机配置，不打印值。本准备轮没有检查数据库/Docker就绪。

本轮无Maven重建；沿用68已冻结jar并重新核对输入/资产。68的213项本地测试是历史结果，本轮未重新运行。当前Prepare/CheckPackage、旧指纹拒绝和重复Prepare拒绝均有实际退出记录，普通PowerShell5.1→CMD→PowerShell7入口同样通过。通过仅证明本地确定性合同与身份检查，不能证明模型理解、网络健康、供应商Schema行为或P02在线成功。即使未来固定P02通过，也只属有限工程探针，仍需冻结配置重复运行、未预置问题、学生收益验证来评估可靠性和产品质量，不临时降低门槛换取通过。

本包授权申请须由用户针对上述完整范围、精确指纹、一次P02/10次/12元单独明确批准；历史已关闭授权不复用。本轮“进入下一步”仅用于准备。

工程成果已冻结，真实在线验收未通过。
