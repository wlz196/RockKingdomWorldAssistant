package com.roco.data.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class AiPetReviewService {

    private static final Logger log = LoggerFactory.getLogger(AiPetReviewService.class);

    @Value("${ai.openai.api-key}")
    private String apiKey;

    @Value("${ai.openai.base-url}")
    private String baseUrl;

    @Value("${ai.openai.model}")
    private String model;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final Pattern TAG_PATTERN = Pattern.compile("<[^>]*>");

    /**
     * 为单个精灵生成战术评述
     */
    public String generateReview(Integer petId) {
        Map<String, Object> pet = jdbcTemplate.queryForMap(
            "SELECT p.*, s.name as feature_name, s.desc as feature_desc, t.name as type_name " +
            "FROM pets p " +
            "LEFT JOIN skill_conf_main s ON p.feature_id = s.id " +
            "LEFT JOIN types t ON p.main_type_id = t.id " +
            "WHERE p.id = ?", petId);

        String structuredPrompt = buildStructuredReviewPrompt(petId, pet);
        if (structuredPrompt != null) {
            return callAI(structuredPrompt);
        }

        String fallbackPrompt = buildFallbackPrompt(petId, pet);
        return callAI(fallbackPrompt);
    }

    private String buildStructuredReviewPrompt(Integer petId, Map<String, Object> pet) {
        try {
            Map<String, Object> tacticalProfile = jdbcTemplate.queryForMap(
                "SELECT version, role_summary, offense_score, defense_score, speed_score, utility_score, synergy_score, flexibility_score, ceiling_score, floor_score, meta_fit_score, strengths, weaknesses, build_dependencies, profile_json, generated_by FROM pet_tactical_profiles WHERE pet_id = ?",
                petId
            );
            List<Map<String, Object>> buildProfiles = jdbcTemplate.queryForList(
                "SELECT build_name, build_type, recommended_skill_set, role_tags, playstyle_summary, strength_notes, weakness_notes, environment_notes, bloodline_options, priority FROM pet_build_profiles WHERE pet_id = ? ORDER BY priority DESC, id ASC LIMIT 2",
                petId
            );

            String petName = Objects.toString(pet.get("name"), "未知精灵");
            String typeName = Objects.toString(pet.get("type_name"), "未知系");
            String featureName = Objects.toString(pet.get("feature_name"), "暂无特性");
            String featureDesc = cleanDescription((String) pet.get("feature_desc"));

            return String.format(
                "你是洛克王国世界的高水平构筑分析师，面向进阶玩家输出战术评述。\n\n" +
                "请严格优先依据以下【结构化战术画像】和【构筑骨架】写作，只有在必要时才用基础资料补充事实，不要回退成对全量技能池的泛泛复述。\n\n" +
                "【精灵基础信息】\n" +
                "名称: %s\n属性: %s\n特性: %s\n特性描述: %s\n\n" +
                "【结构化战术画像】\n" +
                "版本: %s\n" +
                "一句话定位: %s\n" +
                "评分: 输出 %.1f / 生存 %.1f / 速度 %.1f / 功能 %.1f / 自洽 %.1f / 泛用 %.1f / 上限 %.1f / 下限 %.1f / 环境 %.1f\n" +
                "优势: %s\n" +
                "短板: %s\n" +
                "构筑依赖: %s\n" +
                "画像 JSON: %s\n\n" +
                "【构筑骨架】\n%s\n" +
                "【写作要求】\n" +
                "1. 输出 180-260 字，中文，面向进阶玩家。\n" +
                "2. 必须明确说明：主定位、强点、短板、成型条件。\n" +
                "3. 优先解释画像已经得出的结论，不要重新从原始技能池发散分析。\n" +
                "4. 如果画像与构筑都不完整，要明确指出当前判断仍偏保守。\n" +
                "5. 直接输出正文，不要分点，不要标题。",
                petName,
                typeName,
                featureName,
                featureDesc,
                Objects.toString(tacticalProfile.get("version"), "rules-v1"),
                Objects.toString(tacticalProfile.get("role_summary"), "待补画像"),
                toDouble(tacticalProfile.get("offense_score")),
                toDouble(tacticalProfile.get("defense_score")),
                toDouble(tacticalProfile.get("speed_score")),
                toDouble(tacticalProfile.get("utility_score")),
                toDouble(tacticalProfile.get("synergy_score")),
                toDouble(tacticalProfile.get("flexibility_score")),
                toDouble(tacticalProfile.get("ceiling_score")),
                toDouble(tacticalProfile.get("floor_score")),
                toDouble(tacticalProfile.get("meta_fit_score")),
                summarizeJsonText(tacticalProfile.get("strengths")),
                summarizeJsonText(tacticalProfile.get("weaknesses")),
                summarizeJsonText(tacticalProfile.get("build_dependencies")),
                Objects.toString(tacticalProfile.get("profile_json"), "{}"),
                summarizeBuildProfiles(buildProfiles)
            );
        } catch (Exception e) {
            return null;
        }
    }

    private String buildFallbackPrompt(Integer petId, Map<String, Object> pet) {
        String petName = (String) pet.get("name");
        String typeName = (String) pet.get("type_name");
        String featureName = (String) pet.get("feature_name");
        String featureDesc = cleanDescription((String) pet.get("feature_desc"));
        Integer mainTypeId = (Integer) pet.get("main_type_id");

        String globalContext = getQuantileRankDescription("global", -1, pet);
        String typeContext = getQuantileRankDescription("type", mainTypeId, pet);

        List<Map<String, Object>> rawSkills = jdbcTemplate.queryForList(
            "SELECT s.id, s.name, s.desc, s.dam_para, t.name as attr_name " +
            "FROM skill_conf_main s " +
            "JOIN pet_level_skills pls ON s.id = pls.skill_id " +
            "LEFT JOIN types t ON s.skill_dam_type = t.id " +
            "WHERE pls.pet_id = ? " +
            "AND s.id < 7700000 " +
            "AND s.name NOT LIKE '%血脉%' " +
            "ORDER BY s.id DESC", petId);

        Set<String> uniqueTypes = rawSkills.stream()
            .map(s -> (String) s.get("attr_name"))
            .filter(Objects::nonNull)
            .collect(Collectors.toCollection(TreeSet::new));
        String coverageSummary = String.join("、", uniqueTypes);

        StringBuilder skillContext = new StringBuilder();
        for (Map<String, Object> s : rawSkills) {
            String sName = (String) s.get("name");
            String sDesc = (String) s.get("desc");
            String sAttr = (String) s.get("attr_name");
            String cleanedDesc = cleanDescription(sDesc);
            boolean isGeneric = cleanedDesc != null && (cleanedDesc.length() < 15 || cleanedDesc.contains("造成物理伤害") || cleanedDesc.contains("造成魔法伤害"));
            if (isGeneric) {
                skillContext.append(String.format("【%s】[%s系]\n", sName, sAttr));
            } else {
                skillContext.append(String.format("【%s】[%s系]: %s\n", sName, sAttr, cleanedDesc));
            }
        }

        return String.format(
            "你是一个洛克王国顶级战术教官。请根据以下提供的【全量战术数据镜像】，为该精灵撰写一段深度战术点评。\n\n" +
            "【1. 核心属性与数值地位】\n属性: %s | 全球等级分位: %s | 本系内定位: %s\n\n" +
            "【2. 被动核心机制 (特性)】\n[%s]: %s\n\n" +
            "【3. 原生兵器库 (主动技能与打击面审计)】\n" +
            "原生属性覆盖: %s\n" +
            "详细技能列表:\n%s\n" +
            "【4. 系统背景潜能 (外部强化系统)】\n" +
            "- 该精灵可自由佩戴 18 系任一血脉，获得对应的《愿力冲击》（应对状态 1.5 倍威力）技能。\n" +
            "- 核心机制：支持“愿力强化”转属（1号位临时转属性，威力 80），赋予极高的实战博弈深度。\n\n" +
            "【写作要求】\n" +
            "1. 重点分析精灵的“原生特性(Feature)”与“兵器库(Movepool)”的属性分布及机制深度之间的底层协同。\n" +
            "2. 字数 180-230 字，用词必须极度专业、犀利、去口语化。\n" +
            "3. 指出其在当前竞技场环境下的差异化价值。\n" +
            "4. 直接输出正文，不要分点，不要标题。",
            typeName, globalContext, typeContext, featureName, featureDesc, coverageSummary, skillContext.toString()
        );
    }

    private String summarizeBuildProfiles(List<Map<String, Object>> buildProfiles) {
        if (buildProfiles == null || buildProfiles.isEmpty()) {
            return "暂无构筑骨架，当前评述需保守。";
        }
        StringBuilder sb = new StringBuilder();
        for (Map<String, Object> build : buildProfiles) {
            sb.append("- 构筑名: ").append(Objects.toString(build.get("build_name"), "未命名构筑")).append("\n");
            sb.append("  类型: ").append(Objects.toString(build.get("build_type"), "")).append("\n");
            sb.append("  角色标签: ").append(Objects.toString(build.get("role_tags"), "[]")).append("\n");
            sb.append("  技能集: ").append(Objects.toString(build.get("recommended_skill_set"), "[]")).append("\n");
            sb.append("  玩法概述: ").append(Objects.toString(build.get("playstyle_summary"), "暂无")).append("\n");
            sb.append("  优势说明: ").append(Objects.toString(build.get("strength_notes"), "暂无")).append("\n");
            sb.append("  弱点说明: ").append(Objects.toString(build.get("weakness_notes"), "暂无")).append("\n");
            sb.append("  环境说明: ").append(Objects.toString(build.get("environment_notes"), "暂无")).append("\n");
        }
        return sb.toString();
    }

    private String summarizeJsonText(Object raw) {
        if (raw == null) return "暂无";
        String text = raw.toString();
        try {
            JsonNode node = objectMapper.readTree(text);
            if (node.isArray()) {
                List<String> values = new ArrayList<>();
                node.forEach(item -> values.add(item.asText()));
                return String.join("；", values);
            }
        } catch (Exception ignored) {
        }
        return text;
    }

    private String cleanDescription(String raw) {
        if (raw == null) return "";
        return TAG_PATTERN.matcher(raw).replaceAll("").trim();
    }

    private String getQuantileRankDescription(String scope, Integer typeId, Map<String, Object> pet) {
        String table = scope.equals("global") ? "stat_quantiles_global" : "stat_quantiles_by_type";
        String sql = scope.equals("global") ? "SELECT * FROM " + table : "SELECT * FROM " + table + " WHERE type_id = " + typeId;

        List<Map<String, Object>> quantiles = jdbcTemplate.queryForList(sql);
        StringBuilder sb = new StringBuilder();

        String[] keys = {"hp", "attack", "defense", "magic_attack", "magic_defense", "speed"};
        String[] labels = {"精力", "攻击", "防御", "魔攻", "魔抗", "速度"};

        for (int i = 0; i < keys.length; i++) {
            String key = keys[i];
            String label = labels[i];
            Object valObj = pet.get(key);
            if (valObj == null) continue;
            double val = Double.valueOf(valObj.toString());

            Map<String, Object> qRow = quantiles.stream().filter(r -> r.get("stat_key").equals(key)).findFirst().orElse(null);
            if (qRow != null) {
                double p95 = ((Number) qRow.get("p95")).doubleValue();
                double p80 = ((Number) qRow.get("p80")).doubleValue();
                double p50 = ((Number) qRow.get("p50")).doubleValue();

                String rank = (val >= p95) ? "顶尖" : (val >= p80) ? "优秀" : (val >= p50) ? "基准" : "偏弱";
                sb.append(label).append(":").append(rank).append(" ");
            }
        }
        return sb.toString();
    }

    private String callAI(String prompt) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", "Bearer " + apiKey);

            Map<String, Object> body = new HashMap<>();
            body.put("model", model);
            body.put("messages", List.of(Map.of("role", "user", "content", prompt)));
            body.put("temperature", 0.6);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
            ResponseEntity<String> response = restTemplate.postForEntity(baseUrl + "/chat/completions", entity, String.class);

            if (response.getStatusCode() == HttpStatus.OK) {
                JsonNode root = objectMapper.readTree(response.getBody());
                return root.path("choices").get(0).path("message").path("content").asText().trim();
            }
        } catch (Exception e) {
            log.error("AI 评述生成失败: {}", e.getMessage());
        }
        return null;
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

    public void saveReview(Integer petId, String content) {
        if (content == null || content.isEmpty()) return;
        jdbcTemplate.update("INSERT OR REPLACE INTO pet_ai_reviews (pet_id, review_content) VALUES (?, ?)", petId, content);
    }
}
