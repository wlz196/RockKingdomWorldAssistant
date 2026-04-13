// 属性类型常量
const types = [
    { id: 2, name: "普通", color: "#CEF7FFFF" },
    { id: 3, name: "草", color: "#70ECA0FF" },
    { id: 4, name: "火", color: "#FF7B7AFF" },
    { id: 5, name: "水", color: "#51BCFFFF" },
    { id: 6, name: "光", color: "#FFE071FF" },
    { id: 8, name: "地", color: "#FFBF7CFF" },
    { id: 9, name: "冰", color: "#7dd3fc" },
    { id: 10, name: "龙", color: "#8b5cf6" },
    { id: 11, name: "电", color: "#facc15" },
    { id: 12, name: "毒", color: "#a855f7" },
    { id: 13, name: "虫", color: "#D0FF7BFF" },
    { id: 14, name: "武", color: "#dc2626" },
    { id: 15, name: "翼", color: "#79FFE3FF" },
    { id: 16, name: "萌", color: "#f472b6" },
    { id: 17, name: "幽", color: "#7C80FFFF" },
    { id: 18, name: "恶", color: "#AA57FFFF" },
    { id: 19, name: "机械", color: "#94a3b8" },
    { id: 20, name: "幻", color: "#ec4899" }
];

// 属性颜色查找（按名称）
function getTypeColorByName(name) {
    if (!name) return '#94a3b8';
    const t = types.find(t => t.name === name.replace('系', ''));
    return t ? t.color : '#94a3b8';
}

// 属性颜色查找（按ID）
function getTypeColor(typeId) {
    const t = types.find(t => String(t.id) === String(typeId));
    return t ? t.color : '#ccc';
}

// 属性名称查找（按ID）
function getTypeName(typeId) {
    const t = types.find(t => String(t.id) === String(typeId));
    return t ? t.name : '未知';
}
