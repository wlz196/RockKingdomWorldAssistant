package com.roco.data.controller;

import com.roco.data.model.dto.SkillItemDTO;
import com.roco.data.model.entity.Pet;
import com.roco.data.repository.PetRepository;
import com.roco.data.service.DataService;
import com.roco.data.service.AiPetReviewService;
import com.roco.data.service.PetProfileEngineService;
import com.roco.data.service.PetBuildProfileDraftService;
import com.roco.data.service.TacticalTagDraftService;
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
    private final AiPetReviewService aiPetReviewService;
    private final PetProfileEngineService petProfileEngineService;
    private final PetBuildProfileDraftService petBuildProfileDraftService;
    private final TacticalTagDraftService tacticalTagDraftService;

    // 进度追踪状态 (精灵评述)
    private final AtomicBoolean isPetReviewRunning = new AtomicBoolean(false);
    private final AtomicInteger processedPetReviewCount = new AtomicInteger(0);
    private int totalPetReviewsToProcess = 0;

    // 进度追踪状态 (精灵画像)
    private final AtomicBoolean isPetProfileRunning = new AtomicBoolean(false);
    private final AtomicInteger processedPetProfileCount = new AtomicInteger(0);
    private int totalPetProfilesToProcess = 0;

    // 进度追踪状态 (构筑草稿)
    private final AtomicBoolean isBuildDraftRunning = new AtomicBoolean(false);
    private final AtomicInteger processedBuildDraftCount = new AtomicInteger(0);
    private int totalBuildDraftsToProcess = 0;

    // 进度追踪状态 (技能标签)
    private final AtomicBoolean isSkillTagRunning = new AtomicBoolean(false);
    private final AtomicInteger processedSkillTagCount = new AtomicInteger(0);
    private int totalSkillTagsToProcess = 0;

    // 进度追踪状态 (特性标签)
    private final AtomicBoolean isFeatureTagRunning = new AtomicBoolean(false);
    private final AtomicInteger processedFeatureTagCount = new AtomicInteger(0);
    private int totalFeatureTagsToProcess = 0;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    public DataController(DataService dataService,
                          PetRepository petRepository,
                          AiPetReviewService aiPetReviewService,
                          PetProfileEngineService petProfileEngineService,
                          PetBuildProfileDraftService petBuildProfileDraftService,
                          TacticalTagDraftService tacticalTagDraftService) {
        this.dataService = dataService;
        this.petRepository = petRepository;
        this.aiPetReviewService = aiPetReviewService;
        this.petProfileEngineService = petProfileEngineService;
        this.petBuildProfileDraftService = petBuildProfileDraftService;
        this.tacticalTagDraftService = tacticalTagDraftService;
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
        Map<Integer, String> groupNames = dataService.getEggGroupNameMap();

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
            "SELECT d.id as group_id, d.name_cn, d.description, COUNT(e.pet_id) as pet_count " +
            "FROM pet_egg_group_definitions d " +
            "LEFT JOIN pet_egg_groups e ON d.id = e.group_id " +
            "GROUP BY d.id ORDER BY d.id",
            (rs, rowNum) -> {
                Map<String, Object> map = new HashMap<>();
                map.put("groupId", rs.getInt("group_id"));
                map.put("groupName", rs.getString("name_cn"));
                map.put("description", rs.getString("description"));
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

        Map<Integer, String> groupNames = dataService.getEggGroupNameMap();

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

    @GetMapping("/config/analysis-tags")
    public Map<String, Object> getAnalysisTagConfig() {
        String sql = "SELECT config_value FROM global_config WHERE config_key = 'ANALYSIS_TAG_LIBRARY'";
        List<String> values = jdbcTemplate.query(sql, (rs, rowNum) -> rs.getString("config_value"));
        String defaultJson = "{\"actionTags\":[\"直接伤害\",\"连击\",\"条件增伤\",\"斩杀\",\"优先级\",\"打断\",\"护盾\",\"减伤\",\"锁血\",\"回血\",\"回能\",\"净化\",\"驱散\",\"减益\",\"控制\",\"印记施加\",\"印记消耗\",\"转属性\",\"反击\",\"启动\",\"强化\",\"资源循环\"],\"triggerTags\":[\"对克制目标\",\"对异常目标\",\"对低血目标\",\"对高血目标\",\"先手时\",\"后手时\",\"应对成功后\",\"受击后\",\"登场后\",\"自身有印记\",\"对方有印记\",\"连续使用\",\"满能量时\",\"低血量时\",\"特定系别条件\"],\"payoffTags\":[\"启动\",\"爆发\",\"稳定输出\",\"收割\",\"续航\",\"抗压\",\"节奏压制\",\"逼换\",\"反打\",\"资源获取\",\"强势滚雪球\"],\"targetTags\":[\"自身\",\"敌方单体\",\"敌方全体\",\"友方\",\"场地\"],\"synergyTags\":[\"多属性打击面\",\"异常联动\",\"印记联动\",\"应对联动\",\"血脉联动\",\"资源循环\",\"中盘压制\"],\"riskTags\":[\"启动慢\",\"怕控场\",\"怕集火\",\"缺续航\",\"技能位紧张\",\"依赖对位\",\"依赖血脉\"],\"roleTags\":[\"主C\",\"副C\",\"坦克\",\"节奏手\",\"控场\",\"反制位\",\"先发压制\",\"中转枢纽\",\"对策卡\"],\"relatedTags\":[\"优先级\",\"速度压制\",\"节奏压制\",\"条件增伤\",\"爆发\",\"滚雪球\",\"回能\",\"印记联动\"]}";

        Map<String, Object> res = new HashMap<>();
        res.put("config", values.isEmpty() ? defaultJson : values.get(0));
        return res;
    }

    @PostMapping("/config/analysis-tags")
    public Map<String, Object> updateAnalysisTagConfig(@RequestBody Map<String, Object> body) {
        Object config = body.get("config");
        String configText = config == null ? "{}" : config.toString();
        jdbcTemplate.update("INSERT OR REPLACE INTO global_config (config_key, config_value) VALUES ('ANALYSIS_TAG_LIBRARY', ?)", configText);

        Map<String, Object> res = new HashMap<>();
        res.put("success", true);
        res.put("config", configText);
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

    @GetMapping("/analysis/skill-tactical-tags")
    public Map<String, Object> getSkillTacticalTags(
            @RequestParam(required = false, defaultValue = "") String keyword,
            @RequestParam(required = false, defaultValue = "0") int page,
            @RequestParam(required = false, defaultValue = "50") int size) {
        List<Map<String, Object>> rows = jdbcTemplate.query(
            "SELECT st.skill_id, sc.name as skill_name, st.major_category, st.source, st.confidence, st.updated_at " +
            "FROM skill_tactical_tags st " +
            "LEFT JOIN skill_conf_main sc ON st.skill_id = sc.id " +
            "WHERE CAST(st.skill_id AS TEXT) LIKE ? OR IFNULL(sc.name, '') LIKE ? " +
            "ORDER BY st.skill_id ASC LIMIT ? OFFSET ?",
            (rs, rowNum) -> {
                Map<String, Object> map = new HashMap<>();
                map.put("skillId", rs.getInt("skill_id"));
                map.put("skillName", rs.getString("skill_name"));
                map.put("majorCategory", rs.getString("major_category"));
                map.put("source", rs.getString("source"));
                map.put("confidence", rs.getObject("confidence"));
                map.put("updatedAt", rs.getString("updated_at"));
                return map;
            },
            "%" + keyword + "%", "%" + keyword + "%", size, page * size
        );
        Map<String, Object> res = new HashMap<>();
        res.put("items", rows);
        res.put("page", page);
        res.put("size", size);
        return res;
    }

    @GetMapping("/analysis/skill-tactical-tags/{id}/details")
    public Map<String, Object> getSkillTacticalTagDetails(@PathVariable Integer id) {
        List<Map<String, Object>> rows = jdbcTemplate.query(
            "SELECT st.*, sc.name as skill_name, sc.desc as skill_desc FROM skill_tactical_tags st " +
            "LEFT JOIN skill_conf_main sc ON st.skill_id = sc.id WHERE st.skill_id = ?",
            (rs, rowNum) -> {
                Map<String, Object> map = new HashMap<>();
                map.put("skillId", rs.getInt("skill_id"));
                map.put("skillName", rs.getString("skill_name"));
                map.put("skillDesc", rs.getString("skill_desc"));
                map.put("majorCategory", rs.getString("major_category"));
                map.put("actionTags", rs.getString("action_tags"));
                map.put("triggerTags", rs.getString("trigger_tags"));
                map.put("payoffTags", rs.getString("payoff_tags"));
                map.put("targetTags", rs.getString("target_tags"));
                map.put("synergyTags", rs.getString("synergy_tags"));
                map.put("riskTags", rs.getString("risk_tags"));
                map.put("manualScoreAttack", rs.getObject("manual_score_attack"));
                map.put("manualScoreDefense", rs.getObject("manual_score_defense"));
                map.put("manualScoreUtility", rs.getObject("manual_score_utility"));
                map.put("confidence", rs.getObject("confidence"));
                map.put("source", rs.getString("source"));
                map.put("updatedAt", rs.getString("updated_at"));
                return map;
            }, id
        );
        return rows.isEmpty() ? Map.of("skillId", id) : rows.get(0);
    }

    @PostMapping("/analysis/skill-tactical-tags/{id}/save")
    public Map<String, Object> saveSkillTacticalTag(@PathVariable Integer id, @RequestBody Map<String, Object> body) {
        jdbcTemplate.update(
            "INSERT OR REPLACE INTO skill_tactical_tags (skill_id, major_category, action_tags, trigger_tags, payoff_tags, target_tags, synergy_tags, risk_tags, manual_score_attack, manual_score_defense, manual_score_utility, confidence, source, updated_at) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, datetime('now', 'localtime'))",
            id,
            body.get("majorCategory"),
            body.get("actionTags"),
            body.get("triggerTags"),
            body.get("payoffTags"),
            body.get("targetTags"),
            body.get("synergyTags"),
            body.get("riskTags"),
            body.get("manualScoreAttack"),
            body.get("manualScoreDefense"),
            body.get("manualScoreUtility"),
            body.get("confidence"),
            body.get("source")
        );
        return Map.of("success", true);
    }

    @PostMapping("/analysis/skill-tactical-tags/{id}/delete")
    public Map<String, Object> deleteSkillTacticalTag(@PathVariable Integer id) {
        jdbcTemplate.update("DELETE FROM skill_tactical_tags WHERE skill_id = ?", id);
        return Map.of("success", true);
    }

    @PostMapping("/analysis/skill-tactical-tags/{id}/suggest")
    public Map<String, Object> suggestSkillTacticalTag(@PathVariable Integer id) {
        Map<String, Object> draft = tacticalTagDraftService.suggestSkillDraft(id);
        return Map.of("success", true, "draft", draft);
    }

    @GetMapping("/analysis/skill-tactical-tags/generate-candidates")
    public List<Map<String, Object>> getSkillTagGenerateCandidates() {
        return tacticalTagDraftService.getSkillGenerateCandidates();
    }

    @PostMapping("/analysis/skill-tactical-tags/generate-batch")
    public Map<String, Object> generateSkillTagsBatch() {
        if (isSkillTagRunning.get()) {
            return Map.of("success", false, "message", "技能标签任务已在运行中。");
        }
        List<Map<String, Object>> candidates = tacticalTagDraftService.getSkillGenerateCandidates().stream()
            .filter(item -> ((Number) item.get("has_tag")).intValue() == 0)
            .collect(Collectors.toList());
        isSkillTagRunning.set(true);
        processedSkillTagCount.set(0);
        totalSkillTagsToProcess = candidates.size();

        new Thread(() -> {
            log.info("🚀 开始批量生成技能标签，共 {} 条...", totalSkillTagsToProcess);
            ExecutorService executor = Executors.newFixedThreadPool(4);
            for (Map<String, Object> candidate : candidates) {
                Integer id = ((Number) candidate.get("id")).intValue();
                executor.submit(() -> {
                    try {
                        tacticalTagDraftService.generateSkillTag(id);
                        processedSkillTagCount.incrementAndGet();
                    } catch (Exception e) {
                        log.error("❌ 技能标签生成异常 [#{}]: {}", id, e.getMessage());
                    }
                });
            }
            executor.shutdown();
            try {
                executor.awaitTermination(60, TimeUnit.MINUTES);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                isSkillTagRunning.set(false);
            }
        }).start();

        return Map.of("success", true, "message", "技能标签批量生成任务已启动。");
    }

    @GetMapping("/analysis/skill-tactical-tags/generate-status")
    public Map<String, Object> getSkillTagGenerateStatus() {
        int processed = processedSkillTagCount.get();
        double percent = totalSkillTagsToProcess > 0 ? (processed * 100.0 / totalSkillTagsToProcess) : 0;
        return Map.of(
            "running", isSkillTagRunning.get(),
            "processed", processed,
            "total", totalSkillTagsToProcess,
            "percent", Math.min(100.0, percent)
        );
    }

    @GetMapping("/analysis/feature-tactical-tags")
    public Map<String, Object> getFeatureTacticalTags(
            @RequestParam(required = false, defaultValue = "") String keyword,
            @RequestParam(required = false, defaultValue = "0") int page,
            @RequestParam(required = false, defaultValue = "50") int size) {
        List<Map<String, Object>> rows = jdbcTemplate.query(
            "SELECT ft.feature_id, sc.name as feature_name, ft.trigger_mode, ft.value_axis, ft.source, ft.updated_at " +
            "FROM feature_tactical_tags ft " +
            "LEFT JOIN skill_conf_main sc ON ft.feature_id = sc.id " +
            "WHERE CAST(ft.feature_id AS TEXT) LIKE ? OR IFNULL(sc.name, '') LIKE ? " +
            "ORDER BY ft.feature_id ASC LIMIT ? OFFSET ?",
            (rs, rowNum) -> {
                Map<String, Object> map = new HashMap<>();
                map.put("featureId", rs.getInt("feature_id"));
                map.put("featureName", rs.getString("feature_name"));
                map.put("triggerMode", rs.getString("trigger_mode"));
                map.put("valueAxis", rs.getString("value_axis"));
                map.put("source", rs.getString("source"));
                map.put("updatedAt", rs.getString("updated_at"));
                return map;
            },
            "%" + keyword + "%", "%" + keyword + "%", size, page * size
        );
        Map<String, Object> res = new HashMap<>();
        res.put("items", rows);
        res.put("page", page);
        res.put("size", size);
        return res;
    }

    @GetMapping("/analysis/feature-tactical-tags/{id}/details")
    public Map<String, Object> getFeatureTacticalTagDetails(@PathVariable Integer id) {
        List<Map<String, Object>> rows = jdbcTemplate.query(
            "SELECT ft.*, sc.name as feature_name, sc.desc as feature_desc FROM feature_tactical_tags ft " +
            "LEFT JOIN skill_conf_main sc ON ft.feature_id = sc.id WHERE ft.feature_id = ?",
            (rs, rowNum) -> {
                Map<String, Object> map = new HashMap<>();
                map.put("featureId", rs.getInt("feature_id"));
                map.put("featureName", rs.getString("feature_name"));
                map.put("featureDesc", rs.getString("feature_desc"));
                map.put("triggerMode", rs.getString("trigger_mode"));
                map.put("valueAxis", rs.getString("value_axis"));
                map.put("triggerTags", rs.getString("trigger_tags"));
                map.put("payoffTags", rs.getString("payoff_tags"));
                map.put("synergyTags", rs.getString("synergy_tags"));
                map.put("floorBoost", rs.getObject("floor_boost"));
                map.put("ceilingBoost", rs.getObject("ceiling_boost"));
                map.put("notes", rs.getString("notes"));
                map.put("source", rs.getString("source"));
                map.put("updatedAt", rs.getString("updated_at"));
                return map;
            }, id
        );
        return rows.isEmpty() ? Map.of("featureId", id) : rows.get(0);
    }

    @PostMapping("/analysis/feature-tactical-tags/{id}/save")
    public Map<String, Object> saveFeatureTacticalTag(@PathVariable Integer id, @RequestBody Map<String, Object> body) {
        jdbcTemplate.update(
            "INSERT OR REPLACE INTO feature_tactical_tags (feature_id, trigger_mode, value_axis, trigger_tags, payoff_tags, synergy_tags, floor_boost, ceiling_boost, notes, source, updated_at) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, datetime('now', 'localtime'))",
            id,
            body.get("triggerMode"),
            body.get("valueAxis"),
            body.get("triggerTags"),
            body.get("payoffTags"),
            body.get("synergyTags"),
            body.get("floorBoost"),
            body.get("ceilingBoost"),
            body.get("notes"),
            body.get("source")
        );
        return Map.of("success", true);
    }

    @PostMapping("/analysis/feature-tactical-tags/{id}/delete")
    public Map<String, Object> deleteFeatureTacticalTag(@PathVariable Integer id) {
        jdbcTemplate.update("DELETE FROM feature_tactical_tags WHERE feature_id = ?", id);
        return Map.of("success", true);
    }

    @PostMapping("/analysis/feature-tactical-tags/{id}/suggest")
    public Map<String, Object> suggestFeatureTacticalTag(@PathVariable Integer id) {
        Map<String, Object> draft = tacticalTagDraftService.suggestFeatureDraft(id);
        return Map.of("success", true, "draft", draft);
    }

    @GetMapping("/analysis/feature-tactical-tags/generate-candidates")
    public List<Map<String, Object>> getFeatureTagGenerateCandidates() {
        return tacticalTagDraftService.getFeatureGenerateCandidates();
    }

    @PostMapping("/analysis/feature-tactical-tags/generate-batch")
    public Map<String, Object> generateFeatureTagsBatch() {
        if (isFeatureTagRunning.get()) {
            return Map.of("success", false, "message", "特性标签任务已在运行中。");
        }
        List<Map<String, Object>> candidates = tacticalTagDraftService.getFeatureGenerateCandidates().stream()
            .filter(item -> ((Number) item.get("has_tag")).intValue() == 0)
            .collect(Collectors.toList());
        isFeatureTagRunning.set(true);
        processedFeatureTagCount.set(0);
        totalFeatureTagsToProcess = candidates.size();

        new Thread(() -> {
            log.info("🚀 开始批量生成特性标签，共 {} 条...", totalFeatureTagsToProcess);
            ExecutorService executor = Executors.newFixedThreadPool(4);
            for (Map<String, Object> candidate : candidates) {
                Integer id = ((Number) candidate.get("id")).intValue();
                executor.submit(() -> {
                    try {
                        tacticalTagDraftService.generateFeatureTag(id);
                        processedFeatureTagCount.incrementAndGet();
                    } catch (Exception e) {
                        log.error("❌ 特性标签生成异常 [#{}]: {}", id, e.getMessage());
                    }
                });
            }
            executor.shutdown();
            try {
                executor.awaitTermination(60, TimeUnit.MINUTES);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                isFeatureTagRunning.set(false);
            }
        }).start();

        return Map.of("success", true, "message", "特性标签批量生成任务已启动。");
    }

    @GetMapping("/analysis/feature-tactical-tags/generate-status")
    public Map<String, Object> getFeatureTagGenerateStatus() {
        int processed = processedFeatureTagCount.get();
        double percent = totalFeatureTagsToProcess > 0 ? (processed * 100.0 / totalFeatureTagsToProcess) : 0;
        return Map.of(
            "running", isFeatureTagRunning.get(),
            "processed", processed,
            "total", totalFeatureTagsToProcess,
            "percent", Math.min(100.0, percent)
        );
    }

    @GetMapping("/analysis/pet-build-profiles")
    public Map<String, Object> getPetBuildProfiles(
            @RequestParam(required = false, defaultValue = "") String keyword,
            @RequestParam(required = false) Integer petId,
            @RequestParam(required = false, defaultValue = "0") int page,
            @RequestParam(required = false, defaultValue = "50") int size) {
        StringBuilder sql = new StringBuilder(
            "SELECT pb.id, pb.pet_id, p.name as pet_name, pb.build_name, pb.build_type, pb.priority, pb.source, pb.updated_at " +
            "FROM pet_build_profiles pb LEFT JOIN pets p ON pb.pet_id = p.id WHERE 1=1"
        );
        List<Object> params = new ArrayList<>();
        if (keyword != null && !keyword.isBlank()) {
            sql.append(" AND (CAST(pb.pet_id AS TEXT) LIKE ? OR IFNULL(p.name, '') LIKE ? OR IFNULL(pb.build_name, '') LIKE ?)");
            params.add("%" + keyword + "%");
            params.add("%" + keyword + "%");
            params.add("%" + keyword + "%");
        }
        if (petId != null) {
            sql.append(" AND pb.pet_id = ?");
            params.add(petId);
        }
        sql.append(" ORDER BY pb.priority DESC, pb.id ASC LIMIT ? OFFSET ?");
        params.add(size);
        params.add(page * size);

        List<Map<String, Object>> rows = jdbcTemplate.query(sql.toString(), (rs, rowNum) -> {
            Map<String, Object> map = new HashMap<>();
            map.put("id", rs.getInt("id"));
            map.put("petId", rs.getInt("pet_id"));
            map.put("petName", rs.getString("pet_name"));
            map.put("buildName", rs.getString("build_name"));
            map.put("buildType", rs.getString("build_type"));
            map.put("priority", rs.getObject("priority"));
            map.put("source", rs.getString("source"));
            map.put("updatedAt", rs.getString("updated_at"));
            return map;
        }, params.toArray());

        Map<String, Object> res = new HashMap<>();
        res.put("items", rows);
        res.put("page", page);
        res.put("size", size);
        return res;
    }

    @GetMapping("/analysis/pet-build-profiles/{id}/details")
    public Map<String, Object> getPetBuildProfileDetails(@PathVariable Integer id) {
        List<Map<String, Object>> rows = jdbcTemplate.query(
            "SELECT pb.*, p.name as pet_name FROM pet_build_profiles pb LEFT JOIN pets p ON pb.pet_id = p.id WHERE pb.id = ?",
            (rs, rowNum) -> {
                Map<String, Object> map = new HashMap<>();
                map.put("id", rs.getInt("id"));
                map.put("petId", rs.getInt("pet_id"));
                map.put("petName", rs.getString("pet_name"));
                map.put("buildName", rs.getString("build_name"));
                map.put("buildType", rs.getString("build_type"));
                map.put("coreSkillIds", rs.getString("core_skill_ids"));
                map.put("optionalSkillIds", rs.getString("optional_skill_ids"));
                map.put("recommendedSkillSet", rs.getString("recommended_skill_set"));
                map.put("bloodlineOptions", rs.getString("bloodline_options"));
                map.put("natureOptions", rs.getString("nature_options"));
                map.put("talentOptions", rs.getString("talent_options"));
                map.put("roleTags", rs.getString("role_tags"));
                map.put("playstyleSummary", rs.getString("playstyle_summary"));
                map.put("strengthNotes", rs.getString("strength_notes"));
                map.put("weaknessNotes", rs.getString("weakness_notes"));
                map.put("environmentNotes", rs.getString("environment_notes"));
                map.put("source", rs.getString("source"));
                map.put("priority", rs.getObject("priority"));
                map.put("updatedAt", rs.getString("updated_at"));
                return map;
            }, id
        );
        return rows.isEmpty() ? Map.of("id", id) : rows.get(0);
    }

    @PostMapping("/analysis/pet-build-profiles/save")
    public Map<String, Object> savePetBuildProfile(@RequestBody Map<String, Object> body) {
        Object id = body.get("id");
        if (id == null || id.toString().isBlank()) {
            jdbcTemplate.update(
                "INSERT INTO pet_build_profiles (pet_id, build_name, build_type, core_skill_ids, optional_skill_ids, recommended_skill_set, bloodline_options, nature_options, talent_options, role_tags, playstyle_summary, strength_notes, weakness_notes, environment_notes, source, priority, updated_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, datetime('now', 'localtime'))",
                body.get("petId"),
                body.get("buildName"),
                body.get("buildType"),
                body.get("coreSkillIds"),
                body.get("optionalSkillIds"),
                body.get("recommendedSkillSet"),
                body.get("bloodlineOptions"),
                body.get("natureOptions"),
                body.get("talentOptions"),
                body.get("roleTags"),
                body.get("playstyleSummary"),
                body.get("strengthNotes"),
                body.get("weaknessNotes"),
                body.get("environmentNotes"),
                body.get("source"),
                body.get("priority")
            );
        } else {
            jdbcTemplate.update(
                "UPDATE pet_build_profiles SET pet_id = ?, build_name = ?, build_type = ?, core_skill_ids = ?, optional_skill_ids = ?, recommended_skill_set = ?, bloodline_options = ?, nature_options = ?, talent_options = ?, role_tags = ?, playstyle_summary = ?, strength_notes = ?, weakness_notes = ?, environment_notes = ?, source = ?, priority = ?, updated_at = datetime('now', 'localtime') WHERE id = ?",
                body.get("petId"),
                body.get("buildName"),
                body.get("buildType"),
                body.get("coreSkillIds"),
                body.get("optionalSkillIds"),
                body.get("recommendedSkillSet"),
                body.get("bloodlineOptions"),
                body.get("natureOptions"),
                body.get("talentOptions"),
                body.get("roleTags"),
                body.get("playstyleSummary"),
                body.get("strengthNotes"),
                body.get("weaknessNotes"),
                body.get("environmentNotes"),
                body.get("source"),
                body.get("priority"),
                id
            );
        }
        return Map.of("success", true);
    }

    @PostMapping("/analysis/pet-build-profiles/{id}/delete")
    public Map<String, Object> deletePetBuildProfile(@PathVariable Integer id) {
        jdbcTemplate.update("DELETE FROM pet_build_profiles WHERE id = ?", id);
        return Map.of("success", true);
    }

    @PostMapping("/analysis/pet-build-profiles/{petId}/generate")
    public Map<String, Object> generatePetBuildProfile(@PathVariable Integer petId) {
        Map<String, Object> generated = petBuildProfileDraftService.generateDraft(petId);
        Map<String, Object> res = new HashMap<>();
        res.put("success", true);
        res.put("build", generated);
        return res;
    }

    @GetMapping("/analysis/pet-build-profiles/generate-candidates")
    public List<Map<String, Object>> getPetBuildProfileGenerateCandidates() {
        return petBuildProfileDraftService.getGenerateCandidates();
    }

    @PostMapping("/analysis/pet-build-profiles/generate-batch")
    public Map<String, Object> generatePetBuildProfilesBatch() {
        if (isBuildDraftRunning.get()) {
            return Map.of("success", false, "message", "构筑草稿任务已在运行中。");
        }

        List<Map<String, Object>> candidates = petBuildProfileDraftService.getGenerateCandidates();
        isBuildDraftRunning.set(true);
        processedBuildDraftCount.set(0);
        totalBuildDraftsToProcess = candidates.size();

        new Thread(() -> {
            log.info("🚀 开始批量生成构筑草稿，共 {} 只...", totalBuildDraftsToProcess);
            ExecutorService executor = Executors.newFixedThreadPool(4);
            for (Map<String, Object> candidate : candidates) {
                Integer id = ((Number) candidate.get("id")).intValue();
                executor.submit(() -> {
                    try {
                        petBuildProfileDraftService.generateDraft(id);
                        processedBuildDraftCount.incrementAndGet();
                    } catch (Exception e) {
                        log.error("❌ 构筑草稿生成异常 [#{}]: {}", id, e.getMessage());
                    }
                });
            }
            executor.shutdown();
            try {
                executor.awaitTermination(60, TimeUnit.MINUTES);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                isBuildDraftRunning.set(false);
            }
        }).start();

        return Map.of("success", true, "message", "构筑草稿批量生成任务已启动。");
    }

    @GetMapping("/analysis/pet-build-profiles/generate-status")
    public Map<String, Object> getPetBuildProfileGenerateStatus() {
        int processed = processedBuildDraftCount.get();
        double percent = totalBuildDraftsToProcess > 0 ? (processed * 100.0 / totalBuildDraftsToProcess) : 0;
        return Map.of(
            "running", isBuildDraftRunning.get(),
            "processed", processed,
            "total", totalBuildDraftsToProcess,
            "percent", Math.min(100.0, percent)
        );
    }

    @GetMapping("/analysis/pet-tactical-profiles")
    public Map<String, Object> getPetTacticalProfiles(
            @RequestParam(required = false, defaultValue = "") String keyword,
            @RequestParam(required = false, defaultValue = "0") int page,
            @RequestParam(required = false, defaultValue = "50") int size) {
        List<Map<String, Object>> rows = jdbcTemplate.query(
            "SELECT pt.pet_id, p.name as pet_name, pt.version, pt.role_summary, pt.generated_by, pt.updated_at " +
            "FROM pet_tactical_profiles pt LEFT JOIN pets p ON pt.pet_id = p.id " +
            "WHERE CAST(pt.pet_id AS TEXT) LIKE ? OR IFNULL(p.name, '') LIKE ? " +
            "ORDER BY pt.pet_id ASC LIMIT ? OFFSET ?",
            (rs, rowNum) -> {
                Map<String, Object> map = new HashMap<>();
                map.put("petId", rs.getInt("pet_id"));
                map.put("petName", rs.getString("pet_name"));
                map.put("version", rs.getString("version"));
                map.put("roleSummary", rs.getString("role_summary"));
                map.put("generatedBy", rs.getString("generated_by"));
                map.put("updatedAt", rs.getString("updated_at"));
                return map;
            },
            "%" + keyword + "%", "%" + keyword + "%", size, page * size
        );
        Map<String, Object> res = new HashMap<>();
        res.put("items", rows);
        res.put("page", page);
        res.put("size", size);
        return res;
    }

    @GetMapping("/analysis/pet-tactical-profiles/{id}/details")
    public Map<String, Object> getPetTacticalProfileDetails(@PathVariable Integer id) {
        List<Map<String, Object>> rows = jdbcTemplate.query(
            "SELECT pt.*, p.name as pet_name FROM pet_tactical_profiles pt LEFT JOIN pets p ON pt.pet_id = p.id WHERE pt.pet_id = ?",
            (rs, rowNum) -> {
                Map<String, Object> map = new HashMap<>();
                map.put("petId", rs.getInt("pet_id"));
                map.put("petName", rs.getString("pet_name"));
                map.put("version", rs.getString("version"));
                map.put("profileJson", rs.getString("profile_json"));
                map.put("roleSummary", rs.getString("role_summary"));
                map.put("offenseScore", rs.getObject("offense_score"));
                map.put("defenseScore", rs.getObject("defense_score"));
                map.put("speedScore", rs.getObject("speed_score"));
                map.put("utilityScore", rs.getObject("utility_score"));
                map.put("synergyScore", rs.getObject("synergy_score"));
                map.put("flexibilityScore", rs.getObject("flexibility_score"));
                map.put("ceilingScore", rs.getObject("ceiling_score"));
                map.put("floorScore", rs.getObject("floor_score"));
                map.put("metaFitScore", rs.getObject("meta_fit_score"));
                map.put("strengths", rs.getString("strengths"));
                map.put("weaknesses", rs.getString("weaknesses"));
                map.put("buildDependencies", rs.getString("build_dependencies"));
                map.put("generatedBy", rs.getString("generated_by"));
                map.put("updatedAt", rs.getString("updated_at"));
                return map;
            }, id
        );
        return rows.isEmpty() ? Map.of("petId", id) : rows.get(0);
    }

    @PostMapping("/analysis/pet-tactical-profiles/{id}/save")
    public Map<String, Object> savePetTacticalProfile(@PathVariable Integer id, @RequestBody Map<String, Object> body) {
        jdbcTemplate.update(
            "INSERT OR REPLACE INTO pet_tactical_profiles (pet_id, version, profile_json, role_summary, offense_score, defense_score, speed_score, utility_score, synergy_score, flexibility_score, ceiling_score, floor_score, meta_fit_score, strengths, weaknesses, build_dependencies, generated_by, updated_at) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, datetime('now', 'localtime'))",
            id,
            body.get("version"),
            body.get("profileJson"),
            body.get("roleSummary"),
            body.get("offenseScore"),
            body.get("defenseScore"),
            body.get("speedScore"),
            body.get("utilityScore"),
            body.get("synergyScore"),
            body.get("flexibilityScore"),
            body.get("ceilingScore"),
            body.get("floorScore"),
            body.get("metaFitScore"),
            body.get("strengths"),
            body.get("weaknesses"),
            body.get("buildDependencies"),
            body.get("generatedBy")
        );
        return Map.of("success", true);
    }

    @PostMapping("/analysis/pet-tactical-profiles/{id}/generate")
    public Map<String, Object> generatePetTacticalProfile(@PathVariable Integer id) {
        Map<String, Object> generated = petProfileEngineService.generateProfile(id);
        Map<String, Object> res = new HashMap<>();
        res.put("success", true);
        res.put("profile", generated);
        return res;
    }

    @GetMapping("/analysis/pet-tactical-profiles/generate-candidates")
    public List<Map<String, Object>> getPetTacticalProfileGenerateCandidates() {
        return petProfileEngineService.getGenerateCandidates();
    }

    @PostMapping("/analysis/pet-tactical-profiles/generate-batch")
    public Map<String, Object> generatePetTacticalProfilesBatch() {
        if (isPetProfileRunning.get()) {
            return Map.of("success", false, "message", "精灵画像任务已在运行中。");
        }

        List<Map<String, Object>> candidates = petProfileEngineService.getGenerateCandidates();
        isPetProfileRunning.set(true);
        processedPetProfileCount.set(0);
        totalPetProfilesToProcess = candidates.size();

        new Thread(() -> {
            log.info("🚀 开始批量生成精灵画像，共 {} 只...", totalPetProfilesToProcess);
            ExecutorService executor = Executors.newFixedThreadPool(4);
            for (Map<String, Object> candidate : candidates) {
                Integer id = ((Number) candidate.get("id")).intValue();
                executor.submit(() -> {
                    try {
                        petProfileEngineService.generateProfile(id);
                        processedPetProfileCount.incrementAndGet();
                    } catch (Exception e) {
                        log.error("❌ 精灵画像生成异常 [#{}]: {}", id, e.getMessage());
                    }
                });
            }
            executor.shutdown();
            try {
                executor.awaitTermination(60, TimeUnit.MINUTES);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                isPetProfileRunning.set(false);
            }
        }).start();

        return Map.of("success", true, "message", "精灵画像批量生成任务已启动。");
    }

    @GetMapping("/analysis/pet-tactical-profiles/generate-status")
    public Map<String, Object> getPetTacticalProfileGenerateStatus() {
        int processed = processedPetProfileCount.get();
        double percent = totalPetProfilesToProcess > 0 ? (processed * 100.0 / totalPetProfilesToProcess) : 0;
        return Map.of(
            "running", isPetProfileRunning.get(),
            "processed", processed,
            "total", totalPetProfilesToProcess,
            "percent", Math.min(100.0, percent)
        );
    }

    @PostMapping("/analysis/pet-tactical-profiles/{id}/delete")
    public Map<String, Object> deletePetTacticalProfile(@PathVariable Integer id) {
        jdbcTemplate.update("DELETE FROM pet_tactical_profiles WHERE pet_id = ?", id);
        return Map.of("success", true);
    }

    @GetMapping("/analysis/mechanic-glossary")
    public Map<String, Object> getMechanicGlossary(
            @RequestParam(required = false, defaultValue = "") String keyword,
            @RequestParam(required = false, defaultValue = "0") int page,
            @RequestParam(required = false, defaultValue = "50") int size) {
        List<Map<String, Object>> rows = jdbcTemplate.query(
            "SELECT term, category, tactical_meaning, updated_at FROM mechanic_glossary " +
            "WHERE term LIKE ? OR IFNULL(category, '') LIKE ? OR IFNULL(tactical_meaning, '') LIKE ? " +
            "ORDER BY term ASC LIMIT ? OFFSET ?",
            (rs, rowNum) -> {
                Map<String, Object> map = new HashMap<>();
                map.put("term", rs.getString("term"));
                map.put("category", rs.getString("category"));
                map.put("tacticalMeaning", rs.getString("tactical_meaning"));
                map.put("updatedAt", rs.getString("updated_at"));
                return map;
            },
            "%" + keyword + "%", "%" + keyword + "%", "%" + keyword + "%", size, page * size
        );
        Map<String, Object> res = new HashMap<>();
        res.put("items", rows);
        res.put("page", page);
        res.put("size", size);
        return res;
    }

    @GetMapping("/analysis/mechanic-glossary/{term}/details")
    public Map<String, Object> getMechanicGlossaryDetails(@PathVariable String term) {
        List<Map<String, Object>> rows = jdbcTemplate.query(
            "SELECT * FROM mechanic_glossary WHERE term = ?",
            (rs, rowNum) -> {
                Map<String, Object> map = new HashMap<>();
                map.put("term", rs.getString("term"));
                map.put("category", rs.getString("category"));
                map.put("formalDefinition", rs.getString("formal_definition"));
                map.put("tacticalMeaning", rs.getString("tactical_meaning"));
                map.put("parsingHint", rs.getString("parsing_hint"));
                map.put("relatedTags", rs.getString("related_tags"));
                map.put("updatedAt", rs.getString("updated_at"));
                return map;
            }, term
        );
        return rows.isEmpty() ? Map.of("term", term) : rows.get(0);
    }

    @PostMapping("/analysis/mechanic-glossary/{term}/save")
    public Map<String, Object> saveMechanicGlossary(@PathVariable String term, @RequestBody Map<String, Object> body) {
        jdbcTemplate.update(
            "INSERT OR REPLACE INTO mechanic_glossary (term, category, formal_definition, tactical_meaning, parsing_hint, related_tags, updated_at) " +
            "VALUES (?, ?, ?, ?, ?, ?, datetime('now', 'localtime'))",
            term,
            body.get("category"),
            body.get("formalDefinition"),
            body.get("tacticalMeaning"),
            body.get("parsingHint"),
            body.get("relatedTags")
        );
        return Map.of("success", true);
    }

    @PostMapping("/analysis/mechanic-glossary/seed-defaults")
    public Map<String, Object> seedMechanicGlossaryDefaults() {
        List<Map<String, String>> seeds = List.of(
            Map.of("term", "迸发", "category", "机制", "formalDefinition", "在特定条件下触发额外收益或追加伤害。", "tacticalMeaning", "偏上限型收益，常用于爆发与滚雪球。", "parsingHint", "看到追加收益、额外伤害、满足条件后强化时优先考虑。", "relatedTags", "[\"条件增伤\", \"爆发\", \"滚雪球\"]"),
            Map.of("term", "迅捷", "category", "机制", "formalDefinition", "赋予先手节奏或速度相关收益。", "tacticalMeaning", "提升抢节奏能力，提高先手压制与收割稳定性。", "parsingHint", "看到先手、速度提升、出手顺序调整时优先考虑。", "relatedTags", "[\"优先级\", \"速度压制\", \"节奏压制\"]"),
            Map.of("term", "印记", "category", "状态", "formalDefinition", "在自身或敌方身上叠加的可被后续技能利用的状态标记。", "tacticalMeaning", "常用于连段、资源循环与中盘压制。", "parsingHint", "看到叠层、标记、消耗层数换收益时优先考虑。", "relatedTags", "[\"印记联动\", \"资源循环\"]")
        );
        int inserted = 0;
        for (Map<String, String> seed : seeds) {
            Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM mechanic_glossary WHERE term = ?", Integer.class, seed.get("term"));
            if (count != null && count > 0) continue;
            jdbcTemplate.update(
                "INSERT INTO mechanic_glossary (term, category, formal_definition, tactical_meaning, parsing_hint, related_tags, updated_at) VALUES (?, ?, ?, ?, ?, ?, datetime('now', 'localtime'))",
                seed.get("term"), seed.get("category"), seed.get("formalDefinition"), seed.get("tacticalMeaning"), seed.get("parsingHint"), seed.get("relatedTags")
            );
            inserted++;
        }
        return Map.of("success", true, "inserted", inserted);
    }

    @PostMapping("/analysis/mechanic-glossary/{term}/delete")
    public Map<String, Object> deleteMechanicGlossary(@PathVariable String term) {
        jdbcTemplate.update("DELETE FROM mechanic_glossary WHERE term = ?", term);
        return Map.of("success", true);
    }
}
