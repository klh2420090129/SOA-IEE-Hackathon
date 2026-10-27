# Security & Authorization Architecture

## 1. Authentication & Token Standards

The system implements signed JSON Web Tokens (JWT) using industry best practices:
- **Token Claims:**
  - `sub`: Unique User ID (UUID string).
  - `username`: Shippers or planners handle.
  - `roles`: JSON array of authority strings (`ROLE_SHIPPER`, `ROLE_LOAD_PLANNER`, `ROLE_ADMIN`).
  - `iss`: `soa-team8-auth-service`.
  - `aud`: `soa-team8-platform`.
  - `iat`: Issue timestamp (Unix epoch seconds).
  - `exp`: Expiration timestamp (strictly checked; default 1 hour for access tokens).
  - `jti`: Unique JWT ID to prevent replay attacks.
- **Signing Algorithm:** HMAC-SHA256 (demonstration & local testing) with RSA-256 asymmetric signing support for production.

---

## 2. Role-Based Access Control (RBAC) & Defense-in-Depth

Security enforcement is applied at **both** Spring Cloud Gateway and inside each individual microservice using Spring Security filter chains and method-level security (`@PreAuthorize`). A Gateway-only check is explicitly treated as insufficient.

| Resource / Endpoint | Method | Required Role | Ownership / Record-Level Rule |
| :--- | :--- | :--- | :--- |
| `/api/v1/auth/login` | POST | Public | None |
| `/api/v1/auth/me` | GET | Authenticated | Returns current authenticated principal |
| `/api/v1/declarations` | POST | `ROLE_SHIPPER` | Shipper ID bound to token principal |
| `/api/v1/declarations/{id}` | GET | `ROLE_SHIPPER` / `ROLE_LOAD_PLANNER` | Shippers can only read their own declarations; planners can view all |
| `/api/v1/declarations/{id}/workflow` | GET | `ROLE_SHIPPER` / `ROLE_LOAD_PLANNER` | Workflow read projection |
| `/api/v1/rules/**` | GET | Authenticated | Read-only rules access |
| `/api/v1/rules/**` | POST/PUT | `ROLE_ADMIN` | Governance operation; shippers cannot publish rules |
| `/api/v1/assessments` | POST | `ROLE_LOAD_PLANNER` | Shippers cannot create assessment jobs directly |
| `/api/v1/assessments/{id}/approve` | POST | `ROLE_LOAD_PLANNER` | Strict role check; shippers cannot approve candidates |
| `/api/v1/assessments/{id}/reject` | POST | `ROLE_LOAD_PLANNER` | Requires structured reason code |

---

## 3. Credential Safety & Audit Redaction

- Passwords stored using BCrypt (work factor 10+).
- Passwords, signing keys, and secrets are never logged in plain text or written to console/audit records.
- Cross-Origin Resource Sharing (CORS) is configured explicitly for authorized frontend origins only (e.g. `http://localhost:5173`, `http://localhost:3000`).
