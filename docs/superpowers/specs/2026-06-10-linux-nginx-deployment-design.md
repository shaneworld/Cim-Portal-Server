# 生产部署(Linux + Nginx + systemd)设计

**日期:** 2026-06-10
**状态:** 设计已确认(用户批准),进入实现计划
**背景:** 将 AP1 IT CIM Portal 部署到生产:Vue SPA(`cim-portal-client`,Vite→静态 dist)+ Spring Boot 3.3 无状态资源服务器(`cim-portal-server`,JDK 21,`portal.jar`)+ 外部 Oracle + 公司 SSO(OIDC)。目标平台:**Linux 裸机主机 + Nginx(本机终止 TLS)+ systemd**,不使用容器。后端仓库已扁平化(`pom.xml`/`src` 在仓库根,无 `backend/`)。

## 1. 关键决策(已定)

| 主题 | 选择 |
|---|---|
| 平台/打包 | Linux 主机;后端 `portal.jar` 作为 **systemd 服务**;Nginx 服务 SPA 静态文件并反向代理 `/api` |
| TLS | **Nginx 本机终止 TLS**(443,证书文件;80→443;HSTS) |
| 同源 | SPA 与 API 经同一 Nginx 同源 → **无需 CORS**;后端仅绑 `127.0.0.1:8080` |
| 数据库 | 外部 Oracle(运维提供 url/账号);Flyway 启动时执行 oracle V1–V6 |
| 机密注入 | 主机上 `chmod 600` 的外部 `application-prod.yml`(数据源 + 门户密钥路径),经 `--spring.config.additional-location` 加载;**不入库** |
| 门户签名密钥 | 生产用稳定 RSA 密钥(openssl 生成 PEM 文件),`PortalJwtKeys` 从文件路径加载 |

## 2. 拓扑

```
Browser ──443/TLS──▶ Nginx ──┬─ /              → /var/www/cim-portal (SPA dist, history fallback)
                             └─ /api  /actuator → proxy_pass http://127.0.0.1:8080
                                                       │
                              portal.jar (systemd · profile=prod · bind 127.0.0.1:8080) ── ojdbc11 ──▶ 外部 Oracle
                                                       │
Browser ──443──▶ 公司 SSO(OIDC)  ◀── JWKS ── portal.jar
```

后端不对外暴露端口;SPA/API 同源(无 CORS);公司 SSO 在浏览器侧重定向 + 后端侧 JWKS 校验。

## 3. 后端代码改动(提交至 `cim-portal-server`)

- **移除冗余 issuer-uri**:删去 `uat` + `prod` profile 下 `spring.security.oauth2.resourceserver.jwt.issuer-uri: ${OIDC_ISSUER_URI}`。SSO issuer 现由 `security_setting`(运行时)经 `MultiIssuerJwtDecoder` 解析;静态 issuer-uri 会使启动耦合 IdP 可达性,删除之。`OIDC_ISSUER_URI` 不再是部署必需变量。
- **门户密钥支持文件路径**:`PortalJwtKeys` 增加 `@Value("${app.security.portal-jwt.private-key-location:}")` 与 `public-key-location`。`portalKeyPair()` 优先级:① 两个 `*-location` 均设 → 读对应 PEM 文件内容(复用 `parsePemKeyPair`,`stripPemHeaders` 兼容 `-----BEGIN-----` 头);② 两个内联 `*-key` 均设 → 用内联;③ 否则生成临时密钥并 WARN(仅 dev/test)。生产用 ①。
- 测试:`PortalJwtKeysTest`(或扩展现有)—— 给定 location 指向临时 PEM 文件能加载出可签发/校验 `iss=cim-portal` 的密钥;两者都缺时回退临时密钥。

## 4. 运维机密与运行配置(主机上,不入库)

`/etc/cim-portal/application-prod.yml`(`root:cimportal` `chmod 640`):
```yaml
spring:
  datasource:
    url: "jdbc:oracle:thin:@//ORACLE_HOST:1521/PDB_NAME"
    username: "CIM_PORTAL"
    password: "********"
app:
  security:
    portal-jwt:
      private-key-location: /etc/cim-portal/portal-jwt-private.pem
      public-key-location:  /etc/cim-portal/portal-jwt-public.pem
```
RSA 密钥对用 `gen-portal-jwt-key.sh`(openssl `genpkey` PKCS#8 + `rsa -pubout` X.509)一次性生成,放 `/etc/cim-portal/`,`chmod 600`。Oracle 账号需建表权限(Flyway 建表)。

## 5. systemd 服务

`/etc/systemd/system/cim-portal.service`:专用 `cimportal` 用户运行;`ExecStart=/usr/bin/java -jar /opt/cim-portal/portal.jar --spring.profiles.active=prod --spring.config.additional-location=/etc/cim-portal/`;`Restart=on-failure`;`EnvironmentFile=-/etc/cim-portal/portal.env`(可选 JAVA_OPTS/堆);日志走 journald;`server.address=127.0.0.1`(仅本机)经 application-prod 或参数设定。

## 6. 前端构建与 Nginx

- 构建:`npm ci && npm run build`(`VITE_API_BASE_URL` 留空 → 同源)→ `dist/` 部署到 `/var/www/cim-portal`。
- Nginx 站点 `cim-portal.conf`:443 TLS(`ssl_certificate`/`ssl_certificate_key` 占位路径);`root /var/www/cim-portal`;`location / { try_files $uri $uri/ /index.html; }`(SPA 回退);哈希资源长缓存、`index.html` no-cache;`location /api/ { proxy_pass http://127.0.0.1:8080; }` + `/actuator/health`;`proxy_set_header` X-Forwarded-*/Host;gzip;安全头(HSTS、X-Content-Type-Options、X-Frame-Options、Referrer-Policy);80→443 重定向。

## 7. 上线后 SSO 接入

Flyway V6 种入 `security_setting`(SSO 关、内部初始密码为 dev 默认)。首启后:
1. 用内部账号(工号 + 初始密码)登录,**立即在 `/admin/security` 修改初始密码**。
2. 在公司 IdP 注册回调 `https://<host>/auth/callback` + Web Origin。
3. `/admin/security` 设置 ssoEnabled、issuer URI、client ID、username claim → SSO 即时生效(无需重启)。

## 8. 交付物(位置)

- **后端代码改动** → `cim-portal-server`(profile 清理 + 密钥文件加载 + 测试)。
- **`deploy/` 运维包**(`cim-portal-server/deploy/`,与已迁入的 `architecture/` 同仓库):`cim-portal.service`、`nginx/cim-portal.conf`、`application-prod.example.yml`、`portal.env.example`、`gen-portal-jwt-key.sh`、`build.sh`(打 jar + 构建 dist + 组装发布 tar)、`install.md` 步骤。
- **中文部署运行手册** `deploy/部署指南-Linux.md`(与 `docs/Windows-运行指南.md` 风格一致)。

## 9. 验证(可证 vs 运维步骤)

- 可在此证明:后端 `mvn package` 成功;**prod profile 用生成的密钥 + 外部 `application-prod.yml` 对真实 Oracle 容器启动**(确认无临时密钥 WARN、Flyway V1–V6 应用、无 issuer-uri 耦合);前端 `npm run build` 成功;`nginx -t` 校验生成的配置;本机**同源冒烟**(Nginx 服务 dist + 代理到 jar,内部登录可达)。
- 运维步骤(手册):主机置备、真实证书、公司 IdP 注册、目标服务器落地。

## 10. 范围(YAGNI)

容器/k8s;HA/多节点;CI/CD;日志/监控栈;Oracle 置备;自动签发证书(仅留 certbot 提示)。

## 11. 实现分解(一个 spec/plan)

1. **后端 prod 配置**:删 uat/prod issuer-uri;`PortalJwtKeys` 加 `*-location` 文件加载 + 测试;`mvn test` 绿。
2. **deploy 运维包**:systemd unit、nginx 站点、`application-prod.example.yml`、`portal.env.example`、`gen-portal-jwt-key.sh`、`build.sh`。
3. **手册 + 验证**:`部署指南-Linux.md`;后端 prod+Oracle 容器启动验证 + 前端构建 + `nginx -t` + 同源冒烟。
