package com.roco.data.controller;

import com.roco.data.model.dto.SkillItemDTO;
import com.roco.data.model.entity.Pet;
import com.roco.data.repository.PetRepository;
import com.roco.data.service.DataService;
import com.roco.data.service.AiTaggerService;
import com.roco.data.service.AiPetReviewService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Arrays;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/data")
@CrossOrigin("*")
public class DataController {

    private static final Logger log = LoggerFactory.getLogger(DataController.class);

    private final DataService dataService;
    private final PetRepository petRepository;
    private final AiTaggerService aiTaggerService;
    private final AiPetReviewService aiPetReviewService;

    // 进度追踪状态 (技能)
    private final AtomicBoolean isBatchRunning = new AtomicBoolean(false);
    private final AtomicInteger processedCount = new AtomicInteger(0);
    private int totalToProcess = 0;

    // 进度追踪状态 (精灵评述)
    private final AtomicBoolean isPetReviewRunning = new AtomicBoolean(false);
    private final AtomicInteger processedPetReviewCount = new AtomicInteger(0);
    private int totalPetReviewsToProcess = 0;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    public DataController(DataService dataService,
                          PetRepository petRepository,
                          AiTaggerService aiTaggerService,
                          AiPetReviewService aiPetReviewService) {
        this.dataService = dataService;
        this.petRepository = petRepository;
        this.aiTaggerService = aiTaggerService;
        this.aiPetReviewService = aiPetReviewService;
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
                jdbcTemplate.queryForList("SELECT DISTINCT pet_id FROM pet_level_skills", Integer.class));
        } else {
            petsWithSkills = null;
        }

        return petRepository.findAll().stream()
            .filter(p -> (keyword.isEmpty() || (p.getName() != null && p.getName().contains(keyword))))
            .filter(p -> {
                boolean isBoss = p.getIsBoss() != null && p.getIsBoss() == 1;
                if ("boss".equals(category)) {
                    return isBoss;
                } else {
                    return !isBoss;
                }
            })
            .filter(p -> (type == null || type.isEmpty() || type.equals(p.getPrimary_type()) || type.equals(p.getSecondary_type())))
            .filter(p -> {
                if ("book".equals(category)) {
                    return p.getIsOfficial() != null && p.getIsOfficial() == 1;
                } else if ("non-book".equals(category)) {
                    return p.getIsOfficial() == null || p.getIsOfficial() == 0;
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
                map.put("form", p.getForm());
                map.put("isBoss", p.getIsBoss());
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
            @RequestParam(required = false) Boolean hasOwner,
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

        // 实装过滤
        if (hasOwner != null) {
            sql.append(hasOwner ? " AND is_official = 1" : " AND is_official = 0");
        }

        sql.append(" ORDER BY id ASC LIMIT ? OFFSET ?");
        params.add(size);
        params.add(page * size);

        System.out.println("--- SKILL SEARCH DEBUG ---");
        System.out.println("SQL: " + sql.toString());
        System.out.println("Params: " + params);
        System.out.println("hasOwner filter: " + hasOwner);
        System.out.println("--- END DEBUG ---");

        List<SkillItemDTO> results = jdbcTemplate.query(sql.toString(), (rs, rowNum) -> {
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

        return results;
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
                map.put("attribute", typeMap.getOrDefault(rs.getInt("skill_dam_type"), "无别"));
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
            "SELECT m.pet_id, m.source, p.name, p.image_url, p.book_id, p.main_type_id, p.sub_type_id, p.is_official " +
            "FROM pet_level_skills m JOIN pets p ON m.pet_id = p.id " +
            "WHERE m.skill_id = ? ORDER BY p.name",
            (rs, rowNum) -> {
                Map<String, Object> map = new HashMap<>();
                map.put("petId", rs.getInt("pet_id"));
                map.put("name", rs.getString("name"));
                map.put("bookId", rs.getObject("book_id"));
                map.put("type1", rs.getObject("main_type_id"));
                map.put("type2", rs.getObject("sub_type_id"));
                map.put("imageUrl", dataService.formatImageUrl(rs.getString("image_url"), rs.getString("name")));
                map.put("isOfficial", rs.getInt("is_official"));
                int source = rs.getInt("source");
                map.put("source", source == 0 ? "自学" : source == 1 ? "技能石" : source == 2 ? "血脉" : "特性");
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
    @PostMapping("/pets/{petId}/skills/{skillId}/common/toggle")
    public Map<String, Object> toggleCommonSkill(@PathVariable Integer petId, @PathVariable Integer skillId) {
        dataService.toggleCommonSkill(petId, skillId);
        return Map.of("success", true);
    }

    // ===== 特性图鉴 =====
    @GetMapping("/features")
    public List<Map<String, Object>> getFeatures(
            @RequestParam(required = false, defaultValue = "") String keyword,
            @RequestParam(required = false) Integer skillDamType,
            @RequestParam(required = false, defaultValue = "true") boolean onlyOfficial,
            @RequestParam(required = false, defaultValue = "0") int page,
            @RequestParam(required = false, defaultValue = "100") int size) {
        
        StringBuilder sql = new StringBuilder("SELECT id, name, desc, icon, skill_dam_type FROM skill_conf_main WHERE type = 2 AND name LIKE ?");
        List<Object> params = new ArrayList<>();
        params.add("%" + keyword + "%");

        if (skillDamType != null) {
            sql.append(" AND skill_dam_type = ?");
            params.add(skillDamType);
        }

        if (onlyOfficial) {
            sql.append(" AND is_official = 1");
        }

        sql.append(" ORDER BY id ASC LIMIT ? OFFSET ?");
        params.add(size);
        params.add(page * size);

        return jdbcTemplate.query(sql.toString(), (rs, rowNum) -> {
            Map<String, Object> map = new HashMap<>();
            map.put("id", rs.getInt("id"));
            map.put("name", rs.getString("name"));
            map.put("desc", rs.getString("desc"));
            map.put("icon", dataService.formatSkillIcon(rs.getString("icon")));
            map.put("skillDamType", rs.getInt("skill_dam_type"));
            return map;
        }, params.toArray());
    }

    @GetMapping("/benchmarks/global")
    public List<Map<String, Object>> getGlobalBenchmarks() {
        String sql = "SELECT * FROM stat_quantiles_global ORDER BY CASE stat_key " +
                     "WHEN 'hp' THEN 1 WHEN 'attack' THEN 2 WHEN 'defense' THEN 3 " +
                     "WHEN 'magic_attack' THEN 4 WHEN 'magic_defense' THEN 5 " +
                     "WHEN 'speed' THEN 6 WHEN 'total_stats' THEN 7 END";
        return jdbcTemplate.query(sql, (rs, rowNum) -> {
            Map<String, Object> map = new HashMap<>();
            map.put("statKey", rs.getString("stat_key"));
            map.put("p50", rs.getDouble("p50"));
            map.put("p80", rs.getDouble("p80"));
            map.put("p95", rs.getDouble("p95"));
            map.put("maxVal", rs.getDouble("max_val"));
            map.put("dataCount", rs.getInt("data_count"));
            return map;
        });
    }

    @GetMapping("/benchmarks/types")
    public List<Map<String, Object>> getBenchmarksTypes() {
        String sql = "SELECT t.id as typeId, t.name as typeName, q.stat_key, q.p50, q.p80, q.p95, q.max_val, q.data_count, " +
                     "RANK() OVER(PARTITION BY q.stat_key ORDER BY q.p80 DESC) as rank80, " +
                     "RANK() OVER(PARTITION BY q.stat_key ORDER BY q.p95 DESC) as rank95 " +
                     "FROM stat_quantiles_by_type q JOIN types t ON q.type_id = t.id " +
                     "WHERE t.id > 1 " +
                     "ORDER BY q.type_id, CASE q.stat_key " +
                     "WHEN 'hp' THEN 1 WHEN 'attack' THEN 2 WHEN 'defense' THEN 3 " +
                     "WHEN 'magic_attack' THEN 4 WHEN 'magic_defense' THEN 5 " +
                     "WHEN 'speed' THEN 6 WHEN 'total_stats' THEN 7 END";
        return jdbcTemplate.query(sql, (rs, rowNum) -> {
            Map<String, Object> map = new HashMap<>();
            map.put("typeId", rs.getInt("typeId"));
            map.put("typeName", rs.getString("typeName"));
            map.put("statKey", rs.getString("stat_key"));
            map.put("p50", rs.getDouble("p50"));
            map.put("p80", rs.getDouble("p80"));
            map.put("p95", rs.getDouble("p95"));
            map.put("maxVal", rs.getDouble("max_val"));
            map.put("dataCount", rs.getInt("data_count"));
            map.put("rank80", rs.getInt("rank80"));
            map.put("rank95", rs.getInt("rank95"));
            return map;
        });
    }

    @GetMapping("/benchmarks/types/{typeId}")
    public List<Map<String, Object>> getBenchmarksByType(@PathVariable Integer typeId) {
        String sql = "SELECT * FROM (" +
                     "  SELECT t.id as typeId, t.name as typeName, q.stat_key, q.p50, q.p80, q.p95, q.max_val, q.data_count, " +
                     "  RANK() OVER(PARTITION BY q.stat_key ORDER BY q.p80 DESC) as rank80, " +
                     "  RANK() OVER(PARTITION BY stat_key ORDER BY p95 DESC) as rank95 " +
                     "  FROM stat_quantiles_by_type q JOIN types t ON q.type_id = t.id " +
                     "  WHERE t.id > 1" +
                     ") WHERE typeId = ? " +
                     "ORDER BY CASE stat_key " +
                     "WHEN 'hp' THEN 1 WHEN 'attack' THEN 2 WHEN 'defense' THEN 3 " +
                     "WHEN 'magic_attack' THEN 4 WHEN 'magic_defense' THEN 5 " +
                     "WHEN 'speed' THEN 6 WHEN 'total_stats' THEN 7 END";
        return jdbcTemplate.query(sql, (rs, rowNum) -> {
            Map<String, Object> map = new HashMap<>();
            map.put("typeId", rs.getInt("typeId"));
            map.put("typeName", rs.getString("typeName"));
            map.put("statKey", rs.getString("stat_key"));
            map.put("p50", rs.getDouble("p50"));
            map.put("p80", rs.getDouble("p80"));
            map.put("p95", rs.getDouble("p95"));
            map.put("maxVal", rs.getDouble("max_val"));
            map.put("dataCount", rs.getInt("data_count"));
            map.put("rank80", rs.getInt("rank80"));
            map.put("rank95", rs.getInt("rank95"));
            return map;
        }, typeId);
    }

    @GetMapping("/analysis/type-profile/{typeId}")
    public Map<String, Object> getTypeProfile(@PathVariable Integer typeId) {
        Map<String, Object> result = new HashMap<>();
        
        // 获取系别名称
        try {
            String typeName = jdbcTemplate.queryForObject("SELECT name FROM types WHERE id = ?", String.class, typeId);
            result.put("typeName", typeName);
        } catch (Exception e) {
            result.put("typeName", "未知系别");
        }

        // 1. 获取系别基本信息与排名 (Benchmarks)
        // 采用两步法：先拿该系别的 RAW 数据，再在子查询中通过 RANK 计算排名
        String benchmarkSql = "SELECT * FROM (" +
                             "  SELECT t.id as tid, q.stat_key, q.p50, q.p80, q.p95, q.max_val, q.data_count, " +
                             "  RANK() OVER(PARTITION BY q.stat_key ORDER BY q.p80 DESC) as r80, " +
                             "  RANK() OVER(PARTITION BY q.stat_key ORDER BY q.p95 DESC) as r95 " +
                             "  FROM stat_quantiles_by_type q JOIN types t ON q.type_id = t.id " +
                             "  WHERE t.id > 1" +
                             ") WHERE tid = ?";
        
        List<Map<String, Object>> benchmarks = jdbcTemplate.query(benchmarkSql, (rs, rowNum) -> {
            Map<String, Object> m = new HashMap<>();
            m.put("statKey", rs.getString("stat_key"));
            m.put("p50", rs.getDouble("p50"));
            m.put("p80", rs.getDouble("p80"));
            m.put("p95", rs.getDouble("p95"));
            m.put("maxVal", rs.getDouble("max_val"));
            m.put("rank80", rs.getInt("r80"));
            m.put("rank95", rs.getInt("r95"));
            return m;
        }, typeId);
        result.put("benchmarks", benchmarks);

        // 2. 获取全局平均水位线 (Global Benchmarks)
        result.put("globalBenchmarks", getGlobalBenchmarks());

        // 3. 获取该系别实装技能 (Skills - type=1)
        String skillsSql = "SELECT s.id, s.name, s.icon, " +
                          "CASE s.damage_type WHEN 1 THEN '物理' WHEN 2 THEN '魔法' ELSE '变化' END as category, " +
                          "s.dam_para as power, s.energy_cost as pp, s.desc " +
                          "FROM skill_conf_main s " +
                          "WHERE s.type = 1 AND s.is_official = 1 AND s.skill_dam_type = ? " +
                          "ORDER BY s.id ASC";
        List<Map<String, Object>> skills = jdbcTemplate.query(skillsSql, (rs, rowNum) -> {
            Map<String, Object> m = new HashMap<>();
            m.put("id", rs.getInt("id"));
            m.put("name", rs.getString("name"));
            m.put("icon", dataService.formatSkillIcon(rs.getString("icon")));
            m.put("category", rs.getString("category"));
            m.put("power", rs.getString("power"));
            m.put("pp", rs.getString("pp"));
            m.put("desc", rs.getString("desc"));
            return m;
        }, typeId);
        result.put("skills", skills);

        // 4. 获取该系别实装特性 (Features - type=2)
        String featuresSql = "SELECT id, name, icon, desc " +
                            "FROM skill_conf_main WHERE type = 2 AND is_official = 1 AND skill_dam_type = ? " +
                            "ORDER BY id ASC";
        List<Map<String, Object>> features = jdbcTemplate.query(featuresSql, (rs, rowNum) -> {
            Map<String, Object> m = new HashMap<>();
            m.put("id", rs.getInt("id"));
            m.put("name", rs.getString("name"));
            m.put("icon", dataService.formatSkillIcon(rs.getString("icon")));
            m.put("desc", rs.getString("desc"));
            return m;
        }, typeId);
        result.put("features", features);

        return result;
    }

    /**
     * 获取技能列表用于手动打标 (支持分页和系别筛选)
     */
    @GetMapping("/skills/list")
    public Map<String, Object> getSkillsForTagging(
            @RequestParam(required = false) Integer typeId,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "50") int size) {
        
        int offset = (page - 1) * size;
        StringBuilder sql = new StringBuilder("SELECT id, name, desc, ai_tags, skill_dam_type as typeId FROM skill_conf_main WHERE is_official = 1 AND type = 1");
        List<Object> params = new ArrayList<>();
        
        if (typeId != null) {
            sql.append(" AND skill_dam_type = ?");
            params.add(typeId);
        }
        if (keyword != null && !keyword.isEmpty()) {
            sql.append(" AND (name LIKE ? OR desc LIKE ? OR ai_tags LIKE ?)");
            String pattern = "%" + keyword + "%";
            params.add(pattern);
            params.add(pattern);
            params.add(pattern);
        }
        
        sql.append(" ORDER BY id ASC LIMIT ? OFFSET ?");
        params.add(size);
        params.add(offset);
        
        List<Map<String, Object>> skills = jdbcTemplate.query(sql.toString(), (rs, rowNum) -> {
            Map<String, Object> m = new HashMap<>();
            m.put("id", rs.getInt("id"));
            m.put("name", rs.getString("name"));
            m.put("desc", rs.getString("desc"));
            m.put("aiTags", rs.getString("ai_tags"));
            m.put("typeId", rs.getInt("typeId"));
            return m;
        }, params.toArray());
        
        Map<String, Object> result = new HashMap<>();
        result.put("skills", skills);
        result.put("page", page);
        result.put("size", size);
        return result;
    }

    /**
     * 更新技能的 AI 标签
     */
    @PostMapping("/skills/{id}/tags")
    public Map<String, Object> updateSkillTags(@PathVariable int id, @RequestBody Map<String, List<String>> body) {
        List<String> tags = body.get("tags");
        String tagsJson = (tags == null || tags.isEmpty()) ? "" : String.join(",", tags);
        
        jdbcTemplate.update("UPDATE skill_conf_main SET ai_tags = ? WHERE id = ?", tagsJson, id);
        
        Map<String, Object> res = new HashMap<>();
        res.put("success", true);
        res.put("id", id);
        res.put("tags", tags);
        return res;
    }

    /**
     * 获取全局配置
     */
    @GetMapping("/config/tags")
    public Map<String, Object> getTagConfig() {
        String sql = "SELECT config_value FROM global_config WHERE config_key = 'SKILL_TAG_LIBRARY'";
        List<String> values = jdbcTemplate.query(sql, (rs, rowNum) -> rs.getString("config_value"));
        
        Map<String, Object> res = new HashMap<>();
        res.put("tags", values.isEmpty() ? "输出,生存,控制,博弈,机制" : values.get(0));
        return res;
    }

    /**
     * 更新全局标签库配置
     */
    @PostMapping("/config/tags")
    public Map<String, Object> updateTagConfig(@RequestBody Map<String, String> body) {
        String tagsRaw = body.get("tags");
        // 鲁棒性处理：去除首尾空格、按逗号分割、去重、重新组合
        String tagsClean = Arrays.stream(tagsRaw.split("[,，]"))
                                .map(String::trim)
                                .filter(s -> !s.isEmpty())
                                .distinct()
                                .collect(Collectors.joining(","));
        
        jdbcTemplate.update("INSERT OR REPLACE INTO global_config (config_key, config_value) VALUES ('SKILL_TAG_LIBRARY', ?)", tagsClean);
        
        Map<String, Object> res = new HashMap<>();
        res.put("success", true);
        res.put("tags", tagsClean);
        return res;
    }

    /**
     * 获取技能打标统计数据
     */
    @GetMapping("/skills/stats")
    public Map<String, Object> getSkillStats() {
        // 统计时也只关注正式的主动技能
        String allTagsSql = "SELECT ai_tags FROM skill_conf_main WHERE ai_tags IS NOT NULL AND ai_tags != '' AND is_official = 1 AND type = 1";
        List<String> tagsList = jdbcTemplate.queryForList(allTagsSql, String.class);
        
        String totalSql = "SELECT COUNT(*) FROM skill_conf_main WHERE is_official = 1 AND type = 1";
        Integer totalSkills = jdbcTemplate.queryForObject(totalSql, Integer.class);
        
        Map<String, Integer> tagCounts = new HashMap<>();
        int taggedSkillsCount = tagsList.size();
        
        for (String tags : tagsList) {
            String[] split = tags.split("[,，]");
            for (String tag : split) {
                String cleanTag = tag.trim();
                if (!cleanTag.isEmpty()) {
                    tagCounts.put(cleanTag, tagCounts.getOrDefault(cleanTag, 0) + 1);
                }
            }
        }
        
        // 按频率从高到低排序
        List<Map<String, Object>> sortedTags = tagCounts.entrySet().stream()
            .sorted((e1, e2) -> e2.getValue().compareTo(e1.getValue()))
            .map(entry -> {
                Map<String, Object> map = new HashMap<>();
                map.put("name", entry.getKey());
                map.put("count", entry.getValue());
                return map;
            })
            .collect(Collectors.toList());

        Map<String, Object> res = new HashMap<>();
        res.put("totalSkills", totalSkills);
        res.put("taggedSkills", taggedSkillsCount);
        res.put("tagDistribution", sortedTags);
        return res;
    }

    /**
     * AI 智能建议单个技能标签
     */
    @PostMapping("/skills/{id}/ai-suggest")
    public Map<String, Object> aiSuggestSingle(@PathVariable Integer id) {
        Map<String, Object> skill = jdbcTemplate.queryForMap(
            "SELECT name, desc FROM skill_conf_main WHERE id = ?", id);
        
        String name = (String) skill.get("name");
        String desc = (String) skill.get("desc");
        
        Map<String, Object> analysis = aiTaggerService.analyzeSkill(name, desc);
        Map<String, Object> res = new HashMap<>();
        res.put("success", analysis != null);
        res.put("analysis", analysis);
        if (analysis != null) {
            res.put("combinedTags", aiTaggerService.combineTags(analysis));
        }
        return res;
    }

    @PostMapping("/skills/ai-sync-batch")
    public Map<String, Object> aiSyncBatch() {
        // 获取所有正式技能
        List<Map<String, Object>> skills = jdbcTemplate.queryForList(
            "SELECT id, name, desc FROM skill_conf_main WHERE is_official = 1 AND type = 1");
        
        if (isBatchRunning.get()) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "打标任务已经在运行中，请等待完成。");
            return error;
        }

        // 初始化状态
        isBatchRunning.set(true);
        processedCount.set(0);
        totalToProcess = skills.size();

        // 开启后台并行任务
        new Thread(() -> {
            log.info("🚀 开始并行 AI 打标任务，总计 {} 条...", totalToProcess);
            int batchSize = 10;
            int threadCount = 5;
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            
            // 任务分片
            List<List<Map<String, Object>>> chunks = new ArrayList<>();
            for (int i = 0; i < skills.size(); i += batchSize) {
                chunks.add(skills.subList(i, Math.min(i + batchSize, skills.size())));
            }

            for (List<Map<String, Object>> chunk : chunks) {
                executor.submit(() -> {
                    try {
                        List<Map<String, Object>> results = aiTaggerService.analyzeSkillsBatch(chunk);
                        for (Map<String, Object> res : results) {
                            Integer id = (Integer) res.get("id");
                            String combinedTags = aiTaggerService.combineTags(res);
                            jdbcTemplate.update("UPDATE skill_conf_main SET ai_tags = ? WHERE id = ?", combinedTags, id);
                        }
                        processedCount.addAndGet(chunk.size());
                        log.info("✅ 批次处理完成，当前总进度: {} / {}", processedCount.get(), totalToProcess);
                    } catch (Exception e) {
                        log.error("❌ 批次任务异常: {}", e.getMessage());
                    }
                });
            }

            executor.shutdown();
            try {
                if (executor.awaitTermination(60, TimeUnit.MINUTES)) {
                    log.info("🎉 全量 AI 极速打标任务圆满完成！");
                }
            } catch (InterruptedException e) {
                log.error("打标任务被中断: {}", e.getMessage());
                Thread.currentThread().interrupt();
            } finally {
                isBatchRunning.set(false);
            }
        }).start();

        Map<String, Object> res = new HashMap<>();
        res.put("success", true);
        res.put("message", "全量 AI 极速并行任务已在后台启动。处理 1300+ 技能预计耗时 10-20 分钟。");
        return res;
    }

    /**
     * 获取待评述的最终形态精灵列表
     */
    @GetMapping("/pets/review-candidates")
    public List<Map<String, Object>> getReviewCandidates() {
        return jdbcTemplate.queryForList(
            "SELECT p.id, p.name, p.hp, p.attack, p.defense, p.magic_attack, p.magic_defense, p.speed, r.review_content " +
            "FROM pets p " +
            "LEFT JOIN pet_ai_reviews r ON p.id = r.pet_id " +
            "WHERE p.is_official = 1 AND (p.evolution_targets IS NULL OR p.evolution_targets = '') " +
            "ORDER BY p.id ASC");
    }

    /**
     * AI 生成单只精灵战术评述
     */
    @PostMapping("/pets/{id}/ai-review")
    public Map<String, Object> aiReviewSingle(@PathVariable Integer id) {
        String review = aiPetReviewService.generateReview(id);
        if (review != null) {
            aiPetReviewService.saveReview(id, review);
        }
        Map<String, Object> res = new HashMap<>();
        res.put("success", review != null);
        res.put("review", review);
        return res;
    }

    /**
     * 一键极速生成全量最终形态精灵评述
     */
    @PostMapping("/pets/ai-sync-reviews")
    public Map<String, Object> aiSyncPetReviews() {
        if (isPetReviewRunning.get()) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "精灵评述任务已在运行中。");
            return error;
        }

        List<Map<String, Object>> candidates = jdbcTemplate.queryForList(
            "SELECT id FROM pets WHERE is_official = 1 AND (evolution_targets IS NULL OR evolution_targets = '')");
        
        isPetReviewRunning.set(true);
        processedPetReviewCount.set(0);
        totalPetReviewsToProcess = candidates.size();

        new Thread(() -> {
            log.info("🚀 开始并行生成精灵战术评述，共 {} 只...", totalPetReviewsToProcess);
            ExecutorService executor = Executors.newFixedThreadPool(5);
            
            for (Map<String, Object> candidate : candidates) {
                Integer id = (Integer) candidate.get("id");
                executor.submit(() -> {
                    try {
                        String review = aiPetReviewService.generateReview(id);
                        if (review != null) {
                            aiPetReviewService.saveReview(id, review);
                        }
                        processedPetReviewCount.incrementAndGet();
                    } catch (Exception e) {
                        log.error("❌ 精灵评述生成异常 [#{}]: {}", id, e.getMessage());
                    }
                });
            }

            executor.shutdown();
            try {
                executor.awaitTermination(60, TimeUnit.MINUTES);
                log.info("🎉 全量精灵战术评述生成完毕！");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                isPetReviewRunning.set(false);
            }
        }).start();

        Map<String, Object> res = new HashMap<>();
        res.put("success", true);
        res.put("message", "全量精灵评述引擎已启动，预计耗时 10-15 分钟。");
        return res;
    }

    /**
     * 获取精灵评述任务进度
     */
    @GetMapping("/pets/ai-sync-reviews-status")
    public Map<String, Object> getPetReviewStatus() {
        Map<String, Object> res = new HashMap<>();
        int processed = processedPetReviewCount.get();
        double percent = totalPetReviewsToProcess > 0 ? (processed * 100.0 / totalPetReviewsToProcess) : 0;
        
        res.put("running", isPetReviewRunning.get());
        res.put("processed", processed);
        res.put("total", totalPetReviewsToProcess);
        res.put("percent", Math.min(100.0, percent));
        return res;
    }
}
