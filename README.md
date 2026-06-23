# venvify-core

**Core Backend Service** for **VenViFy** — an online event organization and attendance platform.

This service owns the entire core business logic layer, enforcing ACID-compliant transactions for financial operations, booking management, and system orchestration.

---

## Tech Stack

| Layer | Technology |
|---|---|
| Framework | Spring Boot 4.1.0 |
| Language | Java 21 |
| Security & Auth | Spring Security · JWT (Access & Refresh Token) · Spring Cloud OAuth2 (Google) |
| Data Access | Spring Data JPA (Hibernate) |
| Database | MySQL 8.0 |
| Build Tool | Maven |
| Containerization | Docker · Docker Compose |

---

## Architecture Highlights

### Hybrid ID System
- **Internal PK:** `Long (Auto-Increment)` — optimizes index performance and `JOIN` efficiency in MySQL.
- **Public ID:** `UUID v4 (String-36)` — exposed on all API endpoints to prevent **IDOR** (Insecure Direct Object Reference) vulnerabilities and conceal internal growth metrics.

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
| 1 | **Auth & Profile** | Traditional email registration with advanced password hashing · Google OAuth2 integration · Role-based access control (Host, Attendee, Admin) |
| 2 | **Event Management** | Full CRUD for event data · Auto-generated SEO-friendly slugs · Event lifecycle state machine (Draft → Published → Ongoing → Completed / Canceled) |
| 3 | **Slot & Booking** | Booking request intake · Real-time slot availability checks · Temporary slot locking during payment flow |
| 4 | **Wallet & Escrow** | VNPay/MoMo payment gateway integration · In-app wallet top-up · Escrow holding of ticket revenue until event completion · Automatic commission deduction and host disbursement |

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

The API will be available at `http://localhost:8080`.

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

> Copy `.env.example` to `.env` — the `.env` file is gitignored and must never be committed.

---

## License

This project is part of a graduation thesis. All rights reserved.
