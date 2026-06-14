# 公告:定时发布(Scheduled 状态)+ 下划线标签 设计

> 日期:2026-06-13。范围:公告「计划发布」状态可视化 + 调度器发布检查(无 schema 变更);公告管理页标签改为下划线样式。后端 Oracle-only;前端 Vue 3 + TS。

## 背景

公告已支持:Markdown、详情弹窗、到期自动关闭(V17 closed_at + 5 分钟调度任务)、管理端「当前/历史」标签。`effective(now)` 已按 `active && startsAt<=now && now<=endsAt` 过滤——**未到开始时间的 active 公告已被自动隐藏,到点即自动在门户出现**(无需调度器翻转)。本轮 2 项:

1. **定时发布**:把「计划开始时间」纳入调度任务,使公告到点自动发布。鉴于 `effective()` 已实现门户侧到点发布,本轮的价值在于:**管理端可见的「计划发布(Scheduled)」状态徽标** + **调度器的发布检查(日志/可观测)**。
2. **下划线标签**:公告管理页「当前/历史」标签当前为按钮/胶囊样式,观感差;改为**下划线标签**(active 文字加粗 + 主色下划线;inactive 灰字;标签条下边框,内容在下方,无外框)。

## 用户决策(已确认)

- 发布模型:**Scheduled 徽标 + 无新状态**。保留单一 `active` 标志;未到开始时间的 active 公告靠 `effective()` 到点自动发布;管理端「当前」标签为这类公告显示「计划发布」徽标;调度任务增加一次发布检查(记录即将发布的数量)。**无 schema 变更**;Scheduled 项仍归在「当前」标签。
- 标签样式:**下划线标签**(GitHub/Material 风格)。

## 硬约束

- 后端 `mvn test` 绿;**无新迁移**(`OracleMigrationTest` 仍 = 17)。
- 前端 `npm test`(含 i18n zh/en 对齐)+ `npm run build` 绿。
- `publishCheck()` 用注入的 `Clock`(可单测);调度器 initialDelay/fixedDelay 不变(测试不误触发;`@Profile("!test")` 仍生效)。
- `effective()` 与 `closeExpired()` 逻辑不变。
- 下划线标签保留既有 `data-testid`(`ann-tab-active` / `ann-tab-history` 等),并加无障碍属性(role=tablist/tab、aria-selected)。
- commit trailer:`Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>`;提交前测试须绿。

---

## #1 定时发布:Scheduled 状态 + 调度器发布检查

### 后端(无 schema 变更)

- `AnnouncementService` 新增:
  ```java
  @Transactional(readOnly = true)
  public int publishCheck() {
      Instant now = clock.instant();
      return (int) repo.findByActiveTrueAndStartsAtIsNotNull().stream()
          .filter(a -> a.getStartsAt().isAfter(now))   // 尚未到开始时间 = 计划中
          .count();
  }
  ```
  仅统计「计划发布(active 且 startsAt 在未来)」数量,**不翻转任何字段**(发布由 `effective()` 到点自动生效)。Java 侧时间比较,镜像 `closeExpired()`/`effective()`(避免 DB 时区谓词)。
- `AnnouncementRepository` 新增:`List<Announcement> findByActiveTrueAndStartsAtIsNotNull();`
- `AnnouncementScheduler`:在现有 `@Scheduled` 方法内,`closeExpired()` 之后增加发布检查日志:
  ```java
  int upcoming = service.publishCheck();
  if (upcoming > 0) log.info("{} scheduled announcement(s) upcoming", upcoming);
  ```
  调度参数不变(initialDelay 30s、fixedDelay 5min;`@Profile("!test")`)。
- `effective()`、`closeExpired()`、迁移、DTO 均不变。

### 前端

- `src/features/admin/announcements/AnnouncementsAdminView.vue`:
  - 加一个本地判定 `isScheduled(a)`:`a.active && a.startsAt && new Date(a.startsAt).getTime() > Date.now()`。
  - 「当前」标签的状态单元:`isScheduled(a)` → 显示「计划发布」徽标(Badge tone `caution` 或 `muted`,可附 `startsAt` 本地化时间,沿用 Unit C/D 的 `fmt`/locale 方案);否则显示原「启用」徽标。「历史」标签不变(expired/disabled 标记)。
  - 不改变过滤(Scheduled 仍 `active===true` → 当前标签)。
- i18n(两端对齐):`admin.announcements.scheduled`(计划发布 / Scheduled)。

---

## #2 下划线标签(当前 / 历史)

**File:** `src/features/admin/announcements/AnnouncementsAdminView.vue`(标签条部分)。

- 由现有胶囊/按钮组改为下划线标签:
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
  - active:`border-primary` 下划线 + `font-semibold text-foreground`;inactive:`border-transparent text-ink-3 hover:text-foreground`(均为真实 token)。
  - `-mb-px` 让 active 下划线压在标签条的底边框上;内容面板在标签条下方,无外框。
  - 保留 `data-testid`;加 `role="tablist"`/`role="tab"`/`aria-selected`。
- 切换逻辑不变(`tab` ref + `watch(tab, reset)`,见 Unit D)。

---

## 测试策略

- **后端:**
  - `publishCheck()` 单测(可控相对时间):active+startsAt 在未来 → 计入;active+startsAt 在过去 → 不计;active+startsAt null → 不计;inactive+startsAt 未来 → 不计。返回数量正确。
  - 全套绿,`OracleMigrationTest` 仍 = 17(无新迁移)。
- **前端:**
  - i18n zh/en 对齐(新增 `scheduled`)。
  - `AnnouncementsAdminView` spec 扩展:当前标签中,startsAt 在未来的 active 公告显示「计划发布」徽标;startsAt 在过去/无的 active 公告显示「启用」徽标。下划线标签:`aria-selected` 随选中切换;`data-testid` 仍可点击切换(沿用 Unit D 的过滤断言)。
  - `npm test` + `npm run build` 绿。

## 自检 / 一致性

- #1:`publishCheck()`(后端只读统计 + 调度日志,无翻转/无 schema);前端「计划发布」徽标(active && startsAt 未来);Scheduled 归「当前」标签;`effective()` 已负责到点发布。
- #2:下划线标签(border-primary 下划线 + font-semibold;inactive 灰字 hover;-mb-px 压底边框;role/aria-selected;保留 data-testid)。
- 类型/命名一致:`publishCheck():int`、`findByActiveTrueAndStartsAtIsNotNull()`、`isScheduled(a)`、i18n `admin.announcements.scheduled`。
- 无迁移变更;i18n 对齐;调度测试隔离不变。
