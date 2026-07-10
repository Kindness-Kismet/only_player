package one.only.player.settings.screens.about

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mikepenz.aboutlibraries.Libs
import com.mikepenz.aboutlibraries.entity.Library
import one.only.player.core.common.Logger
import one.only.player.core.ui.R
import one.only.player.core.ui.components.SettingsContentTopPadding
import one.only.player.core.ui.designsystem.NextIcons
import one.only.player.core.ui.extensions.plus
import one.only.player.core.ui.extensions.withBottomFallback
import one.only.player.feature.settings.R as SettingsR
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon as MiuixIcon
import top.yukonga.miuix.kmp.basic.IconButton as MiuixIconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Text as MiuixText
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun LibrariesScreen(
    onNavigateUp: () -> Unit,
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val libs = remember(context) { loadLibraries(context) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = stringResource(id = R.string.libraries),
                navigationIcon = {
                    MiuixIconButton(
                        onClick = onNavigateUp,
                        modifier = Modifier
                            .padding(start = 12.dp)
                            .testTag("button_libraries_back"),
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
        if (libs == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding.withBottomFallback())
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.Center,
            ) {
                MiuixText(
                    text = stringResource(id = R.string.unknown_error),
                    style = MiuixTheme.textStyles.body1,
                )
            }

            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = innerPadding.withBottomFallback() +
                PaddingValues(top = SettingsContentTopPadding) +
                PaddingValues(horizontal = 16.dp),
        ) {
            item(key = "libraries_card") {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    insideMargin = PaddingValues(0.dp),
                ) {
                    libs.libraries.forEachIndexed { index, library ->
                        LibraryItem(
                            library = library,
                            modifier = Modifier.testTag("item_library_$index"),
                            onClick = {
                                library.website?.takeIf { it.isNotBlank() }?.let {
                                    uriHandler.openUriOrShowToast(uri = it, context = context)
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LibraryItem(
    library: Library,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    BasicComponent(
        modifier = modifier,
        insideMargin = PaddingValues(16.dp),
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            MiuixText(
                text = library.name,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MiuixTheme.textStyles.headline1,
                modifier = Modifier.weight(1f),
            )
            library.artifactVersion?.let {
                MiuixText(
                    text = it,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    style = MiuixTheme.textStyles.body2,
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            MiuixText(
                text = library.developers.takeIf { it.isNotEmpty() }
                    ?.mapNotNull { it.name }
                    ?.joinToString(", ")
                    ?: library.organization?.name ?: "",
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                style = MiuixTheme.textStyles.body2,
            )
            FlowRow(
                verticalArrangement = Arrangement.spacedBy(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                library.licenses.forEach {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = MiuixTheme.colorScheme.secondaryContainer,
                    ) {
                        MiuixText(
                            text = it.name,
                            color = MiuixTheme.colorScheme.onSecondaryContainer,
                            style = MiuixTheme.textStyles.body2,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                }
            }
        }
    }
}

private fun loadLibraries(context: Context): Libs? = try {
    val librariesJson = context.resources.openRawResource(SettingsR.raw.aboutlibraries)
        .bufferedReader()
        .use { it.readText() }

    Libs.Builder()
        .withJson(librariesJson)
        .build()
} catch (throwable: Throwable) {
    Logger.error(TAG, "Failed to load libraries metadata", throwable)
    null
}

private const val TAG = "LibrariesScreen"
