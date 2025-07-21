// Example test requests for the scheduled delivery feature
// These are curl commands you can use to test the API endpoints

// 1. Create an order with scheduled delivery
curl -X POST http://localhost:8080/api/orders \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer YOUR_JWT_TOKEN" \
  -d '{
    "userId": 1,
    "shippingAddress": "123 Test Street, Test City",
    "paymentMethod": "cod",
    "scheduledDeliveryTime": "2025-07-22T14:00:00",
    "orderItems": [
      {
        "product": {"id": 1},
        "quantity": 2,
        "customizationNotes": "Extra spicy",
        "selectedExtrasJson": "[]",
        "selectedSaucesJson": "[]"
      }
    ]
  }'

// 2. Create an immediate delivery order (scheduledDeliveryTime is null)
curl -X POST http://localhost:8080/api/orders \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer YOUR_JWT_TOKEN" \
  -d '{
    "userId": 1,
    "shippingAddress": "123 Test Street, Test City",
    "paymentMethod": "eft",
    "scheduledDeliveryTime": null,
    "orderItems": [
      {
        "product": {"id": 1},
        "quantity": 1,
        "customizationNotes": "",
        "selectedExtrasJson": "[]",
        "selectedSaucesJson": "[]"
      }
    ]
  }'

// 3. Get available delivery time slots for tomorrow
curl -X GET "http://localhost:8080/api/delivery-slots/available?date=2025-07-22" \
  -H "Authorization: Bearer YOUR_JWT_TOKEN"

// 4. Admin: View all scheduled orders
curl -X GET http://localhost:8080/api/admin/scheduled-deliveries \
  -H "Authorization: Bearer YOUR_ADMIN_JWT_TOKEN"

// 5. Admin: View orders scheduled for a specific time range
curl -X GET "http://localhost:8080/api/admin/scheduled-deliveries/range?start=2025-07-22T09:00:00&end=2025-07-22T17:00:00&status=pending" \
  -H "Authorization: Bearer YOUR_ADMIN_JWT_TOKEN"

// Expected Error Responses:

// Trying to schedule in the past
curl -X POST http://localhost:8080/api/orders \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer YOUR_JWT_TOKEN" \
  -d '{
    "userId": 1,
    "shippingAddress": "123 Test Street",
    "paymentMethod": "cod",
    "scheduledDeliveryTime": "2025-07-20T14:00:00",
    "orderItems": [...]
  }'
// Expected: {"message": "Scheduled delivery time must be in the future"}

// Trying to schedule outside business hours
curl -X POST http://localhost:8080/api/orders \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer YOUR_JWT_TOKEN" \
  -d '{
    "userId": 1,
    "shippingAddress": "123 Test Street",
    "paymentMethod": "cod",
    "scheduledDeliveryTime": "2025-07-22T22:00:00",
    "orderItems": [...]
  }'
// Expected: {"message": "Scheduled delivery must be within business hours (9 AM - 8 PM)"}

// Trying to schedule too far in advance
curl -X POST http://localhost:8080/api/orders \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer YOUR_JWT_TOKEN" \
  -d '{
    "userId": 1,
    "shippingAddress": "123 Test Street",
    "paymentMethod": "cod",
    "scheduledDeliveryTime": "2025-08-01T14:00:00",
    "orderItems": [...]
  }'
// Expected: {"message": "Scheduled delivery can only be set up to 7 days in advance"}
