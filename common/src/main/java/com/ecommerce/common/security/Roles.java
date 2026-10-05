package com.ecommerce.common.security;

/**
 * Realm roles issued by the identity provider (Keycloak). Spring Security prefixes them with
 * {@code ROLE_}; {@link KeycloakJwtAuthenticationConverter} does that, so these constants are the
 * bare names used with {@code hasRole(...)}.
 *
 * <ul>
 *   <li>{@link #USER} - a customer acting on their own data</li>
 *   <li>{@link #MANAGER} / {@link #ADMIN} - back-office staff with cross-customer access</li>
 *   <li>{@link #SERVICE} - machine identity for service-to-service calls (client credentials)</li>
 * </ul>
 */
public final class Roles {

    public static final String USER = "USER";
    public static final String MANAGER = "MANAGER";
    public static final String ADMIN = "ADMIN";
    public static final String SERVICE = "SERVICE";

    private Roles() {
    }
}
