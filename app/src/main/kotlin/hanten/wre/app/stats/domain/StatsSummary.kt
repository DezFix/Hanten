package hanten.wre.app.stats.domain

/**
 * Beta: totals for the selected period, shown as a summary line
 * above the chart ("time · pages · per day").
 */
data class StatsSummary(
	val durationMs: Long,
	val pages: Int,
	val days: Int,
)
