package com.roco.backend.controller;

import com.roco.backend.model.entity.Pet;
import com.roco.backend.repository.PetRepository;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/pets")
public class PetController {
    private final PetRepository repository;

    public PetController(PetRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    public List<Pet> getPets() {
        return repository.findAll().stream().limit(10).collect(Collectors.toList());
    }

    @GetMapping("/{id}")
    public Pet getPet(@PathVariable Integer id) {
        return repository.findById(id).orElse(null);
    }
}
