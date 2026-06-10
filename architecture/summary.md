# Architecture Summary

## System Purpose
AP1 IT CIM Portal is an SSO-gated internal portal for the IT/CIM department of a chip-packaging company. Authenticated staff see a dashboard of manufacturing systems and launch them (web links, environment-specific links, or local desktop apps via a custom URL scheme); administrators manage the catalog, access grants, reference enums, and SSO/security settings.

## Repo Archetype
`full_stack_product` — a Vue 3 SPA (`cim-portal-client`) plus a Spring Boot 3 OAuth2 resource-server API (`cim-portal-server/backend`) backed by a relational database, with an external OIDC identity provider. Two cooperating deployable units + a datastore + external systems is the defining shape.

## Primary Containers or Modules
- Web SPA (cim-portal-client): browser UI; public OIDC (PKCE) client; holds the Bearer token; dashboard + admin.
- Portal API (cim-portal-server): stateless resource server; dual-issuer JWT validation; portal + admin REST; persistence + Flyway.
- Relational Database: Oracle (uat/prod) / MariaDB (dev); stores all portal entities; Flyway-migrated (V1–V6).

## Critical Flows
- SSO login (Authorization Code + PKCE): SSO is the default and auto-initiates; why it matters — it's the primary entry path and most loop/UX risk lives here.
- Internal-login fallback: employee ID + org-wide initial password when SSO is unavailable; matters for resilience when the IdP is down.
- Dual-issuer token validation: the API accepts portal-internal (`cim-portal`) and company-SSO tokens, routed by `iss`; matters because it unifies both login paths on one stateless resource server.
- Grant-based home assembly: all active links are returned with an `accessible` flag and the target hidden when locked; matters because authorization is enforced server-side, not cosmetically.

## Key Decisions
- [DEC-001] Frontend-PKCE SSO over backend session login — keeps the API stateless and Bearer-based | covers: spa, backend-api, spa-auth, rel-spa-sso, rel-ctx-sso
- [DEC-002] Dual-issuer resource server (portal-internal + company SSO) routed by `iss` | covers: be-auth-security, rel-beauth-sso, rel-api-sso
- [DEC-003] Grants gate "open", not visibility — locked cards with server-hidden URLs | covers: be-portal, rel-beportal-link, rel-beportal-user
- [DEC-004] SSO/internal-password config is runtime-editable in `security_setting`, exposed non-secret via public config | covers: be-settings, rel-beportal-settings, rel-spaauth-client
- [DEC-005] DB- and IdP-agnostic: Oracle/MariaDB via parallel Flyway sets; OIDC issuer swappable by config | covers: relational-db, be-persistence, company-sso

## Data Ownership Notes
- Link, LinkAccessGrant: system of record = be-link (Portal API), stored in relational-db.
- EnumValue: system of record = be-enum.
- UserInfo: system of record = be-user (roles/department for authorization; not provisioned from SSO claims).
- SecuritySetting: system of record = be-settings (SSO config + BCrypt internal password).
- User credentials/sessions: external system of record = company-sso (the IdP), not the portal.
- relational-db physically stores all tables but is not the authoritative writer; the API components are.

## Major Risks or Unknowns
- Company SSO issuer URL, client registration, and the username claim that maps to `employee_id` are environment config, not in the repo.
- No HR/IdP user-provisioning sync is present; SSO users must already exist in UserInfo or they authenticate without portal roles.
- Public SPA holds the access token (standard for PKCE SPAs); mitigated by short token lifetime — XSS exposure remains the inherent trade-off.
- Local-app launch depends on per-machine custom URL scheme registration (out of the portal's control).

## Recommended Next Reads
- `cim-portal-server/backend/src/main/java/com/cimportal/auth/MultiIssuerJwtDecoder.java`: the dual-issuer security core.
- `cim-portal-server/backend/src/main/java/com/cimportal/portal/HomeService.java`: grant-based locked-card logic.
- `cim-portal-client/src/features/auth/LoginView.vue`: SSO-default + internal-fallback login flow.
- `cim-portal-client/docs/Keycloak-SSO-dev.md`: dev SSO setup and how to switch to the company IdP.

## Artifact Index
- `architecture/model.yaml`: canonical architecture model
- `architecture/views/system-context.yaml`: system context view
- `architecture/views/container.yaml`: container view
- `architecture/views/component-backend-api.yaml`: backend API component view
- `architecture/views/component-spa.yaml`: SPA component view
- `architecture/summary.md`: this summary
- `architecture/manifest.yaml`: artifact index + scope/mode/evidence
- `architecture/diagram.html`: rendered interactive diagram
