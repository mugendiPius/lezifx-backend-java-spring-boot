# LeziFx Trading Backend — Technical & Onboarding Reference

## Stack

| Layer | Tech |
|---|---|
| Runtime | Java 21, Spring Boot 3.x |
| Security | Spring Security, JWT (JJWT), BCrypt 12 rounds |
| Database | PostgreSQL + Spring Data JPA (Hibernate) |
| Cache | Redis (Lettuce pool) |
| WebSocket | STOMP over SockJS (Spring WebSocket) |
| Payments | Safaricom Daraja API (M-Pesa STK push + B2C) |
| Storage | Cloudinary (logo/favicon uploads) |
| Scheduling | Spring `@Scheduled` tasks |
| Migrations | Flyway (currently disabled — manual DDL in `src/main/resources/db/`) |

---

## How to Run Locally

```bash
# Prerequisites: Java 21, PostgreSQL 15+, Redis 7+

# 1. Create the database
createdb lezifx_db

# 2. Copy env template and fill in values
cp .env.example .env

# 3. Run migrations manually (Flyway is disabled — apply scripts in order)
psql lezifx_db < src/main/resources/db/V1__initial_schema.sql
# ... apply remaining V*.sql files in order

# 4. Start
./mvnw spring-boot:run
```

### Required Environment Variables

| Variable | Default | Notes |
|---|---|---|
| `DATABASE_URL` | `jdbc:postgresql://localhost:5432/lezifx_db` | |
| `DATABASE_USERNAME` | `postgres` | |
| `DATABASE_PASSWORD` | — | |
| `JWT_SECRET` | dev secret | Min 32 chars in production |
| `REDIS_URL` | `redis://localhost:6379` | |
| `SUPER_ADMIN_EMAIL` | `admin@lezifx.com` | Bootstrap super-admin |
| `SUPER_ADMIN_PASSWORD` | `changeme` | Change before first deploy |
| `SUPER_ADMIN_API_KEY` | `lzfx_master_00000000000000000000000000000001` | Master API key for `/superadmin/auth` |
| `FRONTEND_URL` | `http://localhost:5173` | CORS allowed origins (comma-separated) |
| `CLOUDINARY_CLOUD_NAME` | — | Required for logo uploads |
| `CLOUDINARY_API_KEY` | — | |
| `CLOUDINARY_API_SECRET` | — | |
| `DARAJA_CONSUMER_KEY` | — | M-Pesa STK push |
| `DARAJA_CONSUMER_SECRET` | — | |
| `CALLBACK_BASE_URL` | `https://localhost:8080` | Public URL Safaricom calls back to |

---

## Architecture Overview

```
Request
  │
  ├─ ApiKeyResolutionFilter (Order 1)
  │     Reads X-API-Key → looks up TenantApiKey → sets TenantContext
  │     Redis cache: 5 min TTL per key
  │
  ├─ JwtAuthFilter (Order 2)
  │     Reads Authorization: Bearer <token>
  │     Validates JWT → sets Spring SecurityContext principal = userId (UUID string)
  │
  └─ Controller → Service → Repository
        TenantContext.get() used in every service to scope DB queries
```

### Multi-Tenancy

Each tenant is a row in the `tenant` table. Every user, trade, wallet, and transaction belongs to a tenant via a `tenant_id` FK.

- **Domain resolution**: `PublicConfigController` maps `X-Domain` header → tenant via `tenant.allowed_origins` array.
- **API key resolution**: Each tenant has one or more API keys in `tenant_api_key`. The frontend must send its tenant's API key on every request via `X-API-Key`.
- **Master tenant**: UUID `00000000-0000-0000-0000-000000000001`. Used when no domain matches.

### Authentication Flow

1. Client calls `POST /api/v1/auth/login` with `X-API-Key` and `{email, password}`
2. `AuthService.login()` → validates credentials → issues 15-minute JWT (access) + 7-day refresh token (hashed in DB)
3. Subsequent requests: `Authorization: Bearer <accessToken>` + `X-API-Key: <tenantKey>`
4. On 401: client calls `POST /api/v1/auth/refresh` with `{refreshToken}` → new pair issued, old token revoked
5. `POST /api/v1/auth/logout` → deletes all refresh tokens for user → returns 204

Super-admin login: `POST /api/v1/superadmin/auth/login` — requires the master API key in `X-API-Key`. Configured via `SUPER_ADMIN_API_KEY` env var.

### Trading Flow

1. Frontend subscribes to STOMP topic `/topic/{tenantId}/prices/{symbol}` — gets live price ticks every ~2s from `PriceTickScheduler`
2. Player calls `POST /api/v1/trading/buy` with `{pairSymbol, stakeAmount, durationSeconds, isDemo}`
3. `TradeSessionService.buy()` locks the payout rate, creates a `TradeSession` with status `ACTIVE`
4. `SettlementScheduler` runs every second — finds expired ACTIVE sessions, fetches exit price, settles WIN/LOSS
5. On settlement: pushes `TRADE_RESULT` to `/user/queue/trade-result` and `BALANCE_UPDATE` to `/user/queue/balance`

### Platform Modes

| Mode | Behavior |
|---|---|
| `WIN` | Settlement always picks direction favorable to player |
| `NORMAL` | Standard house edge — random market movement |
| `LOSE` | Settlement direction favors house |

Mode is persisted in `PlatformModeService` (Redis-backed with DB fallback). Admins change it via `PUT /api/v1/admin/platform/mode`.

### WebSocket Topics

| Topic | Publisher | Subscriber |
|---|---|---|
| `/topic/{tenantId}/prices/{symbol}` | `PriceTickBroadcastService` | Trading page |
| `/topic/{tenantId}/platform` | `AdminPlatformService`, `HouseBalanceService` | All clients |
| `/topic/{tenantId}/social` | `SocialFeedService` | Trading page |
| `/topic/{tenantId}/admin/alerts` | `AdminAlertService` | Admin dashboard |
| `/user/queue/trade-result` | `SettlementService` | Authenticated user |
| `/user/queue/balance` | `WalletService`, `DepositService`, `WithdrawalService` | Authenticated user |

---

## Key Services

| Service | Responsibility |
|---|---|
| `AuthService` | Login, register, refresh, logout, super-admin login |
| `JwtService` | Token generation, validation, claim extraction |
| `ApiKeyService` | API key creation, revocation, lookup |
| `TradeSessionService` | Buy, active session query, history |
| `SettlementService` | Trade expiry settlement, outcome calculation |
| `PayoutRateService` | Per-duration payout rates (tenant-configurable) |
| `PriceTickBroadcastService` | Price tick generation + WebSocket broadcast |
| `WalletService` | Balance reads/writes, transaction recording |
| `DepositService` | M-Pesa STK push initiation + callback processing |
| `WithdrawalService` | Withdrawal requests, admin approval/rejection, B2C |
| `CommissionService` | Monthly commission statements for super-admin |
| `AdminPlatformService` | Platform mode, kill switch, floor balance, branding |

---

## JSON Serialization Note

Jackson is configured in `JacksonConfig` to use **field-based visibility** (not getter-based). This means:

- Boolean fields named `isXxx` serialize as `"isXxx"` (not `"xxx"`)
- All DTO fields use their declared name directly
- Individual `@JsonProperty("isXxx")` annotations on DTOs serve as explicit documentation

This is a departure from the default Spring Boot Jackson config. Do NOT rely on getter/setter names for serialization — always use field names.

---

## Security Checklist

- [x] JWT validated per request (15-min expiry)
- [x] Refresh tokens hashed in DB, revoked on use
- [x] X-API-Key resolves + isolates tenant on every request (Redis-cached)
- [x] WebSocket auth: JWT + API key validated on STOMP CONNECT
- [x] Tenant isolation enforced at DB query level via `TenantContext`
- [x] Super-admin master API key configurable via env (not hardcoded)
- [x] CORS locked to `FRONTEND_URL` env var in production
- [ ] Admin IP allowlist (planned — Spring Security CIDR filter)
- [ ] Rate limiting on auth endpoints (not implemented)

---

## Adding a New Endpoint (Onboarding)

1. Add DTO in `web/dto/request/` (request) and `web/dto/response/` (response)
2. Add business logic in the appropriate service under `service/`
3. Add controller method under `web/controller/`
4. Update `SecurityConfig.securityFilterChain()` if the endpoint needs different auth rules
5. Add the endpoint to the frontend API file under `src/lib/api/`
6. If the endpoint returns a `boolean isXxx` field, add `@JsonProperty("isXxx")` to the DTO field (or rely on field-based Jackson config)
