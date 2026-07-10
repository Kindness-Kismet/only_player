package one.only.player.settings.screens.privacy

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import one.only.player.core.ui.R
import one.only.player.core.ui.components.PreferenceSwitch
import one.only.player.core.ui.components.SegmentedItemGap
import one.only.player.core.ui.components.SettingsContentTopPadding
import one.only.player.core.ui.designsystem.NextIcons
import one.only.player.core.ui.extensions.withBottomFallback
import one.only.player.core.ui.theme.OnlyPlayerTheme
import top.yukonga.miuix.kmp.basic.Icon as MiuixIcon
import top.yukonga.miuix.kmp.basic.IconButton as MiuixIconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun PrivacyProtectionScreen(
    onNavigateUp: () -> Unit,
    viewModel: PrivacyProtectionViewModel = hiltViewModel(),
) {
    val uiState = viewModel.uiState.collectAsStateWithLifecycle().value

    PrivacyProtectionContent(
        uiState = uiState,
        onNavigateUp = onNavigateUp,
        onEvent = viewModel::onEvent,
    )
}

@Composable
private fun PrivacyProtectionContent(
    uiState: PrivacyProtectionUiState,
    onNavigateUp: () -> Unit,
    onEvent: (PrivacyProtectionUiEvent) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = stringResource(id = R.string.privacy_protection),
                navigationIcon = {
                    MiuixIconButton(
                        onClick = onNavigateUp,
                        modifier = Modifier
                            .padding(start = 12.dp)
                            .testTag("button_privacy_back"),
                    ) {
                        MiuixIcon(
                            imageVector = NextIcons.ArrowBack,
                            contentDescription = stringResource(id = R.string.navigate_up),
                            tint = MiuixTheme.colorScheme.onBackground,
                        )
                    }
                },
            )
        },
        containerColor = MiuixTheme.colorScheme.background,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(state = rememberScrollState())
                .padding(innerPadding.withBottomFallback())
                .padding(top = SettingsContentTopPadding)
                .padding(horizontal = 16.dp),
        ) {
            val isHideInRecentsAvailable = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

            Column(
                verticalArrangement = Arrangement.spacedBy(SegmentedItemGap),
            ) {
                PreferenceSwitch(
                    modifier = Modifier.testTag("switch_settings_privacy_prevent_screenshots"),
                    title = stringResource(id = R.string.prevent_screenshots),
                    description = stringResource(id = R.string.prevent_screenshots_description),
                    icon = NextIcons.HideSource,
                    isChecked = uiState.preferences.shouldPreventScreenshots,
                    onClick = { onEvent(PrivacyProtectionUiEvent.TogglePreventScreenshots) },
                    isFirstItem = true,
                    isLastItem = !isHideInRecentsAvailable,
                )
                if (isHideInRecentsAvailable) {
                    PreferenceSwitch(
                        modifier = Modifier.testTag("switch_settings_privacy_hide_in_recents"),
                        title = stringResource(id = R.string.hide_in_recents),
                        description = stringResource(id = R.string.hide_in_recents_description),
                        icon = NextIcons.Background,
                        isChecked = uiState.preferences.shouldHideInRecents,
                        onClick = { onEvent(PrivacyProtectionUiEvent.ToggleHideInRecents) },
                        isFirstItem = false,
                        isLastItem = true,
                    )
                }
            }
        }
    }
}

@PreviewLightDark
@Composable
private fun PrivacyProtectionScreenPreview() {
    OnlyPlayerTheme {
        PrivacyProtectionContent(
            uiState = PrivacyProtectionUiState(),
            onNavigateUp = {},
            onEvent = {},
        )
    }
}
