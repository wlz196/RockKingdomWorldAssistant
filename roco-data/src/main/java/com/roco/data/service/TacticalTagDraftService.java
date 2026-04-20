package com.roco.data.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class TacticalTagDraftService {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    public Map<String, Object> suggestSkillDraft(Integer skillId) {
        Map<String, Object> skill = jdbcTemplate.queryForMap(
            "SELECT id, name, desc, type, damage_type, skill_type, skill_priority, dam_para, energy_cost, skill_dam_type FROM skill_conf_main WHERE id = ?",
            skillId
        );

        LinkedHashSet<String> actionTags = new LinkedHashSet<>();
        LinkedHashSet<String> triggerTags = new LinkedHashSet<>();
        LinkedHashSet<String> payoffTags = new LinkedHashSet<>();
        LinkedHashSet<String> targetTags = new LinkedHashSet<>();
        LinkedHashSet<String> synergyTags = new LinkedHashSet<>();
        LinkedHashSet<String> riskTags = new LinkedHashSet<>();

        String desc = Objects.toString(skill.get("desc"), "");
        int damageType = toInt(skill.get("damage_type"));
        int priority = toInt(skill.get("skill_priority"));
        int power = parsePower(skill.get("dam_para"));

        String majorCategory = inferMajorCategory(desc, damageType);
        if (damageType == 2 || damageType == 3 || damageType == 4 || power > 0) actionTags.add("直接伤害");
        if (priority > 0) actionTags.add("优先级");
        if (containsAny(desc, "连击")) actionTags.add("连击");
        if (containsAny(desc, "打断")) actionTags.add("打断");
        if (containsAny(desc, "回血", "回复生命", "恢复生命")) actionTags.add("回血");
        if (containsAny(desc, "回复能量", "恢复能量", "回复自身能量")) actionTags.add("回能");
        if (containsAny(desc, "护盾")) actionTags.add("护盾");
        if (containsAny(desc, "减伤")) actionTags.add("减伤");
        if (containsAny(desc, "锁血")) actionTags.add("锁血");
        if (containsAny(desc, "净化", "解除异常")) actionTags.add("净化");
        if (containsAny(desc, "驱散", "消除强化")) actionTags.add("驱散");
        if (containsAny(desc, "眩晕", "睡眠", "冰冻", "麻痹", "恐惧", "沉默", "束缚")) actionTags.add("控制");
        if (containsAny(desc, "强化", "提升自身", "增益")) actionTags.add("强化");
        if (containsAny(desc, "印记")) actionTags.add("印记施加");
        if (containsAny(desc, "反击")) actionTags.add("反击");
        if (containsAny(desc, "条件增伤")) actionTags.add("条件增伤");

        if (containsAny(desc, "先手时")) triggerTags.add("先手时");
        if (containsAny(desc, "后手时")) triggerTags.add("后手时");
        if (containsAny(desc, "受击后", "受到攻击后")) triggerTags.add("受击后");
        if (containsAny(desc, "登场后", "出场时")) triggerTags.add("登场后");
        if (containsAny(desc, "连续使用")) triggerTags.add("连续使用");
        if (containsAny(desc, "满能量")) triggerTags.add("满能量时");
        if (containsAny(desc, "低血", "血量较低")) triggerTags.add("低血量时");
        if (containsAny(desc, "克制")) triggerTags.add("对克制目标");
        if (containsAny(desc, "异常状态")) triggerTags.add("对异常目标");
        if (containsAny(desc, "应对成功")) triggerTags.add("应对成功后");
        if (containsAny(desc, "自身有印记")) triggerTags.add("自身有印记");
        if (containsAny(desc, "对方有印记")) triggerTags.add("对方有印记");

        if (containsAny(desc, "自身")) targetTags.add("自身");
        if (containsAny(desc, "敌方全体", "所有敌方")) targetTags.add("敌方全体");
        else if (containsAny(desc, "敌方", "对手", "目标")) targetTags.add("敌方单体");
        if (containsAny(desc, "友方", "己方")) targetTags.add("友方");
        if (containsAny(desc, "场地", "天气", "环境")) targetTags.add("场地");

        inferHeuristicPayoffAndRisk(desc, power, priority, actionTags, payoffTags, synergyTags, riskTags);

        Map<String, Object> result = new HashMap<>();
        result.put("skillId", skillId);
        result.put("majorCategory", majorCategory);
        result.put("actionTags", toJsonArray(actionTags));
        result.put("triggerTags", toJsonArray(triggerTags));
        result.put("payoffTags", toJsonArray(payoffTags));
        result.put("targetTags", toJsonArray(targetTags));
        result.put("synergyTags", toJsonArray(synergyTags));
        result.put("riskTags", toJsonArray(riskTags));
        result.put("manualScoreAttack", calcAttackScore(actionTags, payoffTags, power, damageType));
        result.put("manualScoreDefense", calcDefenseScore(actionTags));
        result.put("manualScoreUtility", calcUtilityScore(actionTags, triggerTags, payoffTags));
        result.put("confidence", calcConfidence(actionTags, triggerTags, payoffTags, desc));
        result.put("source", "hybrid-draft-v1");
        return result;
    }

    public Map<String, Object> suggestFeatureDraft(Integer featureId) {
        Map<String, Object> feature = jdbcTemplate.queryForMap(
            "SELECT id, name, desc FROM skill_conf_main WHERE id = ?",
            featureId
        );

        String desc = Objects.toString(feature.get("desc"), "");
        LinkedHashSet<String> triggerTags = new LinkedHashSet<>();
        LinkedHashSet<String> payoffTags = new LinkedHashSet<>();
        LinkedHashSet<String> synergyTags = new LinkedHashSet<>();

        String triggerMode = inferTriggerMode(desc);
        String valueAxis = inferValueAxis(desc);

        if (containsAny(desc, "先手时")) triggerTags.add("先手时");
        if (containsAny(desc, "后手时")) triggerTags.add("后手时");
        if (containsAny(desc, "受击后", "受到攻击后")) triggerTags.add("受击后");
        if (containsAny(desc, "登场后", "出场时")) triggerTags.add("登场后");
        if (containsAny(desc, "克制")) triggerTags.add("对克制目标");
        if (containsAny(desc, "异常状态")) triggerTags.add("对异常目标");
        if (containsAny(desc, "应对成功")) triggerTags.add("应对成功后");
        if (containsAny(desc, "低血", "血量较低")) triggerTags.add("低血量时");

        if (containsAny(desc, "回能", "回复能量")) payoffTags.add("资源获取");
        if (containsAny(desc, "增伤", "威力提升", "额外伤害")) payoffTags.add("爆发");
        if (containsAny(desc, "增益", "强化", "提升自身")) payoffTags.add("启动");
        if (containsAny(desc, "回血", "回复生命", "减伤", "护盾", "锁血")) payoffTags.add("抗压");
        if (containsAny(desc, "持续", "叠加", "越战越强")) payoffTags.add("强势滚雪球");

        if (containsAny(desc, "印记")) synergyTags.add("印记联动");
        if (containsAny(desc, "异常")) synergyTags.add("异常联动");
        if (containsAny(desc, "应对")) synergyTags.add("应对联动");
        if (containsAny(desc, "回能", "资源")) synergyTags.add("资源循环");

        Map<String, Object> result = new HashMap<>();
        result.put("featureId", featureId);
        result.put("triggerMode", triggerMode);
        result.put("valueAxis", valueAxis);
        result.put("triggerTags", toJsonArray(triggerTags));
        result.put("payoffTags", toJsonArray(payoffTags));
        result.put("synergyTags", toJsonArray(synergyTags));
        result.put("floorBoost", inferFloorBoost(triggerMode, valueAxis, payoffTags));
        result.put("ceilingBoost", inferCeilingBoost(triggerMode, valueAxis, payoffTags));
        result.put("notes", buildFeatureNotes(desc, triggerMode, valueAxis));
        result.put("source", "hybrid-draft-v1");
        return result;
    }

    public void saveSkillDraft(Integer skillId, Map<String, Object> body) {
        jdbcTemplate.update(
            "INSERT OR REPLACE INTO skill_tactical_tags (skill_id, major_category, action_tags, trigger_tags, payoff_tags, target_tags, synergy_tags, risk_tags, manual_score_attack, manual_score_defense, manual_score_utility, confidence, source, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, datetime('now', 'localtime'))",
            skillId,
            body.get("majorCategory"),
            body.get("actionTags"),
            body.get("triggerTags"),
            body.get("payoffTags"),
            body.get("targetTags"),
            body.get("synergyTags"),
            body.get("riskTags"),
            body.get("manualScoreAttack"),
            body.get("manualScoreDefense"),
            body.get("manualScoreUtility"),
            body.get("confidence"),
            body.get("source")
        );
    }

    public void saveFeatureDraft(Integer featureId, Map<String, Object> body) {
        jdbcTemplate.update(
            "INSERT OR REPLACE INTO feature_tactical_tags (feature_id, trigger_mode, value_axis, trigger_tags, payoff_tags, synergy_tags, floor_boost, ceiling_boost, notes, source, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, datetime('now', 'localtime'))",
            featureId,
            body.get("triggerMode"),
            body.get("valueAxis"),
            body.get("triggerTags"),
            body.get("payoffTags"),
            body.get("synergyTags"),
            body.get("floorBoost"),
            body.get("ceilingBoost"),
            body.get("notes"),
            body.get("source")
        );
    }

    public Map<String, Object> generateSkillTag(Integer skillId) {
        Map<String, Object> draft = suggestSkillDraft(skillId);
        saveSkillDraft(skillId, draft);
        return draft;
    }

    public Map<String, Object> generateFeatureTag(Integer featureId) {
        Map<String, Object> draft = suggestFeatureDraft(featureId);
        saveFeatureDraft(featureId, draft);
        return draft;
    }

    public List<Map<String, Object>> getSkillGenerateCandidates() {
        return jdbcTemplate.queryForList(
            "SELECT sc.id, sc.name, CASE WHEN st.skill_id IS NULL THEN 0 ELSE 1 END AS has_tag " +
            "FROM skill_conf_main sc LEFT JOIN skill_tactical_tags st ON sc.id = st.skill_id " +
            "WHERE sc.type = 1 AND sc.is_official = 1 ORDER BY sc.id ASC"
        );
    }

    public List<Map<String, Object>> getFeatureGenerateCandidates() {
        return jdbcTemplate.queryForList(
            "SELECT DISTINCT sc.id, sc.name, CASE WHEN ft.feature_id IS NULL THEN 0 ELSE 1 END AS has_tag " +
            "FROM pets p JOIN skill_conf_main sc ON sc.id = p.feature_id " +
            "LEFT JOIN feature_tactical_tags ft ON sc.id = ft.feature_id " +
            "WHERE p.is_official = 1 AND (p.evolution_targets IS NULL OR p.evolution_targets = '') AND sc.type = 2 AND sc.is_official = 1 ORDER BY sc.id ASC"
        );
    }

    private String inferMajorCategory(String desc, int damageType) {
        if (damageType == 2 || damageType == 3 || damageType == 4 || containsAny(desc, "伤害", "攻击", "威力")) return "输出";
        if (containsAny(desc, "减伤", "护盾", "锁血", "回复生命")) return "防御";
        return "功能";
    }

    private String inferTriggerMode(String desc) {
        if (containsAny(desc, "若", "当", "满足条件", "克制", "低血", "先手", "后手", "应对成功")) return "条件触发";
        if (containsAny(desc, "受击后", "登场后", "出场时", "回合结束")) return "触发";
        return "常驻";
    }

    private String inferValueAxis(String desc) {
        if (containsAny(desc, "增伤", "额外伤害", "爆发", "威力提升")) return "输出";
        if (containsAny(desc, "减伤", "护盾", "锁血", "回血", "回复生命")) return "坦度";
        if (containsAny(desc, "回能", "恢复能量", "资源", "印记")) return "资源";
        return "功能";
    }

    private void inferHeuristicPayoffAndRisk(String desc,
                                             int power,
                                             int priority,
                                             Set<String> actionTags,
                                             Set<String> payoffTags,
                                             Set<String> synergyTags,
                                             Set<String> riskTags) {
        if (power >= 100 || containsAny(desc, "高额伤害", "大幅提高威力")) payoffTags.add("爆发");
        if (containsAny(desc, "斩杀", "收割", "目标血量越低")) payoffTags.add("收割");
        if (containsAny(desc, "回复生命", "回血", "减伤", "护盾", "锁血")) payoffTags.add("抗压");
        if (containsAny(desc, "回能", "回复能量", "恢复能量")) payoffTags.add("资源获取");
        if (containsAny(desc, "叠加", "持续强化", "越战越强")) payoffTags.add("强势滚雪球");
        if (priority > 0 || containsAny(desc, "先手")) payoffTags.add("节奏压制");

        if (containsAny(desc, "印记")) synergyTags.add("印记联动");
        if (containsAny(desc, "异常")) synergyTags.add("异常联动");
        if (containsAny(desc, "应对")) synergyTags.add("应对联动");
        if (containsAny(desc, "转属性", "血脉")) synergyTags.add("血脉联动");
        if (containsAny(desc, "回能", "叠加")) synergyTags.add("资源循环");

        if (containsAny(desc, "使用后自身", "副作用", "反噬")) riskTags.add("技能位紧张");
        if (containsAny(desc, "先手时", "应对成功", "克制")) riskTags.add("依赖对位");
        if (containsAny(desc, "无法回复", "不可连续")) riskTags.add("缺续航");
    }

    private double calcAttackScore(Set<String> actionTags, Set<String> payoffTags, int power, int damageType) {
        double score = 2.0;
        if (damageType == 2 || damageType == 3 || damageType == 4 || actionTags.contains("直接伤害")) score += 2.5;
        if (actionTags.contains("条件增伤") || payoffTags.contains("爆发")) score += 1.5;
        if (power >= 100) score += 1.5;
        if (payoffTags.contains("收割")) score += 1.0;
        return Math.min(10.0, Math.round(score * 10.0) / 10.0);
    }

    private double calcDefenseScore(Set<String> actionTags) {
        double score = 1.5;
        if (actionTags.contains("减伤")) score += 2.5;
        if (actionTags.contains("护盾")) score += 2.0;
        if (actionTags.contains("回血")) score += 2.0;
        if (actionTags.contains("锁血")) score += 1.5;
        return Math.min(10.0, Math.round(score * 10.0) / 10.0);
    }

    private double calcUtilityScore(Set<String> actionTags, Set<String> triggerTags, Set<String> payoffTags) {
        double score = 2.0;
        if (actionTags.contains("优先级")) score += 1.5;
        if (actionTags.contains("打断")) score += 1.5;
        if (actionTags.contains("控制")) score += 1.5;
        if (actionTags.contains("净化") || actionTags.contains("驱散")) score += 1.0;
        if (!triggerTags.isEmpty()) score += 1.0;
        if (payoffTags.contains("资源获取")) score += 1.0;
        return Math.min(10.0, Math.round(score * 10.0) / 10.0);
    }

    private double calcConfidence(Set<String> actionTags, Set<String> triggerTags, Set<String> payoffTags, String desc) {
        double confidence = 4.0;
        confidence += Math.min(2.0, actionTags.size() * 0.3);
        confidence += Math.min(1.5, triggerTags.size() * 0.3);
        confidence += Math.min(1.5, payoffTags.size() * 0.3);
        if (desc != null && desc.length() > 20) confidence += 0.8;
        return Math.min(9.5, Math.round(confidence * 10.0) / 10.0);
    }

    private double inferFloorBoost(String triggerMode, String valueAxis, Set<String> payoffTags) {
        double score = 4.0;
        if ("常驻".equals(triggerMode)) score += 2.0;
        if ("坦度".equals(valueAxis) || payoffTags.contains("抗压")) score += 2.0;
        if ("资源".equals(valueAxis) || payoffTags.contains("资源获取")) score += 1.0;
        return Math.min(10.0, Math.round(score * 10.0) / 10.0);
    }

    private double inferCeilingBoost(String triggerMode, String valueAxis, Set<String> payoffTags) {
        double score = 4.0;
        if ("条件触发".equals(triggerMode)) score += 1.5;
        if ("输出".equals(valueAxis) || payoffTags.contains("爆发")) score += 2.0;
        if (payoffTags.contains("强势滚雪球")) score += 1.5;
        return Math.min(10.0, Math.round(score * 10.0) / 10.0);
    }

    private String buildFeatureNotes(String desc, String triggerMode, String valueAxis) {
        return "该特性当前按" + triggerMode + " / " + valueAxis + "轴进行初稿分类，后续可结合构筑与画像再细化判断。";
    }

    private boolean containsAny(String text, String... candidates) {
        if (text == null || text.isBlank()) return false;
        for (String candidate : candidates) {
            if (text.contains(candidate)) return true;
        }
        return false;
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

    private String toJsonArray(Collection<String> values) {
        List<String> cleaned = values.stream().filter(Objects::nonNull).map(String::trim).filter(s -> !s.isEmpty()).distinct().collect(Collectors.toList());
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < cleaned.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append("\"").append(cleaned.get(i).replace("\"", "\\\"")).append("\"");
        }
        sb.append("]");
        return sb.toString();
    }
}
