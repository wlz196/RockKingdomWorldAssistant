package com.roco.ai.repository;

import com.roco.ai.model.entity.*;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.List;

public interface SkillInfoRepository extends JpaRepository<SkillInfo, Integer> {
    Optional<SkillInfo> findByName(String name);
    List<SkillInfo> findByNameContaining(String name);
}
