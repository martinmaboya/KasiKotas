package kasiKotas.security;

import kasiKotas.model.User;
import kasiKotas.repository.OrderRepository;
import kasiKotas.service.UserService;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import java.util.Collection;

@Component("authorizationHelper")
public class AuthorizationHelper {

    private final UserService userService;
    private final OrderRepository orderRepository;

    public AuthorizationHelper(UserService userService, OrderRepository orderRepository) {
        this.userService = userService;
        this.orderRepository = orderRepository;
    }

    public boolean canAccessOrder(Authentication authentication, Long orderId) {
        return orderId != null
                && orderRepository.findById(orderId)
                .map(order -> canAccessUser(authentication, order.getUser().getId()))
                .orElse(false);
    }

    public boolean canAccessUser(Authentication authentication, Long targetUserId) {
        if (authentication == null || targetUserId == null || !authentication.isAuthenticated()) {
            return false;
        }

        if (hasAdminRole(authentication.getAuthorities())) {
            return true;
        }

        Object principal = authentication.getPrincipal();
        if (principal instanceof CustomUserDetails customUserDetails) {
            return targetUserId.equals(customUserDetails.getId());
        }

        if (principal instanceof UserDetails userDetails) {
            return matchesUserId(userDetails.getUsername(), targetUserId);
        }

        if (principal instanceof String username && !"anonymousUser".equalsIgnoreCase(username)) {
            return matchesUserId(username, targetUserId);
        }

        return false;
    }

    private boolean matchesUserId(String email, Long targetUserId) {
        if (email == null || email.isBlank()) {
            return false;
        }

        return userService.getUserByEmail(email)
                .map(User::getId)
                .map(targetUserId::equals)
                .orElse(false);
    }

    private boolean hasAdminRole(Collection<? extends GrantedAuthority> authorities) {
        return authorities != null && authorities.stream()
                .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()));
    }
}
