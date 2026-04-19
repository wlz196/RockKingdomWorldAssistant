package com.roco.data.service;

import com.roco.data.model.entity.*;
import com.roco.data.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.Optional;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.Set;
import java.util.HashSet;

@Service
public class DataService {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PetRepository petRepository;

    @Autowired
    private PetSkillMappingRepository mappingRepository;

    @Autowired
    private PetFormRepository formRepository;

    @Autowired
    private EvolutionRepository evolutionRepository;

    @Autowired
    private NatureRepository natureRepository;

    public Map<String, Object> getPetDetails(Integer id) {
        Optional<Pet> petOpt = petRepository.findById(id);
        if (petOpt.isEmpty()) return null;
        Pet p = petOpt.get();

        // 从辅助表填充 Transient 字段
        // 1. 身高体重
        jdbcTemplate.query("SELECT height_min, height_max, weight_min, weight_max FROM pet_dimensions WHERE pet_id = ?", (rs) -> {
            p.setHeight(String.format("%.1fm - %.1fm", rs.getDouble("height_min"), rs.getDouble("height_max")));
            p.setWeight(String.format("%.1fkg - %.1fkg", rs.getDouble("weight_min"), rs.getDouble("weight_max")));
        }, p.getId());

        // 2. 蛋组
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
        details.put("form", p.getForm());
        details.put("totalStats", p.getTotalStats());

        // 扩展字段
        details.put("height", p.getHeight() != null ? p.getHeight() : "未知");
        details.put("weight", p.getWeight() != null ? p.getWeight() : "未知");
        details.put("eggGroups", p.getEggGroups());

        // 特性名称和描述
        details.put("featureName", "暂无特性");
        details.put("featureDesc", "");
        if (p.getPetFeature() != null) {
            jdbcTemplate.query("SELECT name, desc FROM skill_conf_main WHERE id = ?", (rs) -> {
                details.put("featureName", rs.getString("name"));
                details.put("featureDesc", rs.getString("desc"));
            }, p.getPetFeature());
        }

        // 预加载属性映射
        Map<Integer, String> typeMap = new HashMap<>();
        jdbcTemplate.query("SELECT id, name FROM types", (rs) -> {
            typeMap.put(rs.getInt("id"), rs.getString("name").replace("系", ""));
        });

        // 获取该精灵已标记的常用技能 ID 集合
        Set<Integer> commonSkillIds = new HashSet<>();
        jdbcTemplate.query("SELECT skill_id FROM pet_common_skills WHERE pet_id = ?", (rs) -> {
            commonSkillIds.add(rs.getInt("skill_id"));
        }, id);

        // 技能加载（按来源分类）
        List<PetSkillMapping> mappings = mappingRepository.findByPetId(id);
        Map<String, List<Map<String, Object>>> categorizedSkills = new HashMap<>();
        categorizedSkills.put("常用", new ArrayList<>());
        categorizedSkills.put("自学", new ArrayList<>());
        categorizedSkills.put("技能石", new ArrayList<>());
        categorizedSkills.put("血脉", new ArrayList<>());

        for (PetSkillMapping m : mappings) {
            String source = m.getSourceType();
            if (!categorizedSkills.containsKey(source)) {
                categorizedSkills.put(source, new ArrayList<>());
            }
            final String finalSource = source;
            jdbcTemplate.query("SELECT * FROM skill_conf_main WHERE id = ?", (rs) -> {
                Map<String, Object> skillMap = mapSkillRow(rs, typeMap);
                skillMap.put("isCommon", commonSkillIds.contains(skillMap.get("id")));

                if (categorizedSkills.containsKey(finalSource)) {
                    categorizedSkills.get(finalSource).add(skillMap);
                }
                
                // 如果是常用技能，也添加到“常用”分类中（避免重复查询）
                if (commonSkillIds.contains(skillMap.get("id"))) {
                    // 检查是否已经加入过常用（有些技能可能通过多个途径获得，这里只加一次）
                    boolean alreadyInCommon = categorizedSkills.get("常用").stream()
                        .anyMatch(s -> s.get("id").equals(skillMap.get("id")));
                    if (!alreadyInCommon) {
                        categorizedSkills.get("常用").add(skillMap);
                    }
                }
            }, m.getSkillId());
        }

        // 补全那些不在 level_skills 中但被标记为常用的技能（如果有的话）
        for (Integer csId : commonSkillIds) {
            boolean alreadyLoaded = categorizedSkills.values().stream()
                .flatMap(List::stream)
                .anyMatch(s -> s.get("id").equals(csId));
            
            if (!alreadyLoaded) {
                jdbcTemplate.query("SELECT * FROM skill_conf_main WHERE id = ?", (rs) -> {
                    Map<String, Object> skillMap = mapSkillRow(rs, typeMap);
                    skillMap.put("isCommon", true);
                    categorizedSkills.get("常用").add(skillMap);
                }, csId);
            }
        }
        details.put("skills", categorizedSkills);
        details.put("evolutionChain", getEvolutionChain(id));

        // 获取 AI 战术评述
        String aiReview = null;
        try {
            aiReview = jdbcTemplate.queryForObject(
                "SELECT review_content FROM pet_ai_reviews WHERE pet_id = ?",
                String.class, id
            );
        } catch (Exception e) {
            // 如果没有评述则为空
        }
        details.put("aiReview", aiReview);
        details.put("natureRecommendation", getNatureRecommendation(p));

        // 关联形态（Boss 形态）
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

    public String formatImageUrl(String raw, String petName) {
        if (raw == null || raw.isEmpty()) {
            if (petName != null && !petName.isEmpty()) {
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
            int start = raw.lastIndexOf("JL_");
            if (start == -1) return "";
            String segment = raw.substring(start);
            int end = segment.indexOf(".");
            if (end == -1) end = segment.indexOf("'");
            if (end != -1) segment = segment.substring(0, end);
            return "pets/" + segment + ".png";
        } catch (Exception e) {
            return raw;
        }
    }

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

            String subPath = "";
            if (path.contains("FeatureIcon/")) subPath = "FeatureIcon/";
            else if (path.contains("SkillIcon/")) subPath = "SkillIcon/";
            else if (path.contains("Combat/")) subPath = "Combat/";

            if (!subPath.isEmpty()) {
                int start = path.indexOf(subPath);
                String result = path.substring(start);
                int end = result.indexOf(".");
                if (end != -1) result = result.substring(0, end);
                return "skills/" + result + ".png";
            }

            if (raw.startsWith("Skill_")) {
                return "skills/Combat/" + raw + ".png";
            }
            return raw;
        } catch (Exception e) {
            return raw;
        }
    }

    private List<Map<String, Object>> getEvolutionChain(Integer id) {
        Integer rootId = id;
        for (int i = 0; i < 5; i++) {
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

    private Map<Integer, String> getEggGroupNameMap() {
        Map<Integer, String> map = new HashMap<>();
        map.put(1, "植物组"); map.put(2, "动物组"); map.put(3, "龙系组");
        map.put(4, "守护组"); map.put(5, "萌系组"); map.put(6, "精灵组");
        map.put(7, "唯美组"); map.put(8, "力量组"); map.put(9, "矿石组");
        map.put(10, "不死组"); map.put(11, "翼组"); map.put(12, "猎鹰组");
        map.put(13, "幻灵组"); map.put(14, "神系组"); map.put(15, "动作组");
        map.put(16, "未知组");
        return map;
    }

    public String getNatureRecommendation(Pet p) {
        if (p == null) return "数据缺失";
        return "【" + p.getName() + "】系统正在对当前竞技场环境进行深度建模，性格推荐与战术分析模块正在迭代中，敬请期待 AI 战术智库的深度评析。";
    }

    private Map<String, Object> mapSkillRow(java.sql.ResultSet rs, Map<Integer, String> typeMap) throws java.sql.SQLException {
        Map<String, Object> skillMap = new HashMap<>();
        skillMap.put("id", rs.getInt("id"));
        skillMap.put("name", rs.getString("name"));
        skillMap.put("description", rs.getString("desc"));
        skillMap.put("icon", formatSkillIcon(rs.getString("icon")));
        skillMap.put("type", rs.getInt("type"));

        int skillDamType = rs.getInt("skill_dam_type");
        skillMap.put("attribute", typeMap.getOrDefault(skillDamType, "无别"));

        int logicType = rs.getInt("type");
        int damageCategory = rs.getInt("damage_type");
        int skillClass = rs.getInt("skill_type");
        if (logicType == 2) {
            skillMap.put("category", "特性");
        } else {
            skillMap.put("category", switch (damageCategory) {
                case 2 -> "物理";
                case 3 -> "魔法";
                case 4 -> "特殊";
                default -> (skillClass == 3) ? "变化" : "常规";
            });
        }

        String damPara = rs.getString("dam_para");
        skillMap.put("power", damPara != null ? damPara : "0");

        String energyCost = rs.getString("energy_cost");
        try {
            skillMap.put("energyConsumption", energyCost != null
                ? Integer.parseInt(energyCost.replace("[", "").replace("]", ""))
                : 0);
        } catch (Exception e) {
            skillMap.put("energyConsumption", 0);
        }

        skillMap.put("priority", rs.getInt("skill_priority"));
        return skillMap;
    }

    public void toggleCommonSkill(Integer petId, Integer skillId) {
        Integer count = jdbcTemplate.queryForObject(
            "SELECT count(*) FROM pet_common_skills WHERE pet_id = ? AND skill_id = ?",
            Integer.class, petId, skillId
        );
        if (count != null && count > 0) {
            jdbcTemplate.update("DELETE FROM pet_common_skills WHERE pet_id = ? AND skill_id = ?", petId, skillId);
        } else {
            jdbcTemplate.update("INSERT INTO pet_common_skills (pet_id, skill_id) VALUES (?, ?)", petId, skillId);
        }
    }
}
