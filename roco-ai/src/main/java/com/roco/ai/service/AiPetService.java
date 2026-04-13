package com.roco.ai.service;

import com.roco.ai.model.entity.*;
import com.roco.ai.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.Optional;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class AiPetService {

    @Autowired
    private PetRepository petRepository;

    @Autowired
    private SkillInfoRepository skillRepository;

    @Autowired
    private PetSkillMappingRepository mappingRepository;

    @Autowired
    private PetFormRepository formRepository;

    @Autowired
    private TypeChartRepository typeChartRepository;

    @Autowired
    private NatureRepository natureRepository;

    public String getPetInfo(String name) {
        Optional<Pet> petOpt = petRepository.findByName(name);
        if (petOpt.isPresent()) {
            Pet p = petOpt.get();
            return String.format(
                "精灵名称: %s (ID: %s)\n属性: %s %s\n种族值: HP:%d, 攻击:%d, 防御:%d, 魔攻:%d, 魔抗:%d, 速度:%d\n被动特性: %s\n描述: %s",
                p.getName(), p.getT_id(), p.getPrimary_type(),
                (p.getSecondary_type() != null ? p.getSecondary_type() : ""),
                p.getHp(), p.getAttack(), p.getDefense(), p.getSp_atk(), p.getSp_def(), p.getSpeed(),
                p.getAbilitiesText(), p.getDescription() != null ? p.getDescription() : "暂无"
            );
        }
        return "未找到名为 " + name + " 的精灵信息。";
    }

    public String getPetSkills(String petName) {
        Optional<Pet> petOpt = petRepository.findByName(petName);
        if (petOpt.isEmpty()) return "未找到精灵 " + petName;

        List<PetSkillMapping> mappings = mappingRepository.findByPetId(petOpt.get().getId());
        if (mappings.isEmpty()) return "数据库中暂无 " + petName + " 的技能映射记录。";

        StringBuilder sb = new StringBuilder("精灵 " + petName + " 的技能列表:\n");
        for (PetSkillMapping m : mappings) {
            Optional<SkillInfo> s = skillRepository.findById(m.getSkillId());
            if (s.isPresent()) {
                SkillInfo si = s.get();
                sb.append(String.format("[%s] %s (%s) - 威力: %s, 充能: %d, 描述: %s\n",
                    m.getSourceType(), si.getName(), si.getAttribute(), si.getPower(), si.getEnergyConsumption(), si.getDescription()));
            } else {
                sb.append(String.format("[%s] 技能ID %d (详细数据缺失)\n", m.getSourceType(), m.getSkillId()));
            }
        }
        return sb.toString();
    }

    public String getTypeEffectiveness(String attackerType, String defenderBaseType) {
        Optional<TypeChart> chart = typeChartRepository.findByAttackerTypeAndDefenderType(attackerType, defenderBaseType);
        double multiplier = chart.map(TypeChart::getMultiplier).orElse(1.0);
        String evaluation = multiplier > 1 ? "效果拔群" : (multiplier < 1 ? "收效甚微" : "效果一般");
        return String.format("%s 对 %s 的伤害倍率为: %.2f (%s)", attackerType, defenderBaseType, multiplier, evaluation);
    }

    public String getPetForms(String petName) {
        Optional<Pet> pet = petRepository.findByName(petName);
        if (pet.isEmpty()) return "未找到精灵 " + petName;

        List<PetForm> forms = formRepository.findByPetId(pet.get().getId());
        if (forms.isEmpty()) return petName + " 目前没有其他形态记录。";

        StringBuilder sb = new StringBuilder(petName + " 的形态差异分析:\n");
        for (PetForm f : forms) {
            sb.append(String.format("形态: %s\n属性: %s\n种族值: HP:%d, 物攻:%d, 物防:%d, 魔攻:%d, 魔防:%d, 速度:%d\n被动: %s\n进化条件: %s\n---\n",
                f.getFormDisplayName(), f.getAttributes() != null ? f.getAttributes() : "同原形态",
                f.getHp(), f.getAttack(), f.getDefense(), f.getSp_atk(), f.getSp_def(), f.getSpeed(),
                f.getAbilitiesText(), f.getEvolutionCondition() != null ? f.getEvolutionCondition() : "未知"));
        }
        return sb.toString();
    }

    public String getNatureRecommendation(String petName) {
        List<Pet> pets = petRepository.findAll().stream()
                .filter(p -> p.getName() != null && p.getName().equalsIgnoreCase(petName))
                .toList();
        if (pets.isEmpty()) return "未找到精灵 " + petName;
        return getNatureRecommendation(pets.get(0));
    }

    public String getNatureRecommendation(Pet p) {
        if (p == null) return "数据缺失";
        return "【" + p.getName() + "】系统正在对当前竞技场环境进行深度建模，性格推荐与战术分析模块正在迭代中，敬请期待 AI 战术智库的深度评析。";
    }

    public String searchBySkill(String skillName) {
        Optional<SkillInfo> skillOpt = skillRepository.findByName(skillName);
        if (skillOpt.isEmpty()) return "暂无技能 " + skillName + " 的官方记录。";

        List<PetSkillMapping> mappings = mappingRepository.findBySkillId(skillOpt.get().getId());
        if (mappings.isEmpty()) return "暂无精灵记录可习得技能 " + skillName;

        List<String> pets = mappings.stream()
            .map(m -> petRepository.findById(m.getPetId()).map(Pet::getName).orElse("未知精灵"))
            .distinct().limit(20).collect(Collectors.toList());
        String petList = String.join("、", pets);
        return String.format("可习得技能 [%s] 的精灵有 (部分列出): %s%s",
            skillName, petList, mappings.size() > 20 ? " 等" : "");
    }

    public String getCounterPets(String typeName) {
        List<TypeChart> effectiveTypes = typeChartRepository.findByDefenderType(typeName)
            .stream().filter(c -> c.getMultiplier() > 1.0).collect(Collectors.toList());

        if (effectiveTypes.isEmpty()) return "暂无属性直接克制 " + typeName + " 的官方记录。";

        StringBuilder sb = new StringBuilder("克制属性 [" + typeName + "] 的属性及推荐精灵有:\n");
        for (TypeChart tc : effectiveTypes) {
            sb.append(String.format("【%s】(伤害%.1f倍): ", tc.getAttackerType(), tc.getMultiplier()));
            List<Pet> samplePets = petRepository.findAll().stream()
                .filter(p -> tc.getAttackerType().equals(p.getPrimary_type()))
                .limit(3).collect(Collectors.toList());
            sb.append(samplePets.stream().map(Pet::getName).collect(Collectors.joining("、")));
            sb.append("\n");
        }
        return sb.toString();
    }

    public String getRecommendedTeams() {
        return "当前赛季推荐战术体系:\n1. 幻光盾体系: 以'圣湮伊莱娜'为核心，辅以神系控制。\n2. 消耗流: 依靠'南圣兽雀羽'的回复与控制进行慢节奏博弈。\n3. 高爆强推: 以神系多段伤害精灵为核心进行速战。";
    }
}
