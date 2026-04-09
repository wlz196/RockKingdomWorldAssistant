package com.roco.backend.repository;

import com.roco.backend.model.entity.*;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.List;

public interface SkillInfoRepository extends JpaRepository<SkillInfo, Integer> {
    Optional<SkillInfo> findByName(String name);
    List<SkillInfo> findByNameContaining(String name);
}
