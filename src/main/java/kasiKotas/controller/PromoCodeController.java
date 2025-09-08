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
public class PromoCodeController {

    @Autowired
    private PromoCodeService promoCodeService;



    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping
    public ResponseEntity<PromoCode> createPromo(@RequestBody PromoCode promo) {
        return new ResponseEntity<>(promoCodeService.createPromoCode(promo), HttpStatus.CREATED);
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