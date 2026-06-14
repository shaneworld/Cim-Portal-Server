# 首页定宽 / 公告图标 / 管理员快捷链接(架构+Swagger)设计

> 日期:2026-06-13。范围:首页全屏定宽、公告图标尺寸/对齐、管理员头部「架构图 + Swagger API」链接。后端 Oracle-only;前端 Vue 3 + TS。(原 #1 附带的「Quick Links 可滑动区」已应用户要求**跳过**,本轮不做。)

## 背景与目标

1. **全屏拉伸**:首页(HomeView)无 max-width,头部/信息栏/系统网格在超宽屏全屏时过度拉伸。加居中定宽容器。
2. **公告图标**:首页公告条每行图标偏小且顶部对齐(`items-start` + `mt-0.5`);详情弹窗里图标 chip 与类型标签高度不一致。统一尺寸 + 左侧垂直居中;详情弹窗将「图标 + 类型标签」合并为单个胶囊徽标。
3. **管理员头部链接**:管理员登录后,在「管理(Management)」按钮旁加两个链接——**项目架构图** 与 **Swagger API 文档**,新标签打开,仅管理员可见。

## 用户决策(已确认)

- 链接 URL:**环境变量配置**(`VITE_ARCH_URL`、`VITE_SWAGGER_URL`),各自仅在设置时渲染(prod 不设 Swagger → 自动隐藏,契合 prod 禁用 Swagger)。
- 架构图:**后端打包并提供** `architecture/diagram.html` 为静态资源(`/architecture.html`,permitAll),给架构链接一个稳定目标;env 变量指向它(dev 指向 `http://localhost:8080/architecture.html`)。
- Quick Links 可滑动区:**跳过**(本轮不做)。

## 硬约束

- 后端 `mvn test` 绿;**无新迁移**(`OracleMigrationTest` 仍 = 17);只新增静态资源 + SecurityConfig permitAll。
- 前端 `npm test`(含 i18n zh/en 对齐)+ `npm run build` 绿。
- 链接仅管理员可见(`auth.isAdmin`),`target=_blank rel="noopener noreferrer"`,各自 env 未设则不渲染。
- commit trailer:`Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>`;提交前测试须绿。

---

## #1 首页全屏定宽

**File:** `src/features/dashboard/HomeView.vue`。

- HomeView 结构:`<div class="min-h-screen py-4 sm:py-6 px-[max(1rem,7vw)]"><div class="space-y-6"> ...AppHeader / info row / search / SystemGrid... </div></div>`。
- 将内层 `<div class="space-y-6">` 改为 `<div class="mx-auto max-w-[1600px] space-y-6">`。
- 效果:头部、信息栏、系统网格统一在 1600px 内居中,超宽屏不再边到边拉伸;仍给 2xl 的 5 列系统网格留足空间(1600px > 5 列所需)。单点修复,无逐面板 hack。其余 padding(`px-[max(1rem,7vw)]`)保留(窄屏行为不变)。

---

## #2 公告图标

### 2a 公告条行内图标(`src/features/dashboard/AnnouncementsPanel.vue`)

- 行 `<button class="flex w-full items-start gap-3 ...">` → `items-center`(图标相对两行内容垂直居中,即「左侧居中」)。
- 图标 chip:`mt-0.5 flex size-7 ...` → 去掉 `mt-0.5`,`size-7` → `size-8`;内部 `<AppIcon class="size-4">` → `size-[18px]`。与详情弹窗一致。
- 颜色/`colorClasses` 不变;其余行内容(类型 tag、置顶、标题、单行预览)不变。

### 2b 详情弹窗头部(`src/features/dashboard/AnnouncementDetailModal.vue`)

- 现状:`size-8` 图标 chip + 独立的 `px-2 py-0.5 text-xs` 类型 tag,二者高度不齐。
- **合并为单个胶囊徽标**:一个圆角徽标(`inline-flex h-7 items-center gap-1.5 rounded-lg px-2`,用 `colorClasses(typeColor).chip` 的底色/文字色)内含 `<AppIcon :name="typeIcon" class="size-4">` + 类型标签文字(`pick(announcement,'typeLabel')`)。这样图标与类型名天然同高、同基线。
- 置顶标记(`Pin size-3.5` + 文案)保留在同一 `flex items-center gap-2` 行,垂直居中。
- DialogTitle(标题)、元信息行、分隔线、`.markdown-body markdown-body-lg` 正文均不变。
- (备选:若不合并,则给 tag 加 `h-8 inline-flex items-center` 与 chip 同高——但推荐合并胶囊。)

---

## #2b 管理员头部链接(架构图 + Swagger)

### 后端(静态资源 + 安全放行,无迁移)

- 将 `architecture/diagram.html` 复制为静态资源 `src/main/resources/static/architecture.html`。
  - **自包含检查**:diagram.html 若引用同级资源(如 overview.png/svg),需一并复制到 static/ 对应位置或确认其为自包含(architect 生成的 diagram.html 通常内联自包含)。实现时打开文件头部确认无外部相对引用;若有,连同引用资源一起复制并保持相对路径。
- `SecurityConfig`:在现有 permitAll 匹配器中加入 `/architecture.html`(若拆成目录则 `/architecture/**`)。与 `/swagger-ui.html` 同级 permitAll(新标签无法携带 SPA 的 bearer token,故必须 permitAll;内网门户可接受,与 Swagger 一致)。
- 该静态页在 dev 由后端(:8080)直接提供;prod/nginx 下是否暴露由部署 env(`VITE_ARCH_URL`)与 nginx 决定,不在本轮代码内强约束。
- 不动迁移、不动其他端点。

### 前端(头部链接,env 配置,管理员可见)

- `src/features/dashboard/AppHeader.vue`:在 `auth.isAdmin` 的「管理」`RouterLink`(`to="/admin"`)旁,新增两个仅管理员、仅在对应 env 变量已设时渲染的链接:
  - 架构图:`<a v-if="auth.isAdmin && archUrl" :href="archUrl" target="_blank" rel="noopener noreferrer" :title="t('header.architecture')" :aria-label="t('header.architecture')">` 内含 lucide `Network` 图标,ghost 图标按钮样式(与现有 ghost `Button size=icon` 一致:`inline-flex size-9 items-center justify-center rounded-xl ...` 或直接用一个 `<a>` 套 ghost 样式)。
  - API 文档:同上,`swaggerUrl` + lucide `BookText` 图标,`t('header.apiDocs')`。
  - 取值:`const archUrl = import.meta.env.VITE_ARCH_URL as string | undefined`;`const swaggerUrl = import.meta.env.VITE_SWAGGER_URL as string | undefined`(脚本 computed/常量)。
  - 位置:放在「管理」按钮之后、用户信息之前(或紧邻管理按钮),`shrink-0`,与其余头部按钮间距一致(`gap-1.5`)。视觉上从属于管理按钮(ghost 风格,不抢眼)。
- i18n(两端对齐):`header.architecture`(架构图 / Architecture)、`header.apiDocs`(API 文档 / API Docs)。
- 环境变量:
  - `.env.example`:记录 `VITE_ARCH_URL`、`VITE_SWAGGER_URL`(注释说明:留空则隐藏;dev 指向后端 :8080)。
  - `.env.development`(Vite 在 `vite dev` 自动加载):设 `VITE_ARCH_URL=http://localhost:8080/architecture.html`、`VITE_SWAGGER_URL=http://localhost:8080/swagger-ui.html`,使 dev 下两链接默认出现。
  - 构建期变量(`import.meta.env`)——确认 vite/TS 对未声明的 `VITE_*` 不报错(import.meta.env 为宽松类型;如项目有 `env.d.ts` 的 `ImportMetaEnv` 接口,则在其中声明这两个可选字段)。

---

## 测试策略

- **后端:**
  - SecurityConfig 测试(若有)或新增轻测:`GET /architecture.html` 未认证 → 200(permitAll)且非 401。`OracleMigrationTest` 仍 17。
  - `mvn -q test` 绿。
- **前端:**
  - AppHeader spec:管理员 + env 设值 → 架构/API 链接渲染(`target=_blank`、href 正确);非管理员 → 不渲染;env 未设 → 不渲染。用测试态注入/mock `import.meta.env`(或对组件以 props/computed 提取 URL 以便测试;若 import.meta.env mock 困难,则将两个 URL 提取为模块级常量并在测试中 stub,或断言「管理员且 url 存在时渲染」的条件逻辑)。
  - i18n parity(header.architecture / header.apiDocs)。
  - AnnouncementsPanel/AnnouncementDetailModal:渲染断言(图标 chip size-8;详情合并胶囊含 icon + 类型文案)。现有 spec 不因 class 调整失败(它们靠 testid/文案)。
  - HomeView:max-w 容器不破坏现有渲染/测试。
  - `npm test` + `npm run build` 绿。

## 自检 / 一致性

- #1:HomeView 内层容器 `mx-auto max-w-[1600px]`(头部+信息栏+网格统一定宽居中)。
- #2a:公告条 `items-center` + chip size-8 + icon size-[18px](去 mt-0.5);详情弹窗图标+类型合并单胶囊同高。
- #2b:后端静态 `/architecture.html`(permitAll,无迁移);前端 AppHeader 两个管理员 ghost 链接(Network/BookText,新标签),env 配置(VITE_ARCH_URL/VITE_SWAGGER_URL),未设则隐藏;dev env 默认值;i18n header.architecture/apiDocs 两端对齐。
- `OracleMigrationTest`=17;i18n 对齐;Quick Links 本轮跳过。
