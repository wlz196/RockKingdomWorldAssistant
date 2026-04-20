import json
import sqlite3
import os

# 配置路径
DB_PATH = "roco_encyclopedia.db"
PET_JSON = "PETBASE_CONF.json"
SKILL_JSON = "SKILL_CONF.json"
LEVEL_SKILL_JSON = "LEVEL_SKILL_CONF.json"

def reimport_all():
    if not all(os.path.exists(p) for p in [PET_JSON, SKILL_JSON, LEVEL_SKILL_JSON]):
        print("Error: 缺少必要的核心 JSON 文件。")
        return

    conn = sqlite3.connect(DB_PATH)
    cursor = conn.cursor()

    try:
        # 0. 确保 AI 分析辅助表存在
        ensure_analysis_tables(cursor)

        # 1. 重刷技能主库 (skill_conf_main)
        reimport_skills(cursor)

        # 2. 重刷精灵主库 (pets)
        reimport_pets(cursor)

        # 3. 重刷技能关联 (pet_level_skills)
        reimport_level_skills(cursor)

        # 4. 刷新实装状态位 (is_official)
        refresh_official_status(cursor)

        # 5. 计算战力度量衡 (P50/P80/P95)
        calculate_benchmarks(cursor)

        conn.commit()
        print("\n✅ 全量数据刷新完成（主表状态位重构版）")
    except Exception as e:
        conn.rollback()
        import traceback
        traceback.print_exc()
        print(f"\n❌ 执行失败: {e}")
    finally:
        conn.close()


def ensure_analysis_tables(cursor):
    print("正在确保 AI 分析辅助表存在...")

    cursor.execute("""
        CREATE TABLE IF NOT EXISTS skill_tactical_tags (
            skill_id INTEGER PRIMARY KEY,
            major_category TEXT,
            action_tags TEXT,
            trigger_tags TEXT,
            payoff_tags TEXT,
            target_tags TEXT,
            synergy_tags TEXT,
            risk_tags TEXT,
            manual_score_attack REAL,
            manual_score_defense REAL,
            manual_score_utility REAL,
            confidence REAL,
            source TEXT,
            updated_at TEXT
        )
    """)
    cursor.execute("CREATE INDEX IF NOT EXISTS idx_skill_tactical_tags_major_category ON skill_tactical_tags(major_category)")
    cursor.execute("CREATE INDEX IF NOT EXISTS idx_skill_tactical_tags_source ON skill_tactical_tags(source)")

    cursor.execute("""
        CREATE TABLE IF NOT EXISTS feature_tactical_tags (
            feature_id INTEGER PRIMARY KEY,
            trigger_mode TEXT,
            value_axis TEXT,
            trigger_tags TEXT,
            payoff_tags TEXT,
            synergy_tags TEXT,
            floor_boost REAL,
            ceiling_boost REAL,
            notes TEXT,
            source TEXT,
            updated_at TEXT
        )
    """)
    cursor.execute("CREATE INDEX IF NOT EXISTS idx_feature_tactical_tags_trigger_mode ON feature_tactical_tags(trigger_mode)")
    cursor.execute("CREATE INDEX IF NOT EXISTS idx_feature_tactical_tags_source ON feature_tactical_tags(source)")

    cursor.execute("""
        CREATE TABLE IF NOT EXISTS pet_build_profiles (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            pet_id INTEGER NOT NULL,
            build_name TEXT,
            build_type TEXT,
            core_skill_ids TEXT,
            optional_skill_ids TEXT,
            recommended_skill_set TEXT,
            bloodline_options TEXT,
            nature_options TEXT,
            talent_options TEXT,
            role_tags TEXT,
            playstyle_summary TEXT,
            strength_notes TEXT,
            weakness_notes TEXT,
            environment_notes TEXT,
            source TEXT,
            priority INTEGER,
            updated_at TEXT
        )
    """)
    cursor.execute("CREATE INDEX IF NOT EXISTS idx_pet_build_profiles_pet_id ON pet_build_profiles(pet_id)")
    cursor.execute("CREATE INDEX IF NOT EXISTS idx_pet_build_profiles_priority ON pet_build_profiles(priority)")
    cursor.execute("CREATE INDEX IF NOT EXISTS idx_pet_build_profiles_build_type ON pet_build_profiles(build_type)")

    cursor.execute("""
        CREATE TABLE IF NOT EXISTS pet_tactical_profiles (
            pet_id INTEGER PRIMARY KEY,
            version TEXT,
            profile_json TEXT,
            role_summary TEXT,
            offense_score REAL,
            defense_score REAL,
            speed_score REAL,
            utility_score REAL,
            synergy_score REAL,
            flexibility_score REAL,
            ceiling_score REAL,
            floor_score REAL,
            meta_fit_score REAL,
            strengths TEXT,
            weaknesses TEXT,
            build_dependencies TEXT,
            generated_by TEXT,
            updated_at TEXT
        )
    """)
    cursor.execute("CREATE INDEX IF NOT EXISTS idx_pet_tactical_profiles_version ON pet_tactical_profiles(version)")
    cursor.execute("CREATE INDEX IF NOT EXISTS idx_pet_tactical_profiles_generated_by ON pet_tactical_profiles(generated_by)")

    cursor.execute("""
        CREATE TABLE IF NOT EXISTS mechanic_glossary (
            term TEXT PRIMARY KEY,
            category TEXT,
            formal_definition TEXT,
            tactical_meaning TEXT,
            parsing_hint TEXT,
            related_tags TEXT,
            updated_at TEXT
        )
    """)
    cursor.execute("CREATE INDEX IF NOT EXISTS idx_mechanic_glossary_category ON mechanic_glossary(category)")

    print("✅ AI 分析辅助表已就绪")

def reimport_skills(cursor):
    print("正在导入技能库...")
    with open(SKILL_JSON, "r", encoding="utf-8") as f:
        data = json.load(f).get("RocoDataRows", {})
    
    cursor.execute("DROP TABLE IF EXISTS skill_conf_main")
    cursor.execute("""
        CREATE TABLE skill_conf_main (
            id INTEGER PRIMARY KEY,
            name TEXT,
            desc TEXT,
            icon TEXT,
            type INTEGER,
            energy_cost TEXT,
            skill_type INTEGER,
            skill_dam_type INTEGER,
            damage_type INTEGER,
            skill_priority INTEGER,
            dam_para TEXT,
            is_official INTEGER DEFAULT 0 -- 新增实装状态位
        )
    """)
    cursor.execute("CREATE INDEX idx_skill_conf_is_official ON skill_conf_main(is_official)")
    cursor.execute("CREATE INDEX idx_skill_conf_main_type ON skill_conf_main(type)")

    insert_sql = "INSERT INTO skill_conf_main (id, name, desc, icon, type, energy_cost, skill_type, skill_dam_type, damage_type, skill_priority, dam_para, is_official) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)"
    batch = []
    for sid, skill in data.items():
        batch.append((
            int(sid),
            skill.get("name"),
            skill.get("desc"),
            skill.get("icon"),
            skill.get("type"),
            str(skill.get("energy_cost", [])),
            skill.get("skill_type"),
            skill.get("skill_dam_type"),
            skill.get("damage_type"),
            skill.get("skill_priority"),
            str(skill.get("dam_para", []))
        ))
    
    cursor.executemany(insert_sql, batch)
    print(f"✅ 技能库导入完成: {len(batch)} 条记录")

def reimport_pets(cursor):
    print("正在导入精灵库...")
    with open(PET_JSON, "r", encoding="utf-8") as f:
        rows = json.load(f).get("RocoDataRows", {})

    cursor.execute("DROP TABLE IF EXISTS pets")
    cursor.execute("""
        CREATE TABLE pets (
            id            INT,
            name          TEXT,
            form          TEXT,
            description   TEXT,
            is_boss       INT,
            book_id       INT,
            main_type_id  INT,
            sub_type_id   INT,
            hp            INT,
            attack        INT,
            defense       INT,
            magic_attack  INT,
            magic_defense INT,
            speed         INT,
            total_stats   INT,
            feature_id    INT,
            evolution_targets TEXT,
            completeness  INT,
            image_url     TEXT,
            boss_id       INT,
            is_official   INTEGER DEFAULT 0 -- 新增实装状态位
        )
    """)
    cursor.execute("CREATE INDEX idx_pets_is_official ON pets(is_official)")

    # 计算 boss 映射等逻辑保持不变...
    boss_to_base_name = {}
    for pet_id_str, pet in rows.items():
        is_final_form = not pet.get("evolution_pet_id")
        if not is_final_form: continue
        boss_ids = []
        b_id = pet.get("bosspetbase_id")
        if b_id: boss_ids.append(b_id)
        b_ids = pet.get("bosspetbase_id_arry", [])
        boss_ids.extend(b_ids)
        for bid in boss_ids:
            bid_int = int(bid)
            boss_to_base_name[bid_int] = f"{pet.get('name')}({pet.get('form')})" if pet.get('form') else pet.get('name')

    data_to_insert = []
    has_book_id = set()
    candidates = []
    for sid, pet in rows.items():
        if pet.get("SUM_race", 0) <= 0: continue
        name = pet.get("name", "未知")
        form = boss_to_base_name.get(int(sid), "首领") if pet.get("is_boss") == 1 else pet.get("form")
        book_id = pet.get("pictorial_book_id", 0)
        if book_id > 0: has_book_id.add((name, form))
        u_types = pet.get("unit_type", [])
        evo_ids = pet.get("evolution_pet_id", [])
        boss_id_arry = pet.get("bosspetbase_id_arry", [])
        
        is_official = 1 if book_id > 0 else 0
        
        record = (
            int(sid), name, form, pet.get("description", ""),
            pet.get("is_boss"), book_id,
            u_types[0] if len(u_types)>0 else None, 
            u_types[1] if len(u_types)>1 else None,
            pet.get("hp_max_race", 0), pet.get("phy_attack_race", 0),
            pet.get("phy_defence_race", 0), pet.get("spe_attack_race", 0),
            pet.get("spe_defence_race", 0), pet.get("speed_race", 0),
            pet.get("SUM_race", 0), pet.get("pet_feature"),
            ",".join(map(str, evo_ids)) if evo_ids else None,
            pet.get("completeness", 0), pet.get("JL_res", ""),
            boss_id_arry[0] if boss_id_arry else pet.get("bosspetbase_id"),
            is_official
        )
        candidates.append({"record": record, "name": name, "form": form, "book_id": book_id})

    insert_count = 0
    for c in candidates:
        if c["book_id"] == 0 and (c["name"], c["form"]) in has_book_id: continue
        data_to_insert.append(c["record"])
        insert_count += 1

    insert_sql = "INSERT INTO pets VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"
    cursor.executemany(insert_sql, data_to_insert)
    print(f"✅ 精灵库导入完成: {insert_count} 条")

def reimport_level_skills(cursor):
    # 此部分逻辑保持不变，用于后续计算技能实装状态
    print("正在导入技能关联关系...")
    with open(LEVEL_SKILL_JSON, "r", encoding="utf-8") as f:
        data = json.load(f).get("RocoDataRows", {})
    
    cursor.execute("DROP TABLE IF EXISTS pet_level_skills")
    cursor.execute("""
        CREATE TABLE pet_level_skills (
            pet_id INTEGER NOT NULL,
            pet_name TEXT,
            level_skill_conf_id INTEGER NOT NULL,
            skill_id INTEGER NOT NULL,
            source INTEGER NOT NULL,
            learn_level INTEGER NOT NULL DEFAULT -1,
            stage INTEGER NOT NULL DEFAULT -1,
            blood_type TEXT NOT NULL DEFAULT '',
            machine_skill_name TEXT NOT NULL DEFAULT '',
            PRIMARY KEY (pet_id, skill_id, source, learn_level, stage, blood_type)
        )
    """)
    batch = []
    for pid_str, pet_skills in data.items():
        pet_id = int(pid_str)
        p_name = pet_skills.get("editor_name", "")
        for item in pet_skills.get("level", []):
            if isinstance(item, dict) and item.get("param"):
                batch.append((pet_id, p_name, pet_id, int(item["param"]), 0, item.get("level_point", -1), item.get("stage", -1), "", ""))
        machine_group = pet_skills.get("machine_skill_group")
        if isinstance(machine_group, list):
            for item in machine_group:
                if isinstance(item, dict) and item.get("machine_skill_id"):
                    batch.append((pet_id, p_name, pet_id, int(item["machine_skill_id"]), 1, -1, -1, "", item.get("machine_skill_name", "")))
        for key, val in pet_skills.items():
            if key.startswith("blood_skill_") and key != "blood_skill_level_point" and val:
                batch.append((pet_id, p_name, pet_id, int(val), 2, -1, -1, key.replace("blood_skill_", ""), ""))

    cursor.executemany("INSERT OR REPLACE INTO pet_level_skills VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)", batch)
    print(f"✅ 技能关联导入完成: {len(batch)} 条记录")

def refresh_official_status(cursor):
    print("正在计算实装状态位...")
    
    # 1. 之前已在 reimport_pets 中标记了精灵
    
    # 2. 标记正式技能 (被正式精灵拥有的技能)
    cursor.execute("""
        UPDATE skill_conf_main 
        SET is_official = 1 
        WHERE id IN (
            SELECT DISTINCT skill_id FROM pet_level_skills 
            WHERE pet_id IN (SELECT id FROM pets WHERE is_official = 1)
        )
    """)
    
    # 3. 标记正式特性 (被正式精灵拥有的特性)
    cursor.execute("""
        UPDATE skill_conf_main 
        SET is_official = 1 
        WHERE id IN (
            SELECT DISTINCT feature_id FROM pets 
            WHERE is_official = 1 AND feature_id > 0
        )
    """)

    # 4. 根据持有者主属性回填特性属性位 (is_official = 1 的特性)
    # 逻辑：取持有该特性的第一个正式精灵的主属性
    cursor.execute("""
        UPDATE skill_conf_main
        SET skill_dam_type = (
            SELECT p.main_type_id 
            FROM pets p 
            WHERE p.feature_id = skill_conf_main.id AND p.is_official = 1 
            LIMIT 1
        )
        WHERE type = 2 AND is_official = 1
    """)

    # 5. 将特性关联也存入 pet_level_skills (source = 3)
    # 这样可以在通用技能详情接口直接查到使用者
    cursor.execute("""
        INSERT OR REPLACE INTO pet_level_skills (pet_id, pet_name, level_skill_conf_id, skill_id, source, learn_level, stage, blood_type, machine_skill_name)
        SELECT id, name, id, feature_id, 3, -1, -1, '', '' 
        FROM pets 
        WHERE feature_id > 0
    """)
    
    # 6. 删除不再需要的参考表 (可选)
    cursor.execute("DROP TABLE IF EXISTS official_pet_ids")
    cursor.execute("DROP TABLE IF EXISTS official_skill_ids")
    
    s_count = cursor.execute("SELECT COUNT(*) FROM skill_conf_main WHERE is_official = 1").fetchone()[0]
    p_count = cursor.execute("SELECT COUNT(*) FROM pets WHERE is_official = 1").fetchone()[0]
    print(f"✅ 状态位更新完成: 正式精灵 {p_count} | 正式技能/特性 {s_count}")

def calculate_benchmarks(cursor):
    import math
    print("正在计算战力度量衡 (P50/P80/P95)...")
    
    # 1. 创建表
    cursor.execute("DROP TABLE IF EXISTS stat_quantiles_global")
    cursor.execute("""
        CREATE TABLE stat_quantiles_global (
            stat_key TEXT PRIMARY KEY,
            p50 REAL,
            p80 REAL,
            p95 REAL,
            max_val REAL,
            data_count INTEGER
        )
    """)
    
    cursor.execute("DROP TABLE IF EXISTS stat_quantiles_by_type")
    cursor.execute("""
        CREATE TABLE stat_quantiles_by_type (
            type_id INTEGER,
            stat_key TEXT,
            p50 REAL,
            p80 REAL,
            p95 REAL,
            max_val REAL,
            data_count INTEGER,
            PRIMARY KEY (type_id, stat_key)
        )
    """)

    stats = [
        ("hp", "hp"), ("attack", "attack"), ("defense", "defense"),
        ("magic_attack", "magic_attack"), ("magic_defense", "magic_defense"),
        ("speed", "speed"), ("total_stats", "total_stats")
    ]

    def get_percentile(data, p):
        if not data: return 0
        sorted_data = sorted(data)
        n = len(sorted_data)
        idx = (n - 1) * p
        lower = math.floor(idx)
        upper = math.ceil(idx)
        if lower == upper:
            return sorted_data[lower]
        return sorted_data[lower] + (idx - lower) * (sorted_data[upper] - sorted_data[lower])

    # 只统计“最终形态”且“正式”的精灵
    base_sql = "SELECT %s FROM pets WHERE is_official = 1 AND (evolution_targets = '' OR evolution_targets IS NULL)"
    
    # 全局统计
    for key, col in stats:
        values = [row[0] for row in cursor.execute(base_sql % col).fetchall() if row[0] is not None]
        if values:
            p50 = round(get_percentile(values, 0.5), 1)
            p80 = round(get_percentile(values, 0.8), 1)
            p95 = round(get_percentile(values, 0.95), 1)
            mx = round(max(values), 1)
            cursor.execute("INSERT INTO stat_quantiles_global VALUES (?, ?, ?, ?, ?, ?)", 
                         (key, p50, p80, p95, mx, len(values)))
            if key == "speed": print(f"   [全局参考] 速度 MAX={mx:.1f}, P95={p95:.1f}, P80={p80:.1f}, P50={p50:.1f}")
            if key == "total_stats": print(f"   [全局参考] 种族总和 MAX={mx:.1f}, P95={p95:.1f}, P80={p80:.1f}, P50={p50:.1f}")

    # 按系别统计 (排除无系别 ID=1)
    type_ids = [row[0] for row in cursor.execute("SELECT id FROM types WHERE id > 1").fetchall()]
    
    for tid in type_ids:
        for key, col in stats:
            type_sql = base_sql + " AND main_type_id = %d" % tid
            values = [row[0] for row in cursor.execute(type_sql % col).fetchall() if row[0] is not None]
            
            # 如果样本太少，回退到全局
            if len(values) < 10:
                global_data = cursor.execute("SELECT p50, p80, p95, max_val FROM stat_quantiles_global WHERE stat_key = ?", (key,)).fetchone()
                if global_data:
                    cursor.execute("INSERT INTO stat_quantiles_by_type VALUES (?, ?, ?, ?, ?, ?, ?)", 
                                 (tid, key, global_data[0], global_data[1], global_data[2], global_data[3], len(values)))
            else:
                p50 = round(get_percentile(values, 0.5), 1)
                p80 = round(get_percentile(values, 0.8), 1)
                p95 = round(get_percentile(values, 0.95), 1)
                mx = round(max(values), 1)
                cursor.execute("INSERT INTO stat_quantiles_by_type VALUES (?, ?, ?, ?, ?, ?, ?)", 
                             (tid, key, p50, p80, p95, mx, len(values)))

    print("✅ 战力度量衡计算完成")


if __name__ == "__main__":
    reimport_all()
