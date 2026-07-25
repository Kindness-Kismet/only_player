from pathlib import Path
import re

p = Path(r"E:/Downloads/only_player_src/feature/player/src/main/java/one/only/player/feature/player/MediaPlayerScreen.kt")
t = p.read_text(encoding="utf-8")

old_row = """    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(40.dp, alignment = Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {"""
new_row = """    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {"""
if old_row in t:
    t = t.replace(old_row, new_row)
    print("center controls spacing")
else:
    print("center row not found")

old_vol = """                    AnimatedVisibility(
                        modifier = Modifier.align(Alignment.CenterStart),
                        visible = volumeAndBrightnessGestureState.activeGesture == VerticalGesture.VOLUME,
                        enter = fadeIn(),
                        exit = fadeOut(),
                    ) {
                        VerticalProgressView(
                            value = volumeState.volumePercentage,
                            maxValue = volumeState.maxVolumePercentage,
                            icon = painterResource(coreUiR.drawable.ic_volume),
                        )
                    }

                    AnimatedVisibility(
                        modifier = Modifier.align(Alignment.CenterEnd),
                        visible = volumeAndBrightnessGestureState.activeGesture == VerticalGesture.BRIGHTNESS,
                        enter = fadeIn(),
                        exit = fadeOut(),
                    ) {
                        VerticalProgressView(
                            value = brightnessState.brightnessPercentage,
                            icon = painterResource(coreUiR.drawable.ic_brightness),
                        )
                    }"""

new_vol = """                    val useCenterTextIndicator =
                        playerPreferences.volumeBrightnessIndicatorStyle ==
                            one.only.player.core.model.VolumeBrightnessIndicatorStyle.CENTER_TEXT
                    if (useCenterTextIndicator) {
                        AnimatedVisibility(
                            modifier = Modifier.align(Alignment.Center),
                            visible = volumeAndBrightnessGestureState.activeGesture != null,
                            enter = fadeIn(),
                            exit = fadeOut(),
                        ) {
                            val isVolume =
                                volumeAndBrightnessGestureState.activeGesture == VerticalGesture.VOLUME
                            val value = if (isVolume) {
                                volumeState.volumePercentage
                            } else {
                                brightnessState.brightnessPercentage
                            }
                            val label = if (isVolume) {
                                stringResource(coreUiR.string.volume) + " $value%"
                            } else {
                                stringResource(coreUiR.string.brightness) + " $value%"
                            }
                            androidx.compose.material3.Text(
                                text = label,
                                color = Color.White,
                                style = MaterialTheme.typography.headlineSmall,
                                modifier = Modifier
                                    .background(
                                        color = Color.Black.copy(alpha = 0.45f),
                                        shape = MaterialTheme.shapes.medium,
                                    )
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                            )
                        }
                    } else {
                        AnimatedVisibility(
                            modifier = Modifier.align(Alignment.CenterStart),
                            visible = volumeAndBrightnessGestureState.activeGesture == VerticalGesture.VOLUME,
                            enter = fadeIn(),
                            exit = fadeOut(),
                        ) {
                            VerticalProgressView(
                                value = volumeState.volumePercentage,
                                maxValue = volumeState.maxVolumePercentage,
                                icon = painterResource(coreUiR.drawable.ic_volume),
                            )
                        }

                        AnimatedVisibility(
                            modifier = Modifier.align(Alignment.CenterEnd),
                            visible = volumeAndBrightnessGestureState.activeGesture == VerticalGesture.BRIGHTNESS,
                            enter = fadeIn(),
                            exit = fadeOut(),
                        ) {
                            VerticalProgressView(
                                value = brightnessState.brightnessPercentage,
                                icon = painterResource(coreUiR.drawable.ic_brightness),
                            )
                        }
                    }"""

if old_vol in t:
    t = t.replace(old_vol, new_vol)
    print("volume indicator styles")
else:
    print("volume block not found")

# customize click patterns
patterns = [
    "onCustomizeControlsClick = { isCustomizingControls = true },",
    "onCustomizeControlsClick = {\n                                            isCustomizingControls = true\n                                        },",
]
repl = """onCustomizeControlsClick = {
                                            if (isCustomizingControls) {
                                                toggleControlVisibility(PlayerControl.CUSTOMIZE)
                                            } else {
                                                isCustomizingControls = true
                                            }
                                        },"""
changed = False
for pat in patterns:
    if pat in t:
        t = t.replace(pat, repl)
        changed = True
if not changed:
    i = t.find("onCustomizeControlsClick")
    print("customize sample", repr(t[i:i+200]) if i >= 0 else "none")
else:
    print("customize toggle wired")

# imports for Color/MaterialTheme if needed
if "import androidx.compose.ui.graphics.Color" not in t and "Color.White" in t:
    t = t.replace(
        "import androidx.compose.ui.Modifier\n",
        "import androidx.compose.ui.Modifier\nimport androidx.compose.ui.graphics.Color\n",
    )
if "import androidx.compose.material3.MaterialTheme" not in t and "MaterialTheme.typography" in t:
    t = t.replace(
        "import androidx.compose.material3.Text\n",
        "import androidx.compose.material3.MaterialTheme\nimport androidx.compose.material3.Text\n",
    )
if "import androidx.compose.foundation.background" not in t and ".background(" in t:
    t = t.replace(
        "import androidx.compose.foundation.layout.Box\n",
        "import androidx.compose.foundation.background\nimport androidx.compose.foundation.layout.Box\n",
    )

p.write_text(t, encoding="utf-8")
print("media screen written")
