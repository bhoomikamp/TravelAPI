# TravelAPI 🌍

A production-grade **Java Spring Boot backend** deployed on **GCP Cloud Run**, demonstrating real-world microservice patterns, GCP service integration, and Infrastructure as Code with Terraform.

Built as a self-initiated project to demonstrate backend engineering skills with the exact tech stack used in enterprise Java teams.

---

## Tech Stack

| Layer | Technology |
|---|---|
| Language | Java 17 |
| Framework | Spring Boot 3.2, Spring MVC, Spring Security |
| ORM | JPA / Hibernate |
| Database | PostgreSQL (GCP Cloud SQL) |
| Messaging | GCP Cloud Pub/Sub |
| File Storage | GCP Cloud Storage |
| Analytics | GCP BigQuery + Scheduled Queries |
| Scheduling | GCP Cloud Scheduler + Spring `@Scheduled` |
| Deployment | GCP Cloud Run (containerised) |
| IaC | Terraform |
| CI/CD | GitHub Actions + GitHub Workflows |
| API Testing | Postman |
| Auth | JWT (Spring Security stateless) |
| Migration | Flyway |

---

## Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│  Client (Postman / Frontend)                                     │
└────────────────────┬────────────────────────────────────────────┘
                     │ HTTPS REST
┌────────────────────▼────────────────────────────────────────────┐
│  GCP Cloud Run – TravelAPI (Spring Boot, Docker container)       │
│                                                                  │
│  ┌──────────────┐  ┌──────────────┐  ┌───────────────────────┐  │
│  │ AuthController│  │ TripController│  │ ReminderController    │  │
│  └──────┬───────┘  └──────┬───────┘  └──────────┬────────────┘  │
│         │                 │                      │               │
│  ┌──────▼─────────────────▼──────────────────────▼────────────┐  │
│  │              Service Layer                                  │  │
│  │  AuthService  TripService  ReminderService  AnalyticsService│  │
│  └──────┬─────────────────┬──────────────────┬───────────────-┘  │
│         │                 │                  │                    │
└─────────┼─────────────────┼──────────────────┼────────────────────┘
          │                 │                  │
    ┌─────▼──────┐   ┌──────▼──────┐   ┌───────▼──────┐
    │ Cloud SQL  │   │  Pub/Sub    │   │  BigQuery    │
    │ PostgreSQL │   │  Topics /   │   │  Analytics   │
    │ (JPA/ORM)  │   │  Subscript. │   │  Dataset     │
    └────────────┘   └─────────────┘   └──────────────┘
                           │
                    ┌──────▼──────┐
                    │ Cloud       │
                    │ Storage     │
                    │ (attachments│
                    └─────────────┘
```

---

## GCP Services Used

- **Cloud SQL (PostgreSQL)** — primary relational datastore; accessed via Socket Factory from Cloud Run
- **Cloud Run** — serverless container hosting; auto-scales 1–10 instances
- **Cloud Storage** — stores trip attachment files; signed URLs for secure client download
- **Cloud Pub/Sub** — async inter-service messaging (reminder events, trip events)
- **BigQuery** — OLAP analytics queries on trip data; fed by nightly Scheduled Query from Cloud SQL
- **Cloud Scheduler** — triggers `POST /api/internal/reminders/process` every minute via OIDC-authenticated HTTP call
- **IAM & Service Accounts** — least-privilege roles bound to a dedicated `travelapi` Service Account
- **Secret Manager** — stores DB password and JWT secret securely

---

## REST API

All endpoints (except auth) require `Authorization: Bearer <token>` header.

### Auth
| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/auth/register` | Register new user |
| POST | `/api/auth/login` | Login and receive JWT |

### Trips
| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/trips` | Create trip |
| GET | `/api/trips?page=0&size=10` | List trips (paginated) |
| GET | `/api/trips/{id}` | Get trip details |
| GET | `/api/trips/search?destination=Tokyo` | Search by destination |
| PUT | `/api/trips/{id}` | Update trip |
| DELETE | `/api/trips/{id}` | Delete trip |

### Reminders
| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/reminders` | Create reminder |
| GET | `/api/reminders?tripId=1` | List reminders for trip |

### Attachments (GCS)
| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/trips/{id}/attachments` | Upload file to Cloud Storage |
| GET | `/api/trips/{id}/attachments` | List with signed download URLs |
| DELETE | `/api/trips/{id}/attachments/{aid}` | Delete from GCS + DB |

### Analytics (BigQuery)
| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/analytics/top-destinations?limit=5` | Top destinations from BigQuery |
| GET | `/api/analytics/monthly` | Monthly trip stats from BigQuery |

---

## Project Structure

```
travelapi/
├── src/
│   ├── main/java/com/travelapi/
│   │   ├── TravelApiApplication.java      # Entry point
│   │   ├── config/
│   │   │   ├── SecurityConfig.java        # JWT filter, Spring Security
│   │   │   ├── JwtUtils.java              # Token generation/validation
│   │   │   ├── GcpConfig.java             # Cloud Storage, BigQuery beans
│   │   │   └── JpaConfig.java             # JPA auditing
│   │   ├── controller/
│   │   │   ├── AuthController.java
│   │   │   ├── TripController.java
│   │   │   └── Controllers.java           # Reminder, Attachment, Analytics, Internal
│   │   ├── service/
│   │   │   ├── AuthService.java           # Auth + UserDetailsService
│   │   │   ├── TripService.java           # CRUD + Pub/Sub events
│   │   │   ├── ReminderService.java       # Reminder CRUD + @Scheduled processor
│   │   │   ├── PubSubService.java         # GCP Pub/Sub publisher
│   │   │   ├── StorageService.java        # GCP Cloud Storage upload/download
│   │   │   └── AnalyticsService.java      # GCP BigQuery queries
│   │   ├── model/                         # JPA entities (User, Trip, Reminder, Attachment)
│   │   ├── repository/                    # Spring Data JPA repositories
│   │   ├── dto/                           # Request/Response DTOs
│   │   └── exception/                     # Global exception handler
│   ├── resources/
│   │   ├── application.yml                # Main config (GCP, DB, JWT)
│   │   └── db/migration/V1__init_schema.sql  # Flyway migration
│   └── test/
│       └── java/com/travelapi/
│           └── TravelApiTests.java        # Unit tests (Mockito)
├── terraform/
│   ├── main.tf                            # Provider, APIs
│   ├── variables.tf
│   ├── iam.tf                             # Service Account + IAM bindings
│   ├── cloud_run_sql_storage.tf           # Cloud Run, Cloud SQL, GCS
│   ├── pubsub_bigquery_scheduler.tf       # Pub/Sub, BigQuery, Cloud Scheduler
│   └── outputs.tf
├── .github/workflows/
│   └── deploy.yml                         # CI: test → build → push GCR → deploy Cloud Run
├── Dockerfile                             # Multi-stage build for Cloud Run
└── pom.xml
```

---

## Running Locally

### Prerequisites
- Java 17
- Maven 3.9+
- Docker (optional, for local Cloud Run simulation)
- GCP account with a project created

### 1. Cloud SQL Proxy (local dev)
```bash
# Download Cloud SQL Auth Proxy
curl -o cloud-sql-proxy https://storage.googleapis.com/cloud-sql-connectors/cloud-sql-proxy/v2.9.0/cloud-sql-proxy.linux.amd64
chmod +x cloud-sql-proxy

# Start proxy (replace with your instance connection name)
./cloud-sql-proxy YOUR_PROJECT:asia-south1:travelapi-db --port 5432
```

### 2. Set environment variables
```bash
export GCP_PROJECT_ID=your-project-id
export CLOUD_SQL_INSTANCE=your-project:asia-south1:travelapi-db
export DB_PASSWORD=your-db-password
export JWT_SECRET=your-32-char-secret
export GOOGLE_APPLICATION_CREDENTIALS=/path/to/service-account-key.json
```

### 3. Run
```bash
mvn spring-boot:run
```

### 4. Run Tests
```bash
mvn test
```

---

## Deploying with Terraform

```bash
cd terraform

# Initialise (creates GCS backend bucket first)
terraform init

# Plan
terraform plan -var="project_id=YOUR_PROJECT" \
               -var="db_password=YOUR_PASSWORD" \
               -var="jwt_secret=YOUR_SECRET" \
               -var="cloud_run_image=gcr.io/YOUR_PROJECT/travelapi:latest"

# Apply
terraform apply
```

---

## CI/CD (GitHub Actions)

On every push to `main`:
1. **Test** — runs `mvn test` with H2 in-memory DB (no GCP required)
2. **Build** — packages JAR, builds Docker image
3. **Push** — pushes image to Google Container Registry with `sha` and `latest` tags
4. **Deploy** — deploys to Cloud Run with zero-downtime rolling update

Required GitHub secrets:
- `GCP_PROJECT_ID`
- `GCP_WORKLOAD_IDENTITY_PROVIDER`
- `GCP_SERVICE_ACCOUNT`

---

## Key Design Decisions

**Bulk data handling** — JPA batch inserts configured (`batch_size=50`, `order_inserts=true`). Reminder bulk-update uses a single `@Modifying` JPQL query instead of per-row updates.

**Microservice pattern** — Services are isolated by domain (auth, trip, reminder, analytics). Pub/Sub decouples the reminder processor from the trip service so they can scale independently.

**IAM least-privilege** — The Cloud Run Service Account has only the roles it needs: `cloudsql.client`, `storage.objectAdmin` (scoped to the specific bucket), `pubsub.publisher`, `pubsub.subscriber`, `bigquery.dataViewer`, `bigquery.jobUser`.

**Stateless auth** — JWT tokens; no server-side session state, which is required for Cloud Run's multiple instance model.

---

## Author

**Bhoomika M P** — Java Backend Developer | Bangalore
[LinkedIn](https://www.linkedin.com/in/bhoomika-m-p-63b3b7219/)
