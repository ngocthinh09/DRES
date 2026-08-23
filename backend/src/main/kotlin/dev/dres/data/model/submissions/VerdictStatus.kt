package dev.dres.data.model.submissions

import dev.dres.api.rest.types.evaluation.submission.ApiVerdictStatus

enum class VerdictStatus {
        CORRECT, WRONG, INDETERMINATE, UNDECIDABLE, PARTIAL;

    fun toApi(): ApiVerdictStatus = when(this) {
        CORRECT -> ApiVerdictStatus.CORRECT
        WRONG -> ApiVerdictStatus.WRONG
        INDETERMINATE -> ApiVerdictStatus.INDETERMINATE
        UNDECIDABLE -> ApiVerdictStatus.UNDECIDABLE
        PARTIAL -> ApiVerdictStatus.PARTIAL
    }

    fun toDb(): DbVerdictStatus = when(this) {
        CORRECT -> DbVerdictStatus.CORRECT
        WRONG -> DbVerdictStatus.WRONG
        INDETERMINATE -> DbVerdictStatus.INDETERMINATE
        UNDECIDABLE -> DbVerdictStatus.UNDECIDABLE
        PARTIAL -> DbVerdictStatus.PARTIAL
    }

    companion object {

        fun fromApi(status: ApiVerdictStatus): VerdictStatus = when(status) {
            ApiVerdictStatus.CORRECT -> CORRECT
            ApiVerdictStatus.WRONG -> WRONG
            ApiVerdictStatus.INDETERMINATE -> INDETERMINATE
            ApiVerdictStatus.UNDECIDABLE -> UNDECIDABLE
            ApiVerdictStatus.PARTIAL -> PARTIAL
        }

        fun fromDb(status: DbVerdictStatus): VerdictStatus = when(status) {
            DbVerdictStatus.CORRECT -> CORRECT
            DbVerdictStatus.WRONG -> WRONG
            DbVerdictStatus.INDETERMINATE -> INDETERMINATE
            DbVerdictStatus.UNDECIDABLE -> UNDECIDABLE
            DbVerdictStatus.PARTIAL -> PARTIAL
            else -> throw IllegalStateException("Unknown DbVerdictStatus $status")
        }

    }

}
