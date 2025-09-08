package kasiKotas.controller;

    import kasiKotas.model.Extra;
    import kasiKotas.service.ExtraService;
    import org.springframework.beans.factory.annotation.Autowired;
    import org.springframework.http.HttpStatus;
    import org.springframework.http.ResponseEntity;
    import org.springframework.security.access.prepost.PreAuthorize;
    import org.springframework.web.bind.annotation.*;

    import java.util.List;

    @RestController
    @RequestMapping("/api/extras")
    public class ExtraController {

        private final ExtraService extraService;

        @Autowired
        public ExtraController(ExtraService extraService) {
            this.extraService = extraService;
        }


        @GetMapping
        public ResponseEntity<List<Extra>> getAllExtras() {
            List<Extra> extras = extraService.getAllExtras();
            return ResponseEntity.ok(extras);
        }

        @PreAuthorize("hasAuthority('ADMIN') or hasAuthority('CUSTOMER')")
        @GetMapping("/{id}")
        public ResponseEntity<Extra> getExtraById(@PathVariable Long id) {
            return extraService.getExtraById(id)
                    .map(ResponseEntity::ok)
                    .orElseGet(() -> ResponseEntity.notFound().build());
        }

        @PreAuthorize("hasAuthority('ADMIN')")
        @PostMapping
        public ResponseEntity<Extra> createExtra(@RequestBody Extra extra) {
            try {
                Extra createdExtra = extraService.createExtra(extra);
                return new ResponseEntity<>(createdExtra, HttpStatus.CREATED);
            } catch (IllegalArgumentException e) {
                return ResponseEntity.badRequest().build();
            }
        }

        @PreAuthorize("hasAuthority('ADMIN')")
        @PutMapping("/{id}")
        public ResponseEntity<Extra> updateExtra(@PathVariable Long id, @RequestBody Extra extraDetails) {
            try {
                return extraService.updateExtra(id, extraDetails)
                        .map(ResponseEntity::ok)
                        .orElseGet(() -> ResponseEntity.notFound().build());
            } catch (IllegalArgumentException e) {
                return ResponseEntity.badRequest().build();
            }
        }

        @PreAuthorize("hasAuthority('ADMIN')")
        @DeleteMapping("/{id}")
        public ResponseEntity<Void> deleteExtra(@PathVariable Long id) {
            boolean deleted = extraService.deleteExtra(id);
            if (deleted) {
                return ResponseEntity.noContent().build();
            } else {
                return ResponseEntity.notFound().build();
            }
        }
    }
