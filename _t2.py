from pathlib import Path
import re
NL = chr(10)
ps = Path(r'E:/Downloads/only_player_src/feature/player/src/main/java/one/only/player/feature/player/service/PlayerService.kt')
ms = Path(r'E:/Downloads/only_player_src/feature/player/src/main/java/one/only/player/feature/player/MediaPlayerScreen.kt')
gradle = Path(r'E:/Downloads/only_player_src/app/build.gradle.kts')
t = ps.read_text(encoding='utf-8').replace(chr(13)+chr(10), NL).replace(chr(13), NL)
print('start', len(t), 'manual', 'isManualDecoderApply' in t)
if 'import one.only.player.feature.player.extensions.copy' not in t:
    t = t.replace('import one.only.player.feature.player.extensions.decoderPriorityName'+NL, 'import one.only.player.feature.player.extensions.copy'+NL+'import one.only.player.feature.player.extensions.decoderPriorityName'+NL, 1)
    print('import copy')
# make updateExtension suspend and await write
if 'private suspend fun updateExtensionDecoderFromManualSelection' not in t:
    t = t.replace('private fun updateExtensionDecoderFromManualSelection(', 'private suspend fun updateExtensionDecoderFromManualSelection(', 1)
    t = t.replace('        serviceScope.launch(Dispatchers.IO) {'+NL+'            preferencesRepository.updateApplicationPreferences { current ->'+NL, '        preferencesRepository.updateApplicationPreferences { current ->'+NL, 1)
    # remove one closing brace that belonged to launch
    marker = '                }'+NL+'            }'+NL+'        }'+NL+'    }'+NL+NL+'    private fun applyExtensionDecoderForMediaItem'
    repl = '                }'+NL+'            }'+NL+'    }'+NL+NL+'    private fun stampDecoderOnCurrentMediaItem('+NL+'        player: ExoPlayer,'+NL+'        decoderPriority: DecoderPriority?,'+NL+'        isRemembered: Boolean,'+NL+'    ) {'+NL+'        val index = player.currentMediaItemIndex'+NL+'        if (index !in 0 until player.mediaItemCount) return'+NL+'        val current = player.getMediaItemAt(index)'+NL+'        val stamped = current.copy('+NL+'            decoderPriorityName = if (isRemembered) decoderPriority?.name else null,'+NL+'            isDecoderRemembered = isRemembered,'+NL+'            isVideoEffectsAvailable = shouldApplyVideoEffects('+NL+'                decoderPriority ?: activeDecoderPriority,'+NL+'            ),'+NL+'        )'+NL+'        player.replaceMediaItem(index, stamped)'+NL+'    }'+NL+NL+'    private fun applyExtensionDecoderForMediaItem'
    if marker not in t:
        # show context
        i = t.find('private fun applyExtensionDecoderForMediaItem')
        print('MARKER_FAIL', repr(t[i-80:i+20]))
        raise SystemExit('marker fail')
    t = t.replace(marker, repl, 1)
    print('suspend+stamp')
else:
    print('suspend already')
# apply guard
old_apply = '    private fun applyExtensionDecoderForMediaItem(mediaItem: MediaItem?) {'+NL+'        // 仅用于偏好变更时对齐当前解码，不在切条路径调用'+NL+'        if (mediaItem == null) return'+NL
new_apply = '    private fun applyExtensionDecoderForMediaItem(mediaItem: MediaItem?) {'+NL+'        // 仅用于偏好变更时对齐当前解码，不在切条路径调用'+NL+'        if (isManualDecoderApply) return'+NL+'        if (mediaItem == null) return'+NL
if 'if (isManualDecoderApply) return' not in t[t.find('applyExtensionDecoderForMediaItem'):t.find('applyExtensionDecoderForMediaItem')+200]:
    if old_apply not in t:
        raise SystemExit('apply missing')
    t = t.replace(old_apply, new_apply, 1)
    print('apply guard')
else:
    print('apply guard already')
# switch finally
if 'val ass = assHandler ?: return' not in t:
    t = t.replace('        val currentPlayer = session.player as? ExoPlayer ?: return'+NL+'        isDecoderSwitchInFlight = true'+NL, '        val currentPlayer = session.player as? ExoPlayer ?: return'+NL+'        val ass = assHandler ?: return'+NL+'        isDecoderSwitchInFlight = true'+NL+'        try {'+NL, 1)
    t = t.replace('            assHandler = assHandler ?: return,'+NL, '            assHandler = ass,'+NL, 1)
    t = t.replace('        runCatching {'+NL+'            currentPlayer.clearMediaItems()'+NL+'            currentPlayer.stop()'+NL+'            currentPlayer.release()'+NL+'        }'+NL+'        isDecoderSwitchInFlight = false'+NL+'    }'+NL+NL+'    private fun applyAmbienceModeToPlayer', '        runCatching {'+NL+'            currentPlayer.clearMediaItems()'+NL+'            currentPlayer.stop()'+NL+'            currentPlayer.release()'+NL+'        }'+NL+'        } finally {'+NL+'            isDecoderSwitchInFlight = false'+NL+'        }'+NL+'    }'+NL+NL+'    private fun applyAmbienceModeToPlayer', 1)
    print('switch finally')
else:
    print('switch already')
ps.write_text(t, encoding='utf-8', newline='\n')
print('service stage saved', len(t))
