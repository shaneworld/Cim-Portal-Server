# 飞书配置独立管理页 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Move Feishu/Lark settings out of the Security admin page into a dedicated `/admin/feishu` page backed by an isolated `GET/PUT /api/admin/lark-settings` endpoint that touches only the `lark_*` columns.

**Architecture:** Backend adds two thin DTOs + two service methods + one controller for `/api/admin/lark-settings`, and removes the Lark fields from the existing security-settings DTOs/service. Frontend adds a new route, nav item, and `FeishuAdminView.vue`, removes the Lark section from `SecurityAdminView.vue`, and renames the i18n keys. No DB migration (the `lark_*` columns already exist from V19); portal-facing `larkEnabled` behavior is unchanged.

**Tech Stack:** Spring Boot 3.3 / JDK 21 / Oracle (Testcontainers) backend; Vue 3 + TS + Pinia + vue-router + Vitest + msw frontend.

**Repos:** backend `/home/shane/Code/cim-portal/cim-portal-server` (branch `dev`), frontend `/home/shane/Code/cim-portal/cim-portal-client` (branch `dev`). Commit on `dev` in each (project uses branch-driven profiles; `dev` is the working branch).

---

## File Structure

**Backend (cim-portal-server):**
- Create: `src/main/java/com/cimportal/setting/dto/LarkSettingsView.java` — masked response DTO.
- Create: `src/main/java/com/cimportal/setting/dto/LarkSettingsUpdateRequest.java` — inbound write DTO.
- Create: `src/main/java/com/cimportal/setting/LarkSettingsAdminController.java` — `/api/admin/lark-settings`.
- Create: `src/test/java/com/cimportal/setting/LarkSettingsControllerTest.java`.
- Modify: `src/main/java/com/cimportal/setting/SecuritySettingService.java` — add `larkSettingsView()`/`updateLarkSettings()`; strip Lark from `adminView()`/`update()`.
- Modify: `src/main/java/com/cimportal/setting/dto/AdminSettingView.java` — remove 5 Lark fields.
- Modify: `src/main/java/com/cimportal/setting/dto/SecuritySettingUpdateRequest.java` — remove 5 Lark fields.
- Modify: `src/test/java/com/cimportal/setting/SecuritySettingControllerTest.java` — remove the 3 migrated Lark tests.

**Frontend (cim-portal-client):**
- Create: `src/features/admin/feishu/FeishuAdminView.vue`.
- Create: `src/features/admin/feishu/FeishuAdminView.spec.ts`.
- Modify: `src/lib/api/admin.ts` — strip Lark from `SecuritySettings`/`SecuritySettingsInput`; add `LarkSettings`/`LarkSettingsInput` + functions.
- Modify: `src/router/index.ts` — add `feishu` route.
- Modify: `src/features/admin/AdminLayout.vue` — add nav item.
- Modify: `src/features/admin/security/SecurityAdminView.vue` — remove Lark section.
- Modify: `src/features/admin/security/SecurityAdminView.spec.ts` — remove Lark tests + Lark fields from `mockSettings`.
- Modify: `src/lib/i18n/locales/zh.ts`, `src/lib/i18n/locales/en.ts` — `admin.nav.feishu` + `admin.feishu.*`; delete `admin.security.lark`.

---

## Task 1: Backend — Lark settings DTOs + service methods + controller

**Files:**
- Create: `src/main/java/com/cimportal/setting/dto/LarkSettingsView.java`
- Create: `src/main/java/com/cimportal/setting/dto/LarkSettingsUpdateRequest.java`
- Create: `src/main/java/com/cimportal/setting/LarkSettingsAdminController.java`
- Modify: `src/main/java/com/cimportal/setting/SecuritySettingService.java`
- Test: `src/test/java/com/cimportal/setting/LarkSettingsControllerTest.java`

- [ ] **Step 1: Create the response DTO**

`src/main/java/com/cimportal/setting/dto/LarkSettingsView.java`:
```java
package com.cimportal.setting.dto;

public record LarkSettingsView(
    String larkBaseUrl,
    String larkAppId,
    String larkReceiverId,
    String larkReceiverIdType,
    boolean larkAppSecretConfigured
) { }
```

- [ ] **Step 2: Create the request DTO**

`src/main/java/com/cimportal/setting/dto/LarkSettingsUpdateRequest.java`:
```java
package com.cimportal.setting.dto;

public record LarkSettingsUpdateRequest(
    String larkBaseUrl,
    String larkAppId,
    String larkAppSecret,
    String larkReceiverId,
    String larkReceiverIdType
) { }
```

- [ ] **Step 3: Add `larkSettingsView()` + `updateLarkSettings()` to the service**

In `src/main/java/com/cimportal/setting/SecuritySettingService.java`, add these two methods (place after `adminView()`). Add the imports `com.cimportal.setting.dto.LarkSettingsView` and `com.cimportal.setting.dto.LarkSettingsUpdateRequest`:
```java
public LarkSettingsView larkSettingsView() {
    SecuritySetting s = get();
    return new LarkSettingsView(
        s.getLarkBaseUrl(),
        s.getLarkAppId(),
        s.getLarkReceiverId(),
        s.getLarkReceiverIdType(),
        nb(s.getLarkAppSecret())
    );
}

@Transactional
public LarkSettingsView updateLarkSettings(LarkSettingsUpdateRequest req) {
    SecuritySetting s = repo.findById(SINGLETON_ID)
        .orElseThrow(() -> new IllegalStateException("security_setting row missing"));

    if (req.larkBaseUrl() != null) s.setLarkBaseUrl(req.larkBaseUrl().isBlank() ? null : req.larkBaseUrl());
    if (req.larkAppId() != null) s.setLarkAppId(req.larkAppId().isBlank() ? null : req.larkAppId());
    if (req.larkReceiverId() != null) s.setLarkReceiverId(req.larkReceiverId().isBlank() ? null : req.larkReceiverId());
    if (req.larkReceiverIdType() != null) s.setLarkReceiverIdType(req.larkReceiverIdType().isBlank() ? null : req.larkReceiverIdType());
    // null = keep existing secret; blank = clear
    if (req.larkAppSecret() != null) s.setLarkAppSecret(req.larkAppSecret().isBlank() ? null : req.larkAppSecret());
    s.setUpdatedAt(Instant.now());
    SecuritySetting saved = repo.save(s);

    synchronized (this) {
        cached = saved;
    }
    larkTokenCache.clear();

    return larkSettingsView();
}
```

Note: `nb(...)` is the existing private non-blank helper in this file. `larkTokenCache` is the existing injected field. Do NOT remove the `LarkTokenCache` constructor injection — it is now used here.

- [ ] **Step 4: Create the controller**

`src/main/java/com/cimportal/setting/LarkSettingsAdminController.java`:
```java
package com.cimportal.setting;

import com.cimportal.setting.dto.LarkSettingsUpdateRequest;
import com.cimportal.setting.dto.LarkSettingsView;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

/** PORTAL_ADMIN-gated — path under /api/admin/** is secured by SecurityConfig. */
@RestController
@RequestMapping("/api/admin/lark-settings")
public class LarkSettingsAdminController {

    private final SecuritySettingService service;

    public LarkSettingsAdminController(SecuritySettingService service) {
        this.service = service;
    }

    @GetMapping
    public LarkSettingsView get() {
        return service.larkSettingsView();
    }

    @PutMapping
    public LarkSettingsView update(@Valid @RequestBody LarkSettingsUpdateRequest req) {
        return service.updateLarkSettings(req);
    }
}
```

- [ ] **Step 5: Write the controller test**

`src/test/java/com/cimportal/setting/LarkSettingsControllerTest.java`. Mirror the structure of the existing `SecuritySettingControllerTest` (same base class `OracleIntegrationTest`, same `TestJwts.bearerFor(...)` usage, same `MockMvc` autowiring, same `@BeforeEach seed()` that resets the singleton's `lark_*` columns and calls `service.invalidateCache()`). Read `SecuritySettingControllerTest` first to copy the exact imports, the admin/non-admin bearer helpers, and the seed pattern. Tests:
```java
// 1. admin auth required
@Test
void adminGet_requiresPortalAdmin() throws Exception {
    mvc.perform(get("/api/admin/lark-settings"))
        .andExpect(status().isUnauthorized());
}

// 2. PUT sets fields and masks the secret
@Test
void adminPut_setsFields_withoutEchoingPlaintextSecret() throws Exception {
    String body = mvc.perform(put("/api/admin/lark-settings")
            .header("Authorization", adminBearer())
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"larkBaseUrl":"https://open.feishu.cn","larkAppId":"cli_app123",
                 "larkAppSecret":"super-secret-lark","larkReceiverId":"ops@example.com",
                 "larkReceiverIdType":"email"}
                """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.larkBaseUrl").value("https://open.feishu.cn"))
        .andExpect(jsonPath("$.larkAppId").value("cli_app123"))
        .andExpect(jsonPath("$.larkReceiverId").value("ops@example.com"))
        .andExpect(jsonPath("$.larkReceiverIdType").value("email"))
        .andExpect(jsonPath("$.larkAppSecretConfigured").value(true))
        .andReturn().getResponse().getContentAsString();
    assertThat(body).doesNotContain("super-secret-lark").doesNotContain("larkAppSecret\"");
}

// 3. omitting the secret keeps the existing one
@Test
void adminPut_omittingSecret_keepsExistingSecret() throws Exception {
    mvc.perform(put("/api/admin/lark-settings").header("Authorization", adminBearer())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"larkAppId\":\"cli_app1\",\"larkAppSecret\":\"s1\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.larkAppSecretConfigured").value(true));

    mvc.perform(put("/api/admin/lark-settings").header("Authorization", adminBearer())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"larkAppId\":\"cli_app2\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.larkAppId").value("cli_app2"))
        .andExpect(jsonPath("$.larkAppSecretConfigured").value(true));
}

// 4. blank secret clears it
@Test
void adminPut_blankSecret_clearsSecret() throws Exception {
    mvc.perform(put("/api/admin/lark-settings").header("Authorization", adminBearer())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"larkAppId\":\"cli_app1\",\"larkAppSecret\":\"s1\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.larkAppSecretConfigured").value(true));

    mvc.perform(put("/api/admin/lark-settings").header("Authorization", adminBearer())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"larkAppSecret\":\"\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.larkAppSecretConfigured").value(false));
}

// 5. larkEnabled in public config reflects full config and leaks nothing
@Test
void publicConfig_larkEnabled_reflectsConfig() throws Exception {
    mvc.perform(put("/api/admin/lark-settings").header("Authorization", adminBearer())
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"larkAppId":"cli_x","larkAppSecret":"sec","larkReceiverId":"u@e.com","larkReceiverIdType":"email"}
                """))
        .andExpect(status().isOk());

    String cfg = mvc.perform(get("/api/portal/config"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.larkEnabled").value(true))
        .andReturn().getResponse().getContentAsString();
    assertThat(cfg).doesNotContain("cli_x").doesNotContain("sec").doesNotContain("u@e.com");

    // clear receiver -> larkEnabled false
    mvc.perform(put("/api/admin/lark-settings").header("Authorization", adminBearer())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"larkReceiverId\":\"\"}"))
        .andExpect(status().isOk());
    mvc.perform(get("/api/portal/config"))
        .andExpect(jsonPath("$.larkEnabled").value(false));
}
```
If the existing test uses different helper names (e.g. a `bearerFor("ADMIN1")` instead of `adminBearer()`), match whatever `SecuritySettingControllerTest` actually uses — copy its exact auth + seed scaffolding rather than inventing new helpers.

- [ ] **Step 6: Run the new test (expect compile success + green)**

Run: `mvn -q -Dtest=LarkSettingsControllerTest test`
Expected: PASS (5 tests). If it fails to compile because `adminView()`/`update()` still reference Lark, that's fine for now — those are stripped in Task 2; but since Task 1 only ADDS code, it should compile and pass on its own.

- [ ] **Step 7: Commit**

```bash
git add -A
git commit -m "feat(lark-settings): 专属 /api/admin/lark-settings 端点(GET/PUT,密钥掩码)

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 2: Backend — remove Lark from the security-settings DTOs/service + tests

**Files:**
- Modify: `src/main/java/com/cimportal/setting/dto/AdminSettingView.java`
- Modify: `src/main/java/com/cimportal/setting/dto/SecuritySettingUpdateRequest.java`
- Modify: `src/main/java/com/cimportal/setting/SecuritySettingService.java`
- Modify: `src/test/java/com/cimportal/setting/SecuritySettingControllerTest.java`

- [ ] **Step 1: Remove Lark fields from `AdminSettingView`**

Edit `src/main/java/com/cimportal/setting/dto/AdminSettingView.java` to drop the 5 Lark fields. Result:
```java
package com.cimportal.setting.dto;

import java.time.Instant;

public record AdminSettingView(
    boolean ssoEnabled,
    String issuerUri,
    String clientId,
    String scopes,
    String usernameClaim,
    boolean infoPanelEnabled,
    boolean heroEnabled,
    String dutyApiBaseUrl,
    boolean dutyApiKeyConfigured,
    Instant updatedAt
) { }
```

- [ ] **Step 2: Remove Lark fields from `SecuritySettingUpdateRequest`**

Edit `src/main/java/com/cimportal/setting/dto/SecuritySettingUpdateRequest.java` to drop the 5 Lark fields. Keep the `@AssertTrue isSsoConfigValid()` method unchanged. Result:
```java
package com.cimportal.setting.dto;

import jakarta.validation.constraints.AssertTrue;

public record SecuritySettingUpdateRequest(
    Boolean ssoEnabled,
    String issuerUri,
    String clientId,
    String scopes,
    String usernameClaim,
    String initialPassword,
    Boolean infoPanelEnabled,
    Boolean heroEnabled,
    String dutyApiBaseUrl,
    String dutyApiKey
) {
    @AssertTrue(message = "启用 SSO 时 issuerUri 和 clientId 不能为空")
    public boolean isSsoConfigValid() {
        if (ssoEnabled == null || !ssoEnabled) return true;
        return issuerUri != null && !issuerUri.isBlank()
            && clientId != null && !clientId.isBlank();
    }
}
```

- [ ] **Step 3: Strip Lark from `adminView()` and `update()` in the service**

In `src/main/java/com/cimportal/setting/SecuritySettingService.java`:

(a) In `adminView()`, remove the 5 Lark constructor args so it matches the new `AdminSettingView` record:
```java
public AdminSettingView adminView() {
    SecuritySetting s = get();
    return new AdminSettingView(
        s.isSsoEnabled(),
        s.getSsoIssuerUri(),
        s.getSsoClientId(),
        s.getSsoScopes(),
        s.getSsoUsernameClaim(),
        s.isInfoPanelEnabled(),
        s.isHeroEnabled(),
        s.getDutyApiBaseUrl(),
        s.getDutyApiKey() != null && !s.getDutyApiKey().isBlank(),
        s.getUpdatedAt()
    );
}
```

(b) In `update()`, remove the 5 Lark assignment lines and the now-stale `larkTokenCache.clear();` call (cache invalidation for Lark now lives in `updateLarkSettings()`). Delete exactly these lines:
```java
        if (req.larkBaseUrl() != null) s.setLarkBaseUrl(req.larkBaseUrl().isBlank() ? null : req.larkBaseUrl());
        if (req.larkAppId() != null) s.setLarkAppId(req.larkAppId().isBlank() ? null : req.larkAppId());
        if (req.larkReceiverId() != null) s.setLarkReceiverId(req.larkReceiverId().isBlank() ? null : req.larkReceiverId());
        if (req.larkReceiverIdType() != null) s.setLarkReceiverIdType(req.larkReceiverIdType().isBlank() ? null : req.larkReceiverIdType());
        // null = keep existing secret; blank = clear
        if (req.larkAppSecret() != null) s.setLarkAppSecret(req.larkAppSecret().isBlank() ? null : req.larkAppSecret());
```
And remove the `// drop any cached Lark token in case credentials changed` comment + `larkTokenCache.clear();` line from `update()`.

Keep the `LarkTokenCache larkTokenCache` field + constructor injection — it is still used by `updateLarkSettings()` (Task 1). Keep `publicView()` (incl. its `larkEnabled` derivation) and the `nb()` helper unchanged.

- [ ] **Step 4: Remove the 3 migrated Lark tests from `SecuritySettingControllerTest`**

In `src/test/java/com/cimportal/setting/SecuritySettingControllerTest.java`, delete the three Lark test methods and their section comment (the block beginning at `// ── Lark integration config ──`): `adminPut_lark_setsFields_withoutEchoingPlaintextSecret`, `adminPut_lark_omittingSecret_keepsExistingSecret`, and `publicConfig_larkEnabled_reflectsFullConfig_withoutLeakingFields`. **Keep** the `seed()` method's `s.setLark*(null)` reset lines (they still belong to the shared singleton and keep tests isolated). Keep all SSO / duty / hero / password tests unchanged.

- [ ] **Step 5: Run the affected suites (expect green)**

Run: `mvn -q -Dtest=SecuritySettingControllerTest,LarkSettingsControllerTest,OracleMigrationTest test`
Expected: PASS. `OracleMigrationTest` still asserts 19 (no migration added).

- [ ] **Step 6: Run the full backend suite**

Run: `mvn -q test`
Expected: BUILD SUCCESS, 0 failures. (Confirm nothing else referenced the removed Lark fields on `AdminSettingView`/`SecuritySettingUpdateRequest` — if a stray reference fails to compile, it is a leftover read of those fields; remove/adjust it.)

- [ ] **Step 7: Commit**

```bash
git add -A
git commit -m "refactor(security-settings): 移除飞书字段(迁至 /api/admin/lark-settings)

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 3: Frontend — API layer (add Lark settings types/functions, additive)

**Files:**
- Modify: `src/lib/api/admin.ts`

> This task is **additive only** — it does NOT remove the Lark fields from `SecuritySettings`/`SecuritySettingsInput`. That removal happens in Task 5, atomically with the `SecurityAdminView.vue` cleanup, so every commit keeps the frontend type-clean and buildable.

- [ ] **Step 1: Add the Lark settings types + functions**

In `src/lib/api/admin.ts`, immediately after the `updateSecuritySettings` export (currently around line 77), add:
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

- [ ] **Step 2: Typecheck**

Run: `cd /home/shane/Code/cim-portal/cim-portal-client && npx vue-tsc --noEmit`
Expected: clean (no new errors; this is purely additive — `SecuritySettings` still has its Lark fields for now, so `SecurityAdminView.vue` still compiles).

- [ ] **Step 3: Commit**

```bash
git add src/lib/api/admin.ts
git commit -m "feat(api): LarkSettings 类型与 getLarkSettings/updateLarkSettings

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 4: Frontend — FeishuAdminView page + route + nav + i18n

**Files:**
- Create: `src/features/admin/feishu/FeishuAdminView.vue`
- Create: `src/features/admin/feishu/FeishuAdminView.spec.ts`
- Modify: `src/router/index.ts`
- Modify: `src/features/admin/AdminLayout.vue`
- Modify: `src/lib/i18n/locales/zh.ts`, `src/lib/i18n/locales/en.ts`

- [ ] **Step 1: Add i18n keys (both locales)**

In `src/lib/i18n/locales/zh.ts`: add `feishu: '飞书',` to the `admin.nav` object (after `security: '安全',`). Add a new `admin.feishu` block (place it after the `admin.security` block):
```ts
    feishu: {
      title: '飞书集成',
      baseUrl: '接口地址',
      appId: 'App ID',
      appSecret: 'App Secret',
      appSecretConfigured: '已配置,留空不修改',
      appSecretUnset: '未配置',
      appSecretHint: '仅后端保存,不会下发',
      receiverId: '接收者 ID',
      receiverIdType: '接收者 ID 类型',
    },
```
In `src/lib/i18n/locales/en.ts`: add `feishu: 'Feishu',` to the `admin.nav` object (after `security: ...`). Add the matching `admin.feishu` block:
```ts
    feishu: {
      title: 'Feishu Integration',
      baseUrl: 'API Base URL',
      appId: 'App ID',
      appSecret: 'App Secret',
      appSecretConfigured: 'Configured — leave blank to keep',
      appSecretUnset: 'Not configured',
      appSecretHint: 'Stored on the server only; never sent to the browser',
      receiverId: 'Receiver ID',
      receiverIdType: 'Receiver ID Type',
    },
```
(The old `admin.security.lark` block is deleted in Task 5. Both must move together before running the parity test; run the parity test at Task 5 Step 4, not here.)

- [ ] **Step 2: Create `FeishuAdminView.vue`**

`src/features/admin/feishu/FeishuAdminView.vue`:
```vue
<script setup lang="ts">
import { reactive, ref, onMounted } from 'vue'
import { getLarkSettings, updateLarkSettings, type LarkSettings } from '@/lib/api/admin'
import { useToastStore } from '@/stores/toast'
import { useLocale } from '@/lib/i18n/useLocale'
import AdminPanel from '@/features/admin/AdminPanel.vue'
import Input from '@/lib/ui/Input.vue'
import Button from '@/lib/ui/Button.vue'
import Select from '@/lib/ui/Select.vue'

const { t } = useLocale()
const toast = useToastStore()

const loading = ref(true)
const saving = ref(false)

const form = reactive({
  larkBaseUrl: '',
  larkAppId: '',
  larkAppSecret: '',
  larkReceiverId: '',
  larkReceiverIdType: 'email',
})
const larkAppSecretConfigured = ref(false)
const receiverIdTypeOptions = ['open_id', 'user_id', 'union_id', 'email', 'chat_id'].map((v) => ({ value: v, label: v }))

function populate(s: LarkSettings) {
  form.larkBaseUrl = s.larkBaseUrl ?? ''
  form.larkAppId = s.larkAppId ?? ''
  form.larkAppSecret = ''
  form.larkReceiverId = s.larkReceiverId ?? ''
  form.larkReceiverIdType = s.larkReceiverIdType ?? 'email'
  larkAppSecretConfigured.value = !!s.larkAppSecretConfigured
}

onMounted(async () => {
  try {
    populate(await getLarkSettings())
  } finally {
    loading.value = false
  }
})

async function save() {
  saving.value = true
  try {
    const body = {
      larkBaseUrl: form.larkBaseUrl || undefined,
      larkAppId: form.larkAppId || undefined,
      larkReceiverId: form.larkReceiverId || undefined,
      larkReceiverIdType: form.larkReceiverIdType || undefined,
      ...(form.larkAppSecret ? { larkAppSecret: form.larkAppSecret } : {}),
    }
    populate(await updateLarkSettings(body))
    toast.push({ type: 'success', message: t('common.updated') })
  } catch {
    toast.push({ type: 'error', message: t('common.saveFailed') })
  } finally {
    saving.value = false
  }
}
</script>
<template>
  <AdminPanel :title="t('admin.feishu.title')">
    <div class="p-5 space-y-5 max-w-lg">
      <div v-if="loading" class="text-sm text-ink-2">{{ t('common.loading') }}…</div>
      <template v-else>
        <label class="block">
          <span class="mb-1 block text-xs font-medium text-ink-2">{{ t('admin.feishu.baseUrl') }}</span>
          <Input v-model="form.larkBaseUrl" data-testid="lark-base-url" placeholder="https://open.feishu.cn" />
        </label>

        <label class="block">
          <span class="mb-1 block text-xs font-medium text-ink-2">{{ t('admin.feishu.appId') }}</span>
          <Input v-model="form.larkAppId" data-testid="lark-app-id" />
        </label>

        <label class="block">
          <span class="mb-1 block text-xs font-medium text-ink-2">{{ t('admin.feishu.appSecret') }}</span>
          <Input
            v-model="form.larkAppSecret"
            data-testid="lark-app-secret"
            type="password"
            :placeholder="larkAppSecretConfigured ? t('admin.feishu.appSecretConfigured') : t('admin.feishu.appSecretUnset')"
          />
          <span class="mt-1 block text-xs text-ink-3">{{ t('admin.feishu.appSecretHint') }}</span>
        </label>

        <label class="block">
          <span class="mb-1 block text-xs font-medium text-ink-2">{{ t('admin.feishu.receiverId') }}</span>
          <Input v-model="form.larkReceiverId" data-testid="lark-receiver-id" />
        </label>

        <label class="block">
          <span class="mb-1 block text-xs font-medium text-ink-2">{{ t('admin.feishu.receiverIdType') }}</span>
          <Select v-model="form.larkReceiverIdType" :options="receiverIdTypeOptions" />
        </label>

        <Button :disabled="saving" @click="save">{{ t('admin.security.save') }}</Button>
      </template>
    </div>
  </AdminPanel>
</template>
```
Note: reuse the existing `admin.security.save` key for the button (it is generic "保存 / Save" and is NOT removed). If preferred, a `common.save`/`common.updated` already exist — but matching the prior Security page, `admin.security.save` is correct and stays.

- [ ] **Step 3: Add the route**

In `src/router/index.ts`, add after the `security` child route (line ~19):
```ts
      { path: 'feishu', name: 'admin-feishu', component: () => import('@/features/admin/feishu/FeishuAdminView.vue') },
```

- [ ] **Step 4: Add the nav item**

In `src/features/admin/AdminLayout.vue`:
- Add `Send` to the lucide import: change `import { ChevronLeft, Link2, ListChecks, ShieldCheck, Shield, Megaphone, Phone, Monitor } from 'lucide-vue-next'` to include `Send`.
- Add to the `nav` array, right after the `/admin/security` entry:
```ts
  { to: '/admin/feishu', labelKey: 'admin.nav.feishu', icon: Send, enabled: true },
```

- [ ] **Step 5: Write the page spec**

`src/features/admin/feishu/FeishuAdminView.spec.ts` (mirror `SecurityAdminView.spec.ts`'s msw + mount harness, but against `/api/admin/lark-settings`):
```ts
import { describe, it, expect, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { setActivePinia, createPinia } from 'pinia'
import { http, HttpResponse } from 'msw'
import { server } from '@/test/msw'
import { configureClient } from '@/lib/api/client'
import { i18n } from '@/lib/i18n'
import FeishuAdminView from './FeishuAdminView.vue'

const BASE = 'http://localhost:8080'

describe('FeishuAdminView', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    i18n.global.locale.value = 'zh'
    configureClient({ baseUrl: BASE, getToken: () => 'admin-token', getLocale: () => 'zh', onUnauthorized: () => {} })
  })

  const mockSettings = {
    larkBaseUrl: 'https://open.feishu.cn',
    larkAppId: 'cli_app',
    larkReceiverId: 'oc_chat',
    larkReceiverIdType: 'open_id',
    larkAppSecretConfigured: true,
  }

  function setVal(testid: string, val: string) {
    const el = document.body.querySelector(`[data-testid="${testid}"]`) as HTMLInputElement
    el.value = val
    el.dispatchEvent(new Event('input', { bubbles: true }))
  }

  it('loads and binds lark fields on mount; secret stays empty', async () => {
    server.use(http.get(`${BASE}/api/admin/lark-settings`, () => HttpResponse.json(mockSettings)))
    const w = mount(FeishuAdminView, { global: { plugins: [i18n] }, attachTo: document.body })
    await flushPromises()
    expect((document.body.querySelector('[data-testid="lark-base-url"]') as HTMLInputElement).value).toBe('https://open.feishu.cn')
    expect((document.body.querySelector('[data-testid="lark-app-id"]') as HTMLInputElement).value).toBe('cli_app')
    expect((document.body.querySelector('[data-testid="lark-receiver-id"]') as HTMLInputElement).value).toBe('oc_chat')
    expect((document.body.querySelector('[data-testid="lark-app-secret"]') as HTMLInputElement).value).toBe('')
    w.unmount(); document.body.innerHTML = ''
  })

  it('save sends larkAppSecret only when the input has a value', async () => {
    let capturedBody: Record<string, unknown> = {}
    server.use(
      http.get(`${BASE}/api/admin/lark-settings`, () => HttpResponse.json(mockSettings)),
      http.put(`${BASE}/api/admin/lark-settings`, async ({ request }) => {
        capturedBody = (await request.json()) as Record<string, unknown>
        return HttpResponse.json(mockSettings)
      }),
    )
    const w = mount(FeishuAdminView, { global: { plugins: [i18n] }, attachTo: document.body })
    await flushPromises()
    const saveBtn = () => [...document.body.querySelectorAll('button')].find((b) => /保存/.test(b.textContent || ''))!

    saveBtn().click()
    await flushPromises()
    expect(capturedBody.larkAppSecret).toBeUndefined()
    expect(capturedBody).toMatchObject({
      larkBaseUrl: 'https://open.feishu.cn',
      larkAppId: 'cli_app',
      larkReceiverId: 'oc_chat',
      larkReceiverIdType: 'open_id',
    })

    setVal('lark-app-secret', 'super-secret')
    saveBtn().click()
    await flushPromises()
    expect(capturedBody.larkAppSecret).toBe('super-secret')

    w.unmount(); document.body.innerHTML = ''
  })
})
```

- [ ] **Step 6: Run the new spec**

Run: `cd /home/shane/Code/cim-portal/cim-portal-client && npx vitest run src/features/admin/feishu/FeishuAdminView.spec.ts`
Expected: PASS (2 tests).

- [ ] **Step 7: Commit**

```bash
git add src/features/admin/feishu src/router/index.ts src/features/admin/AdminLayout.vue src/lib/i18n/locales/zh.ts src/lib/i18n/locales/en.ts
git commit -m "feat(feishu): 独立飞书配置页 /admin/feishu + 导航项 + i18n

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 5: Frontend — remove Lark from SecurityAdminView + delete old i18n block

**Files:**
- Modify: `src/lib/api/admin.ts`
- Modify: `src/features/admin/security/SecurityAdminView.vue`
- Modify: `src/features/admin/security/SecurityAdminView.spec.ts`
- Modify: `src/lib/i18n/locales/zh.ts`, `src/lib/i18n/locales/en.ts`

- [ ] **Step 0: Remove Lark fields from `SecuritySettings` and `SecuritySettingsInput`**

In `src/lib/api/admin.ts`, delete these lines from `interface SecuritySettings`:
```ts
  larkBaseUrl?: string | null
  larkAppId?: string | null
  larkReceiverId?: string | null
  larkReceiverIdType?: string | null
  larkAppSecretConfigured?: boolean
```
and these from `interface SecuritySettingsInput`:
```ts
  larkBaseUrl?: string
  larkAppId?: string
  larkAppSecret?: string
  larkReceiverId?: string
  larkReceiverIdType?: string
```
(The `LarkSettings`/`LarkSettingsInput` types + `getLarkSettings`/`updateLarkSettings` added in Task 3 stay.)

- [ ] **Step 1: Remove the Lark section from the template**

In `src/features/admin/security/SecurityAdminView.vue`, delete the entire `<!-- Lark (Feishu) integration -->` block (the `<div class="border-t border-border pt-5">` … `</div>` that contains the `admin.security.lark.*` fields — currently lines ~160–200). Leave the SSO and duty sections and the final `<Button>` intact.

- [ ] **Step 2: Remove Lark from the script**

In the same file's `<script setup>`:
- Delete the 5 Lark fields from `form` (`larkBaseUrl, larkAppId, larkAppSecret, larkReceiverId, larkReceiverIdType`).
- Delete `const larkAppSecretConfigured = ref(false)`.
- Delete `const receiverIdTypeOptions = ...` (only the Lark section used it).
- Delete the 6 Lark lines from `populate()` (`form.larkBaseUrl = ...` through `larkAppSecretConfigured.value = ...`).
- Delete the 4 Lark lines from the `save()` body object (`larkBaseUrl/larkAppId/larkReceiverId/larkReceiverIdType`) and the `...(form.larkAppSecret ? { larkAppSecret: form.larkAppSecret } : {})` spread.
- Remove the now-unused `import Select from '@/lib/ui/Select.vue'`.

After this the file keeps: SSO toggle + issuer/clientId/scopes/usernameClaim/initialPassword + the duty section + save.

- [ ] **Step 3: Delete the old i18n `admin.security.lark` block**

In both `src/lib/i18n/locales/zh.ts` and `src/lib/i18n/locales/en.ts`, delete the entire `lark: { … }` block inside `admin.security`. (The replacement `admin.feishu` block was added in Task 4 Step 1.)

- [ ] **Step 4: Update `SecurityAdminView.spec.ts`**

In `src/features/admin/security/SecurityAdminView.spec.ts`:
- Delete the two Lark test cases: `it('loads and binds lark fields on mount', ...)` and `it('save sends larkAppSecret only when the input has a value', ...)`.
- Remove the Lark fields from `mockSettings` (`larkBaseUrl, larkAppId, larkReceiverId, larkReceiverIdType, larkAppSecretConfigured`).
- Keep the SSO load test, the full-payload/initialPassword test, and the omit-initialPassword test. (Those `toMatchObject` assertions don't reference Lark, so they remain valid.)

- [ ] **Step 5: Run the security spec + i18n parity**

Run: `cd /home/shane/Code/cim-portal/cim-portal-client && npx vitest run src/features/admin/security/SecurityAdminView.spec.ts src/lib/i18n`
Expected: PASS (security spec + i18n parity test; `admin.feishu` keys are identical across zh/en and `admin.security.lark` is gone from both).

- [ ] **Step 6: Full frontend test + build**

Run: `cd /home/shane/Code/cim-portal/cim-portal-client && npm test && npm run build`
Expected: all green; `vue-tsc` clean (no remaining references to the removed `SecuritySettings` Lark fields). If `playwright-core` was pruned and a test needs it: `npm i --no-save playwright-core` (do NOT add to package.json).

- [ ] **Step 7: Commit**

```bash
git add -A
git commit -m "refactor(security): 移除安全页飞书分区(迁至独立飞书页)+ 删除旧 i18n 块

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 6: Live smoke test (no code)

- [ ] **Step 1: Rebuild + restart the backend (dev profile)**

```bash
cd /home/shane/Code/cim-portal/cim-portal-server
mvn -q -DskipTests package
fuser -k 8080/tcp   # do NOT pkill -f portal.jar
nohup java -jar target/portal.jar --spring.profiles.active=dev > /tmp/portal-feishu.log 2>&1 &
```
Wait for `Started CimPortalApplication`; confirm the log shows `now at version v19` (no new migration).

- [ ] **Step 2: Verify the endpoints**

```bash
A_TOK=$(curl -s "http://localhost:8080/dev/token?employeeId=ADMIN1" | python3 -c "import sys,json;print(json.load(sys.stdin)['access_token'])")
# security-settings no longer carries any lark field
curl -s http://localhost:8080/api/admin/security-settings -H "Authorization: Bearer $A_TOK" | python3 -c "import sys,json;d=json.load(sys.stdin);print('lark keys in security-settings:',[k for k in d if 'lark' in k.lower()])"
# expect: []
# lark-settings GET (masked)
curl -s http://localhost:8080/api/admin/lark-settings -H "Authorization: Bearer $A_TOK" | python3 -m json.tool
# configure via the new endpoint and confirm larkEnabled flips
curl -s -X PUT http://localhost:8080/api/admin/lark-settings -H "Authorization: Bearer $A_TOK" -H "Content-Type: application/json" \
  -d '{"larkBaseUrl":"https://open.feishu.cn","larkAppId":"cli_x","larkAppSecret":"sec","larkReceiverId":"u@e.com","larkReceiverIdType":"email"}' \
  | python3 -c "import sys,json;d=json.load(sys.stdin);print('configured=',d['larkAppSecretConfigured']);assert 'sec' not in json.dumps(d),'LEAK';print('no plaintext leak OK')"
curl -s http://localhost:8080/api/portal/config | python3 -c "import sys,json;print('larkEnabled=',json.load(sys.stdin)['larkEnabled'])"
# expect: larkEnabled= True
```

- [ ] **Step 3: Frontend check + cleanup**

With `npm run dev` running: log in as admin → the sidebar shows a **飞书** nav item → page loads/saves (secret masked); the **安全** page no longer shows a Feishu section. Then revoke the test Lark config so `larkEnabled` returns to false:
```bash
A_TOK=$(curl -s "http://localhost:8080/dev/token?employeeId=ADMIN1" | python3 -c "import sys,json;print(json.load(sys.stdin)['access_token'])")
curl -s -X PUT http://localhost:8080/api/admin/lark-settings -H "Authorization: Bearer $A_TOK" -H "Content-Type: application/json" \
  -d '{"larkBaseUrl":"","larkAppId":"","larkAppSecret":"","larkReceiverId":"","larkReceiverIdType":""}' > /dev/null
curl -s http://localhost:8080/api/portal/config | python3 -c "import sys,json;print('larkEnabled=',json.load(sys.stdin)['larkEnabled'])"
# expect: larkEnabled= False
```

---

## Notes for the executor

- Commit on branch `dev` in each repo (project convention; not a throwaway feature branch).
- Backend and frontend are separate git repos — run `git` from within the respective repo root.
- Security invariant across every task: the Lark **app secret is never serialized to any response** — only `larkAppSecretConfigured`. Do not add it to `LarkSettingsView` or `PublicConfig`.
- No DB migration in this plan; `OracleMigrationTest` stays at 19.
