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
async function loadBuffs() {
    const grid = document.getElementById('buffGrid');
    if (!grid) return;
    grid.innerHTML = '<div class="loading-spinner">加载中...</div>';
    try {
        const searchInput = document.getElementById('buffSearch');
        const typeSelect = document.getElementById('buffTypeFilter');
        const search = searchInput ? searchInput.value : '';
        const type = typeSelect ? typeSelect.value : '';
        
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
    
    const buffTypeFilter = document.getElementById('buffTypeFilter');
    if (buffTypeFilter) {
        buffTypeFilter.onchange = () => loadBuffs();
    }

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

// --- Stat Simulator Logic (Hardened with Unicode Escapes) ---
let currentSimPet = null;
let natureData = [];
let isSyncingNatureControls = false;
const SIM_ATTRS = [
    { key: 'hp', label: '\u7cbe\u529b', attrId: 79 },
    { key: 'atk', label: '\u653b\u51fb', attrId: 80 },
    { key: 'mag_atk', label: '\u9b54\u653b', attrId: 81 },
    { key: 'def', label: '\u9632\u5fa1', attrId: 82 },
    { key: 'mag_def', label: '\u9b54\u6297', attrId: 83 },
    { key: 'spd', label: '\u901f\u5ea6', attrId: 84 }
];
const DEFAULT_MINUS_RATE = 0.9;

function getDefaultPlusRateByStar(starLevel) {
    const safeStar = Math.max(0, Math.min(5, Number.isFinite(starLevel) ? starLevel : 5));
    return Number((1.1 + safeStar * 0.02).toFixed(2));
}

function clampStarLevel(value) {
    const num = parseInt(value, 10);
    if (Number.isNaN(num)) return 5;
    return Math.max(0, Math.min(5, num));
}

function getAttrLabelById(attrId) {
    const attr = SIM_ATTRS.find(item => item.attrId === Number(attrId));
    return attr ? attr.label : '\u65e0';
}

function populateModifierSelects() {
    const plusSelect = document.getElementById('simPlusAttr');
    const minusSelect = document.getElementById('simMinusAttr');
    if (!plusSelect || !minusSelect) return;

    const options = SIM_ATTRS.map(attr => `<option value="${attr.attrId}">${attr.label}</option>`).join('');
    plusSelect.innerHTML = options;
    minusSelect.innerHTML = options;
    plusSelect.value = '80';
    minusSelect.value = '81';
}

function buildNatureOptionLabel(nature) {
    const plus = nature.plusAttrName || getAttrLabelById(nature.plus_attr_id) || '\u65e0';
    const minus = nature.minusAttrName || getAttrLabelById(nature.minus_attr_id) || '\u65e0';
    return `${nature.name} (\u52a0${plus} / \u51cf${minus})`;
}

function getSelectedModifiers() {
    const starInput = document.getElementById('simStarLevel');
    const plusSelect = document.getElementById('simPlusAttr');
    const minusSelect = document.getElementById('simMinusAttr');
    const plusRateSelect = document.getElementById('simPlusRate');

    const starLevel = clampStarLevel(starInput ? starInput.value : 5);
    const plusAttrId = parseInt(plusSelect ? plusSelect.value : 80, 10);
    const minusAttrId = parseInt(minusSelect ? minusSelect.value : 81, 10);
    const plusRate = Number(plusRateSelect ? plusRateSelect.value : getDefaultPlusRateByStar(starLevel));

    return {
        starLevel,
        plusAttrId,
        minusAttrId,
        plusRate,
        minusRate: DEFAULT_MINUS_RATE
    };
}

function syncSummary(modifiers) {
    const summary = document.getElementById('simSummary');
    if (!summary) return;
    const plusLabel = getAttrLabelById(modifiers.plusAttrId);
    const minusLabel = getAttrLabelById(modifiers.minusAttrId);
    summary.textContent = `当前配置：${modifiers.starLevel}星 / ${plusLabel} +${Math.round((modifiers.plusRate - 1) * 100)}% / ${minusLabel} -10%`;
}

function findMatchingNature(plusAttrId, minusAttrId) {
    return natureData.find(n => {
        return Number(n.plus_attr_id) === Number(plusAttrId)
            && Number(n.minus_attr_id) === Number(minusAttrId);
    });
}

function getNatureSelectLabelByValue(value) {
    const select = document.getElementById('simNature');
    if (!select) return '';
    const option = Array.from(select.options).find(item => item.value === String(value));
    return option ? option.textContent : '';
}

function refreshNatureSelectState() {
    const select = document.getElementById('simNature');
    if (!select) return;
    const modifiers = getSelectedModifiers();
    const matchedNature = findMatchingNature(modifiers.plusAttrId, modifiers.minusAttrId);
    const targetValue = matchedNature ? String(matchedNature.id) : 'custom';

    if (!Array.from(select.options).some(option => option.value === targetValue)) {
        return;
    }

    select.value = targetValue;
    if (select.value !== targetValue) {
        select.selectedIndex = Array.from(select.options).findIndex(option => option.value === targetValue);
    }
}

function renderSimBaseStats(base) {
    const container = document.getElementById('simBaseStats');
    if (!container) return;

    if (!base) {
        container.innerHTML = '<div class="sim-base-placeholder">选择宠物后展示种族值：精力 / 攻击 / 防御 / 魔攻 / 魔抗 / 速度</div>';
        return;
    }

    const labels = {
        hp: '\u7cbe\u529b',
        atk: '\u653b\u51fb',
        def: '\u9632\u5fa1',
        mag_atk: '\u9b54\u653b',
        mag_def: '\u9b54\u6297',
        spd: '\u901f\u5ea6'
    };

    container.innerHTML = Object.entries(base).map(([key, value]) => `
        <div class="sim-base-stat-card">
            <strong>${value}</strong>
            <span>${labels[key]}</span>
        </div>
    `).join('');
}

function syncNatureFromManualControls() {
    if (isSyncingNatureControls) return;
    const select = document.getElementById('simNature');
    if (!select) return;

    isSyncingNatureControls = true;
    refreshNatureSelectState();
    isSyncingNatureControls = false;
}

function syncManualControlsFromNature() {
    const select = document.getElementById('simNature');
    const plusSelect = document.getElementById('simPlusAttr');
    const minusSelect = document.getElementById('simMinusAttr');
    if (!select || !plusSelect || !minusSelect) return;

    if (select.value === 'custom') {
        syncSummary(getSelectedModifiers());
        return;
    }

    const natureId = parseInt(select.value, 10);
    const nature = natureData.find(n => n.id === natureId);
    if (!nature) return;

    isSyncingNatureControls = true;
    plusSelect.value = String(nature.plus_attr_id);
    minusSelect.value = String(nature.minus_attr_id);
    isSyncingNatureControls = false;
    syncSummary(getSelectedModifiers());
}

async function initNatures() {
    try {
        const response = await fetch(`${API_BASE}/natures`);
        natureData = await response.json();
        populateModifierSelects();
        const select = document.getElementById('simNature');
        if (select) {
            select.innerHTML = natureData.map(n => `<option value="${n.id}">${buildNatureOptionLabel(n)}</option>`).join('')
                + `<option value="custom">\u81ea\u5b9a\u4e49\u4fee\u6b63</option>`;
            select.value = "1";
            syncManualControlsFromNature();
        }
        setupSimListeners();
    } catch (e) {
        console.error("Failed to load natures:", e);
    }
}

function setupSimListeners() {
    const simSearchInput = document.getElementById('simPetSearch');
    const simResults = document.getElementById('simPetResults');

    if (simSearchInput) {
        simSearchInput.addEventListener('input', debounce(async (e) => {
            const kw = e.target.value.trim();
            if (kw.length < 1) {
                simResults.style.display = 'none';
                return;
            }
            const res = await fetch(`${API_BASE}/pets?keyword=${encodeURIComponent(kw)}&size=5`);
            const pets = await res.json();
            if (pets.length > 0) {
                simResults.innerHTML = pets.map(p => `
                    <div class="sim-result-item" onclick="selectSimPet(${p.id})">
                        <img src="${MEDIA_BASE}${p.imageUrl || `pets/JL_${p.name}.png`}" alt="${p.name}" onerror="this.style.display='none'; this.nextElementSibling.style.display='inline-block';">
                        <span>${p.name}</span>
                    </div>
                `).join('');
                simResults.style.display = 'block';
            } else {
                simResults.style.display = 'none';
            }
        }, 300));

        document.addEventListener('click', (e) => {
            if (!simResults.contains(e.target) && e.target !== simSearchInput) {
                simResults.style.display = 'none';
            }
        });
    }

    const ivIds = ['hp', 'atk', 'def', 'mag_atk', 'mag_def', 'spd'];
    ivIds.forEach(id => {
        const el = document.getElementById('iv_' + id);
        if (el) {
            el.addEventListener('input', (e) => {
                document.getElementById('val_' + id).textContent = e.target.value;
                updateSimulator();
            });
        }
    });

    const simStarLevel = document.getElementById('simStarLevel');
    const simPlusAttr = document.getElementById('simPlusAttr');
    const simMinusAttr = document.getElementById('simMinusAttr');
    const simPlusRate = document.getElementById('simPlusRate');

    if (simStarLevel) {
        simStarLevel.addEventListener('input', (e) => {
            const starLevel = clampStarLevel(e.target.value);
            e.target.value = starLevel;
            updateSimulator();
        });
    }

    [simPlusAttr, simMinusAttr].forEach(el => {
        if (!el) return;
        el.addEventListener('change', () => {
            syncNatureFromManualControls();
            updateSimulator();
        });
    });

    if (simPlusRate) {
        simPlusRate.addEventListener('change', () => {
            syncSummary(getSelectedModifiers());
            updateSimulator();
        });
    }

    const simNatureSelect = document.getElementById('simNature');
    if (simNatureSelect) {
        simNatureSelect.addEventListener('change', () => {
            if (!isSyncingNatureControls) {
                syncManualControlsFromNature();
            }
            updateSimulator();
        });
    }
}

window.selectSimPet = async (id) => {
    const res = await fetch(`${API_BASE}/pets/${id}/details`);
    currentSimPet = await res.json();
    document.getElementById('simPetSearch').value = currentSimPet.name;
    document.getElementById('simPetResults').style.display = 'none';
    updateSimulator();
};

function updateSimulator() {
    const modifiers = getSelectedModifiers();
    refreshNatureSelectState();
    syncSummary(modifiers);
    if (!currentSimPet) return;

    const ivs = {
        hp: parseInt(document.getElementById('iv_hp').value),
        atk: parseInt(document.getElementById('iv_atk').value),
        def: parseInt(document.getElementById('iv_def').value),
        mag_atk: parseInt(document.getElementById('iv_mag_atk').value),
        mag_def: parseInt(document.getElementById('iv_mag_def').value),
        spd: parseInt(document.getElementById('iv_spd').value)
    };

    const base = {
        hp: currentSimPet.hp || 0,
        atk: currentSimPet.attack || 0,
        def: currentSimPet.defense || 0,
        mag_atk: currentSimPet.magic_attack || currentSimPet.sp_atk || 0,
        mag_def: currentSimPet.magic_defense || currentSimPet.sp_def || 0,
        spd: currentSimPet.speed || 0
    };
    renderSimBaseStats(base);

    const getMod = (attrId) => {
        if (modifiers.plusAttrId === attrId) return modifiers.plusRate;
        if (modifiers.minusAttrId === attrId) return modifiers.minusRate;
        return 1.0;
    };

    // 生命: (1+星级) × 0.85，其他: (1+星级) × 0.55
    const talentModHP = (1 + modifiers.starLevel) * 0.85;
    const talentModOther = (1 + modifiers.starLevel) * 0.55;

    const results = {
        hp: Math.round((base.hp * 1.7 + ivs.hp * talentModHP + 70) * getMod(79) + 100),
        atk: Math.round((base.atk * 1.1 + ivs.atk * talentModOther + 10) * getMod(80) + 50),
        mag_atk: Math.round((base.mag_atk * 1.1 + ivs.mag_atk * talentModOther + 10) * getMod(81) + 50),
        def: Math.round((base.def * 1.1 + ivs.def * talentModOther + 10) * getMod(82) + 50),
        mag_def: Math.round((base.mag_def * 1.1 + ivs.mag_def * talentModOther + 10) * getMod(83) + 50),
        spd: Math.round((base.spd * 1.1 + ivs.spd * talentModOther + 10) * getMod(84) + 50)
    };

    renderSimResults(results);
}

function renderSimResults(res) {
    const container = document.getElementById('simStatsResult');
    const labels = {
        hp: '\u7cbe\u529b', atk: '\u653b\u51fb', def: '\u9632\u5fa1', // 精力, 攻击, 防御
        mag_atk: '\u9b54\u653b', mag_def: '\u9b54\u6297', spd: '\u901f\u5ea6' // 魔攻, 魔抗, 速度
    };

    container.innerHTML = Object.keys(res).map(key => `
        <div class="stat-card-sim">
            <span class="stat-val-sim">${res[key]}</span>
            <span class="stat-label-sim">${labels[key]}</span>
        </div>
    `).join('') + `
        <div class="sim-export-btns">
            <button class="dmg-mode-btn" onclick="exportSimToAttacker()">导出为攻击方</button>
            <button class="dmg-mode-btn" onclick="exportSimToDefender()">导出为防御方</button>
        </div>`;
}

// debounce 已提取到 js/api.js

// ===== 伤害计算器 =====
let dmgCalcInited = false;
let dmgMode = 'calc'; // 'calc' 或 'reverse'
let dmgAtkPetData = null; // 攻击方精灵数据（用于技能列表和属性判断）
let dmgDefPetData = null; // 防御方精灵数据（用于属性克制判断）
let dmgTypeRelations = null; // 属性克制关系缓存
let dmgSkillList = []; // 当前攻击方技能列表

function initDamageCalc() {
    if (dmgCalcInited) return;
    dmgCalcInited = true;

    // 加载属性克制数据
    fetch(`${API_BASE}/types`).then(r => r.json()).then(data => {
        dmgTypeRelations = data.relations || [];
    });

    // 攻击方精灵搜索
    const atkSearch = document.getElementById('dmgAtkPetSearch');
    const atkResults = document.getElementById('dmgAtkPetResults');
    if (atkSearch) {
        atkSearch.addEventListener('input', debounce(async (e) => {
            const kw = e.target.value.trim();
            if (kw.length < 1) { atkResults.style.display = 'none'; return; }
            const res = await fetch(`${API_BASE}/pets?keyword=${encodeURIComponent(kw)}&size=5`);
            const pets = await res.json();
            if (pets.length > 0) {
                atkResults.innerHTML = pets.map(p => `
                    <div class="sim-result-item" onclick="selectDmgAttacker(${p.id})">
                        <img src="${MEDIA_BASE}${p.imageUrl || `pets/JL_${p.name}.png`}" alt="${p.name}" onerror="this.style.display='none'; this.nextElementSibling.style.display='inline-block';"><span>${p.name}</span>
                    </div>`).join('');
                atkResults.style.display = 'block';
            } else { atkResults.style.display = 'none'; }
        }, 300));
    }

    // 防御方精灵搜索
    const defSearch = document.getElementById('dmgDefPetSearch');
    const defResults = document.getElementById('dmgDefPetResults');
    if (defSearch) {
        defSearch.addEventListener('input', debounce(async (e) => {
            const kw = e.target.value.trim();
            if (kw.length < 1) { defResults.style.display = 'none'; return; }
            const res = await fetch(`${API_BASE}/pets?keyword=${encodeURIComponent(kw)}&size=5`);
            const pets = await res.json();
            if (pets.length > 0) {
                defResults.innerHTML = pets.map(p => `
                    <div class="sim-result-item" onclick="selectDmgDefender(${p.id})">
                        <img src="${MEDIA_BASE}${p.imageUrl || `pets/JL_${p.name}.png`}" alt="${p.name}" onerror="this.style.display='none'; this.nextElementSibling.style.display='inline-block';"><span>${p.name}</span>
                    </div>`).join('');
                defResults.style.display = 'block';
            } else { defResults.style.display = 'none'; }
        }, 300));
    }

    // 点击外部关闭搜索结果
    document.addEventListener('click', (e) => {
        if (atkResults && !atkResults.contains(e.target) && e.target !== atkSearch) atkResults.style.display = 'none';
        if (defResults && !defResults.contains(e.target) && e.target !== defSearch) defResults.style.display = 'none';
    });

    // 所有输入变化时自动重算
    const inputIds = ['dmgAtkValue', 'dmgMagAtkValue', 'dmgDefValue', 'dmgMagDefValue',
        'dmgManualPower', 'dmgPowerBonus', 'dmgAtkUp', 'dmgAtkDown', 'dmgDefUp', 'dmgDefDown',
        'dmgPowerBuff', 'dmgActualDamage'];
    inputIds.forEach(id => {
        const el = document.getElementById(id);
        if (el) el.addEventListener('input', () => runDmgCalc());
    });
    const selectIds = ['dmgSkillSelect', 'dmgDamageCategory', 'dmgStab', 'dmgTypeEffect'];
    selectIds.forEach(id => {
        const el = document.getElementById(id);
        if (el) el.addEventListener('change', () => runDmgCalc());
    });
}

// 选择攻击方精灵 → 加载技能列表
window.selectDmgAttacker = async (id) => {
    const res = await fetch(`${API_BASE}/pets/${id}/details`);
    dmgAtkPetData = await res.json();
    document.getElementById('dmgAtkPetSearch').value = dmgAtkPetData.name;
    document.getElementById('dmgAtkPetResults').style.display = 'none';
    loadDmgSkills(dmgAtkPetData);
    autoDetectTypeEffect();
    runDmgCalc();
};

// 选择防御方精灵 → 记录属性用于克制判断
window.selectDmgDefender = async (id) => {
    const res = await fetch(`${API_BASE}/pets/${id}/details`);
    dmgDefPetData = await res.json();
    document.getElementById('dmgDefPetSearch').value = dmgDefPetData.name;
    document.getElementById('dmgDefPetResults').style.display = 'none';
    autoDetectTypeEffect();
    runDmgCalc();
};

// 加载攻击方技能到下拉框
function loadDmgSkills(petData) {
    const select = document.getElementById('dmgSkillSelect');
    if (!select || !petData.skills) return;
    dmgSkillList = [];
    // 合并所有来源的技能
    Object.values(petData.skills).forEach(arr => {
        arr.forEach(s => {
            if (s.type !== 2) { // 排除被动特性
                dmgSkillList.push(s);
            }
        });
    });
    select.innerHTML = '<option value="">手动输入威力</option>' +
        dmgSkillList.map((s, i) => {
            const power = parsePower(s.power);
            const cat = s.category || '';
            return `<option value="${i}">[${cat}] ${s.name} (威力${power})</option>`;
        }).join('');
}

// 解析技能威力（dam_para 格式如 "[80]" 或 "[90, 0, 45, 1, 1]"，取第一个值）
function parsePower(raw) {
    if (!raw) return 0;
    const str = String(raw).replace(/[\[\]]/g, '').trim();
    const first = str.split(',')[0].trim();
    return parseInt(first, 10) || 0;
}

// PLACEHOLDER_DMG_FUNCS

// 自动检测属性克制倍率
function autoDetectTypeEffect() {
    if (!dmgAtkPetData || !dmgDefPetData || !dmgTypeRelations) return;
    const select = document.getElementById('dmgSkillSelect');
    const skillIdx = select ? parseInt(select.value, 10) : NaN;
    if (isNaN(skillIdx) || !dmgSkillList[skillIdx]) return;

    const skill = dmgSkillList[skillIdx];
    // 技能属性ID（skill_dam_type 对应 types 表的 id）
    const skillAttr = skill.attribute; // 属性名称如"火"
    const skillAttrId = types.find(t => t.name === skillAttr)?.id;
    if (!skillAttrId) return;

    const defType1 = parseInt(dmgDefPetData.type1, 10);
    const defType2 = parseInt(dmgDefPetData.type2, 10);

    // 查找克制关系
    let mult = 1;
    const r1 = dmgTypeRelations.find(r => r.attacker_id === skillAttrId && r.defender_id === defType1);
    if (r1) mult *= (r1.multiplier === 1 ? 2 : r1.multiplier === -1 ? 0.5 : 1);
    if (defType2 && defType2 !== defType1) {
        const r2 = dmgTypeRelations.find(r => r.attacker_id === skillAttrId && r.defender_id === defType2);
        if (r2) mult *= (r2.multiplier === 1 ? 2 : r2.multiplier === -1 ? 0.5 : 1);
    }

    const effectSelect = document.getElementById('dmgTypeEffect');
    if (effectSelect) {
        // 匹配最接近的选项
        const closest = [0.25, 0.5, 1, 2, 4].reduce((a, b) => Math.abs(b - mult) < Math.abs(a - mult) ? b : a);
        effectSelect.value = String(closest);
    }
}

// 模式切换
window.switchDmgMode = (mode) => {
    dmgMode = mode;
    document.querySelectorAll('.dmg-mode-btn').forEach(btn => {
        btn.classList.toggle('active', btn.getAttribute('data-mode') === mode);
    });
    const reverseInput = document.getElementById('dmgReverseInput');
    if (reverseInput) reverseInput.style.display = mode === 'reverse' ? '' : 'none';
    const title = document.getElementById('dmgResultTitle');
    if (title) title.textContent = mode === 'calc' ? '伤害值' : '反推防御值';
    runDmgCalc();
};

// 核心计算
function runDmgCalc() {
    const isPhysical = document.getElementById('dmgDamageCategory')?.value === 'physical';

    // 获取攻击能力值
    const atk = parseFloat(document.getElementById(isPhysical ? 'dmgAtkValue' : 'dmgMagAtkValue')?.value) || 0;
    // 获取防御能力值
    const def = parseFloat(document.getElementById(isPhysical ? 'dmgDefValue' : 'dmgMagDefValue')?.value) || 0;

    // 技能威力
    const skillSelect = document.getElementById('dmgSkillSelect');
    const skillIdx = skillSelect ? parseInt(skillSelect.value, 10) : NaN;
    const manualPower = parseFloat(document.getElementById('dmgManualPower')?.value);
    let power = 0;
    if (!isNaN(manualPower) && manualPower > 0) {
        power = manualPower;
    } else if (!isNaN(skillIdx) && dmgSkillList[skillIdx]) {
        power = parsePower(dmgSkillList[skillIdx].power);
    }

    const powerBonus = parseFloat(document.getElementById('dmgPowerBonus')?.value) || 0;
    const atkUp = Math.min(6, Math.max(0, parseInt(document.getElementById('dmgAtkUp')?.value) || 0));
    const atkDown = Math.min(6, Math.max(0, parseInt(document.getElementById('dmgAtkDown')?.value) || 0));
    const defUp = Math.min(6, Math.max(0, parseInt(document.getElementById('dmgDefUp')?.value) || 0));
    const defDown = Math.min(6, Math.max(0, parseInt(document.getElementById('dmgDefDown')?.value) || 0));
    const powerBuff = parseFloat(document.getElementById('dmgPowerBuff')?.value) || 1.0;
    const stab = parseFloat(document.getElementById('dmgStab')?.value) || 1.0;
    const typeEffect = parseFloat(document.getElementById('dmgTypeEffect')?.value) || 1.0;

    // 能力等级 = (1 + 我方攻升 + 敌方防降) / (1 + 我方攻降 + 敌方防升)
    const abilityLevel = (1 + atkUp + defDown) / (1 + atkDown + defUp);

    const resultArea = document.getElementById('dmgResultArea');
    if (!resultArea) return;

    if (dmgMode === 'calc') {
        // 正向计算
        if (atk <= 0 || def <= 0 || power <= 0) {
            resultArea.innerHTML = `<div class="placeholder-text sim-placeholder">
                <i class="fas fa-crosshairs"></i>
                <span>请填写攻击能力值、防御能力值和技能威力</span>
            </div>`;
            return;
        }
        const damage = Math.floor((atk / def) * 0.9 * (power + powerBonus) * abilityLevel * powerBuff * stab * typeEffect);
        renderDmgResult(damage, { atk, def, power, powerBonus, abilityLevel, powerBuff, stab, typeEffect });
    } else {
        // 反推防御
        const actualDmg = parseFloat(document.getElementById('dmgActualDamage')?.value) || 0;
        if (atk <= 0 || power <= 0 || actualDmg <= 0) {
            resultArea.innerHTML = `<div class="placeholder-text sim-placeholder">
                <i class="fas fa-crosshairs"></i>
                <span>请填写攻击能力值、技能威力和实际伤害值</span>
            </div>`;
            return;
        }
        const reversedDef = (atk * 0.9 * (power + powerBonus) * abilityLevel * powerBuff * stab * typeEffect) / actualDmg;
        renderReverseResult(reversedDef, { atk, power, powerBonus, abilityLevel, powerBuff, stab, typeEffect, actualDmg });
    }
}

// PLACEHOLDER_DMG_RENDER

// 渲染正向计算结果
function renderDmgResult(damage, params) {
    const area = document.getElementById('dmgResultArea');
    area.innerHTML = `
        <div class="dmg-result-big">
            <div class="dmg-value">${damage}</div>
            <div class="dmg-label">预计伤害</div>
        </div>
        <div class="dmg-formula-breakdown">
            <h5>公式分解</h5>
            <div class="dmg-formula-row"><span>攻击 / 防御</span><span>${params.atk} / ${params.def} = ${(params.atk / params.def).toFixed(3)}</span></div>
            <div class="dmg-formula-row"><span>× 0.9</span><span>固定系数</span></div>
            <div class="dmg-formula-row"><span>× (威力 + 加成)</span><span>${params.power} + ${params.powerBonus} = ${params.power + params.powerBonus}</span></div>
            <div class="dmg-formula-row"><span>× 能力等级</span><span>${params.abilityLevel.toFixed(3)}</span></div>
            <div class="dmg-formula-row"><span>× 威力buff</span><span>${params.powerBuff}</span></div>
            <div class="dmg-formula-row"><span>× 本系加成</span><span>${params.stab}</span></div>
            <div class="dmg-formula-row"><span>× 属性克制</span><span>${params.typeEffect}</span></div>
            <div class="dmg-formula-row"><span>最终伤害</span><span>${damage}</span></div>
        </div>`;
}

// 渲染反推防御结果
function renderReverseResult(reversedDef, params) {
    const area = document.getElementById('dmgResultArea');
    area.innerHTML = `
        <div class="dmg-reverse-result">
            <div class="dmg-value">${reversedDef.toFixed(1)}</div>
            <div class="dmg-label">反推防御能力值</div>
        </div>
        <div class="dmg-formula-breakdown">
            <h5>反推公式</h5>
            <div class="dmg-formula-row"><span>防御 = 攻击 × 0.9 × 总威力 × 各修正 / 实际伤害</span><span></span></div>
            <div class="dmg-formula-row"><span>攻击能力值</span><span>${params.atk}</span></div>
            <div class="dmg-formula-row"><span>总威力</span><span>${params.power + params.powerBonus}</span></div>
            <div class="dmg-formula-row"><span>能力等级</span><span>${params.abilityLevel.toFixed(3)}</span></div>
            <div class="dmg-formula-row"><span>威力buff × 本系 × 克制</span><span>${params.powerBuff} × ${params.stab} × ${params.typeEffect}</span></div>
            <div class="dmg-formula-row"><span>实际伤害</span><span>${params.actualDmg}</span></div>
            <div class="dmg-formula-row"><span>反推防御</span><span>${reversedDef.toFixed(1)}</span></div>
        </div>`;
}

// 从能力模拟器导出到伤害计算器
window.exportSimToAttacker = () => {
    if (!currentSimPet) return;
    // 切换到伤害计算tab
    document.querySelector('.main-tab[data-tab="damageView"]')?.click();
    // 等待初始化完成后填入数值
    setTimeout(() => {
        const simResults = getSimResults();
        if (simResults) {
            document.getElementById('dmgAtkValue').value = simResults.atk;
            document.getElementById('dmgMagAtkValue').value = simResults.mag_atk;
        }
        runDmgCalc();
    }, 100);
};

window.exportSimToDefender = () => {
    if (!currentSimPet) return;
    document.querySelector('.main-tab[data-tab="damageView"]')?.click();
    setTimeout(() => {
        const simResults = getSimResults();
        if (simResults) {
            document.getElementById('dmgDefValue').value = simResults.def;
            document.getElementById('dmgMagDefValue').value = simResults.mag_def;
        }
        runDmgCalc();
    }, 100);
};

// 获取当前模拟器计算结果
function getSimResults() {
    if (!currentSimPet) return null;
    const ivs = {
        hp: parseInt(document.getElementById('iv_hp')?.value) || 0,
        atk: parseInt(document.getElementById('iv_atk')?.value) || 0,
        def: parseInt(document.getElementById('iv_def')?.value) || 0,
        mag_atk: parseInt(document.getElementById('iv_mag_atk')?.value) || 0,
        mag_def: parseInt(document.getElementById('iv_mag_def')?.value) || 0,
        spd: parseInt(document.getElementById('iv_spd')?.value) || 0
    };
    const base = {
        hp: currentSimPet.hp || 0,
        atk: currentSimPet.attack || 0,
        def: currentSimPet.defense || 0,
        mag_atk: currentSimPet.magic_attack || currentSimPet.sp_atk || 0,
        mag_def: currentSimPet.magic_defense || currentSimPet.sp_def || 0,
        spd: currentSimPet.speed || 0
    };
    const modifiers = getSelectedModifiers();
    const getMod = (attrId) => {
        if (modifiers.plusAttrId === attrId) return modifiers.plusRate;
        if (modifiers.minusAttrId === attrId) return modifiers.minusRate;
        return 1.0;
    };
    const talentModHP = (1 + modifiers.starLevel) * 0.85;
    const talentModOther = (1 + modifiers.starLevel) * 0.55;
    return {
        hp: Math.round((base.hp * 1.7 + ivs.hp * talentModHP + 70) * getMod(79) + 100),
        atk: Math.round((base.atk * 1.1 + ivs.atk * talentModOther + 10) * getMod(80) + 50),
        mag_atk: Math.round((base.mag_atk * 1.1 + ivs.mag_atk * talentModOther + 10) * getMod(81) + 50),
        def: Math.round((base.def * 1.1 + ivs.def * talentModOther + 10) * getMod(82) + 50),
        mag_def: Math.round((base.mag_def * 1.1 + ivs.mag_def * talentModOther + 10) * getMod(83) + 50),
        spd: Math.round((base.spd * 1.1 + ivs.spd * talentModOther + 10) * getMod(84) + 50)
    };
}
