package com.ecommerce.common.security;

import com.ecommerce.common.exception.ResourceNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * The caller of the current request, for object-level authorization (OWASP API1).
 *
 * <p>URL rules in {@link SecurityConfig} decide <em>which kinds of caller</em> may use an endpoint;
 * this class answers the second question: may <em>this</em> caller touch <em>this</em> record.
 * Back-office roles and machine identities see everything; a plain USER only sees records tied to
 * the {@code customer_id} claim in their token.
 */
@Component("currentUser")
public class CurrentUser {

    private final SecurityProperties properties;

    public CurrentUser(SecurityProperties properties) {
        this.properties = properties;
    }

    /** ADMIN, MANAGER or SERVICE: not restricted to a single customer's data. */
    public boolean hasCrossCustomerAccess() {
        return has(Roles.ADMIN) || has(Roles.MANAGER) || has(Roles.SERVICE);
    }

    /** The customer id bound to this caller's token, if the identity provider issued one. */
    public Optional<Long> customerId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof JwtAuthenticationToken jwtAuth) {
            Object claim = jwtAuth.getToken().getClaim(properties.getCustomerIdClaim());
            if (claim != null) {
                try {
                    return Optional.of(Long.parseLong(claim.toString()));
                } catch (NumberFormatException ignored) {
                    return Optional.empty();
                }
            }
        }
        return Optional.empty();
    }

    /** Whether the caller may act on records belonging to {@code customerId}. */
    public boolean canAccessCustomer(Long customerId) {
        return hasCrossCustomerAccess() || customerId().filter(id -> id.equals(customerId)).isPresent();
    }

    /**
     * Fails with a not-found error when the caller may not see a record. Not-found rather than
     * forbidden on purpose: a 403 would confirm to an attacker that the id exists.
     */
    public void requireAccessToCustomer(Long customerId, String resourceName, Long resourceId) {
        if (!canAccessCustomer(customerId)) {
            throw new ResourceNotFoundException(resourceName, resourceId);
        }
    }

    private boolean has(String role) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            return false;
        }
        String expected = "ROLE_" + role;
        for (GrantedAuthority authority : auth.getAuthorities()) {
            if (expected.equals(authority.getAuthority())) {
                return true;
            }
        }
        return false;
    }
}
