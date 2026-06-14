# Lark(飞书)集成:访问申请 + 意见反馈 设计

> 日期:2026-06-14。范围:无权限链接卡片点击发起「访问申请」、页脚「意见反馈」,二者经 Lark/Feishu API 发送消息给管理员指定的 Lark 用户;Lark 配置在管理页维护。后端 Oracle-only;前端 Vue 3 + TS。

## 背景与目标

- 当前无权限链接卡片点击被硬拦截(SystemCard `locked` → preventDefault,仅显示 noAccessHint)。改为:点击 → 弹「访问申请」表单 → 提交经 Lark 发送给指定 Lark 用户。
- 新增「意见反馈」:页脚链接 → 弹反馈表单 → 提交经 Lark 发送给指定 Lark 用户。
- Lark 配置(base URL、app_id、app_secret、接收者 id、接收者 id 类型)在管理页「安全」配置(镜像现有 duty API 配置:密钥掩码、null=保留/空=清除)。

## 用户决策(已确认)

- **Fire-and-forget**:仅发送给 Lark,不落库、无管理端历史。发送失败 → 用户看到错误可重试。
- **反馈入口**:页脚(SupportBar)「意见反馈」链接。
- **默认区域**:base URL 默认 `https://open.feishu.cn`(飞书,中国),管理员可改为 Lark 国际版 `https://open.larksuite.com`。

## Lark API 契约(已知)

- 取 tenant_access_token:`POST {baseUrl}/open-apis/auth/v3/tenant_access_token/internal`,JSON body `{"app_id":...,"app_secret":...}` → 响应 `{"code":0,"tenant_access_token":"t-...","expire":7200}`(code!=0 为失败)。token 有效约 2h。
- 发消息:`POST {baseUrl}/open-apis/im/v1/messages?receive_id_type={type}`,Header `Authorization: Bearer {tenant_access_token}`,body `{"receive_id":"{id}","msg_type":"text","content":"{\"text\":\"...\"}"}`(content 是 JSON 字符串)→ 响应 `{"code":0,...}`(code!=0 失败)。`receive_id_type` ∈ open_id/user_id/union_id/email/chat_id。

## 硬约束

- 后端 `mvn test` 绿;`OracleMigrationTest` 18→**19**(V19 加 lark 列)。
- 前端 `npm test`(含 i18n zh/en 对齐)+ `npm run build` 绿。
- app_secret 绝不下发浏览器(掩码,仅 `larkAppSecretConfigured` 布尔);PublicConfig 只暴露 `larkEnabled` 布尔(无任何密钥/配置明文)。
- 访问申请/反馈端点需认证(`/api/portal/**`),用 CurrentUser 组装发送者信息。
- 测试中**不真实调用 Lark**(mock LarkClient);LarkClient 永不抛(异常 → 记 warn + 返回失败),LarkService 在「未配置/发送失败」时抛明确错误供前端提示。
- commit trailer:`Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>`;提交前测试须绿。

---

## 后端

### 配置(security_setting,V19)
- **迁移 V19** `V19__lark_integration.sql`:
  ```sql
  ALTER TABLE security_setting ADD (
    lark_base_url        VARCHAR2(512) DEFAULT 'https://open.feishu.cn',
    lark_app_id          VARCHAR2(255),
    lark_app_secret      VARCHAR2(512),
    lark_receiver_id     VARCHAR2(255),
    lark_receiver_id_type VARCHAR2(16) DEFAULT 'email'
  );
  ```
  `OracleMigrationTest` 18→19。
- `SecuritySetting.java`:加 `larkBaseUrl, larkAppId, larkAppSecret, larkReceiverId, larkReceiverIdType`(String,getter/setter)。
- `dto/AdminSettingView.java`:加 `larkBaseUrl, larkAppId, larkReceiverId, larkReceiverIdType`(明文回显)+ `larkAppSecretConfigured`(boolean = secret 非空)。**不含明文 secret**。
- `dto/SecuritySettingUpdateRequest.java`:加 `larkBaseUrl, larkAppId, larkAppSecret, larkReceiverId, larkReceiverIdType`。
- `dto/PublicConfig.java`:**加 `boolean larkEnabled`**(= larkAppId、larkAppSecret、larkReceiverId 三者均非空;不暴露任何明文)。
- `SecuritySettingService`:
  - `adminView()`:填 lark 明文字段 + `larkAppSecretConfigured`;不回 secret 明文。
  - `update()`:larkBaseUrl/larkAppId/larkReceiverId/larkReceiverIdType 直接设(允许置空);**larkAppSecret 仅当 req 非 null 时设**(null=保留;空=清除——`if(req.larkAppSecret()!=null) s.setLarkAppSecret(req.larkAppSecret().isBlank()?null:req.larkAppSecret())`,镜像 dutyApiKey)。
  - `publicView()`:`larkEnabled = appId/secret/receiver 均非空`。
  - 保存后失效 Lark token 缓存(注入 `LarkTokenCache` 调 clear/evict;与现有 duty 缓存失效一致)。

### LarkClient(镜像 OnDutyClient)
`com/cimportal/lark/LarkClient.java`(@Component,注入 `RestClient.Builder`,3s connect/read 超时,永不抛):
- `Optional<String> tenantAccessToken(String baseUrl, String appId, String appSecret)`:POST `{baseUrl}/open-apis/auth/v3/tenant_access_token/internal`,body `{app_id, app_secret}`;解析 `{code, tenant_access_token, expire}`(`@JsonIgnoreProperties(ignoreUnknown=true)`);`code==0 && token!=null` → Optional[token],否则 empty(记 warn,不抛)。可顺带返回 expire(用于缓存 TTL)——见缓存。
- `boolean sendText(String baseUrl, String token, String receiveIdType, String receiveId, String text)`:POST `{baseUrl}/open-apis/im/v1/messages?receive_id_type={type}`,Bearer token,body `{receive_id, msg_type:"text", content: <JSON string of {"text": text}>}`(用 Jackson 生成 content 的 JSON 字符串,正确转义);解析响应 `code==0` → true,否则 false(记 warn,不抛)。

### LarkTokenCache(镜像 OnDutyCache)
`com/cimportal/lark/LarkTokenCache.java`(@Component,注入 `Clock` + `LarkClient`):
- 进程内 `ConcurrentHashMap<String, Entry>`,key = `appId`(或 `baseUrl+'|'+appId`);Entry = `(Optional<String> token, Instant expiresAt)`。
- `Optional<String> get(baseUrl, appId, appSecret)`:命中且未过期 → 返回;否则 `client.tenantAccessToken(...)`,成功则缓存 TTL = `min(expire-60s, 2h)`(无 expire 信息时用固定 110 分钟),失败不长缓存(可不缓存 empty 或短 TTL)。
- `void clear()`:清空(SecuritySettingService 保存后调用)。

### LarkService(应用层)
`com/cimportal/lark/LarkService.java`(@Component,注入 `SecuritySettingService`/repo 读配置、`LarkTokenCache`、`LarkClient`、`Clock`):
- `void sendAccessRequest(CurrentUser user, Link link, String reason)`、`void sendFeedback(CurrentUser user, String message)`。
- 读 SecuritySetting 的 lark 配置;若 appId/secret/receiver 任一为空 → 抛 `ApiException.badRequest("Lark 未配置")`(前端提示)。
- 组装中文文本消息:
  - 访问申请:`【访问申请】\n申请人:{displayName}({employeeId}) · 部门 {departmentCode}\n系统:{linkName}\n说明:{reason 或 (无)}\n时间:{now}`
  - 反馈:`【用户反馈】\n来自:{displayName}({employeeId}) · 部门 {departmentCode}\n内容:{message}\n时间:{now}`
  - displayName:user_info 的中文名(若 CurrentUser 无名字,用 employeeId;实现时取可得字段;时间用注入 Clock 本地化或 ISO)。
- 取 token(经 cache),`sendText(...)`;失败(token 取不到或 send 返回 false)→ 抛 `ApiException`(如 502/400「发送失败」)供前端提示。

### 端点(authenticated /api/portal)
`com/cimportal/lark/LarkController.java`(@RestController `/api/portal`):
- `POST /access-requests`,body `AccessRequestRequest(@NotNull Long linkId, String reason)`:经 `CurrentUserService.require()` 取 user;按 linkId 取 Link(不存在 → 404);`larkService.sendAccessRequest(user, link, reason)`;成功 200(空体或 `{ok:true}`)。
- `POST /feedback`,body `FeedbackRequest(@NotBlank String message)`(可加 @Size 上限如 2000):`larkService.sendFeedback(user, message)`;成功 200。
- 二者认证即可(SecurityConfig `/api/portal/**` 已 authenticated)。

---

## 前端

### 配置 store + API
- `src/lib/api/portal.ts`:`PortalConfig` 加 `larkEnabled?: boolean`(config store 透传)。
- `src/lib/api/portal.ts`:`requestAccess(linkId: number, reason?: string)` → POST `/api/portal/access-requests`;`sendFeedback(message: string)` → POST `/api/portal/feedback`。
- `src/lib/api/admin.ts`:`SecuritySettings` 加 `larkBaseUrl?, larkAppId?, larkReceiverId?, larkReceiverIdType?`(string|null)+ `larkAppSecretConfigured?: boolean`;`SecuritySettingsInput` 加 `larkBaseUrl?, larkAppId?, larkAppSecret?, larkReceiverId?, larkReceiverIdType?`。

### 管理页 Lark 配置(SecurityAdminView)
- 在「值班系统对接」分区下新增「Lark(飞书)集成」分区:`larkBaseUrl` 输入(占位 `https://open.feishu.cn`)、`larkAppId` 输入、`larkAppSecret` password 输入(占位据 `larkAppSecretConfigured` 显示「已配置,留空不修改 / 未配置」+ hint「仅后端保存,不下发」)、`larkReceiverId` 输入、`larkReceiverIdType` Select(open_id/user_id/union_id/email/chat_id)。保存:base/appId/receiver/type 直接传;`larkAppSecret` 仅当用户输入了才传(空=不改)。沿用现有保存流(updateSecuritySettings + reload + toast)。
- i18n `admin.security.lark.*`(title、baseUrl、appId、appSecret、appSecretConfigured、appSecretUnset、appSecretHint、receiverId、receiverIdType、各 idType 选项标签)两端对齐。

### 访问申请流程
- `SystemCard.vue`:`onClick` 的 locked 分支由 `e.preventDefault(); return` 改为 `e.preventDefault(); emit('access-request', link); return`。emit 定义加 `'access-request': [HomeLink]`。
- `SystemGrid.vue`:透传 `@access-request="emit('access-request', $event)"`(与现有 `blocked`/`favorite-changed` 透传一致),emit 定义加。
- `HomeView.vue`:监听 SystemGrid 的 `access-request`:若 `config.larkEnabled` → 打开 `AccessRequestModal`(传 link);否则不弹(保持现 noAccess 行为,可 toast `dashboard.noAccessHint`)。
- 新 `src/features/dashboard/AccessRequestModal.vue`:props `open` + `link: HomeLink | null`;表单:系统名(只读展示 `pick(link,'name')`)+ 可选「申请说明」textarea;提交 `requestAccess(link.id, reason)` → 成功 toast(`dashboard.accessRequest.sent`)+ 关闭;失败 toast 错误。提交中禁用。
- i18n `dashboard.accessRequest.*`(title、systemLabel、reasonLabel、reasonPlaceholder、submit、sent、failed)。

### 意见反馈(页脚)
- `src/lib/ui/SupportBar.vue`:在支持电话旁加「意见反馈 / Send feedback」按钮/链接,**仅当 `config.larkEnabled`** 显示(SupportBar 引 config store)。点击打开 `FeedbackModal`。
- 新 `src/features/feedback/FeedbackModal.vue`(或 `src/lib/ui/` 就近):props `open`;表单:`message` textarea(必填,maxlength ~2000)+ 字数/校验;提交 `sendFeedback(message)` → 成功 toast(`feedback.sent`)+ 清空关闭;失败 toast。
- i18n `feedback.*`(link、title、placeholder、submit、sent、failed、required)。

---

## 测试策略

- **后端:**
  - `OracleMigrationTest`=19;V19 后 security_setting 有 lark 列(默认 base feishu)。
  - `LarkClient`(用 `MockRestServiceServer` 绑定 RestClient.Builder,不真实联网):tenantAccessToken 解析 `{code:0,tenant_access_token,expire}` → token;`code!=0` → empty;5xx/超时 → empty(不抛)。sendText:`code:0` → true;`code!=0` → false;异常 → false。content 为合法转义 JSON 字符串。
  - `LarkTokenCache`(可控 Clock):命中/过期;失败不长缓存;clear() 清空。
  - `LarkService`(mock LarkClient/cache):未配置(appId/secret/receiver 缺)→ 抛 badRequest;配置齐全 → 组装消息含申请人/系统/时间并调用 sendText;send 失败 → 抛。
  - `SecuritySettingService`/controller:PUT lark 字段 → adminView 回显 base/appId/receiver/type + `larkAppSecretConfigured=true`,**响应无明文 secret**;PUT 不传 secret → 保留;`/api/portal/config` 含 `larkEnabled`(配齐 true、缺则 false),**不含任何 lark 明文**。
  - LarkController:未认证 401;认证 + mock LarkService → access-request(linkId 不存在 404、存在 200)、feedback(空 message 400、有 message 200)。
  - 全套 `mvn -q test` 绿。
- **前端:**
  - i18n zh/en 对齐(新增 admin.security.lark.*、dashboard.accessRequest.*、feedback.*)。
  - AccessRequestModal:提交调用 requestAccess + 成功 toast;FeedbackModal:必填校验 + 提交调用 sendFeedback + toast。
  - SystemCard:locked 点击 emit `access-request`(不再静默)。HomeView:larkEnabled 时开 modal,否则不开。
  - SupportBar:`config.larkEnabled` 时显示反馈链接,否则隐藏。
  - SecurityAdminView:lark 分区字段存在;保存 secret 仅在输入时带上。
  - `npm test` + `npm run build` 绿。

## 自检 / 一致性

- 配置:security_setting +5 lark 列(V19);AdminSettingView 掩码 secret;PublicConfig 仅 larkEnabled;update null=保留/空=清除(secret)。
- 客户端:LarkClient(token+send,永不抛)+ LarkTokenCache(Clock,~2h,save 失效)+ LarkService(读配置/组装/发送/未配置或失败抛)。
- 端点:/api/portal/access-requests、/feedback(认证,fire-and-forget)。
- 前端:larkEnabled 透传;SystemCard→SystemGrid→HomeView access-request → AccessRequestModal;SupportBar 反馈链接(larkEnabled 门控)→ FeedbackModal;SecurityAdminView lark 分区(secret 掩码)。
- 命名一致:`lark*` 配置字段、`larkAppSecretConfigured`、`larkEnabled`、`requestAccess`/`sendFeedback`、`AccessRequestModal`/`FeedbackModal`、端点路径。
- `OracleMigrationTest`=19;i18n 三组键两端对齐;Lark 测试中 mock,密钥不下发。
- fire-and-forget(无表、无历史);反馈在页脚;base 默认 feishu。
