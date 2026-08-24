package dev.dres.run.transformer

import dev.dres.api.rest.types.evaluation.submission.ApiClientAnswer
import dev.dres.api.rest.types.evaluation.submission.ApiClientAnswerSet
import dev.dres.api.rest.types.evaluation.submission.ApiClientSubmission
import dev.dres.data.model.media.time.TemporalPoint
import dev.dres.data.model.run.TaskId
import dev.dres.run.filter.SubmissionRejectedException
import dev.dres.run.transformer.basics.SubmissionTransformer

/** Parses the compact HCMC AIC textual submission formats into DRES answers. */
class AicTextSubmissionTransformer(
    private val taskId: TaskId,
    private val collectionName: String,
    private val mode: Mode,
    private val videoTimings: Map<String, VideoTiming> = emptyMap()
) : SubmissionTransformer {
    enum class Mode { QA, TRAKE }
    data class VideoTiming(val fps: Float?, val durationMs: Long?)

    override fun transform(submission: ApiClientSubmission): ApiClientSubmission = submission.copy(
        answerSets = submission.answerSets.map { answerSet ->
            require(answerSet.taskId == taskId) { "Submission contains an answer set for another task." }
            val raw = answerSet.answers.singleOrNull()?.takeIf {
                it.text != null && it.mediaItemName == null && it.start == null && it.end == null
            }?.text ?: throw SubmissionRejectedException(submission, "Expected exactly one textual AIC answer.")
            answerSet.copy(answers = when (mode) {
                Mode.QA -> listOf(parseQa(submission, raw))
                Mode.TRAKE -> parseTrake(submission, raw)
            })
        }
    )

    private fun parseQa(submission: ApiClientSubmission, raw: String): ApiClientAnswer {
        val match = QA.matchEntire(raw)
            ?: throw SubmissionRejectedException(submission, "Invalid QA format. Expected QA-<ANSWER>-<VIDEO_ID>-<TIME_MS>.")
        return ApiClientAnswer(
            text = match.groupValues[1],
            mediaItemName = match.groupValues[2],
            mediaItemCollectionName = collectionName,
            start = match.groupValues[3].toLong(),
            end = match.groupValues[3].toLong()
        )
    }

    private fun parseTrake(submission: ApiClientSubmission, raw: String): List<ApiClientAnswer> {
        val match = TRAKE.matchEntire(raw)
            ?: throw SubmissionRejectedException(submission, "Invalid TRAKE format. Expected TR-<VIDEO_ID>-<FRAME_ID_1>,<FRAME_ID_2>,... .")
        val videoId = match.groupValues[1]
        val timing = videoTimings[videoId]
            ?: throw SubmissionRejectedException(submission, "Unknown TRAKE video '$videoId' in collection '$collectionName'.")
        val fps = timing.fps?.takeIf { it.isFinite() && it > 0f }
            ?: throw SubmissionRejectedException(submission, "TRAKE video '$videoId' has no valid FPS metadata.")
        val durationMs = timing.durationMs?.takeIf { it >= 0L }
            ?: throw SubmissionRejectedException(submission, "TRAKE video '$videoId' has no valid duration metadata.")
        return match.groupValues[2].split(',').map { value ->
            val frame = value.toLongOrNull()?.takeIf { it <= Int.MAX_VALUE }
                ?: throw SubmissionRejectedException(submission, "TRAKE frame ID '$value' is outside DRES's supported range.")
            val timestamp = TemporalPoint.Frame.toMilliseconds(frame.toInt(), fps)
            if (timestamp > durationMs) {
                throw SubmissionRejectedException(submission, "TRAKE frame ID '$value' is outside video '$videoId'.")
            }
            ApiClientAnswer(
                text = raw,
                mediaItemName = videoId,
                mediaItemCollectionName = collectionName,
                start = timestamp,
                end = timestamp
            )
        }
    }

    private companion object {
        val QA = Regex("^QA-(.+)-([A-Za-z0-9]+_[A-Za-z0-9_]+)-(\\d+)$")
        val TRAKE = Regex("^TR-([A-Za-z0-9]+_[A-Za-z0-9_]+)-(\\d+(?:,\\d+)*)$")
    }
}
