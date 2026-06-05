# CIMS 门户 — API 参考文档

**版本:** v1 · **日期:** 2026-06-05 · **状态:** 设计阶段契约(权威来源)

本文档是 REST API 的权威契约。后端实现后,将通过 **springdoc-openapi** 从代码生成 OpenAPI
规范与 Swagger UI / Redoc 在线文档,二者须与本文保持一致。

---

## 1. 通用约定

### 基址(Base URL)
按环境配置,前端通过 `VITE_API_BASE_URL` 注入。

| 环境 | 示例基址 |
|---|---|
| Dev | `http://localhost:8080` |
| UAT | `https://uat-portal.example.com` |
| Prod | `https://portal.example.com` |

所有路径均以 `/api` 为前缀。

### 认证
所有接口均需认证(除健康检查外)。请求头携带 OIDC 访问令牌:

```
Authorization: Bearer <access_token>
```

- Web:标准 OIDC 重定向获取令牌。
- 桌面端(Tauri):loopback + PKCE 获取令牌。
- 后端作为 **OAuth2 资源服务器**校验令牌签名与有效期,并从中取出 `subject`(= `employee_id`)。
- 部门/角色**不**取自令牌,而是用 `subject` 在 `user_info` 表查得。

### 角色与授权
- 任意已认证用户可访问 `/api/portal/**`、`/api/i18n/**`、`/api/enums/**`(只读)。
- 仅持 `PORTAL_ADMIN` 角色者可访问 `/api/admin/**`。

### 内容类型
请求与响应均为 `application/json; charset=utf-8`。

### 通用查询参数(列表类接口)
| 参数 | 类型 | 说明 |
|---|---|---|
| `lang` | string | 可选,`zh` 或 `en`;若提供,部分接口可返回扁平化的本地化名称(见各接口)。默认两种语言字段都返回。 |

### 错误响应契约
所有错误返回统一结构:

```json
{
  "timestamp": "2026-06-05T08:30:00Z",
  "status": 409,
  "error": "Conflict",
  "code": "DUPLICATE_CODE",
  "message": "枚举值 code 'OPERATOR' 在类别 ROLE 下已存在",
  "path": "/api/admin/enums/ROLE",
  "fieldErrors": [
    { "field": "code", "message": "已存在" }
  ]
}
```

| HTTP 状态 | 含义 | 典型 `code` |
|---|---|---|
| `400 Bad Request` | 请求体校验失败 | `VALIDATION_FAILED` |
| `401 Unauthorized` | 无令牌或令牌无效/过期 | `UNAUTHENTICATED` |
| `403 Forbidden` | 已认证但无权限(非管理员) | `FORBIDDEN` |
| `404 Not Found` | 资源不存在 | `NOT_FOUND` |
| `409 Conflict` | 唯一约束冲突 / 被引用无法删除 | `DUPLICATE_CODE`、`IN_USE` |
| `500 Internal Server Error` | 服务端异常 | `INTERNAL_ERROR` |

`fieldErrors` 仅在 `400` 时出现。

---

## 2. 数据结构(Schema)

### EnumValue
```jsonc
{
  "id": 1001,
  "category": "DEPARTMENT",        // DEPARTMENT | ROLE | LINK_CATEGORY | LINK_STATUS
  "code": "FAB1-PROD",             // 类别内稳定唯一键
  "labelZh": "一厂生产",
  "labelEn": "FAB1 Production",
  "sortOrder": 10,
  "active": true,
  "createdAt": "2026-06-01T02:00:00Z",
  "updatedAt": "2026-06-04T09:12:00Z"
}
```

### Link
```jsonc
{
  "id": 2001,
  "code": "mes-wip",
  "nameZh": "在制品管理",
  "nameEn": "WIP Management",
  "url": "https://mes.example.com/wip",
  "icon": "factory",               // Lucide 图标名
  "categoryCode": "MES",           // → EnumValue(LINK_CATEGORY).code
  "statusCode": "ACTIVE",          // → EnumValue(LINK_STATUS).code
  "sortOrder": 20,
  "openInNewTab": true,
  "grants": [                      // 仅在管理端单条查询时内联返回
    { "id": 5001, "grantType": "ROLE", "grantCode": "PROCESS_ENGINEER" }
  ],
  "createdAt": "2026-06-01T02:00:00Z",
  "updatedAt": "2026-06-04T09:12:00Z"
}
```

### LinkAccessGrant
```jsonc
{
  "id": 5001,
  "linkId": 2001,
  "grantType": "DEPARTMENT",       // DEPARTMENT | ROLE
  "grantCode": "FAB1-PROD"         // → EnumValue.code(对应类别)
}
```
> 某链接无任何 grant 行 = 对所有人可见。

### Label
```jsonc
{
  "id": 3001,
  "labelKey": "portal.title",
  "type": "SYSTEM_NAME",           // SYSTEM_NAME | UI_TEXT | ...
  "textZh": "CIMS 统一门户",
  "textEn": "CIMS Portal",
  "createdAt": "2026-06-01T02:00:00Z",
  "updatedAt": "2026-06-04T09:12:00Z"
}
```

### UserInfo(只读)
```jsonc
{
  "employeeId": "E10086",
  "displayNameZh": "张工",
  "displayNameEn": "Eng. Zhang",
  "departmentCode": "FAB1-ENG",
  "roleCode": "PROCESS_ENGINEER",
  "email": "zhang@example.com",
  "active": true,
  "syncedAt": "2026-06-05T00:00:00Z"
}
```

---

## 3. 门户接口(任意已认证用户)

### 3.1 获取仪表盘 `GET /api/portal/home`
返回**当前用户有权查看**的链接,按类别分组、按 `sortOrder` 排序。服务端完成权限解析,
不会返回不可见链接。

**请求**:无请求体。可选 `?lang=zh`。

**响应 `200`:**
```jsonc
{
  "categories": [
    {
      "categoryCode": "MES",
      "categoryLabelZh": "制造执行",
      "categoryLabelEn": "MES",
      "links": [
        {
          "id": 2001,
          "code": "mes-wip",
          "nameZh": "在制品管理",
          "nameEn": "WIP Management",
          "url": "https://mes.example.com/wip",
          "icon": "factory",
          "statusCode": "ACTIVE",
          "openInNewTab": true
        }
      ]
    }
  ]
}
```
**错误:** `401`;停用用户 → `403`(`code: USER_INACTIVE`);未配置用户(令牌 subject 在
`user_info` 无匹配行)→ `404`(`code: USER_NOT_PROVISIONED`)。该端点经 `CurrentUserService.require()`
做 fail-closed 鉴别。

### 3.2 获取当前用户 `GET /api/portal/me`
合并令牌身份与 `user_info` 查得的部门/角色。

**响应 `200`:**
```jsonc
{
  "employeeId": "E10086",
  "displayNameZh": "张工",
  "displayNameEn": "Eng. Zhang",
  "departmentCode": "FAB1-ENG",
  "roleCode": "PROCESS_ENGINEER",
  "isAdmin": false
}
```
**错误:** `401`;若令牌 subject 在 `user_info` 无匹配行 → `404`(`code: USER_NOT_PROVISIONED`);
若匹配行 `active = false` → `403`(`code: USER_INACTIVE`)。

### 3.3 获取标签字典 `GET /api/i18n/labels`
供 `vue-i18n` 启动时注入。

**响应 `200`:**
```jsonc
{
  "portal.title":   { "zh": "CIMS 统一门户", "en": "CIMS Portal", "type": "SYSTEM_NAME" },
  "nav.dashboard":  { "zh": "仪表盘",        "en": "Dashboard",   "type": "UI_TEXT" }
}
```
**错误:** `401`。

### 3.4 获取某类别枚举值 `GET /api/enums/{category}`
返回该类别下 `active=true` 的枚举值(用于前端展示,如下拉框)。

**路径参数:** `category` ∈ `DEPARTMENT | ROLE | LINK_CATEGORY | LINK_STATUS`。

**响应 `200`:** `EnumValue[]`(按 `sortOrder`)。
**错误:** `400`(类别非法)、`401`。

---

## 4. 管理接口 — 链接(`PORTAL_ADMIN`)

### 4.1 列出链接 `GET /api/admin/links`
**查询参数(可选):** `categoryCode`、`statusCode`、`q`(对 code/名称模糊搜索)。
**响应 `200`:** `Link[]`(不内联 grants)。

### 4.2 查询单条链接 `GET /api/admin/links/{id}`
**响应 `200`:** `Link`(**内联 `grants`**)。 **错误:** `404`。

### 4.3 创建链接 `POST /api/admin/links`
**请求体:**
```jsonc
{
  "code": "spc-cpk",
  "nameZh": "SPC 能力指数",
  "nameEn": "SPC Cpk",
  "url": "https://spc.example.com/cpk",
  "icon": "line-chart",
  "categoryCode": "SPC",
  "statusCode": "ACTIVE",
  "sortOrder": 30,
  "openInNewTab": true
}
```
**校验:** `code` 唯一且非空;`url` 合法;`categoryCode`/`statusCode` 必须存在于对应枚举类别。
**响应 `201`:** 新建的 `Link`。 **错误:** `400`、`409`(`code` 重复)。

### 4.4 更新链接 `PUT /api/admin/links/{id}`
请求体同创建(全量)。**响应 `200`:** 更新后的 `Link`。 **错误:** `400`、`404`、`409`。

### 4.5 删除链接 `DELETE /api/admin/links/{id}`
级联删除其 `link_access_grant`。**响应 `204`。** **错误:** `404`。

---

## 5. 管理接口 — 链接授权 / 白名单(`PORTAL_ADMIN`)

### 5.1 列出某链接的授权 `GET /api/admin/links/{id}/grants`
**响应 `200`:** `LinkAccessGrant[]`。空数组表示对所有人可见。

### 5.2 全量替换某链接的授权 `PUT /api/admin/links/{id}/grants`
以「整组替换」语义设置白名单(便于前端编辑器一次提交)。

**请求体:**
```jsonc
{
  "grants": [
    { "grantType": "DEPARTMENT", "grantCode": "FAB1-PROD" },
    { "grantType": "ROLE",       "grantCode": "PROCESS_ENGINEER" }
  ]
}
```
传 `"grants": []` 即清空 → 该链接对所有人可见。
**校验:** 每个 `grantCode` 必须存在于其 `grantType` 对应的枚举类别且 `active`。
**响应 `200`:** 替换后的 `LinkAccessGrant[]`。 **错误:** `400`、`404`。

### 5.3 新增单条授权 `POST /api/admin/links/{id}/grants`
**请求体:** `{ "grantType": "ROLE", "grantCode": "QA_ENGINEER" }`
**响应 `201`:** `LinkAccessGrant`。 **错误:** `400`、`404`、`409`(重复)。

### 5.4 删除单条授权 `DELETE /api/admin/links/{id}/grants/{grantId}`
**响应 `204`。** **错误:** `404`。

---

## 6. 管理接口 — 枚举(`PORTAL_ADMIN`)

适用于全部四类:`DEPARTMENT | ROLE | LINK_CATEGORY | LINK_STATUS`。

### 6.1 列出 `GET /api/admin/enums/{category}`
返回该类别全部枚举值(含 `active=false`)。**响应 `200`:** `EnumValue[]`。

### 6.2 创建 `POST /api/admin/enums/{category}`
**请求体:**
```jsonc
{ "code": "SHIFT_LEAD", "labelZh": "班组长", "labelEn": "Shift Lead", "sortOrder": 40, "active": true }
```
**校验:** `(category, code)` 唯一;`code` 非空;`labelZh`/`labelEn` 非空。
**响应 `201`:** `EnumValue`。 **错误:** `400`、`409`(`code` 在该类别已存在)。

### 6.3 更新 `PUT /api/admin/enums/{category}/{id}`
可改 `labelZh`、`labelEn`、`sortOrder`、`active`。**`code` 不可变**(被链接/授权/用户引用)。
**响应 `200`:** `EnumValue`。 **错误:** `400`、`404`。

### 6.4 删除 `DELETE /api/admin/enums/{category}/{id}`
**响应 `204`。** 若该枚举值仍被引用(链接的 category/status、授权的 grantCode、用户的
department/role),返回 **`409`(`code: IN_USE`)**,提示改为「停用」(`active=false`)。
建议前端优先用停用而非删除。

---

## 7. 管理接口 — 标签(`PORTAL_ADMIN`)

### 7.1 列出 `GET /api/admin/labels`
**查询参数(可选):** `type`(按类型筛选)、`q`(对 key/文案模糊搜索)。
**响应 `200`:** `Label[]`。

### 7.2 创建 `POST /api/admin/labels`
**请求体:**
```jsonc
{ "labelKey": "nav.admin", "type": "UI_TEXT", "textZh": "管理", "textEn": "Admin" }
```
**校验:** `labelKey` 唯一且非空;`textZh`/`textEn` 非空。
**响应 `201`:** `Label`。 **错误:** `400`、`409`(`labelKey` 重复)。

### 7.3 更新 `PUT /api/admin/labels/{id}`
可改 `type`、`textZh`、`textEn`。`labelKey` 不可变。**响应 `200`:** `Label`。 **错误:** `400`、`404`。

### 7.4 删除 `DELETE /api/admin/labels/{id}`
**响应 `204`。** **错误:** `404`。

---

## 8. 管理接口 — 用户(只读,`PORTAL_ADMIN`)

### 8.1 列出用户 `GET /api/admin/users`
只读查看 `user_info`(Prod 外部同步;Dev/UAT 为模拟种子)。便于管理员排查授权效果。
**查询参数(可选):** `departmentCode`、`roleCode`、`q`(对工号/姓名搜索)。
**响应 `200`:** `UserInfo[]`。
> 无创建/更新/删除接口——门户绝不写入 `user_info`。

---

## 9. 权限解析规则(服务端,`/api/portal/home` 实现依据)

对每条链接 L 与当前用户 U(其 `departmentCode`、`roleCode` 来自 `user_info`):

```
若 L 无任何 grant 行          → 可见
否则,当满足以下任一条件即可见:
  存在 grant(DEPARTMENT, U.departmentCode)
  存在 grant(ROLE,       U.roleCode)
否则                           → 不可见
```
附加约束:
- **停用用户(fail-closed):** `user_info.active = false` 时,`/api/portal/me` 与
  `/api/portal/home` **均**返回 `403`(`code: USER_INACTIVE`)。两个端点都经
  `CurrentUserService.require()` 鉴别,停用/未配置用户在服务层被直接拒绝(未配置 → `404`
  `USER_NOT_PROVISIONED`),而非返回空结果——这与安全评审的 fail-closed 决策一致。
- **链接状态:** `statusCode` 仅用于展示(在卡片上显示状态徽标),**不影响可见性**。所有
  状态的链接(含 `DEPRECATED`)都会进入解析结果,由前端按状态决定徽标样式。

---

## 10. 健康检查(无需认证)

`GET /actuator/health` → `200 { "status": "UP" }`(Spring Boot Actuator)。
