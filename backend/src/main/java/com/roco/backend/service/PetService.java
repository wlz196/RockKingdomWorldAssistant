package com.roco.backend.service;

import com.roco.backend.model.entity.*;
import com.roco.backend.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.Optional;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.stream.Collectors;

@Service
public class PetService {

    @Autowired
    private JdbcTemplate jdbcTemplate;

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

    @Autowired
    private EvolutionRepository evolutionRepository;

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
        
        // Temporarily disabled AI analysis, just provide a placeholder as requested.
        return "【" + p.getName() + "】系统正在对当前竞技场环境进行深度建模，性格推荐与战术分析模块正在迭代中，敬请期待 AI 战术智库的深度评析。";
    }

    public Map<String, Object> getPetDetails(Integer id) {
        Optional<Pet> petOpt = petRepository.findById(id);
        if (petOpt.isEmpty()) return null;
        Pet p = petOpt.get();
        
        // Populate Transient fields from auxiliary tables
        // 1. Height and Weight from pet_dimensions
        jdbcTemplate.query("SELECT height_min, height_max, weight_min, weight_max FROM pet_dimensions WHERE pet_id = ?", (rs) -> {
            p.setHeight(String.format("%.1fm - %.1fm", rs.getDouble("height_min"), rs.getDouble("height_max")));
            p.setWeight(String.format("%.1fkg - %.1fkg", rs.getDouble("weight_min"), rs.getDouble("weight_max")));
        }, p.getId());

        // 2. Egg Groups from pet_egg_groups
        List<String> groups = new ArrayList<>();
        Map<Integer, String> eggGroupNameMap = getEggGroupNameMap();
        jdbcTemplate.query("SELECT group_id FROM pet_egg_groups WHERE pet_id = ?", (rs) -> {
            groups.add(eggGroupNameMap.getOrDefault(rs.getInt("group_id"), "未知组"));
        }, p.getId());
        p.setEggGroups(groups);

        Map<String, Object> details = new HashMap<>();
        details.put("id", p.getId());
        details.put("bookId", p.getBookId());
        details.put("name", p.getName());
        details.put("type1", p.getPrimary_type());
        details.put("type2", p.getSecondary_type());
        details.put("hp", p.getHp());
        details.put("attack", p.getAttack());
        details.put("defense", p.getDefense());
        details.put("sp_atk", p.getSp_atk());
        details.put("sp_def", p.getSp_def());
        details.put("speed", p.getSpeed());
        details.put("description", p.getDescription());
        details.put("imageUrl", formatImageUrl(p.getImageUrl(), p.getName()));
        
        // New enriched fields
        details.put("height", p.getHeight() != null ? p.getHeight() : "未知");
        details.put("weight", p.getWeight() != null ? p.getWeight() : "未知");
        details.put("eggGroups", p.getEggGroups());
        details.put("moveType", p.getMoveType() != null ? p.getMoveType() : "行走");
        details.put("petScore", p.getPetScore() != null ? p.getPetScore() : 0);
        
        // Fetch characteristic name and desc if available
        details.put("featureName", "暂无特性");
        details.put("featureDesc", "");
        if (p.getPetFeature() != null) {
            jdbcTemplate.query("SELECT name, desc FROM skill_conf_main WHERE id = ?", (rs) -> {
                details.put("featureName", rs.getString("name"));
                details.put("featureDesc", rs.getString("desc"));
            }, p.getPetFeature());
        }
        
        // Pre-fetch types for skill mapping
        Map<Integer, String> typeMap = new HashMap<>();
        jdbcTemplate.query("SELECT id, name FROM types", (rs) -> {
            typeMap.put(rs.getInt("id"), rs.getString("name").replace("系", ""));
        });

        // Skill loading with priority
        List<PetSkillMapping> mappings = mappingRepository.findByPetId(id);
        Map<String, List<Map<String, Object>>> categorizedSkills = new HashMap<>();
        categorizedSkills.put("自学", new ArrayList<>());
        categorizedSkills.put("技能石", new ArrayList<>());
        categorizedSkills.put("血脉", new ArrayList<>());
        
        for (PetSkillMapping m : mappings) {
            String source = m.getSourceType();
            if (!categorizedSkills.containsKey(source)) {
                categorizedSkills.put(source, new ArrayList<>());
            }
            skillRepository.findById(m.getSkillId()).ifPresent(si -> {
                Map<String, Object> skillMap = new HashMap<>();
                skillMap.put("id", si.getId());
                skillMap.put("name", si.getName());
                skillMap.put("description", si.getDescription());
                skillMap.put("icon", formatSkillIcon(si.getIcon()));
                skillMap.put("type", si.getType()); // 1: Active, 2: Passive
                skillMap.put("attribute", typeMap.getOrDefault(si.getDamageType(), "普通"));
                skillMap.put("category", si.getSkillTypeName());
                skillMap.put("power", si.getPower());
                skillMap.put("energyConsumption", si.getEnergyConsumption());
                
                // Fetch priority from skill_conf_main table
                jdbcTemplate.query("SELECT skill_priority FROM skill_conf_main WHERE id = ?", (rs) -> {
                    skillMap.put("priority", rs.getInt("skill_priority"));
                }, si.getId());
                if (!skillMap.containsKey("priority")) skillMap.put("priority", 0);
                
                categorizedSkills.get(source).add(skillMap);
            });
        }
        details.put("skills", categorizedSkills);
        details.put("evolutionChain", getEvolutionChain(id));
        details.put("natureRecommendation", getNatureRecommendation(p));

        // Associated Forms (Multiple Bosses if any)
        List<Map<String, Object>> bossForms = new ArrayList<>();
        if (p.getBookId() != null && p.getBookId() > 0) {
            List<Pet> forms = petRepository.findByBookId(p.getBookId());
            for (Pet f : forms) {
                if (f.getIsBoss() != null && f.getIsBoss() == 1) {
                    Map<String, Object> bossData = new HashMap<>();
                    bossData.put("id", f.getId());
                    bossData.put("name", f.getName());
                    bossData.put("hp", f.getHp());
                    bossData.put("attack", f.getAttack());
                    bossData.put("defense", f.getDefense());
                    bossData.put("sp_atk", f.getSp_atk());
                    bossData.put("sp_def", f.getSp_def());
                    bossData.put("speed", f.getSpeed());
                    bossData.put("type1", f.getPrimary_type());
                    bossData.put("type2", f.getSecondary_type());
                    bossData.put("imageUrl", formatImageUrl(f.getImageUrl(), f.getName()));
                    bossForms.add(bossData);
                }
            }
        }
        details.put("bossForms", bossForms);

        return details;
    }

    private Map<Integer, String> getEggGroupNameMap() {
        Map<Integer, String> map = new HashMap<>();
        map.put(1, "植物组");
        map.put(2, "动物组");
        map.put(3, "龙系组");
        map.put(4, "守护组");
        map.put(5, "萌系组");
        map.put(6, "精灵组");
        map.put(7, "唯美组");
        map.put(8, "力量组");
        map.put(9, "矿石组");
        map.put(10, "不死组");
        map.put(11, "翼组");
        map.put(12, "猎鹰组");
        map.put(13, "幻灵组");
        map.put(14, "神系组");
        map.put(15, "动作组");
        map.put(16, "未知组");
        return map;
    }

    public String formatImageUrl(String raw, String petName) {
        // If raw is empty/null, try to find image by pet name
        if (raw == null || raw.isEmpty()) {
            if (petName != null && !petName.isEmpty()) {
                // Map pet name to image filename convention
                // e.g., "喵喵" -> "JL_miaomiao.png"
                String nameKey = petName.toLowerCase()
                    .replace(" ", "")
                    .replace("·", "")
                    .replace(".", "")
                    .replace("(", "")
                    .replace(")", "");
                return "pets/JL_" + nameKey + ".png";
            }
            return "";
        }
        try {
            // Find the JL_ prefix which is consistent across pet images
            int start = raw.lastIndexOf("JL_");
            if (start == -1) return "";
            String segment = raw.substring(start);
            // Remove everything after the first dot or quote
            int end = segment.indexOf(".");
            if (end == -1) end = segment.indexOf("'");
            if (end != -1) segment = segment.substring(0, end);

            // Map to physical media/pets/ directory
            return "pets/" + segment + ".png";
        } catch (Exception e) {
            return raw;
        }
    }

    // Legacy method for backward compatibility
    public String formatImageUrl(String raw) {
        return formatImageUrl(raw, null);
    }

    public String formatSkillIcon(String raw) {
        if (raw == null || raw.isEmpty()) return "";
        try {
            String path = raw;
            if (path.startsWith("Texture2D'")) {
                path = path.substring(10, path.length() - 1);
            }
            
            // Identify the subfolder (FeatureIcon, SkillIcon, or Combat)
            String subPath = "";
            if (path.contains("FeatureIcon/")) subPath = "FeatureIcon/";
            else if (path.contains("SkillIcon/")) subPath = "SkillIcon/";
            else if (path.contains("Combat/")) subPath = "Combat/";
            
            if (!subPath.isEmpty()) {
                int start = path.indexOf(subPath);
                String result = path.substring(start);
                int end = result.indexOf(".");
                if (end != -1) result = result.substring(0, end);
                
                // Map to physical media/skills/ subfolders
                return "skills/" + result + ".png";
            }
            
            // Fallback for old Skill_Skill_Attack format
            if (raw.startsWith("Skill_")) {
                return "skills/Combat/" + raw + ".png";
            }
            return raw;
        } catch (Exception e) {
            return raw;
        }
    }

    private List<Map<String, Object>> getEvolutionChain(Integer id) {
        // Find root
        Integer rootId = id;
        for (int i = 0; i < 5; i++) { // Limit depth to prevent loops
            List<Evolution> parents = evolutionRepository.findByTargetId(rootId);
            if (parents.isEmpty()) break;
            rootId = parents.get(0).getSourceId();
        }

        List<Map<String, Object>> nodes = new ArrayList<>();
        Map<Integer, Boolean> visited = new HashMap<>();
        collectEvolutions(rootId, nodes, visited, 0);
        return nodes;
    }

    private void collectEvolutions(Integer currentId, List<Map<String, Object>> nodes, Map<Integer, Boolean> visited, int level) {
        if (visited.containsKey(currentId)) return;
        visited.put(currentId, true);

        petRepository.findById(currentId).ifPresent(p -> {
            Map<String, Object> node = new HashMap<>();
            node.put("id", p.getId());
            node.put("name", p.getName());
            node.put("bookId", p.getBookId());
            node.put("type1", p.getPrimary_type());
            node.put("stage", level);
            node.put("imageUrl", formatImageUrl(p.getImageUrl(), p.getName()));
            
            List<Evolution> next = evolutionRepository.findBySourceId(currentId);
            List<Map<String, Object>> targets = new ArrayList<>();
            for (Evolution e : next) {
                Map<String, Object> targetInfo = new HashMap<>();
                targetInfo.put("targetId", e.getTargetId());
                targetInfo.put("needLevel", e.getNeedLevel());
                targetInfo.put("condition", e.getExtraCondition());
                targets.add(targetInfo);
            }
            node.put("evolvesTo", targets);
            nodes.add(node);

            for (Evolution e : next) {
                collectEvolutions(e.getTargetId(), nodes, visited, level + 1);
            }
        });
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
        return "当前赛季推荐战术体系:\n1. 幻光盾体系: 以‘圣湮伊莱娜’为核心，辅以神系控制。\n2. 消耗流: 依靠‘南圣兽雀羽’的回复与控制进行慢节奏博弈。\n3. 高爆强推: 以神系多段伤害精灵为核心进行速战。";
    }
}
