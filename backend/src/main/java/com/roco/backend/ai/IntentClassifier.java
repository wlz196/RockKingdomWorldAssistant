package com.roco.backend.ai;

import org.springframework.stereotype.Component;
import java.util.List;

/**
 * 基于关键词的意图分类器。
 *
 * 分类逻辑：
 *   - TACTICAL：涉及配招、战术、阵容、联动、对战策略等，需要完整规则推理
 *   - SIMPLE：基础数据查询（精灵信息、技能列表、属性克制等），无需战术规则
 */
@Component
public class IntentClassifier {

    // 触发"战术 Agent"的关键词
    private static final List<String> TACTICAL_KEYWORDS = List.of(
        "配招", "配技", "出招", "阵容", "战术", "对战", "推荐技能", "怎么配",
        "如何打", "克制阵", "输出流", "消耗流", "联动", "搭配", "最强",
        "秒杀", "治疗流", "加速流", "反伤流", "对抗", "应对", "如何打败",
        "怎么赢", "战斗", "打法", "套路", "核心技", "无限", "循环"
    );

    public enum Intent {
        TACTICAL,   // 战术推理：配招/阵容/对战策略
        SIMPLE      // 基础查询：精灵信息/技能/属性
    }

    /**
     * 判断用户消息的意图类型。
     * 任意一个战术关键词命中，即路由到战术 Agent。
     */
    public Intent classify(String message) {
        if (message == null || message.isBlank()) {
            return Intent.SIMPLE;
        }
        for (String keyword : TACTICAL_KEYWORDS) {
            if (message.contains(keyword)) {
                return Intent.TACTICAL;
            }
        }
        return Intent.SIMPLE;
    }
}
