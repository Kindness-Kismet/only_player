from pathlib import Path
NL=chr(10)
ps=Path(r'E:/Downloads/only_player_src/feature/player/src/main/java/one/only/player/feature/player/service/PlayerService.kt')
ms=Path(r'E:/Downloads/only_player_src/feature/player/src/main/java/one/only/player/feature/player/MediaPlayerScreen.kt')
gradle=Path(r'E:/Downloads/only_player_src/app/build.gradle.kts')
t=ps.read_text(encoding='utf-8').replace('\r\n',NL).replace('\r',NL)
old_set=('                CustomCommands.SET_DECODER_PRIORITY -> {'+NL+
'                    val name = args.getString(CustomCommands.DECODER_PRIORITY_NAME_KEY).orEmpty()'+NL+
'                    val priority = runCatching { DecoderPriority.valueOf(name) }.getOrNull()'+NL+
'                        ?: return@future SessionResult(SessionError.ERROR_BAD_VALUE)'+NL+
'                    val rememberForFile = args.getBoolean(CustomCommands.REMEMBER_FOR_FILE_KEY, false)'+NL+
'                    val player = mediaSession?.player as? ExoPlayer'+NL+
'                    val mediaItem = player?.currentMediaItem'+NL+
'                    if (mediaItem != null) {'+NL+
'                        // 控件改解码：同步写回扩展名设置（双向同步）'+NL+
'                        updateExtensionDecoderFromManualSelection(mediaItem, priority)'+NL+
'                        if (rememberForFile) {'+NL+
'                            // 与续播进度相同：按 URI 写 media_state.decoder_priority'+NL+
'                            val playbackStateUri = playbackStateCoordinator.resolvePlaybackStateUri(mediaItem)'+NL+
'                            mediaRepository.updateMediumDecoderPriority('+NL+
'                                uri = playbackStateUri,'+NL+
'                                decoderPriority = priority.name,'+NL+
'                            )'+NL+
'                        } else {'+NL+
'                            // 关记住：清掉该文件的 DB 解码'+NL+
'                            val playbackStateUri = playbackStateCoordinator.resolvePlaybackStateUri(mediaItem)'+NL+
'                            mediaRepository.updateMediumDecoderPriority('+NL+
'                                uri = playbackStateUri,'+NL+
'                                decoderPriority = null,'+NL+
'                            )'+NL+
'                        }'+NL+
'                    }'+NL+
'                    // 立即按所选值重建当前播放'+NL+
'                    switchPlayerDecoderPriority(priority)'+NL+
'                    return@future SessionResult(SessionResult.RESULT_SUCCESS)'+NL+
'                }')
new_set=('                CustomCommands.SET_DECODER_PRIORITY -> {'+NL+
'                    val name = args.getString(CustomCommands.DECODER_PRIORITY_NAME_KEY).orEmpty()'+NL+
'                    val priority = runCatching { DecoderPriority.valueOf(name) }.getOrNull()'+NL+
'                        ?: return@future SessionResult(SessionError.ERROR_BAD_VALUE)'+NL+
'                    val rememberForFile = args.getBoolean(CustomCommands.REMEMBER_FOR_FILE_KEY, false)'+NL+
'                    val player = mediaSession?.player as? ExoPlayer'+NL+
'                    val mediaItem = player?.currentMediaItem'+NL+
'                    isManualDecoderApply = true'+NL+
'                    try {'+NL+
'                        if (player != null && mediaItem != null) {'+NL+
'                            val playbackStateUri = playbackStateCoordinator.resolvePlaybackStateUri(mediaItem)'+NL+
'                            if (rememberForFile) {'+NL+
'                                mediaRepository.updateMediumDecoderPriority('+NL+
'                                    uri = playbackStateUri,'+NL+
'                                    decoderPriority = priority.name,'+NL+
'                                )'+NL+
'                                stampDecoderOnCurrentMediaItem('+NL+
'                                    player = player,'+NL+
'                                    decoderPriority = priority,'+NL+
'                                    isRemembered = true,'+NL+
'                                )'+NL+
'                            } else {'+NL+
'                                mediaRepository.updateMediumDecoderPriority('+NL+
'                                    uri = playbackStateUri,'+NL+
'                                    decoderPriority = null,'+NL+
'                                )'+NL+
'                                updateExtensionDecoderFromManualSelection(mediaItem, priority)'+NL+
'                                stampDecoderOnCurrentMediaItem('+NL+
'                                    player = player,'+NL+
'                                    decoderPriority = priority,'+NL+
'                                    isRemembered = false,'+NL+
'                                )'+NL+
'                            }'+NL+
'                        }'+NL+
'                        switchPlayerDecoderPriority(priority)'+NL+
'                    } finally {'+NL+
'                        isManualDecoderApply = false'+NL+
'                    }'+NL+
'                    return@future SessionResult(SessionResult.RESULT_SUCCESS)'+NL+
'                }')
if old_set not in t:
    print('SET missing'); print(repr(t[t.find('SET_DECODER_PRIORITY'):t.find('SET_DECODER_PRIORITY')+200])); raise SystemExit(1)
t=t.replace(old_set,new_set,1); print('SET ok')
old_g=('        serviceScope.launch {'+NL+
'            // 全局解码偏好：严格应用所选值，禁止再经扩展名解析（否则控件选 HW 会被打回 AUTO）'+NL+
'            preferencesRepository.playerPreferences'+NL+
'                .distinctUntilChanged { old, new -> old.decoderPriority == new.decoderPriority }'+NL+
'                .collect { prefs ->'+NL+
'                    if (prefs.decoderPriority != activeDecoderPriority) {'+NL+
'                        switchPlayerDecoderPriority(prefs.decoderPriority)'+NL+
'                    }'+NL+
'                }'+NL+
'        }')
new_g=('        serviceScope.launch {'+NL+
'            // 全局默认解码变更：按 文件记住 > 扩展名 > 全局 重新解析，禁止硬覆盖扩展名'+NL+
'            preferencesRepository.playerPreferences'+NL+
'                .distinctUntilChanged { old, new -> old.decoderPriority == new.decoderPriority }'+NL+
'                .collect {'+NL+
'                    if (isManualDecoderApply) return@collect'+NL+
'                    val current = mediaSession?.player?.currentMediaItem'+NL+
'                    applyExtensionDecoderForMediaItem(current)'+NL+
'                }'+NL+
'        }')
if old_g not in t:
    print('global missing'); raise SystemExit(2)
t=t.replace(old_g,new_g,1); print('global ok')
ps.write_text(t, encoding='utf-8', newline='\n')
mt=ms.read_text(encoding='utf-8').replace('\r\n',NL).replace('\r',NL)
old_c=('                                    controller?.setDecoderPriorityNow('+NL+
'                                        priorityName = priority.name,'+NL+
'                                        rememberForThisFile = isRememberForThisFile,'+NL+
'                                    )'+NL+
'                                    // 同步全局默认，方便设置页一致'+NL+
'                                    viewModel.updateDecoderPriority(priority)'+NL)
new_c=('                                    controller?.setDecoderPriorityNow('+NL+
'                                        priorityName = priority.name,'+NL+
'                                        rememberForThisFile = isRememberForThisFile,'+NL+
'                                    )'+NL)
if old_c not in mt:
    print('ui click missing'); raise SystemExit(3)
mt=mt.replace(old_c,new_c,1); print('ui click ok')
old_r=('                                    controller?.setDecoderPriorityNow('+NL+
'                                        priorityName = selectedPriority.name,'+NL+
'                                        rememberForThisFile = enabled,'+NL+
'                                    )'+NL+
'                                    if (enabled) {'+NL+
'                                        viewModel.rememberDecoderForMediaUri(mediaUri, selectedPriority)'+NL+
'                                    } else {'+NL+
'                                        viewModel.clearDecoderForMediaUri(mediaUri)'+NL+
'                                    }'+NL)
new_r=('                                    controller?.setDecoderPriorityNow('+NL+
'                                        priorityName = selectedPriority.name,'+NL+
'                                        rememberForThisFile = enabled,'+NL+
'                                    )'+NL)
if old_r not in mt:
    print('ui remember missing'); raise SystemExit(4)
mt=mt.replace(old_r,new_r,1); print('ui remember ok')
# remove unused mediaUri line only in decoder block if present and unused
mt=mt.replace('                            val mediaUri = currentMediaUriString()'+NL, '', 1)
ms.write_text(mt, encoding='utf-8', newline='\n')
import re
g=gradle.read_text(encoding='utf-8')
g2=re.sub(r'versionCode\s*=\s*\d+','versionCode = 165',g,count=1)
g2=re.sub(r'versionName\s*=\s*"[^"]+"','versionName = "1.0.164"',g2,count=1)
gradle.write_text(g2,encoding='utf-8')
print('version', re.search(r'versionName\s*=\s*"([^"]+)"',g2).group(1), re.search(r'versionCode\s*=\s*(\d+)',g2).group(1))
print('ALL DONE')
