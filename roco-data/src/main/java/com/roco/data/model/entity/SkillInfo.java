package com.roco.data.model.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "skill_conf_main")
public class SkillInfo {
    @Id
    private Integer id;

    private String name;

    @Column(name = "desc", columnDefinition = "TEXT")
    private String description;

    private String icon;

    private Integer type; // Attribute ID

    @Column(name = "energy_cost")
    private String energyCost;

    @Column(name = "skill_type")
    private Integer skillType;

    @Column(name = "skill_priority")
    private Integer skillPriority;

    @Column(name = "dam_para")
    private String damPara;

    @Column(name = "skill_dam_type")
    private Integer skillDamType;

    @Column(name = "damage_type")
    private Integer damageType;

    // Getters and Setters
    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getIcon() { return icon; }
    public void setIcon(String icon) { this.icon = icon; }

    public Integer getType() { return type; }
    public void setType(Integer type) { this.type = type; }

    public String getEnergyCost() { return energyCost; }
    public void setEnergyCost(String energyCost) { this.energyCost = energyCost; }

    public Integer getSkillType() { return skillType; }
    public void setSkillType(Integer skillType) { this.skillType = skillType; }

    public Integer getSkillPriority() { return skillPriority; }
    public void setSkillPriority(Integer skillPriority) { this.skillPriority = skillPriority; }

    public String getDamPara() { return damPara; }
    public void setDamPara(String damPara) { this.damPara = damPara; }

    public Integer getDamageType() { return damageType; }
    public void setDamageType(Integer damageType) { this.damageType = damageType; }

    public Integer getSkillDamType() { return skillDamType; }
    public void setSkillDamType(Integer skillDamType) { this.skillDamType = skillDamType; }

    // Logic Methods
    public String getTypeName() {
        if (type == null) return "未知";
        return type == 1 ? "主动" : (type == 2 ? "被动" : "其他");
    }

    public String getSkillTypeName() {
        if (skillType == null) return "常规";
        return switch (skillType) {
            case 1 -> "攻击型";
            case 2 -> "防御/辅助";
            case 3 -> "变化型";
            default -> "常规";
        };
    }

    public String getDamageTypeName() {
        if (damageType == null) return "物理";
        return switch (damageType) {
            case 2 -> "物理";
            case 3 -> "魔法";
            case 4 -> "特殊";
            default -> "物理";
        };
    }

    // Compatibility methods for frontend
    public String getAttribute() { return "使用 skill_dam_type 映射"; }
    public String getCategory() { 
        if (type != null && type == 2) return "特性";
        return getDamageTypeName();
    }
    public String getPower() { return damPara != null ? damPara : "0"; }
    public Integer getEnergyConsumption() {
        try {
            if (energyCost != null) {
                return Integer.parseInt(energyCost.replace("[", "").replace("]", ""));
            }
        } catch (Exception e) {}
        return 0;
    }
}
