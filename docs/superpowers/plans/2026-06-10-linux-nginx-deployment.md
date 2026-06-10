# 生产部署(Linux + Nginx + systemd)实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development 或 executing-plans。Steps use checkbox (`- [ ]`).

**Goal:** 让项目可在 Linux 主机以 systemd 跑 `portal.jar`(prod profile)+ Nginx 终止 TLS、服务 SPA dist 并同源反代 `/api`,外部 Oracle,公司 SSO 运行时配置;产出运维包 + 中文手册。

**Architecture:** 后端绑 127.0.0.1:8080;Nginx 443 → 静态 SPA + `proxy_pass /api`;机密在主机外部 `application-prod.yml`;门户 RSA 密钥从 PEM 文件加载。

**Tech Stack:** Spring Boot 3.3.5/JDK 21/Flyway/Oracle;Vue3/Vite;Nginx;systemd;openssl。

**契约/现状(本会话已核对):** spec `docs/superpowers/specs/2026-06-10-linux-nginx-deployment-design.md`。后端仓库**已扁平化**(`cim-portal-server/pom.xml`、`cim-portal-server/src`,无 `backend/`;finalName=`portal`)。`application.yml`:profiles dev/uat/prod/test/oracle-db;uat+prod 含 `spring.security.oauth2.resourceserver.jwt.issuer-uri: ${OIDC_ISSUER_URI}`(冗余,待删);prod 用 `${DB_URL}/${DB_USER}/${DB_PASSWORD}` + flyway oracle + `app.security.mode: oidc`;base `app.cors.allowed-origins: []`。`PortalJwtKeys`:`@Value("${app.security.portal-jwt.private-key:}")`+`public-key`;`portalKeyPair()` 两内联非空→`parsePemKeyPair`,否则生成临时+WARN;`parsePemKeyPair(privatePem,publicPem)` 用 `stripPemHeaders`+base64+PKCS8/X509。前端 `bootstrap.ts` `baseUrl: import.meta.env.VITE_API_BASE_URL ?? ''`(空=同源);`vite.config` 仅 dev 代理。**硬约束:后端 `mvn test` 绿;生产门户密钥稳定(非临时);同源无 CORS;机密不入库;Flyway oracle V1–V6。** 提交:**全部(后端代码 + 运维包 `deploy/` + 手册 + spec/plan)→ `cim-portal-server` 分支 `dev`**(伞目录 `/home/shane/Code/cim-portal` 非 git 仓库;`cim-portal-client` 为同级仓库;跨切面文档统一随后端仓库,与已迁入的 `architecture/` 一致)。提交追加 `Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>`。

---

## Task 1: 后端 prod 配置(cim-portal-server)

**Files:** `cim-portal-server/src/main/resources/application.yml`、`.../java/com/cimportal/auth/PortalJwtKeys.java`、测试

- [ ] **Step 1: Read** 现 `application.yml`、`PortalJwtKeys.java` 全文、现有 `PortalJwtKeys`/auth 测试(若有)。

- [ ] **Step 2: 删冗余 issuer-uri** — 从 `uat` 与 `prod` 两个 profile 文档块中删除整段:
  ```yaml
    security:
      oauth2:
        resourceserver:
          jwt:
            issuer-uri: ${OIDC_ISSUER_URI}
  ```
  保留各自的 `app.security.mode: oidc` 与 datasource/flyway。(SSO issuer 改由 `security_setting` 运行时提供。)

- [ ] **Step 3: `PortalJwtKeys` 支持文件路径** — 加两个字段:
  ```java
  @Value("${app.security.portal-jwt.private-key-location:}") private String privateKeyLocation;
  @Value("${app.security.portal-jwt.public-key-location:}")  private String publicKeyLocation;
  ```
  在 `portalKeyPair()` 起始处优先处理文件路径:
  ```java
  if (!privateKeyLocation.isBlank() && !publicKeyLocation.isBlank()) {
      log.info("Portal JWT: loading RSA key pair from files {} / {}", privateKeyLocation, publicKeyLocation);
      String priv = java.nio.file.Files.readString(java.nio.file.Path.of(privateKeyLocation));
      String pub  = java.nio.file.Files.readString(java.nio.file.Path.of(publicKeyLocation));
      return parsePemKeyPair(priv, pub);
  }
  ```
  其后维持现有「内联非空→parsePemKeyPair」与「否则临时+WARN」分支。`parsePemKeyPair`/`stripPemHeaders` 不变(已兼容 PEM 头)。

- [ ] **Step 4: 测试** — `PortalJwtKeysTest`:用 openssl 或 JDK 生成临时 RSA → 写 PKCS#8 私钥 PEM + X.509 公钥 PEM 到临时文件 → 设 `app.security.portal-jwt.private-key-location/public-key-location` 指向之(用 `@SpringBootTest(properties=...)` 或直接 new + 反射注入 + 调 `portalKeyPair()`);断言能签发并被 `portalJwtDecoder` 校验通过的 `iss=cim-portal` token。可加:两者皆空 → 仍生成临时密钥(不抛错)。

- [ ] **Step 5: 全量** — `cd cim-portal-server && mvn -q test 2>&1 | grep -E 'Tests run:|BUILD' | tail -4` → SUCCESS。

- [ ] **Step 6: Commit**(server dev)`feat(deploy): prod 移除冗余 issuer-uri + 门户签名密钥支持 PEM 文件加载`。

---

## Task 2: deploy 运维包(`cim-portal-server/deploy/`)

**Files(新建):** `deploy/cim-portal.service`、`deploy/nginx/cim-portal.conf`、`deploy/application-prod.example.yml`、`deploy/portal.env.example`、`deploy/gen-portal-jwt-key.sh`、`deploy/build.sh`

- [ ] **Step 1: `gen-portal-jwt-key.sh`**
  ```bash
  #!/usr/bin/env bash
  set -euo pipefail
  OUT="${1:-/etc/cim-portal}"
  install -d -m 700 "$OUT"
  openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out "$OUT/portal-jwt-private.pem"
  openssl rsa -in "$OUT/portal-jwt-private.pem" -pubout -out "$OUT/portal-jwt-public.pem"
  chmod 600 "$OUT/portal-jwt-private.pem"; chmod 644 "$OUT/portal-jwt-public.pem"
  echo "Wrote $OUT/portal-jwt-{private,public}.pem"
  ```

- [ ] **Step 2: `application-prod.example.yml`**
  ```yaml
  # 复制到 /etc/cim-portal/application-prod.yml,填真实值,chmod 640 root:cimportal,勿入库。
  server:
    address: 127.0.0.1   # 仅本机,Nginx 反代
    port: 8080
  spring:
    datasource:
      url: "jdbc:oracle:thin:@//ORACLE_HOST:1521/PDB_NAME"
      username: "CIM_PORTAL"
      password: "CHANGE_ME"
  app:
    security:
      portal-jwt:
        private-key-location: /etc/cim-portal/portal-jwt-private.pem
        public-key-location:  /etc/cim-portal/portal-jwt-public.pem
  ```

- [ ] **Step 3: `portal.env.example`**
  ```bash
  # 复制到 /etc/cim-portal/portal.env(可选);systemd EnvironmentFile 引用。
  JAVA_OPTS=-Xms256m -Xmx512m -XX:+UseG1GC
  ```

- [ ] **Step 4: `cim-portal.service`**
  ```ini
  [Unit]
  Description=AP1 IT CIM Portal API
  After=network-online.target
  Wants=network-online.target

  [Service]
  User=cimportal
  Group=cimportal
  EnvironmentFile=-/etc/cim-portal/portal.env
  ExecStart=/usr/bin/java $JAVA_OPTS -jar /opt/cim-portal/portal.jar \
    --spring.profiles.active=prod \
    --spring.config.additional-location=file:/etc/cim-portal/
  SuccessExitStatus=143
  Restart=on-failure
  RestartSec=5
  NoNewPrivileges=true
  ProtectSystem=full
  ProtectHome=true
  ReadOnlyPaths=/opt/cim-portal
  StandardOutput=journal
  StandardError=journal

  [Install]
  WantedBy=multi-user.target
  ```
  注:`--spring.config.additional-location=file:/etc/cim-portal/` 使 jar 加载该目录下 `application-prod.yml`。

- [ ] **Step 5: `nginx/cim-portal.conf`**
  ```nginx
  server {
    listen 80;
    server_name cim-portal.example.com;          # 改为真实域名
    return 301 https://$host$request_uri;
  }
  server {
    listen 443 ssl http2;
    server_name cim-portal.example.com;           # 改为真实域名
    ssl_certificate     /etc/ssl/cim-portal/fullchain.pem;   # 占位
    ssl_certificate_key /etc/ssl/cim-portal/privkey.pem;     # 占位
    ssl_protocols TLSv1.2 TLSv1.3;
    add_header Strict-Transport-Security "max-age=31536000; includeSubDomains" always;
    add_header X-Content-Type-Options nosniff always;
    add_header X-Frame-Options DENY always;
    add_header Referrer-Policy strict-origin-when-cross-origin always;

    root /var/www/cim-portal;
    index index.html;
    gzip on; gzip_types text/css application/javascript application/json image/svg+xml;

    location /assets/ { expires 1y; add_header Cache-Control "public, immutable"; try_files $uri =404; }
    location = /index.html { add_header Cache-Control "no-cache"; }

    location /api/ {
      proxy_pass http://127.0.0.1:8080;
      proxy_set_header Host $host;
      proxy_set_header X-Real-IP $remote_addr;
      proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
      proxy_set_header X-Forwarded-Proto $scheme;
    }
    location = /actuator/health { proxy_pass http://127.0.0.1:8080; }

    location / { try_files $uri $uri/ /index.html; }   # SPA history fallback
  }
  ```

- [ ] **Step 6: `build.sh`**
  ```bash
  #!/usr/bin/env bash
  # 位于 cim-portal-server/deploy/;cim-portal-client 为同级仓库。产出 <workspace>/dist/release。
  set -euo pipefail
  SERVER="$(cd "$(dirname "$0")/.." && pwd)"     # cim-portal-server
  WS="$(cd "$SERVER/.." && pwd)"                  # 含两仓库的工作目录
  CLIENT="$WS/cim-portal-client"
  REL="$WS/dist/release"; rm -rf "$REL"; mkdir -p "$REL/web"
  echo "== build backend =="
  ( cd "$SERVER" && mvn -q -DskipTests clean package )
  cp "$SERVER/target/portal.jar" "$REL/portal.jar"
  echo "== build frontend =="
  ( cd "$CLIENT" && npm ci && npm run build )      # VITE_API_BASE_URL 留空=同源
  cp -r "$CLIENT/dist/." "$REL/web/"
  cp -r "$SERVER/deploy" "$REL/deploy"
  ( cd "$WS/dist" && tar czf cim-portal-release.tar.gz release )
  echo "Release: $WS/dist/cim-portal-release.tar.gz  (portal.jar + web/ + deploy/)"
  ```
  `chmod +x deploy/*.sh`。

- [ ] **Step 7: Commit**(server dev)`feat(deploy): Linux/Nginx/systemd 运维包(service/nginx/env 模板/密钥脚本/build)`。

---

## Task 3: 手册 + 验证

**Files:** `deploy/部署指南-Linux.md`(新)

- [ ] **Step 1: 写 `部署指南-Linux.md`**(中文,与 `docs/Windows-运行指南.md` 风格一致),含:
  - 前置:Java 21(Temurin)、Node LTS(仅构建机)、Nginx、可访问的 Oracle、域名 + 证书。
  - 构建:在开发/构建机跑 `deploy/build.sh` → `dist/cim-portal-release.tar.gz`。
  - 主机落地:建 `cimportal` 用户;`/opt/cim-portal/portal.jar`;`/var/www/cim-portal/`(web);`gen-portal-jwt-key.sh /etc/cim-portal`;复制并填 `/etc/cim-portal/application-prod.yml`(Oracle url/账号、密钥路径)`chmod 640 root:cimportal`;装 `cim-portal.service` → `systemctl daemon-reload && enable --now cim-portal`;装 nginx 站点(改域名/证书路径)→ `nginx -t && systemctl reload nginx`。
  - 首次健康:`curl -s localhost:8080/actuator/health`;`https://<域名>/api/portal/config`。
  - SSO 接入:内部账号登录(工号 + 初始密码,**立即改密**)→ IdP 注册 `https://<域名>/auth/callback` + Web Origin → `/admin/security` 填 issuer/clientId/usernameClaim 开启 SSO。
  - 升级:重跑 build → 替换 `portal.jar`(`systemctl restart cim-portal`,Flyway 自动迁移)+ 替换 `web/`(`reload nginx`)。
  - 排错:journald 日志(`journalctl -u cim-portal -f`)、临时密钥 WARN = 未配密钥文件、502 = 后端未起、Oracle 连接、Flyway 校验失败。
- [ ] **Step 2: 后端门禁** — `cd cim-portal-server && mvn -q test` → SUCCESS。
- [ ] **Step 3: prod profile + Oracle 容器启动验证** — 起 `gvenzl/oracle-free`;`gen-portal-jwt-key.sh /tmp/cimkeys`;写 `/tmp/cimprod/application-prod.yml`(datasource 指向容器 + 密钥路径 /tmp/cimkeys);`mvn -q -DskipTests package`;`java -jar target/portal.jar --spring.profiles.active=prod --spring.config.additional-location=file:/tmp/cimprod/`(DB_URL 等已在该 yml);确认日志:**无临时密钥 WARN**、`loading RSA key pair from files`、Flyway V1–V6 应用、Started、`/actuator/health` 200;`/api/auth/login` 内部登录拿到 token。完后清理容器。
- [ ] **Step 4: 前端构建** — `cd cim-portal-client && npm run build` → dist 生成。
- [ ] **Step 5: Nginx 配置校验 + 同源冒烟(可选,若本机有 nginx)** — `nginx -t -c <临时含 cim-portal.conf 的配置>` 或 `nginx -t`;本机用临时 nginx 服务 dist + 代理到 8080,`curl https?://localhost/` 得 SPA、`/api/portal/config` 经代理可达。若本机无 nginx,记录为运维步骤并仅做 `nginx -t` 跳过。
- [ ] **Step 6: Commit**(server dev)`docs(deploy): Linux 部署指南 + 验证记录`。

---

## 自检清单(Self-Review)

**规格覆盖(spec §3–§9):** 删 issuer-uri + 密钥文件加载 + 测试 → T1;service/nginx/env 模板/密钥脚本/build → T2;中文手册 + prod×Oracle 启动验证 + 前端构建 + nginx -t + 同源冒烟 → T3。生产稳定密钥(文件加载,无临时)在 T1 + T3§3 复验;同源无 CORS 由 nginx 反代 + `VITE_API_BASE_URL` 空保证。

**占位符扫描:** 无 TBD;service/nginx/yml/脚本均给出完整内容;证书路径/域名/Oracle 连接为运维占位并在手册标注「改为真实值」。

**一致性:** `app.security.portal-jwt.{private,public}-key-location`(PortalJwtKeys ↔ application-prod.example.yml ↔ 密钥脚本输出路径);`--spring.config.additional-location=file:/etc/cim-portal/`(systemd ↔ 手册);后端绑 `127.0.0.1:8080`(application-prod ↔ nginx proxy_pass);finalName `portal.jar`(build.sh ↔ service ExecStart)。**硬约束**(mvn 绿、稳定密钥、无 CORS 同源、机密不入库、Flyway oracle)在 T1/T3 复验。
