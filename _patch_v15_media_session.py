from pathlib import Path

p = Path("feature/player/src/main/java/one/only/player/feature/player/service/PlayerService.kt")
t = p.read_text(encoding="utf-8")

old = '''    /**
     * 真正从系统媒体中心消失：关掉 legacy MediaSessionCompat 的 active。
     * 仅隐藏封面/通知不够——系统仍会通过 MediaSessionCompat 展示控件。
     */
    private fun updateLegacyMediaSessionActive(session: MediaSession? = mediaSession) {
        val activeSession = session ?: return
        val shouldBeActive = shouldPublishMediaSessionNotification(activeSession)
        runCatching {
            val implMethod = activeSession.javaClass.methods.firstOrNull { it.name == "getImpl" && it.parameterCount == 0 }
                ?: activeSession.javaClass.declaredMethods.firstOrNull { it.name == "getImpl" && it.parameterCount == 0 }
            implMethod?.isAccessible = true
            val impl = implMethod?.invoke(activeSession) ?: return@runCatching
            val legacyField = impl.javaClass.declaredFields.firstOrNull {
                it.name == "sessionLegacyStub" || it.type.name.endsWith("MediaSessionLegacyStub")
            } ?: return@runCatching
            legacyField.isAccessible = true
            val legacyStub = legacyField.get(impl) ?: return@runCatching
            val getCompat = legacyStub.javaClass.methods.firstOrNull {
                it.name == "getSessionCompat" && it.parameterCount == 0
            } ?: return@runCatching
            val compat = getCompat.invoke(legacyStub) ?: return@runCatching
            val setActive = compat.javaClass.methods.firstOrNull {
                it.name == "setActive" && it.parameterCount == 1
            } ?: return@runCatching
            setActive.invoke(compat, shouldBeActive)
            Logger.debug(TAG, "Legacy MediaSessionCompat active=$shouldBeActive")
        }.onFailure { error ->
            Logger.error(TAG, "Failed to update legacy MediaSessionCompat active state", error)
        }
    }
'''

new = r'''    /**
     * 真正从系统媒体中心消失：关掉 legacy MediaSessionCompat 的 active。
     * 仅隐藏封面/通知不够——系统仍会通过 MediaSessionCompat 展示控件。
     */
    private fun updateLegacyMediaSessionActive(session: MediaSession? = mediaSession) {
        val activeSession = session ?: return
        val shouldBeActive = shouldPublishMediaSessionNotification(activeSession)
        runCatching {
            val compat = resolveLegacyMediaSessionCompat(activeSession) ?: return@runCatching
            // HIDE / 非 MP3：清空元数据并设为停止，再 setActive(false)
            if (!shouldBeActive) {
                runCatching {
                    val playbackStateClass = Class.forName("androidx.media3.session.legacy.PlaybackStateCompat")
                    val builderClass = Class.forName("androidx.media3.session.legacy.PlaybackStateCompat$Builder")
                    val builder = builderClass.getDeclaredConstructor().newInstance()
                    val stateNone = playbackStateClass.getField("STATE_NONE").getInt(null)
                    builderClass.getMethod(
                        "setState",
                        Int::class.javaPrimitiveType,
                        Long::class.javaPrimitiveType,
                        Float::class.javaPrimitiveType,
                    ).invoke(builder, stateNone, 0L, 0f)
                    val state = builderClass.getMethod("build").invoke(builder)
                    compat.javaClass.methods.firstOrNull { it.name == "setPlaybackState" && it.parameterCount == 1 }
                        ?.invoke(compat, state)
                }
                runCatching {
                    val builderClass = Class.forName("androidx.media3.session.legacy.MediaMetadataCompat$Builder")
                    val builder = builderClass.getDeclaredConstructor().newInstance()
                    val empty = builderClass.getMethod("build").invoke(builder)
                    compat.javaClass.methods.firstOrNull { it.name == "setMetadata" && it.parameterCount == 1 }
                        ?.invoke(compat, empty)
                }
                runCatching {
                    compat.javaClass.methods.firstOrNull { it.name == "setQueue" && it.parameterCount == 1 }
                        ?.invoke(compat, null)
                }
            }
            val setActive = compat.javaClass.methods.firstOrNull {
                it.name == "setActive" && it.parameterCount == 1
            } ?: return@runCatching
            setActive.invoke(compat, shouldBeActive)
            Logger.info(
                TAG,
                "Legacy MediaSessionCompat active=$shouldBeActive visibility=${playerPreferences.mediaSessionVisibility}",
            )
        }.onFailure { error ->
            Logger.error(TAG, "Failed to update legacy MediaSessionCompat active state", error)
        }
    }

    private fun resolveLegacyMediaSessionCompat(session: MediaSession): Any? {
        val impl = session.javaClass.methods
            .firstOrNull { it.name == "getImpl" && it.parameterCount == 0 }
            ?.let { method ->
                runCatching {
                    method.isAccessible = true
                    method.invoke(session)
                }.getOrNull()
            }
            ?: session.javaClass.declaredMethods
                .firstOrNull { it.name == "getImpl" && it.parameterCount == 0 }
                ?.let { method ->
                    runCatching {
                        method.isAccessible = true
                        method.invoke(session)
                    }.getOrNull()
                }
            ?: runCatching {
                val field = session.javaClass.declaredFields.firstOrNull {
                    it.name == "impl" || it.name.contains("Impl")
                }
                field?.isAccessible = true
                field?.get(session)
            }.getOrNull()
            ?: return null

        val legacyStub = runCatching {
            val field = impl.javaClass.declaredFields.firstOrNull {
                it.name == "sessionLegacyStub" || it.type.name.endsWith("MediaSessionLegacyStub")
            }
            field?.isAccessible = true
            field?.get(impl)
        }.getOrNull() ?: return null

        return runCatching {
            legacyStub.javaClass.methods
                .firstOrNull { it.name == "getSessionCompat" && it.parameterCount == 0 }
                ?.invoke(legacyStub)
        }.getOrNull()
    }
'''

if old not in t:
    raise SystemExit("old updateLegacyMediaSessionActive not found")
t = t.replace(old, new)
print("updated updateLegacyMediaSessionActive")

old_fg = '''            if (android.os.Build.VERSION.SDK_INT >= 29) {
                startForeground(
                    0x4F504C59,
                    notification,
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
                )
            } else {
                startForeground(0x4F504C59, notification)
            }'''
new_fg = '''            // 静默前台：API 34+ 必须带类型；更低版本不传 mediaPlayback，减少系统媒体中心挂靠
            if (android.os.Build.VERSION.SDK_INT >= 34) {
                startForeground(
                    0x4F504C59,
                    notification,
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
                )
            } else {
                startForeground(0x4F504C59, notification)
            }'''
if old_fg not in t:
    raise SystemExit("old ensureNonMediaForeground block not found")
t = t.replace(old_fg, new_fg)
print("updated ensureNonMediaForeground")

old_on_update = '''        if (!shouldPublishMediaSessionNotification(session)) {
            // HIDE / 非 MP3：彻底不走默认 MediaStyle 通知
            ensureNonMediaForegroundIfNeeded(startInForegroundRequired = true)
            // 取消可能已有的媒体通知，避免系统媒体中心残留
            runCatching {
                val nm = getSystemService(android.app.NotificationManager::class.java)
                nm?.cancel(DefaultMediaNotificationProvider.DEFAULT_NOTIFICATION_ID)
            }
            return
        }
        super.onUpdateNotification(session, startInForegroundRequired)
'''
new_on_update = '''        if (!shouldPublishMediaSessionNotification(session)) {
            // HIDE / 非 MP3：关掉 MediaSessionCompat，再发静默非媒体通知
            updateLegacyMediaSessionActive(session)
            ensureNonMediaForegroundIfNeeded(startInForegroundRequired = true)
            // 取消可能已有的媒体通知，避免系统媒体中心残留
            runCatching {
                val nm = getSystemService(android.app.NotificationManager::class.java)
                nm?.cancel(DefaultMediaNotificationProvider.DEFAULT_NOTIFICATION_ID)
            }
            return
        }
        updateLegacyMediaSessionActive(session)
        super.onUpdateNotification(session, startInForegroundRequired)
'''
if old_on_update not in t:
    raise SystemExit("old onUpdateNotification block not found")
t = t.replace(old_on_update, new_on_update)
print("updated onUpdateNotification")

old_qn = '''                            val notification = androidx.core.app.NotificationCompat.Builder(this@PlayerService, channelId)
                                .setContentTitle(getString(coreUiR.string.app_name))
                                .setContentText(getString(coreUiR.string.playing_in_background))
                                .setSmallIcon(coreUiR.drawable.ic_play)
                                .setOngoing(true)
                                .setSilent(true)
                                .setLocalOnly(true)
                                .setVisibility(androidx.core.app.NotificationCompat.VISIBILITY_SECRET)
                                .setCategory(androidx.core.app.NotificationCompat.CATEGORY_SERVICE)
                                .setPriority(androidx.core.app.NotificationCompat.PRIORITY_MIN)
                                .build()
                            return androidx.media3.session.MediaNotification(0x4F504C59, notification)
'''
new_qn = '''                            // 非 MediaStyle：系统媒体中心不应展示
                            val notification = androidx.core.app.NotificationCompat.Builder(this@PlayerService, channelId)
                                .setContentTitle(getString(coreUiR.string.app_name))
                                .setContentText(getString(coreUiR.string.playing_in_background))
                                .setSmallIcon(coreUiR.drawable.ic_play)
                                .setOngoing(true)
                                .setSilent(true)
                                .setLocalOnly(true)
                                .setOnlyAlertOnce(true)
                                .setShowWhen(false)
                                .setVisibility(androidx.core.app.NotificationCompat.VISIBILITY_SECRET)
                                .setCategory(androidx.core.app.NotificationCompat.CATEGORY_SERVICE)
                                .setPriority(androidx.core.app.NotificationCompat.PRIORITY_MIN)
                                .build()
                            // 同步关掉 legacy session，防止只藏封面
                            updateLegacyMediaSessionActive(mediaSession)
                            return androidx.media3.session.MediaNotification(0x4F504C59, notification)
'''
if old_qn not in t:
    raise SystemExit("old quiet notification block not found")
t = t.replace(old_qn, new_qn)
print("updated quiet notification")

# Make isLocalMediaController more robust so AUDIO_ONLY never blocks local player
old_local = '''    private fun isLocalMediaController(controller: MediaSession.ControllerInfo): Boolean {
        val packageName = controller.packageName.orEmpty()
        return packageName.isBlank() ||
            packageName == applicationContext.packageName ||
            packageName == "androidx.media3.session" ||
            controller.uid == applicationInfo.uid ||
            controller.uid == android.os.Process.myUid() ||
            // 通知控制器属于本服务，必须放行，否则前台服务/本机会断
            (mediaSession?.isMediaNotificationController(controller) == true)
    }
'''
new_local = '''    private fun isLocalMediaController(controller: MediaSession.ControllerInfo): Boolean {
        val packageName = controller.packageName.orEmpty()
        val myUid = android.os.Process.myUid()
        // 本应用 / Media3 本机会话 / 同 UID / 通知控制器一律视为本地，避免仅 MP3 时点播即返回
        if (packageName.isBlank()) return true
        if (packageName == applicationContext.packageName) return true
        if (packageName == "androidx.media3.session") return true
        if (packageName.startsWith("androidx.media3")) return true
        if (controller.uid == applicationInfo.uid || controller.uid == myUid) return true
        if (mediaSession?.isMediaNotificationController(controller) == true) return true
        // 部分机型本地 MediaController 包名可能是系统壳，但 UID 仍是本应用
        return false
    }
'''
if old_local not in t:
    raise SystemExit("old isLocalMediaController not found")
t = t.replace(old_local, new_local)
print("updated isLocalMediaController")

p.write_text(t, encoding="utf-8")
print("PlayerService patched ok")
