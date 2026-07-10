# Store Application — Developer Guide

A production-grade Spring Boot 3.4.2 REST API managing customers, orders, and products. Deployed on Kubernetes with PostgreSQL 16.2.

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Runtime | Java 17, Spring Boot 3.4.2 |
| Database | PostgreSQL 16.2 (Liquibase migrations) |
| Security | Spring Security 6, HMAC-SHA256 API key authentication |
| Caching | Caffeine (in-process, per-replica) |
| Mapping | MapStruct (compile-time DTO generation) |
| Build | Gradle, Spotless (Palantir format), JaCoCo |
| CI/CD | GitHub Actions → GHCR → Kubernetes |
| Deployment | Kubernetes (2 replicas, rolling updates, hardened security context) |
| Testing | JUnit 5, Mockito, MockMvc, Testcontainers (PostgreSQL) |

## Prerequisites

- Java 17+
- Docker (for PostgreSQL and Testcontainers)
- PostgreSQL 16.2 on `localhost:5433`

Start PostgreSQL:
```shell
docker run -d \
  --name postgres \
  --restart always \
  -e POSTGRES_USER=admin \
  -e POSTGRES_PASSWORD=admin \
  -e POSTGRES_DB=store \
  -v postgres:/var/lib/postgresql/data \
  -p 5433:5432 \
  postgres:16.2 \
  postgres -c wal_level=logical
```

## Running the Application

```shell
./gradlew bootRun
```

With dev profile (DEBUG logging, SQL output):
```shell
./gradlew bootRun --args='--spring.profiles.active=dev'
```

The application uses Liquibase to migrate the schema automatically on startup. Sample seed data is included.

## API Authentication

All `/api/**` endpoints require an `X-API-Key` header. Health/info endpoints are public.

### Generate an API Key (local dev)

```shell
curl -X POST "http://localhost:8080/internal/keys/generate?clientId=my-app"
```

Response:
```json
{"apiKey": "sk_my-app_1700000000_<salt>_<signature>", "clientId": "my-app"}
```

### Use the API Key

```shell
curl -H "X-API-Key: sk_gayan_1783683052_hqwlOWtjDnwemGbMHNAJbg_GwDgms5zTFkD-rQkn5gEpoCgId11T4Xr5M3TlPE33QI" http://localhost:8080/api/v1/product
```

### CORS Bypass

Requests from the configured origin (`APP_SECURITY_CORS_ORIGIN`, default `http://localhost:3000`) bypass API key validation — designed for browser frontends.

## API Endpoints

| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/v1/order?page=0&size=20` | List orders (paginated) |
| GET | `/api/v1/order/{id}` | Get order by ID |
| POST | `/api/v1/order` | Create order |
| POST | `/api/v1/order/checkout` | Create order with inline customer |
| GET | `/api/v1/customer` | List customers |
| GET | `/api/v1/customer?name=X` | Search customers by name |
| POST | `/api/v1/customer` | Create customer |
| GET | `/api/v1/product` | List products |
| GET | `/api/v1/product/{id}` | Get product by ID |
| POST | `/api/v1/product` | Create product |
| GET | `/actuator/health` | Health check (public) |
| GET | `/actuator/info` | App info (public) |

Full API contract: [OpenAPI.yaml](OpenAPI.yaml)

## Data Model

```
Customer (1) ──→ (N) Order (N) ←──→ (M) Product
```

- A customer has a unique email and 0+ orders
- An order has a description, belongs to one customer, and contains 1+ products
- A product has a description and appears in 0+ orders

## Performance Optimizations

The database is **not co-located** with the application — there's significant network latency. Key optimizations:

- **JOIN FETCH** on all queries — loads relationships in 1 round trip instead of N+1
- **Two-query pagination** for orders — paginated IDs + batch fetch (exactly 2 DB round trips)
- **Caffeine caching** — products (30min TTL), orders by ID (LRU, immutable)
- **JDBC batch inserts** (batch_size=30) — reduces join table write round trips
- **GIN trigram index** on customer name — accelerates substring search
- **B-tree index** on FK column (customer_id) — accelerates JOIN operations

## Security

- **HMAC-SHA256 API key authentication** — keys self-validate (no DB lookup per request)
- **Constant-time comparison** — prevents timing attacks on key validation
- **CORS configuration** — frontend origin bypass, restricted methods/headers
- **Spring Security** — CSRF disabled (stateless), session management STATELESS
- **K8s security context** — non-root, read-only filesystem, all capabilities dropped

## Infrastructure Hardening

- **Graceful shutdown** (30s timeout) — in-flight requests complete during rolling updates
- **HikariCP tuning** — pool size 10, leak detection 60s, idle timeout 10min, max lifetime 30min
- **K8s hardened deployment** — `runAsNonRoot`, `readOnlyRootFilesystem`, `drop: ALL` capabilities, `/tmp` emptyDir
- **OWASP Dependency-Check** — fails CI on CVSS ≥ 7
- **Trivy container scan** — fails CI on HIGH/CRITICAL vulnerabilities

## Testing

99 tests across 6 layers:

```shell
# All tests (requires Docker for Testcontainers)
./gradlew test

# Only controller tests (no Docker)
./gradlew test --tests "com.example.store.controller.*"

# Only integration tests (requires Docker)
./gradlew test --tests "com.example.store.integration.*"
```

| Layer | What | Count |
|-------|------|-------|
| Controller (MockMvc) | HTTP contracts, validation, status codes | 23 |
| Service Unit (Mockito) | Business logic branches | 25 |
| Cache (Spring + H2) | @Cacheable/@CacheEvict behavior | 7 |
| Service Integration (PG) | Full flows, transactions | 18 |
| Repository Integration (PG) | JPQL queries, JOIN FETCH | 14 |
| Schema Migration (PG) | Liquibase, constraints, indexes | 12 |

## CI/CD Pipeline

GitHub Actions (`.github/workflows/ci.yml`) — 3 jobs:

1. **build-and-test** — Spotless check → Build → All tests → OWASP scan → Upload artifacts
2. **docker-publish** (main only) — Build JAR → Push to GHCR (tags: SHA + latest)
3. **container-scan** (main only) — Trivy scan for HIGH/CRITICAL CVEs

## Kubernetes Deployment

```shell
# Dev
kubectl apply -f k8s/base/ -n dev && kubectl apply -f k8s/dev/ -n dev

# Staging
kubectl apply -f k8s/base/ -n staging && kubectl apply -f k8s/staging/ -n staging

# Prod
kubectl apply -f k8s/base/ -n prod && kubectl apply -f k8s/prod/ -n prod
```

Production secrets are managed via HashiCorp Vault (ExternalSecret CRD, 1h refresh).

## Docker

```shell
# Build
./gradlew build -x test
docker build -t store:latest .

# Run locally
docker run --rm -p 8080:8080 \
  -e SPRING_DATASOURCE_URL=jdbc:postgresql://host.docker.internal:5433/store \
  store:latest
```

## Configuration

All config is in `src/main/resources/application.yaml` with `${ENV_VAR:default}` placeholders. Defaults are production-safe.

| Variable | Default | Purpose |
|----------|---------|---------|
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://localhost:5433/store` | DB connection |
| `SPRING_DATASOURCE_USERNAME` | `admin` | DB user |
| `SPRING_DATASOURCE_PASSWORD` | `admin` | DB password |
| `APP_SECURITY_API_KEY` | `MkT9W9wcuCWObZT9p9NkH1pnGfBpEY9YRM2owOn6JVw=` | HMAC signing secret |
| `APP_SECURITY_CORS_ORIGIN` | `http://localhost:3000` | Allowed frontend origin |
| `LOGGING_LEVEL_COM_EXAMPLE_STORE` | `INFO` | Log level |

## Project Structure

```
src/main/java/com/example/store/
├── config/          SecurityConfig, CacheConfig
├── controller/      OrderController, CustomerController, ProductController, ApiKeyController
├── service/         OrderService, CustomerService, ProductService, ApiKeyService
├── repository/      JPA repositories with optimized JPQL queries
├── entity/          Customer, Order, Product (JPA entities)
├── dto/             Request/Response DTOs (11 classes)
├── mapper/          MapStruct mappers (compile-time)
├── exception/       GlobalExceptionHandler, ResourceNotFoundException
└── filter/          ApiKeyAuthFilter, RequestIdFilter
```

## Documentation

- [OpenAPI.yaml](OpenAPI.yaml) — API contract
- [ARCHITECTURE.md](ARCHITECTURE.md) — Mermaid architecture diagrams

## Key Design Decisions

| Decision | Why |
|----------|-----|
| HMAC-SHA256 API keys | No DB lookup per request (high-latency link), key self-validates, per-client identification |
| JOIN FETCH everywhere | Minimizes round trips over high-latency DB link |
| Two-query pagination | Avoids Hibernate in-memory pagination, exactly 2 round trips |
| Caffeine over Redis | Local cache sufficient for 2 replicas, no infra dependency |
| No CascadeType on Customer→Orders | Orders managed independently, prevents accidental cascade-delete |
| MapStruct over manual mapping | Compile-time, no reflection, type-safe |
| Testcontainers over H2 | H2 can't test pg_trgm indexes or PostgreSQL-specific behavior |
| Graceful shutdown 30s | Matches K8s terminationGracePeriodSeconds for zero-downtime deploys |
| OWASP + Trivy in CI | Covers both Java deps and container OS/JRE layers |
