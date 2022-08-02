package org.wycliffeassociates.otter.jvm.workbookapp.oqua

data class QuestionDraftReview (
    val question: String?,
    val answer: String?,
    val start: Int,
    val end: Int,
    val result: QuestionResult
) {
    companion object {
        fun mapFromQuestion(question: Question): QuestionDraftReview {
            return QuestionDraftReview(
                question.question,
                question.answer,
                question.start,
                question.end,
                question.result
            )
        }
    }
}