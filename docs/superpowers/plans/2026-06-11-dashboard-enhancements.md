# 首页增强 实现计划(欢迎精简 + 公告 + 值班电话 + 收藏)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development(推荐)。步骤用 `- [ ]`。顺序 A→B→C→D。

**Goal:** A 精简欢迎面板;B 公告(可配置类型 + 全局开关 + 单面板,按类型着色);C 值班电话面板(后台可管理);D 收藏(我的链接)。

**Tech Stack:** Spring Boot 3.3/JDK21/Oracle/Flyway/JPA/Testcontainers;Vue3/TS/Tailwind v3/vue-i18n/Vitest。

---

## 契约/现状(已核对)
- 提交:后端→`cim-portal-server@dev`,前端→`cim-portal-client@dev`,trailer `Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>`。**提交前 `mvn test`(后端)/ `npm run build`+`npm test`(前端)须绿。**
- `HomeView.vue` = `AppHeader → HeroPanel → GlobalSearch → SystemGrid`。`HeroPanel.vue` 现双列大数字。`HomeService.resolveFor(CurrentUser)`→`HomeResponse{categories:[{...,links:[HomeLink]}]}`;`HomeLink` record 在 `portal/dto/HomeLink.java`。`CurrentUser(employeeId,departmentCode,roleCode,admin)`;`PermissionResolver.isVisible(dept,role,Set<String>,grants)`;`HomeService` 已注入 `PermissionGroupMemberRepository.findActiveGroupCodesByEmployeeId`。
- 设置单例:`SecuritySetting` + `SecuritySettingService` + `SecuritySettingAdminController(/api/admin/security-settings GET/PUT)` + DTO `SecuritySettingUpdateRequest`/`AdminSettingView` + `PublicConfig(/api/portal/config)`;前端 `SecurityAdminView.vue`、`stores/config.ts`(`config` 含 ssoEnabled 等)。
- 管理范式:`enumvalue`/`group` 控制器+服务+前端 `features/admin/{enums,groups}` 列表+`*FormModal`;导航 `AdminLayout.vue`;路由 `router/index.ts`。`AppIcon`/`iconMap`(图标 key);`Modal`(size prop)。i18n `locales/{zh,en}.ts`+`i18n.spec.ts`。`OracleIntegrationTest`+`TestJwts.bearerFor`。Flyway oracle 最新 **V8**;`OracleMigrationTest` 断言 8。
- **硬约束:** `mvn test` 绿;`OracleMigrationTest` 依次 9→10→11;i18n 对齐;现有首页/config 向后兼容(`HomeLink` 仅追加 `favorite`;`PublicConfig` 仅追加 `announcementsEnabled`);收藏仅可访问(锁定→403)。
- **调色板常量**(前后端一致):`blue, amber, red, green, purple, slate`。前端 map(`announcementColor.ts`):每键→`{bar,chip,tag}` Tailwind 类(如 red→`bg-red-50 text-red-700 ...`)。后端仅校验 color ∈ 该集合。

---

# A. 欢迎面板精简(前端)
**Files:** Modify `src/features/dashboard/HeroPanel.vue`;Update `HeroPanel.spec.ts`
- [ ] **Read** 现 `HeroPanel.vue` + `HeroPanel.spec.ts`。
- [ ] 重排为紧凑单行:外层 `GlassCard` 改 `flex items-center justify-between gap-4 p-4`(替换 `grid ... p-5`)。左块:LIVE 徽标(保留)+ `h2 text-lg`(原 text-xl)问候 + 一行 `text-xs text-ink-2`「部门 · 角色 · 工号」+ 一行 `text-xs text-ink-3` 时钟。右块:`flex gap-2.5`,3 个统计磁贴 `rounded-xl px-4 py-2 text-center`,数字 `text-xl font-bold`(原 text-3xl extrabold),标签 `text-[11px]`。移动端:`flex-col sm:flex-row`(统计在小屏换行/底部)。保留 `useClock`、`auth`、`stats` 计算。
- [ ] `HeroPanel.spec.ts`:若断言旧 class/结构,改为断言文本(问候/统计值)存在,避免脆弱选择器。
- [ ] `npm run build` + `npm test` 绿。
- [ ] **Commit(前端)**:`refactor(ui): 欢迎面板精简为紧凑单行(保留概念,降低高度)`。

---

# B. 公告(V9 + 类型/开关 + 单面板)

## B1 后端
**Files:** `db/migration/oracle/V9__announcement.sql`;`com/cimportal/announcement/**`;扩展 `SecuritySetting*`、`PublicConfig`;`OracleMigrationTest`。
- [ ] **Read** `SecuritySetting.java`、`SecuritySettingService.java`、`dto/{SecuritySettingUpdateRequest,AdminSettingView,PublicConfig}.java`、`PortalConfigController.java`、`ApiException.java`、enum 管理控制器+测试。
- [ ] **V9**:见 spec B 的完整 SQL(announcement_type + announcement + `ALTER security_setting ADD announcements_enabled` + 3 条种子类型)。`OracleMigrationTest` 8→**9**。
- [ ] `AnnouncementType` 实体 + `AnnouncementTypeRepository`(`Optional<AnnouncementType> findByCode(String)`、`existsByCode`、`List<AnnouncementType> findAllByOrderBySortOrderAsc()`)。`AnnouncementTypeService`(CRUD;create/update 校验 code 唯一 + `color ∈ {blue,amber,red,green,purple,slate}` 否则 `ApiException.badRequest`)。`AnnouncementTypeAdminController("/api/admin/announcement-types")` GET/POST/GET{id}/PUT{id}/DELETE{id}。DTO `AnnouncementTypeRequest(code,labelZh,labelEn,color,icon,sortOrder,active)`/`Response`。
- [ ] `Announcement` 实体(`@Lob String bodyZh/bodyEn`、`typeCode`、`pinned`、`Instant startsAt/endsAt`(null)、`active`、`createdAt`)+ 仓库 `findAllByOrderByPinnedDescCreatedAtDesc()`。`AnnouncementService`:CRUD(create/update 校验 `typeRepo.findByCode(typeCode)` 存在且 active 否则 badRequest);`effective(Instant now)`(过滤 active+窗口,排序);**门户 DTO 内联类型**:`AnnouncementResponse(id,titleZh,titleEn,bodyZh,bodyEn,typeCode,typeLabelZh,typeLabelEn,typeColor,typeIcon,pinned,startsAt,endsAt,active,createdAt)`,在 service 里按 typeCode 取类型填充(找不到类型时回退 color=slate,icon=info)。
- [ ] `AnnouncementAdminController("/api/admin/announcements")` CRUD;`AnnouncementPortalController @GetMapping("/api/portal/announcements")` → `service.effective(Instant.now())`。
- [ ] **开关**:`SecuritySetting` 加字段 `announcementsEnabled`(默认 true);`AdminSettingView`+`SecuritySettingUpdateRequest`+`PublicConfig` 各加 `announcementsEnabled`;`SecuritySettingService` 读写(update 时若请求含该字段则设;public/admin 视图回填)。
- [ ] **测试** `AnnouncementControllerTest`(extends OracleIntegrationTest):类型 CRUD+重复 code 409/400+非法 color 400;公告 CRUD + 无效 typeCode 400;effective(active=0/未到 start/已过 end 不返回;pinned 置顶);门户响应含 typeColor/typeIcon;`GET /api/portal/config` 含 `announcementsEnabled`,PUT security-settings 可改。`mvn -q test` 绿(`OracleMigrationTest` 9)。
- [ ] **Commit(后端)**:`feat(announce): 公告后端(V9 类型表+公告表+全局开关 + 管理&门户 API + 测试)`。

## B2 前端
**Files:** `src/lib/api/announcements.ts`、`announcementTypes.ts`、扩展 `portal.ts`/`stores/config.ts`/types;`src/lib/ui/announcementColor.ts`;`features/dashboard/AnnouncementsPanel.vue`(+spec);`features/admin/announcements/{AnnouncementsAdminView,AnnouncementFormModal}.vue`、`features/admin/announcement-types/{AnnouncementTypesAdminView,AnnouncementTypeFormModal}.vue`;`AdminLayout.vue`、`router/index.ts`、`HomeView.vue`、`locales/{zh,en}.ts`。
- [ ] **Read** `EnumsAdminView`+`EnumFormModal`、`stores/config.ts`、`lib/api/{portal,admin}.ts`、`HomeView.vue`、`AdminLayout.vue`、`router/index.ts`、`SystemCard.vue`(图标网格供类型图标 picker 参考)。
- [ ] 类型 + api:`AnnouncementType{id,code,labelZh,labelEn,color,icon,sortOrder,active}`;`Announcement{...,typeCode,typeLabelZh,typeLabelEn,typeColor,typeIcon,pinned,startsAt?,endsAt?,active,createdAt}`。`config` store 加 `announcementsEnabled`(从 `/api/portal/config`)。`portal.listAnnouncements()`;admin CRUD(announcements + announcement-types)。
- [ ] `announcementColor.ts`:`export const ANNOUNCEMENT_COLORS = ['blue','amber','red','green','purple','slate'] as const` + `colorClasses(key)` → `{wrap,chip,tag}` Tailwind 类串(完整列出 6 键的类,供 purge 静态识别;勿动态拼接)。
- [ ] `AnnouncementsPanel.vue`:`v-if` `config.announcementsEnabled`;`onMounted` 拉 effective + 读 localStorage `cim.dismissedAnnouncements`(number[]);单卡列出,逐条 `colorClasses(a.typeColor)` + `<AppIcon :name="a.typeIcon"/>` + 类型标签(pick)+ pin 标记(pinned)+ 标题/正文(`whitespace-pre-line`,pick)+ × 忽略(写 localStorage、移除)。无剩余→不渲染。
- [ ] `HomeView.vue`:`HeroPanel` 之后插入信息行 `div.grid.gap-4.md:[grid-template-columns:1.95fr_1fr]` 包 `<AnnouncementsPanel/>` 与(C 步的)`<DutyLinesPanel/>`(C 完成前先只放公告)。
- [ ] 管理:`AnnouncementsAdminView`(列表+表单:标题/正文 zh-en textarea、类型 Select(listAnnouncementTypes)、pinned/active Switch、起止 `datetime-local`→ISO)+ 顶部「公告功能」Switch(读/写 security-settings 的 announcementsEnabled);`AnnouncementTypesAdminView`(列表+表单:code、zh/en 名、颜色 Select(ANNOUNCEMENT_COLORS,带色块)、图标 picker(复用图标网格)、排序、active)。导航两项 + 路由 + i18n(`dashboard.announcements.*`、`admin.announcements.*`、`admin.announcementTypes.*`,zh/en 对齐)。
- [ ] `AnnouncementsPanel.spec.ts`:`announcementsEnabled=false`→不渲染;启用+2 条→渲染 2;× 后 localStorage 含 id 且消失。
- [ ] `npm run build`+`npm test` 绿。
- [ ] **Commit(前端)**:`feat(announce): 单面板按类型着色 + 类型/开关管理页 + i18n`。

---

# C. 值班电话(V10)

## C1 后端
**Files:** `db/migration/oracle/V10__duty_line.sql`;`com/cimportal/dutyline/**`;`OracleMigrationTest`。
- [ ] **V10**(见 spec C SQL)。`OracleMigrationTest` 9→**10**。
- [ ] `DutyLine` 实体 + 仓库(`findByActiveTrueOrderBySortOrderAsc`、`findAllByOrderBySortOrderAsc`)+ `DutyLineService`(CRUD + `activeOrdered()`)。`DutyLineAdminController("/api/admin/duty-lines")` CRUD;`DutyLinePortalController @GetMapping("/api/portal/duty-lines")` → active 排序列表。DTO `DutyLineRequest(labelZh,labelEn,phone,sortOrder,active)`/`Response`。
- [ ] 测试 `DutyLineControllerTest`:admin CRUD;门户仅 active 且按 sort。`mvn -q test` 绿(`OracleMigrationTest` 10)。
- [ ] **Commit(后端)**:`feat(duty): 值班电话后端(V10 + 管理&门户 API + 测试)`。

## C2 前端
**Files:** `lib/api/dutyLines.ts`、`portal.ts`(`listDutyLines`);`features/dashboard/DutyLinesPanel.vue`;`features/admin/duty-lines/{DutyLinesAdminView,DutyLineFormModal}.vue`;`AdminLayout.vue`、`router/index.ts`、`HomeView.vue`(信息行右列放入)、`locales/{zh,en}.ts`。
- [ ] `DutyLinesPanel.vue`:拉 `listDutyLines()`;空→不渲染;否则玻璃卡 + 标题(电话图标)+ 每行 电话图标 + 中/英名(pick)+ 号码。
- [ ] 管理 `DutyLinesAdminView` + 表单(zh/en 名、号码、排序、active)+ 导航/路由 + i18n(`dashboard.dutyLines.*`、`admin.dutyLines.*`)。
- [ ] `HomeView.vue` 信息行右列接入 `<DutyLinesPanel/>`(公告左、值班右)。
- [ ] `npm run build`+`npm test` 绿。
- [ ] **Commit(前端)**:`feat(duty): 首页值班电话面板 + 管理页 + i18n`。

---

# D. 收藏(V11)

## D1 后端
**Files:** `db/migration/oracle/V11__favorite.sql`;`com/cimportal/favorite/**`;`portal/dto/HomeLink.java`、`portal/HomeService.java`;`OracleMigrationTest`。
- [ ] **V11**(见 spec D)。`OracleMigrationTest` 10→**11**。
- [ ] `Favorite`(`@IdClass(FavoriteId)`:employeeId,linkId,createdAt)+ 仓库(`@Query findLinkIdsByEmployeeId`、`existsByEmployeeIdAndLinkId`、`deleteByEmployeeIdAndLinkId`)。
- [ ] `HomeLink` record 加 `boolean favorite`;`HomeService` 注入 `FavoriteRepository`,`resolveFor` 取 `favIds` 并标记每链接。
- [ ] `FavoriteController`(`/api/portal/favorites/{linkId}` POST/DELETE):POST 校验链接对当前用户可访问(`PermissionResolver.isVisible(...)` + 组 code;不可访问→403),`if(!exists) save`;DELETE 删除;均 204。复用 HomeService 的「取链接 grants + 用户组 code」逻辑(必要时抽 `PermissionService.isLinkAccessible(user,linkId)`)。
- [ ] 测试 `FavoriteControllerTest`:可访问链接收藏→home `favorite=true`,取消→false;受限链接 POST→403 且未写入。`mvn -q test` 绿(`OracleMigrationTest` 11)。
- [ ] **Commit(后端)**:`feat(fav): 收藏后端(V11 + HomeLink.favorite + 端点含可访问校验 + 测试)`。

## D2 前端
**Files:** `lib/api/portal.ts`(`toggleFavorite`)、types(`HomeLink.favorite`);`features/dashboard/SystemCard.vue`、`HomeView.vue`;`locales/{zh,en}.ts`;`SystemCard.spec.ts`。
- [ ] `HomeLink` 类型加 `favorite`;`toggleFavorite(linkId,on)`(POST/DELETE)。
- [ ] `SystemCard.vue`:可访问卡右上角 `Star`(lucide;favorite→`fill-current text-amber-400`,否则 `text-ink-3`),点按乐观切换 + 调 toggle,失败回滚+toast;锁定卡无星。
- [ ] `HomeView.vue`:`favorites = filtered.flatMap(c=>c.links).filter(l=>l.favorite&&l.accessible)`(按 id 去重);`SystemGrid` 之上当有收藏时渲染「我的收藏 / My links」区(复用卡片网格)。
- [ ] i18n `dashboard.myLinks`、`dashboard.favoriteAdd/Remove`;`SystemCard.spec.ts` 星标切换 + 锁定无星。
- [ ] `npm run build`+`npm test` 绿。
- [ ] **Commit(前端)**:`feat(fav): 卡片星标 + 首页「我的收藏」区 + i18n`。

---

## 自检清单(Self-Review)
- **覆盖:** A 欢迎(HeroPanel);B 公告 V9+类型+公告+开关+内联类型样式+面板+两管理页+测试;C 值班 V10+面板+管理页+测试;D 收藏 V11+favorite 标记+端点+星标+我的收藏+测试。✔
- **占位符:** 迁移 SQL 在 spec;后端关键类/方法已述;前端含组件职责、调色板 map、localStorage 键、聚合逻辑;「按现状」处先 Read。无 TBD。
- **一致性:** 调色板键集合(后端校验 ↔ `announcementColor.ts` ↔ 类型种子);`AnnouncementResponse` 内联 type 字段(后端 ↔ 前端类型 ↔ 面板渲染);`announcementsEnabled`(security_setting ↔ PublicConfig ↔ config store ↔ 面板 v-if ↔ 管理开关);`HomeLink.favorite`(DTO ↔ types ↔ SystemCard/HomeView);`/api/portal/favorites/{linkId}`(后端 ↔ toggleFavorite)。
- **硬约束:** `mvn test` 绿;`OracleMigrationTest` 9→10→11;收藏锁定 403;i18n 对齐;config/home 向后兼容(仅追加字段)。
