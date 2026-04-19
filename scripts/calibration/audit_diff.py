import json
import sqlite3
import os

def audit_database(db_path="roco_encyclopedia.db", snapshot_path="calibration_data/reference_snapshot.json"):
    if not os.path.exists(snapshot_path):
        print(f"Error: Snapshot file {snapshot_path} not found.")
        return

    with open(snapshot_path, "r", encoding="utf-8") as f:
        reference_data = json.load(f)

    conn = sqlite3.connect(db_path)
    conn.row_factory = sqlite3.Row
    cursor = conn.cursor()

    report_lines = [
        "# 数据校准审计报告 (Data Calibration Audit Report)",
        f"\n生成时间: {os.popen('date').read().strip()}",
        "\n| 宠物名称 | 属性项 | 本地值 | 参考值 | 状态 |",
        "| :--- | :--- | :--- | :--- | :--- |"
    ]

    total_diffs = 0
    matched_pets = 0

    for ref in reference_data:
        name = ref["name"]
        # Find matching pet in local DB
        cursor.execute("SELECT * FROM pets WHERE name = ?", (name,))
        local_pet = cursor.fetchone()

        if local_pet:
            matched_pets += 1
            diffs = []
            
            # Map of internal column names to ref keys
            fields = {
                "hp": "hp",
                "attack": "attack",
                "magic_attack": "magic_attack",
                "defense": "defense",
                "magic_defense": "magic_defense",
                "speed": "speed"
            }

            for local_col, ref_key in fields.items():
                local_val = local_pet[local_col]
                ref_val = ref.get(ref_key)

                if local_val != ref_val:
                    diffs.append((local_col, local_val, ref_val))

            if diffs:
                total_diffs += len(diffs)
                for field, l_v, r_v in diffs:
                    report_lines.append(f"| {name} | {field} | {l_v} | {r_v} | [ ] 待修补 |")

    conn.close()

    summary = f"\n\n**审计统计**: 匹配精灵: {matched_pets}, 存在差异项: {total_diffs}"
    report_lines.append(summary)

    report_path = "calibration_data/audit_report.md"
    with open(report_path, "w", encoding="utf-8") as f:
        f.write("\n".join(report_lines))

    print(f"Audit complete. Report saved to {report_path}")
    print(summary)

if __name__ == "__main__":
    audit_database()
