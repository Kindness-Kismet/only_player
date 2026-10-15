package one.only.player

internal class CloudThumbnailConcurrency(maximumConcurrency: Int) {

    var limit = minOf(3, maximumConcurrency)
        private set
    var generation = 0
        private set

    private var maximumConcurrency = maximumConcurrency
    private var direction = 1
    private var reference: Measurement? = null
    private var cooldownWindows = 0
    private val durationsMs = mutableListOf<Long>()

    fun updateCapacity(capacity: Int) {
        maximumConcurrency = capacity
        if (limit <= capacity) return
        changeLimit(capacity)
        reference = null
        cooldownWindows = 1
    }

    fun record(
        generation: Int,
        durationMs: Long,
        isSuccessful: Boolean,
        isSaturatedAtStart: Boolean,
    ) {
        if (generation != this.generation) return
        if (!isSuccessful) {
            changeLimit((limit / 2).coerceAtLeast(1))
            reference = null
            cooldownWindows = 2
            return
        }
        if (!isSaturatedAtStart) return

        durationsMs += durationMs.coerceAtLeast(1L)
        if (durationsMs.size < SAMPLE_WINDOW_SIZE) return
        val measurement = Measurement(limit, limit * 1_000.0 / durationsMs.average())
        durationsMs.clear()

        val previous = reference
        if (previous != null) {
            // 增加并发需要明确收益；减少并发只需保持接近的吞吐量。
            val requiredRatio = if (limit > previous.limit) 1.1 else 0.95
            if (measurement.throughput < previous.throughput * requiredRatio) {
                changeLimit(previous.limit.coerceAtMost(maximumConcurrency))
                direction = -direction
            }
            reference = null
            cooldownWindows = 2
            return
        }
        if (cooldownWindows > 0) {
            cooldownWindows--
            return
        }
        if (maximumConcurrency == 1) return

        if (limit + direction !in 1..maximumConcurrency) direction = -direction
        reference = measurement
        changeLimit(limit + direction)
    }

    private fun changeLimit(value: Int) {
        limit = value
        generation++
        durationsMs.clear()
    }

    private data class Measurement(
        val limit: Int,
        val throughput: Double,
    )

    private companion object {
        const val SAMPLE_WINDOW_SIZE = 4
    }
}
