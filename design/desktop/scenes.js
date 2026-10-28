// 页面场景使用与控件图鉴一致的状态和基础控件。
function sceneCaption() {
    return `<div class="caption-bar scene-caption"><span class="caption-brand">${icon("play")}<span>Only Player</span><span class="scene-demo-label">交互原型</span></span><div class="caption-controls"><button type="button" aria-label="模拟最小化应用" data-action="scene-minimize" data-testid="scene-minimize">−</button><button type="button" aria-label="展开或还原页面预览" data-action="scene-maximize" data-testid="scene-maximize">□</button><button type="button" aria-label="返回控件图鉴" data-route="gallery" data-testid="scene-close">×</button></div></div>`;
}

function sceneSidebar(section = "library") {
    const items =
        section === "settings"
            ? [
                  ["player", "播放"],
                  ["caption", "字幕"],
                  ["appearance", "外观"],
                  ["folder", "存储"],
              ]
            : [
                  ["folder", "全部视频"],
                  ["history", "最近播放"],
                  ["starLine", "收藏"],
                  ["playlistPlay", "播放列表"],
              ];
    const current = section === "settings" ? state.settingsSection : state.librarySection;
    return `<aside class="scene-sidebar"><div class="scene-app-name">${icon(section === "settings" ? "settings" : "folder")}<strong>${section === "settings" ? "设置" : "媒体库"}</strong></div><nav aria-label="${section === "settings" ? "设置分类" : "媒体库分类"}">${items.map(([name, title], index) => `<button type="button" class="${current === title ? "is-current" : ""}" data-action="${section === "settings" ? "settings-section" : "library-section"}" data-section="${title}" ${current === title ? 'aria-current="page"' : ""} data-tooltip="${title}" data-testid="${section}-section-${index}">${icon(name)}<span>${title}</span>${section !== "settings" && index === 0 ? "<small>6</small>" : ""}</button>`).join("")}</nav><div class="scene-sidebar-bottom">${section === "settings" ? `<button type="button" data-scene="library" data-testid="settings-back-library">${icon("arrowLeftCircle")}<span>返回媒体库</span></button>` : `<div class="storage-meter"><span>本地媒体</span><strong>7.1 GB</strong><div class="linear-progress"><span style="--progress:36%"></span></div><small>存放在这台电脑上</small></div><button type="button" data-scene="settings" data-testid="library-open-settings">${icon("settings")}<span>设置</span></button>`}</div></aside>`;
}

function gridMedia(media) {
    const isSelected = state.selection.library.has(media.id);
    return `<article class="video-card ${isSelected ? "is-selected" : ""}" data-grid-media="${media.id}" data-context="library"><div class="video-cover"><button type="button" class="cover-play" aria-label="播放${media.title}" data-action="open-player" data-media="${media.id}" data-testid="video-play-${media.id}">${landscape(media.art)}<span class="cover-play-icon">${icon("play")}</span><span class="duration-pill">${media.duration}</span></button><label class="cover-select"><input class="check" type="checkbox" aria-label="选择${media.title}" data-select-media="${media.id}" data-context="library" ${isSelected ? "checked" : ""} data-testid="library-grid-select-${media.id}"/></label></div><div class="video-card-info"><button type="button" class="video-title" data-action="select-media" data-media="${media.id}" data-context="library" aria-pressed="${isSelected}" data-testid="library-grid-title-${media.id}"><strong>${media.title}</strong><small>${media.resolution} · ${media.size}</small></button>${iconButton("moreVert", `${media.title}的更多操作`, `data-action="media-menu" data-media="${media.id}" data-testid="video-more-${media.id}"`)}</div></article>`;
}

function libraryContent() {
    const items = tableData("library");
    const isSearch = Boolean(state.libraryQuery.trim());
    const heading = isSearch ? `搜索结果 · ${items.length} 项` : state.librarySection;
    return `<div class="library-heading"><div><span class="library-overline">我的片库</span><h2>${heading}</h2><p>${isSearch ? `与“${escapeHtml(state.libraryQuery)}”相关的视频` : "喜欢的影像，随时接着看。"}</p></div>${button(`${icon("add")}添加文件夹`, "primary", 'data-action="add-library-folder" data-testid="library-add-folder"')}</div>${!isSearch && state.librarySection === "全部视频" ? `<section class="continue-card"><div class="continue-art">${landscape("mountain")}</div><div class="continue-copy"><span>继续观看</span><h3>向山而行</h3><p>川西旅行记录 · 还剩 17 分钟</p>${button(`${icon("play")}继续播放`, "primary", 'data-action="open-player" data-media="1" data-resume="true" data-testid="continue-play"')}</div><div class="continue-progress"><span></span></div></section>` : ""}<div class="library-toolbar">${breadcrumb("library-path")}<div class="library-view-controls">${iconButton("dashBoard", "以网格显示", `data-action="library-layout" data-layout="grid" aria-pressed="${state.libraryLayout === "grid"}" data-testid="view-grid"`, state.libraryLayout === "grid" ? "selected" : "")}${iconButton("menu", "以表格显示", `data-action="library-layout" data-layout="table" aria-pressed="${state.libraryLayout === "table"}" data-testid="view-table"`, state.libraryLayout === "table" ? "selected" : "")}${iconButton("extraSettings", "排序方式", 'data-action="library-sort-menu" data-testid="library-sort"')}</div></div>${state.librarySection === "播放列表" ? `<div class="playlist-intro">${icon("playlistPlay")}<span><strong>${escapeHtml(state.values.createdPlaylist ?? "旅行记录")}</strong><small>${state.queue.length} 个视频</small></span>${button("新建片单", "soft", 'data-action="create-playlist" data-testid="library-new-playlist"')}</div>${queueControls()}` : items.length ? (state.libraryLayout === "table" ? `<div data-table-host="library">${mediaTable("library")}</div>` : `<div class="library-selection"><span data-selected-count="library">已选择 ${items.filter((media) => state.selection.library.has(media.id)).length} 项</span><span>${items.length} 个视频</span></div><div class="video-grid">${items.map(gridMedia).join("")}</div>`) : `<div class="empty-result">${icon("search")}<h2>${isSearch ? "没有找到相关视频" : "这里还没有内容"}</h2><p>${isSearch ? "换一个关键词，或者清除当前搜索。" : "从视频的更多菜单里添加收藏。"}</p>${button(isSearch ? "清除搜索" : "查看全部视频", "soft", `data-action="${isSearch ? "clear-library-search" : "library-section"}" data-section="全部视频" data-testid="library-empty-action"`)}</div>`}`;
}

function miniPlayer() {
    const media = MEDIA.find((item) => item.id === state.activeMedia);
    return `<footer class="mini-player"><span class="mini-player-art">${landscape(media.art)}</span><button type="button" class="mini-player-title" data-scene="player" aria-label="打开播放器" data-testid="mini-player-open"><strong data-playing-title>${media.title}</strong><small data-play-state>${state.isPlaying ? "正在播放" : "已暂停"}</small></button><span class="mini-player-time" data-current-time>${formatTime(state.playbackSeconds)}</span>${iconButton("skipPrevious", "上一个视频", 'data-action="previous" data-testid="mini-previous"')}${iconButton(state.isPlaying ? "pause" : "play", state.isPlaying ? "暂停" : "播放", 'data-action="play" data-play-toggle data-testid="mini-play"', "primary")}${iconButton("skipNext", "下一个视频", 'data-action="next" data-testid="mini-next"')}</footer>`;
}

function libraryScene() {
    return `<div class="scene-window library-window">${sceneCaption()}<div class="scene-body">${sceneSidebar()}<div class="library-main"><div class="library-top-tools">${searchBox("library-search", "搜索名称、画质或描述", state.libraryQuery)}${iconButton("update", "刷新媒体库", `data-action="scan" data-scan-button ${state.isScanning ? 'disabled aria-busy="true"' : ""} data-testid="library-refresh"`)}${iconButton("extraSettings", "快捷设置", 'data-action="drawer" data-testid="library-quick-settings"')}</div><div class="library-content" id="library-content">${libraryContent()}</div></div></div>${miniPlayer()}</div>`;
}

function playerScene() {
    const media = MEDIA.find((item) => item.id === state.activeMedia);
    const hasQueue = state.values.isQueueVisible !== false;
    return `<div class="scene-window player-window">${sceneCaption()}<div class="player-titlebar">${iconButton("arrowLeftCircle", "返回媒体库", 'data-scene="library" data-testid="player-back"')}<strong data-playing-title>${media.title}</strong><span class="player-title-meta">${media.resolution} · ${media.duration}</span><span class="player-title-spacer"></span>${iconButton("caption", "字幕样式", 'data-action="drawer" data-testid="player-subtitle"')}${iconButton("playlistPlay", "显示或隐藏播放队列", `data-action="toggle-player-queue" aria-pressed="${hasQueue}" data-testid="player-queue-toggle"`, hasQueue ? "selected" : "")}</div><div class="player-layout ${hasQueue ? "has-queue" : ""}"><div class="video-and-controls"><div class="scene-video" tabindex="0" aria-label="演示播放画面，空格切换播放，左右方向键调整进度" data-testid="player-surface"><div class="scene-video-art" data-player-art>${landscape(media.art)}</div><span class="video-demo-badge">演示画面</span><div class="scene-video-caption"><span data-subtitle-text style="font-size:${state.subtitleSize}px;color:${state.subtitleColor};background:${state.switches.subtitleBackground ? "#00000077" : "transparent"}">山的那边，还是山。</span></div><button type="button" class="video-center-play" data-action="play" data-play-toggle aria-label="${state.isPlaying ? "暂停" : "播放"}" data-testid="video-center-play">${icon(state.isPlaying ? "pause" : "play")}</button></div><div class="scene-playback">${seekControl("player-seek")}<div class="scene-playback-bottom"><div class="demo-row">${iconButton("skipPrevious", "上一个视频", 'data-action="previous" data-testid="player-previous"')}${iconButton(state.isPlaying ? "pause" : "play", state.isPlaying ? "暂停" : "播放", 'data-action="play" data-play-toggle data-testid="player-play"', "primary")}${iconButton("skipNext", "下一个视频", 'data-action="next" data-testid="player-next"')}</div><div class="scene-volume">${icon("volumeUp")}<input type="range" class="range seek-range" min="0" max="100" value="${state.volume}" data-player-volume aria-label="播放器音量" style="--value:${state.volume}%" data-testid="player-volume"/></div><button type="button" class="button is-ghost player-speed" data-action="speed-menu" data-testid="player-speed"><span data-speed-text>${state.speed} 倍</span></button>${iconButton("loop", "循环播放", `data-action="toggle-loop" aria-pressed="${state.switches.loop}" data-loop-toggle data-testid="player-loop"`)}${iconButton("frame", "展开或还原画面", 'data-action="player-theater" aria-pressed="false" data-testid="player-theater"')}</div></div></div>${hasQueue ? `<aside class="player-queue"><div class="player-queue-heading"><h3>接下来播放</h3><span>${state.queue.length} 项</span></div>${queueControls()}<p>拖动左侧手柄调整顺序，也可用上下按钮移动。</p></aside>` : ""}</div><footer class="player-hints"><span><kbd>空格</kbd> 播放 / 暂停</span><span><kbd>←</kbd><kbd>→</kbd> 后退 / 前进</span><span data-play-state>${state.isPlaying ? "正在播放" : "已暂停"}</span></footer></div>`;
}

function settingsContent() {
    if (state.settingsSection === "字幕")
        return `<h2>字幕</h2><p class="settings-lead">让每一句都清楚、合适。</p><section class="settings-card">${subtitleControls("settings-subtitle")}${settingRow("加粗文字", "提高复杂画面上的可读性", switchControl("isBold", "加粗字幕文字"))}</section><h3 class="settings-group-title">语言与同步</h3><section class="settings-card">${preferenceButton("首选语言", "优先加载这一语言的字幕", state.values.language ?? "简体中文", "language-menu", "settings-language")}${settingRow("字幕偏移", "提前或延后显示，单位为毫秒", numberStepper())}</section>`;
    if (state.settingsSection === "外观")
        return `<h2>外观</h2><p class="settings-lead">熟悉的风格，适合你的桌面。</p><section class="settings-card">${preferenceButton("界面主题", "切换整个预览的明暗效果", state.prefs.theme === "light" ? "浅色" : "深色", "theme-menu", "settings-theme")}${preferenceButton("控件密度", "在舒展和紧凑布局间切换", state.prefs.density === "comfortable" ? "舒展" : "紧凑", "density-menu", "settings-density")}</section><h3 class="settings-group-title">主题颜色</h3><section class="settings-card">${accentControls()}<p class="demo-caption">蓝色沿用原版，其余是桌面配色探索。</p></section><h3 class="settings-group-title">动态效果</h3><section class="settings-card">${settingRow("界面动画", "系统要求减少动态效果时优先跟随系统", `<button type="button" class="switch" role="switch" aria-label="界面动画" aria-checked="${state.prefs.shouldAnimate}" data-action="motion" data-testid="settings-motion"></button>`)}</section>`;
    if (state.settingsSection === "存储")
        return `<h2>存储</h2><p class="settings-lead">管理视频来源和缩略图缓存。</p><section class="settings-card"><div class="storage-summary">${icon("folder")}<span><strong>旅行记录</strong><small>视频 / 旅行记录 · 6 个视频</small></span>${button("重新扫描", "soft", 'data-action="scan" data-scan-button data-testid="settings-scan"')}</div></section><h3 class="settings-group-title">缓存</h3><section class="settings-card">${settingRow("缩略图缓存", "用于加快媒体库浏览", `<strong class="cache-size" data-cache-size>${state.values.isCacheCleared ? "0 MB" : "128 MB"}</strong>`)}<div class="linear-progress"><span style="--progress:${state.values.isCacheCleared ? 0 : 32}%" data-cache-progress></span><div class="settings-cache-action">${button("清理缓存", "", 'data-action="clear-cache" data-testid="settings-clear-cache"')}</div></section><h3 class="settings-group-title">导出设置</h3><section class="settings-card"><p class="demo-caption">将当前预览的主题、密度和播放选项保存为配置文件。</p>${button(`${icon("save")}保存预览设置`, "primary", 'data-action="export-preferences" data-testid="export-preferences"')}</section>`;
    return `<h2>播放</h2><p class="settings-lead">每次打开，都从舒服的状态开始。</p><h3 class="settings-group-title">播放行为</h3><section class="settings-card">${settingRow("记住播放进度", "重新打开视频时继续上次播放", switchControl("resume", "记住播放进度"), "history")}${settingRow("后台播放", "窗口最小化后继续播放声音", switchControl("background", "后台播放"), "audio")}${settingRow("循环播放", "当前视频结束后重新开始", switchControl("loop", "循环播放"), "loop")}</section><h3 class="settings-group-title">画面与声音</h3><section class="settings-card">${settingRow("硬件解码", "使用设备能力降低播放负载", switchControl("hardware", "硬件解码"), "player")}${preferenceButton("默认播放速度", "打开视频时采用的速度", `${state.speed} 倍`, "speed-menu", "settings-speed", "speed")}${preferenceButton("画面比例", "选择视频在窗口中的显示方式", state.ratio, "ratio-menu", "settings-ratio", "frame")}</section><h3 class="settings-group-title">音量</h3><section class="settings-card"><div class="range-head"><span>播放音量</span><output data-volume-output>${state.volume}%</output></div><input type="range" class="range" min="0" max="100" value="${state.volume}" data-player-volume aria-label="播放音量" style="--value:${state.volume}%" data-testid="settings-volume"/></section>`;
}

function settingsScene() {
    return `<div class="scene-window settings-window">${sceneCaption()}<div class="scene-body">${sceneSidebar("settings")}<div class="settings-main" id="settings-content">${settingsContent()}</div></div></div>`;
}

function renderScenePage() {
    const labels = { library: "媒体库", player: "播放器", settings: "设置页面" };
    return (
        pageHeading(
            "放进真实的使用场景",
            "在媒体库、播放器和设置页面里，体验控件组合后的密度、层级和操作。",
            "页面预览 · 演示数据",
        ) +
        `<div class="scene-selector" role="tablist" aria-label="页面场景">${Object.entries(labels)
            .map(
                ([id, title]) =>
                    `<button type="button" role="tab" id="scene-tab-${id}" aria-selected="${id === state.scene}" tabindex="${id === state.scene ? 0 : -1}" aria-controls="scene-panel" data-scene="${id}" data-testid="scene-tab-${id}">${title}</button>`,
            )
            .join(
                "",
            )}<span>网页原型 · 可直接操作</span></div><section id="scene-panel" role="tabpanel" aria-labelledby="scene-tab-${state.scene}">${{ library: libraryScene, player: playerScene, settings: settingsScene }[state.scene]()}</section>`
    );
}

function refreshLibrary() {
    if (!$("#library-content")) return;
    $("#library-content").innerHTML = libraryContent();
    syncSelection("library");
}

function refreshPlayerArt() {
    const media = MEDIA.find((item) => item.id === state.activeMedia);
    $$("[data-player-art], .mini-player-art").forEach((element) => {
        element.innerHTML = landscape(media.art);
    });
    const meta = $(".player-title-meta");
    if (meta) meta.textContent = `${media.resolution} · ${media.duration}`;
}

function syncScanControls() {
    $$("[data-scan-button]").forEach((button) => {
        button.disabled = state.isScanning;
        button.setAttribute("aria-busy", String(state.isScanning));
    });
}

function handleSceneInput(element) {
    if (element.id === "library-search") {
        state.libraryQuery = element.value;
        refreshLibrary();
    }
    if (element.id === "spec-radius") {
        state.prefs.radius = Number(element.value);
        applyPreferences();
        $("#spec-radius-output").textContent = `${element.value} 像素`;
    }
}

Object.assign(ACTIONS, {
    "scene-minimize": () => {
        $(".scene-window").classList.toggle("is-collapsed");
    },
    "scene-maximize": () => {
        document.body.classList.toggle("is-scene-expanded");
    },
    "library-section": (element) => {
        state.librarySection = element.dataset.section;
        render();
    },
    "library-layout": (element) => {
        state.libraryLayout = element.dataset.layout;
        refreshLibrary();
        $(`[data-testid="${element.dataset.testid}"]`)?.focus();
    },
    "library-sort-menu": (element) =>
        optionMenu(
            element,
            ["名称", "时长", "大小", "添加时间"],
            { title: "名称", seconds: "时长", sizeValue: "大小", date: "添加时间" }[state.sort.library.key],
            (label) => {
                state.sort.library = {
                    key: { 名称: "title", 时长: "seconds", 大小: "sizeValue", 添加时间: "date" }[label],
                    direction: 1,
                };
                refreshLibrary();
            },
            "媒体排序方式",
        ),
    "clear-library-search": () => {
        state.libraryQuery = "";
        $("#library-search").value = "";
        refreshLibrary();
        $("#library-search").focus();
    },
    "add-library-folder": () => {
        showDialog(
            `<div class="dialog-head"><h2 id="dialog-title">添加文件夹</h2>${iconButton("close", "关闭添加文件夹", 'data-action="dialog-close"')}</div><p>选择一个示例文件夹，体验媒体库的添加流程。</p><div class="directory-hint">${icon("folder")}<span>视频 / 旅行记录 · 6 个视频</span></div><div class="dialog-actions">${button("取消", "", 'data-action="dialog-close"')}${button("添加到媒体库", "primary", 'data-action="confirm-library-folder" data-testid="confirm-library-folder"')}</div>`,
        );
    },
    "confirm-library-folder": () => {
        $("#demo-dialog").close();
        state.librarySection = "全部视频";
        state.libraryQuery = "";
        render();
        showToast("旅行记录已在媒体库中，共 6 个演示视频");
    },
    "open-player": (element) => {
        playMedia(Number(element.dataset.media));
        if (element.dataset.resume) state.playbackSeconds = 432;
        setRoute("scenes", "player");
    },
    "toggle-player-queue": () => {
        state.values.isQueueVisible = state.values.isQueueVisible === false;
        render();
        $('[data-testid="player-queue-toggle"]').focus();
    },
    "player-theater": (element) => {
        const isExpanded = $(".player-window").classList.toggle("is-theater");
        element.setAttribute("aria-pressed", String(isExpanded));
    },
    "settings-section": (element) => {
        state.settingsSection = element.dataset.section;
        render();
    },
    "theme-menu": (element) =>
        optionMenu(
            element,
            ["浅色", "深色"],
            state.prefs.theme === "light" ? "浅色" : "深色",
            (value) => {
                state.prefs.theme = value === "浅色" ? "light" : "dark";
                applyPreferences();
                updatePreferenceValue("theme-menu", value);
            },
            "界面主题",
        ),
    "density-menu": (element) =>
        optionMenu(
            element,
            ["舒展", "紧凑"],
            state.prefs.density === "comfortable" ? "舒展" : "紧凑",
            (value) => {
                state.prefs.density = value === "舒展" ? "comfortable" : "compact";
                applyPreferences();
                updatePreferenceValue("density-menu", value);
            },
            "控件密度",
        ),
    accent: (element) => {
        state.prefs.accent = element.dataset.accent;
        applyPreferences();
        $$('[data-action="accent"]').forEach((button) =>
            button.setAttribute("aria-pressed", String(button.dataset.accent === state.prefs.accent)),
        );
    },
    motion: (element) => {
        state.prefs.shouldAnimate = !state.prefs.shouldAnimate;
        applyPreferences();
        element.setAttribute("aria-checked", String(state.prefs.shouldAnimate));
    },
    "clear-cache": () => {
        state.values.isCacheCleared = true;
        $("[data-cache-size]").textContent = "0 MB";
        $("[data-cache-progress]").style.setProperty("--progress", "0%");
        showToast("演示缓存已清理");
    },
    "export-preferences": () => {
        const blob = new Blob(
            [
                JSON.stringify(
                    {
                        theme: state.prefs,
                        playback: { speed: state.speed, ratio: state.ratio, volume: state.volume },
                        switches: state.switches,
                    },
                    null,
                    4,
                ),
            ],
            { type: "application/json" },
        );
        const url = URL.createObjectURL(blob);
        const anchor = document.createElement("a");
        anchor.href = url;
        anchor.download = "only-player-desktop-preview.json";
        anchor.click();
        setTimeout(() => URL.revokeObjectURL(url), 1000);
        showToast("预览设置已保存为文件");
    },
});

function accentControls() {
    return `<div class="accent-options" aria-label="主题颜色">${[
        ["blue", "原版蓝", "#3482ff"],
        ["jade", "青绿", "#248e7a"],
        ["amber", "暖橙", "#d87924"],
        ["rose", "玫红", "#d75775"],
    ]
        .map(
            ([id, label, color]) =>
                `<button type="button" data-action="accent" data-accent="${id}" aria-pressed="${state.prefs.accent === id}" data-testid="accent-${id}"><span style="background:${color}"></span>${label}</button>`,
        )
        .join("")}</div>`;
}

function renderSpec() {
    const colors = [
        ["操作主色", "--primary", "#3482FF", "#277AF7"],
        ["页面表面", "--page", "#F7F7F7", "#000000"],
        ["内容卡片", "--card", "#FFFFFF", "#242424"],
        ["次要按钮", "--control", "#F0F0F0", "#434343"],
        ["轻选中背景", "--primary-soft", "#EAF2FF", "#2B3B54"],
        ["分隔线", "--line", "#E0E0E0", "#393939"],
    ];
    return (
        renderSpecOverview() +
        '<section class="spec-card"><h2>初稿复核结果</h2><p>已逐组对照原版，修正滑块端点、选择标记、禁用配色、色板、对话框尺寸和进度样式。六种窗口宽度下的 120 个页面组合检查未发现横向溢出。</p><p>保留的桌面调整和网页近似效果，也逐项记录在复核说明里。</p><a href="AUDIT.md" target="_blank">查看逐组差异与修正记录 ↗</a></section>' +
        `<section class="spec-card"><h2>颜色与主题</h2><p>下面的蓝色色板沿用原版非动态取色模式，其他主色用于比较桌面效果。</p>${accentControls()}<div class="token-colors">${colors.map(([label, token, light, dark]) => `<div><span style="background:var(${token})"></span><strong>${label}</strong><small>${light} / ${dark}</small></div>`).join("")}</div></section><section class="spec-card"><h2>字体层级</h2><div class="type-spec"><span style="font-size:32px">让影像回到眼前</span><small>页面大标题 · 32</small><span style="font-size:24px">喜欢的故事，随时接着看</span><small>区域标题 · 24</small><span style="font-size:17px">主文保留原版的舒展感</span><small>原版主文 · 17</small><span style="font-size:14px">桌面常规控件适度收紧字号</span><small>桌面正文 · 14</small><span style="font-size:12px">辅助信息保持清楚，避免抢占视觉注意力</span><small>说明文字 · 12</small></div></section><section class="spec-card"><h2>圆角与密度</h2><p>当前卡片使用 16 像素圆角；下面可以调整卡片圆角，对比桌面密度。开关和滑块保留各自的比例。</p>${rangeControl("spec-radius", "卡片圆角", state.prefs.radius, " 像素", 8, 28)}<div class="table-wrap"><table class="spec-table"><thead><tr><th>控件</th><th>原版依据</th><th>桌面适配</th></tr></thead><tbody><tr><td>按钮</td><td>最小高 40，圆角 16</td><td>高 40 / 32</td></tr><tr><td>开关</td><td>轨道 49 × 28，圆钮 20</td><td>保留原版比例</td></tr><tr><td>分段控件</td><td>高 45，外围间距 5</td><td>高 44 / 36</td></tr><tr><td>菜单</td><td>行高 56，圆角 16</td><td>行高 38，保留圆角</td></tr><tr><td>侧边导航</td><td>展开 240，收起 80</td><td>按窗口宽度切换</td></tr><tr><td>时间轴</td><td>基础滑块高 28</td><td>增加高 6 的播放变体</td></tr></tbody></table></div></section><section class="spec-card"><h2>交互检查</h2><ul><li>按钮、选择、滑块、页签和菜单支持键盘。</li><li>菜单避让窗口边缘，子菜单按左右方向键进入和返回。</li><li>对话框、抽屉约束焦点，关闭后返回触发位置。</li><li>表格支持全选、半选、排序与组合键多选。</li><li>播放队列可以拖动，也提供上下移动按钮。</li><li>主题和密度可以保存，减少动态效果时跟随系统。</li></ul><a href="PLAN.md" target="_blank">查看完整控件映射和后续实施顺序 ↗</a></section>`
    );
}
