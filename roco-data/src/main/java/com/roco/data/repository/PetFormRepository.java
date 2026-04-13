package com.roco.data.repository;

import com.roco.data.model.entity.*;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface PetFormRepository extends JpaRepository<PetForm, Integer> {
    List<PetForm> findByPetId(Integer petId);
    List<PetForm> findByFormNameContaining(String name);
}
