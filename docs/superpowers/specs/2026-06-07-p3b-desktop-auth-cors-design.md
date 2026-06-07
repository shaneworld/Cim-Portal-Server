# P3b 桌面鉴权 + 后端 CORS 设计

**日期:** 2026-06-07
**状态:** 设计已确认,待用户审阅 → 实现计划
**背景:** P3a 已把前端封装为 Tauri 桌面应用(WebView 加载内嵌 dist,API 经 `VITE_API_BASE_URL` 跨域指向后端)。桌面 WebView 源为 `tauri://localhost`(macOS/Linux)/`http://tauri.localhost`(Windows),跨域调用 Spring 后端。本期让既有 **JWT Bearer** 鉴权链路跨域可用:**仅加后端 CORS**,登录机制不变。**后端为主**(`cim-portal-server`,分支 `dev`);前端无代码改动。

## 1. 关键决策(已定)

| 主题 | 选择 |
|---|---|
| 鉴权范围 | **加 CORS 让既有 token 鉴权跨域可用**:沿用 `/dev/token` 登录 + `Authorization: Bearer`,不引入新登录机制。真实 OIDC/SSO(web+桌面)另立后续阶段(现各处皆 stub)。 |
| CORS 配置 | **按 profile 可配置** `app.cors.allowed-origins`;桌面源全环境放行,dev 另加本地 web 源。 |

## 2. 现状(已核对)

- **后端鉴权**:Spring Security OAuth2 Resource Server(JWT),`STATELESS`,CSRF disable。放行 `/actuator/health`、swagger、`/dev/token`;`/api/admin/**` 需 `ROLE_PORTAL_ADMIN`;其余 `authenticated`。**无任何 CORS 配置**(web 同源)。
- `DevTokenController` `@Profile("dev")`:`GET /dev/token?employeeId=` → 本地 RSA 签发 JWT `{access_token, token_type:"Bearer"}`。uat/prod 无此端点(需真实 IdP)。
- **前端**:`useAuthStore` token 存 `localStorage('cimp.token')`;`createDevAuth` `GET {baseUrl}/dev/token?...`;`client.ts` 每请求带 `Accept-Language`(始终)、`Authorization: Bearer`(有 token)、`Content-Type: application/json`(有 body)。所有请求都经 `baseUrl` → 已跨域就绪。OIDC provider 为 stub(`throw 未实现`)。
- `application.yml`:多文档 + `on-profile: dev/uat/prod`;已有 `app.security.mode` 命名空间。
- 后端测试:Testcontainers(MariaDB,`@ActiveProfiles("test")`);控制器测试(EnumAdmin/LinkAdmin/Home/UserAdmin)用 Spring 上下文。**Docker 可用 → 本机可跑 `mvn test`。**

## 3. 组件与文件(后端)

```
src/main/java/com/cimportal/auth/CorsProperties.java   # 新:@ConfigurationProperties(app.cors)
src/main/java/com/cimportal/auth/SecurityConfig.java    # 改:+ CorsConfigurationSource bean + http.cors()
src/main/resources/application.yml                      # 改:app.cors.allowed-origins(base + dev)
src/test/java/com/cimportal/auth/CorsConfigTest.java    # 新:预检 CORS 测试
```
前端:**无代码改动**。`cim-portal-client/src-tauri/README.md` 加一行桌面鉴权边界说明(dev 用 dev-token;uat/prod 待 OIDC)。

## 4. CorsProperties

```java
package com.cimportal.auth;
import org.springframework.boot.context.properties.ConfigurationProperties;
import java.util.List;

@ConfigurationProperties(prefix = "app.cors")
public record CorsProperties(List<String> allowedOrigins) {
    public CorsProperties {
        if (allowedOrigins == null) allowedOrigins = List.of();
    }
}
```
`@EnableConfigurationProperties(CorsProperties.class)` 挂在 SecurityConfig 上。

## 5. SecurityConfig 改动

- 类上加 `@EnableConfigurationProperties(CorsProperties.class)`。
- 过滤链加 `.cors(org.springframework.security.config.Customizer.withDefaults())`(置于 csrf 前后皆可;Spring Security 据此自动放行预检 OPTIONS 并应用下方 bean)。
- 新增 bean:
```java
@Bean
CorsConfigurationSource corsConfigurationSource(CorsProperties props) {
    CorsConfiguration c = new CorsConfiguration();
    c.setAllowedOrigins(props.allowedOrigins());            // 显式源(含 tauri://localhost)
    c.setAllowedMethods(List.of("GET","POST","PUT","DELETE","OPTIONS"));
    c.setAllowedHeaders(List.of("*"));                       // bearer 无 cookie,* 合法
    c.setAllowCredentials(false);
    c.setMaxAge(3600L);
    UrlBasedCorsConfigurationSource src = new UrlBasedCorsConfigurationSource();
    src.registerCorsConfiguration("/**", c);
    return src;
}
```
注:`setAllowedOrigins` 按字符串精确匹配 Origin 头,自定义 scheme `tauri://localhost` 可用(Spring 不校验 scheme);`allowCredentials=false` 时回显具体匹配源。

## 6. application.yml

- **base 文档**(全 profile 继承——桌面源恒定):
```yaml
app:
  cors:
    allowed-origins:
      - tauri://localhost
      - http://tauri.localhost
```
- **dev profile** 段追加本地 web/tauri-dev 源:
```yaml
app:
  cors:
    allowed-origins:
      - http://localhost:5173
      - tauri://localhost
      - http://tauri.localhost
```
（uat/prod 继承 base 的 tauri 源即可;其 web 同源无需 CORS;将来跨域 web 主机可在对应 profile 追加。`test` profile 继承 base → 预检测试可用 tauri 源。)

## 7. 测试

- **`CorsConfigTest`(MockMvc,`@SpringBootTest` + `@AutoConfigureMockMvc`,`@ActiveProfiles("test")`,复用 Testcontainers 基类)**:
  - 允许源预检:`options("/api/portal/home").header("Origin","tauri://localhost").header("Access-Control-Request-Method","GET")` → `status 200` 且 `Access-Control-Allow-Origin: tauri://localhost`。
  - 非允许源:`Origin: https://evil.example` 预检 → 无 `Access-Control-Allow-Origin`(或 403)。
- 既有 23 后端测试保持绿(CORS 不改鉴权语义)。
- **本机实跑校验(非测试)**:重启 dev 后端 `:8080`,`curl -i -X OPTIONS http://localhost:8080/api/portal/home -H 'Origin: tauri://localhost' -H 'Access-Control-Request-Method: GET'` → 见 `Access-Control-Allow-Origin: tauri://localhost`;并 `curl` 一次带该 Origin 的 `/dev/token` 确认放行。

## 8. 鉴权链路(不变,现跨域可用)

桌面:`GET {VITE_API_BASE_URL}/dev/token?employeeId=X` → JWT → `localStorage` → 后续 `Authorization: Bearer`。`/dev/token` 仅 dev profile → **桌面登录仅对 dev 后端可用**;uat/prod 桌面鉴权需真实 OIDC(后续阶段)。CORS 放行后,浏览器对带 `Authorization`/`Content-Type: application/json` 的请求自动发预检,被 §5 配置允许。

## 9. 不在范围内(YAGNI / 后续)

真实 OIDC/SSO(web 与桌面,含系统浏览器 + loopback/深链回调)、prod 桌面鉴权、token 刷新、CSRF(无状态 bearer 不需)、前端登录界面改动、桌面安全存储(替代 localStorage)。
