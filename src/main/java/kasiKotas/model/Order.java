// src/main/java/kasiKotas/model/Order.java
package kasiKotas.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Represents a customer's order in the KasiKotas system.
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
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    @JsonIgnoreProperties({
            "hibernateLazyInitializer",
            "handler",
            "orders",
            "password",
            "version"
    })
    private User user;

    @Column(name = "order_date", nullable = false)
    private LocalDateTime orderDate;

    /**
     * Order fulfilment status.
     *
     * Payment status is handled separately by Payment.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status;

    @Column(nullable = false)
    private Double totalAmount;

    @Column(columnDefinition = "TEXT")
    private String shippingAddress;

    /*
     * PAYMENT
     *
     * Payment information is stored in the Payment entity.
     *
     * Order:
     *   PENDING
     *   PROCESSING
     *   READY
     *   DELIVERED
     *
     * Payment:
     *   PENDING
     *   PAID
     *   FAILED
     *   CANCELLED
     *   REFUNDED
     */
    @OneToOne(
            mappedBy = "order",
            cascade = CascadeType.ALL,
            orphanRemoval = true,
            fetch = FetchType.LAZY
    )
    @JsonIgnore
    private Payment payment;

    // EFT orders store the bank account selected at order creation time.
    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "eft_bank_details_id",
            insertable = false,
            updatable = false
    )
    @JsonIgnoreProperties({
            "hibernateLazyInitializer",
            "handler"
    })
    private BankDetails eftBankDetails;

    // Snapshot of the bank details at the time of order creation.
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

    // DELIVERY or COLLECTION
    @Column
    private String deliveryMethod;

    // Scheduled delivery time
    @Column(name = "scheduled_delivery_time")
    private LocalDateTime scheduledDeliveryTime;

    // Promo code
    @Column
    private String promoCode;

    // Order subtotal before delivery and discount
    @Column
    private Double subtotal;

    // Delivery fee
    @Column
    private Double deliveryFee;

    // Discount amount
    @Column
    private Double discountAmount;

    // Order items
    @OneToMany(
            mappedBy = "order",
            cascade = CascadeType.ALL,
            orphanRemoval = true,
            fetch = FetchType.LAZY
    )
    @JsonIgnoreProperties("order")
    private List<OrderItem> orderItems;

    /*
     * Prevents duplicate order creation when the same
     * checkout request is submitted more than once.
     */
    @Column(name = "idempotency_key", unique = true)
    private String idempotencyKey;

    /*
     * Optimistic locking.
     */
    @Version
    private Long version;

    /**
     * Order fulfilment statuses.
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
        return this.promoCode != null
                && !this.promoCode.trim().isEmpty();
    }

    /**
     * Custom getter for EFT bank details.
     *
     * Reconstructs the BankDetails object from the
     * snapshot stored on the order.
     */
    public BankDetails getEftBankDetails() {

        if (!hasEftBankDetailsSnapshot()) {
            return null;
        }

        return BankDetails.builder()
                .id(this.eftBankDetailsId)
                .bankName(this.eftBankName)
                .accountName(this.eftAccountName)
                .accountNumber(this.eftAccountNumber)
                .shapId(this.eftShapId)
                .branchCode(this.eftBranchCode)
                .build();
    }

    /**
     * Custom setter for EFT bank details.
     *
     * Stores a snapshot of the bank information on the order.
     */
    public void setEftBankDetails(BankDetails eftBankDetails) {

        if (eftBankDetails == null) {

            this.eftBankDetailsId = null;
            this.eftBankName = null;
            this.eftAccountName = null;
            this.eftAccountNumber = null;
            this.eftShapId = null;
            this.eftBranchCode = null;
            this.eftBankDetails = null;

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