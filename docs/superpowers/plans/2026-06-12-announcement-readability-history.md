# 公告可读性 + 自动关闭/历史 实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development. 提交前 `mvn -q test` / `npm test` 须绿。commit trailer:`Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>`。

**Goal:** 公告卡纯文本预览;详情弹窗放大(2xl)+ 正式可读排版 + 类型/图标尺寸统一;到期自动关闭(定时任务设 active=false + closedAt)+ 管理端「当前/历史」标签。

**Architecture:** 后端:V17 加 `closed_at`;`@EnableScheduling` + `AnnouncementService.closeExpired()`(用注入 Clock,5 分钟调度);Response 加 closedAt;update 重新启用清空 closedAt。前端:`markdown.ts` 加 `markdownToText`;AnnouncementsPanel 预览用之;Modal 加 2xl;详情弹窗放大+正式排版+统一头部尺寸+`.markdown-body-lg`;AnnouncementsAdminView 加当前/历史标签按 active 过滤。

**Tech Stack:** Spring Boot 3.3/JDK 21/Oracle/Flyway + Spring scheduling;Vue 3.5 + TS + Tailwind v3 + reka-ui + vue-i18n;既有 markdown-it/DOMPurify。

仓库:后端 `/home/shane/Code/cim-portal/cim-portal-server`,前端 `/home/shane/Code/cim-portal/cim-portal-client`。

---

## 单元 A:后端 —— V17 + 自动关闭 + 历史字段

**Files:**
- Create: `src/main/resources/db/migration/oracle/V17__announcement_closed_at.sql`
- Modify: `src/main/java/com/cimportal/announcement/Announcement.java`(加 closedAt)
- Modify: `src/main/java/com/cimportal/announcement/dto/AnnouncementResponse.java`(加 closedAt)
- Modify: `src/main/java/com/cimportal/announcement/AnnouncementService.java`(closeExpired + toResponse closedAt + update 清空 closedAt)
- Modify: `src/main/java/com/cimportal/announcement/AnnouncementRepository.java`(加按需查询)
- Create: `src/main/java/com/cimportal/announcement/AnnouncementScheduler.java`(@Scheduled 封装)
- Modify: `src/main/java/com/cimportal/CimPortalApplication.java`(@EnableScheduling)
- Modify: `src/test/java/com/cimportal/migration/OracleMigrationTest.java`(16→17)
- Test: `src/test/java/com/cimportal/announcement/AnnouncementCloseExpiredTest.java`(新);扩展 `AnnouncementControllerTest`(若存在)断言 closedAt 序列化 + 重新启用清空。

- [ ] **Step 1: 写 V17 迁移**

`V17__announcement_closed_at.sql`:
```sql
ALTER TABLE announcement ADD (closed_at TIMESTAMP);
```

- [ ] **Step 2: OracleMigrationTest 16→17,跑确认**

把断言迁移数 16 改 17。
Run: `mvn -q -Dtest=OracleMigrationTest test` → 通过("now at version v17")。

- [ ] **Step 3: 实体加 closedAt**

`Announcement.java` 加:
```java
@Column(name = "closed_at")
private Instant closedAt;
```
+ getter/setter(`getClosedAt`/`setClosedAt`)。

- [ ] **Step 4: AnnouncementResponse 加 closedAt**

读 `AnnouncementResponse.java`,在 record 末尾(createdAt 之后)加 `Instant closedAt`。更新 `AnnouncementService` 里构造 `AnnouncementResponse` 的 `toResponse(...)` 方法,把 `a.getClosedAt()` 传入对应位置(所有 new AnnouncementResponse(...) 调用同步加该实参)。

- [ ] **Step 5: 写 closeExpired 失败测试**

先读 `AnnouncementService` 现有构造(它注入了什么:repo、enumValueRepository 等;以及是否已注入 Clock——若未注入则本步加 `Clock` 注入,项目已有 `Clock` bean)。
`AnnouncementCloseExpiredTest.java`(用 `OracleIntegrationTest` 基类或 `@SpringBootTest`,注入 `AnnouncementService` 与 `AnnouncementRepository`;若要可控时间,优先把 `closeExpired` 写成接受/使用注入 Clock,并在测试用 `@MockBean Clock` 或固定 Clock——简单起见:让 `closeExpired()` 用注入的 `Clock`,测试用一个把 clock 固定到某时刻的方式;若难以 mock bean,则让测试直接构造数据:endsAt 设为「过去」相对真实 now)。
最简健壮写法(不依赖 mock clock):测试创建三条公告并调用 `service.closeExpired()`:
```java
// a1: active=true, endsAt = now-1h  → 应被关闭
// a2: active=true, endsAt = null    → 不变
// a3: active=true, endsAt = now+1h  → 不变
// a4: active=false, endsAt = now-1h → 不变(已停用)
int closed = service.closeExpired();
// 断言 closed == 1;重查 a1: active=false 且 closedAt != null;a2/a3 active=true closedAt null;a4 不变
```
(now 用 `Instant.now()` 取,endsAt 用相对偏移;closeExpired 内部用注入 Clock 的 instant(),与 Instant.now() 在测试时差异可忽略——用 ±1h 偏移留足余量。)
Run: `mvn -q -Dtest=AnnouncementCloseExpiredTest test` → 红(方法不存在)。

- [ ] **Step 6: 实现 closeExpired + update 清空 closedAt**

`AnnouncementService`:确保注入 `Clock clock`(构造器加参;Spring 注入既有 Clock bean)。新增:
```java
@org.springframework.transaction.annotation.Transactional
public int closeExpired() {
    Instant now = clock.instant();
    List<Announcement> expired = repo.findByActiveTrueAndEndsAtIsNotNullAndEndsAtBefore(now);
    for (Announcement a : expired) {
        a.setActive(false);
        a.setClosedAt(now);
    }
    repo.saveAll(expired);
    return expired.size();
}
```
`update(...)`:当请求把 active 由 false→true(重新启用)时清空 closedAt。最简稳妥:更新 active 后,若最终 active=true 则 `a.setClosedAt(null)`:
```java
a.setActive(req.activeOrDefault());
if (a.isActive()) a.setClosedAt(null);
```
(放在现有 update 设置字段处;保持其余逻辑不变。)

- [ ] **Step 7: Repository 查询**

`AnnouncementRepository.java` 加:
```java
List<Announcement> findByActiveTrueAndEndsAtIsNotNullAndEndsAtBefore(Instant cutoff);
```

- [ ] **Step 8: 实现通过**

Run: `mvn -q -Dtest=AnnouncementCloseExpiredTest test` → 绿。

- [ ] **Step 9: @EnableScheduling + Scheduler**

`CimPortalApplication.java`:在类上加 `@org.springframework.scheduling.annotation.EnableScheduling`(与 @SpringBootApplication 并列)。
新 `AnnouncementScheduler.java`:
```java
package com.cimportal.announcement;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class AnnouncementScheduler {
    private static final Logger log = LoggerFactory.getLogger(AnnouncementScheduler.class);
    private final AnnouncementService service;
    public AnnouncementScheduler(AnnouncementService service) { this.service = service; }

    @Scheduled(fixedDelayString = "300000", initialDelayString = "30000")
    public void closeExpired() {
        int n = service.closeExpired();
        if (n > 0) log.info("Auto-closed {} expired announcement(s)", n);
    }
}
```
(initialDelay 30s、fixedDelay 5min → 测试上下文(秒级)不会触发,无干扰。)

- [ ] **Step 10: controller 测试:closedAt 序列化 + 重新启用清空**

扩展现有 `AnnouncementControllerTest`(读它,沿用其风格/基类/JWT):
```java
// 建一条 active=false 且(可直接 update 设 active=false 后)→ 历史;断言 admin GET 返回含 closedAt 字段(可为 null)
// update 把某条 active 设回 true → 重查 closedAt == null
// (若便于:建一条 endsAt 过去 active=true,调用 service.closeExpired() 后 admin GET 该条 active=false, closedAt!=null)
```
若无该测试类,可在 `AnnouncementCloseExpiredTest` 内补充 update-清空 用例(active=false+closedAt 设值 → update active=true → closedAt null)。

- [ ] **Step 11: 全测 + 提交**

Run: `mvn -q test` → 绿(`OracleMigrationTest`=17)。
```bash
git add -A
git commit -m "feat(announcements): 到期自动关闭(V17 closed_at + 定时任务)+ Response 暴露 closedAt + 重新启用清空

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## 单元 B:前端 —— markdownToText + 纯文本预览

**Files:**
- Modify: `src/lib/ui/markdown.ts`(加 markdownToText)
- Modify: `src/lib/ui/markdown.spec.ts`(加用例)
- Modify: `src/features/dashboard/AnnouncementsPanel.vue`(预览用 markdownToText)

- [ ] **Step 1: 写 markdownToText 失败测试**

`markdown.spec.ts` 追加:
```ts
import { renderMarkdown, markdownToText } from './markdown'

describe('markdownToText', () => {
  it('strips table markup to readable text', () => {
    const t = markdownToText('| A | B |\n| - | - |\n| 1 | 2 |')
    expect(t).not.toContain('|')
    expect(t).not.toContain('<table')
    expect(t).toContain('A')
    expect(t).toContain('1')
  })
  it('strips emphasis markers', () => {
    expect(markdownToText('**bold** and _em_')).toContain('bold')
    expect(markdownToText('**bold**')).not.toContain('*')
  })
  it('returns empty for blank', () => {
    expect(markdownToText('')).toBe('')
    expect(markdownToText('   ')).toBe('')
  })
  it('does not leak script', () => {
    expect(markdownToText('<script>alert(1)</script>hi').toLowerCase()).not.toContain('<script')
  })
})
```
Run: `npm test -- markdown` → 红(markdownToText 未导出)。

- [ ] **Step 2: 实现 markdownToText**

`markdown.ts` 追加(复用 renderMarkdown 的净化 HTML,再取文本):
```ts
export function markdownToText(src: string): string {
  if (!src || !src.trim()) return ''
  const html = renderMarkdown(src)
  const doc = new DOMParser().parseFromString(html, 'text/html')
  return (doc.body.textContent || '').replace(/\s+/g, ' ').trim()
}
```
(jsdom 测试环境支持 DOMParser。)
Run: `npm test -- markdown` → 绿(全部用例)。

- [ ] **Step 3: AnnouncementsPanel 预览用 markdownToText**

`AnnouncementsPanel.vue`:`import { markdownToText } from '@/lib/ui/markdown'`(若已 import renderMarkdown 则合并具名导入)。预览行:
```vue
<p class="mt-0.5 line-clamp-1 text-xs text-ink-3">{{ markdownToText(pick(a, 'body')) }}</p>
```
(若该文件还没引 markdown 工具,新增 import。)

- [ ] **Step 4: 测 + build**

Run: `npm test`(含既有 AnnouncementsPanel spec;若有断言预览原文则更新)。
Run: `npm run build`。

- [ ] **Step 5: 提交**

```bash
git add -A
git commit -m "feat(announcements): 公告卡预览改为纯文本(markdownToText 去除 Markdown 标记)

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## 单元 C:前端 —— 详情弹窗放大 + 正式排版 + 头部尺寸统一

**Files:**
- Modify: `src/lib/ui/Modal.vue`(加 2xl 尺寸)
- Modify: `src/assets/index.css`(加 .markdown-body-lg)
- Modify: `src/features/dashboard/AnnouncementDetailModal.vue`(size 2xl、正式排版、统一头部、元信息)
- Modify: `src/lib/i18n/locales/zh.ts`、`en.ts`(详情元信息文案)

- [ ] **Step 1: Modal 加 2xl**

`Modal.vue`:`ModalSize` 类型加 `'2xl'`;`maxW` 对象加 `'2xl': '64rem'`。其余不变(仍 `min(92vw, X)`)。

- [ ] **Step 2: .markdown-body-lg 样式**

`index.css` 追加(在 .markdown-body 之后):
```css
.markdown-body-lg { @apply text-base leading-7; }
.markdown-body-lg h1 { @apply text-xl; }
.markdown-body-lg h2 { @apply text-lg; }
.markdown-body-lg h3 { @apply text-base; }
.markdown-body-lg p { @apply my-3; }
.markdown-body-lg ul,.markdown-body-lg ol { @apply my-3; }
.markdown-body-lg table { @apply my-3; }
.markdown-body-lg th,.markdown-body-lg td { @apply px-3 py-1.5; }
```
(仅尺寸/间距覆盖,颜色/边框继承 .markdown-body。用真实 token——@apply 的均为内置工具类。)
Run: `npm run build` 确认 @apply 合法(此步后或在 Step 5 一并跑)。

- [ ] **Step 3: i18n 详情元信息(两端)**

`zh.ts` `dashboard.announcements` 加:`publishedAt: '发布于'`、`window: '生效时间'`、`to: '至'`。
`en.ts`:`publishedAt: 'Published'`、`window: 'Active period'`、`to: 'to'`。
(`pinned` 已存在。)

- [ ] **Step 4: AnnouncementDetailModal 正式排版 + 统一头部 + 2xl**

`AnnouncementDetailModal.vue` 重写模板主体(脚本保持:props open/announcement、emit update:open、pick/t、colorClasses、renderMarkdown;新增按需格式化日期的小函数或用既有 i18n date 工具——若项目有日期格式化沿用,否则用 `new Date(x).toLocaleString()`):
```vue
<template>
  <Modal size="2xl" :open="open" :title="announcement ? pick(announcement,'title') : ''"
         @update:open="(v)=>emit('update:open', v)">
    <div v-if="announcement">
      <!-- 统一头部:类型徽标 -->
      <div class="mb-2 flex items-center gap-2">
        <span class="flex size-8 shrink-0 items-center justify-center rounded-lg"
              :class="colorClasses(announcement.typeColor).chip">
          <AppIcon :name="announcement.typeIcon" class="size-4.5" />
        </span>
        <span class="rounded px-2 py-0.5 text-xs font-medium"
              :class="colorClasses(announcement.typeColor).tag">
          {{ pick(announcement, 'typeLabel') }}
        </span>
        <span v-if="announcement.pinned" class="flex items-center gap-1 text-xs text-ink-3">
          <Pin class="size-3.5" />{{ t('dashboard.announcements.pinned') }}
        </span>
      </div>
      <!-- 元信息 -->
      <div class="text-xs text-ink-3">
        <span>{{ t('dashboard.announcements.publishedAt') }} {{ fmt(announcement.createdAt) }}</span>
        <span v-if="announcement.startsAt || announcement.endsAt"> · {{ t('dashboard.announcements.window') }}
          {{ announcement.startsAt ? fmt(announcement.startsAt) : '' }} {{ t('dashboard.announcements.to') }} {{ announcement.endsAt ? fmt(announcement.endsAt) : '' }}</span>
      </div>
      <hr class="my-3 border-border" />
      <!-- 正文 -->
      <div class="markdown-body markdown-body-lg" v-html="renderMarkdown(pick(announcement, 'body'))"></div>
    </div>
  </Modal>
</template>
```
脚本里加 `function fmt(s?: string){ return s ? new Date(s).toLocaleString() : '' }`(title 已由 Modal 顶部显示为粗体标题,故头部不再重复标题;若希望正文区也有大标题,可在 hr 上方加 `<h2 class="text-xl font-bold">`——但 Modal title 已是 `text-lg font-bold`;为「更显著」,可将 Modal title 留空并在弹窗内用 `<h2 class="text-xl font-bold leading-snug mb-2">{{ pick(announcement,'title') }}</h2>`。实现者二选一:推荐用弹窗内 `text-xl` 大标题、Modal title 传空串,避免双标题)。
注意:`size-4.5` 非默认 Tailwind 间距——用 `class="h-[18px] w-[18px]"` 或在 config 确认;为稳妥用 `class="size-[18px]"`(任意值,合法)。AppIcon 接受 class 透传。

- [ ] **Step 5: 测 + build**

Run: `npm test`(详情/面板 spec;若 AnnouncementsPanel.spec 断言 detail modal 内容仍通过)。
Run: `npm run build`(@apply / 任意值 class 合法)。

- [ ] **Step 6: 提交**

```bash
git add -A
git commit -m "feat(announcements): 详情弹窗放大(2xl)+ 正式排版/大字号 + 类型图标尺寸统一

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## 单元 D:前端 —— 管理端「当前/历史」标签

**Files:**
- Modify: `src/lib/api/announcements.ts`(Announcement 加 closedAt)
- Modify: `src/features/admin/announcements/AnnouncementsAdminView.vue`(标签 + 过滤 + 历史列)
- Modify: `src/lib/i18n/locales/zh.ts`、`en.ts`(标签 + 历史标记文案)

- [ ] **Step 1: 类型加 closedAt**

`announcements.ts`:`Announcement` 接口加 `closedAt?: string | null`。

- [ ] **Step 2: i18n(两端)**

`zh.ts` `admin.announcements` 加:`tabActive: '当前'`、`tabHistory: '历史'`、`closedExpired: '已到期'`、`closedDisabled: '已停用'`、`closedAtLabel: '关闭时间'`。
`en.ts`:`tabActive: 'Active'`、`tabHistory: 'History'`、`closedExpired: 'Expired'`、`closedDisabled: 'Disabled'`、`closedAtLabel: 'Closed at'`。

- [ ] **Step 3: AnnouncementsAdminView 标签 + 过滤**

读现有文件结构(它 `listAdminAnnouncements()` 取全部 + `usePagination`)。改造:
- 加本地状态 `const tab = ref<'active'|'history'>('active')`。
- 计算 `const visible = computed(() => all.value.filter(a => tab.value === 'active' ? a.active : !a.active))`(`all` 为加载到的全部;若现用变量名不同,对齐)。把分页源从 `all` 改为 `visible`(`usePagination(visible, ...)`;若 usePagination 接受 ref/computed,传 `visible`;切换 tab 时重置到第 1 页)。
- 顶部加标签按钮组(主题化,reka-ui Tabs 或两个按钮):
```vue
<div class="mb-3 inline-flex rounded-xl border border-border p-0.5 text-sm">
  <button type="button" class="rounded-lg px-3 py-1" :class="tab==='active' ? 'bg-primary text-white' : 'text-ink-2'" @click="tab='active'">{{ t('admin.announcements.tabActive') }}</button>
  <button type="button" class="rounded-lg px-3 py-1" :class="tab==='history' ? 'bg-primary text-white' : 'text-ink-2'" @click="tab='history'">{{ t('admin.announcements.tabHistory') }}</button>
</div>
```
- 历史 tab 行:在已有列基础上,增加「关闭信息」展示——`a.closedAt ? (t('admin.announcements.closedExpired') + ' · ' + fmt(a.closedAt)) : t('admin.announcements.closedDisabled')`。可放在「状态」列位置或新增一小列;active tab 不显示该信息。用现有行模板条件渲染(`v-if="tab==='history'"`)。
- 「新建公告」按钮:仅 active tab 显示(`v-if="tab==='active'"`),history tab 隐藏(可选,推荐)。
- 切 tab 时若分页页码越界,重置 `page=1`(watch tab 重置)。

- [ ] **Step 4: 测 + build**

Run: `npm test`(若 AnnouncementsAdminView.spec 存在,更新为:默认 active tab 仅显示 active 行;切 history 显示 inactive 行 + 关闭标记。若不存在,加一个轻量 spec mock listAdminAnnouncements 返回 active+inactive 两条,断言默认只见 active 标题,点 history 见 inactive 标题 + closedExpired/closedDisabled 文案)。i18n parity 必须绿。
Run: `npm run build`。

- [ ] **Step 5: 提交**

```bash
git add -A
git commit -m "feat(announcements): 管理端 当前/历史 标签(历史含到期/停用标记 + 关闭时间)

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## 单元 E:联调验证(无代码)

- [ ] **Step 1: 后端重启 + V17**

build jar,`fuser -k 8080/tcp`(勿 pkill portal.jar),启动 dev,日志确认 "now at version v17"。

- [ ] **Step 2: 自动关闭冒烟**

- 用 admin 建一条 endsAt 设为「刚过去」的 active 公告;等调度(或临时把 initialDelay 调小验证,验证后改回)或直接验证 `closeExpired` 已在启动 30s 后跑过 → 该公告 active=false、closedAt 有值;portal `/api/portal/announcements` 不含它。
- 一条无 endsAt 的 active 公告 → 始终在 portal 可见。
- `/api/admin/announcements` 返回含 closedAt 字段。

- [ ] **Step 3: 前端目视(`npm run dev`)**

- 首页公告卡预览为纯文本(造含表格/加粗的公告验证不再显示 `|`/`**`)。
- 点击公告 → 详情弹窗更大(2xl)、字号更大、头部类型徽标/图标尺寸一致、有发布时间/生效区间 + 分隔线 + 正文;右上角关闭按钮可用;深浅色都正常(上轮已修主题)。
- 管理端公告页:当前/历史标签;当前仅 active;历史显示已到期(带关闭时间)/已停用条目;编辑历史条目设 active=true → 回到当前且 closedAt 清空。

---

## 自检(plan vs spec)

- **#1 纯文本预览**:单元 B(markdownToText + AnnouncementsPanel)。✅
- **#2 弹窗放大/正式排版**:单元 C(Modal 2xl + .markdown-body-lg + 元信息/分隔线/大标题)。✅
- **#3 类型/图标尺寸统一**:单元 C-Step4(chip size-8 / icon size-[18px] / tag text-xs / pinned text-xs)。✅
- **#4 自动关闭 + 历史**:单元 A(V17 closed_at + closeExpired + @EnableScheduling + Scheduler + update 清空 + Response closedAt)、单元 D(当前/历史标签 + expired/disabled 标记)。✅
- **无 endsAt 常显**:closeExpired 仅处理 endsAt!=null;effective 不变。✅
- **类型一致**:`markdownToText(src):string`(B 定义,B 使用);`closeExpired():int`、`findByActiveTrueAndEndsAtIsNotNullAndEndsAtBefore`(A);`Announcement.closedAt?`/`AnnouncementResponse.closedAt`(A↔D);Modal `'2xl'`(C);`.markdown-body-lg`(C)。✅
- `OracleMigrationTest`=17;i18n 两端对齐;无正文 schema 变更。✅
- 调度隔离:initialDelay 30s + fixedDelay 5min,测试不误触发;closeExpired 直接单测。✅
