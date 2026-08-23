package dres.run.transformer

import dev.dres.api.rest.types.evaluation.submission.ApiClientAnswer
import dev.dres.api.rest.types.evaluation.submission.ApiClientAnswerSet
import dev.dres.api.rest.types.evaluation.submission.ApiClientSubmission
import dev.dres.run.filter.SubmissionRejectedException
import dev.dres.run.transformer.AicTextSubmissionTransformer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class AicTextSubmissionTransformerTest {
    private fun submission(text: String) = ApiClientSubmission(listOf(ApiClientAnswerSet(taskId = "task", answers = listOf(ApiClientAnswer(text = text)))) )

    @Test fun `parses QA answer containing hyphens`() {
        val transformed = AicTextSubmissionTransformer("task", "collection", AicTextSubmissionTransformer.Mode.QA)
            .transform(submission("QA-light-blue-L22_V004-1234"))
        val answer = transformed.answerSets.single().answers.single()
        assertEquals("light-blue", answer.text)
        assertEquals("L22_V004", answer.mediaItemName)
        assertEquals(1234L, answer.start)
        assertEquals(1234L, answer.end)
    }

    @Test fun `parses TRAKE timestamps as temporal points`() {
        val transformed = AicTextSubmissionTransformer("task", "collection", AicTextSubmissionTransformer.Mode.TRAKE)
            .transform(submission("TR-L22_V004-1200,5600,10200"))
        assertEquals(listOf(1200L, 5600L, 10200L), transformed.answerSets.single().answers.map { it.start })
        assertEquals(listOf("L22_V004", "L22_V004", "L22_V004"), transformed.answerSets.single().answers.map { it.mediaItemName })
    }

    @Test fun `rejects malformed AIC answer`() {
        val transformer = AicTextSubmissionTransformer("task", "collection", AicTextSubmissionTransformer.Mode.TRAKE)
        assertThrows(SubmissionRejectedException::class.java) { transformer.transform(submission("TR-video-12")) }
    }
}
