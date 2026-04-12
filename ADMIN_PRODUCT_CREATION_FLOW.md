# Admin Product Creation Flow (Start to End)

## Checklist

- [x] Explain request entry and security checks
- [x] Explain controller parsing and service validation
- [x] Explain image handling and DB save
- [x] Explain response/error outcomes
- [x] Explain post-create behavior with required extras and availability

---

## 1) Admin sends create request

Frontend sends:

- `POST /api/products`
- Content type: `multipart/form-data`

Expected fields (from `ProductController.createProduct(...)`):

- `name`
- `description`
- `price`
- `stock`
- `imageFile` (optional)

---

## 2) Security check happens first

`ProductController.createProduct(...)` is protected with:

- `@PreAuthorize("hasRole('ADMIN')")`

So only authenticated users with `ADMIN` role can create products.

---

## 3) Controller builds Product object

In `src/main/java/kasiKotas/controller/ProductController.java`:

- A new `Product` object is created.
- Controller sets `name`, `description`, `price`, `stock`.
- Controller passes product + `imageFile` to `ProductService.createProduct(...)`.

---

## 4) Service validates business rules

In `src/main/java/kasiKotas/service/ProductService.java`, `createProduct(...)` validates:

- name is not empty
- description is not empty
- price is positive (`> 0`)
- stock is not negative (`>= 0`)

If validation fails:

- `IllegalArgumentException` is thrown
- controller returns `400 Bad Request`

---

## 5) Image handling (if provided)

If `imageFile` exists:

- max size checked (`<= 20MB`)
- image bytes are stored in `product.image`
- MIME type stored in `product.imageType`
- `imageUrl` is set to `null` (current design uses blob storage)

---

## 6) Persist to database

`productRepository.save(product)` runs:

- new row inserted into `products`
- DB generates `id`
- optimistic lock `version` is maintained by JPA

On success controller returns:

- `201 Created`
- response body = saved product JSON

---

## 7) Error paths

Possible outcomes:

- `400 Bad Request` -> invalid name/description/price/stock/image
- `409 Conflict` -> optimistic locking conflict

---

## 8) What admin must do next (important)

After product creation, if this product depends on extras (example: Russian ingredient), admin should configure required extras:

- `PUT /api/products/{productId}/required-extras` (admin-only)

Example body:

```json
[
  { "extraId": 1, "unitsRequired": 1 }
]
```

This links product -> required ingredient consumption.

---

## 9) Availability behavior after setup

When frontend calls `GET /api/products/get-all`, product response includes:

- `available`
- `effectiveStock`

Computed behavior:

- product unavailable if product stock is 0
- product unavailable if any required extra stock is insufficient

So a product can become out of stock due to required extra depletion, even when `product.stock` is still greater than 0.

---

## End result

When admin adds a product successfully:

- product exists in `products`
- stock initialized
- optional image stored
- product is orderable
- if required extras are mapped, stock + ingredient availability rules are enforced automatically during ordering

