package one.only.player.feature.videopicker.composables

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import one.only.player.core.common.LOCAL_NETWORK_PERMISSION
import one.only.player.core.common.needsLocalNetworkPermission
import one.only.player.core.ui.R
import one.only.player.core.ui.components.AppIconButton as IconButton
import one.only.player.core.ui.components.AppScaffold
import one.only.player.core.ui.components.AppTopAppBar
import one.only.player.core.ui.composables.PermissionDetailView
import one.only.player.core.ui.composables.PermissionRationaleDialog
import one.only.player.core.ui.composables.RuntimePermissionState
import one.only.player.core.ui.composables.rememberRuntimePermissionState
import one.only.player.core.ui.designsystem.AppIcons
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun rememberLocalNetworkPermissionState(): RuntimePermissionState = rememberRuntimePermissionState(permission = LOCAL_NETWORK_PERMISSION)

@Composable
fun RequestLocalNetworkPermissionIfNeeded(
    permissionState: RuntimePermissionState = rememberLocalNetworkPermissionState(),
) {
    if (!needsLocalNetworkPermission()) return
    LifecycleEventEffect(event = Lifecycle.Event.ON_START) {
        if (permissionState.isGranted) return@LifecycleEventEffect
        permissionState.launchPermissionRequest()
    }
}

@Composable
fun LocalNetworkPermissionMissingScreen(
    shouldShowRationale: Boolean,
    onGrantClick: () -> Unit,
    onNavigateUp: () -> Unit,
) {
    AppScaffold(
        topBar = {
            AppTopAppBar(
                title = stringResource(R.string.cloud_servers),
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateUp,
                        modifier = Modifier
                            .padding(start = 12.dp)
                            .testTag("button_local_network_permission_back"),
                    ) {
                        Icon(
                            imageVector = AppIcons.ArrowBack,
                            contentDescription = stringResource(id = R.string.navigate_up),
                            tint = MiuixTheme.colorScheme.onBackground,
                        )
                    }
                },
            )
        },
    ) { _ ->
        if (shouldShowRationale) {
            PermissionRationaleDialog(
                text = stringResource(id = R.string.local_network_permission_info),
                onConfirmButtonClick = onGrantClick,
            )
        } else {
            PermissionDetailView(
                text = stringResource(id = R.string.local_network_permission_settings),
            )
        }
    }
}
