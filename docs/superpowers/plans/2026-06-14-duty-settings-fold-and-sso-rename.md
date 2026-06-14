# 值班接口配置并入值班页 + 安全页改名 SSO Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Move the Duty API config (`dutyApiBaseUrl` + masked `dutyApiKey`) out of the Security page onto the existing Duty Lines page as a settings section, backed by an isolated `GET/PUT /api/admin/duty-settings` endpoint; rename the Security tab/title to SSO (text only).

**Architecture:** Backend adds two DTOs + two service methods + one controller for `/api/admin/duty-settings`, and removes the duty fields from the security-settings DTOs/service. Frontend adds a `DutySettingsForm.vue` card folded into `DutyLinesAdminView.vue`, removes the duty section from `SecurityAdminView.vue`, and updates i18n (new `admin.dutySettings`, Security→SSO label/title). No DB migration; `DutyLineService` reads config off the cached singleton (refreshed on save) so live duty-API behavior is unchanged.

**Tech Stack:** Spring Boot 3.3 / JDK 21 / Oracle (Testcontainers) backend; Vue 3 + TS + Pinia + vue-router + Vitest + msw frontend.

**Repos:** backend `/home/shane/Code/cim-portal/cim-portal-server` (branch `dev`), frontend `/home/shane/Code/cim-portal/cim-portal-client` (branch `dev`). Separate git repos; commit on `dev` in each.

**Security invariant (every task):** the duty API key is NEVER serialized to any response — only `dutyApiKeyConfigured`. Do not add `dutyApiKey` to `DutySettingsView` or `PublicConfig`.

---

## File Structure

**Backend:** Create `dto/DutySettingsView.java`, `dto/DutySettingsUpdateRequest.java`, `DutySettingsAdminController.java`, `test/.../DutySettingsControllerTest.java`. Modify `SecuritySettingService.java`, `dto/AdminSettingView.java`, `dto/SecuritySettingUpdateRequest.java`, `test/.../SecuritySettingControllerTest.java`.

**Frontend:** Create `features/admin/duty-lines/DutySettingsForm.vue`, `features/admin/duty-lines/DutySettingsForm.spec.ts`. Modify `lib/api/admin.ts`, `features/admin/duty-lines/DutyLinesAdminView.vue`, `features/admin/security/SecurityAdminView.vue`, `lib/i18n/locales/zh.ts`, `lib/i18n/locales/en.ts`.

---

## Task 1: Backend — duty-settings DTOs + service methods + controller + test

**Files:**
- Create: `src/main/java/com/cimportal/setting/dto/DutySettingsView.java`
- Create: `src/main/java/com/cimportal/setting/dto/DutySettingsUpdateRequest.java`
- Create: `src/main/java/com/cimportal/setting/DutySettingsAdminController.java`
- Modify: `src/main/java/com/cimportal/setting/SecuritySettingService.java`
- Test: `src/test/java/com/cimportal/setting/DutySettingsControllerTest.java`

This task is ADD-only on the service (do not touch `update()`/`adminView()` — Task 2 does); it must compile + pass on its own.

- [ ] **Step 1: Create `DutySettingsView.java`**
```java
package com.cimportal.setting.dto;

public record DutySettingsView(
    String dutyApiBaseUrl,
    boolean dutyApiKeyConfigured
) { }
```

- [ ] **Step 2: Create `DutySettingsUpdateRequest.java`**
```java
package com.cimportal.setting.dto;

public record DutySettingsUpdateRequest(
    String dutyApiBaseUrl,
    String dutyApiKey
) { }
```

- [ ] **Step 3: Add two methods to `SecuritySettingService.java`**
Add imports `com.cimportal.setting.dto.DutySettingsView` and `com.cimportal.setting.dto.DutySettingsUpdateRequest`. Add after `adminView()`:
```java
public DutySettingsView dutySettingsView() {
    SecuritySetting s = get();
    return new DutySettingsView(
        s.getDutyApiBaseUrl(),
        nb(s.getDutyApiKey())
    );
}

@Transactional
public DutySettingsView updateDutySettings(DutySettingsUpdateRequest req) {
    SecuritySetting s = repo.findById(SINGLETON_ID)
        .orElseThrow(() -> new IllegalStateException("security_setting row missing"));

    if (req.dutyApiBaseUrl() != null) s.setDutyApiBaseUrl(req.dutyApiBaseUrl().isBlank() ? null : req.dutyApiBaseUrl());
    // null = keep existing key; blank = clear
    if (req.dutyApiKey() != null) s.setDutyApiKey(req.dutyApiKey().isBlank() ? null : req.dutyApiKey());
    s.setUpdatedAt(Instant.now());
    SecuritySetting saved = repo.save(s);

    synchronized (this) {
        cached = saved;
    }

    return dutySettingsView();
}
```
`nb()`, `repo`, `SINGLETON_ID`, `cached`, `get()`, `import java.time.Instant` already exist. There is NO duty token cache, so no `clear()` call (unlike the Lark variant).

- [ ] **Step 4: Create `DutySettingsAdminController.java`**
```java
package com.cimportal.setting;

import com.cimportal.setting.dto.DutySettingsUpdateRequest;
import com.cimportal.setting.dto.DutySettingsView;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

/** PORTAL_ADMIN-gated — path under /api/admin/** is secured by SecurityConfig. */
@RestController
@RequestMapping("/api/admin/duty-settings")
public class DutySettingsAdminController {

    private final SecuritySettingService service;

    public DutySettingsAdminController(SecuritySettingService service) {
        this.service = service;
    }

    @GetMapping
    public DutySettingsView get() {
        return service.dutySettingsView();
    }

    @PutMapping
    public DutySettingsView update(@Valid @RequestBody DutySettingsUpdateRequest req) {
        return service.updateDutySettings(req);
    }
}
```

- [ ] **Step 5: Write `DutySettingsControllerTest.java`**
FIRST read `src/test/java/com/cimportal/setting/SecuritySettingControllerTest.java` and the new `LarkSettingsControllerTest.java` (same dir) to copy the EXACT scaffolding: base class `OracleIntegrationTest`, `@AutoConfigureMockMvc`, `@Autowired MockMvc mvc`, `@Autowired TestJwts jwts` (bearer via `"Bearer " + jwts.bearerFor("ADMIN1")` / `"OP1"`), the `@Autowired SecuritySettingRepository settingRepo` + `@Autowired SecuritySettingService service`, and a `@BeforeEach seed()` that loads the singleton, sets `dutyApiBaseUrl=null`, `dutyApiKey=null`, `updatedAt=null`, saves, then `service.invalidateCache()`. Then the tests against `/api/admin/duty-settings`:
```java
@Test
void adminGet_requiresPortalAdmin() throws Exception {
    mvc.perform(get("/api/admin/duty-settings"))
        .andExpect(status().isUnauthorized());
    mvc.perform(get("/api/admin/duty-settings")
            .header("Authorization", "Bearer " + jwts.bearerFor("OP1")))
        .andExpect(status().isForbidden());
}

@Test
void adminPut_setsBaseUrlAndKey_withoutEchoingPlaintextKey() throws Exception {
    String body = mvc.perform(put("/api/admin/duty-settings")
            .header("Authorization", "Bearer " + jwts.bearerFor("ADMIN1"))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"dutyApiBaseUrl\":\"https://duty.example.com\",\"dutyApiKey\":\"super-secret-key\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.dutyApiBaseUrl").value("https://duty.example.com"))
        .andExpect(jsonPath("$.dutyApiKeyConfigured").value(true))
        .andReturn().getResponse().getContentAsString();
    assertThat(body).doesNotContain("super-secret-key").doesNotContain("dutyApiKey\"");
}

@Test
void adminPut_omittingKey_keepsExistingKey() throws Exception {
    mvc.perform(put("/api/admin/duty-settings").header("Authorization", "Bearer " + jwts.bearerFor("ADMIN1"))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"dutyApiBaseUrl\":\"https://duty.example.com\",\"dutyApiKey\":\"k1\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.dutyApiKeyConfigured").value(true));

    mvc.perform(put("/api/admin/duty-settings").header("Authorization", "Bearer " + jwts.bearerFor("ADMIN1"))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"dutyApiBaseUrl\":\"https://duty2.example.com\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.dutyApiBaseUrl").value("https://duty2.example.com"))
        .andExpect(jsonPath("$.dutyApiKeyConfigured").value(true));
}

@Test
void adminPut_blankKey_clearsKey() throws Exception {
    mvc.perform(put("/api/admin/duty-settings").header("Authorization", "Bearer " + jwts.bearerFor("ADMIN1"))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"dutyApiBaseUrl\":\"https://duty.example.com\",\"dutyApiKey\":\"k1\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.dutyApiKeyConfigured").value(true));

    mvc.perform(put("/api/admin/duty-settings").header("Authorization", "Bearer " + jwts.bearerFor("ADMIN1"))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"dutyApiKey\":\"\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.dutyApiKeyConfigured").value(false));
}

@Test
void publicConfig_doesNotExposeDutyApiFields() throws Exception {
    mvc.perform(put("/api/admin/duty-settings").header("Authorization", "Bearer " + jwts.bearerFor("ADMIN1"))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"dutyApiBaseUrl\":\"https://duty.example.com\",\"dutyApiKey\":\"k1\"}"))
        .andExpect(status().isOk());

    String body = mvc.perform(get("/api/portal/config"))
        .andExpect(status().isOk())
        .andReturn().getResponse().getContentAsString();
    assertThat(body).doesNotContain("dutyApi").doesNotContain("duty.example.com").doesNotContain("k1");
}
```
Use the EXACT field/helper names the sibling tests use (e.g. if the repo field is `settingRepo`, the service is `service`). Match imports (`static ...MockMvcRequestBuilders.*`, `MockMvcResultMatchers.*`, `org.assertj...assertThat`, `MediaType`).

- [ ] **Step 6: Run**
`mvn -q -Dtest=DutySettingsControllerTest test` → 5 green. Then `mvn -q -Dtest=SecuritySettingControllerTest,OracleMigrationTest test` (still pass; OracleMigrationTest=19).

- [ ] **Step 7: Commit**
```bash
git add -A
git commit -m "feat(duty-settings): 专属 /api/admin/duty-settings 端点(GET/PUT,密钥掩码)

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 2: Backend — remove duty from security-settings DTOs/service + tests

**Files:**
- Modify: `src/main/java/com/cimportal/setting/dto/AdminSettingView.java`
- Modify: `src/main/java/com/cimportal/setting/dto/SecuritySettingUpdateRequest.java`
- Modify: `src/main/java/com/cimportal/setting/SecuritySettingService.java`
- Modify: `src/test/java/com/cimportal/setting/SecuritySettingControllerTest.java`

- [ ] **Step 1: Remove duty fields from `AdminSettingView.java`**
Make the record exactly:
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
    Instant updatedAt
) { }
```
(Removed `dutyApiBaseUrl`, `dutyApiKeyConfigured`. Note: Lark fields were already removed in a prior round.)

- [ ] **Step 2: Remove duty fields from `SecuritySettingUpdateRequest.java`**
Make it exactly (keep `@AssertTrue`):
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
    Boolean heroEnabled
) {
    @AssertTrue(message = "启用 SSO 时 issuerUri 和 clientId 不能为空")
    public boolean isSsoConfigValid() {
        if (ssoEnabled == null || !ssoEnabled) return true;
        return issuerUri != null && !issuerUri.isBlank()
            && clientId != null && !clientId.isBlank();
    }
}
```

- [ ] **Step 3: Strip duty from `adminView()` and `update()`**
(a) `adminView()` — remove the 2 duty ctor args so it matches the new record:
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
        s.getUpdatedAt()
    );
}
```
(b) `update()` — delete EXACTLY these two lines (and the `// null = keep existing key; blank = clear` comment above the second):
```java
        if (req.dutyApiBaseUrl() != null) s.setDutyApiBaseUrl(req.dutyApiBaseUrl().isBlank() ? null : req.dutyApiBaseUrl());
        if (req.dutyApiKey() != null) s.setDutyApiKey(req.dutyApiKey().isBlank() ? null : req.dutyApiKey());
```
Keep `update()`'s cache refresh (`synchronized (this) { cached = saved; }`) and `return adminView();`. Do NOT touch `dutySettingsView()`/`updateDutySettings()` (Task 1), `publicView()`, `nb()`, `get()`, or any Lark method.

- [ ] **Step 4: Remove the migrated duty tests from `SecuritySettingControllerTest.java`**
Delete the three duty test methods and their section comment (the block starting `// ── Duty external API config ──`): `adminPut_dutyApi_setsBaseUrlAndKey_withoutEchoingPlaintextKey`, `adminPut_dutyApi_omittingKey_keepsExistingKey`, `publicConfig_doesNotExposeDutyApiFields`. KEEP the `seed()` method's `s.setDutyApiBaseUrl(null)`/`s.setDutyApiKey(null)` reset lines. Keep all SSO/hero/password tests.

- [ ] **Step 5: Compile + run affected suites**
`mvn -q -Dtest=SecuritySettingControllerTest,DutySettingsControllerTest,OracleMigrationTest test` → green. Then grep for stray refs: `grep -rn "dutyApiBaseUrl\|dutyApiKey" src/main/java` — remaining refs must be ONLY in: `SecuritySetting` entity (columns), `SecuritySettingService` (`dutySettingsView`/`updateDutySettings`), `DutySettings*` DTOs, and `DutyLineService` (reads `getDutyApiBaseUrl()`/`getDutyApiKey()`). NONE in `adminView`/`update`/`AdminSettingView`/`SecuritySettingUpdateRequest`.

- [ ] **Step 6: Full backend suite**
`mvn -q test` → BUILD SUCCESS, 0 failures, OracleMigrationTest=19.

- [ ] **Step 7: Commit**
```bash
git add -A
git commit -m "refactor(security-settings): 移除值班 API 字段(迁至 /api/admin/duty-settings)

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 3: Frontend — API layer (add duty-settings types/functions, additive)

**Files:**
- Modify: `src/lib/api/admin.ts`

> ADDITIVE only — does NOT remove duty fields from `SecuritySettings`/`SecuritySettingsInput` (Task 5 does that atomically with the SecurityAdminView cleanup, keeping every commit buildable).

- [ ] **Step 1: Add the duty settings types + functions**
In `src/lib/api/admin.ts`, after the `getLarkSettings`/`updateLarkSettings` exports, add:
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

- [ ] **Step 2: Typecheck**
`cd /home/shane/Code/cim-portal/cim-portal-client && npx vue-tsc --noEmit` → clean (purely additive; `SecuritySettings` still has its duty fields, so `SecurityAdminView.vue` still compiles).

- [ ] **Step 3: Commit**
```bash
git add src/lib/api/admin.ts
git commit -m "feat(api): DutySettings 类型与 getDutySettings/updateDutySettings

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 4: Frontend — DutySettingsForm + fold into Duty Lines page + i18n + SSO rename

**Files:**
- Create: `src/features/admin/duty-lines/DutySettingsForm.vue`
- Create: `src/features/admin/duty-lines/DutySettingsForm.spec.ts`
- Modify: `src/features/admin/duty-lines/DutyLinesAdminView.vue`
- Modify: `src/lib/i18n/locales/zh.ts`, `src/lib/i18n/locales/en.ts`

- [ ] **Step 1: i18n — add `admin.dutySettings`, rename Security→SSO (both locales)**
In `src/lib/i18n/locales/zh.ts`:
- Change `admin.nav.security` value from `'安全'` to `'SSO'`.
- Change `admin.security.title` from `'安全／SSO 配置'` to `'SSO 设置'`.
- Add a new `dutySettings` block inside `admin` (place after the `admin.security` block's closing `},`):
```ts
    dutySettings: {
      title: '接口设置',
      baseUrl: '接口地址',
      apiKey: 'API 密钥',
      apiKeyConfigured: '已配置,留空不修改',
      apiKeyUnset: '未配置',
      apiKeyHint: '用于调用外部值班系统;仅后端保存,不会下发',
    },
```
In `src/lib/i18n/locales/en.ts`:
- Change `admin.nav.security` value to `'SSO'`.
- Change `admin.security.title` to `'SSO Settings'`.
- Add:
```ts
    dutySettings: {
      title: 'API Settings',
      baseUrl: 'API base URL',
      apiKey: 'API key',
      apiKeyConfigured: 'Configured — leave blank to keep',
      apiKeyUnset: 'Not configured',
      apiKeyHint: 'Used to call the external duty system; stored server-side only, never sent to the browser',
    },
```
Do NOT remove the `admin.security.dutyApi*` keys yet (Task 5 does, atomically with the SecurityAdminView template removal). Keep `admin.security.save`. Ensure braces/commas balanced.

- [ ] **Step 2: Create `DutySettingsForm.vue`**
First read `src/lib/ui/GlassCard.vue`, `Input.vue`, `Button.vue`, and the SSO page's duty section (currently in `SecurityAdminView.vue`) + the new `FeishuAdminView.vue` to match conventions exactly. Then:
```vue
<script setup lang="ts">
import { reactive, ref, onMounted } from 'vue'
import { getDutySettings, updateDutySettings, type DutySettings } from '@/lib/api/admin'
import { useToastStore } from '@/stores/toast'
import { useLocale } from '@/lib/i18n/useLocale'
import GlassCard from '@/lib/ui/GlassCard.vue'
import Input from '@/lib/ui/Input.vue'
import Button from '@/lib/ui/Button.vue'

const { t } = useLocale()
const toast = useToastStore()

const loading = ref(true)
const saving = ref(false)

const form = reactive({
  dutyApiBaseUrl: '',
  dutyApiKey: '',
})
const dutyApiKeyConfigured = ref(false)

function populate(s: DutySettings) {
  form.dutyApiBaseUrl = s.dutyApiBaseUrl ?? ''
  form.dutyApiKey = ''
  dutyApiKeyConfigured.value = !!s.dutyApiKeyConfigured
}

onMounted(async () => {
  try {
    populate(await getDutySettings())
  } finally {
    loading.value = false
  }
})

async function save() {
  saving.value = true
  try {
    const body = {
      dutyApiBaseUrl: form.dutyApiBaseUrl || undefined,
      ...(form.dutyApiKey ? { dutyApiKey: form.dutyApiKey } : {}),
    }
    populate(await updateDutySettings(body))
    toast.push({ type: 'success', message: t('common.updated') })
  } catch {
    toast.push({ type: 'error', message: t('common.saveFailed') })
  } finally {
    saving.value = false
  }
}
</script>
<template>
  <GlassCard class="shrink-0 p-5">
    <h2 class="mb-4 text-base font-semibold text-ink-1">{{ t('admin.dutySettings.title') }}</h2>
    <div v-if="loading" class="text-sm text-ink-2">{{ t('common.loading') }}…</div>
    <div v-else class="max-w-lg space-y-4">
      <label class="block">
        <span class="mb-1 block text-xs font-medium text-ink-2">{{ t('admin.dutySettings.baseUrl') }}</span>
        <Input v-model="form.dutyApiBaseUrl" data-testid="duty-api-base-url" placeholder="http://duty-system:port" />
      </label>

      <label class="block">
        <span class="mb-1 block text-xs font-medium text-ink-2">{{ t('admin.dutySettings.apiKey') }}</span>
        <Input
          v-model="form.dutyApiKey"
          data-testid="duty-api-key"
          type="password"
          :placeholder="dutyApiKeyConfigured ? t('admin.dutySettings.apiKeyConfigured') : t('admin.dutySettings.apiKeyUnset')"
        />
        <span class="mt-1 block text-xs text-ink-3">{{ t('admin.dutySettings.apiKeyHint') }}</span>
      </label>

      <Button :disabled="saving" @click="save">{{ t('admin.security.save') }}</Button>
    </div>
  </GlassCard>
</template>
```
Confirm `common.loading`/`common.updated`/`common.saveFailed`/`admin.security.save` exist (they do — used by other admin pages). If `GlassCard` needs different classes to sit above the table, match how other pages stack cards.

- [ ] **Step 3: Fold the form into `DutyLinesAdminView.vue`**
Add `import DutySettingsForm from './DutySettingsForm.vue'` to the script. Change the template root so the form sits above the table panel. Replace the outer `<AdminPanel ...> … </AdminPanel>` wrapping with:
```html
<template>
  <div class="flex h-full flex-col gap-4">
    <DutySettingsForm />
    <AdminPanel class="min-h-0 flex-1" :title="t('admin.dutyLines.title')" v-model:page-size="rowsPerPage">
      <!-- existing #actions / list / #footer content UNCHANGED -->
    </AdminPanel>
  </div>

  <DutyLineFormModal v-model:open="formOpen" :value="editing" @saved="load" />
  <ConfirmDialog
    v-model:open="confirmOpen"
    :title="t('admin.dutyLines.deleteTitle')"
    :message="t('admin.dutyLines.deleteMessage', { label: pending ? pick(pending, 'label') : '' })"
    @confirm="doDelete"
    @cancel="confirmOpen = false"
  />
</template>
```
Keep the `DutyLineFormModal` + `ConfirmDialog` as siblings at the template root (they were already siblings of the AdminPanel). Only the `AdminPanel` gets wrapped in the new flex-col div with `DutySettingsForm` above it, and gains `class="min-h-0 flex-1"`. Do not change any of the table/actions/footer markup or the script's load/pagination/CRUD logic.

- [ ] **Step 4: Write `DutySettingsForm.spec.ts`**
Mirror `FeishuAdminView.spec.ts` / `SecurityAdminView.spec.ts` harness, against `/api/admin/duty-settings`:
```ts
import { describe, it, expect, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { setActivePinia, createPinia } from 'pinia'
import { http, HttpResponse } from 'msw'
import { server } from '@/test/msw'
import { configureClient } from '@/lib/api/client'
import { i18n } from '@/lib/i18n'
import DutySettingsForm from './DutySettingsForm.vue'

const BASE = 'http://localhost:8080'

describe('DutySettingsForm', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    i18n.global.locale.value = 'zh'
    configureClient({ baseUrl: BASE, getToken: () => 'admin-token', getLocale: () => 'zh', onUnauthorized: () => {} })
  })

  const mockSettings = { dutyApiBaseUrl: 'https://duty.example.com', dutyApiKeyConfigured: true }

  function setVal(testid: string, val: string) {
    const el = document.body.querySelector(`[data-testid="${testid}"]`) as HTMLInputElement
    el.value = val
    el.dispatchEvent(new Event('input', { bubbles: true }))
  }

  it('loads and binds duty fields on mount; key stays empty', async () => {
    server.use(http.get(`${BASE}/api/admin/duty-settings`, () => HttpResponse.json(mockSettings)))
    const w = mount(DutySettingsForm, { global: { plugins: [i18n] }, attachTo: document.body })
    await flushPromises()
    expect((document.body.querySelector('[data-testid="duty-api-base-url"]') as HTMLInputElement).value).toBe('https://duty.example.com')
    expect((document.body.querySelector('[data-testid="duty-api-key"]') as HTMLInputElement).value).toBe('')
    w.unmount(); document.body.innerHTML = ''
  })

  it('save sends dutyApiKey only when the input has a value', async () => {
    let capturedBody: Record<string, unknown> = {}
    server.use(
      http.get(`${BASE}/api/admin/duty-settings`, () => HttpResponse.json(mockSettings)),
      http.put(`${BASE}/api/admin/duty-settings`, async ({ request }) => {
        capturedBody = (await request.json()) as Record<string, unknown>
        return HttpResponse.json(mockSettings)
      }),
    )
    const w = mount(DutySettingsForm, { global: { plugins: [i18n] }, attachTo: document.body })
    await flushPromises()
    const saveBtn = () => [...document.body.querySelectorAll('button')].find((b) => /保存/.test(b.textContent || ''))!

    saveBtn().click()
    await flushPromises()
    expect(capturedBody.dutyApiKey).toBeUndefined()
    expect(capturedBody).toMatchObject({ dutyApiBaseUrl: 'https://duty.example.com' })

    setVal('duty-api-key', 'k-secret')
    saveBtn().click()
    await flushPromises()
    expect(capturedBody.dutyApiKey).toBe('k-secret')

    w.unmount(); document.body.innerHTML = ''
  })
})
```
Match the EXISTING spec's import paths exactly (e.g. `@/test/msw`).

- [ ] **Step 5: Run + build**
`npx vitest run src/features/admin/duty-lines/DutySettingsForm.spec.ts src/lib/i18n` → green (form spec + i18n parity). Then `npm run build` → green. If playwright-core pruned + needed: `npm i --no-save playwright-core` (no package.json change).

- [ ] **Step 6: Commit**
```bash
git add src/features/admin/duty-lines src/lib/i18n/locales/zh.ts src/lib/i18n/locales/en.ts
git commit -m "feat(duty): 值班接口配置并入值班页(DutySettingsForm)+ 安全标签改名 SSO + i18n

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 5: Frontend — remove duty from SecurityAdminView + types + old i18n keys

**Files:**
- Modify: `src/lib/api/admin.ts`
- Modify: `src/features/admin/security/SecurityAdminView.vue`
- Modify: `src/lib/i18n/locales/zh.ts`, `src/lib/i18n/locales/en.ts`
- Modify (only if it references duty): `src/features/admin/security/SecurityAdminView.spec.ts`

- [ ] **Step 1: Remove duty fields from `SecuritySettings`/`SecuritySettingsInput` in `admin.ts`**
Delete from `interface SecuritySettings`:
```ts
  dutyApiBaseUrl?: string | null
  dutyApiKeyConfigured?: boolean
```
Delete from `interface SecuritySettingsInput`:
```ts
  dutyApiBaseUrl?: string
  dutyApiKey?: string
```
KEEP `DutySettings`/`DutySettingsInput` + `getDutySettings`/`updateDutySettings` (Task 3).

- [ ] **Step 2: Remove the duty section from `SecurityAdminView.vue`**
- Template: delete the `<!-- Duty system integration -->` block — the `<div class="border-t border-border pt-5">…</div>` whose `<h3>` uses `admin.security.dutyApiSection` (contains the `dutyApiBaseUrl` + `dutyApiKey` fields). Leave the SSO fields + `initialPassword` + the final `<Button>` intact.
- Script: delete from `form` the `dutyApiBaseUrl` and `dutyApiKey` lines; delete `const dutyApiKeyConfigured = ref(false)`; delete the duty lines in `populate()` (`form.dutyApiBaseUrl = ...`, `form.dutyApiKey = ''`, `dutyApiKeyConfigured.value = ...`); delete from `save()`'s body the `dutyApiBaseUrl: form.dutyApiBaseUrl || undefined` line and the `...(form.dutyApiKey ? { dutyApiKey: form.dutyApiKey } : {})` spread.
- After trimming, the SSO page keeps: SSO toggle + issuer/clientId/scopes/usernameClaim/initialPassword + save. Confirm no remaining `duty`/`Select`-from-duty references (Select was already removed in the Lark round; verify nothing dangles).

- [ ] **Step 3: Delete the old `admin.security.dutyApi*` keys (both locales)**
In `src/lib/i18n/locales/zh.ts` and `en.ts`, delete these keys from `admin.security`: `dutyApiSection`, `dutyApiBaseUrl`, `dutyApiKey`, `dutyApiKeyHint`, `dutyApiKeyConfigured`, `dutyApiKeyUnset`. KEEP `admin.security.save` and all SSO keys. The new `admin.dutySettings` block (Task 4) stays.

- [ ] **Step 4: Check `SecurityAdminView.spec.ts`**
Read it. If `mockSettings` contains any `dutyApi*` field or there is a duty-specific test, remove them. (Per the spec it currently does NOT test duty, so this may be a no-op — confirm by reading, don't assume.)

- [ ] **Step 5: Targeted test + typecheck**
`npx vitest run src/features/admin/security/SecurityAdminView.spec.ts src/lib/i18n` → green. `npx vue-tsc --noEmit` → clean. Grep `grep -rn "dutyApiBaseUrl\|dutyApiKeyConfigured\|dutyApiKey\|admin.security.duty" src` — remaining refs only in `DutySettingsForm.vue` + `admin.ts` DutySettings types + `admin.dutySettings` i18n. None in the security path.

- [ ] **Step 6: Full frontend test + build**
`npm test` → all green (incl i18n parity). `npm run build` → green.

- [ ] **Step 7: Commit**
```bash
git add -A
git commit -m "refactor(security): 移除安全页值班分区(迁至值班页)+ 删除旧 i18n 键

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 6: Live smoke test (no code)

- [ ] **Step 1: Rebuild + restart backend (dev profile)**
```bash
cd /home/shane/Code/cim-portal/cim-portal-server
mvn -q -DskipTests package
fuser -k 8080/tcp   # do NOT pkill -f portal.jar
nohup java -jar target/portal.jar --spring.profiles.active=dev > /tmp/portal-duty.log 2>&1 &
```
Wait for `Started CimPortalApplication`; confirm schema at v19 (no new migration).

- [ ] **Step 2: Verify endpoints**
```bash
A_TOK=$(curl -s "http://localhost:8080/dev/token?employeeId=ADMIN1" | python3 -c "import sys,json;print(json.load(sys.stdin)['access_token'])")
OP_TOK=$(curl -s "http://localhost:8080/dev/token?employeeId=OP1" | python3 -c "import sys,json;print(json.load(sys.stdin)['access_token'])")
# security-settings no longer carries duty
curl -s http://localhost:8080/api/admin/security-settings -H "Authorization: Bearer $A_TOK" | python3 -c "import sys,json;d=json.load(sys.stdin);print('duty keys:',[k for k in d if 'duty' in k.lower()])"
# expect: []
# duty-settings GET (masked) + auth
curl -s -o /dev/null -w "anon GET: %{http_code}\n" http://localhost:8080/api/admin/duty-settings
curl -s -o /dev/null -w "OP1 GET:  %{http_code}\n" http://localhost:8080/api/admin/duty-settings -H "Authorization: Bearer $OP_TOK"
curl -s http://localhost:8080/api/admin/duty-settings -H "Authorization: Bearer $A_TOK" | python3 -m json.tool
# PUT then confirm masking + no public leak
curl -s -X PUT http://localhost:8080/api/admin/duty-settings -H "Authorization: Bearer $A_TOK" -H "Content-Type: application/json" \
  -d '{"dutyApiBaseUrl":"https://duty.smoke","dutyApiKey":"smoke-key"}' \
  | python3 -c "import sys,json;d=json.load(sys.stdin);print('base=',d['dutyApiBaseUrl'],'configured=',d['dutyApiKeyConfigured']);assert 'smoke-key' not in json.dumps(d),'LEAK';print('no plaintext leak OK')"
curl -s http://localhost:8080/api/portal/config | python3 -c "import sys,json;d=json.load(sys.stdin);assert 'smoke-key' not in json.dumps(d) and 'duty.smoke' not in json.dumps(d);print('public config clean OK')"
```

- [ ] **Step 3: Frontend check + cleanup**
With `npm run dev`: log in as admin → sidebar tab reads **SSO**; the SSO page has no duty section; the 值班电话 page shows a 接口设置 section on top (key masked) above the personnel list. Then restore duty config to whatever it was (or clear it):
```bash
A_TOK=$(curl -s "http://localhost:8080/dev/token?employeeId=ADMIN1" | python3 -c "import sys,json;print(json.load(sys.stdin)['access_token'])")
curl -s -X PUT http://localhost:8080/api/admin/duty-settings -H "Authorization: Bearer $A_TOK" -H "Content-Type: application/json" \
  -d '{"dutyApiBaseUrl":"","dutyApiKey":""}' > /dev/null
echo "duty config cleared"
```

---

## Notes for the executor

- Commit on branch `dev` in each repo.
- Security invariant: the duty API key is never serialized to any response (only `dutyApiKeyConfigured`).
- No DB migration; `OracleMigrationTest` stays at 19.
- Ordering keeps both repos buildable at every commit (backend: add then remove; frontend: additive types → form+fold+i18n → atomic removal).
