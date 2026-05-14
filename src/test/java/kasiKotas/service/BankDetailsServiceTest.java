package kasiKotas.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import kasiKotas.model.BankDetails;
import kasiKotas.model.BankDetailsAudit;
import kasiKotas.repository.BankDetailsAuditRepository;
import kasiKotas.repository.BankDetailsRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BankDetailsServiceTest {

    @Mock
    private BankDetailsRepository bankDetailsRepository;

    @Mock
    private BankDetailsAuditRepository bankDetailsAuditRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void getRandomEftBankDetailsReturnsConfiguredAccount() {
        BankDetailsService service = new BankDetailsService(bankDetailsRepository, bankDetailsAuditRepository, objectMapper);
        BankDetails first = buildDetails(1L, "111111");
        BankDetails second = buildDetails(2L, "222222");

        when(bankDetailsRepository.findAll()).thenReturn(List.of(first, second));

        for (int i = 0; i < 20; i++) {
            Optional<BankDetails> selected = service.getRandomEftBankDetails();
            assertTrue(selected.isPresent());
            assertTrue(List.of(first.getId(), second.getId()).contains(selected.get().getId()));
        }
    }

    @Test
    void saveOrUpdateBankDetailsRejectsThirdAccount() {
        BankDetailsService service = new BankDetailsService(bankDetailsRepository, bankDetailsAuditRepository, objectMapper);
        BankDetails newAccount = buildDetails(null, "333333");

        when(bankDetailsRepository.findByAccountNumber("333333")).thenReturn(Optional.empty());
        when(bankDetailsRepository.count()).thenReturn(2L);

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> service.saveOrUpdateBankDetails(newAccount)
        );

        assertEquals("You can only configure up to 2 EFT accounts.", ex.getMessage());
    }

    @Test
    void saveOrUpdateBankDetailsCreatesAuditEntryForNewAccount() {
        BankDetailsService service = new BankDetailsService(bankDetailsRepository, bankDetailsAuditRepository, objectMapper);
        authenticateAsAdmin();

        BankDetails newAccount = buildDetails(null, "333333");

        when(bankDetailsRepository.findByAccountNumber("333333")).thenReturn(Optional.empty());
        when(bankDetailsRepository.count()).thenReturn(0L);
        when(bankDetailsRepository.save(any(BankDetails.class))).thenAnswer(invocation -> {
            BankDetails saved = invocation.getArgument(0);
            saved.setId(10L);
            return saved;
        });
        when(bankDetailsAuditRepository.save(any(BankDetailsAudit.class))).thenAnswer(invocation -> invocation.getArgument(0));

        BankDetails saved = service.saveOrUpdateBankDetails(newAccount);

        assertEquals(10L, saved.getId());
        ArgumentCaptor<BankDetailsAudit> auditCaptor = ArgumentCaptor.forClass(BankDetailsAudit.class);
        verify(bankDetailsAuditRepository).save(auditCaptor.capture());

        BankDetailsAudit audit = auditCaptor.getValue();
        assertEquals(BankDetailsAudit.AuditAction.CREATE, audit.getAction());
        assertEquals("admin@example.com", audit.getActorUsername());
        assertTrue(audit.getBeforeSnapshotJson() == null || audit.getBeforeSnapshotJson().isBlank());
        assertTrue(audit.getAfterSnapshotJson().contains("333333"));
    }

    @Test
    void saveOrUpdateBankDetailsCreatesAuditEntryForUpdate() {
        BankDetailsService service = new BankDetailsService(bankDetailsRepository, bankDetailsAuditRepository, objectMapper);
        authenticateAsAdmin();

        BankDetails existing = buildDetails(5L, "111111");
        existing.setVersion(2L);

        when(bankDetailsRepository.findById(5L)).thenReturn(Optional.of(existing));
        when(bankDetailsRepository.findByAccountNumber("222222")).thenReturn(Optional.empty());
        when(bankDetailsRepository.save(any(BankDetails.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(bankDetailsAuditRepository.save(any(BankDetailsAudit.class))).thenAnswer(invocation -> invocation.getArgument(0));

        BankDetails updated = buildDetails(5L, "222222");
        BankDetails saved = service.saveOrUpdateBankDetails(updated);

        assertEquals("222222", saved.getAccountNumber());
        ArgumentCaptor<BankDetailsAudit> auditCaptor = ArgumentCaptor.forClass(BankDetailsAudit.class);
        verify(bankDetailsAuditRepository).save(auditCaptor.capture());

        BankDetailsAudit audit = auditCaptor.getValue();
        assertEquals(BankDetailsAudit.AuditAction.UPDATE, audit.getAction());
        assertEquals("admin@example.com", audit.getActorUsername());
        assertTrue(audit.getBeforeSnapshotJson().contains("111111"));
        assertTrue(audit.getAfterSnapshotJson().contains("222222"));
    }

    @Test
    void getAuditHistoryReturnsNewestFirst() {
        BankDetailsService service = new BankDetailsService(bankDetailsRepository, bankDetailsAuditRepository, objectMapper);
        when(bankDetailsAuditRepository.findAllByOrderByChangedAtDesc()).thenReturn(List.of(
                BankDetailsAudit.builder().id(2L).action(BankDetailsAudit.AuditAction.UPDATE).actorUsername("admin").build(),
                BankDetailsAudit.builder().id(1L).action(BankDetailsAudit.AuditAction.CREATE).actorUsername("admin").build()
        ));

        List<BankDetailsAudit> history = service.getAuditHistory();

        assertEquals(2, history.size());
        assertEquals(2L, history.get(0).getId());
        assertEquals(1L, history.get(1).getId());
    }

    private BankDetails buildDetails(Long id, String accountNumber) {
        return BankDetails.builder()
                .id(id)
                .bankName("Demo Bank")
                .accountName("Kasi Kotas")
                .accountNumber(accountNumber)
                .branchCode("250655")
                .shapId("DEMO123")
                .build();
    }

    private void authenticateAsAdmin() {
        User principal = new User("admin@example.com", "", List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }
}

