package dev.dres.run.validation

import dev.dres.data.model.media.MediaItemId
import dev.dres.data.model.submissions.DbAnswerSet
import dev.dres.data.model.submissions.DbAnswerType
import dev.dres.data.model.submissions.DbVerdictStatus
import dev.dres.run.validation.interfaces.AnswerSetValidator
import kotlinx.dnq.query.asSequence

/** Validates an AIC Q&A answer against one of several video/range answer groups. */
class QaAnswerSetValidator(private val targets: List<Target>) : AnswerSetValidator {
    data class Target(
        val answerPatterns: List<Regex>,
        val itemId: MediaItemId,
        val start: Long,
        val end: Long
    )

    override val deferring = false

    override fun validate(answerSet: DbAnswerSet) {
        val answer = answerSet.answers.asSequence().singleOrNull()
        val correct = answer?.takeIf { it.type == DbAnswerType.TEMPORAL }?.let {
            matches(it.text, it.item?.mediaItemId, it.start, it.end)
        } ?: false
        answerSet.status = if (correct) DbVerdictStatus.CORRECT else DbVerdictStatus.WRONG
    }

    /**
     * Returns whether a temporal Q&A answer belongs to any configured query target.
     * The text check is deliberately scoped to the same target's item/range.
     */
    internal fun matches(text: String?, itemId: MediaItemId?, start: Long?, end: Long?): Boolean =
        text != null && itemId != null && start != null && end == start && targets.any { target ->
            itemId == target.itemId &&
                start in target.start..target.end &&
                target.answerPatterns.any { it.matches(text) }
        }
}
