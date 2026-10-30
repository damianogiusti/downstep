package com.damianogiusti.slowdowner.desktop.update

import java.awt.GraphicsEnvironment
import java.util.Locale
import javax.swing.JOptionPane
import javax.swing.SwingUtilities
import javax.swing.Timer

/**
 * Said by a copy opened by hand while an update is put in place, just before it quits. It closes by
 * itself: a copy that stays open, even on a dialog, keeps locking the files the update moves.
 * Plain Swing: the app is not started, and the strings are the only two of the app outside Compose.
 */
internal object UpdatingNotice {
    private const val VISIBLE_MS = 3_000

    fun show() {
        if (GraphicsEnvironment.isHeadless()) return
        val italian = Locale.getDefault(Locale.Category.DISPLAY).language == "it"
        val message = if (italian) {
            "Downstep sta finendo l'aggiornamento e si riaprirà da solo. Se non succede entro qualche minuto, aprilo di nuovo."
        } else {
            "Downstep is finishing its update and will open by itself. If it does not within a few minutes, open it again."
        }
        runCatching {
            SwingUtilities.invokeAndWait {
                val dialog = JOptionPane(message, JOptionPane.INFORMATION_MESSAGE).createDialog("Downstep")
                Timer(VISIBLE_MS) { dialog.dispose() }.apply { isRepeats = false }.start()
                // Modal: returns when the timer or the user closes it.
                dialog.isVisible = true
            }
        }
    }
}
