package com.roco.backend.repository;

import com.roco.backend.model.entity.*;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface PetRepository extends JpaRepository<Pet, Integer> {
    Optional<Pet> findByName(String name);
    java.util.List<Pet> findByBookId(Integer bookId);
}
