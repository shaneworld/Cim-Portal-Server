# 公告 Markdown / 信息栏定高 / 自定义日期选择器 / 移除快捷链接 设计

> 日期:2026-06-12。范围:撤销上一轮的快捷链接,重做公告(Markdown+表格、首页详情弹窗、响应式表单+实时预览)、信息栏定高+值班滚动条、自定义主题化日期时间选择器。后端 Oracle-only;前端 Vue 3 + TS。

## 背景

上一轮(Info Dashboard v2)交付了快捷链接、值班外部 API、公告定高、三面板布局。本轮需求:

1. **移除快捷链接**(整套撤销)。
2. **值班电话区加滚动条;信息栏固定在最佳高度。** 值班区按当日排班显示各部门值班人 + 电话(经 API,已于上轮实现)。
3. **公告弹窗响应式自适应窗口;放大正文编辑区;支持基础 Markdown(含表格、~~图片~~)。首页点击公告打开弹窗显示完整内容。**
4. **公告时间选择组件需与全站主题一致。**

## 用户决策(已确认)

- 移除快捷链接:**前向 V16 `DROP TABLE`**,保留历史(V14 不删);`OracleMigrationTest` 15→**16**。
- Markdown 图片:**放弃图片**;**必须支持表格**。即 `<img>` 在净化阶段移除,表格保留。
- 日期选择器:**自建主题化组件**(不引第三方 datepicker 依赖)。
- 公告编辑:**实时预览面板**(编辑区 + 渲染预览)。

## 硬约束

- 后端 `mvn test` 绿;`OracleMigrationTest`=**16**;quicklink 测试随代码删除。
- 前端 `npm test`(含 i18n zh/en 对齐)+ `npm run build` 绿。
- Markdown 渲染**必须净化**:移除 `<script>`、事件处理属性、`<img>`、`javascript:` 等;**保留表格**(table/thead/tbody/tr/th/td)、标题、列表、强调、链接(`<a>` 加 `rel="noopener noreferrer"` 且限 http/https)、代码块、引用。
- 公告正文为自由文本(CLOB,zh/en),**无 schema 变更**;Markdown 即以纯文本存储。
- 日期选择器 v-model 与现表单绑定兼容(本地 `YYYY-MM-DDTHH:MM` 字符串,可空)。
- commit trailer:`Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>`;提交前测试须绿。

---

## Part 1:移除快捷链接

### 后端
- 删除整个包 `com/cimportal/quicklink/`(`QuickLink`、`QuickLinkRepository`、`QuickLinkService`、`QuickLinkPortalController`、`QuickLinkAdminController`、`dto/QuickLinkRequest`、`dto/QuickLinkResponse`)。
- 删除测试 `src/test/java/com/cimportal/quicklink/QuickLinkControllerTest.java`。
- 新增迁移 `V16__drop_quick_link.sql`:`DROP TABLE quick_link CASCADE CONSTRAINTS;`(Oracle 语法;`PURGE` 可选)。
- `OracleMigrationTest` 断言 15→**16**。
- V14 文件**保留不动**(前向迁移,历史一致)。

### 前端(删除 + 解除引用)
- 删除文件:`src/lib/api/quickLinks.ts`、`src/features/dashboard/QuickLinksPanel.vue`、`src/features/admin/quick-links/QuickLinksAdminView.vue`、`src/features/admin/quick-links/QuickLinkFormModal.vue`。
- `src/lib/api/portal.ts`:删除 `listQuickLinks` 及 `QuickLink` import。
- `src/features/dashboard/HomeView.vue`:删除 `QuickLinksPanel` 的 import 与模板用法(右列改造见 Part 2)。
- `src/router/index.ts`:删除 `quick-links` 路由。
- `src/features/admin/AdminLayout.vue`:删除 `quickLinks` 导航项(及其未再使用的 `ExternalLink` import,若别处不再用)。
- i18n `zh.ts`+`en.ts`:删除 `admin.nav.quickLinks`、`admin.quickLinks.*`、`admin.quickLinkForm.*`、`dashboard.quickLinks.*`(两端同步,保持对齐)。
- 删除快捷链接相关 spec(若有 `QuickLinksPanel.spec.ts` 等)。
- `iconMap` 的 `'external-link'` 条目**保留**(通用,无害)。

---

## Part 2:信息栏定高 + 值班滚动条

**File:** `HomeView.vue`、`DutyLinesPanel.vue`、`AnnouncementsPanel.vue`、`src/assets/index.css`(滚动条样式,若需)。

- 移除快捷链接后,信息栏右列**仅 `DutyLinesPanel`**。
- 信息栏行设为**固定高度**:容器 `grid gap-4 md:[grid-template-columns:1.95fr_1fr]`,加行高 `h-[clamp(18rem,42vh,26rem)]`(两栏等高);两个面板各 `h-full`。
- `AnnouncementsPanel` 与 `DutyLinesPanel` 改为 `GlassCard` 内 `flex flex-col h-full`:标题固定,列表区 `flex-1 min-h-0 overflow-y-auto`(替换公告现有 `max-h-[min(22rem,60vh)]`,改由父级定高驱动)。值班空数据时(`lines.length===0`)显示占位文案(新增 `dashboard.dutyLines.empty`)而非隐藏整卡——因为现在它是定高行的固定一栏。**注意**:`infoPanelEnabled` 关闭时整行仍隐藏(沿用)。
- 滚动条:加细主题化滚动条样式(`scrollbar-width: thin` + webkit 伪元素,颜色用主题 border/primary 透明度),作用于这些滚动区(可加一个工具类 `.scroll-slim`)。
- 值班区已显示「部门 · 值班人(dutyName) · 电话」(上轮经外部 API);本部分仅加滚动 + 定高,不改数据逻辑。
- 移动端(< md):单列;两卡各自 `max-h-[60vh] overflow-y-auto`(不强制等高行,避免移动端过高)。

---

## Part 3:公告 Markdown + 详情弹窗 + 响应式表单实时预览

### 依赖与渲染工具
- 新增依赖:`markdown-it`、`dompurify`(+ 必要的 `@types`)。
- 新增 `src/lib/ui/markdown.ts`:`renderMarkdown(src: string): string`
  - `markdown-it({ html:false, linkify:true, breaks:true })`,表格默认启用。
  - 渲染后用 DOMPurify 净化:`ALLOWED_TAGS` 含 `p, br, hr, h1-h6, strong, em, del, blockquote, ul, ol, li, code, pre, a, table, thead, tbody, tr, th, td`;**不含 `img`**;`ALLOWED_ATTR` 仅 `href, title, align`(表格 align)、`a` 加 `target=_blank rel=noopener noreferrer`(用 DOMPurify hook 强制);禁 `javascript:`/`data:` 协议(`ALLOWED_URI_REGEXP` 限 http/https/相对)。
  - 空/纯空白输入 → 返回空串。
- 样式:`.markdown-body`(放 `src/assets/index.css` 或单独 css)——表格带边框/斑马纹、`th` 背景、`code`/`pre` 主题色、`a` 主题色、合理行距;深浅色适配。

### 首页点击 → 详情弹窗
- `AnnouncementsPanel.vue`:
  - 行改为可点击(`<button type=button class="w-full text-left ...">`,`@click="open(a)"`),保留图标 chip + 类型 tag + 置顶标记 + 标题;正文从「整段内联」改为**单行预览**(标题下 `line-clamp-1 text-xs text-ink-3`,取 body 纯文本首行/截断)。
  - 选中项 → 打开 `AnnouncementDetailModal`(新组件)。
- 新 `src/features/dashboard/AnnouncementDetailModal.vue`:
  - 用现有 `Modal`(`size="lg"`,已 `min(92vw,X)` + `max-h-90vh` 响应式滚动)。
  - 头部:类型 tag(颜色/图标)+ 标题 + 置顶标记 + 起止时间(若有,用本地化格式)。
  - 正文:`<div class="markdown-body" v-html="renderMarkdown(pick(a,'body'))">`(已净化)。
  - 关闭按钮 / 点遮罩关闭(Modal 既有行为)。

### 管理端表单:放大 + 实时预览
- `src/features/admin/announcements/AnnouncementFormModal.vue`:
  - Modal `size` 由 `lg`→`xl`(60rem;仍 `min(92vw,…)` 响应式)。
  - 正文 zh/en:textarea 由 `h-24` 放大(如 `h-56`/`min-h-[14rem]`,可纵向 resize);右侧(宽屏 `md:` 起)并排**实时预览** `.markdown-body`(`renderMarkdown`),窄屏堆叠或加「编辑/预览」切换(用简单本地 `tab` 状态;窄屏默认编辑,可切预览)。
  - 加「支持 Markdown(含表格)」提示文案(i18n)。
  - 其余字段不变(标题、类型、置顶、启用),时间字段改用 Part 4 的选择器。

---

## Part 4:自定义主题化日期时间选择器

**File:** 新 `src/lib/ui/DateTimePicker.vue`;改 `AnnouncementFormModal.vue`(替换两个原生 `datetime-local`)。

- 组件接口:
  - `modelValue: string`(本地 `YYYY-MM-DDTHH:MM`,空串表示未选);`@update:modelValue`。
  - props:`placeholder?`、`clearable?`(默认 true,起止时间可空)、`disabled?`。
- UI:
  - 触发器:一个主题化按钮/输入框,显示已选时间(本地化格式)或 placeholder + 日历图标(lucide `Calendar`)。
  - 弹层:reka-ui `Popover`(项目已用 reka-ui),内含:
    - 月历网格(周一/周日起首——与站点 i18n 一致,沿用 `dashboard.weekday.*`);上/下月切换;高亮选中日 + 今日。
    - 时间:时(0-23)+ 分(步进 5 或 1)两个 `Select`/滚动列或 NumberInput;采用既有 `Select`/`NumberInput` UI 组件以保持主题一致。
    - 「清除」「今天/现在」「确定」操作(简洁)。
  - glassmorphism 主题:`GlassCard`/既有 token(border、bg-surface、primary)。
- 日期数学用浏览器原生 `Date`(应用代码内允许;workflow 脚本限制不适用此处);注意时区——值始终按**本地**年月日时分拼接 `YYYY-MM-DDTHH:MM`,与表单现有 `toLocalInput/fromLocalInput` 逻辑一致(复用或对齐)。
- 替换:`AnnouncementFormModal` 的 startsAt/endsAt 两个原生输入 → 两个 `<DateTimePicker v-model=...>`,绑定与提交逻辑保持不变(仍是本地字符串 ↔ ISO 转换)。

---

## 测试策略

- **后端:**
  - `OracleMigrationTest`=16(V16 drop 已应用)。
  - 删除 `QuickLinkControllerTest`;确认无其他引用 quicklink 的测试/代码残留(编译通过)。
  - `mvn -q test` 全绿。
- **前端:**
  - i18n zh/en 对齐(删除 QL keys 后仍 parity)。
  - `markdown.ts` 单测:表格 Markdown → 含 `<table>`;`<script>alert</script>` / `<img>` / `onerror=` / `javascript:` 链接 → 被净化移除;普通强调/链接保留且 `<a>` 带 `rel`。
  - `DateTimePicker` 单测:选日期+时间 emit 正确 `YYYY-MM-DDTHH:MM`;clear emit 空串;传入值正确回显。
  - `AnnouncementsPanel` 单测:点击行打开详情弹窗(detail modal 出现、渲染标题/正文)。
  - 删除 QL 相关 spec。
  - `npm test` + `npm run build` 绿。

## 自检 / 一致性

- 移除 QL:前后端文件全删 + V16 drop + i18n keys 两端删除;`OracleMigrationTest`=16;无残留引用(编译/构建即验证)。
- Part 2:右列仅 Duty;信息行定高 `clamp(18rem,42vh,26rem)`,两卡 `h-full` 内部滚动 + 细滚动条;值班空态占位;`infoPanelEnabled` 门控不变;移动端降级。
- Part 3:`markdown-it`+`dompurify`;表格保留 / 图片+脚本净化;首页行可点击 → 详情弹窗(响应式);admin 表单 xl + 放大 textarea + 实时预览;`.markdown-body` 共用。
- Part 4:自建 `DateTimePicker`(reka-ui Popover + 既有 UI 组件),v-model `YYYY-MM-DDTHH:MM` 兼容,替换原生输入,主题一致。
- 无 schema 变更于公告(Markdown 即文本)。
