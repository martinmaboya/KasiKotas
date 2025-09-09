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

    // You might want to allow non-admins to validate/use promo codes
    @GetMapping("/validate/{code}")
    public ResponseEntity<PromoCode> validate(@PathVariable String code, @RequestParam(required = false) Double orderAmount) {
        return ResponseEntity.ok(promoCodeService.validatePromoCode(code, orderAmount));
    }

    @PostMapping("/use/{code}")
    public ResponseEntity<?> use(@PathVariable String code, @RequestParam Double orderAmount) {
        promoCodeService.usePromoCode(code, orderAmount);
        return ResponseEntity.ok().build();
    }


        @PreAuthorize("hasRole('ADMIN')")
        @DeleteMapping("/{id}")
        public ResponseEntity<?> delete(@PathVariable Long id) {
            promoCodeService.deletePromoCode(id);
            return ResponseEntity.ok().build();
        }
}