# Frontend Product Update Guide

This guide explains how the admin frontend should update products (including stock/quantity) without errors.

## Endpoint

- Base path: `/api/products/{id}`
- Auth: Admin JWT/session required (`hasRole('ADMIN')`)

## Option A (Recommended): JSON Update

Use this when updating text/number fields (name, description, price, stock). No image required.

### Allowed fields

- `name`
- `description`
- `price`
- `stock`

### Example request

```json
{
  "stock": 12
}
```

### Fetch example

```javascript
export async function updateProductJson(productId, payload, token) {
  const res = await fetch(`/api/products/${productId}`, {
    method: 'PUT',
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${token}`,
    },
    body: JSON.stringify(payload),
  });

  const data = await res.json();
  if (!res.ok) {
    throw new Error(data?.message || 'Failed to update product.');
  }
  return data;
}
```

## Option B: Multipart Update (Image + Fields)

Use this when you want to update the image (and optionally other fields).

### Required fields

- `name`
- `description`
- `price`
- `stock`

### Optional file

- `imageFile`

### Fetch example

```javascript
export async function updateProductWithImage(productId, values, imageFile, token) {
  const form = new FormData();
  form.append('name', values.name);
  form.append('description', values.description);
  form.append('price', String(values.price));
  form.append('stock', String(values.stock));
  if (imageFile) {
    form.append('imageFile', imageFile);
  }

  const res = await fetch(`/api/products/${productId}`, {
    method: 'PUT',
    headers: {
      Authorization: `Bearer ${token}`,
    },
    body: form,
  });

  const data = await res.json();
  if (!res.ok) {
    throw new Error(data?.message || 'Failed to update product.');
  }
  return data;
}
```

## Error Handling (Important)

- Use the backend error payload `message` (from `ApiError`) for user feedback.
- Do not show success on HTTP 200 only; always update UI from the response payload.

## Common Causes of 500 Errors

1. Sending JSON to the old multipart-only endpoint.
2. Missing required fields when using multipart update.
3. `price` or `stock` being negative, or `price` being 0.

## Quick Troubleshooting

- Confirm `Content-Type` is `application/json` for JSON updates.
- Confirm your token is for an admin user.
- Inspect the response body in devtools for the real error message.

