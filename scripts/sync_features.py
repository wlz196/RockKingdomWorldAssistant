import json
import sqlite3
import os

def sync_pet_features():
    json_path = '/Users/wanglianzuo/project/roco/parser/output/tables/PET/PETBASE_CONF.json'
    db_path = '/Users/wanglianzuo/project/roco/roco_encyclopedia.db'
    
    if not os.path.exists(json_path):
        print(f"Error: JSON file not found at {json_path}")
        return

    print("Loading JSON data...")
    with open(json_path, 'r', encoding='utf-8') as f:
        data = json.load(f)

    print(f"Connecting to database at {db_path}...")
    conn = sqlite3.connect(db_path)
    cursor = conn.cursor()

    count = 0
    print("Updating pet features...")
    for item in data:
        pet_id = item.get('id')
        feature_id = item.get('pet_feature')
        
        if pet_id is not None and feature_id is not None:
            # Update the pets table where id matches
            cursor.execute(
                "UPDATE pets SET pet_feature = ? WHERE id = ?",
                (feature_id, pet_id)
            )
            count += 1
            if count % 100 == 0:
                print(f"Processed {count} pets...")

    conn.commit()
    conn.close()
    print(f"Sync complete! Updated {count} pet features.")

if __name__ == "__main__":
    sync_pet_features()
