package one.only.player.core.common.media

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import java.io.File
import java.io.InputStream

data class MpegTsProgramMapPidFix(
    val declaredPmtPid: Int,
    val actualPmtPid: Int,
    val patPacketPatches: List<MpegTsPacketPatch>,
)

data class MpegTsPacketPatch(
    val packetStart: Long,
    val packetBytes: ByteArray,
)

fun hasMpegTsPacketSync(inputStream: InputStream): Boolean {
    val header = ByteArray(TS_PACKET_SIZE * TS_PACKET_CHECK_COUNT)
    val bytesRead = inputStream.readAtMost(header)
    if (bytesRead <= TS_PACKET_SIZE * (TS_PACKET_CHECK_COUNT - 1)) return false

    return (0 until TS_PACKET_CHECK_COUNT).all { packetIndex ->
        header[packetIndex * TS_PACKET_SIZE].toUnsignedInt() == TS_SYNC_BYTE
    }
}

fun detectMpegTsProgramMapPidFix(inputStream: InputStream): MpegTsProgramMapPidFix? {
    val buffer = ByteArray(TS_PACKET_SIZE * TS_PID_SCAN_PACKET_COUNT)
    val bytesRead = inputStream.readAtMost(buffer)
    if (!hasTsPacketSync(buffer, bytesRead)) return null

    val actualPmtPid = findActualProgramMapPid(buffer, bytesRead) ?: return null
    if (actualPmtPid == TS_PAT_PID || actualPmtPid == TS_NULL_PACKET_PID) return null
    val patPatchPlan = createPatPacketPatches(
        buffer = buffer,
        bytesRead = bytesRead,
        actualPmtPid = actualPmtPid,
    ) ?: return null

    return MpegTsProgramMapPidFix(
        declaredPmtPid = patPatchPlan.declaredPmtPid,
        actualPmtPid = actualPmtPid,
        patPacketPatches = patPatchPlan.packetPatches,
    )
}

fun Uri.detectMpegTsProgramMapPidFix(context: Context): MpegTsProgramMapPidFix? = runCatching {
    when (scheme) {
        ContentResolver.SCHEME_CONTENT -> context.contentResolver.openInputStream(this)?.use(::detectMpegTsProgramMapPidFix)
        ContentResolver.SCHEME_FILE -> path?.let { File(it).inputStream().use(::detectMpegTsProgramMapPidFix) }
        else -> null
    }
}.getOrNull()

fun File.detectMpegTsProgramMapPidFix(): MpegTsProgramMapPidFix? = runCatching {
    val file = takeIf(File::isFile) ?: return@runCatching null
    file.inputStream().use(::detectMpegTsProgramMapPidFix)
}.getOrNull()

fun ByteArray.patchMpegTsProgramMapPid(
    bufferOffset: Int,
    readStart: Long,
    readEnd: Long,
    fix: MpegTsProgramMapPidFix,
) {
    fix.patPacketPatches.forEach { patch ->
        val packetStart = patch.packetStart
        val packetEnd = packetStart + patch.packetBytes.size
        val copyStart = maxOf(readStart, packetStart)
        val copyEnd = minOf(readEnd, packetEnd)
        if (copyStart >= copyEnd) return@forEach

        System.arraycopy(
            patch.packetBytes,
            (copyStart - packetStart).toInt(),
            this,
            bufferOffset + (copyStart - readStart).toInt(),
            (copyEnd - copyStart).toInt(),
        )
    }

    val firstPacketStart = readStart.nextMpegTsPacketBoundary()
    var packetStart = firstPacketStart
    while (packetStart + TS_PACKET_SIZE <= readEnd) {
        val localPacketStart = bufferOffset + (packetStart - readStart).toInt()
        patchProgramMapPid(
            buffer = this,
            packetStart = localPacketStart,
            packetEnd = localPacketStart + TS_PACKET_SIZE,
            fix = fix,
        )
        packetStart += TS_PACKET_SIZE
    }
}

fun Int.toMpegTsPidHex(): String = "0x${toString(radix = 16).padStart(4, '0')}"

private data class PatPacketPatchPlan(
    val declaredPmtPid: Int,
    val packetPatches: List<MpegTsPacketPatch>,
)

private data class PatSection(
    val section: Int,
    val programMapPids: List<Int>,
)

private fun hasTsPacketSync(
    buffer: ByteArray,
    bytesRead: Int,
): Boolean {
    if (bytesRead < TS_PACKET_SIZE * 2) return false
    return buffer[0].toUnsignedInt() == TS_SYNC_BYTE &&
        buffer[TS_PACKET_SIZE].toUnsignedInt() == TS_SYNC_BYTE
}

private fun createPatPacketPatches(
    buffer: ByteArray,
    bytesRead: Int,
    actualPmtPid: Int,
): PatPacketPatchPlan? {
    val declaredPmtPids = linkedSetOf<Int>()
    val patches = mutableListOf<MpegTsPacketPatch>()
    forEachTsPacket(buffer, bytesRead) { packetStart, packetEnd ->
        val patSection = findPatSection(buffer, packetStart, packetEnd) ?: return@forEachTsPacket
        val declaredPmtPid = patSection.programMapPids
            .filterNot { it == actualPmtPid }
            .distinct()
            .singleOrNull()
            ?: return@forEachTsPacket
        if (declaredPmtPid == TS_PAT_PID || declaredPmtPid == TS_NULL_PACKET_PID) return@forEachTsPacket

        createPatPacketPatch(
            buffer = buffer,
            packetStart = packetStart,
            packetEnd = packetEnd,
            section = patSection.section,
            declaredPmtPid = declaredPmtPid,
            actualPmtPid = actualPmtPid,
        )?.let { patch ->
            declaredPmtPids += declaredPmtPid
            patches += patch
        }
    }

    val declaredPmtPid = declaredPmtPids.singleOrNull() ?: return null
    if (patches.isEmpty()) return null
    return PatPacketPatchPlan(
        declaredPmtPid = declaredPmtPid,
        packetPatches = patches,
    )
}

private fun findPatSection(
    buffer: ByteArray,
    packetStart: Int,
    packetEnd: Int,
): PatSection? {
    if (packetPid(buffer, packetStart) != TS_PAT_PID) return null

    val section = psiSectionOffset(buffer, packetStart, packetEnd) ?: return null
    if (!isPsiSection(buffer, section, packetEnd, TS_TABLE_ID_PAT)) return null

    val sectionEnd = psiSectionEnd(buffer, section)
    val programMapPids = mutableListOf<Int>()
    var entryOffset = section + 8
    val entryEnd = sectionEnd - 4
    while (entryOffset + 4 <= entryEnd) {
        val programNumber = (buffer[entryOffset].toUnsignedInt() shl 8) or
            buffer[entryOffset + 1].toUnsignedInt()
        if (programNumber != 0) {
            programMapPids += readPid(buffer, entryOffset + 2)
        }
        entryOffset += 4
    }

    if (programMapPids.isEmpty()) return null
    return PatSection(
        section = section,
        programMapPids = programMapPids,
    )
}

private fun createPatPacketPatch(
    buffer: ByteArray,
    packetStart: Int,
    packetEnd: Int,
    section: Int,
    declaredPmtPid: Int,
    actualPmtPid: Int,
): MpegTsPacketPatch? {
    val packet = buffer.copyOfRange(packetStart, packetEnd)
    val localSection = section - packetStart
    val sectionEnd = psiSectionEnd(packet, localSection)
    val crcOffset = sectionEnd - 4
    if (crcOffset < localSection) return null

    var entryOffset = localSection + 8
    val entryEnd = crcOffset
    var hasPatChanged = false
    while (entryOffset + 4 <= entryEnd) {
        val programNumber = (packet[entryOffset].toUnsignedInt() shl 8) or
            packet[entryOffset + 1].toUnsignedInt()
        if (programNumber != 0 && readPid(packet, entryOffset + 2) == declaredPmtPid) {
            writePid(packet, entryOffset + 2, actualPmtPid)
            hasPatChanged = true
        }
        entryOffset += 4
    }
    if (!hasPatChanged) return null

    val expectedCrc = readPsiSectionCrc(packet, crcOffset)
    val patchedCrc = mpegCrc32(
        buffer = packet,
        start = localSection,
        endExclusive = crcOffset,
    )
    if (patchedCrc != expectedCrc) return null

    return MpegTsPacketPatch(
        packetStart = packetStart.toLong(),
        packetBytes = packet,
    )
}

private fun findActualProgramMapPid(
    buffer: ByteArray,
    bytesRead: Int,
): Int? {
    val pmtPids = linkedSetOf<Int>()
    forEachTsPacket(buffer, bytesRead) { packetStart, packetEnd ->
        val section = psiSectionOffset(buffer, packetStart, packetEnd) ?: return@forEachTsPacket
        if (isPsiSection(buffer, section, packetEnd, TS_TABLE_ID_PMT) && hasValidPsiSectionCrc(buffer, section)) {
            pmtPids += packetPid(buffer, packetStart)
        }
    }
    return pmtPids.singleOrNull()
}

private inline fun forEachTsPacket(
    buffer: ByteArray,
    bytesRead: Int,
    block: (packetStart: Int, packetEnd: Int) -> Unit,
) {
    var packetStart = 0
    while (packetStart + TS_PACKET_SIZE <= bytesRead) {
        if (buffer[packetStart].toUnsignedInt() == TS_SYNC_BYTE) {
            block(packetStart, packetStart + TS_PACKET_SIZE)
        }
        packetStart += TS_PACKET_SIZE
    }
}

private fun patchProgramMapPid(
    buffer: ByteArray,
    packetStart: Int,
    packetEnd: Int,
    fix: MpegTsProgramMapPidFix,
): Boolean {
    if (buffer[packetStart].toUnsignedInt() != TS_SYNC_BYTE) return false
    if (packetPid(buffer, packetStart) != TS_PAT_PID) return false

    val section = psiSectionOffset(buffer, packetStart, packetEnd) ?: return false
    if (!isPsiSection(buffer, section, packetEnd, TS_TABLE_ID_PAT)) return false

    val sectionEnd = psiSectionEnd(buffer, section)
    var entryOffset = section + 8
    val entryEnd = sectionEnd - 4
    var hasPatChanged = false
    while (entryOffset + 4 <= entryEnd) {
        val programNumber = (buffer[entryOffset].toUnsignedInt() shl 8) or
            buffer[entryOffset + 1].toUnsignedInt()
        if (programNumber != 0 && readPid(buffer, entryOffset + 2) == fix.declaredPmtPid) {
            writePid(buffer, entryOffset + 2, fix.actualPmtPid)
            hasPatChanged = true
        }
        entryOffset += 4
    }
    if (hasPatChanged) {
        val crcOffset = sectionEnd - 4
        val crc = mpegCrc32(
            buffer = buffer,
            start = section,
            endExclusive = crcOffset,
        )
        buffer[crcOffset] = ((crc ushr 24) and 0xFF).toByte()
        buffer[crcOffset + 1] = ((crc ushr 16) and 0xFF).toByte()
        buffer[crcOffset + 2] = ((crc ushr 8) and 0xFF).toByte()
        buffer[crcOffset + 3] = (crc and 0xFF).toByte()
    }
    return hasPatChanged
}

private fun psiSectionOffset(
    buffer: ByteArray,
    packetStart: Int,
    packetEnd: Int,
): Int? {
    if ((buffer[packetStart + 1].toUnsignedInt() and 0x40) == 0) return null

    val payloadOffset = payloadOffset(buffer, packetStart, packetEnd) ?: return null
    if (payloadOffset >= packetEnd) return null

    val pointer = buffer[payloadOffset].toUnsignedInt()
    val section = payloadOffset + 1 + pointer
    return section.takeIf { it + 3 <= packetEnd }
}

private fun payloadOffset(
    buffer: ByteArray,
    packetStart: Int,
    packetEnd: Int,
): Int? {
    val adaptationFieldControl = (buffer[packetStart + 3].toUnsignedInt() shr 4) and 0x03
    return when (adaptationFieldControl) {
        1 -> packetStart + 4
        3 -> {
            val adaptationFieldLength = buffer[packetStart + 4].toUnsignedInt()
            (packetStart + 5 + adaptationFieldLength).takeIf { it <= packetEnd }
        }
        else -> null
    }
}

private fun isPsiSection(
    buffer: ByteArray,
    section: Int,
    packetEnd: Int,
    tableId: Int,
): Boolean {
    if (buffer[section].toUnsignedInt() != tableId) return false
    if ((buffer[section + 1].toUnsignedInt() and 0x80) == 0) return false

    val sectionEnd = psiSectionEnd(buffer, section)
    return sectionEnd <= packetEnd
}

private fun psiSectionEnd(buffer: ByteArray, section: Int): Int {
    val sectionLength = ((buffer[section + 1].toUnsignedInt() and 0x0F) shl 8) or
        buffer[section + 2].toUnsignedInt()
    return section + 3 + sectionLength
}

private fun hasValidPsiSectionCrc(buffer: ByteArray, section: Int): Boolean {
    val sectionEnd = psiSectionEnd(buffer, section)
    val crcOffset = sectionEnd - 4
    if (crcOffset < section) return false

    val expectedCrc = readPsiSectionCrc(buffer, crcOffset)
    return mpegCrc32(
        buffer = buffer,
        start = section,
        endExclusive = crcOffset,
    ) == expectedCrc
}

private fun readPsiSectionCrc(buffer: ByteArray, crcOffset: Int): Int = (buffer[crcOffset].toUnsignedInt() shl 24) or
    (buffer[crcOffset + 1].toUnsignedInt() shl 16) or
    (buffer[crcOffset + 2].toUnsignedInt() shl 8) or
    buffer[crcOffset + 3].toUnsignedInt()

private fun packetPid(buffer: ByteArray, packetStart: Int): Int = readPid(buffer, packetStart + 1)

private fun readPid(buffer: ByteArray, offset: Int): Int = ((buffer[offset].toUnsignedInt() and 0x1F) shl 8) or
    buffer[offset + 1].toUnsignedInt()

private fun writePid(
    buffer: ByteArray,
    offset: Int,
    pid: Int,
) {
    buffer[offset] = ((buffer[offset].toUnsignedInt() and 0xE0) or ((pid shr 8) and 0x1F)).toByte()
    buffer[offset + 1] = (pid and 0xFF).toByte()
}

private fun mpegCrc32(
    buffer: ByteArray,
    start: Int,
    endExclusive: Int,
): Int {
    var crc = 0xFFFFFFFF.toInt()
    for (index in start until endExclusive) {
        crc = crc xor (buffer[index].toUnsignedInt() shl 24)
        repeat(8) {
            crc = if ((crc and CRC32_MPEG_MSB_MASK) != 0) {
                (crc shl 1) xor CRC32_MPEG_POLYNOMIAL
            } else {
                crc shl 1
            }
        }
    }
    return crc
}

private fun InputStream.readAtMost(buffer: ByteArray): Int {
    var offset = 0
    while (offset < buffer.size) {
        val read = read(buffer, offset, buffer.size - offset)
        if (read < 0) break
        offset += read
    }
    return offset
}

private fun Byte.toUnsignedInt(): Int = toInt() and 0xFF

private fun Long.nextMpegTsPacketBoundary(): Long {
    val remainder = this % TS_PACKET_SIZE
    return if (remainder == 0L) this else this + TS_PACKET_SIZE - remainder
}

private const val TS_PACKET_SIZE = 188
private const val TS_PACKET_CHECK_COUNT = 3
private const val TS_SYNC_BYTE = 0x47
private const val TS_PAT_PID = 0
private const val TS_NULL_PACKET_PID = 0x1FFF
private const val TS_TABLE_ID_PAT = 0x00
private const val TS_TABLE_ID_PMT = 0x02
private const val TS_PID_SCAN_PACKET_COUNT = 512
private const val CRC32_MPEG_POLYNOMIAL = 0x04C11DB7
private const val CRC32_MPEG_MSB_MASK = 0x80000000.toInt()
