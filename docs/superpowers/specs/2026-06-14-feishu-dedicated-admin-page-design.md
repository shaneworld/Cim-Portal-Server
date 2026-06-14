# 飞书(Feishu/Lark)配置独立管理页 设计

> 日期:2026-06-14。范围:把飞书集成配置从「安全」管理页中抽离,改为管理后台中一个独立页面 `/admin/feishu`,由一个**专属后端端点** `/api/admin/lark-settings` 读写(只触碰 `security_setting` 表的 `lark_*` 列)。不改变门户侧行为(`larkEnabled` 门控、访问申请弹窗、意见反馈入口均不变)。

## 背景

飞书的 5 个配置字段(`larkBaseUrl/larkAppId/larkAppSecret/larkReceiverId/larkReceiverIdType`)目前与 SSO、值班 API 配置同存于 `SecurityAdminView.vue`,共用 `GET/PUT /api/admin/security-settings`(一次性读取并整体提交全部字段)。这带来两个问题:(1)发现性差——管理员要在「安全」页里找飞书设置;(2)若让独立页复用同一 PUT 但只提交飞书字段,后端会把未提交的 SSO/值班非密字段按「blank→null」清空,造成误删配置。

## 用户决策(已确认)

- **专属端点**:新增 `GET/PUT /api/admin/lark-settings`,只读写 `lark_*` 列。`larkAppSecret` 始终掩码(只回 `larkAppSecretConfigured` 布尔),`null=保留 / blank=清空`;保存时清空飞书 token 缓存。SSO/值班配置完全不受影响。
- **从安全页移除**:飞书配置整体迁出「安全」页(移动,非复制)。「安全」页仅保留 SSO + 值班 API;飞书配置只在新页面。单一数据源。
- **命名**:导航与页面标题用「飞书 / Feishu」。

## 硬约束

- 后端 `mvn test` 绿。**无 DB 迁移**(`lark_*` 列已存在于 V19);`OracleMigrationTest` 仍为 **19**。
- 前端 `npm test`(含 i18n zh/en 对齐)+ `npm run build` 绿。
- **安全**:`larkAppSecret` 绝不下发浏览器——`LarkSettingsView` 只含 `larkAppSecretConfigured`;`/api/portal/config` 仅暴露 `larkEnabled`(不变)。
- `larkEnabled` 推导(`appId && appSecret && receiverId` 三者非空)与门户门控行为**保持不变**。
- commit trailer:`Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>`;提交前测试须绿。

---

## 后端(cim-portal-server)

### 新 DTO
- `setting/dto/LarkSettingsView.java`(record,响应):`larkBaseUrl, larkAppId, larkReceiverId, larkReceiverIdType, boolean larkAppSecretConfigured`。**无明文 secret**。
- `setting/dto/LarkSettingsUpdateRequest.java`(record,请求):`larkBaseUrl, larkAppId, larkAppSecret, larkReceiverId, larkReceiverIdType`(均 String,可空)。

### SecuritySettingService:新增两方法
- `LarkSettingsView larkSettingsView()`:从单例读取,`larkAppSecretConfigured = nb(secret)`。
- `@Transactional LarkSettingsView updateLarkSettings(LarkSettingsUpdateRequest req)`:语义与现有 `update()` 中的飞书分支逐字一致——
  - `larkBaseUrl/larkAppId/larkReceiverId/larkReceiverIdType`:非 null 时设置,`blank→null`;
  - `larkAppSecret`:`null=保留`,`blank=清空`,否则设置;
  - `setUpdatedAt(Instant.now())`;`repo.save`;刷新 `cached`;`larkTokenCache.clear()`;返回 `larkSettingsView()`。

### SecuritySettingService:从安全设置中移除飞书
- `adminView()`:删去 5 个飞书参数(`larkBaseUrl/larkAppId/larkReceiverId/larkReceiverIdType/larkAppSecretConfigured`)。
- `update()`:删去 5 个飞书字段的赋值分支(`larkBaseUrl/larkAppId/larkReceiverId/larkReceiverIdType/larkAppSecret`)。**保留** `larkTokenCache.clear()`? —— 否:该清缓存仅与飞书凭据相关,移入 `updateLarkSettings()`;`update()` 不再 import/调用 `LarkTokenCache`(若 `update()` 再无其它用途则从构造器移除注入;但 `updateLarkSettings()` 仍需,故 `LarkTokenCache` 注入**保留**在 service)。
- `publicView()`:`larkEnabled` 推导**不变**(仍读实体 `lark_*`)。

### DTO 收缩(安全设置)
- `dto/AdminSettingView.java`:删去 `larkBaseUrl, larkAppId, larkReceiverId, larkReceiverIdType, larkAppSecretConfigured` 5 字段。
- `dto/SecuritySettingUpdateRequest.java`:删去 `larkBaseUrl, larkAppId, larkAppSecret, larkReceiverId, larkReceiverIdType` 5 字段(`@AssertTrue isSsoConfigValid` 不变)。

### 新控制器
- `setting/LarkSettingsAdminController.java`,`@RequestMapping("/api/admin/lark-settings")`(`/api/admin/**` 已由 SecurityConfig 限 PORTAL_ADMIN):
  - `@GetMapping LarkSettingsView get()` → `service.larkSettingsView()`。
  - `@PutMapping LarkSettingsView update(@Valid @RequestBody LarkSettingsUpdateRequest req)` → `service.updateLarkSettings(req)`。

### 测试
- 新 `LarkSettingsControllerTest`(扩展 `OracleIntegrationTest` + `TestJwts`,真 Oracle):
  - `adminGet_requiresPortalAdmin`:无/非管理员 token → 401/403。
  - `adminPut_setsFields_withoutEchoingPlaintextSecret`:PUT 全字段 → 回显 base/appId/receiver/type + `larkAppSecretConfigured:true`,且响应体**不含**明文 secret、不含 `larkAppSecret"` 键。
  - `adminPut_omittingSecret_keepsExistingSecret`:先设 secret,再仅 PUT appId(无 secret)→ 仍 `configured:true`。
  - `adminPut_blankSecret_clearsSecret`:PUT `larkAppSecret:""` → `configured:false`。
  - `publicConfig_larkEnabled_reflectsConfig`:经新端点配齐 appId+secret+receiver → `/api/portal/config` `larkEnabled:true`;清空 receiver → `false`;且 public config 不泄露任何飞书明文。
- 调整 `SecuritySettingControllerTest`:删除 3 个飞书用例(`adminPut_lark_*`、`publicConfig_larkEnabled_*`)——已移入新测试;`seed()` 仍重置 `lark_*` 列(保持隔离)。其余 SSO/值班/hero 用例不变。
- `OracleMigrationTest` 仍断言 19(无新迁移)。

---

## 前端(cim-portal-client)

### API 层 `src/lib/api/admin.ts`
- 从 `SecuritySettings`(view)删去 `larkBaseUrl/larkAppId/larkReceiverId/larkReceiverIdType/larkAppSecretConfigured`。
- 从 `SecuritySettingsInput` 删去 `larkBaseUrl/larkAppId/larkAppSecret/larkReceiverId/larkReceiverIdType`。
- 新增类型与函数:
  ```ts
  export interface LarkSettings {
    larkBaseUrl?: string | null
    larkAppId?: string | null
    larkReceiverId?: string | null
    larkReceiverIdType?: string | null
    larkAppSecretConfigured?: boolean
  }
  export interface LarkSettingsInput {
    larkBaseUrl?: string
    larkAppId?: string
    larkAppSecret?: string
    larkReceiverId?: string
    larkReceiverIdType?: string
  }
  export const getLarkSettings = () => request<LarkSettings>('GET', '/api/admin/lark-settings')
  export const updateLarkSettings = (body: LarkSettingsInput) =>
    request<LarkSettings>('PUT', '/api/admin/lark-settings', body)
  ```

### 路由 + 导航
- `src/router/index.ts`:在 `security` 路由后加 `{ path: 'feishu', name: 'admin-feishu', component: () => import('@/features/admin/feishu/FeishuAdminView.vue') }`。
- `src/features/admin/AdminLayout.vue`:`nav` 数组在 `/admin/security` 后加 `{ to: '/admin/feishu', labelKey: 'admin.nav.feishu', icon: Send, enabled: true }`(`Send` 来自 `lucide-vue-next`)。

### 新页面 `src/features/admin/feishu/FeishuAdminView.vue`
- 结构镜像现 `SecurityAdminView` 的飞书分区:`AdminPanel`(title `admin.feishu.title`)+ `loading`/`saving` + `max-w-lg` 表单。
- 字段:`larkBaseUrl`(占位 `https://open.feishu.cn`)、`larkAppId`、`larkAppSecret`(password,占位据 `configured` 显「已配置,留空不修改/未配置」+ hint)、`larkReceiverId`、`larkReceiverIdType`(Select:open_id/user_id/union_id/email/chat_id)。
- 数据:`onMounted` 调 `getLarkSettings()`→`populate`;`populate` 中 secret 重置为 `''`,`larkReceiverIdType ?? 'email'`,`larkAppSecretConfigured` 存独立 ref。
- 保存 `save()`:body 带 `larkBaseUrl/larkAppId/larkReceiverId/larkReceiverIdType`(`|| undefined`),`larkAppSecret` **仅当非空才带**;`updateLarkSettings(body)`→`populate(resp)`+成功 toast;catch→失败 toast。复用 `t('common.updated')`/`t('common.saveFailed')`。

### `SecurityAdminView.vue` 收缩
- 删除整个「Lark(飞书)集成」分区(模板 line 160–200 区块)。
- 删除 `form` 中 5 个 lark 字段、`larkAppSecretConfigured` ref、`receiverIdTypeOptions`(若仅飞书用)、`populate()` 中 5 行 lark 赋值、`save()` body 中 lark 相关项。`Select` import 若不再使用则移除。

### i18n(两端,键集一致)
- `admin.nav.feishu`:zh「飞书」/ en「Feishu」。
- 新 `admin.feishu`(由 `admin.security.lark` 迁移并改键):`{ title, baseUrl, appId, appSecret, appSecretConfigured, appSecretUnset, appSecretHint, receiverId, receiverIdType }`;`title` zh「飞书集成」/ en「Feishu Integration」,其余沿用原 lark 文案。
- 删除 `admin.security.lark` 整块(两端)。

### 测试
- 新 `src/features/admin/feishu/FeishuAdminView.spec.ts`(镜像原 SecurityAdminView 的飞书断言,改 msw 路径为 `/api/admin/lark-settings`):
  - mount 后绑定 base/appId/receiver;secret 输入恒为空。
  - save:blank secret → body 不含 `larkAppSecret`,但含 base/appId/receiver/type;非空 secret → body 含该值。
- 调整 `SecurityAdminView.spec.ts`:删除 2 个飞书用例(`loads and binds lark fields`、`save sends larkAppSecret only when...`);`mockSettings` 删去 lark 字段;保留 SSO/initialPassword 用例。
- i18n parity 测试经 `admin.feishu` + `admin.nav.feishu` 两端对齐后通过。
- (可选)`router/index.spec.ts` 不强制改;`/admin/feishu` 仍受 `meta.admin` 门控。

---

## 测试策略 / 联调

- 后端:`mvn -q test` 绿(148 → 仍 148 上下;3 个飞书用例从 SecuritySettingControllerTest 迁入新测试,净增若干断言)。`OracleMigrationTest`=19。
- 前端:`npm test`(parity)+ `npm run build` 绿。
- 联调:重启 dev(`fuser -k 8080/tcp`,勿 pkill);`GET /api/admin/lark-settings` 回显掩码;`GET /api/admin/security-settings` **不再含**任何 lark 字段;`PUT /api/admin/lark-settings` 配齐 → `/api/portal/config` `larkEnabled:true`;前端管理后台出现「飞书」导航项,页面可配置(secret 掩码);「安全」页不再有飞书分区。门户侧访问申请/反馈行为不变。

## 自检 / 一致性

- 安全:secret 仅存于实体 + 入站 `LarkSettingsUpdateRequest`;`LarkSettingsView`/`PublicConfig` 均无明文;无端点返回实体。
- 单一数据源:飞书仅 `/admin/feishu` 可写;安全页与 `/api/admin/security-settings` 不再涉及 lark。
- 行为不变:`larkEnabled` 推导、门户门控、token 缓存清理语义保持。
- 命名一致:后端 `lark-settings`/`LarkSettings*`(沿用代码内 Lark 命名);前端路由 `feishu`、i18n `admin.feishu`/`admin.nav.feishu`、页面标题「飞书集成」。
- 无 DB 迁移;`OracleMigrationTest`=19。
