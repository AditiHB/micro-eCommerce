package com.ecommerce.inventoryservice;

import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/inventory")
public class InventoryController {
    
    private final InventoryRepository repository;

    public InventoryController(InventoryRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    public List<Inventory> getAll() {
        return repository.findAll();
    }

    @PostMapping
    public Inventory addStock(@RequestBody Inventory inventory) {
        return repository.findByProductId(inventory.getProductId())
                .map(existing -> {
                    existing.setQuantity(existing.getQuantity() + inventory.getQuantity());
                    return repository.save(existing);
                }).orElseGet(() -> repository.save(inventory));
    }
}
