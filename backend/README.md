# CIMS 门户后端

Spring Boot 3 模块化单体(按功能分包)。设计见
`../docs/superpowers/specs/2026-06-05-cims-portal-design.md`,API 契约见
`../docs/api/api-reference.md`,实现计划见
`../docs/superpowers/plans/2026-06-05-backend-api.md`。

## 技术栈
Java 17(GraalVM 21.3.3)、Spring Boot 3.3.5、Spring Data JPA、Spring Security
(OAuth2 资源服务器 / JWT)、Flyway 10(MariaDB + Oracle 厂商拆分迁移)、
springdoc-openapi 2.6、JUnit 5 + Testcontainers。

## 模块
`common`(错误契约)、`auth`(OIDC/JWT 安全、当前用户、dev mock 令牌)、
`user`(user_info 只读)、`enumvalue`(枚举 CRUD)、`label`(标签 CRUD + i18n)、
`link`(链接 CRUD + 白名单授权)、`portal`(仪表盘权限解析)、`seed`(Dev/UAT 种子)。

## 运行(dev / MariaDB)
1. 准备本地库:
   ```sql
   CREATE DATABASE IF NOT EXISTS cim_portal CHARACTER SET utf8mb4;
   CREATE USER IF NOT EXISTS 'cim_portal'@'127.0.0.1' IDENTIFIED BY 'cim_portal';
   GRANT ALL PRIVILEGES ON cim_portal.* TO 'cim_portal'@'127.0.0.1';
   FLUSH PRIVILEGES;
   ```
2. 启动(JDK 17 必须):
   ```bash
   export JAVA_HOME=/home/shane/.local/share/mise/installs/java/graalvm-21.3.3+java17
   export PATH=$JAVA_HOME/bin:$PATH
   SPRING_PROFILES_ACTIVE=dev mvn spring-boot:run
   # 或:mvn -DskipTests package && SPRING_PROFILES_ACTIVE=dev java -XX:-UseContainerSupport -jar target/portal.jar
   ```
   > 注:本机 GraalVM 21.3(JDK17)的 cgroup 探测有 NPE bug。`mvn spring-boot:run` 与
   > `mvn test` 已在 pom 中配置 `-XX:-UseContainerSupport`;**直接 `java -jar` 时必须手动加该 JVM 参数**。
3. 取 mock 令牌(仅 dev):`GET http://localhost:8080/dev/token?employeeId=ADMIN1`
   预置身份:OP1 / ENG1 / QA1 / ADMIN1。
4. 调用:`curl -H "Authorization: Bearer <token>" http://localhost:8080/api/portal/home`
5. Swagger UI:http://localhost:8080/swagger-ui.html(prod 已关闭)。
6. 停止:`fuser -k 8080/tcp`(切勿 `pkill -f portal.jar`)。

## 测试
```bash
export JAVA_HOME=/home/shane/.local/share/mise/installs/java/graalvm-21.3.3+java17
export PATH=$JAVA_HOME/bin:$PATH
mvn test
```
需要 Docker(Testcontainers):MariaDB 集成测试 + Oracle 迁移验证。

## Profile / 环境
- `dev`:MariaDB + 进程内 mock OIDC(/dev/token)+ 演示数据种子。
- `uat`:Oracle + 真实 OIDC,运行演示种子(外部 user_info 同步源不可用)。需要 `DB_URL`、`DB_USER`、`DB_PASSWORD`、`OIDC_ISSUER_URI`。
- `prod`:Oracle + 真实 OIDC,springdoc 关闭。需要同上环境变量。

## 按分支自动选择 Profile(无需手动指定)
分支与 profile 的映射:`dev → dev`、`hotfix → uat`、`release → prod`(其他分支默认 `dev`)。

机制:`.githooks/post-checkout` 在切换分支时,把对应 profile 写入
`backend/config/application.yml`(已 gitignore)。Spring Boot 会自动从工作目录的 `./config/`
加载该文件,因此在 `backend/` 下用 `mvn spring-boot:run` 或 `java -jar` 启动时**自动**选中
对应 profile,无需 `SPRING_PROFILES_ACTIVE`。该文件不提交,分支间合并不会冲突;测试用
`@ActiveProfiles("test")`,不受影响。

**克隆后一次性启用**(每个 clone 各做一次,因 `core.hooksPath` 是本地配置):
```bash
sh setup-hooks.sh        # = git config core.hooksPath .githooks,并立即生成当前分支的覆盖文件
```
之后 `git checkout dev|hotfix|release` 即自动切换 profile。

## 安全说明
- JWT 仅做认证(sub=employeeId);部门/角色/管理员身份来自 `user_info` 表(prod 外部同步,dev/uat 模拟),不取自令牌。
- `/api/admin/**` 需要 `PORTAL_ADMIN` 角色;`/api/portal/**` 需要已认证用户,停用/未配置用户由 `CurrentUserService.require()` 拒绝(403/404)。
- 待办:uat/prod 的 JWT 增加 audience 校验(见 application.yml 注释)。
