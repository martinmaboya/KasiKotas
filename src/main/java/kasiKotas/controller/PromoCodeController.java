package kasiKotas.controller;

import kasiKotas.model.PromoCode;
import kasiKotas.service.PromoCodeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize; // Make sure this import exists
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/promo-codes")
@CrossOrigin("*")
public class PromoCodeController {

    @Autowired
    private PromoCodeService promoCodeService;

    @PreAuthorize("hasRole('ADMIN')") // Add this
    @PostMapping
    public ResponseEntity<PromoCode> createPromo(@RequestBody PromoCode promo) {
        return new ResponseEntity<>(promoCodeService.createPromoCode(promo), HttpStatus.CREATED);
    }

    @PreAuthorize("hasRole('ADMIN')") // Add this if only admins should view all
    @GetMapping
    public List<PromoCode> getAll() {
        return promoCodeService.getAllPromoCodes();
    }

    // You might want to allow non-admins to validate/use promo codes
    @GetMapping("/validate/{code}")
    public ResponseEntity<PromoCode> validate(@PathVariable String code) {
        return ResponseEntity.ok(promoCodeService.validatePromoCode(code));
    }

    @PostMapping("/use/{code}")
    public ResponseEntity<?> use(@PathVariable String code) {
        promoCodeService.usePromoCode(code);
        return ResponseEntity.ok().build();
    }

    @PreAuthorize("hasRole('ADMIN')")// Add this
    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable Long id) {
        promoCodeService.deletePromoCode(id);
        return ResponseEntity.ok().build();
    }
}