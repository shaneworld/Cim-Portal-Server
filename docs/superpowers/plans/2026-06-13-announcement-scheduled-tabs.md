# 公告定时发布(Scheduled)+ 下划线标签 实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development. 提交前 `mvn -q test` / `npm test` 须绿。commit trailer:`Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>`。

**Goal:** 后端调度器增加发布检查(publishCheck,只读统计 + 日志,无 schema 变更);管理端「当前」标签为未到开始时间的 active 公告显示「计划发布」徽标;「当前/历史」标签改为下划线样式 + 无障碍属性。

**Architecture:** `effective()` 已按时间窗到点发布,无需翻转 active。后端加 `AnnouncementService.publishCheck()`(active && startsAt 在未来的计数,Java 侧比较,注入 Clock)+ repo finder + 调度器日志。前端 `AnnouncementsAdminView` 加 `isScheduled` 判定与「计划发布」徽标,并把标签条改为下划线样式。

**Tech Stack:** Spring Boot 3.3/JDK 21/Oracle + Spring scheduling;Vue 3.5 + TS + Tailwind v3 + vue-i18n。

仓库:后端 `/home/shane/Code/cim-portal/cim-portal-server`,前端 `/home/shane/Code/cim-portal/cim-portal-client`。

---

## 单元 A:后端 —— publishCheck + 调度日志(无迁移)

**Files:**
- Modify: `src/main/java/com/cimportal/announcement/AnnouncementService.java`(加 publishCheck)
- Modify: `src/main/java/com/cimportal/announcement/AnnouncementRepository.java`(加 finder)
- Modify: `src/main/java/com/cimportal/announcement/AnnouncementScheduler.java`(日志)
- Test: `src/test/java/com/cimportal/announcement/AnnouncementCloseExpiredTest.java`(同类追加 publishCheck 用例,或新建 AnnouncementPublishCheckTest)

- [ ] **Step 1: 写 publishCheck 失败测试**

先读 `AnnouncementService`(确认 repo 字段名 `repo`、已注入 `Clock clock`)与 `AnnouncementCloseExpiredTest`(沿用其 `OracleIntegrationTest` 基类 + 直接 `repo.save(Announcement)` 造数风格、typeCode "INFO")。在 `AnnouncementCloseExpiredTest` 追加:
```java
@Test
void publishCheck_countsActiveFutureStart() {
    Instant now = Instant.now();
    save(active=true,  startsAt=now.plusSeconds(3600), endsAt=null); // 计入
    save(active=true,  startsAt=now.minusSeconds(3600), endsAt=null); // 已开始 → 不计
    save(active=true,  startsAt=null,                   endsAt=null); // 无开始 → 不计
    save(active=false, startsAt=now.plusSeconds(3600), endsAt=null); // 未启用 → 不计
    assertThat(service.publishCheck()).isEqualTo(1);
}
```
(用现有测试里构造/保存 Announcement 的同款 helper;若没有 helper,直接 new Announcement() 设全部非空字段 titleZh/En、bodyZh/En、typeCode="INFO"、pinned=false、active、startsAt、endsAt 后 repo.save。)
Run: `mvn -q -Dtest=AnnouncementCloseExpiredTest test` → 红(publishCheck 未定义)。

- [ ] **Step 2: 实现 repo finder + publishCheck**

`AnnouncementRepository.java` 加:
```java
List<Announcement> findByActiveTrueAndStartsAtIsNotNull();
```
`AnnouncementService.java` 加:
```java
@Transactional(readOnly = true)
public int publishCheck() {
    Instant now = clock.instant();
    return (int) repo.findByActiveTrueAndStartsAtIsNotNull().stream()
        .filter(a -> a.getStartsAt().isAfter(now))
        .count();
}
```
(只读统计,不翻转字段。)
Run: `mvn -q -Dtest=AnnouncementCloseExpiredTest test` → 绿。

- [ ] **Step 3: 调度器加发布检查日志**

`AnnouncementScheduler.java` 现有 `@Scheduled` 方法(调用 `service.closeExpired()`),在其后追加:
```java
int upcoming = service.publishCheck();
if (upcoming > 0) log.info("{} scheduled announcement(s) upcoming", upcoming);
```
(调度注解/延迟/`@Profile("!test")` 不变。)

- [ ] **Step 4: 全测 + 提交**

Run: `mvn -q test` → 绿(`OracleMigrationTest` 仍 = 17,无新迁移)。
```bash
git add -A
git commit -m "feat(announcements): 调度发布检查 publishCheck(统计计划发布数量并记录,无 schema 变更)

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## 单元 B:前端 —— 「计划发布」徽标 + 下划线标签

**Files:**
- Modify: `src/features/admin/announcements/AnnouncementsAdminView.vue`
- Modify: `src/lib/i18n/locales/zh.ts`、`en.ts`(加 `admin.announcements.scheduled`)
- Test: `src/features/admin/announcements/AnnouncementsAdminView.spec.ts`(扩展)

- [ ] **Step 1: i18n(两端对齐)**

`zh.ts` `admin.announcements` 加 `scheduled: '计划发布'`;`en.ts` 加 `scheduled: 'Scheduled'`。

- [ ] **Step 2: 写「计划发布」徽标失败测试**

读现有 `AnnouncementsAdminView.spec.ts`(它 mock `listAdminAnnouncements`/MSW + mount;active 行用 `enabled` Badge,history 用 expired/disabled)。追加一条 active 且 startsAt 在未来的公告到 mock 数据,断言「当前」标签里它显示 `scheduled` 文案(Scheduled),而一条 active+startsAt 过去/无的公告显示「启用」文案。
示例(沿用现有 spec 的 mock 风格,startsAt 用未来 ISO):
```ts
// mock 返回:
//  { id:1, active:true, startsAt: <future ISO>, ... titleZh/En:'计划中/Scheduled1' }
//  { id:2, active:true, startsAt: null, ... titleEn:'LiveOne' }
// 默认 active 标签:断言 wrapper 文本含 'Scheduled'(计划发布)针对 id1 行;id2 行显示 enabled 文案
```
用未来时间:`new Date(Date.now()+3600_000).toISOString()`。
Run: `npm test -- AnnouncementsAdminView` → 红。

- [ ] **Step 3: 实现 isScheduled + 徽标**

`AnnouncementsAdminView.vue` 脚本加:
```ts
function isScheduled(a: Announcement) {
  return a.active && !!a.startsAt && new Date(a.startsAt).getTime() > Date.now()
}
```
状态单元(当前标签,`v-else` 分支,即非 history):改为——
```vue
<template v-if="tab === 'history'"> <!-- 既有 expired/disabled 标记，不变 --> ... </template>
<template v-else>
  <Badge v-if="isScheduled(row)" tone="caution">{{ t('admin.announcements.scheduled') }}</Badge>
  <Badge v-else :tone="row.active ? 'go' : 'muted'">{{ row.active ? t('common.enabled') : t('common.disabled') }}</Badge>
</template>
```
(对齐现有 Badge 用法/tone 名与 enabled/disabled 文案键——读文件确认现用的是 `common.enabled`/`common.disabled` 还是别的;保持与现有 active 徽标一致,仅在 isScheduled 时替换为 scheduled 徽标。可附 `fmt(row.startsAt)` 显示开始时间,可选。)
Run: `npm test -- AnnouncementsAdminView` → 绿。

- [ ] **Step 4: 下划线标签**

把现有标签条(胶囊/按钮组,`inline-flex rounded-xl border ... bg-primary`)替换为下划线标签:
```vue
<div class="mb-4 flex gap-6 border-b border-border" role="tablist">
  <button type="button" role="tab" :aria-selected="tab==='active'" data-testid="ann-tab-active"
    class="-mb-px border-b-2 pb-2 text-sm transition"
    :class="tab==='active' ? 'border-primary font-semibold text-foreground' : 'border-transparent text-ink-3 hover:text-foreground'"
    @click="tab='active'">{{ t('admin.announcements.tabActive') }}</button>
  <button type="button" role="tab" :aria-selected="tab==='history'" data-testid="ann-tab-history"
    class="-mb-px border-b-2 pb-2 text-sm transition"
    :class="tab==='history' ? 'border-primary font-semibold text-foreground' : 'border-transparent text-ink-3 hover:text-foreground'"
    @click="tab='history'">{{ t('admin.announcements.tabHistory') }}</button>
</div>
```
(保留 `data-testid`;`@click` 设 `tab`;`watch(tab, reset)` 不变。tokens 均真实:border-border/border-primary/text-foreground/text-ink-3。)

- [ ] **Step 5: 测 + build**

Run: `npm test`(i18n parity + AnnouncementsAdminView spec:tab 过滤断言仍绿;新增 scheduled 徽标断言绿;aria-selected 随选中切换——若加断言则验证 `ann-tab-active` 的 `aria-selected` 切换)。
Run: `npm run build`。

- [ ] **Step 6: 提交**

```bash
git add -A
git commit -m "feat(announcements): 当前标签显示「计划发布」徽标 + 标签改为下划线样式(含无障碍)

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## 单元 C:联调验证(无代码)

- [ ] **Step 1: 后端**:build + 重启 dev(`fuser -k 8080/tcp`,勿 pkill portal.jar);无新迁移仍 v17。admin 建一条 active + startsAt 未来 1h 的公告 → `/api/portal/announcements` 暂不含它(未到点);改 startsAt 到过去 → 门户出现(effective 到点发布)。日志在调度跑后出现 "N scheduled announcement(s) upcoming"(若有计划项)。
- [ ] **Step 2: 前端**(`npm run dev`):公告管理页标签为下划线样式(active 主色下划线 + 加粗,inactive 灰字 hover);「当前」标签中未到开始时间的 active 公告显示「计划发布」徽标;到点后(或 startsAt 过去)显示「启用」。深浅色正常。
- [ ] **Step 3:** 验证后删除测试公告,保持 dev 数据干净。

---

## 自检(plan vs spec)

- **#1 定时发布**:单元 A(publishCheck + repo finder + 调度日志,无迁移)、单元 B-Step3(前端「计划发布」徽标)。✅ effective() 已负责到点发布(不翻转)。
- **#2 下划线标签**:单元 B-Step4(border-primary 下划线 + font-semibold;inactive 灰字 hover;-mb-px;role/aria-selected;保留 data-testid)。✅
- 类型/命名一致:`publishCheck():int`、`findByActiveTrueAndStartsAtIsNotNull()`、`isScheduled(a)`、i18n `admin.announcements.scheduled`。✅
- `OracleMigrationTest` 仍 17(无迁移);i18n 两端对齐;调度测试隔离(`@Profile("!test")`)不变。✅
- Scheduled 项仍归「当前」标签(过滤不变);历史标签 expired/disabled 不变。✅
