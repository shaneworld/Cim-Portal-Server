# CIMS 门户 — 设计文档

**日期:** 2026-06-05
**状态:** 设计已确认,待进入实现阶段

## 1. 目标

为计算机集成制造系统(CIMS)提供统一入口门户。它以卡片式仪表盘集中展示通往各制造子系统
(MES、SPC、AMS 等)的链接。每个用户能看到哪些卡片,取决于其**部门**与**角色**。管理员
可完全从前端管理整个目录——链接、访问规则、双语标签,以及若干可编辑的枚举列表。界面支持
**中英双语**,并支持**浅色 / 深色 / 跟随系统**主题切换。

同时交付为 **Web 应用**与 **Windows 桌面客户端**(Tauri),覆盖三套环境:**Dev、UAT、Prod**。

## 2. 关键决策(概览)

| 主题 | 决策 |
|---|---|
| 能否纯前端实现? | **不能。** 管理员的修改必须对所有用户即时生效并持久化 → 必须有共享后端 + 数据库。 |
| 前端技术栈 | Vue 3 + TypeScript + Vite + Tailwind CSS + **shadcn-vue**(基于 Reka UI)。*注:`shadcn/ui` 仅支持 React;`shadcn-vue` 是 Vue 原生对应版本。* |
| 后端技术栈 | Spring Boot 3(Java),OAuth2 资源服务器。 |
| 后端结构 | **按功能分包**(package-by-feature)的模块化单体,单一可部署单元。 |
| 数据库 | Dev 用 **MariaDB**;UAT 与 Prod 用 **Oracle**。JPA + Flyway,按数据库厂商拆分迁移脚本。 |
| 单点登录(SSO) | 可插拔的 OIDC 边界。Dev 用 mock 颁发者;UAT/Prod 用企业真实 IdP(后续选定)。 |
| 身份 → 部门/角色 | SSO 仅负责认证。部门/角色来自 `user_info` 表(Prod 由外部同步;Dev/UAT **模拟/预置种子数据**)。 |
| 访问模型 | 每条链接配置部门/角色**白名单**;**任一匹配即可见**(match-ANY);白名单为空 = 所有人可见。 |
| 桌面端 | Tauri 2 将同一份 SPA 构建产物打包为 Windows EXE;loopback + PKCE 认证;令牌存入 Windows 凭据管理器;**Tauri 自动更新**,各环境独立发布源。 |
| 国际化策略 | 数据库驱动:可编辑标签 + 内容内联 `zh`/`en` 字段,启动时注入 `vue-i18n`。 |
| 文档 | **VitePress** 静态文档站;API 文档现手写中文 Markdown,后端实现时用 **springdoc-openapi** 自动生成在线交互版。 |

## 3. 架构

```
┌────────────────────────┐     ┌────────────────────────────┐
│  Web 浏览器              │     │  Windows 桌面端 (.exe)       │
│  托管 SPA 构建产物        │     │  Tauri 2 外壳 + 同一份 SPA   │
│                         │     │  · WebView2 渲染 SPA         │
│                         │     │  · loopback + PKCE OAuth     │
│                         │     │  · 令牌 → Windows 凭据管理器  │
│                         │     │  · Tauri 自动更新(按环境)    │
└───────────┬─────────────┘     └──────────────┬──────────────┘
            │  同一份 Vue SPA 构建,API 基址按环境配置          │
            └───────────────────┬───────────────────────────────┘
                                 │ HTTPS · Bearer(OIDC)· JSON REST
                  ┌──────────────▼───────────────┐
                  │  Spring Boot 3 API            │
                  │  OAuth2 资源服务器 +          │
                  │  权限解析 + CRUD              │
                  └──────────────┬────────────────┘
                                 │ JPA + Flyway
   Dev: profile=dev / MariaDB    UAT: profile=uat / Oracle    Prod: profile=prod / Oracle
   OIDC: mock 颁发者(dev) → 企业真实 IdP(uat/prod),可插拔
```

**原则:**
- **一份 SPA,三种交付**(浏览器、各环境 Tauri 构建)。前端单一代码库。
- **以 `PortalApi` JSON 契约解耦。** 前端先对接 localStorage 模拟实现;通过 `VITE_API_MODE`
  切换到 HTTP 客户端。
- **服务端权限解析。** 前端绝不决定可见性;`GET /api/portal/home` 只返回调用者有权查看的链接。
- **数据库可移植**:JPA + Flyway,迁移脚本按 `db/migration/{mariadb,oracle}` 拆分
  (外加 `seed-dev` 放模拟数据),按 Spring profile 选择。`GenerationType.IDENTITY` 在
  MariaDB(AUTO_INCREMENT)与 Oracle(identity 列)上均可用。

### 环境配置接缝

| 层 | Dev | UAT | Prod |
|---|---|---|---|
| 前端(Vite 模式) | `.env.development` | `.env.uat` | `.env.production` |
| 前端配置项 | API 基址、OIDC 颁发者/客户端、应用标题 | (同上键) | (同上键) |
| 后端(Spring profile) | `dev` | `uat` | `prod` |
| 数据库 | MariaDB(本地) | Oracle | Oracle |
| OIDC | mock 颁发者 | 真实 IdP | 真实 IdP |
| `user_info` 来源 | 模拟(种子) | 模拟(种子) | 外部同步 |
| Tauri 构建 + 更新源 | dev | uat | prod |

## 4. 数据模型

所有管理员可编辑的展示文本均带 `zh` + `en` 列。所有可变表均含时间戳(`created_at`、
`updated_at`),下文为简洁省略。

### `enum_value` — 支撑全部四类可编辑枚举的通用表(特性 7)
```
id, category, code, label_zh, label_en, sort_order, active
  category ∈ { DEPARTMENT, ROLE, LINK_CATEGORY, LINK_STATUS }
  唯一约束 (category, code)
```
单表,完整 CRUD;新增类别无需改表结构。`code` 是稳定主键(如 `FAB1-PROD`);双语标签仅用于展示。

### `link` — 仪表盘卡片(特性 3)
```
id, code, name_zh, name_en, url, icon,
  category_code → enum_value(LINK_CATEGORY),
  status_code   → enum_value(LINK_STATUS),
  sort_order, open_in_new_tab
```
`icon` 存 Lucide 图标名(精选集合;管理端用可搜索选择器——不允许任意上传)。

### `link_access_grant` — 每条链接的白名单(特性 4)
```
id, link_id → link, grant_type, grant_code
  grant_type ∈ { DEPARTMENT, ROLE };  grant_code → enum_value.code
  唯一约束 (link_id, grant_type, grant_code)
```
**没有任何**授权行的链接对所有人可见。否则,当用户部门匹配某条 DEPARTMENT 授权,
**或**角色匹配某条 ROLE 授权时可见。

### `label` — 可从前端编辑的静态 UI 文案(特性 6)
```
id, label_key, type, text_zh, text_en
  type ∈ { SYSTEM_NAME, UI_TEXT, … }   唯一约束 (label_key)
```
启动时注入 `vue-i18n`。`type` 即你描述的「类型标识」(系统名 vs 其他),供管理端筛选。

### `user_info` — 只读;Prod 外部同步,Dev/UAT 模拟
```
employee_id(主键,匹配 SSO subject/claim),
  display_name_zh, display_name_en,
  department_code → enum_value(DEPARTMENT),
  role_code       → enum_value(ROLE),
  email, active, synced_at
```
单部门 + 单角色。门户**绝不写入**此表;在 Prod 中它甚至可以是一个数据库视图。Dev/UAT 的种子
数据放在 profile 限定的迁移里(`db/migration/seed-dev`),绝不会在 Prod 执行。

### 身份解析
`token → subject(employee_id) → user_info 行 → {department_code, role_code} → 匹配链接授权`。
`enum_value` 的 DEPARTMENT/ROLE 列表是管理员写授权时可选的**词表**;`user_info` 才是实际成员归属。

## 5. API 接口面

> 完整请求/响应字段、示例与错误码见 [`docs/api/api-reference.md`](../../api/api-reference.md)。

### 门户接口(任意已认证用户)
```
GET  /api/portal/home      → 解析后的仪表盘:可见链接按类别分组、按 sort_order 排序(服务端解析)
GET  /api/portal/me        → 调用者身份(token + user_info)
GET  /api/i18n/labels      → { key: { zh, en, type } },供 vue-i18n 注入
GET  /api/enums/{category} → 该类别的启用枚举值(用于展示)
```

### 管理接口(仅 `PORTAL_ADMIN` 角色)
```
GET|POST|PUT|DELETE  /api/admin/links
GET|POST|PUT|DELETE  /api/admin/links/{id}/grants
GET|POST|PUT|DELETE  /api/admin/enums/{category}
GET|POST|PUT|DELETE  /api/admin/labels
GET                  /api/admin/users           (user_info 只读视图)
```

**鉴权**由服务端强制(Spring Security):`/api/admin/**` 需要 token 中的管理员角色。前端隐藏
管理控制台仅为便利,不构成安全边界。

**错误契约:** `401`(无/无效令牌)、`403`(已认证但非管理员)、`400`(校验失败)、
`409`(唯一约束冲突,如 `code` 重复)。

## 6. 前端设计

### 国际化(i18n)
`vue-i18n`,两层:
- *框架/静态文案与系统名* → 取自 `/api/i18n/labels`(可编辑 `label` 表),启动时注入。
- *内容*(链接名、枚举标签)→ 每条记录内联 `zh`/`en`;`useLocale()` 组合式函数按当前语言选字段。
- 语言切换在页头,持久化到 `localStorage`,默认取 `navigator.language`。

### 主题
浅色 / 深色 / 跟随系统。Tailwind `dark` class 策略;`useTheme()` 组合式函数默认 `system`
(`prefers-color-scheme`),用户显式选择后持久化到 `localStorage`。shadcn-vue 令牌遵循此机制。

### 页面
- **仪表盘(`/`):** 响应式卡片网格,卡片按双语类别标题分组、按 `sort_order` 排序。每张卡片:
  图标 + 本地化名称 + 可选状态徽标;点击打开链接(依 `open_in_new_tab` 决定是否新标签页)。
  页头含主题与语言切换。
- **管理控制台(`/admin`,仅管理员):** 四个区块共用一套布局——
  - **链接** — CRUD + 白名单编辑器(勾选部门/角色;为空 = 所有人)。
  - **枚举** — 一个按类别参数化的通用「枚举管理器」组件,对部门、角色、链接类别、链接状态做
    CRUD(共用 `enum_value` 结构):内联 zh/en、排序、启用开关。
  - **标签** — 带 `type` 筛选的表格,内联 zh/en 编辑。
  - **用户** — `user_info` 只读视图(便于排查授权)。
- **图标:** 通过 `lucide-vue-next` 使用 Lucide;`link.icon` 存图标名;管理端用可搜索选择器。

### API 客户端
一个 TypeScript `PortalApi` 接口,两套实现:localStorage 模拟实现(前端独立开发)与 HTTP 实现;
通过 `VITE_API_MODE` 选择。

## 7. 后端模块组织

按功能分包的模块化单体(单一 Spring Boot 可部署单元)。每个模块自包含
(`web` → `service` → `repo` → `domain` + `dto`)。**跨模块的业务操作(写入、含业务规则的流程)
必须经目标模块的服务层**;但在单体内,跨模块的**只读 / 校验**访问(如 `link` 校验枚举值是否存在、
`portal` 解析时批量读取 link/grant/enum)**允许直接注入对方仓储**——这是经评审有意采纳的务实折中,
避免为简单读取增加无意义的服务层转发。依赖方向保持单向无环。

```
com.cimportal
├── CimPortalApplication.java
├── common/   横切关注点:错误契约(@ControllerAdvice)、Web/Jackson/CORS 配置、基础 DTO
├── auth/     认证与安全:OAuth2 资源服务器配置、token→principal、方法级安全、当前用户(/api/portal/me)
├── user/     user_info:只读仓储、管理只读视图、Dev/UAT 同步模拟(种子)
├── enum_/    通用 enum_value CRUD            → /api/enums, /api/admin/enums
├── label/    label CRUD + i18n 输出          → /api/i18n/labels, /api/admin/labels
├── link/     link + link_access_grant CRUD   → /api/admin/links
└── portal/   仪表盘解析 + 权限引擎            → /api/portal/home
              (经服务层依赖 link、user、enum_)
```

依赖方向单向:`portal → {link, user, enum_}`;所有模块可用 `common`/`auth`。**权限解析引擎**
位于 `portal`——它是唯一把用户部门/角色与链接授权做联结的地方。Flyway 迁移脚本放在功能包之外,
位于 `resources/db/migration/{mariadb,oracle,seed-dev}`。

## 8. 桌面端(Tauri)

- **内嵌同一份 SPA。** Tauri 2 将同一份 Vue 构建产物编译为 Windows `.exe` + 安装包
  (NSIS/MSI)。无需第二套前端。
- **桌面端认证:** loopback 重定向 + PKCE——拉起系统浏览器,在 `127.0.0.1:<port>` 接回调。
  令牌经 Tauri keychain 插件存入 **Windows 凭据管理器**(绝不明文落盘)。
- **远程后端:** API 基址为各环境的构建期配置。
- **自动更新:** Tauri updater,配签名密钥对与**各环境独立的发布源**(UAT 构建查 UAT 源;
  Prod 构建查 Prod 源)。

## 9. 文档站点与 API 文档

- **文档站:** 用 **VitePress**(与前端技术栈一致)搭建静态文档站,根目录为 `docs/`。本设计文档与
  API 参考都以 Markdown 撰写并由站点托管;`npm run docs:dev` 本地预览,`docs:build` 产出可部署
  的静态站点。导航至少包含:概览、架构、数据模型、API 参考、部署。
- **API 文档(双轨):**
  1. **手写中文 Markdown**(`docs/api/api-reference.md`):随设计交付,可立即查阅;是契约的权威来源。
  2. **后端 OpenAPI**:后端实现时引入 **springdoc-openapi**,从代码自动生成 OpenAPI 规范,
     提供 Swagger UI / Redoc 交互式在线文档,并可嵌入 VitePress 站点。手写文档与生成文档保持一致。

## 10. 测试策略

- **后端:**
  - **权限解析**单元测试(空授权、部门匹配、角色匹配、无匹配、停用用户)及校验/冲突路径。
  - `@WebMvcTest` 控制器切片测试,含 401/403。
  - 仓储/迁移测试在 **MariaDB 与 Oracle 双库**上用 Testcontainers 运行,发布前验证方言/Flyway 拆分。
- **前端:**
  - 组件测试(Vitest + Vue Test Utils):卡片网格、白名单编辑器、主题/语言组合式函数。
  - 模拟 API 兼作契约夹具。
  - Playwright 冒烟流程:以各预置身份登录 → 断言应出现哪些卡片。
- **桌面端:** 冒烟测试 Tauri 构建能启动 bundle,并对 mock 颁发者完成 loopback-PKCE 流程。

## 11. 不在范围内(YAGNI)

- 规则引擎式访问控制(布尔条件、按时间)——每链接白名单已足够。
- 用户与链接之间的分组/间接层。
- 门户自管用户目录 / 写入 `user_info`。
- 任意图标上传(改用精选 Lucide 集合)。
- 「一切皆字典」的通用建模(方案 B)与静态文件 i18n(方案 C)。
