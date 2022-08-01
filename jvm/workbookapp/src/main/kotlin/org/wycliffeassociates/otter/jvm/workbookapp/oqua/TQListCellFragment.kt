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
                    item.review = "correct"
                }
            }
            incorrectButton = togglebutton("Incorrect", toggleGroup) {
                action {
                    item.review = "incorrect"
                }
            }
            invalidButton = togglebutton("Invalid", toggleGroup) {
                action {
                    item.review = "invalid"
                }
            }

            itemProperty.onChange {
                when (it?.review) {
                    "correct" -> toggleGroup.selectToggle(correctButton)
                    "incorrect" -> toggleGroup.selectToggle(incorrectButton)
                    "invalid" -> toggleGroup.selectToggle(invalidButton)
                    else -> toggleGroup.selectToggle(null)
                }
            }
        }
        textfield {
            visibleWhen(invalidButton.selectedProperty())
            managedWhen(visibleProperty())

            itemProperty.onChange {
                text = it?.explanation
            }
            textProperty().onChange {
                item?.explanation = it
            }
        }
    }
}