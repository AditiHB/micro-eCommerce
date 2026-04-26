package com.ecommerce.paymentservice;

import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {
    
    private final PaymentRepository repository;

    public PaymentController(PaymentRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    public List<Payment> getAll() {
        return repository.findAll();
    }

    @PostMapping
    public Payment processPayment(@RequestBody Payment payment) {
        // Simulate payment processing logic
        payment.setStatus("PROCESSED");
        return repository.save(payment);
    }
}
