package com.roco.data.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class PetBuildProfileDraftService {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public Map<String, Object> generateDraft(Integer petId) {
        Map<String, Object> draft = buildDraftPayload(petId);
        saveDraft(draft);
        return draft;
    }

    public void saveDraft(Map<String, Object> draft) {
        jdbcTemplate.update(
            "INSERT INTO pet_build_profiles (pet_id, build_name, build_type, core_skill_ids, optional_skill_ids, recommended_skill_set, bloodline_options, nature_options, talent_options, role_tags, playstyle_summary, strength_notes, weakness_notes, environment_notes, source, priority, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, datetime('now', 'localtime'))",
            draft.get("petId"),
            draft.get("buildName"),
            draft.get("buildType"),
            draft.get("coreSkillIds"),
            draft.get("optionalSkillIds"),
            draft.get("recommendedSkillSet"),
            draft.get("bloodlineOptions"),
            draft.get("natureOptions"),
            draft.get("talentOptions"),
            draft.get("roleTags"),
            draft.get("playstyleSummary"),
            draft.get("strengthNotes"),
            draft.get("weaknessNotes"),
            draft.get("environmentNotes"),
            draft.get("source"),
            draft.get("priority")
        );
    }

    public List<Map<String, Object>> getGenerateCandidates() {
        return jdbcTemplate.queryForList(
            "SELECT p.id, p.name, p.book_id, p.main_type_id, p.sub_type_id, EXISTS(SELECT 1 FROM pet_build_profiles pb WHERE pb.pet_id = p.id) AS has_build " +
            "FROM pets p WHERE p.is_official = 1 AND (p.evolution_targets IS NULL OR p.evolution_targets = '') ORDER BY p.id ASC"
        );
    }

    public Map<String, Object> buildDraftPayload(Integer petId) {
        Map<String, Object> pet = jdbcTemplate.queryForMap(
            "SELECT p.*, t1.name as main_type_name, t2.name as sub_type_name FROM pets p " +
            "LEFT JOIN types t1 ON p.main_type_id = t1.id " +
            "LEFT JOIN types t2 ON p.sub_type_id = t2.id WHERE p.id = ?",
            petId
        );

        List<Map<String, Object>> skills = jdbcTemplate.queryForList(
            "SELECT sc.id, sc.name, sc.type, sc.damage_type, sc.skill_priority, sc.dam_para, sc.skill_dam_type, sc.is_official, pls.source, pls.learn_level " +
            "FROM pet_level_skills pls JOIN skill_conf_main sc ON pls.skill_id = sc.id WHERE pls.pet_id = ? AND sc.type = 1 ORDER BY pls.source ASC, sc.id ASC",
            petId
        );

        if (skills.isEmpty()) {
            Map<String, Object> result = new HashMap<>();
            result.put("petId", petId);
            result.put("buildName", Objects.toString(pet.get("name"), "未知精灵") + "基础构筑");
            result.put("buildType", "通用");
            result.put("coreSkillIds", "[]");
            result.put("optionalSkillIds", "[]");
            result.put("recommendedSkillSet", "[]");
            result.put("bloodlineOptions", "[]");
            result.put("natureOptions", "[]");
            result.put("talentOptions", "[]");
            result.put("roleTags", "[]");
            result.put("playstyleSummary", "当前缺少可用主动技能，需先补技能池后再生成构筑草稿。");
            result.put("strengthNotes", "面板信息已就绪，但技能池不足以支持构筑判断。");
            result.put("weaknessNotes", "缺少主动技能记录，当前无法形成可靠配招骨架。");
            result.put("environmentNotes", "待技能数据补齐后再评估环境定位。");
            result.put("source", "auto-draft-v1");
            result.put("priority", 50);
            return result;
        }

        String attackStyle = inferAttackStyle(pet);
        List<Map<String, Object>> scoredSkills = skills.stream()
            .map(skill -> scoreSkill(skill, attackStyle))
            .sorted((a, b) -> Double.compare(toDouble(b.get("weight")), toDouble(a.get("weight"))))
            .collect(Collectors.toList());

        List<Integer> coreSkillIds = scoredSkills.stream().limit(4).map(skill -> toInt(skill.get("id"))).distinct().collect(Collectors.toList());
        List<Integer> optionalSkillIds = scoredSkills.stream().skip(Math.min(4, scoredSkills.size())).limit(6).map(skill -> toInt(skill.get("id"))).filter(id -> !coreSkillIds.contains(id)).distinct().collect(Collectors.toList());
        List<Integer> recommendedSkillSet = new ArrayList<>(coreSkillIds.stream().limit(4).collect(Collectors.toList()));
        if (recommendedSkillSet.isEmpty() && !optionalSkillIds.isEmpty()) recommendedSkillSet.addAll(optionalSkillIds.stream().limit(4).collect(Collectors.toList()));

        List<String> roleTags = inferRoleTags(pet, scoredSkills);
        String buildName = inferBuildName(pet, attackStyle, roleTags);
        String buildType = inferBuildType(scoredSkills);
        String playstyleSummary = inferPlaystyleSummary(pet, attackStyle, scoredSkills, roleTags);
        String strengthNotes = inferStrengthNotes(pet, scoredSkills, attackStyle);
        String weaknessNotes = inferWeaknessNotes(pet, scoredSkills, attackStyle);
        String environmentNotes = inferEnvironmentNotes(roleTags, scoredSkills);

        Map<String, Object> result = new HashMap<>();
        result.put("petId", petId);
        result.put("buildName", buildName);
        result.put("buildType", buildType);
        result.put("coreSkillIds", toJson(coreSkillIds));
        result.put("optionalSkillIds", toJson(optionalSkillIds));
        result.put("recommendedSkillSet", toJson(recommendedSkillSet));
        result.put("bloodlineOptions", "[]");
        result.put("natureOptions", "[]");
        result.put("talentOptions", "[]");
        result.put("roleTags", toJson(roleTags));
        result.put("playstyleSummary", playstyleSummary);
        result.put("strengthNotes", strengthNotes);
        result.put("weaknessNotes", weaknessNotes);
        result.put("environmentNotes", environmentNotes);
        result.put("source", "auto-draft-v1");
        result.put("priority", 50);
        return result;
    }

    private Map<String, Object> scoreSkill(Map<String, Object> skill, String attackStyle) {
        Map<String, Object> result = new HashMap<>(skill);
        double weight = 0.0;
        int source = toInt(skill.get("source"));
        int damageType = toInt(skill.get("damage_type"));
        int priority = toInt(skill.get("skill_priority"));
        int power = parsePower(skill.get("dam_para"));

        weight += 1.0;
        if (source == 1 || source == 2) weight += 2.0;
        if (priority > 0) weight += 2.0;
        if (power >= 100) weight += 2.0;
        else if (power >= 70) weight += 1.2;
        else if (power > 0) weight += 0.6;

        if ("物攻".equals(attackStyle) && damageType == 2) weight += 2.5;
        if ("魔攻".equals(attackStyle) && damageType == 3) weight += 2.5;
        if ("双攻".equals(attackStyle) && (damageType == 2 || damageType == 3)) weight += 1.8;
        if (damageType == 1 || damageType == 0) weight += 0.6; // 变化/非伤害保底权重

        result.put("weight", weight);
        return result;
    }

    private String inferAttackStyle(Map<String, Object> pet) {
        int atk = toInt(pet.get("attack"));
        int matk = toInt(pet.get("magic_attack"));
        if (atk >= matk + 15) return "物攻";
        if (matk >= atk + 15) return "魔攻";
        return "双攻";
    }

    private List<String> inferRoleTags(Map<String, Object> pet, List<Map<String, Object>> scoredSkills) {
        List<String> tags = new ArrayList<>();
        int speed = toInt(pet.get("speed"));
        int hp = toInt(pet.get("hp"));
        int defense = toInt(pet.get("defense"));
        int magicDefense = toInt(pet.get("magic_defense"));
        int attack = toInt(pet.get("attack"));
        int magicAttack = toInt(pet.get("magic_attack"));
        boolean hasPriority = scoredSkills.stream().anyMatch(skill -> toInt(skill.get("skill_priority")) > 0);
        long supportCount = scoredSkills.stream().filter(skill -> toInt(skill.get("damage_type")) == 1 || toInt(skill.get("damage_type")) == 0).count();

        if (attack >= 120 || magicAttack >= 120) tags.add("主C");
        else tags.add("副C");
        if (speed >= 115 || hasPriority) tags.add("先手");
        if (hp >= 120 || (defense >= 115 && magicDefense >= 115)) tags.add("站场");
        if (supportCount >= 2) tags.add("功能");
        return tags.stream().distinct().limit(3).collect(Collectors.toList());
    }

    private String inferBuildName(Map<String, Object> pet, String attackStyle, List<String> roleTags) {
        int speed = toInt(pet.get("speed"));
        if (roleTags.contains("站场")) return "站场" + attackStyle + "流";
        if (roleTags.contains("先手") && speed >= 115) return "高速" + attackStyle + "流";
        if (roleTags.contains("功能")) return "中速压制流";
        return "均衡输出流";
    }

    private String inferBuildType(List<Map<String, Object>> scoredSkills) {
        long specialSourceCount = scoredSkills.stream().limit(4).filter(skill -> {
            int source = toInt(skill.get("source"));
            return source == 1 || source == 2;
        }).count();
        return specialSourceCount >= 2 ? "上限" : "通用";
    }

    private String inferPlaystyleSummary(Map<String, Object> pet, String attackStyle, List<Map<String, Object>> scoredSkills, List<String> roleTags) {
        boolean hasPriority = scoredSkills.stream().anyMatch(skill -> toInt(skill.get("skill_priority")) > 0);
        if (roleTags.contains("站场")) {
            return "依靠较好的站场能力争取回合交换空间，再用核心技能逐步推进局面。";
        }
        if (hasPriority || roleTags.contains("先手")) {
            return "依靠速度线与先手技能争夺节奏，围绕主输出技能持续压制。";
        }
        if ("物攻".equals(attackStyle)) {
            return "以物理输出技能为主轴，在中前段主动换血中寻找突破口。";
        }
        if ("魔攻".equals(attackStyle)) {
            return "以魔法输出技能为主轴，通过稳定输出来建立中盘优势。";
        }
        return "以均衡输出与灵活换位为主，依靠技能池广度寻找更稳的对局解法。";
    }

    private String inferStrengthNotes(Map<String, Object> pet, List<Map<String, Object>> scoredSkills, String attackStyle) {
        List<String> notes = new ArrayList<>();
        int speed = toInt(pet.get("speed"));
        int attack = toInt(pet.get("attack"));
        int magicAttack = toInt(pet.get("magic_attack"));
        boolean hasPriority = scoredSkills.stream().anyMatch(skill -> toInt(skill.get("skill_priority")) > 0);
        boolean hasHighPower = scoredSkills.stream().anyMatch(skill -> parsePower(skill.get("dam_para")) >= 100);
        if (speed >= 115) notes.add("速度线较好，具备争夺先手的基础。 ");
        if (attack >= 120 || magicAttack >= 120) notes.add("主输出面板突出，具备稳定压制能力。 ");
        if (hasPriority) notes.add("技能池具备先手工具，能提升节奏主动权。 ");
        if (hasHighPower) notes.add("核心候选技能威力较高，具备中前段压制空间。 ");
        if (notes.isEmpty()) notes.add("技能池与面板基础完整，适合先形成一套保守通用构筑。 ");
        return String.join("", notes).trim();
    }

    private String inferWeaknessNotes(Map<String, Object> pet, List<Map<String, Object>> scoredSkills, String attackStyle) {
        List<String> notes = new ArrayList<>();
        int speed = toInt(pet.get("speed"));
        int hp = toInt(pet.get("hp"));
        int defense = toInt(pet.get("defense"));
        int magicDefense = toInt(pet.get("magic_defense"));
        int attack = toInt(pet.get("attack"));
        int magicAttack = toInt(pet.get("magic_attack"));
        boolean hasPriority = scoredSkills.stream().anyMatch(skill -> toInt(skill.get("skill_priority")) > 0);
        if (speed < 100 && !hasPriority) notes.add("速度线偏弱，容易在节奏交换中吃亏。 ");
        if (hp < 100 && defense < 100 && magicDefense < 100) notes.add("容错较低，面对高压对局时换入压力较大。 ");
        if (attack < 100 && magicAttack < 100) notes.add("输出端不够突出，压制能力可能有限。 ");
        long specialSourceCount = scoredSkills.stream().limit(4).filter(skill -> {
            int source = toInt(skill.get("source"));
            return source == 1 || source == 2;
        }).count();
        if (specialSourceCount >= 3) notes.add("较依赖特定来源技能成型，稳定性仍需实战验证。 ");
        if (notes.isEmpty()) notes.add("当前构筑仍偏保守骨架，后续需结合标签与实战再细化。 ");
        return String.join("", notes).trim();
    }

    private String inferEnvironmentNotes(List<String> roleTags, List<Map<String, Object>> scoredSkills) {
        boolean hasPriority = scoredSkills.stream().anyMatch(skill -> toInt(skill.get("skill_priority")) > 0);
        if (roleTags.contains("先手") || hasPriority) {
            return "更适合承担中前段节奏交换职责，面对慢速对手时更容易拿到主动。";
        }
        if (roleTags.contains("站场")) {
            return "更适合放进偏中盘博弈的体系，利用站场能力累积回合价值。";
        }
        return "当前更适合作为通用初稿，需结合具体环境与对位再做修订。";
    }

    private int parsePower(Object raw) {
        if (raw == null) return 0;
        String text = raw.toString().replace("[", "").replace("]", "").trim();
        if (text.isEmpty()) return 0;
        try {
            if (text.contains(",")) text = text.split(",")[0].trim();
            return Integer.parseInt(text);
        } catch (Exception e) {
            return 0;
        }
    }

    private int toInt(Object value) {
        if (value == null) return 0;
        if (value instanceof Number number) return number.intValue();
        try {
            return Integer.parseInt(value.toString());
        } catch (Exception e) {
            return 0;
        }
    }

    private double toDouble(Object value) {
        if (value == null) return 0.0;
        if (value instanceof Number number) return number.doubleValue();
        try {
            return Double.parseDouble(value.toString());
        } catch (Exception e) {
            return 0.0;
        }
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            return "[]";
        }
    }
}
