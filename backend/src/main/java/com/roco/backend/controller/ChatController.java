package com.roco.backend.controller;

import com.roco.backend.ai.IntentClassifier;
import com.roco.backend.ai.RocoAgent;
import com.roco.backend.ai.SimpleRocoAgent;
import com.roco.backend.model.dto.ChatRequest;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/chat")
@CrossOrigin("*")
public class ChatController {

    private final RocoAgent tacticalAgent;
    private final SimpleRocoAgent simpleAgent;
    private final IntentClassifier intentClassifier;

    public ChatController(RocoAgent tacticalAgent,
                          SimpleRocoAgent simpleAgent,
                          IntentClassifier intentClassifier) {
        this.tacticalAgent = tacticalAgent;
        this.simpleAgent = simpleAgent;
        this.intentClassifier = intentClassifier;
    }

    @PostMapping
    public String chat(@RequestBody ChatRequest request) {
        String message = request.getMessage();
        IntentClassifier.Intent intent = intentClassifier.classify(message);

        if (intent == IntentClassifier.Intent.TACTICAL) {
            // 战术推理：配招/阵容/对战分析 → 使用完整提示词 Agent
            return tacticalAgent.chat(message);
        } else {
            // 基础查询：精灵信息/技能/属性 → 使用轻量级 Agent
            return simpleAgent.chat(message);
        }
    }
}
