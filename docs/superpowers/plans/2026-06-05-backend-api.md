# CIMS 门户后端 API 实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 实现 CIMS 门户的 Spring Boot 3 后端 REST API —— 数据模型、Flyway 迁移(MariaDB+Oracle)、枚举/标签/链接/授权的 CRUD、`/api/portal/home` 服务端权限解析、OIDC 资源服务器认证、Dev/UAT 模拟数据,以及 springdoc OpenAPI 在线文档。

**Architecture:** 按功能分包的模块化单体(`com.cimportal` 下 `common/auth/user/enumvalue/label/link/portal`)。控制器→服务→仓储分层;跨模块只经服务调用。JPA + Flyway,迁移脚本按 `mariadb`/`oracle` 拆分;模拟数据用 profile 限定的 Java 种子器(Dev/UAT)。权限解析全在服务端:`/api/portal/home` 只返回当前用户可见的链接。

**Tech Stack:** Java 17、Spring Boot 3.3.5、Spring Data JPA、Spring Security(OAuth2 Resource Server / JWT)、Flyway 10(+flyway-mysql)、MariaDB(dev)/ Oracle(uat,prod)、springdoc-openapi 2.6、JUnit 5 + Testcontainers(mariadb、oracle-free)+ spring-security-test、Maven。

**契约来源:** `docs/api/api-reference.md`(权威 API 契约)与 `docs/superpowers/specs/2026-06-05-cim-portal-design.md`(设计)。

---

## 文件结构

所有路径相对仓库根 `cim-portal-server/`。

```
backend/
├── .mise.toml                         # 锁定 Java 17
├── pom.xml
└── src
    ├── main
    │   ├── java/com/cimportal
    │   │   ├── CimPortalApplication.java
    │   │   ├── common/
    │   │   │   ├── error/ApiError.java
    │   │   │   ├── error/ErrorCode.java
    │   │   │   ├── error/ApiException.java          # NotFound/Conflict/Validation/Forbidden 工厂
    │   │   │   ├── error/GlobalExceptionHandler.java
    │   │   │   └── config/OpenApiConfig.java
    │   │   ├── auth/
    │   │   │   ├── SecurityConfig.java
    │   │   │   ├── UserInfoAuthoritiesConverter.java # Jwt → 含 ROLE_* 的认证
    │   │   │   ├── CurrentUser.java                  # record(employeeId,dept,role,admin)
    │   │   │   ├── CurrentUserService.java
    │   │   │   ├── MeController.java                 # GET /api/portal/me
    │   │   │   ├── MeResponse.java
    │   │   │   └── dev/DevTokenController.java       # @Profile("dev") mock 发令牌
    │   │   ├── user/
    │   │   │   ├── UserInfo.java                     # @Entity
    │   │   │   ├── UserInfoRepository.java
    │   │   │   ├── UserAdminController.java          # GET /api/admin/users
    │   │   │   └── UserInfoResponse.java
    │   │   ├── enumvalue/
    │   │   │   ├── EnumValue.java                    # @Entity
    │   │   │   ├── EnumCategory.java                 # enum
    │   │   │   ├── EnumValueRepository.java
    │   │   │   ├── EnumValueService.java
    │   │   │   ├── EnumController.java               # GET /api/enums/{category}
    │   │   │   ├── EnumAdminController.java          # CRUD /api/admin/enums/{category}
    │   │   │   └── dto/{EnumValueResponse,EnumValueRequest}.java
    │   │   ├── label/
    │   │   │   ├── Label.java                        # @Entity
    │   │   │   ├── LabelRepository.java
    │   │   │   ├── LabelService.java
    │   │   │   ├── LabelI18nController.java          # GET /api/i18n/labels
    │   │   │   ├── LabelAdminController.java         # CRUD /api/admin/labels
    │   │   │   └── dto/{LabelResponse,LabelRequest}.java
    │   │   ├── link/
    │   │   │   ├── Link.java                         # @Entity
    │   │   │   ├── LinkAccessGrant.java              # @Entity
    │   │   │   ├── GrantType.java                    # enum
    │   │   │   ├── LinkRepository.java
    │   │   │   ├── LinkAccessGrantRepository.java
    │   │   │   ├── LinkService.java
    │   │   │   ├── LinkAdminController.java          # CRUD /api/admin/links(+grants)
    │   │   │   └── dto/{LinkResponse,LinkRequest,GrantResponse,GrantRequest,GrantsReplaceRequest}.java
    │   │   ├── portal/
    │   │   │   ├── PermissionResolver.java           # 纯函数:用户×链接 → 可见?
    │   │   │   ├── HomeService.java
    │   │   │   ├── HomeController.java               # GET /api/portal/home
    │   │   │   └── dto/{HomeResponse,HomeCategory,HomeLink}.java
    │   │   └── seed/DevDataSeeder.java               # @Profile({"dev","uat"})
    │   └── resources
    │       ├── application.yml                       # 公共 + profile 片段
    │       └── db/migration
    │           ├── mariadb/V1__schema.sql
    │           └── oracle/V1__schema.sql
    └── test/java/com/cimportal
        ├── support/
        │   ├── MariaDbIntegrationTest.java           # @SpringBootTest + Testcontainers MariaDB 基类
        │   └── TestJwts.java                         # 构造测试 JWT 的工具
        ├── migration/OracleMigrationTest.java        # Testcontainers oracle-free 跑 Flyway
        ├── enumvalue/EnumValueRepositoryTest.java
        ├── enumvalue/EnumAdminControllerTest.java
        ├── label/LabelServiceTest.java
        ├── link/LinkAdminControllerTest.java
        ├── portal/PermissionResolverTest.java
        ├── portal/HomeIntegrationTest.java
        └── auth/SecurityIntegrationTest.java
```

---

## Task 1: 安装并锁定 JDK 17 与 Docker

**Files:**
- Create: `backend/.mise.toml`

- [ ] **Step 1: 安装 JDK 17(mise)**

Run:
```bash
mise use -g java@temurin-17
java -version
```
Expected: `openjdk version "17.x.x"`(若 `mise` 未生效,执行 `eval "$(mise activate zsh)"` 后重试)。

- [ ] **Step 2: 在 backend 目录锁定版本**

Create `backend/.mise.toml`:
```toml
[tools]
java = "temurin-17"
```

- [ ] **Step 3: 安装 Docker(Testcontainers 需要)**

Run(Arch Linux):
```bash
sudo pacman -S --noconfirm docker
sudo systemctl enable --now docker.service
sudo usermod -aG docker "$USER"   # 之后需重新登录或 newgrp docker
newgrp docker
docker run --rm hello-world
```
Expected: 输出 `Hello from Docker!`。若所在环境无法装系统 Docker,请改用 Podman 并设 `DOCKER_HOST`,或在能跑 Docker 的 CI 上执行集成测试。

- [ ] **Step 4: 提交版本锁定**

```bash
cd /home/shane/Code/cim-portal/cim-portal-server
git add backend/.mise.toml
git commit -m "build: 锁定后端 JDK 为 temurin-17"
```

---

## Task 2: Maven 工程脚手架与可启动的应用

**Files:**
- Create: `backend/pom.xml`
- Create: `backend/src/main/java/com/cimportal/CimPortalApplication.java`
- Create: `backend/src/main/resources/application.yml`
- Test: `backend/src/test/java/com/cimportal/CimPortalApplicationTests.java`

- [ ] **Step 1: 写 `pom.xml`**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>
  <parent>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-parent</artifactId>
    <version>3.3.5</version>
    <relativePath/>
  </parent>
  <groupId>com.cimportal</groupId>
  <artifactId>cim-portal-backend</artifactId>
  <version>0.1.0</version>
  <name>cim-portal-backend</name>
  <properties>
    <java.version>17</java.version>
    <testcontainers.version>1.20.3</testcontainers.version>
    <springdoc.version>2.6.0</springdoc.version>
  </properties>
  <dependencies>
    <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-web</artifactId></dependency>
    <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-data-jpa</artifactId></dependency>
    <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-validation</artifactId></dependency>
    <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-security</artifactId></dependency>
    <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-oauth2-resource-server</artifactId></dependency>
    <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-actuator</artifactId></dependency>
    <dependency><groupId>org.flywaydb</groupId><artifactId>flyway-core</artifactId></dependency>
    <dependency><groupId>org.flywaydb</groupId><artifactId>flyway-mysql</artifactId></dependency>
    <dependency><groupId>org.mariadb.jdbc</groupId><artifactId>mariadb-java-client</artifactId><scope>runtime</scope></dependency>
    <dependency><groupId>com.oracle.database.jdbc</groupId><artifactId>ojdbc11</artifactId><scope>runtime</scope></dependency>
    <dependency><groupId>org.springdoc</groupId><artifactId>springdoc-openapi-starter-webmvc-ui</artifactId><version>${springdoc.version}</version></dependency>
    <!-- test -->
    <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-test</artifactId><scope>test</scope></dependency>
    <dependency><groupId>org.springframework.security</groupId><artifactId>spring-security-test</artifactId><scope>test</scope></dependency>
    <dependency><groupId>org.testcontainers</groupId><artifactId>junit-jupiter</artifactId><scope>test</scope></dependency>
    <dependency><groupId>org.testcontainers</groupId><artifactId>mariadb</artifactId><scope>test</scope></dependency>
    <dependency><groupId>org.testcontainers</groupId><artifactId>oracle-free</artifactId><scope>test</scope></dependency>
  </dependencies>
  <dependencyManagement>
    <dependencies>
      <dependency>
        <groupId>org.testcontainers</groupId><artifactId>testcontainers-bom</artifactId>
        <version>${testcontainers.version}</version><type>pom</type><scope>import</scope>
      </dependency>
    </dependencies>
  </dependencyManagement>
  <build>
    <finalName>portal</finalName>
    <plugins>
      <plugin><groupId>org.springframework.boot</groupId><artifactId>spring-boot-maven-plugin</artifactId></plugin>
    </plugins>
  </build>
</project>
```

- [ ] **Step 2: 写应用入口**

`backend/src/main/java/com/cimportal/CimPortalApplication.java`:
```java
package com.cimportal;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class CimPortalApplication {
    public static void main(String[] args) {
        SpringApplication.run(CimPortalApplication.class, args);
    }
}
```

- [ ] **Step 3: 写 `application.yml`(公共 + 三 profile)**

`backend/src/main/resources/application.yml`:
```yaml
spring:
  application:
    name: cim-portal
  jpa:
    open-in-view: false
    hibernate:
      ddl-auto: validate
    properties:
      hibernate.format_sql: true
  flyway:
    enabled: true

springdoc:
  swagger-ui:
    path: /swagger-ui.html

management:
  endpoints:
    web:
      exposure:
        include: health

---
spring:
  config:
    activate:
      on-profile: dev
  datasource:
    url: jdbc:mariadb://127.0.0.1:3306/cim_portal
    username: cim_portal
    password: cim_portal
  flyway:
    locations: classpath:db/migration/mariadb
# dev 用本地生成的 RSA 密钥充当 mock OIDC(见 SecurityConfig)
app:
  security:
    mode: dev-jwt

---
spring:
  config:
    activate:
      on-profile: uat
  datasource:
    url: ${DB_URL}
    username: ${DB_USER}
    password: ${DB_PASSWORD}
  flyway:
    locations: classpath:db/migration/oracle
  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: ${OIDC_ISSUER_URI}
app:
  security:
    mode: oidc

---
spring:
  config:
    activate:
      on-profile: prod
  datasource:
    url: ${DB_URL}
    username: ${DB_USER}
    password: ${DB_PASSWORD}
  flyway:
    locations: classpath:db/migration/oracle
  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: ${OIDC_ISSUER_URI}
app:
  security:
    mode: oidc
```

- [ ] **Step 4: 写启动冒烟测试**

`backend/src/test/java/com/cimportal/CimPortalApplicationTests.java`:
```java
package com.cimportal;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class CimPortalApplicationTests {
    @Test
    void contextLoads() { }
}
```

此测试在 `test` profile 下运行,数据库由后续基类用 Testcontainers 提供。本任务先确保编译通过——`test` profile 暂无数据源,故此处先标记为待 Task 5 基类就绪后通过;现在仅验证编译。

- [ ] **Step 5: 验证编译**

Run:
```bash
cd /home/shane/Code/cim-portal/cim-portal-server/backend
mvn -q -DskipTests compile
```
Expected: `BUILD SUCCESS`。

- [ ] **Step 6: 提交**

```bash
cd /home/shane/Code/cim-portal/cim-portal-server
git add backend/pom.xml backend/src
git commit -m "build: Spring Boot 3 后端脚手架与三环境 profile"
```

---

## Task 3: Flyway 架构迁移(MariaDB)

**Files:**
- Create: `backend/src/main/resources/db/migration/mariadb/V1__schema.sql`

- [ ] **Step 1: 写 MariaDB 架构迁移**

```sql
CREATE TABLE enum_value (
  id          BIGINT       NOT NULL AUTO_INCREMENT,
  category    VARCHAR(32)  NOT NULL,
  code        VARCHAR(64)  NOT NULL,
  label_zh    VARCHAR(255) NOT NULL,
  label_en    VARCHAR(255) NOT NULL,
  sort_order  INT          NOT NULL DEFAULT 0,
  active      TINYINT(1)   NOT NULL DEFAULT 1,
  created_at  DATETIME     NOT NULL,
  updated_at  DATETIME     NOT NULL,
  PRIMARY KEY (id),
  CONSTRAINT uk_enum_category_code UNIQUE (category, code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE link (
  id             BIGINT       NOT NULL AUTO_INCREMENT,
  code           VARCHAR(64)  NOT NULL,
  name_zh        VARCHAR(255) NOT NULL,
  name_en        VARCHAR(255) NOT NULL,
  url            VARCHAR(1024) NOT NULL,
  icon           VARCHAR(64)  NOT NULL,
  category_code  VARCHAR(64)  NOT NULL,
  status_code    VARCHAR(64)  NOT NULL,
  sort_order     INT          NOT NULL DEFAULT 0,
  open_in_new_tab TINYINT(1)  NOT NULL DEFAULT 1,
  created_at     DATETIME     NOT NULL,
  updated_at     DATETIME     NOT NULL,
  PRIMARY KEY (id),
  CONSTRAINT uk_link_code UNIQUE (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE link_access_grant (
  id          BIGINT      NOT NULL AUTO_INCREMENT,
  link_id     BIGINT      NOT NULL,
  grant_type  VARCHAR(16) NOT NULL,
  grant_code  VARCHAR(64) NOT NULL,
  PRIMARY KEY (id),
  CONSTRAINT uk_grant UNIQUE (link_id, grant_type, grant_code),
  CONSTRAINT fk_grant_link FOREIGN KEY (link_id) REFERENCES link(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE label (
  id          BIGINT       NOT NULL AUTO_INCREMENT,
  label_key   VARCHAR(128) NOT NULL,
  type        VARCHAR(32)  NOT NULL,
  text_zh     VARCHAR(1024) NOT NULL,
  text_en     VARCHAR(1024) NOT NULL,
  created_at  DATETIME     NOT NULL,
  updated_at  DATETIME     NOT NULL,
  PRIMARY KEY (id),
  CONSTRAINT uk_label_key UNIQUE (label_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE user_info (
  employee_id     VARCHAR(64)  NOT NULL,
  display_name_zh VARCHAR(255) NOT NULL,
  display_name_en VARCHAR(255) NOT NULL,
  department_code VARCHAR(64)  NOT NULL,
  role_code       VARCHAR(64)  NOT NULL,
  email           VARCHAR(255),
  active          TINYINT(1)   NOT NULL DEFAULT 1,
  synced_at       DATETIME     NOT NULL,
  PRIMARY KEY (employee_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

- [ ] **Step 2: 提交**

```bash
cd /home/shane/Code/cim-portal/cim-portal-server
git add backend/src/main/resources/db/migration/mariadb
git commit -m "feat(db): MariaDB 架构迁移 V1"
```

(MariaDB 迁移将在 Task 5 的 Testcontainers 集成测试中被实际执行验证。)

---

## Task 4: Flyway 架构迁移(Oracle)+ Oracle 迁移验证测试

**Files:**
- Create: `backend/src/main/resources/db/migration/oracle/V1__schema.sql`
- Test: `backend/src/test/java/com/cimportal/migration/OracleMigrationTest.java`

- [ ] **Step 1: 写 Oracle 架构迁移**

```sql
CREATE TABLE enum_value (
  id          NUMBER GENERATED BY DEFAULT AS IDENTITY (START WITH 1000),
  category    VARCHAR2(32)  NOT NULL,
  code        VARCHAR2(64)  NOT NULL,
  label_zh    VARCHAR2(255) NOT NULL,
  label_en    VARCHAR2(255) NOT NULL,
  sort_order  NUMBER(10)    DEFAULT 0 NOT NULL,
  active      NUMBER(1)     DEFAULT 1 NOT NULL,
  created_at  TIMESTAMP     NOT NULL,
  updated_at  TIMESTAMP     NOT NULL,
  CONSTRAINT pk_enum_value PRIMARY KEY (id),
  CONSTRAINT uk_enum_category_code UNIQUE (category, code)
);

CREATE TABLE link (
  id             NUMBER GENERATED BY DEFAULT AS IDENTITY (START WITH 1000),
  code           VARCHAR2(64)   NOT NULL,
  name_zh        VARCHAR2(255)  NOT NULL,
  name_en        VARCHAR2(255)  NOT NULL,
  url            VARCHAR2(1024) NOT NULL,
  icon           VARCHAR2(64)   NOT NULL,
  category_code  VARCHAR2(64)   NOT NULL,
  status_code    VARCHAR2(64)   NOT NULL,
  sort_order     NUMBER(10)     DEFAULT 0 NOT NULL,
  open_in_new_tab NUMBER(1)     DEFAULT 1 NOT NULL,
  created_at     TIMESTAMP      NOT NULL,
  updated_at     TIMESTAMP      NOT NULL,
  CONSTRAINT pk_link PRIMARY KEY (id),
  CONSTRAINT uk_link_code UNIQUE (code)
);

CREATE TABLE link_access_grant (
  id          NUMBER GENERATED BY DEFAULT AS IDENTITY (START WITH 1000),
  link_id     NUMBER       NOT NULL,
  grant_type  VARCHAR2(16) NOT NULL,
  grant_code  VARCHAR2(64) NOT NULL,
  CONSTRAINT pk_grant PRIMARY KEY (id),
  CONSTRAINT uk_grant UNIQUE (link_id, grant_type, grant_code),
  CONSTRAINT fk_grant_link FOREIGN KEY (link_id) REFERENCES link(id) ON DELETE CASCADE
);

CREATE TABLE label (
  id          NUMBER GENERATED BY DEFAULT AS IDENTITY (START WITH 1000),
  label_key   VARCHAR2(128)  NOT NULL,
  type        VARCHAR2(32)   NOT NULL,
  text_zh     VARCHAR2(1024) NOT NULL,
  text_en     VARCHAR2(1024) NOT NULL,
  created_at  TIMESTAMP      NOT NULL,
  updated_at  TIMESTAMP      NOT NULL,
  CONSTRAINT pk_label PRIMARY KEY (id),
  CONSTRAINT uk_label_key UNIQUE (label_key)
);

CREATE TABLE user_info (
  employee_id     VARCHAR2(64)  NOT NULL,
  display_name_zh VARCHAR2(255) NOT NULL,
  display_name_en VARCHAR2(255) NOT NULL,
  department_code VARCHAR2(64)  NOT NULL,
  role_code       VARCHAR2(64)  NOT NULL,
  email           VARCHAR2(255),
  active          NUMBER(1)     DEFAULT 1 NOT NULL,
  synced_at       TIMESTAMP     NOT NULL,
  CONSTRAINT pk_user_info PRIMARY KEY (employee_id)
);
```

- [ ] **Step 2: 写失败测试(Oracle 迁移在干净库上成功)**

`OracleMigrationTest.java`:
```java
package com.cimportal.migration;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.oracle.OracleContainer;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class OracleMigrationTest {

    @Container
    static final OracleContainer ORACLE =
        new OracleContainer("gvenzl/oracle-free:slim-faststart");

    @Test
    void oracleMigrationsApplyCleanly() {
        Flyway flyway = Flyway.configure()
            .dataSource(ORACLE.getJdbcUrl(), ORACLE.getUsername(), ORACLE.getPassword())
            .locations("classpath:db/migration/oracle")
            .load();
        MigrateResult result = flyway.migrate();
        assertThat(result.success).isTrue();
        assertThat(result.migrationsExecuted).isEqualTo(1);
    }
}
```

- [ ] **Step 3: 运行测试,确认通过**

Run:
```bash
cd /home/shane/Code/cim-portal/cim-portal-server/backend
mvn -q -Dtest=OracleMigrationTest test
```
Expected: PASS(首次拉取 Oracle 镜像较慢,数分钟)。若失败且报方言语法,修正 `oracle/V1__schema.sql` 后重跑。

- [ ] **Step 4: 提交**

```bash
cd /home/shane/Code/cim-portal/cim-portal-server
git add backend/src/main/resources/db/migration/oracle backend/src/test/java/com/cimportal/migration
git commit -m "feat(db): Oracle 架构迁移 V1 + Testcontainers 验证"
```

---

## Task 5: 测试基类(Testcontainers MariaDB)+ EnumValue 实体与仓储

**Files:**
- Create: `backend/src/test/java/com/cimportal/support/MariaDbIntegrationTest.java`
- Create: `backend/src/main/java/com/cimportal/enumvalue/EnumCategory.java`
- Create: `backend/src/main/java/com/cimportal/enumvalue/EnumValue.java`
- Create: `backend/src/main/java/com/cimportal/enumvalue/EnumValueRepository.java`
- Test: `backend/src/test/java/com/cimportal/enumvalue/EnumValueRepositoryTest.java`

- [ ] **Step 1: 写 MariaDB 集成测试基类**

`MariaDbIntegrationTest.java`:
```java
package com.cimportal.support;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
public abstract class MariaDbIntegrationTest {

    static final MariaDBContainer<?> DB = new MariaDBContainer<>("mariadb:11.4");

    static { DB.start(); }   // 单容器跨全部测试复用

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", DB::getJdbcUrl);
        r.add("spring.datasource.username", DB::getUsername);
        r.add("spring.datasource.password", DB::getPassword);
        r.add("spring.flyway.locations", () -> "classpath:db/migration/mariadb");
    }
}
```

补充:在 `application.yml` 末尾追加 `test` profile 片段(让 `@ActiveProfiles("test")` 有 OIDC mock 配置):
```yaml
---
spring:
  config:
    activate:
      on-profile: test
app:
  security:
    mode: dev-jwt
```

- [ ] **Step 2: 写 EnumCategory 与 EnumValue 实体**

`EnumCategory.java`:
```java
package com.cimportal.enumvalue;

public enum EnumCategory { DEPARTMENT, ROLE, LINK_CATEGORY, LINK_STATUS }
```

`EnumValue.java`:
```java
package com.cimportal.enumvalue;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import java.time.Instant;

@Entity
@Table(name = "enum_value",
       uniqueConstraints = @UniqueConstraint(name = "uk_enum_category_code",
                                             columnNames = {"category", "code"}))
public class EnumValue {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private EnumCategory category;

    @Column(nullable = false, length = 64)
    private String code;

    @Column(name = "label_zh", nullable = false) private String labelZh;
    @Column(name = "label_en", nullable = false) private String labelEn;
    @Column(name = "sort_order", nullable = false) private int sortOrder;
    @Column(nullable = false) private boolean active = true;

    @CreationTimestamp @Column(name = "created_at", updatable = false) private Instant createdAt;
    @UpdateTimestamp  @Column(name = "updated_at") private Instant updatedAt;

    protected EnumValue() { }

    public EnumValue(EnumCategory category, String code, String labelZh, String labelEn,
                     int sortOrder, boolean active) {
        this.category = category; this.code = code; this.labelZh = labelZh;
        this.labelEn = labelEn; this.sortOrder = sortOrder; this.active = active;
    }

    public Long getId() { return id; }
    public EnumCategory getCategory() { return category; }
    public String getCode() { return code; }
    public String getLabelZh() { return labelZh; }
    public String getLabelEn() { return labelEn; }
    public int getSortOrder() { return sortOrder; }
    public boolean isActive() { return active; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void setLabelZh(String v) { this.labelZh = v; }
    public void setLabelEn(String v) { this.labelEn = v; }
    public void setSortOrder(int v) { this.sortOrder = v; }
    public void setActive(boolean v) { this.active = v; }
}
```

- [ ] **Step 3: 写仓储**

`EnumValueRepository.java`:
```java
package com.cimportal.enumvalue;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface EnumValueRepository extends JpaRepository<EnumValue, Long> {
    List<EnumValue> findByCategoryOrderBySortOrderAscIdAsc(EnumCategory category);
    List<EnumValue> findByCategoryAndActiveTrueOrderBySortOrderAscIdAsc(EnumCategory category);
    Optional<EnumValue> findByCategoryAndCode(EnumCategory category, String code);
    boolean existsByCategoryAndCode(EnumCategory category, String code);
}
```

- [ ] **Step 4: 写失败测试**

`EnumValueRepositoryTest.java`:
```java
package com.cimportal.enumvalue;

import com.cimportal.support.MariaDbIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

class EnumValueRepositoryTest extends MariaDbIntegrationTest {

    @Autowired EnumValueRepository repo;

    @Test
    void savesAndFindsActiveByCategoryOrdered() {
        repo.save(new EnumValue(EnumCategory.ROLE, "OPERATOR", "操作员", "Operator", 20, true));
        repo.save(new EnumValue(EnumCategory.ROLE, "ADMIN", "管理员", "Admin", 10, true));
        repo.save(new EnumValue(EnumCategory.ROLE, "OLD", "旧", "Old", 5, false));

        var active = repo.findByCategoryAndActiveTrueOrderBySortOrderAscIdAsc(EnumCategory.ROLE);

        assertThat(active).extracting(EnumValue::getCode).containsExactly("ADMIN", "OPERATOR");
        assertThat(repo.existsByCategoryAndCode(EnumCategory.ROLE, "OLD")).isTrue();
    }
}
```

- [ ] **Step 5: 运行,确认先失败再通过**

Run:
```bash
cd /home/shane/Code/cim-portal/cim-portal-server/backend
mvn -q -Dtest=EnumValueRepositoryTest test
```
Expected: PASS(Hibernate `ddl-auto=validate` 对照 MariaDB 迁移校验实体映射;若字段不匹配会报错 → 修正实体或迁移)。

- [ ] **Step 6: 提交**

```bash
cd /home/shane/Code/cim-portal/cim-portal-server
git add backend/src/test/java/com/cimportal/support backend/src/main/java/com/cimportal/enumvalue backend/src/test/java/com/cimportal/enumvalue backend/src/main/resources/application.yml
git commit -m "feat(enum): EnumValue 实体/仓储 + MariaDB 集成测试基类"
```

---

## Task 6: Link 与 LinkAccessGrant 实体、仓储

**Files:**
- Create: `backend/src/main/java/com/cimportal/link/GrantType.java`
- Create: `backend/src/main/java/com/cimportal/link/Link.java`
- Create: `backend/src/main/java/com/cimportal/link/LinkAccessGrant.java`
- Create: `backend/src/main/java/com/cimportal/link/LinkRepository.java`
- Create: `backend/src/main/java/com/cimportal/link/LinkAccessGrantRepository.java`
- Test: `backend/src/test/java/com/cimportal/link/LinkRepositoryTest.java`

- [ ] **Step 1: 写枚举与实体**

`GrantType.java`:
```java
package com.cimportal.link;

public enum GrantType { DEPARTMENT, ROLE }
```

`Link.java`:
```java
package com.cimportal.link;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import java.time.Instant;

@Entity
@Table(name = "link", uniqueConstraints = @UniqueConstraint(name = "uk_link_code", columnNames = "code"))
public class Link {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false, length = 64) private String code;
    @Column(name = "name_zh", nullable = false) private String nameZh;
    @Column(name = "name_en", nullable = false) private String nameEn;
    @Column(nullable = false, length = 1024) private String url;
    @Column(nullable = false, length = 64) private String icon;
    @Column(name = "category_code", nullable = false, length = 64) private String categoryCode;
    @Column(name = "status_code", nullable = false, length = 64) private String statusCode;
    @Column(name = "sort_order", nullable = false) private int sortOrder;
    @Column(name = "open_in_new_tab", nullable = false) private boolean openInNewTab = true;
    @CreationTimestamp @Column(name = "created_at", updatable = false) private Instant createdAt;
    @UpdateTimestamp @Column(name = "updated_at") private Instant updatedAt;

    protected Link() { }

    public Long getId() { return id; }
    public String getCode() { return code; }
    public String getNameZh() { return nameZh; }
    public String getNameEn() { return nameEn; }
    public String getUrl() { return url; }
    public String getIcon() { return icon; }
    public String getCategoryCode() { return categoryCode; }
    public String getStatusCode() { return statusCode; }
    public int getSortOrder() { return sortOrder; }
    public boolean isOpenInNewTab() { return openInNewTab; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void setCode(String v) { this.code = v; }
    public void setNameZh(String v) { this.nameZh = v; }
    public void setNameEn(String v) { this.nameEn = v; }
    public void setUrl(String v) { this.url = v; }
    public void setIcon(String v) { this.icon = v; }
    public void setCategoryCode(String v) { this.categoryCode = v; }
    public void setStatusCode(String v) { this.statusCode = v; }
    public void setSortOrder(int v) { this.sortOrder = v; }
    public void setOpenInNewTab(boolean v) { this.openInNewTab = v; }
}
```

`LinkAccessGrant.java`:
```java
package com.cimportal.link;

import jakarta.persistence.*;

@Entity
@Table(name = "link_access_grant",
       uniqueConstraints = @UniqueConstraint(name = "uk_grant",
           columnNames = {"link_id", "grant_type", "grant_code"}))
public class LinkAccessGrant {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "link_id", nullable = false) private Long linkId;
    @Enumerated(EnumType.STRING) @Column(name = "grant_type", nullable = false, length = 16)
    private GrantType grantType;
    @Column(name = "grant_code", nullable = false, length = 64) private String grantCode;

    protected LinkAccessGrant() { }
    public LinkAccessGrant(Long linkId, GrantType grantType, String grantCode) {
        this.linkId = linkId; this.grantType = grantType; this.grantCode = grantCode;
    }
    public Long getId() { return id; }
    public Long getLinkId() { return linkId; }
    public GrantType getGrantType() { return grantType; }
    public String getGrantCode() { return grantCode; }
}
```

- [ ] **Step 2: 写仓储**

`LinkRepository.java`:
```java
package com.cimportal.link;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface LinkRepository extends JpaRepository<Link, Long> {
    boolean existsByCode(String code);
    Optional<Link> findByCode(String code);
    List<Link> findAllByOrderBySortOrderAscIdAsc();
}
```

`LinkAccessGrantRepository.java`:
```java
package com.cimportal.link;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface LinkAccessGrantRepository extends JpaRepository<LinkAccessGrant, Long> {
    List<LinkAccessGrant> findByLinkId(Long linkId);
    List<LinkAccessGrant> findByLinkIdIn(List<Long> linkIds);
    void deleteByLinkId(Long linkId);
}
```

- [ ] **Step 3: 写失败测试**

`LinkRepositoryTest.java`:
```java
package com.cimportal.link;

import com.cimportal.support.MariaDbIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

class LinkRepositoryTest extends MariaDbIntegrationTest {
    @Autowired LinkRepository links;
    @Autowired LinkAccessGrantRepository grants;

    @Test
    void cascadeDeleteRemovesGrants() {
        Link l = links.save(newLink("mes-wip"));
        grants.save(new LinkAccessGrant(l.getId(), GrantType.ROLE, "OPERATOR"));
        assertThat(grants.findByLinkId(l.getId())).hasSize(1);

        links.deleteById(l.getId());
        links.flush();

        assertThat(grants.findByLinkId(l.getId())).isEmpty();
    }

    private Link newLink(String code) {
        Link l = new Link();
        l.setCode(code); l.setNameZh("名"); l.setNameEn("name");
        l.setUrl("https://x"); l.setIcon("factory");
        l.setCategoryCode("MES"); l.setStatusCode("ACTIVE");
        l.setSortOrder(1); l.setOpenInNewTab(true);
        return l;
    }
}
```

- [ ] **Step 4: 运行,确认通过**

Run: `mvn -q -Dtest=LinkRepositoryTest test`(在 `backend/` 下)
Expected: PASS。

- [ ] **Step 5: 提交**

```bash
cd /home/shane/Code/cim-portal/cim-portal-server
git add backend/src/main/java/com/cimportal/link backend/src/test/java/com/cimportal/link
git commit -m "feat(link): Link/LinkAccessGrant 实体与仓储(级联删除)"
```

---

## Task 7: Label 与 UserInfo 实体、仓储

**Files:**
- Create: `backend/src/main/java/com/cimportal/label/Label.java`
- Create: `backend/src/main/java/com/cimportal/label/LabelRepository.java`
- Create: `backend/src/main/java/com/cimportal/user/UserInfo.java`
- Create: `backend/src/main/java/com/cimportal/user/UserInfoRepository.java`
- Test: `backend/src/test/java/com/cimportal/label/LabelRepositoryTest.java`

- [ ] **Step 1: 写 Label 实体与仓储**

`Label.java`:
```java
package com.cimportal.label;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import java.time.Instant;

@Entity
@Table(name = "label", uniqueConstraints = @UniqueConstraint(name = "uk_label_key", columnNames = "label_key"))
public class Label {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "label_key", nullable = false, length = 128) private String labelKey;
    @Column(nullable = false, length = 32) private String type;
    @Column(name = "text_zh", nullable = false, length = 1024) private String textZh;
    @Column(name = "text_en", nullable = false, length = 1024) private String textEn;
    @CreationTimestamp @Column(name = "created_at", updatable = false) private Instant createdAt;
    @UpdateTimestamp @Column(name = "updated_at") private Instant updatedAt;

    protected Label() { }
    public Label(String labelKey, String type, String textZh, String textEn) {
        this.labelKey = labelKey; this.type = type; this.textZh = textZh; this.textEn = textEn;
    }
    public Long getId() { return id; }
    public String getLabelKey() { return labelKey; }
    public String getType() { return type; }
    public String getTextZh() { return textZh; }
    public String getTextEn() { return textEn; }
    public void setType(String v) { this.type = v; }
    public void setTextZh(String v) { this.textZh = v; }
    public void setTextEn(String v) { this.textEn = v; }
}
```

`LabelRepository.java`:
```java
package com.cimportal.label;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface LabelRepository extends JpaRepository<Label, Long> {
    boolean existsByLabelKey(String labelKey);
    List<Label> findByType(String type);
    List<Label> findAllByOrderByLabelKeyAsc();
}
```

- [ ] **Step 2: 写 UserInfo 实体与仓储**

`UserInfo.java`:
```java
package com.cimportal.user;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "user_info")
public class UserInfo {
    @Id @Column(name = "employee_id", length = 64) private String employeeId;
    @Column(name = "display_name_zh", nullable = false) private String displayNameZh;
    @Column(name = "display_name_en", nullable = false) private String displayNameEn;
    @Column(name = "department_code", nullable = false, length = 64) private String departmentCode;
    @Column(name = "role_code", nullable = false, length = 64) private String roleCode;
    @Column private String email;
    @Column(nullable = false) private boolean active = true;
    @Column(name = "synced_at", nullable = false) private Instant syncedAt;

    protected UserInfo() { }
    public UserInfo(String employeeId, String displayNameZh, String displayNameEn,
                    String departmentCode, String roleCode, String email,
                    boolean active, Instant syncedAt) {
        this.employeeId = employeeId; this.displayNameZh = displayNameZh;
        this.displayNameEn = displayNameEn; this.departmentCode = departmentCode;
        this.roleCode = roleCode; this.email = email; this.active = active; this.syncedAt = syncedAt;
    }
    public String getEmployeeId() { return employeeId; }
    public String getDisplayNameZh() { return displayNameZh; }
    public String getDisplayNameEn() { return displayNameEn; }
    public String getDepartmentCode() { return departmentCode; }
    public String getRoleCode() { return roleCode; }
    public String getEmail() { return email; }
    public boolean isActive() { return active; }
    public Instant getSyncedAt() { return syncedAt; }
}
```

`UserInfoRepository.java`:
```java
package com.cimportal.user;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface UserInfoRepository extends JpaRepository<UserInfo, String> {
    List<UserInfo> findByDepartmentCode(String departmentCode);
    List<UserInfo> findByRoleCode(String roleCode);
}
```

- [ ] **Step 3: 写失败测试(Label 唯一键)**

`LabelRepositoryTest.java`:
```java
package com.cimportal.label;

import com.cimportal.support.MariaDbIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

class LabelRepositoryTest extends MariaDbIntegrationTest {
    @Autowired LabelRepository repo;

    @Test
    void savesAndChecksKey() {
        repo.save(new Label("portal.title", "SYSTEM_NAME", "门户", "Portal"));
        assertThat(repo.existsByLabelKey("portal.title")).isTrue();
        assertThat(repo.findByType("SYSTEM_NAME")).hasSize(1);
    }
}
```

- [ ] **Step 4: 运行,确认通过**

Run: `mvn -q -Dtest=LabelRepositoryTest test`
Expected: PASS。

- [ ] **Step 5: 提交**

```bash
cd /home/shane/Code/cim-portal/cim-portal-server
git add backend/src/main/java/com/cimportal/label backend/src/main/java/com/cimportal/user backend/src/test/java/com/cimportal/label
git commit -m "feat: Label 与 UserInfo 实体/仓储"
```

---

## Task 8: 统一错误契约(ApiError + 全局异常处理)

**Files:**
- Create: `backend/src/main/java/com/cimportal/common/error/ErrorCode.java`
- Create: `backend/src/main/java/com/cimportal/common/error/ApiException.java`
- Create: `backend/src/main/java/com/cimportal/common/error/ApiError.java`
- Create: `backend/src/main/java/com/cimportal/common/error/GlobalExceptionHandler.java`

- [ ] **Step 1: 写 ErrorCode 与 ApiException**

`ErrorCode.java`:
```java
package com.cimportal.common.error;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST),
    UNAUTHENTICATED(HttpStatus.UNAUTHORIZED),
    FORBIDDEN(HttpStatus.FORBIDDEN),
    NOT_FOUND(HttpStatus.NOT_FOUND),
    USER_NOT_PROVISIONED(HttpStatus.NOT_FOUND),
    USER_INACTIVE(HttpStatus.FORBIDDEN),
    DUPLICATE_CODE(HttpStatus.CONFLICT),
    IN_USE(HttpStatus.CONFLICT),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR);

    public final HttpStatus status;
    ErrorCode(HttpStatus status) { this.status = status; }
}
```

`ApiException.java`:
```java
package com.cimportal.common.error;

public class ApiException extends RuntimeException {
    public final ErrorCode code;
    public ApiException(ErrorCode code, String message) { super(message); this.code = code; }

    public static ApiException notFound(String what)    { return new ApiException(ErrorCode.NOT_FOUND, what + " 不存在"); }
    public static ApiException duplicate(String message) { return new ApiException(ErrorCode.DUPLICATE_CODE, message); }
    public static ApiException inUse(String message)     { return new ApiException(ErrorCode.IN_USE, message); }
    public static ApiException badRequest(String message){ return new ApiException(ErrorCode.VALIDATION_FAILED, message); }
}
```

- [ ] **Step 2: 写 ApiError 与全局处理器**

`ApiError.java`:
```java
package com.cimportal.common.error;

import java.time.Instant;
import java.util.List;

public record ApiError(
    Instant timestamp, int status, String error, String code,
    String message, String path, List<FieldError> fieldErrors
) {
    public record FieldError(String field, String message) { }
}
```

`GlobalExceptionHandler.java`:
```java
package com.cimportal.common.error;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.List;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiError> handleApi(ApiException ex, HttpServletRequest req) {
        return build(ex.code, ex.getMessage(), req, List.of());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest req) {
        var fields = ex.getBindingResult().getFieldErrors().stream()
            .map(f -> new ApiError.FieldError(f.getField(), f.getDefaultMessage())).toList();
        return build(ErrorCode.VALIDATION_FAILED, "请求体校验失败", req, fields);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> handleDenied(AccessDeniedException ex, HttpServletRequest req) {
        return build(ErrorCode.FORBIDDEN, "无权访问", req, List.of());
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiError> handleAuth(AuthenticationException ex, HttpServletRequest req) {
        return build(ErrorCode.UNAUTHENTICATED, "未认证", req, List.of());
    }

    private ResponseEntity<ApiError> build(ErrorCode code, String message,
                                           HttpServletRequest req, List<ApiError.FieldError> fields) {
        ApiError body = new ApiError(Instant.now(), code.status.value(),
            code.status.getReasonPhrase(), code.name(), message, req.getRequestURI(),
            fields.isEmpty() ? null : fields);
        return ResponseEntity.status(code.status).body(body);
    }
}
```

- [ ] **Step 3: 编译验证**

Run: `mvn -q -DskipTests compile`(在 `backend/`)
Expected: `BUILD SUCCESS`。(此处属基础设施,行为将在后续控制器测试中被覆盖。)

- [ ] **Step 4: 提交**

```bash
cd /home/shane/Code/cim-portal/cim-portal-server
git add backend/src/main/java/com/cimportal/common/error
git commit -m "feat(common): 统一错误契约与全局异常处理"
```

---

## Task 9: 安全配置 + Jwt→权限转换 + 当前用户解析

**Files:**
- Create: `backend/src/main/java/com/cimportal/auth/CurrentUser.java`
- Create: `backend/src/main/java/com/cimportal/auth/UserInfoAuthoritiesConverter.java`
- Create: `backend/src/main/java/com/cimportal/auth/CurrentUserService.java`
- Create: `backend/src/main/java/com/cimportal/auth/SecurityConfig.java`
- Create: `backend/src/main/java/com/cimportal/auth/dev/DevTokenController.java`
- Create: `backend/src/test/java/com/cimportal/support/TestJwts.java`

设计要点:JWT 仅做**认证**(`sub` = employeeId)。**部门/角色与管理员身份从 `user_info` 表解析**——`role_code == "PORTAL_ADMIN"` 即管理员,转换器据此授予 `ROLE_PORTAL_ADMIN`。dev/test 用本地 RSA 密钥充当 mock OIDC;uat/prod 用 `issuer-uri` 对接真实 IdP。

- [ ] **Step 1: 写 CurrentUser 与转换器**

`CurrentUser.java`:
```java
package com.cimportal.auth;

public record CurrentUser(String employeeId, String departmentCode, String roleCode, boolean admin) {
    public static final String ADMIN_ROLE_CODE = "PORTAL_ADMIN";
}
```

`UserInfoAuthoritiesConverter.java`:
```java
package com.cimportal.auth;

import com.cimportal.user.UserInfo;
import com.cimportal.user.UserInfoRepository;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/** 认证后用 sub 查 user_info,据 role_code 决定是否授予 ROLE_PORTAL_ADMIN。 */
@Component
public class UserInfoAuthoritiesConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private final UserInfoRepository users;
    public UserInfoAuthoritiesConverter(UserInfoRepository users) { this.users = users; }

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        List<GrantedAuthority> authorities = new ArrayList<>();
        String employeeId = jwt.getSubject();
        users.findById(employeeId)
             .filter(UserInfo::isActive)
             .filter(u -> CurrentUser.ADMIN_ROLE_CODE.equals(u.getRoleCode()))
             .ifPresent(u -> authorities.add(new SimpleGrantedAuthority("ROLE_PORTAL_ADMIN")));
        return new JwtAuthenticationToken(jwt, authorities, employeeId);
    }
}
```

- [ ] **Step 2: 写 CurrentUserService(解析当前用户的部门/角色)**

`CurrentUserService.java`:
```java
package com.cimportal.auth;

import com.cimportal.common.error.ApiException;
import com.cimportal.common.error.ErrorCode;
import com.cimportal.user.UserInfo;
import com.cimportal.user.UserInfoRepository;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
public class CurrentUserService {
    private final UserInfoRepository users;
    public CurrentUserService(UserInfoRepository users) { this.users = users; }

    /** 已认证用户的 employeeId(token sub)。 */
    public String currentEmployeeId() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getName() == null)
            throw new ApiException(ErrorCode.UNAUTHENTICATED, "未认证");
        return auth.getName();
    }

    /** 解析当前用户;未配置→404,停用→403。 */
    public CurrentUser require() {
        String id = currentEmployeeId();
        UserInfo u = users.findById(id)
            .orElseThrow(() -> new ApiException(ErrorCode.USER_NOT_PROVISIONED, "用户未在 user_info 中配置: " + id));
        if (!u.isActive())
            throw new ApiException(ErrorCode.USER_INACTIVE, "用户已停用: " + id);
        boolean admin = CurrentUser.ADMIN_ROLE_CODE.equals(u.getRoleCode());
        return new CurrentUser(u.getEmployeeId(), u.getDepartmentCode(), u.getRoleCode(), admin);
    }
}
```

- [ ] **Step 3: 写 SecurityConfig(dev 用本地密钥,uat/prod 用 issuer)**

`SecurityConfig.java`:
```java
package com.cimportal.auth;

import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.JWKSet;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.SecurityFilterChain;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;

@Configuration
public class SecurityConfig {

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http,
                                    UserInfoAuthoritiesConverter authoritiesConverter) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/health", "/swagger-ui.html", "/swagger-ui/**",
                                 "/v3/api-docs/**", "/dev/token").permitAll()
                .requestMatchers("/api/admin/**").hasRole("PORTAL_ADMIN")
                .anyRequest().authenticated())
            .oauth2ResourceServer(oauth -> oauth.jwt(jwt -> jwt.jwtAuthenticationConverter(authoritiesConverter)));
        return http.build();
    }

    /** dev/test:进程内 RSA 密钥对,既签发(DevTokenController)又校验(JwtDecoder)。 */
    @Bean
    @Profile({"dev", "test"})
    KeyPair devKeyPair() throws Exception {
        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048);
        return gen.generateKeyPair();
    }

    @Bean
    @Profile({"dev", "test"})
    JwtDecoder devJwtDecoder(KeyPair kp) {
        return NimbusJwtDecoder.withPublicKey((RSAPublicKey) kp.getPublic()).build();
    }

    @Bean
    @Profile({"dev", "test"})
    JwtEncoder devJwtEncoder(KeyPair kp) {
        RSAKey jwk = new RSAKey.Builder((RSAPublicKey) kp.getPublic())
            .privateKey((RSAPrivateKey) kp.getPrivate()).build();
        return new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(jwk)));
    }
}
```
> uat/prod 不声明上述 dev bean,`spring.security.oauth2.resourceserver.jwt.issuer-uri` 会自动装配标准 `JwtDecoder` 对接真实 IdP。

- [ ] **Step 4: 写 dev mock 发令牌端点**

`DevTokenController.java`:
```java
package com.cimportal.auth.dev;

import org.springframework.context.annotation.Profile;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;

/** 仅 dev:用 employeeId 换一个本地签名的 JWT,免去真实 IdP。 */
@RestController
@Profile("dev")
public class DevTokenController {
    private final JwtEncoder encoder;
    public DevTokenController(JwtEncoder encoder) { this.encoder = encoder; }

    @GetMapping("/dev/token")
    public Map<String, String> token(@RequestParam String employeeId) {
        JwtClaimsSet claims = JwtClaimsSet.builder()
            .subject(employeeId)
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plus(12, ChronoUnit.HOURS))
            .build();
        String token = encoder.encode(JwtEncoderParameters.from(claims)).getTokenValue();
        return Map.of("access_token", token, "token_type", "Bearer");
    }
}
```

- [ ] **Step 5: 写测试 JWT 工具**

`TestJwts.java`:
```java
package com.cimportal.support;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Component
public class TestJwts {
    @Autowired JwtEncoder encoder;

    /** 生成 sub=employeeId 的 Bearer 值,用于 MockMvc 的 Authorization 头。 */
    public String bearerFor(String employeeId) {
        JwtClaimsSet claims = JwtClaimsSet.builder()
            .subject(employeeId).issuedAt(Instant.now())
            .expiresAt(Instant.now().plus(1, ChronoUnit.HOURS)).build();
        return encoder.encode(JwtEncoderParameters.from(claims)).getTokenValue();
    }
}
```

- [ ] **Step 6: 编译验证**

Run: `mvn -q -DskipTests compile`
Expected: `BUILD SUCCESS`(安全行为将在 Task 13 集成测试覆盖)。

- [ ] **Step 7: 提交**

```bash
cd /home/shane/Code/cim-portal/cim-portal-server
git add backend/src/main/java/com/cimportal/auth backend/src/test/java/com/cimportal/support/TestJwts.java
git commit -m "feat(auth): OIDC 资源服务器 + user_info 权限解析 + dev mock 令牌"
```

---

## Task 10: 枚举 CRUD(服务 + 公共/管理控制器)

**Files:**
- Create: `backend/src/main/java/com/cimportal/enumvalue/dto/EnumValueResponse.java`
- Create: `backend/src/main/java/com/cimportal/enumvalue/dto/EnumValueRequest.java`
- Create: `backend/src/main/java/com/cimportal/enumvalue/EnumValueService.java`
- Create: `backend/src/main/java/com/cimportal/enumvalue/EnumController.java`
- Create: `backend/src/main/java/com/cimportal/enumvalue/EnumAdminController.java`
- Test: `backend/src/test/java/com/cimportal/enumvalue/EnumAdminControllerTest.java`

- [ ] **Step 1: 写 DTO**

`dto/EnumValueResponse.java`:
```java
package com.cimportal.enumvalue.dto;

import com.cimportal.enumvalue.EnumCategory;
import com.cimportal.enumvalue.EnumValue;
import java.time.Instant;

public record EnumValueResponse(Long id, EnumCategory category, String code,
                                String labelZh, String labelEn, int sortOrder,
                                boolean active, Instant createdAt, Instant updatedAt) {
    public static EnumValueResponse of(EnumValue e) {
        return new EnumValueResponse(e.getId(), e.getCategory(), e.getCode(),
            e.getLabelZh(), e.getLabelEn(), e.getSortOrder(), e.isActive(),
            e.getCreatedAt(), e.getUpdatedAt());
    }
}
```

`dto/EnumValueRequest.java`:
```java
package com.cimportal.enumvalue.dto;

import jakarta.validation.constraints.NotBlank;

public record EnumValueRequest(
    @NotBlank String code,
    @NotBlank String labelZh,
    @NotBlank String labelEn,
    int sortOrder,
    Boolean active
) {
    public boolean activeOrDefault() { return active == null || active; }
}
```

- [ ] **Step 2: 写服务**

`EnumValueService.java`:
```java
package com.cimportal.enumvalue;

import com.cimportal.common.error.ApiException;
import com.cimportal.enumvalue.dto.EnumValueRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class EnumValueService {
    private final EnumValueRepository repo;
    public EnumValueService(EnumValueRepository repo) { this.repo = repo; }

    @Transactional(readOnly = true)
    public List<EnumValue> listActive(EnumCategory category) {
        return repo.findByCategoryAndActiveTrueOrderBySortOrderAscIdAsc(category);
    }

    @Transactional(readOnly = true)
    public List<EnumValue> listAll(EnumCategory category) {
        return repo.findByCategoryOrderBySortOrderAscIdAsc(category);
    }

    @Transactional
    public EnumValue create(EnumCategory category, EnumValueRequest req) {
        if (repo.existsByCategoryAndCode(category, req.code()))
            throw ApiException.duplicate("枚举值 code '" + req.code() + "' 在类别 " + category + " 下已存在");
        return repo.save(new EnumValue(category, req.code(), req.labelZh(), req.labelEn(),
            req.sortOrder(), req.activeOrDefault()));
    }

    @Transactional
    public EnumValue update(EnumCategory category, Long id, EnumValueRequest req) {
        EnumValue e = repo.findById(id).filter(x -> x.getCategory() == category)
            .orElseThrow(() -> ApiException.notFound("枚举值"));
        // code 不可变(被引用);仅更新展示字段
        e.setLabelZh(req.labelZh());
        e.setLabelEn(req.labelEn());
        e.setSortOrder(req.sortOrder());
        e.setActive(req.activeOrDefault());
        return e;
    }

    @Transactional
    public void delete(EnumCategory category, Long id) {
        EnumValue e = repo.findById(id).filter(x -> x.getCategory() == category)
            .orElseThrow(() -> ApiException.notFound("枚举值"));
        // 引用检查(链接 category/status、授权 grant_code、用户 dept/role)在 Task 12/13 接入后,
        // 此处先做基础删除;被外键或唯一约束阻止时由 DataIntegrityViolation → 由调用方改用停用。
        repo.delete(e);
    }
}
```
> 注:更完整的「IN_USE 引用检查」在 Task 12 之后可加一条跨仓储校验;当前实现满足基础 CRUD,删除被引用值时建议前端改用「停用」。

- [ ] **Step 3: 写控制器**

`EnumController.java`:
```java
package com.cimportal.enumvalue;

import com.cimportal.enumvalue.dto.EnumValueResponse;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/enums")
public class EnumController {
    private final EnumValueService service;
    public EnumController(EnumValueService service) { this.service = service; }

    @GetMapping("/{category}")
    public List<EnumValueResponse> list(@PathVariable EnumCategory category) {
        return service.listActive(category).stream().map(EnumValueResponse::of).toList();
    }
}
```

`EnumAdminController.java`:
```java
package com.cimportal.enumvalue;

import com.cimportal.enumvalue.dto.EnumValueRequest;
import com.cimportal.enumvalue.dto.EnumValueResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/admin/enums")
public class EnumAdminController {
    private final EnumValueService service;
    public EnumAdminController(EnumValueService service) { this.service = service; }

    @GetMapping("/{category}")
    public List<EnumValueResponse> list(@PathVariable EnumCategory category) {
        return service.listAll(category).stream().map(EnumValueResponse::of).toList();
    }

    @PostMapping("/{category}")
    @ResponseStatus(HttpStatus.CREATED)
    public EnumValueResponse create(@PathVariable EnumCategory category, @Valid @RequestBody EnumValueRequest req) {
        return EnumValueResponse.of(service.create(category, req));
    }

    @PutMapping("/{category}/{id}")
    public EnumValueResponse update(@PathVariable EnumCategory category, @PathVariable Long id,
                                    @Valid @RequestBody EnumValueRequest req) {
        return EnumValueResponse.of(service.update(category, id, req));
    }

    @DeleteMapping("/{category}/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable EnumCategory category, @PathVariable Long id) {
        service.delete(category, id);
    }
}
```

- [ ] **Step 4: 写失败测试(管理 CRUD 含 409 与鉴权)**

`EnumAdminControllerTest.java`:
```java
package com.cimportal.enumvalue;

import com.cimportal.support.MariaDbIntegrationTest;
import com.cimportal.support.TestJwts;
import com.cimportal.user.UserInfo;
import com.cimportal.user.UserInfoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@AutoConfigureMockMvc
class EnumAdminControllerTest extends MariaDbIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired TestJwts jwts;
    @Autowired UserInfoRepository users;
    @Autowired EnumValueRepository enums;

    @BeforeEach
    void seedUsers() {
        users.deleteAll(); enums.deleteAll();
        users.save(new UserInfo("ADMIN1", "管理员", "Admin", "IT", "PORTAL_ADMIN", null, true, Instant.now()));
        users.save(new UserInfo("OP1", "操作员", "Op", "FAB1-PROD", "OPERATOR", null, true, Instant.now()));
    }

    @Test
    void adminCreatesEnum_thenDuplicateConflicts() throws Exception {
        String admin = "Bearer " + jwts.bearerFor("ADMIN1");
        String body = "{\"code\":\"FAB1-PROD\",\"labelZh\":\"一厂生产\",\"labelEn\":\"FAB1\",\"sortOrder\":10}";

        mvc.perform(post("/api/admin/enums/DEPARTMENT").header("Authorization", admin)
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.code").value("FAB1-PROD"));

        mvc.perform(post("/api/admin/enums/DEPARTMENT").header("Authorization", admin)
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("DUPLICATE_CODE"));
    }

    @Test
    void nonAdminForbidden_anonymousUnauthorized() throws Exception {
        String op = "Bearer " + jwts.bearerFor("OP1");
        String body = "{\"code\":\"X\",\"labelZh\":\"x\",\"labelEn\":\"x\",\"sortOrder\":0}";

        mvc.perform(post("/api/admin/enums/ROLE").header("Authorization", op)
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isForbidden());

        mvc.perform(post("/api/admin/enums/ROLE")
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isUnauthorized());
    }
}
```

- [ ] **Step 5: 运行,确认通过**

Run: `mvn -q -Dtest=EnumAdminControllerTest test`
Expected: PASS。

- [ ] **Step 6: 提交**

```bash
cd /home/shane/Code/cim-portal/cim-portal-server
git add backend/src/main/java/com/cimportal/enumvalue backend/src/test/java/com/cimportal/enumvalue/EnumAdminControllerTest.java
git commit -m "feat(enum): 枚举 CRUD 端点 + 鉴权与冲突测试"
```

---

## Task 11: 标签 CRUD + i18n 字典端点

**Files:**
- Create: `backend/src/main/java/com/cimportal/label/dto/LabelResponse.java`
- Create: `backend/src/main/java/com/cimportal/label/dto/LabelRequest.java`
- Create: `backend/src/main/java/com/cimportal/label/LabelService.java`
- Create: `backend/src/main/java/com/cimportal/label/LabelI18nController.java`
- Create: `backend/src/main/java/com/cimportal/label/LabelAdminController.java`
- Test: `backend/src/test/java/com/cimportal/label/LabelControllerTest.java`

- [ ] **Step 1: 写 DTO**

`dto/LabelResponse.java`:
```java
package com.cimportal.label.dto;

import com.cimportal.label.Label;

public record LabelResponse(Long id, String labelKey, String type, String textZh, String textEn) {
    public static LabelResponse of(Label l) {
        return new LabelResponse(l.getId(), l.getLabelKey(), l.getType(), l.getTextZh(), l.getTextEn());
    }
}
```

`dto/LabelRequest.java`:
```java
package com.cimportal.label.dto;

import jakarta.validation.constraints.NotBlank;

public record LabelRequest(@NotBlank String labelKey, @NotBlank String type,
                           @NotBlank String textZh, @NotBlank String textEn) { }
```

- [ ] **Step 2: 写服务**

`LabelService.java`:
```java
package com.cimportal.label;

import com.cimportal.common.error.ApiException;
import com.cimportal.label.dto.LabelRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class LabelService {
    private final LabelRepository repo;
    public LabelService(LabelRepository repo) { this.repo = repo; }

    public record LabelEntry(String zh, String en, String type) { }

    @Transactional(readOnly = true)
    public Map<String, LabelEntry> i18nMap() {
        Map<String, LabelEntry> map = new LinkedHashMap<>();
        for (Label l : repo.findAllByOrderByLabelKeyAsc())
            map.put(l.getLabelKey(), new LabelEntry(l.getTextZh(), l.getTextEn(), l.getType()));
        return map;
    }

    @Transactional(readOnly = true)
    public List<Label> list(String type) {
        return type == null ? repo.findAllByOrderByLabelKeyAsc() : repo.findByType(type);
    }

    @Transactional
    public Label create(LabelRequest req) {
        if (repo.existsByLabelKey(req.labelKey()))
            throw ApiException.duplicate("labelKey '" + req.labelKey() + "' 已存在");
        return repo.save(new Label(req.labelKey(), req.type(), req.textZh(), req.textEn()));
    }

    @Transactional
    public Label update(Long id, LabelRequest req) {
        Label l = repo.findById(id).orElseThrow(() -> ApiException.notFound("标签"));
        l.setType(req.type()); l.setTextZh(req.textZh()); l.setTextEn(req.textEn());
        return l;   // labelKey 不可变
    }

    @Transactional
    public void delete(Long id) {
        Label l = repo.findById(id).orElseThrow(() -> ApiException.notFound("标签"));
        repo.delete(l);
    }
}
```

- [ ] **Step 3: 写控制器**

`LabelI18nController.java`:
```java
package com.cimportal.label;

import com.cimportal.label.LabelService.LabelEntry;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/i18n")
public class LabelI18nController {
    private final LabelService service;
    public LabelI18nController(LabelService service) { this.service = service; }

    @GetMapping("/labels")
    public Map<String, LabelEntry> labels() { return service.i18nMap(); }
}
```

`LabelAdminController.java`:
```java
package com.cimportal.label;

import com.cimportal.label.dto.LabelRequest;
import com.cimportal.label.dto.LabelResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/admin/labels")
public class LabelAdminController {
    private final LabelService service;
    public LabelAdminController(LabelService service) { this.service = service; }

    @GetMapping
    public List<LabelResponse> list(@RequestParam(required = false) String type) {
        return service.list(type).stream().map(LabelResponse::of).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public LabelResponse create(@Valid @RequestBody LabelRequest req) {
        return LabelResponse.of(service.create(req));
    }

    @PutMapping("/{id}")
    public LabelResponse update(@PathVariable Long id, @Valid @RequestBody LabelRequest req) {
        return LabelResponse.of(service.update(id, req));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) { service.delete(id); }
}
```

- [ ] **Step 4: 写失败测试(i18n map 任意用户可读;创建需管理员)**

`LabelControllerTest.java`:
```java
package com.cimportal.label;

import com.cimportal.support.MariaDbIntegrationTest;
import com.cimportal.support.TestJwts;
import com.cimportal.user.UserInfo;
import com.cimportal.user.UserInfoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@AutoConfigureMockMvc
class LabelControllerTest extends MariaDbIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired TestJwts jwts;
    @Autowired UserInfoRepository users;
    @Autowired LabelRepository labels;

    @BeforeEach
    void seed() {
        users.deleteAll(); labels.deleteAll();
        users.save(new UserInfo("ADMIN1", "管理员", "Admin", "IT", "PORTAL_ADMIN", null, true, Instant.now()));
        users.save(new UserInfo("OP1", "操作员", "Op", "FAB1-PROD", "OPERATOR", null, true, Instant.now()));
        labels.save(new Label("portal.title", "SYSTEM_NAME", "门户", "Portal"));
    }

    @Test
    void anyAuthedUserReadsI18nMap() throws Exception {
        mvc.perform(get("/api/i18n/labels").header("Authorization", "Bearer " + jwts.bearerFor("OP1")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$['portal.title'].zh").value("门户"))
            .andExpect(jsonPath("$['portal.title'].type").value("SYSTEM_NAME"));
    }

    @Test
    void onlyAdminCreates() throws Exception {
        String body = "{\"labelKey\":\"nav.admin\",\"type\":\"UI_TEXT\",\"textZh\":\"管理\",\"textEn\":\"Admin\"}";
        mvc.perform(post("/api/admin/labels").header("Authorization", "Bearer " + jwts.bearerFor("OP1"))
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isForbidden());
        mvc.perform(post("/api/admin/labels").header("Authorization", "Bearer " + jwts.bearerFor("ADMIN1"))
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isCreated());
    }
}
```

- [ ] **Step 5: 运行,确认通过**

Run: `mvn -q -Dtest=LabelControllerTest test`
Expected: PASS。

- [ ] **Step 6: 提交**

```bash
cd /home/shane/Code/cim-portal/cim-portal-server
git add backend/src/main/java/com/cimportal/label backend/src/test/java/com/cimportal/label/LabelControllerTest.java
git commit -m "feat(label): 标签 CRUD 与 i18n 字典端点"
```

---

## Task 12: 链接 CRUD + 白名单授权

**Files:**
- Create: `backend/src/main/java/com/cimportal/link/dto/LinkRequest.java`
- Create: `backend/src/main/java/com/cimportal/link/dto/LinkResponse.java`
- Create: `backend/src/main/java/com/cimportal/link/dto/GrantRequest.java`
- Create: `backend/src/main/java/com/cimportal/link/dto/GrantResponse.java`
- Create: `backend/src/main/java/com/cimportal/link/dto/GrantsReplaceRequest.java`
- Create: `backend/src/main/java/com/cimportal/link/LinkService.java`
- Create: `backend/src/main/java/com/cimportal/link/LinkAdminController.java`
- Test: `backend/src/test/java/com/cimportal/link/LinkAdminControllerTest.java`

- [ ] **Step 1: 写 DTO**

`dto/LinkRequest.java`:
```java
package com.cimportal.link.dto;

import jakarta.validation.constraints.NotBlank;

public record LinkRequest(
    @NotBlank String code, @NotBlank String nameZh, @NotBlank String nameEn,
    @NotBlank String url, @NotBlank String icon,
    @NotBlank String categoryCode, @NotBlank String statusCode,
    int sortOrder, Boolean openInNewTab
) {
    public boolean openInNewTabOrDefault() { return openInNewTab == null || openInNewTab; }
}
```

`dto/GrantResponse.java`:
```java
package com.cimportal.link.dto;

import com.cimportal.link.GrantType;
import com.cimportal.link.LinkAccessGrant;

public record GrantResponse(Long id, Long linkId, GrantType grantType, String grantCode) {
    public static GrantResponse of(LinkAccessGrant g) {
        return new GrantResponse(g.getId(), g.getLinkId(), g.getGrantType(), g.getGrantCode());
    }
}
```

`dto/LinkResponse.java`:
```java
package com.cimportal.link.dto;

import com.cimportal.link.Link;
import java.time.Instant;
import java.util.List;

public record LinkResponse(
    Long id, String code, String nameZh, String nameEn, String url, String icon,
    String categoryCode, String statusCode, int sortOrder, boolean openInNewTab,
    List<GrantResponse> grants, Instant createdAt, Instant updatedAt
) {
    public static LinkResponse of(Link l, List<GrantResponse> grants) {
        return new LinkResponse(l.getId(), l.getCode(), l.getNameZh(), l.getNameEn(), l.getUrl(),
            l.getIcon(), l.getCategoryCode(), l.getStatusCode(), l.getSortOrder(),
            l.isOpenInNewTab(), grants, l.getCreatedAt(), l.getUpdatedAt());
    }
}
```

`dto/GrantRequest.java`:
```java
package com.cimportal.link.dto;

import com.cimportal.link.GrantType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record GrantRequest(@NotNull GrantType grantType, @NotBlank String grantCode) { }
```

`dto/GrantsReplaceRequest.java`:
```java
package com.cimportal.link.dto;

import jakarta.validation.Valid;
import java.util.List;

public record GrantsReplaceRequest(@Valid List<GrantRequest> grants) {
    public List<GrantRequest> safeGrants() { return grants == null ? List.of() : grants; }
}
```

- [ ] **Step 2: 写服务**

`LinkService.java`:
```java
package com.cimportal.link;

import com.cimportal.common.error.ApiException;
import com.cimportal.enumvalue.EnumCategory;
import com.cimportal.enumvalue.EnumValueRepository;
import com.cimportal.link.dto.GrantRequest;
import com.cimportal.link.dto.LinkRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class LinkService {
    private final LinkRepository links;
    private final LinkAccessGrantRepository grants;
    private final EnumValueRepository enums;

    public LinkService(LinkRepository links, LinkAccessGrantRepository grants, EnumValueRepository enums) {
        this.links = links; this.grants = grants; this.enums = enums;
    }

    @Transactional(readOnly = true)
    public List<Link> listAll() { return links.findAllByOrderBySortOrderAscIdAsc(); }

    @Transactional(readOnly = true)
    public Link get(Long id) { return links.findById(id).orElseThrow(() -> ApiException.notFound("链接")); }

    @Transactional(readOnly = true)
    public List<LinkAccessGrant> grantsOf(Long linkId) {
        get(linkId);
        return grants.findByLinkId(linkId);
    }

    @Transactional
    public Link create(LinkRequest req) {
        if (links.existsByCode(req.code()))
            throw ApiException.duplicate("链接 code '" + req.code() + "' 已存在");
        requireEnum(EnumCategory.LINK_CATEGORY, req.categoryCode());
        requireEnum(EnumCategory.LINK_STATUS, req.statusCode());
        Link l = new Link();
        apply(l, req);
        return links.save(l);
    }

    @Transactional
    public Link update(Long id, LinkRequest req) {
        Link l = get(id);
        if (!l.getCode().equals(req.code()) && links.existsByCode(req.code()))
            throw ApiException.duplicate("链接 code '" + req.code() + "' 已存在");
        requireEnum(EnumCategory.LINK_CATEGORY, req.categoryCode());
        requireEnum(EnumCategory.LINK_STATUS, req.statusCode());
        apply(l, req);
        return l;
    }

    @Transactional
    public void delete(Long id) { links.delete(get(id)); }   // grant 由外键级联删除

    @Transactional
    public List<LinkAccessGrant> replaceGrants(Long linkId, List<GrantRequest> reqs) {
        get(linkId);
        for (GrantRequest g : reqs) requireGrantCode(g);
        grants.deleteByLinkId(linkId);
        grants.flush();
        for (GrantRequest g : reqs)
            grants.save(new LinkAccessGrant(linkId, g.grantType(), g.grantCode()));
        return grants.findByLinkId(linkId);
    }

    @Transactional
    public LinkAccessGrant addGrant(Long linkId, GrantRequest req) {
        get(linkId);
        requireGrantCode(req);
        boolean dup = grants.findByLinkId(linkId).stream()
            .anyMatch(g -> g.getGrantType() == req.grantType() && g.getGrantCode().equals(req.grantCode()));
        if (dup) throw ApiException.duplicate("该授权已存在");
        return grants.save(new LinkAccessGrant(linkId, req.grantType(), req.grantCode()));
    }

    @Transactional
    public void deleteGrant(Long linkId, Long grantId) {
        LinkAccessGrant g = grants.findById(grantId)
            .filter(x -> x.getLinkId().equals(linkId))
            .orElseThrow(() -> ApiException.notFound("授权"));
        grants.delete(g);
    }

    private void apply(Link l, LinkRequest req) {
        l.setCode(req.code()); l.setNameZh(req.nameZh()); l.setNameEn(req.nameEn());
        l.setUrl(req.url()); l.setIcon(req.icon());
        l.setCategoryCode(req.categoryCode()); l.setStatusCode(req.statusCode());
        l.setSortOrder(req.sortOrder()); l.setOpenInNewTab(req.openInNewTabOrDefault());
    }

    private void requireEnum(EnumCategory category, String code) {
        if (enums.findByCategoryAndCode(category, code).isEmpty())
            throw ApiException.badRequest(category + " 不存在枚举值: " + code);
    }

    private void requireGrantCode(GrantRequest g) {
        EnumCategory cat = g.grantType() == GrantType.DEPARTMENT
            ? EnumCategory.DEPARTMENT : EnumCategory.ROLE;
        if (enums.findByCategoryAndCode(cat, g.grantCode()).isEmpty())
            throw ApiException.badRequest(cat + " 不存在枚举值: " + g.grantCode());
    }
}
```

- [ ] **Step 3: 写控制器**

`LinkAdminController.java`:
```java
package com.cimportal.link;

import com.cimportal.link.dto.*;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/links")
public class LinkAdminController {
    private final LinkService service;
    public LinkAdminController(LinkService service) { this.service = service; }

    @GetMapping
    public List<LinkResponse> list() {
        return service.listAll().stream().map(l -> LinkResponse.of(l, List.of())).toList();
    }

    @GetMapping("/{id}")
    public LinkResponse get(@PathVariable Long id) {
        var l = service.get(id);
        var grants = service.grantsOf(id).stream().map(GrantResponse::of).toList();
        return LinkResponse.of(l, grants);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public LinkResponse create(@Valid @RequestBody LinkRequest req) {
        return LinkResponse.of(service.create(req), List.of());
    }

    @PutMapping("/{id}")
    public LinkResponse update(@PathVariable Long id, @Valid @RequestBody LinkRequest req) {
        return LinkResponse.of(service.update(id, req), List.of());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) { service.delete(id); }

    @GetMapping("/{id}/grants")
    public List<GrantResponse> grants(@PathVariable Long id) {
        return service.grantsOf(id).stream().map(GrantResponse::of).toList();
    }

    @PutMapping("/{id}/grants")
    public List<GrantResponse> replaceGrants(@PathVariable Long id, @Valid @RequestBody GrantsReplaceRequest req) {
        return service.replaceGrants(id, req.safeGrants()).stream().map(GrantResponse::of).toList();
    }

    @PostMapping("/{id}/grants")
    @ResponseStatus(HttpStatus.CREATED)
    public GrantResponse addGrant(@PathVariable Long id, @Valid @RequestBody GrantRequest req) {
        return GrantResponse.of(service.addGrant(id, req));
    }

    @DeleteMapping("/{id}/grants/{grantId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteGrant(@PathVariable Long id, @PathVariable Long grantId) {
        service.deleteGrant(id, grantId);
    }
}
```

- [ ] **Step 4: 写失败测试(创建链接 + 全量替换白名单)**

`LinkAdminControllerTest.java`:
```java
package com.cimportal.link;

import com.cimportal.enumvalue.EnumCategory;
import com.cimportal.enumvalue.EnumValue;
import com.cimportal.enumvalue.EnumValueRepository;
import com.cimportal.support.MariaDbIntegrationTest;
import com.cimportal.support.TestJwts;
import com.cimportal.user.UserInfo;
import com.cimportal.user.UserInfoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@AutoConfigureMockMvc
class LinkAdminControllerTest extends MariaDbIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired TestJwts jwts;
    @Autowired UserInfoRepository users;
    @Autowired EnumValueRepository enums;
    @Autowired LinkRepository links;

    String admin;

    @BeforeEach
    void seed() {
        users.deleteAll(); enums.deleteAll(); links.deleteAll();
        users.save(new UserInfo("ADMIN1", "管理员", "Admin", "IT", "PORTAL_ADMIN", null, true, Instant.now()));
        enums.save(new EnumValue(EnumCategory.LINK_CATEGORY, "MES", "制造执行", "MES", 1, true));
        enums.save(new EnumValue(EnumCategory.LINK_STATUS, "ACTIVE", "启用", "Active", 1, true));
        enums.save(new EnumValue(EnumCategory.ROLE, "OPERATOR", "操作员", "Operator", 1, true));
        admin = "Bearer " + jwts.bearerFor("ADMIN1");
    }

    @Test
    void createLinkThenReplaceGrants() throws Exception {
        String linkBody = "{\"code\":\"mes-wip\",\"nameZh\":\"在制品\",\"nameEn\":\"WIP\"," +
            "\"url\":\"https://x\",\"icon\":\"factory\",\"categoryCode\":\"MES\"," +
            "\"statusCode\":\"ACTIVE\",\"sortOrder\":10}";
        String id = mvc.perform(post("/api/admin/links").header("Authorization", admin)
                .contentType(MediaType.APPLICATION_JSON).content(linkBody))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.code").value("mes-wip"))
            .andReturn().getResponse().getContentAsString().replaceAll(".*\"id\":(\\d+).*", "$1");

        String grantsBody = "{\"grants\":[{\"grantType\":\"ROLE\",\"grantCode\":\"OPERATOR\"}]}";
        mvc.perform(put("/api/admin/links/" + id + "/grants").header("Authorization", admin)
                .contentType(MediaType.APPLICATION_JSON).content(grantsBody))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].grantCode").value("OPERATOR"));

        // 清空 → 对所有人可见
        mvc.perform(put("/api/admin/links/" + id + "/grants").header("Authorization", admin)
                .contentType(MediaType.APPLICATION_JSON).content("{\"grants\":[]}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void rejectsUnknownCategory() throws Exception {
        String bad = "{\"code\":\"x\",\"nameZh\":\"x\",\"nameEn\":\"x\",\"url\":\"https://x\"," +
            "\"icon\":\"i\",\"categoryCode\":\"NOPE\",\"statusCode\":\"ACTIVE\",\"sortOrder\":0}";
        mvc.perform(post("/api/admin/links").header("Authorization", admin)
                .contentType(MediaType.APPLICATION_JSON).content(bad))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }
}
```

- [ ] **Step 5: 运行,确认通过**

Run: `mvn -q -Dtest=LinkAdminControllerTest test`
Expected: PASS。

- [ ] **Step 6: 提交**

```bash
cd /home/shane/Code/cim-portal/cim-portal-server
git add backend/src/main/java/com/cimportal/link backend/src/test/java/com/cimportal/link/LinkAdminControllerTest.java
git commit -m "feat(link): 链接 CRUD 与白名单授权(全量替换/增删)"
```

---

## Task 13: 权限解析器(纯函数,单元测试)

**Files:**
- Create: `backend/src/main/java/com/cimportal/portal/PermissionResolver.java`
- Test: `backend/src/test/java/com/cimportal/portal/PermissionResolverTest.java`

- [ ] **Step 1: 写解析器**

`PermissionResolver.java`:
```java
package com.cimportal.portal;

import com.cimportal.link.GrantType;
import com.cimportal.link.LinkAccessGrant;

import java.util.List;

/** 纯函数:给定用户部门/角色与某链接的授权集,判断是否可见。无任何 IO。 */
public final class PermissionResolver {
    private PermissionResolver() { }

    public static boolean isVisible(String departmentCode, String roleCode, List<LinkAccessGrant> grants) {
        if (grants == null || grants.isEmpty()) return true;   // 无授权 = 所有人可见
        for (LinkAccessGrant g : grants) {
            if (g.getGrantType() == GrantType.DEPARTMENT && g.getGrantCode().equals(departmentCode)) return true;
            if (g.getGrantType() == GrantType.ROLE && g.getGrantCode().equals(roleCode)) return true;
        }
        return false;
    }
}
```

- [ ] **Step 2: 写失败测试(覆盖空/部门匹配/角色匹配/不匹配)**

`PermissionResolverTest.java`:
```java
package com.cimportal.portal;

import com.cimportal.link.GrantType;
import com.cimportal.link.LinkAccessGrant;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PermissionResolverTest {

    private LinkAccessGrant grant(GrantType t, String code) {
        return new LinkAccessGrant(1L, t, code);
    }

    @Test
    void emptyGrantsVisibleToEveryone() {
        assertThat(PermissionResolver.isVisible("ANY", "ANY", List.of())).isTrue();
    }

    @Test
    void visibleWhenDepartmentMatches() {
        var grants = List.of(grant(GrantType.DEPARTMENT, "FAB1-PROD"));
        assertThat(PermissionResolver.isVisible("FAB1-PROD", "OPERATOR", grants)).isTrue();
    }

    @Test
    void visibleWhenRoleMatches() {
        var grants = List.of(grant(GrantType.ROLE, "PROCESS_ENGINEER"));
        assertThat(PermissionResolver.isVisible("QA", "PROCESS_ENGINEER", grants)).isTrue();
    }

    @Test
    void hiddenWhenNeitherMatches() {
        var grants = List.of(grant(GrantType.DEPARTMENT, "IT"), grant(GrantType.ROLE, "ADMIN"));
        assertThat(PermissionResolver.isVisible("QA", "OPERATOR", grants)).isFalse();
    }
}
```

- [ ] **Step 3: 运行,确认通过**

Run: `mvn -q -Dtest=PermissionResolverTest test`
Expected: PASS(纯单元测试,无需容器)。

- [ ] **Step 4: 提交**

```bash
cd /home/shane/Code/cim-portal/cim-portal-server
git add backend/src/main/java/com/cimportal/portal/PermissionResolver.java backend/src/test/java/com/cimportal/portal/PermissionResolverTest.java
git commit -m "feat(portal): 权限解析纯函数 + 单元测试"
```

---

## Task 14: HomeService + /api/portal/home + /api/portal/me

**Files:**
- Create: `backend/src/main/java/com/cimportal/portal/dto/HomeLink.java`
- Create: `backend/src/main/java/com/cimportal/portal/dto/HomeCategory.java`
- Create: `backend/src/main/java/com/cimportal/portal/dto/HomeResponse.java`
- Create: `backend/src/main/java/com/cimportal/portal/HomeService.java`
- Create: `backend/src/main/java/com/cimportal/portal/HomeController.java`
- Create: `backend/src/main/java/com/cimportal/auth/MeResponse.java`
- Create: `backend/src/main/java/com/cimportal/auth/MeController.java`
- Test: `backend/src/test/java/com/cimportal/portal/HomeIntegrationTest.java`

- [ ] **Step 1: 写 DTO**

`dto/HomeLink.java`:
```java
package com.cimportal.portal.dto;

public record HomeLink(Long id, String code, String nameZh, String nameEn,
                       String url, String icon, String statusCode, boolean openInNewTab) { }
```

`dto/HomeCategory.java`:
```java
package com.cimportal.portal.dto;

import java.util.List;

public record HomeCategory(String categoryCode, String categoryLabelZh,
                           String categoryLabelEn, List<HomeLink> links) { }
```

`dto/HomeResponse.java`:
```java
package com.cimportal.portal.dto;

import java.util.List;

public record HomeResponse(List<HomeCategory> categories) { }
```

- [ ] **Step 2: 写 HomeService(组装可见链接,按类别分组)**

`HomeService.java`:
```java
package com.cimportal.portal;

import com.cimportal.auth.CurrentUser;
import com.cimportal.enumvalue.EnumCategory;
import com.cimportal.enumvalue.EnumValue;
import com.cimportal.enumvalue.EnumValueRepository;
import com.cimportal.link.Link;
import com.cimportal.link.LinkAccessGrant;
import com.cimportal.link.LinkAccessGrantRepository;
import com.cimportal.link.LinkRepository;
import com.cimportal.portal.dto.HomeCategory;
import com.cimportal.portal.dto.HomeLink;
import com.cimportal.portal.dto.HomeResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class HomeService {
    private final LinkRepository links;
    private final LinkAccessGrantRepository grants;
    private final EnumValueRepository enums;

    public HomeService(LinkRepository links, LinkAccessGrantRepository grants, EnumValueRepository enums) {
        this.links = links; this.grants = grants; this.enums = enums;
    }

    @Transactional(readOnly = true)
    public HomeResponse resolveFor(CurrentUser user) {
        List<Link> all = links.findAllByOrderBySortOrderAscIdAsc();
        if (all.isEmpty()) return new HomeResponse(List.of());

        Map<Long, List<LinkAccessGrant>> grantsByLink = grants
            .findByLinkIdIn(all.stream().map(Link::getId).toList())
            .stream().collect(Collectors.groupingBy(LinkAccessGrant::getLinkId));

        Map<String, EnumValue> categoryLabels = enums
            .findByCategoryOrderBySortOrderAscIdAsc(EnumCategory.LINK_CATEGORY)
            .stream().collect(Collectors.toMap(EnumValue::getCode, e -> e, (a, b) -> a, LinkedHashMap::new));

        // 保持类别枚举的 sortOrder 顺序;未在枚举中的类别 code 追加到末尾
        Map<String, List<HomeLink>> byCategory = new LinkedHashMap<>();
        categoryLabels.keySet().forEach(code -> byCategory.put(code, new ArrayList<>()));

        for (Link l : all) {
            var g = grantsByLink.getOrDefault(l.getId(), List.of());
            if (!PermissionResolver.isVisible(user.departmentCode(), user.roleCode(), g)) continue;
            byCategory.computeIfAbsent(l.getCategoryCode(), k -> new ArrayList<>())
                .add(new HomeLink(l.getId(), l.getCode(), l.getNameZh(), l.getNameEn(),
                    l.getUrl(), l.getIcon(), l.getStatusCode(), l.isOpenInNewTab()));
        }

        List<HomeCategory> categories = new ArrayList<>();
        for (var entry : byCategory.entrySet()) {
            if (entry.getValue().isEmpty()) continue;  // 跳过无可见链接的类别
            EnumValue meta = categoryLabels.get(entry.getKey());
            String zh = meta != null ? meta.getLabelZh() : entry.getKey();
            String en = meta != null ? meta.getLabelEn() : entry.getKey();
            categories.add(new HomeCategory(entry.getKey(), zh, en, entry.getValue()));
        }
        return new HomeResponse(categories);
    }
}
```

- [ ] **Step 3: 写 HomeController 与 MeController**

`HomeController.java`:
```java
package com.cimportal.portal;

import com.cimportal.auth.CurrentUserService;
import com.cimportal.portal.dto.HomeResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/portal")
public class HomeController {
    private final HomeService home;
    private final CurrentUserService currentUser;
    public HomeController(HomeService home, CurrentUserService currentUser) {
        this.home = home; this.currentUser = currentUser;
    }

    @GetMapping("/home")
    public HomeResponse home() { return home.resolveFor(currentUser.require()); }
}
```

`MeResponse.java`:
```java
package com.cimportal.auth;

public record MeResponse(String employeeId, String displayNameZh, String displayNameEn,
                         String departmentCode, String roleCode, boolean isAdmin) { }
```

`MeController.java`:
```java
package com.cimportal.auth;

import com.cimportal.user.UserInfo;
import com.cimportal.user.UserInfoRepository;
import com.cimportal.common.error.ApiException;
import com.cimportal.common.error.ErrorCode;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/portal")
public class MeController {
    private final CurrentUserService currentUser;
    private final UserInfoRepository users;
    public MeController(CurrentUserService currentUser, UserInfoRepository users) {
        this.currentUser = currentUser; this.users = users;
    }

    @GetMapping("/me")
    public MeResponse me() {
        CurrentUser u = currentUser.require();   // 未配置→404, 停用→403
        UserInfo info = users.findById(u.employeeId())
            .orElseThrow(() -> new ApiException(ErrorCode.USER_NOT_PROVISIONED, "用户未配置"));
        return new MeResponse(info.getEmployeeId(), info.getDisplayNameZh(), info.getDisplayNameEn(),
            info.getDepartmentCode(), info.getRoleCode(), u.admin());
    }
}
```

- [ ] **Step 4: 写失败测试(端到端可见性:不同身份看到不同卡片)**

`HomeIntegrationTest.java`:
```java
package com.cimportal.portal;

import com.cimportal.enumvalue.EnumCategory;
import com.cimportal.enumvalue.EnumValue;
import com.cimportal.enumvalue.EnumValueRepository;
import com.cimportal.link.*;
import com.cimportal.support.MariaDbIntegrationTest;
import com.cimportal.support.TestJwts;
import com.cimportal.user.UserInfo;
import com.cimportal.user.UserInfoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@AutoConfigureMockMvc
class HomeIntegrationTest extends MariaDbIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired TestJwts jwts;
    @Autowired UserInfoRepository users;
    @Autowired EnumValueRepository enums;
    @Autowired LinkRepository links;
    @Autowired LinkAccessGrantRepository grants;

    @BeforeEach
    void seed() {
        users.deleteAll(); enums.deleteAll(); links.deleteAll(); grants.deleteAll();
        enums.save(new EnumValue(EnumCategory.LINK_CATEGORY, "MES", "制造执行", "MES", 1, true));
        enums.save(new EnumValue(EnumCategory.LINK_STATUS, "ACTIVE", "启用", "Active", 1, true));

        users.save(new UserInfo("OP1", "操作员", "Op", "FAB1-PROD", "OPERATOR", null, true, Instant.now()));
        users.save(new UserInfo("QA1", "质量", "QA", "QA", "QA_ENGINEER", null, true, Instant.now()));
        users.save(new UserInfo("OFF", "离职", "Gone", "QA", "QA_ENGINEER", null, false, Instant.now()));

        Link open = newLink("public"); links.save(open);            // 无授权 → 所有人
        Link opOnly = newLink("op-only"); links.save(opOnly);
        grants.save(new LinkAccessGrant(opOnly.getId(), GrantType.ROLE, "OPERATOR"));
    }

    @Test
    void operatorSeesBothLinks() throws Exception {
        mvc.perform(get("/api/portal/home").header("Authorization", "Bearer " + jwts.bearerFor("OP1")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.categories[0].links.length()").value(2));
    }

    @Test
    void qaSeesOnlyPublicLink() throws Exception {
        mvc.perform(get("/api/portal/home").header("Authorization", "Bearer " + jwts.bearerFor("QA1")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.categories[0].links.length()").value(1))
            .andExpect(jsonPath("$.categories[0].links[0].code").value("public"));
    }

    @Test
    void inactiveUserForbidden() throws Exception {
        mvc.perform(get("/api/portal/home").header("Authorization", "Bearer " + jwts.bearerFor("OFF")))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("USER_INACTIVE"));
    }

    @Test
    void unprovisionedUserNotFoundOnMe() throws Exception {
        mvc.perform(get("/api/portal/me").header("Authorization", "Bearer " + jwts.bearerFor("GHOST")))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("USER_NOT_PROVISIONED"));
    }

    private Link newLink(String code) {
        Link l = new Link();
        l.setCode(code); l.setNameZh("名"); l.setNameEn("name");
        l.setUrl("https://x"); l.setIcon("factory");
        l.setCategoryCode("MES"); l.setStatusCode("ACTIVE");
        l.setSortOrder(1); l.setOpenInNewTab(true);
        return l;
    }
}
```

- [ ] **Step 5: 运行,确认通过**

Run: `mvn -q -Dtest=HomeIntegrationTest test`
Expected: PASS。

- [ ] **Step 6: 提交**

```bash
cd /home/shane/Code/cim-portal/cim-portal-server
git add backend/src/main/java/com/cimportal/portal backend/src/main/java/com/cimportal/auth/MeResponse.java backend/src/main/java/com/cimportal/auth/MeController.java backend/src/test/java/com/cimportal/portal/HomeIntegrationTest.java
git commit -m "feat(portal): /portal/home 权限解析端点 + /portal/me"
```

---

## Task 15: 用户只读端点(/api/admin/users)

**Files:**
- Create: `backend/src/main/java/com/cimportal/user/UserInfoResponse.java`
- Create: `backend/src/main/java/com/cimportal/user/UserAdminController.java`
- Test: `backend/src/test/java/com/cimportal/user/UserAdminControllerTest.java`

- [ ] **Step 1: 写响应 DTO 与控制器**

`UserInfoResponse.java`:
```java
package com.cimportal.user;

import java.time.Instant;

public record UserInfoResponse(String employeeId, String displayNameZh, String displayNameEn,
                               String departmentCode, String roleCode, String email,
                               boolean active, Instant syncedAt) {
    public static UserInfoResponse of(UserInfo u) {
        return new UserInfoResponse(u.getEmployeeId(), u.getDisplayNameZh(), u.getDisplayNameEn(),
            u.getDepartmentCode(), u.getRoleCode(), u.getEmail(), u.isActive(), u.getSyncedAt());
    }
}
```

`UserAdminController.java`:
```java
package com.cimportal.user;

import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/admin/users")
public class UserAdminController {
    private final UserInfoRepository users;
    public UserAdminController(UserInfoRepository users) { this.users = users; }

    @GetMapping
    public List<UserInfoResponse> list(@RequestParam(required = false) String departmentCode,
                                       @RequestParam(required = false) String roleCode) {
        List<UserInfo> result;
        if (departmentCode != null) result = users.findByDepartmentCode(departmentCode);
        else if (roleCode != null) result = users.findByRoleCode(roleCode);
        else result = users.findAll();
        return result.stream().map(UserInfoResponse::of).toList();
    }
}
```
> 只读:不提供 POST/PUT/DELETE——门户绝不写 user_info。

- [ ] **Step 2: 写失败测试(仅管理员可读)**

`UserAdminControllerTest.java`:
```java
package com.cimportal.user;

import com.cimportal.support.MariaDbIntegrationTest;
import com.cimportal.support.TestJwts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@AutoConfigureMockMvc
class UserAdminControllerTest extends MariaDbIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired TestJwts jwts;
    @Autowired UserInfoRepository users;

    @BeforeEach
    void seed() {
        users.deleteAll();
        users.save(new UserInfo("ADMIN1", "管理员", "Admin", "IT", "PORTAL_ADMIN", null, true, Instant.now()));
        users.save(new UserInfo("OP1", "操作员", "Op", "FAB1-PROD", "OPERATOR", null, true, Instant.now()));
    }

    @Test
    void adminListsUsers() throws Exception {
        mvc.perform(get("/api/admin/users").header("Authorization", "Bearer " + jwts.bearerFor("ADMIN1")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void operatorForbidden() throws Exception {
        mvc.perform(get("/api/admin/users").header("Authorization", "Bearer " + jwts.bearerFor("OP1")))
            .andExpect(status().isForbidden());
    }
}
```

- [ ] **Step 3: 运行,确认通过**

Run: `mvn -q -Dtest=UserAdminControllerTest test`
Expected: PASS。

- [ ] **Step 4: 提交**

```bash
cd /home/shane/Code/cim-portal/cim-portal-server
git add backend/src/main/java/com/cimportal/user/UserInfoResponse.java backend/src/main/java/com/cimportal/user/UserAdminController.java backend/src/test/java/com/cimportal/user/UserAdminControllerTest.java
git commit -m "feat(user): user_info 只读管理端点"
```

---

## Task 16: Dev/UAT 模拟数据种子器

**Files:**
- Create: `backend/src/main/java/com/cimportal/seed/DevDataSeeder.java`
- Test: `backend/src/test/java/com/cimportal/seed/DevDataSeederTest.java`

模拟 `user_info`(外部同步源在 dev/uat 不可用)以及一批演示枚举/链接/标签,使应用开箱可用。幂等:仅当对应表为空时插入。

- [ ] **Step 1: 写种子器**

`DevDataSeeder.java`:
```java
package com.cimportal.seed;

import com.cimportal.enumvalue.EnumCategory;
import com.cimportal.enumvalue.EnumValue;
import com.cimportal.enumvalue.EnumValueRepository;
import com.cimportal.label.Label;
import com.cimportal.label.LabelRepository;
import com.cimportal.link.*;
import com.cimportal.user.UserInfo;
import com.cimportal.user.UserInfoRepository;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.ApplicationArguments;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Component
@Profile({"dev", "uat"})
public class DevDataSeeder implements ApplicationRunner {

    private final EnumValueRepository enums;
    private final LinkRepository links;
    private final LinkAccessGrantRepository grants;
    private final LabelRepository labels;
    private final UserInfoRepository users;

    public DevDataSeeder(EnumValueRepository enums, LinkRepository links,
                         LinkAccessGrantRepository grants, LabelRepository labels,
                         UserInfoRepository users) {
        this.enums = enums; this.links = links; this.grants = grants;
        this.labels = labels; this.users = users;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (enums.count() == 0) seedEnums();
        if (users.count() == 0) seedUsers();
        if (labels.count() == 0) seedLabels();
        if (links.count() == 0) seedLinks();
    }

    private void seedEnums() {
        enums.save(new EnumValue(EnumCategory.DEPARTMENT, "FAB1-PROD", "一厂生产", "FAB1 Production", 10, true));
        enums.save(new EnumValue(EnumCategory.DEPARTMENT, "QA", "质量", "Quality", 20, true));
        enums.save(new EnumValue(EnumCategory.DEPARTMENT, "IT", "信息技术", "IT", 30, true));
        enums.save(new EnumValue(EnumCategory.ROLE, "OPERATOR", "操作员", "Operator", 10, true));
        enums.save(new EnumValue(EnumCategory.ROLE, "PROCESS_ENGINEER", "工艺工程师", "Process Engineer", 20, true));
        enums.save(new EnumValue(EnumCategory.ROLE, "QA_ENGINEER", "质量工程师", "QA Engineer", 30, true));
        enums.save(new EnumValue(EnumCategory.ROLE, "PORTAL_ADMIN", "门户管理员", "Portal Admin", 40, true));
        enums.save(new EnumValue(EnumCategory.LINK_CATEGORY, "MES", "制造执行", "MES", 10, true));
        enums.save(new EnumValue(EnumCategory.LINK_CATEGORY, "QUALITY", "质量", "Quality", 20, true));
        enums.save(new EnumValue(EnumCategory.LINK_STATUS, "ACTIVE", "启用", "Active", 10, true));
        enums.save(new EnumValue(EnumCategory.LINK_STATUS, "MAINTENANCE", "维护中", "Maintenance", 20, true));
    }

    private void seedUsers() {
        Instant now = Instant.now();
        users.save(new UserInfo("OP1", "欧阳操作", "Olivia Operator", "FAB1-PROD", "OPERATOR", "op1@example.com", true, now));
        users.save(new UserInfo("ENG1", "伊森工程", "Ethan Engineer", "FAB1-PROD", "PROCESS_ENGINEER", "eng1@example.com", true, now));
        users.save(new UserInfo("QA1", "全权质量", "Quinn Quality", "QA", "QA_ENGINEER", "qa1@example.com", true, now));
        users.save(new UserInfo("ADMIN1", "亚当管理", "Adam Admin", "IT", "PORTAL_ADMIN", "admin1@example.com", true, now));
    }

    private void seedLabels() {
        labels.save(new Label("portal.title", "SYSTEM_NAME", "CIMS 统一门户", "CIMS Portal"));
        labels.save(new Label("nav.dashboard", "UI_TEXT", "仪表盘", "Dashboard"));
        labels.save(new Label("nav.admin", "UI_TEXT", "管理", "Admin"));
    }

    private void seedLinks() {
        Link wip = save("mes-wip", "在制品管理", "WIP Management", "https://mes.example.com/wip", "factory", "MES", "ACTIVE", 10);
        grants.save(new LinkAccessGrant(wip.getId(), GrantType.DEPARTMENT, "FAB1-PROD"));
        Link spc = save("qa-spc", "SPC 分析", "SPC Analysis", "https://spc.example.com", "line-chart", "QUALITY", "ACTIVE", 20);
        grants.save(new LinkAccessGrant(spc.getId(), GrantType.ROLE, "QA_ENGINEER"));
        save("docs", "帮助文档", "Docs", "https://docs.example.com", "book", "MES", "ACTIVE", 30); // 无授权 → 所有人
    }

    private Link save(String code, String zh, String en, String url, String icon,
                      String cat, String status, int sort) {
        Link l = new Link();
        l.setCode(code); l.setNameZh(zh); l.setNameEn(en); l.setUrl(url); l.setIcon(icon);
        l.setCategoryCode(cat); l.setStatusCode(status); l.setSortOrder(sort); l.setOpenInNewTab(true);
        return links.save(l);
    }
}
```

- [ ] **Step 2: 写测试(dev profile 下种子器把空库填充)**

`DevDataSeederTest.java`:
```java
package com.cimportal.seed;

import com.cimportal.enumvalue.EnumValueRepository;
import com.cimportal.support.MariaDbIntegrationTest;
import com.cimportal.user.UserInfoRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/** 用 dev profile 启动一次,让 ApplicationRunner 种子器执行。 */
@SpringBootTest
@ActiveProfiles("dev-seed-test")
class DevDataSeederTest extends MariaDbIntegrationTest {
    @Autowired UserInfoRepository users;
    @Autowired EnumValueRepository enums;

    @Test
    void seederPopulatesEmptyDatabase() {
        // 基类已用 Testcontainers MariaDB;DevDataSeeder 在 dev/uat 激活。
        // 此处用专门 profile 'dev-seed-test'(在 application.yml 复用 dev 的 app.security.mode + 激活种子)。
        assertThat(users.count()).isGreaterThanOrEqualTo(4);
        assertThat(enums.count()).isGreaterThanOrEqualTo(10);
    }
}
```

补充:在 `application.yml` 追加一个用于测试的种子 profile(继承 test 安全模式,并让 `@Profile({"dev","uat"})` 的种子器生效——做法是给 DevDataSeeder 的 `@Profile` 增加 `"dev-seed-test"`):

将 `DevDataSeeder` 的注解改为:
```java
@Profile({"dev", "uat", "dev-seed-test"})
```
并在 `application.yml` 追加:
```yaml
---
spring:
  config:
    activate:
      on-profile: dev-seed-test
app:
  security:
    mode: dev-jwt
```
> 注意:`MariaDbIntegrationTest` 用 `@ActiveProfiles("test")`;本测试类用 `@ActiveProfiles("dev-seed-test")` 覆盖,需要在该基类中允许子类覆盖 profile——做法是本测试**不**继承基类的 `@ActiveProfiles`,而是直接复制基类的 `@DynamicPropertySource` 容器配置。为避免重复,把容器装配抽到 `MariaDbIntegrationTest` 的静态块(已如此),子类仅用自身的 `@SpringBootTest @ActiveProfiles("dev-seed-test") @Testcontainers` 并复用静态容器。若实现时发现 profile 叠加复杂,可改为在测试内**手动调用** `new DevDataSeeder(...).run(null)` 做断言,等效且更简单。

- [ ] **Step 3: 运行,确认通过**

Run: `mvn -q -Dtest=DevDataSeederTest test`
Expected: PASS。(若 profile 叠加困难,按 Step 2 注记改为手动调用种子器后重跑。)

- [ ] **Step 4: 提交**

```bash
cd /home/shane/Code/cim-portal/cim-portal-server
git add backend/src/main/java/com/cimportal/seed backend/src/test/java/com/cimportal/seed backend/src/main/resources/application.yml
git commit -m "feat(seed): Dev/UAT 模拟 user_info 与演示数据种子器"
```

---

## Task 17: springdoc OpenAPI 配置

**Files:**
- Create: `backend/src/main/java/com/cimportal/common/config/OpenApiConfig.java`
- Test: `backend/src/test/java/com/cimportal/OpenApiDocsTest.java`

- [ ] **Step 1: 写 OpenAPI 配置(标题/版本/Bearer 安全方案)**

`OpenApiConfig.java`:
```java
package com.cimportal.common.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {
    @Bean
    OpenAPI cimPortalOpenApi() {
        final String scheme = "bearer-jwt";
        return new OpenAPI()
            .info(new Info().title("CIMS 门户 API").version("v1")
                .description("CIMS 门户后端 REST API。契约见 docs/api/api-reference.md。"))
            .components(new Components().addSecuritySchemes(scheme,
                new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")))
            .addSecurityItem(new SecurityRequirement().addList(scheme));
    }
}
```

- [ ] **Step 2: 写测试(OpenAPI JSON 可访问且含我们的路径)**

`OpenApiDocsTest.java`:
```java
package com.cimportal;

import com.cimportal.support.MariaDbIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@AutoConfigureMockMvc
class OpenApiDocsTest extends MariaDbIntegrationTest {
    @Autowired MockMvc mvc;

    @Test
    void apiDocsExposeOurEndpoints() throws Exception {
        mvc.perform(get("/v3/api-docs"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.paths['/api/portal/home']").exists())
            .andExpect(jsonPath("$.paths['/api/admin/links']").exists());
    }
}
```

- [ ] **Step 3: 运行,确认通过**

Run: `mvn -q -Dtest=OpenApiDocsTest test`
Expected: PASS。`/v3/api-docs` 与 `/swagger-ui.html` 在 SecurityConfig 中已放行。

- [ ] **Step 4: 提交**

```bash
cd /home/shane/Code/cim-portal/cim-portal-server
git add backend/src/main/java/com/cimportal/common/config/OpenApiConfig.java backend/src/test/java/com/cimportal/OpenApiDocsTest.java
git commit -m "feat(docs): springdoc OpenAPI(Bearer 安全方案)"
```

---

## Task 18: 全量回归 + 本地 dev 运行手册

**Files:**
- Create: `backend/README.md`

- [ ] **Step 1: 跑全部测试**

Run:
```bash
cd /home/shane/Code/cim-portal/cim-portal-server/backend
mvn -q test
```
Expected: 全部 PASS(含 Oracle 迁移、MariaDB 集成、权限解析、各控制器、OpenAPI)。

- [ ] **Step 2: 准备本地 dev 数据库(MariaDB)**

Run(本机已装 MariaDB 时):
```bash
mariadb -u root -p -e "CREATE DATABASE IF NOT EXISTS cim_portal CHARACTER SET utf8mb4; \
CREATE USER IF NOT EXISTS 'cim_portal'@'127.0.0.1' IDENTIFIED BY 'cim_portal'; \
GRANT ALL PRIVILEGES ON cim_portal.* TO 'cim_portal'@'127.0.0.1'; FLUSH PRIVILEGES;"
```

- [ ] **Step 3: 打包并以 dev profile 启动**

Run:
```bash
cd /home/shane/Code/cim-portal/cim-portal-server/backend
mvn -q -DskipTests package
SPRING_PROFILES_ACTIVE=dev java -jar target/portal.jar
```
Expected:应用启动,Flyway 应用 `mariadb/V1`,DevDataSeeder 填充演示数据。

- [ ] **Step 4: 手动冒烟(换 dev 令牌后调用受保护端点)**

在另一个终端:
```bash
TOKEN=$(curl -s "http://localhost:8080/dev/token?employeeId=ADMIN1" | sed -E 's/.*"access_token":"([^"]+)".*/\1/')
curl -s -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/portal/me
curl -s -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/portal/home
```
Expected:`/me` 返回 ADMIN1 身份(isAdmin=true);`/home` 返回演示链接(管理员部门 IT,看到无授权的 "docs",以及按其他授权匹配的卡片)。再用 `employeeId=OP1` 重复,观察可见卡片不同。

- [ ] **Step 5: 写后端 README**

`backend/README.md`:
```markdown
# CIMS 门户后端

Spring Boot 3 模块化单体。详见 `../docs/superpowers/specs/2026-06-05-cim-portal-design.md`
与 API 契约 `../docs/api/api-reference.md`。

## 运行(dev / MariaDB)
1. 建库:见本仓库实现计划 Task 18 Step 2。
2. `SPRING_PROFILES_ACTIVE=dev mvn spring-boot:run`(或打包后 `java -jar target/portal.jar`)。
3. 取 mock 令牌:`GET http://localhost:8080/dev/token?employeeId=ADMIN1`。
4. Swagger UI:http://localhost:8080/swagger-ui.html

## 测试
`mvn test` —— 含 Testcontainers(需 Docker):MariaDB 集成测试 + Oracle 迁移验证。

## Profile
- `dev`:MariaDB + 进程内 mock OIDC + 演示数据种子。
- `uat` / `prod`:Oracle + 真实 OIDC(`OIDC_ISSUER_URI`、`DB_URL/DB_USER/DB_PASSWORD`)。
  uat 同样跑数据种子(外部同步源不可用)。
```

- [ ] **Step 6: 提交**

```bash
cd /home/shane/Code/cim-portal/cim-portal-server
git add backend/README.md
git commit -m "docs: 后端 README 与本地运行手册"
```

---

## 自检清单(Self-Review)

**规格覆盖:**
- SSO(可插拔 OIDC,dev mock)→ Task 9。
- 双语:枚举/链接/标签内联 zh/en + i18n 字典端点 → Task 5/7/10/11/14。
- 卡片仪表盘数据(分组/排序/可见性)→ Task 12/13/14。
- 灵活访问控制(每链接白名单 match-ANY,空=所有人)→ Task 12/13/14。
- 标签系统(zh/en + type,前端可编辑)→ Task 7/11。
- 枚举 CRUD(四类别)→ Task 5/10。
- user_info 只读 + dev/uat 模拟 → Task 7/15/16。
- 三环境(dev=MariaDB / uat,prod=Oracle)→ Task 2/3/4 + profile。
- 模块化分包 → 全程 `common/auth/user/enumvalue/label/link/portal/seed`。
- OpenAPI 文档 → Task 17。
- (Tauri 桌面、VitePress 文档站、前端 SPA 为独立子项目计划,不在本计划。)

**类型一致性核对:** `CurrentUser`、`EnumCategory`、`GrantType`、各 DTO 在定义后于后续任务中按相同签名引用;仓储方法名(`findByCategoryAndActiveTrueOrderBySortOrderAscIdAsc`、`findByLinkIdIn`、`deleteByLinkId` 等)在 service 中一致使用。

**占位符扫描:** 无 TODO/TBD;每个代码步骤含完整可编译代码。Task 16 对 profile 叠加给出了「手动调用种子器」的明确备选实现路径(非占位)。

**已知实现注记(非阻塞):** 枚举删除的「IN_USE 跨表引用检查」当前为基础实现(依赖外键/建议停用);如需严格 409,可在 Task 12 之后向 `EnumValueService.delete` 注入 link/grant/user 仓储做引用计数,再补一条测试。
