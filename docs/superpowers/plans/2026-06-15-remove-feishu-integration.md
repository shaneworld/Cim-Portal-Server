# 移除飞书集成 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Completely remove the Feishu/Lark integration and the two features that exist only to deliver to it (access-request, feedback), reverting to pre-Feishu behavior; drop the `lark_*` DB columns via V20.

**Architecture:** Backend deletes the whole `lark/` package + `LarkSettings*` + the lark parts of `SecuritySettingService`/`PublicConfig`/`SecuritySetting` + a V20 drop migration, in one atomic change. Frontend deletes the Feishu admin page, access-request and feedback features, their API/types/i18n, and reverts the no-access link click to the existing `blocked`→`noAccessHint` info toast — in one atomic change. SSO/duty/announcement functionality is untouched.

**Tech Stack:** Spring Boot 3.3 / JDK 21 / Oracle (Testcontainers) backend; Vue 3 + TS + Pinia + vue-router + Vitest backend/frontend.

**Repos:** backend `/home/shane/Code/cim-portal/cim-portal-server` (branch `dev`), frontend `/home/shane/Code/cim-portal/cim-portal-client` (branch `dev`). Separate git repos; commit on `dev` in each.

---

## Task 1: Backend — remove Feishu/Lark entirely (atomic)

**Files — delete:**
- `src/main/java/com/cimportal/lark/` (LarkClient, LarkTokenCache, LarkService, LarkController, dto/AccessRequestRequest, dto/FeedbackRequest)
- `src/main/java/com/cimportal/setting/LarkSettingsAdminController.java`, `setting/dto/LarkSettingsView.java`, `setting/dto/LarkSettingsUpdateRequest.java`
- `src/test/java/com/cimportal/lark/` (LarkClientTest, LarkControllerTest, LarkServiceTest), `src/test/java/com/cimportal/setting/LarkSettingsControllerTest.java`

**Files — modify:** `setting/SecuritySettingService.java`, `setting/dto/PublicConfig.java`, `setting/SecuritySetting.java`, `test/.../OracleMigrationTest.java`, `test/.../SecuritySettingControllerTest.java`
**Files — create:** `src/main/resources/db/migration/oracle/V20__remove_lark_integration.sql`

- [ ] **Step 1: Delete the lark code + LarkSettings code + their tests**
```bash
cd /home/shane/Code/cim-portal/cim-portal-server
git rm -r src/main/java/com/cimportal/lark src/test/java/com/cimportal/lark
git rm src/main/java/com/cimportal/setting/LarkSettingsAdminController.java \
       src/main/java/com/cimportal/setting/dto/LarkSettingsView.java \
       src/main/java/com/cimportal/setting/dto/LarkSettingsUpdateRequest.java \
       src/test/java/com/cimportal/setting/LarkSettingsControllerTest.java
```

- [ ] **Step 2: Strip Feishu from `SecuritySettingService.java`**
- Remove the `import com.cimportal.lark.LarkTokenCache;` and the `LarkSettingsView`/`LarkSettingsUpdateRequest` imports.
- Remove the `LarkTokenCache larkTokenCache` field and its constructor parameter + assignment (the constructor now takes only the still-used deps — read the file; likely `SecuritySettingRepository` + `PasswordEncoder`).
- Delete the `larkSettingsView()` and `updateLarkSettings()` methods.
- In `publicView()`, remove the `larkEnabled` derivation line and drop the last argument from `new PublicConfig(...)`. The result:
```java
public PublicConfig publicView() {
    SecuritySetting s = get();
    return new PublicConfig(
        s.isSsoEnabled(),
        s.getSsoIssuerUri(),
        s.getSsoClientId(),
        s.getSsoScopes(),
        s.getSsoUsernameClaim(),
        s.isInfoPanelEnabled(),
        s.isHeroEnabled()
    );
}
```
- KEEP `nb()` (still used by `dutySettingsView()`), `dutySettingsView()`, `updateDutySettings()`, `get()`, `adminView()`, `update()`, `invalidateCache()`, etc. unchanged.

- [ ] **Step 3: Remove `larkEnabled` from `PublicConfig.java`**
Delete the `boolean larkEnabled` component (last field). Result record components: `ssoEnabled, issuerUri, clientId, scopes, usernameClaim, infoPanelEnabled, heroEnabled`. (Match the exact existing field names — read the file; e.g. `issuerUri` may be named `authority`/`issuerUri` — keep whatever is there, only remove `larkEnabled`.)

- [ ] **Step 4: Remove the 5 lark fields from `SecuritySetting.java`**
Delete the `@Column lark_base_url/lark_app_id/lark_app_secret/lark_receiver_id/lark_receiver_id_type` field declarations and their getters/setters. Leave all duty/SSO/hero/panel fields intact.

- [ ] **Step 5: Create `V20__remove_lark_integration.sql`**
`src/main/resources/db/migration/oracle/V20__remove_lark_integration.sql`:
```sql
ALTER TABLE security_setting DROP (
  lark_base_url, lark_app_id, lark_app_secret, lark_receiver_id, lark_receiver_id_type
);
```

- [ ] **Step 6: Bump `OracleMigrationTest` to 20**
In `src/test/java/.../OracleMigrationTest.java`, change the expected migration count assertion 19 → 20.

- [ ] **Step 7: Clean `SecuritySettingControllerTest.java`**
Remove any references to the removed lark setters/fields — in particular the `seed()` lines `s.setLarkBaseUrl(null); s.setLarkAppId(null); s.setLarkAppSecret(null); s.setLarkReceiverId(null); s.setLarkReceiverIdType(null);` (these setters no longer exist). Grep the test for `ark` to be sure none remain. Keep all SSO/duty/hero/password tests.

- [ ] **Step 8: Compile + full suite**
Run: `mvn -q test`
Expected: BUILD SUCCESS, 0 failures. `OracleMigrationTest`=20 (Flyway applies V20; `security_setting` loses the 5 columns; `ddl-auto=validate` still passes since the entity no longer maps them). Then `grep -rniE "lark" src/main/java src/test/java` → expect ZERO (only the V19 filename in resources + V20 remain as migration history, which is fine).

- [ ] **Step 9: Commit**
```bash
git add -A
git commit -m "feat(remove): 移除飞书集成后端(lark 包/设置端点/PublicConfig.larkEnabled/实体列)+ V20 删列

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 2: Frontend — remove Feishu admin page, access-request, feedback (atomic)

**Files — delete:** `src/features/admin/feishu/` (FeishuAdminView.vue + spec), `src/features/dashboard/AccessRequestModal.vue` + spec, `src/features/feedback/` (FeedbackModal.vue + spec)
**Files — modify:** `src/router/index.ts`, `src/features/admin/AdminLayout.vue`, `src/lib/api/admin.ts`, `src/lib/api/portal.ts`, `src/features/dashboard/SystemCard.vue` (+ spec), `src/features/dashboard/SystemGrid.vue`, `src/features/dashboard/HomeView.vue`, `src/lib/ui/SupportBar.vue` (+ spec), `src/lib/i18n/locales/zh.ts`, `src/lib/i18n/locales/en.ts`

- [ ] **Step 1: Delete the Feishu/access-request/feedback components + specs**
```bash
cd /home/shane/Code/cim-portal/cim-portal-client
git rm -r src/features/admin/feishu src/features/feedback
git rm src/features/dashboard/AccessRequestModal.vue src/features/dashboard/AccessRequestModal.spec.ts
```

- [ ] **Step 2: Route + nav**
- `src/router/index.ts`: delete the line `{ path: 'feishu', name: 'admin-feishu', component: () => import('@/features/admin/feishu/FeishuAdminView.vue') },`.
- `src/features/admin/AdminLayout.vue`: delete the nav entry `{ to: '/admin/feishu', labelKey: 'admin.nav.feishu', icon: Send, enabled: true },`; remove `Send` from the `lucide-vue-next` import if it is no longer used elsewhere in the file (grep `Send` within the file first).

- [ ] **Step 3: API layer**
- `src/lib/api/admin.ts`: delete `interface LarkSettings`, `interface LarkSettingsInput`, `getLarkSettings`, `updateLarkSettings`.
- `src/lib/api/portal.ts`: delete `requestAccess` and `sendFeedback` exports; remove `larkEnabled?: boolean` from `interface PortalConfig`.

> Wiring note: `blocked` is handled by `SystemGrid.onBlocked`, which opens a maintenance/deprecated **ConfirmDialog with a "proceed and open anyway" action** — wrong semantics for a no-permission link. So do NOT route locked clicks through `blocked`. Instead the card shows the no-access info toast directly.

- [ ] **Step 4: SystemCard — locked click shows the no-access info toast directly**
In `src/features/dashboard/SystemCard.vue`:
- `defineEmits`: remove `'access-request': [HomeLink]`. Result: `defineEmits<{ blocked: [HomeLink]; 'favorite-changed': [{ id: number; favorite: boolean }] }>()`.
- Add `import { useToastStore } from '@/stores/toast'` and `const toast = useToastStore()` in `<script setup>` (`t` is already available via `useLocale`).
- `onClick` locked branch: change `if (locked.value) { e.preventDefault(); emit('access-request', props.link); return }` → `if (locked.value) { e.preventDefault(); toast.push({ type: 'info', message: t('dashboard.noAccessHint') }); return }`.
- Leave the `<a>` `:title="locked ? t('dashboard.noAccessHint') : undefined"`, `cursor-not-allowed`, `opacity-60`, and the `blocked` (non-ACTIVE) + favorite branches unchanged.

- [ ] **Step 5: SystemGrid passthrough**
In `src/features/dashboard/SystemGrid.vue`:
- `defineEmits`: remove `'access-request': [HomeLink]` (keep `favorite-changed`).
- On `<SystemCard>`: remove `@access-request="emit('access-request', $event)"`. Keep `@blocked="onBlocked"` and `@favorite-changed`.
- Leave `onBlocked`, `proceed`, the ConfirmDialog, and the maintenance/deprecated dialog computed UNCHANGED.

- [ ] **Step 6: HomeView — drop the access-request modal/handler**
In `src/features/dashboard/HomeView.vue`:
- Remove `import AccessRequestModal from './AccessRequestModal.vue'` and the `<AccessRequestModal v-model:open="arOpen" :link="arLink" />` from the template.
- Remove `const arOpen = ref(false)`, `const arLink = ref<HomeLink | null>(null)`, and `function onAccessRequest(...)`.
- Remove `@access-request="onAccessRequest"` from `<SystemGrid>` (keep `@favorite-changed`).
- Remove the `import { useToastStore }` + `const toast = useToastStore()` if `toast` is no longer referenced elsewhere in HomeView (it was added only for the access-request fallback — verify with grep). Likewise drop any other import left unused. `npx vue-tsc --noEmit` will flag anything missed.
- Do NOT change `onBlocked` (it lives in SystemGrid, not HomeView).

- [ ] **Step 7: SupportBar — remove feedback link**
In `src/lib/ui/SupportBar.vue`: delete the `import FeedbackModal`, `const fbOpen = ref(false)`, the `<button v-if="config.larkEnabled" @click="fbOpen = true">…{{ t('feedback.link') }}</button>`, and the `<FeedbackModal v-model:open="fbOpen" />`. If `config`/`storeToRefs`/`useConfigStore`/`ref` are no longer used, remove those imports too.

- [ ] **Step 8: i18n (both locales)**
In `src/lib/i18n/locales/zh.ts` and `en.ts`, delete: `admin.nav.feishu`, the `admin.feishu { … }` block, the `dashboard.accessRequest { … }` block, and the top-level `feedback { … }` block. KEEP `dashboard.noAccessHint`. Ensure zh/en key sets stay identical and braces/commas balanced.

- [ ] **Step 9: Specs**
- `SystemCard.spec.ts`: if a test asserts the locked click emits `access-request`, change it to assert `blocked` (the locked branch now emits `blocked`); otherwise no change. Keep favorite tests.
- `SupportBar.spec.ts`: delete any test asserting the feedback link visibility / `larkEnabled` gating. Keep the rest.
- (AccessRequestModal.spec, FeedbackModal.spec, FeishuAdminView.spec were deleted with their components.)

- [ ] **Step 10: Verify**
Run: `npx vitest run` → all green (incl. i18n parity). `npm run build` → green. `npx vue-tsc --noEmit` → clean.
Then grep: `grep -rniE "lark|feishu|accessRequest|access-request|sendFeedback|requestAccess|larkEnabled|FeedbackModal|AccessRequestModal" src` → expect ZERO.
If playwright-core was pruned and needed: `npm i --no-save playwright-core` (no package.json change).

- [ ] **Step 11: Commit**
```bash
git add -A
git commit -m "feat(remove): 移除飞书前端(管理页/路由/导航/API)+ 访问申请与意见反馈功能 + i18n

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 3: Live smoke test (no code)

- [ ] **Step 1: Rebuild + restart backend (dev)**
```bash
cd /home/shane/Code/cim-portal/cim-portal-server
mvn -q -DskipTests package
fuser -k 8080/tcp   # do NOT pkill -f portal.jar
nohup java -jar target/portal.jar --spring.profiles.active=dev > /tmp/portal-rmlark.log 2>&1 &
```
Wait for `Started CimPortalApplication`; confirm the log shows Flyway applying **V20** (now at version v20) and no startup/validate error (entity no longer maps the dropped columns).

- [ ] **Step 2: Verify endpoints gone + config clean**
```bash
A_TOK=$(curl -s "http://localhost:8080/dev/token?employeeId=ADMIN1" | python3 -c "import sys,json;print(json.load(sys.stdin)['access_token'])")
# public config no longer carries larkEnabled
curl -s http://localhost:8080/api/portal/config | python3 -c "import sys,json;d=json.load(sys.stdin);print('larkEnabled present?', 'larkEnabled' in d); print('keys:', list(d))"
# removed endpoints → 404
curl -s -o /dev/null -w "lark-settings GET: %{http_code}\n" http://localhost:8080/api/admin/lark-settings -H "Authorization: Bearer $A_TOK"
curl -s -o /dev/null -w "access-requests POST: %{http_code}\n" -X POST http://localhost:8080/api/portal/access-requests -H "Authorization: Bearer $A_TOK" -H "Content-Type: application/json" -d '{"linkId":1}'
curl -s -o /dev/null -w "feedback POST: %{http_code}\n" -X POST http://localhost:8080/api/portal/feedback -H "Authorization: Bearer $A_TOK" -H "Content-Type: application/json" -d '{"message":"x"}'
# expect: larkEnabled present? False ; all three 404
# confirm columns dropped
docker exec cim-oracle-xe bash -lc 'echo "SET HEADING OFF;\nSELECT count(*) FROM user_tab_columns WHERE table_name=\"SECURITY_SETTING\" AND column_name LIKE \"LARK%\";" | sqlplus -S CIM_PORTAL/cim_portal@//localhost:1521/XEPDB1' 2>&1 | tail -3
# expect: 0
```

- [ ] **Step 3: Frontend check (npm run dev)**
Admin sidebar has no **飞书** item; `/admin/feishu` not reachable; a no-access link card click shows the info toast「无访问权限」(no modal); footer has no feedback link. SSO/duty pages unaffected.

---

## Notes for the executor
- Commit on branch `dev` in each repo. Backend and frontend each land in ONE atomic commit so every commit builds.
- No behavior kept that points at a dead channel; `dashboard.noAccessHint` is the only surviving remnant of the access-request UX (the hover title + click toast).
- V20 is forward-only (drops columns). Safe pre-prod; if already in prod, coordinate with CNO before applying.
