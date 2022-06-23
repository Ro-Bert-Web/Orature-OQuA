/**
 * Copyright (C) 2020-2022 Wycliffe Associates
 *
 * This file is part of Orature.
 *
 * Orature is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Orature is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Orature.  If not, see <https://www.gnu.org/licenses/>.
 */
package org.wycliffeassociates.otter.jvm.markerapp.app.viewmodel

import com.github.thomasnield.rxkotlinfx.observeOnFx
import com.sun.glass.ui.Screen
import io.reactivex.Completable
import io.reactivex.Observable
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.subjects.PublishSubject
import javafx.beans.property.*
import javafx.beans.value.ChangeListener
import javafx.scene.control.Slider
import javafx.scene.image.Image
import javafx.scene.paint.Color
import org.slf4j.LoggerFactory
import org.wycliffeassociates.otter.common.audio.AudioFile
import org.wycliffeassociates.otter.common.device.IAudioPlayer
import org.wycliffeassociates.otter.jvm.controls.controllers.AudioPlayerController
import org.wycliffeassociates.otter.jvm.controls.controllers.ScrollSpeed
import org.wycliffeassociates.otter.jvm.controls.waveform.WaveformImageBuilder
import org.wycliffeassociates.otter.jvm.controls.model.VerseMarkerModel
import org.wycliffeassociates.otter.jvm.device.audio.AudioConnectionFactory
import org.wycliffeassociates.otter.jvm.utils.onChangeAndDoNow
import org.wycliffeassociates.otter.jvm.workbookplugin.plugin.ParameterizedScope
import tornadofx.*
import java.io.File
import java.lang.Integer.min
import org.wycliffeassociates.otter.jvm.controls.model.SECONDS_ON_SCREEN
import kotlin.math.max
import org.wycliffeassociates.otter.jvm.controls.model.ChunkMarkerModel

private const val WAV_COLOR = "#0A337390"
private const val BACKGROUND_COLOR = "#FFFFFF"

class VerseMarkerViewModel : ViewModel() {

    private val logger = LoggerFactory.getLogger(VerseMarkerViewModel::class.java)

    private val width = Screen.getMainScreen().platformWidth
    private val height = min(Screen.getMainScreen().platformHeight, 500)

    val waveformMinimapImage = SimpleObjectProperty<Image>()
    /** Call this before leaving the view to avoid memory leak */
    var imageCleanup: () -> Unit = {}

    private val waveformSubject = PublishSubject.create<Image>()
    val waveform: Observable<Image>
        get() = waveformSubject

    lateinit var waveformMinimapImageListener: ChangeListener<Image>

    var markerStateProperty = SimpleObjectProperty<VerseMarkerModel>()
    val currentMarkerNumberProperty = SimpleIntegerProperty(0)

    lateinit var markerModel: VerseMarkerModel
    val markers = observableListOf<ChunkMarkerModel>()
    val markerCountProperty = markers.sizeProperty

    var audioController: AudioPlayerController? = null

    val audioPlayer = SimpleObjectProperty<IAudioPlayer>()

    val isLoadingProperty = SimpleBooleanProperty(false)
    val isPlayingProperty = SimpleBooleanProperty(false)
    val markerRatioProperty = SimpleStringProperty()
    val headerTitle = SimpleStringProperty()
    val headerSubtitle = SimpleStringProperty()
    val compositeDisposable = CompositeDisposable()
    val positionProperty = SimpleDoubleProperty(0.0)
    var imageWidthProperty = SimpleDoubleProperty()

    private var sampleRate: Int = 0 // beware of divided by 0
    private var totalFrames: Int = 0 // beware of divided by 0
    private var resumeAfterScroll = false

    fun onDock() {
        isLoadingProperty.set(true)
        val audio = loadAudio()
        loadMarkers(audio)
        loadTitles()
        createWaveformImages(audio)
    }

    private fun loadAudio(): AudioFile {
        val scope = scope as ParameterizedScope
        val player = (scope.workspace.params["audioConnectionFactory"] as AudioConnectionFactory).getPlayer()
        val audioFile = File(scope.parameters.named["wav"])
        val audio = AudioFile(audioFile)
        player.load(audioFile)
        player.getAudioReader()?.let {
            sampleRate = it.sampleRate
            totalFrames = it.totalFrames
        }
        audioPlayer.set(player)
        return audio
    }

    private fun loadMarkers(audio: AudioFile) {
        val initialMarkerCount = audio.metadata.getCues().size
        scope as ParameterizedScope
        val totalMarkers: Int = scope.parameters.named["marker_total"]?.toInt() ?: initialMarkerCount
        markerModel = VerseMarkerModel(audio, totalMarkers)
        markerCountProperty.onChangeAndDoNow {
            markerRatioProperty.set("$it/$totalMarkers")
        }
        markers.setAll(markerModel.markers)
    }

    private fun loadTitles() {
        scope as ParameterizedScope
        headerTitle.set(scope.parameters.named["action_title"])
        headerSubtitle.set(scope.parameters.named["content_title"])
    }

    private fun writeMarkers(): Completable {
        audioPlayer.get()?.pause()
        audioPlayer.get()?.close()
        return markerModel.writeMarkers()
    }

    fun calculatePosition() {
        audioPlayer.get()?.let { audioPlayer ->
            val current = audioPlayer.getLocationInFrames()
            val duration = audioPlayer.getDurationInFrames().toDouble()
            val percentPlayed = current / duration
            val pos = percentPlayed * imageWidthProperty.value
            positionProperty.set(pos)
            updateCurrentPlaybackMarker(current)
        }
    }

    fun saveAndQuit() {
        compositeDisposable.clear()
        waveformMinimapImage.set(null)
        currentMarkerNumberProperty.set(-1)
        imageCleanup()

        (scope as ParameterizedScope).let {
            writeMarkers()
                .doOnError { e ->
                    logger.error("Error in closing the maker app", e)
                }
                .subscribe {
                    runLater {
                        it.navigateBack()
                        System.gc()
                    }
                }
        }
    }

    fun placeMarker() {
        markerModel.addMarker(audioPlayer.get().getLocationInFrames())
        markers.setAll(markerModel.markers)
    }

    fun seekNext() {
        val wasPlaying = audioPlayer.get().isPlaying()
        if (wasPlaying) {
            audioController?.toggle()
        }
        seek(markerModel.seekNext(audioPlayer.get().getLocationInFrames()))
        if (wasPlaying) {
            audioController?.toggle()
        }
    }

    fun seekPrevious() {
        val wasPlaying = audioPlayer.get().isPlaying()
        if (wasPlaying) {
            audioController?.toggle()
        }
        seek(markerModel.seekPrevious(audioPlayer.get().getLocationInFrames()))
        if (wasPlaying) {
            audioController?.toggle()
        }
    }

    fun initializeAudioController(slider: Slider) {
        audioController = AudioPlayerController(slider)
        audioController?.load(audioPlayer.get())
        isPlayingProperty.bind(audioController!!.isPlayingProperty)
    }

    fun pause() {
        audioController?.pause()
    }

    private fun isPlaying(): Boolean {
        return audioController?.isPlayingProperty?.value ?: false
    }

    fun rewind(speed: ScrollSpeed) {
        if (isPlaying()) {
            resumeAfterScroll = true
            mediaToggle()
        }
        audioController?.rewind(speed)
    }

    fun fastForward(speed: ScrollSpeed) {
        if (isPlaying()) {
            resumeAfterScroll = true
            mediaToggle()
        }
        audioController?.fastForward(speed)
    }

    fun resumeMedia() {
        if (resumeAfterScroll) {
            mediaToggle()
            resumeAfterScroll = false
        }
    }

    fun mediaToggle() {
        if (audioController?.isPlayingProperty?.value == false) {
            /* trigger change to auto-scroll when it starts playing */
            val currentMarkerIndex = currentMarkerNumberProperty.value
            currentMarkerNumberProperty.set(-1)
            currentMarkerNumberProperty.set(currentMarkerIndex)
        }
        audioController?.toggle()
    }

    fun seek(location: Int) {
        audioController?.seek(location)
        updateCurrentPlaybackMarker(location)
    }

    private fun createWaveformImages(audio: AudioFile) {
        imageWidthProperty.set(computeImageWidth(SECONDS_ON_SCREEN))

        val builder = WaveformImageBuilder(
            wavColor = Color.web(WAV_COLOR),
            background = Color.web(BACKGROUND_COLOR)
        )

        builder
            .build(
                audio.reader(),
                width = imageWidthProperty.value.toInt(),
                height = 50
            )
            .observeOnFx()
            .map { image ->
                waveformMinimapImage.set(image)
            }
            .ignoreElement()
            .andThen(
                builder.buildWaveformAsync(
                    audio.reader(),
                    width = imageWidthProperty.value.toInt(),
                    height = height,
                    waveformSubject
                )
            ).subscribe {
                runLater {
                    isLoadingProperty.set(false)
                }
            }
    }

    private fun computeImageWidth(secondsOnScreen: Int): Double {
        if (sampleRate == 0) {
            return 0.0
        }

        val samplesPerScreenWidth = sampleRate * secondsOnScreen
        val samplesPerPixel = samplesPerScreenWidth / width
        val pixelsInDuration = audioPlayer.get().getDurationInFrames() / samplesPerPixel
        return pixelsInDuration.toDouble()
    }

    fun getLocationInFrames(): Int {
        return audioPlayer.get().getLocationInFrames() ?: 0
    }

    fun getDurationInFrames(): Int {
        return audioPlayer.get().getDurationInFrames() ?: 0
    }

    fun pixelsInHighlight(controlWidth: Double): Double {
        if (sampleRate == 0 || totalFrames == 0) {
            return 1.0
        }

        val framesInHighlight = sampleRate * SECONDS_ON_SCREEN
        val framesPerPixel = totalFrames / max(controlWidth, 1.0)
        return max(framesInHighlight / framesPerPixel, 1.0)
    }

    private fun updateCurrentPlaybackMarker(currentFrame: Int) {
        val currentMarkerFrame = markerModel.seekCurrent(currentFrame)
        val currentMarker = markers.find { it.frame == currentMarkerFrame }
        val index = currentMarker?.let { markers.indexOf(it) } ?: 0
        currentMarkerNumberProperty.set(index)
    }

    fun requestAudioLocation(): Int {
        return audioPlayer.value?.getLocationInFrames() ?: 0
    }

    fun undoMarker() {
        markerModel.undo()
        markers.setAll(markerModel.markers)
    }

    fun redoMarker() {
        markerModel.redo()
        markers.setAll(markerModel.markers)
    }
}
