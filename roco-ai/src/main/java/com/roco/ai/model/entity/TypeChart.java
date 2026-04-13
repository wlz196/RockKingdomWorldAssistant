package com.roco.ai.model.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "type_chart")
public class TypeChart {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "attack_type")
    private String attackerType;

    @Column(name = "defense_type")
    private String defenderType;

    @Column(name = "effectiveness")
    private Double multiplier;

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }
    public String getAttackerType() { return attackerType; }
    public void setAttackerType(String attackerType) { this.attackerType = attackerType; }
    public String getDefenderType() { return defenderType; }
    public void setDefenderType(String defenderType) { this.defenderType = defenderType; }
    public Double getMultiplier() { return multiplier; }
    public void setMultiplier(Double multiplier) { this.multiplier = multiplier; }
}
