const MEDIA = [
    { id: 1, title: "向山而行", subtitle: "川西旅行记录", duration: "24:18", seconds: 1458, size: "2.4 GB", sizeValue: 2400, resolution: "4K", art: "mountain", date: "2026-10-18" },
    { id: 2, title: "海边的早晨", subtitle: "日出与潮汐", duration: "12:36", seconds: 756, size: "860 MB", sizeValue: 860, resolution: "1080P", art: "sea", date: "2026-10-12" },
    { id: 3, title: "穿过森林", subtitle: "秋日散步", duration: "18:42", seconds: 1122, size: "1.6 GB", sizeValue: 1600, resolution: "4K", art: "forest", date: "2026-10-09" },
    { id: 4, title: "风经过的地方", subtitle: "沙漠短片", duration: "08:24", seconds: 504, size: "420 MB", sizeValue: 420, resolution: "1080P", art: "dunes", date: "2026-09-26" },
    { id: 5, title: "云上的一天", subtitle: "山间延时摄影", duration: "06:58", seconds: 418, size: "730 MB", sizeValue: 730, resolution: "4K", art: "cloud", date: "2026-09-21" },
    { id: 6, title: "城市慢下来", subtitle: "周末影像", duration: "15:30", seconds: 930, size: "1.1 GB", sizeValue: 1100, resolution: "1080P", art: "city", date: "2026-09-14" },
];

const state = {
    route: "gallery", category: "all", scene: "library", query: "",
    prefs: { theme: "light", density: "comfortable", accent: "blue", radius: 16, shouldAnimate: true },
    values: {}, segments: {}, switches: { resume: true, hardware: true, background: false, loop: false, subtitleBackground: true, isBold: false },
    selection: { gallery: new Set([1]), library: new Set() }, lastSelected: {},
    sort: { gallery: { key: "title", direction: 1 }, library: { key: "title", direction: 1 } },
    favorites: new Set([3]), queue: [1, 2, 3], activeMedia: 1, playbackSeconds: 432, volume: 72, speed: "1.0", ratio: "适应窗口",
    isPlaying: false, isScanning: false, taskProgress: 64, libraryQuery: "", libraryLayout: "grid", librarySection: "全部视频", settingsSection: "播放", isRailExpanded: true,
    rangeStart: 20, rangeEnd: 72, offset: 0, channels: { hue: 218, saturation: 80, value: 100, alpha: 100 }, subtitleSize: 22, subtitleColor: "#ffffff",
    dialogReturnFocus: null, drawerReturnFocus: null, toastAction: null, toastTimer: null, tooltipTimer: null, tooltipAnchor: null,
};

function icon(name) {
    return `<svg class="icon" viewBox="0 0 24 24" aria-hidden="true">${ICONS[name]}</svg>`;
}

function escapeHtml(value) {
    return String(value).replace(/[&<>"]/g, (character) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;" })[character]);
}

function button(label, tone, attrs = "") {
    return `<button type="button" class="button ${tone ? `is-${tone}` : ""}" ${attrs}>${label}</button>`;
}

function iconButton(name, label, attrs = "", tone = "") {
    return `<button type="button" class="icon-button ${tone ? `is-${tone}` : ""}" aria-label="${escapeHtml(label)}" data-tooltip="${escapeHtml(label)}" ${attrs}>${icon(name)}</button>`;
}

function switchControl(key, label, isDisabled = false) {
    return `<button type="button" class="switch" role="switch" aria-label="${label}" aria-checked="${Boolean(state.switches[key])}" data-switch="${key}" data-testid="switch-${key}" ${isDisabled ? "disabled" : ""}></button>`;
}

function settingRow(title, summary, trailing, leading = "") {
    return `<div class="setting-row">${leading ? `<span class="setting-leading">${icon(leading)}</span>` : ""}<span class="setting-copy"><strong>${title}</strong>${summary ? `<small>${summary}</small>` : ""}</span>${trailing}</div>`;
}

function rangeControl(id, label, value, suffix = "%", min = 0, max = 100, extra = "") {
    const current = state.values[id] ?? value;
    return `<div class="range-group"><div class="range-head"><label for="${id}">${label}</label><output id="${id}-output" for="${id}">${current}${suffix}</output></div><input class="range" id="${id}" type="range" min="${min}" max="${max}" value="${current}" style="--value:${(current - min) / (max - min) * 100}%" data-suffix="${suffix}" data-testid="${id}" ${extra} /><div class="range-scale"><span>${min}${suffix}</span><span>${max}${suffix}</span></div></div>`;
}

function fieldControl(id, label, value = "", extra = "", hint = "") {
    return `<div class="field"><label class="field-label" for="${id}">${label}</label><input class="field-input" id="${id}" value="${escapeHtml(state.values[id] ?? value)}" data-testid="${id}" ${hint ? `aria-describedby="${id}-hint"` : ""} ${extra} />${hint ? `<p class="field-hint ${extra.includes('aria-invalid="true"') ? "field-error" : ""}" id="${id}-hint">${hint}</p>` : ""}</div>`;
}

function segmented(id, options, initial = 0, text = "") {
    const selected = state.segments[id] ?? initial;
    return `<div class="segmented" role="tablist" aria-label="${id === "view-mode" ? "浏览方式" : "选项切换"}" style="--tab-index:${selected};--tab-count:${options.length}"><span class="segment-thumb" aria-hidden="true"></span>${options.map((option, index) => `<button type="button" role="tab" id="${id}-${index}" aria-selected="${index === selected}" tabindex="${index === selected ? 0 : -1}" ${text ? `aria-controls="${id}-panel"` : ""} data-segment="${id}" data-index="${index}" data-testid="${id}-${index}">${option}</button>`).join("")}</div>${text ? `<div class="tab-content" id="${id}-panel" role="tabpanel" aria-labelledby="${id}-${selected}" data-segment-panel="${id}">${text}</div>` : ""}`;
}

const previews = {
    "action-buttons": () => `<div class="demo-row">${button(`${icon("fileOpen")}打开文件`, "primary", 'data-action="toast" data-message="已触发打开文件操作" data-testid="primary-open"')}${button("添加文件夹", "", 'data-action="toast" data-message="已触发添加文件夹操作" data-testid="secondary-folder"')}</div><div class="demo-row">${button("查看详情", "ghost", 'data-open-source="action-buttons" data-testid="ghost-details"')}${button(`${icon("delete")}移除`, "danger", 'data-action="toast" data-message="已触发移除操作，演示内容可通过重置恢复" data-testid="danger-remove"')}</div>`,
    "button-states": () => `<div class="state-buttons">${button("默认", "primary", 'data-action="toast" data-message="默认按钮已点击" data-testid="state-default"')}${button("悬停", "primary", 'data-forced-state="hovered" data-action="toast" data-message="这是悬停样式" data-testid="state-hover"')}${button("按下", "primary", 'data-forced-state="pressed" data-action="toast" data-message="这是按下样式" data-testid="state-pressed"')}${button("焦点", "primary", 'data-forced-state="focused" data-action="toast" data-message="焦点样式让键盘定位更清楚" data-testid="state-focus"')}${button("禁用", "primary", 'disabled data-testid="state-disabled"')}${button('<span class="spinner" aria-hidden="true"></span>处理中', "primary", 'disabled aria-busy="true" data-testid="state-loading"')}</div>`,
    "icon-buttons": () => `<div class="demo-row spread">${iconButton("play", "播放", 'data-action="play" data-play-toggle data-testid="icon-play"', "primary")}${iconButton("folder", "打开文件夹", 'data-action="toast" data-message="已触发文件夹操作" data-testid="icon-folder"', "filled")}${iconButton("heart", "收藏", 'data-action="favorite" data-media="1" aria-pressed="false" data-testid="icon-favorite"')}${iconButton("moreVert", "更多操作", 'data-open-source="icon-buttons" data-testid="icon-more"')}${iconButton("delete", "禁用的移除按钮", 'disabled data-testid="icon-disabled"', "filled")}</div><p class="demo-caption">悬停或用键盘聚焦，可以看到提示；收藏状态可切换。</p>`,
    "text-input": () => `<div class="demo-row">${fieldControl("playlist-name", "播放列表名称", "周末片单", 'maxlength="32"', "最多 32 个字")}${fieldControl("invalid-name", "名称校验", "", 'aria-invalid="true" placeholder="请输入名称" data-validate="required"', "名称不能为空")}</div>${fieldControl("disabled-path", "只读路径", "视频 / 旅行记录", "disabled")}`,
    "floating-label": () => `<div class="floating-field"><input id="floating-title" class="field-input" placeholder=" " value="${escapeHtml(state.values["floating-title"] ?? "")}" data-testid="floating-title" /><label for="floating-title">播放列表名称</label></div><div class="floating-field"><input id="floating-summary" class="field-input" placeholder=" " value="${escapeHtml(state.values["floating-summary"] ?? "想一看再看的风景")}" data-testid="floating-summary" /><label for="floating-summary">备注</label></div>`,
    "checkboxes": () => `<div class="demo-row spread"><label class="check-label"><input class="check" id="check-off" type="checkbox" data-testid="check-off" />未选中</label><label class="check-label"><input class="check" id="check-on" type="checkbox" checked data-testid="check-on" />已选中</label><label class="check-label"><input class="check" id="check-mixed" type="checkbox" data-indeterminate data-testid="check-mixed" />半选</label></div><div class="demo-row"><label class="check-label"><input class="check" type="checkbox" checked disabled data-testid="check-disabled" />禁用</label><span class="demo-caption">保留原版的圆形勾选标记</span></div>`,
    "radio-options": () => `<div class="radio-list" role="radiogroup" aria-label="解码方式">${["自动选择", "优先硬件解码", "优先软件解码"].map((text, index) => `<label class="radio-option"><span class="setting-copy"><strong>${text}</strong>${index === 0 ? "<small>根据视频格式选择合适的方式</small>" : ""}</span><input type="radio" name="decoder" value="${index}" ${String(state.values.decoder ?? 0) === String(index) ? "checked" : ""} data-testid="decoder-${index}" /><span class="radio-marker">${icon("check")}</span></label>`).join("")}</div>`,
    "switches": () => `${settingRow("记住播放进度", "下次打开时继续播放", switchControl("resume", "记住播放进度"))}${settingRow("后台播放", "最小化窗口后继续播放声音", switchControl("background", "后台播放"))}${settingRow("硬件解码", "此项用于展示禁用状态", switchControl("hardware", "硬件解码", true))}`,
    "sliders": () => `${rangeControl("demo-volume", "音量", 72)}${rangeControl("demo-disabled-slider", "禁用状态", 40, "%", 0, 100, "disabled")}`,
    "segments": () => `${segmented("view-mode", ["文件夹", "全部视频", "收藏"], 1, "全部视频 · 当前目录里的内容在同一页展示")}<p class="demo-caption">点击或使用左右方向键，选中背景会平滑移动。</p>`,
    "tabs": () => `<div class="pill-tabs" role="tablist" aria-label="媒体类型">${["视频", "音频", "字幕"].map((text, index) => `<button type="button" role="tab" aria-selected="${index === (state.segments["media-tabs"] ?? 0)}" tabindex="${index === (state.segments["media-tabs"] ?? 0) ? 0 : -1}" aria-controls="media-tabs-panel" id="media-tabs-${index}" data-segment="media-tabs" data-index="${index}" data-testid="media-tabs-${index}">${text}</button>`).join("")}</div><div class="tab-content" id="media-tabs-panel" role="tabpanel" aria-labelledby="media-tabs-${state.segments["media-tabs"] ?? 0}" data-segment-panel="media-tabs">视频文件 · 播放和管理你保存的影像</div>`,
    "progress": () => `<div class="range-head"><span>媒体扫描</span><span data-progress-text>${state.taskProgress}%</span></div><div class="linear-progress" role="progressbar" aria-label="扫描进度" aria-valuemin="0" aria-valuemax="100" aria-valuenow="${state.taskProgress}" data-task-progress><span style="--progress:${state.taskProgress}%"></span></div><div class="demo-row spread"><div class="demo-row"><span class="ring-progress" style="--progress:${state.taskProgress}" data-task-ring aria-hidden="true"></span><span class="spinner is-large" aria-hidden="true"></span><span class="demo-label">确定 / 不定进度</span></div>${button("推进进度", "ghost", 'data-action="progress" data-testid="progress-advance"')}</div>`,
};

function getReadyComponents() {
    return COMPONENT_CATALOG.filter((component) => Object.hasOwn(previews, component.id));
}

function pageHeading(title, description, eyebrow = "移动端风格 · 桌面交互") {
    return `<div class="page-heading"><div><div class="eyebrow"><span class="status-dot"></span>${eyebrow}</div><h1>${title}</h1><p class="page-description">${description}</p></div><span class="version-pill">第一版 · 风格验证</span></div>`;
}

function renderGallery() {
    const ready = getReadyComponents();
    const query = state.query.trim().toLocaleLowerCase();
    const filtered = ready.filter((component) => (state.category === "all" || component.category === state.category) && `${component.title} ${component.description} ${component.apis.join(" ")}`.toLocaleLowerCase().includes(query));
    const category = COMPONENT_CATEGORIES.find((item) => item.id === state.category);
    const labels = { original: "延续原样", adapted: "桌面适配", added: "桌面补充" };
    const heading = pageHeading(query ? `搜索“${escapeHtml(state.query)}”` : state.category === "all" ? "桌面控件图鉴" : category.title, state.category === "all" ? "保留柔和圆角、干净表面和蓝色主调，为鼠标、键盘与宽屏重新安排。每一组都可以直接操作。" : category.description);
    const strip = `<div class="overview-strip"><span class="overview-count"><strong>${filtered.length}</strong>组可交互控件</span>${Object.entries(labels).map(([type, label]) => `<span class="legend"><span class="legend-dot ${type}"></span>${label}</span>`).join("")}<span class="demo-caption">切换顶部主题，查看另一面</span></div>`;
    const sections = COMPONENT_CATEGORIES.filter((item) => item.id !== "all").map((item, index) => {
        const components = filtered.filter((component) => component.category === item.id);
        if (!components.length) return "";
        return `<section class="gallery-section" aria-labelledby="section-${item.id}"><div class="section-heading"><span class="section-index">${String(index + 1).padStart(2, "0")}</span><h2 id="section-${item.id}">${item.title}</h2><p>${item.description}</p></div><div class="showcase-grid">${components.map((component) => `<article class="showcase-card" id="component-${component.id}" data-component="${component.id}"><header class="showcase-header"><h3>${component.title}</h3><p>${component.description}</p></header><div class="demo-stage">${previews[component.id]()}</div><footer class="showcase-footer"><span class="origin-tag ${component.origin}">${labels[component.origin]}</span><button type="button" class="source-button" data-open-source="${component.id}" data-testid="source-${component.id}">查看依据${icon("arrowRightUp")}</button></footer></article>`).join("")}</div></section>`;
    }).join("");
    return heading + strip + (filtered.length ? sections : `<div class="empty-result">${icon("search")}<h2>没有找到相关控件</h2><p>换一个名称试试，或者查看全部分类。</p>${button("清除搜索", "primary", 'data-action="clear-search" data-testid="clear-catalog-search"')}</div>`);
}

function renderSpec() {
    return pageHeading("把细节变成统一的规则", "先复用成熟的基础控件，再把桌面需要的密度、输入和布局补齐。", "设计依据 · 适配方案") + `<section class="spec-card"><h2>核对过的源码基线</h2><p>项目当前使用 0.9.4，这个版本已经支持桌面平台，并提供基础控件、侧边导航、面包屑、滚动条和弹窗。</p><p>新的原生窗口与标题栏模块只出现在研究时的上游主分支里，当前版本尚未包含。它与基础库里的跨平台窗口弹窗是不同模块。</p><div class="source-links"><a href="https://github.com/compose-miuix-ui/miuix/tree/${UPSTREAM_REF}" target="_blank" rel="noreferrer">查看固定版本源码 ↗</a><a href="PLAN.md" target="_blank">阅读完整方案 ↗</a></div></section><section class="spec-card"><h2>保留的风格特征</h2><ul><li>圆形多选框，单选采用蓝色勾选标记。</li><li>开关轨道 49 × 28，白色圆钮直径 20。</li><li>滑块保留 28 高的粗圆角轨道，播放进度另做细轨道变体。</li><li>轮廓分段采用灰色底板和移动的选中背景。</li><li>普通卡片保持平整，浮层使用适度阴影。</li></ul></section><section class="spec-card"><h2>从控件到桌面应用</h2><p>这份网页用于确认风格与交互，采用演示媒体。后续优先评估跨平台组件复用，将文件树、表格、窗口装饰和播放时间轴单独封装，再接入桌面的文件访问和播放内核。</p><p>浏览器中的圆角与动画是视觉近似。原生窗口拖拽、系统材质和精确弹簧效果，需要在桌面应用中验证。</p></section>`;
}

function hydrate(root = document) {
    root.querySelectorAll("[data-icon]").forEach((element) => { element.innerHTML = icon(element.dataset.icon); });
    root.querySelectorAll("[data-forced-state]").forEach((element) => { element.classList.add(`is-${element.dataset.forcedState}`); });
    root.querySelectorAll("[data-indeterminate]").forEach((element) => { element.indeterminate = !Object.hasOwn(state.values, element.id); });
    root.querySelectorAll('input[type="checkbox"][id]').forEach((element) => { if (Object.hasOwn(state.values, element.id)) element.checked = Boolean(state.values[element.id]); });
}

function render() {
    hideTooltip();
    const ready = getReadyComponents();
    document.getElementById("category-nav").innerHTML = COMPONENT_CATEGORIES.map((category) => `<button type="button" data-category="${category.id}" data-testid="nav-${category.id}" class="${state.route === "gallery" && state.category === category.id ? "is-active" : ""}" ${state.route === "gallery" && state.category === category.id ? 'aria-current="page"' : ""}>${icon(category.icon)}${category.title}<span class="nav-count">${category.id === "all" ? ready.length : ready.filter((component) => component.category === category.id).length}</span></button>`).join("");
    document.querySelectorAll('.route-tabs [data-route]').forEach((element) => { const isSelected = element.dataset.route === state.route; element.setAttribute("aria-selected", String(isSelected)); element.tabIndex = isSelected ? 0 : -1; });
    document.querySelectorAll('.scene-nav [data-scene]').forEach((element) => { element.classList.toggle("is-active", state.route === "scenes" && element.dataset.scene === state.scene); });
    const content = document.getElementById("content");
    content.setAttribute("aria-labelledby", `tab-${state.route}`);
    if (state.route === "gallery") content.innerHTML = renderGallery();
    if (state.route === "spec") content.innerHTML = renderSpec();
    if (state.route === "scenes") content.innerHTML = typeof renderScenePage === "function" ? renderScenePage() : pageHeading("页面组合预览", "媒体库、播放器和设置页正在补齐，可以先在控件图鉴中体验基础样式。", "页面预览 · 演示内容");
    hydrate(content);
    document.getElementById("catalog-search").value = state.query;
    if (typeof hydrateExtended === "function") hydrateExtended(content);
}

function setRoute(route, detail) {
    state.route = route;
    if (route === "gallery" && detail) state.category = detail;
    if (route === "scenes" && detail) state.scene = detail;
    history.pushState(null, "", `#${route}${detail ? `/${detail}` : ""}`);
    render();
    document.getElementById("content").scrollTop = 0;
    closeSidebar();
}

function readHash() {
    const [route, detail] = location.hash.slice(1).split("/");
    if (["gallery", "scenes", "spec"].includes(route)) state.route = route;
    if (route === "gallery" && COMPONENT_CATEGORIES.some((item) => item.id === detail)) state.category = detail;
    if (route === "scenes" && ["library", "player", "settings"].includes(detail)) state.scene = detail;
}

function applyPreferences() {
    const root = document.documentElement;
    root.dataset.theme = state.prefs.theme;
    root.dataset.density = state.prefs.density;
    root.dataset.accent = state.prefs.accent;
    root.dataset.motion = state.prefs.shouldAnimate ? "normal" : "reduced";
    root.style.setProperty("--radius-card", `${state.prefs.radius}px`);
    const theme = document.getElementById("theme-button");
    theme.setAttribute("aria-pressed", String(state.prefs.theme === "dark"));
    theme.setAttribute("aria-label", `切换为${state.prefs.theme === "dark" ? "浅色" : "深色"}模式`);
    theme.innerHTML = icon(state.prefs.theme === "dark" ? "brightness" : "darkMode") + `<span id="theme-label">${state.prefs.theme === "dark" ? "深色" : "浅色"}</span>`;
    document.querySelectorAll('.density-control [data-density]').forEach((element) => { element.setAttribute("aria-pressed", String(element.dataset.density === state.prefs.density)); });
    try { localStorage.setItem("only-player-desktop-preview", JSON.stringify(state.prefs)); } catch { /* 浏览器可能禁止本地预览保存偏好。 */ }
}

function showToast(message, actionLabel = "", callback = null) {
    clearTimeout(state.toastTimer);
    state.toastAction = callback;
    document.getElementById("toast-root").innerHTML = `<div class="toast">${icon("check")}<span>${escapeHtml(message)}</span>${actionLabel ? `<button type="button" data-action="toast-undo" data-testid="toast-undo">${escapeHtml(actionLabel)}</button>` : ""}<button type="button" class="toast-close" data-action="toast-close" aria-label="关闭消息" data-testid="toast-close">${icon("close")}</button></div>`;
    state.toastTimer = setTimeout(closeToast, actionLabel ? 9000 : 4000);
}

function closeToast() { clearTimeout(state.toastTimer); state.toastAction = null; document.getElementById("toast-root").innerHTML = ""; }

function showSource(id) {
    const component = COMPONENT_CATALOG.find((item) => item.id === id);
    const guide = component.isUnreleased ? `https://github.com/compose-miuix-ui/miuix/blob/${WINDOW_REF}/docs/zh_CN/guide/miuix-window.md` : component.guide ? `https://github.com/compose-miuix-ui/miuix/blob/${UPSTREAM_REF}/docs/zh_CN/components/${component.guide}.md` : "";
    showDialog(`<div class="dialog-head"><h2 id="dialog-title">${component.title}</h2>${iconButton("close", "关闭对话框", 'data-action="dialog-close" data-testid="dialog-close"')}</div><p>${component.details}</p><div class="source-api">${component.apis.map(escapeHtml).join(" · ")}</div>${component.isUnreleased ? '<p class="field-error">此模块不包含在项目当前使用的 0.9.4 中。</p>' : ""}<div class="source-links">${guide ? `<a href="${guide}" target="_blank" rel="noreferrer">查看上游文档 ↗</a>` : ""}<a href="PLAN.md" target="_blank">阅读完整方案 ↗</a></div><div class="dialog-actions">${button("知道了", "primary", 'data-action="dialog-close" data-testid="dialog-confirm"')}</div>`);
}

function showDialog(html) {
    hideTooltip();
    const dialog = document.getElementById("demo-dialog");
    state.dialogReturnFocus = document.activeElement;
    dialog.innerHTML = html;
    dialog.showModal();
    hydrate(dialog);
}

function closeSidebar() { document.body.classList.remove("is-sidebar-open"); document.querySelector(".sidebar-scrim").hidden = true; document.querySelector(".mobile-menu").setAttribute("aria-expanded", "false"); }

function hideTooltip() {
    clearTimeout(state.tooltipTimer);
    document.getElementById("tooltip").hidden = true;
    if (state.tooltipAnchor?.getAttribute("aria-describedby") === "tooltip") state.tooltipAnchor.removeAttribute("aria-describedby");
    state.tooltipAnchor = null;
}

function scheduleTooltip(anchor, delay = 420) {
    hideTooltip();
    state.tooltipAnchor = anchor;
    state.tooltipTimer = setTimeout(() => {
        if (!anchor.isConnected) return;
        const tooltip = document.getElementById("tooltip");
        tooltip.textContent = anchor.dataset.tooltip;
        tooltip.hidden = false;
        const bounds = anchor.getBoundingClientRect();
        const box = tooltip.getBoundingClientRect();
        tooltip.style.left = `${Math.max(8, Math.min(innerWidth - box.width - 8, bounds.left + (bounds.width - box.width) / 2))}px`;
        tooltip.style.top = `${bounds.top > box.height + 14 ? bounds.top - box.height - 8 : bounds.bottom + 8}px`;
        anchor.setAttribute("aria-describedby", "tooltip");
    }, delay);
}

function updatePlayControls() {
    document.querySelectorAll("[data-play-toggle]").forEach((element) => { element.innerHTML = icon(state.isPlaying ? "pause" : "play"); element.setAttribute("aria-label", state.isPlaying ? "暂停" : "播放"); element.dataset.tooltip = state.isPlaying ? "暂停 · 空格" : "播放 · 空格"; });
    document.querySelectorAll("[data-play-state]").forEach((element) => { element.textContent = state.isPlaying ? "正在播放" : "已暂停"; });
}

const ACTIONS = {
    toast: (element) => showToast(element.dataset.message),
    theme: () => { state.prefs.theme = state.prefs.theme === "light" ? "dark" : "light"; applyPreferences(); },
    sidebar: () => { const isOpen = document.body.classList.toggle("is-sidebar-open"); document.querySelector(".sidebar-scrim").hidden = !isOpen; document.querySelector(".mobile-menu").setAttribute("aria-expanded", String(isOpen)); },
    "close-sidebar": closeSidebar,
    "dialog-close": () => document.getElementById("demo-dialog").close(),
    "toast-close": closeToast,
    "toast-undo": () => { const callback = state.toastAction; closeToast(); if (callback) callback(); },
    "clear-search": () => { state.query = ""; state.category = "all"; render(); document.getElementById("catalog-search").focus(); },
    play: () => { state.isPlaying = !state.isPlaying; updatePlayControls(); },
    favorite: (element) => { const id = Number(element.dataset.media); const isFavorite = state.favorites.has(id); if (isFavorite) state.favorites.delete(id); else state.favorites.add(id); element.classList.toggle("is-selected", !isFavorite); element.setAttribute("aria-pressed", String(!isFavorite)); showToast(isFavorite ? "已取消收藏" : "已收藏"); },
    progress: () => { state.taskProgress = state.taskProgress >= 100 ? 0 : Math.min(100, state.taskProgress + 12); document.querySelectorAll("[data-task-progress]").forEach((element) => { element.setAttribute("aria-valuenow", String(state.taskProgress)); element.firstElementChild.style.setProperty("--progress", `${state.taskProgress}%`); }); document.querySelectorAll("[data-progress-text]").forEach((element) => { element.textContent = `${state.taskProgress}%`; }); document.querySelectorAll("[data-task-ring]").forEach((element) => { element.style.setProperty("--progress", state.taskProgress); }); },
    reset: () => { state.values = {}; state.segments = {}; state.isPlaying = false; state.taskProgress = 64; state.switches = { resume: true, hardware: true, background: false, loop: false, subtitleBackground: true, isBold: false }; state.favorites = new Set([3]); render(); showToast("演示状态已重置"); },
};

document.addEventListener("click", (event) => {
    const element = event.target.closest("button, a");
    if (!element) return;
    if (element.dataset.route) { event.preventDefault(); setRoute(element.dataset.route); return; }
    if (element.dataset.category) { state.query = ""; setRoute("gallery", element.dataset.category); return; }
    if (element.dataset.scene) { state.query = ""; setRoute("scenes", element.dataset.scene); return; }
    if (element.dataset.density) { state.prefs.density = element.dataset.density; applyPreferences(); return; }
    if (element.dataset.openSource) { showSource(element.dataset.openSource); return; }
    if (element.dataset.switch) { const key = element.dataset.switch; state.switches[key] = !state.switches[key]; document.querySelectorAll(`[data-switch="${key}"]`).forEach((control) => control.setAttribute("aria-checked", String(state.switches[key]))); if (typeof onSwitchChanged === "function") onSwitchChanged(key); return; }
    if (element.dataset.segment) {
        const group = element.dataset.segment;
        const index = Number(element.dataset.index);
        state.segments[group] = index;
        const list = element.closest('[role="tablist"]');
        list.style.setProperty("--tab-index", index);
        list.querySelectorAll('[role="tab"]').forEach((tab) => { const isSelected = tab === element; tab.setAttribute("aria-selected", String(isSelected)); tab.tabIndex = isSelected ? 0 : -1; });
        const panel = document.querySelector(`[data-segment-panel="${group}"]`);
        if (panel) { panel.setAttribute("aria-labelledby", element.id); panel.textContent = `${element.textContent} · ${group === "media-tabs" ? ["播放和管理你保存的影像", "集中管理音乐和声音", "选择适合视频的字幕文件"][index] : ["按所在目录浏览内容", "当前目录里的内容在同一页展示", "收藏的内容更容易找到"][index]}`; }
        if (typeof onSegmentChanged === "function") onSegmentChanged(group, index);
        return;
    }
    if (element.dataset.action) ACTIONS[element.dataset.action](element, event);
    if (typeof handleExtendedClick === "function") handleExtendedClick(element, event);
});

document.addEventListener("input", (event) => {
    const element = event.target;
    if (element.id === "catalog-search") { state.query = element.value; state.route = "gallery"; render(); return; }
    if (element.matches(".range")) { const value = Number(element.value); state.values[element.id] = value; element.style.setProperty("--value", `${(value - Number(element.min)) / (Number(element.max) - Number(element.min)) * 100}%`); const output = document.getElementById(`${element.id}-output`); if (output) output.textContent = `${value}${element.dataset.suffix ?? "%"}`; }
    if (element.matches(".field-input")) { state.values[element.id] = element.value; if (element.dataset.validate === "required") { const isValid = Boolean(element.value.trim()); element.setAttribute("aria-invalid", String(!isValid)); const hint = document.getElementById(`${element.id}-hint`); hint.textContent = isValid ? "名称可以使用" : "名称不能为空"; hint.classList.toggle("field-error", !isValid); } }
    if (typeof handleExtendedInput === "function") handleExtendedInput(element);
});

document.addEventListener("change", (event) => { const element = event.target; if (element.type === "checkbox" && element.id) state.values[element.id] = element.checked; if (element.type === "radio") state.values[element.name] = element.value; if (typeof handleExtendedChange === "function") handleExtendedChange(element); });
document.addEventListener("keydown", (event) => {
    const element = event.target;
    const isEditing = element.matches("input, textarea, select, [contenteditable]");
    if (event.key === "/" && !isEditing && !document.querySelector("dialog[open]")) { event.preventDefault(); document.getElementById("catalog-search").focus(); return; }
    if (event.key === "Escape") { hideTooltip(); closeSidebar(); }
    const tablist = element.closest('[role="tablist"]');
    if (tablist && ["ArrowLeft", "ArrowRight", "Home", "End"].includes(event.key)) { const tabs = [...tablist.querySelectorAll('[role="tab"]')]; const index = tabs.indexOf(element); if (index < 0) return; const next = event.key === "Home" ? 0 : event.key === "End" ? tabs.length - 1 : (index + (event.key === "ArrowRight" ? 1 : -1) + tabs.length) % tabs.length; event.preventDefault(); tabs[next].focus(); tabs[next].click(); return; }
    if (event.code === "Space" && !isEditing && !element.closest("button, a, summary, dialog, #menu-root") && state.route === "scenes" && state.scene === "player") { event.preventDefault(); ACTIONS.play(); }
    if (typeof handleExtendedKey === "function") handleExtendedKey(event);
});

document.addEventListener("pointerover", (event) => { const anchor = event.target.closest("[data-tooltip]"); if (anchor && !anchor.contains(event.relatedTarget)) scheduleTooltip(anchor); });
document.addEventListener("pointerout", (event) => { if (state.tooltipAnchor && !state.tooltipAnchor.contains(event.relatedTarget)) hideTooltip(); });
document.addEventListener("focusin", (event) => { const anchor = event.target.closest("[data-tooltip]"); if (anchor) scheduleTooltip(anchor, 0); });
document.addEventListener("focusout", hideTooltip);
document.getElementById("content").addEventListener("scroll", hideTooltip);
document.getElementById("demo-dialog").addEventListener("close", () => { if (state.dialogReturnFocus?.isConnected) state.dialogReturnFocus.focus(); });
document.getElementById("demo-dialog").addEventListener("click", (event) => { if (event.target !== event.currentTarget) return; const box = event.currentTarget.getBoundingClientRect(); if (event.clientX < box.left || event.clientX > box.right || event.clientY < box.top || event.clientY > box.bottom) event.currentTarget.close(); });
window.addEventListener("popstate", () => { readHash(); render(); });

document.addEventListener("DOMContentLoaded", () => {
    try { const preferences = JSON.parse(localStorage.getItem("only-player-desktop-preview") || "null"); if (preferences && ["light", "dark"].includes(preferences.theme) && ["comfortable", "compact"].includes(preferences.density)) state.prefs = { ...state.prefs, ...preferences }; } catch { /* 浏览器可能禁止本地预览保存偏好。 */ }
    readHash();
    hydrate(document);
    applyPreferences();
    render();
});
