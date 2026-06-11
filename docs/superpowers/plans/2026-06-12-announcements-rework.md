# 公告/值班 重构实现计划(类型入枚举 + 单开关 + 常显面板)

> REQUIRED SUB-SKILL: superpowers:subagent-driven-development。两单元:A 后端重构 → B 前端重构。提交前 `mvn test` / `npm run build`+`npm test` 须绿。trailer `Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>`。

**重构目标(改造已建功能):**
1. 公告类型并入枚举系统(删除独立 `announcement_type` 表/管理页);`enum_value` 增 `color`/`icon`,新增 `ANNOUNCEMENT_TYPE` 类别。
2. 新增「一般通知 / General Notice」类型(code `GENERAL`,slate + megaphone)。
3. 取消手动关闭(去掉 × + localStorage);公告按管理员设定的生效窗口自动过期;**面板常显**,无公告时显示「暂无公告 / No announcements」。
4. 公告 + 值班合为一个信息块,由**单一开关** `infoPanelEnabled` 控制(删除原 `announcementsEnabled`/`dutyLinesEnabled` 两开关);开关置于 **安全/设置 管理页**;hero `compact = infoPanelEnabled`;块内:公告(左,常显)+ 值班(右,空则隐藏、公告占满)。

## 现状(已核对)
- `enum_value(id,category,code,label_zh,label_en,sort_order,active,created_at,updated_at)`,唯一 `(category,code)`;`EnumCategory{DEPARTMENT,ROLE,LINK_CATEGORY,LINK_STATUS}`;`EnumValueService` create/update 用 `EnumValueRequest(code,labelZh,labelEn,sortOrder,active)`;`EnumAdminController` `/{category}` CRUD;`EnumController`(门户)`/{category}` 列 active。前端 `EnumsAdminView`(category tabs)+ `EnumFormModal`(code/zh/en/sort/active)+ `lib/api/enums`。
- `announcement_type` 表 + `AnnouncementType` 实体/repo/`AnnouncementTypeService`/`AnnouncementTypeAdminController`/dto;`AnnouncementService.toResponse` 经 `typeRepo.findByCode` 注入 typeColor/typeIcon/typeLabel(fallback slate/info)。`Announcement.typeCode`。
- `SecuritySetting` 含 `announcementsEnabled` + `dutyLinesEnabled`;`PublicConfig`/`AdminSettingView`/`SecuritySettingUpdateRequest` 同;`SecuritySettingService` 读写;前端 `SecurityAdminView`、`stores/config.ts`(`config.announcementsEnabled/dutyLinesEnabled`)。
- 前端 `features/dashboard/{AnnouncementsPanel,DutyLinesPanel}.vue`(各 `cfg.config.<flag>` 门控)、`HomeView.vue`(info row + `:compact="!!(config.announcementsEnabled||config.dutyLinesEnabled)"`)、`features/admin/announcement-types/*`(待删)、`features/admin/announcements/*`(顶部有开关 switch,待删该 switch)、`features/admin/duty-lines/*`(顶部有开关,待删)。i18n `locales/{zh,en}.ts` + 对齐测试;`AnnouncementsPanel.spec`(含 dismiss 用例,待改)。Flyway oracle 最新 **V11**;`OracleMigrationTest` 断言 11。
- 调色板键 `blue|amber|red|green|purple|slate`(`announcementColor.ts` `colorClasses`)。

---

# 单元 A:后端重构

**Files:** `db/migration/oracle/V12__announcement_types_into_enum.sql`(新);`enumvalue/*`(改);`announcement/*`(改/删);`setting/*`(改);测试。

## A1 Flyway V12
- [ ] 写迁移:
```sql
ALTER TABLE enum_value ADD (color VARCHAR2(16) DEFAULT NULL NULL, icon VARCHAR2(64) DEFAULT NULL NULL);

INSERT INTO enum_value (category, code, label_zh, label_en, sort_order, active, color, icon, created_at, updated_at)
  SELECT 'ANNOUNCEMENT_TYPE', code, label_zh, label_en, sort_order, active, color, icon, SYSTIMESTAMP, SYSTIMESTAMP
  FROM announcement_type;

INSERT INTO enum_value (category, code, label_zh, label_en, sort_order, active, color, icon, created_at, updated_at)
  VALUES ('ANNOUNCEMENT_TYPE','GENERAL','一般通知','General Notice',40,1,'slate','megaphone',SYSTIMESTAMP,SYSTIMESTAMP);

DROP TABLE announcement_type;

ALTER TABLE security_setting DROP (announcements_enabled, duty_lines_enabled);
ALTER TABLE security_setting ADD (info_panel_enabled NUMBER(1) DEFAULT 1 NOT NULL);
```
- [ ] `OracleMigrationTest` 断言 11 → **12**。
> 先确认 `enum_value` 的 created_at/updated_at 是否 NOT NULL；上面 INSERT 已显式给 SYSTIMESTAMP,安全。

## A2 枚举:color/icon
- [ ] `EnumCategory` 增 `ANNOUNCEMENT_TYPE`。
- [ ] `EnumValue` 实体加 `@Column(length=16) String color`(nullable)、`@Column(length=64) String icon`(nullable)+ getter/setter;构造器保持(新建时 color/icon 由 setter 设)。
- [ ] `EnumValueRequest` 加 `String color`、`String icon`(可空);`EnumValueResponse` 加 `color`、`icon`(+ `of` 映射)。`EnumValueService.create/update` 设 color/icon(原样透传,可 null)。
- [ ] 校验(可选):仅当 category=ANNOUNCEMENT_TYPE 时,若 color 非空须 ∈ 调色板 `{blue,amber,red,green,purple,slate}`,否则 `ApiException.badRequest`。其他类别忽略 color/icon。

## A3 公告:改用枚举解析类型 + 删 announcement_type
- [ ] `AnnouncementService`:注入 `EnumValueRepository`(替代 `AnnouncementTypeRepository`)。`toResponse` 用 `enumRepo.findByCategoryAndCode(ANNOUNCEMENT_TYPE, typeCode)`(若无此方法则加;或 `findByCategoryOrderBy...` 过滤)取 color/icon/labelZh/labelEn 注入(fallback color=slate,icon=info,label=typeCode)。create/update 校验 typeCode 是该类别下存在且 active 的枚举值,否则 badRequest。
- [ ] **删除** `AnnouncementType.java`、`AnnouncementTypeRepository.java`、`AnnouncementTypeService.java`、`AnnouncementTypeAdminController.java`、`dto/AnnouncementTypeRequest.java`、`dto/AnnouncementTypeResponse.java`。确认无其他引用。
- [ ] `EnumValueRepository`:如缺,加 `Optional<EnumValue> findByCategoryAndCode(EnumCategory, String)`、`boolean existsByCategoryAndCodeAndActiveTrue(...)`。

## A4 设置:单开关
- [ ] `SecuritySetting`:删 `announcementsEnabled`、`dutyLinesEnabled`;加 `boolean infoPanelEnabled = true`。`PublicConfig`/`AdminSettingView`/`SecuritySettingUpdateRequest`:同样替换为 `infoPanelEnabled`。`SecuritySettingService`:读写 `infoPanelEnabled`(update 时若请求含则设)。

## A5 测试 + 提交
- [ ] 更新受影响测试:`AnnouncementControllerTest`(类型现为枚举:测试前用 `EnumValueService`/直接存一个 ANNOUNCEMENT_TYPE 枚举值,或依赖 V12 迁移已种入 INFO/MAINTENANCE/OUTAGE/GENERAL → 用 'INFO';断言 typeColor/typeIcon 来自枚举;config 现含 `infoPanelEnabled` 而非两旧标志);`DutyLineControllerTest`/`SecuritySettingControllerTest`(config 标志改名;`@BeforeEach` 重置 `infoPanelEnabled=true`);删除/替换任何 `AnnouncementType*Test`;`EnumValue` 测试若有,加 color/icon。
- [ ] `cd cim-portal-server && mvn -q test 2>&1 | grep -E 'Tests run:|BUILD' | tail -4` → SUCCESS(`OracleMigrationTest`=12)。
- [ ] **Commit(后端)**:`refactor(announce): 类型并入枚举(enum_value color/icon + ANNOUNCEMENT_TYPE + GENERAL)+ 单一 infoPanelEnabled 开关 + 删 announcement_type`。

---

# 单元 B:前端重构

**Files:** `lib/api/{enums,portal}.ts`、`stores/config.ts`、`features/admin/enums/{EnumsAdminView,EnumFormModal}.vue`、`features/admin/announcements/*`、删 `features/admin/announcement-types/*` + `lib/api/announcementTypes.ts`、`features/dashboard/{AnnouncementsPanel,DutyLinesPanel}.vue`、`HomeView.vue`、`features/admin/security/SecurityAdminView.vue`、`AdminLayout.vue`、`router/index.ts`、`locales/{zh,en}.ts`、相关 spec。

## B1 删除独立类型管理 + 枚举接管
- [ ] **删除** `features/admin/announcement-types/`(View+Modal)、`lib/api/announcementTypes.ts`;从 `AdminLayout.vue` 导航 + `router/index.ts` 移除 `announcement-types` 项/路由;移除 i18n `admin.nav.announcementTypes`、`admin.announcementTypes.*`、`admin.announcementTypeForm.*`(zh+en 同步,保持对齐)。
- [ ] `lib/api/enums.ts`:`EnumValue` 类型 + `EnumValueInput` 加 `color?: string|null`、`icon?: string|null`。
- [ ] `EnumsAdminView.vue`:categories 列表加 `{ code:'ANNOUNCEMENT_TYPE', labelKey:'admin.enums.categories.announcementType' }`(+ i18n 键 zh「公告类型」/en「Announcement Type」)。列表行在该类别下可显示颜色点 + 图标(可选美化)。
- [ ] `EnumFormModal.vue`:form 加 `color`、`icon`;**当 `category === 'ANNOUNCEMENT_TYPE'`** 显示:颜色 `<Select>`(`ANNOUNCEMENT_COLORS` from `lib/ui/announcementColor`,带色块)+ 图标 picker(复用图标网格 `ICON_KEYS`/`AppIcon`)。其他类别不显示且提交 color/icon 为 undefined/null。编辑回填 color/icon。
- [ ] 公告表单 `features/admin/announcements/AnnouncementFormModal.vue`:类型下拉来源改为 `listEnum('ANNOUNCEMENT_TYPE')`(active),value=code,label=pick。(原来若用 announcementTypes api → 改 enums。)

## B2 单开关 + 面板行为
- [ ] `lib/api/portal.ts` `PortalConfig`:删 `announcementsEnabled?`、`dutyLinesEnabled?`,加 `infoPanelEnabled?: boolean`。`lib/api/admin.ts`(SecuritySettings 类型)同步:删两标志加 `infoPanelEnabled`。
- [ ] `SecurityAdminView.vue`:加「信息栏(公告 + 值班电话)」开关,绑 `infoPanelEnabled`(随其余安全设置一并 PUT/GET)。i18n `admin.security.infoPanelEnabled`。
- [ ] 删除 `features/admin/announcements/AnnouncementsAdminView.vue` 与 `features/admin/duty-lines/DutyLinesAdminView.vue` 顶部的「功能开关」Switch(及相关 security-settings 读写代码);两页只保留内容 CRUD。移除其 i18n `featureEnabled` 等键。
- [ ] `AnnouncementsPanel.vue`:门控改 `cfg.config.infoPanelEnabled`;**移除** dismiss 按钮 + `DISMISSED_KEY`/localStorage/`dismissed`/`items` 过滤(直接用 `all`);模板:`v-if="cfg.config.infoPanelEnabled"` 始终渲染卡;`all.length` 为 0 时显示占位「`dashboard.announcements.empty` = 暂无公告 / No announcements」。移除 `X`/`Pin`? 保留 Pin(置顶标记仍要)。移除 dismiss i18n。
- [ ] `DutyLinesPanel.vue`:门控改 `cfg.config.infoPanelEnabled`;空列表仍不渲染(隐藏)。
- [ ] `HomeView.vue`:info row `v-if="config.infoPanelEnabled"`;`<HeroPanel :compact="!!config.infoPanelEnabled">`;布局:公告占主(若值班为空则公告占满,值班隐藏)——用 `grid` 在有值班时 `1.95fr_1fr`,否则公告单列(可让 AnnouncementsPanel 始终在,DutyLinesPanel 自隐,grid 自然塌缩;若想公告占满,可用 flex/grid auto)。

## B3 测试 + 提交
- [ ] `AnnouncementsPanel.spec.ts`:删 dismiss 相关用例;改门控为 `infoPanelEnabled`;加用例:启用 + 0 公告 → 渲染占位「暂无公告」;启用 + N → 渲染 N(无 × 按钮)。`DutyLinesPanel.spec.ts`:门控改 `infoPanelEnabled`;空→不渲染。`EnumFormModal`/`EnumsAdminView` 测试若有:ANNOUNCEMENT_TYPE 下显示 color/icon。删除 announcement-types 相关测试。i18n 对齐测试须绿。
- [ ] `npm run build` + `npm test` 绿。
- [ ] **Commit(前端)**:`refactor(announce): 类型入枚举(Enums 增 ANNOUNCEMENT_TYPE + color/icon)+ 单 infoPanelEnabled 开关 + 公告常显无关闭 + 删类型管理页`。

---

## 自检清单
- 覆盖:① 枚举接管(A2/A3 + B1)、② GENERAL 种子(A1)、③ 常显/无关闭/自动过期(B2 panel)、④ 单开关 + 分组(A4 + B2)。
- 一致性:`info_panel_enabled`(security_setting ↔ PublicConfig ↔ config store ↔ HomeView v-if/compact ↔ SecurityAdminView 开关);`ANNOUNCEMENT_TYPE`(EnumCategory ↔ 迁移种子 ↔ 公告类型下拉 ↔ 解析);`enum_value.color/icon`(实体 ↔ DTO ↔ 表单 ↔ 调色板 `announcementColor`);删除项无悬挂引用(announcement_type 后端类、announcement-types 前端、两旧 flag、dismiss 逻辑)。
- 硬约束:`mvn test` 绿、`OracleMigrationTest`=12;i18n 对齐;无 group grant/现有行为不破坏;构建无 TS 悬挂引用(删文件后清干净 import)。
