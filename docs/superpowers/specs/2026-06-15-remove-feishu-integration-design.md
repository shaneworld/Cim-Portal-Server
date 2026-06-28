# 移除飞书(Feishu/Lark)集成 设计

> 日期:2026-06-15。范围:完全移除飞书集成及其仅服务于飞书的两项功能——**访问申请**(无权限链接点击→申请)与**意见反馈**。回到飞书引入前的行为:无权限链接点击显示 `noAccessHint` 信息提示;页脚无反馈入口;管理后台无飞书页。删除 `lark_*` 数据库列(V20)。SSO/值班等其它功能不受影响。

## 用户决策(已确认)

- **两项依赖功能整体移除**:访问申请流程与意见反馈仅以飞书为投递渠道,随飞书一并删除;无权限链接点击回退为信息提示(`dashboard.noAccessHint`),复用既有 `blocked` 事件通道。
- **删除数据库列**:新增迁移 **V20** `DROP` `security_setting` 上的 5 个 `lark_*` 列;移除实体字段;`OracleMigrationTest` 19→**20**(前向迁移,投产前安全)。

## 硬约束

- 后端 `mvn test` 绿;`OracleMigrationTest`=**20**。
- 前端 `npm test`(含 i18n zh/en 对齐)+ `npm run build` 绿。
- 每次提交均可编译/构建(后端删除原子化;前端删除原子化)。
- 行为回归:`larkEnabled` 概念彻底消失(`/api/portal/config` 不再含该字段);`/api/portal/access-requests`、`/api/portal/feedback` 端点移除。
- commit trailer:`Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>`。

---

## 后端(cim-portal-server)—— 一次原子删除

### 删除文件
- 整个 `com/cimportal/lark/` 包:`LarkClient`、`LarkTokenCache`、`LarkService`、`LarkController`、`dto/AccessRequestRequest`、`dto/FeedbackRequest`。
- `setting/LarkSettingsAdminController.java`、`setting/dto/LarkSettingsView.java`、`setting/dto/LarkSettingsUpdateRequest.java`。
- 测试:`lark/LarkClientTest`、`lark/LarkControllerTest`、`lark/LarkServiceTest`、`setting/LarkSettingsControllerTest`。

### `SecuritySettingService.java`
- 删除 `larkSettingsView()`、`updateLarkSettings()`。
- 删除 `LarkTokenCache larkTokenCache` 字段与构造器注入参数(及相关 import)。
- `publicView()`:删除 `larkEnabled` 推导,`new PublicConfig(...)` 去掉该实参。
- **保留** `nb()` 助手(仍被 `dutySettingsView()` 的 `dutyApiKeyConfigured = nb(...)` 使用)、`dutySettingsView()`/`updateDutySettings()`、`get()`/`adminView()`/`update()`。

### `setting/dto/PublicConfig.java`
- 删除 `boolean larkEnabled` 字段(record 末位)。

### `setting/SecuritySetting.java`
- 删除 5 个字段 `larkBaseUrl/larkAppId/larkAppSecret/larkReceiverId/larkReceiverIdType` 及其 getter/setter 与 `@Column` 注解。

### 迁移 `V20__remove_lark_integration.sql`(新增)
```sql
ALTER TABLE security_setting DROP (
  lark_base_url, lark_app_id, lark_app_secret, lark_receiver_id, lark_receiver_id_type
);
```

### 测试
- `OracleMigrationTest`:断言 19 → **20**。
- `SecuritySettingControllerTest`:删除 `seed()` 中 `s.setLark*(null)` 等已不存在的 setter 调用;确认无其它 lark 引用。其余 SSO/duty/hero/password 用例不变。
- 全量 `mvn test` 绿。

### 不动
- `CurrentUser`/`CurrentUserService`、`LinkRepository`、`link/`、`dutyline/OnDutyClient`/`OnDutyCache`(值班,与飞书无关)等保持原样。

---

## 前端(cim-portal-client)—— 一次原子删除

### 删除文件/目录
- `src/features/admin/feishu/`(`FeishuAdminView.vue` + spec)。
- `src/features/dashboard/AccessRequestModal.vue` + `AccessRequestModal.spec.ts`。
- `src/features/feedback/`(`FeedbackModal.vue` + spec)。

### 路由与导航
- `src/router/index.ts`:删除 `feishu` 子路由。
- `src/features/admin/AdminLayout.vue`:删除 feishu 导航项;若 `Send` 图标仅此处使用,移除其 import。

### API 层
- `src/lib/api/admin.ts`:删除 `LarkSettings`、`LarkSettingsInput`、`getLarkSettings`、`updateLarkSettings`。
- `src/lib/api/portal.ts`:删除 `requestAccess`、`sendFeedback`;`PortalConfig` 删除 `larkEnabled?`。

### 仪表盘(访问申请回退)
> 注意:既有 `blocked` 事件由 `SystemGrid.onBlocked` 处理——弹「维护/停用」确认框并提供「仍要打开」操作,语义与「无权限」不同(无权限**不应**提供继续打开)。因此锁定点击不复用 `blocked`,而由卡片**直接弹信息提示**。
- `SystemCard.vue`:`defineEmits` 删除 `'access-request'`(保留 `blocked`、`favorite-changed`)。引入 `useToastStore`;`onClick` 锁定分支改为直接提示:`if (locked.value) { e.preventDefault(); toast.push({ type: 'info', message: t('dashboard.noAccessHint') }); return }`(`t` 已由 `useLocale` 提供;`<a>` 的 `title=noAccessHint`、`cursor-not-allowed`、`opacity-60` 保留)。
- `SystemGrid.vue`:`defineEmits` 删除 `'access-request'`;`<SystemCard>` 删除 `@access-request` 透传(保留 `@blocked="onBlocked"`、`@favorite-changed`)。`onBlocked`/确认框(维护/停用)**不变**。
- `HomeView.vue`:删除 `AccessRequestModal` import 与模板挂载、`arOpen`/`arLink`、`onAccessRequest`、`SystemGrid` 上的 `@access-request`;清理因此不再使用的 import(如 `useToastStore`/`config` 若 HomeView 它处未用,由 `vue-tsc` 提示后移除)。
- 净效果:无权限链接点击 → 信息提示「无访问权限」,无弹窗、不可继续打开。

### 页脚反馈
- `src/lib/ui/SupportBar.vue`:删除 `FeedbackModal` import、`fbOpen`、`v-if="config.larkEnabled"` 的反馈按钮与 `<FeedbackModal>` 挂载;若 `config`/`storeToRefs`/`useConfigStore` 不再使用则清理。

### i18n(两端)
- 删除 `admin.nav.feishu`、`admin.feishu` 整块。
- 删除 `dashboard.accessRequest` 整块。**保留** `dashboard.noAccessHint`。
- 删除顶层 `feedback` 整块。
- zh/en 键集保持一致(parity 测试通过)。

### 测试
- 删除上述被删组件的 spec(AccessRequestModal、FeedbackModal、FeishuAdminView)。
- `SystemCard.spec.ts`:若有 `access-request` 断言则改为/删除;锁定点击改断言 `blocked` 触发(若原先有该用例)。
- `SupportBar.spec.ts`:删除依赖 `larkEnabled`/反馈链接可见性的用例。
- i18n parity 测试通过(两端同步删除)。
- `npm test` + `npm run build` 绿;`vue-tsc --noEmit` 干净;grep 确认无 `lark|feishu|accessRequest|sendFeedback|requestAccess|larkEnabled|FeedbackModal|AccessRequestModal` 残留(除变更记录文档外)。

---

## 测试策略 / 联调

- 后端:`mvn -q test` 绿,`OracleMigrationTest`=20;重启 dev → Flyway 应用 V20(`security_setting` 不再有 lark 列);`GET /api/portal/config` 不含 `larkEnabled`;`POST /api/portal/access-requests`、`/api/portal/feedback` → 404;`GET /api/admin/lark-settings` → 404。
- 前端:管理后台无「飞书」导航项/页面;无权限链接点击 → 信息提示;页脚无反馈入口;`/admin/feishu` 不可达。
- DB:V20 之后列已删除;若回滚需谨慎(前向迁移)。

## 自检 / 一致性

- 后端:lark 包与 LarkSettings* 全删;SecuritySettingService 去飞书但保留 duty/SSO;PublicConfig 去 larkEnabled;实体去 5 列;V20 drop;OracleMigrationTest=20;SecuritySettingControllerTest 清理。
- 前端:feishu 页/路由/导航/API/access-request/feedback/i18n 全删;SystemCard 锁定走 `blocked`→noAccessHint;无残留引用。
- 行为:飞书及其两项功能消失;`noAccessHint` 提示保留;SSO/值班不受影响。
- 文档:保留既有部署文档;本设计为移除记录。
