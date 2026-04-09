package com.roco.backend.controller;

import com.roco.backend.model.dto.SkillItemDTO;
import com.roco.backend.model.entity.Pet;
import com.roco.backend.repository.PetRepository;
import com.roco.backend.service.PetService;
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
    
    private final PetService petService;
    private final PetRepository petRepository;
    
    @Autowired
    private JdbcTemplate jdbcTemplate;

    public DataController(PetService petService,
                          PetRepository petRepository) {
        this.petService = petService;
        this.petRepository = petRepository;
    }

    @GetMapping("/pets")
    public List<Map<String, Object>> searchPets(
            @RequestParam(required = false, defaultValue = "") String keyword,
            @RequestParam(required = false) String type,
            @RequestParam(required = false, defaultValue = "all") String category,
            @RequestParam(required = false, defaultValue = "0") int page,
            @RequestParam(required = false, defaultValue = "50") int size) {
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
            .sorted((p1, p2) -> {
                boolean hasBookId1 = p1.getBookId() != null && p1.getBookId() > 0;
                boolean hasBookId2 = p2.getBookId() != null && p2.getBookId() > 0;
                
                if (hasBookId1 && hasBookId2) {
                    return p1.getBookId().compareTo(p2.getBookId());
                } else if (hasBookId1) {
                    return -1; // p1 comes first
                } else if (hasBookId2) {
                    return 1; // p2 comes first
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
                map.put("imageUrl", petService.formatImageUrl(p.getImageUrl(), p.getName()));
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
        return petService.getPetDetails(id);
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
        
        // ... (rest of the pre-fetch logic)
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

        // DIAGNOSTIC LOGGING
        System.out.println("--- SKILL SEARCH DEBUG ---");
        System.out.println("SQL: " + sql.toString());
        System.out.println("Params: " + params);
        System.out.println("--- END DEBUG ---");

        return jdbcTemplate.query(sql.toString(), (rs, rowNum) -> {
            SkillItemDTO dto = new SkillItemDTO();
            dto.setId(rs.getInt("id"));
            dto.setName(rs.getString("name"));
            
            // Map skill icons to physical PNG assets across three subfolders
            String rawIcon = rs.getString("icon");
            dto.setIcon(petService.formatSkillIcon(rawIcon));
            
            dto.setDesc(rs.getString("desc"));
            
            // Use skill_dam_type for Attribute mapping as clarified by user
            dto.setAttribute(typeMap.getOrDefault(rs.getInt("skill_dam_type"), "无别"));
            
            // Refined category display logic based on user mappings:
            // damage_type: 1=None, 2=Phys, 3=Magic, 4=Spec
            // skill_type: 1=Attack, 2=Defense, 3=Status
            // type: 2=Passive
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
                // Parse "[7700001, ...]"
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

    @GetMapping("/types")
    public Map<String, Object> getTypeData() {
        Map<String, Object> result = new HashMap<>();
        result.put("list", jdbcTemplate.queryForList("SELECT * FROM types"));
        result.put("relations", jdbcTemplate.queryForList("SELECT * FROM type_relations"));
        return result;
    }
}
