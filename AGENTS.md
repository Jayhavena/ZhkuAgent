# ZhkuAgent — Agent Development Rules

## 1. Project Direction

ZhkuAgent is a campus rule verification Agent. Its core workflow is:

**User Question → Clarification → Retrieval → Evidence Verification → Applicability Check → Answer**

- Prioritize reliable end-to-end rule verification, source traceability, and applicability checks.
- Keep model decisions separate from host-side permission checks, parameter validation, tool execution, budgets, and publication gates.
- Reuse existing architecture and capabilities; do not expand scope or build auxiliary systems without a concrete task requirement.
- Agent Evaluation serves real Agent quality verification. Prioritize the real execution → Trace/Evidence → existing Evaluator/Grader → Report chain; do not build a parallel scoring framework.
- Read relevant project documentation before architectural or business changes. Do not treat historical status reports as proof of current behavior.

## 2. Project Map and Documentation

### Repository structure

The repository is a Maven multi-module project:

| Module | Responsibility |
|---|---|
| **agent-runtime/** | Shared Run lifecycle, snapshots, tool-call protocol, events, and runtime errors |
| **agent/** | General Agent, main loop, tools, sessions, RAG capabilities, and Vue frontend |
| **knowledge/** | Campus knowledge governance, retrieval, rule verification, controlled entry points, and evaluation |

- **agent-runtime** is a shared library, not an independent application entry point.
- **agent** and **knowledge** share the runtime but do not depend on each other's business modules.
- Campus-specific rules and knowledge belong in **knowledge**.
- The frontend is located at **agent/frontend/**.
- Preserve existing module boundaries. Do not move business responsibilities into the shared runtime without an explicit architectural reason.

### Technology stack

- Backend: Java 21, Maven multi-module, Spring Boot 3.4.
- Knowledge and retrieval: PostgreSQL, pgvector, Flyway, Spring AI.
- AI integrations: Spring AI and Spring AI Alibaba; consult the relevant module POM for the actual provider integrations and versions.
- Frontend: Vue 3, Vue Router 4, Vite 4.
- Docker Compose provides the local PostgreSQL environment.

The root and module **pom.xml** files are authoritative for backend dependency versions. **agent/frontend/package.json** and its lockfile are authoritative for frontend dependencies. Different Maven modules currently manage Spring AI dependencies separately; do not assume they use identical versions.

### Documentation navigation

Read the following sources according to the task:

| Source | Purpose |
|---|---|
| **README.md** | Project positioning, module architecture, basic build and startup |
| **docs/README.md** | Documentation index and directory responsibilities |
| **docs/zhkuagent/** | Project scope, product decisions, status and milestones |
| **docs/design/** | Architecture and interface contracts |
| **docs/decisions/** | Existing engineering decisions and trade-offs |
| **docs/agents/** | Issue, domain and collaboration conventions |
| **docs/collaboration/** | Cross-module ownership and integration rules |
| **docs/agent/eval/chain/README.md** | Evaluation architecture, commands and grading boundaries |
| **docs/superpowers/plans/** | Task-specific implementation plans and acceptance criteria |
| **docs/evidence/** | Historical execution evidence and verification records |

Documentation rules:

- Start from **docs/README.md**, then follow the relevant topic-specific documents.
- Verify the referenced document's date, scope, branch and source SHA before relying on its conclusions.
- A newer document is not automatically authoritative if its task scope or source identity does not match.
- Code, build configuration, tests and actual runtime behavior determine the current implementation.
- Historical PASS results do not establish current behavior.
- If documentation conflicts, identify the conflict and preserve the evidence. Do not silently choose one version or rewrite history.
- Do not create another project overview, status index or evaluation architecture document when an existing document already serves that purpose.

## 3. Development Commands

Run commands from the repository root unless otherwise stated.

### Known command entry points

| Purpose | Working directory | Command |
|---|---|---|
| Build knowledge module and dependencies | Repository root | **.\mvnw.cmd -pl knowledge -am -DskipTests package** |
| Agent Evaluation regression | Repository root | **.\mvnw.cmd -pl knowledge -am -Dtest=AgentEvalChainRegressionTest -Dsurefire.failIfNoSpecifiedTests=false test** |
| Frontend production build | **agent/frontend/** | **npm.cmd run build** |
| Frontend controlled regression tests | Repository root | **node --test agent/frontend/tests/controlled-*.test.mjs** |

Command boundaries:

- The Maven package command skips tests. It proves packaging only, not test or runtime acceptance.
- Agent Evaluation regression uses archived evidence and the existing Grader. It does not prove current real-model behavior.
- The documented frontend test command covers the **controlled-*.test.mjs** subset, not necessarily the entire frontend test suite.
- The current frontend **package.json** does not define a **test** script. Do not assume **npm test** exists.
- Before running other tests, inspect the relevant module's test configuration, test dependencies and resource requirements.
- Do not invent scripts or claim a command is an established project entry point without verifying it.
- Use the repository's existing scripts and test conventions before introducing new tooling.

### Execution principles

- Run the smallest relevant check first.
- Expand regression coverage according to the actual impact of the change.
- Distinguish compilation, unit tests, integration tests, database tests, browser/E2E tests, offline replay, real-model execution and product acceptance.
- Never report an unexecuted test as PASS.
- Real Probe and Live execution are not ordinary development commands. They require their own explicit authorization and approved execution package.

## 4. Autonomy and Authorization

Within the explicitly authorized task scope, Agents may independently inspect code, plan, modify authorized files, write and run relevant tests, fix in-scope defects, and delegate independent work. Do not repeatedly ask permission for ordinary actions already covered by the task.

- User authorization defines the current task scope; these project rules define the default safety boundaries. General task authorization does not grant unrelated permissions or waive safety requirements.
- If an operation exceeds scope, stop that operation, continue safe read-only analysis where useful, and report the additional authorization needed.
- Discovering an unrelated defect does not authorize changing it.
- Historical authorizations remain limited to their original conditions:
  - **Local Git commit (2026-10-13):** authorized after the documented acceptance and staging-review workflow. Follow **docs/git-commit-workflow.md**. This does not authorize push, PR, tag changes, deployment, or history rewriting.
  - **Docker startup (2026-10-28):** authorized on demand for the installed Docker Desktop and existing ZhkuAgent containers. Follow **docs/local-docker-readiness.md**. This does not authorize privilege escalation, volume deletion, database replacement, or credential reset.
- Real Probe, Live, batch model execution, production/shared database writes, destructive Git operations, deployment, and global system configuration changes require the corresponding explicit authorization unless a valid existing authorization clearly covers the exact operation.
- Real Probe authorization must be checked against the approved package identity/SHA, quota, budget, and validity. Preparation or zero-call preflight is not execution authorization.

## 5. Architecture and Code Conventions

### Shared architecture contracts

The following are shared contracts across production, frontend, runtime and evaluation modules:

- Task, Turn and Run identity and lifecycle.
- Session isolation, idempotent submission, cancellation and expiration semantics.
- Agent action protocols, tool names and parameter structures.
- Tool observations, Trace and execution results.
- Evidence identity, source location, citation ownership and applicability.
- Publication gates, error codes and terminal reasons.
- SSE/HTTP request, response and status semantics.
- Evaluation input, collection, grading and report contracts.

Before changing a shared contract:

1. Inspect the existing implementation and relevant design/decision documents.
2. Identify the current contract and the exact proposed behavior change.
3. Identify affected production modules, frontend, tests and Evaluation.
4. Explain backward compatibility and any migration requirements.
5. Define the necessary regression tests and documentation updates.
6. Confirm file ownership and the task's acceptance criteria.

Follow **docs/collaboration/parallel-development-and-integration-v1.md** for shared contract ownership and cross-module coordination.

Do not silently change a shared contract by modifying one module first and notifying other owners afterward. Changes involving authentication, task state machines, concurrency, tool protocols, evidence or publication gates require explicit attention to business semantics.

### Code conventions

- Add concise, accurate Chinese comments at critical business boundaries, design trade-offs and easily misused locations.
- Comments should explain why a design exists or what risk it prevents, not mechanically repeat the code.
- Keep comments synchronized with implementation changes.
- Follow existing module naming, package structure, formatting and test conventions.
- Prefer small, cohesive changes over speculative abstractions or broad refactoring.
- Do not introduce another framework, state-management library, UI library or dependency without a concrete requirement and compatibility assessment.
- Reuse existing runtime, retrieval, evaluation and frontend capabilities wherever possible.

## 6. Protect Code and Workspaces

Before editing, confirm repository root, Worktree, branch, HEAD, staged/unstaged/untracked changes, task scope, file ownership, dependencies, and acceptance criteria.

- Preserve all pre-existing user and Agent changes. Do not assume existing changes belong to the current task.
- Modify only files needed for the authorized task. No unrelated refactoring or opportunistic fixes.
- One active write owner per file; do not write into another Agent's active Worktree.
- Prefer isolated Worktrees for independent parallel implementation. Follow **docs/collaboration/parallel-development-and-integration-v1.md**.
- Use **git worktree list --porcelain** to inspect current registered Worktrees. Do not rely on historical Worktree lists or hardcoded branch snapshots.
- Preserve each Worktree's independent index. Do not infer that reverse staged differences mean the index should be reset or rebuilt.
- Never use destructive cleanup or broad operations as shortcuts, including **git reset --hard**, **git clean**, broad **git restore**, forced checkout/switch, or unreviewed stash restoration.
- Do not alter shared hooks, protected refs, branches, tags, or another Worktree as an incidental operation.
- A Git Worktree is not an isolated runtime environment. Check database, ports, services, processes, browser profiles and temporary resources separately.

## 7. Credentials, Database, Docker, and Windows

### Credentials and database

- Before each database or DashScope operation, re-check the required **ZHKU_DB_PASSWORD** or **DASHSCOPE_API_KEY** in Process → User → Machine environment scopes. Do not reuse observations from an earlier session.
- Process environment values take priority. The existing **infra/.env** is only a compatibility fallback for the explicitly selected existing data directory via **-DataRoot**; never search other Worktrees, backups, or arbitrary credential files.
- Inject available values only into the required child process. Never print, persist, commit, or expose secrets in command arguments, files, logs, reports, screenshots, or responses. Do not ask the user to repeat an already configured accessible key.
- Database write acceptance uses a dedicated temporary test database, never the original shared demonstration database merely because its password is available.
- If credentials are inaccessible, inspect environment inheritance and launcher context, then report the exact boundary. Never fabricate credentials or claim an unperformed provider/database call succeeded.

### Docker

Before Docker-dependent work, use the repository entry point:

~~~powershell
.\knowledge\scripts\ensure-zhku-postgres.cmd -DockerOnly
~~~

Use the same command without **-DockerOnly** only when the existing shared demonstration database is genuinely required. Use **-RepairConfiguration** only for the documented port, SQL bind, non-secret container environment, or restart-policy drift, and only under the recovery conditions in **docs/local-docker-readiness.md**.

- Repair only documented configuration drift and only through the documented repair path.
- Preserve and verify the original named volume identity. If the container/volume identity is uncertain, stop; never create a replacement empty database automatically.
- Recovery completion requires the original volume identity, healthy container, expected loopback port connectivity, and read-only authentication using the original user/database/password. **healthy** alone is insufficient.
- A stopped container with drift must not be automatically rebuilt. Do not use **down -v**, delete volumes, prune, reset passwords, switch global Docker context, connect remote engines, or terminate unrelated processes.
- Docker startup permission does not bypass DSH, Windows ACL, UAC, or host approval. Do not configure global Docker/Desktop startup. On CLI timeout, re-check state read-only because the service may have completed an accepted operation.

### Windows terminal and demo

- Codex-injected PATH may differ from the user's ordinary Windows PowerShell/CMD PATH.
- A previously observed PowerShell 7 candidate is **%USERPROFILE%\.cache\codex-runtimes\codex-primary-runtime\dependencies\native\powershell\pwsh.exe**. Check that the file exists before use; never assume the cache path is permanent.
- Prefer **knowledge\scripts\start-controlled-manual-demo.cmd** for the controlled manual demo. **-CheckEnvironment** checks startup environment only; it does not run database, model, or Maven acceptance.
- Verify user-facing commands in an ordinary user terminal where feasible. Success of **Get-Command pwsh/rg** inside Codex does not prove availability in the user's terminal; remove unnecessary CLI dependencies or provide a verified alternative.
- For Windows PowerShell 5.1 Chinese encoding issues, exit the demo with **q**, then set the current session's **Console.InputEncoding**, **Console.OutputEncoding**, and **$OutputEncoding** to **UTF8Encoding(false)** before relaunching. The demo's inner process must also explicitly use UTF-8 rather than relying on the Agent terminal default. Do not change system language or global encoding. Java **file.encoding** does not repair an already mis-decoded parent terminal.

## 8. Git, Testing, and Evidence

### Git

Follow **docs/git-commit-workflow.md**:

**明确结果 → 验收通过 → 核对暂存差异 → 本地提交 → 记录 SHA → 下一任务**

- Stage only the reviewed explicit file list; never use **git add .** or **git add -A** when unrelated changes exist.
- Before commit, review the complete staged diff, file scope, dependencies, secrets, whitespace, and consistency with the accepted source state.
- Exclude credentials, databases, complete PDFs, build outputs, caches, temporary recovery ZIPs, and unrelated learning/career materials.
- An unaccepted task must not be committed as completed. A local commit does not authorize public upload.
- After commit, verify and record the actual SHA, parent, resulting tree, committed file list, and evidence location.

### Testing and evaluation

- Run the smallest relevant checks first, then expand according to impact. Report actual commands and results; an unexecuted test is never PASS.
- Distinguish compilation, unit, integration, database, browser/E2E, real-model execution, and product acceptance.
- Use statuses accurately: **PASS**, **FAIL**, **PARTIAL**, **BLOCKED**, **NOT_EVALUABLE**. **NOT_EVALUABLE** is not PASS; historical PASS does not prove current behavior; offline fixtures do not prove real-model behavior.
- For Agent Evaluation, follow **docs/agent/eval/chain/README.md** and reuse existing Case, Dataset, Trace, Evidence, Evaluator, and Grader capabilities.
- The Evaluation CI workflow **.github/workflows/agent-eval-chain.yml** validates archived-evidence replay and the existing Grader. It does not perform real Probe or Live execution.
- Real Probe/Live still requires valid authorization. Do not put real external model execution into ordinary CI or silently consume an approved quota.

## 9. Issue Management and Delivery

- GitHub Issues are the project's issue-tracking surface and use the **gh** CLI. Follow **docs/agents/issue-tracker.md**.
- Use the canonical triage vocabulary in **docs/agents/triage-labels.md**. Wayfinding labels are a separate vocabulary; do not confuse or invent labels.
- The repository uses one shared domain context. Follow **docs/agents/domain.md**.
- **docs/decisions/** is the existing engineering decision ledger, separate from **docs/adr/**. Do not create missing glossary/ADR files or rename legacy files during unrelated work. For an explicit domain-modeling or ADR task, follow the domain documentation workflow.
- For every non-trivial task, report actual scope, repository/Worktree/branch/HEAD, changed files, tests actually run, evidence, remaining risks/unverified behavior, and the next action or required authorization.
- Distinguish implementation from verification, offline replay from real execution, and local acceptance from release/product acceptance.

### Documentation maintenance loop

- Before starting a non-trivial task, identify the authoritative documents it can affect, starting from **docs/README.md**; keep documentation updates in the same task batch as the change they describe.
- When behavior, contracts, architecture, decisions, or milestones change, update the affected authoritative documents in the same batch. Do not create another status, index, or overview document when an existing one already serves the purpose.
- Never rewrite historical reports or evidence to match new conclusions; record the update in the applicable current document and preserve the original record.
- When moving or deleting files, first search the old path and file name, classify the references found (navigation links, runtime or test dependencies, historical evidence), update live references and directory indexes, keep historical evidence unchanged, then re-run **scripts/validate-doc-links.ps1** and report unresolved references.
- Before delivery, run **scripts/validate-doc-links.ps1** and report the result, or explain why it was not applicable; do not batch-rewrite historical documents just to make the scan green.
- In the delivery report, list every document changed by the task, or state why no document change was needed.

**Principle: understand the current state, work autonomously within authorization, protect existing changes, verify actual results, and report with evidence.**

## 10. Workspace Layout and Archival

The workspace root is fixed at **D:\alldesk\Projects\ZhkuAgent**; **main/** is the canonical repository and **main/docs/** is the documentation root.

- Create new files and directories only inside the workspace root and inside an existing partition. Never create project directories under **D:\alldesk**, **D:\**, or the user profile. Tool-generated Worktrees are not manual placement, but they must not hold the only copy of anything.
- Partition responsibilities:

  | Directory | Purpose | Git |
  | --- | --- | --- |
  | **main/** | Canonical repository: code, scripts, formal docs | Yes |
  | **worktrees/** | Parallel Worktrees, support junctions, safety backups | No |
  | **support/** | Non-Git tool sources and run scripts | No |
  | **archive/** | Read-only historical archive, YYYY-MM-DD-topic/ | No |
  | **audit/** / **audit-and-temp-archive/** | Audit evidence referenced by committed reports; read-only, never renamed | No |
  | **backups/** / **migration/** / **eval/** | Pre-cleanup backups, migration conclusions, evaluation output | No |

- Inside a task, add content only to existing partitions. If a new top-level directory is genuinely required, first update the partition table in **README-MIGRATION.md** with its purpose, retention period, and references.
- Put temporary artifacts in **main/tmp/** or **main/.scratch/**; remove or archive them when the task ends. Do not leave loose files at the workspace root.
- The **archive/** root must include a **README.md** that indexes every entry with source, date, reason for retention, and whether anything still references it; add a row for each new archive.
- Do not commit personal job-search or learning material; keep it under a dated **archive/** directory.
- Preserve absolute paths inside historical evidence, logs, and audit reports. Update paths only in scripts that are still meant to run.
