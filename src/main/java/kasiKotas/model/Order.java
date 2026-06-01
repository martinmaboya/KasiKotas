// src/main/java/kasiKotas/model/Order.java
package kasiKotas.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.util.StringUtils; // Added for isValid() in BankDetails

import java.time.LocalDateTime;
import java.util.List;

/**
 * Represents a customer's order in the KasiKotas system.
 * This is a JPA Entity, mapping to an 'orders' table in the database.
 *
 * Includes relationships to User and OrderItems, and now includes promo code and financial breakdown fields.
 */
@Entity
@Table(name = "orders")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Many-to-one relationship with User
    // FetchType.LAZY means the User data is loaded only when it's accessed.
    // @JoinColumn specifies the foreign key column in the 'orders' table.
    // JsonIgnoreProperties is crucial here to prevent infinite recursion/lazy loading issues
    // when serializing Order (which has a User) and User (which might have Orders).
    @ManyToOne(fetch = FetchType.LAZY) // Keeping LAZY as it's generally better for performance
    @JoinColumn(name = "user_id", nullable = false)
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler", "orders", "password", "version"})
    private User user;

    @Column(name = "order_date", nullable = false)
    private LocalDateTime orderDate;

    @Enumerated(EnumType.STRING) // Stores the enum name (e.g., "PENDING") as a string in the DB
    @Column(nullable = false)
    private OrderStatus status; // PENDING, PROCESSING, DELIVERED, CANCELLED, etc.

    @Column(nullable = false)
    private Double totalAmount;

    @Column(columnDefinition = "TEXT") // Use TEXT for potentially longer addresses
    private String shippingAddress;

    // Field for payment method (e.g., "COD", "EFT")
    @Column(nullable = false)
    private String paymentMethod; // e.g., "cod", "eft"

    // EFT orders store the bank account selected at order creation time.
    // This ManyToOne relationship is for linking to an existing BankDetails entity.
    // The insertable/updatable = false means Hibernate won't manage the eft_bank_details_id column via this relationship.
    // It expects the column to be managed separately (e.g., via the eftBankDetailsId field below).
    @JsonIgnore // Still ignore this to prevent serialization issues and rely on custom getter
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "eft_bank_details_id", insertable = false, updatable = false)
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private BankDetails eftBankDetails; // This field will be populated by Hibernate on load if eftBankDetailsId is set

    // These fields are snapshots of the bank details at the time of order creation
    // They should be included in the JSON response, so @JsonIgnore is removed.
    @Column(name = "eft_bank_details_id")
    private Long eftBankDetailsId;

    @Column(name = "eft_bank_name")
    private String eftBankName;

    @Column(name = "eft_account_name")
    private String eftAccountName;

    @Column(name = "eft_account_number")
    private String eftAccountNumber;

    @Column(name = "eft_shap_id")
    private String eftShapId;

    @Column(name = "eft_branch_code")
    private String eftBranchCode;

    // Field for delivery method (e.g., "DELIVERY", "COLLECTION")
    @Column
    private String deliveryMethod; // e.g., "DELIVERY", "COLLECTION"

    // Field for scheduled delivery time (null for immediate delivery)
    @Column(name = "scheduled_delivery_time")
    private LocalDateTime scheduledDeliveryTime;

    // NEW: Promo code and financial breakdown fields
    @Column
    private String promoCode; // The promo code applied (e.g., "SAVE20")

    @Column
    private Double subtotal; // Order subtotal (before delivery and discount)

    @Column
    private Double deliveryFee; // Delivery fee amount

    @Column
    private Double discountAmount; // Discount amount applied by promo code

    // One-to-Many relationship with OrderItem
    // 'mappedBy' indicates that the 'order' field in the OrderItem entity owns the relationship.
    // CascadeType.ALL means that operations (like persist, remove) on the Order will cascade to its OrderItems.
    // orphanRemoval = true means if an OrderItem is removed from the 'orderItems' list, it's also deleted from DB.
    // FetchType.LAZY is used for performance, loading items only when accessed.
    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @JsonIgnoreProperties("order") // Ignore the 'order' field within the 'orderItems' list when serializing Order
    private List<OrderItem> orderItems;

    @Version // For optimistic locking to handle concurrent updates gracefully
    private Long version;

    /**
     * Enum for defining possible order statuses.
     */
    public enum OrderStatus {
        PENDING,
        PROCESSING,
        READY,
        OUT_FOR_DELIVERY,
        DELIVERED,
        COLLECTED,
        CANCELLED
    }

    public boolean hasPromoCode() {
        return this.promoCode != null && !this.promoCode.trim().isEmpty();
    }

    // Custom getter for eftBankDetails to reconstruct the object from snapshot fields
    public BankDetails getEftBankDetails() {
        if (!hasEftBankDetailsSnapshot()) {
            return null;
        }

        return BankDetails.builder()
                .id(this.eftBankDetailsId) // Use the snapshot ID
                .bankName(this.eftBankName)
                .accountName(this.eftAccountName)
                .accountNumber(this.eftAccountNumber)
                .shapId(this.eftShapId)
                .branchCode(this.eftBranchCode)
                .build();
    }

    // Custom setter for eftBankDetails to populate snapshot fields
    public void setEftBankDetails(BankDetails eftBankDetails) {
        if (eftBankDetails == null) {
            this.eftBankDetailsId = null;
            this.eftBankName = null;
            this.eftAccountName = null;
            this.eftAccountNumber = null;
            this.eftShapId = null;
            this.eftBranchCode = null;
            this.eftBankDetails = null; // Also clear the ManyToOne relationship
            return;
        }

        this.eftBankDetailsId = eftBankDetails.getId();
        this.eftBankName = eftBankDetails.getBankName();
        this.eftAccountName = eftBankDetails.getAccountName();
        this.eftAccountNumber = eftBankDetails.getAccountNumber();
        this.eftShapId = eftBankDetails.getShapId();
        this.eftBranchCode = eftBankDetails.getBranchCode();
    }

    private boolean hasEftBankDetailsSnapshot() {
        return eftBankName != null
                || eftAccountName != null
                || eftAccountNumber != null
                || eftShapId != null
                || eftBranchCode != null;
    }
}
