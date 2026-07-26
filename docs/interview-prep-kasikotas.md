# KasiKotas — Interview Preparation Guide

## What is KasiKotas?

KasiKotas is a **food ordering web application** for ordering kotas (a popular South African street food).
It is a full-stack project with a Spring Boot REST API backend deployed on Render, backed by a PostgreSQL database.

---

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Language | Java 17 |
| Framework | Spring Boot 3.2.5 |
| Security | Spring Security + JWT (stateless) |
| Database | PostgreSQL + JPA/Hibernate |
| Caching | Caffeine (in-memory) |
| Biometric Auth | WebAuthn / Passkey (Yubico webauthn-server-core 2.6.0) |
| Payments | Stripe |
| Image Storage | Cloudinary |
| PDF Generation | OpenPDF |
| Boilerplate | Lombok |
| Testing | JUnit + Mockito |
| Deployment | Render (Docker) |

---

## Key Features — Talk About These

### 1. JWT Authentication & Security
- Stateless JWT-based auth with a custom `JwtAuthFilter`
- Role-based access control (`CUSTOMER` vs `ADMIN`) using `@PreAuthorize`
- Account locking / suspension feature
- Password reset via OTP sent to email

### 2. Passkey / Biometric Login (WebAuthn)
- Full WebAuthn registration and assertion flow using Yubico's library
- Users enroll fingerprint or Face ID after a successful password login
- Subsequent logins use biometrics — no password needed
- Challenges stored in the database with a 5-minute expiry TTL
- Works on any modern mobile browser over HTTPS — no app required

### 3. Order Management with Concurrency Safety
- Pessimistic locking on stock decrements to prevent overselling under high traffic
- Optimistic locking (`@Version`) on Order and User entities for concurrent updates
- Server-side price recalculation — never trusts the frontend for financial data
- Inventory automatically restored when an order is cancelled or deleted

### 4. Daily Order Limit (Sold Out Feature)
- Admin sets a daily kota limit
- Orders are blocked once the limit is reached
- Pessimistic locking prevents race conditions when multiple users order simultaneously

### 5. Promo Code System
- Supports percentage and fixed-amount discounts
- Validated entirely server-side during order creation

### 6. EFT Payment with Bank Details Snapshot
- Bank details are snapshotted onto the order at creation time
- Bank details are AES-256 encrypted at rest
- A random bank account is assigned server-side — frontend never chooses it

### 7. Performance Optimizations
- Caffeine caching on user orders (`@Cacheable`) with cache eviction on mutations
- JOIN FETCH queries to eliminate N+1 query problems
- HTTP response compression (60–80% size reduction)
- HikariCP connection pool tuning (max 10, min idle 2)
- Database indices on frequently queried columns

### 8. Scheduled Delivery
- Users can schedule a delivery for a future time slot
- A scheduling service processes upcoming orders using `@EnableScheduling`

### 9. Products, Extras & Sauces Inventory
- Products have required extras (e.g., bread type) tracked with their own inventory
- Extras and sauces stock is decremented atomically per order
- Stock is restored on cancellation or deletion

---

## Interview Questions & Your Answers

### "Tell me about a challenge you faced and how you solved it."

> One challenge was preventing overselling when multiple users order the same product simultaneously.
> I solved this using pessimistic locking at the database level — the stock decrement only succeeds
> if current stock is sufficient, and it holds a lock during that check. I also used optimistic locking
> with `@Version` on entities to handle concurrent updates gracefully and throw a meaningful error
> instead of silently corrupting data.

---

### "How did you handle security in this project?"

> I used Spring Security with stateless JWT authentication. Every protected endpoint goes through
> a custom JWT filter. Role-based access is enforced with `@PreAuthorize`. Sensitive data like bank
> account numbers are AES-256 encrypted. I also implemented WebAuthn passkeys so users can log in
> with fingerprint or Face ID, which eliminates password-based attack vectors like phishing and
> credential stuffing entirely.

---

### "How did you ensure the application performs well?"

> I added Caffeine caching for user orders to avoid repeated database hits. I rewrote a key query
> to use JOIN FETCH instead of lazy loading, which eliminated N+1 query problems. I enabled HTTP
> response compression, tuned the HikariCP connection pool, and added database indices on columns
> used in WHERE clauses and JOINs.

---

### "What is WebAuthn and why did you implement it?"

> WebAuthn is a W3C browser standard that allows websites to use device biometrics — fingerprint
> or Face ID — for authentication. I implemented it because it is more secure than passwords
> (no phishing, no credential stuffing) and gives mobile users a much better experience.
> The backend uses Yubico's library to generate challenges, verify authenticator responses,
> and store public key credentials. The browser handles the biometric prompt natively.

---

### "This is a web app — how does biometric login work without a mobile app?"

> The biometric prompt is triggered by the browser via the `navigator.credentials` API, not a
> native app SDK. As long as the site is served over HTTPS, any modern mobile browser (Chrome,
> Safari) can use the device's fingerprint sensor or Face ID. No app installation is needed.
> It is the same standard used by Google, GitHub, and most major platforms today.

---

### "How does your order pricing work — can the user manipulate the price?"

> No. All pricing is calculated server-side. When an order is submitted, the backend fetches the
> current product prices from the database, recalculates the subtotal, applies the delivery fee,
> validates and applies any promo code discount, and sets the final total. The frontend never
> sends a price — it only sends product IDs and quantities.

---

### "What would you improve if you had more time?"

> I would add Redis for distributed caching so the app can scale horizontally across multiple
> instances. I would also implement proper async email notifications using Spring's `@Async` —
> currently email sending is stubbed out. And I would add more comprehensive integration tests
> covering the full order flow end-to-end.

---

## Key Numbers to Remember

| Detail | Value |
|--------|-------|
| WebAuthn challenge TTL | 5 minutes |
| Delivery fee | R5 |
| Cache TTL | 60 seconds |
| Cache max entries | 2 000 |
| DB connection pool max | 10 |
| DB connection pool min idle | 2 |
| JWT | Stateless, role embedded in token |
| Encryption | AES-256 for bank details |
| Java version | 17 |
| Spring Boot version | 3.2.5 |

---

## One-Line Project Summary (use this to open)

> "KasiKotas is a full-stack food ordering web application I built from scratch using Java Spring Boot,
> PostgreSQL, and JWT security. It includes features like biometric passkey login, concurrency-safe
> order processing, server-side pricing, and a daily order limit system — all deployed on Render."

---

Good luck on 14 July!
