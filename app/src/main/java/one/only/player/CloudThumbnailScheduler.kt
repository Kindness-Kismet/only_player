package one.only.player

import android.app.ActivityManager
import android.content.Context
import android.os.PowerManager
import android.os.SystemClock
import coil3.decode.DataSource
import coil3.request.ImageResult
import coil3.request.SuccessResult
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import one.only.player.core.common.Logger

internal class CloudThumbnailScheduler(context: Context) {

    private val activityManager = context.getSystemService(ActivityManager::class.java)
    private val powerManager = context.getSystemService(PowerManager::class.java)
    private val memoryInfo = ActivityManager.MemoryInfo()
    private val deviceLimit = minOf(
        Runtime.getRuntime().availableProcessors().coerceIn(2, 8),
        when {
            activityManager.isLowRamDevice || activityManager.memoryClass <= 128 -> 2
            activityManager.memoryClass <= 256 -> 4
            activityManager.memoryClass <= 384 -> 6
            else -> 8
        },
    )
    private val lock = Any()
    private val changes = MutableStateFlow(0L)
    private val servers = mutableMapOf<String, ServerState>()
    private var activeRequests = 0
    private var capacity = deviceLimit
    private var lastCapacityCheckMs = -CAPACITY_CHECK_INTERVAL_MS

    suspend fun load(
        serverKey: String,
        block: suspend () -> ImageResult,
    ): ImageResult {
        val permit = acquire(serverKey)
        val startedAtMs = SystemClock.elapsedRealtime()
        var outcome = LoadOutcome.IGNORED
        try {
            currentCoroutineContext().ensureActive()
            val result = block()
            currentCoroutineContext().ensureActive()
            outcome = when {
                result is SuccessResult && result.dataSource == DataSource.MEMORY_CACHE -> LoadOutcome.IGNORED
                result is SuccessResult -> LoadOutcome.SUCCESS
                else -> LoadOutcome.FAILURE
            }
            return result
        } finally {
            release(permit, SystemClock.elapsedRealtime() - startedAtMs, outcome)
        }
    }

    private suspend fun acquire(serverKey: String): Permit {
        while (true) {
            currentCoroutineContext().ensureActive()
            val observedChange = synchronized(lock) {
                updateCapacity()
                val server = servers.getOrPut(serverKey) { ServerState(CloudThumbnailConcurrency(capacity)) }
                server.concurrency.updateCapacity(capacity)
                if (activeRequests < capacity && server.activeRequests < server.concurrency.limit) {
                    activeRequests++
                    server.activeRequests++
                    return Permit(server, server.concurrency.generation, server.activeRequests == server.concurrency.limit)
                }
                changes.value
            }
            changes.first { it != observedChange }
        }
    }

    private fun release(
        permit: Permit,
        durationMs: Long,
        outcome: LoadOutcome,
    ) {
        val adjustment = synchronized(lock) {
            activeRequests--
            permit.server.activeRequests--
            updateCapacity()
            val concurrency = permit.server.concurrency
            val previousLimit = concurrency.limit
            concurrency.updateCapacity(capacity)
            if (outcome != LoadOutcome.IGNORED) {
                concurrency.record(permit.generation, durationMs, outcome == LoadOutcome.SUCCESS, permit.isSaturatedAtStart)
            }
            changes.value++
            if (concurrency.limit != previousLimit) "$previousLimit -> ${concurrency.limit}, capacity=$capacity" else null
        }
        if (BuildConfig.DEBUG && adjustment != null) {
            Logger.debug("CloudThumbnailScheduler", "concurrency $adjustment")
        }
    }

    // 只限制后续请求，不中断已开始的原生解码；所有服务器共用设备容量。
    private fun updateCapacity() {
        val now = SystemClock.elapsedRealtime()
        if (now - lastCapacityCheckMs < CAPACITY_CHECK_INTERVAL_MS) return
        lastCapacityCheckMs = now
        activityManager.getMemoryInfo(memoryInfo)
        capacity = when {
            memoryInfo.lowMemory -> 1
            powerManager.currentThermalStatus >= PowerManager.THERMAL_STATUS_SEVERE -> minOf(2, deviceLimit)
            else -> deviceLimit
        }
    }

    private class ServerState(val concurrency: CloudThumbnailConcurrency) {
        var activeRequests = 0
    }

    private data class Permit(
        val server: ServerState,
        val generation: Int,
        val isSaturatedAtStart: Boolean,
    )

    private enum class LoadOutcome {
        SUCCESS,
        FAILURE,
        IGNORED,
    }

    private companion object {
        const val CAPACITY_CHECK_INTERVAL_MS = 2_000L
    }
}
