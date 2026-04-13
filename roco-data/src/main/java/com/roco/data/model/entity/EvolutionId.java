package com.roco.data.model.entity;

import java.io.Serializable;

public class EvolutionId implements Serializable {
    private Integer sourceId;
    private Integer targetId;

    public EvolutionId() {}
    public EvolutionId(Integer sourceId, Integer targetId) {
        this.sourceId = sourceId;
        this.targetId = targetId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof EvolutionId that)) return false;
        return sourceId.equals(that.sourceId) && targetId.equals(that.targetId);
    }

    @Override
    public int hashCode() {
        return 31 * sourceId.hashCode() + targetId.hashCode();
    }
}
