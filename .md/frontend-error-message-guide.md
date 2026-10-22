# Frontend: Displaying Backend Error Messages

## Context

The backend now includes the customer's first name directly inside error messages for:

- Sold out (daily limit reached)
- Only X kota left (partial capacity)
- Product sold out (stock)
- Extra sold out (stock)

Example response from the backend:

```json
{
  "message": "Sorry Thabo, we are sold out today. Please try again tomorrow.",
  "code": "ORDER_LIMIT_EXCEEDED",
  "path": "/api/orders"
}
```

The name comes from the authenticated user's database record — not from localStorage.

---

## What to do

Wherever you currently place an order (checkout, Yoco flow, etc.), catch the error response and display `error.message` directly. Do not prepend the name from localStorage — it is already in the message.

### fetch

```js
const res = await fetch('/api/orders', {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify(payload),
});

if (!res.ok) {
  const error = await res.json();
  showToast(error.message); // "Sorry Thabo, we are sold out today..."
}
```

### axios

```js
try {
  await axios.post('/api/orders', payload);
} catch (err) {
  const message = err.response?.data?.message ?? 'Something went wrong.';
  showToast(message); // "Sorry Thabo, we are sold out today..."
}
```

---

## Error codes you can check

If you want to handle specific errors differently (e.g. redirect vs toast):

| `code`                | Meaning                                      |
|-----------------------|----------------------------------------------|
| `ORDER_LIMIT_EXCEEDED`| Sold out or not enough kotas left today      |
| `INSUFFICIENT_STOCK`  | A product or extra ran out of stock          |
| `CONCURRENCY_CONFLICT`| High traffic — safe to retry                 |
| `BAD_REQUEST`         | Validation error in the request payload      |

```js
const { message, code } = err.response?.data ?? {};

if (code === 'ORDER_LIMIT_EXCEEDED' || code === 'INSUFFICIENT_STOCK') {
  showErrorModal(message); // Personalised message from backend
} else if (code === 'CONCURRENCY_CONFLICT') {
  showToast(message); // "High traffic right now. Please try again."
} else {
  showToast(message ?? 'Something went wrong.');
}
```

---

## What NOT to do

Do not do this — the name is already in the message:

```js
// ❌ Wrong — double name
const name = localStorage.getItem('firstName');
showToast(`Sorry ${name}, ${error.message}`);

// ✅ Correct — message already says "Sorry Thabo, ..."
showToast(error.message);
```
