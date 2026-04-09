#!/bin/bash

# Roco Encyclopedia Data Sync Pipeline
# This script automates binary parsing and database importing.

echo "Starting Roco Data Synchronization..."
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"
PARSER_DIR="$PROJECT_ROOT/parser"

# 1. Parse Binary to JSON
echo "1. Parsing Binary Data to JSON..."
cd "$PARSER_DIR"
python3 extract_tables.py

# 2. Import into SQLite
echo "2. Importing Data into SQLite Database..."
cd "$PARSER_DIR/scripts"

# Order matters: Types/Buffs first, then Pets/Skills/Mappings
python3 create_types_db.py
python3 create_attr_db.py
python3 create_buff_types_db.py
python3 create_buffs_db.py
python3 create_natures_db.py
python3 import_skill_conf_main.py
python3 import_pets.py
python3 import_evolutions.py
python3 import_bloodlines.py
python3 import_pet_level_skills.py
python3 import_learn_mappings.py
python3 import_pet_talents.py
python3 import_pet_talent_relations.py
python3 import_pet_egg_group_membership.py
python3 patch_special_evolutions.py

echo "--------------------------------------"
echo "Sync Completed! Database is now updated."
echo "Location: $PROJECT_ROOT/roco_encyclopedia.db"
echo "--------------------------------------"
