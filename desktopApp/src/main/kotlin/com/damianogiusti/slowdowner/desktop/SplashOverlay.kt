package com.damianogiusti.slowdowner.desktop

import com.damianogiusti.slowdowner.ui.resources.Res
import com.damianogiusti.slowdowner.ui.resources.inter_regular
import com.damianogiusti.slowdowner.ui.resources.splash_line_1
import com.damianogiusti.slowdowner.ui.resources.splash_line_10
import com.damianogiusti.slowdowner.ui.resources.splash_line_2
import com.damianogiusti.slowdowner.ui.resources.splash_line_3
import com.damianogiusti.slowdowner.ui.resources.splash_line_4
import com.damianogiusti.slowdowner.ui.resources.splash_line_5
import com.damianogiusti.slowdowner.ui.resources.splash_line_6
import com.damianogiusti.slowdowner.ui.resources.splash_line_7
import com.damianogiusti.slowdowner.ui.resources.splash_line_8
import com.damianogiusti.slowdowner.ui.resources.splash_line_9
import kotlinx.coroutines.runBlocking
import org.jetbrains.compose.resources.getFontResourceBytes
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.getSystemResourceEnvironment
import java.awt.AlphaComposite
import java.awt.Color
import java.awt.Font
import java.awt.Graphics2D
import java.awt.HeadlessException
import java.awt.RenderingHints
import java.awt.SplashScreen
import kotlin.concurrent.thread
import kotlin.random.Random

/** The launcher's splash (desktopApp/splash), or null when there is none: a dev run, a headless JVM. */
internal fun launcherSplash(): SplashScreen? = try {
    SplashScreen.getSplashScreen()
} catch (_: HeadlessException) {
    null
} catch (_: UnsupportedOperationException) {
    // A launcher or a platform without splash support.
    null
}

/**
 * Draws the version and a line that changes every few seconds over the launcher's splash, until the
 * window replaces it. On its own thread: main() goes on starting the app meanwhile.
 *
 * The picture comes from the launcher before the JVM starts; these texts come once it runs, a moment
 * later. They need the app's strings and font, read from the Compose resources before any window, in
 * the same language the app then shows.
 */
internal object SplashOverlay {

    fun start(splash: SplashScreen, version: String) {
        thread(isDaemon = true, name = "splash-overlay") { show(splash, version) }
    }

    private fun show(splash: SplashScreen, version: String) {
        val texts = try {
            runBlocking { SplashTexts.load() }
        } catch (e: Exception) {
            // Nothing to log to yet (the log starts after the lock). The splash keeps its picture.
            System.err.println("No texts on the splash: $e")
            return
        }
        var index = Random.nextInt(texts.lines.size)
        try {
            while (true) {
                draw(splash, version, texts.lines[index], texts.font)
                Thread.sleep(LINE_MS)
                index = (index + 1) % texts.lines.size
            }
        } catch (_: IllegalStateException) {
            // The window has replaced the splash: SplashScreen refuses calls once it is closed.
        }
    }

    private fun draw(splash: SplashScreen, version: String, line: String, font: Font) {
        val width = splash.size.width
        val height = splash.size.height
        val graphics = splash.createGraphics()
        try {
            // The overlay stays over every frame of the animation: clear the previous line first.
            graphics.composite = AlphaComposite.Clear
            graphics.fillRect(0, 0, width, height)
            graphics.setPaintMode()
            graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
            graphics.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON)
            graphics.drawCentred(version, font.deriveFont(VERSION_SIZE), VERSION_COLOR, VERSION_Y, width)
            graphics.drawCentred(line, fitting(graphics, font.deriveFont(LINE_SIZE), line, width), LINE_COLOR, LINE_Y, width)
        } finally {
            graphics.dispose()
        }
        splash.update()
    }

    /** A smaller size for a line too long for the splash (a longer translation). */
    private fun fitting(graphics: Graphics2D, font: Font, text: String, width: Int): Font {
        val available = width - 2 * SIDE_MARGIN
        val measured = graphics.getFontMetrics(font).stringWidth(text)
        return if (measured <= available) font else font.deriveFont(font.size2D * available / measured)
    }

    private fun Graphics2D.drawCentred(text: String, font: Font, colour: Color, centreY: Int, width: Int) {
        this.font = font
        color = colour
        val metrics = getFontMetrics(font)
        val baseline = centreY + (metrics.ascent - metrics.descent) / 2
        drawString(text, (width - metrics.stringWidth(text)) / 2, baseline)
    }

    // Long enough to read a line, short enough that a slow start visibly moves on: the app is not stuck.
    private const val LINE_MS = 5_000L

    // In splash points, as desktopApp/icons/make-splash.py draws the picture: name centred at 134,
    // spinner at 222. The version sits under the name, the line further down, above the spinner.
    private const val VERSION_Y = 156
    private const val LINE_Y = 192
    private const val SIDE_MARGIN = 16
    private const val VERSION_SIZE = 12f
    private const val LINE_SIZE = 13f

    // onSurfaceVariant of the dark theme (shared-ui/.../ui/theme/Color.kt): quieter than the name.
    private val VERSION_COLOR = Color(0xA4AAB4)
    private val LINE_COLOR = Color(0xA4AAB4)
}

/** What the overlay writes, in the user's language: the lines in a fixed order, and the app's font. */
internal class SplashTexts(val lines: List<String>, val font: Font) {
    companion object {
        private val LINES = listOf(
            Res.string.splash_line_1, Res.string.splash_line_2, Res.string.splash_line_3, Res.string.splash_line_4,
            Res.string.splash_line_5, Res.string.splash_line_6, Res.string.splash_line_7, Res.string.splash_line_8,
            Res.string.splash_line_9, Res.string.splash_line_10,
        )

        suspend fun load(): SplashTexts {
            val environment = getSystemResourceEnvironment()
            val font = Font.createFont(Font.TRUETYPE_FONT, getFontResourceBytes(environment, Res.font.inter_regular).inputStream())
            return SplashTexts(LINES.map { getString(environment, it) }, font)
        }
    }
}
