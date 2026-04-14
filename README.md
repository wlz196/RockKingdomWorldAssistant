# 洛克王国数据百科与助手 (RoCo Encyclopedia)

> 洛克王国的资料库与辅助工具，支持查询精灵图鉴、技能血脉，以及模拟宠物能力和实战伤害对决。

## 项目简介

本项目是一个洛克王国的百科辅助站点，分为资料查询和战术模拟两部分核心功能：

- **数据资料查询**：
  - **精灵大全**：种族值、生蛋组、进化路线、身高体重、主副属性等核心指标
  - **技能血脉**：可学技能集、威力表现、血脉结晶机制等
  - **异常状态**：整理各种 Buff 的数值与修正效果
  - **特性与资源**：宠物性格表、天赋项等参考
- **辅助战术模拟**：
  - **能力模拟面板**：根据pvp拉平的等级星级，手动填入性格天分修正计算精灵数值
  - **伤害计算对抗**：模拟敌方不同性格天赋，计算伤害
  - **模拟对战**：根据双方精确属性，计算对战结果与伤害

---

## 模块结构

本工程现已进行针对性拆分与微服务化组织，拆分成独立的前后端数据链路：

```text
RockKingdomWorldAssistant/
├── roco-data/                  # 数据支撑后端 (Spring Boot)
│   ├── controller/             # 百科 RESTful 接口 (提供 /api/v1/data/*)
│   ├── model & repository/     # 映射 SQLite 本地数据库实体
│   └── 对应端口：8081
├── frontend-data/              # 百科主前端 (Vite + Vanilla JS)
│   ├── encyclopedia.html       # 应用级 SPA 主入口
│   ├── encyclopedia.js         # DOM 路由、搜索与核心算子推演
│   ├── encyclopedia.css        # 毛玻璃 (Glassmorphism) 主题框架
│   └── 对应端口：5173
├── media/                      # 静态多媒体资源库 (精灵图与技能 Icon)
├── roco_encyclopedia.db        # 核心数据库文件 (SQLite)
├── start-local.sh              # 统一启动脚本
└── [Deprecated] roco-ai/等     # 已分离的 AI 聊天扩展业务
```

---


## 本地运行

本工程提供了一键启动所有的环境方案，非常便捷。

### 环境要求

- **Java 21+**
- **Maven 3.8+**
- **Node.js 20+**

### 一键开箱启动

项目根目录提供了串接启动脚本 `start-local.sh`。您可以直接利用脚本将所需的前后端全部起跑。

```bash
# 进入工程目录
cd RockKingdomWorldAssistant

# 为了只跑纯净百科（不启动 AI 附属聊天程序），请带上参数
./start-local.sh data
```

启动完毕后控制台会打印路由，浏览器直接访问本地：
- **百科数据后台**: `http://localhost:8081`
- **前端交互全景界面**: `http://localhost:5173`

---

## 生产部署构建

如果要发布到远端服务器，前端基于 **Vite** 执行编译，可以直接交付 nginx：

```bash
cd frontend-data
npm install
npm run build
# 产出的内容均存放于 dist/，将内部文件映射到 web 容器中即可。
```

后端的 Jar 包建议同样通过 Maven 打包后按需守护进程启动。

---

## 许可证 (License)

本项目基于 **MIT License** 开源，详情请查看 [LICENSE](LICENSE) 文件。
