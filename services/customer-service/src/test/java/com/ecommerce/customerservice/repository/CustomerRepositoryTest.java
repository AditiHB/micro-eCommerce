package com.ecommerce.customerservice.repository;

import com.ecommerce.common.testsupport.PostgresIntegrationTest;
import com.ecommerce.customerservice.Customer;
import com.ecommerce.customerservice.CustomerRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@PostgresIntegrationTest
@DisplayName("CustomerRepository (PostgreSQL)")
class CustomerRepositoryTest {

    @Autowired
    private CustomerRepository repository;
    @Autowired
    private EntityManager entityManager;

    @BeforeEach
    void setUp() {
        repository.deleteAllInBatch();
    }

    private Customer save(String name, String email) {
        return repository.saveAndFlush(Customer.builder().name(name).email(email).build());
    }

    @Test
    @DisplayName("a customer is stored with a version and timestamps")
    void roundTrip() {
        Customer saved = save("John Doe", "john@example.com");
        entityManager.clear();

        Customer found = repository.findById(saved.getId()).orElseThrow();
        assertThat(found.getVersion()).isZero();
        assertThat(found.getCreatedAt()).isNotNull();
    }

    @Test
    @DisplayName("the database enforces unique emails")
    void uniqueEmail() {
        save("John Doe", "john@example.com");

        assertThatThrownBy(() -> save("Someone Else", "john@example.com")).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("existsByEmail and existsByEmailAndIdNot tell 'taken' from 'mine'")
    void emailExistence() {
        Customer john = save("John Doe", "john@example.com");
        Customer jane = save("Jane Doe", "jane@example.com");

        assertThat(repository.existsByEmail("john@example.com")).isTrue();
        assertThat(repository.existsByEmail("nobody@example.com")).isFalse();
        assertThat(repository.existsByEmailAndIdNot("john@example.com", john.getId())).as("it is mine").isFalse();
        assertThat(repository.existsByEmailAndIdNot("john@example.com", jane.getId())).as("it is John's").isTrue();
    }

    @Test
    @DisplayName("two writers holding the same version cannot both win")
    void optimisticLocking() {
        Customer saved = save("John Doe", "john@example.com");
        entityManager.clear();
        Customer copyA = repository.findById(saved.getId()).orElseThrow();
        entityManager.detach(copyA);
        Customer copyB = repository.findById(saved.getId()).orElseThrow();
        entityManager.detach(copyB);

        copyA.setName("From A");
        repository.saveAndFlush(copyA);
        copyB.setName("From B");

        assertThatThrownBy(() -> repository.saveAndFlush(copyB)).isInstanceOf(ObjectOptimisticLockingFailureException.class);
    }
}
