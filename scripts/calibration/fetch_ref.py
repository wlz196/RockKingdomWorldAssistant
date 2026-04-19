import requests
import json
import time
import os

def fetch_monster_data(max_id=2500):
    base_url = "https://rkteambuilder.com/api/monsters/{}"
    results = []
    
    # Ensure data directory exists
    os.makedirs("calibration_data", exist_ok=True)
    
    print(f"Starting to fetch monster data from ID 1 to {max_id}...")
    
    consecutive_errors = 0
    for m_id in range(1, max_id + 1):
        try:
            url = base_url.format(m_id)
            response = requests.get(url, timeout=10)
            
            if response.status_code == 200:
                data = response.json()
                # Simplified record for audit
                record = {
                    "ref_id": data.get("id"),
                    "name": data.get("localized", {}).get("zh", {}).get("name"),
                    "hp": data.get("base_hp"),
                    "attack": data.get("base_phy_atk"),
                    "magic_attack": data.get("base_mag_atk"),
                    "defense": data.get("base_phy_def"),
                    "magic_defense": data.get("base_mag_def"),
                    "speed": data.get("base_spd"),
                    "types": [data.get("main_type"), data.get("sub_type")]
                }
                results.append(record)
                print(f"[{m_id}] Fetched: {record['name']}")
                consecutive_errors = 0
                
                # Incremental save every 50 records
                if len(results) % 50 == 0:
                    with open("calibration_data/reference_snapshot.json", "w", encoding="utf-8") as f:
                        json.dump(results, f, ensure_ascii=False, indent=2)
                    print(f"--- Saved progress ({len(results)} records) ---")

            elif response.status_code == 404:
                consecutive_errors += 1
                if consecutive_errors > 30: # Stop after 30 consecutive not found
                    print(f"Reached end of data at ID {m_id}")
                    break
            else:
                print(f"Error fetching ID {m_id}: {response.status_code}")
                
            # Rate limiting
            time.sleep(0.12)
            
        except Exception as e:
            print(f"Exception at ID {m_id}: {str(e)}")
            time.sleep(2)
            
    # Final save
    with open("calibration_data/reference_snapshot.json", "w", encoding="utf-8") as f:
        json.dump(results, f, ensure_ascii=False, indent=2)
        
    print(f"Successfully saved {len(results)} records to calibration_data/reference_snapshot.json")

if __name__ == "__main__":
    fetch_monster_data()
