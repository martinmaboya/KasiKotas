# KasiKotas

A food ordering web app for kotas — a popular South African street food. Built with Spring Boot, PostgreSQL, and deployed on Render.

**Backend API:** https://kasikotas.onrender.com  
**Frontend:** https://kasikotas.co.za

---

## Tech Stack

- **Java 17** / Spring Boot 3.2.5
- **Spring Security** — JWT auth + role-based access (CUSTOMER / ADMIN)
- **WebAuthn** — Passkey / biometric login via Yubico webauthn-server-core
- **PostgreSQL** + Spring Data JPA
- **Caffeine** — in-memory caching
- **Resend SDK** — transactional email (OTP)
- **Cloudinary** — product image storage
- **Render** — deployment via Docker

---

## Key Features

- JWT + refresh token authentication
- Passkey / fingerprint login (WebAuthn)
- Order placement with daily limit enforcement
- Pessimistic locking on stock — prevents overselling under high traffic
- Extras and sauces with independent inventory
- Promo codes (percentage and fixed-amount)
- EFT payments with AES-256 encrypted bank details
- Scheduled delivery time slots
- OTP-based password reset via email
- Product reviews (one per user per product)
- Admin order and product management

---

## Environment Variables

| Variable | Description |
|----------|-------------|
| `DATABASE_URL` | PostgreSQL connection URL |
| `DB_USERNAME` | Database username |
| `DB_PASSWORD` | Database password |
| `JWT_SECRET` | Secret for signing JWT tokens |
| `RESEND_API_KEY` | Resend API key for OTP emails |
| `BANK_ENCRYPTION_KEY` | Base64 AES-256 key for bank details |
| `WEBAUTHN_RP_ID` | WebAuthn RP ID — your domain, no protocol |
| `WEBAUTHN_ALLOWED_ORIGINS` | Comma-separated allowed origins |
| `YOCO_SECRET_KEY` | Yoco payment secret key |
| `YOCO_WEBHOOK_SECRET` | Yoco webhook signature secret |
| `APP_BASE_URL` | Backend base URL (used for keep-alive ping) |

---

## Running Locally

**Prerequisites:** Java 17+, Maven 3.8+, PostgreSQL

```bash
git clone https://github.com/martinmaboya/KasiKotas.git
cd KasiKotas
# Set the environment variables above, then:
mvn spring-boot:run
```

API runs on `http://localhost:8080`.

```bash
# Run tests
mvn test
```

---

## Author

**Nhlanhla Maboya**
