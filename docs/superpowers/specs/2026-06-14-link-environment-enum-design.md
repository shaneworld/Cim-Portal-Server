# 链接「环境(Environment)」纳入枚举管理 设计

> 日期:2026-06-14。范围:把链接的 environment 字段从硬编码(LinkEnv Java 枚举 + 前端 LINK_ENVS 常量)改为由枚举系统管理(新增 LINK_ENV 枚举类别,完全枚举驱动含颜色,镜像公告类型)。后端 Oracle-only;前端 Vue 3 + TS。

## 背景

`environment` 当前:后端 `LinkEnv {DEV,UAT,RELEASE}`(@Enumerated,nullable,无校验),前端 `LINK_ENVS` 常量硬编码徽章颜色(DEV=slate、UAT=amber、RELEASE=emerald),表单为硬编码下拉。管理员无法增删环境。目标:像 ANNOUNCEMENT_TYPE 一样,environment 成为可在「枚举管理」页配置的 `LINK_ENV` 类别(含颜色),链接表单下拉与卡片徽章均由枚举驱动。

## 用户决策(已确认)

- **完全枚举驱动含颜色**(类比 ANNOUNCEMENT_TYPE):管理员在枚举页增删改 LINK_ENV 及其颜色;表单下拉枚举驱动 + 校验;卡片徽章由枚举颜色渲染;新增环境无需改代码。
- **去掉 `LinkEnv` Java 枚举**,environment 存为 String code,按 LINK_ENV 校验(列已是 VARCHAR2(16),现有 DEV/UAT/RELEASE 作为枚举 code 保持有效);environment 仍为可选(可空)。

## 硬约束

- 后端 `mvn test` 绿;`OracleMigrationTest` 17→**18**(V18 seed LINK_ENV)。
- 前端 `npm test`(含 i18n zh/en 对齐)+ `npm run build` 绿。
- environment 可选:为空时不校验、不渲染徽章(沿用 `v-if`)。
- 调色板沿用既有 6 色 `{blue, amber, red, green, purple, slate}`;RELEASE 由 emerald → **green**(palette 内)。
- 徽章文字仍显示**环境 code**(保持现有外观);颜色来自枚举 `envColor`。
- commit trailer:`Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>`;提交前测试须绿。

---

## 后端

### 枚举类别 + 校验
- `com/cimportal/enumvalue/EnumCategory.java`:加 `LINK_ENV`(现有 DEPARTMENT, ROLE, LINK_CATEGORY, LINK_STATUS, ANNOUNCEMENT_TYPE → +LINK_ENV)。
- `EnumValueService.validateColorForCategory`:当前仅 ANNOUNCEMENT_TYPE 校验颜色 ∈ VALID_COLORS。改为 **ANNOUNCEMENT_TYPE 或 LINK_ENV** 都校验颜色 ∈ palette。(`if ((category == ANNOUNCEMENT_TYPE || category == LINK_ENV) && color != null && !VALID_COLORS.contains(color)) throw ...`)

### Link 模型:LinkEnv → String
- 删除 `com/cimportal/link/LinkEnv.java`。
- `Link.java`:`@Enumerated(EnumType.STRING) @Column(name="environment", length=16) private LinkEnv environment;` → `@Column(name="environment", length=16) private String environment;`(去 @Enumerated;getter/setter 改 String)。
- `dto/LinkRequest.java`:`LinkEnv environment` → `String environment`(可空,无 @NotBlank)。
- `dto/LinkResponse.java`:`LinkEnv environment` → `String environment`;**新增** `String envColor, String envLabelZh, String envLabelEn`(在 environment 之后)。
- `portal/dto/HomeLink.java`:`LinkEnv environment` → `String environment`;**新增** `String envColor, String envLabelZh, String envLabelEn`。

### LinkService:校验 + 解析内联
- create/update:environment 非空时按 LINK_ENV 校验(沿用既有 `requireEnum`/`existsByCategoryAndCodeAndActiveTrue` 模式;为空跳过)。`l.setEnvironment(req.environment())` 保持(现为 String)。
- 链接 → LinkResponse / HomeLink 的映射:解析 environment 的颜色/标签(镜像 AnnouncementService.toResponse):
  ```java
  // env 可空
  String envColor = null, envLabelZh = null, envLabelEn = null;
  if (env != null && !env.isBlank()) {
    EnumValue ev = enumRepo.findByCategoryAndCode(EnumCategory.LINK_ENV, env).orElse(null);
    envColor   = ev != null && ev.getColor() != null ? ev.getColor() : "slate";
    envLabelZh = ev != null ? ev.getLabelZh() : env;
    envLabelEn = ev != null ? ev.getLabelEn() : env;
  }
  ```
  注入 `EnumValueRepository`(LinkService 已注入用于 category/status 校验)。HomeService 构造 HomeLink 处同样解析(若 HomeLink 由 HomeService 组装,需在该处解析并注入 enumRepo;复用同一逻辑/小工具方法)。

### 迁移 V18
`V18__link_env_into_enum.sql`:为现有环境 code seed LINK_ENV 枚举行(无表结构变更,只插数据):
```sql
INSERT INTO enum_value (category, code, label_zh, label_en, sort_order, active, color, icon, created_at, updated_at) VALUES
  ('LINK_ENV','DEV','开发环境','DEV',10,1,'slate',NULL,SYSTIMESTAMP,SYSTIMESTAMP);
INSERT INTO enum_value (category, code, label_zh, label_en, sort_order, active, color, icon, created_at, updated_at) VALUES
  ('LINK_ENV','UAT','测试环境','UAT',20,1,'amber',NULL,SYSTIMESTAMP,SYSTIMESTAMP);
INSERT INTO enum_value (category, code, label_zh, label_en, sort_order, active, color, icon, created_at, updated_at) VALUES
  ('LINK_ENV','RELEASE','生产环境','RELEASE',30,1,'green',NULL,SYSTIMESTAMP,SYSTIMESTAMP);
```
(icon 留空 NULL;label_en 用 code 以保持徽章/历史显示;label_zh 给中文名。`OracleMigrationTest` 断言 18。)

---

## 前端

### 枚举管理页 + 表单
- `src/lib/api/enums.ts` / 类型:`EnumCategory` 加 `'LINK_ENV'`。
- `src/features/admin/enums/EnumsAdminView.vue` `CATEGORIES` 数组加 `{ code: 'LINK_ENV', labelKey: 'admin.enums.categories.linkEnv' }`(置于 linkStatus 后或末尾)。
- `src/features/admin/enums/EnumFormModal.vue`:颜色选择器现仅 ANNOUNCEMENT_TYPE 显示;改为 **LINK_ENV 也显示颜色选择器**;但 **图标选择器仍仅 ANNOUNCEMENT_TYPE**(环境徽章用圆点,不用图标)。即把 `isAnnouncementType` 拆为 `showColor`(ANNOUNCEMENT_TYPE || LINK_ENV)与 `showIcon`(ANNOUNCEMENT_TYPE);请求体里 color 在 showColor 时带上,icon 在 showIcon 时带上。
- i18n:`admin.enums.categories.linkEnv`(环境 / Environment)两端对齐。

### 链接表单
- `src/features/admin/links/LinkFormModal.vue`:环境改为枚举驱动下拉。
  - 删除硬编码 `envOpts`(DEV/UAT/RELEASE)。
  - 挂载时 `listEnum('LINK_ENV')`(与已有 category/status 枚举加载并列);options = `[{ value: ENV_NONE, label: t('common.none') }, ...envEnums.filter(active).map(e => ({ value: e.code, label: pick(e,'label') }))]`。保留 `ENV_NONE` → 提交时转 undefined/null(可选)。
  - 绑定/提交逻辑不变(environment 仍是 string code)。

### 徽章渲染:LINK_ENVS → envColorClasses(envColor)
- 新增 `src/lib/ui/envColor.ts`:`envColorClasses(color?: string): { badge: string; dot: string }`,把 6 色 palette 映射为 ring 风格徽章 + 圆点类(与当前 LINK_ENVS 的 badge/dot 风格一致),slate 兜底。例如 green:`{ badge: 'bg-emerald-500/10 text-emerald-600 ring-emerald-500/25 dark:text-emerald-300', dot: 'bg-emerald-500' }`(green→emerald 视觉,沿用现 RELEASE 外观);slate/amber 沿用现有;blue/red/purple 补齐同风格。全量字面类字符串(Tailwind 可扫描)。
- `src/features/dashboard/SystemCard.vue`:`LINK_ENVS[link.environment].badge/.dot` → `envColorClasses(link.envColor).badge/.dot`;徽章文字仍 `{{ link.environment }}`(code)。`v-if="link.environment"` 不变。
- `src/features/admin/links/LinksAdminView.vue`:同样改用 `envColorClasses(l.envColor)`;徽章文字 `l.environment`。
- 删除 `LINK_ENVS` 常量(`src/constants.ts`)及其 import(SystemCard/LinksAdminView 改用 envColor.ts)。
- 类型 `src/lib/api/types.ts` `HomeLink` + `src/lib/api/admin.ts` `AdminLink`:`environment?: 'DEV'|'UAT'|'RELEASE'` → `environment?: string | null`;新增 `envColor?: string | null`、`envLabelZh?: string | null`、`envLabelEn?: string | null`。

---

## 测试策略

- **后端:**
  - `OracleMigrationTest`=18;V18 seed 后 `/api/enums/LINK_ENV` 返回 DEV/UAT/RELEASE(含颜色)。
  - LinkService:环境为未知 code → 400;为 null/空 → 通过;为有效 LINK_ENV code → 通过。LinkResponse/HomeLink 含 envColor + envLabel(有 env 时解析,无 env 时为 null)。
  - EnumValueService:LINK_ENV 颜色 ∈ palette 通过、非法 → 400。
  - 现有 link 测试若断言 `LinkEnv` 类型/枚举,改为 String。
  - `mvn -q test` 绿。
- **前端:**
  - `envColorClasses` 单测:6 色各返回非空 badge+dot;未知/空 → slate 兜底。
  - LinkFormModal:环境下拉来自 `listEnum('LINK_ENV')`(mock)+ None 选项;保存提交 string code。
  - SystemCard/LinksAdminView:徽章用 envColor 渲染(给定 envColor 断言对应 badge class;无 environment 不渲染)。
  - EnumFormModal:LINK_ENV 显示颜色选择器、不显示图标选择器;ANNOUNCEMENT_TYPE 两者都显示。
  - Enums CATEGORIES 含 LINK_ENV;i18n parity(linkEnv 两端)。
  - `npm test` + `npm run build` 绿。

## 自检 / 一致性

- 后端:EnumCategory +LINK_ENV;删 LinkEnv;Link/DTO/HomeLink environment→String + envColor/envLabel 内联解析;LinkService 校验(可选);EnumValueService 颜色校验含 LINK_ENV;V18 seed;OracleMigrationTest=18。
- 前端:EnumCategory +LINK_ENV;CATEGORIES + EnumFormModal(LINK_ENV 颜色、无图标);LinkFormModal 枚举驱动下拉;envColorClasses 替换 LINK_ENVS;SystemCard/LinksAdminView 用 envColor;类型 environment→string + envColor/label;i18n linkEnv。
- 命名一致:`LINK_ENV`(枚举类别)、`envColor`/`envLabelZh`/`envLabelEn`(响应内联)、`envColorClasses`(前端调色)、palette green(RELEASE)。
- environment 可选语义全程保留;徽章文字仍显示 code,颜色由枚举驱动。
