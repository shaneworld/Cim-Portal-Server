# 公告:纯文本预览 / 详情弹窗可读性 / 类型图标一致 / 自动关闭 + 历史 设计

> 日期:2026-06-12。范围:公告卡片预览去 Markdown 原文、详情弹窗放大与正式排版、弹窗类型/图标尺寸统一、到期自动关闭(禁用)+ 管理端公告历史。后端 Oracle-only;前端 Vue 3 + TS。

## 背景

公告已支持 Markdown(表格)、首页点击详情弹窗、管理端表单实时预览(前几轮)。本轮 4 项:

1. **公告卡预览显示了 Markdown 原文**(如 `| A | B |`),需改为纯文本预览。
2. **详情弹窗太小、字号太小**;公告可能较长,需更专业、可读、正式的排版。
3. **弹窗内类型徽标与图标尺寸不一致**,需统一。
4. **无时间范围的公告默认常显**;设了时间范围则**到期自动关闭并禁用**;管理端新增**「公告历史」面板**记录已关闭公告。

## 用户决策(已确认)

- 自动关闭机制:**Spring 定时任务**(@EnableScheduling + @Scheduled ~5 分钟),到期(endsAt < now)将 `active=false` 并记录 `closedAt`。真实持久禁用 + 精确关闭时间。
- 历史范围:**所有已关闭/已禁用**(到期自动关闭 + 管理员手动停用);用标记区分 expired(closedAt 有值) vs disabled(closedAt 为空)。
- 位置:**公告管理页内的「当前 / 历史」标签切换**(不新增导航项)。当前页 = active=true(常显 + 计划中);历史页 = active=false。

## 硬约束

- 后端 `mvn test` 绿;`OracleMigrationTest`=**17**(V17 加 closed_at)。
- 前端 `npm test`(含 i18n zh/en 对齐)+ `npm run build` 绿。
- 公告正文为自由文本(CLOB),无正文 schema 变更;仅加 `closed_at` 列。
- 定时任务使用注入的 `Clock`(便于单测);任务 fixedDelay/initialDelay 取较大值,避免测试期间误触发干扰。
- Markdown 渲染净化不变(表格保留、图片/脚本剥离);纯文本提取同样安全(取文本内容)。
- commit trailer:`Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>`;提交前测试须绿。

---

## #1 公告卡纯文本预览

**File:** `src/lib/ui/markdown.ts`、`src/features/dashboard/AnnouncementsPanel.vue`。

- `markdown.ts` 新增 `markdownToText(src: string): string`:`renderMarkdown(src)` 得到净化 HTML → 用 `DOMParser`/临时元素取 `textContent` → 折叠空白(`replace(/\s+/g,' ').trim()`)→ 返回纯文本;空输入返回空串。(复用既有净化渲染,避免新依赖。)
- `AnnouncementsPanel.vue` 预览行(当前 `<p class="mt-0.5 line-clamp-1 text-xs text-ink-3">{{ pick(a, 'body') }}</p>`)改为 `{{ markdownToText(pick(a, 'body')) }}`,仍 `line-clamp-1`。表格/强调/标题等 Markdown 标记不再以原文出现。

## #2 详情弹窗放大 + 正式可读排版

**File:** `src/lib/ui/Modal.vue`、`src/features/dashboard/AnnouncementDetailModal.vue`、`src/assets/index.css`。

- `Modal.vue` 的 `maxW` 增加 `'2xl': '64rem'`(类型 `ModalSize` 加 `'2xl'`);详情弹窗 `size` 由 `lg` 改为 `2xl`(仍 `min(92vw, 64rem)` + `max-h-[90vh]` 响应式)。
- `index.css` 新增 `.markdown-body-lg`(在 `.markdown-body` 基础上增大):正文 `text-base`、`leading-7`;标题更大(h1 `text-xl`、h2 `text-lg`)、段落 `my-3`、表格/代码间距更舒展。仅详情弹窗用 `class="markdown-body markdown-body-lg"`;管理端预览保持紧凑 `.markdown-body`。
- 详情弹窗正式排版(`AnnouncementDetailModal.vue`):
  - 头部:类型徽标(见 #3 统一尺寸)+ 标题作为显著标题(`text-xl font-bold leading-snug`)。
  - 元信息行:发布时间(createdAt,本地化)+ 若有起止时间则展示生效区间(本地化,带 i18n 标签);`text-xs text-ink-3`。
  - 分隔线(`border-t border-border my-3`)。
  - 正文:`<div class="markdown-body markdown-body-lg" v-html="renderMarkdown(pick(announcement,'body'))">`。
  - Modal 自带右上角关闭按钮(上轮已加),无需再加。

## #3 弹窗类型/图标尺寸统一

**File:** `src/features/dashboard/AnnouncementDetailModal.vue`。

- 现状不一致:chip `size-7` + icon `size-4`、tag `text-[11px]`、pinned `size-3`。
- 统一为一致比例:chip `size-8` 居中 + `AppIcon class="size-4.5"`;type tag `text-xs px-2 py-0.5`;pinned 标记 `text-xs` + `Pin size-3.5`;同一行 `items-center gap-2` 垂直居中对齐。
- 颜色仍用 `colorClasses(typeColor)`(已含深浅色变体,上轮修复)。

## #4 到期自动关闭 + 公告历史

### 后端

- **迁移 V17** `V17__announcement_closed_at.sql`:`ALTER TABLE announcement ADD (closed_at TIMESTAMP);`(可空)。`OracleMigrationTest` 16→**17**。
- `Announcement` 实体:加 `@Column(name="closed_at") private Instant closedAt;`(可空)+ getter/setter。
- `AnnouncementResponse`:加 `Instant closedAt`(toResponse 填充)。`AnnouncementRequest` 不加(closedAt 由系统维护;手动停用经 active 字段)。
- **启用调度**:`@EnableScheduling`(加在 `CimPortalApplication` 或一个 `@Configuration`)。
- **关闭任务**:`AnnouncementService.closeExpired()`(纯逻辑,注入的 `Clock`):取 `active=true && endsAt != null && endsAt < clock.instant()` 的公告 → `active=false`,`closedAt = clock.instant()`,保存。返回关闭数量(便于测试/日志)。
- **调度封装**:一个 `@Scheduled(fixedDelayString="300000", initialDelayString="30000")`(5 分钟,启动后 30s 首跑)的方法调用 `closeExpired()`;放在 service 或单独 `AnnouncementScheduler`。日志记录关闭数量。
  - 测试隔离:`@Scheduled` 的固定/初始延迟较大,测试上下文内不会触发;`closeExpired()` 直接单测。
- **手动停用** 仍经 `update`(active=false),不设 closedAt → 历史里标记为 disabled。**重新启用**(active=true)经 update;应**清空 closedAt**(update 时若请求 active=true 则 `closedAt=null`),使其回到当前列表且不带历史关闭时间。
- 门户 `effective(now)` 不变(active && 窗口内)。管理端 `listAll()` 不变(返回全部,含 active=false)。

### 前端

- `src/lib/api/announcements.ts`:`Announcement` 类型加 `closedAt?: string | null`。
- `src/features/admin/announcements/AnnouncementsAdminView.vue`:
  - 顶部加 **「当前 / 历史」** 标签切换(本地 `tab` 状态,reka-ui 或简单按钮组,主题化)。
  - 用单次 `listAdminAnnouncements()` 结果按 `active` 客户端过滤:当前 = `active===true`;历史 = `active===false`。各自分页(沿用 `usePagination`,基于过滤后的列表)。
  - 历史行:额外列展示关闭信息——`closedAt` 有值显示「已到期 + 关闭时间(本地化)」;`closedAt` 为空显示「已手动停用」。沿用既有类型 tag/标题/时间窗列;行操作(编辑/删除)保留(编辑改 active=true 即回到当前)。
  - 当前页保留「新建公告」按钮;历史页可隐藏新建(可选)。
- i18n(两端对齐):`admin.announcements.tabActive`(当前 / Active)、`tabHistory`(历史 / History)、`closedExpired`(已到期 / Expired)、`closedDisabled`(已停用 / Disabled)、`closedAtLabel`(关闭时间 / Closed at)、详情弹窗元信息 `dashboard.announcements.publishedAt`(发布于 / Published)、`window`(生效时间 / Active period)等所需键。

---

## 测试策略

- **后端:**
  - `OracleMigrationTest`=17。
  - `AnnouncementService.closeExpired()` 单测(可控 `Clock`):endsAt 在过去且 active → 关闭后 active=false、closedAt=clock.instant();endsAt 为空 → 不动;endsAt 在未来 → 不动;已 inactive → 不动。返回数量正确。
  - `update` 重新启用清空 closedAt:active false→true 时 closedAt 置空(单测或 controller 测试)。
  - `AnnouncementResponse` 含 closedAt(controller 测试断言序列化)。
  - 调度不在测试期误触发(initialDelay 较大);不强求测 @Scheduled 注解本身。
  - `mvn -q test` 绿。
- **前端:**
  - `markdownToText` 单测:表格 `| A | B |\n|-|-|\n|1|2|` → 不含 `|`/`<table`,得可读文本;`**x**` → `x`;空 → 空串;`<script>` 不泄漏。
  - `AnnouncementsPanel` 预览用 `markdownToText`(断言不含原始 `|`)。
  - `AnnouncementsAdminView` 标签过滤:当前 tab 仅 active=true 行;历史 tab 仅 active=false 行,且历史行展示 expired/disabled 标记。
  - 详情弹窗:用 `markdown-body-lg`、size=2xl;头部尺寸统一(可断言关键 class 存在)。
  - i18n zh/en 对齐;`npm test` + `npm run build` 绿。

## 自检 / 一致性

- #1:`markdownToText`(C 工具)→ AnnouncementsPanel 预览;纯文本、净化安全。
- #2:Modal 加 2xl(64rem)、详情弹窗 size=2xl、`.markdown-body-lg` 大字号正式排版 + 元信息 + 分隔线。
- #3:详情头部 chip size-8 / icon size-4.5 / tag text-xs / pinned text-xs 统一对齐。
- #4:V17 closed_at;`@EnableScheduling` + `closeExpired()`(Clock,5min 调度);Response 加 closedAt;重新启用清空 closedAt;管理端「当前/历史」标签按 active 过滤,历史标 expired/disabled;`effective()`/`listAll()` 不变。
- 类型一致:`markdownToText(src):string`;`Announcement.closedAt?`;`closeExpired():int`(或返回关闭列表)。
- `OracleMigrationTest`=17;i18n 对齐;无正文 schema 变更。
