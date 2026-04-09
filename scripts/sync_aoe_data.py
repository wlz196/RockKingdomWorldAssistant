import json
import sqlite3
import os

DB_PATH = 'data.db'
DATA_DIR = 'aoe_top_data'

def sync_pets_and_mappings():
    print("Syncing pets and skill mappings...")
    with open(os.path.join(DATA_DIR, 'Pets.json'), 'r', encoding='utf-8') as f:
        pets = json.load(f)
    
    conn = sqlite3.connect(DB_PATH)
    cursor = conn.cursor()
    
    # We'll clear the mapping table and rebuild it to ensure accuracy
    cursor.execute('DELETE FROM pet_skill_mapping')
    
    count = 0
    for p in pets:
        p_id = p['id']
        name = p['localized']['zh']['name']
        primary_type = p['main_type']['localized']['zh'] if p.get('main_type') else None
        secondary_type = p['sub_type']['localized']['zh'] if p.get('sub_type') else None
        
        hp = p.get('base_hp', 0)
        atk = p.get('base_phy_atk', 0)
        df = p.get('base_phy_def', 0)
        sp_atk = p.get('base_mag_atk', 0)
        sp_def = p.get('base_mag_def', 0)
        spd = p.get('base_spd', 0)
        
        # Load detailed pet info for trait and categorized moves
        detail_path = os.path.join(DATA_DIR, 'pets', f"{p_id}.json")
        abilities_text = ""
        desc = ""
        
        if os.path.exists(detail_path):
            with open(detail_path, 'r', encoding='utf-8') as f_detail:
                detail = json.load(f_detail)
                
                # Characteristics (Trait)
                trait = detail.get('trait')
                if trait and trait.get('localized'):
                    zh_trait = trait['localized'].get('zh', {})
                    abilities_text = f"【{zh_trait.get('name', '')}】: {zh_trait.get('description', '')}"
                
                # Description
                world_profile = detail.get('world_profile')
                desc = world_profile.get('introduction', '') if world_profile else ''

                # Process Skill Categories
                def add_mappings(moves, source_type):
                    for m in moves:
                        m_id = m.get('id') or m.get('move_id')
                        if m_id:
                            cursor.execute('''
                                INSERT OR IGNORE INTO pet_skill_mapping (pet_id, skill_id, source_type)
                                VALUES (?, ?, ?)
                            ''', (p_id, m_id, source_type))

                add_mappings(detail.get('move_pool', []), '自学')
                add_mappings(detail.get('move_stones', []), '技能石')
                add_mappings(detail.get('legacy_moves', []), '血脉')
        else:
            # Fallback for simple description if detail not found
            world_profile = p.get('world_profile')
            desc = world_profile.get('introduction', '') if world_profile else ''

        # Image URL from rocom.aoe.top
        image_url = f"https://rocom.aoe.top/assets/webp/friends/JL_{p['name']}.webp"

        cursor.execute('''
            INSERT OR REPLACE INTO pet_info (id, t_id, name, primary_type, secondary_type, hp, attack, defense, sp_atk, sp_def, speed, abilities_text, description, image_url)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        ''', (p_id, str(p_id), name, primary_type, secondary_type, hp, atk, df, sp_atk, sp_def, spd, abilities_text, desc, image_url))
        count += 1
    
    conn.commit()
    conn.close()
    print(f"Synced {count} pets and their mappings.")

def sync_moves():
    print("Syncing moves...")
    with open(os.path.join(DATA_DIR, 'moves.json'), 'r', encoding='utf-8') as f:
        moves = json.load(f)
        
    conn = sqlite3.connect(DB_PATH)
    cursor = conn.cursor()
    
    count = 0
    for m in moves:
        m_id = m['id']
        name = m['localized']['zh']['name']
        attr = m['move_type']['localized']['zh'] if m.get('move_type') else 'None'
        cat = m.get('move_category', 'Status')
        pwr = str(m.get('power', '0'))
        cost = m.get('energy_cost', 0)
        desc = m['localized']['zh'].get('description', '')
        
        cursor.execute('''
            INSERT OR REPLACE INTO skill_info (id, name, attribute, category, power, energy_consumption, description)
            VALUES (?, ?, ?, ?, ?, ?, ?)
        ''', (m_id, name, attr, cat, pwr, cost, desc))
        count += 1
        
    conn.commit()
    conn.close()
    print(f"Synced {count} moves.")

def sync_natures():
    print("Syncing natures...")
    with open(os.path.join(DATA_DIR, 'personalities.json'), 'r', encoding='utf-8') as f:
        natures = json.load(f)
        
    conn = sqlite3.connect(DB_PATH)
    cursor = conn.cursor()
    
    cursor.execute('DELETE FROM nature')
    
    count = 0
    for n in natures:
        name = n['localized']['zh']
        name_en = n['name']
        hp_mod = n.get('hp_mod_pct', 0.0)
        atk_mod = n.get('phy_atk_mod_pct', 0.0)
        mag_atk_mod = n.get('mag_atk_mod_pct', 0.0)
        def_mod = n.get('phy_def_mod_pct', 0.0)
        mag_def_mod = n.get('mag_def_mod_pct', 0.0)
        spd_mod = n.get('spd_mod_pct', 0.0)
        
        cursor.execute('''
            INSERT INTO nature (name, name_en, hp_mod, phy_atk_mod, mag_atk_mod, phy_def_mod, mag_def_mod, spd_mod)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
        ''', (name, name_en, hp_mod, atk_mod, mag_atk_mod, def_mod, mag_def_mod, spd_mod))
        count += 1
        
    conn.commit()
    conn.close()
    print(f"Synced {count} natures.")

if __name__ == "__main__":
    sync_moves() # Sync skills first so mapping foreign keys work (though SQLite defaults to no ENFORCEMENT)
    sync_pets_and_mappings()
    sync_natures()
