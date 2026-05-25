# Observable Reactive GraphQL E-Commerce Platform — Quarkus v3

A production-grade, highly resilient, and fully observable **reactive e-commerce backend** built on the **Quarkus 3.x** framework (Java 21). Designed around domain-driven clean architecture principles, it utilizes a non-blocking reactive stack powered by **Mutiny**, **Hibernate Reactive with Panache**, and **SmallRye GraphQL** to achieve peak throughput and sub-millisecond execution latencies.

The system is fortified with a **comprehensive observability suite** comprising OpenTelemetry, Jaeger, Prometheus, Grafana, Loki, and Node Exporter, fully integrated via Docker Compose to offer instant dashboard-level visibility from SQL statements up to the GraphQL layer.

---

## Key Features

| Domain Module | Capabilities |
| :--- | :--- |
| **Auth & Users** | Secure registration, login, logout, and JWT token lifecycle (access & refresh tokens with custom RSA key signing) via GraphQL mutations and queries. |
| **Roles & RBAC** | Custom permission configurations and granular role-based security filters mapping directly to Quarkus `@RolesAllowed` in GraphQL schemas. |
| **Category** | Complete hierarchical category management, monthly/yearly category pricing metrics, and real-time total revenue statistics. |
| **Product** | Dynamic inventory catalogs, support for **Base64-encoded image uploads**, rating calculations, stock metrics, and soft-delete capabilities. |
| **Cart** | High-performance real-time shopping cart operations (items addition, batch modifications, quantity checks) designed for high concurrent throughput. |
| **Merchants** | Comprehensive merchant onboarding featuring dedicated modular sub-domains: **MerchantDetail** (dual-image brand setup with Cover & Logo), **MerchantAward** (sertifikasi), **MerchantBusiness** (rekening bank), **MerchantPolicy** (syarat toko), and **MerchantSocialLink** (tautan medsos). |
| **Order** | Order creation pipelines, real-time total calculations, payment status updates (CASH, CREDIT_CARD, etc.), order sold-out stats, and merchant-specific revenue analytics. |
| **Review & Detail** | customer review ratings, textual comments, and detailed batch-wise review item details (`ReviewDetail`) validating purchase satisfaction. |
| **Shipping Address**| Dynamic shipping profile management for active users, allowing multiple addresses with granular status tracking. |
| **Slider & Banner** | Digital brand campaigns featuring carousel image sliders and header promo banners with Base64 media upload capabilities. |
| **Transaction** | Centralized financial audit ledger collecting transaction events across the system, payment method distribution, and monthly/yearly volume reports. |
| **Observability** | Fully integrated telemetry collecting traces, metrics, and logs (OpenTelemetry, Prometheus, Jaeger, Loki, Grafana, Node Exporter). |
| **Containerized Setup**| Local orchestration using Docker Compose featuring an authenticated Redis instance, PostgreSQL, and the full observability stack. |

---

## Architecture Overview

The platform implements a **decoupled domain structure** within a single highly optimized reactive runtime unit. It utilizes a fully non-blocking asynchronous I/O thread model powered by **Eclipse Vert.x** under the hood of Quarkus.

### Core Architecture Principles

*   **Non-Blocking Asynchronous I/O**: High-performance GraphQL and DB operations use Mutiny `Uni` objects to ensure no OS threads are blocked under high request load.
*   **Active Record & Repository Pattern**: Clean persistence separation using Hibernate Reactive with Panache, guaranteeing asynchronous database interaction.
*   **Flyway Database Migrations**: Seamless version control for database schemas, run automatically on application startup.
*   **RSA Signed JWT Authentication**: Stateless request authorization handled by SmallRye JWT, verified securely via public keys.
*   **OpenTelemetry & Context Propagation**: Automatic propagation of `traceId` and `spanId` from GraphQL resolvers down to Hibernate SQL executions, injected dynamically into log statements.

```mermaid
graph TB
    classDef client fill:#0f172a,stroke:#38bdf8,color:#e0f2fe,stroke-width:2px,font-weight:bold
    classDef gateway fill:#1e293b,stroke:#22d3ee,color:#cffafe,stroke-width:2px,font-weight:bold
    classDef domain fill:#1e1b4b,stroke:#818cf8,color:#e0e7ff,stroke-width:1.5px
    classDef infra fill:#172554,stroke:#60a5fa,color:#dbeafe,stroke-width:1.5px
    classDef obs fill:#052e16,stroke:#4ade80,color:#dcfce7,stroke-width:1.5px

    Client["Client Applications<br/>(GraphQL Clients / API Consumers)"]:::client

    subgraph QuarkusApp["Quarkus Reactive Application Engine"]
        direction TB
        GRAPHQL["SmallRye GraphQL<br/>GraphQL Resolvers Gateway"]:::gateway
        SECURITY["SmallRye JWT / Elytron<br/>RBAC Authorization Middleware"]:::gateway

        subgraph BusinessDomains["Reactive Service Layers"]
            AUTH["Auth / User / Role Services"]:::domain
            CATALOG["Product / Category / Slider / Banner Services"]:::domain
            MERCHANT["Merchant / Business / Detail / Policy / Social Services"]:::domain
            SALES["Cart / Order / Shipping / Review / Transaction Services"]:::domain
        end
    end

    Client -->|"/graphql"| GRAPHQL
    GRAPHQL --> SECURITY
    SECURITY --> BusinessDomains

    subgraph Infrastructure["Infrastructure Layer"]
        direction LR
        PG[("PostgreSQL DB<br/>example_quarkus")]:::infra
        REDIS[("Redis Cache DB<br/>Port :6380 (Auth)")]:::infra
    end

    BusinessDomains -->|"Hibernate Reactive"| PG
    BusinessDomains -->|"Reactive Redis Client"| REDIS

    subgraph Observability["Observability Suite"]
        direction LR
        OTEL["OTel Collector<br/>gRPC Port :4317"]:::obs
        PROM["Prometheus<br/>Metrics Scraper :9090"]:::obs
        JAEGER["Jaeger Tracing<br/>Portal :16686"]:::obs
        LOKI["Loki Log DB<br/>Port :3100"]:::obs
        GRAFANA["Grafana Dashboards<br/>Portal :3000"]:::obs
        NODEX["Node Exporter<br/>Host Metrics"]:::obs
    end

    QuarkusApp -.->|"OTLP Spans / Logs"| OTEL
    QuarkusApp -.->|"/q/metrics"| PROM
    OTEL -.-> JAEGER
    PROM -.-> GRAFANA
    LOKI -.-> GRAFANA
    JAEGER -.-> GRAFANA
    NODEX -.-> PROM
```

---

## Telemetry & Observability Architecture

```mermaid
graph TB
    classDef service fill:#1e1b4b,stroke:#818cf8,color:#e0e7ff,stroke-width:1.5px
    classDef collector fill:#172554,stroke:#60a5fa,color:#dbeafe,stroke-width:1.5px
    classDef storage fill:#052e16,stroke:#4ade80,color:#dcfce7,stroke-width:1.5px
    classDef viz fill:#431407,stroke:#fb923c,color:#fed7aa,stroke-width:2px,font-weight:bold

    subgraph Sources["Telemetry Sources"]
        direction TB
        QUARKUS["Quarkus E-Commerce App<br/>(Traces, Metrics, Logs)"]:::service
        NODES["Host / Node System"]:::service
    end

    subgraph Collectors["Collection Layer"]
        direction TB
        OTEL["OTel Collector<br/>Receives OTLP Spans"]:::collector
        PROM["Prometheus<br/>Scrapes /q/metrics"]:::collector
        NODEX["Node Exporter<br/>System Metrics"]:::collector
    end

    subgraph Storage["Storage Layer"]
        direction TB
        PROM_TSDB["Prometheus TSDB"]:::storage
        LOKI_STORE["Grafana Loki"]:::storage
        JAEGER_STORE["Jaeger Storage"]:::storage
    end

    subgraph Visualization["Visualization & Monitoring"]
        GRAFANA["Grafana<br/>Unified Dashboard Portal"]:::viz
    end

    QUARKUS -->|"OTLP gRPC :4317"| OTEL
    QUARKUS -->|"/q/metrics"| PROM
    QUARKUS -->|"Console JSON Logs"| LOKI_STORE
    NODES --> NODEX

    NODEX --> PROM
    PROM --> PROM_TSDB
    OTEL --> JAEGER_STORE

    PROM_TSDB --> GRAFANA
    LOKI_STORE --> GRAFANA
    JAEGER_STORE --> GRAFANA
```

---

## Technology Stack

| Category | Technologies | Purpose |
| :--- | :--- | :--- |
| **Programming Language**| Java 21 (JDK 21) | Ultra-fast execution, native image support, modern thread constructs. |
| **Framework Engine** | Quarkus v3.30+ | Supersonic Subatomic Java framework with lightning fast boot time. |
| **Reactive Paradigm** | Mutiny | Advanced, non-blocking asynchronous event-driven development library. |
| **GraphQL Engine** | SmallRye GraphQL | MicroProfile GraphQL standards implementation utilizing build-time optimizations. |
| **ORM / Persistent** | Hibernate Reactive + Panache | Non-blocking database access wrapping the active-record repository. |
| **Database Engine** | PostgreSQL v17 | Persistent SQL transaction ledger database. |
| **Caching Core** | Redis v7.4 | Single Redis cache authenticated via `dragon_knight` password. |
| **Auth Tokens** | SmallRye JWT | RSA public/private key verification standards for APIs. |
| **DB Migrations** | Flyway | Structured schema migrations executed cleanly on system startup. |
| **Docker Engine** | Docker Compose | Local container virtualization and dependency staging. |

---

## Getting Started

### Prerequisites

Verify that the following configurations are installed on your workstation:
- [Git](https://git-scm.com/)
- [Java Development Kit (JDK 21+)](https://adoptium.net/)
- [Maven 3.9+](https://maven.apache.org/)
- [Docker Engine & Docker Compose](https://docs.docker.com/get-docker/)

---

### 1. Configure the Environment Stacks

Compile all backing containers and infrastructure utilities via Docker Compose:

```bash
docker-compose up -d
```

Verify that all services are healthy and running:

```bash
docker-compose ps
```

*Note: Postgres tables and baseline structures are automatically constructed on startup via the Flyway migration files located in `src/main/resources/db/migration`.*

---

### 2. Launching the App in Development Mode

Run the application in Quarkus Dev Mode with full live-coding hot reload enabled:

```bash
./mvnw quarkus:dev
```

> **GraphQL Playground Portal:** The SmallRye GraphQL UI playground is instantly accessible at `http://localhost:8080/q/graphql-ui` during dev mode.
> **Dev UI Portal:** The Quarkus Dev UI panel is instantly accessible at `http://localhost:8080/q/dev/` during active dev sessions.

---

### 3. Packaging and Running the App

Package the application into an optimized runnable Jar package:

```bash
./mvnw clean package
```

The compiled runner Jar will reside in the `target/quarkus-app/` directory. Launch it using:

```bash
java -jar target/quarkus-app/quarkus-run.jar
```

If you wish to compile a native executable direct binary via GraalVM/Mandrel:

```bash
./mvnw package -Dnative -Dquarkus.native.container-build=true
```

---

## GraphQL HURL Integration Testing

The application includes a complete suite of integration test suites written in **[Hurl](https://hurl.dev/)** to validate all GraphQL mutations, queries, headers, and access control policies.

All test suites are stored within the `hurl/` directory:

| Test File | GraphQL Target APIs | Description |
| :--- | :--- | :--- |
| **`auth.hurl`** | `AuthGraphQL` & `UserGraphQL` | Validates registration, login, refresh tokens, user updates, and logout. |
| **`category.hurl`**| `CategoryGraphQL` | Validates category creation, updates, pagination queries, and soft-delete/restoration. |
| **`product.hurl`** | `ProductGraphQL` | Validates product creation with Base64 image payload, updates, pagination, and soft delete. |
| **`cart.hurl`** | `CartGraphQL` | Validates additions to cart, querying items, and deleting cart items. |
| **`merchant.hurl`**| `MerchantGraphQL` | Validates merchant profile management, detailed queries, and soft-deletes. |
| **`order.hurl`** | `OrderGraphQL` | Validates order placing flow, pagination queries, status changes, and soft-delete/restoration. |
| **`review.hurl`** | `ReviewGraphQL` | Validates client ratings, comments addition, and pagination reviews list. |
| **`shipping.hurl`**| `ShippingAddressGraphQL` | Validates shipping profile configurations and addresses search. |
| **`transaction.hurl`**| `TransactionGraphQL` | Validates e-commerce billing ledger records, payment method breakdowns, and volume stats. |

### Run all tests sequentially

```bash
hurl --test hurl/*.hurl
```

---

## Port Map Registry

| Application/Service Portal | Address Protocol / Access URL |
| :--- | :--- |
| **GraphQL API Endpoint** | `http://localhost:8080/graphql` |
| **GraphQL UI Playground** | `http://localhost:8080/q/graphql-ui` |
| **Quarkus Dev UI Dashboard** | `http://localhost:8080/q/dev/` (Dev Mode Only) |
| **Quarkus Health Check Page** | `http://localhost:8080/q/health` |
| **PostgreSQL Database** | `localhost:5432` (example_quarkus / password) |
| **Redis Cache Instance** | `localhost:6380` (password: `dragon_knight`) |
| **Jaeger Tracing Portal** | [http://localhost:16686](http://localhost:16686) |
| **Prometheus Console** | [http://localhost:9090](http://localhost:9090) |
| **Loki Log Portal** | `http://localhost:3100` |
| **Grafana Monitoring Dashboard**| [http://localhost:3000](http://localhost:3000) *(Credentials: `admin`/`admin`)* |
| **OpenTelemetry gRPC Collector**| `localhost:4317` |

---

## Workspace Directory Tree

```
quarkus-graphql-ecommerce/
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── com/example/
│   │   │       ├── config/            # Initializers and setups
│   │   │       ├── domain/            # Domain models, requests, and responses
│   │   │       ├── exception/         # Reactive custom domain exceptions & mappers
│   │   │       ├── graphql/           # GraphQL API Resolvers (20 modules)
│   │   │       │   ├── AuthGraphQL.java
│   │   │       │   ├── UserGraphQL.java
│   │   │       │   ├── RoleGraphQL.java
│   │   │       │   ├── CategoryGraphQL.java
│   │   │       │   ├── ProductGraphQL.java
│   │   │       │   ├── CartGraphQL.java
│   │   │       │   ├── OrderGraphQL.java
│   │   │       │   ├── ShippingAddressGraphQL.java
│   │   │       │   ├── SliderGraphQL.java
│   │   │       │   ├── BannerGraphQL.java
│   │   │       │   ├── ReviewGraphQL.java
│   │   │       │   ├── ReviewDetailGraphQL.java
│   │   │       │   ├── MerchantGraphQL.java
│   │   │       │   ├── MerchantDetailGraphQL.java
│   │   │       │   ├── MerchantAwardGraphQL.java
│   │   │       │   ├── MerchantBusinessGraphQL.java
│   │   │       │   ├── MerchantPolicyGraphQL.java
│   │   │       │   ├── MerchantSocialLinkGraphQL.java
│   │   │       │   └── TransactionGraphQL.java
│   │   │       ├── entity/            # Hibernate Reactive Panache Entities
│   │   │       ├── repository/        # Reactive Persistence Repository layer
│   │   │       ├── security/          # Password and JWT services
│   │   │       └── service/           # Reactive Service interfaces & implementations
│   │   └── resources/
│   │       ├── db/migration/          # Flyway SQL schema scripts
│   │       ├── application.properties  # Central application configurations
│   │       ├── privateKey.pem         # RSA private key for JWT generation
│   │       └── publicKey.pem          # RSA public key for JWT validation
│   └── test/
├── hurl/                              # GraphQL Integration tests (9 official suites)
│   ├── auth.hurl
│   ├── cart.hurl
│   ├── category.hurl
│   ├── merchant.hurl
│   ├── order.hurl
│   ├── product.hurl
│   ├── review.hurl
│   ├── shipping.hurl
│   └── transaction.hurl
├── observability/                     # Telemetry configurations
│   ├── grafana/                       #   Grafana data source provisioning
│   ├── loki-config.yaml               #   Loki aggregator profile
│   ├── otel-collector.yaml            #   OpenTelemetry collector targets
│   └── prometheus.yml                 #   Prometheus metrics scraper targets
├── Dockerfile                         # JVM multi-stage deployment build
├── Dockerfile.native                  # GraalVM Native runtime build
├── docker-compose.yml                 # Local virtualization compose script
├── pom.xml                            # Maven workspace configurations
└── README.md                          # Platform instructions documentation
```

---

## License

This project is open-sourced under the MIT License for development and educational purposes.

---
<p align="center">
  Built with Java, Quarkus Reactive, Mutiny, SmallRye GraphQL, OpenTelemetry, and Grafana.
</p>