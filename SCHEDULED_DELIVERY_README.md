# Scheduled Delivery Feature Implementation

This document describes the implementation of the scheduled delivery feature for the KasiKotas application.

## Features Implemented

### 1. Database Changes
- Added `scheduled_delivery_time` column to the `orders` table
- Created database migration script: `database_migration_scheduled_delivery.sql`
- Added database indexes for improved query performance

### 2. Backend API Changes

#### Updated Order Entity (`Order.java`)
- Added `scheduledDeliveryTime` field of type `LocalDateTime`
- Field is nullable (NULL indicates immediate delivery)

#### Updated Order Repository (`OrderRepository.java`)
- Added `findByScheduledDeliveryTimeBetweenAndStatus()` method for time range queries
- Added `findByScheduledDeliveryTimeIsNotNull()` method to find all scheduled orders

#### Updated Order Controller (`OrderController.java`)
- Modified `createOrder` endpoint to accept `scheduledDeliveryTime`
- Added validation for scheduled delivery time:
  - Must be in the future
  - Maximum 7 days in advance
  - Must be within business hours (9 AM - 8 PM)
- Returns appropriate error messages for invalid scheduled times

#### New Services and Controllers

##### DeliverySchedulingService
- Runs every 5 minutes to check for orders due for processing
- Automatically updates order status from PENDING to PROCESSING when delivery window approaches (30 minutes)
- Provides methods to query scheduled orders

##### DeliverySlotController
- **Endpoint**: `GET /api/delivery-slots/available?date=YYYY-MM-DD`
- Returns available time slots for a given date
- Validates date constraints (not in past, not more than 7 days ahead)

##### ScheduledDeliveryController (Admin Only)
- **Endpoint**: `GET /api/admin/scheduled-deliveries` - Get all scheduled orders
- **Endpoint**: `GET /api/admin/scheduled-deliveries/range` - Get orders in time range
- Requires ADMIN role for access

### 3. Application Configuration
- Added `@EnableScheduling` to `KasiKotasApplication.java`
- Enables the scheduled task processing for delivery management

## API Usage Examples

### Creating an Order with Scheduled Delivery

```json
POST /api/orders
{
  "userId": 1,
  "shippingAddress": "123 Main St",
  "paymentMethod": "cod",
  "scheduledDeliveryTime": "2025-07-22T14:00:00.000Z",
  "orderItems": [
    {
      "product": {"id": 1},
      "quantity": 2,
      "customizationNotes": "No onions",
      "selectedExtrasJson": "[]",
      "selectedSaucesJson": "[{\"id\":1,\"name\":\"Ketchup\"}]"
    }
  ]
}
```

### Creating an Immediate Delivery Order

```json
POST /api/orders
{
  "userId": 1,
  "shippingAddress": "123 Main St",
  "paymentMethod": "cod",
  "scheduledDeliveryTime": null,
  "orderItems": [...]
}
```

### Getting Available Time Slots

```http
GET /api/delivery-slots/available?date=2025-07-22
```

Response:
```json
["09:00", "10:00", "11:00", "12:00", "13:00", "14:00", "15:00", "16:00", "17:00", "18:00", "19:00", "20:00"]
```

### Admin: View Scheduled Orders

```http
GET /api/admin/scheduled-deliveries
```

```http
GET /api/admin/scheduled-deliveries/range?start=2025-07-22T09:00:00&end=2025-07-22T17:00:00&status=pending
```

## Business Rules

1. **Time Validation**:
   - Scheduled time must be in the future
   - Maximum 7 days in advance
   - Business hours: 9 AM to 8 PM only

2. **Processing Logic**:
   - Orders are automatically moved to PROCESSING status 30 minutes before scheduled delivery time
   - Background service checks every 5 minutes for due orders

3. **Availability**:
   - Time slots are generated hourly (9:00, 10:00, etc.)
   - No double-booking prevention implemented (can be added based on business needs)

## Database Migration

Run the SQL script `database_migration_scheduled_delivery.sql` to add the required database column:

```sql
ALTER TABLE orders ADD COLUMN scheduled_delivery_time TIMESTAMP NULL;
CREATE INDEX idx_orders_scheduled_delivery_time ON orders(scheduled_delivery_time);
```

## Security

- All scheduled delivery endpoints require authentication
- Admin-only endpoints are properly protected with `@PreAuthorize("hasRole('ADMIN')")`
- Input validation prevents invalid date/time values

## Testing Considerations

1. Test scheduled delivery time validation
2. Test automatic order status transitions
3. Test time slot availability API
4. Test edge cases (past dates, far future dates)
5. Test business hours validation
6. Verify proper handling of null scheduledDeliveryTime (immediate delivery)

## Future Enhancements

1. **Capacity Management**: Limit number of orders per time slot
2. **Notification System**: Email/SMS notifications before delivery
3. **Time Zone Support**: Handle different time zones
4. **Delivery Route Optimization**: Group orders by location and time
5. **Customer Rescheduling**: Allow customers to modify scheduled delivery times
