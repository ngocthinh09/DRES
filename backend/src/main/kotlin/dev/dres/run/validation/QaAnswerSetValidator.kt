package dev.dres.run.validation

import dev.dres.data.model.media.MediaItemId
import dev.dres.data.model.submissions.DbAnswerSet
import dev.dres.data.model.submissions.DbAnswerType
import dev.dres.data.model.submissions.DbVerdictStatus
import dev.dres.run.validation.interfaces.AnswerSetValidator
import kotlinx.dnq.query.asSequence

/** Validates an AIC Q&A answer against its text, video and accepted temporal range. */
class QaAnswerSetValidator(
    private val answerPattern: Regex,
    private val itemId: MediaItemId,
    private val start: Long,
    private val end: Long
) : AnswerSetValidator {
    override val deferring = false

    override fun validate(answerSet: DbAnswerSet) {
        val answer = answerSet.answers.asSequence().singleOrNull()
        val correct = answer != null &&
            answer.type == DbAnswerType.TEMPORAL &&
            answer.item?.mediaItemId == itemId &&
            answer.start != null && answer.end != null && answer.start == answer.end &&
            answer.start!! in start..end &&
            answer.text != null && answerPattern.matches(answer.text!!)
        answerSet.status = if (correct) DbVerdictStatus.CORRECT else DbVerdictStatus.WRONG
    }
}
