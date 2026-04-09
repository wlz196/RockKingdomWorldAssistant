# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.
Always respond and communicate in Simplified Chinese (简体中文). All generated code comments should also be in Chinese

## Project Overview

洛克王国战术 AI 助手 — a tactical AI assistant for the Roco Kingdom mobile game. It provides pet data queries, skill analysis, team building advice, and type matchup calculations via a dual-agent AI architecture.

## Build & Run

### Backend (Spring Boot)
```bash
cd backend
export AI_API_KEY=your-api-key-here
mvn spring-boot:run          # runs on :8081
mvn package                  # build jar
mvn test                     # run tests (requires SQLite DB at configured path)
```

### Frontend (Vite + Vanilla JS)
```bash
cd frontend
npm install
npm run dev                  # dev server on :5173
npm run build                # production build
```

### Data Sync (Python)
```bash
# Sync pet/skill data from aoe-top open source project
git clone https://github.com/aoe-top/rocom.aoe.top.git aoe_top_data
python3 scripts/sync_aoe_data.py

# Full binary parse + DB import pipeline
bash scripts/sync_data.sh

# Wiki crawler (alternative data source)
python3 scripts/crawler.py
```

## Architecture

### Dual-Agent Intent Routing
The core design pattern is keyword-based intent routing in `controller/ChatController`:
- `IntentClassifier` (in `ai/` package) scans user messages for ~30 tactical keywords (配招, 阵容, 战术, etc.)
- **TACTICAL** intent → `RocoAgent` — full 4-chapter game mechanics system prompt (~800 tokens), handles team building, matchup analysis, energy management reasoning
- **SIMPLE** intent → `SimpleRocoAgent` — minimal system prompt (~50 tokens), handles basic data lookups
- Both agents share the same 8 function-calling tools defined in `ai/PetTools`

### AI Integration
- Uses **LangChain4j** (v0.35.0) with OpenAI-compatible API format
- Agents are declared as `@AiService` interfaces with `@SystemMessage` annotations in `ai/` package
- Tools are `@Tool`-annotated methods in `ai/PetTools` that delegate to `service/PetService`
- AI provider configured in `application.yml` via `langchain4j.open-ai` properties, API key from `AI_API_KEY` env var

### Backend Package Structure
```
com.roco.backend/
├── config/        — WebConfig (static resource mapping)
├── controller/    — ChatController, DataController, PetController
├── model/entity/  — JPA entities (Pet, SkillInfo, Evolution, etc.)
├── model/dto/     — DTOs (ChatRequest, SkillDTO, SkillItemDTO)
├── repository/    — Spring Data JPA repositories
├── service/       — PetService (business logic)
└── ai/            — IntentClassifier, PetTools, RocoAgent, SimpleRocoAgent
```

### Two REST Controller Paths
- `ChatController` (`/api/v1/chat`) — AI chat endpoint with intent routing
- `DataController` (`/api/v1/data`) — direct data API for the encyclopedia frontend (pets, skills, natures, bloodlines, buffs, talents, types)

### Frontend Pages
- `index.html` + `main.js` — AI chat interface (glassmorphism UI)
- `encyclopedia.html` + `encyclopedia.js` — full data encyclopedia with 9 tabs (pets, skills, bloodlines, buffs, natures, talents, type matchups, stat simulator, damage calculator)
- `js/constants.js` — shared type constants and color helpers
- `js/api.js` — backend URL config and utility functions
- `query.html` — basic table query page

### Database
- **SQLite** via JDBC + JPA/Hibernate with `SQLiteDialect`
- DB path is hardcoded in `application.yml` — update `spring.datasource.url` for your environment
- Key tables: `pets`, `skill_conf_main`, `pet_skill_mapping`, `type_relations`, `types`, `natures`, `evolutions`, `bloodlines`, `buffs`, `pet_talents`, `pet_dimensions`, `pet_egg_groups`
- `PetService` (in `service/` package) mixes JPA repositories with raw `JdbcTemplate` queries for tables without entity mappings (e.g., `skill_conf_main`, `pet_dimensions`, `buff_types`)

### Static Resources
- `WebConfig` (in `config/` package) maps `/media/**` → `media/` (pet/skill images) and `/**` → `frontend/` for serving the frontend from the backend

### Scripts & Tools
- `scripts/` — data sync scripts (`sync_aoe_data.py`, `sync_data.sh`, `crawler.py`, etc.) and deployment scripts (`start_backend.sh`)
- `tools/` — development helper pages (`data_dashboard.html`, `db_explorer.html`, `migrate_images.py`)
- `sql/` — database initialization scripts (`init_db.sql`)
- `docs/` — design documents and screenshots

### Parser Scripts
- `parser/scripts/` contains Python import scripts that populate SQLite tables from parsed game data files
- Each script targets a specific table (e.g., `import_pets.py`, `import_pet_level_skills.py`, `create_types_db.py`)

## Key Conventions

- All game domain terms are in Chinese (精灵=pet, 技能=skill, 属性=type, 性格=nature, 血脉=bloodline)
- Type IDs are integers mapped to names in the `types` table (e.g., 2=普通, 3=草, 4=火, 5=水)
- Pet images use `JL_` prefix convention, skill icons map across `FeatureIcon/`, `SkillIcon/`, `Combat/` subdirectories
- Skill sources in `pet_skill_mapping` are categorized as: 自学 (self-learn), 技能石 (skill stone), 血脉 (bloodline)
