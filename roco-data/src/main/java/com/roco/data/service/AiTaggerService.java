package com.roco.data.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class AiTaggerService {

    private static final Logger log = LoggerFactory.getLogger(AiTaggerService.class);

    @Value("${ai.openai.api-key}")
    private String apiKey;

    @Value("${ai.openai.base-url}")
    private String baseUrl;

    @Value("${ai.openai.model}")
    private String model;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 调用 AI 分析技能并生成标签
     */
    public Map<String, Object> analyzeSkill(String name, String desc) {
        String prompt = String.format(
            "你是一个洛克王国策略专家。请根据以下技能信息进行战术打标。\n\n" +
            "技能名称: %s\n" +
            "技能描述: %s\n\n" +
            "【打标规则】\n" +
            "1. 必须且只能从 [攻击, 防御, 状态] 中选择一个作为 'base_tag'。有直接伤害判为攻击，核心为减伤判为防御，其余判为状态。\n" +
            "2. 从以下子标签中选择相关的项放入 'sub_tags' 数组中: \n" +
            "   回复自身能量, 叠加威力, 减少本技能能耗, 条件增伤, 应对状态, 应对攻击, 应对防御, 连击, 叠加连击数, 打断, 使用后自我惩罚, 斩杀收益, 增益, 回复自身生命, 赋予敌方减益, 应对成功额外效果, 自身获得印记, 敌方获得印记, 减伤, 锁血, 对方攻击变治疗\n" +
            "3. 请用 JSON 格式输出，格式如下：\n" +
            "{\"base_tag\": \"xxx\", \"sub_tags\": [\"tag1\", \"tag2\"]}",
            name, desc
        );

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", "Bearer " + apiKey);

            Map<String, Object> body = new HashMap<>();
            body.put("model", model);
            
            List<Map<String, String>> messages = new ArrayList<>();
            messages.add(Map.of("role", "user", "content", prompt));
            body.put("messages", messages);
            body.put("temperature", 0.3);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
            ResponseEntity<String> response = restTemplate.postForEntity(baseUrl + "/chat/completions", entity, String.class);

            if (response.getStatusCode() == HttpStatus.OK) {
                JsonNode root = objectMapper.readTree(response.getBody());
                String content = root.path("choices").get(0).path("message").path("content").asText();
                
                // 清理可能存在的 Markdown 代码块标记回程
                content = content.replaceAll("```json", "").replaceAll("```", "").trim();
                
                JsonNode result = objectMapper.readTree(content);
                Map<String, Object> map = new HashMap<>();
                map.put("baseTag", result.path("base_tag").asText());
                
                List<String> subTags = new ArrayList<>();
                result.path("sub_tags").forEach(node -> subTags.add(node.asText()));
                map.put("subTags", subTags);
                
                return map;
            }
        } catch (Exception e) {
            log.error("AI 打标失败: {} - {}", name, e.getMessage());
        }
        return null;
    }

    /**
     * 将解析出的标签组合成数据库存储格式
     */
    public String combineTags(Map<String, Object> analysis) {
        if (analysis == null) return null;
        String baseTag = (String) analysis.get("baseTag");
        List<String> subTags = (List<String>) analysis.get("subTags");
        
        List<String> all = new ArrayList<>();
        all.add(baseTag);
        if (subTags != null) {
            all.addAll(subTags);
        }
        
        return all.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .distinct()
                .collect(Collectors.joining(","));
    }

    /**
     * 批量分析多个技能 (极速模式)
     */
    public List<Map<String, Object>> analyzeSkillsBatch(List<Map<String, Object>> skills) {
        StringBuilder skillListText = new StringBuilder();
        for (Map<String, Object> s : skills) {
            skillListText.append(String.format("ID: %s | 名称: %s | 描述: %s\n", s.get("id"), s.get("name"), s.get("desc")));
        }

        String prompt = String.format(
            "你是一个洛克王国策略专家。请为以下技能列表进行批量战术打标。\n\n" +
            "【待分析列表】\n%s\n" +
            "【强制规则】\n" +
            "1. 每个技能必须且只能从 [攻击, 防御, 状态] 中选择一个作为 'base_tag'。有直接伤害判为攻击，核心保护判为防御，其余为状态。\n" +
            "2. 子标签限定范围: 回复自身能量, 叠加威力, 减少本技能能耗, 条件增伤, 应对状态, 应对攻击, 应对防御, 连击, 叠加连击数, 打断, 使用后自我惩罚, 斩杀收益, 增益, 回复自身生命, 赋予敌方减益, 应对成功额外效果, 自身获得印记, 敌方获得印记, 减伤, 锁血, 对方攻击变治疗\n" +
            "3. 输出格式要求：必须返回一个 JSON 数组，每个元素包含 id, base_tag, sub_tags 字段。",
            skillListText.toString()
        );

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", "Bearer " + apiKey);

            Map<String, Object> body = new HashMap<>();
            body.put("model", model);
            body.put("messages", List.of(Map.of("role", "user", "content", prompt)));
            body.put("temperature", 0.2);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
            ResponseEntity<String> response = restTemplate.postForEntity(baseUrl + "/chat/completions", entity, String.class);

            if (response.getStatusCode() == HttpStatus.OK) {
                JsonNode root = objectMapper.readTree(response.getBody());
                String content = root.path("choices").get(0).path("message").path("content").asText();
                content = content.replaceAll("```json", "").replaceAll("```", "").trim();
                
                JsonNode resultsNode = objectMapper.readTree(content);
                List<Map<String, Object>> results = new ArrayList<>();
                if (resultsNode.isArray()) {
                    for (JsonNode node : resultsNode) {
                        Map<String, Object> map = new HashMap<>();
                        map.put("id", node.path("id").asInt());
                        map.put("baseTag", node.path("base_tag").asText());
                        List<String> subTags = new ArrayList<>();
                        node.path("sub_tags").forEach(t -> subTags.add(t.asText()));
                        map.put("subTags", subTags);
                        results.add(map);
                    }
                }
                return results;
            }
        } catch (Exception e) {
            log.error("批量 AI 打标失败: {}", e.getMessage());
        }
        return Collections.emptyList();
    }
}
