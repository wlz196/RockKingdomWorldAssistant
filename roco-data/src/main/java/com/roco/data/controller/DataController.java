package com.roco.data.controller;

import com.roco.data.model.dto.SkillItemDTO;
import com.roco.data.model.entity.Pet;
import com.roco.data.repository.PetRepository;
import com.roco.data.service.DataService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/data")
@CrossOrigin("*")
public class DataController {

    private final DataService dataService;
    private final PetRepository petRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    public DataController(DataService dataService,
                          PetRepository petRepository) {
        this.dataService = dataService;
        this.petRepository = petRepository;
    }

    @GetMapping("/pets")
    public List<Map<String, Object>> searchPets(
            @RequestParam(required = false, defaultValue = "") String keyword,
            @RequestParam(required = false) String type,
            @RequestParam(required = false, defaultValue = "all") String category,
            @RequestParam(required = false) Boolean hasFeature,
            @RequestParam(required = false) Boolean hasSkill,
            @RequestParam(required = false, defaultValue = "0") int page,
            @RequestParam(required = false, defaultValue = "50") int size) {

        // 预查有技能的精灵ID集合（仅在需要时查询）
        final java.util.Set<Integer> petsWithSkills;
        if (hasSkill != null) {
            petsWithSkills = new java.util.HashSet<>(
                jdbcTemplate.queryForList("SELECT DISTINCT pet_id FROM pet_skill_mapping", Integer.class));
        } else {
            petsWithSkills = null;
        }

        return petRepository.findAll().stream()
            .filter(p -> (keyword.isEmpty() || (p.getName() != null && p.getName().contains(keyword))))
            .filter(p -> (p.getIsBoss() == null || p.getIsBoss() != 1))
            .filter(p -> (type == null || type.isEmpty() || type.equals(p.getPrimary_type()) || type.equals(p.getSecondary_type())))
            .filter(p -> {
                if ("book".equals(category)) {
                    return p.getBookId() != null && p.getBookId() > 0;
                } else if ("non-book".equals(category)) {
                    return p.getBookId() == null || p.getBookId() <= 0;
                } else if ("complete".equals(category)) {
                    return p.getCompleteness() != null && p.getCompleteness() == 1;
                }
                return true;
            })
            .filter(p -> {
                if (hasFeature != null) {
                    boolean has = p.getPetFeature() != null && p.getPetFeature() > 0;
                    return hasFeature ? has : !has;
                }
                return true;
            })
            .filter(p -> {
                if (petsWithSkills != null) {
                    boolean has = petsWithSkills.contains(p.getId());
                    return hasSkill ? has : !has;
                }
                return true;
            })
            .sorted((p1, p2) -> {
                boolean hasBookId1 = p1.getBookId() != null && p1.getBookId() > 0;
                boolean hasBookId2 = p2.getBookId() != null && p2.getBookId() > 0;
                if (hasBookId1 && hasBookId2) {
                    return p1.getBookId().compareTo(p2.getBookId());
                } else if (hasBookId1) {
                    return -1;
                } else if (hasBookId2) {
                    return 1;
                } else {
                    return p1.getId().compareTo(p2.getId());
                }
            })
            .skip((long) page * size)
            .limit(size)
            .map(p -> {
                Map<String, Object> map = new java.util.HashMap<>();
                map.put("id", p.getId());
                map.put("bookId", p.getBookId());
                map.put("name", p.getName());
                map.put("type1", p.getPrimary_type() == null ? "" : p.getPrimary_type());
                map.put("type2", p.getSecondary_type() == null ? "" : p.getSecondary_type());
                map.put("imageUrl", dataService.formatImageUrl(p.getImageUrl(), p.getName()));
                return map;
            })
            .collect(Collectors.toList());
    }

    @GetMapping("/pets/count")
    public long getPetCount() {
        return petRepository.count();
    }

    @GetMapping("/pets/{id}/details")
    public Map<String, Object> getPetDetails(@PathVariable Integer id) {
        return dataService.getPetDetails(id);
    }

    @GetMapping("/skills")
    public List<SkillItemDTO> searchSkills(
            @RequestParam(required = false, defaultValue = "") String keyword,
            @RequestParam(required = false) Integer skillDamType,
            @RequestParam(required = false) Integer filterLogicType,
            @RequestParam(required = false) Integer filterSkillCategory,
            @RequestParam(required = false) Integer filterDamageCategory,
            @RequestParam(required = false, defaultValue = "0") int page,
            @RequestParam(required = false, defaultValue = "100") int size) {

        Map<Integer, String> typeMap = new java.util.HashMap<>();
        jdbcTemplate.query("SELECT id, name FROM types", (rs) -> {
            typeMap.put(rs.getInt("id"), rs.getString("name").replace("系", ""));
        });

        StringBuilder sql = new StringBuilder("SELECT * FROM skill_conf_main WHERE name LIKE ?");
        List<Object> params = new ArrayList<>();
        params.add("%" + keyword + "%");

        if (skillDamType != null) {
            sql.append(" AND skill_dam_type = ?");
            params.add(skillDamType);
        }
        if (filterLogicType != null) {
            sql.append(" AND type = ?");
            params.add(filterLogicType);
        }
        if (filterSkillCategory != null) {
            sql.append(" AND skill_type = ?");
            params.add(filterSkillCategory);
        }
        if (filterDamageCategory != null) {
            sql.append(" AND damage_type = ?");
            params.add(filterDamageCategory);
        }

        sql.append(" ORDER BY id ASC LIMIT ? OFFSET ?");
        params.add(size);
        params.add(page * size);

        System.out.println("--- SKILL SEARCH DEBUG ---");
        System.out.println("SQL: " + sql.toString());
        System.out.println("Params: " + params);
        System.out.println("--- END DEBUG ---");

        return jdbcTemplate.query(sql.toString(), (rs, rowNum) -> {
            SkillItemDTO dto = new SkillItemDTO();
            dto.setId(rs.getInt("id"));
            dto.setName(rs.getString("name"));

            String rawIcon = rs.getString("icon");
            dto.setIcon(dataService.formatSkillIcon(rawIcon));

            dto.setDesc(rs.getString("desc"));
            dto.setAttribute(typeMap.getOrDefault(rs.getInt("skill_dam_type"), "无别"));

            int logicType = rs.getInt("type");
            int damageCategory = rs.getInt("damage_type");
            int skillClass = rs.getInt("skill_type");

            if (logicType == 2) {
                dto.setCategory("特性");
            } else {
                dto.setCategory(switch (damageCategory) {
                    case 2 -> "物理";
                    case 3 -> "魔法";
                    case 4 -> "特殊";
                    default -> (skillClass == 3) ? "变化" : "常规";
                });
            }

            dto.setPower(rs.getString("dam_para") != null ? rs.getString("dam_para") : "0");

            String energyCost = rs.getString("energy_cost");
            try {
                if (energyCost != null) {
                    dto.setPp(Integer.parseInt(energyCost.replace("[", "").replace("]", "")));
                } else {
                    dto.setPp(0);
                }
            } catch (Exception e) {
                dto.setPp(0);
            }

            return dto;
        }, params.toArray());
    }

    // ===== 技能详情 =====
    @GetMapping("/skills/{id}/details")
    public Map<String, Object> getSkillDetails(@PathVariable Integer id) {
        // 查技能基本信息
        Map<Integer, String> typeMap = new HashMap<>();
        jdbcTemplate.query("SELECT id, name FROM types", (rs) -> {
            typeMap.put(rs.getInt("id"), rs.getString("name").replace("系", ""));
        });

        List<Map<String, Object>> skills = jdbcTemplate.query(
            "SELECT * FROM skill_conf_main WHERE id = ?",
            (rs, rowNum) -> {
                Map<String, Object> map = new HashMap<>();
                map.put("id", rs.getInt("id"));
                map.put("name", rs.getString("name"));
                map.put("desc", rs.getString("desc"));
                map.put("icon", dataService.formatSkillIcon(rs.getString("icon")));
                map.put("attribute", typeMap.getOrDefault(rs.getInt("skill_dam_type"), "无"));
                map.put("power", rs.getString("dam_para") != null ? rs.getString("dam_para") : "0");
                map.put("priority", rs.getInt("skill_priority"));

                int logicType = rs.getInt("type");
                int damageCategory = rs.getInt("damage_type");
                int skillClass = rs.getInt("skill_type");
                if (logicType == 2) {
                    map.put("category", "特性");
                } else {
                    map.put("category", switch (damageCategory) {
                        case 2 -> "物理";
                        case 3 -> "魔法";
                        case 4 -> "特殊";
                        default -> (skillClass == 3) ? "变化" : "常规";
                    });
                }

                String energyCost = rs.getString("energy_cost");
                try {
                    map.put("pp", energyCost != null ? Integer.parseInt(energyCost.replace("[", "").replace("]", "")) : 0);
                } catch (Exception e) { map.put("pp", 0); }
                return map;
            }, id);

        if (skills.isEmpty()) return Map.of("error", "技能不存在");
        Map<String, Object> result = skills.get(0);

        // 查能学习该技能的精灵列表
        List<Map<String, Object>> learners = jdbcTemplate.query(
            "SELECT m.pet_id, m.source, p.name, p.image_url, p.book_id, p.main_type_id, p.sub_type_id " +
            "FROM pet_skill_mapping m JOIN pets p ON m.pet_id = p.id WHERE m.skill_id = ? ORDER BY p.name",
            (rs, rowNum) -> {
                Map<String, Object> map = new HashMap<>();
                map.put("petId", rs.getInt("pet_id"));
                map.put("name", rs.getString("name"));
                map.put("bookId", rs.getObject("book_id"));
                map.put("type1", rs.getObject("main_type_id"));
                map.put("type2", rs.getObject("sub_type_id"));
                map.put("imageUrl", dataService.formatImageUrl(rs.getString("image_url"), rs.getString("name")));
                int source = rs.getInt("source");
                map.put("source", source == 0 ? "自学" : source == 1 ? "技能石" : "血脉");
                return map;
            }, id);
        result.put("learners", learners);
        return result;
    }

    @GetMapping("/natures")
    public List<Map<String, Object>> getAllNatures() {
        return jdbcTemplate.queryForList(
            "SELECT n.*, aPlus.name as plusAttrName, aMinus.name as minusAttrName " +
            "FROM natures n " +
            "LEFT JOIN attributes aPlus ON n.plus_attr_id = aPlus.id " +
            "LEFT JOIN attributes aMinus ON n.minus_attr_id = aMinus.id"
        );
    }

    @GetMapping("/bloodlines")
    public List<Map<String, Object>> getBloodlines() {
        List<Map<String, Object>> bloodlines = jdbcTemplate.queryForList("SELECT * FROM bloodlines");
        for (Map<String, Object> bl : bloodlines) {
            String skillIdsStr = (String) bl.get("skill_ids");
            if (skillIdsStr != null && !skillIdsStr.isEmpty()) {
                String cleaned = skillIdsStr.replace("[", "").replace("]", "").trim();
                if (!cleaned.isEmpty()) {
                    String[] ids = cleaned.split(",");
                    List<String> names = new ArrayList<>();
                    for (String id : ids) {
                        try {
                            String name = jdbcTemplate.queryForObject(
                                "SELECT name FROM skill_conf_main WHERE id = ?",
                                String.class,
                                Integer.parseInt(id.trim())
                            );
                            names.add(name);
                        } catch (Exception e) {
                            names.add("未知技能(" + id.trim() + ")");
                        }
                    }
                    bl.put("skillNames", String.join(", ", names));
                }
            }
        }
        return bloodlines;
    }

    @GetMapping("/buffs")
    public List<Map<String, Object>> getBuffs(
            @RequestParam(required = false, defaultValue = "") String keyword,
            @RequestParam(required = false) Integer type) {
        StringBuilder sql = new StringBuilder(
            "SELECT b.*, bt.name as typeName " +
            "FROM buffs b " +
            "LEFT JOIN buff_types bt ON b.type = bt.id " +
            "WHERE b.name LIKE ?"
        );
        List<Object> params = new ArrayList<>();
        params.add("%" + keyword + "%");
        if (type != null) {
            sql.append(" AND b.type = ?");
            params.add(type);
        }
        return jdbcTemplate.queryForList(sql.toString(), params.toArray());
    }

    @GetMapping("/talents")
    public List<Map<String, Object>> getTalents() {
        return jdbcTemplate.queryForList("SELECT * FROM pet_talents WHERE is_referenced = 1");
    }

    // ===== 身高体重精确匹配 =====
    @GetMapping("/dimensions/match")
    public List<Map<String, Object>> matchByDimensions(
            @RequestParam Double height,
            @RequestParam Double weight) {
        // 查找身高体重范围包含输入值的精灵，按匹配度排序
        String sql = "SELECT d.pet_id, p.name, p.image_url, p.book_id, p.main_type_id, p.sub_type_id, " +
            "d.height_min, d.height_max, d.weight_min, d.weight_max, " +
            "ABS((d.height_min + d.height_max) / 2.0 - ?) + ABS((d.weight_min + d.weight_max) / 2.0 - ?) AS distance " +
            "FROM pet_dimensions d JOIN pets p ON d.pet_id = p.id " +
            "WHERE d.height_min <= ? AND d.height_max >= ? AND d.weight_min <= ? AND d.weight_max >= ? " +
            "ORDER BY distance ASC LIMIT 50";

        return jdbcTemplate.query(sql, (rs, rowNum) -> {
            Map<String, Object> map = new HashMap<>();
            map.put("id", rs.getInt("pet_id"));
            map.put("name", rs.getString("name"));
            map.put("bookId", rs.getObject("book_id"));
            map.put("type1", rs.getObject("main_type_id"));
            map.put("type2", rs.getObject("sub_type_id"));
            map.put("imageUrl", dataService.formatImageUrl(rs.getString("image_url"), rs.getString("name")));
            map.put("heightMin", rs.getDouble("height_min"));
            map.put("heightMax", rs.getDouble("height_max"));
            map.put("weightMin", rs.getDouble("weight_min"));
            map.put("weightMax", rs.getDouble("weight_max"));
            map.put("distance", rs.getDouble("distance"));
            return map;
        }, height, weight, height, height, weight, weight);
    }

    // ===== 身高体重查询 =====
    @GetMapping("/dimensions/search")
    public List<Map<String, Object>> searchByDimensions(
            @RequestParam(required = false) Double heightMin,
            @RequestParam(required = false) Double heightMax,
            @RequestParam(required = false) Double weightMin,
            @RequestParam(required = false) Double weightMax) {
        StringBuilder sql = new StringBuilder(
            "SELECT d.pet_id, p.name, p.image_url, p.book_id, p.main_type_id, p.sub_type_id, " +
            "d.height_min, d.height_max, d.weight_min, d.weight_max " +
            "FROM pet_dimensions d JOIN pets p ON d.pet_id = p.id WHERE 1=1"
        );
        List<Object> params = new ArrayList<>();
        if (heightMin != null) {
            sql.append(" AND d.height_max >= ?");
            params.add(heightMin);
        }
        if (heightMax != null) {
            sql.append(" AND d.height_min <= ?");
            params.add(heightMax);
        }
        if (weightMin != null) {
            sql.append(" AND d.weight_max >= ?");
            params.add(weightMin);
        }
        if (weightMax != null) {
            sql.append(" AND d.weight_min <= ?");
            params.add(weightMax);
        }
        sql.append(" ORDER BY p.name ASC LIMIT 200");

        return jdbcTemplate.query(sql.toString(), (rs, rowNum) -> {
            Map<String, Object> map = new HashMap<>();
            map.put("id", rs.getInt("pet_id"));
            map.put("name", rs.getString("name"));
            map.put("bookId", rs.getObject("book_id"));
            map.put("type1", rs.getObject("main_type_id"));
            map.put("type2", rs.getObject("sub_type_id"));
            map.put("imageUrl", dataService.formatImageUrl(rs.getString("image_url"), rs.getString("name")));
            map.put("heightMin", rs.getDouble("height_min"));
            map.put("heightMax", rs.getDouble("height_max"));
            map.put("weightMin", rs.getDouble("weight_min"));
            map.put("weightMax", rs.getDouble("weight_max"));
            return map;
        }, params.toArray());
    }

    // ===== 蛋组查询 =====
    @GetMapping("/egg-groups")
    public List<Map<String, Object>> getEggGroups(
            @RequestParam(required = false) Integer groupId) {
        Map<Integer, String> groupNames = new HashMap<>();
        groupNames.put(1, "植物组"); groupNames.put(2, "动物组"); groupNames.put(3, "龙系组");
        groupNames.put(4, "守护组"); groupNames.put(5, "萌系组"); groupNames.put(6, "精灵组");
        groupNames.put(7, "唯美组"); groupNames.put(8, "力量组"); groupNames.put(9, "矿石组");
        groupNames.put(10, "不死组"); groupNames.put(11, "翼组"); groupNames.put(12, "猎鹰组");
        groupNames.put(13, "幻灵组"); groupNames.put(14, "神系组"); groupNames.put(15, "动作组");

        if (groupId != null) {
            return jdbcTemplate.query(
                "SELECT e.group_id, p.id as pet_id, p.name, p.image_url, p.book_id, p.main_type_id, p.sub_type_id " +
                "FROM pet_egg_groups e JOIN pets p ON e.pet_id = p.id WHERE e.group_id = ? ORDER BY p.name",
                (rs, rowNum) -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("petId", rs.getInt("pet_id"));
                    map.put("name", rs.getString("name"));
                    map.put("bookId", rs.getObject("book_id"));
                    map.put("type1", rs.getObject("main_type_id"));
                    map.put("type2", rs.getObject("sub_type_id"));
                    map.put("imageUrl", dataService.formatImageUrl(rs.getString("image_url"), rs.getString("name")));
                    map.put("groupId", rs.getInt("group_id"));
                    map.put("groupName", groupNames.getOrDefault(rs.getInt("group_id"), "未知组"));
                    return map;
                }, groupId);
        }

        return jdbcTemplate.query(
            "SELECT group_id, COUNT(*) as pet_count FROM pet_egg_groups GROUP BY group_id ORDER BY group_id",
            (rs, rowNum) -> {
                Map<String, Object> map = new HashMap<>();
                int gid = rs.getInt("group_id");
                map.put("groupId", gid);
                map.put("groupName", groupNames.getOrDefault(gid, "未知组"));
                map.put("petCount", rs.getInt("pet_count"));
                return map;
            });
    }

    @GetMapping("/egg-groups/breed-check")
    public Map<String, Object> checkBreedCompatibility(
            @RequestParam Integer petId1,
            @RequestParam Integer petId2) {
        List<Integer> groups1 = jdbcTemplate.queryForList(
            "SELECT group_id FROM pet_egg_groups WHERE pet_id = ?", Integer.class, petId1);
        List<Integer> groups2 = jdbcTemplate.queryForList(
            "SELECT group_id FROM pet_egg_groups WHERE pet_id = ?", Integer.class, petId2);

        Map<Integer, String> groupNames = new HashMap<>();
        groupNames.put(1, "植物组"); groupNames.put(2, "动物组"); groupNames.put(3, "龙系组");
        groupNames.put(4, "守护组"); groupNames.put(5, "萌系组"); groupNames.put(6, "精灵组");
        groupNames.put(7, "唯美组"); groupNames.put(8, "力量组"); groupNames.put(9, "矿石组");
        groupNames.put(10, "不死组"); groupNames.put(11, "翼组"); groupNames.put(12, "猎鹰组");
        groupNames.put(13, "幻灵组"); groupNames.put(14, "神系组"); groupNames.put(15, "动作组");

        List<String> commonGroups = groups1.stream()
            .filter(groups2::contains)
            .map(gid -> groupNames.getOrDefault(gid, "未知组"))
            .collect(Collectors.toList());

        String name1 = petRepository.findById(petId1).map(Pet::getName).orElse("未知");
        String name2 = petRepository.findById(petId2).map(Pet::getName).orElse("未知");

        Map<String, Object> result = new HashMap<>();
        result.put("pet1", name1);
        result.put("pet2", name2);
        result.put("pet1Groups", groups1.stream().map(g -> groupNames.getOrDefault(g, "未知组")).collect(Collectors.toList()));
        result.put("pet2Groups", groups2.stream().map(g -> groupNames.getOrDefault(g, "未知组")).collect(Collectors.toList()));
        result.put("canBreed", !commonGroups.isEmpty());
        result.put("commonGroups", commonGroups);
        return result;
    }

    @GetMapping("/types")
    public Map<String, Object> getTypeData() {
        Map<String, Object> result = new HashMap<>();
        result.put("list", jdbcTemplate.queryForList("SELECT * FROM types"));
        result.put("relations", jdbcTemplate.queryForList("SELECT * FROM type_relations"));
        return result;
    }
}
