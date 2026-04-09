# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.
Always respond and communicate in Simplified Chinese (简体中文). All generated code comments should also be in Chinese

## 项目概述

洛克王国战术 AI 助手 — 面向洛克王国手游的战术 AI 助手，提供精灵数据查询、技能分析、阵容推荐、属性克制计算等功能，采用双 Agent 意图路由架构。

## 构建与运行

### 后端 (Spring Boot)
```bash
cd backend
export AI_API_KEY=your-api-key-here
mvn spring-boot:run          # 启动在 :8081
mvn package                  # 构建 jar
mvn test                     # 运行测试（需要 SQLite 数据库）
```

### 前端 (Vite + Vanilla JS)
```bash
cd frontend
npm install
npm run dev                  # 开发服务器 :5173
npm run build                # 生产构建
```

## 架构

### 双 Agent 意图路由
核心设计模式是 `controller/ChatController` 中基于关键词的意图路由：
- `IntentClassifier`（`ai/` 包）扫描用户消息中的约 30 个战术关键词（配招、阵容、战术等）
- **TACTICAL** 意图 → `RocoAgent` — 完整的 4 章游戏机制系统提示词（约 800 token），处理阵容搭配、克制分析、能量管理推理
- **SIMPLE** 意图 → `SimpleRocoAgent` — 精简系统提示词（约 50 token），处理基础数据查询
- 两个 Agent 共享 `ai/PetTools` 中定义的 8 个 function-calling 工具

### AI 集成
- 使用 **LangChain4j**（v0.35.0），兼容 OpenAI API 格式
- Agent 在 `ai/` 包中以 `@AiService` 接口 + `@SystemMessage` 注解声明
- 工具为 `ai/PetTools` 中的 `@Tool` 注解方法，委托给 `service/PetService`
- AI 提供商在 `application.yml` 中通过 `langchain4j.open-ai` 属性配置，API Key 来自 `AI_API_KEY` 环境变量

### 后端包结构
```
com.roco.backend/
├── config/        — WebConfig（静态资源映射）
├── controller/    — ChatController, DataController, PetController
├── model/entity/  — JPA 实体（Pet, SkillInfo, Evolution 等）
├── model/dto/     — DTO（ChatRequest, SkillDTO, SkillItemDTO）
├── repository/    — Spring Data JPA 仓库
├── service/       — PetService（业务逻辑）
└── ai/            — IntentClassifier, PetTools, RocoAgent, SimpleRocoAgent
```

### 两条 REST 接口路径
- `ChatController`（`/api/v1/chat`）— AI 对话端点，含意图路由
- `DataController`（`/api/v1/data`）— 百科前端的数据 API（精灵、技能、性格、血脉、增益、天赋、属性）

### 前端页面
- `index.html` + `main.js` — AI 对话界面（玻璃拟态 UI）
- `encyclopedia.html` + `encyclopedia.js` — 完整数据百科，含 9 个标签页（精灵、技能、血脉、增益、性格、天赋、属性克制、数值模拟器、伤害计算器）
- `js/constants.js` — 共享属性常量和颜色辅助函数
- `js/api.js` — 后端 URL 配置和工具函数
- `query.html` — 基础表格查询页面

### 数据库
- **SQLite** 通过 JDBC + JPA/Hibernate 配合 `SQLiteDialect`
- 数据库路径在 `application.yml` 中配置 — 需根据环境修改 `spring.datasource.url`
- 主要表：`pets`, `skill_conf_main`, `pet_skill_mapping`, `type_relations`, `types`, `natures`, `evolutions`, `bloodlines`, `buffs`, `pet_talents`, `pet_dimensions`, `pet_egg_groups`
- `PetService`（`service/` 包）混合使用 JPA 仓库和原生 `JdbcTemplate` 查询（用于无实体映射的表，如 `skill_conf_main`, `pet_dimensions`, `buff_types`）

### 静态资源
- `WebConfig`（`config/` 包）映射 `/media/**` → `media/`（精灵/技能图片）和 `/**` → `frontend/`（从后端提供前端服务）

## 关键约定

- 所有游戏领域术语使用中文（精灵=pet, 技能=skill, 属性=type, 性格=nature, 血脉=bloodline）
- 属性 ID 为整数，映射到 `types` 表中的名称（如 2=普通, 3=草, 4=火, 5=水）
- 精灵图片使用 `JL_` 前缀命名，技能图标分布在 `FeatureIcon/`、`SkillIcon/`、`Combat/` 子目录
- `pet_skill_mapping` 中的技能来源分类：自学、技能石、血脉
