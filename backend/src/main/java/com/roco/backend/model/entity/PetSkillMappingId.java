package com.roco.backend.model.entity;

import java.io.Serializable;
import java.util.Objects;

public class PetSkillMappingId implements Serializable {
    private Integer petId;
    private Integer skillId;
    private Integer source;
    private Integer learnLevel;

    public PetSkillMappingId() {}
    public PetSkillMappingId(Integer petId, Integer skillId, Integer source, Integer learnLevel) {
        this.petId = petId;
        this.skillId = skillId;
        this.source = source;
        this.learnLevel = learnLevel;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PetSkillMappingId that = (PetSkillMappingId) o;
        return Objects.equals(petId, that.petId) && Objects.equals(skillId, that.skillId) && Objects.equals(source, that.source) && Objects.equals(learnLevel, that.learnLevel);
    }

    @Override
    public int hashCode() {
        return Objects.hash(petId, skillId, source, learnLevel);
    }
}
