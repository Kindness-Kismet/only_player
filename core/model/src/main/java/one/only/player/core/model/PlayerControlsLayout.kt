package one.only.player.core.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.element
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.encoding.decodeStructure
import kotlinx.serialization.encoding.encodeStructure

@Serializable
enum class PlayerControlZone {
    TOP_RIGHT,
    BOTTOM_LEFT,
    ABOVE_SEEKBAR_RIGHT,
}

@Serializable
data class PlayerControlLayoutEntry(
    val control: PlayerControl,
    val zone: PlayerControlZone,
)

@Serializable(with = PlayerControlsLayoutSerializer::class)
class PlayerControlsLayout(
    entries: List<PlayerControlLayoutEntry> = defaultEntries(),
    version: Int = CURRENT_VERSION,
) {

    val entries: List<PlayerControlLayoutEntry> = normalizeEntries(entries, version)

    fun controlsIn(zone: PlayerControlZone): List<PlayerControl> = entries
        .filter { it.zone == zone }
        .map { it.control }

    fun move(
        control: PlayerControl,
        toZone: PlayerControlZone,
        toIndex: Int,
    ): PlayerControlsLayout {
        if (control !in customizableControls) return this

        // 进度条上方右侧区域只允许 1 个控件
        if (toZone == PlayerControlZone.ABOVE_SEEKBAR_RIGHT) {
            val withoutMoved = entries.filterNot { it.control == control }
            val othersInZone = withoutMoved.filter { it.zone == PlayerControlZone.ABOVE_SEEKBAR_RIGHT }
            val remaining = withoutMoved.filterNot { it.zone == PlayerControlZone.ABOVE_SEEKBAR_RIGHT }.toMutableList()
            // 原该区控件退回底部左侧末尾
            othersInZone.forEach { old ->
                remaining += PlayerControlLayoutEntry(
                    control = old.control,
                    zone = PlayerControlZone.BOTTOM_LEFT,
                )
            }
            remaining += PlayerControlLayoutEntry(
                control = control,
                zone = PlayerControlZone.ABOVE_SEEKBAR_RIGHT,
            )
            // 右上角固定最多 4 个
            return enforceZoneLimits(PlayerControlsLayout(remaining))
        }

        val remainingEntries = entries.filterNot { it.control == control }.toMutableList()
        val targetIndexes = remainingEntries.withIndex()
            .filter { it.value.zone == toZone }
            .map { it.index }

        val insertAt = when {
            targetIndexes.isEmpty() -> remainingEntries.size
            toIndex <= 0 -> targetIndexes.first()
            toIndex >= targetIndexes.size -> targetIndexes.last() + 1
            else -> targetIndexes[toIndex]
        }

        remainingEntries.add(
            index = insertAt,
            element = PlayerControlLayoutEntry(
                control = control,
                zone = toZone,
            ),
        )
        return enforceZoneLimits(PlayerControlsLayout(remainingEntries))
    }

    private fun enforceZoneLimits(layout: PlayerControlsLayout): PlayerControlsLayout {
        val topRight = layout.controlsIn(PlayerControlZone.TOP_RIGHT)
        val above = layout.controlsIn(PlayerControlZone.ABOVE_SEEKBAR_RIGHT)
        val bottom = layout.controlsIn(PlayerControlZone.BOTTOM_LEFT).toMutableList()

        val limitedTop = topRight.take(MAX_TOP_RIGHT_CONTROLS)
        topRight.drop(MAX_TOP_RIGHT_CONTROLS).forEach { overflow ->
            if (overflow !in bottom) bottom += overflow
        }

        val limitedAbove = above.take(MAX_ABOVE_SEEKBAR_CONTROLS)
        above.drop(MAX_ABOVE_SEEKBAR_CONTROLS).forEach { overflow ->
            if (overflow !in bottom && overflow !in limitedTop) bottom += overflow
        }

        val rebuilt = buildList {
            limitedTop.forEach {
                add(PlayerControlLayoutEntry(it, PlayerControlZone.TOP_RIGHT))
            }
            limitedAbove.forEach {
                add(PlayerControlLayoutEntry(it, PlayerControlZone.ABOVE_SEEKBAR_RIGHT))
            }
            bottom.forEach {
                add(PlayerControlLayoutEntry(it, PlayerControlZone.BOTTOM_LEFT))
            }
        }
        return PlayerControlsLayout(rebuilt)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PlayerControlsLayout) return false
        return entries == other.entries
    }

    override fun hashCode(): Int = entries.hashCode()

    override fun toString(): String = "PlayerControlsLayout(entries=$entries)"

    companion object {
        val topRightControls: List<PlayerControl> = listOf(
            PlayerControl.PLAYLIST,
            PlayerControl.PLAYBACK_SPEED,
            PlayerControl.AUDIO,
            PlayerControl.SUBTITLE,
        )

        val bottomLeftControls: List<PlayerControl> = listOf(
            PlayerControl.LOCK,
            PlayerControl.MUTE,
            PlayerControl.MARK,
            PlayerControl.SCALE,
            PlayerControl.DECODER,
            PlayerControl.AMBIENCE_MODE,
            PlayerControl.VIDEO_FILTERS,
            PlayerControl.PIP,
            PlayerControl.SCREENSHOT,
            PlayerControl.BACKGROUND_PLAY,
            PlayerControl.LOOP,
            PlayerControl.SHUFFLE,
            PlayerControl.SLEEP_TIMER,
            PlayerControl.ROTATE,
            PlayerControl.CUSTOMIZE,
        )

        val customizableControls: Set<PlayerControl> =
            (topRightControls + bottomLeftControls).toSet()

        internal const val CURRENT_VERSION = 7
        const val MAX_TOP_RIGHT_CONTROLS = 4
        const val MAX_ABOVE_SEEKBAR_CONTROLS = 1

        private val introducedControlVersions: Map<PlayerControl, Int> = mapOf(
            PlayerControl.MUTE to 2,
            PlayerControl.MARK to 3,
            PlayerControl.ROTATE to 4,
            PlayerControl.CUSTOMIZE to 5,
        )

        fun defaultEntries(): List<PlayerControlLayoutEntry> = buildList {
            addAll(
                topRightControls.map { control ->
                    PlayerControlLayoutEntry(
                        control = control,
                        zone = PlayerControlZone.TOP_RIGHT,
                    )
                },
            )
            addAll(
                bottomLeftControls.map { control ->
                    PlayerControlLayoutEntry(
                        control = control,
                        zone = PlayerControlZone.BOTTOM_LEFT,
                    )
                },
            )
        }

        private fun normalizeEntries(
            entries: List<PlayerControlLayoutEntry>,
            version: Int,
        ): List<PlayerControlLayoutEntry> {
            val controlsToReposition = introducedControlVersions
                .filterValues { introducedVersion -> version < introducedVersion }
                .keys
            val seenControls = mutableSetOf<PlayerControl>()
            val normalizedEntries = entries
                .filter { it.control in customizableControls }
                .filter { it.control !in controlsToReposition }
                .filter { seenControls.add(it.control) }
                .toMutableList()
            val defaultEntries = defaultEntries()
            val defaultControlIndexes = defaultEntries
                .mapIndexed { index, entry -> entry.control to index }
                .toMap()

            defaultEntries.forEachIndexed { defaultIndex, defaultEntry ->
                if (defaultEntry.control in seenControls) return@forEachIndexed
                val insertAfter = normalizedEntries.indexOfLast { entry ->
                    entry.zone == defaultEntry.zone && defaultControlIndexes.getValue(entry.control) < defaultIndex
                }
                val insertAt = when {
                    insertAfter >= 0 -> insertAfter + 1
                    else -> normalizedEntries.indexOfFirst { entry ->
                        entry.zone == defaultEntry.zone && defaultControlIndexes.getValue(entry.control) > defaultIndex
                    }.takeIf { it >= 0 } ?: normalizedEntries.size
                }
                normalizedEntries.add(insertAt, defaultEntry)
                seenControls.add(defaultEntry.control)
            }

            // 区域数量限制：右上 4、进度条上方右 1
            val topRight = normalizedEntries.filter { it.zone == PlayerControlZone.TOP_RIGHT }
            val above = normalizedEntries.filter { it.zone == PlayerControlZone.ABOVE_SEEKBAR_RIGHT }
            val bottom = normalizedEntries.filter { it.zone == PlayerControlZone.BOTTOM_LEFT }.toMutableList()
            topRight.drop(MAX_TOP_RIGHT_CONTROLS).forEach { overflow ->
                if (overflow.control !in bottom.map { it.control }) {
                    bottom += PlayerControlLayoutEntry(overflow.control, PlayerControlZone.BOTTOM_LEFT)
                }
            }
            above.drop(MAX_ABOVE_SEEKBAR_CONTROLS).forEach { overflow ->
                if (overflow.control !in bottom.map { it.control }) {
                    bottom += PlayerControlLayoutEntry(overflow.control, PlayerControlZone.BOTTOM_LEFT)
                }
            }
            return buildList {
                addAll(topRight.take(MAX_TOP_RIGHT_CONTROLS))
                addAll(above.take(MAX_ABOVE_SEEKBAR_CONTROLS))
                addAll(bottom)
            }
        }
    }
}

object PlayerControlsLayoutSerializer : KSerializer<PlayerControlsLayout> {
    private val entriesSerializer = ListSerializer(PlayerControlLayoutEntry.serializer())

    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("one.only.player.core.model.PlayerControlsLayout") {
        element("entries", entriesSerializer.descriptor)
        element<Int>("version")
    }

    override fun serialize(
        encoder: Encoder,
        value: PlayerControlsLayout,
    ) {
        encoder.encodeStructure(descriptor) {
            encodeSerializableElement(
                descriptor = descriptor,
                index = 0,
                serializer = entriesSerializer,
                value = value.entries,
            )
            encodeIntElement(
                descriptor = descriptor,
                index = 1,
                value = PlayerControlsLayout.CURRENT_VERSION,
            )
        }
    }

    override fun deserialize(decoder: Decoder): PlayerControlsLayout {
        var entries = PlayerControlsLayout.defaultEntries()
        var version = 0

        decoder.decodeStructure(descriptor) {
            while (true) {
                when (val index = decodeElementIndex(descriptor)) {
                    CompositeDecoder.DECODE_DONE -> break
                    0 -> entries = decodeSerializableElement(
                        descriptor = descriptor,
                        index = index,
                        deserializer = entriesSerializer,
                    )
                    1 -> version = decodeIntElement(
                        descriptor = descriptor,
                        index = index,
                    )
                }
            }
        }

        return PlayerControlsLayout(
            entries = entries,
            version = version,
        )
    }
}
