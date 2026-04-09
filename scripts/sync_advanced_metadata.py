import json
import sqlite3
import os

DB_PATH = '/Users/wanglianzuo/project/roco/roco_encyclopedia.db'
PET_CONF_PATH = '/Users/wanglianzuo/project/roco/parser/output/tables/PET/PETBASE_CONF.json'

def sync_data():
    if not os.path.exists(DB_PATH):
        print(f"Error: Database not found at {DB_PATH}")
        return

    conn = sqlite3.connect(DB_PATH)
    cursor = conn.cursor()

    # Add missing columns to pets table if they don't exist
    cols = [
        ("move_type", "TEXT"),
        ("pet_score", "INTEGER"),
        ("habitat_id", "INTEGER")
    ]
    for col_name, col_type in cols:
        try:
            cursor.execute(f"ALTER TABLE pets ADD COLUMN {col_name} {col_type}")
            print(f"Added column {col_name} to pets table.")
        except sqlite3.OperationalError:
            # Column already exists
            pass

    # Ensure pet_egg_groups table exists
    cursor.execute("""
        CREATE TABLE IF NOT EXISTS pet_egg_groups (
            pet_id INTEGER,
            group_id INTEGER,
            PRIMARY KEY (pet_id, group_id),
            FOREIGN KEY (pet_id) REFERENCES pets(id)
        )
    """)

    with open(PET_CONF_PATH, 'r', encoding='utf-8') as f:
        data = json.load(f)

    print(f"Syncing data for {len(data)} pets...")

    for pet in data:
        pet_id = pet.get('id')
        if not pet_id:
            continue

        move_type = pet.get('move_type')
        pet_score = pet.get('pet_scroe', 0)
        habitat_id = pet.get('pet_habitat_group_role_type', 0)
        egg_groups = pet.get('egg_group', [])

        # Update pets table
        cursor.execute("""
            UPDATE pets 
            SET move_type = ?, pet_score = ?, habitat_id = ?
            WHERE id = ?
        """, (move_type, pet_score, habitat_id, pet_id))

        # Update pet_egg_groups table
        # Clear existing relations for this pet first to avoid duplicates or orphans
        cursor.execute("DELETE FROM pet_egg_groups WHERE pet_id = ?", (pet_id,))
        for g_id in egg_groups:
            cursor.execute("""
                INSERT OR IGNORE INTO pet_egg_groups (pet_id, group_id)
                VALUES (?, ?)
            """, (pet_id, g_id))

    conn.commit()
    conn.close()
    print("Advanced metadata sync completed successfully.")

if __name__ == "__main__":
    sync_data()
