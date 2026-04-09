# 洛克王国战术 AI 助手

> 世界赛级别的精灵战术 AI，基于游戏底层机制进行深度推理，支持意图路由：简单查询极速响应，战术分析深度推理。

## 项目简介

本项目是一个面向洛克王国手游的战术 AI 助手，具备以下核心能力：

- **精灵查询**：种族值、被动特性、形态差异
- **技能分析**：可学技能列表（专属技能 / 技能石 / 血脉技能）
- **配招建议**：严格基于 4 技能槽约束，分析特性与技能的深度联动
- **能量管理**：结合四大回能途径（特性/技能回能、回复瓶、通用恢复）给出节奏建议
- **属性克制**：查询任意属性间的克制倍率与反制精灵
- **性格推荐**：根据种族值优势给出精准的百分比修正建议（基于数据库 ±10% 逻辑）

---

## 技术架构

```
┌─────────────────────────────────────────────────────────┐
│                    前端 (Web Dashboard)                   │
│  Vite + Vanilla JS + Glassmorphism UI  :5173             │
└─────────────────────────┬───────────────────────────────┘
                          │ HTTP POST /api/v1/chat
┌─────────────────────────▼───────────────────────────────┐
│               后端 (Spring Boot 3.2.4)  :8081            │
│                                                          │
│  ┌─ IntentClassifier（关键词路由，0 API 消耗） ──────────┐ │
│  │  含"配招/阵容/联动..."                               │ │
│  │      ↓ TACTICAL                  ↓ SIMPLE           │ │
│  │  RocoAgent                SimpleRocoAgent           │ │
│  │  系统提示词：4章战术手册    系统提示词：4行精简         │ │
│  │  (~800 token)             (~50 token)               │ │
│  └───────────────────────────────────────────────────── ┘ │
│                                                          │
│  Function Calling Tools (8个，两个 Agent 共用)            │
│    getPetInfo / getPetSkills / getTypeEffectiveness      │
│    getPetForms / getNatureRecommendation / searchBySkill │
│    getCounterPets / getRecommendedTeams                  │
│                                                          │
│  AI 驱动：LangChain4j + OpenAI 兼容 API                  │
└─────────────────────────┬───────────────────────────────┘
                          │ JPA + SQLite
┌─────────────────────────▼───────────────────────────────┐
│                   数据库 (SQLite)                         │
│  pets │ skill_conf_main │ pet_skill_mapping │ types      │
│  type_relations │ natures │ evolutions │ bloodlines      │
└─────────────────────────────────────────────────────────┘
```

---

## 意图路由设计

受 Claude Code 源码架构启发，本项目实现了基于关键词的双 Agent 路由：

| 意图类型 | 触发条件 | 路由目标 | 系统提示词 Token |
|---------|---------|---------|----------------|
| `TACTICAL` | 含"配招/阵容/联动/对战/打法..."等 30 个关键词 | `RocoAgent` | ~800 token（完整战术手册） |
| `SIMPLE` | 其他基础查询 | `SimpleRocoAgent` | ~50 token（极简提示词） |

分类器特点：零 API 消耗，亚毫秒级延迟，关键词命中任意一个即触发战术模式。

---

## 项目结构

```
RockKingdomWorldAssistant/
├── backend/                    # Spring Boot 后端
│   └── src/main/java/com/roco/backend/
│       ├── ai/                     # RocoAgent, SimpleRocoAgent, IntentClassifier, PetTools
│       ├── config/                 # WebConfig（静态资源映射）
│       ├── controller/             # ChatController, DataController, PetController
│       ├── model/                  # JPA 实体与 DTO
│       ├── repository/             # Spring Data JPA 仓库
│       └── service/                # PetService（业务逻辑）
├── frontend/                   # Vite + Vanilla JS 前端
│   ├── index.html + main.js        # AI 对话界面（玻璃拟态 UI）
│   ├── encyclopedia.html + .js     # 数据百科（9 个标签页）
│   ├── js/constants.js             # 属性常量与颜色辅助
│   └── js/api.js                   # 后端 URL 配置
└── media/                      # 精灵与技能图片资源
    ├── pets/                       # 精灵图片（JL_ 前缀）
    └── skills/                     # 技能图标
```

---

## 本地运行

### 环境要求

- Java 21+
- Maven 3.8+
- Node.js 20+

### 后端

```bash
cd backend
export AI_API_KEY=your-api-key-here
mvn spring-boot:run
# 服务启动在 http://localhost:8081
```

### 前端

```bash
cd frontend
npm install
npm run dev
# 访问 http://localhost:5173
```

---

## API 文档

### 对话接口

```
POST /api/v1/chat
Content-Type: application/json

{
  "message": "罗隐怎么配招？"
}
```

**TACTICAL 路由响应示例：**

```
推荐配招（4技能槽）：
① 魔爪（耗能0，回1能）— 回能核心
② 蝙蝠魔爪（吸血）— 生存续航
③ 彼岸之手（残血爆发）— 收割手段
④ 岩土暴击（增伤）— 压制输出

体系核心：石头大餐（扣5%血换1能）+ 魔爪 0耗能回能，形成动能永动链。
```

**SIMPLE 路由响应示例：**

```
罗隐（暗属性）
种族值：物攻 115 / 速度 108 / 物防 72
被动特性：石头大餐（消耗5%当前HP，回复1点能量）
```

### 数据接口

`DataController`（`/api/v1/data`）提供百科前端所需的数据 API，包括精灵、技能、性格、血脉、增益、天赋、属性等查询接口。
