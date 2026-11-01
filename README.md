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
| `WEBAUTHN_RP_ID` | WebAuthn RP ID — your domain, no protocol (e.g. `kasikotas.co.za`) |
| `WEBAUTHN_ALLOWED_ORIGINS` | Comma-separated allowed origins (e.g. `https://kasikotas.co.za`) |
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

## Frontend Integration

This section is for whoever builds or maintains the frontend.

### Error responses

All API errors return this shape:

```json
{
  "message": "Sorry Martin, we are sold out today. Please try again tomorrow.",
  "code": "ORDER_LIMIT_EXCEEDED",
  "path": "/api/orders"
}
```

Always display `error.message` directly — the backend already includes the customer's name in the message. Do not prepend the name from localStorage.

```js
try {
  await axios.post('/api/orders', payload);
} catch (err) {
  const { message, code } = err.response?.data ?? {};
  showToast(message ?? 'Something went wrong.');
}
```

**Error codes:**

| Code | Meaning |
|------|---------|
| `ORDER_LIMIT_EXCEEDED` | Sold out or not enough kotas left today |
| `INSUFFICIENT_STOCK` | A product or extra ran out of stock |
| `CONCURRENCY_CONFLICT` | High traffic — safe to retry |
| `BAD_REQUEST` | Validation error in the request |

---

### Payment fields

Every order response includes:

```json
{
  "paymentMethod": "YOCO",
  "paymentStatus": "PAID"
}
```

**Payment methods:** `YOCO`, `EFT`, `COD`  
**Payment statuses:** `PENDING`, `PAID`, `FAILED`, `CANCELLED`, `REFUNDED`

Always read `paymentStatus` from the backend — do not derive it locally from order status.

**Yoco** — status is updated automatically by the Yoco webhook. Do not let admin change it manually.  
**EFT / COD** — status starts as `PENDING`. Admin confirms payment via:

```http
PUT /api/orders/{orderId}/payment-status
{ "status": "PAID" }
```

**Suggested display labels:**

```js
const methodLabel = { YOCO: 'Yoco', EFT: 'EFT Transfer', COD: 'Cash on Delivery' };
const statusLabel = { PENDING: 'Payment pending', PAID: 'Paid', FAILED: 'Payment failed', CANCELLED: 'Cancelled', REFUNDED: 'Refunded' };
```

---

### Forgot password — firstName in response

The `/api/auth/forgot-password` response includes `firstName` so the frontend can personalise the UI without a separate API call:

```json
{
  "success": true,
  "message": "OTP has been sent to your email",
  "firstName": "Martin"
}
```

Returns `"firstName": ""` when the email is not found (security best practice — success is always returned either way).

---

## Author

**Martin Maboya**
