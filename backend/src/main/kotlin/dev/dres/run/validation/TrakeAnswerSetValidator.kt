package dev.dres.run.validation

import dev.dres.data.model.media.MediaItemId
import dev.dres.data.model.submissions.DbAnswerSet
import dev.dres.data.model.submissions.DbAnswerType
import dev.dres.data.model.submissions.DbVerdictStatus
import dev.dres.run.validation.interfaces.AnswerSetValidator
import kotlinx.dnq.query.asSequence

/** Validates ordered TRAKE timestamps against manually authored inclusive ranges. */
class TrakeAnswerSetValidator(private val targets: List<RangeTarget>) : AnswerSetValidator {
    data class RangeTarget(val itemId: MediaItemId, val start: Long, val end: Long)
    override val deferring = false

    override fun validate(answerSet: DbAnswerSet) {
        val answers = answerSet.answers.asSequence().toList()
        if (targets.isEmpty() || answers.size != targets.size) {
            answerSet.status = DbVerdictStatus.WRONG
            return
        }
        val matched = answers.zip(targets).count { (answer, target) ->
            answer.type == DbAnswerType.TEMPORAL &&
                answer.item?.mediaItemId == target.itemId &&
                answer.start != null && answer.end == answer.start &&
                answer.start!! in target.start..target.end
        }
        answerSet.status = when {
            matched == targets.size -> DbVerdictStatus.CORRECT
            matched * 2 >= targets.size -> DbVerdictStatus.PARTIAL
            else -> DbVerdictStatus.WRONG
        }
    }
}
