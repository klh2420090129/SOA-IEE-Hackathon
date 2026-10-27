# Freight Planning System — Local Startup & Synthetic Seed Data Guide

## 1. Prerequisites
- Java 21 LTS
- Maven 3.9+
- Docker & Docker Compose
- Node.js 20+ (for Frontend)
- Python 3.12+ (for Optimizer Worker)

---

## 2. Infrastructure Startup (MySQL & Kafka)
Launch isolated MySQL schemas and Apache Kafka broker:
```bash
docker compose up -d
```
Verify health:
- MySQL port `3306` (schemas: `auth_db`, `cargo_declaration_db`, `compatibility_rules_db`, `compartment_db`, `optimization_db`, `validation_db`).
- Kafka broker port `9092` with topic `soa.s06.t08.events`.

---

## 3. Microservices Build & Startup Order

Build all services:
```bash
mvn clean test
```

Start microservices in sequence:
1. **Service Discovery (Eureka):**
   ```bash
   mvn -pl service-discovery spring-boot:run
   ```
   (Wait until http://localhost:8761 is responsive).

2. **API Gateway:**
   ```bash
   mvn -pl api-gateway spring-boot:run
   ```
   (Available on http://localhost:8080).

3. **Domain Services:**
   ```bash
   mvn -pl auth-service spring-boot:run
   mvn -pl cargo-declaration-service spring-boot:run
   mvn -pl compatibility-rule-service spring-boot:run
   mvn -pl compartment-service spring-boot:run
   mvn -pl optimization-service spring-boot:run
   mvn -pl validation-service spring-boot:run
   ```

---

## 4. Synthetic Seed Data & Credentials

### Demo Users (Auth Service)
| Role | Username | Password |
| :--- | :--- | :--- |
| **Shipper** | `shipper@soa8.org` | `ShipperPass123!` |
| **Load Planner** | `planner@soa8.org` | `PlannerPass123!` |
| **Admin (Governance)** | `admin@soa8.org` | `AdminPass123!` |

### Default Compatibility Rule Snapshot (`snap-2026-q4-v1`)
- **Rule 1 (`CMV-HAZ-1-3`):** Explosives (Class 1) and Flammable Liquids (Class 3) must be separated by at least 3000 mm.
- **Rule 2 (`CMV-HAZ-1-2.1`):** Co-loading Explosives (Class 1) and Flammable Gases (Class 2.1) is forbidden.
- **Rule 3 (`CMV-HAZ-4.3-8`):** Dangerous When Wet (Class 4.3) and Corrosives (Class 8) must be separated by at least 2000 mm.
