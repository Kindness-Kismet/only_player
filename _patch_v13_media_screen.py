from pathlib import Path
import re

p = Path(r"E:/Downloads/only_player_src/feature/player/src/main/java/one/only/player/feature/player/MediaPlayerScreen.kt")
t = p.read_text(encoding="utf-8")

# 1) currentMediaFileName + rotateAndRemember
if "fun currentMediaFileName" not in t:
    m = re.search(r"player \?: return\n", t)
    if not m:
        raise SystemExit("no player return anchor")
    insert = """
    fun currentMediaFileName(): String? {
        val mediaItem = player.currentMediaItem
        val candidates = listOfNotNull(
            mediaItem?.localConfiguration?.uri?.lastPathSegment,
            mediaItem?.localConfiguration?.uri?.path,
            mediaItem?.mediaMetadata?.title?.toString(),
            mediaItem?.mediaId,
            mediaItem?.mediaMetadata?.extras?.getString("media_metadata_remote_file_path"),
        )
        for (candidate in candidates) {
            val name = one.only.player.core.model.PerFilePlaybackPreference.fromPathOrName(candidate)
            if (!name.isNullOrBlank()) return name
        }
        return null
    }

    fun rotateAndRemember() {
        val orient = when (configuration.orientation) {
            Configuration.ORIENTATION_LANDSCAPE -> one.only.player.core.model.LastPlayerScreenOrientation.PORTRAIT
            else -> one.only.player.core.model.LastPlayerScreenOrientation.LANDSCAPE
        }
        rotationState.rotate()
        viewModel.rememberOrientationForFile(currentMediaFileName(), orient)
    }

"""
    t = t[: m.end()] + insert + t[m.end() :]
    print("inserted helpers")
else:
    print("helpers exist")

# If rotateAndRemember missing but currentMediaFileName exists
if "fun rotateAndRemember" not in t and "fun currentMediaFileName" in t:
    t = t.replace(
        "fun currentMediaFileName(): String? {",
        """fun rotateAndRemember() {
        val orient = when (configuration.orientation) {
            Configuration.ORIENTATION_LANDSCAPE -> one.only.player.core.model.LastPlayerScreenOrientation.PORTRAIT
            else -> one.only.player.core.model.LastPlayerScreenOrientation.LANDSCAPE
        }
        rotationState.rotate()
        viewModel.rememberOrientationForFile(currentMediaFileName(), orient)
    }

    fun currentMediaFileName(): String? {""",
        1,
    )
    print("added rotateAndRemember")

# Replace rotationState.rotate() with rotateAndRemember() except definition
t2 = []
for line in t.splitlines(keepends=True):
    if "rotationState.rotate()" in line and "fun rotateAndRemember" not in line:
        line = line.replace("rotationState.rotate()", "rotateAndRemember()")
    t2.append(line)
t = "".join(t2)
print("rotate calls rewritten")

# 2) effectiveDecoderPriority
m = re.search(r"val effectiveDecoderPriority = run \{[\s\S]*?\n    \}\n", t)
if m:
    t = (
        t[: m.start()]
        + """    val effectiveDecoderPriority = run {
        val fileName = currentMediaFileName()
        applicationPreferences.perFilePreferenceForPath(fileName)?.decoderPriority
            ?: fileName?.let { applicationPreferences.decoderPriorityForPath(it) }
            ?: playerPreferences.decoderPriority
    }
"""
        + t[m.end() :]
    )
    print("effectiveDecoderPriority rewritten")
else:
    print("effectiveDecoderPriority missing")

# 3) MenuRoute.Decoder
m = re.search(r"MenuRoute\.Decoder -> \{[\s\S]*?onDismiss = ::dismissOverlay,\s*\}\s*\}", t)
if m:
    new_block = """MenuRoute.Decoder -> {
                            val fileName = currentMediaFileName()
                            var selectedPriority by remember(fileName, applicationPreferences, playerPreferences.decoderPriority) {
                                val mediaExtension = fileName
                                    ?.substringAfterLast('.', missingDelimiterValue = "")
                                    ?.takeIf { it.isNotBlank() && it.length <= 10 && it.all { ch -> ch.isLetterOrDigit() } }
                                    ?.lowercase()
                                val initial =
                                    applicationPreferences.perFilePreferenceForPath(fileName)?.decoderPriority
                                        ?: mediaExtension?.let { ext ->
                                            applicationPreferences.normalizedExtensionDecoderPreferences()
                                                .firstOrNull { it.extension == ext }
                                                ?.decoderPriority
                                        }
                                        ?: playerPreferences.decoderPriority
                                mutableStateOf(initial)
                            }
                            DecoderPrioritySelectorContent(
                                currentDecoderPriority = selectedPriority,
                                onDecoderPriorityClick = { priority ->
                                    selectedPriority = priority
                                    viewModel.updateDecoderPriority(priority)
                                },
                                onRememberForThisFileClick = {
                                    viewModel.rememberDecoderForFile(
                                        fileName = fileName,
                                        decoderPriority = selectedPriority,
                                    )
                                },
                                onDismiss = ::dismissOverlay,
                            )
                        }"""
    t = t[: m.start()] + new_block + t[m.end() :]
    print("MenuRoute.Decoder rewritten")
else:
    print("MenuRoute.Decoder not found")

t = t.replace("viewModel.updateDecoderPriorityForExtension(extension, it)", "viewModel.updateDecoderPriority(it)")
t = re.sub(
    r"viewModel\.updateDecoderPriorityForExtension\(\s*extension\s*=\s*mediaExtension,\s*decoderPriority\s*=\s*it,\s*\)",
    "viewModel.updateDecoderPriority(it)",
    t,
)

# 4) VB indicator
vb_pat = (
    r"val useCenterTextIndicator =[\s\S]*?"
    r"VerticalProgressView\(\s*value = brightnessState\.brightnessPercentage,\s*"
    r"icon = painterResource\(coreUiR\.drawable\.ic_brightness\),\s*\)\s*\}\s*\}"
)
m = re.search(vb_pat, t)
if m:
    new_vb = """val isVbGestureActive = volumeAndBrightnessGestureState.activeGesture != null
                    LaunchedEffect(isVbGestureActive) {
                        if (isVbGestureActive) {
                            controlsVisibilityState.hideControls()
                        }
                    }
                    val useCenterTextIndicator =
                        playerPreferences.volumeBrightnessIndicatorStyle ==
                            one.only.player.core.model.VolumeBrightnessIndicatorStyle.CENTER_TEXT
                    if (useCenterTextIndicator) {
                        if (isVbGestureActive) {
                            val isVolume =
                                volumeAndBrightnessGestureState.activeGesture == VerticalGesture.VOLUME
                            val value = if (isVolume) {
                                volumeState.volumePercentage
                            } else {
                                brightnessState.brightnessPercentage
                            }
                            val iconRes = if (isVolume) {
                                coreUiR.drawable.ic_volume
                            } else {
                                coreUiR.drawable.ic_brightness
                            }
                            Box(
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .fillMaxWidth(),
                                contentAlignment = Alignment.Center,
                            ) {
                                Row(
                                    modifier = Modifier
                                        .background(
                                            color = Color.Black.copy(alpha = 0.45f),
                                            shape = MaterialTheme.shapes.medium,
                                        )
                                        .padding(horizontal = 18.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    androidx.compose.material3.Icon(
                                        painter = painterResource(iconRes),
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(24.dp),
                                    )
                                    androidx.compose.material3.Text(
                                        text = "$value%",
                                        color = Color.White,
                                        style = MaterialTheme.typography.headlineSmall,
                                    )
                                }
                            }
                        }
                    } else {
                        if (volumeAndBrightnessGestureState.activeGesture == VerticalGesture.VOLUME) {
                            Box(modifier = Modifier.align(Alignment.CenterStart)) {
                                VerticalProgressView(
                                    value = volumeState.volumePercentage,
                                    maxValue = volumeState.maxVolumePercentage,
                                    icon = painterResource(coreUiR.drawable.ic_volume),
                                )
                            }
                        }
                        if (volumeAndBrightnessGestureState.activeGesture == VerticalGesture.BRIGHTNESS) {
                            Box(modifier = Modifier.align(Alignment.CenterEnd)) {
                                VerticalProgressView(
                                    value = brightnessState.brightnessPercentage,
                                    icon = painterResource(coreUiR.drawable.ic_brightness),
                                )
                            }
                        }
                    }"""
    t = t[: m.start()] + new_vb + t[m.end() :]
    print("VB indicator rewritten")
else:
    print("VB block not matched")

p.write_text(t, encoding="utf-8")
print("saved", p)
for s in [
    "currentMediaFileName",
    "rotateAndRemember",
    "rememberDecoderForFile",
    "isVbGestureActive",
    "selectedPriority",
    "perFilePreferenceForPath",
]:
    print(s, s in t)
