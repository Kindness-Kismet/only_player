package one.only.player.core.model

import kotlin.math.roundToInt
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ApplicationPreferences(
    val appLanguage: String = "",
    val sortBy: Sort.By = Sort.By.TITLE,
    val sortOrder: Sort.Order = Sort.Order.ASCENDING,
    val themeConfig: ThemeConfig = ThemeConfig.SYSTEM,
    val shouldUseDynamicColors: Boolean = true,
    val shouldNavigateHomeOnTitleLongPress: Boolean = false,
    val shouldUseFloatingNavigationBar: Boolean = false,
    val shouldBlurFloatingNavigationBar: Boolean = true,
    val shouldBlurTopBar: Boolean = true,
    val shouldEnablePredictiveBack: Boolean = false,
    val shouldPreventScreenshots: Boolean = false,
    val shouldHideInRecents: Boolean = false,
    val shouldShowCloudTab: Boolean = true,
    val shouldMarkLastPlayedMedia: Boolean = true,
    val shouldRestoreLastPlayedMediaInFolders: Boolean = false,
    val shouldIgnoreNoMediaFiles: Boolean = false,
    val isRecycleBinEnabled: Boolean = false,
    val excludeFolders: List<StoragePath> = emptyList(),
    val scanFolders: List<StoragePath> = emptyList(),
    val localFolderLastPlayedMediaUris: Map<String, String> = emptyMap(),
    val remoteFolderLastPlayedMediaPaths: Map<String, String> = emptyMap(),
    val mediaViewMode: MediaViewMode = MediaViewMode.FOLDER_TREE,
    @SerialName("mediaLayoutMode")
    val videoLayoutMode: MediaLayoutMode = MediaLayoutMode.LIST,
    @SerialName("mediaLayoutScale")
    val videoLayoutScale: Float = DEFAULT_MEDIA_LAYOUT_SCALE,
    val folderLayoutMode: MediaLayoutMode = videoLayoutMode,
    val folderLayoutScale: Float = videoLayoutScale,
    @SerialName("directoryLayouts")
    val directoryQuickSettings: Map<StoragePath, MediaQuickSettingsOverrides> = emptyMap(),
    val cloudQuickSettingsByServerId: Map<String, CloudQuickSettings> = emptyMap(),

    // 字段显示
    val shouldShowDurationField: Boolean = true,
    val shouldShowExtensionField: Boolean = false,
    val shouldShowPathField: Boolean = true,
    val shouldShowResolutionField: Boolean = false,
    val shouldShowSizeField: Boolean = false,
    val shouldShowThumbnailField: Boolean = true,
    val shouldShowPlayedProgress: Boolean = true,

    // 缩略图生成
    val thumbnailGenerationStrategy: ThumbnailGenerationStrategy = ThumbnailGenerationStrategy.FRAME_AT_PERCENTAGE,
    val thumbnailFramePosition: Float = DEFAULT_THUMBNAIL_FRAME_POSITION,

    // 应用更新
    val shouldCheckForUpdatesOnStartup: Boolean = false,
    val updateChannel: UpdateChannel = UpdateChannel.STABLE,
    val manualVideoPaths: List<StoragePath> = emptyList(),
    val pendingExternalVideoPaths: List<StoragePath> = emptyList(),
) {

    fun isPathExcluded(path: StoragePath): Boolean = excludeFolders.any(path::isInside)

    // 扫描目录白名单为空时表示扫描全部存储
    fun isPathInsideScanFolders(path: StoragePath): Boolean = scanFolders.isEmpty() || scanFolders.any(path::isInside)

    fun normalizedVideoLayoutScale(): Float = videoLayoutScale
        .coerceIn(MIN_MEDIA_LAYOUT_SCALE, MAX_MEDIA_LAYOUT_SCALE)
        .roundToStep(MEDIA_LAYOUT_SCALE_STEP)

    fun withVideoLayoutScale(scale: Float): ApplicationPreferences = copy(
        videoLayoutScale = scale
            .coerceIn(MIN_MEDIA_LAYOUT_SCALE, MAX_MEDIA_LAYOUT_SCALE)
            .roundToStep(MEDIA_LAYOUT_SCALE_STEP),
    )

    fun cloudQuickSettings(serverId: Long?): CloudQuickSettings {
        val key = serverId?.takeIf { it > 0L }?.toString() ?: return CloudQuickSettings()
        return cloudQuickSettingsByServerId[key] ?: CloudQuickSettings()
    }

    fun withCloudQuickSettings(
        serverId: Long?,
        settings: CloudQuickSettings,
    ): ApplicationPreferences {
        val key = serverId?.takeIf { it > 0L }?.toString() ?: return this
        return copy(
            cloudQuickSettingsByServerId = cloudQuickSettingsByServerId + (key to settings.normalized()),
        )
    }

    fun withoutCloudQuickSettings(serverId: Long): ApplicationPreferences = copy(
        cloudQuickSettingsByServerId = cloudQuickSettingsByServerId - serverId.toString(),
    )

    companion object {
        const val DEFAULT_THUMBNAIL_FRAME_POSITION = 0.5f
        const val DEFAULT_MEDIA_LAYOUT_SCALE = 1f
        const val MIN_MEDIA_LAYOUT_SCALE = 0.75f
        const val MAX_MEDIA_LAYOUT_SCALE = 1.5f
        const val MEDIA_LAYOUT_SCALE_STEP = 0.05f
    }
}

@Serializable
data class CloudQuickSettings(
    val sortBy: Sort.By = Sort.By.TITLE,
    val sortOrder: Sort.Order = Sort.Order.ASCENDING,
    @SerialName("mediaLayoutMode")
    val videoLayoutMode: MediaLayoutMode = MediaLayoutMode.LIST,
    @SerialName("mediaLayoutScale")
    val videoLayoutScale: Float = ApplicationPreferences.DEFAULT_MEDIA_LAYOUT_SCALE,
    val folderLayoutMode: MediaLayoutMode = videoLayoutMode,
    val folderLayoutScale: Float = videoLayoutScale,
    @SerialName("directoryLayouts")
    val directoryQuickSettings: Map<String, MediaQuickSettingsOverrides> = emptyMap(),
    val shouldShowExtensionField: Boolean = false,
    val shouldShowPathField: Boolean = true,
    val shouldShowSizeField: Boolean = true,
    val shouldShowThumbnailField: Boolean = true,
    val shouldShowPlayedProgress: Boolean = true,
) {
    fun normalizedVideoLayoutScale(): Float = videoLayoutScale
        .coerceIn(ApplicationPreferences.MIN_MEDIA_LAYOUT_SCALE, ApplicationPreferences.MAX_MEDIA_LAYOUT_SCALE)
        .roundToStep(ApplicationPreferences.MEDIA_LAYOUT_SCALE_STEP)

    fun withVideoLayoutScale(scale: Float): CloudQuickSettings = copy(
        videoLayoutScale = scale
            .coerceIn(ApplicationPreferences.MIN_MEDIA_LAYOUT_SCALE, ApplicationPreferences.MAX_MEDIA_LAYOUT_SCALE)
            .roundToStep(ApplicationPreferences.MEDIA_LAYOUT_SCALE_STEP),
    )

    fun normalized(): CloudQuickSettings = copy(
        sortBy = sortBy.takeIf { it in SUPPORTED_SORT_OPTIONS } ?: Sort.By.TITLE,
        videoLayoutScale = normalizedVideoLayoutScale(),
    )

    companion object {
        val SUPPORTED_SORT_OPTIONS = listOf(Sort.By.TITLE, Sort.By.SIZE, Sort.By.PATH)
    }
}

private fun Float.roundToStep(step: Float): Float = (this / step).roundToInt() * step
