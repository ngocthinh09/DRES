package dev.dres.data.model.run

import dev.dres.data.model.template.task.DbTaskTemplate
import dev.dres.data.model.template.task.options.DbTargetOption
import dev.dres.data.model.submissions.DbSubmission
import dev.dres.run.validation.*
import dev.dres.run.validation.interfaces.AnswerSetValidator
import dev.dres.run.validation.judged.BasicJudgementValidator
import dev.dres.run.validation.judged.BasicVoteValidator
import dev.dres.run.validation.judged.ItemRange
import jetbrains.exodus.database.TransientEntityStore
import kotlinx.dnq.query.*

/**
 * An abstract [DbTask] implementation for interactive [DbTask], i.e. [DbTask]s that rely on human interaction, such as [DbSubmission]s
 *
 * @author Luca Rossetto & Ralph Gasser
 * @version 1.0.0
 */
abstract class AbstractInteractiveTask(store: TransientEntityStore, task: DbTask) : AbstractTask(store, task) {


    /** The total duration in seconds of this task. Usually determined by the [DbTaskTemplate] but can be adjusted! */
    abstract override var duration: Long?

    /** The [AnswerSetValidator] used to validate [DbSubmission]s. */
    final override val validator: AnswerSetValidator

    init {
        this.validator = this.store.transactional(true) {
            val template = task.template
            when (val targetOption = template.taskGroup.type.target) {
                DbTargetOption.MEDIA_ITEM -> MediaItemsAnswerSetValidator(template.targets.filter { it.item ne null }
                    .mapDistinct { it.item }.toSet())

                DbTargetOption.MEDIA_SEGMENT -> {
                    val target =
                        template.targets.filter { (it.item ne null) and (it.start ne null) and (it.end ne null) }
                            .asSequence().map { TransientMediaSegment(it.item!!, it.range!!) }.toSet()
                    TemporalContainmentAnswerSetValidator(target)
                }

                DbTargetOption.TEXT -> TextAnswerSetValidator(
                    template.targets.filter { it.text ne null }.asSequence().map { it.text!! }.toList()
                )

                DbTargetOption.TEXT_VIDEO_SEGMENT -> {
                    val target = template.targets
                        .filter { (it.type eq dev.dres.data.model.template.task.DbTargetType.TEXT_MEDIA_ITEM_TEMPORAL_RANGE) and (it.item ne null) and (it.start ne null) and (it.end ne null) and (it.text ne null) }
                        .singleOrNull()
                        ?: throw IllegalStateException("A TEXT_VIDEO_SEGMENT task requires exactly one composite target.")
                    QaAnswerSetValidator(
                        answerPattern = qaPattern(target.text!!),
                        itemId = target.item!!.mediaItemId,
                        start = target.start!!,
                        end = target.end!!
                    )
                }

                DbTargetOption.TRAKE -> {
                    val targets = template.targets
                        .filter { (it.type eq dev.dres.data.model.template.task.DbTargetType.MEDIA_ITEM_TEMPORAL_RANGE) and (it.item ne null) and (it.start ne null) and (it.end ne null) }
                        .asSequence().sortedBy { it.ordinal }.map {
                            TrakeAnswerSetValidator.RangeTarget(it.item!!.mediaItemId, it.start!!, it.end!!)
                        }.toList()
                    require(targets.isNotEmpty()) { "A TRAKE task requires at least one temporal target." }
                    require(targets.map { it.itemId }.distinct().size == 1) { "All TRAKE targets must belong to the same media item." }
                    TrakeAnswerSetValidator(targets)
                }

                DbTargetOption.JUDGEMENT -> {
                    val knownRanges =
                        template.targets.filter { (it.item ne null) and (it.start ne null) and (it.end ne null) }
                            .asSequence().map {
                                ItemRange(it.item?.name!!, it.start!!, it.end!!)
                            }.toSet()
                    BasicJudgementValidator(template.toApi(), this.store, template.taskGroup.type.toApi(), knownCorrectRanges = knownRanges)
                }

                DbTargetOption.VOTE -> {
                    val knownRanges =
                        template.targets.filter { (it.item ne null) and (it.start ne null) and (it.end ne null) }
                            .asSequence().map {
                                ItemRange(it.item?.name!!, it.start!!, it.end!!)
                            }.toSet()
                    val parameters =
                        template.taskGroup.type.configurations.filter { it.key eq targetOption.description }
                            .asSequence().associate { it.key to it.value }
                    BasicVoteValidator(template.toApi(), this.store, template.taskGroup.type.toApi(), knownCorrectRanges = knownRanges, parameters = parameters)
                }

                else -> throw IllegalStateException("The provided target option ${targetOption.description} is not supported by interactive tasks.")
            }
        }
    }

    /** Keeps the literal/regex answer convention of [TextAnswerSetValidator]. */
    private fun qaPattern(target: String): Regex = when {
        target.startsWith("\\") && target.endsWith("\\i") -> Regex(target.substring(1, target.length - 2), setOf(RegexOption.CANON_EQ, RegexOption.IGNORE_CASE))
        target.startsWith("\\") && target.endsWith("\\") -> Regex(target.substring(1, target.length - 1), RegexOption.CANON_EQ)
        else -> Regex(target, setOf(RegexOption.CANON_EQ, RegexOption.LITERAL))
    }

}
