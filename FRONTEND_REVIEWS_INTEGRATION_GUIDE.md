# Frontend Reviews Integration Guide

This guide covers everything frontend needs so product reviews work end-to-end with the current backend.

## 1) What backend already provides

- Public read endpoints for reviews and review summary
- Authenticated customer write endpoints (create/update/delete)
- Product payload now includes review summary fields:
  - `averageRating` (number, rounded to 1 decimal)
  - `totalReviews` (number)
- One review per user per product (POST acts as upsert)

## 2) Base URL and auth

Set your API base URL (example production):

- `https://kasikotas-app.onrender.com`

Use JWT for protected endpoints:

- Header: `Authorization: Bearer <token>`
- Write endpoints require `CUSTOMER` role

## 3) Endpoints frontend should use

### Public (no token required)

- `GET /api/products/get-all`
  - Product list, includes `averageRating` and `totalReviews`
- `GET /api/products/{productId}`
  - Single product, includes `averageRating` and `totalReviews`
- `GET /api/products/{productId}/reviews`
  - Full review list for product
- `GET /api/products/{productId}/reviews/summary`
  - Summary only (`averageRating`, `totalReviews`)

### Protected (customer token required)

- `POST /api/products/{productId}/reviews`
  - Create or update current user's review
- `DELETE /api/products/{productId}/reviews/{reviewId}`
  - Delete current user's review

## 4) Request and response contracts

## 4.1 POST create/update review

### Request body

```json
{
  "rating": 5,
  "comment": "Best kota ever"
}
```

Rules:

- `rating` is required and must be `1..5`
- `comment` optional
- max `comment` length: `1000`

### Success responses

When created:

- HTTP `201`

When updated:

- HTTP `200`

Body for both:

```json
{
  "action": "CREATED",
  "review": {
    "id": 101,
    "productId": 4,
    "userId": 22,
    "reviewerName": "John Doe",
    "rating": 5,
    "comment": "Best kota ever",
    "createdAt": "2026-04-12T12:40:15.321",
    "updatedAt": "2026-04-12T12:40:15.321"
  }
}
```

`action` is:

- `CREATED` (new review)
- `UPDATED` (existing review changed)

### Error responses

- `400` validation/business errors
- `401` missing/invalid token
- `403` not `CUSTOMER` role
- `404` product not found

Typical error body:

```json
{
  "message": "Rating must be between 1 and 5."
}
```

## 4.2 GET reviews list

`GET /api/products/{productId}/reviews`

Response:

```json
[
  {
    "id": 101,
    "productId": 4,
    "userId": 22,
    "reviewerName": "John Doe",
    "rating": 5,
    "comment": "Best kota ever",
    "createdAt": "2026-04-12T12:40:15.321",
    "updatedAt": "2026-04-12T12:40:15.321"
  }
]
```

## 4.3 GET reviews summary

`GET /api/products/{productId}/reviews/summary`

Response:

```json
{
  "productId": 4,
  "averageRating": 4.7,
  "totalReviews": 38
}
```

## 5) Frontend implementation flow (recommended)

## 5.1 Product list/cards

1. Call `GET /api/products/get-all`
2. Render stars using:
   - `product.averageRating`
   - `product.totalReviews`
3. If either field is missing in older cached data, fallback to:
   - `GET /api/products/{productId}/reviews/summary`

Recommended fallback rule:

- If `averageRating` is `undefined` or `totalReviews` is `undefined`, call summary endpoint.
- If values are present (including `0`), do not call fallback.

## 5.2 Product detail page

1. Load product by id (`GET /api/products/{id}`)
2. Load review list (`GET /api/products/{id}/reviews`)
3. If user logged in:
   - Find `myReview` by matching `review.userId === currentUserId`
   - Pre-fill form for edit when `myReview` exists

## 5.3 Submit review (create/update)

+**Important:** Customer can ONLY review products if they have a completed order (status = `DELIVERED` or `COLLECTED`) containing that product.

1. Check if user has eligible order for this product:
   - If not eligible, show: "You can only review products you have ordered and received."
   - Hide review form if ineligible
2. Validate client-side (`rating 1..5`, comment length <= 1000)
3. `POST /api/products/{id}/reviews` with JWT
4. On success:
   - if `action === "CREATED"` show "Review added"
   - if `action === "UPDATED"` show "Review updated"
5. Refresh these in parallel:
   - product summary display (from product response or summary endpoint)
   - review list

If backend returns `400` with message "You can only review products you have ordered and received.", user is ineligible.

## 5.4 Delete review

1. Call `DELETE /api/products/{id}/reviews/{reviewId}` with JWT
2. On `204`, refresh list + summary

## 6) UI/UX rules for 100% reliability

- Disable submit button while request in progress
- Show inline validation errors before API call
- Handle role/auth states:
  - not logged in: show read-only reviews + login prompt
  - logged in but not customer: hide write actions
- Keep review text optional, but keep rating required
- On API error, display backend `message` when present

## 7) Suggested TypeScript types

```ts
export type ReviewResponse = {
  id: number;
  productId: number;
  userId: number;
  reviewerName: string;
  rating: number;
  comment: string | null;
  createdAt: string;
  updatedAt: string;
};

export type ReviewSummaryResponse = {
  productId: number;
  averageRating: number;
  totalReviews: number;
};

export type ReviewUpsertResponse = {
  action: "CREATED" | "UPDATED";
  review: ReviewResponse;
};

export type CreateReviewRequest = {
  rating: number;
  comment?: string;
};
```

## 8) Quick API examples

## Get reviews

```bash
curl -X GET "https://kasikotas-app.onrender.com/api/products/4/reviews"
```

## Create or update review

```bash
curl -X POST "https://kasikotas-app.onrender.com/api/products/4/reviews" \
  -H "Authorization: Bearer <JWT_TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{"rating":5,"comment":"Fire kota"}'
```

## Delete review

```bash
curl -X DELETE "https://kasikotas-app.onrender.com/api/products/4/reviews/101" \
  -H "Authorization: Bearer <JWT_TOKEN>"
```

## 9) QA checklist for frontend

- Product cards show rating + count without extra per-card calls
- New review returns `201` + `action=CREATED`
- Editing same product review returns `200` + `action=UPDATED`
- Unauthorized write returns proper message handling (`401/403`)
- Invalid rating shows validation error (`400`)
- After create/update/delete, list and summary refresh correctly
- Zero-reviews state renders cleanly (`averageRating=0`, `totalReviews=0`)
