# Extras Inventory + Frontend Integration Guide

## What this solves

This implementation adds real inventory tracking for extras (example: Russian) and links extras to products that require them.

It guarantees:

- Extra stock decreases when an order consumes that extra.
- This includes both:
  - required extras for a product (ingredient dependency), and
  - optional extras selected by the customer.
- When an extra reaches `0`, products that require it become unavailable.
- Overselling is prevented during concurrent requests.

---

## Backend flow (how it works)

### 1) Extras now have stock

`Extra` now includes:

- `stock` (persisted)
- `available` (computed, transient: `stock > 0`)

Returned from:

- `GET /api/extras`
- `GET /api/extras/{id}`

### 2) Product -> required extra mapping

A new mapping table links products to required extras:

- table: `product_extra_requirements`
- columns:
  - `product_id`
  - `extra_id`
  - `units_required`

Meaning:

- If product X requires Russian `x1`, each quantity of product X consumes 1 Russian.

API:

- `GET /api/products/{productId}/required-extras` (authenticated)
- `PUT /api/products/{productId}/required-extras` (admin only)

`PUT` body example:

```json
[
  { "extraId": 1, "unitsRequired": 1 },
  { "extraId": 3, "unitsRequired": 2 }
]
```

### 3) Order creation inventory deductions

During `createOrder`:

1. Product stock is decremented atomically (`UPDATE ... WHERE stock >= qty`).
2. Required extras are calculated per order item and accumulated.
3. Selected optional extras are parsed from `selectedExtrasJson` and accumulated.
4. Extra stock is decremented atomically per extra (`UPDATE ... WHERE stock >= demand`).
5. If any decrement fails, request fails and transaction rolls back.

So you never get partial/dirty decrements if order fails midway.

### 4) Product availability now accounts for required extras

Product response includes:

- `available`
- `effectiveStock`

Rules:

- `available = product.stock > 0` AND no required-extra shortage.
- `effectiveStock = product.stock` when available, else `0`.

So if Russian stock is 0 and a product requires Russian, that product becomes out-of-stock even if product stock is still > 0.

---

## Frontend integration (must-do)

## Checklist

- [ ] Use `available` / `effectiveStock` from products API for stock UI.
- [ ] Use extras `stock` / `available` from extras API.
- [ ] Send selected extras in order payload with stable `id` (preferred).
- [ ] Keep optional extra quantity > 0 when sending.
- [ ] Show backend error messages when stock is insufficient.
- [ ] Refresh products + extras after order success/failure.

### A) Product listing / cart guards

Use `GET /api/products/get-all` and drive UI from:

- `product.available`
- `product.effectiveStock`

UI behavior:

- If `available === false` or `effectiveStock <= 0`:
  - disable Add to Cart
  - show "Out of stock"

Do not rely only on old `product.stock` for customer-facing availability.

### B) Extras picker behavior

Use `GET /api/extras` and for each extra:

- disable extra if `available === false` or `stock <= 0`
- show remaining stock badge if you want (example: "3 left")

### C) Order payload format for extras

For each `orderItem`, send `selectedExtrasJson` as a JSON string.

Preferred item format inside that JSON array:

```json
[
  { "id": 1, "name": "Russian", "quantity": 1 }
]
```

Notes:

- `id` is preferred and safest.
- `name` fallback is supported, but keep `id` whenever possible.
- `quantity` means units per kota for that order item (default 1 when omitted).
- Quantity must be > 0.

### D) Error handling (important)

If backend returns 400 with messages like:

- `Insufficient stock for product: ...`
- `Insufficient stock for extra: ...`

Frontend should:

1. Show toast/dialog with backend message.
2. Refetch products and extras immediately.
3. Reconcile cart quantities with latest stock/availability.

### E) Required-extra configuration (admin UI)

Admin can configure product dependencies using:

- `PUT /api/products/{productId}/required-extras`

Recommended admin UX:

- Multi-select extras per product.
- Input `unitsRequired` per selected extra.
- Save via a single PUT replacing full requirement set.

---

## Security behavior (current)

- `PUT /api/products/{productId}/required-extras` -> admin role required.
- `GET /api/products/{productId}/required-extras` -> authenticated (not public).

If you want this GET endpoint public, backend security config must explicitly permit it.

---

## Database migration

Run:

- `database_migration_extra_inventory_tracking.sql`

It does:

- adds `extras.stock`
- creates `product_extra_requirements`
- adds indexes

---

## End-to-end scenario (your Russian example)

Given:

- Russian extra stock = 10
- Product `Big Kota` requires Russian x1

Then:

1. User orders 1 `Big Kota` -> Russian becomes 9.
2. User adds Russian as optional extra too (`quantity: 1`) -> Russian decreases by another 1.
3. When Russian reaches 0:
   - all products requiring Russian return `available=false` and `effectiveStock=0`
   - optional Russian extra also returns unavailable.

This is exactly the behavior needed to prevent selling unavailable ingredient-based products.

---

## Frontend anti-race recommendation

Even with perfect backend locking, frontend should reduce user confusion by:

- Rechecking products/extras right before final place-order click.
- Disabling submit button while request is in-flight.
- Handling stock failure response gracefully and refreshing data.

This keeps UX clean under high concurrency while backend enforces correctness.
