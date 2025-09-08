package kasiKotas.controller;

import kasiKotas.model.Sauce;
import kasiKotas.service.SauceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/sauces")
public class SauceController {

    private final SauceService sauceService;

    @Autowired
    public SauceController(SauceService sauceService) {
        this.sauceService = sauceService;
    }

    // Get all sauces
    @GetMapping
    public ResponseEntity<List<Sauce>> getAllSauces() {
        List<Sauce> sauces = sauceService.getAllSauces();
        return ResponseEntity.ok(sauces);
    }

    @PreAuthorize("hasRole('ADMIN') or hasRole('CUSTOMER')")
    @GetMapping("/{id}")
    public ResponseEntity<Sauce> getSauceById(@PathVariable Long id) {
        return sauceService.getSauceById(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping
    public ResponseEntity<Sauce> createSauce(@RequestBody Sauce sauce) {
        try {
            Sauce createdSauce = sauceService.createSauce(sauce);
            return new ResponseEntity<>(createdSauce, HttpStatus.CREATED);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{id}")
    public ResponseEntity<Sauce> updateSauce(@PathVariable Long id, @RequestBody Sauce sauceDetails) {
        try {
            return sauceService.updateSauce(id, sauceDetails)
                    .map(ResponseEntity::ok)
                    .orElseGet(() -> ResponseEntity.notFound().build());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteSauce(@PathVariable Long id) {
        boolean deleted = sauceService.deleteSauce(id);
        if (deleted) {
            return ResponseEntity.noContent().build();
        } else {
            return ResponseEntity.notFound().build();
        }
    }
}