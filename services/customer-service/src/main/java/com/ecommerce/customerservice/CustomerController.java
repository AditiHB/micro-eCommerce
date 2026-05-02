package com.ecommerce.customerservice;

import org.springframework.web.bind.annotation.*;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RestController
@RequestMapping("/api/customers")
public class CustomerController {
    
    private static final Logger log = LoggerFactory.getLogger(CustomerController.class);
    
    private final CustomerRepository repository;

    public CustomerController(CustomerRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    public List<Customer> getAll() {
        log.info("Fetching all customers");
        return repository.findAll();
    }

    @PostMapping
    public Customer create(@RequestBody Customer customer) {
        log.info("Creating a new customer: {}", customer.getName());
        return repository.save(customer);
    }
}
