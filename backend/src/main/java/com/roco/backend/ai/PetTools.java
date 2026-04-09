package com.roco.backend.ai;

import com.roco.backend.service.PetService;
import dev.langchain4j.agent.tool.Tool;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class PetTools {

    @Autowired
    private PetService petService;

    @Tool("获取指定名称精灵的基础数值信息，包括种族值、属性和被动特性描述")
    public String getPetInfo(String petName) {
        return petService.getPetInfo(petName);
    }

    @Tool("获取指定精灵可习得的技能列表，包含专属技能、技能石以及血脉技能")
    public String getPetSkills(String petName) {
        return petService.getPetSkills(petName);
    }

    @Tool("获取两个属性之间的相性比率（克制倍率），例如‘火’对‘草’")
    public String getTypeEffectiveness(String attackerType, String defenderType) {
        return petService.getTypeEffectiveness(attackerType, defenderType);
    }

    @Tool("获取指定精灵的不同进化形态或特殊形态的差异，包括种族值变化和被动变化")
    public String getPetForms(String petName) {
        return petService.getPetForms(petName);
    }

    @Tool("根据精灵的种族值优势，给出战术性格推荐")
    public String getNatureRecommendation(String petName) {
        return petService.getNatureRecommendation(petName);
    }

    @Tool("通过技能名称反向查询哪些精灵可以习得该技能")
    public String searchBySkill(String skillName) {
        return petService.searchBySkill(skillName);
    }

    @Tool("查询指定属性的天敌（被哪些属性克制）以及对应的推荐反制精灵")
    public String getCounterPets(String typeName) {
        return petService.getCounterPets(typeName);
    }

    @Tool("获取当前版本的推荐战术体系与核心阵容组合建议")
    public String getRecommendedTeams() {
        return petService.getRecommendedTeams();
    }
}
