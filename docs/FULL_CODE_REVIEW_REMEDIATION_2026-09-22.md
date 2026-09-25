# 老林 AI 应用生成平台整改基线

> 文档性质：基于 `FULL_CODE_REVIEW_2026-09-21.md` 的复核与整改版，不修改原评审文件。
>
> 评审日期：2026-09-22
>
> 结论：当前版本不满足公网发布条件。完成本文档 P0 止血、P1 安全与可靠性整改，并通过验收门禁后，才允许受控灰度。

## 1. 使用方式与结论

本文件是可执行的整改台账，不是对所有架构建议的无条件承诺。每项整改必须先确认适用的部署拓扑、数据模型和产品语义，再执行代码变更。

当前风险结论应理解为“发布风险”而非单个漏洞的 CVSS 分数：在普通登录用户可以触发 AI 代码生成、构建服务与 API/静态预览同源的前提下，风险为 **Critical**；如果先关闭不可信构建、隔离静态域并轮换凭据，风险可降为受控的高风险整改状态。

原报告中的安全主线可信，尤其是宿主机构建、AI 文件工具、原始 HTML、静态源码访问、弱密码和长期会话权限问题；但原报告中部分条目混合了已确认事实、生产配置推断和待动态验证结论，因此不能逐条无条件照搬。

### 1.1 证据等级

| 标记 | 含义 | 执行要求 |
|---|---|---|
| `Confirmed` | 源码或本地命令可以直接证明 | 进入整改台账，并补回归测试 |
| `High confidence` | 根因明确，但需要框架/运行时验证攻击结果 | 先做动态验证，再锁定最终严重性 |
| `Conditional` | 依赖生产域名、Cookie、代理、权限或网络拓扑 | 先补充部署前提，不得写成全环境事实 |
| `Needs dynamic` | 静态分析无法判断是否可利用 | 在隔离环境完成 PoC 或明确关闭入口 |

### 1.2 当前基线

- 后端 Git 基线：`664933c2815f1fb0056faffcbcdb1f22ea7cd85f`。
- 前端 Git 基线：`dfb3576a0287e271c035018b89841df6febbb1c2`。
- 两个工作区均存在未提交或未跟踪文件；原评审文件本身也尚未纳入 Git，整改完成后应提交基线与报告版本。
- 后端验证环境：JDK 21.0.11、Maven 3.9.16。
- 前端验证环境：Node 24.18.0、npm 11.16.0；项目规范要求 Node 22，因此 CI 必须固定 Node 22，不应把本机 Node 24 的结果当作唯一基线。
- 当前前端 `npm run build`、`npm run type-check`、`npm run lint` 失败；后端 App 模块测试 9 项中 4 项失败；截图模块工作区测试 5 项中 4 项因 Mockito Agent 失败。
- 前端 `src/api/`、`src/utils/visualEditor.ts`、`package-lock.json` 和截图测试当前未跟踪；干净 clone 无法复现当前工作区。

## 2. 威胁模型与资产

整改前必须在部署文档中确认以下前提：

| 角色 | 能力 | 主要风险 |
|---|---|---|
| T1 未认证访客 | 访问公开列表、详情、静态接口 | SQL 注入、源码泄露、缓存/限流污染 |
| T2 普通登录用户 | 提交 prompt、调用 AI 工具、触发构建和预览 | 宿主机命令执行、文件越界、费用滥用、XSS |
| T3 被攻破的内部组件 | 访问 Dubbo/Nacos/Redis 或服务间接口 | 横向移动、敏感数据读取、伪造内部请求 |

需要保护的资产包括 JVM 主机及环境变量、AI/COS/数据库/Redis/Nacos 凭据、用户源码和 prompt、聊天历史、管理员会话、生成页面所在浏览器上下文和截图服务网络权限。

## 3. P0 止血项

P0 的目标是先切断攻击链，优先采用可回滚的开关、网络策略和入口关闭措施。独立域、异步 Worker 和对象存储迁移属于后续永久方案，不应被误写成一天内完成的交付承诺。

### P0-01 禁止宿主机执行不可信生成项目

- **来源**：原报告 H-01；证据等级 `High confidence`。
- **证据**：`lin-ai-code-app/src/main/java/com/lin/linaicodeapp/core/builder/VueProjectBuilder.java` 执行 `npm install` 和 `npm run build`；`lin-ai-code-ai/src/main/java/com/lin/linaicodemother/ai/tools/FileWriteTool.java` 允许 AI 写入 `package.json`；Vue prompt 明确要求生成 package scripts。
- **当前止血**：关闭 VUE_PROJECT 生成、构建和部署入口，或仅允许内部管理员在隔离环境调用。
- **永久修复**：一次性非特权沙箱；只挂载项目目录；根文件系统只读；禁用宿主机 socket；限制 CPU、内存、PID、磁盘和执行时长；默认断网；固定模板、依赖 allowlist、lockfile、内部 registry 或离线缓存；构建脚本只能使用服务端固定值。
- **重要约束**：`npm ci --ignore-scripts` 只约束安装阶段，后续 `npm run build` 仍可能执行 package scripts，必须校验并固定 build script，不能把 `ignore-scripts` 当作完整隔离方案。
- **验收**：恶意 `preinstall/install/postinstall` 只能在沙箱内失败或被拒绝；宿主机、项目根外、环境变量、内网地址均不可访问；超时必须终止整个进程组/cgroup，不能只杀 npm 父进程。

### P0-02 封闭 AI 文件工具的根目录边界

- **来源**：原报告 H-02；证据等级 `Confirmed`。
- **证据**：`FileReadTool`、`FileWriteTool`、`FileModifyTool`、`FileDeleteTool`、`FileDirReadTool` 对绝对路径和 `../` 解析后没有统一根目录校验。
- **永久修复**：统一 `root.toAbsolutePath().normalize()`、`root.resolve(input).normalize()`、`startsWith(root)` 校验；拒绝绝对路径、空路径和符号链接逃逸；限制单文件和项目总大小；关键文件采用白名单而非 basename 黑名单。
- **验收**：`/etc/passwd`、`../../outside`、编码变体、Windows 盘符、项目内指向外部的 symlink 全部拒绝，合法项目内读写正常。

### P0-03 关闭匿名源码读取

- **来源**：原报告 H-06；“无鉴权读取生成目录”是 `Confirmed`，目录穿越是 `Needs dynamic`。
- **证据**：`lin-ai-code-app/src/main/java/com/lin/linaicodeapp/controller/StaticResourceController.java` 直接读取 `CODE_OUTPUT_ROOT_DIR`，无所有权校验，也不限于构建产物。
- **当前止血**：关闭静态预览公网入口，或只允许登录所有者访问。
- **永久修复**：预览只读固定产物根目录；Vue 只暴露 `dist`；HTML 只暴露明确白名单文件；采用独立静态域或短时签名 URL；禁止目录、未知扩展名和符号链接；补 `nosniff`、CSP 和缓存策略。
- **说明**：前端当前构造的路径是 `codeGenType_appId`，不是部署 `deployKey`。匿名读取 `package.json`、`src/*` 和配置文件属于确定性泄露；`../` 是否能穿过实际容器路由必须用 MockMvc/真实反向代理验证。

### P0-04 禁止原始 AI HTML 进入主站 DOM

- **来源**：原报告 H-04；证据等级 `Confirmed`。
- **证据**：`frontend/src/components/MarkdownRenderer.vue` 使用 `v-html` 和 Markdown-It `html: true`；AI 内容被写入聊天历史后再次渲染。
- **永久修复**：默认 `html: false`；如果产品必须支持部分 HTML，使用成熟 sanitizer 的明确标签、属性和协议白名单；禁止事件属性、SVG、`javascript:` 和内联脚本；补 CSP。
- **验收**：实时回复、历史消息、工具执行结果中的事件属性、SVG、危险 URL 均只显示为文本，不执行脚本。

### P0-05 轮换工作区与环境凭据

- **来源**：原报告 M-12 不足以覆盖该问题；当前工作区发现 `application-local.yml` 中存在非占位 AI/COS 凭据，且 AI 请求/响应正文日志开启。
- **处理要求**：立即撤销并轮换 AI、COS 及相关访问凭据；检查 IDE、shell history、日志、构建产物、镜像和备份；不要在报告、提交记录或工单中回显具体值。
- **配置修复**：凭据只从 Secret/环境变量注入；生产禁止默认激活 `local` profile；缺少必需 Secret 时启动失败；生产关闭 prompt、源码和 Authorization 正文日志。
- **验收**：`git ls-files`、`git grep --no-index`、`target/`、镜像和部署包均完成扫描；日志脱敏测试通过；轮换后的旧凭据不可用。

### P0-06 消除排序字段注入风险

- **来源**：原报告 H-03；证据等级 `High confidence`。
- **证据**：应用公开列表和管理员列表将客户端 `sortField` 传入 QueryWrapper 字符串 `orderBy`；MyBatis-Flex 当前版本的该重载使用原始列表达式。
- **永久修复**：仅允许固定 API 字段映射到 `QueryColumn`；`sortOrder` 只接受 `ascend/descend`；无效字段返回 400 或使用固定默认排序；默认排序应包含稳定的 `create_time,id`。
- **验收**：`SLEEP(5)`、括号、注释符、逗号等输入均为 400，SQL 中不出现原文；合法字段生成预期 SQL。

## 4. P1 安全与可靠性整改

### P1-01 认证、密码和会话

- 使用 Argon2id 或 BCrypt 随机盐，旧 MD5 账号登录成功后渐进迁移。
- 登录按账号和可信客户端 IP 双维度限流；失败退避并记录审计事件；后台新用户使用一次性激活令牌或随机临时密码，禁止固定 `12345678`。
- Session 只保存 `userId` 和最小元数据；应用服务每次鉴权加载当前用户，角色变更或封禁立即生效；登录时调用 `changeSessionId()`；注销调用 `invalidate()`。
- Controller 禁止返回 `User` 实体；`/user/get`、内部 Dubbo 接口和 OpenAPI 模型均改用不含密码的 DTO；生产关闭或保护 Knife4j。

### P1-02 CORS、CSRF 和 HTTP 语义

- `allowedOriginPatterns("*") + allowCredentials(true)` 改为环境配置的精确 Origin 白名单；明确 Cookie 的 `HttpOnly`、`Secure`、`SameSite` 和有效期。
- 状态变更统一使用 POST/PUT/DELETE；生成接口不得用 GET 修改数据库、文件或消耗额度；SSE 使用先 POST 创建任务、再用短时 token 订阅，或改用 fetch 流式读取。
- 异常处理映射真实 HTTP 状态码；SSE 在响应提交前使用 HTTP 错误，提交后使用统一 `error`/`done` 协议，不能在业务错误后无条件发送成功语义。
- 登录 redirect 只允许站内相对路径，并使用 `URL` 解析和编码，禁止直接拼接完整 `window.location.href`。

### P1-03 公开数据与授权契约

- 增加明确的 `visibility/published` 模型；匿名详情只返回 Public DTO，不返回 `initPrompt`、`deployKey`、内部 `userId` 或其他非公开元数据。
- `/user/get/vo` 是否公开必须由产品确认；即使公开，也只能返回最小 PublicUserVO，不能通过同类 Controller 自调用绕过安全注解。
- 应用聊天历史、下载、部署和编辑接口统一校验所有权或管理员角色；权限测试覆盖降权、删除、封禁和跨用户 ID。

### P1-04 数据一致性与部署发布

- 注册使用数据库唯一约束和重复键异常映射；仓库引入 Flyway/Liquibase，不能依赖本机 IDE 数据源中的不可审计 schema。
- 删除 App 使用事务或可靠的删除工作流，失败不得继续返回成功；同时清理聊天历史、生成目录、部署目录和 COS 封面，并建立生命周期清理任务。
- 部署使用至少 128 bit 随机标识；复制到临时目录、校验完成后原子 rename/swap；清理旧资源；唯一键冲突必须在覆盖目录前处理并重试。
- 生成目录按 `codeType_appId` 复用时先清理或写入临时版本目录，避免旧的 `style.css/script.js/index` 混入新版本。

### P1-05 分页、限流、缓存和任务边界

- 统一 `pageNum >= 1`，普通接口 `1..20`，管理员接口按资源设置上限；管理员聊天历史入口也必须校验上限。
- 限流先完成参数、存在性和所有权校验，再按 `userId:appId` 限流；只信任受控反向代理注入的地址，网关应剥离外部伪造的转发头；任意 appId 不得无限创建 Redis 限流键。
- 游标使用 `(createTime,id)`，排序与条件保持一致；缓存 key 使用归一化后的参数，更新/删除时失效，或在没有测量收益时直接移除缓存。
- 生成、构建和截图任务使用有界队列、每 App 单飞锁、全局并发上限、取消和重试状态；截图虚拟线程必须捕获异常并可观测。

### P1-06 前端交付与 ID 契约

- 修复 `vite.config.ts` 语法残片、类型错误和 lint 错误；CI 使用只读 lint 检查，禁止把 `eslint --fix` 作为门禁命令。
- 将 `src/api/`、`src/utils/visualEditor.ts` 和 `package-lock.json` 纳入版本控制，或在 CI 中明确生成并校验版本；干净 clone 必须能执行 `npm ci`。
- 固定 Node 22、npm 版本和后端 JDK/Maven；修正 Vite proxy、API 默认端口和 OpenAPI schema 的服务边界，不允许 `8123/8124/8125/8080` 依赖隐式本机配置。
- Java Snowflake `Long` ID 在 OpenAPI/TypeScript 中统一按字符串传输；补充大于 `2^53-1` 的契约测试。
- 修复聊天管理员消息枚举、假删除、登录 redirect 和事件监听器/ EventSource 清理。路由大小写不一致作为命名规范问题处理，不再断言当前一定跳转失败。
- 前端当前没有 Vitest、Playwright 或 Cypress 测试运行器；安全回归和 E2E 验收必须先引入测试基础设施，或明确登记为人工验收，不能把尚不存在的 E2E 结果当作 CI 门禁。

### P1-07 生成页面与主站隔离

- 生成内容迁移到不持有主站 Cookie 的独立静态域；API 域、主站域和生成域的 Cookie、CORS、CSP 边界写入部署基线。
- iframe 使用最小权限 `sandbox="allow-scripts"`，不添加 `allow-same-origin`；可视化编辑改为带随机 nonce 的受控桥接，不直接注入任意 iframe DOM。
- `message` 事件必须校验 `event.source`、`event.origin`、nonce 和运行时 schema；发送消息使用精确 `targetOrigin`，不得依赖 `*`。
- **验收**：生成页面不能读取主站 DOM、读取 API 会话响应、导航顶层窗口或伪造其他 iframe 消息；合法元素选择和编辑功能在新桥接协议下仍可用。

## 5. P2 架构演进

以下项目应按规模、SLO、并发和成本触发，不作为当前小规模系统的立即发布门槛：

1. **异步生成 Worker**：API 返回 `taskId`，Worker 执行 AI、构建和制品发布；状态存储 `QUEUED/RUNNING/SUCCEEDED/FAILED/CANCELLED`。
2. **隔离构建与制品存储**：构建在沙箱完成，产物进入版本化对象存储和独立静态域；支持回滚、清理和多实例访问。
3. **统一认证授权**：先统一 principal、Session 和 DTO，再评估 Spring Security、网关和 Dubbo mTLS；不得把架构重写与 P0 止血绑定。
4. **数据库迁移与索引**：先清洗重复数据并用 `EXPLAIN ANALYZE` 建立基线，再添加唯一索引和复合索引；本机现有索引不能替代迁移文件。
5. **截图服务隔离**：恢复 Chrome sandbox；固定 ChromeDriver 和依赖，禁止运行时联网下载；每任务独立浏览器上下文，限制跳转协议、域名、重定向、网络和时长。
6. **依赖与供应链治理**：父 POM 只保留 dependency management；拆分 common 的 COS/codegen 依赖；生成 SBOM，固定 lockfile、内部 registry、许可证和漏洞门禁。
7. **可观测性**：Actuator 受保护，统一 traceId；采集 API、DB、Redis、Dubbo、AI token、构建耗时、队列长度、磁盘和截图失败率。

## 6. 验收门禁

### 6.1 安全回归

- 恶意 lifecycle 无法在宿主机、项目根外或沙箱外创建文件。
- 所有绝对路径、`../`、编码变体和 symlink 逃逸请求均被拒绝。
- 匿名无法读取 `package.json`、`src/*`、配置文件和历史版本制品。
- XSS payload 在实时消息、历史消息和工具结果中均不执行。
- 降权、封禁、删除用户后，旧 Session 下一请求立即失效。
- 非白名单 Origin、缺失 CSRF token、错误 redirect 和伪造转发头均按预期拒绝。
- SQL 排序注入输入返回 400，且 SQL 日志没有原始输入。

### 6.2 工程门禁

在干净 clone、固定 Node 22/JDK 21 环境执行：

```bash
npm ci
npm run type-check
npx eslint .
npm run build
mvn clean verify
```

连续两次通过后才允许灰度。CI 必须确认没有依赖 IDE、本机未跟踪文件、`target/`、`tmp/` 或本地 profile 才能构建成功。

### 6.3 发布门槛

- P0 全部关闭或有经过评审的临时隔离措施。
- P1 安全项、权限回归、数据库迁移和工程门禁完成。
- P2 架构项可以延期，但必须有明确触发条件、风险接受人和时间表。
- 生产拓扑、Cookie 属性、静态域、反向代理可信边界和内部 RPC 网络策略已记录并经过动态验证。

## 7. 责任与变更记录建议

每条整改记录至少包含：负责人、目标版本、代码变更、数据库/配置变更、回滚方案、验证命令、动态测试证据和残余风险。安全项与架构演进项分开跟踪；“删除死代码”和依赖瘦身只能在构建门禁恢复后执行。

本文件只新增整改基线，未修改原评审文件或业务代码。
