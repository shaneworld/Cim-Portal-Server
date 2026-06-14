# Lark 集成:访问申请 + 意见反馈 实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development。提交前 `mvn -q test` / `npm test` 须绿。commit trailer:`Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>`。

**Goal:** 无权限链接卡点击发起访问申请、页脚意见反馈,经 Lark/Feishu 发送给管理员配置的 Lark 用户;Lark 配置在安全管理页(密钥掩码)。fire-and-forget,不落库。

**Architecture:** 后端 security_setting 加 lark 配置(V19,镜像 duty API 掩码)+ PublicConfig.larkEnabled;LarkClient(token+send,镜像 OnDutyClient)+ LarkTokenCache(镜像 OnDutyCache)+ LarkService(读配置/组装/发送/未配置或失败抛)+ LarkController(/api/portal access-requests、feedback)。前端:配置 larkEnabled;SecurityAdminView Lark 分区;locked 卡 → access-request → HomeView AccessRequestModal;SupportBar 反馈链接 → FeedbackModal。

**Tech Stack:** Spring Boot 3.3/JDK 21/Oracle/Flyway/Spring RestClient;Vue 3.5 + TS + Tailwind + vue-i18n。

仓库:后端 `/home/shane/Code/cim-portal/cim-portal-server`,前端 `/home/shane/Code/cim-portal/cim-portal-client`。

---

## 单元 A:后端 —— Lark 配置(V19 + security_setting + 掩码 + larkEnabled)

**Files:**
- Create: `src/main/resources/db/migration/oracle/V19__lark_integration.sql`
- Modify: `setting/SecuritySetting.java`、`setting/dto/AdminSettingView.java`、`setting/dto/SecuritySettingUpdateRequest.java`、`setting/dto/PublicConfig.java`、`setting/SecuritySettingService.java`
- Modify: `src/test/java/com/cimportal/migration/OracleMigrationTest.java`(18→19)
- Test: `setting/SecuritySettingControllerTest`(扩展)

- [ ] **Step 1: V19 迁移 + OracleMigrationTest 19**

`V19__lark_integration.sql`:
```sql
ALTER TABLE security_setting ADD (
  lark_base_url         VARCHAR2(512) DEFAULT 'https://open.feishu.cn',
  lark_app_id           VARCHAR2(255),
  lark_app_secret       VARCHAR2(512),
  lark_receiver_id      VARCHAR2(255),
  lark_receiver_id_type VARCHAR2(16) DEFAULT 'email'
);
```
`OracleMigrationTest`:18 → 19。Run `mvn -q -Dtest=OracleMigrationTest test` → 绿("now at version v19")。

- [ ] **Step 2: SecuritySetting 实体 + DTO 加 lark 字段**

`SecuritySetting.java`:加 `String larkBaseUrl, larkAppId, larkAppSecret, larkReceiverId, larkReceiverIdType` + getter/setter(列名 lark_base_url 等)。
`AdminSettingView.java`(record):加 `String larkBaseUrl, String larkAppId, String larkReceiverId, String larkReceiverIdType, boolean larkAppSecretConfigured`(无明文 secret)。
`SecuritySettingUpdateRequest.java`(record):加 `String larkBaseUrl, larkAppId, larkAppSecret, larkReceiverId, larkReceiverIdType`。
`PublicConfig.java`(record):加 `boolean larkEnabled`。

- [ ] **Step 3: SecuritySettingService adminView/update/publicView**

读 `SecuritySettingService.java`(现有 dutyApi* 掩码逻辑)。
- `adminView()`:填 lark 明文字段 + `larkAppSecretConfigured = s.getLarkAppSecret()!=null && !s.getLarkAppSecret().isBlank()`。
- `update(req)`:`setLarkBaseUrl/AppId/ReceiverId/ReceiverIdType(req...)`(直接设,允许空);secret:`if (req.larkAppSecret() != null) s.setLarkAppSecret(req.larkAppSecret().isBlank() ? null : req.larkAppSecret());`(镜像 dutyApiKey)。
- `publicView()`(构造 PublicConfig 处):`boolean larkEnabled = notBlank(s.getLarkAppId()) && notBlank(s.getLarkAppSecret()) && notBlank(s.getLarkReceiverId());`(notBlank 小工具:`x!=null && !x.isBlank()`),传入 PublicConfig。
- (token 缓存失效在单元 B 接入 LarkTokenCache 后,在此 update() 末尾调用 `larkTokenCache.clear()`——单元 B Step 完成后回填;本单元先不引,见 B。)

- [ ] **Step 4: SecuritySettingControllerTest 扩展**

沿用现有测试:PUT lark 字段(base/appId/secret/receiver/type)→ GET adminView 回显 base/appId/receiver/type + `larkAppSecretConfigured==true`,**响应体不含明文 secret 值**;PUT 不传 larkAppSecret(仅改 appId)→ secret 保留(configured 仍 true);`GET /api/portal/config`:配齐时 `larkEnabled==true` 且**不含任何 lark 明文**;缺 receiver 时 `larkEnabled==false`。

- [ ] **Step 5: 全测 + 提交**

`mvn -q test` 绿(`OracleMigrationTest`=19)。
```bash
git add -A
git commit -m "feat(lark): Lark 配置纳入 security_setting(V19,密钥掩码)+ PublicConfig.larkEnabled

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## 单元 B:后端 —— LarkClient + LarkTokenCache + LarkService + 端点

**Files:**
- Create: `src/main/java/com/cimportal/lark/LarkClient.java`、`LarkTokenCache.java`、`LarkService.java`、`LarkController.java`、`dto/AccessRequestRequest.java`、`dto/FeedbackRequest.java`
- Modify: `setting/SecuritySettingService.java`(update() 末尾调 larkTokenCache.clear())
- Test: `src/test/java/com/cimportal/lark/LarkClientTest.java`、`LarkServiceTest.java`、`LarkControllerTest.java`

- [ ] **Step 1: 写 LarkClient 失败测试**

读 `dutyline/OnDutyClient.java` + 其测试(MockRestServiceServer 绑定 RestClient.Builder 的方式;OnDutyClient 有可注入 builder 的构造)。`LarkClientTest`:
```java
// tenant token:mock POST {base}/open-apis/auth/v3/tenant_access_token/internal → 200 {"code":0,"tenant_access_token":"t-abc","expire":7200}
//   → tenantAccessToken(base, "id","sec") == Optional["t-abc"]
// code!=0:{"code":99991663,"msg":"x"} → empty
// 5xx / 异常 → empty(不抛)
// sendText:mock POST {base}/open-apis/im/v1/messages?receive_id_type=email → 200 {"code":0} → true
//   code!=0 → false;异常 → false
//   断言请求体 content 是合法 JSON 字符串(含转义),Authorization: Bearer t-abc
```
镜像 OnDutyClientTest 的 MockRestServiceServer 用法。Run `mvn -q -Dtest=LarkClientTest test` → 红。

- [ ] **Step 2: 实现 LarkClient**

`LarkClient.java`(@Component,注入 `RestClient.Builder builder` + 可注入构造供测试,3s 超时工厂,镜像 OnDutyClient):
```java
@JsonIgnoreProperties(ignoreUnknown=true) record TokenResp(int code, String tenant_access_token, Integer expire) {}
@JsonIgnoreProperties(ignoreUnknown=true) record SendResp(int code, String msg) {}

public Optional<TokenInfo> tenantAccessToken(String baseUrl, String appId, String appSecret) {
  if (blank(baseUrl)||blank(appId)||blank(appSecret)) return Optional.empty();
  try {
    TokenResp r = client().post().uri(baseUrl + "/open-apis/auth/v3/tenant_access_token/internal")
       .contentType(APPLICATION_JSON).body(Map.of("app_id",appId,"app_secret",appSecret))
       .retrieve().body(TokenResp.class);
    if (r!=null && r.code()==0 && r.tenant_access_token()!=null)
      return Optional.of(new TokenInfo(r.tenant_access_token(), r.expire()==null?7200:r.expire()));
    log.warn("Lark token failed: code={}", r==null?null:r.code()); return Optional.empty();
  } catch (Exception e) { log.warn("Lark token error: {}", e.toString()); return Optional.empty(); }
}
public boolean sendText(String baseUrl, String token, String receiveIdType, String receiveId, String text) {
  try {
    String content = objectMapper.writeValueAsString(Map.of("text", text)); // 正确转义
    SendResp r = client().post().uri(baseUrl + "/open-apis/im/v1/messages?receive_id_type={t}", receiveIdType)
       .header("Authorization","Bearer "+token).contentType(APPLICATION_JSON)
       .body(Map.of("receive_id",receiveId,"msg_type","text","content",content))
       .retrieve().body(SendResp.class);
    if (r!=null && r.code()==0) return true;
    log.warn("Lark send failed: code={}", r==null?null:r.code()); return false;
  } catch (Exception e) { log.warn("Lark send error: {}", e.toString()); return false; }
}
```
(`TokenInfo(String token, int expireSeconds)` 简单 record;`client()` 用注入 builder + 超时工厂构建,镜像 OnDutyClient;注入 `ObjectMapper`。)
Run `mvn -q -Dtest=LarkClientTest test` → 绿。

- [ ] **Step 3: LarkTokenCache(镜像 OnDutyCache)**

`LarkTokenCache.java`(@Component,注入 `Clock` + `LarkClient`):
```java
private record Entry(Optional<String> token, Instant expiresAt) {}
private final Map<String, Entry> cache = new ConcurrentHashMap<>();
public Optional<String> get(String baseUrl, String appId, String appSecret) {
  Instant now = clock.instant(); String key = baseUrl + "|" + appId;
  Entry e = cache.get(key);
  if (e != null && e.expiresAt().isAfter(now)) return e.token();
  Optional<LarkClient.TokenInfo> info = client.tenantAccessToken(baseUrl, appId, appSecret);
  if (info.isEmpty()) return Optional.empty(); // 失败不缓存
  long ttl = Math.max(60, info.get().expireSeconds() - 60); ttl = Math.min(ttl, 7200);
  cache.put(key, new Entry(Optional.of(info.get().token()), now.plusSeconds(ttl)));
  return Optional.of(info.get().token());
}
public void clear() { cache.clear(); }
```
`LarkServiceTest` 后续 mock 这个;`SecuritySettingService.update()` 末尾注入并调 `larkTokenCache.clear()`(回到 A-Step3 的占位,现接上)。

- [ ] **Step 4: 写 LarkService 失败测试 + 实现**

`LarkServiceTest`(mock `LarkTokenCache`、`LarkClient`、读 SecuritySetting 的途径——若 LarkService 注入 `SecuritySettingRepository`/Service,mock 返回配置;用固定 Clock):
```java
// 未配置(appId 空)→ sendAccessRequest 抛 ApiException(badRequest)
// 配置齐全 + cache 返回 token + client.sendText 返回 true → 不抛;捕获 sendText 的 text 参数含 "访问申请"、系统名、employeeId
// sendText 返回 false → 抛 ApiException
// sendFeedback 同理:文本含 "用户反馈" + message
```
`LarkService.java`(@Component):注入 `SecuritySettingService`(或 repo 读单例配置)、`LarkTokenCache`、`LarkClient`、`Clock`。
```java
public void sendAccessRequest(CurrentUser user, Link link, String reason) {
  Cfg c = cfg(); // 读 lark 配置,缺 → throw badRequest("Lark 未配置")
  String linkName = link.getNameZh(); // 或按需
  String text = "【访问申请】\n申请人:" + user.employeeId() + " · 部门 " + user.departmentCode()
      + "\n系统:" + linkName + "\n说明:" + (reason==null||reason.isBlank()?"(无)":reason)
      + "\n时间:" + clock.instant();
  send(c, text);
}
public void sendFeedback(CurrentUser user, String message) {
  Cfg c = cfg();
  String text = "【用户反馈】\n来自:" + user.employeeId() + " · 部门 " + user.departmentCode()
      + "\n内容:" + message + "\n时间:" + clock.instant();
  send(c, text);
}
private void send(Cfg c, String text) {
  String token = tokenCache.get(c.baseUrl, c.appId, c.appSecret)
      .orElseThrow(() -> ApiException.badGateway("Lark 鉴权失败"));
  if (!client.sendText(c.baseUrl, token, c.receiverIdType, c.receiverId, text))
      throw ApiException.badGateway("Lark 发送失败");
}
```
(`Cfg` 内部小 record 持 baseUrl/appId/appSecret/receiverId/receiverIdType;`cfg()` 读 SecuritySetting,任一 appId/appSecret/receiverId 空 → `ApiException.badRequest("Lark 未配置")`;baseUrl 空兜底 'https://open.feishu.cn',type 空兜底 'email'。`ApiException.badGateway` 若不存在则用既有 5xx/`badRequest` 工厂——按现有 ApiException 可用方法选,确保是非 2xx 让前端提示。CurrentUser 字段按实际:employeeId/departmentCode;若可取中文名则拼上。)
Run `mvn -q -Dtest=LarkServiceTest test` → 绿。

- [ ] **Step 5: LarkController + DTO + 测试**

`dto/AccessRequestRequest.java`:`record AccessRequestRequest(@NotNull Long linkId, String reason)`。
`dto/FeedbackRequest.java`:`record FeedbackRequest(@NotBlank @Size(max=2000) String message)`。
`LarkController.java`(@RestController `@RequestMapping("/api/portal")`):注入 `LarkService`、`CurrentUserService`、`LinkRepository`(取 Link)。
```java
@PostMapping("/access-requests")
public void accessRequest(@Valid @RequestBody AccessRequestRequest req) {
  CurrentUser u = currentUser.require();
  Link link = linkRepo.findById(req.linkId()).orElseThrow(() -> ApiException.notFound("链接"));
  lark.sendAccessRequest(u, link, req.reason());
}
@PostMapping("/feedback")
public void feedback(@Valid @RequestBody FeedbackRequest req) {
  lark.sendFeedback(currentUser.require(), req.message());
}
```
`LarkControllerTest`(OracleIntegrationTest + TestJwts;mock `LarkService` via `@MockBean` 以免真实发送):未带 token → 401;带 token:access-request linkId 不存在 → 404、存在 → 200(verify lark.sendAccessRequest 调用);feedback 空 message → 400、有 message → 200。
Run `mvn -q -Dtest=LarkControllerTest test` → 绿。

- [ ] **Step 6: 全测 + 提交**

`mvn -q test` 绿。
```bash
git add -A
git commit -m "feat(lark): LarkClient/TokenCache/Service + 访问申请与反馈端点(fire-and-forget)

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## 单元 C:前端 —— 配置 larkEnabled + API + 管理页 Lark 分区

**Files:**
- Modify: `src/lib/api/portal.ts`(PortalConfig.larkEnabled + requestAccess/sendFeedback)、`src/lib/api/admin.ts`(SecuritySettings/Input lark 字段)
- Modify: `src/features/admin/security/SecurityAdminView.vue`(Lark 分区)
- Modify: `src/lib/i18n/locales/zh.ts`、`en.ts`(admin.security.lark.*)

- [ ] **Step 1: API 类型 + 函数**

`portal.ts`:`PortalConfig` 加 `larkEnabled?: boolean`。新增:
```ts
export const requestAccess = (linkId: number, reason?: string) =>
  request<void>('POST', '/api/portal/access-requests', { linkId, reason })
export const sendFeedback = (message: string) =>
  request<void>('POST', '/api/portal/feedback', { message })
```
(对齐 `request` 签名——读现有 request 的 body 传参方式。)
`admin.ts`:`SecuritySettings` 加 `larkBaseUrl?: string|null; larkAppId?: string|null; larkReceiverId?: string|null; larkReceiverIdType?: string|null; larkAppSecretConfigured?: boolean`;`SecuritySettingsInput` 加 `larkBaseUrl?, larkAppId?, larkAppSecret?, larkReceiverId?, larkReceiverIdType?`(string)。

- [ ] **Step 2: i18n(两端)**

`admin.security.lark`:`{ title, baseUrl, appId, appSecret, appSecretConfigured, appSecretUnset, appSecretHint, receiverId, receiverIdType }`。zh 例:title「Lark(飞书)集成」、baseUrl「接口地址」、appId「App ID」、appSecret「App Secret」、appSecretConfigured「已配置,留空不修改」、appSecretUnset「未配置」、appSecretHint「仅后端保存,不会下发」、receiverId「接收者 ID」、receiverIdType「接收者 ID 类型」。en 对应英文。两端键集一致。

- [ ] **Step 3: SecurityAdminView Lark 分区**

在「值班系统对接」分区后,加「Lark(飞书)集成」分区(沿用该文件分区/保存写法):
- `larkBaseUrl` Input(占位 https://open.feishu.cn)、`larkAppId` Input、`larkAppSecret` password Input(占位据 `settings.larkAppSecretConfigured` 显「已配置,留空不修改/未配置」+ hint)、`larkReceiverId` Input、`larkReceiverIdType` Select(选项 open_id/user_id/union_id/email/chat_id,值=label 即可)。
- 保存 body:`larkBaseUrl/larkAppId/larkReceiverId/larkReceiverIdType` 直接带;`larkAppSecret` 仅当输入非空才带(空=不改)。沿用现有 `updateSecuritySettings` + reload + toast。

- [ ] **Step 4: 测 + build**

`npm test`(i18n parity;SecurityAdminView 若有 spec,断言 lark 字段存在 + 保存 secret 仅在输入时带)。`npm run build`。

- [ ] **Step 5: 提交**

```bash
git add -A
git commit -m "feat(lark): 前端 Lark 配置(安全页分区,密钥掩码)+ larkEnabled/requestAccess/sendFeedback API

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## 单元 D:前端 —— 访问申请流程(locked 卡 → 弹窗)

**Files:**
- Modify: `src/features/dashboard/SystemCard.vue`(locked emit access-request)、`src/features/dashboard/SystemGrid.vue`(透传)、`src/features/dashboard/HomeView.vue`(监听 + modal)
- Create: `src/features/dashboard/AccessRequestModal.vue`
- Modify: `src/lib/i18n/locales/zh.ts`、`en.ts`(dashboard.accessRequest.*)
- Test: AccessRequestModal spec + SystemCard spec

- [ ] **Step 1: SystemCard locked → emit access-request**

`SystemCard.vue`:emit 定义加 `'access-request': [HomeLink]`。`onClick` locked 分支:`if (locked.value) { e.preventDefault(); emit('access-request', props.link); return }`(替换原 `return`)。其余不变(仍 `cursor-not-allowed` 视觉;title noAccessHint 保留)。

- [ ] **Step 2: SystemGrid 透传**

`SystemGrid.vue`:`<SystemCard ... @access-request="emit('access-request', $event)" />`;emit 定义加 `'access-request': [HomeLink]`。

- [ ] **Step 3: i18n + AccessRequestModal**

i18n(两端)`dashboard.accessRequest`:`{ title:'申请访问/Request access', systemLabel:'系统/System', reasonLabel:'申请说明(可选)/Reason (optional)', reasonPlaceholder, submit:'提交申请/Request', sent:'申请已发送/Request sent', failed:'发送失败/Failed to send' }`。
新 `AccessRequestModal.vue`:props `open:boolean`、`link: HomeLink | null`;emit `update:open`。表单:系统名只读(`pick(link,'name')`)、可选 reason textarea;`submit()`:`await requestAccess(link.id, reason)` → `toast.push success sent` + emit update:open false;catch → toast error failed;`submitting` 禁用。用 Modal(size md)+ footer 取消/提交。

- [ ] **Step 4: HomeView 接 access-request**

`HomeView.vue`:SystemGrid 用法加 `@access-request="onAccessRequest"`。脚本:`const arOpen=ref(false); const arLink=ref<HomeLink|null>(null); function onAccessRequest(l:HomeLink){ if(config.larkEnabled){ arLink.value=l; arOpen.value=true } else { toast.push({type:'info',message:t('dashboard.noAccessHint')}) } }`(引 toast/config)。模板末尾 `<AccessRequestModal v-model:open="arOpen" :link="arLink" />`。

- [ ] **Step 5: 测 + build + 提交**

`npm test`(AccessRequestModal:提交调用 requestAccess + toast;SystemCard:locked emit access-request)。`npm run build`。
```bash
git add -A
git commit -m "feat(access-request): 无权限链接点击发起访问申请弹窗(larkEnabled 门控)

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## 单元 E:前端 —— 意见反馈(页脚链接 + 弹窗)

**Files:**
- Modify: `src/lib/ui/SupportBar.vue`(反馈链接 + modal,larkEnabled 门控)
- Create: `src/features/feedback/FeedbackModal.vue`
- Modify: `src/lib/i18n/locales/zh.ts`、`en.ts`(feedback.*)
- Test: FeedbackModal spec

- [ ] **Step 1: i18n + FeedbackModal**

i18n(两端)`feedback`:`{ link:'意见反馈/Feedback', title:'意见反馈/Send feedback', placeholder:'请输入您的意见或问题…/Your feedback…', submit:'提交/Submit', sent:'反馈已发送,谢谢!/Thanks, sent!', failed:'发送失败/Failed to send', required:'请输入内容/Please enter your feedback' }`。
新 `FeedbackModal.vue`:props `open`;emit `update:open`。表单:`message` textarea(必填,maxlength 2000);`submit()`:空 → 行内 required 提示/禁用;`await sendFeedback(message)` → toast success sent + 清空 + 关闭;catch → toast failed。Modal size md + footer。

- [ ] **Step 2: SupportBar 反馈链接(larkEnabled)**

`SupportBar.vue`:引 config store + FeedbackModal + 本地 `fbOpen=ref(false)`。在支持电话旁加按钮 `<button v-if="cfg.config.larkEnabled" @click="fbOpen=true" class="...link 样式...">{{ t('feedback.link') }}</button>`(主题化、与页脚风格一致)。模板加 `<FeedbackModal v-model:open="fbOpen" />`。

- [ ] **Step 3: 测 + build + 提交**

`npm test`(FeedbackModal:必填 + 提交调用 sendFeedback + toast;SupportBar:larkEnabled 时显示链接,否则隐藏——若有 SupportBar spec 则扩展)。i18n parity。`npm run build`。
```bash
git add -A
git commit -m "feat(feedback): 页脚意见反馈入口 + 弹窗(经 Lark 发送,larkEnabled 门控)

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## 单元 F:联调验证(无代码)

- [ ] **Step 1: 后端**:build + 重启 dev(`fuser -k 8080/tcp`,勿 pkill portal.jar)。日志 "now at version v19"。`GET /api/portal/config`:未配置 lark 时 `larkEnabled:false`、无 lark 明文。PUT `/api/admin/security-settings` 配 larkBaseUrl/appId/appSecret/receiverId/receiverIdType;GET adminView:回显 base/appId/receiver/type + `larkAppSecretConfigured:true`,无明文 secret;config 现 `larkEnabled:true`。POST `/api/portal/access-requests`{linkId:<某无权限链接>,reason}、`/api/portal/feedback`{message}——若填了**真实** Lark 凭据则消息送达指定用户;否则(测试凭据)返回「鉴权/发送失败」错误(预期,证明链路通)。
- [ ] **Step 2: 前端**(`npm run dev`):管理页安全 → Lark 分区可配置(secret 掩码);配置 larkEnabled 后:无权限链接卡点击 → 弹「申请访问」(系统名 + 说明)→ 提交 toast;页脚出现「意见反馈」→ 弹窗提交 toast。未配置时:无权限卡保持原 noAccess 行为、页脚无反馈链接。
- [ ] **Step 3:** 清理:撤销测试期填入的 Lark 配置(或保留真实配置,听用户)。

---

## 自检(plan vs spec)

- 配置(A):V19 5 列;SecuritySetting/AdminSettingView(掩码 secret)/UpdateRequest/PublicConfig(larkEnabled);Service adminView/update(null=保留/空=清除)/publicView;OracleMigrationTest=19。✅
- 客户端(B):LarkClient(token+send,永不抛)+ LarkTokenCache(Clock,TTL≤2h,clear)+ LarkService(读配置/组装中文消息/未配置或失败抛)+ LarkController(/access-requests、/feedback,认证)+ DTO;update() clear token 缓存。✅
- 前端配置(C):PortalConfig.larkEnabled + requestAccess/sendFeedback;admin.ts lark 字段;SecurityAdminView Lark 分区(secret 掩码);i18n。✅
- 访问申请(D):SystemCard emit→SystemGrid 透传→HomeView(larkEnabled 门控)→AccessRequestModal;i18n。✅
- 反馈(E):SupportBar 链接(larkEnabled 门控)+ FeedbackModal;i18n。✅
- 命名一致:lark 配置字段、`larkAppSecretConfigured`、`larkEnabled`、`requestAccess`/`sendFeedback`、`AccessRequestModal`/`FeedbackModal`、`/api/portal/access-requests`、`/api/portal/feedback`、`TokenInfo`/`clear()`。✅
- 安全:secret 不下发(AdminSettingView 掩码、PublicConfig 仅 larkEnabled);端点认证;测试 mock Lark 不真实联网。✅
- fire-and-forget(无表/无历史);反馈在页脚;base 默认 feishu;OracleMigrationTest=19;i18n 三组键两端对齐。✅
