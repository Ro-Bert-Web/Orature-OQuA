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
package org.wycliffeassociates.otter.jvm.workbookapp.ui.screens.dialogs

import com.github.thomasnield.rxkotlinfx.observeOnFx
import javafx.geometry.Pos
import javafx.scene.effect.BlurType
import javafx.scene.effect.DropShadow
import javafx.scene.paint.Color
import javafx.scene.paint.ImagePattern
import javafx.scene.shape.Rectangle
import org.wycliffeassociates.otter.jvm.workbookapp.ui.NavigationMediator
import org.wycliffeassociates.otter.jvm.workbookapp.ui.components.drawer.ThemeColorEvent
import org.wycliffeassociates.otter.jvm.workbookapp.ui.screens.HomePage
import org.wycliffeassociates.otter.jvm.workbookapp.ui.styles.SplashScreenStyles
import org.wycliffeassociates.otter.jvm.workbookapp.ui.viewmodel.SettingsViewModel
import org.wycliffeassociates.otter.jvm.workbookapp.ui.viewmodel.SplashScreenViewModel
import tornadofx.*

class SplashScreen : View() {
    private val viewModel: SplashScreenViewModel by inject()
    private val settingsViewModel: SettingsViewModel by inject()
    private val navigator: NavigationMediator by inject()

    override fun onDock() {
        super.onDock()
        root.scene.fill = Color.TRANSPARENT
    }

    override val root = stackpane {
        addStylesheet(SplashScreenStyles::class)
        addClass(SplashScreenStyles.splashRoot)
        alignment = Pos.TOP_CENTER

        prefWidth = 676.0
        prefHeight = 580.0

        // 576 x 480
        val rect = Rectangle(576.0, 480.0)
        rect.arcHeight = 30.0
        rect.arcWidth = 30.0

        rect.effect = DropShadow(BlurType.GAUSSIAN, Color.BLACK, 20.0, 0.0, 3.0, 3.0)
        val img = ImagePattern(resources.image("/orature_splash.png"))
        rect.fill = img
        add(rect)

        progressbar(viewModel.progressProperty) {
            addClass(SplashScreenStyles.splashProgress)
            prefWidth = 376.0
            translateY = 360.0
        }
    }

    init {
        viewModel
            .initApp()
            .subscribe(
                {},
                { finish() },
                { finish() }
            )
    }

    private fun finish() {
        viewModel.initAudioSystem()
        viewModel.theme.preferredTheme
            .observeOnFx()
            .doFinally {
                close()
                settingsViewModel.setAppOrientation()
                primaryStage.show()
                navigator.dock<HomePage>()
            }
            .observeOnFx()
            .subscribe { theme ->
                fire(ThemeColorEvent(this::class, theme))
            }
    }
}
