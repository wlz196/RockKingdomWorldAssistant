package com.roco.ai.repository;

import com.roco.ai.model.entity.*;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface NatureRepository extends JpaRepository<Nature, Integer> {
    Optional<Nature> findByName(String name);
}
