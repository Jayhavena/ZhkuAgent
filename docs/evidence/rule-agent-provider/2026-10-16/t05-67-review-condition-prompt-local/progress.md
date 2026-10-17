# 本轮执行记录：已批准的核验提示最小修改

约束：20分钟；零外发、数据库、Docker；只改系统提示及核验合同说明，不改变条件记录、模型、预算或守卫。

设计来自本会话已确认的小改动，不另建大型计划。复用现有codex/t05-online-evidence工作树，保留主仓库及既有改动。

步骤：新增实际载荷/同源约束/身份与容量/原键对照测试→观察预期失败→两文件实现→指定本地类回归→独立审查→本地提交。

Ruling: 条件键说明使用同一个常量投影到系统提示和reviewContract；不添加ReviewedCondition字段，避免形状与身份无必要变化。

Ruling: 本轮只编译与测试，不打包jar；下一轮构建核对前旧jar继续保留，不能用于新源码在线验收。
RED已确认：5项测试，1预期失败（出站conditionIdForm缺失），0错误。
实现：系统提示与reviewContract.conditionIdForm使用同一段原键规则；删除旧长句中的简写，输入形状/守卫保持。

GREEN：11个明确领域类163项，0失败/错误，进程退出及资源关闭确认。
Ruling: 两类批次路径测试在基线仍断言62，实际Host/launcher已经65；与本轮提示修改无关，单列未纳入本轮领域回归，不宣称全套通过。若下一轮继续构建/入口验收需先处理该既有失配。
测试追加保存实际reviewPayload输出，便于审阅；仅重跑新增5项验证该证据写出，不扩展生产范围。

完成：163项局部回归通过；新增payload记录后5项复验通过；独立审查无发现。
Final: Ruling: 审查未运行测试→以本轮实际报告为准；未判断模型遵循→继续未知；未判断所有输入容量→仅声明本样本7706/65536；旧路径测试→单列不混入通过统计；证据目录参数→每次已显式传入。风险及代价详见review.json。

Precommit note: raw Surefire red failure XML contains generated trailing whitespace; preserve original bytes and exempt only this raw diagnostic from whitespace checks. No source/doc whitespace exemption.
