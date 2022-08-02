package org.wycliffeassociates.otter.jvm.workbookapp.oqua

import javafx.beans.property.SimpleObjectProperty
import org.wycliffeassociates.otter.common.audio.AudioFile
import org.wycliffeassociates.otter.common.data.workbook.Workbook
import org.wycliffeassociates.otter.common.device.IAudioPlayer
import org.wycliffeassociates.otter.jvm.controls.model.VerseMarkerModel
import org.wycliffeassociates.otter.jvm.workbookapp.di.IDependencyGraphProvider
import org.wycliffeassociates.otter.jvm.workbookapp.ui.viewmodel.SettingsViewModel
import org.wycliffeassociates.otter.jvm.workbookapp.ui.viewmodel.WorkbookDataStore
import tornadofx.*
import java.io.FileNotFoundException
import javax.inject.Inject

class ChapterViewModel : ViewModel() {
    private val wbDataStore: WorkbookDataStore by inject()
@Inject
    lateinit var draftReviewRepo: DraftReviewRepository

    val settingsViewModel: SettingsViewModel by inject()

    lateinit var verseMarkerModel: VerseMarkerModel

    val questions = observableListOf<Question>()
    val audioPlayerProperty = SimpleObjectProperty<IAudioPlayer>(null)

    lateinit var workbook: Workbook
    var chapterNumber = 0

    init {
        (app as IDependencyGraphProvider).dependencyGraph.inject(this)
    }

    fun dock() {
        workbook = wbDataStore.workbook
        chapterNumber = wbDataStore.chapter.sort

        loadChapterAudio()
        loadQuestions()
    }

    fun undock() {
        closeChapterAudio()
        saveDraftReview()
    }

    private fun loadChapterAudio() {
        wbDataStore
            .chapter
            .audio
            .selected
            .value
            ?.value
            ?.let { take ->
                val audioPlayer = (app as IDependencyGraphProvider).dependencyGraph.injectPlayer()
                audioPlayer.load(take.file)
                audioPlayerProperty.set(audioPlayer)

                val numberOfChunks = wbDataStore.chapter.chunks.count().blockingGet().toInt()

                verseMarkerModel = VerseMarkerModel(AudioFile(take.file), numberOfChunks)
            }
    }

    private fun loadQuestions() {
        val newQuestions = loadQuestionsResource()
        try {
            draftReviewRepo
                .readDraftReviewFile(workbook, chapterNumber)
                .draftReviews
                .let { draftReviews ->
                    loadDraftReviewIntoQuestions(newQuestions, draftReviews)
                }
        } catch (_: FileNotFoundException) {
            /**
             * Nothing needs to be done with the error
             * Because it could just be a new chapter
             * that hasn't been graded yet.
             */
        }

        questions.setAll(newQuestions)
    }

    private fun loadQuestionsResource(): List<Question> {
        return wbDataStore
            .getSourceChapter()
            .blockingGet()
            ?.let { chapter ->
                questionsDedup(chapter
                    .chunks
                    .toList()
                    .blockingGet()
                    .flatMap { chunk ->
                        Question.getQuestionsFromChunk(chunk)
                    }
                ).toList()
            } ?: listOf()
    }

    private fun loadDraftReviewIntoQuestions(questions: List<Question>, draftReviews: List<QuestionDraftReview>) {
        questions.forEach { question ->
            draftReviews.find { loadedReview ->
                (question.question == loadedReview.question)
                        && (question.answer == loadedReview.answer)
            }?.run {
                question.result = this.result
            }
        }
    }

    private fun closeChapterAudio() {
        audioPlayerProperty.value.close()
    }

    private fun saveDraftReview() {
        draftReviewRepo.writeDraftReviewFile(workbook, chapterNumber, questions)
    }

    fun jumpToVerse(verse: Int) {
        if (verse > 0 && verse <= verseMarkerModel.markers.size) {
            val frame = getVerseFrame(verse)
            audioPlayerProperty.value.seek(frame)
            audioPlayerProperty.value.play()
        }
    }

    private fun getVerseFrame(verse: Int): Int {
        return verseMarkerModel.markers[verse - 1].frame
    }
}