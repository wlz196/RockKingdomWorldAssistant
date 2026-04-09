import requests
import sqlite3
import json
import time
import re
from bs4 import BeautifulSoup

# ==========================================
# 洛克王国战术 AI 助手 - 数据采集脚本 (Crawler)
# ==========================================

BASE_URL = "https://wiki.lcx.cab/lk/"
HEADERS = {
    "User-Agent": "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
    "Referer": "https://wiki.lcx.cab/lk/index.php"
}
DB_PATH = "data.db"

def get_db_conn():
    return sqlite3.connect(DB_PATH)

# --- 基础工具函数 ---
session = requests.Session()
session.headers.update(HEADERS)

# 初始化 Session (访问首页获取 Cookie)
def init_session():
    print("Initializing session...")
    try:
        session.get(BASE_URL + "index.php", timeout=10)
        print("Session initialized.")
    except Exception as e:
        print(f"Error initializing session: {e}")

def fetch_json(endpoint, params=None):
    url = BASE_URL + endpoint
    try:
        response = session.get(url, params=params, timeout=10)
        response.raise_for_status()
        return response.json()
    except Exception as e:
        # 调试信息
        if "403" in str(e):
            print(f"403 Forbidden at {url}. Attempting to re-init session...")
            init_session()
        print(f"Error fetching JSON from {url}: {e}")
        return None

def fetch_html(endpoint, params=None):
    url = BASE_URL + endpoint
    try:
        response = session.get(url, params=params, timeout=10)
        response.raise_for_status()
        return response.text
    except Exception as e:
        print(f"Error fetching HTML from {url}: {e}")
        return None

# --- 步骤 1: 全量抓取精灵列表 ---
def crawl_pets():
    print("Step 1: Fetching pet list...")
    conn = get_db_conn()
    cursor = conn.cursor()
    page = 1
    total_count = 0
    while True:
        print(f"  Fetching pet page {page}...", flush=True)
        data = fetch_json("get_pokemon_data.php", {"page": page, "exclude_details": 1})
        
        # API 在无数据时可能返回 {"error":"error"} 或空列表 []
        if not data or not isinstance(data, list):
            print(f"  No more pet data or error at page {page}. Ending Step 1.")
            break
        
        for p in data:
            if not isinstance(p, dict): continue
            attrs = p.get("attributes", "").split(",")
            p_type = attrs[0] if len(attrs) > 0 else None
            s_type = attrs[1] if len(attrs) > 1 else None
            
            cursor.execute("""
                INSERT OR REPLACE INTO pet_info 
                (id, t_id, name, primary_type, secondary_type, hp, attack, defense, sp_atk, sp_def, speed, abilities_text, description, training_json)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """, (
                p.get("id"), p.get("t_id"), p.get("name"), p_type, s_type,
                p.get("hp"), p.get("attack"), p.get("defense"), 
                p.get("special_attack"), p.get("special_defense"), p.get("speed"),
                p.get("abilities_text"), p.get("description"), p.get("training")
            ))
            total_count += 1
        
        conn.commit()
        page += 1
        time.sleep(0.3)
    conn.close()
    print(f"Step 1 Complete. Total pets ingested: {total_count}")

# --- 步骤 2: 全量抓取技能列表 ---
def crawl_skills():
    print("Step 2: Fetching skill list...")
    conn = get_db_conn()
    cursor = conn.cursor()
    page = 1
    total_count = 0
    while True:
        print(f"  Fetching skill page {page}...", flush=True)
        data = fetch_json("get_skill_data.php", {
            "page": page, "category": "all", "attribute": "all", 
            "sort": "", "direction": "desc", "energy_value": "all"
        })
        
        if not data or not isinstance(data, list):
            print(f"  No more skill data or error at page {page}. Ending Step 2.")
            break
        
        for s in data:
            if not isinstance(s, dict): continue
            cursor.execute("""
                INSERT OR REPLACE INTO skill_info 
                (id, name, attribute, category, power, energy_consumption, description, beizhu)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """, (
                s.get("id"), s.get("name"), s.get("attribute"), s.get("category"),
                s.get("power"), s.get("energy_consumption"), s.get("description"), s.get("beizhu")
            ))
            total_count += 1
            
        conn.commit()
        page += 1
        time.sleep(0.3)
    conn.close()
    print(f"Step 2 Complete. Total skills ingested: {total_count}")

# --- 步骤 3: 逐只抓取精灵形态 ---
def crawl_forms():
    print("Step 3: Fetching pet forms...")
    conn = get_db_conn()
    cursor = conn.cursor()
    cursor.execute("SELECT id FROM pet_info")
    pet_ids = [row[0] for row in cursor.fetchall()]
    
    for pid in pet_ids:
        print(f"  Fetching forms for pet ID {pid}...")
        data = fetch_json("get_pokemon_forms.php", {"pokemon_id": pid})
        if data:
            for f in data:
                cursor.execute("""
                    INSERT OR REPLACE INTO pet_form 
                    (id, pet_id, form_name, form_display_name, hp, attack, defense, sp_atk, sp_def, speed, abilities_text, evolution_condition, attributes, form_image)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, (
                    f.get("id"), f.get("pokemon_id"), f.get("form_name"), f.get("form_display_name"),
                    f.get("hp"), f.get("attack"), f.get("defense"), f.get("special_attack"), f.get("special_defense"), f.get("speed"),
                    f.get("abilities_text"), f.get("evolution_condition"), f.get("attributes"), f.get("form_image")
                ))
            conn.commit()
        time.sleep(0.3)
    conn.close()

# --- 步骤 4: 解析精灵详情 (技能映射) ---
def crawl_mappings():
    print("Step 4: Fetching skill mappings from detail pages...")
    conn = get_db_conn()
    cursor = conn.cursor()
    cursor.execute("SELECT t_id, id FROM pet_info")
    pets = cursor.fetchall()
    
    for tid, pid in pets:
        print(f"  Parsing detail for {tid}...", flush=True)
        html = fetch_html("detail.php", {"t_id": tid, "in_iframe": 1})
        if not html:
            continue
        
        soup = BeautifulSoup(html, "html.parser")
        # Wiki 使用 Bootstrap 5 Accordion 手风琴组件
        items = soup.find_all("div", class_="accordion-item")
        for item in items:
            btn = item.find("button", class_="accordion-button")
            if not btn:
                continue
            
            title = btn.text.strip()
            source_type = None
            if "精灵技能" in title:
                source_type = "innate"
            elif "可学习技能石" in title:
                source_type = "skill_stone"
            elif "血脉技能" in title:
                source_type = "bloodline"
            
            if source_type:
                # 在 accordion-body 中找到所有技能详情链接
                links = item.find_all("a", href=re.compile(r"skill_detail\.php\?id=(\d+)"))
                for link in links:
                    sid_match = re.search(r"id=(\d+)", link['href'])
                    if sid_match:
                        sid = sid_match.group(1)
                        cursor.execute("""
                            INSERT OR IGNORE INTO pet_skill_mapping (pet_id, skill_id, source_type)
                            VALUES (?, ?, ?)
                        """, (pid, sid, source_type))
        conn.commit()
        time.sleep(0.5)
    conn.close()

# --- 步骤 5: 解析属性克制 ---
def crawl_type_chart():
    print("Step 5: Fetching type chart...")
    # 鉴于属性克制表较小且稳定，通常可以直接写入，或从小循环中抓取。
    # 这里示例逻辑，实际可能需要更复杂的 DOM 解析。
    pass

# --- 步骤 6: 解析性格修正 ---
def crawl_nature():
    print("Step 6: Fetching nature chart...")
    pass

# --- 程序入口 ---
if __name__ == "__main__":
    init_session()
    crawl_pets()
    crawl_skills()
    crawl_forms()
    crawl_mappings()
    print("Full data crawl complete.")
