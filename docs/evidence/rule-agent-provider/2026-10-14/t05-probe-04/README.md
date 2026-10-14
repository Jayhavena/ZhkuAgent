# T05剩余任务3：真实探针失败，按约定停止

本轮开始2026-10-14 10:40:58，用户明确允许call_0b75ea7bcd2c476082647541154a0cec所述本批最多12次外发。批准包SHA256 ca44be57b868e4ebdb8f588fc6cde8f1332d092207d9d18ed38900abee697f61；实现检查点c43dc8cf3b819e998791b0c59e814e1cf31a4aa6，回执2fc4a359a360997ed03403c83f204982868a5d40，jar SHA256 9b54ba0b81a8e06752e0d15f1967a136b8061055302594afbd9792e05d51abe6。源码/请求包未修改，旧失败和评分不回改。

实际本批仅2次HTTP，均DECISION，均收到HTTP200完整响应；P01在READ动作参数校验停止。P02、PUBLIC续答及独立语义核验未执行，剩余10次不继续使用。本批及T05均未通过，不保存实现完成提交、不进入T06。

| 阶段 | 已验证结果 | 证据 |
| --- | --- | --- |
| 启动前配置 | 外层200秒参数超过执行器150秒上限，函数体未进入；随后一次恢复脚本语法错误，脚本体未进入；均模型/DB0 | launcher-parameter-error.json、launcher-parser-error.json；两个原预检回执保留 |
| 唯一实际启动 | 新鲜读取环境凭证，仅记录可用性；修正外层参数后10:44:01至10:44:16首次执行，未重试模型 | authorization-and-preflight-resumed.json、approved-launcher-result.json |
| 模型调用1 | SEARCH合法，实际检索10个候选；候选UUID均规范，包含第4条 | actual-outcome-sanitized.json、原始outcome SHA |
| 模型调用2 | ACTION_PARAMETERS / READ_HANDLE_UUID，规范UUID校验拒绝；尚未执行READ | provider-traces.jsonl、failure-assessment.json；完整自由响应不保存，不能还原错误元素值 |
| 发布 | TaskStatus FAILED、PROCESSING_FAILURE，无主张、无学生证据；未把执行失败称为资料缺失 | 实际Java快照经前端合同接受 |
| 退出 | 宿主resourcesClosed=true、父启动器退出；只读检查探针容器和Java进程均0 | probe-result.json、approved-launcher-result.json、resource-exit-check.json |

已知用量只按RECEIVED每次计入一次：输入2327 token，输出150 token。费用未知，不推算账单。两次BEGIN与模型逻辑计数2、allowanceConsumed2一致；toolStepCount1只有SEARCH。独立核验调用数0，不把其性能、解析或相关性评价为成功/失败。

准确卡点：READ响应至少一个handles元素未能通过RuleAgentProtocol.checkRead的UUID.fromString及规范字符串一致性检查（包括非字符串/不可解析/非规范形式）。不能猜测它是条款号、复制错误、对象、占位符或额外说明；原完整自由响应没有保留。实际检索器已提供有效UUID及所需第4条候选，因此本批不是“未找到资料”或已确认“资料不适用”；也没有进入FINISH/Publisher或核验链路，不能据此认定该链路再次失败。

后续范围只针对READ候选引用及受控诊断：先本地补足错误元素位置、JSON类型、规范格式检查结果等安全诊断（不记录任意正文/异常文本），核对模型可见字段形状与宿主约束，再选择最小约束修正。具体错误值及成因仍未知，不带猜测修改政策或检索。任何新真实探针需新的具体载荷/次数授权；当前失败即停止，不自动补测、不提高预算、不新增政策题库、不重构整个项目。

failure-assessment.json是对实际结构化产物的机械核对：probePassed=false，safetyChecksPassed=true；不代表任务质量通过。脱敏副本删除访问/续答token；原始文件保留本机，摘要绑定其身份。该失败现场为t05-live-48509ea06b12415daee4f1e842fe774c，只暂存明确安全产物，不提交数据库、PDF、凭证、原始访问token或构建jar。

本轮只允许证据保存提交，标题明确真实探针失败；这是失败记录检查点，不是T05完成提交。实际外发授权记录与不可重复脚本保留，旧请求包的NOT_AUTHORIZED字段不改动，当前批准由单独回执绑定。父提交源码仍为已通过本地检查的版本，下一轮复用，不重复实施前两步。
