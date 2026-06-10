# AP1 IT CIM Portal — Linux 生产部署指南(Nginx + systemd)

本指南把门户部署到一台 **Linux 主机**:后端 `portal.jar` 以 **systemd** 运行,**Nginx 在本机终止 TLS**、对外服务 SPA 静态文件并同源反向代理 `/api`,数据库使用外部 **Oracle**,登录走公司 **SSO(OIDC)** 并保留内部账号回退。

> 同源(SPA 与 API 经同一 Nginx)⇒ **无需 CORS**;后端只绑 `127.0.0.1:8080`,不直接对外。

## 0. 架构
```
浏览器 ─443/TLS─▶ Nginx ─┬─ /              → /var/www/cim-portal(SPA dist,history 回退)
                         └─ /api /actuator → http://127.0.0.1:8080
                                                   │ ojdbc11
                          portal.jar(systemd,profile=prod,绑 127.0.0.1:8080)──▶ 外部 Oracle
                                                   │
浏览器 ─443─▶ 公司 SSO(OIDC) ◀── JWKS ── portal.jar
```

## 1. 前置
- **应用主机(Linux)**:JDK 21(Temurin/Adoptium)、Nginx、`openssl`;一个域名 + 该域名的 TLS 证书(或用 certbot);可访问的 **Oracle**(URL/账号,账号需建表权限——Flyway 启动建表)。
- **构建机**:JDK 21 + Maven、Node.js LTS(`npm`);本仓库 `cim-portal-server` 与同级 `cim-portal-client`。

## 2. 构建发布包(构建机)
```bash
cd cim-portal-server
deploy/build.sh          # 构建后端 jar + 前端 dist + 打包
# 产物:<workspace>/dist/cim-portal-release.tar.gz
#   release/portal.jar  release/web/  release/deploy/
```
前端以 **`VITE_API_BASE_URL` 留空** 构建(同源,经 Nginx 代理 `/api`)。把 `cim-portal-release.tar.gz` 传到应用主机并解压。

## 3. 主机落地(以 root)
```bash
# 3.1 专用用户 + 目录
useradd --system --no-create-home --shell /usr/sbin/nologin cimportal || true
install -d -o cimportal -g cimportal /opt/cim-portal
install -d /var/www/cim-portal
install -d -m 700 /etc/cim-portal

# 3.2 后端 jar + 前端静态文件
install -o cimportal -g cimportal -m 644 release/portal.jar /opt/cim-portal/portal.jar
cp -r release/web/. /var/www/cim-portal/

# 3.3 生成稳定的门户签名密钥(只做一次;务必备份私钥)
release/deploy/gen-portal-jwt-key.sh /etc/cim-portal
chown root:cimportal /etc/cim-portal/portal-jwt-*.pem

# 3.4 运行配置(机密,不入库)
cp release/deploy/application-prod.example.yml /etc/cim-portal/application-prod.yml
#   编辑:填 Oracle url/账号/密码;密钥路径默认即 /etc/cim-portal/portal-jwt-*.pem
chown root:cimportal /etc/cim-portal/application-prod.yml && chmod 640 /etc/cim-portal/application-prod.yml
# (可选)cp release/deploy/portal.env.example /etc/cim-portal/portal.env   # JVM 参数

# 3.5 systemd 服务
cp release/deploy/cim-portal.service /etc/systemd/system/cim-portal.service
systemctl daemon-reload
systemctl enable --now cim-portal
journalctl -u cim-portal -f          # 看启动日志(Ctrl-C 退出)
```
启动日志应出现 `Portal JWT: loading RSA key pair from files ...`(**不应**出现 ephemeral 临时密钥 WARN)与 Flyway `now at version v6`。

```bash
# 3.6 Nginx 站点(改 server_name + 证书路径)
cp release/deploy/nginx/cim-portal.conf /etc/nginx/sites-available/cim-portal.conf
#   编辑:server_name=真实域名;ssl_certificate / ssl_certificate_key=真实证书路径
ln -sf /etc/nginx/sites-available/cim-portal.conf /etc/nginx/sites-enabled/cim-portal.conf
nginx -t && systemctl reload nginx
```

## 4. 首次自检
```bash
curl -s http://127.0.0.1:8080/actuator/health        # {"status":"UP"}
curl -s https://<域名>/api/portal/config              # {"ssoEnabled":false,...}
```
浏览器打开 `https://<域名>/` 应进入登录页。

## 5. 接入公司 SSO(上线后,无需重启)
1. **置备用户**:门户授权依据本地 `user_info`(employee_id/部门/角色/active)。生产不种入示例用户——由 HR/IdP 同步或手工导入(SSO 用户名须等于 `employee_id`)。
2. **首个管理员 + 改密**:用内部账号登录(工号 + 初始密码,初始密码为 `security_setting` 种子默认 `cimp@123`),立刻在 **`/admin/security`** 修改初始密码。
3. **IdP 注册**:在公司 OIDC 注册一个 public client(Authorization Code + PKCE/S256),回调 `https://<域名>/auth/callback`,Web Origin 加 `https://<域名>`。
4. **填 SSO 配置**:`/admin/security` 设置 `启用 SSO`、Issuer URI、Client ID、用户名 Claim(等于工号的 claim,常见 `preferred_username`)→ 保存即生效。后端按该 Issuer 自动拉 JWKS 校验 token。

## 6. 升级
```bash
# 构建机重跑 deploy/build.sh,新包传到主机解压
install -o cimportal -g cimportal -m 644 release/portal.jar /opt/cim-portal/portal.jar
systemctl restart cim-portal        # Flyway 自动迁移到最新版本
cp -r release/web/. /var/www/cim-portal/ && systemctl reload nginx
```

## 7. 排错
| 现象 | 排查 |
|---|---|
| 日志出现 ephemeral 临时密钥 WARN | 未配 `application-prod.yml` 的 `portal-jwt.*-key-location` 或 PEM 文件不可读 → 内部登录 token 重启失效 |
| `502 Bad Gateway` | 后端未起或未在 8080:`systemctl status cim-portal`、`journalctl -u cim-portal -e` |
| 启动报数据库/Flyway | Oracle 连接(url/账号/网络)、账号建表权限;`now at version` 是否到 v6 |
| 登录 401 | 用户不在 `user_info`(未置备)、内部密码错(`/admin/security` 重设)、SSO 未配置 |
| SSO 跳转后报错 | IdP 回调 URI / Web Origin 未注册;`/admin/security` 的 Issuer/ClientId/Claim 是否正确 |
| 刷新子路由 404 | Nginx 缺 `try_files ... /index.html`(SPA 回退) |

## 8. 安全清单
- `application-prod.yml`、`portal-jwt-private.pem` 权限收紧(`640`/`600`),**不入库**,**备份私钥**(丢失则所有内部 token 失效)。
- 仅 Nginx(443)对外;后端绑 `127.0.0.1`。
- 上线后立即修改内部初始密码;按需关闭 `/swagger-ui`(prod 已禁用 api-docs/swagger)。
