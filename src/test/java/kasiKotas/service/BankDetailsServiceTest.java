package kasiKotas.service;

import kasiKotas.model.BankDetails;
import kasiKotas.repository.BankDetailsRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BankDetailsServiceTest {

    @Mock
    private BankDetailsRepository bankDetailsRepository;

    @Test
    void getRandomEftBankDetailsReturnsConfiguredAccount() {
        BankDetailsService service = new BankDetailsService(bankDetailsRepository);
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
        BankDetailsService service = new BankDetailsService(bankDetailsRepository);
        BankDetails newAccount = buildDetails(null, "333333");

        when(bankDetailsRepository.findByAccountNumber("333333")).thenReturn(Optional.empty());
        when(bankDetailsRepository.count()).thenReturn(2L);

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> service.saveOrUpdateBankDetails(newAccount)
        );

        assertEquals("You can only configure up to 2 EFT accounts.", ex.getMessage());
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
}

