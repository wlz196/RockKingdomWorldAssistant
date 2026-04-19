# 洛克王国百科助：未提交功能（Pending Changes）盘点报告

本报告汇总了当前工作区中所有已实现但尚未执行 `git commit` 的核心功能与代码改动。

---

## 1. AI 战术点评系统 (V5.5 大师版)
这是目前变动最大的核心模块，旨在将百科从“数据查阅”提升为“战术分析”。
- **后端核心**：`AiPetReviewService.java` [未跟踪]
    - 实现了全量非血脉技能扫描。
    - 集成了“系别属性审计”逻辑（修正了 Dimo 等多系别精灵的识别问题）。
    - 注入了血脉与愿力系统的战术通识。
- **管理后台**：`pet_review_manager.html` [未跟踪]
    - 专门设计的精灵评述同步界面，支持 248 只最终形态精灵的一键批量同步。
- **详情页集成**：`encyclopedia.js/html/css` [已修改]
    - 在精灵详情弹窗中新增了“AI 战术点评”磁贴。
    - 采用深紫渐变与玻璃拟态视觉设计。

## 2. AI 自动打标系统 (Skill Tagger)
用于将复杂的技能描述转化为机器可读的战术标签。
- **后端核心**：`AiTaggerService.java` [未跟踪]
    - 支持基于 OpenAI 的语义解析，自动识别“应对”、“先手”、“恢复”等 20+ 种机制。
- **管理界面**：`tagger.html` [未跟踪]
    - 可视化打标控制台，支持导出标签到 `skill_conf_main` 表。

## 3. 百科详情页 UI 实重大升级
- **抽屉式详情逻辑**：`encyclopedia.js` 执行了大规模重构，现在点击精灵会弹出全屏玻璃拟态抽屉。
- **多维度数值对标**：集成全球/系别两级 P95/P80 种族值分位对标（通过 `stat_quantiles_...` 表支持）。
- **组件化重构**：优化了技能列表、血脉信息、种族值雷达图的渲染效率。

## 4. 后端数据中台扩展
- **控制器重构**：`DataController.java` 新增了 15+ 个 API 接口 [已修改]。
    - 包含：`/benchmarks`（数据对标）、`/skills/stats`（打标统计）、`/pets/review-candidates`（评述候选名单）等。
- **服务层增强**：`DataService.java` [已修改] 增加了 AI 评述的加载逻辑。

## 5. 辅助管理工具库 [均为未跟踪新文件]
- **`stats.html`**：实时统计数据分布，用于校验数据库重构后的完整性。
- **`gallery.html`**：资产管理工具，用于检查物理攻击/魔法攻击图标等资源路径。

---

## 修改文件清单预览 (Stat)
- `frontend-data/encyclopedia.js` (+539 lines)
- `roco-data/.../DataController.java` (+581 lines)
- `roco-data/src/main/resources/application.yml` (新增 AI 密钥配置)
- `AiPetReviewService.java` (新增 AI 评述逻辑)
- `AiTaggerService.java` (新增 AI 打标逻辑)

**注意**：部分文件（如 `roco_encyclopedia.db.pre_reimport_bk`）为数据库备份，提交时建议忽略。
