# KasiKotas — Complete Entity Relationship Diagram (ERD)

## Entity Relationship Diagram (Mermaid)

```mermaid
erDiagram

    users {
        BIGINT id PK
        VARCHAR email UK
        VARCHAR password
        VARCHAR firstName
        VARCHAR lastName
        VARCHAR address
        VARCHAR roomNumber
        VARCHAR phoneNumber
        VARCHAR role
        BOOLEAN isLocked
        BIGINT version
    }

    products {
        BIGINT id PK
        VARCHAR name
        TEXT description
        DOUBLE price
        VARCHAR imageUrl
        INTEGER stock
        BLOB image
        VARCHAR imageType
        BIGINT version
    }

    extras {
        BIGINT id PK
        VARCHAR name UK
        DOUBLE price
        INTEGER stock
        TEXT description
        BIGINT version
    }

    sauces {
        BIGINT id PK
        VARCHAR name UK
        DOUBLE price
        TEXT description
        BIGINT version
    }

    product_extra_requirements {
        BIGINT id PK
        BIGINT product_id FK
        BIGINT extra_id FK
        INTEGER units_required
    }

    orders {
        BIGINT id PK
        BIGINT user_id FK
        TIMESTAMP order_date
        VARCHAR status
        DOUBLE total_amount
        DOUBLE subtotal
        DOUBLE delivery_fee
        DOUBLE discount_amount
        TEXT shipping_address
        VARCHAR payment_method
        VARCHAR delivery_method
        TIMESTAMP scheduled_delivery_time
        VARCHAR promo_code
        BIGINT eft_bank_details_id FK
        VARCHAR eft_bank_name
        VARCHAR eft_account_name
        VARCHAR eft_account_number
        VARCHAR eft_shap_id
        VARCHAR eft_branch_code
        BIGINT version
    }

    order_items {
        BIGINT id PK
        BIGINT order_id FK
        BIGINT product_id FK
        INTEGER quantity
        DOUBLE price_at_time_of_order
        TEXT customization_notes
        TEXT selected_extras_json
        TEXT selected_sauces_json
    }

    reviews {
        BIGINT id PK
        BIGINT product_id FK
        BIGINT user_id FK
        INTEGER rating
        TEXT comment
        TIMESTAMP created_at
        TIMESTAMP updated_at
        BIGINT version
    }

    promo_codes {
        BIGINT id PK
        VARCHAR code UK
        DOUBLE discount_amount
        INTEGER percentage_discount
        INTEGER max_usages
        INTEGER usage_count
        DATE expiry_date
        DOUBLE minimum_order_amount
        TEXT description
        BIGINT version
    }

    bank_details {
        BIGINT id PK
        VARCHAR bank_name
        VARCHAR account_name
        VARCHAR account_number UK
        VARCHAR shap_id
        VARCHAR branch_code
        VARCHAR account_number_checksum
        VARCHAR account_name_checksum
        VARCHAR bank_name_checksum
        TIMESTAMP last_verified_at
        BOOLEAN is_archived
        BIGINT version
    }

    bank_details_audit {
        BIGINT id PK
        BIGINT bank_details_id
        VARCHAR action
        VARCHAR actor_username
        TIMESTAMP changed_at
        TEXT before_snapshot_json
        TEXT after_snapshot_json
    }

    passkey_credentials {
        BIGINT id PK
        BIGINT user_id FK
        VARCHAR credential_id UK
        TEXT public_key
        BIGINT sign_count
        VARCHAR transports
        VARCHAR nickname
        TIMESTAMP created_at
        TIMESTAMP last_used_at
    }

    webauthn_challenges {
        VARCHAR request_id PK
        VARCHAR email
        BIGINT user_id
        VARCHAR challenge
        VARCHAR type
        TEXT request_json
        TIMESTAMP created_at
        TIMESTAMP expires_at
    }

    password_reset_tokens {
        BIGINT id PK
        BIGINT user_id
        VARCHAR email
        VARCHAR token UK
        VARCHAR otp
        TIMESTAMP expires_at
        BOOLEAN used
        TIMESTAMP created_at
    }

    daily_order_limit {
        BIGINT id PK
        INTEGER limit_value
    }

    %% Relationships
    users ||--o{ orders : "places"
    users ||--o{ reviews : "writes"
    users ||--o{ passkey_credentials : "enrolls"

    orders ||--|{ order_items : "contains"
    orders }o--o| bank_details : "snapshots EFT from"

    order_items }o--|| products : "references"

    products ||--o{ reviews : "receives"
    products ||--o{ product_extra_requirements : "requires"

    extras ||--o{ product_extra_requirements : "used in"

    bank_details ||--o{ bank_details_audit : "audited by"
```

---

## Data Flow — How Everything Connects

### 1. User Registration & Login
```
users
  └── password_reset_tokens   (for OTP / forgot password flow)
  └── passkey_credentials     (for fingerprint / Face ID login)
  └── webauthn_challenges     (temporary challenge during passkey registration or login)
```

### 2. Placing an Order
```
users
  └── orders
        ├── order_items
        │     └── products          (price snapshotted at order time)
        │           └── extras      (selected extras stored as JSON in order_items)
        │           └── sauces      (selected sauces stored as JSON in order_items)
        ├── bank_details            (EFT only — account snapshotted onto order)
        └── promo_codes             (validated and discount applied server-side)
```

### 3. Product Catalogue
```
products
  └── product_extra_requirements   (defines which extras are REQUIRED per product)
        └── extras                 (the actual extra item with its own stock)
```

### 4. Reviews
```
users ──┐
        ├──► reviews ◄── products
```
One user can review one product once (unique constraint on product_id + user_id).

### 5. Security & Audit
```
bank_details
  └── bank_details_audit           (every CREATE / UPDATE / DELETE is logged)

users
  └── passkey_credentials          (stored public key after fingerprint enrollment)
  └── webauthn_challenges          (short-lived challenge rows, deleted after use)
  └── password_reset_tokens        (OTP tokens for password reset, expire after use)
```

### 6. Admin Controls
```
daily_order_limit                  (single row — admin sets max kotas per day)
promo_codes                        (admin creates discount codes)
bank_details                       (admin manages EFT accounts)
```

---

## Relationships Summary

| Table | Relates To | Type | Description |
|-------|-----------|------|-------------|
| users | orders | One-to-Many | A user places many orders |
| users | reviews | One-to-Many | A user writes many reviews |
| users | passkey_credentials | One-to-Many | A user can enroll multiple passkeys |
| orders | order_items | One-to-Many | An order contains many items |
| orders | bank_details | Many-to-One | EFT orders reference one bank account |
| order_items | products | Many-to-One | Each item references one product |
| products | reviews | One-to-Many | A product receives many reviews |
| products | product_extra_requirements | One-to-Many | A product can require many extras |
| extras | product_extra_requirements | One-to-Many | An extra can be required by many products |
| bank_details | bank_details_audit | One-to-Many | Every change to bank details is audited |

---

## Key Design Decisions

- **EFT Bank Details Snapshot** — When an EFT order is placed, the bank account details
  (name, number, branch code) are copied directly onto the order row. This means even if
  the admin later changes or deletes the bank account, the original payment details are
  preserved on the order forever.

- **Extras & Sauces as JSON** — Selected extras and sauces per order item are stored as
  JSON strings (`selected_extras_json`, `selected_sauces_json`) rather than separate join
  tables. This keeps the schema simple while still capturing the full selection at order time.

- **Passkey Challenges are Temporary** — `webauthn_challenges` rows are deleted immediately
  after they are consumed (used once). They also expire after 5 minutes if unused.

- **Reviews are Unique per User per Product** — A database unique constraint on
  `(product_id, user_id)` ensures one review per user per product.

- **Daily Order Limit is a Single Row** — `daily_order_limit` holds one record that the
  admin updates. It is read with a pessimistic lock during order creation to prevent
  race conditions.

- **Optimistic Locking** — `users`, `orders`, `products`, `extras`, `sauces`, `promo_codes`,
  `bank_details`, and `reviews` all have a `version` column for optimistic locking.
