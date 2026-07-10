package one.only.player.core.ui.theme

import android.os.Build
import androidx.annotation.ChecksSdkIntAtLeast

// 系统动态取色支持判定，Android 12+ 可用
@ChecksSdkIntAtLeast(api = Build.VERSION_CODES.S)
fun supportsDynamicTheming() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
