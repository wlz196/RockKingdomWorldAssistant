package com.roco.ai.repository;

import com.roco.ai.model.entity.*;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface PetSkillMappingRepository extends JpaRepository<PetSkillMapping, PetSkillMappingId> {
    List<PetSkillMapping> findByPetId(Integer petId);
    List<PetSkillMapping> findBySkillId(Integer skillId);
}
