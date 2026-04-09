package com.roco.backend.model.dto;

public class SkillDTO {
    private Integer id;
    private String name;
    private String desc;
    private String icon;
    private Integer type;
    private String energyCost;
    private Integer skillType;
    private Integer skillDamType;
    private Integer damageType;
    private Integer skillPriority;
    private String damPara;

    public SkillDTO() {}

    public SkillDTO(Integer id, String name, String desc, String icon, Integer type, String energyCost, Integer skillType, Integer skillDamType, Integer damageType, Integer skillPriority, String damPara) {
        this.id = id;
        this.name = name;
        this.desc = desc;
        this.icon = icon;
        this.type = type;
        this.energyCost = energyCost;
        this.skillType = skillType;
        this.skillDamType = skillDamType;
        this.damageType = damageType;
        this.skillPriority = skillPriority;
        this.damPara = damPara;
    }

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDesc() { return desc; }
    public void setDesc(String desc) { this.desc = desc; }

    public String getIcon() { return icon; }
    public void setIcon(String icon) { this.icon = icon; }

    public Integer getType() { return type; }
    public void setType(Integer type) { this.type = type; }

    public String getEnergyCost() { return energyCost; }
    public void setEnergyCost(String energyCost) { this.energyCost = energyCost; }

    public Integer getSkillType() { return skillType; }
    public void setSkillType(Integer skillType) { this.skillType = skillType; }

    public Integer getSkillDamType() { return skillDamType; }
    public void setSkillDamType(Integer skillDamType) { this.skillDamType = skillDamType; }

    public Integer getDamageType() { return damageType; }
    public void setDamageType(Integer damageType) { this.damageType = damageType; }

    public Integer getSkillPriority() { return skillPriority; }
    public void setSkillPriority(Integer skillPriority) { this.skillPriority = skillPriority; }

    public String getDamPara() { return damPara; }
    public void setDamPara(String damPara) { this.damPara = damPara; }
}
