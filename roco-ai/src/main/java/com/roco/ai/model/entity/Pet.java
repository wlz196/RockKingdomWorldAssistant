package com.roco.ai.model.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "pets")
public class Pet {
    @Id
    private Integer id;

    private String name;

    @Column(name = "is_boss")
    private Integer isBoss;

    @Column(name = "main_type_id")
    private Integer mainTypeId;

    @Column(name = "sub_type_id")
    private Integer subTypeId;

    private Integer hp;
    private Integer attack;
    private Integer defense;

    @Column(name = "magic_attack")
    private Integer magicAttack;

    @Column(name = "magic_defense")
    private Integer magicDefense;

    private Integer speed;

    @Column(name = "book_id")
    private Integer bookId;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "evolution_targets", columnDefinition = "TEXT")
    private String evolutionTargets;

    private Integer completeness;

    // Getters and Setters
    public Integer getCompleteness() { return completeness; }
    public void setCompleteness(Integer completeness) { this.completeness = completeness; }
    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public Integer getMainTypeId() { return mainTypeId; }
    public void setMainTypeId(Integer mainTypeId) { this.mainTypeId = mainTypeId; }

    public Integer getSubTypeId() { return subTypeId; }
    public void setSubTypeId(Integer subTypeId) { this.subTypeId = subTypeId; }

    public Integer getHp() { return hp; }
    public void setHp(Integer hp) { this.hp = hp; }

    public Integer getAttack() { return attack; }
    public void setAttack(Integer attack) { this.attack = attack; }

    public Integer getDefense() { return defense; }
    public void setDefense(Integer defense) { this.defense = defense; }

    public Integer getMagicAttack() { return magicAttack; }
    public void setMagicAttack(Integer magicAttack) { this.magicAttack = magicAttack; }

    public Integer getMagicDefense() { return magicDefense; }
    public void setMagicDefense(Integer magicDefense) { this.magicDefense = magicDefense; }

    public Integer getSpeed() { return speed; }
    public void setSpeed(Integer speed) { this.speed = speed; }

    public Integer getBookId() { return bookId; }
    public void setBookId(Integer bookId) { this.bookId = bookId; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getEvolutionTargets() { return evolutionTargets; }
    public void setEvolutionTargets(String evolutionTargets) { this.evolutionTargets = evolutionTargets; }

    public String getAbilitiesText() { return evolutionTargets != null ? evolutionTargets : ""; }
    @Column(name = "image_url")
    private String imageUrl;

    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }

    // Compatibility methods for old getters if needed (optional for MVP)
    @Transient
    public String getPrimary_type() { return String.valueOf(mainTypeId); }

    @Transient
    public String getSecondary_type() { return subTypeId != null ? String.valueOf(subTypeId) : null; }

    @Transient
    public Integer getSp_atk() { return magicAttack; }

    @Transient
    public Integer getSp_def() { return magicDefense; }

    @Transient
    public String getT_id() { return String.valueOf(bookId); }

    public Integer getIsBoss() { return isBoss; }
    public void setIsBoss(Integer isBoss) { this.isBoss = isBoss; }

    @Column(name = "pet_feature")
    private Integer petFeature;

    @Column(name = "move_type")
    private String moveType;

    @Column(name = "pet_score")
    private Integer petScore;

    @Column(name = "habitat_id")
    private Integer habitatId;

    @Transient
    private String height;
    @Transient
    private String weight;
    @Transient
    private java.util.List<String> eggGroups;

    public Integer getPetFeature() { return petFeature; }
    public void setPetFeature(Integer petFeature) { this.petFeature = petFeature; }

    public String getMoveType() { return moveType; }
    public void setMoveType(String moveType) { this.moveType = moveType; }

    public Integer getPetScore() { return petScore; }
    public void setPetScore(Integer petScore) { this.petScore = petScore; }

    public Integer getHabitatId() { return habitatId; }
    public void setHabitatId(Integer habitatId) { this.habitatId = habitatId; }

    public String getHeight() { return height; }
    public void setHeight(String height) { this.height = height; }

    public String getWeight() { return weight; }
    public void setWeight(String weight) { this.weight = weight; }

    public java.util.List<String> getEggGroups() { return eggGroups; }
    public void setEggGroups(java.util.List<String> eggGroups) { this.eggGroups = eggGroups; }
}
