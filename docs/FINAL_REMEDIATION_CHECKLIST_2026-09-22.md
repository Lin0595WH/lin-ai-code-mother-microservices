# 最终整改执行清单

> 配套基线：[`FINAL_OPTIMIZATION_REMEDIATION_2026-09-22.md`](./FINAL_OPTIMIZATION_REMEDIATION_2026-09-22.md)
>
> 使用规则：每项只有在代码/配置已经合并、验证命令已执行、证据已归档后才能勾选。历史评审中的“建议”或本机未跟踪文件不算完成。

## 1. 状态与责任

| 字段 | 约定 |
|---|---|
| `Owner` | 填具体人或团队，不填“后端/前端”这种无人负责的角色 |
| `Target` | 填版本/发布日期；没有日期的条目不能进入完成状态 |
| `Evidence` | 代码 diff、配置/迁移 diff、命令退出码、动态 PoC、回滚记录 |
| `Blocked` | 只能写外部依赖或产品决定，并同时记录临时隔离措施 |
| 完成定义 | P0/P1 代码与动态验证通过，干净 clone 门禁通过，残余风险有明确接受人 |

当前默认状态：除“现状验证”外，所有整改项均为未完成。

## 2. 现状验证记录

- [x] 已确认后端基线 `664933c2815f1fb0056faffcbcdb1f22ea7cd85f`。
- [x] 已确认前端基线 `dfb3576a0287e271c035018b89841df6febbb1c2`。
- [x] 已复现前端 `npm run build`、`npm run type-check`、`npx eslint . --no-fix` 失败。
- [x] 已复现 `mvn -pl lin-ai-code-app -am test` 为 9 项中 4 项失败。
- [x] 当前工作树截图测试 5/5 通过，但测试文件未跟踪，不能作为干净 clone 证据。
- [x] 已确认服务 Jar 没有 `BOOT-INF`/`Main-Class`。
- [x] 已发现 ignored 本地配置中的看似真实凭据；具体值不写入工单或提交。

## 3. P0 止血清单

### P0-01 不可信构建与文件工具

| 完成 | Owner | Target | 必交付 | 验收证据 |
|---|---|---|---|---|
| [ ] |  |  | 关闭 VUE_PROJECT 公网生成/构建/部署开关，并记录恢复条件 | 未授权请求得到明确 403/业务码；网关/配置截图或导出 |
| [ ] |  |  | `ProjectPathResolver` 统一覆盖 Read/Write/Modify/Delete/DirRead | 参数化测试覆盖绝对路径、`..`、编码、盘符、symlink |
| [ ] |  |  | 构建迁移非特权、临时、默认断网沙箱；固定依赖、脚本和 lockfile | 恶意 lifecycle PoC 在宿主机和项目根外无文件；超时清理 cgroup |
| [ ] |  |  | 单文件/项目总大小、文件数、扩展名和执行时限有硬上限 | 超限请求 4xx；内存/CPU/PID/磁盘达到上限时任务可终止 |

### P0-02 预览、生成域和浏览器隔离

| 完成 | Owner | Target | 必交付 | 验收证据 |
|---|---|---|---|---|
| [ ] |  |  | 静态接口暂时关闭公网源码访问 | 匿名请求 `package.json`/`src/*`/配置返回 403/404 |
| [ ] |  |  | 预览根只允许 `dist`/固定 HTML 产物；canonical、symlink、扩展名校验 | 路径矩阵测试和反向代理真实请求 |
| [ ] |  |  | owner 或短时签名 URL；预览域不共享 API Cookie | 跨用户 ID/过期签名均失败 |
| [ ] |  |  | 生成域与主站分离；iframe 最小 `sandbox="allow-scripts"`，不加 `allow-same-origin` | 生成 JS 无法读父 DOM、主站 API 响应或导航顶层窗口 |
| [ ] |  |  | message bridge 校验 `source`、`origin`、nonce、schema，发送使用精确 `targetOrigin` | 伪造消息不改变编辑状态；合法元素选择通过 |

### P0-03 内容安全与凭据

| 完成 | Owner | Target | 必交付 | 验收证据 |
|---|---|---|---|---|
| [ ] |  |  | Markdown-It `html:false`，或完成最小 sanitizer 白名单 | 实时/历史/工具结果 XSS payload 均不执行 |
| [ ] |  |  | 主站和生成域设置 CSP、`nosniff`、`Referrer-Policy` | 浏览器响应头检查；内联脚本/危险协议被拒 |
| [ ] |  |  | 撤销并轮换 AI/COS/数据库/Nacos/Redis 凭据；删除 tracked `root/root`、`nacos/nacos` 默认值 | 旧凭据尝试失败；不得在 CI 输出值；默认值不能用于任何非本机环境 |
| [ ] |  |  | 生产关闭 prompt、源码、Authorization 正文日志；浏览器端只记录 traceId/错误码 | 脱敏日志测试；服务端和浏览器日志 grep 无密钥、prompt、源码 |
| [ ] |  |  | 将 `application-local.yml` 移出 resources/构建上下文，生产不默认激活 local；扫描 `target/classes` 和 Jar | `jar tf`/secret scan 无凭据；缺 Secret 启动失败 |
| [ ] |  |  | Redis cache/memory 使用独立 ACL/TLS、显式 key prefix 和明确 DTO/类型白名单 | 低权限账号不能跨 namespace 读写或触发任意类型反序列化；TTL/隔离策略有运行证据 |

### P0-04 查询注入与跨站写

| 完成 | Owner | Target | 必交付 | 验收证据 |
|---|---|---|---|---|
| [ ] |  |  | App/User sortField 改为固定 QueryColumn 白名单 | `SLEEP(5)`、注释、括号、逗号返回 400；SQL 无原文 |
| [ ] |  |  | CORS 使用配置化精确 Origin，不允许 wildcard + credentials | 非白名单预检无 CORS；白名单仅 HTTPS |
| [ ] |  |  | 写操作 CSRF 防护；Cookie 设置 HttpOnly/Secure/SameSite/短期 | 无 token 写请求 403；跨站表单不能创建/删除/生成 |
| [ ] |  |  | 生成从 GET query 改 POST body + fetch 流或短时 token SSE | 访问日志、Referer、缓存键中不出现 prompt |

## 4. P1 安全与可靠性清单

### P1-01 认证、会话与 DTO

- [ ] 使用 Argon2id/BCrypt 随机盐；旧 MD5 登录成功后渐进迁移；同密码产生不同哈希。
- [ ] 登录按账号和可信代理 IP 限流、失败退避并记录审计；后台新用户不再使用固定临时密码。
- [ ] Session 只保存 `userId`/最小元数据；登录调用 `changeSessionId()`，注销调用 `invalidate()`。
- [ ] App 鉴权每次加载当前用户，角色变更/封禁/删除在下一请求生效。
- [ ] 明确 `status/disabled` 与角色 enum 契约；若不支持封禁，删除 `ban` 注释/分支；管理员不能写任意角色字符串，角色变更有审计。
- [ ] Controller、Dubbo、OpenAPI 均不返回 `User` 实体或 `userPassword`。
- [ ] 匿名 app/user 详情使用 Public DTO 和明确 published/owner 策略；不返回 `initPrompt`、`deployKey`、内部 ID。

### P1-02 数据一致性、迁移与部署

- [ ] 增加可审计迁移：`user_account UNIQUE`、`deploy_key UNIQUE`、聊天游标和常用查询复合索引。
- [ ] 注册重复键映射为明确业务错误；并发注册测试 20 次只成功 1 次。
- [ ] 删除 App 使用事务或可靠清理工作流；聊天、代码目录、部署目录、COS 资源没有孤儿。
- [ ] deploy key 使用至少 128 bit 随机值；临时目录校验后原子 swap，重部署清理旧文件。
- [ ] 生成写入使用临时版本目录和每 app 单飞；空 CSS/JS 也会清理旧版本文件，不会混入旧产物。
- [ ] 统一 `CODE_OUTPUT_ROOT_DIR`/`CODE_DEPLOY_ROOT_DIR` 与 URL contract；部署成功后执行真实 GET smoke test。
- [ ] 失败构建/保存传播错误，只有产物校验通过才发送 `done`；SSE 错误事件不会再发送成功语义。
- [ ] 部署/生成状态以服务端 ready 事件或有上限的轮询为准；前端不以固定 sleep 判定成功。

### P1-03 分页、上下文、限流和任务边界

- [ ] 所有 DTO 使用服务端 `@Valid`；普通接口 page size `1..20`，管理接口按资源设定上限。
- [ ] 聊天游标使用 `(createTime,id)`，排序与条件一致；记忆加载使用 `.limit(maxCount)` 而不是跳过最新记录。
- [ ] 限流在认证/存在性/所有权检查之后执行，key 至少包含 `userId:appId`；只信任受控代理头。
- [ ] 无命中率/P95 证据的精选缓存删除；保留缓存时 key 只含归一化参数并有更新失效。
- [ ] 每 app 单飞、全局有界队列和并发上限；AI memory 按调用隔离；客户端断开能取消模型/构建进程。
- [ ] `MessageWindowChatMemory` 不在多个并发 SSE 间共享可变实例；同一 app 串行化或使用带版本的 Redis CAS/队列，跨请求 key 有 namespace。
- [ ] prompt/message 有请求大小、token、文件数和日预算上限；额度预检早于模型调用。

### P1-04 前端工程与契约

- [ ] 修复 `vite.config.ts` 语法、`UserManagePage.vue` ID 类型和全部 lint 错误。
- [ ] 将 `src/api/`、`src/utils/visualEditor.ts`、`package-lock.json` 纳入版本控制；用干净 clone 验证。
- [ ] 直接声明实际 import 的 `dayjs`、`@ant-design/icons-vue`；不依赖传递依赖偶然提升。
- [ ] 固定 Node 22/npm 版本；生产缺少 `VITE_API_BASE_URL`/`VITE_DEPLOY_DOMAIN` 时构建失败，不静默回落到 localhost。
- [ ] 生产和浏览器运行时统一请求 Higress `:8080`；验证网关按 API path 将请求路由至 Nacos 注册的 user/app 服务。Vite 开发 proxy 只转发到该统一入口，不承担服务发现；验证 Cookie、CORS/CSRF、SSE 和各服务路径。
- [ ] 按 user/app 服务分别生成 OpenAPI，或使用网关聚合 schema；生成期 schema URL 与浏览器运行时统一请求 `:8080` 分开管理。修正 8123/8124/8125/8080 混用、错误 `static/**` 生成函数和 SSE 假类型。
- [ ] Snowflake ID 在 OpenAPI/TypeScript/路由中统一为 string；补 `>2^53-1` 契约测试。
- [ ] 干净 clone 可由受控脚本生成或获取 `src/api/`、`src/utils/visualEditor.ts` 和 lockfile；生产构建不读取未跟踪文件、IDE、`target/`、`tmp/` 或 local profile，并通过 secret/SBOM 扫描。
- [ ] 修复管理员 `ai` 筛选、假删除、登录 redirect、路由大小写；部署按钮按 owner/admin 禁用但仍依赖后端鉴权。
- [ ] EventSource/timeout/message listener 在 `onUnmounted` 清理；网络断开不能按 `CONNECTING` 伪装成功。
- [ ] `window.open` 使用 `noopener,noreferrer`；历史消息使用稳定 ID 作为 Vue key；加载更多期间不能改变正在生成消息的目标。
- [ ] 服务端原始 `message`、SSE `event.data` 和完整 URL 不直接展示或写入浏览器日志；统一映射稳定错误码/traceId。
- [ ] 前端 request 层统一处理真实 HTTP `401/403/429/5xx`（而非只识别 2xx 业务码）；401 清理会话并安全跳转，429/5xx 可重试且不泄露响应正文。
- [ ] 路由参数切换时取消旧流、重置状态并重新加载；预览 iframe 在真实 `load` 前不标记 ready，生成版本变化时强制刷新。

### P1-05 内部服务、截图与打包

- [ ] Dubbo/Nacos 只在内部网络可达，启用 token/TLS/mTLS 或等价服务鉴权。
- [ ] 内部 RPC 使用最小 DTO；截图接口不接受任意 URL，拒绝私网、metadata、`file:`、`data:`、`javascript:` 和未签名重定向。
- [ ] Chrome 恢复 sandbox，固定 driver/浏览器版本，不在运行时联网下载；每租户/任务隔离浏览器状态。
- [ ] 为运行服务声明 Spring Boot Maven plugin；`java -jar` 和 `BOOT-INF` 检查通过。
- [ ] 父 POM 只做 dependency management；common 不再向模型/客户端传递 COS/codegen 等无关依赖。

### P1-06 账户滥用与发布契约

- [ ] 注册按账号、可信 IP、设备/挑战和全局成本预算限流；重复点击有服务端幂等/速率保护，不能只依赖前端 loading。
- [ ] 公开精选卡的 `view` 链路与聊天历史权限一致：移除伪公开入口，或提供明确的 PublicPreview DTO/只读策略，不用 query 参数绕过鉴权。
- [ ] `R1-11`、`R1-12` 的代码 diff、迁移、clean-clone 和角色/封禁动态证据均已归档。

## 5. P2 触发式优化

以下项目只有在出现对应信号时才立项；没有信号就不增加系统复杂度。

| 触发信号 | 最小演进 | 退出/验收 |
|---|---|---|
| 单次生成占用 HTTP/SSE 超时或并发超过 worker 容量 | taskId + 有界 Worker + 状态机 | 可取消/重试，队列和失败率有指标 |
| 多实例/重启导致本地制品不可见 | 版本化对象存储 + 独立静态域/CDN | 任意实例可预览、回滚、清理 |
| 截图 P95 或队列超预算 | 有界 browser worker/context + 出站策略 | 任务隔离，资源有硬上限 |
| 依赖/CVE/镜像体积成为发布阻断 | common/POM 拆分 + SBOM/registry lock | 依赖树与许可证有 CI 报告 |
| 无法定位 AI/DB/构建慢请求 | Actuator + traceId + token/队列/磁盘指标 | 有仪表盘、告警和容量基线 |
| 首屏 bundle、聊天渲染或历史内存超过预算 | 路由懒加载、组件按需引入、按语言 highlight、历史窗口化/虚拟列表 | 有 bundle/交互延迟/内存基线，优化后无功能回归 |

## 6. 关闭标准与签字

- [ ] 所有 P0 条目关闭，或有已评审的入口关闭/网络隔离补偿控制。
- [ ] 所有 P1 条目有代码 diff、动态验证和回滚记录。
- [ ] 后端 `mvn clean verify` 连续两次通过。
- [ ] 前端 Node 22 下 `npm ci`、type-check、lint、build 连续两次通过。
- [ ] 干净 clone 不依赖 IDE、本机 `target/`、`tmp/`、未跟踪生成文件或 local profile。
- [ ] Secret、Cookie、静态域、代理可信边界和内部 RPC 网络策略已写入部署基线。
- [ ] 残余风险有接受人、到期日和补偿控制；没有用“后续优化”替代 P0 止血。

| 角色 | 姓名 | 日期 | 签字/链接 |
|---|---|---|---|
| 产品 |  |  |  |
| 安全 |  |  |  |
| 后端 |  |  |  |
| 前端 |  |  |  |
| 运维 |  |  |  |

## 7. 历史 ID 映射

| 最终 ID | 历史来源 |
|---|---|
| `R0-01` | H-01 |
| `R0-02` | H-02 |
| `R0-03` | H-06 |
| `R0-04` | H-05 |
| `R0-05` | H-04 |
| `R0-06` | H-03 |
| `R0-07` | M-12 + 本次凭据复核 |
| `R0-08` | H-07/H-08/H-09 |
| `R0-09` | 本次内部 RPC/截图复核 |
| `R1-01` | M-01/M-13 |
| `R1-02` | M-03/M-04 |
| `R1-03` | M-02 |
| `R1-04` | M-05/M-08 + 本次上下文复核 |
| `R1-05` | M-06/M-07 |
| `R1-06` | M-09/M-10/M-11 + 本次并发复核 |
| `R1-07` | M-14/M-15/L-03/L-04 + 本次前端基线复核 |
| `R1-08` | 本次打包/依赖复核 |
| `R1-09` | 本次输入/ID/成本复核 |
| `R1-10` | 本次部署 URL/制品根复核 |
| `R1-11` | 本次工作树/供应链复核 |
| `R1-12` | 本次角色/封禁契约复核 |
