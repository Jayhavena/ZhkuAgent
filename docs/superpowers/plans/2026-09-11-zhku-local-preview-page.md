# 仲园 Agent 极简本机问答页面实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**目标：** 在不新增问答业务逻辑的前提下，为仲园 Agent 的本机预览应用提供一个可访问、可提问、可查看依据的极简浏览器页面。

**架构：** 新增一个只在 `zhku-local-preview` Profile 与显式开关同时启用时注册的页面控制器，用 `GET /zhku/local-preview` 返回受控的类路径 HTML 资源。页面使用原生浏览器 JavaScript 调用既有 `POST /zhku/local-preview/answers` 接口，并按 `GROUNDED`、`INSUFFICIENT_EVIDENCE`、400 和 503 的既有契约渲染界面；不改变 `GroundedAnswerService`、数据库模式或问答接口。

**技术栈：** Java 21、Spring Boot、Spring MVC、JUnit 5、MockMvc、原生 HTML/CSS/JavaScript。

**设计依据：** `docs/zhkuagent/仲园Agent-极简本机问答页面设计.md`

## 全局约束

- 页面仅用于本机验证；不得添加登录、上传、历史记录、模型调用、公开部署或敏感校务数据入口。
- 本机隔离是功能验收条件：专用启动入口以外不得注册页面控制器，也不得通过通用静态资源路径访问页面。
- 页面与页面控制器必须同时受 `zhku-local-preview` Profile 和 `zhku.local-preview.enabled=true` 限制。
- 页面资源放在 `src/main/resources/zhku/local-preview/`，不得放入通用静态资源目录。
- 浏览器端只允许调用既有对外地址 `/api/zhku/local-preview/answers`，不得复制规则判断、资料检索或出处组装逻辑。
- 回答、提示和出处使用文本节点渲染；不得对服务端返回字段使用 `innerHTML`。
- UI 必须使用中文，保留必要的技术状态值 `GROUNDED` 与 `INSUFFICIENT_EVIDENCE` 仅在代码和测试中使用。
- 所有新增的关键隔离或安全边界应有简洁、明确的中文注释；不逐行注释。
- 每个任务完成后先运行其指定测试，展示差异，只有用户明确批准该任务差异后才创建提交；绝不合并到其他分支。

---

## 文件结构

| 文件 | 变更 | 职责 |
| --- | --- | --- |
| `src/main/java/com/yupi/yuaiagent/zhku/knowledge/web/ZhkuLocalPreviewPageController.java` | 新建 | 在专用本机预览条件下提供 HTML 页面资源。 |
| `src/main/resources/zhku/local-preview/index.html` | 新建 | 单文件页面，承载视觉样式、输入校验、请求和状态渲染。 |
| `src/test/java/com/yupi/yuaiagent/zhku/knowledge/web/ZhkuLocalPreviewPageControllerTest.java` | 新建 | 验证页面路由、UTF-8 响应和资源内容。 |
| `src/test/java/com/yupi/yuaiagent/zhku/knowledge/web/ZhkuLocalPreviewPageResourceTest.java` | 新建 | 验证页面对接口的唯一依赖、安全渲染约束和关键界面标记。 |
| `src/test/java/com/yupi/yuaiagent/zhku/knowledge/web/ZhkuLocalPreviewConfigurationTest.java` | 修改 | 补充页面控制器在专用 Profile 外不注册的隔离测试。 |
| `docs/zhkuagent/仲园Agent-本机HTTP问答接口开发者验收手册.md` | 修改 | 增加浏览器页面启动、访问与手工验收步骤。 |

## 接口与依赖约定

### 页面服务接口

```java
// GET /zhku/local-preview
ResponseEntity<Resource> page();
```

该接口只返回 `classpath:zhku/local-preview/index.html`，响应 `Content-Type` 为 `text/html;charset=UTF-8`。应用对外还会加上既有的 `/api` 前缀，因此浏览器访问地址为 `/api/zhku/local-preview`。它不访问数据库、不读取问题参数，也不创建 `GroundedAnswerService`。

### 既有问答接口（不可修改）

```text
应用内部映射：POST /zhku/local-preview/answers
浏览器对外地址：POST /api/zhku/local-preview/answers
请求：{ "question": "..." }
成功：{ "mode", "answer", "citations", "notice" }
输入错误：HTTP 400，{ "code": "INVALID_QUESTION", "message": "..." }
服务不可用：HTTP 503，{ "code": "KNOWLEDGE_SERVICE_UNAVAILABLE", "message": "..." }
```

### 页面 DOM 约定

页面必须提供下列稳定标记，供浏览器脚本和资源契约测试使用：

```text
#question-input        问题输入框
#submit-question       提交按钮
#question-error        输入错误提示区
#answer-panel          查询结果容器
#answer-status         有依据或资料不足状态文字
#answer-content        回答正文
#citation-panel        整个出处区域
#citation-list         出处条目容器
#preview-notice        接口返回的本机验证提示
#service-error         网络或服务异常提示
```

---

### 任务 1：受控页面路由与资源骨架

**文件：**

- 新建：`src/test/java/com/yupi/yuaiagent/zhku/knowledge/web/ZhkuLocalPreviewPageControllerTest.java`
- 新建：`src/main/java/com/yupi/yuaiagent/zhku/knowledge/web/ZhkuLocalPreviewPageController.java`
- 新建：`src/main/resources/zhku/local-preview/index.html`
- 修改：`src/test/java/com/yupi/yuaiagent/zhku/knowledge/web/ZhkuLocalPreviewConfigurationTest.java`

**消费：** `ZhkuLocalPreviewController` 已有的 Profile、显式开关和 URL 前缀约定。

**产出：** `GET /zhku/local-preview` 的本机页面路由；后续页面脚本只依赖此资源与既有问答接口。

- [ ] **步骤 1：编写页面路由的失败测试**

新建 `ZhkuLocalPreviewPageControllerTest`，以 `MockMvcBuilders.standaloneSetup` 创建 `ZhkuLocalPreviewPageController`。测试资源路由、UTF-8 和页面标题：

```java
@Test
void shouldReturnUtf8LocalPreviewPage() throws Exception {
    MockMvc mockMvc = MockMvcBuilders.standaloneSetup(
            new ZhkuLocalPreviewPageController()
    ).build();

    mockMvc.perform(get("/zhku/local-preview"))
            .andExpect(status().isOk())
            .andExpect(header().string(
                    HttpHeaders.CONTENT_TYPE,
                    "text/html;charset=UTF-8"
            ))
            .andExpect(content().string(containsString(
                    "<title>仲园 Agent｜校园资料问答</title>"
            )));
}
```

在 `ZhkuLocalPreviewConfigurationTest` 新增隔离测试：

```java
@Test
void shouldNotRegisterPreviewPageControllerOutsideLocalPreviewProfile() {
    new ApplicationContextRunner()
            .withUserConfiguration(ZhkuLocalPreviewPageController.class)
            .run(context -> assertThat(context)
                    .doesNotHaveBean(ZhkuLocalPreviewPageController.class));
}
```

- [ ] **步骤 2：运行测试并确认它失败**

运行：

```powershell
.\mvnw.cmd '-Dtest=ZhkuLocalPreviewPageControllerTest,ZhkuLocalPreviewConfigurationTest' test
```

预期：失败，原因是 `ZhkuLocalPreviewPageController` 尚不存在；不得通过删除测试或修改既有问答接口规避失败。

- [ ] **步骤 3：以最小代码实现受控页面路由和骨架资源**

新建控制器。注解条件必须与既有 `ZhkuLocalPreviewController` 保持一致；中文注释解释“资源不走通用静态目录”的隔离原因。

```java
@RestController
@Profile("zhku-local-preview")
@ConditionalOnProperty(
        prefix = "zhku.local-preview",
        name = "enabled",
        havingValue = "true"
)
@RequestMapping("/zhku/local-preview")
public class ZhkuLocalPreviewPageController {

    private static final MediaType HTML_UTF8 = new MediaType(
            MediaType.TEXT_HTML,
            StandardCharsets.UTF_8
    );

    @GetMapping
    public ResponseEntity<Resource> page() {
        // 显式控制页面路由，避免本机预览资源随通用应用入口意外公开。
        return ResponseEntity.ok()
                .contentType(HTML_UTF8)
                .body(new ClassPathResource("zhku/local-preview/index.html"));
    }
}
```

同时创建最小 HTML 骨架，至少包含 UTF-8 元信息、标题、`main` 元素和所有“页面 DOM 约定”中列出的标记；此时不实现请求逻辑。

```html
<!doctype html>
<html lang="zh-CN">
<head>
  <meta charset="UTF-8">
  <title>仲园 Agent｜校园资料问答</title>
</head>
<body>
  <main aria-labelledby="page-title">
    <h1 id="page-title">仲园 Agent</h1>
    <p>校园资料问答｜本机验证</p>
    <textarea id="question-input"></textarea>
    <button id="submit-question" type="button">查询资料</button>
    <p id="question-error" hidden></p>
    <section id="answer-panel" hidden>
      <p id="answer-status"></p>
      <p id="answer-content"></p>
      <section id="citation-panel" hidden><div id="citation-list"></div></section>
      <p id="preview-notice"></p>
    </section>
    <p id="service-error" hidden></p>
  </main>
</body>
</html>
```

- [ ] **步骤 4：运行测试并确认通过**

运行：

```powershell
.\mvnw.cmd '-Dtest=ZhkuLocalPreviewPageControllerTest,ZhkuLocalPreviewConfigurationTest' test
```

预期：全部通过。确认既有 `ZhkuLocalPreviewControllerTest` 未被修改。

- [ ] **步骤 5：展示差异并在用户批准后提交**

展示：

```powershell
git diff -- src/main/java/com/yupi/yuaiagent/zhku/knowledge/web/ZhkuLocalPreviewPageController.java src/main/resources/zhku/local-preview/index.html src/test/java/com/yupi/yuaiagent/zhku/knowledge/web/ZhkuLocalPreviewPageControllerTest.java src/test/java/com/yupi/yuaiagent/zhku/knowledge/web/ZhkuLocalPreviewConfigurationTest.java
```

仅在用户明确批准本任务差异后执行：

```powershell
git add -- src/main/java/com/yupi/yuaiagent/zhku/knowledge/web/ZhkuLocalPreviewPageController.java src/main/resources/zhku/local-preview/index.html src/test/java/com/yupi/yuaiagent/zhku/knowledge/web/ZhkuLocalPreviewPageControllerTest.java src/test/java/com/yupi/yuaiagent/zhku/knowledge/web/ZhkuLocalPreviewConfigurationTest.java
git commit -m "feat(zhku): 提供本机问答页面路由"
```

不得执行合并或推送。

---

### 任务 2：实现极简页面的查询与安全状态渲染

**文件：**

- 新建：`src/test/java/com/yupi/yuaiagent/zhku/knowledge/web/ZhkuLocalPreviewPageResourceTest.java`
- 修改：`src/main/resources/zhku/local-preview/index.html`

**消费：** 任务 1 提供的页面资源，以及既有 `POST /zhku/local-preview/answers` 响应契约。

**产出：** 可在浏览器中提交问题、展示有依据回答或资料不足状态的单文件页面；所有服务端文字通过 `textContent` 进行安全渲染。

- [ ] **步骤 1：编写页面资源的失败契约测试**

新建 `ZhkuLocalPreviewPageResourceTest`，通过 `ClassPathResource` 读取页面文本。该测试不伪造浏览器运行时；它锁定页面必须保留的接口边界和安全约束，浏览器交互留给任务 3 手工验收。

```java
@Test
void shouldKeepPageBoundToExistingAnswerApiAndSafeTextRendering() throws Exception {
    String page = new String(
            new ClassPathResource("zhku/local-preview/index.html")
                    .getInputStream().readAllBytes(),
            StandardCharsets.UTF_8
    );

    assertThat(page).contains("/api/zhku/local-preview/answers");
    assertThat(page).contains("id=\"question-input\"");
    assertThat(page).contains("id=\"citation-panel\"");
    assertThat(page).contains("GROUNDED");
    assertThat(page).contains("INSUFFICIENT_EVIDENCE");
    assertThat(page).contains("textContent");
    assertThat(page).doesNotContain("innerHTML");
}
```

- [ ] **步骤 2：运行测试并确认它失败**

运行：

```powershell
.\mvnw.cmd '-Dtest=ZhkuLocalPreviewPageResourceTest' test
```

预期：失败，因为任务 1 的骨架尚无既有问答接口调用、状态值和安全文本渲染逻辑。

- [ ] **步骤 3：完善单文件页面**

在 `index.html` 中实现以下内容，保持单栏“校园资料册”视觉，不引入第三方库：

1. 顶部展示“仲园 Agent”“校园资料问答”“本机验证”，并写明“仅基于已审核资料回答；资料不足会明确说明”。
2. 增加 `textarea#question-input`、`button#submit-question`、示例问题提示和 `#question-error`。
3. 使用深农业绿 `#17382D`、叶绿 `#317047`、米纸 `#F7F5ED`、卡片 `#FFFEF9`、分隔线 `#D2DCCD`、出处暖棕 `#AD6A35`；窄屏下输入区和按钮上下排列。
4. 表单提交时用 `trim()` 校验 1 至 500 个字符。校验失败时保留输入、填充 `#question-error`，且不发起网络请求。
5. 查询中禁用按钮并显示“正在查询资料…”。无论请求成功、失败或异常，都在 `finally` 中恢复按钮。
6. 通过唯一的对外路径 `fetch("/api/zhku/local-preview/answers", ...)` 发送 JSON 请求。这里必须保留 `/api`，因为页面实际由 `http://127.0.0.1:8123/api/zhku/local-preview` 提供；控制器的内部映射不含该前缀：

```javascript
const response = await fetch("/api/zhku/local-preview/answers", {
  method: "POST",
  headers: { "Content-Type": "application/json; charset=UTF-8" },
  body: JSON.stringify({ question })
});
```

7. 对 `response.ok === false`，优先读取 `message`；HTTP 400 显示输入修正提示，HTTP 503 或网络异常显示“本机知识服务暂不可用，请确认预览服务和数据库均已启动”。
8. 对 `mode === "GROUNDED"`，将 `answer` 写入 `#answer-content`，逐条创建出处元素并追加到 `#citation-list`，显示 `#citation-panel`，再将 `notice` 写入 `#preview-notice`。
9. 对 `mode === "INSUFFICIENT_EVIDENCE"`，显示“当前资料未覆盖该问题”，清空并隐藏 `#citation-panel`，展示接口的 `notice`。
10. 对其他 `mode`、缺少 `answer` 或有依据回答却没有出处的响应，显示服务不可用提示，不把不完整响应当作正常答案。
11. 所有接口字段都通过 `element.textContent = value` 或 `document.createElement` 生成，不得使用 `innerHTML`。

关键状态分支应采用下面的结构，字段名不得另起一套：

```javascript
if (payload.mode === "GROUNDED" && payload.answer && payload.citations?.length) {
  renderGroundedAnswer(payload);
  return;
}
if (payload.mode === "INSUFFICIENT_EVIDENCE") {
  renderInsufficientEvidence(payload);
  return;
}
showServiceError();
```

- [ ] **步骤 4：运行资源契约和既有接口测试**

运行：

```powershell
.\mvnw.cmd '-Dtest=ZhkuLocalPreviewPageResourceTest,ZhkuLocalPreviewPageControllerTest,ZhkuLocalPreviewControllerTest' test
```

预期：全部通过。既有接口测试继续验证 `GROUNDED`、`INSUFFICIENT_EVIDENCE`、400、503 和 JSON UTF-8，不修改其断言。

- [ ] **步骤 5：展示差异并在用户批准后提交**

展示：

```powershell
git diff -- src/main/resources/zhku/local-preview/index.html src/test/java/com/yupi/yuaiagent/zhku/knowledge/web/ZhkuLocalPreviewPageResourceTest.java
```

仅在用户明确批准本任务差异后执行：

```powershell
git add -- src/main/resources/zhku/local-preview/index.html src/test/java/com/yupi/yuaiagent/zhku/knowledge/web/ZhkuLocalPreviewPageResourceTest.java
git commit -m "feat(zhku): 完成本机问答页面交互"
```

不得执行合并或推送。

---

### 任务 3：补充开发者验收手册并完成真实链路验证

**文件：**

- 修改：`docs/zhkuagent/仲园Agent-本机HTTP问答接口开发者验收手册.md`

**消费：** 任务 1 的页面路由、任务 2 的浏览器交互、既有本机预览启动入口。

**产出：** 开发者无需了解前端实现即可启动页面、完成两条核心旅程并记录失败时的检查顺序。

- [ ] **步骤 1：先确认文档缺少页面验收说明**

运行：

```powershell
rg -n "local-preview$|浏览器|校园资料问答|查询资料" docs/zhkuagent/仲园Agent-本机HTTP问答接口开发者验收手册.md
```

预期：当前手册没有完整的浏览器页面入口、两类结果和错误状态验收步骤。该步骤是文档需求确认，不修改代码。

- [ ] **步骤 2：更新手工验收说明**

在手册中新增“浏览器页面验收”小节，使用现有专用启动命令，不读取或输出 `infra/.env` 中的密码：

```powershell
Set-Location D:\alldesk\Projects\SuperAgent-zhkuagent-mvp
$zhkuDbPassword = ((Get-Content -LiteralPath 'infra/.env' | Where-Object { $_ -match '^ZHKU_DB_PASSWORD=' }) -replace '^ZHKU_DB_PASSWORD=', '')
$env:ZHKU_DB_PASSWORD = $zhkuDbPassword
.\mvnw.cmd '-Dspring-boot.run.main-class=com.yupi.yuaiagent.zhku.knowledge.web.ZhkuLocalPreviewApplication' spring-boot:run
```

说明浏览器地址为：

```text
http://127.0.0.1:8123/api/zhku/local-preview
```

手册必须列出以下可复现验证和预期结果：

| 操作 | 预期结果 |
| --- | --- |
| 输入“公共选修课每学期最多修多少学分？”并查询 | 显示有依据回答、PDF 第 76—77 页定位和本机验证提示。 |
| 输入“奖学金申请截止日期是什么时候？”并查询 | 显示当前资料未覆盖，不显示出处卡片。 |
| 清空输入后查询 | 显示输入错误，原输入不被异常清除。 |
| 停止预览服务或数据库后查询 | 显示本机知识服务不可用，不显示密码、连接串或堆栈。 |

同时给出页面打不开时的检查顺序：先确认启动窗口显示 `127.0.0.1:8123`，再确认 URL 是页面路由而非 `/answers`，最后确认 Docker PostgreSQL 正在运行。不得建议把服务改为 `0.0.0.0`。

- [ ] **步骤 3：运行最终自动化回归测试**

运行：

```powershell
.\mvnw.cmd '-Dtest=ZhkuLocalPreviewPageControllerTest,ZhkuLocalPreviewPageResourceTest,ZhkuLocalPreviewControllerTest,ZhkuLocalPreviewConfigurationTest,ZhkuLocalPreviewApplicationTest' test
```

预期：全部通过。不要把“全仓库 `mvn test`”作为本任务完成门槛，因为现有通用 Agent 测试依赖与本机页面无关的模型或外部环境。

- [ ] **步骤 4：执行两条核心浏览器旅程并记录结果**

按手册启动专用应用，在浏览器中完成：

1. 有依据回答：公共选修课学分问题；
2. 资料不足：奖学金截止日期问题。

记录每条旅程的结果为“通过”或“失败”，失败时记录可见错误信息；不得记录数据库密码或完整连接配置。

- [ ] **步骤 5：展示差异并在用户批准后提交**

展示：

```powershell
git diff -- docs/zhkuagent/仲园Agent-本机HTTP问答接口开发者验收手册.md
```

仅在用户明确批准本任务差异后执行：

```powershell
git add -- docs/zhkuagent/仲园Agent-本机HTTP问答接口开发者验收手册.md
git commit -m "docs(zhku): 补充本机问答页面验收"
```

不得执行合并或推送。

---

## 实施完成后的核验清单

- [ ] 页面只在专用本机预览条件满足时提供，普通应用环境不会注册页面控制器。
- [ ] 页面路由、问答接口均使用 UTF-8，Windows PowerShell 与浏览器中文显示正常。
- [ ] 页面只调用一个既有问答接口，不含资料检索或规则判断的重复实现。
- [ ] 有依据回答始终紧贴至少一条出处；资料不足始终没有出处卡片。
- [ ] 输入错误、服务不可用和异常响应不泄露本机部署细节。
- [ ] 所有服务端返回文本都通过安全文本节点呈现，不使用 `innerHTML`。
- [ ] 所有新增和修改文件都经过任务级测试、差异评审和用户批准后提交，且未合并。
