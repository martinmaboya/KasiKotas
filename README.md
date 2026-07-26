# 🥪 KasiKotas

A full-stack food ordering web application for ordering kotas — a popular South African street food.
Built with a Spring Boot REST API backend, PostgreSQL database, and deployed on Render.

---

## 🚀 Live Demo

> **Backend API:** [https://kasikotas.onrender.com](https://kasikotas.onrender.com)
> **Frontend:** [https://kasikotas-frondend.onrender.com](https://kasikotas-frondend.onrender.com)

---

## ✨ Features

- 🔐 **JWT Authentication** — Stateless token-based auth with role-based access control (`CUSTOMER` / `ADMIN`)
- 🪪 **Passkey / Biometric Login** — Fingerprint and Face ID login via WebAuthn (works in any modern mobile browser, no app needed)
- 🛒 **Order Management** — Full order lifecycle: place, track, update status, cancel
- 🔒 **Concurrency-Safe Stock** — Pessimistic locking prevents overselling under high traffic
- 📦 **Extras & Sauces Inventory** — Each extra and sauce has its own tracked stock
- 💸 **Promo Codes** — Percentage and fixed-amount discount codes with expiry and usage limits
- 🏦 **EFT Payments** — Bank details snapshotted onto orders at creation time, AES-256 encrypted at rest
- 📅 **Scheduled Delivery** — Users can schedule orders for a future time slot
- 🚫 **Daily Order Limit** — Admin-configurable daily kota cap with sold-out protection
- ⭐ **Product Reviews** — One review per user per product with rating and comment
- 📧 **Email Notifications** — OTP password reset emails via Resend SDK
- 🖼️ **Image Uploads** — Product images stored on Cloudinary
- ⚡ **Performance Optimized** — Caffeine caching, JOIN FETCH queries, response compression, HikariCP tuning
- 🔑 **Account Security** — Account locking, OTP-based password reset, bank details audit trail

---

## 🛠️ Tech Stack

| Layer | Technology |
|-------|-----------|
| Language | Java 17 |
| Framework | Spring Boot 3.2.5 |
| Security | Spring Security + JWT (jjwt 0.11.5) |
| Biometric Auth | WebAuthn — Yubico webauthn-server-core 2.6.0 |
| Database | PostgreSQL |
| ORM | Spring Data JPA / Hibernate |
| Caching | Caffeine |
| Email | Resend SDK 3.1.0 |
| Payments | Stripe Java SDK |
| Image Storage | Cloudinary |
| PDF Generation | OpenPDF |
| Boilerplate | Lombok |
| Testing | JUnit 5 + Mockito |
| Deployment | Render (Docker) |

---

## 📐 Architecture

```
┌─────────────────────────────────────────────────────┐
│                    Frontend (React)                  │
│              https://kasikotas-frondend.onrender.com │
└───────────────────────┬─────────────────────────────┘
                        │ HTTPS / REST API
┌───────────────────────▼─────────────────────────────┐
│              Spring Boot REST API                    │
│                                                      │
│  ┌─────────────┐  ┌──────────────┐  ┌────────────┐  │
│  │ Controllers │→ │   Services   │→ │Repositories│  │
│  └─────────────┘  └──────────────┘  └─────┬──────┘  │
│                                           │          │
│  ┌────────────────────────────────────────▼───────┐  │
│  │              PostgreSQL Database               │  │
│  └────────────────────────────────────────────────┘  │
│                                                      │
│  ┌──────────┐  ┌──────────┐  ┌────────────────────┐  │
│  │ Resend   │  │Cloudinary│  │  Caffeine Cache     │  │
│  │ (Email)  │  │ (Images) │  │  (User Orders)      │  │
│  └──────────┘  └──────────┘  └────────────────────┘  │
└─────────────────────────────────────────────────────┘
```

---

## 🗄️ Database Schema

| Table | Description |
|-------|-------------|
| `users` | Customer and admin accounts |
| `orders` | Customer orders with pricing breakdown |
| `order_items` | Individual items within an order |
| `products` | Kota products with stock and images |
| `extras` | Add-on items with their own inventory |
| `sauces` | Sauce options per order item |
| `product_extra_requirements` | Required extras per product |
| `reviews` | Product ratings and comments |
| `promo_codes` | Discount codes with usage tracking |
| `bank_details` | EFT bank accounts (AES-256 encrypted) |
| `bank_details_audit` | Audit log for every bank details change |
| `passkey_credentials` | Enrolled WebAuthn public keys per user |
| `webauthn_challenges` | Short-lived WebAuthn challenge rows |
| `password_reset_tokens` | OTP tokens for password reset |
| `daily_order_limit` | Admin-configured daily kota cap |

---

## 🔑 API Endpoints

### Auth
| Method | Endpoint | Auth | Description |
|--------|----------|------|-------------|
| POST | `/api/auth/login` | No | Password login |
| POST | `/api/auth/forgot-password` | No | Send OTP to email |
| POST | `/api/auth/verify-otp` | No | Verify OTP |
| POST | `/api/auth/reset-password` | No | Reset password with token |
| POST | `/api/auth/passkey/register/options` | No | Get passkey registration challenge |
| POST | `/api/auth/passkey/register/verify` | No | Complete passkey registration |
| POST | `/api/auth/passkey/login/options` | No | Get passkey login challenge |
| POST | `/api/auth/passkey/login/verify` | No | Login with passkey |
| GET | `/api/auth/passkey` | Yes | List enrolled passkeys |
| DELETE | `/api/auth/passkey/{id}` | Yes | Delete a passkey |

### Orders
| Method | Endpoint | Auth | Description |
|--------|----------|------|-------------|
| POST | `/api/orders` | Yes | Place a new order |
| GET | `/api/orders/user/{userId}` | Yes | Get orders for a user |
| GET | `/api/orders` | Admin | Get all orders |
| GET | `/api/orders/{id}` | Admin | Get order by ID |
| PUT | `/api/orders/{id}/status` | Admin | Update order status |
| DELETE | `/api/orders/{id}` | Admin | Delete an order |

### Products
| Method | Endpoint | Auth | Description |
|--------|----------|------|-------------|
| GET | `/api/products/get-all` | No | Get all products |
| GET | `/api/products/{id}/image` | No | Get product image |
| POST | `/api/products` | Admin | Create product |
| PUT | `/api/products/{id}` | Admin | Update product |
| DELETE | `/api/products/{id}` | Admin | Delete product |

### Reviews
| Method | Endpoint | Auth | Description |
|--------|----------|------|-------------|
| GET | `/api/products/{id}/reviews` | No | Get reviews for a product |
| GET | `/api/products/{id}/reviews/summary` | No | Get rating summary |
| POST | `/api/products/{id}/reviews` | Yes | Submit a review |
| DELETE | `/api/products/{id}/reviews` | Yes | Delete own review |

---

## ⚙️ Environment Variables

Set these in your Render dashboard (or `.env` for local development):

| Variable | Description |
|----------|-------------|
| `DATABASE_URL` | PostgreSQL connection URL |
| `DB_USERNAME` | Database username |
| `DB_PASSWORD` | Database password |
| `JWT_SECRET` | Secret key for signing JWT tokens |
| `RESEND_API_KEY` | Resend API key for sending emails |
| `BANK_ENCRYPTION_KEY` | Base64-encoded AES-256 key for bank details |
| `WEBAUTHN_RP_ID` | WebAuthn relying party ID (your domain, no protocol) |
| `WEBAUTHN_RP_NAME` | WebAuthn relying party display name |
| `WEBAUTHN_ALLOWED_ORIGINS` | Comma-separated list of allowed origins |
| `APP_BASE_URL` | Base URL of the backend (used for keep-alive ping) |

---

## 🏃 Running Locally

### Prerequisites
- Java 17+
- Maven 3.8+
- PostgreSQL

### Steps

```bash
# 1. Clone the repository
git clone https://github.com/your-username/KasiKotas.git
cd KasiKotas

# 2. Create a PostgreSQL database
createdb kasikotas_db

# 3. Set environment variables (or update application.properties for local dev)
export DATABASE_URL=jdbc:postgresql://localhost:5432/kasikotas_db
export DB_USERNAME=your_db_user
export DB_PASSWORD=your_db_password
export RESEND_API_KEY=your_resend_api_key
export JWT_SECRET=your_jwt_secret

# 4. Build and run
mvn spring-boot:run
```

The API will be available at `http://localhost:8080`.

---

## 🐳 Docker

```bash
# Build the image
docker build -t kasikotas .

# Run the container
docker run -p 8080:8080 \
  -e DATABASE_URL=jdbc:postgresql://host:5432/kasikotas_db \
  -e DB_USERNAME=user \
  -e DB_PASSWORD=password \
  -e RESEND_API_KEY=your_key \
  -e JWT_SECRET=your_secret \
  kasikotas
```

---

## 🧪 Running Tests

```bash
mvn test
```

---

## 🔐 Security Highlights

- Passwords hashed with **BCrypt**
- JWT tokens are **stateless** — no server-side sessions
- Bank account numbers **AES-256 encrypted** at rest with tamper-detection checksums
- WebAuthn passkeys eliminate password-based attack vectors (no phishing, no credential stuffing)
- **Pessimistic locking** on stock and daily limits prevents race conditions
- **Optimistic locking** (`@Version`) on all critical entities
- Account locking after suspicious activity
- All bank detail changes logged to an immutable audit table
- OTP tokens expire after **15 minutes** and are single-use

---

## 👤 Author

**Nhlanhla Maboya**
- GitHub: [@your-username](https://github.com/your-username)
- LinkedIn: [your-linkedin](https://linkedin.com/in/your-linkedin)

---

## 📄 License

This project is licensed under the MIT License.
