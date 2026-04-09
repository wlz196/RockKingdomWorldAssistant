# 洛克王国战术 AI 助手 🏆

> 世界赛级别的精灵战术 AI，基于游戏底层机制进行深度推理，支持意图路由：简单查询极速响应，战术分析深度推理。

## 在线访问

| 服务 | 地址 |
|------|------|
| 🌐 Web 前端 | http://159.65.135.84:3000 |
| ⚡ 后端 API | http://159.65.135.84:8081 |

---

## 项目简介

本项目是一个面向洛克王国手游的**战术 AI 助手**，具备以下核心能力：

- **精灵查询**：种族值、被动特性、形态差异
- **技能分析**：可学技能列表（专属技能 / 技能石 / 血脉技能）
- **配招建议**：严格基于 4 技能槽约束，分析特性与技能的深度联动
- **能量管理**：结合四大回能途径（特性/技能回能、回复瓶、通用恢复）给出节奏建议
- **属性克制**：查询任意属性间的克制倍率与反制精灵
- **性格推荐**：根据种族值优势给出精准的百分比修正建议 (基于数据库 ±10% 逻辑)
- **数据同步**：支持从 aoe-top 开源项目一键同步最新官方数据

---

## 技术架构

```
┌─────────────────────────────────────────────────────────┐
│                    前端 (Web Dashboard)                   │
│  Vite + Vanilla JS + Glassmorphism UI  :3000             │
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
│  AI 驱动：Claude Sonnet (via gpt-agent.cc)               │
└─────────────────────────┬───────────────────────────────┘
                          │ JPA + SQLite
┌─────────────────────────▼───────────────────────────────┐
│                   数据库 (SQLite)                         │
│  pet_info │ pet_form │ skill_info │ pet_skill_mapping     │
│  type_chart │ nature │ recommended_team                  │
└─────────────────────────────────────────────────────────┘
```

---

## 意图路由设计

受 Claude Code 源码架构启发，本项目实现了**基于关键词的双 Agent 路由**：

| 意图类型 | 触发条件 | 路由目标 | 系统提示词 Token |
|---------|---------|---------|----------------|
| `TACTICAL` | 含"配招/阵容/联动/对战/打法..."等30个关键词 | `RocoAgent` | ~800 token（完整战术手册） |
| `SIMPLE` | 其他基础查询 | `SimpleRocoAgent` | ~50 token（极简提示词） |

**分类器特点：**
- 零 API 消耗，亚毫秒级延迟
- 关键词命中任意一个即触发战术模式
- 可随时扩展关键词列表

---

## AI 战术推理能力（TACTICAL 模式）

| 章节 | 核心内容 |
|------|---------|
| **能量动能博弈** | 上限10点；特性/技能/回复瓶/通用恢复（占1回合+5能）四大途径 |
| **属性计算体系** | `(种族资质+个体资质) × 性格修正(±10%) + 成长/星级加成` |
| **4技能槽硬约束** | 严格4槽位；特性触发条件优先匹配技能 |
| **高级战术规则** | 换宠惩罚（占1回合）；血脉影响技能池与共鸣魔法 |
| **容错逻辑** | 查不到先尝试别名/推理，最后才告知数据缺失 |
| **输出规范** | 配招回复 ≤300 字，先结论再逻辑 |

---

## 项目结构

```
roco/
├── backend/                    # Spring Boot 后端
│   ├── src/main/java/com/roco/backend/
│   │   ├── RocoAgent.java          # 战术 Agent（含4章游戏机制提示词）
│   │   ├── SimpleRocoAgent.java    # 查询 Agent（极简提示词）
│   │   ├── IntentClassifier.java   # 意图分类器（关键词路由）
│   │   ├── ChatController.java     # REST API + 路由控制
│   │   ├── PetTools.java           # Function Calling Tools (8个)
│   │   ├── PetService.java         # 业务逻辑层
│   │   └── *.java                  # 实体类与 Repository
│   └── src/main/resources/
│       └── application.yml         # AI API Key（环境变量）、数据库配置
├── frontend/                   # Vite 前端
│   ├── index.html              # 玻璃拟态 UI 布局
│   ├── main.js                 # 聊天逻辑
│   └── style.css               # Glassmorphism 样式
├── data.db                     # SQLite 数据库（376只精灵 + 1.3万条技能映射）
├── crawler.py                  # Wiki 数据爬虫
├── init_db.sql                 # 数据库初始化脚本
└── 设计文档.md                 # 详细设计文档
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
# 设置 AI API Key 环境变量
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

### 数据爬虫

```bash
# 从 wiki.lcx.cab 抓取精灵/技能数据写入 data.db
python3 crawler.py
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

**响应示例（TACTICAL 路由）：**

```
推荐配招（4技能槽）：
① 魔爪（耗能0，回1能）— 回能核心
② 蝙蝠魔爪（吸血）— 生存续航
③ 彼岸之手（残血爆发）— 收割手段
④ 岩土暴击（增伤）— 压制输出

体系核心：石头大餐（扣5%血换1能）+ 魔爪 0耗能回能，形成动能永动链。
```

**响应示例（SIMPLE 路由）：**

```
罗隐（暗属性）
种族值：物攻 115 / 速度 108 / 物防 72
被动特性：石头大餐（消耗5%当前HP，回复1点能量）
```

---

## 数据来源

通过 `crawler.py` 逆向抓取 [洛克王国 Wiki](https://wiki.lcx.cab/lk/) 的 JSON API 并落库：

| 表名 | 数据量 | 内容 |
|------|--------|------|
| `pet_info` | 505 条 | 种族值、简介 (已同步 aoe-top) |
| `skill_info` | 487 条 | 技能名称、属性、耗能、描述 |
| `nature` | 30 条 | 10% 比例修正值 (含多种稀有性格) |
| `type_chart` | 100+ 条 | 属性克制倍率 |

---

## 部署状态

| 项目 | 状态 |
|------|------|
| 云服务器 | DigitalOcean（新加坡）`159.65.135.84` |
| 后端进程 | `nohup java -jar backend.jar` |
| 前端服务 | `python3 -m http.server 3000` |
| 防火墙 | 入站开放 3000（Web）、8081（API） |
| AI Key 管理 | 环境变量 `AI_API_KEY`（不入代码库） |

---

## ⚖️ 数据同步与主权 (aoe-top 集成)

本项目支持通过 `sync_aoe_data.py` 脚本从 [aoe-top/rocom.aoe.top](https://github.com/aoe-top/rocom.aoe.top) (MIT License) 同步最新的官方精灵与技能数据。

### 如何同步数据：

1. **克隆数据源** (建议放在项目根目录):
   ```bash
   git clone https://github.com/aoe-top/rocom.aoe.top.git aoe_top_data
   ```

2. **运行同步脚本**:
   ```bash
   python3 sync_aoe_data.py
   ```
   该脚本会自动解析 `Pets.json`、`moves.json` 和 `personalities.json` 并增量更新本地 `data.db`。

3. **优势**:
   - **完全离线**: 无需实时联网抓取。
   - **精确修正**: 性格推荐算法已升级为基于数据库实测修正值的精确匹配。
   - **扩展性**: 支持后续自定义数据的二次开发。
