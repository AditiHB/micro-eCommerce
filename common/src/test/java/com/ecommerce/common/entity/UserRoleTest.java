package com.ecommerce.common.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

@DisplayName("UserRole Enum Tests")
class UserRoleTest {

    @Test
    @DisplayName("Should have CUSTOMER role")
    void testCustomerRole() {
        UserRole role = UserRole.CUSTOMER;

        assertThat(role).isEqualTo(UserRole.CUSTOMER);
        assertThat(role.name()).isEqualTo("CUSTOMER");
    }

    @Test
    @DisplayName("Should have ADMIN role")
    void testAdminRole() {
        UserRole role = UserRole.ADMIN;

        assertThat(role).isEqualTo(UserRole.ADMIN);
        assertThat(role.name()).isEqualTo("ADMIN");
    }

    @Test
    @DisplayName("Should have VENDOR role")
    void testVendorRole() {
        UserRole role = UserRole.VENDOR;

        assertThat(role).isEqualTo(UserRole.VENDOR);
        assertThat(role.name()).isEqualTo("VENDOR");
    }

    @Test
    @DisplayName("Should have SUPPORT role")
    void testSupportRole() {
        UserRole role = UserRole.SUPPORT;

        assertThat(role).isEqualTo(UserRole.SUPPORT);
        assertThat(role.name()).isEqualTo("SUPPORT");
    }

    @Test
    @DisplayName("Should support role comparison")
    void testRoleComparison() {
        UserRole role1 = UserRole.CUSTOMER;
        UserRole role2 = UserRole.CUSTOMER;
        UserRole role3 = UserRole.ADMIN;

        assertThat(role1).isEqualTo(role2);
        assertThat(role1).isNotEqualTo(role3);
    }

    @Test
    @DisplayName("Should iterate all roles")
    void testIterateAllRoles() {
        UserRole[] roles = UserRole.values();

        assertThat(roles).isNotEmpty();
        assertThat(roles.length).isGreaterThanOrEqualTo(2);
    }

    @Test
    @DisplayName("Should convert string to role")
    void testStringToRole() {
        UserRole role = UserRole.valueOf("CUSTOMER");

        assertThat(role).isEqualTo(UserRole.CUSTOMER);
    }
}
