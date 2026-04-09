package com.roco.backend.repository;

import com.roco.backend.model.entity.*;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface EvolutionRepository extends JpaRepository<Evolution, EvolutionId> {
    List<Evolution> findBySourceId(Integer sourceId);
    List<Evolution> findByTargetId(Integer targetId);
}
