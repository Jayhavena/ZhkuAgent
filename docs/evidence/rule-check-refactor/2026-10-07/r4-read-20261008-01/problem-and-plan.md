# R4：在线读取必须使用激活且合格的索引资料

执行日期：2026-10-08；证据命名空间始于2026-10-07。G0资料及人工参考未通过，本批只有确定性工程夹具。

## 问题与最小设计

默认词法/向量入口以前只按profile ID读取，没有要求ACTIVE；索引run状态、版本和校验和也未约束查询。向量路径先把排序后的全部索引物化到Java，再过滤并截断，导致JDBC读取没有上限。

默认构造只允许ACTIVE；向量路径在embedding调用前检查状态和声明维度，候选查询再次核对状态，覆盖embedding期间退役。已存在的离线评测使用BUILDING进行评测后才激活，因此追加显式`forOfflineEvaluation`工厂，允许BUILDING/ACTIVE但拒绝RETIRED；该入口不是用户授权，也不放宽资料资格。仅两个现有评测调用改用此入口。

SQL预过滤当前schema可表达的条件：完成run、run/profile/切分版本一致、来源已发布/学生可见/分享已确认、scope相符、来源版本及片段校验和与索引一致。向量随后按原余弦距离/UUID精确排序并LIMIT，Java仍复核资料资格；词法仍在合法集合上按原词面评分后截断，不提前截无序集合。

不新增依赖、迁移、ANN索引、产品服务或学生协议。时效/取代仍以false/null映射，不能据此关闭RC-05-LIFECYCLE。版本/hash比较是已存元数据一致性，不代表每次查询重新核对原始PDF字节或审核质量。

## 实际反例与覆盖

- BUILDING/RETIRED/不存在的profile不能在线读取，embedding计数0；声明维度错误不外发，返回长度错误只使向量路径失败。
- 草稿、内部、未确认分享、范围不符、未完成/失败/回滚run、run/profile/切分版本不一致、source版本或chunk/index校验和漂移不可召回。
- 当前库约束禁止草稿或未授权仍学生可见：先证明该非法组合被库拒绝，再以合法内部状态验证不可召回，没有禁用约束。权利字段独立判定另由既有过滤器单元覆盖。
- 不合格最近向量不能挤掉合法较远条款；同距稳定顺序、null scope与旧正常排名保持。
- 实际ResultSet行数与查询计划一起保存，区分“返回给Java的行”与“数据库扫描过的行”。三条合成索引下旧路径JDBC物化3行，新路径1行；这不是大数据性能提升或真实召回质量结论。
- 离线工厂读取BUILDING但不激活它，线上仍拒绝；离线也不允许RETIRED或不合格资料。

## 失败记录与应对

red-01测试包装未实现完整DataSource接口，编译失败，测试未执行；数据库未迁移完成，finally退出容器。red-02实际22项：11断言失败、2夹具违反既有CHECK约束；修正接口和夹具后，red-03实际22项/11断言失败/错误跳过0，才记行为RED。三个批次均保留。

green-01实际24项通过。首轮P0 Acceptance19类142项通过；最终独立审查发现该类裸Maven仍默认原库且新增激活操作会改变全局ACTIVE，必须补类内临时目标校验和拒绝后的清理保护。最终状态以收口报告及复验批次为准，不能仅因green-01通过就忽略该风险。

## 范围、回退与依据

生产范围只有两类Jdbc retriever；测试范围是JdbcCandidateRetrieverTest及两类eval调用，新增目标防护由审查补修声明。runner扩展明确的单元/数据库白名单与本批evidenceDir；真实向量供应商评测类不登记、不执行。

回退先关闭受影响入口，再按当前哈希审查`before/`逐文件恢复，保留历史证据和问题记录。只在独立样例目录演练，不reset/clean原工作区，不改演示库或清理私有卷。

按AGENTS使用Context7核对[PostgreSQL16 EXPLAIN](https://www.postgresql.org/docs/16/using-explain.html)、[参数类型](https://www.postgresql.org/docs/16/sql-prepare.html)及[pgvector精确过滤检索](https://github.com/pgvector/pgvector)。当前迁移只有普通索引，本批不加HNSW/IVFFlat；实际计划记录扫描节点，不用LIMIT推断数据库只扫描一行。
