# 显示设置(Hero 开关 + 显示设置页)+ 配置刷新修复 实现计划

> REQUIRED SUB-SKILL: superpowers:subagent-driven-development。单元 A 后端 → B 前端。提交前测试须绿。trailer `Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>`。

## 背景 / 根因
- **Bug(#1):** `stores/config.ts` 的 `load()` 有 `if (loaded.value) return`,整会话只取一次 `/api/portal/config`,**保存设置后或进入首页时从不刷新**。导致开关切换/旧会话下首页读到陈旧 config(`infoPanelEnabled` 为旧值或 undefined)→ 面板不显示。后端/DB 均正确(info_panel_enabled=1、config 返回 true、数据齐全)。
- **需求(#2):** 新增 Hero 面板开关;把 Hero 开关 + 信息栏开关归到一个新的「显示设置 / Display」管理页(信息栏开关从 安全页 迁出)。

## 现状(已核对)
- `stores/config.ts`:`config` ref + `loaded` + `load()`(guarded)。`PortalConfig` 现含 `ssoEnabled, authority, clientId, scopes, usernameClaim, infoPanelEnabled?`。
- `SecuritySetting` 含 `infoPanelEnabled`;`PublicConfig`/`AdminSettingView`/`SecuritySettingUpdateRequest` 同;`SecuritySettingService`;`/api/admin/security-settings` GET/PUT;前端 `SecurityAdminView.vue`(含 infoPanelEnabled Switch,line 73-76)。
- `HomeView.vue`:`<HeroPanel :compact="!!config.infoPanelEnabled" />`;info row `v-if="config.infoPanelEnabled"`;`load()` 取 home。`AdminLayout.vue` 导航 + `router/index.ts`。Flyway oracle 最新 **V12**;`OracleMigrationTest` 断言 12。
- 调色板/枚举类型已就绪(上轮)。

## 硬约束
`mvn test` 绿;`OracleMigrationTest`=13;i18n 对齐;首页向后兼容(config 仅追加 `heroEnabled`);保存设置或进入首页后,开关变更**无需手动刷新**即体现。

---

# 单元 A:后端(V13 + hero 开关)

**Files:** `db/migration/oracle/V13__hero_enabled.sql`(新);`setting/{SecuritySetting,SecuritySettingService}.java`、`dto/{PublicConfig,AdminSettingView,SecuritySettingUpdateRequest}.java`;`OracleMigrationTest`;`SecuritySettingControllerTest`。
- [ ] **V13**:`ALTER TABLE security_setting ADD (hero_enabled NUMBER(1) DEFAULT 1 NOT NULL);`。`OracleMigrationTest` 12→**13**。
- [ ] `SecuritySetting` 加 `boolean heroEnabled = true`(+getter/setter)。`PublicConfig`、`AdminSettingView`、`SecuritySettingUpdateRequest` 各加 `heroEnabled`(与 `infoPanelEnabled` 并列)。`SecuritySettingService.publicView/adminView/update` 处理(update 时若请求含则设)。
- [ ] 测试:`SecuritySettingControllerTest` `@BeforeEach` 重置 `heroEnabled=true`;断言 `/api/portal/config` 含 `heroEnabled`、PUT 可改。`mvn -q test` 绿(`OracleMigrationTest`=13)。
- [ ] **Commit(后端)**:`feat(settings): hero 面板开关(V13 hero_enabled + config/admin DTO)`。

---

# 单元 B:前端(配置刷新修复 + 显示设置页 + Hero 开关)

**Files:** `stores/config.ts`、`features/dashboard/HomeView.vue`、`lib/api/{portal,admin}.ts`、新 `features/admin/display/DisplaySettingsAdminView.vue`、`features/admin/security/SecurityAdminView.vue`(移除 info 开关)、`AdminLayout.vue`、`router/index.ts`、`locales/{zh,en}.ts`、相关 spec。

## B1 配置刷新修复(#1)
- [ ] `stores/config.ts`:加 `async function reload() { try { config.value = await getConfig() } catch { /* keep */ } loaded.value = true }`(无视 loaded,强制取)。导出 `reload`。保留 `load()`(bootstrap 用)。
- [ ] `HomeView.vue`:在 `load()`(onMounted)里**先 `await useConfigStore().reload()`** 再取 home(或并行);确保每次进入首页拿到最新 config → 面板按当前开关渲染。引入 config store。
- [ ] 设置保存后刷新:`DisplaySettingsAdminView` 与 `SecurityAdminView` 保存成功后调用 `useConfigStore().reload()`(切换即时生效)。

## B2 显示设置页 + Hero 开关(#2)
- [ ] `lib/api/portal.ts` `PortalConfig` 加 `heroEnabled?: boolean`。`lib/api/admin.ts` `SecuritySettings`/`SecuritySettingsInput` 加 `heroEnabled?: boolean`。
- [ ] 新 `features/admin/display/DisplaySettingsAdminView.vue`:加载 `getSecuritySettings()`;两个 Switch —— `heroEnabled`(Hero 面板)、`infoPanelEnabled`(信息栏:公告 + 值班);保存 `updateSecuritySettings({heroEnabled, infoPanelEnabled})`(仅这两项即可,后端 update 只改传入字段;若 update 要求完整对象,则传当前其余值)成功后 `reload()` config + toast。
- [ ] `SecurityAdminView.vue`:**移除** infoPanelEnabled Switch + 其 form 字段(及 save body 里的 infoPanelEnabled);Security 页仅保留 SSO 配置。其 save 也调用 `reload()`(保持一致,可选)。
- [ ] `AdminLayout.vue` 导航 + `router/index.ts`:新增 `display` 项/路由(`admin.nav.display`)。位置建议在 security 之前或之后。
- [ ] `HomeView.vue`:`<HeroPanel v-if="config.heroEnabled" :compact="!!config.infoPanelEnabled" />`(heroEnabled 默认 true → 现状不变)。
- [ ] i18n:加 `admin.nav.display`(显示设置 / Display)、`admin.display.*`(title、heroPanel、infoPanel、说明);把 `admin.security.infoPanelEnabled` 文案迁到 `admin.display.infoPanel`(Security 页不再用);zh/en 对齐。

## B3 测试 + 提交
- [ ] `config.ts` 若有 store 测试:加 reload 用例。`HomeView` spec:mock config reload(确保仍渲染)。`SecurityAdminView`/新 `DisplaySettingsAdminView` 轻量测试(开关存在 + 保存调 update + reload)。i18n 对齐绿。
- [ ] `npm run build` + `npm test` 绿(注意删除 SecurityAdminView 的 info 开关后,任何引用其 testid `info-panel-enabled` 的测试迁到新页)。
- [ ] **Commit(前端)**:`feat(settings): 显示设置页(Hero + 信息栏开关)+ 进入首页/保存后刷新 config(修复开关不生效)`。

---

## 自检
- #1 修复:config `reload()` + HomeView 进入即 reload + 保存后 reload → 开关变更无需手刷即生效。
- #2:`heroEnabled`(V13 ↔ config ↔ HomeView v-if)、显示设置页含两开关、信息栏开关迁出安全页。
- 一致性:`heroEnabled`/`infoPanelEnabled`(security_setting ↔ PublicConfig ↔ config store ↔ HomeView ↔ DisplaySettingsAdminView);`OracleMigrationTest`=13;i18n 对齐;HeroPanel `compact` 仍 = infoPanelEnabled。
