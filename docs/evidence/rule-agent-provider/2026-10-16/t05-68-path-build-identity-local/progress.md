# 新路径与本地构建身份执行记录

已批准小改动：Host及启动器绑定未占用的2026-10-16/t05-69-review-condition-request，修正两类测试中的当前路径断言；历史夹具路径保持。

TDD：先更新路径测试及三处启动器路径一致性测试并观察失败，再改路径；13个明确ruleagent类回归，通过后独立提交，再统一构建与字节/运行资产核对。

Ruling: 69只预留为新申请路径，本轮不生成目录或正式请求包，不授予额度。
Ruling: 重建前保存旧默认jar79a22f1c副本；所有既有冻结jar及65原包/归档保持。
Ruling: 测试的当前路径预期必须独立于Host常量，避免一起写错仍假绿；新增检查覆盖启动器申请/live/log三处。

边界：20分钟，零真实模型、数据库、Docker操作，不修改提示/模型/预算或放宽防覆盖守卫。

RED:5 tests,5 expected failures,0 errors. Production host/launcher paths now changed from65 to69; no guard or business behavior changed.

首次完整回归213项：0断言失败、9夹具输出目录缺失错误；原日志和XML保留。显式创建本轮test-evidence目录后，同一13类命令重跑：213项、0失败/错误/跳过。独立审查无Critical/Important；CRLF提示按项目cr-at-eol检查，保留原字节。本检查点仅绑定路径及本地测试，尚未重建jar。

源码已提交bc5cdf4。普通终端离线BuildOnly退出0；实际jar/class/15资产运行核对通过。未生成69目录，真实模型/DB/Docker均0。原始诊断XML尾空格按准确范围豁免，不改写历史证据。详见README及build/runtime/execution记录。

归档检查发现manifest提取的换行转换；已改二进制原样提取并重跑身份核对，通过。jar标准manifest末尾空行精确豁免，不修改实际jar。
