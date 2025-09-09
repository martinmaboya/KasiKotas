package kasiKotas.controller;

import kasiKotas.model.PromoCode;
import kasiKotas.service.PromoCodeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize; // Make sure this import exists
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/promo-codes")
public class PromoCodeController {

    @Autowired
    private PromoCodeService promoCodeService;



        @PreAuthorize("hasRole('ADMIN')")
        @PostMapping
        public ResponseEntity<?> createPromo(@RequestBody PromoCode promo) {
            try {
                PromoCode created = promoCodeService.createPromoCode(promo);
                return new ResponseEntity<>(created, HttpStatus.CREATED);
            } catch (DataIntegrityViolationException e) {
                // Handle duplicate code constraint violation
                if (e.getMessage().contains("promocode_code_key") || e.getMessage().contains("duplicate key")) {
                    return ResponseEntity.badRequest()
                            .body(Map.of("error", "Promo code already exists", 
                                       "message", "A promo code with this code already exists. Please use a different code."));
                }
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Database constraint violation", 
                                   "message", "Invalid data provided."));
            } catch (Exception e) {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(Map.of("error", "Internal server error", 
                                   "message", "An unexpected error occurred."));
            }
        }

        @PreAuthorize("hasRole('ADMIN')")
        @GetMapping
        public List<PromoCode> getAll() {
            return promoCodeService.getAllPromoCodes();
        }

        @PreAuthorize("hasRole('ADMIN')")
        @GetMapping("/{code}")
        public ResponseEntity<?> getByCode(@PathVariable String code) {
            try {
                PromoCode promo = promoCodeService.getPromoCodeByCode(code);
                return ResponseEntity.ok(promo);
            } catch (IllegalArgumentException e) {
                return ResponseEntity.notFound().build();
            } catch (Exception e) {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "error", "Internal Server Error",
                    "message", "An unexpected error occurred while retrieving the promo code"
                ));
            }
        }

    // You might want to allow non-admins to validate/use promo codes
    @GetMapping("/validate/{code}")
    public ResponseEntity<?> validate(@PathVariable String code, @RequestParam(required = false) Double orderAmount) {
        try {
            PromoCode validPromo = promoCodeService.validatePromoCode(code, orderAmount);
            return ResponseEntity.ok(Map.of(
                "valid", true,
                "message", "Promo code is valid",
                "promoCode", validPromo,
                "discountAmount", validPromo.getDiscountAmount(),
                "isPercentage", validPromo.getPercentageDiscount() == 1,
                "description", validPromo.getDescription() != null ? validPromo.getDescription() : "Discount applied"
            ));
        } catch (IllegalArgumentException e) {
            // Invalid promo code or insufficient order amount
            return ResponseEntity.badRequest().body(Map.of(
                "valid", false,
                "error", "Invalid Request",
                "message", e.getMessage()
            ));
        } catch (IllegalStateException e) {
            // Expired or usage limit reached
            return ResponseEntity.status(HttpStatus.GONE).body(Map.of(
                "valid", false,
                "error", "Promo Code Unavailable", 
                "message", e.getMessage()
            ));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                "valid", false,
                "error", "Internal Server Error",
                "message", "An unexpected error occurred while validating the promo code"
            ));
        }
    }

    @PostMapping("/use/{code}")
    public ResponseEntity<?> use(@PathVariable String code, @RequestParam Double orderAmount) {
        try {
            PromoCode usedPromo = promoCodeService.usePromoCode(code, orderAmount);
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Promo code '" + code + "' has been successfully applied",
                "usageCount", usedPromo.getUsageCount(),
                "remainingUses", usedPromo.getMaxUsages() - usedPromo.getUsageCount(),
                "discountAmount", usedPromo.getDiscountAmount(),
                "isPercentage", usedPromo.getPercentageDiscount() == 1
            ));
        } catch (IllegalArgumentException e) {
            // Invalid promo code or insufficient order amount
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "error", "Invalid Request",
                "message", e.getMessage()
            ));
        } catch (IllegalStateException e) {
            // Expired or usage limit reached
            return ResponseEntity.status(HttpStatus.GONE).body(Map.of(
                "success", false,
                "error", "Promo Code Unavailable",
                "message", e.getMessage()
            ));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                "success", false,
                "error", "Internal Server Error",
                "message", "An unexpected error occurred while applying the promo code"
            ));
        }
    }


        @PreAuthorize("hasRole('ADMIN')")
        @DeleteMapping("/{id}")
        public ResponseEntity<?> delete(@PathVariable Long id) {
            try {
                promoCodeService.deletePromoCode(id);
                return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Promo code deleted successfully"
                ));
            } catch (IllegalArgumentException e) {
                return ResponseEntity.notFound().build();
            } catch (Exception e) {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "success", false,
                    "error", "Internal Server Error",
                    "message", "An unexpected error occurred while deleting the promo code"
                ));
            }
        }
}