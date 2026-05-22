# Frontend integration and EFT bank details guide

This document lists the exact frontend changes required to work with the backend EFT snapshot approach, and performance improvements you should implement.

1) EFT bank details display and ordering flow

- When the user selects 'EFT' as payment method and places an order, the backend will store a snapshot of the selected bank details in the order (eftBankName, eftAccountName, eftAccountNumber, eftShapId, eftBranchCode). The frontend should:
  - On the order confirmation page (after successful order creation) display the EFT snapshot returned by the /api/orders POST response. Do NOT request bank details separately.
  - Show a clear message: "Your order has been placed. Please pay R{amount} to the following bank details for this order only. Use order #{orderId} as the reference." Include ShapID as an alternative.
  - If the API returns null or missing EFT fields in the response, show an error fallback: "EFT payment details are unavailable for this order. Please contact support and include order #{orderId}."

2) How to request and render product lists for performance

- Use pagination: request 24 or 48 items per page.
- Use lightweight product summary fields only (id, name, price, thumbnailUrl, isInStock, shortDescription).
- Implement skeleton UI: show placeholder cards immediately while the list fetches.
- Lazy-load images: use <img loading="lazy"> or IntersectionObserver to download images only once the card is near viewport.
- Use a CDN for images or ensure thumbnails have Cache-Control: public, max-age=31536000 and immutable when URLs are hashed.
- Use client-side caching: store last-seen product list in IndexedDB/localStorage and show it while fetching fresh data in background.

3) Handling missing EFT bank details errors

- If server responds with 'EFT payment details are unavailable' error when the order is placed, the frontend should:
  - Show a friendly message as above with contact email/phone and the order number.
  - Provide a visible retry button that calls a backend endpoint to refresh EFT details for that order (if backend supports it).

4) Security/UI best-practices

- Never allow the frontend to POST raw bank detail modifications. Only admins should be able to change bank details.
- Never display full bank details to unauthorized users.
- Avoid storing bank details in browser storage.

5) UX for switching bank details (admin flow)

- If admin needs to change bank details: have a separate admin UI that updates a bank_details resource via API. Bank details in existing orders must remain unchanged (snapshots).

6) Testing checklist for frontend QA

- Place order with EFT; confirm the order response contains eft_* fields and they render correctly in confirmation page.
- Place order with EFT when bank_details service is unavailable; confirm the fallback error message and order is still created but with warning.
- Test on slow network (simulate 3G) and confirm skeleton UI shows immediately and images load progressively.
- Test pagination/infinite scroll for correctness and performance.

7) Example order success payload (what frontend expects)

{
  "id": 282,
  "totalAmount": 50.0,
  "paymentMethod": "EFT",
  "eftBankName": "Capitec bank",
  "eftAccountName": "Martin Maboya",
  "eftAccountNumber": "1539228116",
  "eftShapId": "0677439994@capitec",
  "eftBranchCode": "470010"
}

8) Notes about reusing bank details in future orders

- Frontend can show a selectable list of 'Available bank accounts' in checkout (fetched from /api/bank-details). When user selects one, the backend will copy the snapshot into the order at creation.
- Do not rely on the bank_details table as authoritative for an existing order after creation — use the snapshot fields stored in the order.

---

If you want, I can also produce a small JS snippet for the order confirmation page that renders eft fields and falls back to the support message when missing.
