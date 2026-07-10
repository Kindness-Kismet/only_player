package one.only.player.core.ui.composables

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import one.only.player.core.ui.R
import one.only.player.core.ui.preview.DayNightPreview
import one.only.player.core.ui.theme.OnlyPlayerTheme
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun PermissionDetailView(
    text: String,
    onOpenSettings: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(id = R.string.permission_not_granted),
            style = MiuixTheme.textStyles.title2,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 5.dp),
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = text,
            style = MiuixTheme.textStyles.body1,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 5.dp),
        )
        Spacer(modifier = Modifier.height(10.dp))
        Button(
            onClick = {
                if (onOpenSettings != null) {
                    onOpenSettings()
                } else {
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.parse("package:" + context.packageName)
                        context.startActivity(this)
                    }
                }
            },
        ) {
            Text(text = stringResource(R.string.open_settings))
        }
    }
}

@DayNightPreview
@Composable
fun PermissionDetailViewPreview() {
    OnlyPlayerTheme {
        Surface {
            PermissionDetailView(
                text = stringResource(
                    id = R.string.permission_settings,
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                ),
            )
        }
    }
}
