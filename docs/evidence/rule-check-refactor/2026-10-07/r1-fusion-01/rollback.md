# R1 回退边界

本批无迁移、数据库写入、供应商操作或资料发布，不需要数据回滚。已有dirty文件和其他任务内容必须保留；不使用git reset/clean，也不按HEAD覆盖文件。

代码回退仅将 before/knowledge/ 下两份Java文件按原字节恢复到对应路径，恢复前先核对 source-after.json 的交付哈希。如果当前内容已有后续编辑，不直接覆盖，依据 changes.diff 人工撤销本批增量。恢复后校验 source-before.json 的原SHA-256。

文档/台账是追加的历史事实，通常保留并登记“修复已回退”而不抹去验收。若必须逐文件回退，before/ 保留本批8份文件；主路线图的原指纹单独登记在 scope-extension-before.json。不要恢复旧issues.json而抹去后续执行记录。

恢复旧实现将重新引入截断缺陷，RC-02须重开；重新验证必须使用新Phase，不覆盖本批RED/GREEN/环境失败XML和日志。并行任务存在时按相关源码哈希判断是否允许恢复，不回退其他模块或已有数据目录。
