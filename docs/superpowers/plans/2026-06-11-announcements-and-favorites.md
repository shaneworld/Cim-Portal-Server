# 公告 + 收藏 实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development(推荐)或 superpowers:executing-plans。步骤用 `- [ ]`。

**Goal:** ① 公告(管理员发布,首页展示,生效窗口 + 置顶 + 级别,前端可忽略);② 收藏(用户收藏可访问链接,首页「我的收藏」区 + 卡片星标)。

**Architecture:** 公告 = `announcement` 表(V9)+ 管理 CRUD + 门户 effective 查询;前端 `AnnouncementsPanel` 按级别着色 + localStorage 忽略。收藏 = `favorite` 表(V10);`HomeLink` 加 `favorite` 标记;`POST/DELETE /api/portal/favorites/{linkId}`(仅可访问);前端星标 + 从 categories 聚合「我的收藏」。

**Tech Stack:** Spring Boot 3.3/JDK21/Oracle/Flyway/JPA/Testcontainers;Vue3/TS/Tailwind v3/vue-i18n/Vitest。

---

## 契约/现状(已核对)
- 提交:后端→`cim-portal-server@dev`,前端→`cim-portal-client@dev`。trailer `Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>`。后端 `mvn test`(Testcontainers oracle-free);前端 `npm run build`(vue-tsc+vite)+ `npm test`(vitest,含 i18n 对齐)。**提交前测试须绿再提交。**
- `HomeService.resolveFor(CurrentUser)` 在 `src/main/java/com/cimportal/portal/HomeService.java`,产 `HomeResponse{categories}`;`HomeLink` 在 `portal/dto/HomeLink.java`(record,字段见 spec)。`CurrentUser(employeeId,departmentCode,roleCode,admin)`;`CurrentUserService` 注入。`PermissionResolver.isVisible(dept,role,Set<String> groupCodes,List<LinkAccessGrant>)`。`HomeService` 已注入 `PermissionGroupMemberRepository.findActiveGroupCodesByEmployeeId(empId)`(权限组功能引入)。
- 前端 `HomeView.vue`:`<HeroPanel/> <GlobalSearch/> <SystemGrid :categories="filtered"/>`;`SystemCard.vue` props `link`(含 `accessible`、`favorite`(新增))。`src/lib/api/portal.ts`(`getHome`、`request`)、`src/lib/api/admin.ts`、`src/lib/api/types.ts`(`HomeResponse/HomeCategory/HomeLink` 类型)。admin 导航 `src/features/admin/AdminLayout.vue`,路由 `src/router/index.ts`。i18n `locales/{zh,en}.ts`。
- **硬约束:** `mvn test` 绿;`OracleMigrationTest` 9(公告后)→10(收藏后);i18n 键对齐;收藏仅可访问(锁定链接 POST→403);现有首页行为不破坏。

---

# 功能 ① 公告

## Task 1.1:阅读现状
- [ ] **Read** `HomeService.java`、`portal/dto/HomeLink.java`、`HomeController.java`、`auth/CurrentUser*.java`、`auth/SecurityConfig.java`(`/api/portal/**` 是否需鉴权——确认非 permitAll,需登录)、一个管理控制器+服务(如 `enumvalue` 或 `group`)作 CRUD 范式、`migration/OracleMigrationTest.java`、`support/{OracleIntegrationTest,TestJwts}.java`;前端 `EnumsAdminView.vue`+`EnumFormModal.vue`、`HomeView.vue`、`lib/api/{portal,admin,types}.ts`、`AdminLayout.vue`、`router/index.ts`、`locales/{zh,en}.ts`。

## Task 1.2:Flyway V9
**Files:** Create `src/main/resources/db/migration/oracle/V9__announcement.sql`(SQL 见 spec ① 数据模型)。
- [ ] 写迁移。
- [ ] `OracleMigrationTest` 断言 `8` → `9`。

## Task 1.3:实体 + 枚举 + 仓库
**Files:** Create `com/cimportal/announcement/{Announcement.java, AnnouncementLevel.java, AnnouncementRepository.java}`
- [ ] `AnnouncementLevel`:`public enum AnnouncementLevel { INFO, MAINTENANCE, OUTAGE }`。
- [ ] `Announcement` 实体:`id`(IDENTITY)、`titleZh/titleEn`(String 255)、`@Lob String bodyZh/bodyEn`、`@Enumerated(EnumType.STRING) @Column(length=16) AnnouncementLevel level`、`boolean pinned`、`Instant startsAt`(nullable)、`Instant endsAt`(nullable)、`boolean active=true`、`Instant createdAt=Instant.now()`。getter/setter + 无参构造。
- [ ] `AnnouncementRepository extends JpaRepository<Announcement,Long>`:
```java
List<Announcement> findAllByOrderByPinnedDescCreatedAtDesc();
```

## Task 1.4:DTO + 服务
**Files:** Create `announcement/dto/{AnnouncementRequest.java, AnnouncementResponse.java}`、`announcement/AnnouncementService.java`
- [ ] `AnnouncementRequest`(record,校验):`@NotBlank titleZh,titleEn,bodyZh,bodyEn`;`@NotNull AnnouncementLevel level`;`Boolean pinned`;`Instant startsAt`;`Instant endsAt`;`Boolean active`。
- [ ] `AnnouncementResponse`(record):`id,titleZh,titleEn,bodyZh,bodyEn,level,pinned,startsAt,endsAt,active,createdAt` + `static of(Announcement)`。
- [ ] `AnnouncementService`:
```java
public List<AnnouncementResponse> listAll(){ return repo.findAllByOrderByPinnedDescCreatedAtDesc().stream().map(AnnouncementResponse::of).toList(); }
public AnnouncementResponse get(Long id){ return AnnouncementResponse.of(require(id)); }
@Transactional public AnnouncementResponse create(AnnouncementRequest r){ Announcement a=new Announcement(); apply(a,r); return AnnouncementResponse.of(repo.save(a)); }
@Transactional public AnnouncementResponse update(Long id, AnnouncementRequest r){ Announcement a=require(id); apply(a,r); return AnnouncementResponse.of(a); }
@Transactional public void delete(Long id){ require(id); repo.deleteById(id); }
public List<AnnouncementResponse> effective(Instant now){
  return repo.findAllByOrderByPinnedDescCreatedAtDesc().stream()
    .filter(a -> a.isActive()
        && (a.getStartsAt()==null || !a.getStartsAt().isAfter(now))
        && (a.getEndsAt()==null   || !a.getEndsAt().isBefore(now)))
    .map(AnnouncementResponse::of).toList();
}
private void apply(Announcement a, AnnouncementRequest r){ a.setTitleZh(r.titleZh()); a.setTitleEn(r.titleEn()); a.setBodyZh(r.bodyZh()); a.setBodyEn(r.bodyEn()); a.setLevel(r.level()); a.setPinned(Boolean.TRUE.equals(r.pinned())); a.setStartsAt(r.startsAt()); a.setEndsAt(r.endsAt()); a.setActive(r.active()==null||r.active()); }
private Announcement require(Long id){ return repo.findById(id).orElseThrow(()->ApiException.notFound("公告不存在")); }
```
> `ApiException.notFound` 若无,用现状等价写法。

## Task 1.5:控制器
**Files:** Create `announcement/AnnouncementAdminController.java`、`announcement/AnnouncementPortalController.java`
- [ ] Admin `@RequestMapping("/api/admin/announcements")`:GET list、`POST`(`@Valid` 201)、`GET/{id}`、`PUT/{id}`、`DELETE/{id}`(204)。
- [ ] Portal `@GetMapping("/api/portal/announcements")` → `service.effective(Instant.now())`。(在 `/api/portal/**`,需登录,非 admin。)

## Task 1.6:后端测试
**Files:** Create `src/test/java/com/cimportal/announcement/AnnouncementControllerTest.java`(extends `OracleIntegrationTest`)
- [ ] admin 创建(201)、列表、改、删(204)用 `TestJwts.bearerFor(<PORTAL_ADMIN>)`。
- [ ] effective:造 4 条 —— active 且无窗口(返回)、`active=false`(不返回)、`startsAt`=未来(不返回)、`endsAt`=过去(不返回);再造一条 `pinned=true` 验证排在最前。GET `/api/portal/announcements` 用普通用户 token。
- [ ] `mvn -q test` 绿(含 `OracleMigrationTest` 9)。
- [ ] **Commit(后端)**:`feat(announce): 公告后端(V9 + 实体/服务/管理&门户 API + 测试)`。

## Task 1.7:前端 API + i18n
**Files:** `src/lib/api/announcements.ts`(admin CRUD)、`src/lib/api/portal.ts`(加 `listAnnouncements`)、`src/lib/api/types.ts`(`Announcement` 类型)、`locales/{zh,en}.ts`
- [ ] 类型 `Announcement{id,titleZh,titleEn,bodyZh,bodyEn,level:'INFO'|'MAINTENANCE'|'OUTAGE',pinned,startsAt?,endsAt?,active,createdAt}`。
- [ ] `portal.ts`:`export const listAnnouncements = () => request<Announcement[]>('GET','/api/portal/announcements')`。
- [ ] admin api:list/get/create/update/delete → `/api/admin/announcements`。
- [ ] i18n:`dashboard.announcements.*`(区标题)、`admin.announcements.*`(列/表单/级别 INFO/MAINTENANCE/OUTAGE 标签/pinned/window/空态)。zh/en 同结构。

## Task 1.8:前端 AnnouncementsPanel
**Files:** Create `src/features/dashboard/AnnouncementsPanel.vue`;Modify `HomeView.vue`(在 `<HeroPanel/>` 后插入 `<AnnouncementsPanel/>`);Test `AnnouncementsPanel.spec.ts`
- [ ] Panel:`onMounted` 拉 `listAnnouncements()`;读 `localStorage['cim.dismissedAnnouncements']`(JSON number[]);过滤掉已忽略;无剩余 → 不渲染。
- [ ] 渲染:`OUTAGE` 或 `pinned` → 横幅样式;其余 → 紧凑卡。级别色:INFO `bg-[#eff4ff] text-[#3538cd] border-[#c7d7fe]`、MAINTENANCE `bg-[#fffaeb] text-[#b45309] border-[#fde68a]`、OUTAGE `bg-[#fef2f2] text-[#b42318] border-[#fecaca]`(深色模式可加 dark: 变体或用半透明)。图标:INFO `Info`、MAINTENANCE `Wrench`、OUTAGE `AlertTriangle`(lucide)。正文 `class="whitespace-pre-line"`,用 `pick(a,'title')`/`pick(a,'body')`。
- [ ] 忽略按钮(X):把 id 加入 dismissed 数组写回 localStorage,并从渲染列表移除。
- [ ] Test:mock `listAnnouncements` 返回 2 条;断言渲染 2;点 X 后 localStorage 含该 id 且该条消失。(参照现有组件测试 + MSW/vi.mock 方式。)
- [ ] `npm run build` + `npm test` 绿。

## Task 1.9:前端 公告管理页
**Files:** Create `src/features/admin/announcements/{AnnouncementsAdminView.vue,AnnouncementFormModal.vue}`;Modify `AdminLayout.vue`(导航项)、`router/index.ts`(子路由)
- [ ] 列表视图(仿 `EnumsAdminView`):列 标题(pick)、级别(色标签)、pinned、生效窗口、active、操作(编辑/删除用 ConfirmDialog)。
- [ ] 表单弹窗(`Modal size="lg"`):中/英标题(Input)、中/英正文(`<textarea>`,多行)、级别 Select(INFO/MAINTENANCE/OUTAGE)、pinned Switch、active Switch、可选起止时间(`<input type="datetime-local">` → 转 ISO Instant;留空=null)。create/update。
- [ ] 导航 + 路由注册(i18n 文案 `admin.nav.announcements`)。
- [ ] `npm run build` + `npm test` 绿。
- [ ] **Commit(前端)**:`feat(announce): 公告面板(级别/置顶/可忽略)+ 管理页 + i18n`。

---

# 功能 ② 收藏 / 我的链接

## Task 2.1:Flyway V10 + 实体/仓库
**Files:** Create `src/main/resources/db/migration/oracle/V10__favorite.sql`(SQL 见 spec ②)、`com/cimportal/favorite/{Favorite.java,FavoriteId.java,FavoriteRepository.java}`
- [ ] V10 迁移;`OracleMigrationTest` 9 → **10**。
- [ ] `Favorite`(`@IdClass(FavoriteId)`:`@Id String employeeId`(col employee_id)、`@Id Long linkId`(col link_id)、`Instant createdAt`)。`FavoriteId`(employeeId,linkId)实现 equals/hashCode。
- [ ] `FavoriteRepository extends JpaRepository<Favorite,FavoriteId>`:
```java
@Query("select f.linkId from Favorite f where f.employeeId = ?1")
List<Long> findLinkIdsByEmployeeId(String employeeId);
boolean existsByEmployeeIdAndLinkId(String employeeId, Long linkId);
void deleteByEmployeeIdAndLinkId(String employeeId, Long linkId);
```

## Task 2.2:HomeLink.favorite + HomeService
**Files:** Modify `portal/dto/HomeLink.java`、`portal/HomeService.java`
- [ ] `HomeLink` record 末尾增 `boolean favorite`。更新构造调用点。
- [ ] `HomeService`:注入 `FavoriteRepository`;`resolveFor` 开头 `Set<Long> favIds = new HashSet<>(favoriteRepo.findLinkIdsByEmployeeId(user.employeeId()));`;构造 `HomeLink` 时传 `favIds.contains(l.getId())`。**不影响 accessible 逻辑**。

## Task 2.3:收藏端点(仅可访问)
**Files:** Create `favorite/FavoriteController.java`(+ 复用 grant/group 查询)
- [ ] 注入 `CurrentUserService`、`FavoriteRepository`、`LinkRepository`、`LinkAccessGrantRepository`(或现有取 grants 的方式)、`PermissionGroupMemberRepository`。
- [ ] `POST /api/portal/favorites/{linkId}`:取当前用户;查链接(不存在→404);用 `PermissionResolver.isVisible(dept,role,groupCodes,grantsOfLink)` 判可访问,不可访问→403(`ApiException.forbidden`/等价);`if(!exists) save(new Favorite(emp,linkId,now))`;返回 200/204。
- [ ] `DELETE /api/portal/favorites/{linkId}`:`deleteByEmployeeIdAndLinkId`;204。
> 复用 `HomeService` 已有的「取某链接 grants + 用户组 code」逻辑;若该逻辑内联在 HomeService,可抽一个 `PermissionService.isLinkAccessible(user, linkId)` 小方法供两处复用(可选,优先最小改动)。

## Task 2.4:后端测试
**Files:** Create `src/test/java/com/cimportal/favorite/FavoriteControllerTest.java`(extends `OracleIntegrationTest`)
- [ ] 造可访问链接(无 grant)→ 用户 `POST /api/portal/favorites/{id}` 200;`GET /api/portal/home` 该链接 `favorite=true`;`DELETE` 后 `favorite=false`。
- [ ] 造受限链接(加 ROLE grant 用户不匹配)→ `POST` 该链接 **403**,且未写入收藏。
- [ ] `mvn -q test` 绿(含 `OracleMigrationTest` 10)。
- [ ] **Commit(后端)**:`feat(fav): 收藏后端(V10 + HomeLink.favorite + 收藏端点含可访问校验 + 测试)`。

## Task 2.5:前端 星标 + 我的收藏区
**Files:** Modify `src/lib/api/portal.ts`(`toggleFavorite`)、`src/lib/api/types.ts`(`HomeLink.favorite`)、`src/features/dashboard/SystemCard.vue`、`src/features/dashboard/HomeView.vue`、`locales/{zh,en}.ts`;Test `SystemCard.spec.ts`
- [ ] 类型 `HomeLink` 加 `favorite: boolean`。`toggleFavorite(linkId:number,on:boolean)` → `request(on?'POST':'DELETE', '/api/portal/favorites/'+linkId)`。
- [ ] `SystemCard.vue`:可访问卡(`link.accessible`)右上角加星按钮(`Star` lucide,filled 态用 `fill-current text-amber-400`,否则 `text-ink-3`);点按乐观切换本地 `link.favorite` 并调 `toggleFavorite`,失败回滚 + toast。锁定卡不显示星。emit 一个 `favorited` 事件或直接改 prop 的本地副本(按现状最小实现;若 props 只读,用本地 ref 镜像 + emit 通知父级更新)。
- [ ] `HomeView.vue`:计算 `favorites = filtered.flatMap(c=>c.links).filter(l=>l.favorite && l.accessible)`(去重 by id);在 `<SystemGrid>` 之上,当 `favorites.length` 时渲染「我的收藏 / My links」标题 + 一个复用卡片网格(可直接用 `SystemGrid` 传一个伪 category,或简单 grid 渲染 `SystemCard`)。
- [ ] i18n:`dashboard.myLinks`(我的收藏 / My links)、`dashboard.favoriteAdd`/`favoriteRemove`(aria/title)。zh/en 对齐。
- [ ] Test:`SystemCard` 渲染可访问卡含星按钮;点击调用 toggle(mock)并切换态;锁定卡无星。
- [ ] `npm run build` + `npm test` 绿。
- [ ] **Commit(前端)**:`feat(fav): 卡片星标收藏 + 首页「我的收藏」区 + i18n`。

---

## 自检清单(Self-Review)
- **规格覆盖:** 公告 V9/实体/服务 effective/管理&门户 API/测试(1.2–1.6)、前端面板+管理页+i18n(1.7–1.9);收藏 V10/实体/HomeLink.favorite/HomeService/端点可访问校验/测试(2.1–2.4)、前端星标+我的收藏+i18n(2.5)。✔
- **占位符:** 关键后端给完整代码;前端步骤含组件职责、级别色值、localStorage 键、聚合逻辑;「按现状」处先 Read(1.1)。无 TBD。
- **一致性:** `AnnouncementLevel{INFO,MAINTENANCE,OUTAGE}`(实体↔DTO↔前端类型↔级别色)、effective 过滤规则(服务↔测试)、`HomeLink.favorite`(DTO 2.2 ↔ types 2.5)、`/api/portal/favorites/{linkId}` POST/DELETE(2.3↔2.5)、可访问校验复用 `PermissionResolver`+组 code(2.3↔HomeService 现状)。
- **硬约束:** `mvn test` 绿;`OracleMigrationTest` 9→10(1.2/2.1);收藏锁定链接 403(2.3/2.4);i18n 对齐(1.7/2.5);首页现有行为不破坏(HomeLink 仅追加字段,accessible 不变)。
