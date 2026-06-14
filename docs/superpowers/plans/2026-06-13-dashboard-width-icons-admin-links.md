# 首页定宽 / 公告图标 / 管理员快捷链接 实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development. 提交前 `mvn -q test` / `npm test` 须绿。commit trailer:`Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>`。

**Goal:** 首页内容居中定宽(max-w-1600);公告图标尺寸统一并左侧居中、详情弹窗图标+类型合并单胶囊;管理员头部新增「架构图 + Swagger」env 配置链接(后端以静态资源提供 diagram.html)。

**Architecture:** 纯前端样式/布局调整(HomeView 定宽、两个公告组件图标);AppHeader 加两个管理员、env 配置、新标签链接;后端把 architecture/diagram.html 作为静态资源 + SecurityConfig permitAll(无迁移)。

**Tech Stack:** Vue 3.5 + TS + Tailwind v3 + vue-i18n + lucide;Spring Boot 3.3 静态资源 + Spring Security。

仓库:后端 `/home/shane/Code/cim-portal/cim-portal-server`,前端 `/home/shane/Code/cim-portal/cim-portal-client`。

---

## 单元 A:后端 —— 架构图静态资源 + 安全放行(无迁移)

**Files:**
- Create: `src/main/resources/static/architecture.html`(由 `architecture/diagram.html` 复制)
- Modify: `src/main/java/com/cimportal/auth/SecurityConfig.java`(permitAll 加 `/architecture.html`)
- Test: `src/test/java/com/cimportal/auth/`(若有 security/smoke 测试则加一条;否则在现有集成测试加断言)

- [ ] **Step 1: 复制 diagram.html 为静态资源**

先确认自包含:`head -c 2000 architecture/diagram.html` 看是否有外部相对引用(`src="..."`/`href="..."` 指向同级文件如 overview.png/svg)。architect 生成的 diagram.html 通常内联自包含。
- 若自包含:`cp architecture/diagram.html src/main/resources/static/architecture.html`(目录不存在则 `mkdir -p src/main/resources/static`)。
- 若引用了同级资源:同时把被引用文件复制到 `src/main/resources/static/` 并保持相对文件名一致。
确认文件存在:`ls -la src/main/resources/static/architecture.html`。

- [ ] **Step 2: SecurityConfig 放行 /architecture.html,跑确认未认证可达**

读 `SecurityConfig.java`,现有 permitAll 形如:
```java
.requestMatchers("/actuator/health", "/swagger-ui.html", "/swagger-ui/**",
                 "/v3/api-docs/**", AppConstants.Paths.DEV_TOKEN,
                 AppConstants.Paths.PORTAL_CONFIG,
                 AppConstants.Auth.LOGIN_PATH).permitAll()
```
在该 permitAll 列表加入 `"/architecture.html"`(与 swagger 同级)。
测试:在现有安全/集成测试(找一个用 MockMvc 且 `@AutoConfigureMockMvc` 的测试,或新建 `ArchitectureStaticResourceTest extends OracleIntegrationTest`)加:
```java
@Test
void architectureHtml_isPubliclyServed() throws Exception {
    mockMvc.perform(get("/architecture.html"))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith("text/html"));
}
```
(用项目现有 MockMvc 测试基类风格;若集成测试用 TestRestTemplate/WebTestClient,则对应调整。关键断言:未带 token GET /architecture.html → 200,非 401。)
Run: `mvn -q -Dtest=ArchitectureStaticResourceTest test`(或所选测试)→ 绿。

- [ ] **Step 3: 全测 + 提交**

Run: `mvn -q test` → 绿,`OracleMigrationTest` 仍 17(无新迁移)。
```bash
git add -A
git commit -m "feat(architecture): 后端以静态资源提供架构图 /architecture.html(permitAll)

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## 单元 B:前端 —— 首页定宽 + 公告图标

**Files:**
- Modify: `src/features/dashboard/HomeView.vue`(定宽容器)
- Modify: `src/features/dashboard/AnnouncementsPanel.vue`(行内图标)
- Modify: `src/features/dashboard/AnnouncementDetailModal.vue`(合并图标+类型胶囊)

- [ ] **Step 1: HomeView 定宽**

`HomeView.vue`:内层 `<div class="space-y-6">` 改为 `<div class="mx-auto max-w-[1600px] space-y-6">`。外层 `min-h-screen py-4 sm:py-6 px-[max(1rem,7vw)]` 不变。

- [ ] **Step 2: AnnouncementsPanel 行内图标居中 + 放大**

`AnnouncementsPanel.vue` 公告行:
- 行 `<button ... class="flex w-full items-start gap-3 rounded-xl border p-3 text-left transition hover:bg-muted/40" ...>` → 把 `items-start` 改为 `items-center`。
- 图标 chip `<span class="mt-0.5 flex size-7 shrink-0 items-center justify-center rounded-lg" :class="...chip">` → 去掉 `mt-0.5`,`size-7` → `size-8`。
- chip 内 `<AppIcon :name="a.typeIcon" class="size-4" />` → `class="size-[18px]"`。
- 其余(类型 tag、置顶、标题、`line-clamp-1` 预览)不变。

- [ ] **Step 3: AnnouncementDetailModal 合并图标 + 类型为单胶囊**

`AnnouncementDetailModal.vue` 头部当前:
```vue
<div class="flex flex-wrap items-center gap-2">
  <span class="grid size-8 shrink-0 place-items-center rounded-lg" :class="colorClasses(announcement.typeColor).chip">
    <AppIcon :name="announcement.typeIcon" class="size-[18px]" />
  </span>
  <span class="rounded px-2 py-0.5 text-xs font-medium" :class="colorClasses(announcement.typeColor).tag">{{ pick(announcement, 'typeLabel') }}</span>
  <span v-if="announcement.pinned" class="flex items-center gap-1 text-xs text-ink-3"><Pin class="size-3.5" />{{ t('dashboard.announcements.pinned') }}</span>
</div>
```
改为(图标 + 类型名合并为一个等高胶囊,用 chip 配色):
```vue
<div class="flex flex-wrap items-center gap-2">
  <span class="inline-flex h-7 items-center gap-1.5 rounded-lg px-2 text-xs font-medium" :class="colorClasses(announcement.typeColor).chip">
    <AppIcon :name="announcement.typeIcon" class="size-4" />
    {{ pick(announcement, 'typeLabel') }}
  </span>
  <span v-if="announcement.pinned" class="flex items-center gap-1 text-xs text-ink-3"><Pin class="size-3.5" />{{ t('dashboard.announcements.pinned') }}</span>
</div>
```
(单胶囊:`inline-flex h-7 items-center gap-1.5 rounded-lg px-2`,图标 size-4 与类型文字同行同高;不再有独立 chip/tag 高度差。DialogTitle、元信息、分隔线、`markdown-body markdown-body-lg` 正文不变。)

- [ ] **Step 4: 测 + build**

Run: `npm test`(现有 AnnouncementsPanel/Detail spec 靠 testid/文案,应不受 class 调整影响;HomeView 定宽不破坏渲染)。
Run: `npm run build`。

- [ ] **Step 5: 提交**

```bash
git add -A
git commit -m "feat(dashboard): 首页定宽(max-w-1600 居中)+ 公告图标居中放大 + 详情类型合并胶囊

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## 单元 C:前端 —— 管理员头部链接(架构 + Swagger,env 配置)

**Files:**
- Modify: `src/features/dashboard/AppHeader.vue`(两个管理员链接)
- Modify: `src/lib/i18n/locales/zh.ts`、`en.ts`(header.architecture / header.apiDocs)
- Modify/Create: `.env.development`(dev URL)、`.env.example`(文档)
- Modify(若存在): `src/env.d.ts` / `src/vite-env.d.ts`(声明可选 VITE_ARCH_URL/VITE_SWAGGER_URL)
- Test: `src/features/dashboard/AppHeader.spec.ts`(新建或扩展)

- [ ] **Step 1: i18n(两端)**

`zh.ts` `header` 块加 `architecture: '架构图'`、`apiDocs: 'API 文档'`;`en.ts` `architecture: 'Architecture'`、`apiDocs: 'API Docs'`。(`header.admin` 已存在。)

- [ ] **Step 2: env 变量声明 + 值**

- 读是否有 `src/vite-env.d.ts` 或 `src/env.d.ts` 含 `interface ImportMetaEnv`。若有,加可选字段:
  ```ts
  readonly VITE_ARCH_URL?: string
  readonly VITE_SWAGGER_URL?: string
  ```
  (若无该接口/文件,`import.meta.env.VITE_X` 在 TS 下默认 `string | boolean | undefined` 宽松类型,可不声明;但若 `npm run build` 的 vue-tsc 报错则补一个 `ImportMetaEnv` 声明。)
- 新建/改 `.env.development`(Vite dev 自动加载),加:
  ```
  VITE_ARCH_URL=http://localhost:8080/architecture.html
  VITE_SWAGGER_URL=http://localhost:8080/swagger-ui.html
  ```
- `.env.example`:追加两行 + 注释:
  ```
  # 管理员头部链接(留空则隐藏)。dev 指向后端 :8080;prod 按部署填(Swagger 在 prod 禁用,通常留空)
  VITE_ARCH_URL=
  VITE_SWAGGER_URL=
  ```

- [ ] **Step 3: 写 AppHeader 失败测试**

读现有 AppHeader.vue + 任何 AppHeader spec / 其他头部测试的 mount 风格(auth store mock:`auth.isAdmin`)。`AppHeader.spec.ts`:
```ts
// mock auth store isAdmin=true;stub import.meta.env via vi.stubEnv('VITE_ARCH_URL','http://x/arch') + vi.stubEnv('VITE_SWAGGER_URL','http://x/api')
// 断言:渲染含 data-testid="header-arch-link" 的 <a href="http://x/arch" target="_blank">,以及 header-api-link href="http://x/api"
// isAdmin=false → 两链接都不存在
// isAdmin=true 但两 env 未设(vi.stubEnv(...,'')) → 两链接都不存在
```
注意:`import.meta.env` 在 vitest 用 `vi.stubEnv('VITE_ARCH_URL', '...')`(并 `vi.unstubAllEnvs()` 清理),组件需在 setup 内读取 `import.meta.env.VITE_ARCH_URL`(而非模块顶层常量缓存,否则 stub 不生效——故在组件 `<script setup>` 内用 `computed(() => import.meta.env.VITE_ARCH_URL)` 或直接在 setup 读取)。若 vi.stubEnv 对 import.meta.env 不生效(Vite 编译期内联),退路:把两个 URL 读取封装为一个可 mock 的小函数/或用 `import.meta.env` 在 setup 内读取并通过组件 props 注入测试值;实现者择可行方案,确保测试能驱动「有值渲染/无值隐藏/非管理员隐藏」三态。
Run: `npm test -- AppHeader` → 红。

- [ ] **Step 4: 实现 AppHeader 链接**

`AppHeader.vue` `<script setup>`:加 lucide 图标 import `Network, BookText`;加
```ts
const archUrl = computed(() => import.meta.env.VITE_ARCH_URL as string | undefined)
const swaggerUrl = computed(() => import.meta.env.VITE_SWAGGER_URL as string | undefined)
```
模板:在 `<RouterLink v-if="auth.isAdmin" to="/admin" ...>` 之后加(仅管理员 + 有 url 才渲染,ghost 图标按钮样式,与现有 ghost icon 按钮一致 `inline-flex size-9 items-center justify-center rounded-xl text-ink-2 hover:bg-muted hover:text-foreground transition`):
```vue
<a v-if="auth.isAdmin && archUrl" :href="archUrl" target="_blank" rel="noopener noreferrer"
   data-testid="header-arch-link" :title="t('header.architecture')" :aria-label="t('header.architecture')"
   class="inline-flex size-9 shrink-0 items-center justify-center rounded-xl text-ink-2 transition hover:bg-muted hover:text-foreground">
  <Network class="size-4" />
</a>
<a v-if="auth.isAdmin && swaggerUrl" :href="swaggerUrl" target="_blank" rel="noopener noreferrer"
   data-testid="header-api-link" :title="t('header.apiDocs')" :aria-label="t('header.apiDocs')"
   class="inline-flex size-9 shrink-0 items-center justify-center rounded-xl text-ink-2 transition hover:bg-muted hover:text-foreground">
  <BookText class="size-4" />
</a>
```
(放在管理按钮之后、用户信息 span 之前;`gap-1.5` 由父 `<nav>` 提供。tokens `text-ink-2`/`hover:bg-muted`/`hover:text-foreground` 均真实。)
Run: `npm test -- AppHeader` → 绿(三态)。

- [ ] **Step 5: 全测 + build**

Run: `npm test`(i18n parity 含新键;AppHeader spec 绿;其他头部相关 spec 不被新链接破坏——若有 `find('a')`/`find('button')` 位置型断言因新 `<a>` 失败,改为按 testid 定位)。
Run: `npm run build`(确认 import.meta.env / vue-tsc 通过;若 TS 报未知 env,按 Step 2 补 ImportMetaEnv 声明)。

- [ ] **Step 6: 提交**

```bash
git add -A
git commit -m "feat(header): 管理员头部新增架构图 + Swagger 链接(env 配置,未设则隐藏,新标签打开)

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## 单元 D:联调验证(无代码)

- [ ] **Step 1: 后端**:build + 重启 dev(`fuser -k 8080/tcp`,勿 pkill portal.jar)。`curl -s -o /dev/null -w "%{http_code}" http://localhost:8080/architecture.html` → 200。`/swagger-ui.html` → 200(dev)。无新迁移仍 v17。
- [ ] **Step 2: 前端**(`npm run dev`,dev env 已设):管理员登录后头部「管理」旁出现架构图(Network)+ API 文档(BookText)两个图标按钮,点击新标签分别打开 `:8080/architecture.html` 与 `:8080/swagger-ui.html`;非管理员不显示。全屏超宽屏:首页内容在 1600px 内居中不再拉伸。公告条图标居中放大;点击公告 → 详情弹窗类型为单个图标+文字胶囊、同高对齐。深浅色正常。

---

## 自检(plan vs spec)

- **#1 定宽**:B-Step1(HomeView `mx-auto max-w-[1600px]`)。✅
- **#2a 图标**:B-Step2(公告条 items-center + size-8 + size-[18px])、B-Step3(详情合并胶囊)。✅
- **#2b 管理员链接**:A(后端静态 /architecture.html + permitAll,无迁移)、C(AppHeader 两链接 env 配置 + i18n + dev env + 测试三态)。✅
- 类型/命名一致:`VITE_ARCH_URL`/`VITE_SWAGGER_URL`、`archUrl`/`swaggerUrl`、testid `header-arch-link`/`header-api-link`、i18n `header.architecture`/`header.apiDocs`。✅
- `OracleMigrationTest`=17(无迁移);i18n 两端对齐;Quick Links 跳过。✅
