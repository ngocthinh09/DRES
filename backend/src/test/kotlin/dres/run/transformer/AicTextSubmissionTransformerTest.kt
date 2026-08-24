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

    @Test fun `converts zero based TRAKE frame IDs to millisecond temporal points`() {
        val transformed = AicTextSubmissionTransformer(
            "task", "collection", AicTextSubmissionTransformer.Mode.TRAKE,
            mapOf("L22_V004" to AicTextSubmissionTransformer.VideoTiming(30f, 1_304_266L))
        ).transform(submission("TR-L22_V004-0,30,45"))
        assertEquals(listOf(0L, 1000L, 1500L), transformed.answerSets.single().answers.map { it.start })
        assertEquals(listOf("L22_V004", "L22_V004", "L22_V004"), transformed.answerSets.single().answers.map { it.mediaItemName })
    }

    @Test fun `rejects malformed AIC answer`() {
        val transformer = AicTextSubmissionTransformer("task", "collection", AicTextSubmissionTransformer.Mode.TRAKE)
        assertThrows(SubmissionRejectedException::class.java) { transformer.transform(submission("TR-video-12")) }
    }

    @Test fun `rejects TRAKE video without valid timing metadata`() {
        val transformer = AicTextSubmissionTransformer(
            "task", "collection", AicTextSubmissionTransformer.Mode.TRAKE,
            mapOf("L22_V004" to AicTextSubmissionTransformer.VideoTiming(null, 1_000L))
        )
        assertThrows(SubmissionRejectedException::class.java) { transformer.transform(submission("TR-L22_V004-30")) }
    }

    @Test fun `rejects TRAKE frame outside video duration`() {
        val transformer = AicTextSubmissionTransformer(
            "task", "collection", AicTextSubmissionTransformer.Mode.TRAKE,
            mapOf("L22_V004" to AicTextSubmissionTransformer.VideoTiming(30f, 1_000L))
        )
        assertThrows(SubmissionRejectedException::class.java) { transformer.transform(submission("TR-L22_V004-31")) }
    }
}
