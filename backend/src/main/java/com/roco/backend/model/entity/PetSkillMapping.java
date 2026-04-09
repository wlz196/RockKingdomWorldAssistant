package com.roco.backend.model.entity;

import jakarta.persistence.*;
import java.io.Serializable;


@Entity
@Table(name = "pet_level_skills")
@IdClass(PetSkillMappingId.class)
public class PetSkillMapping implements Serializable {
    
    @Id
    @Column(name = "pet_id")
    private Integer petId;
    
    @Id
    @Column(name = "skill_id")
    private Integer skillId;
    
    @Id
    @Column(name = "source")
    private Integer source;
    
    @Id
    @Column(name = "learn_level")
    private Integer learnLevel;

    @Column(name = "pet_name")
    private String petName;

    public Integer getPetId() { return petId; }
    public void setPetId(Integer petId) { this.petId = petId; }
    public Integer getSkillId() { return skillId; }
    public void setSkillId(Integer skillId) { this.skillId = skillId; }
    public Integer getSource() { return source; }
    public void setSource(Integer source) { this.source = source; }
    public String getPetName() { return petName; }
    public void setPetName(String petName) { this.petName = petName; }

    // Compatibility method
    public String getSourceType() {
        if (source == null) return "未知";
        switch (source) {
            case 0: return "自学";
            case 1: return "技能石";
            case 2: return "血脉";
            default: return "其他";
        }
    }
}
