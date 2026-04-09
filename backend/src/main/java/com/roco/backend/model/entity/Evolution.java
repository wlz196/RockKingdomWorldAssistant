package com.roco.backend.model.entity;

import jakarta.persistence.*;
import java.io.Serializable;

@Entity
@Table(name = "evolutions")
@IdClass(EvolutionId.class)
public class Evolution {

    @Id
    @Column(name = "source_id")
    private Integer sourceId;

    @Id
    @Column(name = "target_id")
    private Integer targetId;

    @Column(name = "source_name")
    private String sourceName;

    @Column(name = "target_name")
    private String targetName;

    @Column(name = "need_level")
    private Integer needLevel;

    @Column(name = "extra_condition")
    private String extraCondition;

    // Getters and Setters
    public Integer getSourceId() { return sourceId; }
    public void setSourceId(Integer sourceId) { this.sourceId = sourceId; }
    public Integer getTargetId() { return targetId; }
    public void setTargetId(Integer targetId) { this.targetId = targetId; }
    public String getSourceName() { return sourceName; }
    public void setSourceName(String sourceName) { this.sourceName = sourceName; }
    public String getTargetName() { return targetName; }
    public void setTargetName(String targetName) { this.targetName = targetName; }
    public Integer getNeedLevel() { return needLevel; }
    public void setNeedLevel(Integer needLevel) { this.needLevel = needLevel; }
    public String getExtraCondition() { return extraCondition; }
    public void setExtraCondition(String extraCondition) { this.extraCondition = extraCondition; }
}
