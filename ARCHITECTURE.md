# Store Application — Architecture Diagrams

## System Overview

```mermaid
graph TB
    %% External Clients
    Frontend[Store Frontend SPA<br/>CORS Origin Bypass]
    APIClient[External API Clients<br/>X-API-Key: sk_...]
    KubeProbe[K8s Probes<br/>No Auth]

    %% CI/CD
    subgraph CI["GitHub Actions CI/CD"]
        Build[Build & Test<br/>Spotless + JUnit + JaCoCo]
        OWASP[OWASP Dependency-Check<br/>Fail CVSS ≥ 7]
        DockerBuild[Docker Build & Push<br/>GHCR]
        Trivy[Trivy Container Scan<br/>HIGH/CRITICAL]
        Build --> OWASP
        Build --> DockerBuild
        DockerBuild --> Trivy
    end

    %% Kubernetes Cluster
    subgraph K8s["Kubernetes Cluster"]
        Ingress[K8s Ingress<br/>TLS Termination<br/>Routes: /api/** + /actuator/health/**]

        subgraph Pod1["Pod 1 (runAsUser:1000, readOnlyFS, drop ALL)"]
            subgraph App1["Spring Boot 3.4.2 / Java 17"]
                subgraph Security1["Spring Security Filter Chain"]
                    CORS1[CORS Filter]
                    APIKeyFilter1[ApiKeyAuthFilter<br/>HMAC-SHA256 Validation]
                end
                ReqIdFilter1[RequestIdFilter<br/>X-Request-ID → MDC]

                subgraph Controllers1["Controllers"]
                    OrderCtrl[OrderController<br/>/api/v1/order]
                    CustomerCtrl[CustomerController<br/>/api/v1/customer]
                    ProductCtrl[ProductController<br/>/api/v1/product]
                    KeyCtrl[ApiKeyController<br/>/internal/keys<br/>Cluster-only]
                end

                subgraph Services1["Services"]
                    OrderSvc[OrderService]
                    CustomerSvc[CustomerService]
                    ProductSvc[ProductService]
                    ApiKeySvc[ApiKeyService<br/>HMAC-SHA256<br/>Generate + Validate]
                end

                subgraph Caching1["Caffeine Cache"]
                    CProducts[products<br/>500 entries, 30min TTL]
                    CProductById[productById<br/>500 entries, 30min TTL]
                    COrderById[orderById<br/>1000 entries, LRU only]
                end

                subgraph Data1["Data Layer"]
                    Mappers[MapStruct Mappers<br/>Compile-time]
                    Repos[JPA Repositories<br/>JOIN FETCH queries]
                    HikariCP[HikariCP<br/>Pool: 10, Leak: 60s<br/>Idle: 10min, MaxLife: 30min]
                end

                Actuator1[Actuator<br/>/health /info]
            end
            TmpVol1["/tmp (emptyDir)"]
        end

        subgraph Pod2["Pod 2 (identical replica)"]
            App2[Spring Boot Instance 2<br/>Same architecture]
        end

        SVC[K8s Service<br/>ClusterIP :80 → :8080]
    end

    %% Database
    subgraph DB["PostgreSQL 16.2 (High Latency)"]
        Tables["customer | purchase_order<br/>product | order_product"]
        Indexes["GIN trigram (customer.name)<br/>B-tree (customer_id FK)"]
        Liquibase["Liquibase Managed Schema"]
    end

    %% Secrets
    subgraph Vault["HashiCorp Vault"]
        DBCreds[store/prod/db<br/>url, username, password]
        APICreds[store/prod/security<br/>api-signing-secret]
    end

    %% Connections
    Frontend -->|Origin header match| Ingress
    APIClient -->|X-API-Key header| Ingress
    KubeProbe -->|/actuator/health| Pod1
    KubeProbe -->|/actuator/health| Pod2

    Ingress --> SVC
    SVC --> Pod1
    SVC --> Pod2

    %% Internal flow (Pod 1)
    CORS1 --> APIKeyFilter1
    APIKeyFilter1 --> ReqIdFilter1
    ReqIdFilter1 --> Controllers1
    Controllers1 --> Services1
    Services1 --> Caching1
    Services1 --> Data1
    HikariCP -->|"2 round trips max<br/>(high latency link)"| DB

    %% Vault to K8s
    Vault -->|ExternalSecret CRD<br/>Refresh 1h| K8s

    %% CI to Registry
    Trivy -->|"ghcr.io/store-main:latest"| Ingress
```

---

## Request Flow (Sequence Diagram)

```mermaid
sequenceDiagram
    participant C as Client
    participant I as K8s Ingress
    participant S as SecurityFilterChain
    participant AK as ApiKeyAuthFilter
    participant R as RequestIdFilter
    participant CT as Controller
    participant SV as Service
    participant CA as Caffeine Cache
    participant H as HikariCP (10 conns)
    participant DB as PostgreSQL 16.2

    C->>I: GET /api/v1/order (X-API-Key, X-Request-ID)
    I->>S: Route to pod
    S->>AK: Validate HMAC-SHA256 key
    AK->>AK: Split key → recompute signature → constant-time compare
    AK->>R: Auth OK → set SecurityContext
    R->>R: Set MDC(requestId)
    R->>CT: Forward
    CT->>SV: getAllOrders(page, size)
    SV->>H: Query 1: SELECT ids (paginated)
    H->>DB: Lightweight ID query
    DB-->>H: IDs + count
    SV->>H: Query 2: JOIN FETCH by IDs
    H->>DB: Full fetch with relationships
    DB-->>H: Orders + customers + products
    H-->>SV: Entities
    SV->>SV: MapStruct → DTOs
    SV-->>CT: PaginatedOrderResponse
    CT-->>C: 200 OK (JSON)
```

---

## Data Model (ER Diagram)

```mermaid
erDiagram
    CUSTOMER {
        bigserial id PK
        varchar name "NOT NULL"
        varchar email "NOT NULL, UNIQUE"
    }
    PURCHASE_ORDER {
        bigserial id PK
        varchar description "NOT NULL"
        bigint customer_id FK "NOT NULL, indexed"
    }
    PRODUCT {
        bigserial id PK
        varchar description "NOT NULL"
    }
    ORDER_PRODUCT {
        bigint order_id FK,PK
        bigint product_id FK,PK
    }

    CUSTOMER ||--o{ PURCHASE_ORDER : "has many"
    PURCHASE_ORDER }o--o{ PRODUCT : "contains"
    PURCHASE_ORDER ||--o{ ORDER_PRODUCT : ""
    PRODUCT ||--o{ ORDER_PRODUCT : ""
```

---

## Security Filter Chain

```mermaid
graph LR
    Request[Incoming Request] --> CORS{CORS Filter}
    CORS -->|Origin matches| Bypass[Auth: cors-origin]
    CORS -->|No match| APIKey{ApiKeyAuthFilter}
    APIKey -->|Missing or Invalid| Reject[401 Unauthorized]
    APIKey -->|Valid HMAC| Auth[Auth: clientId]
    Bypass --> ReqId[RequestIdFilter]
    Auth --> ReqId
    ReqId --> Controller[Controller]

    Actuator[Actuator Health] --> Health[200 OK No Auth]
    Internal[Internal Endpoints] --> KeyGen[ApiKeyController No Auth]
```

---

## CI/CD Pipeline

```mermaid
graph LR
    subgraph Trigger
        Code[Code Push]
    end

    subgraph Job1[Build and Test]
        Format[Spotless Check]
        Build[Gradle Build]
        Tests[99 Tests]
        Format --> Build --> Tests
    end

    subgraph Job2[Dependency Check]
        OWASP[OWASP Scan]
    end

    subgraph Job3[Docker Publish]
        JAR[Build JAR]
        Push[Push to GHCR]
        JAR --> Push
    end

    subgraph Job4[Container Scan]
        Trivy[Trivy HIGH+CRITICAL]
    end

    Code --> Format
    Code --> OWASP
    Tests -->|Pass| JAR
    OWASP -->|Pass| JAR
    Push --> Trivy
```

---

## Deployment Environments

```mermaid
graph TB
    subgraph Dev[Dev Environment]
        DevCM[ConfigMap: DEBUG logging]
        DevSecret[Secret: admin credentials]
    end

    subgraph Staging[Staging Environment]
        StageCM[ConfigMap: INFO logging]
        StageSecret[Secret: staging credentials]
    end

    subgraph Prod[Production Environment]
        ProdCM[ConfigMap: INFO logging]
        ProdES[ExternalSecret from Vault]
    end

    Base[Base Deployment: 2 replicas, security context, graceful shutdown] --> Dev
    Base --> Staging
    Base --> Prod
```
