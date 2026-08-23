package dres.run.score.scorer

import dev.dres.api.rest.types.evaluation.submission.ApiAnswerSet
import dev.dres.api.rest.types.evaluation.submission.ApiSubmission
import dev.dres.api.rest.types.evaluation.submission.ApiVerdictStatus
import dev.dres.data.model.run.interfaces.TaskId
import dev.dres.data.model.template.team.TeamId
import dev.dres.run.score.Scoreable
import dev.dres.run.score.scorer.TrakeTaskScorer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class TrakeTaskScorerTest {
    private val started = 1_000L
    private val team: TeamId = "team"
    private val scoreable = object : Scoreable {
        override val taskId: TaskId = "task"
        override val teams = listOf(team)
        override val duration = 300L
        override val started = this@TrakeTaskScorerTest.started
        override val ended: Long? = null
    }

    private fun submission(status: ApiVerdictStatus, timestamp: Long) = ApiSubmission(
        submissionId = "${status}-${timestamp}", teamId = team, memberId = "member", teamName = "team", memberName = "member", timestamp = timestamp,
        answers = listOf(ApiAnswerSet("answer-$timestamp", status, "task", emptyList()))
    )

    @Test fun `uses half score for first partial when no full result exists`() {
        val score = TrakeTaskScorer(scoreable, emptyMap(), null)
            .calculateScores(sequenceOf(submission(ApiVerdictStatus.PARTIAL, started)))
        assertEquals(50.0, score[team])
    }

    @Test fun `full result supersedes earlier partial without treating it as wrong`() {
        val score = TrakeTaskScorer(scoreable, emptyMap(), null).calculateScores(
            sequenceOf(submission(ApiVerdictStatus.PARTIAL, started), submission(ApiVerdictStatus.CORRECT, started + 150_000))
        )
        assertEquals(75.0, score[team])
    }

    @Test fun `counts only wrong attempts before chosen result`() {
        val score = TrakeTaskScorer(scoreable, emptyMap(), null).calculateScores(
            sequenceOf(submission(ApiVerdictStatus.WRONG, started), submission(ApiVerdictStatus.PARTIAL, started))
        )
        assertEquals(45.0, score[team])
    }
}
