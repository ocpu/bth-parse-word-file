package me.ocpu.converter

import javafx.application.Application
import javafx.application.Platform
import javafx.geometry.Pos
import javafx.scene.control.ContentDisplay
import javafx.scene.control.ProgressBar
import javafx.stage.DirectoryChooser
import tornadofx.*
import java.io.File
import java.util.*
import kotlin.concurrent.thread


fun main(args: Array<String>) {
  Application.launch(ConverterApp::class.java)
}

class ConverterApp : App(Converter::class, Styles::class)

class Converter : View("File Importer") {
  override val root = gridpane {
    prefWidth = 400.0
    prefHeight = 100.0
    alignment = Pos.CENTER
    vbox(spacing = 5) {
      var progress: ProgressBar? = null
      hbox {
        val location = textfield()
        val fileDialogBtn = button("Open") {
          action {
            val dirChooser = DirectoryChooser()
            val dir = dirChooser.showDialog(currentWindow)
            location.text = dir.absolutePath
          }
        }
        fileDialogBtn.isFocused
        button("Start") {
          action {
            if (location.text.isBlank()) return@action
            val progressBar = progress!!
            val filePool = LinkedList(File(location.text).listFiles().filter { it.name.endsWith(".docx") })
            val part = 100.0 / filePool.size
            progressBar.progress = -1.0
            for (i in 0 until (Math.ceil(filePool.size / 10.0).toInt())) thread {
              while (filePool.isNotEmpty()) {
                convertAndInsert(synchronized(filePool) { filePool.removeFirst() }, filePool)
                Platform.runLater {
                  if (progressBar.progress < 0.0)
                    progressBar.progress = part
                  else progressBar.progress += part
                }
              }
            }
          }
        }
      }
      progress = progressbar {
        setProgress(0.0)
        prefWidth = 275.0
      }
    }
  }
}

class Styles : Stylesheet() {
  companion object {
    val main by cssclass("main")
  }

  init {
    s(main) {
      contentDisplay = ContentDisplay.CENTER
      fillWidth = true
    }
  }
}
