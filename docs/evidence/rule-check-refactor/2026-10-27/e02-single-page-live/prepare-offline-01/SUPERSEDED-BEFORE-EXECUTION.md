# 本包已作废，禁止授权

- 本目录的 `request-package.json`：`3a96201b583ddb68f91ca7ad5b4e35dc0f1029f17afe4dc5f3abb1a8e976fa85`
- 作废原因：包内 `sourceInputsSha256` 记录的启动器
  `knowledge/scripts/start-rule-agent-three-question-test.ps1` 为 `89bb1c3b…`；该脚本随后加入
  E02 档位（`-Profile`/`-Dzhku.three.profile=E02`、`$HardBudgetRmb` 4.02 闸门），磁盘字节已变为 `d076ea39…`。
  因此本包在当前工作树上运行 `verify` 必定失败，**更不能用于 Live**。
- 替代包：`../prepare-offline-02`，sha256 `b52d47d0ec31ca745b52b844da0459575ff1a7035b0f4beef87fe868abd93178`（已 verify 通过）。
- 本包从未执行：零 Provider 调用、零密钥读取、未建数据库、未建容器、未提交。
- 保留原因：留作“冻结后被改动”的证据，不删除。
