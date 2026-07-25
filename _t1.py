from pathlib import Path
p = Path(r'E:/Downloads/only_player_src/feature/player/src/main/java/one/only/player/feature/player/service/PlayerService.kt')
t = p.read_text(encoding='utf-8')
old = '    private var activeDecoderPriority: DecoderPriority = DecoderPriority.AUTOMATIC\n    @Volatile private var isDecoderSwitchInFlight: Boolean = false\n'
new = old + '    @Volatile private var isManualDecoderApply: Boolean = false\n'
if 'isManualDecoderApply' not in t:
    if old not in t:
        raise SystemExit('flags missing')
    p.write_text(t.replace(old, new, 1), encoding='utf-8')
    print('flags added')
else:
    print('flags already')
