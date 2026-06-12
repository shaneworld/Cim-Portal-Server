# 公告 Markdown / 信息栏定高 / 日期选择器 / 移除快捷链接 实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development. 提交前 `mvn -q test` / `npm test` 须绿。commit trailer:`Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>`。

**Goal:** 撤销快捷链接;公告支持 Markdown(表格,无图片)+ 首页点击详情弹窗 + 管理端响应式表单与实时预览;信息栏定高、值班区滚动条;自建主题化日期时间选择器替换原生 datetime-local。

**Architecture:** 后端仅删 quicklink 包 + V16 DROP 迁移。前端:删除快捷链接全部文件/引用/i18n;新增 `markdown.ts`(markdown-it + DOMPurify 净化,留表格去图片)、`AnnouncementDetailModal.vue`、`DateTimePicker.vue`;改造 `AnnouncementsPanel`(行可点击→详情)、`AnnouncementFormModal`(xl + 放大 + 实时预览 + 新日期组件)、`HomeView`/两面板(定高 + 内滚)。

**Tech Stack:** Spring Boot 3.3/JDK 21/Oracle/Flyway;Vue 3.5 + TS + Tailwind v3 + reka-ui + vue-i18n;新增 `markdown-it`、`dompurify`。

仓库:后端 `/home/shane/Code/cim-portal/cim-portal-server`,前端 `/home/shane/Code/cim-portal/cim-portal-client`。

> 周历起始:用**周日**起首,与现有 `dashboard.weekday`(sun, mon, ... sat)顺序一致。

---

## 单元 A:后端 —— 移除快捷链接 + V16 DROP

**Files:**
- Delete: `src/main/java/com/cimportal/quicklink/`(整包:QuickLink, QuickLinkRepository, QuickLinkService, QuickLinkPortalController, QuickLinkAdminController, dto/QuickLinkRequest, dto/QuickLinkResponse)
- Delete: `src/test/java/com/cimportal/quicklink/QuickLinkControllerTest.java`
- Create: `src/main/resources/db/migration/oracle/V16__drop_quick_link.sql`
- Modify: `src/test/java/com/cimportal/migration/OracleMigrationTest.java`(15→16)

- [ ] **Step 1: 写 V16 迁移**

`V16__drop_quick_link.sql`:
```sql
DROP TABLE quick_link CASCADE CONSTRAINTS;
```

- [ ] **Step 2: 删除 quicklink 包与测试**

```bash
git rm -r src/main/java/com/cimportal/quicklink
git rm src/test/java/com/cimportal/quicklink/QuickLinkControllerTest.java
```
然后 `grep -rn "quicklink\|QuickLink\|quick_link\|quick-links" src/main src/test` 确认无残留引用(应只剩 V14 迁移文件本身,保留)。

- [ ] **Step 3: 更新 OracleMigrationTest 15→16**

把断言迁移数 `15` 改为 `16`。
Run: `mvn -q -Dtest=OracleMigrationTest test`
Expected: 通过,日志 "Successfully applied ... now at version v16"(测试 schema 从零跑 V1..V16,含 V14 create + V16 drop)。

- [ ] **Step 4: 全测**

Run: `mvn -q test`
Expected: 绿;无 quicklink 测试;无编译残留。

- [ ] **Step 5: 提交**

```bash
git add -A
git commit -m "revert(quick-links): 移除快捷链接后端(V16 drop quick_link)

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## 单元 B:前端 —— 移除快捷链接(全部引用 + i18n)

**Files:**
- Delete: `src/lib/api/quickLinks.ts`、`src/features/dashboard/QuickLinksPanel.vue`、`src/features/admin/quick-links/QuickLinksAdminView.vue`、`src/features/admin/quick-links/QuickLinkFormModal.vue`、任何 `QuickLink*.spec.ts`
- Modify: `src/lib/api/portal.ts`、`src/features/dashboard/HomeView.vue`、`src/router/index.ts`、`src/features/admin/AdminLayout.vue`、`src/lib/i18n/locales/zh.ts`、`en.ts`

- [ ] **Step 1: 删除快捷链接文件**

```bash
git rm src/lib/api/quickLinks.ts src/features/dashboard/QuickLinksPanel.vue
git rm -r src/features/admin/quick-links
# 若存在 QL 相关 spec:
git rm src/features/dashboard/QuickLinksPanel.spec.ts 2>/dev/null || true
```

- [ ] **Step 2: 解除 portal.ts 引用**

`src/lib/api/portal.ts`:删除 `import type { QuickLink } from './quickLinks'`(或具名 import)与 `export const listQuickLinks = ...` 一行。

- [ ] **Step 3: HomeView 去除 QuickLinksPanel**

`HomeView.vue`:删除 `import QuickLinksPanel from './QuickLinksPanel.vue'` 与模板中的 `<QuickLinksPanel />`。右列暂时仅留 `<DutyLinesPanel />`(布局定高在单元 D 完成,这里先保证编译通过):
```vue
<div v-if="config.infoPanelEnabled" class="grid gap-4 md:[grid-template-columns:1.95fr_1fr]">
  <AnnouncementsPanel />
  <DutyLinesPanel />
</div>
```

- [ ] **Step 4: 路由 + 导航去除**

`src/router/index.ts`:删除 `{ path: 'quick-links', ... }` 路由项。
`src/features/admin/AdminLayout.vue`:删除 `quickLinks` 导航项;若 `ExternalLink` 图标 import 在该文件已无其他用处则一并删除(`grep ExternalLink src/features/admin/AdminLayout.vue`)。

- [ ] **Step 5: 删除 i18n keys(两端对齐)**

`zh.ts` 与 `en.ts` 同步删除:`admin.nav.quickLinks`、`admin.quickLinks`(整块)、`admin.quickLinkForm`(整块)、`dashboard.quickLinks`(整块)。

- [ ] **Step 6: 测 + build**

Run: `npm test`(i18n parity 必须仍绿——两端都删干净)。
Run: `npm run build`(无悬空 import/类型错误)。
若有测试引用已删组件,删除对应 spec。

- [ ] **Step 7: 提交**

```bash
git add -A
git commit -m "revert(quick-links): 移除快捷链接前端(面板/管理页/路由/导航/i18n)

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## 单元 C:前端 —— Markdown 渲染工具 + 样式

**Files:**
- Modify: `package.json`(加依赖)
- Create: `src/lib/ui/markdown.ts`
- Create: `src/lib/ui/markdown.spec.ts`
- Modify: `src/assets/index.css`(加 `.markdown-body` + `.scroll-slim` 样式)

- [ ] **Step 1: 装依赖**

Run:
```bash
npm install markdown-it dompurify
npm install -D @types/markdown-it
```
(`dompurify` v3 自带类型,无需 @types;若 tsc 报缺类型再加 `@types/dompurify`。)
确认 `package.json` dependencies 含 `markdown-it`、`dompurify`。

- [ ] **Step 2: 写 markdown.spec.ts(失败测试)**

`src/lib/ui/markdown.spec.ts`:
```ts
import { describe, it, expect } from 'vitest'
import { renderMarkdown } from './markdown'

describe('renderMarkdown', () => {
  it('renders tables', () => {
    const md = '| A | B |\n| - | - |\n| 1 | 2 |'
    const html = renderMarkdown(md)
    expect(html).toContain('<table')
    expect(html).toContain('<td')
  })
  it('strips images', () => {
    const html = renderMarkdown('![x](http://e.com/a.png)')
    expect(html).not.toContain('<img')
  })
  it('strips script and event handlers', () => {
    const html = renderMarkdown('<script>alert(1)</script> hi')
    expect(html).not.toContain('<script')
    expect(html.toLowerCase()).not.toContain('onerror')
  })
  it('keeps links but adds rel and blocks javascript: protocol', () => {
    expect(renderMarkdown('[ok](https://e.com)')).toContain('rel="noopener noreferrer"')
    const bad = renderMarkdown('[x](javascript:alert(1))')
    expect(bad.toLowerCase()).not.toContain('javascript:')
  })
  it('returns empty string for blank input', () => {
    expect(renderMarkdown('   ')).toBe('')
    expect(renderMarkdown('')).toBe('')
  })
})
```
Run: `npm test -- markdown` → 红(模块不存在)。

- [ ] **Step 3: 实现 markdown.ts**

`src/lib/ui/markdown.ts`:
```ts
import MarkdownIt from 'markdown-it'
import DOMPurify from 'dompurify'

const md = new MarkdownIt({ html: false, linkify: true, breaks: true })
// markdown-it 默认启用表格(GFM table)。

// 强制外链安全属性
DOMPurify.addHook('afterSanitizeAttributes', (node) => {
  if (node.tagName === 'A') {
    node.setAttribute('target', '_blank')
    node.setAttribute('rel', 'noopener noreferrer')
  }
})

const PURIFY_CONFIG = {
  ALLOWED_TAGS: [
    'p','br','hr','h1','h2','h3','h4','h5','h6',
    'strong','em','del','blockquote','ul','ol','li',
    'code','pre','a','table','thead','tbody','tr','th','td',
  ],
  ALLOWED_ATTR: ['href','title','align','target','rel'],
  ALLOWED_URI_REGEXP: /^(?:https?:|mailto:|\/|#)/i,
} as const

export function renderMarkdown(src: string): string {
  if (!src || !src.trim()) return ''
  const rawHtml = md.render(src)
  return DOMPurify.sanitize(rawHtml, PURIFY_CONFIG as any)
}
```
(注:`ALLOWED_ATTR` 含 target/rel 以便 hook 设置后不被剔除;`img` 不在 ALLOWED_TAGS,自动剥离。)
Run: `npm test -- markdown` → 绿。

- [ ] **Step 4: 加 `.markdown-body` + `.scroll-slim` 样式**

`src/assets/index.css` 追加(用项目既有 CSS 变量/Tailwind 颜色,保持深浅色一致):
```css
.markdown-body { @apply text-sm leading-relaxed text-ink-1; }
.markdown-body h1,.markdown-body h2,.markdown-body h3 { @apply font-bold mt-3 mb-1.5; }
.markdown-body p { @apply my-1.5; }
.markdown-body ul { @apply list-disc pl-5 my-1.5; }
.markdown-body ol { @apply list-decimal pl-5 my-1.5; }
.markdown-body a { @apply text-primary underline; }
.markdown-body code { @apply rounded bg-surface/60 px-1 py-0.5 text-[0.85em]; }
.markdown-body pre { @apply rounded-lg bg-surface/60 p-3 overflow-x-auto my-2; }
.markdown-body blockquote { @apply border-l-2 border-border pl-3 text-ink-2 my-2; }
.markdown-body table { @apply w-full border-collapse my-2 text-sm; }
.markdown-body th,.markdown-body td { @apply border border-border px-2 py-1 text-left; }
.markdown-body thead th { @apply bg-surface/60 font-semibold; }
.markdown-body tbody tr:nth-child(even) { @apply bg-surface/30; }

.scroll-slim { scrollbar-width: thin; scrollbar-color: rgb(148 163 184 / 0.5) transparent; }
.scroll-slim::-webkit-scrollbar { width: 6px; height: 6px; }
.scroll-slim::-webkit-scrollbar-thumb { @apply rounded-full bg-border; }
.scroll-slim::-webkit-scrollbar-track { background: transparent; }
```
(若 `text-ink-1`/`bg-surface`/`border-border` 等不是项目 token,改用实际 token——先看 index.css 与 tailwind.config.js 现有命名再写。)
Run: `npm run build` → 成功(@apply 引用的类均存在)。

- [ ] **Step 5: 提交**

```bash
git add -A
git commit -m "feat(markdown): 公告 Markdown 渲染工具(markdown-it+DOMPurify,留表格去图片)+ .markdown-body/.scroll-slim 样式

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## 单元 D:前端 —— 信息栏定高 + 值班滚动条 + 公告卡内滚

**Files:**
- Modify: `src/features/dashboard/HomeView.vue`、`src/features/dashboard/AnnouncementsPanel.vue`、`src/features/dashboard/DutyLinesPanel.vue`
- Modify: `src/lib/i18n/locales/zh.ts`、`en.ts`(加 `dashboard.dutyLines.empty`)

- [ ] **Step 1: 加 i18n `dashboard.dutyLines.empty`(两端)**

`zh.ts`:`dashboard.dutyLines` 加 `empty: '暂无值班信息'`。
`en.ts`:`empty: 'No duty info'`。
(`dashboard.dutyLines.title` 已存在。)

- [ ] **Step 2: HomeView 信息行定高**

`HomeView.vue` 信息行:
```vue
<div v-if="config.infoPanelEnabled"
     class="grid gap-4 md:[grid-template-columns:1.95fr_1fr] md:h-[clamp(18rem,42vh,26rem)]">
  <AnnouncementsPanel class="min-h-0" />
  <DutyLinesPanel class="min-h-0" />
</div>
```
(`md:h-[...]` 仅桌面定高;移动端不强制等高。两面板加 `min-h-0` 以允许内部滚动。)

- [ ] **Step 3: AnnouncementsPanel 改为 flex 列 + 内滚**

`AnnouncementsPanel.vue`:`GlassCard` 加 `class="p-4 flex flex-col h-full"`(原有 class 合并);标题 `<h2>` 保持;把原 `max-h-[min(22rem,60vh)] overflow-y-auto`(line 30 容器)改为 `flex-1 min-h-0 overflow-y-auto scroll-slim`(由父级定高驱动,自身撑满)。空占位与列表仍在该滚动容器内。
(移动端:因 HomeView 仅 `md:h-` 定高,移动端 `h-full` 在无固定高父级时塌缩——为安全,给该滚动容器同时保留移动端上限:`max-h-[60vh] md:max-h-none flex-1`。)

- [ ] **Step 4: DutyLinesPanel 改为 flex 列 + 内滚 + 空态占位**

`DutyLinesPanel.vue`:
- 现为 `v-if="cfg.config.infoPanelEnabled && lines.length > 0"` 才渲染卡片 → 改为 `v-if="cfg.config.infoPanelEnabled"` 始终渲染卡片(因现在是定高行的固定一栏);`lines.length===0` 时在列表区显示 `t('dashboard.dutyLines.empty')` 居中占位。
- `GlassCard` 加 `flex flex-col h-full`;标题固定;列表 `space-y-2` 外层包 `flex-1 min-h-0 overflow-y-auto scroll-slim`(同样加移动端 `max-h-[60vh] md:max-h-none`)。
- 保留行内容(部门 label · dutyName · phone)与 `data-testid` 不变。
示意:
```vue
<template v-if="cfg.config.infoPanelEnabled">
  <GlassCard class="p-4 flex flex-col h-full">
    <h2 class="mb-3 flex items-center gap-2 text-sm font-semibold text-ink-2">
      <Phone class="size-4 shrink-0" />{{ t('dashboard.dutyLines.title') }}
    </h2>
    <div class="flex-1 min-h-0 overflow-y-auto scroll-slim max-h-[60vh] md:max-h-none">
      <div v-if="lines.length === 0" class="py-4 text-center text-sm text-ink-3">
        {{ t('dashboard.dutyLines.empty') }}
      </div>
      <div v-else class="space-y-2">
        <!-- 现有行 v-for ... 不变 -->
      </div>
    </div>
  </GlassCard>
</template>
```

- [ ] **Step 5: 测 + build**

Run: `npm test`(若 DutyLinesPanel.spec 断言「空数据隐藏整卡」需改为「显示空占位」)。
Run: `npm run build`。

- [ ] **Step 6: 提交**

```bash
git add -A
git commit -m "feat(dashboard): 信息栏桌面定高 + 公告/值班卡内部滚动条 + 值班空态占位

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## 单元 E:前端 —— 公告首页详情弹窗(点击打开,Markdown 渲染)

**Files:**
- Create: `src/features/dashboard/AnnouncementDetailModal.vue`
- Modify: `src/features/dashboard/AnnouncementsPanel.vue`
- Test: `src/features/dashboard/AnnouncementsPanel.spec.ts`(新增或扩展)
- Modify: `src/lib/i18n/locales/zh.ts`、`en.ts`(如需 `dashboard.announcements.detailTitle` 等,可选)

- [ ] **Step 1: 写 AnnouncementsPanel 点击→弹窗的失败测试**

`AnnouncementsPanel.spec.ts`(参照现有 dashboard spec 的 mount + mock listAnnouncements 风格):
```ts
// mock listAnnouncements 返回一条 {id:1, titleZh:'标题A', bodyZh:'| A | B |\n|-|-|\n|1|2|', typeColor, typeIcon, ...}
// 挂载后点击该公告行(button) → AnnouncementDetailModal 出现,渲染 '标题A' 且 body 含 <table>
```
具体:`await w.find('[data-testid="announcement-row-1"]').trigger('click')`;断言 detail modal 文本含标题、`w.html()` 含 `<table`。
Run: `npm test -- AnnouncementsPanel` → 红。

- [ ] **Step 2: 创建 AnnouncementDetailModal.vue**

```vue
<script setup lang="ts">
import { Pin } from 'lucide-vue-next'
import Modal from '@/lib/ui/Modal.vue'
import AppIcon from '@/lib/ui/AppIcon.vue'
import type { Announcement } from '@/lib/api/announcements'
import { useLocale } from '@/lib/i18n/useLocale'
import { colorClasses } from '@/lib/ui/announcementColor'
import { renderMarkdown } from '@/lib/ui/markdown'

const props = defineProps<{ open: boolean; announcement: Announcement | null }>()
const emit = defineEmits<{ (e: 'update:open', v: boolean): void }>()
const { pick, t } = useLocale()
</script>

<template>
  <Modal :open="open" size="lg" @update:open="emit('update:open', $event)"
         :title="announcement ? pick(announcement, 'title') : ''">
    <div v-if="announcement">
      <div class="mb-3 flex flex-wrap items-center gap-1.5">
        <span class="flex size-6 items-center justify-center rounded-lg" :class="colorClasses(announcement.typeColor).chip">
          <AppIcon :name="announcement.typeIcon" class="size-3.5" />
        </span>
        <span class="rounded px-1.5 py-0.5 text-[11px] font-medium" :class="colorClasses(announcement.typeColor).tag">
          {{ pick(announcement, 'typeLabel') }}
        </span>
        <span v-if="announcement.pinned" class="flex items-center gap-0.5 text-[11px] text-ink-3">
          <Pin class="size-3" />{{ t('dashboard.announcements.pinned') }}
        </span>
      </div>
      <div class="markdown-body" v-html="renderMarkdown(pick(announcement, 'body'))"></div>
    </div>
  </Modal>
</template>
```
(确认 `Modal` 的 props 名:open + update:open + title + size——按单元 0 探查结果,Modal 用 `:open`/`@update:open`?核对 Modal.vue 实际 props;若是 `modelValue` 则相应调整。`Announcement` 类型与 `pick`/`colorClasses`/`AppIcon` 沿用 AnnouncementsPanel 既有 import。)

- [ ] **Step 3: AnnouncementsPanel 行可点击 + 接入弹窗**

`AnnouncementsPanel.vue`:
- 引入 `import AnnouncementDetailModal from './AnnouncementDetailModal.vue'`;状态 `const selected = ref<Announcement|null>(null); const detailOpen = ref(false)`;`function open(a){ selected.value=a; detailOpen.value=true }`。
- 每条公告外层由 `<div>` 改为 `<button type="button" :data-testid="\`announcement-row-${a.id}\`" class="w-full text-left ..."(原 class)" @click="open(a)">`。
- 正文从整段内联改为单行预览:把 `<p class="mt-0.5 whitespace-pre-line text-xs text-ink-2">{{ pick(a,'body') }}</p>` 改为 `<p class="mt-0.5 line-clamp-1 text-xs text-ink-3">{{ pick(a,'body') }}</p>`(纯文本截断;Markdown 源在预览里只显示首行即可,详情弹窗看完整渲染)。
- 模板末尾加 `<AnnouncementDetailModal v-model:open="detailOpen" :announcement="selected" />`。

- [ ] **Step 4: 测**

Run: `npm test -- AnnouncementsPanel` → 绿。
Run: `npm test`(整体)+ `npm run build`。

- [ ] **Step 5: 提交**

```bash
git add -A
git commit -m "feat(announcements): 首页公告可点击打开详情弹窗(Markdown 渲染,响应式)

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## 单元 F:前端 —— 自定义主题化日期时间选择器

**Files:**
- Create: `src/lib/ui/DateTimePicker.vue`
- Test: `src/lib/ui/DateTimePicker.spec.ts`
- (在单元 G 接入 AnnouncementFormModal)

- [ ] **Step 1: 写 DateTimePicker 失败测试**

`src/lib/ui/DateTimePicker.spec.ts`:
```ts
import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import DateTimePicker from './DateTimePicker.vue'

describe('DateTimePicker', () => {
  it('renders trigger with placeholder when empty', () => {
    const w = mount(DateTimePicker, { props: { modelValue: '', placeholder: '选择时间' } })
    expect(w.text()).toContain('选择时间')
  })
  it('shows the bound value in the trigger', () => {
    const w = mount(DateTimePicker, { props: { modelValue: '2026-06-12T09:30' } })
    expect(w.text()).toContain('2026')
    expect(w.text()).toContain('09:30')
  })
  it('emits empty string when cleared', async () => {
    const w = mount(DateTimePicker, { props: { modelValue: '2026-06-12T09:30', clearable: true } })
    await w.find('[data-testid="dtp-clear"]').trigger('click')
    expect(w.emitted('update:modelValue')?.at(-1)).toEqual([''])
  })
})
```
Run: `npm test -- DateTimePicker` → 红。

- [ ] **Step 2: 实现 DateTimePicker.vue**

接口:`modelValue: string`(本地 `YYYY-MM-DDTHH:MM`,空=未选)、`placeholder?`、`clearable?`(默认 true)、`disabled?`;emit `update:modelValue`。
要点(完整组件,使用 reka-ui Popover + 既有 Select/NumberInput;周日起首,沿用 `dashboard.weekday`):
```vue
<script setup lang="ts">
import { computed, ref } from 'vue'
import { Calendar, X, ChevronLeft, ChevronRight } from 'lucide-vue-next'
import { PopoverRoot, PopoverTrigger, PopoverPortal, PopoverContent } from 'reka-ui'
import { useLocale } from '@/lib/i18n/useLocale'

const props = withDefaults(defineProps<{
  modelValue: string; placeholder?: string; clearable?: boolean; disabled?: boolean
}>(), { placeholder: '', clearable: true, disabled: false })
const emit = defineEmits<{ (e: 'update:modelValue', v: string): void }>()
const { t } = useLocale()

const open = ref(false)
// 解析 modelValue → 年/月/日/时/分;空则默认今天 09:00 作为视图(不写回，直到用户选择)
function parse(v: string) {
  const m = /^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2})$/.exec(v)
  if (!m) return null
  return { y:+m[1], mo:+m[2]-1, d:+m[3], h:+m[4], mi:+m[5] }
}
const parsed = computed(() => parse(props.modelValue))
const display = computed(() => {
  const p = parsed.value; if (!p) return ''
  const pad = (n:number)=>String(n).padStart(2,'0')
  return `${p.y}-${pad(p.mo+1)}-${pad(p.d)} ${pad(p.h)}:${pad(p.mi)}`
})

// 视图月份(以选中或今天初始化)
const today = new Date()
const viewY = ref(parsed.value?.y ?? today.getFullYear())
const viewMo = ref(parsed.value?.mo ?? today.getMonth())
const selHour = ref(parsed.value?.h ?? 9)
const selMin  = ref(parsed.value?.mi ?? 0)

const weekdayKeys = ['sun','mon','tue','wed','thu','fri','sat'] as const
const grid = computed(() => {
  const first = new Date(viewY.value, viewMo.value, 1)
  const startDow = first.getDay() // 0=Sun
  const daysInMonth = new Date(viewY.value, viewMo.value+1, 0).getDate()
  const cells: ({d:number}|null)[] = []
  for (let i=0;i<startDow;i++) cells.push(null)
  for (let d=1; d<=daysInMonth; d++) cells.push({ d })
  return cells
})
function prevMonth(){ if(viewMo.value===0){viewMo.value=11;viewY.value--}else viewMo.value-- }
function nextMonth(){ if(viewMo.value===11){viewMo.value=0;viewY.value++}else viewMo.value++ }

function isSelected(d:number){ const p=parsed.value; return !!p && p.y===viewY.value && p.mo===viewMo.value && p.d===d }
function isToday(d:number){ return today.getFullYear()===viewY.value && today.getMonth()===viewMo.value && today.getDate()===d }

const pad = (n:number)=>String(n).padStart(2,'0')
function pick(d:number){
  const v = `${viewY.value}-${pad(viewMo.value+1)}-${pad(d)}T${pad(selHour.value)}:${pad(selMin.value)}`
  emit('update:modelValue', v); open.value=false
}
function applyTime(){ // 改时分后若已选日期则即时回写
  const p=parsed.value; if(!p) return
  emit('update:modelValue', `${p.y}-${pad(p.mo+1)}-${pad(p.d)}T${pad(selHour.value)}:${pad(selMin.value)}`)
}
function clear(){ emit('update:modelValue',''); open.value=false }
function setToday(){ const n=new Date(); selHour.value=n.getHours(); selMin.value=n.getMinutes()
  emit('update:modelValue', `${n.getFullYear()}-${pad(n.getMonth()+1)}-${pad(n.getDate())}T${pad(n.getHours())}:${pad(n.getMinutes())}`); open.value=false }
</script>

<template>
  <PopoverRoot v-model:open="open">
    <PopoverTrigger as-child :disabled="disabled">
      <button type="button" :disabled="disabled"
        class="flex h-10 w-full items-center gap-2 rounded-xl border border-input bg-transparent px-3 text-left text-sm outline-none focus-visible:ring-2 focus-visible:ring-ring disabled:opacity-50">
        <Calendar class="size-4 shrink-0 text-ink-3" />
        <span class="flex-1 truncate" :class="display ? '' : 'text-ink-3'">{{ display || placeholder }}</span>
        <X v-if="clearable && display" data-testid="dtp-clear" class="size-4 shrink-0 text-ink-3 hover:text-ink-1"
           @click.stop="clear" />
      </button>
    </PopoverTrigger>
    <PopoverPortal>
      <PopoverContent align="start" :side-offset="6"
        class="z-50 w-[18rem] rounded-2xl border border-border bg-white p-3 shadow-2xl dark:bg-[#141b2e]">
        <!-- 月份导航 -->
        <div class="mb-2 flex items-center justify-between">
          <button type="button" class="rounded-lg p-1 hover:bg-surface/60" @click="prevMonth"><ChevronLeft class="size-4"/></button>
          <span class="text-sm font-semibold">{{ viewY }}-{{ pad(viewMo+1) }}</span>
          <button type="button" class="rounded-lg p-1 hover:bg-surface/60" @click="nextMonth"><ChevronRight class="size-4"/></button>
        </div>
        <!-- 星期表头 -->
        <div class="grid grid-cols-7 text-center text-[11px] text-ink-3">
          <span v-for="k in weekdayKeys" :key="k">{{ t('dashboard.weekday.' + k) }}</span>
        </div>
        <!-- 日期网格 -->
        <div class="mt-1 grid grid-cols-7 gap-0.5">
          <template v-for="(c,i) in grid" :key="i">
            <span v-if="!c"></span>
            <button v-else type="button"
              class="rounded-lg py-1 text-sm hover:bg-primary/10"
              :class="[isSelected(c.d) ? 'bg-primary text-white hover:bg-primary' : '', isToday(c.d) && !isSelected(c.d) ? 'ring-1 ring-primary/40' : '']"
              @click="pick(c.d)">{{ c.d }}</button>
          </template>
        </div>
        <!-- 时间 + 操作 -->
        <div class="mt-3 flex items-center gap-2 border-t border-border pt-3">
          <select v-model.number="selHour" class="h-8 rounded-lg border border-input bg-transparent px-2 text-sm" @change="applyTime">
            <option v-for="h in 24" :key="h" :value="h-1">{{ pad(h-1) }}</option>
          </select>
          <span>:</span>
          <select v-model.number="selMin" class="h-8 rounded-lg border border-input bg-transparent px-2 text-sm" @change="applyTime">
            <option v-for="m in 60" :key="m" :value="m-1">{{ pad(m-1) }}</option>
          </select>
          <div class="ml-auto flex gap-1">
            <button v-if="clearable" type="button" class="rounded-lg px-2 py-1 text-xs text-ink-2 hover:bg-surface/60" @click="clear">{{ t('common.cancel') }}</button>
            <button type="button" class="rounded-lg bg-primary px-2 py-1 text-xs text-white" @click="setToday">{{ t('ui.datepicker.now') }}</button>
          </div>
        </div>
      </PopoverContent>
    </PopoverPortal>
  </PopoverRoot>
</template>
```
(若 reka-ui 的 Popover 导出名/用法与项目其他用例不同,先 `grep -rn "reka-ui" src/lib/ui src/features | grep -i popover` 看现有用法对齐;时分用原生 `<select>` 即可,主题化样式已加。`common.cancel` 已存在;新增 `ui.datepicker.now`。)

- [ ] **Step 3: 加 i18n `ui.datepicker.now`(两端)**

`zh.ts` `ui` 块加 `datepicker: { now: '此刻' }`;`en.ts` `datepicker: { now: 'Now' }`。

- [ ] **Step 4: 测**

Run: `npm test -- DateTimePicker` → 绿。
Run: `npm run build`。

- [ ] **Step 5: 提交**

```bash
git add -A
git commit -m "feat(ui): 自定义主题化日期时间选择器(reka-ui Popover + 月历 + 时分)

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## 单元 G:前端 —— 公告管理表单:xl + 放大正文 + 实时预览 + 接入日期组件

**Files:**
- Modify: `src/features/admin/announcements/AnnouncementFormModal.vue`
- Modify: `src/lib/i18n/locales/zh.ts`、`en.ts`(加 `admin.announcementForm.markdownHint`、`preview` 等)

- [ ] **Step 1: Modal xl + 放大正文 textarea + 并排预览**

`AnnouncementFormModal.vue`:
- Modal `size="lg"` → `size="xl"`。
- bodyZh / bodyEn 区:textarea 高度 `h-24` → `min-h-[14rem]`(保留 `resize-y`);用一个两列布局(宽屏 `md:grid md:grid-cols-2 md:gap-3`):左 textarea,右 `.markdown-body` 实时预览 `v-html="renderMarkdown(form.bodyZh)"`(zh、en 各一组)。窄屏:预览置于 textarea 下方(默认堆叠即可,无需切换;若想省空间可加本地 `tab`——本计划用堆叠,YAGNI)。
- 引入 `import { renderMarkdown } from '@/lib/ui/markdown'`。
- 在正文标签旁加提示 `{{ t('admin.announcementForm.markdownHint') }}`(「支持 Markdown,含表格」)。
- 预览容器加 `max-h-[14rem] overflow-y-auto scroll-slim rounded-xl border border-input p-2`,空内容时显示淡灰提示 `t('admin.announcementForm.preview')`。

- [ ] **Step 2: 接入 DateTimePicker 替换原生 datetime-local**

`AnnouncementFormModal.vue`:
- `import DateTimePicker from '@/lib/ui/DateTimePicker.vue'`。
- startsAt / endsAt 的 `<input type="datetime-local" ...>` 替换为 `<DateTimePicker v-model="form.startsAt" :placeholder="t('admin.announcementForm.startsAtLabel')" />`(endsAt 同理)。
- 表单 state 现已是本地 `YYYY-MM-DDTHH:MM` 字符串(line 35-44 的 toLocalInput/fromLocalInput),与 DateTimePicker 的 modelValue 直接兼容——绑定/提交逻辑不变。

- [ ] **Step 3: i18n(两端)**

`zh.ts` `admin.announcementForm` 加:`markdownHint: '支持 Markdown(含表格)'`、`preview: '预览'`。
`en.ts`:`markdownHint: 'Markdown supported (incl. tables)'`、`preview: 'Preview'`。

- [ ] **Step 4: 测 + build**

Run: `npm test`(i18n parity + 既有 announcement form 相关 spec;若有 spec 断言原生 datetime 输入存在,改为断言 DateTimePicker 存在或移除该断言)。
Run: `npm run build`。

- [ ] **Step 5: 提交**

```bash
git add -A
git commit -m "feat(announcements): 管理表单 xl + 放大正文 + Markdown 实时预览 + 主题化日期选择器

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## 单元 H:联调验证(无代码)

- [ ] **Step 1: 后端重启 + 迁移 V16**

build jar,`fuser -k 8080/tcp`(勿 pkill portal.jar),`java -jar target/portal.jar --spring.profiles.active=dev`,日志确认 "now at version v16",`quick_link` 表已 DROP。

- [ ] **Step 2: 端点冒烟**

- `GET /api/portal/quick-links` → 404(已移除)。
- `GET /api/portal/announcements`、`/api/portal/duty-lines` 正常。

- [ ] **Step 3: 前端目视(`npm run dev`)**

- 管理导航无「快捷链接」;`/admin/quick-links` 路由失效。
- 首页信息栏:无快捷链接;公告卡 + 值班卡桌面等高、各自滚动条;值班区按当日排班显示部门值班人+电话(外部 API 配好时)。
- 公告:列表行单行预览;点击 → 详情弹窗渲染完整 Markdown(造一条含表格的公告验证表格渲染、确认无图片/脚本注入)。
- 管理端新建/编辑公告:弹窗宽(xl)、正文区大、右侧实时预览随输入更新表格;起止时间用主题化日期选择器(月历 + 时分 + 清除/此刻);窗口缩放弹窗自适应。

---

## 自检(plan vs spec)

- **Part 1 移除 QL**:单元 A(后端删包 + V16 drop + OracleMigrationTest=16)、单元 B(前端删文件/引用/路由/导航/i18n)。✅
- **Part 2 定高 + 值班滚动条**:单元 D(HomeView `md:h-clamp`、两卡 flex 内滚 + `.scroll-slim`、值班空态)。✅ 值班「当日各部门值班人+电话经 API」上轮已实现,本轮不改数据逻辑。
- **Part 3 公告 Markdown + 详情弹窗 + 响应式表单+预览**:单元 C(渲染工具+样式,留表格去图片)、单元 E(首页点击→详情弹窗)、单元 G(表单 xl+放大+实时预览)。✅
- **Part 4 主题化日期选择器**:单元 F(DateTimePicker)、单元 G Step2(接入)。✅
- **净化**:单元 C markdown.ts(去 script/img/事件属性/javascript:,留表格);单测覆盖。✅
- **i18n 对齐**:B 删 keys、D/F/G 增 keys,两端同步。✅
- **类型一致**:`renderMarkdown(src:string):string`(C 定义,E/G 使用);`DateTimePicker` props `modelValue/placeholder/clearable/disabled` + emit `update:modelValue`(F 定义,G 使用);`AnnouncementDetailModal` props `open/announcement` + `update:open`(E)。✅
- **无 schema 变更于公告**(Markdown 即文本)。✅
- **Modal props 名**:E/G 使用前须核对 `Modal.vue` 实际是 `:open/@update:open` 还是 `modelValue`(探查见;实现时对齐)。
