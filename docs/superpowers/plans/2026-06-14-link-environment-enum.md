# 链接环境纳入枚举管理 实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development. 提交前 `mvn -q test` / `npm test` 须绿。commit trailer:`Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>`。

**Goal:** 链接 environment 从硬编码 LinkEnv 改为 LINK_ENV 枚举类别(完全枚举驱动含颜色,镜像 ANNOUNCEMENT_TYPE):后端 String code + 校验 + 内联 envColor/envLabel + V18 seed;前端枚举页 LINK_ENV 标签、表单枚举下拉、徽章由 envColor 渲染。

**Architecture:** 后端删 LinkEnv 枚举,environment→String,按 LINK_ENV 校验,映射时解析 envColor/envLabel(同 AnnouncementService),V18 seed DEV/UAT/RELEASE。前端 EnumCategory +LINK_ENV、枚举页颜色(无图标)、LinkForm 枚举下拉、新 envColorClasses 取代 LINK_ENVS。

**Tech Stack:** Spring Boot 3.3/JDK 21/Oracle/Flyway;Vue 3.5 + TS + Tailwind v3 + vue-i18n。

仓库:后端 `/home/shane/Code/cim-portal/cim-portal-server`,前端 `/home/shane/Code/cim-portal/cim-portal-client`。

---

## 单元 A:后端 —— LINK_ENV 枚举类别 + environment 改 String + 内联 + V18

**Files:**
- Modify: `src/main/java/com/cimportal/enumvalue/EnumCategory.java`
- Modify: `src/main/java/com/cimportal/enumvalue/EnumValueService.java`
- Delete: `src/main/java/com/cimportal/link/LinkEnv.java`
- Modify: `src/main/java/com/cimportal/link/Link.java`
- Modify: `src/main/java/com/cimportal/link/dto/LinkRequest.java`、`dto/LinkResponse.java`
- Modify: `src/main/java/com/cimportal/portal/dto/HomeLink.java`
- Modify: `src/main/java/com/cimportal/link/LinkService.java`
- Modify: `src/main/java/com/cimportal/portal/HomeService.java`(若 HomeLink 在此组装)
- Create: `src/main/resources/db/migration/oracle/V18__link_env_into_enum.sql`
- Modify: `src/test/java/com/cimportal/migration/OracleMigrationTest.java`(17→18)
- Test: link/enum 相关测试(见步骤)

- [ ] **Step 1: V18 迁移 + OracleMigrationTest 18**

`V18__link_env_into_enum.sql`:
```sql
INSERT INTO enum_value (category, code, label_zh, label_en, sort_order, active, color, icon, created_at, updated_at) VALUES
  ('LINK_ENV','DEV','开发环境','DEV',10,1,'slate',NULL,SYSTIMESTAMP,SYSTIMESTAMP);
INSERT INTO enum_value (category, code, label_zh, label_en, sort_order, active, color, icon, created_at, updated_at) VALUES
  ('LINK_ENV','UAT','测试环境','UAT',20,1,'amber',NULL,SYSTIMESTAMP,SYSTIMESTAMP);
INSERT INTO enum_value (category, code, label_zh, label_en, sort_order, active, color, icon, created_at, updated_at) VALUES
  ('LINK_ENV','RELEASE','生产环境','RELEASE',30,1,'green',NULL,SYSTIMESTAMP,SYSTIMESTAMP);
```
`OracleMigrationTest`:断言 17 → 18。
Run: `mvn -q -Dtest=OracleMigrationTest test` → 绿(now at version v18)。

- [ ] **Step 2: EnumCategory + 颜色校验加 LINK_ENV**

`EnumCategory.java`:`{ DEPARTMENT, ROLE, LINK_CATEGORY, LINK_STATUS, ANNOUNCEMENT_TYPE }` → 末尾加 `, LINK_ENV`。
`EnumValueService.validateColorForCategory`:现为
```java
if (category == EnumCategory.ANNOUNCEMENT_TYPE && color != null && !VALID_COLORS.contains(color))
  throw ApiException.badRequest(...);
```
改条件为 `(category == EnumCategory.ANNOUNCEMENT_TYPE || category == EnumCategory.LINK_ENV)`。

- [ ] **Step 3: 删 LinkEnv,environment 改 String(entity + DTOs)**

- 删除 `LinkEnv.java`。
- `Link.java`:把
  ```java
  @Enumerated(EnumType.STRING)
  @Column(name = "environment", length = 16) private LinkEnv environment;
  ```
  改为
  ```java
  @Column(name = "environment", length = 16) private String environment;
  ```
  getter/setter 改 `String`。移除 `import ...LinkEnv;`。
- `dto/LinkRequest.java`:`LinkEnv environment` → `String environment`(可空,无校验注解)。移除 LinkEnv import。
- `dto/LinkResponse.java`:`LinkEnv environment` → `String environment`;并在 environment 之后**新增** `String envColor, String envLabelZh, String envLabelEn`。移除 LinkEnv import。
- `portal/dto/HomeLink.java`:`LinkEnv environment` → `String environment`;新增 `String envColor, String envLabelZh, String envLabelEn`。移除 LinkEnv import。
- grep 确认无其它 LinkEnv 引用:`grep -rn "LinkEnv" src/main src/test`(应只剩将要改的映射处/测试,改完为 0)。

- [ ] **Step 4: LinkService 校验 + 解析 envColor/envLabel**

读 `LinkService.java`(它已注入 `EnumValueRepository` 用于 category/status 校验,方法名如 `requireEnum`)。
- create/update:对 environment 非空时校验:
  ```java
  if (req.environment() != null && !req.environment().isBlank()) requireEnum(EnumCategory.LINK_ENV, req.environment());
  ```
  (沿用既有 requireEnum 形态;为空跳过。)`l.setEnvironment(req.environment())` 不变(现 String)。
- 在构造 `LinkResponse` 的映射方法里,解析 env 颜色/标签并传入新增字段:
  ```java
  String env = l.getEnvironment();
  String envColor = null, envLabelZh = null, envLabelEn = null;
  if (env != null && !env.isBlank()) {
    EnumValue ev = enumRepo.findByCategoryAndCode(EnumCategory.LINK_ENV, env).orElse(null);
    envColor   = ev != null && ev.getColor() != null ? ev.getColor() : "slate";
    envLabelZh = ev != null ? ev.getLabelZh() : env;
    envLabelEn = ev != null ? ev.getLabelEn() : env;
  }
  // new LinkResponse(..., env, envColor, envLabelZh, envLabelEn, ...)
  ```
  (用实际 repo 字段名;`findByCategoryAndCode` 已存在。)

- [ ] **Step 5: HomeService 组装 HomeLink 同样解析**

读 `HomeService.java` 看 HomeLink 如何组装。在其 link→HomeLink 映射处用同样逻辑解析 envColor/envLabelZh/envLabelEn 并传入(HomeService 需注入 `EnumValueRepository`;若已注入则复用)。为避免重复,可在 LinkService 或一个小工具暴露 `resolveEnv(String code)` 返回 `EnumValue`(或一个三元组),HomeService 复用;最简:两处各写同样的 4 行解析(DRY 取舍上可接受,但若 HomeService 已能调用 LinkService 的工具则复用)。实现者择一,确保两条响应路径(admin LinkResponse + portal HomeLink)都带 envColor/envLabel。

- [ ] **Step 6: 后端测试**

- LinkService 测试(沿用既有 link 测试基类/风格):environment="ZZZ"(未知)→ 创建/更新 400;environment=null → 通过;environment="DEV" → 通过且 LinkResponse.envColor=="slate"(DEV seed)、envLabelEn=="DEV"。HomeLink 路径同样含 envColor(若有 home 测试)。
- EnumValueService 测试:LINK_ENV + color="green" 通过;color="teal"(非 palette)→ 400。
- 现有断言 LinkEnv 类型的测试改为 String。
- `OracleMigrationTest`=18。
Run: `mvn -q test` → 全绿。

- [ ] **Step 7: 提交**

```bash
git add -A
git commit -m "feat(link): 环境纳入枚举(LINK_ENV)+ 校验 + 响应内联 envColor/envLabel + V18 seed(删 LinkEnv)

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## 单元 B:前端 —— 枚举页 LINK_ENV + 表单枚举下拉

**Files:**
- Modify: `src/lib/api/enums.ts`(EnumCategory 类型)
- Modify: `src/features/admin/enums/EnumsAdminView.vue`(CATEGORIES)
- Modify: `src/features/admin/enums/EnumFormModal.vue`(showColor/showIcon)
- Modify: `src/features/admin/links/LinkFormModal.vue`(环境枚举下拉)
- Modify: `src/lib/i18n/locales/zh.ts`、`en.ts`(linkEnv)
- Test: 相关 spec

- [ ] **Step 1: EnumCategory 类型 + CATEGORIES + i18n**

- `src/lib/api/enums.ts`:`EnumCategory` 联合类型加 `'LINK_ENV'`。
- `EnumsAdminView.vue` `CATEGORIES`:加 `{ code: 'LINK_ENV', labelKey: 'admin.enums.categories.linkEnv' }`(置于 `linkStatus` 之后)。
- i18n(两端):`admin.enums.categories.linkEnv` → zh `环境` / en `Environment`。

- [ ] **Step 2: EnumFormModal 颜色(LINK_ENV)无图标**

读 `EnumFormModal.vue`。当前 `const isAnnouncementType = computed(() => props.category === 'ANNOUNCEMENT_TYPE')` 控制颜色+图标显示。改为两个计算:
```ts
const showColor = computed(() => props.category === 'ANNOUNCEMENT_TYPE' || props.category === 'LINK_ENV')
const showIcon  = computed(() => props.category === 'ANNOUNCEMENT_TYPE')
```
模板:颜色选择块的 `v-if="isAnnouncementType"` → `v-if="showColor"`;图标选择块 → `v-if="showIcon"`。请求体里:color 在 `showColor.value` 时带上;icon 在 `showIcon.value` 时带上(替换原 isAnnouncementType 判断)。
(LINK_ENV 显示颜色、不显示图标;ANNOUNCEMENT_TYPE 两者都显示。)

- [ ] **Step 3: LinkFormModal 环境枚举下拉**

读 `LinkFormModal.vue`。当前硬编码:
```ts
const ENV_NONE = '__none__'
const envOpts: Opt[] = [ { value: ENV_NONE, label: t('common.none') }, { value:'DEV',...}, {value:'UAT',...}, {value:'RELEASE',...} ]
```
改为枚举驱动:
- 加 `const envEnums = ref<EnumValue[]>([])`;在已有枚举加载处(它已 listEnum category/status — 找到 onMounted/load)加 `envEnums.value = await listEnum('LINK_ENV')`(容错 try/catch)。
- options 计算:`const envOpts = computed<Opt[]>(() => [{ value: ENV_NONE, label: t('common.none') }, ...envEnums.value.filter(e=>e.active).map(e=>({ value: e.code, label: pick(e,'label') }))])`(保留 ENV_NONE → 提交时映射为 undefined,沿用现有 environment 处理)。
- 模板 Select 绑定不变(options 改为 computed envOpts)。提交逻辑不变(environment string code 或 undefined)。
(`EnumValue` 类型、`listEnum`、`pick` 按现有 import 引入。)

- [ ] **Step 4: 测 + build**

Run: `npm test`(EnumFormModal/Links 相关 spec;若 LinkForm spec 断言硬编码 env 选项,改为 mock listEnum('LINK_ENV'))。i18n parity(linkEnv)。
Run: `npm run build`。

- [ ] **Step 5: 提交**

```bash
git add -A
git commit -m "feat(link/enums): 枚举页新增环境(LINK_ENV,含颜色)+ 链接表单环境改枚举驱动下拉

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## 单元 C:前端 —— envColorClasses 取代 LINK_ENVS(徽章渲染)

**Files:**
- Create: `src/lib/ui/envColor.ts`
- Create: `src/lib/ui/envColor.spec.ts`
- Modify: `src/features/dashboard/SystemCard.vue`、`src/features/admin/links/LinksAdminView.vue`
- Modify: `src/lib/api/types.ts`(HomeLink)、`src/lib/api/admin.ts`(AdminLink)
- Modify: `src/constants.ts`(删除 LINK_ENVS)

- [ ] **Step 1: 类型加 envColor/envLabel,environment→string**

- `types.ts` `HomeLink`:`environment?: 'DEV'|'UAT'|'RELEASE'` → `environment?: string | null`;加 `envColor?: string | null; envLabelZh?: string | null; envLabelEn?: string | null`。
- `admin.ts` `AdminLink`:同样 environment→`string | null` + envColor/envLabelZh/envLabelEn 可选。

- [ ] **Step 2: 写 envColorClasses 失败测试**

`src/lib/ui/envColor.spec.ts`:
```ts
import { describe, it, expect } from 'vitest'
import { envColorClasses } from './envColor'
describe('envColorClasses', () => {
  it('returns badge+dot for each palette color', () => {
    for (const c of ['blue','amber','red','green','purple','slate']) {
      const r = envColorClasses(c)
      expect(r.badge).toBeTruthy(); expect(r.dot).toBeTruthy()
    }
  })
  it('falls back to slate for unknown/empty', () => {
    expect(envColorClasses('teal')).toEqual(envColorClasses('slate'))
    expect(envColorClasses(undefined)).toEqual(envColorClasses('slate'))
    expect(envColorClasses(null as any)).toEqual(envColorClasses('slate'))
  })
})
```
Run: `npm test -- envColor` → 红。

- [ ] **Step 3: 实现 envColor.ts**

`src/lib/ui/envColor.ts`(ring 风格徽章 + 圆点,沿用原 LINK_ENVS 视觉:slate/amber 不变,RELEASE 用 emerald 视觉=green;补齐 blue/red/purple 同风格;全量字面类):
```ts
export interface EnvBadge { badge: string; dot: string }
const PALETTE: Record<string, EnvBadge> = {
  slate:  { badge: 'bg-slate-500/10 text-slate-600 ring-slate-500/20 dark:text-slate-300',     dot: 'bg-slate-500 dark:bg-slate-400' },
  amber:  { badge: 'bg-amber-500/10 text-amber-600 ring-amber-500/25 dark:text-amber-300',     dot: 'bg-amber-500' },
  green:  { badge: 'bg-emerald-500/10 text-emerald-600 ring-emerald-500/25 dark:text-emerald-300', dot: 'bg-emerald-500' },
  blue:   { badge: 'bg-blue-500/10 text-blue-600 ring-blue-500/25 dark:text-blue-300',         dot: 'bg-blue-500' },
  red:    { badge: 'bg-red-500/10 text-red-600 ring-red-500/25 dark:text-red-300',             dot: 'bg-red-500' },
  purple: { badge: 'bg-purple-500/10 text-purple-600 ring-purple-500/25 dark:text-purple-300', dot: 'bg-purple-500' },
}
export function envColorClasses(color?: string | null): EnvBadge {
  return PALETTE[color ?? ''] ?? PALETTE.slate
}
```
Run: `npm test -- envColor` → 绿。

- [ ] **Step 4: SystemCard + LinksAdminView 用 envColorClasses**

- `SystemCard.vue`:`import { LINK_ENVS } from '@/constants'` → `import { envColorClasses } from '@/lib/ui/envColor'`。环境徽章:
  ```vue
  <span v-if="link.environment"
    :class="['inline-flex shrink-0 items-center gap-1.5 rounded-full px-2.5 py-1 text-[10px] font-semibold uppercase tracking-wide ring-1 ring-inset', envColorClasses(link.envColor).badge]">
    <span :class="['size-1.5 rounded-full', envColorClasses(link.envColor).dot]"></span>{{ link.environment }}
  </span>
  ```
  (徽章文字仍 `link.environment`(code);颜色来自 link.envColor。)
- `LinksAdminView.vue`:同样把 `LINK_ENVS[l.environment].badge/.dot` → `envColorClasses(l.envColor).badge/.dot`;文字 `l.environment`;import 改为 envColor。

- [ ] **Step 5: 删除 LINK_ENVS 常量**

`src/constants.ts`:删除 `LINK_ENVS` 定义。`grep -rn "LINK_ENVS" src` 确认无残留引用(SystemCard/LinksAdminView 已改)。

- [ ] **Step 6: 测 + build**

Run: `npm test`(envColor spec + 任何断言 LINK_ENVS/env 徽章的 spec;SystemCard spec 若断言旧 env class,改为给 link 设 envColor 并断言新 class 或移除脆弱断言)。
Run: `npm run build`(确认无 LINK_ENVS 悬空 import、类型 environment→string 不破坏)。

- [ ] **Step 7: 提交**

```bash
git add -A
git commit -m "feat(link): 环境徽章改由枚举颜色渲染(envColorClasses 取代 LINK_ENVS 硬编码)

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## 单元 D:联调验证(无代码)

- [ ] **Step 1: 后端**:build + 重启 dev(`fuser -k 8080/tcp`,勿 pkill portal.jar)。日志 "now at version v18"。`GET /api/enums/LINK_ENV`(带 token)返回 DEV/UAT/RELEASE(含 color slate/amber/green)。admin 建链接 environment="DEV" → 200;="ZZZ" → 400。`GET /api/portal/home` 链接含 environment + envColor。
- [ ] **Step 2: 前端**(`npm run dev`):枚举管理页出现「环境」类别,可增改环境 + 颜色(无图标选择器);链接表单「环境」下拉来自枚举(含 None);首页/管理列表环境徽章颜色由枚举驱动;新增一个环境(如 SIT,color blue)→ 选给某链接 → 徽章蓝色渲染(无需改代码)。删除测试链接/环境保持数据干净。

---

## 自检(plan vs spec)

- 后端:EnumCategory +LINK_ENV(A2);EnumValueService 颜色校验含 LINK_ENV(A2);删 LinkEnv + environment→String + DTO/HomeLink + envColor/envLabel(A3);LinkService 校验 + 解析(A4);HomeService 解析(A5);V18 seed(A1);OracleMigrationTest=18(A1)。✅
- 前端:EnumCategory 类型 + CATEGORIES + i18n linkEnv(B1);EnumFormModal showColor(LINK_ENV)/showIcon(B2);LinkForm 枚举下拉(B3);envColorClasses + 徽章渲染 + 删 LINK_ENVS + 类型(C)。✅
- 命名一致:`LINK_ENV`;响应 `envColor`/`envLabelZh`/`envLabelEn`;前端 `envColorClasses`;RELEASE→green(视觉 emerald)。✅
- environment 可选语义保留(校验仅非空时、`v-if` 渲染);徽章文字=code,颜色=envColor。✅
- `OracleMigrationTest`=18;i18n 两端对齐。✅
