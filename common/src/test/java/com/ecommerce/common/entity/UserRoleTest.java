package com.ecommerce.common.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

@DisplayName("UserRole Enum Tests")
class UserRoleTest {

    @Test
    @DisplayName("Should have USER role")
    void testUserRole() {
        UserRole role = UserRole.USER;

        assertThat(role).isEqualTo(UserRole.USER);
        assertThat(role.name()).isEqualTo("USER");
    }

    @Test
    @DisplayName("Should have ADMIN role")
    void testAdminRole() {
        UserRole role = UserRole.ADMIN;

        assertThat(role).isEqualTo(UserRole.ADMIN);
        assertThat(role.name()).isEqualTo("ADMIN");
    }

    @Test
    @DisplayName("Should have MANAGER role")
    void testManagerRole() {
        UserRole role = UserRole.MANAGER;

        assertThat(role).isEqualTo(UserRole.MANAGER);
        assertThat(role.name()).isEqualTo("MANAGER");
    }

    @Test
    @DisplayName("Should have exactly the expected set of roles")
    void testAllRolesPresent() {
        UserRole[] roles = UserRole.values();

        assertThat(roles).containsExactlyInAnyOrder(UserRole.USER, UserRole.ADMIN, UserRole.MANAGER);
    }

    @Test
    @DisplayName("Should support role comparison")
    void testRoleComparison() {
        UserRole role1 = UserRole.USER;
        UserRole role2 = UserRole.USER;
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
        UserRole role = UserRole.valueOf("USER");

        assertThat(role).isEqualTo(UserRole.USER);
    }
}
