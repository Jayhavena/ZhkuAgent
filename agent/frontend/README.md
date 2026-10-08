# 仲园 Agent 前端

当前候选版为 `0.1.0-rc.1`：校园规则查证为主入口，v2五状态浏览器验收已完成，候选前端85项测试和构建通过；旧v1页面兼容验收仍待连接恢复。完整工程/真实资料放行分别见[候选版说明](../../docs/releases/v0.1.0-rc.1/README.md)。

## 历史里程碑与既有演示入口

**当前3C已完成并通过12项门禁。** 提交键、确认查询及刷新保护已接线，本轮46项前端测试和构建通过；带重修会话的完整网页已通过V21兼容修复、普通终端启动及真实Nginx检查。[本轮验收、使用约束与证据](../../docs/evidence/milestone-3c/2026-10-07/3c-3/acceptance.md)。本轮模型为协议/业务替身，供应商调用0；下文P4是此前版本的用户人工报告，不代替本版本人工或真实模型效果。

Vue3 校园工作台已经连接受控后端：登录与退出、学分提交/轮询/取消、本人历史、重修澄清与控制状态恢复。**用户已确认P4人工功能验收通过，功能开发收口。** [人工确认与最终收尾](../../docs/evidence/controlled-web-demo/2026-10-06/p4/final-closeout/manual-acceptance.md)区分用户报告、构建、接口夹具、真实后端及未验证项；不等于所有浏览器质量检查通过。[启动与5～8分钟演示](../../docs/manual/controlled-campus-web-demo.md)。

在仓库根目录用普通 Windows 终端：

```powershell
.\knowledge\scripts\start-controlled-web-demo.cmd
```

默认网页 `http://127.0.0.1:58080/campus/login`。启动器隐藏设置 `user-a`、`user-b`、`maintainer` 密码，建立本次专用库并构建页面；`r` 重启保持本次库，`q` 退出删除本次临时数据。不是生产账号系统，无开放注册。

页面路由：`/campus/login`、`/campus/run?submissionKey=...`、`/campus/history?runId=...`、`/campus/conversation?sessionId=...`。旧runId链接仍可读取本人任务，不会附上不相干的提交键。请求同源 `/api/zhku/controlled-preview/v1`；身份/CSRF/资料许可都由Java裁决。ID不是权限，防连点不是后端幂等。页面不恢复完整聊天；答案隐藏不能解释成执行失败。

3C提交操作：首次提交生成键并冻结原请求；网络结果不明时优先“确认本次提交”，不会自动POST重试。只有当前页面仍持有原请求快照才允许手动重发同一次提交；刷新只保留最多5条用户/键/Run编号/时间元数据，不能重发丢失的原课程。“新建任务”才生成新键。401后同一账号重新登录可以查询；明确退出或切换账号会清理旧提示和结果。`UNCONFIRMED`不是未受理证明，`INTERRUPTED`不表示自动续跑。

开发者已把 Node/npm 配入当前终端PATH时，在本目录构建；从仓库根运行前端协议与协调测试。普通人工演示优先用上面的完整.cmd入口，它会查找现有Node/npm，不要求裸命令可用：

```powershell
# 在 agent/frontend
npm.cmd run build
# 在仓库根目录
node --test agent/frontend/tests/controlled-*.test.mjs
```

保留现有Vue3/Vite4/Router4及依赖锁，不增加UI或状态框架。`npm run dev`仅是开发服务器；本版默认展示是构建产物＋固定Nginx镜像，不能把开发服务与Nginx验收混称。`tests/controlled-browser-fixture.mjs`是显式测试接口，不进入生产入口，不能证明可信登录、数据库或真实模型。

已有受控服务可单独使用 `.\start-nginx-preview.cmd -BackendPort <实际端口>`；停止 `.\start-nginx-preview.cmd -Action Stop`。完整启动器使用每次独立ProjectName并负责自己的清理，不停止这个独立默认项目。[Nginx职责与故障定位](../../docs/manual/nginx-local-preview.md)。

旧通用页面独立保留，但本Nginx只代理校园受控API，旧 `/api/ai/manus/chat` 为404，不能从这里演示通用Agent。不重新启用旧外部代理。
