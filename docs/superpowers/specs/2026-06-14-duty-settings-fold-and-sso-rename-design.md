# 值班接口配置并入值班页 + 安全页改名 SSO 设计

> 日期:2026-06-14。范围:(1) 把「值班系统对接」配置(`dutyApiBaseUrl` + 掩码 `dutyApiKey`)从「安全」页迁出,作为一个配置分区**并入既有「值班电话」管理页**,由专属端点 `/api/admin/duty-settings` 读写(只触碰 duty API 两列);(2) 把「安全」导航标签与页面标题改为 **SSO**(仅文案,路由 `/admin/security` 与文件名不变)。门户侧值班 API 调用行为不变。

## 背景

`dutyApiBaseUrl`/`dutyApiKey` 目前与 SSO 同存于 `SecurityAdminView`,共用 `GET/PUT /api/admin/security-settings`(整体读写全部字段)。`DutyLineService` 通过 `SecuritySettingService.get()`(缓存单例)读取这两列调用外部值班 API。既有 `/admin/duty-lines`(值班电话)页管理的是值班**人员/电话数据**(CRUD 列表),与值班 API **凭据配置**是两回事。用户决定:把 API 凭据配置并入值班电话页(同页两区:接口设置 + 人员列表),不新增导航项;并把「安全」标签更名 SSO(SSO 与内部初始密码留在该页)。

## 用户决策(已确认)

- **并入值班电话页**:值班 API 配置作为「值班电话」页顶部的「接口设置」分区;仍走专属端点 `/api/admin/duty-settings`(只读写 duty 两列,绝不波及 SSO);不新增侧栏项。
- **安全 → SSO(仅文案)**:导航标签与页面标题改 SSO;路由 `/admin/security`、组件文件 `SecurityAdminView.vue` 不变。
- 后端采用专属端点(沿用上一轮 Feishu 同样的隔离理由),非复用 security-settings。

## 硬约束

- 后端 `mvn test` 绿;**无 DB 迁移**(duty 两列已存在);`OracleMigrationTest` 仍 **19**。
- 前端 `npm test`(含 i18n zh/en 对齐)+ `npm run build` 绿。
- **安全**:`dutyApiKey` 绝不下发浏览器——`DutySettingsView` 只含 `dutyApiKeyConfigured`;`PublicConfig` 不含 duty(本就不含)。
- 行为不变:`DutyLineService` 仍从 `SecuritySettingService.get()` 缓存单例读取 baseUrl/key;`updateDutySettings()` 刷新该缓存,确保即时生效。
- commit trailer:`Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>`;提交前测试须绿。

---

## 后端(cim-portal-server)

### 新 DTO
- `setting/dto/DutySettingsView.java`(record,响应):`String dutyApiBaseUrl, boolean dutyApiKeyConfigured`。**无明文 key**。
- `setting/dto/DutySettingsUpdateRequest.java`(record,请求):`String dutyApiBaseUrl, String dutyApiKey`。

### SecuritySettingService:新增两方法
- `DutySettingsView dutySettingsView()`:从单例读取;`dutyApiKeyConfigured = nb(dutyApiKey)`。
- `@Transactional DutySettingsView updateDutySettings(DutySettingsUpdateRequest req)`:
  - `dutyApiBaseUrl`:非 null 时设置,`blank→null`;
  - `dutyApiKey`:`null=保留`,`blank=清空`,否则设置;
  - `setUpdatedAt(Instant.now())`;`repo.save`;`synchronized (this) { cached = saved; }`;返回 `dutySettingsView()`。(duty 无 token 缓存,无需 clear。)

### SecuritySettingService:从安全设置移除 duty
- `adminView()`:删去 `dutyApiBaseUrl` 与 `dutyApiKeyConfigured` 两个构造参数。
- `update()`:删去 duty 两行赋值(`dutyApiBaseUrl`、`dutyApiKey` 及其 `// null=keep / blank=clear` 注释)。`update()` 仍保留其缓存刷新与 `return adminView()`。
- `publicView()`、`nb()`、`get()`、Lark 相关方法不变。

### DTO 收缩(安全设置)
- `dto/AdminSettingView.java`:删去 `dutyApiBaseUrl`、`dutyApiKeyConfigured`。
- `dto/SecuritySettingUpdateRequest.java`:删去 `dutyApiBaseUrl`、`dutyApiKey`(`@AssertTrue isSsoConfigValid` 不变)。

### 新控制器
- `setting/DutySettingsAdminController.java`,`@RequestMapping("/api/admin/duty-settings")`:
  - `@GetMapping DutySettingsView get()` → `service.dutySettingsView()`。
  - `@PutMapping DutySettingsView update(@Valid @RequestBody DutySettingsUpdateRequest req)` → `service.updateDutySettings(req)`。

### 测试
- 新 `DutySettingsControllerTest`(扩展 `OracleIntegrationTest`,`@AutoConfigureMockMvc`,`MockMvc mvc`,`TestJwts jwts`,`@BeforeEach seed()` 重置 duty 两列 + `service.invalidateCache()`;镜像 `SecuritySettingControllerTest` 脚手架):
  - `adminGet_requiresPortalAdmin`:匿名 → 401;非管理员 `OP1` → 403。
  - `adminPut_setsBaseUrlAndKey_withoutEchoingPlaintextKey`:PUT `{"dutyApiBaseUrl":"https://duty.example.com","dutyApiKey":"super-secret-key"}` → 200,`$.dutyApiBaseUrl` 回显,`$.dutyApiKeyConfigured`=true;响应体 `doesNotContain("super-secret-key")` 且不含 `dutyApiKey"` 键。
  - `adminPut_omittingKey_keepsExistingKey`:先设 baseUrl+key,再仅 PUT 新 baseUrl(无 key)→ baseUrl 更新、`dutyApiKeyConfigured` 仍 true。
  - `adminPut_blankKey_clearsKey`:设 key 后 PUT `{"dutyApiKey":""}` → `dutyApiKeyConfigured`=false。
  - `publicConfig_doesNotExposeDutyApiFields`:经新端点配 duty → `/api/portal/config` 体不含 `dutyApi`/`duty.example.com`/key 值。
- `SecuritySettingControllerTest`:删除迁出的两个 duty 用例(`adminPut_dutyApi_setsBaseUrlAndKey_withoutEchoingPlaintextKey`、`adminPut_dutyApi_omittingKey_keepsExistingKey`)与 `publicConfig_doesNotExposeDutyApiFields`(三者迁入新测试);`seed()` 仍保留 `setDutyApi*(null)` 重置。其余 SSO/hero/password 用例不变。
- `OracleMigrationTest`=19(无迁移)。

---

## 前端(cim-portal-client)

### API 层 `src/lib/api/admin.ts`
- 从 `SecuritySettings`(view)删去 `dutyApiBaseUrl`、`dutyApiKeyConfigured`。
- 从 `SecuritySettingsInput` 删去 `dutyApiBaseUrl`、`dutyApiKey`。
- 新增:
  ```ts
  export interface DutySettings {
    dutyApiBaseUrl?: string | null
    dutyApiKeyConfigured?: boolean
  }
  export interface DutySettingsInput {
    dutyApiBaseUrl?: string
    dutyApiKey?: string
  }
  export const getDutySettings = () => request<DutySettings>('GET', '/api/admin/duty-settings')
  export const updateDutySettings = (body: DutySettingsInput) =>
    request<DutySettings>('PUT', '/api/admin/duty-settings', body)
  ```

### 新组件 `src/features/admin/duty-lines/DutySettingsForm.vue`
- 自包含:`onMounted` 调 `getDutySettings()`→`populate`;`save()` 调 `updateDutySettings()`。
- 用 `GlassCard`(`p-5`),标题 `admin.dutySettings.title`,`max-w-lg` 表单:
  - `dutyApiBaseUrl` Input(占位 `http://duty-system:port`);
  - `dutyApiKey` password Input(占位据 `dutyApiKeyConfigured` 显「已配置,留空不修改/未配置」+ `apiKeyHint`);
  - 保存 Button(复用 `admin.security.save`)。
- `populate`:`dutyApiKey` 重置为 `''`;`dutyApiKeyConfigured` 存独立 ref。
- 保存 body:`dutyApiBaseUrl: form.dutyApiBaseUrl || undefined`;`dutyApiKey` **仅当非空才带**;`updateDutySettings`→`populate(resp)`+成功 toast(`common.updated`),catch→失败 toast(`common.saveFailed`)。

### 并入 `src/features/admin/duty-lines/DutyLinesAdminView.vue`
- 根节点由单个 `<AdminPanel>` 改为纵向栈:
  ```html
  <div class="flex h-full flex-col gap-4">
    <DutySettingsForm />
    <AdminPanel class="min-h-0 flex-1" :title="t('admin.dutyLines.title')" v-model:page-size="rowsPerPage">
      …(现有 actions/列表/footer 不变)…
    </AdminPanel>
  </div>
  <DutyLineFormModal .../> <ConfirmDialog .../>  <!-- 仍并列于根 fragment -->
  ```
- import `DutySettingsForm`。其余逻辑(load/分页/增删)不变。响应式页大小仍由表格自身 `AdminPanel` 测量。

### `SecurityAdminView.vue` 收缩
- 删除「值班系统对接」分区(`admin.security.dutyApiSection` 那个 `border-t pt-5` 块及其两字段)。
- 删除 `form` 的 `dutyApiBaseUrl/dutyApiKey`、`dutyApiKeyConfigured` ref、`populate()` 中 duty 行、`save()` body 中 duty 项(`dutyApiBaseUrl` 与 `...(form.dutyApiKey ? {dutyApiKey} : {})`)。
- 保留:SSO 开关 + issuer/clientId/scopes/usernameClaim/initialPassword + 保存按钮。

### i18n(两端,键集一致)
- `admin.nav.security` 值 → `SSO`(两端)。(键名不变)
- `admin.security.title` → zh「SSO 设置」/ en「SSO Settings」。
- 从 `admin.security` **删除** duty 键:`dutyApiSection, dutyApiBaseUrl, dutyApiKey, dutyApiKeyHint, dutyApiKeyConfigured, dutyApiKeyUnset`。**保留** `admin.security.save`(SSO 页 + Feishu 页 + 值班接口表单共用)。
- 新增 `admin.dutySettings` 块(两端):
  - zh:`{ title:'接口设置', baseUrl:'接口地址', apiKey:'API 密钥', apiKeyConfigured:'已配置,留空不修改', apiKeyUnset:'未配置', apiKeyHint:'用于调用外部值班系统;仅后端保存,不会下发' }`
  - en:`{ title:'API Settings', baseUrl:'API base URL', apiKey:'API key', apiKeyConfigured:'Configured — leave blank to keep', apiKeyUnset:'Not configured', apiKeyHint:'Used to call the external duty system; stored server-side only, never sent to the browser' }`

### 测试
- 新 `src/features/admin/duty-lines/DutySettingsForm.spec.ts`(镜像现有 admin 表单 msw + mount 范式,针对 `/api/admin/duty-settings`):
  - mount 后绑定 `dutyApiBaseUrl`;`dutyApiKey` 输入恒为空。
  - save:blank key → body 不含 `dutyApiKey`,含 `dutyApiBaseUrl`;非空 key → body 含该值。
- `SecurityAdminView.spec.ts`:不受影响(其 `mockSettings` 与用例不含 duty);若误含则一并清理。
- i18n parity:`admin.dutySettings` 两端 + 删除 `admin.security.duty*` 后键集仍对齐。

---

## 测试策略 / 联调

- 后端:`mvn -q test` 绿;`OracleMigrationTest`=19。
- 前端:`npm test`(parity)+ `npm run build`。
- 联调:重启 dev(`fuser -k 8080/tcp`,勿 pkill);`GET /api/admin/security-settings` 不再含 duty 字段;`GET /api/admin/duty-settings` 回显掩码;`PUT /api/admin/duty-settings` 配 baseUrl+key → adminView 掩码,`/api/portal/config` 不泄露;前端「值班电话」页顶部出现「接口设置」分区(key 掩码),「安全」标签显示 **SSO** 且页内无值班分区。门户侧 Duty Phone(经外部 API)行为不变。
- 清理:撤销测试期填入的 duty 配置(或保留,听用户)。

## 自检 / 一致性

- 安全:`dutyApiKey` 仅存实体 + 入站 `DutySettingsUpdateRequest`;`DutySettingsView`/`PublicConfig` 无明文;无端点返回实体。
- 单一数据源:duty API 配置仅 `/api/admin/duty-settings` 可写;安全(SSO)页与 `/api/admin/security-settings` 不再涉及 duty。
- 行为不变:`DutyLineService` 读路径不变,缓存随 `updateDutySettings()` 刷新。
- 命名一致:后端 `duty-settings`/`DutySettings*`;前端表单 `DutySettingsForm`、i18n `admin.dutySettings`;SSO 改名仅 `admin.nav.security` 值 + `admin.security.title`。
- 无 DB 迁移;`OracleMigrationTest`=19。
