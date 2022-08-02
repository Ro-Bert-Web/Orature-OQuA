package org.wycliffeassociates.otter.jvm.workbookapp.oqua

import javafx.beans.binding.Bindings
import javafx.scene.control.ToggleButton
import javafx.scene.control.ToggleGroup
import tornadofx.*

class TQListCellFragment: ListCellFragment<Question>() {
    private val toggleGroup = ToggleGroup()

    private val questionProperty = Bindings.createStringBinding(
        { itemProperty.value?.question },
        itemProperty
    )
    private val answerProperty = Bindings.createStringBinding(
        { itemProperty.value?.answer },
        itemProperty
    )
    private val verseProperty = Bindings.createStringBinding(
        {
            itemProperty.value?.let { question ->
                getVerseLabel(question)
            }
        },
        itemProperty
    )

    lateinit var correctButton: ToggleButton
    lateinit var incorrectButton: ToggleButton
    lateinit var invalidButton: ToggleButton

    private fun getVerseLabel(question: Question): String {
        return if (question.start == question.end) {
            "Verse ${question.start}"
        } else {
            "Verses ${question.start} - ${question.end}"
        }
    }

    override val root = vbox {
        text(verseProperty)
        text(questionProperty) {
            addClass("oqua-question-text")
        }
        text(answerProperty)
        hbox {
            correctButton = togglebutton("Correct", toggleGroup) {
                action {
                    item.result.result = ResultValue.CORRECT
                }
            }
            incorrectButton = togglebutton("Incorrect", toggleGroup) {
                action {
                    item.result.result = ResultValue.INCORRECT
                }
            }
            invalidButton = togglebutton("Invalid Question", toggleGroup) {
                action {
                    item.result.result = ResultValue.INVALID_QUESTION
                }
            }

            itemProperty.onChange {
                when (it?.result?.result) {
                    ResultValue.CORRECT -> toggleGroup.selectToggle(correctButton)
                    ResultValue.INCORRECT -> toggleGroup.selectToggle(incorrectButton)
                    ResultValue.INVALID_QUESTION -> toggleGroup.selectToggle(invalidButton)
                    ResultValue.UNANSWERED -> toggleGroup.selectToggle(null)
                }
            }
        }
        textfield {
            visibleWhen(invalidButton.selectedProperty())
            managedWhen(visibleProperty())

            itemProperty.onChange {
                text = it?.result?.explanation
            }
            textProperty().onChange {
                item?.result?.explanation = it ?: ""
            }
        }
    }
}