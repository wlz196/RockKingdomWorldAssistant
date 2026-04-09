package com.roco.backend.ai;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.spring.AiService;

/**
 * 轻量级查询 Agent，无战术推理规则，专门处理基础数据查询。
 * 适用意图：查精灵信息、查技能、查属性克制、查精灵简介等。
 */
@AiService
public interface SimpleRocoAgent {
    @SystemMessage({
        "你是洛克王国的数据助手。你的任务是通过调用工具查询数据库，",
        "准确、简洁地回答用户关于精灵基础信息的问题。",
        "回复控制在 150 字以内，直接给出数据，不需要战术分析。",
        "若查不到对应精灵，尝试别名后告知用户未找到，并提示检查名称是否正确。"
    })
    String chat(String userMessage);
}
