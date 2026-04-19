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
     * 为单个精灵生成战术评述 (全量机制 V5.5 深度数据镜像版)
     */
    public String generateReview(Integer petId) {
        // 1. 获取精灵基本信息和特性
        Map<String, Object> pet = jdbcTemplate.queryForMap(
            "SELECT p.*, s.name as feature_name, s.desc as feature_desc, t.name as type_name " +
            "FROM pets p " +
            "LEFT JOIN skill_conf_main s ON p.feature_id = s.id " +
            "LEFT JOIN types t ON p.main_type_id = t.id " +
            "WHERE p.id = ?", petId);

        String petName = (String) pet.get("name");
        String typeName = (String) pet.get("type_name");
        String featureName = (String) pet.get("feature_name");
        String featureDesc = cleanDescription((String) pet.get("feature_desc"));
        Integer mainTypeId = (Integer) pet.get("main_type_id");

        // 2. 获取分位数据
        String globalContext = getQuantileRankDescription("global", -1, pet);
        String typeContext = getQuantileRankDescription("type", mainTypeId, pet);

        // 3. 获取全量非血脉技能库
        List<Map<String, Object>> rawSkills = jdbcTemplate.queryForList(
            "SELECT s.id, s.name, s.desc, s.dam_para, t.name as attr_name " +
            "FROM skill_conf_main s " +
            "JOIN pet_level_skills pls ON s.id = pls.skill_id " +
            "LEFT JOIN types t ON s.skill_dam_type = t.id " + // 修正系别映射
            "WHERE pls.pet_id = ? " +
            "AND s.id < 7700000 " +
            "AND s.name NOT LIKE '%血脉%' " +
            "ORDER BY s.id DESC", petId);

        // 4. 提取属性覆盖审计
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

        // 5. 构建专家提示词 (V5.6 数据审计专家版 - 无主观偏见)
        String prompt = String.format(
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
            "1. 重点分析精灵的“原生特性(Feature)”与“兵器库(Movepool)”的属性分布及机制深度之间的底层协同。例如特性如何放大打击面优势，或者特定的机制如何支撑其战术地位。\n" +
            "2. 字数 180-230 字，用词必须极度专业、犀利、去口语化。\n" +
            "3. 指出其在当前竞技场环境下的差异化价值（如：多点打击核心、中盘反制点、高频控制轴）。\n" +
            "4. 评价该精灵在结合“血脉与愿力自由切换”系统后，战术上限的扩展方向。\n" +
            "5. 直接输出正文，不要分点，不要标题。",
            petName, typeName, globalContext, typeContext, featureName, featureDesc, coverageSummary, skillContext.toString()
        );

        return callAI(prompt);
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
            body.put("temperature", 0.7);

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

    public void saveReview(Integer petId, String content) {
        if (content == null || content.isEmpty()) return;
        jdbcTemplate.update("INSERT OR REPLACE INTO pet_ai_reviews (pet_id, review_content) VALUES (?, ?)", petId, content);
    }
}
