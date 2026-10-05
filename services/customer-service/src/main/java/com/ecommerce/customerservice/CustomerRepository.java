package com.ecommerce.customerservice;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomerRepository extends JpaRepository<Customer, Long> {

    boolean existsByEmail(String email);

    /** True if some <em>other</em> customer already uses this address. */
    boolean existsByEmailAndIdNot(String email, Long id);
}
