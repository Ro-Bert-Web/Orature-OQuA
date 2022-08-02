package org.wycliffeassociates.otter.jvm.workbookapp.oqua

enum class ResultValue {
    CORRECT,
    INCORRECT,
    INVALID_QUESTION,
    UNANSWERED
}

data class QuestionResult(
    var result: ResultValue = ResultValue.UNANSWERED,
    var explanation: String = ""
)