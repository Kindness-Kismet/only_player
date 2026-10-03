package one.only.player.core.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

// 外部存储上的媒体路径。该挂载点大小写不敏感，故比较按大小写归一，value 保留真实写法供访问与展示。
// 不可用于 /data 等大小写敏感的挂载点
@Serializable(with = StoragePathSerializer::class)
class StoragePath private constructor(val value: String) : Comparable<StoragePath> {

    // 只折叠 ASCII：各文件系统折叠规则的公共子集，不会把非 ASCII 的不同文件判成同一个
    private val comparisonKey: String = buildString(value.length) {
        value.forEach { char -> append(char.foldAscii()) }
    }

    val name: String get() = value.substringAfterLast(SEPARATOR)

    val parent: StoragePath?
        get() = if (isRoot) null else of(value.substringBeforeLast(SEPARATOR).ifEmpty { "/" })

    // 文件系统根，不对应任何可浏览的媒体目录
    val isRoot: Boolean get() = comparisonKey == SEPARATOR.toString()

    // 按分隔符对齐，避免 /a/bc 被当成在 /a/b 之下
    fun isInside(directory: StoragePath): Boolean {
        if (directory.comparisonKey.isEmpty()) return false
        if (comparisonKey == directory.comparisonKey) return true

        // 根目录归一后就是分隔符，直接拼接会多出一个
        return comparisonKey.startsWith(directory.comparisonKey.trimEnd(SEPARATOR) + SEPARATOR)
    }

    override fun equals(other: Any?): Boolean = this === other || (other is StoragePath && comparisonKey == other.comparisonKey)

    override fun hashCode(): Int = comparisonKey.hashCode()

    override fun compareTo(other: StoragePath): Int = comparisonKey.compareTo(other.comparisonKey)

    override fun toString(): String = value

    companion object {
        const val SEPARATOR = '/'

        private val REDUNDANT_SEPARATORS = Regex("$SEPARATOR{2,}")

        fun of(raw: String): StoragePath = StoragePath(raw.normalized())

        // 与路径比较共用折叠规则，供按名匹配单级目录或文件时复用
        fun namesEqual(first: String, second: String): Boolean {
            if (first.length != second.length) return false

            return first.indices.all { index -> first[index].foldAscii() == second[index].foldAscii() }
        }

        private fun Char.foldAscii(): Char = if (this in 'A'..'Z') lowercaseChar() else this

        private fun String.normalized(): String {
            val unified = replace('\\', SEPARATOR).replace(REDUNDANT_SEPARATORS, SEPARATOR.toString())
            return unified.takeIf { it.length <= 1 } ?: unified.trimEnd(SEPARATOR)
        }
    }
}

internal object StoragePathSerializer : KSerializer<StoragePath> {

    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("StoragePath", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: StoragePath) = encoder.encodeString(value.value)

    override fun deserialize(decoder: Decoder): StoragePath = StoragePath.of(decoder.decodeString())
}
