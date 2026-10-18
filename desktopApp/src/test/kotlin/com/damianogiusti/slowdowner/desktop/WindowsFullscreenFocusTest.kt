package com.damianogiusti.slowdowner.desktop

import java.awt.Frame
import java.awt.GraphicsEnvironment
import java.awt.event.WindowAdapter
import java.awt.event.WindowListener
import javax.swing.JDialog
import javax.swing.JFrame
import javax.swing.SwingUtilities
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** A Windows fullscreen window that a file picker, or another app, does not minimize. */
class WindowsFullscreenFocusTest {

    private class MinimizeOnFocusLoss : WindowAdapter()

    @Test
    fun `only the minimize-on-focus-loss listeners are removed`() {
        if (GraphicsEnvironment.isHeadless()) return
        SwingUtilities.invokeAndWait {
            val frame = Frame()
            try {
                val other = object : WindowAdapter() {}
                frame.addWindowListener(MinimizeOnFocusLoss())
                frame.addWindowListener(other)
                val isHandler: (WindowListener) -> Boolean = { it is MinimizeOnFocusLoss }

                assertTrue(frame.removeFullscreenFocusLossHandling(isHandler))
                assertEquals(listOf<WindowListener>(other), frame.windowListeners.toList())
                assertFalse(frame.isAlwaysOnTop, "not left always on top")
                assertFalse(frame.removeFullscreenFocusLossHandling(isHandler), "nothing left to remove")
            } finally {
                frame.dispose()
            }
        }
    }

    /**
     * The real thing: a full-screen window on this screen, and a dialog it owns (as the import picker
     * is) taking the focus. Shows a window over the whole screen for a moment, so it runs only when
     * asked: ./gradlew :desktopApp:test -PfullscreenWindowTests=true, on Windows (and once more with
     * -Dsun.java2d.d3d=false in the test JVM for the GDI pipeline, where the JDK would minimize it).
     */
    @Test
    fun `a dialog the fullscreen window owns does not minimize it`() {
        if (!isWindows || GraphicsEnvironment.isHeadless() || System.getProperty("slowdowner.fullscreenWindowTests") != "true") return
        lateinit var frame: JFrame
        SwingUtilities.invokeAndWait {
            frame = JFrame("Downstep fullscreen test")
            frame.isVisible = true
            frame.graphicsConfiguration.device.fullScreenWindow = frame
        }
        try {
            // The JDK adds its listener with invokeLater: after this round trip it is there. Only the
            // GDI pipeline has one (sun.java2d.d3d=false, or no Direct3D); Direct3D's does not minimize.
            SwingUtilities.invokeAndWait {}
            SwingUtilities.invokeAndWait {
                val gdi = frame.windowListeners.any { it.javaClass.name.endsWith("Win32FSWindowAdapter") }
                assertEquals(gdi, frame.removeFullscreenFocusLossHandling())
            }
            lateinit var dialog: JDialog
            SwingUtilities.invokeAndWait {
                dialog = JDialog(frame, "Picker", false)
                dialog.setSize(300, 200)
                dialog.setLocationRelativeTo(frame)
                dialog.isVisible = true
                dialog.toFront()
                dialog.requestFocus()
            }
            Thread.sleep(800)
            SwingUtilities.invokeAndWait {
                assertNotEquals(Frame.ICONIFIED, frame.extendedState and Frame.ICONIFIED, "minimized by its own dialog")
                assertSame(frame, frame.graphicsConfiguration.device.fullScreenWindow)
                dialog.dispose()
            }
        } finally {
            SwingUtilities.invokeAndWait {
                frame.graphicsConfiguration.device.fullScreenWindow = null
                frame.dispose()
            }
        }
    }
}
