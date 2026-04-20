package com.roco.data.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class PetProfileEngineService {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public Map<String, Object> generateProfile(Integer petId) {
        Map<String, Object> payload = buildProfilePayload(petId);
        saveProfile(petId, payload);
        return payload;
    }

    public void saveProfile(Integer petId, Map<String, Object> payload) {
        jdbcTemplate.update(
            "INSERT OR REPLACE INTO pet_tactical_profiles (pet_id, version, profile_json, role_summary, offense_score, defense_score, speed_score, utility_score, synergy_score, flexibility_score, ceiling_score, floor_score, meta_fit_score, strengths, weaknesses, build_dependencies, generated_by, updated_at) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, datetime('now', 'localtime'))",
            petId,
            payload.get("version"),
            payload.get("profileJson"),
            payload.get("roleSummary"),
            payload.get("offenseScore"),
            payload.get("defenseScore"),
            payload.get("speedScore"),
            payload.get("utilityScore"),
            payload.get("synergyScore"),
            payload.get("flexibilityScore"),
            payload.get("ceilingScore"),
            payload.get("floorScore"),
            payload.get("metaFitScore"),
            payload.get("strengths"),
            payload.get("weaknesses"),
            payload.get("buildDependencies"),
            payload.get("generatedBy")
        );
    }

    public List<Map<String, Object>> getGenerateCandidates() {
        return jdbcTemplate.queryForList(
            "SELECT p.id, p.name, p.book_id, p.main_type_id, p.sub_type_id, pt.pet_id as has_profile " +
            "FROM pets p " +
            "LEFT JOIN pet_tactical_profiles pt ON p.id = pt.pet_id " +
            "WHERE p.is_official = 1 AND (p.evolution_targets IS NULL OR p.evolution_targets = '') " +
            "ORDER BY p.id ASC"
        );
    }

    public Map<String, Object> buildProfilePayload(Integer petId) {
        Map<String, Object> pet = jdbcTemplate.queryForMap(
            "SELECT p.*, t1.name as main_type_name, t2.name as sub_type_name, s.name as feature_name, s.desc as feature_desc " +
            "FROM pets p " +
            "LEFT JOIN types t1 ON p.main_type_id = t1.id " +
            "LEFT JOIN types t2 ON p.sub_type_id = t2.id " +
            "LEFT JOIN skill_conf_main s ON p.feature_id = s.id " +
            "WHERE p.id = ?", petId
        );

        List<Map<String, Object>> buildProfiles = jdbcTemplate.queryForList(
            "SELECT * FROM pet_build_profiles WHERE pet_id = ? ORDER BY priority DESC, id ASC", petId
        );
        List<Map<String, Object>> skillTags = jdbcTemplate.queryForList(
            "SELECT st.skill_id, sc.name, st.major_category, st.action_tags, st.trigger_tags, st.payoff_tags, st.synergy_tags, st.risk_tags, st.manual_score_attack, st.manual_score_defense, st.manual_score_utility " +
            "FROM skill_tactical_tags st LEFT JOIN skill_conf_main sc ON st.skill_id = sc.id " +
            "WHERE st.skill_id IN (SELECT skill_id FROM pet_level_skills WHERE pet_id = ?)", petId
        );
        List<Map<String, Object>> petSkills = jdbcTemplate.queryForList(
            "SELECT sc.id, sc.name, sc.desc, sc.skill_priority, sc.damage_type, sc.skill_type, sc.skill_dam_type, sc.dam_para, pls.source FROM pet_level_skills pls JOIN skill_conf_main sc ON pls.skill_id = sc.id WHERE pls.pet_id = ?",
            petId
        );
        List<Map<String, Object>> featureTags = jdbcTemplate.queryForList(
            "SELECT * FROM feature_tactical_tags WHERE feature_id = ?", pet.get("feature_id")
        );
        List<Map<String, Object>> glossaryTerms = jdbcTemplate.queryForList("SELECT term, tactical_meaning FROM mechanic_glossary ORDER BY term ASC LIMIT 50");
        List<Map<String, Object>> globalQuantiles = jdbcTemplate.queryForList("SELECT * FROM stat_quantiles_global ORDER BY stat_key ASC");
        List<Map<String, Object>> typeQuantiles = jdbcTemplate.queryForList(
            "SELECT * FROM stat_quantiles_by_type WHERE type_id = ? ORDER BY stat_key ASC", pet.get("main_type_id")
        );

        Map<String, String> statProfile = buildStatProfile(pet, globalQuantiles, typeQuantiles);
        Set<String> aggregatedActionTags = collectTagValues(skillTags, "action_tags");
        Set<String> aggregatedTriggerTags = collectTagValues(skillTags, "trigger_tags");
        Set<String> aggregatedPayoffTags = collectTagValues(skillTags, "payoff_tags");
        Set<String> aggregatedSynergyTags = collectTagValues(skillTags, "synergy_tags");
        Set<String> aggregatedRiskTags = collectTagValues(skillTags, "risk_tags");
        Set<String> featureTriggerTags = collectTagValues(featureTags, "trigger_tags");
        Set<String> featurePayoffTags = collectTagValues(featureTags, "payoff_tags");
        Set<String> featureSynergyTags = collectTagValues(featureTags, "synergy_tags");
        Set<String> buildRoleTags = collectBuildRoleTags(buildProfiles);

        Map<String, Object> conclusions = buildConclusions(pet, buildProfiles, petSkills, statProfile, aggregatedActionTags, aggregatedPayoffTags, aggregatedSynergyTags, buildRoleTags, featureTriggerTags, featurePayoffTags);
        List<String> strengths = buildStrengths(pet, buildProfiles, statProfile, petSkills, aggregatedActionTags, aggregatedPayoffTags, aggregatedSynergyTags, featurePayoffTags);
        List<String> weaknesses = buildWeaknesses(pet, buildProfiles, statProfile, petSkills, aggregatedActionTags, aggregatedRiskTags, featureTriggerTags);
        List<String> dependencies = buildDependencies(buildProfiles, aggregatedActionTags, aggregatedSynergyTags, featureTriggerTags, featureSynergyTags);

        double offenseScore = calcOffenseScore(pet, statProfile, aggregatedActionTags, aggregatedPayoffTags, skillTags);
        double defenseScore = calcDefenseScore(pet, statProfile, aggregatedActionTags, featureTags);
        double speedScore = calcSpeedScore(pet, statProfile, petSkills, aggregatedActionTags);
        double utilityScore = calcUtilityScore(aggregatedActionTags, aggregatedPayoffTags, skillTags);
        double synergyScore = calcSynergyScore(buildProfiles, aggregatedSynergyTags, featureSynergyTags, featureTags);
        double flexibilityScore = calcFlexibilityScore(buildProfiles, petSkills, buildRoleTags);
        double ceilingScore = clampScore((offenseScore + synergyScore + (containsAny(aggregatedPayoffTags, "爆发", "收割", "强势滚雪球") ? 1.0 : 0.0)) / 2.0);
        double floorScore = clampScore((defenseScore + flexibilityScore + (containsAny(aggregatedActionTags, "护盾", "减伤", "回血", "回能") ? 0.8 : 0.0)) / 2.0);
        double metaFitScore = clampScore((speedScore + utilityScore + synergyScore + flexibilityScore) / 4.0);

        Map<String, Object> evidence = buildEvidence(pet, buildProfiles, statProfile, petSkills, skillTags, featureTags, strengths, weaknesses, dependencies);

        Map<String, Object> profileJsonMap = new LinkedHashMap<>();
        profileJsonMap.put("petId", petId);
        profileJsonMap.put("petName", pet.get("name"));
        profileJsonMap.put("types", Arrays.asList(pet.get("main_type_name"), pet.get("sub_type_name")).stream().filter(Objects::nonNull).collect(Collectors.toList()));
        profileJsonMap.put("featureName", pet.get("feature_name"));
        profileJsonMap.put("statProfile", statProfile);
        profileJsonMap.put("buildCount", buildProfiles.size());
        profileJsonMap.put("skillTagCount", skillTags.size());
        profileJsonMap.put("skillPoolCount", petSkills.size());
        profileJsonMap.put("glossaryPreview", glossaryTerms.stream().limit(8).map(m -> m.get("term")).collect(Collectors.toList()));
        profileJsonMap.put("conclusions", conclusions);
        profileJsonMap.put("evidence", evidence);
        profileJsonMap.put("buildProfiles", buildProfiles);
        profileJsonMap.put("featureTags", featureTags);
        profileJsonMap.put("skillTags", skillTags.stream().limit(12).collect(Collectors.toList()));

        Map<String, Object> result = new HashMap<>();
        result.put("version", "rules-v1");
        result.put("profileJson", toJson(profileJsonMap));
        result.put("roleSummary", conclusions.get("roleSummary"));
        result.put("offenseScore", offenseScore);
        result.put("defenseScore", defenseScore);
        result.put("speedScore", speedScore);
        result.put("utilityScore", utilityScore);
        result.put("synergyScore", synergyScore);
        result.put("flexibilityScore", flexibilityScore);
        result.put("ceilingScore", ceilingScore);
        result.put("floorScore", floorScore);
        result.put("metaFitScore", metaFitScore);
        result.put("strengths", toJson(strengths));
        result.put("weaknesses", toJson(weaknesses));
        result.put("buildDependencies", toJson(dependencies));
        result.put("generatedBy", "PetProfileEngineService/rules-v1");
        return result;
    }

    private Map<String, String> buildStatProfile(Map<String, Object> pet, List<Map<String, Object>> globalQuantiles, List<Map<String, Object>> typeQuantiles) {
        Map<String, String> statProfile = new LinkedHashMap<>();
        statProfile.put("speedTier", getTier(toInt(pet.get("speed")), typeQuantiles, globalQuantiles, "speed"));
        statProfile.put("attackTier", getTier(toInt(pet.get("attack")), typeQuantiles, globalQuantiles, "attack"));
        statProfile.put("magicAttackTier", getTier(toInt(pet.get("magic_attack")), typeQuantiles, globalQuantiles, "magic_attack"));
        statProfile.put("hpTier", getTier(toInt(pet.get("hp")), typeQuantiles, globalQuantiles, "hp"));
        statProfile.put("defenseTier", getTier(toInt(pet.get("defense")), typeQuantiles, globalQuantiles, "defense"));
        statProfile.put("magicDefenseTier", getTier(toInt(pet.get("magic_defense")), typeQuantiles, globalQuantiles, "magic_defense"));
        statProfile.put("totalTier", getTier(toInt(pet.get("total_stats")), typeQuantiles, globalQuantiles, "total_stats"));
        return statProfile;
    }

    private Map<String, Object> buildConclusions(Map<String, Object> pet,
                                                 List<Map<String, Object>> buildProfiles,
                                                 List<Map<String, Object>> petSkills,
                                                 Map<String, String> statProfile,
                                                 Set<String> actionTags,
                                                 Set<String> payoffTags,
                                                 Set<String> synergyTags,
                                                 Set<String> buildRoleTags,
                                                 Set<String> featureTriggerTags,
                                                 Set<String> featurePayoffTags) {
        String mainRole = inferMainRole(statProfile, buildRoleTags, actionTags, payoffTags);
        String secondaryRole = inferSecondaryRole(statProfile, actionTags, payoffTags, synergyTags);
        String engineType = inferEngineType(featureTriggerTags, featurePayoffTags, synergyTags, actionTags);
        String winCondition = inferWinCondition(statProfile, actionTags, payoffTags, buildProfiles);
        String failurePoints = inferFailurePoints(statProfile, petSkills, actionTags);
        String roleSummary = mainRole + (secondaryRole != null ? " / " + secondaryRole : "") + "，核心依赖" + engineType;

        Map<String, Object> conclusions = new LinkedHashMap<>();
        conclusions.put("mainRole", mainRole);
        conclusions.put("secondaryRole", secondaryRole);
        conclusions.put("engineType", engineType);
        conclusions.put("winCondition", winCondition);
        conclusions.put("failurePoints", failurePoints);
        conclusions.put("roleSummary", roleSummary);
        conclusions.put("buildStatus", buildProfiles.isEmpty() ? "缺少稳定构筑" : "已有构筑支撑");
        conclusions.put("statProfile", statProfile);
        conclusions.put("typeName", pet.get("main_type_name"));
        return conclusions;
    }

    private List<String> buildStrengths(Map<String, Object> pet,
                                        List<Map<String, Object>> buildProfiles,
                                        Map<String, String> statProfile,
                                        List<Map<String, Object>> petSkills,
                                        Set<String> actionTags,
                                        Set<String> payoffTags,
                                        Set<String> synergyTags,
                                        Set<String> featurePayoffTags) {
        LinkedHashSet<String> strengths = new LinkedHashSet<>();
        if (isHighTier(statProfile.get("speedTier"))) strengths.add("速度线处于优势档，具备先手争夺或中前段抢节奏能力");
        if (isHighTier(statProfile.get("attackTier")) || isHighTier(statProfile.get("magicAttackTier"))) strengths.add("主输出轴分位较高，具备形成稳定压制面的纸面基础");
        if (isHighTier(statProfile.get("hpTier")) || (isHighTier(statProfile.get("defenseTier")) && isHighTier(statProfile.get("magicDefenseTier")))) strengths.add("耐久端不差，能为中盘换入和持续博弈提供容错");
        if (containsAny(actionTags, "优先级", "打断", "控制", "驱散", "净化")) strengths.add("技能组带有功能性工具，面对不同对位时更容易掌握节奏主动权");
        if (containsAny(payoffTags, "爆发", "收割", "强势滚雪球", "资源获取")) strengths.add("技能收益结构偏正向，具备滚雪球或终结残局的空间");
        if (!featurePayoffTags.isEmpty()) strengths.add("特性能提供额外收益轴，可放大原生技能组的实战上限");
        if (!buildProfiles.isEmpty()) {
            String note = Objects.toString(buildProfiles.get(0).get("strength_notes"), "").trim();
            if (!note.isEmpty()) strengths.add(note);
        }
        if (strengths.isEmpty()) strengths.add("基础面板与技能池已具备起步分析价值，但仍需更多标签支撑精细判断");
        return new ArrayList<>(strengths).stream().limit(4).collect(Collectors.toList());
    }

    private List<String> buildWeaknesses(Map<String, Object> pet,
                                         List<Map<String, Object>> buildProfiles,
                                         Map<String, String> statProfile,
                                         List<Map<String, Object>> petSkills,
                                         Set<String> actionTags,
                                         Set<String> riskTags,
                                         Set<String> featureTriggerTags) {
        LinkedHashSet<String> weaknesses = new LinkedHashSet<>();
        if (isLowTier(statProfile.get("speedTier")) && !containsAny(actionTags, "优先级")) weaknesses.add("速度线偏弱且缺少显著先手工具，容易在节奏交换中落后");
        if (isLowTier(statProfile.get("hpTier")) && isLowTier(statProfile.get("defenseTier")) && isLowTier(statProfile.get("magicDefenseTier"))) weaknesses.add("身板与容错偏低，若前几回合未能建立主动，容易被高压环境直接压穿");
        if (isLowTier(statProfile.get("attackTier")) && isLowTier(statProfile.get("magicAttackTier"))) weaknesses.add("输出轴纸面一般，若没有额外收益条件，压制能力会偏平");
        if (!featureTriggerTags.isEmpty() && containsAny(featureTriggerTags, "对克制目标", "先手时", "应对成功后")) weaknesses.add("特性收益带有触发门槛，需要对位或操作条件成立后才能稳定兑现");
        if (containsAny(riskTags, "启动慢", "怕控场", "怕集火", "缺续航", "技能位紧张")) weaknesses.add("技能与构筑存在明显翻车点，需要围绕环境做好补位与保护");
        if (!buildProfiles.isEmpty()) {
            String note = Objects.toString(buildProfiles.get(0).get("weakness_notes"), "").trim();
            if (!note.isEmpty()) weaknesses.add(note);
        } else {
            weaknesses.add("缺少成熟构筑数据，当前画像对具体配招与上限路径的判断仍偏保守");
        }
        return new ArrayList<>(weaknesses).stream().limit(4).collect(Collectors.toList());
    }

    private List<String> buildDependencies(List<Map<String, Object>> buildProfiles,
                                           Set<String> actionTags,
                                           Set<String> synergyTags,
                                           Set<String> featureTriggerTags,
                                           Set<String> featureSynergyTags) {
        LinkedHashSet<String> dependencies = new LinkedHashSet<>();
        if (!buildProfiles.isEmpty()) {
            Map<String, Object> build = buildProfiles.get(0);
            if (hasText(build.get("core_skill_ids"))) dependencies.add("依赖核心技能链成型后才能稳定兑现主战术轴");
            if (hasText(build.get("bloodline_options"))) dependencies.add("存在血脉选项依赖，合理血脉会明显影响上限与对局覆盖");
            if (hasText(build.get("nature_options")) || hasText(build.get("talent_options"))) dependencies.add("对性格或特长配置有一定依赖，需要围绕主构筑方向取舍");
            if (hasText(build.get("recommended_skill_set"))) dependencies.add("推荐技能集较固定，技能位取舍将直接影响强度表现");
        } else {
            dependencies.add("缺少构筑数据，建议优先补充 pet_build_profiles 明确主战术轴");
        }
        if (containsAny(actionTags, "优先级") && containsAny(featureTriggerTags, "先手时")) dependencies.add("先手与特性触发存在联动，构筑上应尽量保证抢节奏手段");
        if (containsAny(synergyTags, "印记联动", "异常联动") || containsAny(featureSynergyTags, "印记联动", "异常联动")) dependencies.add("依赖联动型技能或状态条件成立，单挂时强度可能不如完整体系");
        if (dependencies.isEmpty()) dependencies.add("当前画像主要基于基础面板与技能池生成，建议逐步补齐构筑和标签数据");
        return new ArrayList<>(dependencies).stream().limit(4).collect(Collectors.toList());
    }

    private Map<String, Object> buildEvidence(Map<String, Object> pet,
                                              List<Map<String, Object>> buildProfiles,
                                              Map<String, String> statProfile,
                                              List<Map<String, Object>> petSkills,
                                              List<Map<String, Object>> skillTags,
                                              List<Map<String, Object>> featureTags,
                                              List<String> strengths,
                                              List<String> weaknesses,
                                              List<String> dependencies) {
        Map<String, Object> evidence = new LinkedHashMap<>();
        List<String> matchedRules = new ArrayList<>();
        if (isHighTier(statProfile.get("speedTier"))) matchedRules.add("speed-high-tier");
        if (isHighTier(statProfile.get("attackTier")) || isHighTier(statProfile.get("magicAttackTier"))) matchedRules.add("offense-high-tier");
        if (!buildProfiles.isEmpty()) matchedRules.add("has-build-profile");
        if (!skillTags.isEmpty()) matchedRules.add("has-skill-tags");
        if (!featureTags.isEmpty()) matchedRules.add("has-feature-tags");

        evidence.put("matchedRules", matchedRules);
        evidence.put("keyBuilds", buildProfiles.stream().limit(2).map(build -> Map.of(
            "buildName", Objects.toString(build.get("build_name"), "未命名构筑"),
            "buildType", Objects.toString(build.get("build_type"), ""),
            "priority", Objects.toString(build.get("priority"), "")
        )).collect(Collectors.toList()));
        evidence.put("keySkills", petSkills.stream().limit(6).map(skill -> Map.of(
            "id", skill.get("id"),
            "name", Objects.toString(skill.get("name"), ""),
            "priority", Objects.toString(skill.get("skill_priority"), "0"),
            "power", Objects.toString(skill.get("dam_para"), "")
        )).collect(Collectors.toList()));
        evidence.put("keyFeature", Map.of(
            "name", Objects.toString(pet.get("feature_name"), "暂无特性"),
            "hasFeatureTags", !featureTags.isEmpty(),
            "featureDesc", Objects.toString(pet.get("feature_desc"), "")
        ));
        evidence.put("notableStats", statProfile);
        evidence.put("summarySignals", Map.of(
            "strengthCount", strengths.size(),
            "weaknessCount", weaknesses.size(),
            "dependencyCount", dependencies.size()
        ));
        return evidence;
    }

    private String inferMainRole(Map<String, String> statProfile, Set<String> buildRoleTags, Set<String> actionTags, Set<String> payoffTags) {
        if (buildRoleTags.contains("主C")) return "主力输出核心";
        if (buildRoleTags.contains("控场")) return "控场节奏位";
        if (buildRoleTags.contains("坦克")) return "承压站场位";
        if (isHighTier(statProfile.get("speedTier")) && (isHighTier(statProfile.get("attackTier")) || isHighTier(statProfile.get("magicAttackTier")))) return "高速压制核心";
        if (containsAny(actionTags, "控制", "打断") && isHighTier(statProfile.get("speedTier"))) return "先手节奏位";
        if (containsAny(payoffTags, "续航", "抗压") && (isHighTier(statProfile.get("hpTier")) || isHighTier(statProfile.get("defenseTier")))) return "中场消耗位";
        if (isHighTier(statProfile.get("attackTier")) || isHighTier(statProfile.get("magicAttackTier"))) return "中盘输出位";
        return "战术支撑位";
    }

    private String inferSecondaryRole(Map<String, String> statProfile, Set<String> actionTags, Set<String> payoffTags, Set<String> synergyTags) {
        if (containsAny(actionTags, "优先级", "打断", "控制")) return "节奏辅助";
        if (containsAny(payoffTags, "收割", "爆发")) return "残局终结";
        if (containsAny(synergyTags, "资源循环", "中盘压制")) return "滚雪球推进";
        if (isHighTier(statProfile.get("defenseTier")) || isHighTier(statProfile.get("magicDefenseTier"))) return "承压换位";
        return null;
    }

    private String inferEngineType(Set<String> featureTriggerTags, Set<String> featurePayoffTags, Set<String> synergyTags, Set<String> actionTags) {
        if (containsAny(featurePayoffTags, "资源获取") || containsAny(actionTags, "回能")) return "资源循环发动机";
        if (containsAny(featurePayoffTags, "爆发", "强势滚雪球") || containsAny(synergyTags, "中盘压制")) return "滚雪球发动机";
        if (containsAny(featureTriggerTags, "先手时") || containsAny(actionTags, "优先级")) return "抢节奏发动机";
        if (containsAny(actionTags, "护盾", "减伤", "回血")) return "站场容错发动机";
        return "技能池与面板联动发动机";
    }

    private String inferWinCondition(Map<String, String> statProfile, Set<String> actionTags, Set<String> payoffTags, List<Map<String, Object>> buildProfiles) {
        if (!buildProfiles.isEmpty() && hasText(buildProfiles.get(0).get("playstyle_summary"))) return Objects.toString(buildProfiles.get(0).get("playstyle_summary"));
        if (containsAny(payoffTags, "爆发", "收割")) return "通过建立爆发回合或残局收割完成终结";
        if (containsAny(actionTags, "控制", "打断", "优先级")) return "通过掌控先手与中盘节奏，逐步把对手压入不利交换";
        if (isHighTier(statProfile.get("attackTier")) || isHighTier(statProfile.get("magicAttackTier"))) return "依靠高分位输出轴在正确对位中持续施压";
        return "通过技能池与面板优势寻找稳定换入点，逐步累积资源优势";
    }

    private String inferFailurePoints(Map<String, String> statProfile, List<Map<String, Object>> petSkills, Set<String> actionTags) {
        if (isLowTier(statProfile.get("speedTier")) && !containsAny(actionTags, "优先级")) return "若无法抢到节奏或吃到对面先手压制，很容易被迫交出主动权";
        if (isLowTier(statProfile.get("hpTier")) && isLowTier(statProfile.get("defenseTier")) && isLowTier(statProfile.get("magicDefenseTier"))) return "若前排无法创造安全换入窗口，容易在中前段直接被高压环境打穿";
        boolean hasSupportiveSkill = petSkills.stream().anyMatch(skill -> toInt(skill.get("skill_priority")) > 0 || toInt(skill.get("damage_type")) == 1 || toInt(skill.get("skill_type")) == 3);
        if (!hasSupportiveSkill) return "技能组支撑偏少，若主输出轴被限制，后续博弈空间会明显收窄";
        return "一旦核心收益条件迟迟无法兑现，整体强度会回落到较普通的纸面区间";
    }

    private double calcOffenseScore(Map<String, Object> pet, Map<String, String> statProfile, Set<String> actionTags, Set<String> payoffTags, List<Map<String, Object>> skillTags) {
        double base = Math.max(normalizeByTier(statProfile.get("attackTier")), normalizeByTier(statProfile.get("magicAttackTier")));
        if (containsAny(actionTags, "直接伤害", "连击", "条件增伤")) base += 0.8;
        if (containsAny(payoffTags, "爆发", "收割", "强势滚雪球")) base += 0.8;
        if (!skillTags.isEmpty()) base += Math.min(0.6, skillTags.size() * 0.05);
        return clampScore(base);
    }

    private double calcDefenseScore(Map<String, Object> pet, Map<String, String> statProfile, Set<String> actionTags, List<Map<String, Object>> featureTags) {
        double base = (normalizeByTier(statProfile.get("hpTier")) + normalizeByTier(statProfile.get("defenseTier")) + normalizeByTier(statProfile.get("magicDefenseTier"))) / 3.0;
        if (containsAny(actionTags, "护盾", "减伤", "回血", "锁血")) base += 1.0;
        if (!featureTags.isEmpty()) base += 0.5;
        return clampScore(base);
    }

    private double calcSpeedScore(Map<String, Object> pet, Map<String, String> statProfile, List<Map<String, Object>> petSkills, Set<String> actionTags) {
        double base = normalizeByTier(statProfile.get("speedTier"));
        boolean hasPriority = containsAny(actionTags, "优先级") || petSkills.stream().anyMatch(skill -> toInt(skill.get("skill_priority")) > 0);
        if (hasPriority) base += 1.0;
        return clampScore(base);
    }

    private double calcUtilityScore(Set<String> actionTags, Set<String> payoffTags, List<Map<String, Object>> skillTags) {
        double base = 4.0;
        if (containsAny(actionTags, "控制", "打断", "驱散", "净化", "转属性")) base += 2.0;
        if (containsAny(payoffTags, "资源获取", "续航", "抗压")) base += 1.2;
        base += Math.min(1.5, skillTags.size() * 0.08);
        return clampScore(base);
    }

    private double calcSynergyScore(List<Map<String, Object>> buildProfiles, Set<String> skillSynergyTags, Set<String> featureSynergyTags, List<Map<String, Object>> featureTags) {
        double base = 4.5;
        if (!buildProfiles.isEmpty()) base += 1.2;
        if (!skillSynergyTags.isEmpty()) base += 1.4;
        if (!featureSynergyTags.isEmpty()) base += 1.2;
        if (!featureTags.isEmpty()) base += 0.5;
        return clampScore(base);
    }

    private double calcFlexibilityScore(List<Map<String, Object>> buildProfiles, List<Map<String, Object>> petSkills, Set<String> buildRoleTags) {
        double base = 4.0;
        base += Math.min(2.0, buildProfiles.size() * 0.8);
        base += Math.min(1.5, Math.max(0, petSkills.size() - 6) * 0.08);
        if (buildRoleTags.size() >= 2) base += 1.0;
        return clampScore(base);
    }

    private Set<String> collectTagValues(List<Map<String, Object>> rows, String key) {
        Set<String> result = new LinkedHashSet<>();
        for (Map<String, Object> row : rows) {
            Object raw = row.get(key);
            if (raw == null) continue;
            result.addAll(parseTextArray(raw.toString()));
        }
        return result;
    }

    private Set<String> collectBuildRoleTags(List<Map<String, Object>> buildProfiles) {
        Set<String> tags = new LinkedHashSet<>();
        for (Map<String, Object> build : buildProfiles) {
            tags.addAll(parseTextArray(Objects.toString(build.get("role_tags"), "")));
        }
        return tags;
    }

    private List<String> parseTextArray(String raw) {
        if (raw == null || raw.isBlank()) return Collections.emptyList();
        try {
            Object parsed = objectMapper.readValue(raw, Object.class);
            if (parsed instanceof List<?> list) {
                return list.stream().map(String::valueOf).map(String::trim).filter(s -> !s.isEmpty()).distinct().collect(Collectors.toList());
            }
        } catch (Exception ignored) {
        }
        return Arrays.stream(raw.split("[,，]"))
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .distinct()
            .collect(Collectors.toList());
    }

    private String getTier(int value, List<Map<String, Object>> typeQuantiles, List<Map<String, Object>> globalQuantiles, String key) {
        Map<String, Object> row = typeQuantiles.stream().filter(q -> key.equals(q.get("stat_key"))).findFirst().orElse(
            globalQuantiles.stream().filter(q -> key.equals(q.get("stat_key"))).findFirst().orElse(null)
        );
        if (row == null) return "基准";
        double p95 = toDouble(row.get("p95"));
        double p80 = toDouble(row.get("p80"));
        double p50 = toDouble(row.get("p50"));
        if (value >= p95) return "顶尖";
        if (value >= p80) return "优秀";
        if (value >= p50) return "基准";
        return "偏弱";
    }

    private boolean isHighTier(String tier) {
        return "顶尖".equals(tier) || "优秀".equals(tier);
    }

    private boolean isLowTier(String tier) {
        return "偏弱".equals(tier);
    }

    private boolean containsAny(Set<String> source, String... candidates) {
        for (String candidate : candidates) {
            if (source.contains(candidate)) return true;
        }
        return false;
    }

    private boolean hasText(Object value) {
        return value != null && !value.toString().isBlank();
    }

    private double normalizeByTier(String tier) {
        if ("顶尖".equals(tier)) return 9.0;
        if ("优秀".equals(tier)) return 7.2;
        if ("基准".equals(tier)) return 5.4;
        return 3.2;
    }

    private double clampScore(double score) {
        return Math.min(10.0, Math.max(1.0, Math.round(score * 10.0) / 10.0));
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

    private int toInt(Object value) {
        if (value == null) return 0;
        if (value instanceof Number number) return number.intValue();
        try {
            return Integer.parseInt(value.toString());
        } catch (Exception e) {
            return 0;
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
