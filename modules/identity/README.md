# 账号与权限（模块 A）

注册、登录、退出、当前用户、角色分配、实验室类别分配和 Spring AOP 接口权限校验。负责用户、角色、权限、用户角色、角色权限与实验室类别六张表，并验证正常授权与越权场景。

## 交付物

- [建表与初始化 SQL](../../db/init/002-identity.sql)（在 D 的 `001-platform.sql` 之后执行；`docs/deploy.md` 初始化段落需追加一行 `SOURCE db/init/002-identity.sql`，已在本 PR 说明中知会 D）
- 模块代码 `src/main/java/edu/course/lab/cms/identity/`（entity / mapper / service / controller / security / session / dto）
- 测试 `src/test/java/edu/course/lab/cms/identity/`（单元 + MockMvc + 真实 MySQL 集成）
- 本文档（含权限矩阵与验证记录）

## 表结构

| 表 | 说明 | 关键约束 |
| --- | --- | --- |
| `lab_type` | 实验室类别（控制定向通报阅读范围） | `code` 唯一 |
| `sys_user` | 账户 | `username` 唯一（含已删除行，用户名不可复用）；`deleted` 逻辑删除；外键到 `lab_type` |
| `sys_role` | 角色 | `code` 唯一 |
| `sys_permission` | 权限标识 | `code` 唯一 |
| `sys_user_role` | 用户角色绑定 | 复合主键，双向外键 |
| `sys_role_permission` | 角色权限绑定 | 复合主键，双向外键 |

时间字段由数据库 `DEFAULT CURRENT_TIMESTAMP(6)` / `ON UPDATE CURRENT_TIMESTAMP(6)` 维护；应用连接统一 `time_zone='+00:00'`，与共享持久层约定一致。软删除过滤在手写 SQL 中显式书写 `AND deleted = 0`。

## 权限矩阵

| 权限标识 | READER | AUTHOR | REVIEWER | ADMIN |
| --- | --- | --- | --- | --- |
| `user:manage`（账号、角色、类别维护） | | | | ✔ |
| `category:manage`（B 的分类管理） | | | | ✔ |
| `article:create`（C 的新建编辑） | | ✔ | | |
| `article:submit`（C 的提交审核） | | ✔ | | |
| `article:review`（C 的审核） | | | ✔ | |
| `article:publish`（C 的发布） | | ✔ | | |
| `audit.read`（D 的审计查询，标识含点号，与 D 的 `AuditController` 硬编码对齐） | | | | ✔ |

权限标识常量见 `IdentityPermissions`，与 B、C、D 按 [shared-contract.md](../../docs/shared-contract.md) 对齐。

## 对外接口

读者/作者/审核员/管理员通用（登录态由 JSESSIONID 会话承载）：

| 方法与路径 | 说明 | 未登录 | 其他失败 |
| --- | --- | --- | --- |
| `POST /api/auth/register` | 注册（默认绑定读者角色，实验室类别为空，注册请求无法指定角色） | 允许 | 409 `USERNAME_TAKEN`；参数校验 400 `VALIDATION_FAILED` |
| `POST /api/auth/login` | 登录，建立会话（轮换会话 ID 防会话固定），密码 BCrypt 校验 | 允许 | 401 `INVALID_CREDENTIALS`（用户不存在/停用/密码错统一口径） |
| `POST /api/auth/logout` | 退出并使会话失效 | 允许（幂等返回 OK） | — |
| `GET /api/auth/me` | 当前用户（id、用户名、昵称、实验室类别、角色、权限集合） | 401 `UNAUTHENTICATED` | — |

管理端（Service 方法上 `@RequiresPermission("user:manage")`，AOP 统一校验）：

| 方法与路径 | 说明 | 未登录 | 无权限 |
| --- | --- | --- | --- |
| `GET /api/admin/users?keyword=&page=&size=` | 用户分页查询（可选用户名/昵称模糊过滤） | 401 | 403 `FORBIDDEN` |
| `PUT /api/admin/users/{id}/roles` | 覆盖式角色分配（body `{"roleIds":[...]}`），每个被授予角色随业务事务写一条 `ASSIGN_ROLE` 成功审计 | 401 | 403 |
| `PUT /api/admin/users/{id}/lab-type` | 分配/清除实验室类别（body `{"labTypeId":n}` 或 `null`） | 401 | 403 |
| `GET /api/admin/lab-types` | 实验室类别列表 | 401 | 403 |

## 给其他模块的接口

- `CurrentUserProvider`（platform 定义）：`SessionCurrentUserProvider` 已实现并注册为 Bean。会话只存 `userId`，角色/权限/实验室类别每次请求实时查询——管理员调整角色或类别后**立即生效，无需重新登录**。匿名或会话失效返回空，使用者按 401 处理。
- `CurrentUser`（`userId`、`labTypeId`、`permissions`）：B 可用 `user:manage` 判定分类管理权限；C 用 `permissions` 判定 `article:*`，用 `labTypeId` 过滤定向通报可见范围。
- `LabTypeService.isValid(Long labTypeId)` / `requireValid(Long)`：C 发布定向通报前校验目标实验室类别有效性（`null` 视为未分配，合法）。
- 审计接入：`UserAdminService.assignRoles` 已在业务事务内调用 `AuditService.recordSuccess(actorId, ASSIGN_ROLE, USER, userId)`，回滚时审计随业务一起回滚（集成测试已验证）。

## 设计说明与已知坑

- **鉴权切面**：`@RequiresPermission` 注解加在 Service 等 Spring Bean 的公开方法上，`PermissionAspect` 在方法调用前校验：无会话抛 401 `UNAUTHENTICATED`，缺权限抛 403 `FORBIDDEN`，与 D 的全局异常处理对齐。同类内部自调用不经过代理，不会生效（约定跨模块走 Controller → Service）。
- **`@Around` 必须写 `argNames`**：未用 `-parameters` 编译时反射拿不到参数名，`@annotation(x)` 的注解绑定会抛 `Required to bind 2 arguments, but only bound 1`。已显式声明 `argNames = "joinPoint,requiresPermission"`，并对整个模块开启 `maven.compiler.parameters`。
- **不要在切面类上加 `@Order`**：在 Spring 6.2 实测会破坏上述注解绑定（最小复现可稳定重现）；切面顺序交由默认规则即可。
- **路径变量显式命名**：`@PathVariable("userId")` 需写全参数名（父工程未开 `-parameters`；本模块已自行开启，但显式命名更稳）。
- **BCrypt**：`spring-security-crypto` 的 `BCryptPasswordEncoder`，只引入加密工具包，不引入完整 Security 框架；密码哈希只落库，接口与审计均不输出。
- 注册并发兜底：先查重再插入，命中唯一索引抛 `DuplicateKeyException` 转 409。
- `sys_user` 主键回填：行记录使用 Java record，MyBatis 无法对不可变对象执行 `useGeneratedKeys` 回填，改为插入后按唯一用户名查询 ID。

## 验证记录（2026-11-27，本机 JDK 21 / MySQL 8.0.29 / Tomcat 10.1.55）

- 单元测试 26 项全部通过（JUnit 5）：BCrypt 编码与随机盐、AOP 代理 401/403/放行、注册参数校验、登录建立会话与错误口令 401、退出失效、me 的匿名 401、管理端分页与请求体/路径校验。
- 真实 MySQL 集成测试（`CMS_VERIFY_MYSQL=true`，`IdentityMySqlTest`）通过：种子账号登录、注册默认读者与唯一约束、管理员权限解析（含 D 的 `audit.read`）、类别有效性、AOP 代理 401、角色分配审计与业务同事务、无效角色整体回滚且审计不落库、类别调整后当前用户实时生效。
- D 的 `AuditMySqlTest` 在本模块加入后复跑通过。
- WAR 部署 Tomcat 10.1.55 端到端 HTTP 验证通过：匿名 401、错误口令 401、作者登录/me、作者查审计与访问管理端均 403、管理员查审计 200、角色分配后权限即时生效、`ASSIGN_ROLE` 审计可查、类别调整即时生效、注册默认读者、重复注册 409、退出后 401。
- Windows 下用 `mysql` 客户端导入 SQL 需 `--default-character-set=utf8mb4`（客户端默认 gbk 会把 UTF-8 中文字节误判为超长，报 `Data too long`）。

## PR 影响说明（知会 D）

1. `docs/deploy.md` 初始化段落追加 `SOURCE db/init/002-identity.sql`（按账户与分类 → 文章与审核 → 日志顺序中的第一段）。
2. 联调待办可勾掉：「与 A 确认 `CurrentUserProvider` 会话实现、管理员 `audit.read` 权限」——两者已就绪并实测。
3. `cms-identity` 新增依赖：`spring-security-crypto`（BCrypt）、`aspectjweaver`（切面，与 cms-app 同版本 1.9.25）。
