const $ = (selector, root = document) => root.querySelector(selector);
const $$ = (selector, root = document) => [...root.querySelectorAll(selector)];
let menuState = null;
let draggedMedia = null;
let scanTimer = null;

function landscape(type = "mountain") {
    const palettes = {
        mountain: ["#dde9e7", "#e5d8b9", "#788e83", "#415e58", "#283d3a"],
        sea: ["#e2e8e1", "#ead9bf", "#aac2bc", "#7baba9", "#4c8389"],
        forest: ["#dde1c6", "#f4e8bd", "#9fafa0", "#62857a", "#3a5c54"],
        dunes: ["#e6d7be", "#f8e6bf", "#ceaf83", "#b4835f", "#8e6652"],
        cloud: ["#cddde3", "#eef0e0", "#b9ced2", "#8ea9ba", "#577b98"],
        city: ["#dbd2cb", "#f2d5b6", "#b8aa9e", "#827b78", "#4e575f"],
    };
    const colors = palettes[type];
    return `<svg class="landscape landscape-${type}" viewBox="0 0 600 340" preserveAspectRatio="xMidYMid slice" aria-hidden="true"><path fill="${colors[0]}" d="M0 0h600v340H0z"/><circle cx="440" cy="98" r="42" fill="${colors[1]}"/><path fill="${colors[2]}" d="M0 206 105 130 170 179 283 75 430 197 527 146 600 176V340H0Z"/><path fill="${colors[3]}" d="M0 216 89 187 180 258 313 175 385 208 457 191 600 255V340H0Z"/><path fill="${colors[4]}" d="M0 301 124 264 211 293 377 258 492 274 600 247V340H0Z"/><path d="M254 111 283 75 325 129 297 115 284 126 275 110Z" fill="${colors[1]}" opacity=".75"/><path d="M385 274 321 303 346 321 301 340h-47l55-25-22-17 77-26Z" fill="${colors[0]}" opacity=".4"/></svg>`;
}

function searchBox(id, placeholder, value = "") {
    return `<div class="inline-search">${icon("search")}<input id="${id}" type="search" placeholder="${placeholder}" aria-label="${placeholder}" value="${escapeHtml(value)}" data-testid="${id}" autocomplete="off"/>${iconButton("close", "清除搜索", `data-action="clear-field" data-target="${id}" data-testid="${id}-clear"`)}</div>`;
}

function mediaThumb(media) {
    return `<span class="media-thumb">${landscape(media.art)}</span>`;
}

function preferenceButton(title, summary, value, action, id, leading = "") {
    return `<button type="button" class="preference-button" data-action="${action}" data-testid="${id}">${leading ? `<span class="setting-leading">${icon(leading)}</span>` : ""}<span class="setting-copy"><strong>${title}</strong>${summary ? `<small>${summary}</small>` : ""}</span><span class="preference-value">${value}</span><span class="chevron">${icon("expandMore")}</span></button>`;
}

function breadcrumb(id, selected = 2) {
    const index = state.segments[id] ?? selected;
    return `<nav class="breadcrumbs" aria-label="目录路径">${["此电脑", "视频", "旅行记录"]
        .slice(0, index + 1)
        .map(
            (title, order) =>
                `${order ? '<span class="crumb-arrow" aria-hidden="true">›</span>' : ""}<button type="button" data-action="breadcrumb" data-group="${id}" data-index="${order}" class="${order === index ? "is-current" : ""}" ${order === index ? 'aria-current="location"' : ""} data-testid="${id}-${order}">${order === 0 ? icon("folder") : ""}${title}</button>`,
        )
        .join(
            "",
        )}${index < 2 ? button("进入旅行记录", "ghost", `data-action="breadcrumb" data-group="${id}" data-index="2" data-testid="${id}-enter"`) : ""}</nav>`;
}

function dualRange(prefix = "interval") {
    return `<div class="range-head"><span>保留片段</span><output data-range-output>${state.rangeStart} — ${state.rangeEnd} 秒</output></div><div class="dual-range" style="--start:${state.rangeStart}%;--end:${state.rangeEnd}%"><div class="dual-track"><span></span></div><input type="range" class="dual-handle" min="0" max="100" value="${state.rangeStart}" aria-label="片段起点" data-range-end="start" data-testid="${prefix}-start"/><input type="range" class="dual-handle" min="0" max="100" value="${state.rangeEnd}" aria-label="片段终点" data-range-end="end" data-testid="${prefix}-end"/></div><div class="range-scale"><span>0 秒</span><span>100 秒</span></div>`;
}

function numberStepper() {
    return `<div class="stepper">${iconButton("remove", "字幕提前 100 毫秒", 'data-action="step-number" data-delta="-100" data-testid="offset-minus"')}<input type="number" id="subtitle-offset" min="-5000" max="5000" step="100" value="${state.offset}" aria-label="字幕时间偏移，毫秒" data-testid="subtitle-offset"/>${iconButton("add", "字幕延后 100 毫秒", 'data-action="step-number" data-delta="100" data-testid="offset-plus"')}</div>`;
}

function hsvColor({ hue, saturation, value } = state.channels) {
    const chroma = ((value / 100) * saturation) / 100;
    const intermediate = chroma * (1 - Math.abs(((hue / 60) % 2) - 1));
    const minimum = value / 100 - chroma;
    const channels =
        hue < 60
            ? [chroma, intermediate, 0]
            : hue < 120
              ? [intermediate, chroma, 0]
              : hue < 180
                ? [0, chroma, intermediate]
                : hue < 240
                  ? [0, intermediate, chroma]
                  : hue < 300
                    ? [intermediate, 0, chroma]
                    : [chroma, 0, intermediate];
    return `#${channels
        .map((channel) =>
            Math.round((channel + minimum) * 255)
                .toString(16)
                .padStart(2, "0"),
        )
        .join("")}`;
}

const PALETTE_SWATCHES = [
    [10, 100],
    [35, 100],
    [70, 100],
    [100, 85],
    [100, 65],
    [100, 45],
    [100, 20],
].flatMap(([saturation, value], row) =>
    Array.from({ length: 13 }, (_, column) => {
        const channels =
            column === 12
                ? { hue: 0, saturation: 0, value: 100 * (1 - row / 6) }
                : { hue: column * 30, saturation, value };
        return { ...channels, color: hsvColor(channels), row, column };
    }),
);

function colorControls() {
    const channels = [
        ["hue", "色相", 360],
        ["saturation", "饱和度", 100],
        ["value", "明度", 100],
        ["alpha", "透明度", 100],
    ];
    return `<div class="color-sample"><span data-color-preview style="background:${hsvColor()};opacity:${state.channels.alpha / 100}"></span></div>
        <div class="palette-grid" role="group" aria-label="七行色板，使用方向键选择">${PALETTE_SWATCHES.map((swatch, index) => `<button type="button" style="background:${swatch.color}" aria-label="第 ${swatch.row + 1} 行，第 ${swatch.column + 1} 列，${swatch.color}" aria-pressed="false" tabindex="-1" data-action="grid-color" data-swatch-index="${index}" data-testid="palette-cell-${index}"></button>`).join("")}</div>
        <div class="palette"><span class="demo-label">当前颜色</span><label class="color-hex"><span class="sr-only">颜色值</span><input id="color-hex" value="${hsvColor()}" pattern="#[0-9a-fA-F]{6}" maxlength="7" data-testid="color-hex"/></label></div>
        <details class="color-details"><summary>精细调整色相与透明度</summary><div>${channels.map(([id, label, max]) => `<label class="color-channel"><span>${label}</span><input type="range" min="0" max="${max}" value="${state.channels[id]}" data-channel="${id}" class="range color-range color-range-${id}" aria-label="${label}" data-testid="color-${id}"/><output data-channel-output="${id}">${state.channels[id]}</output></label>`).join("")}</div></details>`;
}

function mediaRows(items, context = "gallery") {
    return items
        .map(
            (media) =>
                `<div class="media-row ${state.selection[context].has(media.id) ? "is-selected" : ""}" data-media-row="${media.id}" data-context="${context}"><button type="button" class="media-main" data-action="select-media" data-context="${context}" data-media="${media.id}" aria-pressed="${state.selection[context].has(media.id)}" data-testid="${context}-media-${media.id}">${mediaThumb(media)}<span class="setting-copy"><strong>${escapeHtml(media.title)}</strong><small>${media.subtitle} · ${media.resolution}</small></span><span class="media-time">${media.duration}</span></button>${iconButton("moreVert", `${media.title}的更多操作`, `data-action="media-menu" data-media="${media.id}" data-testid="${context}-more-${media.id}"`)}</div>`,
        )
        .join("");
}

function tableData(context) {
    const sort = state.sort[context];
    const query = context === "library" ? state.libraryQuery : "";
    return MEDIA.filter((media) =>
        `${media.title} ${media.subtitle} ${media.resolution}`.toLowerCase().includes(query.toLowerCase()),
    )
        .filter((media) => context !== "library" || state.librarySection !== "收藏" || state.favorites.has(media.id))
        .slice(0, context === "gallery" ? 4 : 6)
        .sort(
            (left, right) =>
                (typeof left[sort.key] === "number"
                    ? left[sort.key] - right[sort.key]
                    : String(left[sort.key]).localeCompare(String(right[sort.key]), "zh-CN")) * sort.direction,
        );
}

function mediaTable(context) {
    const items = tableData(context);
    const selected = state.selection[context];
    const sort = state.sort[context];
    return `<div class="table-toolbar"><span data-selected-count="${context}">已选择 ${items.filter((media) => selected.has(media.id)).length} 项</span><span class="demo-caption">点击表头排序</span></div><div class="table-wrap"><table class="media-table" data-table-context="${context}"><thead><tr><th class="selection-cell"><input class="check" type="checkbox" aria-label="全选当前列表" data-table-all="${context}" data-testid="${context}-select-all"/></th>${[
        ["title", "名称"],
        ["seconds", "时长"],
        ["sizeValue", "大小"],
    ]
        .map(
            ([key, title]) =>
                `<th scope="col" aria-sort="${sort.key === key ? (sort.direction === 1 ? "ascending" : "descending") : "none"}"><button type="button" data-action="sort-table" data-context="${context}" data-sort="${key}" data-testid="${context}-sort-${key}">${title}<span aria-hidden="true">${sort.key === key ? (sort.direction === 1 ? "↑" : "↓") : "↕"}</span></button></th>`,
        )
        .join(
            "",
        )}</tr></thead><tbody>${items.map((media) => `<tr class="${selected.has(media.id) ? "is-selected" : ""}" data-table-row="${media.id}" data-context="${context}"><td class="selection-cell"><input class="check" type="checkbox" aria-label="选择${media.title}" data-select-media="${media.id}" data-context="${context}" ${selected.has(media.id) ? "checked" : ""} data-testid="${context}-select-${media.id}"/></td><td><button type="button" class="table-name" data-action="select-media" data-media="${media.id}" data-context="${context}" aria-pressed="${selected.has(media.id)}" data-testid="${context}-row-${media.id}">${icon("video")}${media.title}</button></td><td>${media.duration}</td><td>${media.size}</td></tr>`).join("")}</tbody></table>${!items.length ? '<p class="table-empty">没有匹配的视频</p>' : ""}</div>`;
}

function queueControls() {
    return `<div class="queue-list" data-queue>${state.queue
        .map((id, index) => {
            const media = MEDIA.find((item) => item.id === id);
            return `<div class="queue-item ${id === state.activeMedia ? "is-current" : ""}" data-queue-item="${id}"><span class="drag-handle" draggable="true" data-drag-media="${id}" aria-hidden="true">⠿</span><button type="button" class="queue-main" data-action="queue-play" data-media="${id}" aria-label="播放${media.title}" data-testid="queue-play-${id}"><span class="queue-index">${id === state.activeMedia ? icon("volumeUp") : index + 1}</span><span>${media.title}<small>${media.duration}</small></span></button>${iconButton("arrowUpward", `上移${media.title}`, `data-action="queue-move" data-media="${id}" data-delta="-1" ${index === 0 ? "disabled" : ""} data-testid="queue-up-${id}"`)}${iconButton("arrowDownward", `下移${media.title}`, `data-action="queue-move" data-media="${id}" data-delta="1" ${index === state.queue.length - 1 ? "disabled" : ""} data-testid="queue-down-${id}"`)}</div>`;
        })
        .join("")}</div>`;
}

function playbackControls(prefix = "controls") {
    return `<div class="player-button-row"><div class="demo-row">${iconButton("shuffle", "切换随机播放", `data-action="toggle-button" aria-pressed="false" data-testid="${prefix}-shuffle"`)}</div><div class="demo-row">${iconButton("skipPrevious", "上一个视频", `data-action="previous" data-testid="${prefix}-previous"`)}${iconButton(state.isPlaying ? "pause" : "play", state.isPlaying ? "暂停" : "播放", `data-action="play" data-play-toggle data-testid="${prefix}-play"`, "primary")}${iconButton("skipNext", "下一个视频", `data-action="next" data-testid="${prefix}-next"`)}</div><div class="demo-row">${iconButton("loop", "切换循环播放", `data-action="toggle-loop" aria-pressed="${state.switches.loop}" data-loop-toggle data-testid="${prefix}-loop"`)}</div></div><div class="volume-control">${icon("volumeUp")}<input type="range" class="range" min="0" max="100" value="${state.volume}" data-player-volume aria-label="播放音量" style="--value:${state.volume}%" data-testid="${prefix}-volume"/><output data-volume-output>${state.volume}%</output></div>`;
}

function seekControl(prefix = "seek") {
    const media = MEDIA.find((item) => item.id === state.activeMedia);
    return `<div class="seek-control"><input type="range" class="range seek-range" min="0" max="${media.seconds}" value="${state.playbackSeconds}" data-seek aria-label="播放进度" aria-valuetext="${formatTime(state.playbackSeconds)}" style="--value:${(state.playbackSeconds / media.seconds) * 100}%" data-testid="${prefix}-position"/><div class="range-scale"><span data-current-time>${formatTime(state.playbackSeconds)}</span><span data-duration>${media.duration}</span></div></div>`;
}

function subtitleControls(prefix = "subtitle") {
    return `<div class="subtitle-preview">${landscape("mountain")}<span data-subtitle-text style="font-size:${state.subtitleSize}px;color:${state.subtitleColor};font-weight:${state.switches.isBold ? 600 : 400};background:${state.switches.subtitleBackground ? "#00000077" : "transparent"}">山的那边，还是山。</span></div><div class="range-head"><label for="${prefix}-size">文字大小</label><output data-subtitle-size>${state.subtitleSize}</output></div><input type="range" class="range" id="${prefix}-size" min="14" max="36" value="${state.subtitleSize}" data-subtitle-size-input style="--value:${((state.subtitleSize - 14) / 22) * 100}%" data-testid="${prefix}-size"/>${settingRow("字幕背景", "", switchControl("subtitleBackground", "显示字幕背景"))}<div class="demo-row"><span class="demo-label">文字颜色</span>${["#ffffff", "#ffe7a1", "#9edaff"].map((color, index) => `<button type="button" class="swatch-button" style="--swatch:${color}" aria-label="${["白色字幕", "淡黄色字幕", "淡蓝色字幕"][index]}" aria-pressed="${state.subtitleColor === color}" data-action="subtitle-color" data-color="${color}" data-testid="${prefix}-color-${index}"></button>`).join("")}</div>`;
}

function formatTime(seconds) {
    return `${String(Math.floor(seconds / 60)).padStart(2, "0")}:${String(Math.floor(seconds % 60)).padStart(2, "0")}`;
}

Object.assign(previews, {
    "floating-actions": () =>
        `<div class="demo-row spread"><button type="button" class="fab" aria-label="添加视频" data-action="create-playlist" data-testid="floating-add">${icon("add")}</button><div class="floating-toolbar">${iconButton("play", "播放选中项", 'data-action="play" data-play-toggle data-testid="floating-play"')}${iconButton("heart", "收藏选中项", 'data-action="toggle-button" aria-pressed="false" data-testid="floating-favorite"')}<span class="toolbar-divider"></span>${iconButton("delete", "移除选中项", 'data-action="delete-dialog" data-testid="floating-delete"')}</div></div><p class="demo-caption">浮动工具条承载当前选中内容的操作。</p>`,
    "filter-chips": () =>
        `<div class="chips" aria-label="分辨率筛选">${["全部", "超高清", "全高清", "已收藏"].map((text, index) => `<button type="button" class="chip" aria-pressed="${(state.segments.filters ?? 0) === index}" data-action="filter-chip" data-index="${index}" data-testid="filter-${index}">${text}</button>`).join("")}</div><div class="demo-row" data-filter-tags><span class="removable-tag">旅行记录${iconButton("close", "移除旅行记录筛选", 'data-action="remove-tag" data-testid="remove-tag"')}</span><span class="demo-caption" data-filter-summary>全部内容</span></div>`,
    "search-input": () =>
        `${searchBox("demo-search", "搜索视频", state.values["demo-search"] ?? "")}<div class="search-results" id="demo-search-results" aria-live="polite">${MEDIA.slice(
            0,
            2,
        )
            .map(
                (media) =>
                    `<button type="button" data-action="search-result" data-media="${media.id}" data-testid="search-result-${media.id}">${icon("video")}<span>${media.title}</span><small>${media.duration}</small></button>`,
            )
            .join("")}</div>`,
    dropdowns: () =>
        `${preferenceButton("默认画质", "", state.values.quality ?? "原始画质", "quality-menu", "quality-menu")}${preferenceButton("字幕语言", "", state.values.language ?? "简体中文", "language-menu", "language-menu")}<p class="demo-caption">选中项显示勾选；使用方向键也能操作。</p>`,
    "range-sliders": () => dualRange(),
    "vertical-sliders": () =>
        `<div class="equalizer">${[
            ["60 赫兹", 3],
            ["230 赫兹", 1],
            ["910 赫兹", -2],
            ["3.6 千赫", 2],
            ["14 千赫", 4],
        ]
            .map(
                ([label, value], index) =>
                    `<label class="equalizer-band"><output id="equalizer-${index}-output">${state.values[`equalizer-${index}`] ?? value} 分贝</output><input type="range" class="range vertical-range" id="equalizer-${index}" min="-12" max="12" value="${state.values[`equalizer-${index}`] ?? value}" style="--value:${(((state.values[`equalizer-${index}`] ?? value) + 12) / 24) * 100}%" aria-label="${label}增益" data-suffix=" 分贝" data-testid="equalizer-${index}"/><span>${label}</span></label>`,
            )
            .join("")}</div>`,
    "number-picker": () =>
        `<div class="number-demo"><div class="number-wheel" role="spinbutton" tabindex="0" aria-label="定时关闭，分钟" aria-valuemin="5" aria-valuemax="120" aria-valuenow="${state.values.timer ?? 30}" data-number-wheel data-testid="number-wheel"><span data-wheel-before>${(state.values.timer ?? 30) - 5}</span><strong data-wheel-value>${state.values.timer ?? 30}</strong><span data-wheel-after>${(state.values.timer ?? 30) + 5}</span></div><div class="demo-stack"><span class="demo-label">字幕偏移 · 毫秒</span>${numberStepper()}<span class="demo-caption">可滚动调整定时，或直接输入偏移。</span></div></div>`,
    "color-picker": colorControls,
    "navigation-rail": () =>
        `<div class="rail-demo"><nav class="mini-rail ${state.isRailExpanded ? "is-expanded" : ""}" aria-label="侧栏示例">${iconButton("menu", state.isRailExpanded ? "收起侧栏" : "展开侧栏", 'data-action="toggle-rail" data-testid="rail-toggle"')}${[
            ["folder", "媒体库"],
            ["heart", "收藏"],
            ["history", "最近播放"],
        ]
            .map(
                ([name, title], index) =>
                    `<button type="button" data-action="rail-select" data-index="${index}" class="${(state.segments.rail ?? 0) === index ? "is-selected" : ""}" aria-label="${title}" aria-pressed="${(state.segments.rail ?? 0) === index}" data-tooltip="${title}" data-testid="rail-item-${index}">${icon(name)}<span>${title}</span></button>`,
            )
            .join(
                "",
            )}</nav><div class="rail-demo-content"><span data-rail-label>${["媒体库", "收藏", "最近播放"][state.segments.rail ?? 0]}</span><div class="skeleton"></div><div class="skeleton"></div></div></div>`,
    "navigation-bar": () =>
        `<nav class="bottom-nav" aria-label="底部导航示例">${[
            ["folder", "媒体库"],
            ["cloud", "云端"],
            ["starLine", "收藏"],
            ["settings", "设置"],
        ]
            .map(
                ([name, title], index) =>
                    `<button type="button" data-action="bottom-nav" data-index="${index}" aria-pressed="${(state.segments.bottomNav ?? 0) === index}" data-testid="bottom-nav-${index}">${icon(name)}<span>${title}</span></button>`,
            )
            .join(
                "",
            )}</nav><nav class="floating-toolbar floating-nav" aria-label="悬浮导航示例">${["folder", "cloud", "starLine", "settings"].map((name, index) => iconButton(name, ["媒体库", "云端", "收藏", "设置"][index], `data-action="floating-nav" aria-pressed="${(state.segments.floatingNav ?? 0) === index}" data-index="${index}" data-testid="floating-nav-${index}"`, (state.segments.floatingNav ?? 0) === index ? "selected" : "")).join("")}</nav>`,
    breadcrumbs: () =>
        `${breadcrumb("demo-path")}<div class="directory-hint">${icon("folder")}<span data-path-label>旅行记录 · 6 个视频</span></div>`,
    "app-bar": () =>
        `<div class="sample-appbar">${iconButton("arrowLeftCircle", "返回媒体库", 'data-scene="library" data-testid="sample-back"')}<strong>旅行记录</strong><span></span>${iconButton("search", "搜索控件", 'data-action="focus-search" data-testid="sample-search"')}${iconButton("moreVert", "更多操作", 'data-action="general-menu" data-testid="sample-more"')}</div><div class="layout-blocks" aria-label="侧栏、内容和详情的三栏布局示意"><span>侧栏</span><span>内容区域</span><span>详情</span></div>`,
    "window-caption": () =>
        `<div class="window-sample"><div class="caption-bar"><span class="caption-brand">${icon("play")}<span>Only Player</span></span><div class="caption-controls"><button type="button" aria-label="模拟最小化" data-action="caption-minimize" data-testid="caption-minimize">−</button><button type="button" aria-label="模拟最大化" data-action="caption-maximize" data-testid="caption-maximize">□</button><button type="button" aria-label="模拟关闭窗口" data-action="caption-close" data-testid="caption-close">×</button></div></div><div class="window-sample-content">窗口标题栏预览</div></div><p class="demo-caption">模拟外观与状态，原生窗口功能在桌面应用阶段接入。</p>`,
    cards: () =>
        `<div class="sample-cards">${[
            ["folder", "本地视频", "6 个文件"],
            ["starLine", "我的收藏", "3 个文件"],
        ]
            .map(
                ([name, title, summary], index) =>
                    `<button type="button" class="sample-card ${state.values[`card-${index}`] ? "is-selected" : ""}" aria-pressed="${Boolean(state.values[`card-${index}`])}" data-action="card-select" data-index="${index}" data-testid="sample-card-${index}">${icon(name)}<strong>${title}</strong><small>${summary}</small></button>`,
            )
            .join("")}</div><p class="demo-caption">点击卡片切换选中；常规卡片保持平整。</p>`,
    preferences: () =>
        `<div class="preference-group">${settingRow("继续上次播放", "记住每个视频的位置", switchControl("resume", "继续上次播放"), "history")}${preferenceButton("音频设置", "均衡器和输出", "", "drawer", "preference-audio", "audio")}${preferenceButton("字幕语言", "", state.values.language ?? "简体中文", "language-menu", "preference-language", "caption")}</div>`,
    "file-list": () => mediaRows(MEDIA.slice(0, 2)),
    "file-tree": () =>
        `<div class="file-tree"><details open><summary>${icon("folder")}视频<span>6</span></summary><div class="tree-children"><details open><summary>${icon("folder")}旅行记录<span>3</span></summary><div class="tree-children">${["向山而行", "海边的早晨", "穿过森林"].map((title, index) => `<button type="button" data-action="tree-file" data-title="${title}" aria-pressed="false" data-testid="tree-file-${index}">${icon("video")}${title}</button>`).join("")}</div></details><details><summary>${icon("folder")}短片<span>2</span></summary><div class="tree-children"><button type="button" data-action="tree-file" data-title="风经过的地方" aria-pressed="false" data-testid="tree-short">${icon("video")}风经过的地方</button></div></details><button type="button" data-action="tree-file" data-title="城市慢下来" aria-pressed="false" data-testid="tree-city">${icon("video")}城市慢下来</button></div></details></div>`,
    "data-table": () => `<div data-table-host="gallery">${mediaTable("gallery")}</div>`,
    scrollbars: () =>
        `<div class="scroll-sample" tabindex="0" role="region" aria-label="可滚动的文件列表" data-testid="scroll-container">${Array.from({ length: 12 }, (_, index) => `<div>${icon("video")}旅行片段 ${String(index + 1).padStart(2, "0")}<span>${index + 2}:30</span></div>`).join("")}</div><p class="demo-caption">鼠标滚轮、拖动滚动条或键盘翻页。</p>`,
    "badges-dividers": () =>
        `<div class="demo-row spread"><span class="badged-icon">${icon("folder")}<span class="badge">6</span></span><span class="badged-icon">${icon("cloud")}<span class="badge is-dot"></span></span><span class="count-tag">已收藏 <strong>3</strong></span></div><div class="sample-divider"></div><div class="demo-row"><span class="small-title">播放选项</span><span class="vertical-divider"></span><span class="demo-label">让分组有清楚的边界</span></div>`,
    dialogs: () =>
        `<div class="dialog-mini"><span class="dialog-mini-icon">${icon("playlistPlay")}</span><strong>为喜欢的片段建个片单</strong><p>名称、确认与取消，简单清楚。</p></div><div class="demo-row">${button("新建播放列表", "primary", 'data-action="create-playlist" data-testid="open-create-dialog"')}${button("确认对话框", "", 'data-action="delete-dialog" data-testid="open-delete-dialog"')}</div>`,
    drawers: () =>
        `<div class="drawer-illustration"><span>画面上下文</span><div><i></i><i></i><i></i></div></div>${button("打开快捷设置", "primary", 'data-action="drawer" data-testid="open-drawer"')}`,
    menus: () =>
        `<button type="button" class="context-target" data-action="general-menu" data-context-target data-testid="context-target">${icon("movie")}<span>旅行记录<small>点击或在这里按右键</small></span>${icon("moreVert")}</button><p class="demo-caption">菜单会避让边缘，也支持方向键选择。</p>`,
    "cascading-menu": () =>
        `<div class="menu-illustration"><div>播放方式<span>›</span></div><div class="submenu-illustration">顺序播放<span>✓</span></div></div>${button("展开级联菜单", "", 'data-action="cascade-menu" data-testid="open-cascade"')}`,
    tooltips: () =>
        `<div class="demo-row spread">${iconButton("pip", "画中画 · 浮动播放窗口", 'data-action="toast" data-message="已触发画中画操作" data-testid="tooltip-plain"', "filled")}${button("查看丰富提示", "soft", 'data-action="rich-tooltip" data-testid="rich-tooltip"')}<span class="demo-label">悬停或聚焦左侧图标</span></div><div class="tooltip-example" aria-hidden="true">播放 / 暂停 · 空格</div>`,
    snackbar: () =>
        `<div class="snackbar-example">${icon("check")}<span>已添加到播放列表</span><span>撤销</span></div><div class="demo-row">${button("显示消息", "", 'data-action="toast" data-message="播放设置已保存" data-testid="show-toast"')}${button("体验撤销", "primary", 'data-action="undo-demo" data-testid="show-undo"')}</div>`,
    "empty-states": () =>
        `<div class="mini-empty">${icon("folder")}<strong>还没有视频</strong><span>添加一个文件夹，开始整理你的片库。</span>${button("添加文件夹", "soft", 'data-action="empty-add" data-testid="empty-add"')}</div><div class="skeleton-row" aria-label="加载占位示意"><i class="skeleton"></i><div><i class="skeleton"></i><i class="skeleton"></i></div></div>`,
    refresh: () =>
        `<div class="refresh-status">${state.isScanning ? '<span class="spinner is-large" aria-hidden="true"></span>' : icon("update")}<span><strong data-scan-status>${state.isScanning ? "正在扫描媒体" : "媒体库已是最新"}</strong><small data-scan-detail>${state.isScanning ? "正在检查文件夹…" : "6 个视频 · 可以重新扫描"}</small></span></div>${button(state.isScanning ? "正在刷新…" : "刷新媒体库", "primary", `data-action="scan" data-scan-button ${state.isScanning ? 'disabled aria-busy="true"' : ""} data-testid="scan-library"`)}<div class="linear-progress ${state.isScanning ? "is-infinite" : ""}" data-scan-progress><span style="--progress:${state.isScanning ? 35 : 100}%"></span></div>`,
    "playback-controls": () => playbackControls(),
    seekbar: () => `${seekControl()}${dualRange("playback-interval")}`,
    queue: queueControls,
    "subtitle-style": subtitleControls,
    "player-options": () =>
        `${preferenceButton("播放速度", "", `${state.speed} 倍`, "speed-menu", "options-speed")}${preferenceButton("画面比例", "", state.ratio, "ratio-menu", "options-ratio")}${settingRow("循环播放", "当前视频结束后重新播放", switchControl("loop", "循环播放"))}`,
});

function refreshComponent(id, focusId = "") {
    const stage = $(`#component-${id} .demo-stage`);
    if (!stage) return;
    stage.innerHTML = previews[id]();
    hydrate(stage);
    hydrateExtended(stage);
    if (focusId) $(`[data-testid="${focusId}"]`, stage)?.focus();
}

function hydrateExtended(root) {
    $$(".range", root).forEach(updateRangeAppearance);
    syncSelection("gallery");
    syncSelection("library");
    updatePlayControls();
    if ($("[data-color-preview]", root)) syncColors();
    if ($("[data-range-end]", root)) syncRange();
    if ($("[data-number-wheel]", root)) updateWheel(0);
    $$(".skeleton-row, .layout-blocks", root).forEach((element) => element.setAttribute("role", "img"));
    $$("[data-range-end]", root).forEach((input) => {
        input.setAttribute("aria-valuetext", `${input.value} 秒`);
    });
    const filterSummary = $("[data-filter-summary]", root);
    if (filterSummary)
        filterSummary.textContent = ["全部内容", "只显示超高清视频", "只显示全高清视频", "只显示收藏内容"][
            state.segments.filters ?? 0
        ];
}

function menuItems() {
    return [
        {
            label: "播放",
            icon: "play",
            run: () => {
                state.isPlaying = true;
                updatePlayControls();
                showToast("开始播放演示视频");
            },
        },
        { label: "加入播放队列", icon: "playlistPlay", run: () => addToQueue(state.activeMedia) },
        {
            label: "播放方式",
            icon: "loop",
            children: ["顺序播放", "单个循环", "随机播放"].map((label) => ({
                label,
                isChecked: (state.values.playOrder ?? "顺序播放") === label,
                run: () => {
                    state.values.playOrder = label;
                    showToast(`已切换为${label}`);
                },
            })),
        },
        { separator: true },
        { label: "复制文件名称", icon: "copy", run: () => showToast("已触发复制操作：向山而行") },
        { label: "查看文件信息", icon: "info", run: () => showMediaInfo(state.activeMedia) },
        { label: "导出缩略图", icon: "image", isDisabled: true },
        { separator: true },
        { label: "从列表移除", icon: "delete", isDanger: true, run: () => ACTIONS["delete-dialog"]() },
    ];
}

function positionPopup(popup, anchor, point, isSubmenu) {
    popup.style.left = "0px";
    popup.style.top = "0px";
    const bounds = anchor.getBoundingClientRect();
    const size = popup.getBoundingClientRect();
    let left = point ? point.x : isSubmenu ? bounds.right + 6 : bounds.left;
    let top = point ? point.y : isSubmenu ? bounds.top - 8 : bounds.bottom + 7;
    if (isSubmenu && left + size.width > innerWidth - 8) left = bounds.left - size.width - 6;
    if (!isSubmenu && top + size.height > innerHeight - 8 && bounds.top > size.height && !point)
        top = bounds.top - size.height - 7;
    popup.style.left = `${Math.max(8, Math.min(left, innerWidth - size.width - 8))}px`;
    popup.style.top = `${Math.max(8, Math.min(top, innerHeight - size.height - 8))}px`;
}

function paintMenu(items, level, anchor, point = null) {
    const host = $("#menu-root");
    $$(`[data-menu-level]`, host)
        .filter((panel) => Number(panel.dataset.menuLevel) >= level)
        .forEach((panel) => panel.remove());
    menuState.levels = menuState.levels.slice(0, level);
    menuState.levels.push({ items, anchor });
    const popup = document.createElement("div");
    popup.className = "menu-popup";
    popup.dataset.menuLevel = level;
    popup.setAttribute("role", "menu");
    popup.setAttribute("aria-label", level ? "更多播放方式" : menuState.label);
    popup.innerHTML = items
        .map((item, index) =>
            item.separator
                ? '<div class="menu-separator" role="separator"></div>'
                : `<button type="button" role="${Object.hasOwn(item, "isChecked") ? "menuitemradio" : "menuitem"}" ${Object.hasOwn(item, "isChecked") ? `aria-checked="${item.isChecked}"` : ""} ${item.children ? 'aria-haspopup="menu" aria-expanded="false"' : ""} class="${item.isDanger ? "menu-danger" : ""}" ${item.isDisabled ? "disabled" : ""} data-action="menu-select" data-level="${level}" data-menu-index="${index}" tabindex="-1" data-testid="menu-${level}-${index}">${item.icon ? icon(item.icon) : ""}<span>${item.label}</span>${item.children ? '<span aria-hidden="true">›</span>' : item.isChecked ? `<span>${icon("check")}</span>` : ""}</button>`,
        )
        .join("");
    host.append(popup);
    positionPopup(popup, anchor, point, level > 0);
    const first = $('button[aria-checked="true"]:not(:disabled)', popup) || $("button:not(:disabled)", popup);
    first?.focus({ preventScroll: true });
}

function openMenu(anchor, items, label = "操作菜单", point = null) {
    closeMenu(false);
    hideTooltip();
    const content = $("#content");
    menuState = {
        anchor,
        label,
        levels: [],
        returnFocus: anchor,
        scrollTop: content.scrollTop,
        scrollLeft: content.scrollLeft,
    };
    anchor.setAttribute("aria-expanded", "true");
    anchor.setAttribute("aria-haspopup", "menu");
    const activeDialog = $("dialog[open]");
    if (activeDialog) activeDialog.append($("#menu-root"));
    paintMenu(items, 0, anchor, point);
}

function closeMenu(shouldRestoreFocus = true) {
    if (!menuState) return;
    const current = menuState;
    menuState = null;
    current.anchor.setAttribute("aria-expanded", "false");
    $("#menu-root").replaceChildren();
    document.body.append($("#menu-root"));
    if (shouldRestoreFocus && current.returnFocus.isConnected) current.returnFocus.focus();
}

function optionMenu(anchor, labels, value, onSelect, label) {
    openMenu(
        anchor,
        labels.map((text) => ({ label: text, isChecked: text === value, run: () => onSelect(text) })),
        label,
    );
}

function updatePreferenceValue(action, value) {
    $$(`[data-action="${action}"] .preference-value`).forEach((element) => {
        element.textContent = value;
    });
}

function syncSelection(context) {
    const items = tableData(context);
    const selected = state.selection[context];
    const count = items.filter((item) => selected.has(item.id)).length;
    $$(`[data-table-all="${context}"]`).forEach((checkbox) => {
        checkbox.checked = items.length > 0 && count === items.length;
        checkbox.indeterminate = count > 0 && count < items.length;
        checkbox.disabled = items.length === 0;
    });
    $$(`[data-selected-count="${context}"]`).forEach((element) => {
        element.textContent = `已选择 ${count} 项`;
    });
    $$(`[data-select-media][data-context="${context}"]`).forEach((input) => {
        input.checked = selected.has(Number(input.dataset.selectMedia));
    });
    $$(
        `[data-table-row][data-context="${context}"], [data-media-row][data-context="${context}"], [data-grid-media][data-context="${context}"]`,
    ).forEach((row) => {
        row.classList.toggle(
            "is-selected",
            selected.has(Number(row.dataset.tableRow ?? row.dataset.mediaRow ?? row.dataset.gridMedia)),
        );
    });
    $$(`[data-action="select-media"][data-context="${context}"]`).forEach((button) => {
        button.setAttribute("aria-pressed", String(selected.has(Number(button.dataset.media))));
    });
}

function selectMedia(id, context, event) {
    const selection = state.selection[context];
    const previous = state.lastSelected[context];
    const ids = tableData(context).map((media) => media.id);
    if (event.shiftKey && previous !== undefined && ids.includes(previous)) {
        const start = ids.indexOf(previous);
        const end = ids.indexOf(id);
        ids.slice(Math.min(start, end), Math.max(start, end) + 1).forEach((mediaId) => selection.add(mediaId));
    } else if (event.ctrlKey || event.metaKey) {
        if (selection.has(id)) selection.delete(id);
        else selection.add(id);
    } else {
        const shouldClear = selection.size === 1 && selection.has(id);
        selection.clear();
        if (!shouldClear) selection.add(id);
    }
    state.lastSelected[context] = id;
    syncSelection(context);
}

function syncRange() {
    $$(".dual-range").forEach((element) => {
        element.style.setProperty("--start", state.rangeStart);
        element.style.setProperty("--end", state.rangeEnd);
    });
    $$("[data-range-end]").forEach((input) => {
        input.value = input.dataset.rangeEnd === "start" ? state.rangeStart : state.rangeEnd;
        input.setAttribute("aria-valuetext", `${input.value} 秒`);
    });
    $$("[data-range-output]").forEach((element) => {
        element.textContent = `${state.rangeStart} — ${state.rangeEnd} 秒`;
    });
}

function setHexColor(color) {
    const rgb = color
        .slice(1)
        .match(/../g)
        .map((channel) => parseInt(channel, 16) / 255);
    const max = Math.max(...rgb);
    const min = Math.min(...rgb);
    const delta = max - min;
    let hue = state.channels.hue;
    if (delta)
        hue =
            ((max === rgb[0]
                ? (rgb[1] - rgb[2]) / delta
                : max === rgb[1]
                  ? (rgb[2] - rgb[0]) / delta + 2
                  : (rgb[0] - rgb[1]) / delta + 4) *
                60 +
                360) %
            360;
    state.channels = {
        ...state.channels,
        hue: Math.round(hue),
        saturation: max === 0 ? 0 : Math.round((delta / max) * 100),
        value: Math.round(max * 100),
    };
    syncColors();
}

function syncColors() {
    const color = hsvColor();
    $$("[data-color-preview]").forEach((element) => {
        element.style.background = color;
        element.style.opacity = state.channels.alpha / 100;
    });
    $$("[data-channel]").forEach((input) => {
        input.value = state.channels[input.dataset.channel];
        input.style.setProperty("--hue", state.channels.hue);
        input.style.setProperty("--channel-color", color);
    });
    $$("[data-channel-output]").forEach((output) => {
        output.textContent = state.channels[output.dataset.channelOutput];
    });
    if ($("#color-hex") && document.activeElement !== $("#color-hex")) $("#color-hex").value = color;
    const isGray = state.channels.saturation < 5;
    const nearestColumn = isGray ? 12 : Math.min(11, Math.round(state.channels.hue / 30));
    const candidates = PALETTE_SWATCHES.filter((swatch) => swatch.column === nearestColumn);
    const nearest = candidates.reduce((best, swatch) => {
        const distance = (candidate) =>
            (isGray ? 0 : (candidate.saturation - state.channels.saturation) ** 2) +
            (candidate.value - state.channels.value) ** 2;
        return distance(swatch) < distance(best) ? swatch : best;
    });
    $$("[data-swatch-index]").forEach((button) => {
        const isSelected = PALETTE_SWATCHES[Number(button.dataset.swatchIndex)] === nearest;
        button.setAttribute("aria-pressed", String(isSelected));
        button.tabIndex = isSelected ? 0 : -1;
    });
}

function updateWheel(delta) {
    const value = Math.max(5, Math.min(120, (state.values.timer ?? 30) + delta));
    state.values.timer = value;
    const wheel = $("[data-number-wheel]");
    if (!wheel) return;
    wheel.setAttribute("aria-valuenow", value);
    $("[data-wheel-value]", wheel).textContent = value;
    $("[data-wheel-before]", wheel).textContent = value > 5 ? value - 5 : "—";
    $("[data-wheel-after]", wheel).textContent = value < 120 ? value + 5 : "—";
}

function syncPlayback() {
    const media = MEDIA.find((item) => item.id === state.activeMedia);
    $$("[data-seek]").forEach((input) => {
        input.max = media.seconds;
        input.value = state.playbackSeconds;
        input.style.setProperty("--value", `${(state.playbackSeconds / media.seconds) * 100}%`);
        input.setAttribute("aria-valuetext", formatTime(state.playbackSeconds));
    });
    $$("[data-current-time]").forEach((element) => {
        element.textContent = formatTime(state.playbackSeconds);
    });
    $$("[data-duration]").forEach((element) => {
        element.textContent = media.duration;
    });
    $$("[data-playing-title]").forEach((element) => {
        element.textContent = media.title;
    });
    updatePlayControls();
}

function updateQueue(focusId = "") {
    $$("[data-queue]").forEach((element) => {
        element.outerHTML = queueControls();
    });
    if (focusId) $(`[data-testid="${focusId}"]`)?.focus();
}

function playMedia(id) {
    state.activeMedia = id;
    state.playbackSeconds = 0;
    state.isPlaying = true;
    if (!state.queue.includes(id)) state.queue.push(id);
    updateQueue();
    syncPlayback();
    refreshPlayerArt();
}

function addToQueue(id) {
    if (state.queue.includes(id)) {
        showToast("这个视频已经在播放队列里");
        return;
    }
    state.queue.push(id);
    updateQueue();
    showToast("已加入播放队列");
}

function moveQueue(id, newIndex) {
    const oldIndex = state.queue.indexOf(id);
    if (oldIndex === newIndex || newIndex < 0 || newIndex >= state.queue.length) return;
    state.queue.splice(oldIndex, 1);
    state.queue.splice(newIndex, 0, id);
    updateQueue(`queue-play-${id}`);
}

function onSwitchChanged(key) {
    if (key === "loop")
        $$("[data-loop-toggle]").forEach((button) => {
            button.setAttribute("aria-pressed", String(state.switches.loop));
            button.classList.toggle("is-selected", state.switches.loop);
        });
    if (key === "subtitleBackground" || key === "isBold") syncSubtitles();
}

function syncSubtitles() {
    $$("[data-subtitle-text]").forEach((element) => {
        element.style.fontSize = `${state.subtitleSize}px`;
        element.style.color = state.subtitleColor;
        element.style.background = state.switches.subtitleBackground ? "#00000077" : "transparent";
        element.style.fontWeight = state.switches.isBold ? "600" : "400";
    });
    $$("[data-subtitle-size]").forEach((element) => {
        element.textContent = state.subtitleSize;
    });
    $$("[data-subtitle-size-input]").forEach((input) => {
        input.value = state.subtitleSize;
        updateRangeAppearance(input);
    });
    $$('[data-action="subtitle-color"]').forEach((button) => {
        button.setAttribute("aria-pressed", String(button.dataset.color === state.subtitleColor));
    });
}

function showMediaInfo(id) {
    const media = MEDIA.find((item) => item.id === id);
    showDialog(
        `<div class="dialog-head"><h2 id="dialog-title">文件信息</h2>${iconButton("close", "关闭文件信息", 'data-action="dialog-close"')}</div><div class="info-art">${landscape(media.art)}</div><p><strong>${media.title}</strong><br>${media.subtitle}</p><div class="source-api">时长：${media.duration}<br>大小：${media.size}<br>分辨率：${media.resolution}<br>添加日期：${media.date}</div><div class="dialog-actions">${button("关闭", "primary", 'data-action="dialog-close"')}</div>`,
    );
}

Object.assign(ACTIONS, {
    "clear-field": (element) => {
        const input = document.getElementById(element.dataset.target);
        input.value = "";
        input.dispatchEvent(new Event("input", { bubbles: true }));
        input.focus();
    },
    "filter-chip": (element) => {
        state.segments.filters = Number(element.dataset.index);
        $$("[data-action='filter-chip']").forEach((button) =>
            button.setAttribute("aria-pressed", String(button === element)),
        );
        $("[data-filter-summary]").textContent = ["全部内容", "只显示超高清视频", "只显示全高清视频", "只显示收藏内容"][
            state.segments.filters
        ];
    },
    "remove-tag": (element) => {
        element.closest(".removable-tag").remove();
        $("[data-filter-summary]").textContent = "已清除文件夹筛选";
        $("[data-action='filter-chip']").focus();
    },
    "search-result": (element) => {
        playMedia(Number(element.dataset.media));
        showToast(`已选择：${MEDIA.find((item) => item.id === state.activeMedia).title}`);
    },
    "quality-menu": (element) =>
        optionMenu(
            element,
            ["原始画质", "超高清", "全高清", "高清"],
            state.values.quality ?? "原始画质",
            (value) => {
                state.values.quality = value;
                updatePreferenceValue("quality-menu", value);
            },
            "选择默认画质",
        ),
    "language-menu": (element) =>
        optionMenu(
            element,
            ["简体中文", "繁体中文", "英语", "跟随视频"],
            state.values.language ?? "简体中文",
            (value) => {
                state.values.language = value;
                updatePreferenceValue("language-menu", value);
            },
            "选择字幕语言",
        ),
    "speed-menu": (element) =>
        optionMenu(
            element,
            ["0.5", "0.75", "1.0", "1.25", "1.5", "2.0"].map((speed) => `${speed} 倍`),
            `${state.speed} 倍`,
            (value) => {
                state.speed = value.split(" ")[0];
                updatePreferenceValue("speed-menu", value);
                $$("[data-speed-text]").forEach((label) => {
                    label.textContent = value;
                });
            },
            "播放速度",
        ),
    "ratio-menu": (element) =>
        optionMenu(
            element,
            ["适应窗口", "原始比例", "16:9", "4:3", "填满画面"],
            state.ratio,
            (value) => {
                state.ratio = value;
                updatePreferenceValue("ratio-menu", value);
                showToast(`画面比例：${value}`);
            },
            "画面比例",
        ),
    "menu-select": (element) => {
        const level = Number(element.dataset.level);
        const item = menuState.levels[level].items[Number(element.dataset.menuIndex)];
        if (item.children) {
            element.setAttribute("aria-expanded", "true");
            paintMenu(item.children, level + 1, element);
            return;
        }
        closeMenu();
        item.run();
    },
    "general-menu": (element) => openMenu(element, menuItems()),
    "media-menu": (element) => {
        const id = Number(element.dataset.media);
        openMenu(
            element,
            [
                { label: "播放视频", icon: "play", run: () => playMedia(id) },
                { label: "加入播放队列", icon: "playlistPlay", run: () => addToQueue(id) },
                {
                    label: state.favorites.has(id) ? "取消收藏" : "收藏视频",
                    icon: "heart",
                    run: () => {
                        if (state.favorites.has(id)) state.favorites.delete(id);
                        else state.favorites.add(id);
                        showToast("收藏状态已更新");
                        refreshLibrary();
                    },
                },
                { separator: true },
                { label: "文件信息", icon: "info", run: () => showMediaInfo(id) },
            ],
            "视频操作",
        );
    },
    "cascade-menu": (element) => openMenu(element, menuItems().slice(0, 3), "播放方式菜单"),
    "step-number": (element) => {
        state.offset = Math.min(5000, Math.max(-5000, state.offset + Number(element.dataset.delta)));
        $("#subtitle-offset").value = state.offset;
    },
    "grid-color": (element) => {
        const swatch = PALETTE_SWATCHES[Number(element.dataset.swatchIndex)];
        state.channels = { ...state.channels, hue: swatch.hue, saturation: swatch.saturation, value: swatch.value };
        syncColors();
    },
    "toggle-rail": () => {
        state.isRailExpanded = !state.isRailExpanded;
        refreshComponent("navigation-rail", "rail-toggle");
    },
    "rail-select": (element) => {
        state.segments.rail = Number(element.dataset.index);
        refreshComponent("navigation-rail", element.dataset.testid);
    },
    "bottom-nav": (element) => {
        state.segments.bottomNav = Number(element.dataset.index);
        $$('[data-action="bottom-nav"]').forEach((button) =>
            button.setAttribute("aria-pressed", String(button === element)),
        );
    },
    "floating-nav": (element) => {
        state.segments.floatingNav = Number(element.dataset.index);
        $$('[data-action="floating-nav"]').forEach((button) => {
            button.setAttribute("aria-pressed", String(button === element));
            button.classList.toggle("is-selected", button === element);
        });
    },
    breadcrumb: (element) => {
        const group = element.dataset.group;
        state.segments[group] = Number(element.dataset.index);
        if (group === "demo-path") {
            refreshComponent("breadcrumbs");
            $("[data-path-label]").textContent = [
                "此电脑 · 视频所在的位置",
                "视频 · 3 个文件夹",
                "旅行记录 · 6 个视频",
            ][state.segments[group]];
        } else refreshLibrary();
    },
    "focus-search": () => $("#catalog-search").focus(),
    "caption-minimize": (element) => {
        const sample = element.closest(".window-sample");
        sample.classList.toggle("is-minimized");
    },
    "caption-maximize": (element) => {
        const sample = element.closest(".window-sample");
        sample.classList.remove("is-minimized");
        sample.classList.toggle("is-maximized");
    },
    "caption-close": () => showToast("已触发关闭窗口操作，预览保留在当前页面"),
    "card-select": (element) => {
        const key = `card-${element.dataset.index}`;
        state.values[key] = !state.values[key];
        element.classList.toggle("is-selected", state.values[key]);
        element.setAttribute("aria-pressed", String(state.values[key]));
    },
    "select-media": (element, event) => selectMedia(Number(element.dataset.media), element.dataset.context, event),
    "tree-file": (element) => {
        $$('[data-action="tree-file"]').forEach((button) =>
            button.setAttribute("aria-pressed", String(button === element)),
        );
        showToast(`已选择：${element.dataset.title}`);
    },
    "sort-table": (element) => {
        const context = element.dataset.context;
        const sort = state.sort[context];
        sort.direction = sort.key === element.dataset.sort ? -sort.direction : 1;
        sort.key = element.dataset.sort;
        const host = $(`[data-table-host="${context}"]`);
        host.innerHTML = mediaTable(context);
        syncSelection(context);
        $(`[data-testid="${element.dataset.testid}"]`, host).focus();
    },
    "create-playlist": () => {
        showDialog(
            `<form id="playlist-form"><div class="dialog-head"><h2 id="dialog-title">新建播放列表</h2>${iconButton("close", "关闭新建播放列表", 'data-action="dialog-close" data-testid="create-close"')}</div><p>把喜欢的影片放在一起，下次接着看。</p><div class="field"><label class="field-label" for="new-playlist-name">名称</label><input class="field-input" id="new-playlist-name" name="name" placeholder="例如：周末片单" maxlength="32" required autofocus data-testid="new-playlist-name"/><p class="field-hint" id="new-playlist-hint">最多 32 个字</p></div><div class="dialog-actions">${button("取消", "", 'data-action="dialog-close" data-testid="create-cancel"')}<button type="submit" class="button is-primary" data-testid="create-submit">创建</button></div></form>`,
        );
        $("#new-playlist-name").focus();
    },
    "delete-dialog": () =>
        showDialog(
            `<div class="dialog-head"><h2 id="dialog-title">移除演示项目？</h2>${iconButton("close", "关闭确认对话框", 'data-action="dialog-close"')}</div><p>项目会从当前演示队列移除，可以通过消息里的撤销恢复。</p><div class="dialog-actions">${button("取消", "", 'data-action="dialog-close" data-testid="delete-cancel"')}${button("移除", "danger", 'data-action="confirm-remove" data-testid="delete-confirm"')}</div>`,
        ),
    "confirm-remove": () => {
        $("#demo-dialog").close();
        ACTIONS["undo-demo"]();
    },
    drawer: () => {
        state.drawerReturnFocus = document.activeElement;
        const drawer = $("#demo-drawer");
        drawer.innerHTML = `<div class="dialog-head"><h2 id="drawer-title">快捷设置</h2>${iconButton("close", "关闭快捷设置", 'data-action="drawer-close" data-testid="drawer-close"')}</div><p class="demo-caption">调整播放体验，马上看到变化。</p><section class="drawer-section"><h3>播放</h3>${settingRow("记住播放进度", "下次从离开的位置继续", switchControl("resume", "记住播放进度"))}${preferenceButton("播放速度", "", `${state.speed} 倍`, "speed-menu", "drawer-speed")}${preferenceButton("字幕语言", "", state.values.language ?? "简体中文", "language-menu", "drawer-language")}</section><section class="drawer-section"><h3>字幕样式</h3>${subtitleControls("drawer-subtitle")}</section><div class="dialog-actions">${button("完成", "primary", 'data-action="drawer-close" data-testid="drawer-done"')}</div>`;
        drawer.showModal();
    },
    "drawer-close": () => {
        closeMenu(false);
        $("#demo-drawer").close();
    },
    "rich-tooltip": (element) => {
        openMenu(element, [{ label: "打开播放器预览", run: () => setRoute("scenes", "player") }], "画中画说明");
        const popup = $(".menu-popup");
        popup.classList.add("rich-tip");
        popup.insertAdjacentHTML(
            "afterbegin",
            "<h3>让视频留在手边</h3><p>独立的小窗口可以悬浮在其他内容上方，适合一边观看一边处理其他事情。</p>",
        );
        positionPopup(popup, element, null, false);
    },
    "undo-demo": () => {
        if (!state.queue.length) {
            showToast("演示队列已经为空");
            return;
        }
        const id = state.queue.pop();
        const media = MEDIA.find((item) => item.id === id);
        updateQueue();
        showToast(`已移除“${media.title}”`, "撤销", () => {
            state.queue.push(id);
            updateQueue();
            showToast("已恢复到播放队列");
        });
    },
    "empty-add": (element) => {
        element.closest(".mini-empty").innerHTML =
            `${icon("check")}<strong>已添加旅行记录</strong><span>共找到 6 个演示视频</span>${button("查看媒体库", "primary", 'data-scene="library"')}`;
    },
    scan: () => {
        state.isScanning = true;
        refreshComponent("refresh");
        syncScanControls();
        scanTimer = setTimeout(() => {
            state.isScanning = false;
            refreshComponent("refresh");
            syncScanControls();
            showToast("扫描完成，共 6 个演示视频");
        }, 2200);
    },
    "toggle-button": (element) => {
        const isSelected = element.getAttribute("aria-pressed") !== "true";
        element.setAttribute("aria-pressed", String(isSelected));
        element.classList.toggle("is-selected", isSelected);
    },
    "toggle-loop": () => {
        state.switches.loop = !state.switches.loop;
        onSwitchChanged("loop");
        $$('[data-switch="loop"]').forEach((element) =>
            element.setAttribute("aria-checked", String(state.switches.loop)),
        );
    },
    previous: () => {
        const index = state.queue.indexOf(state.activeMedia);
        if (state.queue.length) playMedia(state.queue[(index - 1 + state.queue.length) % state.queue.length]);
    },
    next: () => {
        const index = state.queue.indexOf(state.activeMedia);
        if (state.queue.length) playMedia(state.queue[(index + 1) % state.queue.length]);
    },
    "queue-play": (element) => playMedia(Number(element.dataset.media)),
    "queue-move": (element) => {
        const id = Number(element.dataset.media);
        moveQueue(id, state.queue.indexOf(id) + Number(element.dataset.delta));
    },
    "subtitle-color": (element) => {
        state.subtitleColor = element.dataset.color;
        syncSubtitles();
    },
});

function handleExtendedInput(element) {
    if (element.id === "demo-search") {
        state.values["demo-search"] = element.value;
        const items = MEDIA.filter((media) => `${media.title} ${media.subtitle}`.includes(element.value));
        $("#demo-search-results").innerHTML = items.length
            ? items
                  .slice(0, 3)
                  .map(
                      (media) =>
                          `<button type="button" data-action="search-result" data-media="${media.id}">${icon("video")}<span>${media.title}</span><small>${media.duration}</small></button>`,
                  )
                  .join("")
            : '<span class="search-no-result">没有找到匹配视频</span>';
    }
    if (element.dataset.rangeEnd) {
        const value = Number(element.value);
        if (element.dataset.rangeEnd === "start") state.rangeStart = Math.min(value, state.rangeEnd);
        else state.rangeEnd = Math.max(value, state.rangeStart);
        syncRange();
    }
    if (element.dataset.channel) {
        state.channels[element.dataset.channel] = Number(element.value);
        syncColors();
    }
    if (element.id === "color-hex") {
        const isValid = /^#[0-9a-fA-F]{6}$/.test(element.value);
        element.setAttribute("aria-invalid", String(!isValid));
        if (isValid) setHexColor(element.value);
    }
    if (element.hasAttribute("data-player-volume")) {
        state.volume = Number(element.value);
        $$("[data-volume-output]").forEach((output) => {
            output.textContent = `${state.volume}%`;
        });
        $$("[data-player-volume]").forEach((input) => {
            input.value = state.volume;
            updateRangeAppearance(input);
        });
    }
    if (element.hasAttribute("data-seek")) {
        state.playbackSeconds = Number(element.value);
        syncPlayback();
    }
    if (element.hasAttribute("data-subtitle-size-input")) {
        state.subtitleSize = Number(element.value);
        syncSubtitles();
    }
    handleSceneInput(element);
}

function handleExtendedChange(element) {
    if (element.dataset.tableAll) {
        const context = element.dataset.tableAll;
        tableData(context).forEach((media) => {
            if (element.checked) state.selection[context].add(media.id);
            else state.selection[context].delete(media.id);
        });
        syncSelection(context);
    }
    if (element.dataset.selectMedia) {
        const id = Number(element.dataset.selectMedia);
        const selection = state.selection[element.dataset.context];
        if (element.checked) selection.add(id);
        else selection.delete(id);
        state.lastSelected[element.dataset.context] = id;
        syncSelection(element.dataset.context);
    }
    if (element.id === "subtitle-offset") {
        state.offset = Math.min(5000, Math.max(-5000, Math.round((Number(element.value) || 0) / 100) * 100));
        element.value = state.offset;
    }
}

function handleExtendedKey(event) {
    if (menuState) {
        const popup = event.target.closest("[data-menu-level]");
        if (event.key === "Escape") {
            event.preventDefault();
            closeMenu();
            return;
        }
        if (event.key === "Tab") {
            closeMenu();
            return;
        }
        if (!popup) return;
        const level = Number(popup.dataset.menuLevel);
        const buttons = $$("button:not(:disabled)", popup);
        const index = buttons.indexOf(event.target);
        if (["ArrowDown", "ArrowUp", "Home", "End"].includes(event.key)) {
            event.preventDefault();
            const next =
                event.key === "Home"
                    ? 0
                    : event.key === "End"
                      ? buttons.length - 1
                      : (index + (event.key === "ArrowDown" ? 1 : -1) + buttons.length) % buttons.length;
            buttons[next].focus();
        }
        if (event.key === "ArrowRight" && event.target.hasAttribute("aria-haspopup")) {
            event.preventDefault();
            event.target.click();
        }
        if (event.key === "ArrowLeft" && level > 0) {
            event.preventDefault();
            const anchor = menuState.levels[level].anchor;
            $$("[data-menu-level]")
                .filter((panel) => Number(panel.dataset.menuLevel) >= level)
                .forEach((panel) => panel.remove());
            menuState.levels = menuState.levels.slice(0, level);
            anchor.setAttribute("aria-expanded", "false");
            anchor.focus();
        }
        return;
    }
    const activeDialog = $("dialog[open]");
    if (event.key === "Tab" && activeDialog) {
        const controls = $$(
            'button:not(:disabled), input:not(:disabled), a[href], select:not(:disabled), textarea:not(:disabled), [tabindex="0"]',
            activeDialog,
        ).filter((element) => element.getClientRects().length > 0 && element.tabIndex >= 0);
        const first = controls[0];
        const last = controls.at(-1);
        if (event.shiftKey && document.activeElement === first) {
            event.preventDefault();
            last.focus();
        }
        if (!event.shiftKey && document.activeElement === last) {
            event.preventDefault();
            first.focus();
        }
    }
    if (event.target.hasAttribute("data-number-wheel") && ["ArrowUp", "ArrowDown", "Home", "End"].includes(event.key)) {
        event.preventDefault();
        updateWheel(event.key === "Home" ? -120 : event.key === "End" ? 120 : event.key === "ArrowUp" ? 5 : -5);
    }
    if (
        event.target.hasAttribute("data-swatch-index") &&
        ["ArrowLeft", "ArrowRight", "ArrowUp", "ArrowDown", "Home", "End"].includes(event.key)
    ) {
        event.preventDefault();
        const index = Number(event.target.dataset.swatchIndex);
        const delta = { ArrowLeft: -1, ArrowRight: 1, ArrowUp: -13, ArrowDown: 13 }[event.key];
        const next = event.key === "Home" ? 0 : event.key === "End" ? 90 : Math.max(0, Math.min(90, index + delta));
        const target = $(`[data-swatch-index="${next}"]`);
        target.click();
        target.focus();
    }
    if (event.target.closest(".file-tree")) {
        const summary = event.target.closest("summary");
        if (summary && ["ArrowLeft", "ArrowRight"].includes(event.key)) {
            event.preventDefault();
            summary.parentElement.open = event.key === "ArrowRight";
        }
    }
    if (
        state.route === "scenes" &&
        state.scene === "player" &&
        !event.target.closest("input, button, a, summary, dialog, textarea, select") &&
        ["ArrowLeft", "ArrowRight"].includes(event.key)
    ) {
        event.preventDefault();
        const media = MEDIA.find((item) => item.id === state.activeMedia);
        state.playbackSeconds = Math.min(
            media.seconds,
            Math.max(0, state.playbackSeconds + (event.key === "ArrowRight" ? 5 : -5)),
        );
        syncPlayback();
    }
}

document.addEventListener("submit", (event) => {
    if (event.target.id !== "playlist-form") return;
    event.preventDefault();
    const input = $("#new-playlist-name");
    const name = input.value.trim();
    if (!name) {
        input.setAttribute("aria-invalid", "true");
        $("#new-playlist-hint").textContent = "请输入名称，不能只有空格";
        input.focus();
        return;
    }
    $("#demo-dialog").close();
    state.values.createdPlaylist = name;
    refreshLibrary();
    showToast(`已创建播放列表“${name}”`);
});
document.addEventListener(
    "pointerdown",
    (event) => {
        if (menuState && !event.target.closest("#menu-root") && !menuState.anchor.contains(event.target))
            closeMenu(false);
    },
    true,
);
document.addEventListener(
    "keydown",
    (event) => {
        if (menuState && event.key === "Escape") event.preventDefault();
    },
    true,
);
document.addEventListener("contextmenu", (event) => {
    const target = event.target.closest("[data-context-target], [data-media-row], [data-table-row], [data-grid-media]");
    if (!target) return;
    event.preventDefault();
    const anchor = target.matches("button") ? target : $("button", target);
    const id = Number(target.dataset.mediaRow ?? target.dataset.tableRow ?? target.dataset.gridMedia);
    if (id) state.activeMedia = id;
    openMenu(anchor, menuItems(), "右键操作菜单", { x: event.clientX, y: event.clientY });
});
document.addEventListener(
    "wheel",
    (event) => {
        if (!event.target.closest("[data-number-wheel]")) return;
        event.preventDefault();
        updateWheel(event.deltaY > 0 ? -5 : 5);
    },
    { passive: false },
);
document.addEventListener("dragstart", (event) => {
    const handle = event.target.closest("[data-drag-media]");
    if (!handle) return;
    draggedMedia = Number(handle.dataset.dragMedia);
    event.dataTransfer.effectAllowed = "move";
    event.dataTransfer.setData("text/plain", String(draggedMedia));
});
document.addEventListener("dragover", (event) => {
    const row = event.target.closest("[data-queue-item]");
    if (draggedMedia === null || !row) return;
    event.preventDefault();
    $$(".is-dragover").forEach((item) => item.classList.remove("is-dragover"));
    row.classList.add("is-dragover");
});
document.addEventListener("drop", (event) => {
    const row = event.target.closest("[data-queue-item]");
    if (draggedMedia === null || !row) return;
    event.preventDefault();
    moveQueue(draggedMedia, state.queue.indexOf(Number(row.dataset.queueItem)));
    draggedMedia = null;
});
document.addEventListener("dragend", () => {
    draggedMedia = null;
    $$(".is-dragover").forEach((item) => item.classList.remove("is-dragover"));
});
window.addEventListener("resize", () => {
    closeMenu(false);
    hideTooltip();
});
$("#content").addEventListener("scroll", (event) => {
    if (!menuState) return;
    const content = event.currentTarget;
    // 打开菜单前产生的滚动事件可能延后送达，只响应位置真正变化的滚动。
    if (content.scrollTop !== menuState.scrollTop || content.scrollLeft !== menuState.scrollLeft) closeMenu(false);
});
$("#demo-drawer").addEventListener("close", () => {
    closeMenu(false);
    if (state.drawerReturnFocus?.isConnected) state.drawerReturnFocus.focus();
});
$("#demo-drawer").addEventListener("click", (event) => {
    if (event.target !== event.currentTarget) return;
    const bounds = event.currentTarget.getBoundingClientRect();
    if (
        event.clientX < bounds.left ||
        event.clientX > bounds.right ||
        event.clientY < bounds.top ||
        event.clientY > bounds.bottom
    )
        event.currentTarget.close();
});
setInterval(() => {
    if (!state.isPlaying || document.hidden) return;
    const media = MEDIA.find((item) => item.id === state.activeMedia);
    state.playbackSeconds = Math.min(media.seconds, state.playbackSeconds + Number(state.speed));
    if (state.playbackSeconds >= media.seconds) {
        if (state.switches.loop) state.playbackSeconds = 0;
        else state.isPlaying = false;
    }
    syncPlayback();
}, 1000);
