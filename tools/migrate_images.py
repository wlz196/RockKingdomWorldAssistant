import json
import sqlite3
import os

# 配置路径
DB_PATH = "/Users/wanglianzuo/project/roco/roco_encyclopedia.db"
JSON_PATH = "/Users/wanglianzuo/project/roco/parser/output/tables/PET/PETBASE_CONF.json"

def migrate():
    # 1. 读取 JSON 数据
    print(f"Loading data from {JSON_PATH}...")
    with open(JSON_PATH, 'r', encoding='utf-8') as f:
        data = json.load(f)
    
    # 2. 连接数据库
    print(f"Connecting to database {DB_PATH}...")
    conn = sqlite3.connect(DB_PATH)
    cursor = conn.cursor()
    
    # 3. 批量更新
    count = 0
    errors = 0
    for entry in data:
        pet_id = entry.get('id')
        img_res = entry.get('JL_res')
        
        if pet_id is not None and img_res:
            try:
                # 检查该 ID 是否在 pets 表中
                cursor.execute("UPDATE pets SET image_url = ? WHERE id = ?", (img_res, pet_id))
                if cursor.rowcount > 0:
                    count += 1
            except Exception as e:
                print(f"Error updating ID {pet_id}: {e}")
                errors += 1
    
    conn.commit()
    conn.close()
    print(f"Migration complete! Updated {count} records. Errors: {errors}")

if __name__ == "__main__":
    migrate()
