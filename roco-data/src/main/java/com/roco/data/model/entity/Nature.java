package com.roco.data.model.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "natures")
public class Nature {
    @Id
    private Integer id;

    private String name;

    @Column(name = "plus_attr_id")
    private Integer plusAttrId;

    @Column(name = "minus_attr_id")
    private Integer minusAttrId;

    @Column(name = "plus_rate")
    private Integer plusRate;

    @Column(name = "minus_rate")
    private Integer minusRate;

    @Column(name = "plus_grow")
    private Integer plusGrow;

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public Integer getPlusAttrId() { return plusAttrId; }
    public void setPlusAttrId(Integer plusAttrId) { this.plusAttrId = plusAttrId; }

    public Integer getMinusAttrId() { return minusAttrId; }
    public void setMinusAttrId(Integer minusAttrId) { this.minusAttrId = minusAttrId; }

    public Integer getPlusRate() { return plusRate; }
    public void setPlusRate(Integer plusRate) { this.plusRate = plusRate; }

    public Integer getMinusRate() { return minusRate; }
    public void setMinusRate(Integer minusRate) { this.minusRate = minusRate; }

    public Integer getPlusGrow() { return plusGrow; }
    public void setPlusGrow(Integer plusGrow) { this.plusGrow = plusGrow; }

    // Compatibility methods for Double modifiers
    // 79=HP, 80=Atk, 81=Magic Atk, 82=Def, 83=Magic Def, 84=Speed
    private Double getMod(int attrId) {
        if (plusAttrId != null && plusAttrId == attrId) return (plusRate != null ? plusRate / 1000.0 : 1.1);
        if (minusAttrId != null && minusAttrId == attrId) return (minusRate != null ? minusRate / 1000.0 : 0.9);
        return 1.0;
    }

    public Double getHpMod() { return getMod(79); }
    public Double getPhyAtkMod() { return getMod(80); }
    public Double getMagAtkMod() { return getMod(81); }
    public Double getPhyDefMod() { return getMod(82); }
    public Double getMagDefMod() { return getMod(83); }
    public Double getSpdMod() { return getMod(84); }
    public String getNameEn() { return ""; }
}
