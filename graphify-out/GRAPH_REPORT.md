# Graph Report - clothes  (2026-10-07)

## Corpus Check
- 92 files · ~26,124 words
- Verdict: corpus is large enough that graph structure adds value.
- Unclassified: 18 file(s) not represented in the graph (top: (none) 6, .example 3, .css 2)

## Summary
- 755 nodes · 1621 edges · 56 communities (19 shown, 37 thin omitted)
- Extraction: 97% EXTRACTED · 3% INFERRED · 0% AMBIGUOUS · INFERRED: 42 edges (avg confidence: 0.85)
- Token cost: 0 input · 0 output

## Community Hubs (Navigation)
- frontend-user/src/api/types.ts
- Đăng ký và đăng nhập
- AuthIntegrationTests
- SecurityConfig.java
- ApiResponse
- uuid
- frontend-admin/package.json
- Account
- AccountStatus
- AuthController.java
- AuthService
- ClothesApplication.java
- AGENTS.md
- CLAUDE.md
- lemonadex.project:clothes
- frontend-user/package.json
- frontend-admin/src/api/types.ts
- frontend-admin/src/auth/AuthContext.tsx
- HomePage.tsx
- PostgresTestSupport
- compilerOptions
- compilerOptions
- frontend-admin/src/main.tsx
- Role
- org.junit.jupiter.api.Test
- frontend-admin/src/format.ts
- AdminLayout.tsx
- .register
- Customer

## God Nodes (most connected - your core abstractions)
1. `AuthIntegrationTests` - 44 edges
2. `CommerceIntegrationTests` - 42 edges
3. `ApiResponse` - 24 edges
4. `Account` - 24 edges
5. `AuthService` - 23 edges
6. `ErrorBanner()` - 17 edges
7. `GlobalExceptionHandler` - 17 edges
8. `Customer` - 17 edges
9. `AccountStatus` - 16 edges
10. `AccountRepository` - 15 edges

## Surprising Connections (you probably didn't know these)
- `V2: auth và xóa mềm` --references--> `AuthIntegrationTests`  [INFERRED]
  docs/database-migration.md → src/test/java/lemonadex/project/clothes/AuthIntegrationTests.java
- `V2: auth và xóa mềm` --references--> `ClothesApplicationTests`  [INFERRED]
  docs/database-migration.md → src/test/java/lemonadex/project/clothes/ClothesApplicationTests.java
- `Overview` --references--> `CategoryResponse`  [EXTRACTED]
  frontend-admin/src/pages/HomePage.tsx → frontend-admin/src/api/types.ts
- `AdminLayout()` --calls--> `useAuth()`  [EXTRACTED]
  frontend-admin/src/components/AdminLayout.tsx → frontend-admin/src/auth/AuthContext.tsx
- `HomePage()` --calls--> `useAuth()`  [EXTRACTED]
  frontend-admin/src/pages/HomePage.tsx → frontend-admin/src/auth/AuthContext.tsx

## Import Cycles
- None detected.

## Communities (56 total, 37 thin omitted)

### Community 0 - "frontend-user/src/api/types.ts"
Cohesion: 0.07
Nodes (73): api, ApiError, errorMessage(), getToken(), onUnauthorized(), Query, request(), RequestOptions (+65 more)

### Community 1 - "Đăng ký và đăng nhập"
Cohesion: 0.07
Nodes (23): Gateway, Kiến trúc MVC, Luồng đăng ký, Luồng đăng nhập và xác thực, Persistence và xóa mềm, Gateway, Kiểm thử, Request (+15 more)

### Community 2 - "AuthIntegrationTests"
Cohesion: 0.06
Nodes (3): V2: auth và xóa mềm, AuthIntegrationTests, ClothesApplicationTests

### Community 3 - "SecurityConfig.java"
Cohesion: 0.05
Nodes (6): SecurityConfig, ApiErrorWriter, RequestIdFilter, CorsProperties, SecurityProperties, AccountJwtAuthenticationConverter

### Community 5 - "ApiResponse"
Cohesion: 0.13
Nodes (3): ApiResponse, EmailAlreadyRegisteredException, GlobalExceptionHandler

### Community 7 - "frontend-admin/package.json"
Cohesion: 0.07
Nodes (28): dependencies, @fontsource-variable/geist, @phosphor-icons/react, react, react-dom, react-router-dom, devDependencies, @types/react (+20 more)

### Community 9 - "AccountStatus"
Cohesion: 0.05
Nodes (11): AccountController, AccountSummaryResponse, PageResponse, ResourceNotFoundException, AccountStatus, ACTIVE, INACTIVE, LOCKED (+3 more)

### Community 14 - "AuthService"
Cohesion: 0.28
Nodes (6): AuthMapper, AccountRepository, AccountAccessService, AccountQueryService, AuthService, AuthTokenService

### Community 22 - "frontend-user/package.json"
Cohesion: 0.07
Nodes (26): dependencies, react, react-dom, react-router-dom, devDependencies, @types/react, @types/react-dom, typescript (+18 more)

### Community 24 - "frontend-admin/src/api/types.ts"
Cohesion: 0.12
Nodes (19): Adjustment, CartItemResponse, CartResponse, CategoryRequest, CategoryResponse, CheckoutRequest, LoginRequest, OrderItemResponse (+11 more)

### Community 25 - "frontend-admin/src/auth/AuthContext.tsx"
Cohesion: 0.21
Nodes (13): api, ApiError, getToken(), onUnauthorized(), Query, request(), RequestOptions, setToken() (+5 more)

### Community 26 - "HomePage.tsx"
Cohesion: 0.25
Nodes (12): OrderResponse, Page, formatMoney(), AsyncState, useAsync(), greeting(), HomePage(), loadOverview() (+4 more)

### Community 31 - "compilerOptions"
Cohesion: 0.12
Nodes (15): compilerOptions, isolatedModules, jsx, lib, module, moduleResolution, noEmit, noFallthroughCasesInSwitch (+7 more)

### Community 34 - "compilerOptions"
Cohesion: 0.12
Nodes (15): compilerOptions, isolatedModules, jsx, lib, module, moduleResolution, noEmit, noFallthroughCasesInSwitch (+7 more)

### Community 36 - "frontend-admin/src/main.tsx"
Cohesion: 0.27
Nodes (8): errorMessage(), useAuth(), ErrorBanner(), Field(), RequireAdmin(), LoginPage(), PlaceholderPage(), @fontsource-variable/geist

### Community 39 - "Role"
Cohesion: 0.11
Nodes (3): BaseEntity, Permission, Role

### Community 42 - "org.junit.jupiter.api.Test"
Cohesion: 0.08
Nodes (4): LoginRequest, RegisterRequest, ArchitectureTests, CommerceIntegrationTests

### Community 45 - "frontend-admin/src/format.ts"
Cohesion: 0.22
Nodes (6): OrderStatus, PaymentStatus, dateTime, ORDER_STATUS_LABEL, PAYMENT_STATUS_LABEL, vnd

### Community 47 - "AdminLayout.tsx"
Cohesion: 0.38
Nodes (6): AdminLayout(), initials(), NAV, NavItem, SideNavLink(), @phosphor-icons/react

## Knowledge Gaps
- **146 isolated node(s):** `name`, `private`, `version`, `type`, `dev` (+141 more)
  These have ≤1 connection - possible missing edges or undocumented components. (Counts symbols only; 293 node(s) total have ≤1 connection when file, concept and rationale nodes are included.)
- **37 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `AuthIntegrationTests` connect `AuthIntegrationTests` to `SecurityConfig.java`, `Account`, `org.junit.jupiter.api.Test`, `AuthService`, `PostgresTestSupport`?**
  _High betweenness centrality (0.078) - this node is a cross-community bridge._
- **What connects `name`, `private`, `version` to the rest of the system?**
  _146 weakly-connected nodes found - possible documentation gaps or missing edges._
- **Should `frontend-user/src/api/types.ts` be split into smaller, more focused modules?**
  _Cohesion score 0.07152875175315568 - nodes in this community are weakly interconnected._
- **Why does `AuthService` connect `AuthService` to `AuthIntegrationTests`, `uuid`, `AuthController.java`, `org.junit.jupiter.api.Test`, `.register`?**
  _High betweenness centrality (0.048) - this node is a cross-community bridge._
- **Should `Đăng ký và đăng nhập` be split into smaller, more focused modules?**
  _Cohesion score 0.07407407407407407 - nodes in this community are weakly interconnected._
- **Why does `Account` connect `Account` to `AuthIntegrationTests`, `uuid`, `Role`, `AccountStatus`, `AuthService`, `.register`, `Customer`?**
  _High betweenness centrality (0.045) - this node is a cross-community bridge._
- **Should `AuthIntegrationTests` be split into smaller, more focused modules?**
  _Cohesion score 0.05536568694463431 - nodes in this community are weakly interconnected._