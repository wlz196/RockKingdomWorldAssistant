package com.roco.backend.repository;

import com.roco.backend.model.entity.*;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.List;

public interface TypeChartRepository extends JpaRepository<TypeChart, Integer> {
    Optional<TypeChart> findByAttackerTypeAndDefenderType(String attacker, String defender);
    List<TypeChart> findByAttackerType(String attacker);
    List<TypeChart> findByDefenderType(String defender);
}
