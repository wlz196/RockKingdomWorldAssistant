import re
import sqlite3
from pathlib import Path

DB_PATH = Path("/Users/wanglianzuo/project/RockKingdomWorldAssistant/roco_encyclopedia.db")
SOURCE_MD = Path("/Users/wanglianzuo/project/RockKingdomWorldAssistant/game_mechanics_glossary.md")


def infer_category(term: str, definition: str) -> str:
    if "印记" in term:
        return "印记"
    if term in {"雨天", "沙暴", "暴风雪"} or "天气" in definition or "环境" in definition:
        return "环境"
    if term in {"中毒", "冻结", "灼烧", "萌化", "寄生", "眩晕", "中毒效果", "减益", "增益"}:
        return "状态"
    if term in {"迸发", "木桶状态", "蓄力", "积蓄", "迅捷", "过载", "传动", "打断", "吸血", "冷却", "连击数", "连击", "先手", "应对攻击", "应对防御", "应对状态", "应对", "紧急脱离", "脱离", "离场", "返场", "本系技能", "全技能威力", "全技能能耗", "x号位"}:
        return "机制"
    if term in {"力竭", "魔力值"}:
        return "术语"
    return "术语"


def infer_related_tags(term: str, definition: str) -> list[str]:
    tags = []
    if "印记" in term or "印记" in definition:
        tags.append("印记联动")
    if term in {"迸发", "过载", "连击", "连击数"}:
        tags.append("爆发")
    if term in {"迅捷", "先手", "应对", "应对攻击", "应对防御", "应对状态", "打断"}:
        tags.append("节奏压制")
    if term in {"迅捷", "先手"}:
        tags.append("优先级")
    if term in {"打断"}:
        tags.append("打断")
    if term in {"吸血", "灼烧", "冻结", "中毒", "寄生"}:
        tags.append("抗压")
    if term in {"魔力值", "全技能能耗", "湿润印记", "光合印记"}:
        tags.append("资源循环")
    return list(dict.fromkeys(tags))


def infer_tactical_meaning(term: str, definition: str) -> str:
    if term == "迸发":
        return "偏上限型收益，通常用于爆发回合、抢节奏或滚雪球推进。"
    if term == "迅捷":
        return "会显著影响入场节奏与先手权判断，是高节奏体系的重要机制。"
    if term == "打断":
        return "能够直接中断对手本回合动作，是极高价值的节奏压制手段。"
    if "印记" in term:
        return "属于持续型联动资源，会影响后续技能收益、站场质量或回合资源交换。"
    if term in {"中毒", "冻结", "灼烧", "寄生", "萌化", "眩晕"}:
        return "属于状态或减益机制，会直接影响站场、换入与回合容错。"
    if term in {"雨天", "沙暴", "暴风雪"}:
        return "属于环境机制，会改变双方技能威力、能耗或回合结算方式。"
    return "该词条会影响技能或特性的战术判断，建议结合具体技能链和构筑一起理解。"


def infer_parsing_hint(term: str, definition: str) -> str:
    return f"当技能或特性描述中出现“{term}”时，应优先按其正式定义解释，并结合触发条件、收益类型和节奏价值判断战术含义。"


def parse_rows(md_text: str):
    rows = []
    pattern = re.compile(r"^\|\s*(\d+)\s*\|\s*\*\*(.+?)\*\*\s*\|\s*(.+?)\s*\|$", re.MULTILINE)
    for match in pattern.finditer(md_text):
        term = match.group(2).strip()
        definition = match.group(3).strip()
        rows.append((term, definition))
    return rows


def main():
    md_text = SOURCE_MD.read_text(encoding="utf-8")
    rows = parse_rows(md_text)
    if not rows:
        raise RuntimeError("未从 glossary markdown 中解析到任何词条")

    conn = sqlite3.connect(DB_PATH)
    cur = conn.cursor()

    imported = 0
    for term, definition in rows:
        category = infer_category(term, definition)
        tactical_meaning = infer_tactical_meaning(term, definition)
        parsing_hint = infer_parsing_hint(term, definition)
        related_tags = infer_related_tags(term, definition)

        cur.execute(
            """
            INSERT OR REPLACE INTO mechanic_glossary
            (term, category, formal_definition, tactical_meaning, parsing_hint, related_tags, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, datetime('now', 'localtime'))
            """,
            (
                term,
                category,
                definition,
                tactical_meaning,
                parsing_hint,
                str(related_tags).replace("'", '"'),
            ),
        )
        imported += 1

    conn.commit()
    conn.close()
    print(f"imported={imported}")


if __name__ == "__main__":
    main()
