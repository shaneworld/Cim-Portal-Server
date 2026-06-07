# P3b 桌面鉴权 + 后端 CORS 实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 后端加可配置 CORS,使 Tauri 桌面应用(源 `tauri://localhost` / `http://tauri.localhost`)能跨域调用既有 JWT Bearer API 与 `/dev/token` 登录;前端无改动。

**Architecture:** 仅后端:新增 `CorsProperties`(`app.cors.allowed-origins`)+ `CorsConfigurationSource` bean + `SecurityConfig.cors()`;`application.yml` 按 profile 配置允许源(桌面源全环境放行,dev 加本地 web 源)。鉴权语义不变。

**Tech Stack:** Spring Boot 3 + Spring Security(OAuth2 Resource Server,JWT,STATELESS)+ MariaDB/Flyway + Testcontainers/MockMvc。

**契约/现状(已核对):** spec `docs/superpowers/specs/2026-06-07-p3b-desktop-auth-cors-design.md`。`SecurityConfig`(`com.cimportal.auth`)现:csrf disable、STATELESS、放行 `/actuator/health`+swagger+`/dev/token`、`/api/admin/**` 需 `ROLE_PORTAL_ADMIN`、其余 authenticated、`oauth2ResourceServer.jwt`、自定义 entryPoint/accessDeniedHandler;**无 CORS**。`DevTokenController` `@Profile("dev")`。`application.yml` 多文档(`on-profile: dev/uat/prod`),已有 `app.security.mode`。测试 Testcontainers(MariaDB,`@ActiveProfiles("test")`);控制器测试在 `com.cimportal.{enumvalue,link,portal,user}`,基类 `com.cimportal.support.MariaDbIntegrationTest`。**Docker 可用 → 本机 `mvn test` 可跑。既有 23 测试保持绿。** 仓库 `/home/shane/Code/cim-portal/cim-portal-server`,模块 `backend/`,分支 `dev`(本计划直接在 `dev` 上做并提交;不另起分支——与既有后端工作流一致)。门禁:`cd backend && mvn -q test`。提交追加 `Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>`。前端 README 改动在 `cim-portal-client`(分支 `dev`)。

---

## 文件结构

```
backend/src/main/java/com/cimportal/auth/CorsProperties.java   # T1 新
backend/src/main/java/com/cimportal/auth/SecurityConfig.java    # T2 改
backend/src/main/resources/application.yml                      # T2 改
backend/src/test/java/com/cimportal/auth/CorsConfigTest.java    # T3 新
(cim-portal-client) src-tauri/README.md                         # T4 改(边界说明)
```

---

## Task 1: CorsProperties

**Files:** Create `backend/src/main/java/com/cimportal/auth/CorsProperties.java`

- [ ] **Step 1: 创建 `CorsProperties.java`**
```java
package com.cimportal.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "app.cors")
public record CorsProperties(List<String> allowedOrigins) {
    public CorsProperties {
        if (allowedOrigins == null) {
            allowedOrigins = List.of();
        }
    }
}
```

- [ ] **Step 2: 编译校验** — `cd /home/shane/Code/cim-portal/cim-portal-server/backend && mvn -q compile 2>&1 | tail -5` → BUILD SUCCESS(仅新增类,未引用前可编译)。

- [ ] **Step 3: Commit**
```bash
cd /home/shane/Code/cim-portal/cim-portal-server
git add backend/src/main/java/com/cimportal/auth/CorsProperties.java
git commit -m "feat(auth): CorsProperties(app.cors.allowed-origins 配置绑定)

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 2: SecurityConfig 启用 CORS + application.yml 允许源

**Files:** Modify `backend/src/main/java/com/cimportal/auth/SecurityConfig.java`, `backend/src/main/resources/application.yml`

- [ ] **Step 1: 看现状** — Read `SecurityConfig.java`(确认 import 与 `filterChain` 链式调用)与 `application.yml`(确认 base 文档末尾与 `dev` profile 段位置)。

- [ ] **Step 2: 改 `SecurityConfig.java`**
  - 类注解加 `@org.springframework.boot.context.properties.EnableConfigurationProperties(CorsProperties.class)`(置于 `@Configuration` 下一行)。
  - import 增:
    ```java
    import org.springframework.security.config.Customizer;
    import org.springframework.web.cors.CorsConfiguration;
    import org.springframework.web.cors.CorsConfigurationSource;
    import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
    import java.util.List;
    ```
  - `filterChain` 链中,在 `.csrf(AbstractHttpConfigurer::disable)` 之后插入 `.cors(Customizer.withDefaults())`。
  - 新增 bean(类内任意位置):
    ```java
    @Bean
    CorsConfigurationSource corsConfigurationSource(CorsProperties props) {
        CorsConfiguration c = new CorsConfiguration();
        c.setAllowedOrigins(props.allowedOrigins());
        c.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        c.setAllowedHeaders(List.of("*"));
        c.setAllowCredentials(false);
        c.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource src = new UrlBasedCorsConfigurationSource();
        src.registerCorsConfiguration("/**", c);
        return src;
    }
    ```

- [ ] **Step 3: 改 `application.yml`**
  - **base 文档**(`spring.application.name: cim-portal` 同一文档内、根级)加:
    ```yaml
    app:
      cors:
        allowed-origins:
          - tauri://localhost
          - http://tauri.localhost
    ```
    (若 base 已无 `app:` 根键则新增此块;注意 YAML 缩进与现有结构一致。)
  - **dev profile 段**(`on-profile: dev` 的文档,已有 `app.security.mode: dev-jwt`)在其 `app:` 下加 `cors.allowed-origins`,使 dev 为:
    ```yaml
    app:
      security:
        mode: dev-jwt
      cors:
        allowed-origins:
          - http://localhost:5173
          - tauri://localhost
          - http://tauri.localhost
    ```

- [ ] **Step 4: 编译 + 启动期配置校验** — `cd /home/shane/Code/cim-portal/cim-portal-server/backend && mvn -q compile 2>&1 | tail -5` → BUILD SUCCESS。(完整验证在 T3 测试。)

- [ ] **Step 5: Commit**
```bash
cd /home/shane/Code/cim-portal/cim-portal-server
git add backend/src/main/java/com/cimportal/auth/SecurityConfig.java backend/src/main/resources/application.yml
git commit -m "feat(auth): 启用 CORS(http.cors + CorsConfigurationSource)+ app.cors.allowed-origins(base tauri 源 / dev 加 localhost:5173)

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 3: CORS 预检测试

**Files:** Create `backend/src/test/java/com/cimportal/auth/CorsConfigTest.java`

- [ ] **Step 1: 看测试基类** — Read `backend/src/test/java/com/cimportal/support/MariaDbIntegrationTest.java` 与一个现有控制器测试(如 `backend/src/test/java/com/cimportal/portal/HomeIntegrationTest.java`)确认:基类注解(`@SpringBootTest`/Testcontainers/`@ActiveProfiles("test")`)、是否已 `@AutoConfigureMockMvc`、`MockMvc` 注入方式。**按其实际风格**写本测试(下方为典型形态,如基类已提供 MockMvc 则直接 `@Autowired`)。

- [ ] **Step 2: 失败测试 `CorsConfigTest.java`(预检)**
```java
package com.cimportal.auth;

import com.cimportal.support.MariaDbIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class CorsConfigTest extends MariaDbIntegrationTest {

    @Autowired MockMvc mockMvc;

    @Test
    void preflightFromTauriOriginIsAllowed() throws Exception {
        mockMvc.perform(options("/api/portal/home")
                .header("Origin", "tauri://localhost")
                .header("Access-Control-Request-Method", "GET"))
            .andExpect(status().isOk())
            .andExpect(header().string("Access-Control-Allow-Origin", "tauri://localhost"));
    }

    @Test
    void preflightFromDisallowedOriginHasNoAcao() throws Exception {
        mockMvc.perform(options("/api/portal/home")
                .header("Origin", "https://evil.example")
                .header("Access-Control-Request-Method", "GET"))
            .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }
}
```
注:`test` profile 继承 base `app.cors.allowed-origins`(含 `tauri://localhost`)。若基类非 `MariaDbIntegrationTest` 或未用 MockMvc(改用 `WebTestClient`/`TestRestTemplate`),按基类风格等价改写(断言同义:允许源回显 ACAO、非允许源无 ACAO)。

- [ ] **Step 3: 运行确认 FAIL** — `cd /home/shane/Code/cim-portal/cim-portal-server/backend && mvn -q test -Dtest=CorsConfigTest 2>&1 | tail -25`。若 T2 未生效会 FAIL(无 ACAO)。**前置:若 T2 已实现,本测试应直接 PASS**;为遵循 TDD,可临时在 `application.yml` 注释掉 tauri 源确认 FAIL 再恢复——或接受「实现先行、测试锁定」。(实现者择一,关键是测试存在且最终绿。)

- [ ] **Step 4: 跑测试 + 全量** — `mvn -q test -Dtest=CorsConfigTest 2>&1 | tail -15`(2 用例绿)→ 再 `mvn -q test 2>&1 | tail -20`(全量:既有 23 + 本 2 = 25,BUILD SUCCESS)。

- [ ] **Step 5: Commit**
```bash
cd /home/shane/Code/cim-portal/cim-portal-server
git add backend/src/test/java/com/cimportal/auth/CorsConfigTest.java
git commit -m "test(auth): CORS 预检测试(tauri://localhost 放行回显 ACAO;非允许源无 ACAO)

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 4: 前端 README 边界说明

**Files:** Modify `/home/shane/Code/cim-portal/cim-portal-client/src-tauri/README.md`

- [ ] **Step 1: 在 README「范围」段前补一段「鉴权」** — 加:
```markdown
## 鉴权(P3b)

桌面 WebView 源 `tauri://localhost`(Win:`http://tauri.localhost`)跨域调用后端;后端已配置 CORS(`app.cors.allowed-origins`)放行该源。沿用既有 JWT Bearer 流:登录经 `GET {VITE_API_BASE_URL}/dev/token?employeeId=` 取 token 存 `localStorage`,后续 `Authorization: Bearer`。
**`/dev/token` 仅 dev profile** → 桌面登录目前仅对 **dev** 后端可用;uat/prod 桌面鉴权需真实 OIDC(后续阶段)。
```

- [ ] **Step 2: Commit(前端仓库)**
```bash
cd /home/shane/Code/cim-portal/cim-portal-client
git add src-tauri/README.md
git commit -m "docs(desktop): 桌面鉴权(CORS + dev-token)边界说明

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 5: 实跑校验 + 收尾

- [ ] **Step 1: 后端全量门禁** — `cd /home/shane/Code/cim-portal/cim-portal-server/backend && mvn -q test 2>&1 | tail -20` → BUILD SUCCESS(25 测试绿)。
- [ ] **Step 2: 重启 dev 后端 + 实跑 CORS 校验** — 重打包并起 dev(沿用既有方式;**勿 pkill -f portal.jar**,用 `fuser -k 8080/tcp`):
```bash
cd /home/shane/Code/cim-portal/cim-portal-server/backend
mvn -q -DskipTests package 2>&1 | tail -3
fuser -k 8080/tcp 2>/dev/null; sleep 3; rm -f /tmp/be.log
SPRING_PROFILES_ACTIVE=dev nohup java -jar target/portal.jar > /tmp/be.log 2>&1 &
for i in $(seq 1 60); do curl -s -o /dev/null "http://localhost:8080/dev/token?employeeId=ADMIN1" && break; sleep 1; done
echo "--- 预检(tauri 源)---"
curl -s -i -X OPTIONS http://localhost:8080/api/portal/home -H 'Origin: tauri://localhost' -H 'Access-Control-Request-Method: GET' | grep -i 'access-control-allow-origin\|HTTP/'
echo "--- 预检(非允许源,应无 ACAO)---"
curl -s -i -X OPTIONS http://localhost:8080/api/portal/home -H 'Origin: https://evil.example' -H 'Access-Control-Request-Method: GET' | grep -i 'access-control-allow-origin\|HTTP/'
echo "--- dev-token 带 tauri 源(放行)---"
curl -s -i "http://localhost:8080/dev/token?employeeId=ADMIN1" -H 'Origin: tauri://localhost' | grep -i 'access-control-allow-origin\|HTTP/'
```
Expected:tauri 源预检见 `Access-Control-Allow-Origin: tauri://localhost`;evil 源无 ACAO;dev-token 响应带 ACAO=tauri://localhost。
- [ ] **Step 3: 汇报边界** — 明确:后端 CORS 已加并实测放行 tauri 源;桌面登录用 dev-token(dev profile)→ 跨域链路通。**桌面应用本身仍未在本机运行**(缺 webkit2gtk-4.1,P3a 边界不变);uat/prod 桌面鉴权 = 后续 OIDC 阶段。
- [ ] **Step 4: 后端推送(若用户在该 turn 已授权推 dev)** — 默认仅本地提交;如需推:`cd /home/shane/Code/cim-portal/cim-portal-server && git push origin dev`(出站,确认/遵循用户指示)。

---

## 自检清单(Self-Review)

**规格覆盖(spec §3–§8):** CorsProperties → T1;SecurityConfig.cors + bean + yml 允许源(base/dev)→ T2;预检测试(允许/非允许)→ T3;前端 README 边界 → T4;全量门禁 + 实跑 curl 校验 + 边界汇报 → T5。鉴权链路不变(只加 CORS)→ 贯穿。

**占位符扫描:** 无 TBD;代码/yml/命令完整。T3 标注「按测试基类实际风格改写」(MockMvc vs WebTestClient)——因基类细节实现时确认;断言语义已固定(ACAO 回显/缺失)。

**类型/命名一致性:** `app.cors.allowed-origins` ↔ `CorsProperties(prefix="app.cors", allowedOrigins)`;bean `corsConfigurationSource(CorsProperties)`;`http.cors(Customizer.withDefaults())` 取该 bean;允许源 `tauri://localhost`/`http://tauri.localhost`(base)+ `http://localhost:5173`(dev);`test` 继承 base。**硬约束**(23→25 测试绿、鉴权语义不变、勿 pkill portal.jar 用 fuser、CORS 实测)在 T3/T5 复核。
