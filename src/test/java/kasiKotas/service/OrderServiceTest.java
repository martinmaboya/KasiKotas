package kasiKotas.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import kasiKotas.model.BankDetails;
import kasiKotas.model.Order;
import kasiKotas.model.OrderItem;
import kasiKotas.model.Product;
import kasiKotas.model.User;
import kasiKotas.repository.ExtraRepository;
import kasiKotas.repository.OrderItemRepository;
import kasiKotas.repository.OrderRepository;
import kasiKotas.repository.ProductExtraRequirementRepository;
import kasiKotas.repository.ProductRepository;
import kasiKotas.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock private OrderRepository orderRepository;
    @Mock private OrderItemRepository orderItemRepository;
    @Mock private UserRepository userRepository;
    @Mock private ProductRepository productRepository;
    @Mock private ExtraRepository extraRepository;
    @Mock private ProductExtraRequirementRepository productExtraRequirementRepository;
    @Mock private ProductService productService;
    @Mock private BankDetailsService bankDetailsService;
    @Mock private DailyOrderLimitService dailyOrderLimitService;
    @Mock private PromoCodeService promoCodeService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void createOrderStoresImmutableEftSnapshotOnTheOrder() {
        OrderService service = new OrderService(
                orderRepository,
                orderItemRepository,
                userRepository,
                productRepository,
                extraRepository,
                productExtraRequirementRepository,
                productService,
                bankDetailsService,
                dailyOrderLimitService,
                promoCodeService,
                objectMapper
        );

        User customer = new User();
        customer.setId(7L);
        customer.setEmail("customer@example.com");
        customer.setPassword("secret");
        customer.setFirstName("Ava");
        customer.setLastName("Mokoena");
        customer.setRole(User.UserRole.CUSTOMER);

        Product product = Product.builder()
                .id(3L)
                .name("Classic Kota")
                .description("Demo product")
                .price(45.0)
                .stock(20)
                .build();

        BankDetails selectedAccount = BankDetails.builder()
                .id(99L)
                .bankName("Safe Bank")
                .accountName("Kasi Kotas Pty Ltd")
                .accountNumber("123456789")
                .shapId("SHAP-001")
                .branchCode("250655")
                .build();

        OrderItem orderItem = new OrderItem();
        Product productRef = new Product();
        productRef.setId(3L);
        orderItem.setProduct(productRef);
        orderItem.setQuantity(2);

        Order order = new Order();
        order.setUser(new User(7L));
        order.setPaymentMethod("EFT");
        order.setDeliveryMethod("DELIVERY");
        order.setShippingAddress("123 Main Road");
        order.setOrderItems(List.of(orderItem));

        when(dailyOrderLimitService.getOrderLimit()).thenReturn(Optional.empty());
        when(userRepository.findById(7L)).thenReturn(Optional.of(customer));
        when(productRepository.findById(3L)).thenReturn(Optional.of(product));
        when(productService.decreaseStock(3L, 2)).thenReturn(true);
        when(productExtraRequirementRepository.findByProductId(3L)).thenReturn(List.of());
        when(bankDetailsService.getRandomEftBankDetails()).thenReturn(Optional.of(selectedAccount));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(orderItemRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        Order savedOrder = service.createOrder(order);

        assertNotNull(savedOrder.getEftBankDetails());
        assertNotSame(selectedAccount, savedOrder.getEftBankDetails());
        assertEquals(Long.valueOf(99L), savedOrder.getEftBankDetails().getId());
        assertEquals("Safe Bank", savedOrder.getEftBankDetails().getBankName());
        assertEquals("Kasi Kotas Pty Ltd", savedOrder.getEftBankDetails().getAccountName());
        assertEquals("123456789", savedOrder.getEftBankDetails().getAccountNumber());
        assertEquals("SHAP-001", savedOrder.getEftBankDetails().getShapId());
        assertEquals("250655", savedOrder.getEftBankDetails().getBranchCode());

        selectedAccount.setBankName("Compromised Bank");
        selectedAccount.setAccountNumber("999999999");

        assertEquals("Safe Bank", savedOrder.getEftBankDetails().getBankName());
        assertEquals("123456789", savedOrder.getEftBankDetails().getAccountNumber());
    }
}


