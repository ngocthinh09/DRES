package dev.dres.run.score.scorer

import dev.dres.data.model.submissions.Submission
import dev.dres.data.model.submissions.VerdictStatus
import dev.dres.data.model.template.team.TeamId
import dev.dres.run.score.Scoreable
import jetbrains.exodus.database.TransientEntityStore
import kotlin.math.max

/** HCMC AIC TRAKE scorer with first-full-or-first-partial semantics. */
class TrakeTaskScorer(
    scoreable: Scoreable,
    private val maxPointsPerTask: Double,
    private val maxPointsAtTaskEnd: Double,
    private val penaltyPerWrongSubmission: Double,
    store: TransientEntityStore?
) : AbstractTaskScorer(scoreable, store) {
    constructor(run: Scoreable, parameters: Map<String, String>, store: TransientEntityStore?) : this(
        run,
        parameters["maxPointsPerTask"]?.toDoubleOrNull() ?: DEFAULT_MAX,
        parameters["maxPointsAtTaskEnd"]?.toDoubleOrNull()
            ?: (parameters["maxPointsPerTask"]?.toDoubleOrNull() ?: DEFAULT_MAX) / 2.0,
        parameters["penaltyPerWrongSubmission"]?.toDoubleOrNull()
            ?: (parameters["maxPointsPerTask"]?.toDoubleOrNull() ?: DEFAULT_MAX) / 10.0,
        store
    )

    override fun calculateScores(submissions: Sequence<Submission>): Map<TeamId, Double> {
        val taskStart = scoreable.started ?: throw IllegalArgumentException("No task start time specified.")
        val taskDuration = scoreable.duration?.toDouble()?.times(1000.0)
        return scoreable.teams.associateWith { teamId ->
            val verdicts = submissions.filter { it.teamId == teamId }.sortedBy { it.timestamp }
                .flatMap { it.answerSets().filter { answerSet ->
                    answerSet.status() in setOf(VerdictStatus.CORRECT, VerdictStatus.PARTIAL, VerdictStatus.WRONG)
                } }.toList()
            val full = verdicts.indexOfFirst { it.status() == VerdictStatus.CORRECT }
            val partial = verdicts.indexOfFirst { it.status() == VerdictStatus.PARTIAL }
            val event = when {
                full >= 0 -> full to false
                partial >= 0 -> partial to true
                else -> null
            }
            if (event == null) {
                0.0
            } else {
                val (index, isPartial) = event
                val timeFraction = taskDuration?.let { 1.0 - (verdicts[index].submission.timestamp - taskStart) / it } ?: 1.0
                val wrongBefore = verdicts.take(index).count { it.status() == VerdictStatus.WRONG }
                val score = max(0.0, maxPointsAtTaskEnd + (maxPointsPerTask - maxPointsAtTaskEnd) * timeFraction - wrongBefore * penaltyPerWrongSubmission)
                if (isPartial) score / 2.0 else score
            }
        }
    }

    private companion object { const val DEFAULT_MAX = 100.0 }
}
