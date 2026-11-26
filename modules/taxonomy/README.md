# 内容分类（模块 B）

模块 B 负责 `cms_category` 分类表，让后台维护多级分类、读者与文章共用同一套分类。本模块同时提供面向模块 C 的分类查询，以及面向模块 D 的审计调用。

## 交付

- [分类表与演示数据 SQL](../../db/init/002-taxonomy.sql)
- 分类服务：`edu.course.lab.cms.taxonomy.service`（`CategoryService` 实现 `CategoryQueryService`、`CategoryAdminService`）
- 树逻辑：`edu.course.lab.cms.taxonomy.support.CategoryTree`
- 接口：`CategoryController`（读者树）、`AdminCategoryController`（后台维护）
- 自测：[CategoryTreeTest](../../cms-app/src/test/java/edu/course/lab/cms/taxonomy/CategoryTreeTest.java)、[CategoryControllerTest](../../cms-app/src/test/java/edu/course/lab/cms/taxonomy/CategoryControllerTest.java)、[CategoryMySqlTest](../../cms-app/src/test/java/edu/course/lab/cms/taxonomy/CategoryMySqlTest.java)
- [HTTP 验收记录](verification.log)

测试类放在 `cms-app` 的测试源码里，与 `AuditControllerTest`、`AuditMySqlTest` 的做法一致，因为只有该模块装配了 `spring-test`、`hibernate-validator` 和 JDBC 驱动。按功能包名 `edu.course.lab.cms.taxonomy` 组织，便于与 `modules/taxonomy` 对应。

## 表结构

`cms_category`：`id`、`parent_id`、`name`、`sort_order`、`enabled`、`created_at`、`updated_at`。

- 根分类的 `parent_id` 固定为 `0`，不做自引用外键：根节点没有父行，外键会拒收；层级合法性由 Service 校验。
- `uk_category_parent_name (parent_id, name)` 保证同级重名被数据库拦截，Service 先做一次可读的检查，冲突时返回 `CATEGORY_NAME_DUPLICATED`。
- `idx_category_parent_sort (parent_id, sort_order, id)` 支撑树查询与排序。
- 演示数据：安全制度、培训指引、安全通报三个根分类，加 6 个子分类，ID 固定便于文章引用。

## 接口

| 方法 | 路径 | 权限 | 说明 |
| --- | --- | --- | --- |
| GET | `/api/categories/tree` | 匿名 | 读者树，只含有效分类 |
| GET | `/api/categories/tree?includeDisabled=true` | `category.manage` | 管理端树，含停用节点 |
| GET | `/api/admin/categories` | `category.manage` | 平铺列表，默认含停用节点 |
| POST | `/api/admin/categories` | `category.manage` | 新增，`parentId` 省略即挂根 |
| PUT | `/api/admin/categories/{id}` | `category.manage` | 改名、换父级、改排序 |
| PUT | `/api/admin/categories/{id}/enabled` | `category.manage` | 启停，写审计 |
| PUT | `/api/admin/categories/sort` | `category.manage` | 同一父级下批量排序 |
| DELETE | `/api/admin/categories/{id}` | `category.manage` | 删除 |

树查询示例（`GET /api/categories/tree`，节选）：

```json
{"code":"OK","message":"成功","data":[
  {"id":3,"parentId":0,"name":"安全通报","sortOrder":30,"enabled":true,
   "children":[
     {"id":105,"parentId":3,"name":"化学类通报","sortOrder":10,"enabled":true,"children":[]},
     {"id":106,"parentId":3,"name":"机电类通报","sortOrder":20,"enabled":true,"children":[]}
   ]}
]}
```

错误码：

- `UNAUTHENTICATED`(401)、`FORBIDDEN`(403)：无会话、无 `category.manage`。
- `VALIDATION_FAILED`(400)：名称或排序值不合法。
- `CATEGORY_PARENT_CYCLE`(409)：新父级是自身或子孙。
- `CATEGORY_HAS_CHILDREN`(409)：还有子分类。
- `CATEGORY_IN_USE`(409)：已被文章引用。
- `CATEGORY_NOT_FOUND`(404)、`CATEGORY_PARENT_NOT_FOUND`(404)：分类或父级不存在。
- `CATEGORY_NAME_DUPLICATED`(409)：同级重名。
- `CATEGORY_SORT_INVALID`(400)：排序列表含重复 ID 或跨父级。

## 分类变更规则

1. 树查询先批量取全表，再按 `parent_id` 组树，同级按 `sort_order` 升序、`id` 升序，保证顺序稳定。单次请求只有一次分类查询。
2. 换父级前校验：父级存在（`0` 表示根）、新父级不是自身、沿 `parent_id` 上溯不出现自身，链条超过 64 层按非法处理。
3. 删除前先查子分类；文章引用由模块 C 的 `cms_article.category_id` 外键兜底，触发 `DataIntegrityViolationException` 时统一返回 `CATEGORY_IN_USE`。C 也可以用 `CategoryArticleReference` 实现前置检查，模块 B 通过 `ObjectProvider` 可选装配，不实现时不影响启动。
4. 启停只改自身 `enabled`，级联影响在读取时计算：祖先停用则整条子树对读者不可见。启停使用原状态做联合条件更新，避免重复或并发提交改乱状态；成功记录在业务事务内调用 `AuditService.recordSuccess`，动作取 `ENABLE_CATEGORY`/`DISABLE_CATEGORY`，对象类型 `CATEGORY`。
5. 排序接口只接受同一父级下的分类 ID，按传入顺序以 10 为步长重排 `sort_order`；重复 ID、跨父级 ID 直接拒绝。
6. 名称统一 `strip` 后入库，空名或超过 64 字符拒绝。

## 交给其他模块的接口

- 给 C：`CategoryQueryService.findById`（按 ID 查分类快照）、`isPublishable`（分类及全部祖先有效才可发布）、`effectiveCategoryIds`（取有效子孙 ID，`0` 表示全站）；统一使用 `categoryId`。
- 给 A：后台写操作统一要求 `category.manage`，权限标识按共享草案使用小写点分格式。
- 给 D：启停动作走 `AuditConstants` 现有常量，未改动平台代码。

待 C 建立文章表后补：读者列表、搜索、详情过滤共用 `isPublishable` / `effectiveCategoryIds`，停用分类下的文章随分类一起隐藏。

## 自测与验收

`mvn clean verify` 通过，34 项测试中 3 项按 `CMS_VERIFY_MYSQL` 开关跳过；带 `CMS_VERIFY_MYSQL=true` 时 34 项全通过（含真实 MySQL、Tomcat 部署与 HTTP 调用）。

覆盖的用例：

- 两级以上分类组树、同级排序稳定、悬挂节点按根处理、停用子树对读者隐藏、管理端可见。
- 循环父级：自身作父级、子孙作父级均被拦截；换到别的树、提升为根允许。
- 同级重名（含首尾空格）、父级不存在、排序列表重复或跨父级。
- 含子分类的删除被拒绝；被文章引用时由外键兜底返回 `CATEGORY_IN_USE`；删除文章后可正常删除分类。
- 停用后 `isPublishable` 为假、有效子孙集合剔除该子树、审计表出现 `DISABLE_CATEGORY`，恢复启用后出现 `ENABLE_CATEGORY`。
- HTTP：匿名读树 200；`includeDisabled=true` 与后台写接口在无会话时 401；名称空白、排序值为负返回 `VALIDATION_FAILED`。

演示「修改分类后检索随之变化」需要模块 C 的文章，本模块已用分类树给出对应变化：停用 `106 机电类通报` 后读者树只剩 `化学类通报`，恢复启用后两者都在（见 [verification.log](verification.log)）。

## 人工校验记录

- 2026-11-27，JDK 21.0.8、Maven 3.9.9、MySQL 8.0.46、Tomcat 10.1.54，数据库 `lab_safety_cms` 按 `001-platform.sql` → `002-taxonomy.sql` 顺序初始化。
- 首次运行 34 项测试时发现三处问题并已修正：`@PathVariable` 未显式命名（父工程未开 `-parameters`，导致 500）、实体 `getEnabled` 与 `isEnabled` 重复 getter 被 MyBatis 判为非法、树组装对不可变空列表排序抛异常。修正后全绿。
- `@Transactional` 使 `CategoryService` 成为 JDK 动态代理，只能按接口类型注入；因此拆出 `CategoryQueryService` 与 `CategoryAdminService`，控制器只依赖接口。
- 部署 `cms.war` 到 Tomcat 后，`/api/health`、`/api/health/db` 与 `/api/categories/tree` 均返回 200，匿名访问管理端接口返回 401（`UNAUTHENTICATED`）。

## 待联调

- A 落地 `CurrentUserProvider` 后，后台接口的 `category.manage` 由真实会话判定；目前无会话一律 401，无法演示作者/管理员差异。
- 与 C 确认 `CategoryArticleReference` 是否实现前置引用检查，以及读者端是否统一调用 `isPublishable`。
- 与 D 确认分类启停的审计记录能在 `/api/audit/logs` 中按 `CATEGORY` 与动作筛选出来。
