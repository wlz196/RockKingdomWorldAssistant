package com.roco.backend.model.dto;

public class SkillItemDTO {
    private Integer id;
    private String name;
    private String icon;
    private String attribute; // 系别 (如：光)
    private String category;  // 分类 (如：物理)
    private String power;    // 威力
    private Integer pp;       // 能量消耗
    private String desc;

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getIcon() { return icon; }
    public void setIcon(String icon) { this.icon = icon; }

    public String getAttribute() { return attribute; }
    public void setAttribute(String attribute) { this.attribute = attribute; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getPower() { return power; }
    public void setPower(String power) { this.power = power; }

    public Integer getPp() { return pp; }
    public void setPp(Integer pp) { this.pp = pp; }

    public String getDesc() { return desc; }
    public void setDesc(String desc) { this.desc = desc; }
}
