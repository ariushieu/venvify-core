# venvify-core

**Core Backend Service** for **VenViFy** — an online event organization and attendance platform.

This service owns the entire core business logic layer, enforcing ACID-compliant transactions for financial operations, booking management, and system orchestration.

---

## Tech Stack

| Layer | Technology |
|---|---|
| Framework | Spring Boot 4.1.0 |
| Language | Java 21 |
| Security & Auth | Spring Security · JWT (Access & Refresh Token) · Email verification |
| Data Access | Spring Data JPA (Hibernate) |
| Database | MySQL 8.0 |
| Build Tool | Maven |
| Containerization | Docker · Docker Compose |

---

## Architecture Highlights

### Hybrid ID System
- **Internal PK:** `Long (Auto-Increment)` — optimizes index performance and `JOIN` efficiency in MySQL.
- **Public ID:** `UUIDv7 (String-36)` — exposed on API endpoints to prevent **IDOR** (Insecure Direct Object Reference) vulnerabilities while keeping indexes more time-local than UUIDv4.

### Database per Service
Fully isolated data ownership. `venvify-core` connects exclusively to its own MySQL instance and communicates with the real-time service (Node.js) only via REST API secured with an internal token.

### Concurrency Control
JPA-level locking applied to handle race conditions when multiple attendees compete for the same ticket slot simultaneously.

### Design Principles
Strictly follows **SOLID** principles and enterprise-grade Clean Code standards throughout the codebase.

---

## Core Features

| # | Module | Description |
|---|---|---|
| 1 | **Auth & Profile** | Email registration/login · Refresh-token rotation · Email OTP verification · Role-based access control |
| 2 | **Event Management** | Event CRUD · SEO-friendly slugs · Draft/Published/Cancelled lifecycle · host/admin cancellation refund flow |
| 3 | **Slot & Booking** | Wallet-funded booking · event-row locking against oversell · ticket transfer/resale via wallet |
| 4 | **Wallet & Escrow** | Double-entry ledger · internal wallet · escrow hold/refund/release · dev-only top-up outside prod; Sepay/OAuth/payout are planned P2 slices |

---

## Getting Started

### Prerequisites
- Java 21+
- Maven 3.9+
- Docker & Docker Compose

### 1. Clone the repository
```bash
git clone https://github.com/your-org/venvify-core.git
cd venvify-core
```

### 2. Configure environment variables
```bash
cp .env.example .env
# Edit .env and fill in your credentials
```

### 3. Start the database
```bash
docker-compose up -d
```

### 4. Run the application
```bash
./mvnw spring-boot:run
```

The API will be available at `http://localhost:8080/api/v1`.

---

## Project Structure

```
venvify-core/
├── src/
│   ├── main/
│   │   ├── java/com/venvify/
│   │   └── resources/
│   └── test/
├── docker-compose.yml
├── .env.example
└── pom.xml
```

---

## Environment Variables

| Variable | Description |
|---|---|
| `MYSQL_DATABASE` | Database name (default: `venvify_db`) |
| `MYSQL_ROOT_PASSWORD` | MySQL root password |
| `MYSQL_USER` | Application database user |
| `MYSQL_PASSWORD` | Application database password |
| `SECRET_KEY` | JWT HS256 secret, at least 32 bytes |
| `RESEND_API_KEY` | Resend API key for outbound email |
| `RESEND_FROM_EMAIL` | Sender address, defaults to `onboarding@resend.dev` |
| `APP_CORS_ALLOWED_ORIGINS` | CSV list of allowed frontend origins |
| `APP_LOG_LEVEL` | Application logger level, defaults to `INFO` |
| `SQL_LOG_LEVEL` | Hibernate SQL logger level, defaults to `WARN` |

> Copy `.env.example` to `.env` — the `.env` file is gitignored and must never be committed.

---

## License

This project is part of a graduation thesis. All rights reserved.
