package com.roco.ai.model.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "pet_form")
public class PetForm {
    @Id
    private Integer id;

    @Column(name = "pet_id")
    private Integer petId;

    @Column(name = "form_name")
    private String formName;

    @Column(name = "form_display_name")
    private String formDisplayName;

    private Integer hp;
    private Integer attack;
    private Integer defense;
    private Integer sp_atk;
    private Integer sp_def;
    private Integer speed;

    @Column(name = "abilities_text", columnDefinition = "TEXT")
    private String abilitiesText;

    @Column(name = "evolution_condition", columnDefinition = "TEXT")
    private String evolutionCondition;

    private String attributes;

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }
    public Integer getPetId() { return petId; }
    public void setPetId(Integer petId) { this.petId = petId; }
    public String getFormName() { return formName; }
    public void setFormName(String formName) { this.formName = formName; }
    public String getFormDisplayName() { return formDisplayName; }
    public void setFormDisplayName(String formDisplayName) { this.formDisplayName = formDisplayName; }
    public Integer getHp() { return hp; }
    public void setHp(Integer hp) { this.hp = hp; }
    public Integer getAttack() { return attack; }
    public void setAttack(Integer attack) { this.attack = attack; }
    public Integer getDefense() { return defense; }
    public void setDefense(Integer defense) { this.defense = defense; }
    public Integer getSp_atk() { return sp_atk; }
    public void setSp_atk(Integer sp_atk) { this.sp_atk = sp_atk; }
    public Integer getSp_def() { return sp_def; }
    public void setSp_def(Integer sp_def) { this.sp_def = sp_def; }
    public Integer getSpeed() { return speed; }
    public void setSpeed(Integer speed) { this.speed = speed; }
    public String getAbilitiesText() { return abilitiesText; }
    public void setAbilitiesText(String abilitiesText) { this.abilitiesText = abilitiesText; }
    public String getEvolutionCondition() { return evolutionCondition; }
    public void setEvolutionCondition(String evolutionCondition) { this.evolutionCondition = evolutionCondition; }
    public String getAttributes() { return attributes; }
    public void setAttributes(String attributes) { this.attributes = attributes; }
}
