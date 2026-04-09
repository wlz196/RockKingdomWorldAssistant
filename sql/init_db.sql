-- 洛克王国数据表初始化脚本
-- 2026-03-25

-- 1. 精灵基础信息表
CREATE TABLE IF NOT EXISTS pet_info (
    id              INTEGER PRIMARY KEY,
    t_id            VARCHAR(10) NOT NULL,
    name            VARCHAR(50) NOT NULL,
    primary_type    VARCHAR(20),
    secondary_type  VARCHAR(20),
    hp              INTEGER,
    attack          INTEGER,
    defense         INTEGER,
    sp_atk          INTEGER,
    sp_def          INTEGER,
    speed           INTEGER,
    abilities_text  TEXT,
    description     TEXT,
    training_json   TEXT,
    image_url       VARCHAR(255),
    last_updated    DATETIME DEFAULT CURRENT_TIMESTAMP
);
CREATE UNIQUE INDEX IF NOT EXISTS idx_pet_tid ON pet_info(t_id);

-- 2. 精灵形态表
CREATE TABLE IF NOT EXISTS pet_form (
    id                  INTEGER PRIMARY KEY,
    pet_id              INTEGER NOT NULL,
    form_name           VARCHAR(50),
    form_display_name   VARCHAR(50),
    hp                  INTEGER,
    attack              INTEGER,
    defense             INTEGER,
    sp_atk              INTEGER,
    sp_def              INTEGER,
    speed               INTEGER,
    abilities_text      TEXT,
    evolution_condition TEXT,
    attributes          VARCHAR(50),
    form_image          VARCHAR(255),
    FOREIGN KEY (pet_id) REFERENCES pet_info(id)
);

-- 3. 技能全局字典表
CREATE TABLE IF NOT EXISTS skill_info (
    id                  INTEGER PRIMARY KEY,
    name                VARCHAR(50) NOT NULL,
    attribute           VARCHAR(20),
    category            VARCHAR(10),
    power               VARCHAR(10),
    energy_consumption  INTEGER,
    description         TEXT,
    beizhu              TEXT
);

-- 4. 精灵-技能关联表
CREATE TABLE IF NOT EXISTS pet_skill_mapping (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    pet_id      INTEGER NOT NULL,
    skill_id    INTEGER NOT NULL,
    source_type VARCHAR(20) NOT NULL,
    FOREIGN KEY (pet_id)  REFERENCES pet_info(id),
    FOREIGN KEY (skill_id) REFERENCES skill_info(id)
);
CREATE UNIQUE INDEX IF NOT EXISTS idx_psm_unique ON pet_skill_mapping(pet_id, skill_id, source_type);

-- 5. 属性克制表
CREATE TABLE IF NOT EXISTS type_chart (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    attack_type     VARCHAR(20) NOT NULL,
    defense_type    VARCHAR(20) NOT NULL,
    effectiveness   REAL NOT NULL
);
CREATE UNIQUE INDEX IF NOT EXISTS idx_type_matchup ON type_chart(attack_type, defense_type);

-- 6. 性格修正表
CREATE TABLE IF NOT EXISTS nature (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    name            VARCHAR(20) NOT NULL,
    name_en         VARCHAR(50),
    hp_mod          REAL DEFAULT 0.0,
    phy_atk_mod     REAL DEFAULT 0.0,
    mag_atk_mod     REAL DEFAULT 0.0,
    phy_def_mod     REAL DEFAULT 0.0,
    mag_def_mod     REAL DEFAULT 0.0,
    spd_mod         REAL DEFAULT 0.0,
    increased_stat  VARCHAR(20),
    decreased_stat  VARCHAR(20)
);

-- 7. 推荐阵容表
CREATE TABLE IF NOT EXISTS recommended_team (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    team_name       VARCHAR(100),
    pet_ids         TEXT,
    description     TEXT,
    source          VARCHAR(50)
);
