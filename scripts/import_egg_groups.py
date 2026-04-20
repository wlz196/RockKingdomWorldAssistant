import json
import sqlite3
import os

DB_PATH = "roco_encyclopedia.db"
JSON_PATH = "PET_LIKE_ELEMENT_ID_NAME_DESC.json"

def import_egg_groups():
    if not os.path.exists(JSON_PATH):
        print(f"Error: {JSON_PATH} not found.")
        return

    conn = sqlite3.connect(DB_PATH)
    cursor = conn.cursor()

    try:
        with open(JSON_PATH, "r", encoding="utf-8") as f:
            data = json.load(f)

        print(f"Importing {len(data)} egg groups...")
        
        # 清空表以便重新导入
        cursor.execute("DELETE FROM pet_egg_group_definitions")
        
        for item in data:
            cursor.execute(
                "INSERT INTO pet_egg_group_definitions (id, name_en, name_cn, description) VALUES (?, ?, ?, ?)",
                (item["id"], item["name"], item["name_cn"], item["desc"])
            )
        
        conn.commit()
        print("✅ Success: Egg group definitions imported.")
    except Exception as e:
        conn.rollback()
        print(f"❌ Error: {e}")
    finally:
        conn.close()

if __name__ == "__main__":
    import_egg_groups()
