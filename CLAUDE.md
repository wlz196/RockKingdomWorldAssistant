# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.
Always respond and communicate in Simplified Chinese (简体中文). All generated code comments should also be in Chinese

## 项目概述

洛克王国战术 AI 助手 — 面向洛克王国手游的战术 AI 助手，提供精灵数据查询、技能分析、阵容推荐、属性克制计算等功能，采用双 Agent 意图路由架构。

## 环境要求

- Java 17（pom.xml 中 `java.version` 指定）
- Maven 3.8+
- Node.js 20+
- SQLite 数据库文件 `roco_encyclopedia.db`

## 构建与运行

### 后端 (Spring Boot 3.2.4)
```bash
cd backend
export AI_API_KEY=your-api-key-here
mvn spring-boot:run          # 启动在 :8081
mvn package                  # 构建 jar
mvn test                     # 运行全部测试（需要 SQLite 数据库）
mvn test -Dtest=PetRepositoryTest  # 运行单个测试类
```

注意：`application.yml` 中 `spring.datasource.url` 默认为 `jdbc:sqlite:/root/roco/roco_encyclopedia.db`，本地开发需改为实际路径（数据库文件在项目根目录）。

### 前端 (Vite + Vanilla JS)
```bash
cd frontend
npm install
npm run dev                  # 开发服务器 :5173
npm run build                # 生产构建
```

前端无 lint/格式化工具配置，纯 Vanilla JS + Vite 构建。

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
- 使用 **Lombok**（`@Data` 等注解用于实体和 DTO）

### 两条 REST 接口路径
- `ChatController`（`/api/v1/chat`）— AI 对话端点，含意图路由
- `DataController`（`/api/v1/data`）— 百科前端的数据 API（精灵、技能、性格、血脉、增益、天赋、属性）

### 数据访问的双轨模式
- 有 JPA 实体的表（`pets`, `pet_skill_mapping`, `evolutions`, `natures`, `type_relations` 等）通过 `repository/` 中的 Spring Data JPA 仓库访问
- 无 JPA 实体的表（`skill_conf_main`, `pet_dimensions`, `buff_types`, `buffs`, `bloodlines`, `pet_talents`, `attributes`）通过 `JdbcTemplate` 原生 SQL 查询，主要在 `PetService` 和 `DataController` 中

### 前端页面
- `index.html` + `main.js` — AI 对话界面（玻璃拟态 UI）
- `encyclopedia.html` + `encyclopedia.js` — 完整数据百科，含 9 个标签页（精灵、技能、血脉、增益、性格、天赋、属性克制、数值模拟器、伤害计算器）
- `js/constants.js` — 共享属性常量和颜色辅助函数
- `js/api.js` — 后端 URL 配置和工具函数
- `query.html` — 基础表格查询页面

### 数据库
- **SQLite** 通过 JDBC + JPA/Hibernate 配合 `org.hibernate.community.dialect.SQLiteDialect`（来自 `hibernate-community-dialects` 包）
- `ddl-auto: none` — Hibernate 不管理表结构，所有表由数据库文件预置
- 主要表：`pets`, `skill_conf_main`, `pet_skill_mapping`, `type_relations`, `types`, `natures`, `evolutions`, `bloodlines`, `buffs`, `pet_talents`, `pet_dimensions`, `pet_egg_groups`

### 静态资源
- `WebConfig`（`config/` 包）映射 `/media/**` → `media/`（精灵/技能图片）和 `/**` → `frontend/`（从后端提供前端服务）

### 测试
- 测试使用 `@SpringBootTest` 加载完整上下文，需要 `@MockBean ChatLanguageModel` 来绕过 LangChain4j 自动配置对 AI 模型的依赖
- 测试依赖 SQLite 数据库文件存在于配置路径

## 关键约定

- 所有游戏领域术语使用中文（精灵=pet, 技能=skill, 属性=type, 性格=nature, 血脉=bloodline）
- 属性 ID 为整数，映射到 `types` 表中的名称（如 2=普通, 3=草, 4=火, 5=水）
- 精灵图片使用 `JL_` 前缀命名，技能图标分布在 `FeatureIcon/`、`SkillIcon/`、`Combat/` 子目录
- `pet_skill_mapping` 中的技能来源分类：自学、技能石、血脉
- `skill_conf_main` 中的分类字段：`type`（1=主动, 2=被动）、`damage_type`（1=无, 2=物理, 3=魔法, 4=特殊）、`skill_type`（1=攻击, 2=防御, 3=变化）
