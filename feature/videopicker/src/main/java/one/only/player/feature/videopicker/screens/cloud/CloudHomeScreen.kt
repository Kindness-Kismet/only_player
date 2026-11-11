package one.only.player.feature.videopicker.screens.cloud

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import one.only.player.core.common.needsLocalNetworkPermission
import one.only.player.core.model.RemoteServer
import one.only.player.core.model.ServerProtocol
import one.only.player.core.ui.R
import one.only.player.core.ui.components.AppDialog
import one.only.player.core.ui.components.AppDialogHost
import one.only.player.core.ui.components.AppScaffold
import one.only.player.core.ui.components.AppTopAppBar
import one.only.player.core.ui.components.CancelButton
import one.only.player.core.ui.components.PageContentTopPadding
import one.only.player.core.ui.components.PreferenceSwitch
import one.only.player.core.ui.designsystem.AppIcons
import one.only.player.core.ui.extensions.copy
import one.only.player.core.ui.extensions.withBottomFallback
import one.only.player.feature.videopicker.composables.MediaMessageState
import one.only.player.feature.videopicker.composables.RequestLocalNetworkPermissionIfNeeded
import one.only.player.feature.videopicker.composables.rememberLocalNetworkPermissionState
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.DropdownArrowEndAction
import top.yukonga.miuix.kmp.basic.DropdownImpl
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.ListPopupColumn
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.PopupPositionProvider
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.squircle.squircleClip
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowListPopup

@Composable
fun CloudHomeRoute(
    viewModel: CloudHomeViewModel = hiltViewModel(),
    onNavigateUp: () -> Unit,
    onServerClick: (Long) -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val localNetworkPermissionState = rememberLocalNetworkPermissionState()
    var pendingServerId by rememberSaveable { mutableStateOf<Long?>(null) }

    RequestLocalNetworkPermissionIfNeeded(permissionState = localNetworkPermissionState)

    LaunchedEffect(localNetworkPermissionState.isGranted, pendingServerId) {
        val serverId = pendingServerId ?: return@LaunchedEffect
        if (needsLocalNetworkPermission() && !localNetworkPermissionState.isGranted) {
            return@LaunchedEffect
        }
        pendingServerId = null
        onServerClick(serverId)
    }

    CloudHomeScreen(
        uiState = uiState,
        onNavigateUp = onNavigateUp,
        onServerClick = { serverId ->
            if (needsLocalNetworkPermission() && !localNetworkPermissionState.isGranted) {
                pendingServerId = serverId
                localNetworkPermissionState.launchPermissionRequest()
                return@CloudHomeScreen
            }
            onServerClick(serverId)
        },
        onEvent = viewModel::onEvent,
    )
}

@Composable
internal fun CloudHomeScreen(
    uiState: CloudHomeUiState,
    onNavigateUp: () -> Unit = {},
    onServerClick: (Long) -> Unit = {},
    onEvent: (CloudHomeEvent) -> Unit = {},
) {
    var shouldShowAddDialog by rememberSaveable { mutableStateOf(false) }
    var editingServer: RemoteServer? by remember { mutableStateOf(null) }
    var deletingServer: RemoteServer? by remember { mutableStateOf(null) }
    val scrollBehavior = MiuixScrollBehavior()

    AppScaffold(
        topBar = {
            AppTopAppBar(
                title = stringResource(R.string.cloud_servers),
                scrollBehavior = scrollBehavior,
                actions = {
                    IconButton(
                        onClick = { shouldShowAddDialog = true },
                        modifier = Modifier.testTag("btn_cloud_add_server"),
                    ) {
                        Icon(
                            imageVector = AppIcons.Add,
                            contentDescription = stringResource(R.string.add_server),
                            tint = MiuixTheme.colorScheme.onBackground,
                        )
                    }
                },
            )
        },
        contentWindowInsets = WindowInsets.displayCutout,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .padding(top = innerPadding.calculateTopPadding())
                .padding(start = innerPadding.calculateStartPadding(LocalLayoutDirection.current)),
        ) {
            val contentPadding = innerPadding.copy(top = PageContentTopPadding, start = 0.dp).withBottomFallback()
            if (uiState.servers.isEmpty()) {
                EmptyCloudHomeContent(contentPadding = contentPadding)
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize(),
                    contentPadding = contentPadding,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(uiState.servers, key = { it.id }) { server ->
                        ServerListItem(
                            server = server,
                            onClick = { onServerClick(server.id) },
                            onEditClick = { editingServer = server },
                            onDeleteClick = { deletingServer = server },
                        )
                    }
                }
            }
        }
    }

    AppDialogHost(dialog = Unit.takeIf { shouldShowAddDialog }) {
        AddEditServerDialog(
            server = null,
            onDismiss = { shouldShowAddDialog = false },
            onSave = { server ->
                onEvent(CloudHomeEvent.SaveServer(server))
                shouldShowAddDialog = false
            },
        )
    }

    AppDialogHost(dialog = editingServer) { server ->
        AddEditServerDialog(
            server = server,
            onDismiss = { editingServer = null },
            onSave = { updated ->
                onEvent(CloudHomeEvent.SaveServer(updated))
                editingServer = null
            },
        )
    }

    deletingServer?.let { server ->
        AppDialog(
            onDismissRequest = { deletingServer = null },
            title = stringResource(R.string.delete_server),
            content = {
                Text(text = stringResource(R.string.delete_server_confirmation, server.name))
            },
            confirmButton = {
                TextButton(
                    modifier = Modifier.testTag("btn_cloud_delete_server_confirm"),
                    text = stringResource(R.string.delete),
                    onClick = {
                        onEvent(CloudHomeEvent.DeleteServer(server.id))
                        deletingServer = null
                    },
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                )
            },
            dismissButton = { CancelButton(onClick = { deletingServer = null }) },
        )
    }
}

@Composable
private fun EmptyCloudHomeContent(
    contentPadding: PaddingValues,
) {
    MediaMessageState(
        icon = AppIcons.Cloud,
        title = stringResource(R.string.no_servers_configured),
        contentPadding = contentPadding,
    )
}

@Composable
private fun ServerListItem(
    server: RemoteServer,
    onClick: () -> Unit,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .padding(horizontal = 12.dp)
            .testTag("cloud_server_item_${server.id}"),
        onClick = onClick,
        showIndication = true,
        insideMargin = PaddingValues(start = 10.dp, end = 16.dp, top = 10.dp, bottom = 10.dp),
    ) {
        Row(
            modifier = Modifier.heightIn(min = 56.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .padding(end = 10.dp)
                    .size(40.dp),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = AppIcons.Cloud,
                    contentDescription = null,
                    tint = MiuixTheme.colorScheme.onSurface,
                    modifier = Modifier.size(25.dp),
                )
            }
            Column(
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = server.name.ifBlank { server.host },
                    maxLines = 1,
                    style = MiuixTheme.textStyles.headline1,
                    color = MiuixTheme.colorScheme.onSurface,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
                Text(
                    text = "${server.protocol.name} · ${server.host}${server.port?.let { ":$it" } ?: ""}",
                    maxLines = 1,
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
            }
            Row {
                IconButton(onClick = onEditClick) {
                    Icon(
                        imageVector = AppIcons.Edit,
                        contentDescription = stringResource(R.string.edit_server),
                        tint = MiuixTheme.colorScheme.onSurface,
                    )
                }
                IconButton(onClick = onDeleteClick) {
                    Icon(
                        imageVector = AppIcons.Delete,
                        contentDescription = stringResource(R.string.delete_server),
                        tint = MiuixTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}

@Composable
private fun AddEditServerDialog(
    server: RemoteServer?,
    onDismiss: () -> Unit,
    onSave: (RemoteServer) -> Unit,
) {
    val isEditing = server != null
    var name by rememberSaveable { mutableStateOf(server?.name ?: "") }
    var protocol by rememberSaveable { mutableStateOf(server?.protocol ?: ServerProtocol.WEBDAV) }
    var host by rememberSaveable { mutableStateOf(server?.host ?: "") }
    var port by rememberSaveable { mutableStateOf(server?.port?.toString() ?: "") }
    var path by rememberSaveable { mutableStateOf(server?.path ?: "/") }
    var username by rememberSaveable { mutableStateOf(server?.username ?: "") }
    var password by rememberSaveable { mutableStateOf(server?.password ?: "") }
    var isProxyEnabled by rememberSaveable { mutableStateOf(server?.isProxyEnabled ?: false) }
    var proxyHost by rememberSaveable { mutableStateOf(server?.proxyHost ?: "") }
    var proxyPort by rememberSaveable { mutableStateOf(server?.proxyPort?.toString() ?: "") }
    AppDialog(
        modifier = Modifier.testTag("dialog_cloud_server"),
        onDismissRequest = onDismiss,
        title = stringResource(
            if (isEditing) R.string.edit_server else R.string.add_server,
        ),
        content = {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ServerProtocolPreference(protocol = protocol, onSelect = { protocol = it })

                TextField(
                    value = name,
                    onValueChange = { name = it },
                    label = stringResource(R.string.server_name),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("input_cloud_server_name"),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TextField(
                        value = host,
                        onValueChange = { host = it },
                        label = stringResource(R.string.server_host),
                        singleLine = true,
                        modifier = Modifier.weight(1f).testTag("input_cloud_server_host"),
                    )
                    TextField(
                        value = port,
                        onValueChange = { port = it.filter { c -> c.isDigit() } },
                        label = stringResource(R.string.server_port),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.width(140.dp).testTag("input_cloud_server_port"),
                    )
                }
                TextField(
                    value = path,
                    onValueChange = { path = it },
                    label = stringResource(R.string.server_path),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("input_cloud_server_path"),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TextField(
                        value = username,
                        onValueChange = { username = it },
                        label = stringResource(R.string.server_username),
                        singleLine = true,
                        modifier = Modifier.weight(1f).testTag("input_cloud_server_username"),
                    )
                    TextField(
                        value = password,
                        onValueChange = { password = it },
                        label = stringResource(R.string.server_password),
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.weight(1f).testTag("input_cloud_server_password"),
                    )
                }

                PreferenceSwitch(
                    modifier = Modifier.squircleClip(16.dp).testTag("switch_cloud_server_proxy"),
                    title = stringResource(R.string.proxy_settings),
                    isChecked = isProxyEnabled,
                    onClick = { isProxyEnabled = !isProxyEnabled },
                )
                if (isProxyEnabled) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        TextField(
                            value = proxyHost,
                            onValueChange = { proxyHost = it },
                            label = stringResource(R.string.proxy_host),
                            singleLine = true,
                            modifier = Modifier.weight(1f).testTag("input_cloud_server_proxy_host"),
                        )
                        TextField(
                            value = proxyPort,
                            onValueChange = { proxyPort = it.filter { c -> c.isDigit() } },
                            label = stringResource(R.string.proxy_port),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.width(140.dp).testTag("input_cloud_server_proxy_port"),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                modifier = Modifier.testTag("btn_cloud_server_save"),
                text = stringResource(R.string.save),
                enabled = host.isNotBlank(),
                colors = ButtonDefaults.textButtonColorsPrimary(),
                onClick = {
                    val result = RemoteServer(
                        id = server?.id ?: 0,
                        name = name.trim(),
                        protocol = protocol,
                        host = host.trim(),
                        port = port.toIntOrNull(),
                        path = path.ifBlank { "/" },
                        username = username,
                        password = password,
                        isProxyEnabled = isProxyEnabled,
                        proxyHost = proxyHost.trim(),
                        proxyPort = proxyPort.toIntOrNull(),
                    )
                    onSave(result)
                },
            )
        },
        dismissButton = { CancelButton(onClick = onDismiss) },
    )
}

@Composable
private fun ServerProtocolPreference(
    protocol: ServerProtocol,
    onSelect: (ServerProtocol) -> Unit,
) {
    var isExpanded by remember { mutableStateOf(false) }
    var isHeld by remember { mutableStateOf(false) }
    BasicComponent(
        title = stringResource(R.string.server_protocol),
        summary = protocol.name,
        modifier = Modifier.squircleClip(16.dp).testTag("item_cloud_server_protocol"),
        role = Role.DropdownList,
        holdDownState = isHeld,
        onClick = {
            isExpanded = !isExpanded
            if (isExpanded) isHeld = true
        },
        endActions = {
            DropdownArrowEndAction(MiuixTheme.colorScheme.onSurfaceVariantActions)
            WindowListPopup(
                show = isExpanded,
                alignment = PopupPositionProvider.Align.End,
                popupModifier = Modifier.semantics { testTagsAsResourceId = true },
                onDismissRequest = { isExpanded = false },
                onDismissFinished = { isHeld = false },
            ) {
                ListPopupColumn {
                    ServerProtocol.entries.forEachIndexed { index, item ->
                        Box(
                            modifier = Modifier.fillMaxWidth().squircleClip(16.dp).testTag("option_cloud_server_protocol_${item.name.lowercase()}"),
                            propagateMinConstraints = true,
                        ) {
                            DropdownImpl(
                                item = DropdownItem(text = item.name),
                                optionSize = ServerProtocol.entries.size,
                                index = index,
                                isSelected = protocol == item,
                                onSelectedIndexChange = {
                                    onSelect(item)
                                    isExpanded = false
                                },
                            )
                        }
                    }
                }
            }
        },
    )
}
