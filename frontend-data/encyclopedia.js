// ===== 配置和常量已提取到 js/api.js 和 js/constants.js =====

// ===== 全局状态 =====
let currentPage = 0;
let currentType = '';
let currentSearch = '';
let currentSkillSearch = '';
let currentBloodlineSearch = '';
let currentNatureSearch = '';
let currentTalentSearch = '';
let currentCategory = 'all';
let currentHasFeature = null;
let currentHasSkill = null;
let currentDetailData = null;
let currentMainTab = 'petView';
let activeMode = 'pets';
let lastTypeData = null;
let currentSkillPage = 0;
var skillLogicType = '';
var skillAttrId = '';
var skillClassId = '';
var skillDamageId = '';
var skillHasOwner = '';

// ===== 初始化与 Tab 路由 =====
function initMainTabs() {
    const tabs = document.querySelectorAll('.main-tab');
    tabs.forEach(tab => {
        tab.onclick = () => {
            tabs.forEach(t => t.classList.remove('active'));
            tab.classList.add('active');
            
            const target = tab.getAttribute('data-tab');
            document.querySelectorAll('.tab-pane').forEach(p => p.style.display = 'none');
            const pane = document.getElementById(target);
            if (pane) pane.style.display = 'block';
            
            currentMainTab = target;
            if (target === 'petView') {
                activeMode = 'pets';
                loadPets();
            }
            if (target === 'skillView') {
                activeMode = 'skills';
                initSkillFilters();
                loadSkillsGallery();
            }
            if (target === 'bloodlineView') loadBloodlines();
            if (target === 'buffView') loadBuffs();
            if (target === 'natureView') loadNatures();
            if (target === 'talentView') loadTalents();
            if (target === 'typeView') loadTypeMatrix();
            if (target === 'statView') {
                if (currentSimPet) updateSimulator();
            }
            if (target === 'damageView') {
                initDamageCalc();
            }
            if (target === 'dimensionView') {
                // 身高体重查询页无需自动加载
            }
            if (target === 'eggGroupView') {
                loadEggGroupFilters();
            }
        };
    });
}

// ===== 技能筛选器 =====
function initSkillFilters() {
    console.log('Initializing Skill Filters...');
    const renderFilterRow = (containerId, items, stateKey) => {
        const container = document.getElementById(containerId);
        if (!container) {
            console.error(`Filter container not found: ${containerId}`);
            return;
        }
        container.innerHTML = '';
        
        // Add "All" button
        const allBtn = document.createElement('button');
        const currentState = window[stateKey] || '';
        allBtn.className = `type-btn ${currentState === '' ? 'active' : ''}`;
        allBtn.innerHTML = `<span>全部</span>`;
        allBtn.onclick = () => {
            window[stateKey] = '';
            currentSkillPage = 0;
            initSkillFilters();
            loadSkillsGallery();
        };
        container.appendChild(allBtn);

        items.forEach(item => {
            const btn = document.createElement('button');
            const itemId = String(item.id);
            const isActive = currentState === itemId;
            btn.className = `type-btn ${isActive ? 'active' : ''}`;
            
            if (isActive && item.color) {
                btn.style.background = item.color;
                btn.style.color = 'white';
            }

            let content = `<span>${item.name}</span>`;
            if (item.color) {
                content = `<span class="type-dot" style="background: ${item.color}"></span>` + content;
            }
            btn.innerHTML = content;

            btn.onclick = () => {
                window[stateKey] = isActive ? '' : itemId;
                currentSkillPage = 0;
                initSkillFilters();
                loadSkillsGallery();
            };
            container.appendChild(btn);
        });
        console.log(`Rendered ${items.length + 1} items for ${containerId}`);
    };

    // Row 1: Logic
    renderFilterRow('skillLogicFilters', [
        { id: 1, name: '主动' },
        { id: 2, name: '被动/特性' }
    ], 'skillLogicType');

    // Row 2: Attribute
    renderFilterRow('skillAttrFilters', types, 'skillAttrId');

    // Row 3: Class (Nature)
    renderFilterRow('skillClassFilters', [
        { id: 1, name: '攻击型' },
        { id: 2, name: '防御/辅助' },
        { id: 3, name: '变化型' }
    ], 'skillClassId');

    // Row 4: Damage Mode
    renderFilterRow('skillDamageFilters', [
        { id: 2, name: '物理' },
        { id: 3, name: '魔法' },
        { id: 4, name: '特殊' }
    ], 'skillDamageId');

    // Row 5: Has Owner
    renderFilterRow('skillOwnerFilters', [
        { id: 1, name: '有精灵拥有' },
        { id: 0, name: '无精灵拥有' }
    ], 'skillHasOwner');
}

function initSidebar() {
    const typeFilters = document.getElementById('typeFilters');
    const categoryFilters = document.getElementById('categoryFilters');
    
    if (!categoryFilters || !typeFilters) return;

    // Category filters logic
    categoryFilters.querySelectorAll('.cat-btn').forEach(btn => {
        btn.onclick = () => {
            categoryFilters.querySelectorAll('.cat-btn').forEach(b => b.classList.remove('active'));
            btn.classList.add('active');
            currentCategory = btn.getAttribute('data-cat');
            currentPage = 0;
            // 显示/隐藏暂未收录子筛选
            const subFilters = document.getElementById('nonBookSubFilters');
            if (subFilters) {
                subFilters.style.display = currentCategory === 'non-book' ? 'flex' : 'none';
            }
            // 重置子筛选状态
            currentHasFeature = null;
            currentHasSkill = null;
            document.querySelectorAll('.sub-filter-btn').forEach(b => b.classList.remove('active'));
            const allSubBtn = document.querySelector('.sub-filter-btn[data-filter="all"]');
            if (allSubBtn) allSubBtn.classList.add('active');
            loadPets();
        };
    });

    // 暂未收录子筛选按钮
    document.querySelectorAll('.sub-filter-btn').forEach(btn => {
        btn.onclick = () => {
            document.querySelectorAll('.sub-filter-btn').forEach(b => b.classList.remove('active'));
            btn.classList.add('active');
            const filter = btn.getAttribute('data-filter');
            currentHasFeature = null;
            currentHasSkill = null;
            if (filter === 'hasFeature') currentHasFeature = true;
            else if (filter === 'noFeature') currentHasFeature = false;
            else if (filter === 'hasSkill') currentHasSkill = true;
            else if (filter === 'noSkill') currentHasSkill = false;
            currentPage = 0;
            loadPets();
        };
    });
    
    // Fetch total count
    fetch(`${API_BASE}/pets/count`)
        .then(res => res.json())
        .then(count => {
            const countEl = document.getElementById('petCount');
            if (countEl) countEl.innerText = count;
        });

    typeFilters.innerHTML = '';
    types.forEach(t => {
        const btn = document.createElement('button');
        btn.className = 'type-btn';
        btn.innerHTML = `<span>${t.name}</span>`;
        btn.onclick = () => {
            const typeId = String(t.id);
            if (currentType === typeId) {
                currentType = '';
                btn.classList.remove('active');
            } else {
                document.querySelectorAll('.type-btn').forEach(b => b.classList.remove('active'));
                currentType = typeId;
                btn.classList.add('active');
            }
            currentPage = 0;
            loadPets();
        };
        typeFilters.appendChild(btn);
    });

}

function initDetailDrawer() {
    const drawer = document.getElementById('detailDrawer');
    const overlay = document.getElementById('drawerOverlay');
    const closeBtn = document.getElementById('closeDrawer');
    if (!drawer) return;

    const closeDrawer = () => {
        drawer.classList.remove('active');
    };

    if (overlay) overlay.onclick = closeDrawer;
    if (closeBtn) closeBtn.onclick = closeDrawer;

    document.addEventListener('keydown', (e) => {
        if (e.key === 'Escape') closeDrawer();
    });
}

function loadSkillsGallery() {
    const grid = document.getElementById('skillGrid');
    if (!grid) return;
    grid.innerHTML = '<div style="grid-column: 1/-1; text-align:center; padding: 2rem;">加载中...</div>';

    const params = new URLSearchParams({
        keyword: currentSkillSearch,
        skillDamType: skillAttrId,
        filterLogicType: skillLogicType,
        filterSkillCategory: skillClassId,
        filterDamageCategory: skillDamageId,
        page: currentSkillPage,
        size: 20
    });
    if (skillHasOwner === '1') {
        params.set('hasOwner', 'true');
    } else if (skillHasOwner === '0') {
        params.set('hasOwner', 'false');
    }

    const url = `${API_BASE}/skills?${params.toString()}`;
    console.log('Fetching skills with tactical matrix:', url);
    
    fetch(url)
        .then(res => res.json())
        .then(data => {
            renderSkillGalleryCards(data);
        })
        .catch(e => {
            grid.innerHTML = `<div style="grid-column: 1/-1; text-align:center; color:red;">加载失败: ${e.message}</div>`;
        });
}

function renderSkillGalleryCards(skills) {
    const grid = document.getElementById('skillGrid');
    if (!grid) return;
    
    grid.innerHTML = '';
    if (skills.length === 0) {
        grid.innerHTML = '<div style="grid-column: 1/-1; text-align:center; padding: 2rem;">未找到相关技能</div>';
        return;
    }

    skills.forEach(s => {
        const card = document.createElement('div');
        card.className = 'pet-card'; // Reuse pet-card style for consistency
        
        const attrColor = getTypeColorByName(s.attribute);
        
        card.innerHTML = `
            <div class="pet-id">ID: ${s.id}</div>
            <div class="pet-image-container" style="height: 120px;">
                <img src="${MEDIA_BASE}${s.icon || 'skills/' + s.id + '_png.png'}" 
                      alt="${s.name}" 
                      class="pet-image" 
                      onerror="this.style.display='none'; this.nextElementSibling.style.display='flex';">
                 <div class="paw-icon" style="display:none; font-size: 3rem;"><i class="fas fa-bolt"></i></div>
            </div>
            <div class="pet-name">${s.name}</div>
            <div class="pet-types" style="margin-bottom: 10px;">
                <span class="type-tag" style="background:${attrColor}">${s.attribute}</span>
                <span class="type-tag" style="background:#64748b">${s.category}</span>
            </div>
            <div style="display: flex; justify-content: space-around; font-size: 0.8rem; color: #64748b; background: rgba(0,0,0,0.03); padding: 8px; border-radius: 12px;">
                <span>威力: <strong>${s.power || '--'}</strong></span>
                <span>消耗: <strong>${s.pp || '0'}</strong></span>
            </div>
            <p style="font-size: 0.75rem; color: #888; margin-top: 10px; line-height: 1.4; display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden;">
                ${s.desc || '无详细描述'}
            </p>
        `;
        card.style.cursor = 'pointer';
        card.onclick = () => showSkillDetail(s.id);
        grid.appendChild(card);
    });
    document.getElementById('skillCount').innerText = skills.length;
    document.getElementById('skillPageInfo').innerText = `第 ${currentSkillPage + 1} 页`;
}

// getTypeColorByName, getTypeColor, getTypeName 已提取到 js/constants.js

// ===== 精灵列表与分页 =====
async function loadPets() {
    const grid = document.getElementById('petGrid');
    if (!grid) return;
    grid.innerHTML = '<div style="grid-column: 1/-1; text-align:center; padding: 2rem;">加载中...</div>';
    
    try {
        const url = `${API_BASE}/pets?keyword=${encodeURIComponent(currentSearch)}&type=${encodeURIComponent(currentType)}&category=${currentCategory}&page=${currentPage}&size=20` +
            (currentHasFeature !== null ? `&hasFeature=${currentHasFeature}` : '') +
            (currentHasSkill !== null ? `&hasSkill=${currentHasSkill}` : '');
        const response = await fetch(url);
        const data = await response.json();
        const pets = Array.isArray(data) ? data : (data.content || []);
        
        grid.innerHTML = '';
        if (pets.length === 0) {
            grid.innerHTML = '<div style="grid-column: 1/-1; text-align:center; padding: 2rem;">未找到相关精灵</div>';
            return;
        }

        pets.forEach(p => {
            const card = document.createElement('div');
            card.className = 'pet-card';
            const displayId = p.bookId && p.bookId > 0 ? p.bookId : p.id;
            const primaryColor = getTypeColor(p.type1);
            const finalImageUrl = p.imageUrl || `pets/JL_${p.name}.png`;
            
            card.innerHTML = `
                <div class="pet-id">#${displayId}</div>
                <div class="pet-image-container">
                    <img src="${MEDIA_BASE}${finalImageUrl}" 
                          alt="${p.name}" 
                          class="pet-image" 
                          onerror="this.style.display='none'; this.nextElementSibling.style.display='flex';">
                     <div class="paw-icon" style="display:none;"><i class="fas fa-paw"></i></div>
                </div>
                <div class="pet-name">${p.name}</div>
                <div class="pet-types">
                    <span class="type-tag" style="background:${primaryColor}">${getTypeName(p.type1)}</span>
                    ${p.type2 ? `<span class="type-tag" style="background:${getTypeColor(p.type2)}">${getTypeName(p.type2)}</span>` : ''}
                </div>
            `;
            card.onclick = () => showDetail(p.id);
            grid.appendChild(card);
        });
        
        const pageInfo = document.getElementById('pageInfo');
        if (pageInfo) pageInfo.innerText = `第 ${currentPage + 1} 页`;
    } catch (e) {
        grid.innerHTML = `<div style="grid-column: 1/-1; text-align:center; color:red;">加载失败: ${e.message}</div>`;
    }
}

// ===== 精灵详情抽屉 =====
async function showDetail(id) {
    const drawer = document.getElementById('detailDrawer');
    const body = document.getElementById('drawerBody');
    if (!drawer || !body) return;
    body.innerHTML = '<div style="padding:2rem; text-align:center">正在加载秘笈...</div>';
    drawer.classList.add('active');

    try {
        const response = await fetch(`${API_BASE}/pets/${id}/details`);
        const d = await response.json();
        currentDetailData = d;
        
        body.innerHTML = `
            <div class="detail-header">
                <div class="detail-main-img">
                    <img src="${MEDIA_BASE}${d.imageUrl || `pets/JL_${d.name}.png`}" 
                          alt="${d.name}" 
                          class="detail-img-asset"
                          onerror="this.style.display='none'; this.nextElementSibling.style.display='flex';">
                    <div class="pet-img-placeholder" style="display:none; width: 140px; height: 140px; font-size: 4rem; background: linear-gradient(135deg, ${getTypeColor(d.type1)}66, ${getTypeColor(d.type1)}cc)">
                        <i class="fas fa-paw"></i>
                    </div>
                </div>
                <div class="detail-info">
                    <div class="pet-id">#${d.bookId && d.bookId > 0 ? d.bookId : d.id}</div>
                    <h2>${d.name}</h2>
                    <div class="pet-types">
                        <span class="type-tag" style="background:${getTypeColor(d.type1)}">${getTypeName(d.type1)}</span>
                        ${d.type2 ? `<span class="type-tag" style="background:${getTypeColor(d.type2)}">${getTypeName(d.type2)}</span>` : ''}
                    </div>
                </div>
            </div>

            <div class="detail-stats-section" id="detailStats">
                <div class="bio-meta-row">
                    <div class="bio-tag" title="身高"><i class="fas fa-ruler-vertical"></i> ${d.height}</div>
                    <div class="bio-tag" title="体重"><i class="fas fa-weight-hanging"></i> ${d.weight}</div>
                    <div class="bio-tag" title="蛋组"><i class="fas fa-egg"></i> ${d.eggGroups && d.eggGroups.length > 0 ? d.eggGroups.join(' / ') : '未知蛋组'}</div>
                    <div class="bio-tag" title="行走方式"><i class="fas fa-shoe-prints"></i> ${d.moveType}</div>
                    <div class="bio-tag score-tag" title="战力评分"><i class="fas fa-chart-line"></i> 战力:${d.petScore}</div>
                </div>
                ${renderStatsBars(d)}
            </div>

            <div class="evolution-section">
                <h3><i class="fas fa-layer-group"></i> 进化与形态</h3>
                <div class="evolution-timeline">
                    ${renderEvolutionChain(d.evolutionChain, d.id, d.bossForms)}
                </div>
            </div>

            <div class="tabs-container">
                <div class="tabs-nav">
                    <button class="tab-link active" onclick="switchTab(event, 'skill-self')">
                        <i class="fas fa-book"></i> <span>自学技能</span>
                    </button>
                    <button class="tab-link" onclick="switchTab(event, 'skill-stone')">
                        <i class="fas fa-gem"></i> <span>技能石</span>
                    </button>
                    <button class="tab-link" onclick="switchTab(event, 'skill-bloodline')">
                        <i class="fas fa-dna"></i> <span>血脉库</span>
                    </button>
                </div>
                <div id="skill-self" class="tab-content">${renderSkillsList(d.skills['自学'])}</div>
                <div id="skill-stone" class="tab-content" style="display:none">${renderSkillsList(d.skills['技能石'])}</div>
                <div id="skill-bloodline" class="tab-content" style="display:none">${renderSkillsList(d.skills['血脉'])}</div>
            </div>

            <div class="ai-review-box">
                <h3><i class="fas fa-microchip"></i> AI 战术智库 (性格模拟)</h3>
                <div style="font-size:0.9rem; line-height:1.8;">
                    ${d.natureRecommendation ? d.natureRecommendation.replace(/\n/g, '<br>') : '暂无分析数据'}
                </div>
                <div style="margin-top:1rem; font-size:0.75rem; color: #666; border-top: 1px solid #ccc; padding-top:0.5rem;">
                    * 以上分析基于当前数据库种族值修正算法生成。
                </div>
            </div>
        `;
    } catch (e) {
        body.innerHTML = `<div style="color:red">详情加载失败: ${e.message}</div>`;
    }
}

function renderSkillsList(skills) {
    if (!skills || skills.length === 0) return '<p style="padding:2rem; color:#94a3b8; text-align:center;">暂无此类技能记录</p>';
    
    return `<div class="skill-list-v2" style="margin-top:1rem">
        ${skills.map(s => {
            const attrColor = getTypeColorByName(s.attribute);
            const categoryIcon = s.category === '物理' ? 'fa-khanda' : (s.category === '魔法' ? 'fa-wand-sparkles' : 'fa-bolt-lightning');
            
            return `
                <div class="skill-item" style="border-left-color: ${attrColor}">
                    <!-- Top section: Icon, Info, and Stats -->
                    <div class="skill-header-v2">
                        <div class="skill-icon-wrapper">
                            <img src="${MEDIA_BASE}${s.icon || 'skills/' + s.id + '_png.png'}" 
                                 alt="${s.name}" 
                                 class="skill-icon"
                                 onerror="this.style.display='none'; this.nextElementSibling.style.display='flex';">
                            <div class="pet-img-placeholder" style="display:none; width:100%; height:100%; font-size:1rem; border-radius:50%; background:${attrColor}22; color:${attrColor}; align-items:center; justify-content:center;">
                                <i class="fas fa-certificate"></i>
                            </div>
                        </div>
                        
                        <div class="skill-info-meta">
                            <div class="skill-name">${s.name}</div>
                            <div class="skill-labels-row">
                                <span class="skill-tag" style="background: ${attrColor}15; color: ${attrColor}; border: 1px solid ${attrColor}33; padding: 2px 8px; border-radius: 4px; font-size: 0.7rem;">
                                    <i class="fas fa-certificate"></i> ${s.attribute}
                                </span>
                                <span class="skill-tag" style="background: #f1f5f9; color: #475569; border: 1px solid #e2e8f0; padding: 2px 8px; border-radius: 4px; font-size: 0.7rem;">
                                    <i class="fas ${categoryIcon}"></i> ${s.category}
                                </span>
                            </div>
                        </div>

                        <div class="skill-stats-v2">
                            <div class="skill-stat-badge-v2">
                                <span class="label">威力</span>
                                <span class="value" style="color: ${s.power && s.power !== '0' ? '#ef4444' : '#94a3b8'}">
                                    ${s.power === '0' || !s.power ? '--' : s.power}
                                </span>
                            </div>
                            <div class="skill-stat-badge-v2">
                                <span class="label">PP</span>
                                <span class="value">${s.energyConsumption}</span>
                            </div>
                            <div class="skill-stat-badge-v2" style="background: ${s.priority > 0 ? 'rgba(59, 130, 246, 0.1)' : 'transparent'}">
                                <span class="label">优先</span>
                                <span class="value" style="color: ${s.priority > 0 ? '#3b82f6' : '#94a3b8'}">
                                    ${s.priority > 0 ? '+' + s.priority : '0'}
                                </span>
                            </div>
                        </div>
                    </div>

                    <!-- Bottom section: Description -->
                    <div class="skill-desc-v2">
                        ${s.description || '该技能能量流转稳定，暂无特殊效果描述。'}
                    </div>
                </div>
            `;
        }).join('')}
    </div>`;
}

window.updateDetailForm = function(isBoss, bossIndex = 0) {
    const d = currentDetailData;
    const statsContainer = document.getElementById('detailStats');
    if (!statsContainer) return;
    
    if (isBoss) {
        const bossData = d.bossForms[bossIndex];
        statsContainer.innerHTML = renderStatsBars(bossData, true, d);
    } else {
        statsContainer.innerHTML = renderStatsBars(d);
    }
};

function renderStatsBars(data, isBoss = false, normalData = null) {
    const compare = isBoss ? normalData : null; 

    return `
        <div class="stats-bars-container">
            ${renderStatRow('HP', data.hp, '#ef4444', compare ? compare.hp : null)}
            ${renderStatRow('物攻', data.attack, '#f59e0b', compare ? compare.attack : null)}
            ${renderStatRow('物防', data.defense, '#3b82f6', compare ? compare.defense : null)}
            ${renderStatRow('魔攻', data.sp_atk, '#8b5cf6', compare ? compare.sp_atk : null)}
            ${renderStatRow('魔防', data.sp_def, '#06b6d4', compare ? compare.sp_def : null)}
            ${renderStatRow('速度', data.speed, '#10b981', compare ? compare.speed : null)}
        </div>
        <div class="trait-box">
            <h3 style="margin-bottom:0.5rem; color:var(--accent-blue)">✨ 精灵特性: ${data.featureName || '暂无'}</h3>
            <p style="font-size:0.95rem; line-height:1.6; color:var(--text-primary); font-weight:500; background: rgba(59, 130, 246, 0.05); padding: 10px; border-radius: 8px;">
                ${data.featureDesc || '该精灵目前没有特殊的战斗特性。'}
            </p>
            <hr style="margin:1rem 0; border:none; border-top:1px solid #eee;">
            <h3 style="margin-bottom:0.5rem; color:var(--text-secondary)">📜 背景简介</h3>
            <p style="font-size:0.85rem; color:var(--text-secondary)">${data.description || '这个精灵很神秘，还没有详细介绍。'}</p>
        </div>
    `;
}

function renderStatRow(label, value, color, compareValue = null) {
    const percentage = Math.min((value / 200) * 100, 100);
    let diffHtml = '';
    if (compareValue !== null) {
        const diff = value - compareValue;
        if (diff !== 0) {
            diffHtml = `<span class="stat-diff ${diff > 0 ? 'diff-positive' : 'diff-negative'}">
                (${diff > 0 ? '+' : ''}${diff})
            </span>`;
        }
    }

    const icons = {
        'HP': 'fas fa-heart',
        '物攻': 'fas fa-gavel',
        '物防': 'fas fa-shield-alt',
        '魔攻': 'fas fa-magic',
        '魔防': 'fas fa-hand-holding-magic',
        '速度': 'fas fa-running'
    };

    return `
        <div class="stat-row">
            <div class="stat-label">
                <i class="${icons[label] || 'fas fa-chart-bar'}"></i>
                <span>${label}</span>
            </div>
            <div class="stat-bar-outer">
                <div class="stat-bar-inner" style="width: ${percentage}%; background: ${color}"></div>
            </div>
            <div class="stat-value">${value}${diffHtml}</div>
        </div>
    `;
}

function renderEvolutionChain(chain, currentId, bossForms = []) {
    let html = '';
    if (chain && chain.length > 0) {
        chain.sort((a, b) => a.stage - b.stage);
        html += chain.map((node) => {
            const isCurrent = node.id === currentId;
            const color = getTypeColor(node.type1);
            let nodeHtml = `
                <div class="evo-node ${isCurrent ? 'active' : ''}" onclick="showDetail(${node.id})">
                    <img src="${MEDIA_BASE}${node.imageUrl || `pets/JL_${node.name}.png`}" 
                          alt="${node.name}" 
                          class="evo-img-asset"
                          onerror="this.style.display='none'; this.nextElementSibling.style.display='flex';">
                    <div class="pet-img-placeholder" style="display:none; width:50px; height:50px; font-size:1.2rem; background: linear-gradient(135deg, ${color}66, ${color}cc)">
                        <i class="fas fa-paw"></i>
                    </div>
                    <div class="evo-node-id">#${node.bookId || node.id}</div>
                    <div class="evo-node-name">${node.name}</div>
                </div>
            `;
            if (node.evolvesTo && node.evolvesTo.length > 0) {
                const next = node.evolvesTo[0];
                nodeHtml += `
                    <div class="evo-arrow">
                        <div class="evo-level">Lv.${next.needLevel}</div>
                        <i class="fas fa-long-arrow-alt-right"></i>
                    </div>
                `;
            }
            return nodeHtml;
        }).join('');
    }

    if (bossForms && bossForms.length > 0) {
        html += '<div class="evo-arrow" style="margin: 0 1rem; opacity: 0.3;"> | </div>';
        html += bossForms.map((boss, idx) => {
            const color = getTypeColor(boss.type1);
            return `
                <div class="evo-node" onclick="updateDetailForm(true, ${idx})">
                    <img src="${MEDIA_BASE}${boss.imageUrl || `pets/JL_${boss.name}.png`}" 
                          alt="${boss.name}" 
                          class="evo-img-asset"
                          style="border: 2px solid var(--accent-gold); border-radius: 50%; padding: 2px;"
                          onerror="this.style.display='none'; this.nextElementSibling.style.display='flex';">
                    <div class="pet-img-placeholder" style="display:none; width:50px; height:50px; font-size:1.2rem; background: linear-gradient(135deg, ${color}66, ${color}cc); border: 2px solid var(--accent-gold)">
                        <i class="fas fa-crown"></i>
                    </div>
                    <div class="evo-node-id">首领</div>
                    <div class="evo-node-name">${boss.name}</div>
                </div>
            `;
        }).join('');
    }

    return html || '<p>暂无形态数据</p>';
}

window.switchTab = function(evt, tabId) {
    const container = evt.currentTarget.closest('.tabs-container');
    container.querySelectorAll('.tab-content').forEach(tc => tc.style.display = 'none');
    container.querySelectorAll('.tab-link').forEach(tl => tl.classList.remove('active'));
    const target = document.getElementById(tabId);
    if (target) target.style.display = 'block';
    evt.currentTarget.classList.add('active');
};

// ===== 血脉 Tab =====
async function loadBloodlines() {
    const grid = document.getElementById('bloodlineGrid');
    if (!grid) return;
    grid.innerHTML = '<div class="loading-spinner">加载中...</div>';
    try {
        const response = await fetch(`${API_BASE}/bloodlines`);
        const data = await response.json();
        const keyword = currentBloodlineSearch.trim().toLowerCase();
        const filtered = data.filter(b => {
            if (!keyword) return true;
            return [b.name, b.short_name, b.skillNames].filter(Boolean).join(' ').toLowerCase().includes(keyword);
        });
        grid.innerHTML = filtered.length ? filtered.map(b => `
            <div class="data-card">
                <h4>${b.name}</h4>
                <p style="font-size:0.85rem; color:#64748b; margin-top:0.5rem">${b.short_name || ''}</p>
                <div style="margin-top:0.5rem; padding-top:0.5rem; border-top:1px solid #f1f5f9">
                    <strong>关联技能:</strong> <span style="color:var(--accent-blue)">${b.skillNames || '无'}</span>
                </div>
            </div>
        `).join('') : `<div style="grid-column: 1/-1; text-align:center; padding:2rem; color:#64748b;">未找到相关血脉</div>`;
    } catch (e) {
        grid.innerHTML = `<div style="text-align:center; color:red; padding:2rem;">加载失败: ${e.message}</div>`;
    }
}

// ===== Buff Tab =====
let currentBuffType = '';

function initBuffFilters() {
    const container = document.getElementById('buffTypeFilters');
    if (!container || container.children.length > 0) return;

    const buffTypes = [
        { id: '', name: '全部' },
        { id: '1', name: '增益' },
        { id: '2', name: '减益' },
        { id: '3', name: '特性' },
        { id: '4', name: '印记' }
    ];

    buffTypes.forEach(t => {
        const btn = document.createElement('button');
        btn.className = `type-btn ${currentBuffType === t.id ? 'active' : ''}`;
        btn.innerHTML = `<span>${t.name}</span>`;
        btn.onclick = () => {
            currentBuffType = t.id;
            container.querySelectorAll('.type-btn').forEach(b => b.classList.remove('active'));
            btn.classList.add('active');
            loadBuffs();
        };
        container.appendChild(btn);
    });
}

async function loadBuffs() {
    const grid = document.getElementById('buffGrid');
    if (!grid) return;
    grid.innerHTML = '<div class="loading-spinner">加载中...</div>';
    try {
        const searchInput = document.getElementById('buffSearch');
        const search = searchInput ? searchInput.value : '';
        const type = currentBuffType;

        const response = await fetch(`${API_BASE}/buffs?keyword=${encodeURIComponent(search)}${type ? `&type=${type}` : ''}`);
        const data = await response.json();
        grid.innerHTML = data.map(b => `
            <div class="data-card">
                <div style="display:flex; justify-content:space-between; align-items:start">
                    <h4>${b.name}</h4>
                    <span class="skill-tag" style="background:#e0f2fe; color:#0369a1">${b.typeName || '未知类型'}</span>
                </div>
                <p style="font-size:0.9rem; color:#475569; margin:0.5rem 0">${b.description || '无详细描述'}</p>
                <div style="display:flex; justify-content:space-between; font-size:0.75rem; color:#94a3b8">
                    <span>${b.is_clean_when_rest ? '换下清除' : '持续生效'}</span>
                    <span>优先级: ${b.trigger_priority}</span>
                </div>
            </div>
        `).join('');
    } catch (e) {
        grid.innerHTML = `<div style="text-align:center; color:red; padding:2rem;">加载失败: ${e.message}</div>`;
    }
}

// ===== 性格 Tab =====
async function loadNatures() {
    const grid = document.getElementById('natureGrid');
    if (!grid) return;
    grid.innerHTML = '<div class="loading-spinner">加载中...</div>';
    try {
        const response = await fetch(`${API_BASE}/natures`);
        const data = await response.json();
        const keyword = currentNatureSearch.trim().toLowerCase();
        const filtered = data.filter(n => {
            if (!keyword) return true;
            return [n.name, n.plusAttrName, n.minusAttrName].filter(Boolean).join(' ').toLowerCase().includes(keyword);
        });
        
        grid.innerHTML = filtered.length ? filtered.map(n => {
            const plus = n.plusAttrName ? `<span class="diff-positive">${n.plusAttrName}↑</span>` : '无';
            const minus = n.minusAttrName ? `<span class="diff-negative">${n.minusAttrName}↓</span>` : '无';
            return `
                <div class="data-card nature-card">
                    <h4>${n.name}</h4>
                    <div style="display:flex; gap:1rem; margin-top:0.5rem">
                        <div>增益: ${plus}</div>
                        <div>减益: ${minus}</div>
                    </div>
                </div>
            `;
        }).join('') : `<div style="grid-column: 1/-1; text-align:center; padding:2rem; color:#64748b;">未找到相关性格</div>`;
    } catch (e) {
        grid.innerHTML = `<div style="text-align:center; color:red; padding:2rem;">加载失败: ${e.message}</div>`;
    }
}

// ===== 天分 Tab =====
async function loadTalents() {
    const grid = document.getElementById('talentGrid');
    if (!grid) return;
    grid.innerHTML = '<div class="loading-spinner">加载中...</div>';
    try {
        const response = await fetch(`${API_BASE}/talents`);
        const data = await response.json();
        const keyword = currentTalentSearch.trim().toLowerCase();
        const filtered = data.filter(t => {
            if (!keyword) return true;
            return [t.name, t.desc].filter(Boolean).join(' ').toLowerCase().includes(keyword);
        });
        grid.innerHTML = filtered.length ? filtered.map(t => `
            <div class="data-card">
                <h4>${t.name}</h4>
                <p style="font-size:0.9rem; color:#475569; margin-top:0.5rem">${t.desc || '无描述'}</p>
            </div>
        `).join('') : `<div style="grid-column: 1/-1; text-align:center; padding:2rem; color:#64748b;">未找到相关特长</div>`;
    } catch (e) {
        grid.innerHTML = `<div style="text-align:center; color:red; padding:2rem;">加载失败: ${e.message}</div>`;
    }
}

// ===== 属性克制矩阵 =====
async function loadTypeMatrix() {
    const container = document.getElementById('typeMatrix');
    if (!container) return;
    container.innerHTML = '<div class="loading-spinner">加载中...</div>';
    try {
        const response = await fetch(`${API_BASE}/types`);
        const data = await response.json();
        lastTypeData = data;
        
        // Populate Calculator Selects
        const t1 = document.getElementById('calcType1');
        const t2 = document.getElementById('calcType2');
        if (t1 && t1.options.length === 0) {
            data.list.forEach(t => {
                const opt1 = new Option(t.name, t.id);
                const opt2 = new Option(t.name, t.id);
                t1.add(opt1);
                t2.add(opt2);
            });
            t1.onchange = () => updateTypeCalculator();
            t2.onchange = () => updateTypeCalculator();
        }

        let html = '<table class="type-matrix"><thead><tr><th>攻击 \\ 防御</th>';
        data.list.forEach(t => html += `<th>${t.name}</th>`);
        html += '</tr></thead><tbody>';
        
        data.list.forEach(atk => {
            html += `<tr><th>${atk.name}</th>`;
            data.list.forEach(def => {
                const rel = data.relations.find(r => r.attacker_id === atk.id && r.defender_id === def.id);
                let val = '1';
                let cls = '';
                if (rel) {
                    if (rel.multiplier === 1) { val = '2'; cls = 'eff-200'; }
                    if (rel.multiplier === -1) { val = '½'; cls = 'eff-50'; }
                    if (rel.multiplier === 0) { val = '0'; cls = 'eff-0'; }
                }
                html += `<td class="${cls}">${val}</td>`;
            });
            html += '</tr>';
        });
        html += '</tbody></table>';
        container.innerHTML = html;
        
        // First calculation
        updateTypeCalculator();
    } catch (e) {
        container.innerHTML = `<div style="text-align:center; color:red; padding:2rem;">加载失败: ${e.message}</div>`;
    }
}

function updateTypeCalculator() {
    const t1 = parseInt(document.getElementById('calcType1').value);
    const t2Val = document.getElementById('calcType2').value;
    const t2 = t2Val ? parseInt(t2Val) : null;
    const results = document.getElementById('calcResults');
    if (!lastTypeData) return;

    const defensiveTable = {}; 
    
    // Defensive Analysis
    lastTypeData.list.forEach(atk => {
        let m1 = 1.0;
        let m2 = 1.0;
        const rel1 = lastTypeData.relations.find(r => r.attacker_id === atk.id && r.defender_id === t1);
        if (rel1) m1 = rel1.multiplier === 1 ? 2.0 : (rel1.multiplier === -1 ? 0.5 : 0.0);
        if (t2) {
            const rel2 = lastTypeData.relations.find(r => r.attacker_id === atk.id && r.defender_id === t2);
            if (rel2) m2 = rel2.multiplier === 1 ? 2.0 : (rel2.multiplier === -1 ? 0.5 : 0.0);
        }
        const total = m1 * m2;
        if (!defensiveTable[total]) defensiveTable[total] = [];
        defensiveTable[total].push(atk.name);
    });

    // Offensive Analysis
    const offensiveTable = [];
    [t1, t2].forEach((tid) => {
        if (!tid) return;
        const strongAgainst = lastTypeData.relations
            .filter(r => r.attacker_id === tid && r.multiplier === 1)
            .map(r => lastTypeData.list.find(t => t.id === r.defender_id)?.name)
            .filter(n => n);
        const weakAgainst = lastTypeData.relations
            .filter(r => r.attacker_id === tid && r.multiplier === -1)
            .map(r => lastTypeData.list.find(t => t.id === r.defender_id)?.name)
            .filter(n => n);
        offensiveTable.push({ name: lastTypeData.list.find(t => t.id === tid).name, strong: strongAgainst, weak: weakAgainst });
    });

    const buckets = [
        { val: 4.0, label: '4.0x 致命伤害', color: '#dc2626' },
        { val: 2.0, label: '2.0x 克制伤害', color: '#f97316' },
        { val: 0.5, label: '0.5x 抵抗伤害', color: '#10b981' },
        { val: 0.25, label: '0.25x 极强抵抗', color: '#06b6d4' },
        { val: 0.0, label: '0x 伤害免疫', color: '#1e293b' }
    ];

    let html = `
        <div class="defensive-card" style="grid-column: 1/-1;">
            <h4 style="margin-bottom: 1rem; color: #475569; border-bottom: 2px solid #f1f5f9; padding-bottom: 0.5rem;"><i class="fas fa-shield-alt"></i> 防御性能 (作为防御方受击倍率)</h4>
            <div style="display: grid; grid-template-columns: repeat(auto-fit, minmax(200px, 1fr)); gap: 1rem;">
                ${buckets.map(b => {
                    const types = defensiveTable[b.val] || [];
                    if (types.length === 0) return '';
                    return `
                        <div class="result-bucket" style="padding: 1rem; background: #f8fafc; border-radius: 12px; border-top: 4px solid ${b.color}">
                            <div style="font-weight: 700; font-size: 0.8rem; color: ${b.color}; margin-bottom: 0.5rem;">${b.label}</div>
                            <div style="display: flex; flex-wrap: wrap; gap: 0.4rem;">
                                ${types.map(tn => `<span class="type-tag" style="background: ${getTypeColorByName(tn)}; color: white; border: none; font-size: 0.7rem; padding: 2px 8px;">${tn}</span>`).join('')}
                            </div>
                        </div>
                    `;
                }).join('')}
            </div>
        </div>
        <div class="offensive-card" style="grid-column: 1/-1; margin-top: 1rem;">
            <h4 style="margin-bottom: 1rem; color: #475569; border-bottom: 2px solid #f1f5f9; padding-bottom: 0.5rem;"><i class="fas fa-fist-raised"></i> 攻击性能 (作为攻击方克制能力)</h4>
            <div style="display: grid; grid-template-columns: repeat(auto-fit, minmax(300px, 1fr)); gap: 1rem;">
                ${offensiveTable.map(ot => `
                    <div style="background: #f8fafc; padding: 1rem; border-radius: 12px;">
                        <div style="font-weight: 700; margin-bottom: 0.75rem; display: flex; align-items: center; gap: 0.5rem;">
                            <span class="type-tag" style="background: ${getTypeColorByName(ot.name)}; color: white; border: none;">${ot.name}</span> 攻击表现:
                        </div>
                        <div style="margin-bottom: 0.5rem;">
                            <span style="color: #f97316; font-size: 0.8rem; font-weight: 600;">克制 (2.0x):</span>
                            <div style="display: flex; flex-wrap: wrap; gap: 0.4rem; margin-top: 0.3rem;">
                                ${ot.strong.map(tn => `<span class="type-tag" style="background: ${getTypeColorByName(tn)}; color: white; border: none; font-size: 0.7rem; opacity: 0.9;">${tn}</span>`).join('') || '<span style="color:#94a3b8; font-size:0.75rem">无</span>'}
                            </div>
                        </div>
                        <div>
                            <span style="color: #10b981; font-size: 0.8rem; font-weight: 600;">被抵抗 (0.5x):</span>
                            <div style="display: flex; flex-wrap: wrap; gap: 0.4rem; margin-top: 0.3rem;">
                                ${ot.weak.map(tn => `<span class="type-tag" style="background: ${getTypeColorByName(tn)}; color: white; border: none; font-size: 0.7rem; opacity: 0.9;">${tn}</span>`).join('') || '<span style="color:#94a3b8; font-size:0.75rem">无</span>'}
                            </div>
                        </div>
                    </div>
                `).join('')}
            </div>
        </div>
    `;
    results.innerHTML = html;
}

// ===== 技能详情弹窗 =====
async function showSkillDetail(skillId) {
    const drawer = document.getElementById('detailDrawer');
    const body = document.getElementById('drawerBody');
    if (!drawer || !body) return;

    drawer.classList.add('active');
    body.innerHTML = '<div style="text-align:center; padding:3rem;">加载中...</div>';

    try {
        const res = await fetch(`${API_BASE}/skills/${skillId}/details`);
        const d = await res.json();
        if (d.error) { body.innerHTML = `<div style="padding:2rem; color:red;">${d.error}</div>`; return; }

        const attrColor = getTypeColorByName(d.attribute);
        const learnersHtml = (d.learners || []).map(p => {
            const imgSrc = p.imageUrl ? `${MEDIA_BASE}${p.imageUrl}` : '';
            const typeColor = getTypeColor(p.type1);
            return `
                <div class="pet-card" onclick="showDetail(${p.petId})" style="cursor:pointer">
                    <div class="pet-image-container" style="height:80px">
                        ${imgSrc ? `<img src="${imgSrc}" alt="${p.name}" class="pet-image" onerror="this.style.display='none'">` : ''}
                    </div>
                    <div class="pet-name">${p.name}</div>
                    <div class="pet-types">
                        <span class="type-tag" style="background:${typeColor}">${getTypeName(p.type1)}</span>
                        <span class="type-tag" style="background:#94a3b8">${p.source}</span>
                    </div>
                </div>`;
        }).join('');

        body.innerHTML = `
            <div style="text-align:center; padding:1.5rem 0;">
                <img src="${MEDIA_BASE}${d.icon || 'skills/' + d.id + '_png.png'}" style="width:80px; height:80px; object-fit:contain;" onerror="this.style.display='none'">
                <h2 style="margin:0.5rem 0;">${d.name}</h2>
                <div style="display:flex; gap:8px; justify-content:center; margin-bottom:1rem;">
                    <span class="type-tag" style="background:${attrColor}">${d.attribute}</span>
                    <span class="type-tag" style="background:#64748b">${d.category}</span>
                </div>
            </div>
            <div style="display:grid; grid-template-columns:repeat(3,1fr); gap:12px; margin-bottom:1.5rem; text-align:center;">
                <div style="background:rgba(0,0,0,0.03); padding:12px; border-radius:12px;">
                    <div style="font-size:0.75rem; color:#64748b;">威力</div>
                    <div style="font-size:1.2rem; font-weight:700;">${d.power || '--'}</div>
                </div>
                <div style="background:rgba(0,0,0,0.03); padding:12px; border-radius:12px;">
                    <div style="font-size:0.75rem; color:#64748b;">消耗</div>
                    <div style="font-size:1.2rem; font-weight:700;">${d.pp || 0}</div>
                </div>
                <div style="background:rgba(0,0,0,0.03); padding:12px; border-radius:12px;">
                    <div style="font-size:0.75rem; color:#64748b;">优先级</div>
                    <div style="font-size:1.2rem; font-weight:700;">${d.priority || 0}</div>
                </div>
            </div>
            <div style="background:rgba(0,0,0,0.03); padding:16px; border-radius:12px; margin-bottom:1.5rem;">
                <div style="font-size:0.82rem; font-weight:700; color:#64748b; margin-bottom:8px;">技能描述</div>
                <p style="line-height:1.6; color:#334155;">${d.desc || '暂无描述'}</p>
            </div>
            <div>
                <div style="font-size:0.82rem; font-weight:700; color:#64748b; margin-bottom:12px;">可学习该技能的精灵 (${(d.learners || []).length})</div>
                <div class="pet-grid">${learnersHtml || '<div style="grid-column:1/-1; text-align:center; color:#94a3b8; padding:1rem;">暂无精灵记录</div>'}</div>
            </div>`;
    } catch (e) {
        body.innerHTML = `<div style="padding:2rem; color:red;">加载失败: ${e.message}</div>`;
    }
}

// ===== 身高体重查询 =====
async function loadDimensionSearch() {
    const grid = document.getElementById('dimResultGrid');
    const countEl = document.getElementById('dimResultCount');
    if (!grid) return;

    const height = document.getElementById('dimHeight')?.value || '';
    const weight = document.getElementById('dimWeight')?.value || '';

    if (!height || !weight) {
        grid.innerHTML = '<div style="grid-column:1/-1; text-align:center; padding:2rem; color:#64748b;">请同时输入身高和体重</div>';
        if (countEl) countEl.textContent = '';
        return;
    }

    grid.innerHTML = '<div style="grid-column:1/-1; text-align:center; padding:2rem;">查找中...</div>';
    try {
        const res = await fetch(`${API_BASE}/dimensions/match?height=${height}&weight=${weight}`);
        const data = await res.json();
        if (countEl) countEl.textContent = `找到 ${data.length} 只精灵（按匹配度排序）`;

        if (!data.length) {
            grid.innerHTML = '<div style="grid-column:1/-1; text-align:center; padding:2rem; color:#64748b;">未找到匹配的精灵</div>';
            return;
        }
        grid.innerHTML = data.map((p, i) => {
            const type1Name = getTypeName(p.type1);
            const type1Color = getTypeColor(p.type1);
            const imgSrc = p.imageUrl ? `${MEDIA_BASE}${p.imageUrl}` : '';
            return `
                <div class="pet-card" onclick="showDetail(${p.id})" style="cursor:pointer">
                    <div class="pet-id">${i === 0 ? '最佳匹配' : '#' + (i + 1)}</div>
                    <div class="pet-image-container" style="height:100px">
                        ${imgSrc ? `<img src="${imgSrc}" alt="${p.name}" class="pet-image" onerror="this.style.display='none'">` : ''}
                    </div>
                    <div class="pet-name">${p.name}</div>
                    <div class="pet-types">
                        <span class="type-tag" style="background:${type1Color}">${type1Name}</span>
                    </div>
                    <div style="font-size:0.75rem; color:#64748b; margin-top:4px">
                        身高: ${p.heightMin.toFixed(1)}-${p.heightMax.toFixed(1)}m<br>
                        体重: ${p.weightMin.toFixed(1)}-${p.weightMax.toFixed(1)}kg
                    </div>
                </div>`;
        }).join('');
    } catch (e) {
        grid.innerHTML = `<div style="grid-column:1/-1; text-align:center; color:red; padding:2rem;">加载失败: ${e.message}</div>`;
    }
}

// ===== 蛋组查询 =====
let currentEggGroupId = null;
let breedPetId1 = null;
let breedPetId2 = null;

async function loadEggGroupFilters() {
    const container = document.getElementById('eggGroupFilters');
    const select = document.getElementById('eggGroupSelect');
    if (!container) return;
    if (container.children.length > 0) return; // 已加载则跳过

    try {
        const res = await fetch(`${API_BASE}/egg-groups`);
        const groups = await res.json();

        // 渲染按钮
        container.innerHTML = groups.map(g =>
            `<button class="type-btn" data-gid="${g.groupId}">${g.groupName} (${g.petCount})</button>`
        ).join('');

        // 渲染下拉选择器
        if (select) {
            groups.forEach(g => {
                const opt = document.createElement('option');
                opt.value = g.groupId;
                opt.textContent = `${g.groupName} (${g.petCount}只)`;
                select.appendChild(opt);
            });
            select.onchange = () => {
                const gid = select.value ? parseInt(select.value) : null;
                currentEggGroupId = gid;
                // 同步按钮高亮
                container.querySelectorAll('.type-btn').forEach(b => {
                    b.classList.toggle('active', b.dataset.gid === select.value);
                });
                if (gid) loadEggGroupPets(gid);
                else {
                    document.getElementById('eggGroupPetGrid').innerHTML = '';
                    document.getElementById('eggGroupPetCount').textContent = '';
                }
            };
        }

        // 按钮点击联动下拉
        container.querySelectorAll('.type-btn').forEach(btn => {
            btn.onclick = () => {
                const gid = parseInt(btn.dataset.gid);
                container.querySelectorAll('.type-btn').forEach(b => b.classList.remove('active'));
                if (currentEggGroupId === gid) {
                    currentEggGroupId = null;
                    if (select) select.value = '';
                    document.getElementById('eggGroupPetGrid').innerHTML = '';
                    document.getElementById('eggGroupPetCount').textContent = '';
                } else {
                    currentEggGroupId = gid;
                    btn.classList.add('active');
                    if (select) select.value = String(gid);
                    loadEggGroupPets(gid);
                }
            };
        });
    } catch (e) {
        container.innerHTML = `<span style="color:red">加载蛋组失败: ${e.message}</span>`;
    }
}

async function loadEggGroupPets(groupId) {
    const grid = document.getElementById('eggGroupPetGrid');
    if (!grid) return;
    grid.innerHTML = '<div style="grid-column:1/-1; text-align:center; padding:2rem;">加载中...</div>';

    try {
        const res = await fetch(`${API_BASE}/egg-groups?groupId=${groupId}`);
        const data = await res.json();
        const countEl = document.getElementById('eggGroupPetCount');
        if (countEl) countEl.textContent = `共 ${data.length} 只精灵`;
        grid.innerHTML = data.map(p => {
            const type1Name = getTypeName(p.type1);
            const type1Color = getTypeColor(p.type1);
            const imgSrc = p.imageUrl ? `${MEDIA_BASE}${p.imageUrl}` : '';
            return `
                <div class="pet-card" onclick="openDetail(${p.petId})" style="cursor:pointer">
                    <div class="pet-card-img">
                        ${imgSrc ? `<img src="${imgSrc}" alt="${p.name}" onerror="this.style.display='none'">` : ''}
                    </div>
                    <div class="pet-card-info">
                        <h4>${p.name}</h4>
                        <span class="type-badge" style="background:${type1Color}">${type1Name}</span>
                    </div>
                </div>`;
        }).join('');
    } catch (e) {
        grid.innerHTML = `<div style="grid-column:1/-1; text-align:center; color:red; padding:2rem;">加载失败: ${e.message}</div>`;
    }
}

// 生蛋兼容性 — 精灵搜索弹出
function initBreedPetSearch(inputId, resultsId, setPetId) {
    const input = document.getElementById(inputId);
    const results = document.getElementById(resultsId);
    if (!input || !results) return;

    input.oninput = debounce(async () => {
        const keyword = input.value.trim();
        if (!keyword) { results.style.display = 'none'; return; }
        try {
            const res = await fetch(`${API_BASE}/pets?keyword=${encodeURIComponent(keyword)}&size=8`);
            const pets = await res.json();
            if (!pets.length) { results.style.display = 'none'; return; }
            results.innerHTML = pets.map(p =>
                `<div class="result-item" style="padding:8px 12px; cursor:pointer; border-bottom:1px solid #f1f5f9;"
                      onmouseover="this.style.background='#f1f5f9'" onmouseout="this.style.background=''"
                      onclick="window._selectBreedPet('${inputId}', '${resultsId}', ${p.id}, '${p.name.replace(/'/g, "\\'")}', '${setPetId}')">${p.name}</div>`
            ).join('');
            results.style.display = 'block';
        } catch (e) { results.style.display = 'none'; }
    }, 250);

    document.addEventListener('click', (e) => {
        if (!results.contains(e.target) && e.target !== input) results.style.display = 'none';
    });
}

window._selectBreedPet = function(inputId, resultsId, petId, petName, varName) {
    document.getElementById(inputId).value = petName;
    document.getElementById(resultsId).style.display = 'none';
    if (varName === 'breedPetId1') breedPetId1 = petId;
    else breedPetId2 = petId;
};

async function checkBreedCompat() {
    const resultDiv = document.getElementById('breedResult');
    if (!resultDiv) return;

    if (!breedPetId1 || !breedPetId2) {
        resultDiv.innerHTML = '<div style="color:#f59e0b; font-weight:600;">请先选择两只精灵</div>';
        return;
    }

    resultDiv.innerHTML = '<div>检查中...</div>';
    try {
        const res = await fetch(`${API_BASE}/egg-groups/breed-check?petId1=${breedPetId1}&petId2=${breedPetId2}`);
        const data = await res.json();
        const canBreed = data.canBreed;
        const icon = canBreed ? '&#10004;' : '&#10008;';
        const color = canBreed ? '#22c55e' : '#ef4444';
        const msg = canBreed
            ? `可以生蛋！共同蛋组: ${data.commonGroups.join('、')}`
            : '不能生蛋，没有共同蛋组';

        resultDiv.innerHTML = `
            <div style="font-size:1.5rem; color:${color}; font-weight:700; margin-bottom:0.5rem">${icon} ${msg}</div>
            <div style="color:#64748b; font-size:0.9rem">
                ${data.pet1} 的蛋组: ${data.pet1Groups.join('、') || '无'}<br>
                ${data.pet2} 的蛋组: ${data.pet2Groups.join('、') || '无'}
            </div>`;
    } catch (e) {
        resultDiv.innerHTML = `<div style="color:red">查询失败: ${e.message}</div>`;
    }
}

// ===== DOMContentLoaded 入口 =====
document.addEventListener('DOMContentLoaded', () => {
    console.log('Encyclopedia DOM Loaded');
    initMainTabs();
    initSidebar();
    initDetailDrawer();
    loadPets();
    
    const petSearch = document.getElementById('petSearch');
    if (petSearch) {
        petSearch.oninput = debounce((e) => {
            currentSearch = e.target.value;
            currentPage = 0;
            loadPets();
        }, 300);
    }

    const skillSearch = document.getElementById('skillSearch');
    if (skillSearch) {
        skillSearch.oninput = debounce((e) => {
            currentSkillSearch = e.target.value;
            currentSkillPage = 0;
            loadSkillsGallery();
        }, 300);
    }

    const bloodlineSearch = document.getElementById('bloodlineSearch');
    if (bloodlineSearch) {
        bloodlineSearch.oninput = debounce((e) => {
            currentBloodlineSearch = e.target.value;
            loadBloodlines();
        }, 250);
    }

    const natureSearch = document.getElementById('natureSearch');
    if (natureSearch) {
        natureSearch.oninput = debounce((e) => {
            currentNatureSearch = e.target.value;
            loadNatures();
        }, 250);
    }

    const talentSearch = document.getElementById('talentSearch');
    if (talentSearch) {
        talentSearch.oninput = debounce((e) => {
            currentTalentSearch = e.target.value;
            loadTalents();
        }, 250);
    }

    const buffSearch = document.getElementById('buffSearch');
    if (buffSearch) {
        buffSearch.oninput = debounce(() => loadBuffs(), 300);
    }

    initBuffFilters();

    const prevPage = document.getElementById('prevPage');
    if (prevPage) prevPage.onclick = () => { if (currentPage > 0) { currentPage--; loadPets(); } };

    const nextPage = document.getElementById('nextPage');
    if (nextPage) nextPage.onclick = () => { currentPage++; loadPets(); };

    const prevSkillPage = document.getElementById('prevSkillPage');
    if (prevSkillPage) prevSkillPage.onclick = () => { if (currentSkillPage > 0) { currentSkillPage--; loadSkillsGallery(); } };

    const nextSkillPage = document.getElementById('nextSkillPage');
    if (nextSkillPage) nextSkillPage.onclick = () => { currentSkillPage++; loadSkillsGallery(); };

    // 身高体重查询按钮
    const dimSearchBtn = document.getElementById('dimSearchBtn');
    if (dimSearchBtn) dimSearchBtn.onclick = () => loadDimensionSearch();

    // 蛋组生蛋兼容性搜索
    initBreedPetSearch('breedPet1Search', 'breedPet1Results', 'breedPetId1');
    initBreedPetSearch('breedPet2Search', 'breedPet2Results', 'breedPetId2');
    const breedCheckBtn = document.getElementById('breedCheckBtn');
    if (breedCheckBtn) breedCheckBtn.onclick = () => checkBreedCompat();

    // Initialize Stat Simulator
    initSkillFilters();
    initNatures();
});


// --- NEW SIMULATOR LOGIC (PORTED) ---

const IMAGE_BASE = MEDIA_BASE.replace(/\/media\/?$/, "");
const PVP_STAR_LEVEL = 5;
const PRESET_STORAGE_KEY = 'myAtkPetPresetsV1';


let simPet = null;
let battleAtkPet = null;
let battleDefPet = null;
let duelMyPet = null;
let duelEnemyPet = null;

const D = {
    
    
    

    simSearch: document.getElementById('simSearch'),
    simSearchResults: document.getElementById('simSearchResults'),
    simPetInfo: document.getElementById('simPetInfo'),
    simPetImg: document.getElementById('simPetImg'),
    simPetName: document.getElementById('simPetName'),
    simBaseHp: document.getElementById('simBaseHp'),
    simBaseAtk: document.getElementById('simBaseAtk'),
    simBaseDef: document.getElementById('simBaseDef'),
    simBaseMatk: document.getElementById('simBaseMatk'),
    simBaseMdef: document.getElementById('simBaseMdef'),
    simBaseSpd: document.getElementById('simBaseSpd'),
    simNaturePlus: document.getElementById('simNaturePlus'),
    simNatureMinus: document.getElementById('simNatureMinus'),
    simPresetSelect: document.getElementById('simPresetSelect'),
    simPresetLoadBtn: document.getElementById('simPresetLoadBtn'),
    simPresetSaveBtn: document.getElementById('simPresetSaveBtn'),
    simPresetDeleteBtn: document.getElementById('simPresetDeleteBtn'),
    simIvs: {
        hp: document.getElementById('simIvHp'),
        atk: document.getElementById('simIvAtk'),
        def: document.getElementById('simIvDef'),
        matk: document.getElementById('simIvMatk'),
        mdef: document.getElementById('simIvMdef'),
        spd: document.getElementById('simIvSpd')
    },
    simFinalHp: document.getElementById('simFinalHp'),
    simFinalAtk: document.getElementById('simFinalAtk'),
    simFinalDef: document.getElementById('simFinalDef'),
    simFinalMatk: document.getElementById('simFinalMatk'),
    simFinalMdef: document.getElementById('simFinalMdef'),
    simFinalSpd: document.getElementById('simFinalSpd'),
    simRangeHp: document.getElementById('simRangeHp'),
    simRangeAtk: document.getElementById('simRangeAtk'),
    simRangeDef: document.getElementById('simRangeDef'),
    simRangeMatk: document.getElementById('simRangeMatk'),
    simRangeMdef: document.getElementById('simRangeMdef'),
    simRangeSpd: document.getElementById('simRangeSpd'),
    simGapHp: document.getElementById('simGapHp'),
    simGapAtk: document.getElementById('simGapAtk'),
    simGapDef: document.getElementById('simGapDef'),
    simGapMatk: document.getElementById('simGapMatk'),
    simGapMdef: document.getElementById('simGapMdef'),
    simGapSpd: document.getElementById('simGapSpd'),

    battleAtkSearch: document.getElementById('battleAtkSearch'),
    battleAtkSearchResults: document.getElementById('battleAtkSearchResults'),
    battleNaturePlus: document.getElementById('battleNaturePlus'),
    battleNatureMinus: document.getElementById('battleNatureMinus'),
    battlePresetSelect: document.getElementById('battlePresetSelect'),
    battlePresetLoadBtn: document.getElementById('battlePresetLoadBtn'),
    battleIvs: {
        hp: document.getElementById('battleIvHp'),
        atk: document.getElementById('battleIvAtk'),
        def: document.getElementById('battleIvDef'),
        matk: document.getElementById('battleIvMatk'),
        mdef: document.getElementById('battleIvMdef'),
        spd: document.getElementById('battleIvSpd')
    },

    battleDefSearch: document.getElementById('battleDefSearch'),
    battleDefSearchResults: document.getElementById('battleDefSearchResults'),
    battleDefHpRange: document.getElementById('battleDefHpRange'),
    battleDefHpNeutral0: document.getElementById('battleDefHpNeutral0'),
    battleDefHpPlus0: document.getElementById('battleDefHpPlus0'),
    battleDefHpNeutral10: document.getElementById('battleDefHpNeutral10'),
    battleDefHpPlus10: document.getElementById('battleDefHpPlus10'),
    battleDefHpMinus0: document.getElementById('battleDefHpMinus0'),
    battleDefHpMinus10: document.getElementById('battleDefHpMinus10'),

    simplePowerGroup: document.getElementById('simplePowerGroup'),
    complexInputs: document.getElementById('complexInputs'),
    dmgPower: document.getElementById('dmgPower'),
    dmgHits: document.getElementById('dmgHits'),

    cplFinalPower: document.getElementById('cplFinalPower'),
    cplSkillPower: document.getElementById('cplSkillPower'),
    cplResponseMult: document.getElementById('cplResponseMult'),
    cplPowerBonus: document.getElementById('cplPowerBonus'),
    cplAtkUp: document.getElementById('cplAtkUp'),
    cplDefDown: document.getElementById('cplDefDown'),
    cplAtkDown: document.getElementById('cplAtkDown'),
    cplDefUp: document.getElementById('cplDefUp'),
    cplStab: document.getElementById('cplStab'),
    cplTypeEff: document.getElementById('cplTypeEff'),
    cplWeather: document.getElementById('cplWeather'),
    cplMitigation: document.getElementById('cplMitigation'),

    resDmgAll: document.getElementById('resDmgAll'),
    resDmgMinus0: document.getElementById('resDmgMinus0'),
    resDmgMinus10: document.getElementById('resDmgMinus10'),
    resDmgNeutral0: document.getElementById('resDmgNeutral0'),
    resDmgNeutral10: document.getElementById('resDmgNeutral10'),
    resDmgPlus0: document.getElementById('resDmgPlus0'),
    resDmgPlus10: document.getElementById('resDmgPlus10'),

    duelMySearch: document.getElementById('duelMySearch'),
    duelMySearchResults: document.getElementById('duelMySearchResults'),
    duelMyPetInfo: document.getElementById('duelMyPetInfo'),
    duelMyImg: document.getElementById('duelMyImg'),
    duelMyName: document.getElementById('duelMyName'),
    duelMyBaseHp: document.getElementById('duelMyBaseHp'),
    duelMyBaseAtk: document.getElementById('duelMyBaseAtk'),
    duelMyBaseDef: document.getElementById('duelMyBaseDef'),
    duelMyBaseMatk: document.getElementById('duelMyBaseMatk'),
    duelMyBaseMdef: document.getElementById('duelMyBaseMdef'),
    duelMyBaseSpd: document.getElementById('duelMyBaseSpd'),
    duelMyNaturePlus: document.getElementById('duelMyNaturePlus'),
    duelMyNatureMinus: document.getElementById('duelMyNatureMinus'),
    duelMyPresetSelect: document.getElementById('duelMyPresetSelect'),
    duelMyPresetLoadBtn: document.getElementById('duelMyPresetLoadBtn'),
    duelMyIvs: {
        hp: document.getElementById('duelMyIvHp'),
        atk: document.getElementById('duelMyIvAtk'),
        def: document.getElementById('duelMyIvDef'),
        matk: document.getElementById('duelMyIvMatk'),
        mdef: document.getElementById('duelMyIvMdef'),
        spd: document.getElementById('duelMyIvSpd')
    },
    duelMyFinalHp: document.getElementById('duelMyFinalHp'),
    duelMyFinalAtk: document.getElementById('duelMyFinalAtk'),
    duelMyFinalDef: document.getElementById('duelMyFinalDef'),
    duelMyFinalMatk: document.getElementById('duelMyFinalMatk'),
    duelMyFinalMdef: document.getElementById('duelMyFinalMdef'),
    duelMyFinalSpd: document.getElementById('duelMyFinalSpd'),

    duelEnemySearch: document.getElementById('duelEnemySearch'),
    duelEnemySearchResults: document.getElementById('duelEnemySearchResults'),
    duelEnemyPetInfo: document.getElementById('duelEnemyPetInfo'),
    duelEnemyImg: document.getElementById('duelEnemyImg'),
    duelEnemyName: document.getElementById('duelEnemyName'),
    duelEnemyBaseHp: document.getElementById('duelEnemyBaseHp'),
    duelEnemyBaseAtk: document.getElementById('duelEnemyBaseAtk'),
    duelEnemyBaseDef: document.getElementById('duelEnemyBaseDef'),
    duelEnemyBaseMatk: document.getElementById('duelEnemyBaseMatk'),
    duelEnemyBaseMdef: document.getElementById('duelEnemyBaseMdef'),
    duelEnemyBaseSpd: document.getElementById('duelEnemyBaseSpd'),
    duelEnemyNaturePlus: document.getElementById('duelEnemyNaturePlus'),
    duelEnemyNatureMinus: document.getElementById('duelEnemyNatureMinus'),
    duelEnemyPresetSelect: document.getElementById('duelEnemyPresetSelect'),
    duelEnemyPresetLoadBtn: document.getElementById('duelEnemyPresetLoadBtn'),
    duelEnemyIvs: {
        hp: document.getElementById('duelEnemyIvHp'),
        atk: document.getElementById('duelEnemyIvAtk'),
        def: document.getElementById('duelEnemyIvDef'),
        matk: document.getElementById('duelEnemyIvMatk'),
        mdef: document.getElementById('duelEnemyIvMdef'),
        spd: document.getElementById('duelEnemyIvSpd')
    },
    duelEnemyFinalHp: document.getElementById('duelEnemyFinalHp'),
    duelEnemyFinalAtk: document.getElementById('duelEnemyFinalAtk'),
    duelEnemyFinalDef: document.getElementById('duelEnemyFinalDef'),
    duelEnemyFinalMatk: document.getElementById('duelEnemyFinalMatk'),
    duelEnemyFinalMdef: document.getElementById('duelEnemyFinalMdef'),
    duelEnemyFinalSpd: document.getElementById('duelEnemyFinalSpd'),

    duelMyToEnemyPower: document.getElementById('duelMyToEnemyPower'),
    duelMyToEnemyHits: document.getElementById('duelMyToEnemyHits'),
    duelMySimplePowerGroup: document.getElementById('duelMySimplePowerGroup'),
    duelMyComplexInputs: document.getElementById('duelMyComplexInputs'),
    duelMyCplFinalPower: document.getElementById('duelMyCplFinalPower'),
    duelMyCplSkillPower: document.getElementById('duelMyCplSkillPower'),
    duelMyCplResponseMult: document.getElementById('duelMyCplResponseMult'),
    duelMyCplPowerBonus: document.getElementById('duelMyCplPowerBonus'),
    duelMyCplAtkUp: document.getElementById('duelMyCplAtkUp'),
    duelMyCplDefDown: document.getElementById('duelMyCplDefDown'),
    duelMyCplAtkDown: document.getElementById('duelMyCplAtkDown'),
    duelMyCplDefUp: document.getElementById('duelMyCplDefUp'),
    duelMyCplStab: document.getElementById('duelMyCplStab'),
    duelMyCplTypeEff: document.getElementById('duelMyCplTypeEff'),
    duelMyCplWeather: document.getElementById('duelMyCplWeather'),
    duelMyCplMitigation: document.getElementById('duelMyCplMitigation'),
    duelEnemyToMyPower: document.getElementById('duelEnemyToMyPower'),
    duelEnemyToMyHits: document.getElementById('duelEnemyToMyHits'),
    duelEnemySimplePowerGroup: document.getElementById('duelEnemySimplePowerGroup'),
    duelEnemyComplexInputs: document.getElementById('duelEnemyComplexInputs'),
    duelEnemyCplFinalPower: document.getElementById('duelEnemyCplFinalPower'),
    duelEnemyCplSkillPower: document.getElementById('duelEnemyCplSkillPower'),
    duelEnemyCplResponseMult: document.getElementById('duelEnemyCplResponseMult'),
    duelEnemyCplPowerBonus: document.getElementById('duelEnemyCplPowerBonus'),
    duelEnemyCplAtkUp: document.getElementById('duelEnemyCplAtkUp'),
    duelEnemyCplDefDown: document.getElementById('duelEnemyCplDefDown'),
    duelEnemyCplAtkDown: document.getElementById('duelEnemyCplAtkDown'),
    duelEnemyCplDefUp: document.getElementById('duelEnemyCplDefUp'),
    duelEnemyCplStab: document.getElementById('duelEnemyCplStab'),
    duelEnemyCplTypeEff: document.getElementById('duelEnemyCplTypeEff'),
    duelEnemyCplWeather: document.getElementById('duelEnemyCplWeather'),
    duelEnemyCplMitigation: document.getElementById('duelEnemyCplMitigation'),
    duelMyToEnemySingle: document.getElementById('duelMyToEnemySingle'),
    duelMyToEnemyTotal: document.getElementById('duelMyToEnemyTotal'),
    duelMyToEnemyPct: document.getElementById('duelMyToEnemyPct'),
    duelMyToEnemyTurns: document.getElementById('duelMyToEnemyTurns'),
    duelEnemyToMySingle: document.getElementById('duelEnemyToMySingle'),
    duelEnemyToMyTotal: document.getElementById('duelEnemyToMyTotal'),
    duelEnemyToMyPct: document.getElementById('duelEnemyToMyPct'),
    duelEnemyToMyTurns: document.getElementById('duelEnemyToMyTurns')
};

const ATTR_MAP = { hp: 79, atk: 80, matk: 81, def: 82, mdef: 83, spd: 84 };
const ATTR_LABELS = {
    '': '无', hp: '精力', atk: '物攻', def: '物防', matk: '魔攻', mdef: '魔抗', spd: '速度'
};

function pickNumber(obj, keys, fallback = 0) {
    for (const key of keys) {
        const v = obj?.[key];
        if (v !== undefined && v !== null && v !== '') {
            const n = Number(v);
            if (!Number.isNaN(n)) return n;
        }
    }
    return fallback;
}

function normalizePetStats(pet) {
    return {
        ...pet,
        hp: pickNumber(pet, ['hp']),
        attack: pickNumber(pet, ['attack', 'atk']),
        defense: pickNumber(pet, ['defense', 'def']),
        magic_attack: pickNumber(pet, ['magic_attack', 'magicAttack', 'sp_atk', 'spAttack']),
        magic_defense: pickNumber(pet, ['magic_defense', 'magicDefense', 'sp_def', 'spDefense']),
        speed: pickNumber(pet, ['speed', 'spd'])
    };
}

function fmt1(v) {
    const n = Number(v);
    return Number.isFinite(n) ? n.toFixed(1) : '0.0';
}

function resolvePetImageUrl(imageUrl) {
    if (!imageUrl) return '';
    if (/^https?:\/\//i.test(imageUrl)) return imageUrl;
    const clean = String(imageUrl).replace(/^\/+/, '');
    if (clean.startsWith('media/')) return `${IMAGE_BASE}/${clean}`;
    return `${IMAGE_BASE}/media/${clean}`;
}

function debounce(func, wait) {
    let t;
    return (...args) => {
        clearTimeout(t);
        t = setTimeout(() => func.apply(this, args), wait);
    };
}

function getSelectedDamageMode() {
    return document.querySelector('input[name="dmgMode"]:checked')?.value || 'simple';
}

function getSelectedDamageType() {
    return document.querySelector('input[name="dmgType"]:checked')?.value || 'physical';
}

function getSelectedDuelDamageMode(name) {
    return document.querySelector(`input[name="${name}"]:checked`)?.value || 'simple';
}

function setupNatureSelectors(elPlus, elMinus) {
    const options = [
        { value: '', label: '无' },
        { value: 'hp', label: '精力' },
        { value: 'atk', label: '物攻' },
        { value: 'def', label: '物防' },
        { value: 'matk', label: '魔攻' },
        { value: 'mdef', label: '魔抗' },
        { value: 'spd', label: '速度' }
    ];
    const html = options.map(o => `<option value="${o.value}">${o.label}</option>`).join('');
    elPlus.innerHTML = html;
    elMinus.innerHTML = html;
    elPlus.value = '';
    elMinus.value = '';
}

function normalizeNaturePair(elPlus, elMinus, changed) {
    if (elPlus.value && elPlus.value === elMinus.value) {
        if (changed === 'plus') elMinus.value = '';
        else elPlus.value = '';
    }
}

function getIvs(ivsEls) {
    return {
        hp: Number(ivsEls.hp.value) || 0,
        atk: Number(ivsEls.atk.value) || 0,
        def: Number(ivsEls.def.value) || 0,
        matk: Number(ivsEls.matk.value) || 0,
        mdef: Number(ivsEls.mdef.value) || 0,
        spd: Number(ivsEls.spd.value) || 0
    };
}

function setIvs(ivsEls, ivs) {
    for (const key of Object.keys(ivsEls)) {
        const n = Number(ivs?.[key]);
        ivsEls[key].value = Number.isFinite(n) ? String(Math.max(0, Math.min(10, Math.trunc(n)))) : '0';
    }
}

function calcFinalStats(pet, ivs, plusAttr, minusAttr) {
    const star = PVP_STAR_LEVEL;
    const plusId = plusAttr ? ATTR_MAP[plusAttr] : undefined;
    const minusId = minusAttr ? ATTR_MAP[minusAttr] : undefined;

    const getMod = (attrId) => {
        if (plusId === attrId) return 1.1 + star * 0.02;
        if (minusId === attrId) return 0.9;
        return 1.0;
    };

    const talentHP = (1 + star) * 0.85;
    const talentOther = (1 + star) * 0.55;

    const calc = (base, iv, mod, isHp = false) => {
        if (isHp) return (base * 1.7 + iv * talentHP + 70) * mod + 100;
        return (base * 1.1 + iv * talentOther + 10) * mod + 50;
    };

    return {
        hp: calc(pet.hp, ivs.hp, getMod(ATTR_MAP.hp), true),
        atk: calc(pet.attack, ivs.atk, getMod(ATTR_MAP.atk)),
        def: calc(pet.defense, ivs.def, getMod(ATTR_MAP.def)),
        matk: calc(pet.magic_attack, ivs.matk, getMod(ATTR_MAP.matk)),
        mdef: calc(pet.magic_defense, ivs.mdef, getMod(ATTR_MAP.mdef)),
        spd: calc(pet.speed, ivs.spd, getMod(ATTR_MAP.spd))
    };
}

function buildDefRanges(pet) {
    const star = PVP_STAR_LEVEL;
    const talentHP = (1 + star) * 0.85;
    const talentOther = (1 + star) * 0.55;
    const plusMod = 1.1 + star * 0.02;
    const minusMod = 0.9;

    const calcHp = (base, iv, mod) => (base * 1.7 + iv * talentHP + 70) * mod + 100;
    const calcDef = (base, iv, mod) => (base * 1.1 + iv * talentOther + 10) * mod + 50;

    const buildRange = (base, mod, isHp = false) => ({
        min: isHp ? calcHp(base, 0, mod) : calcDef(base, 0, mod),
        max: isHp ? calcHp(base, 10, mod) : calcDef(base, 10, mod)
    });

    return {
        hp: {
            minus: buildRange(pet.hp, minusMod, true),
            neutral: buildRange(pet.hp, 1.0, true),
            plus: buildRange(pet.hp, plusMod, true)
        },
        def: {
            minus: buildRange(pet.defense, minusMod),
            neutral: buildRange(pet.defense, 1.0),
            plus: buildRange(pet.defense, plusMod)
        },
        mdef: {
            minus: buildRange(pet.magic_defense, minusMod),
            neutral: buildRange(pet.magic_defense, 1.0),
            plus: buildRange(pet.magic_defense, plusMod)
        }
    };
}

function calcTurnsRange(hpRange, dmgRange) {
    if (!hpRange || !dmgRange || dmgRange.min <= 0 || dmgRange.max <= 0) return '-';
    const fast = Math.max(1, Math.ceil(hpRange.min / dmgRange.max));
    const slow = Math.max(1, Math.ceil(hpRange.max / dmgRange.min));
    return fast === slow ? `${fast}击` : `${fast}~${slow}击`;
}

function getPresets() {
    try {
        const raw = localStorage.getItem(PRESET_STORAGE_KEY);
        const data = raw ? JSON.parse(raw) : [];
        return Array.isArray(data) ? data : [];
    } catch (e) {
        console.error('读取模板失败:', e);
        return [];
    }
}

function savePresets(list) {
    localStorage.setItem(PRESET_STORAGE_KEY, JSON.stringify(list));
}

function presetLabel(p) {
    return `${p.petName} [加:${ATTR_LABELS[p.naturePlus || '']} 减:${ATTR_LABELS[p.natureMinus || '']}]`;
}

function renderPresetSelects(selectedId = '') {
    const list = getPresets().sort((a, b) => (b.updatedAt || 0) - (a.updatedAt || 0));
    const html = list.length
        ? list.map(p => `<option value="${p.id}">${presetLabel(p)}</option>`).join('')
        : '<option value="">未保存任何配置</option>';
    D.simPresetSelect.innerHTML = html;
    D.battlePresetSelect.innerHTML = html;
    D.duelMyPresetSelect.innerHTML = html;
    D.duelEnemyPresetSelect.innerHTML = html;
    if (selectedId) {
        D.simPresetSelect.value = selectedId;
        D.battlePresetSelect.value = selectedId;
        D.duelMyPresetSelect.value = selectedId;
        D.duelEnemyPresetSelect.value = selectedId;
    }
}

function collectSimConfig() {
    return {
        naturePlus: D.simNaturePlus.value || '',
        natureMinus: D.simNatureMinus.value || '',
        ivs: getIvs(D.simIvs)
    };
}

function applyConfigToSim(cfg) {
    D.simNaturePlus.value = cfg?.naturePlus || '';
    D.simNatureMinus.value = cfg?.natureMinus || '';
    normalizeNaturePair(D.simNaturePlus, D.simNatureMinus, 'minus');
    setIvs(D.simIvs, cfg?.ivs || {});
}

function applyConfigToBattle(cfg) {
    D.battleNaturePlus.value = cfg?.naturePlus || '';
    D.battleNatureMinus.value = cfg?.natureMinus || '';
    normalizeNaturePair(D.battleNaturePlus, D.battleNatureMinus, 'minus');
    setIvs(D.battleIvs, cfg?.ivs || {});
}

function applyConfigToDuelMy(cfg) {
    D.duelMyNaturePlus.value = cfg?.naturePlus || '';
    D.duelMyNatureMinus.value = cfg?.natureMinus || '';
    normalizeNaturePair(D.duelMyNaturePlus, D.duelMyNatureMinus, 'minus');
    setIvs(D.duelMyIvs, cfg?.ivs || {});
}

function applyConfigToDuelEnemy(cfg) {
    D.duelEnemyNaturePlus.value = cfg?.naturePlus || '';
    D.duelEnemyNatureMinus.value = cfg?.natureMinus || '';
    normalizeNaturePair(D.duelEnemyNaturePlus, D.duelEnemyNatureMinus, 'minus');
    setIvs(D.duelEnemyIvs, cfg?.ivs || {});
}

function fillPetCard(pet, refs) {
    if (!pet) return;
    refs.box.classList.remove('hidden');
    refs.img.src = resolvePetImageUrl(pet.imageUrl);
    refs.name.innerText = pet.name;
    refs.baseHp.innerText = pet.hp;
    refs.baseAtk.innerText = pet.attack;
    refs.baseDef.innerText = pet.defense;
    refs.baseMatk.innerText = pet.magic_attack;
    refs.baseMdef.innerText = pet.magic_defense;
    refs.baseSpd.innerText = pet.speed;
}

function setTopTab() {}

let fitTimer = null;

function requestWindowFit() {}

function updateDamageModeUI() {
    const mode = getSelectedDamageMode();
    D.simplePowerGroup.classList.toggle('hidden', mode === 'complex');
    D.complexInputs.classList.toggle('hidden', mode !== 'complex');
    requestWindowFit();
}

function updateDuelDamageModeUI() {
    const myMode = getSelectedDuelDamageMode('duelMyToEnemyMode');
    D.duelMySimplePowerGroup.classList.toggle('hidden', myMode === 'complex');
    D.duelMyComplexInputs.classList.toggle('hidden', myMode !== 'complex');

    const enemyMode = getSelectedDuelDamageMode('duelEnemyToMyMode');
    D.duelEnemySimplePowerGroup.classList.toggle('hidden', enemyMode === 'complex');
    D.duelEnemyComplexInputs.classList.toggle('hidden', enemyMode !== 'complex');

    requestWindowFit();
}

function calcComplexPower(raw) {
    const skillPower = Math.max(0, Number(raw.skillPower) || 0);
    const responseMult = Math.max(0, Number(raw.responseMult) || 0);
    const powerBonus = Number(raw.powerBonus) || 0;
    const atkUp = (Number(raw.atkUp) || 0) / 100;
    const defDown = (Number(raw.defDown) || 0) / 100;
    const atkDown = (Number(raw.atkDown) || 0) / 100;
    const defUp = (Number(raw.defUp) || 0) / 100;
    const stab = Math.max(0, Number(raw.stab) || 0);
    const typeEff = Math.max(0, Number(raw.typeEff) || 0);
    const weather = Math.max(0, Number(raw.weather) || 0);
    const mitigation = Math.min(100, Math.max(0, Number(raw.mitigation) || 0)) / 100;

    const effectivePower = skillPower * responseMult + powerBonus;
    const ratio = (1 + atkUp + defDown) / Math.max(0.01, (1 + atkDown + defUp));
    const power = Math.max(0, effectivePower * ratio * stab * typeEff * weather);
    return {
        power,
        mitigationFactor: 1 - mitigation
    };
}

async function searchPets(keyword) {
    const res = await fetch(`${API_BASE}/pets?keyword=${encodeURIComponent(keyword)}&size=10`);
    const data = await res.json();
    return Array.isArray(data) ? data : (data.content || []);
}

async function fetchPetDetails(id) {
    const res = await fetch(`${API_BASE}/pets/${id}/details`);
    return normalizePetStats(await res.json());
}

function bindSearch(inputEl, resultEl, onSelect) {
    inputEl.addEventListener('input', debounce(async (e) => {
        const keyword = e.target.value.trim();
        if (!keyword) {
            resultEl.style.display = 'none';
            return;
        }
        try {
            const pets = await searchPets(keyword);
            if (!pets.length) {
                resultEl.innerHTML = '<div style="padding:8px;color:#888;">未找到精灵</div>';
                resultEl.style.display = 'block';
                return;
            }
            resultEl.innerHTML = pets.map(p => `
                <div class="search-item" data-id="${p.id}">
                    <img src="${resolvePetImageUrl(p.imageUrl)}" style="width:20px;height:20px;vertical-align:middle;margin-right:5px;">
                    ${p.name}
                </div>
            `).join('');
            resultEl.style.display = 'block';
            resultEl.querySelectorAll('.search-item').forEach(item => {
                item.addEventListener('click', () => {
                    resultEl.style.display = 'none';
                    inputEl.value = item.innerText.trim();
                    onSelect(item.dataset.id);
                });
            });
        } catch (err) {
            console.error('搜索失败:', err);
        }
    }, 300));
}

function updateSimUI() {
    if (!simPet) return;
    const ivs = getIvs(D.simIvs);
    const stats = calcFinalStats(simPet, ivs, D.simNaturePlus.value, D.simNatureMinus.value);
    D.simFinalHp.innerText = fmt1(stats.hp);
    D.simFinalAtk.innerText = fmt1(stats.atk);
    D.simFinalDef.innerText = fmt1(stats.def);
    D.simFinalMatk.innerText = fmt1(stats.matk);
    D.simFinalMdef.innerText = fmt1(stats.mdef);
    D.simFinalSpd.innerText = fmt1(stats.spd);

    const setRange = (key, rangeEl, gapEl) => {
        const ivMin = { ...ivs, [key]: 0 };
        const ivMax = { ...ivs, [key]: 10 };
        const minStats = calcFinalStats(simPet, ivMin, D.simNaturePlus.value, D.simNatureMinus.value);
        const maxStats = calcFinalStats(simPet, ivMax, D.simNaturePlus.value, D.simNatureMinus.value);

        const map = {
            hp: 'hp',
            atk: 'atk',
            def: 'def',
            matk: 'matk',
            mdef: 'mdef',
            spd: 'spd'
        };
        const attr = map[key];
        rangeEl.innerText = `${fmt1(minStats[attr])}~${fmt1(maxStats[attr])}`;
        gapEl.innerText = `+${fmt1(maxStats[attr] - minStats[attr])}`;
    };

    setRange('hp', D.simRangeHp, D.simGapHp);
    setRange('atk', D.simRangeAtk, D.simGapAtk);
    setRange('def', D.simRangeDef, D.simGapDef);
    setRange('matk', D.simRangeMatk, D.simGapMatk);
    setRange('mdef', D.simRangeMdef, D.simGapMdef);
    setRange('spd', D.simRangeSpd, D.simGapSpd);
}

function updateBattleUI() {
    const resetDamageRows = () => {
        D.resDmgAll.innerText = '0~0';
        D.resDmgMinus0.innerText = '0 (0%)';
        D.resDmgMinus10.innerText = '0 (0%)';
        D.resDmgNeutral0.innerText = '0 (0%)';
        D.resDmgNeutral10.innerText = '0 (0%)';
        D.resDmgPlus0.innerText = '0 (0%)';
        D.resDmgPlus10.innerText = '0 (0%)';
    };

    if (!battleDefPet) {
        D.battleDefHpRange.innerText = '0~0';
        D.battleDefHpNeutral0.innerText = '0';
        D.battleDefHpPlus0.innerText = '0';
        D.battleDefHpNeutral10.innerText = '0';
        D.battleDefHpPlus10.innerText = '0';
        D.battleDefHpMinus0.innerText = '0';
        D.battleDefHpMinus10.innerText = '0';
        resetDamageRows();
        return;
    }

    const ranges = buildDefRanges(battleDefPet);
    D.battleDefHpRange.innerText = `${fmt1(ranges.hp.minus.min)}~${fmt1(ranges.hp.plus.max)}`;
    D.battleDefHpNeutral0.innerText = fmt1(ranges.hp.neutral.min);
    D.battleDefHpPlus0.innerText = fmt1(ranges.hp.plus.min);
    D.battleDefHpNeutral10.innerText = fmt1(ranges.hp.neutral.max);
    D.battleDefHpPlus10.innerText = fmt1(ranges.hp.plus.max);
    D.battleDefHpMinus0.innerText = fmt1(ranges.hp.minus.min);
    D.battleDefHpMinus10.innerText = fmt1(ranges.hp.minus.max);

    if (!battleAtkPet) {
        resetDamageRows();
        return;
    }

    const mode = getSelectedDamageMode();
    const dmgType = getSelectedDamageType();
    const hits = Math.max(1, Math.trunc(Number(D.dmgHits.value) || 1));

    let power = Number(D.dmgPower.value) || 0;
    let mitigationFactor = 1;
    if (mode === 'complex') {
        const cpl = calcComplexPower({
            skillPower: D.cplSkillPower.value,
            responseMult: D.cplResponseMult.value,
            powerBonus: D.cplPowerBonus.value,
            atkUp: D.cplAtkUp.value,
            defDown: D.cplDefDown.value,
            atkDown: D.cplAtkDown.value,
            defUp: D.cplDefUp.value,
            stab: D.cplStab.value,
            typeEff: D.cplTypeEff.value,
            weather: D.cplWeather.value,
            mitigation: D.cplMitigation.value
        });
        power = cpl.power;
        mitigationFactor = cpl.mitigationFactor;
    }
    D.cplFinalPower.innerText = fmt1(power);

    const atkStats = calcFinalStats(battleAtkPet, getIvs(D.battleIvs), D.battleNaturePlus.value, D.battleNatureMinus.value);
    const atkValue = dmgType === 'physical' ? atkStats.atk : atkStats.matk;
    const defByNature = dmgType === 'physical' ? ranges.def : ranges.mdef;

    const calc = (atk, def, pwr) => (atk / def) * 0.9 * pwr * mitigationFactor * hits;

    const toDmgRange = (range) => ({
        min: calc(atkValue, range.max, power),
        max: calc(atkValue, range.min, power)
    });

    const minusDmg = toDmgRange(defByNature.minus);
    const neutralDmg = toDmgRange(defByNature.neutral);
    const plusDmg = toDmgRange(defByNature.plus);

    const dmgMin = Math.min(minusDmg.min, neutralDmg.min, plusDmg.min);
    const dmgMax = Math.max(minusDmg.max, neutralDmg.max, plusDmg.max);

    const toPointText = (hp, dmg) => {
        const pct = hp > 0 ? (dmg / hp) * 100 : 0;
        return `${fmt1(dmg)} (${fmt1(pct)}%)`;
    };

    D.resDmgMinus0.innerText = toPointText(ranges.hp.minus.min, minusDmg.max);
    D.resDmgMinus10.innerText = toPointText(ranges.hp.minus.max, minusDmg.min);
    D.resDmgNeutral0.innerText = toPointText(ranges.hp.neutral.min, neutralDmg.max);
    D.resDmgNeutral10.innerText = toPointText(ranges.hp.neutral.max, neutralDmg.min);
    D.resDmgPlus0.innerText = toPointText(ranges.hp.plus.min, plusDmg.max);
    D.resDmgPlus10.innerText = toPointText(ranges.hp.plus.max, plusDmg.min);

    D.resDmgAll.innerText = `${fmt1(dmgMin)}~${fmt1(dmgMax)}`;
}

function getCheckedRadio(name, fallback = 'physical') {
    return document.querySelector(`input[name="${name}"]:checked`)?.value || fallback;
}

function calcDuelDirection(attackerPet, attackerIvsEls, attackerPlusEl, attackerMinusEl, defenderPet, defenderIvsEls, defenderPlusEl, defenderMinusEl, dmgType, power, hits, mitigationFactor = 1) {
    if (!attackerPet || !defenderPet) return null;

    const atkStats = calcFinalStats(
        attackerPet,
        getIvs(attackerIvsEls),
        attackerPlusEl.value,
        attackerMinusEl.value
    );
    const defStats = calcFinalStats(
        defenderPet,
        getIvs(defenderIvsEls),
        defenderPlusEl.value,
        defenderMinusEl.value
    );

    const atkVal = dmgType === 'physical' ? atkStats.atk : atkStats.matk;
    const defVal = Math.max(1, dmgType === 'physical' ? defStats.def : defStats.mdef);
    const p = Math.max(0, Number(power) || 0);
    const h = Math.max(1, Math.trunc(Number(hits) || 1));

    const single = (atkVal / defVal) * 0.9 * p * mitigationFactor;
    const total = single * h;
    const pct = defStats.hp > 0 ? (total / defStats.hp) * 100 : 0;
    const turns = total > 0 ? Math.max(1, Math.ceil(defStats.hp / total)) : 0;

    return {
        single,
        total,
        pct,
        turns: turns > 0 ? `${turns}击` : '-'
    };
}

function updateDuelUI() {
    const renderFinalStats = (pet, ivEls, plusEl, minusEl, refs) => {
        if (!pet) {
            refs.hp.innerText = '0.0';
            refs.atk.innerText = '0.0';
            refs.def.innerText = '0.0';
            refs.matk.innerText = '0.0';
            refs.mdef.innerText = '0.0';
            refs.spd.innerText = '0.0';
            return;
        }
        const stats = calcFinalStats(pet, getIvs(ivEls), plusEl.value, minusEl.value);
        refs.hp.innerText = fmt1(stats.hp);
        refs.atk.innerText = fmt1(stats.atk);
        refs.def.innerText = fmt1(stats.def);
        refs.matk.innerText = fmt1(stats.matk);
        refs.mdef.innerText = fmt1(stats.mdef);
        refs.spd.innerText = fmt1(stats.spd);
    };

    renderFinalStats(duelMyPet, D.duelMyIvs, D.duelMyNaturePlus, D.duelMyNatureMinus, {
        hp: D.duelMyFinalHp,
        atk: D.duelMyFinalAtk,
        def: D.duelMyFinalDef,
        matk: D.duelMyFinalMatk,
        mdef: D.duelMyFinalMdef,
        spd: D.duelMyFinalSpd
    });

    renderFinalStats(duelEnemyPet, D.duelEnemyIvs, D.duelEnemyNaturePlus, D.duelEnemyNatureMinus, {
        hp: D.duelEnemyFinalHp,
        atk: D.duelEnemyFinalAtk,
        def: D.duelEnemyFinalDef,
        matk: D.duelEnemyFinalMatk,
        mdef: D.duelEnemyFinalMdef,
        spd: D.duelEnemyFinalSpd
    });

    const reset = () => {
        D.duelMyToEnemySingle.innerText = '0.0';
        D.duelMyToEnemyTotal.innerText = '0.0';
        D.duelMyToEnemyPct.innerText = '0.0%';
        D.duelMyToEnemyTurns.innerText = '-';
        D.duelEnemyToMySingle.innerText = '0.0';
        D.duelEnemyToMyTotal.innerText = '0.0';
        D.duelEnemyToMyPct.innerText = '0.0%';
        D.duelEnemyToMyTurns.innerText = '-';
    };

    if (!duelMyPet || !duelEnemyPet) {
        D.duelMyCplFinalPower.innerText = '0.0';
        D.duelEnemyCplFinalPower.innerText = '0.0';
        reset();
        return;
    }

    const myMode = getSelectedDuelDamageMode('duelMyToEnemyMode');
    const enemyMode = getSelectedDuelDamageMode('duelEnemyToMyMode');

    let myPower = Number(D.duelMyToEnemyPower.value) || 0;
    let myMitigationFactor = 1;
    if (myMode === 'complex') {
        const cpl = calcComplexPower({
            skillPower: D.duelMyCplSkillPower.value,
            responseMult: D.duelMyCplResponseMult.value,
            powerBonus: D.duelMyCplPowerBonus.value,
            atkUp: D.duelMyCplAtkUp.value,
            defDown: D.duelMyCplDefDown.value,
            atkDown: D.duelMyCplAtkDown.value,
            defUp: D.duelMyCplDefUp.value,
            stab: D.duelMyCplStab.value,
            typeEff: D.duelMyCplTypeEff.value,
            weather: D.duelMyCplWeather.value,
            mitigation: D.duelMyCplMitigation.value
        });
        myPower = cpl.power;
        myMitigationFactor = cpl.mitigationFactor;
    }
    D.duelMyCplFinalPower.innerText = fmt1(myPower);

    let enemyPower = Number(D.duelEnemyToMyPower.value) || 0;
    let enemyMitigationFactor = 1;
    if (enemyMode === 'complex') {
        const cpl = calcComplexPower({
            skillPower: D.duelEnemyCplSkillPower.value,
            responseMult: D.duelEnemyCplResponseMult.value,
            powerBonus: D.duelEnemyCplPowerBonus.value,
            atkUp: D.duelEnemyCplAtkUp.value,
            defDown: D.duelEnemyCplDefDown.value,
            atkDown: D.duelEnemyCplAtkDown.value,
            defUp: D.duelEnemyCplDefUp.value,
            stab: D.duelEnemyCplStab.value,
            typeEff: D.duelEnemyCplTypeEff.value,
            weather: D.duelEnemyCplWeather.value,
            mitigation: D.duelEnemyCplMitigation.value
        });
        enemyPower = cpl.power;
        enemyMitigationFactor = cpl.mitigationFactor;
    }
    D.duelEnemyCplFinalPower.innerText = fmt1(enemyPower);

    const myToEnemy = calcDuelDirection(
        duelMyPet,
        D.duelMyIvs,
        D.duelMyNaturePlus,
        D.duelMyNatureMinus,
        duelEnemyPet,
        D.duelEnemyIvs,
        D.duelEnemyNaturePlus,
        D.duelEnemyNatureMinus,
        getCheckedRadio('duelMyToEnemyType', 'physical'),
        myPower,
        D.duelMyToEnemyHits.value,
        myMitigationFactor
    );

    const enemyToMy = calcDuelDirection(
        duelEnemyPet,
        D.duelEnemyIvs,
        D.duelEnemyNaturePlus,
        D.duelEnemyNatureMinus,
        duelMyPet,
        D.duelMyIvs,
        D.duelMyNaturePlus,
        D.duelMyNatureMinus,
        getCheckedRadio('duelEnemyToMyType', 'physical'),
        enemyPower,
        D.duelEnemyToMyHits.value,
        enemyMitigationFactor
    );

    if (!myToEnemy || !enemyToMy) {
        reset();
        return;
    }

    D.duelMyToEnemySingle.innerText = fmt1(myToEnemy.single);
    D.duelMyToEnemyTotal.innerText = fmt1(myToEnemy.total);
    D.duelMyToEnemyPct.innerText = `${fmt1(myToEnemy.pct)}%`;
    D.duelMyToEnemyTurns.innerText = myToEnemy.turns;

    D.duelEnemyToMySingle.innerText = fmt1(enemyToMy.single);
    D.duelEnemyToMyTotal.innerText = fmt1(enemyToMy.total);
    D.duelEnemyToMyPct.innerText = `${fmt1(enemyToMy.pct)}%`;
    D.duelEnemyToMyTurns.innerText = enemyToMy.turns;
}

function updateAll() {
    updateSimUI();
    updateBattleUI();
    updateDuelUI();
}

function setupEventListeners() {
    
    
    

    bindSearch(D.simSearch, D.simSearchResults, async (id) => {
        simPet = await fetchPetDetails(id);
        fillPetCard(simPet, {
            box: D.simPetInfo,
            img: D.simPetImg,
            name: D.simPetName,
            baseHp: D.simBaseHp,
            baseAtk: D.simBaseAtk,
            baseDef: D.simBaseDef,
            baseMatk: D.simBaseMatk,
            baseMdef: D.simBaseMdef,
            baseSpd: D.simBaseSpd
        });
        updateAll();
    });

    bindSearch(D.battleAtkSearch, D.battleAtkSearchResults, async (id) => {
        battleAtkPet = await fetchPetDetails(id);
        D.battleNaturePlus.value = '';
        D.battleNatureMinus.value = '';
        setIvs(D.battleIvs, { hp: 0, atk: 0, def: 0, matk: 0, mdef: 0, spd: 0 });
        updateAll();
    });

    bindSearch(D.battleDefSearch, D.battleDefSearchResults, async (id) => {
        battleDefPet = await fetchPetDetails(id);
        updateAll();
    });

    bindSearch(D.duelMySearch, D.duelMySearchResults, async (id) => {
        duelMyPet = await fetchPetDetails(id);
        fillPetCard(duelMyPet, {
            box: D.duelMyPetInfo,
            img: D.duelMyImg,
            name: D.duelMyName,
            baseHp: D.duelMyBaseHp,
            baseAtk: D.duelMyBaseAtk,
            baseDef: D.duelMyBaseDef,
            baseMatk: D.duelMyBaseMatk,
            baseMdef: D.duelMyBaseMdef,
            baseSpd: D.duelMyBaseSpd
        });
        updateAll();
    });

    bindSearch(D.duelEnemySearch, D.duelEnemySearchResults, async (id) => {
        duelEnemyPet = await fetchPetDetails(id);
        fillPetCard(duelEnemyPet, {
            box: D.duelEnemyPetInfo,
            img: D.duelEnemyImg,
            name: D.duelEnemyName,
            baseHp: D.duelEnemyBaseHp,
            baseAtk: D.duelEnemyBaseAtk,
            baseDef: D.duelEnemyBaseDef,
            baseMatk: D.duelEnemyBaseMatk,
            baseMdef: D.duelEnemyBaseMdef,
            baseSpd: D.duelEnemyBaseSpd
        });
        updateAll();
    });

    document.addEventListener('click', (e) => {
        [
            [D.simSearch, D.simSearchResults],
            [D.battleAtkSearch, D.battleAtkSearchResults],
            [D.battleDefSearch, D.battleDefSearchResults],
            [D.duelMySearch, D.duelMySearchResults],
            [D.duelEnemySearch, D.duelEnemySearchResults]
        ].forEach(([input, box]) => {
            if (!input.contains(e.target) && !box.contains(e.target)) box.style.display = 'none';
        });
    });

    D.simNaturePlus.addEventListener('change', () => { normalizeNaturePair(D.simNaturePlus, D.simNatureMinus, 'plus'); updateAll(); });
    D.simNatureMinus.addEventListener('change', () => { normalizeNaturePair(D.simNaturePlus, D.simNatureMinus, 'minus'); updateAll(); });
    D.battleNaturePlus.addEventListener('change', () => { normalizeNaturePair(D.battleNaturePlus, D.battleNatureMinus, 'plus'); updateAll(); });
    D.battleNatureMinus.addEventListener('change', () => { normalizeNaturePair(D.battleNaturePlus, D.battleNatureMinus, 'minus'); updateAll(); });
    D.duelMyNaturePlus.addEventListener('change', () => { normalizeNaturePair(D.duelMyNaturePlus, D.duelMyNatureMinus, 'plus'); updateAll(); });
    D.duelMyNatureMinus.addEventListener('change', () => { normalizeNaturePair(D.duelMyNaturePlus, D.duelMyNatureMinus, 'minus'); updateAll(); });
    D.duelEnemyNaturePlus.addEventListener('change', () => { normalizeNaturePair(D.duelEnemyNaturePlus, D.duelEnemyNatureMinus, 'plus'); updateAll(); });
    D.duelEnemyNatureMinus.addEventListener('change', () => { normalizeNaturePair(D.duelEnemyNaturePlus, D.duelEnemyNatureMinus, 'minus'); updateAll(); });

    Object.values(D.simIvs).forEach(el => el.addEventListener('input', updateAll));
    Object.values(D.battleIvs).forEach(el => el.addEventListener('input', updateAll));
    Object.values(D.duelMyIvs).forEach(el => el.addEventListener('input', updateAll));
    Object.values(D.duelEnemyIvs).forEach(el => el.addEventListener('input', updateAll));

    ['dmgMode', 'dmgType'].forEach(name => {
        document.querySelectorAll(`input[name="${name}"]`).forEach(el => el.addEventListener('change', () => {
            updateDamageModeUI();
            updateAll();
        }));
    });

    [
        D.dmgPower, D.dmgHits,
        D.cplSkillPower, D.cplResponseMult, D.cplPowerBonus,
        D.cplAtkUp, D.cplDefDown, D.cplAtkDown, D.cplDefUp,
        D.cplStab, D.cplTypeEff, D.cplWeather, D.cplMitigation
    ].forEach(el => el.addEventListener('input', updateAll));

    [
        D.duelMyToEnemyPower,
        D.duelMyToEnemyHits,
        D.duelEnemyToMyPower,
        D.duelEnemyToMyHits
    ].forEach(el => el.addEventListener('input', updateAll));

    ['duelMyToEnemyType', 'duelEnemyToMyType'].forEach(name => {
        document.querySelectorAll(`input[name="${name}"]`).forEach(el => {
            el.addEventListener('change', updateAll);
        });
    });

    ['duelMyToEnemyMode', 'duelEnemyToMyMode'].forEach(name => {
        document.querySelectorAll(`input[name="${name}"]`).forEach(el => {
            el.addEventListener('change', () => {
                updateDuelDamageModeUI();
                updateAll();
            });
        });
    });

    D.simPresetSaveBtn.addEventListener('click', () => {
        if (!simPet) {
            alert('请先在 Tab1 选择精灵后再保存模板。');
            return;
        }
        const list = getPresets();
        const existing = list.find(p => p.petId === simPet.id);
        const payload = {
            id: existing?.id || `pet-${simPet.id}`,
            petId: simPet.id,
            petName: simPet.name,
            naturePlus: D.simNaturePlus.value || '',
            natureMinus: D.simNatureMinus.value || '',
            ivs: getIvs(D.simIvs),
            updatedAt: Date.now()
        };
        const next = existing ? list.map(p => p.id === existing.id ? payload : p) : [...list, payload];
        savePresets(next);
        renderPresetSelects(payload.id);
    });


        [
            D.duelMyCplSkillPower, D.duelMyCplResponseMult, D.duelMyCplPowerBonus,
            D.duelMyCplAtkUp, D.duelMyCplDefDown, D.duelMyCplAtkDown, D.duelMyCplDefUp,
            D.duelMyCplStab, D.duelMyCplTypeEff, D.duelMyCplWeather, D.duelMyCplMitigation,
            D.duelEnemyCplSkillPower, D.duelEnemyCplResponseMult, D.duelEnemyCplPowerBonus,
            D.duelEnemyCplAtkUp, D.duelEnemyCplDefDown, D.duelEnemyCplAtkDown, D.duelEnemyCplDefUp,
            D.duelEnemyCplStab, D.duelEnemyCplTypeEff, D.duelEnemyCplWeather, D.duelEnemyCplMitigation
        ].forEach(el => el.addEventListener('input', updateAll));
    D.simPresetLoadBtn.addEventListener('click', async () => {
        const preset = getPresets().find(p => p.id === D.simPresetSelect.value);
        if (!preset) return;
        if (!simPet || simPet.id !== preset.petId) {
            simPet = await fetchPetDetails(preset.petId);
            fillPetCard(simPet, {
                box: D.simPetInfo,
                img: D.simPetImg,
                name: D.simPetName,
                baseHp: D.simBaseHp,
                baseAtk: D.simBaseAtk,
                baseDef: D.simBaseDef,
                baseMatk: D.simBaseMatk,
                baseMdef: D.simBaseMdef,
                baseSpd: D.simBaseSpd
            });
        }
        applyConfigToSim(preset);
        updateAll();
    });

    D.simPresetDeleteBtn.addEventListener('click', () => {
        const id = D.simPresetSelect.value;
        if (!id) return;
        const next = getPresets().filter(p => p.id !== id);
        savePresets(next);
        renderPresetSelects();
    });

    D.battlePresetLoadBtn.addEventListener('click', async () => {
        const preset = getPresets().find(p => p.id === D.battlePresetSelect.value);
        if (!preset) return;
        if (!battleAtkPet || battleAtkPet.id !== preset.petId) {
            battleAtkPet = await fetchPetDetails(preset.petId);
            D.battleAtkSearch.value = preset.petName;
        }
        applyConfigToBattle(preset);
        updateAll();
    });

    D.duelMyPresetLoadBtn.addEventListener('click', async () => {
        const preset = getPresets().find(p => p.id === D.duelMyPresetSelect.value);
        if (!preset) return;
        if (!duelMyPet || duelMyPet.id !== preset.petId) {
            duelMyPet = await fetchPetDetails(preset.petId);
            D.duelMySearch.value = preset.petName;
            fillPetCard(duelMyPet, {
                box: D.duelMyPetInfo,
                img: D.duelMyImg,
                name: D.duelMyName,
                baseHp: D.duelMyBaseHp,
                baseAtk: D.duelMyBaseAtk,
                baseDef: D.duelMyBaseDef,
                baseMatk: D.duelMyBaseMatk,
                baseMdef: D.duelMyBaseMdef,
                baseSpd: D.duelMyBaseSpd
            });
        }
        applyConfigToDuelMy(preset);
        updateAll();
    });

    D.duelEnemyPresetLoadBtn.addEventListener('click', async () => {
        const preset = getPresets().find(p => p.id === D.duelEnemyPresetSelect.value);
        if (!preset) return;
        if (!duelEnemyPet || duelEnemyPet.id !== preset.petId) {
            duelEnemyPet = await fetchPetDetails(preset.petId);
            D.duelEnemySearch.value = preset.petName;
            fillPetCard(duelEnemyPet, {
                box: D.duelEnemyPetInfo,
                img: D.duelEnemyImg,
                name: D.duelEnemyName,
                baseHp: D.duelEnemyBaseHp,
                baseAtk: D.duelEnemyBaseAtk,
                baseDef: D.duelEnemyBaseDef,
                baseMatk: D.duelEnemyBaseMatk,
                baseMdef: D.duelEnemyBaseMdef,
                baseSpd: D.duelEnemyBaseSpd
            });
        }
        applyConfigToDuelEnemy(preset);
        updateAll();
    });
}

function init() {
    setupNatureSelectors(D.simNaturePlus, D.simNatureMinus);
    setupNatureSelectors(D.battleNaturePlus, D.battleNatureMinus);
    setupNatureSelectors(D.duelMyNaturePlus, D.duelMyNatureMinus);
    setupNatureSelectors(D.duelEnemyNaturePlus, D.duelEnemyNatureMinus);
    renderPresetSelects();
    
    updateDamageModeUI();
    updateDuelDamageModeUI();
    setupEventListeners();
    updateAll();
    requestWindowFit();
}

init();
