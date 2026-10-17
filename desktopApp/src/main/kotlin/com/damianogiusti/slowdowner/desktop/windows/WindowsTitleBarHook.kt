package com.damianogiusti.slowdowner.desktop.windows

import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.unit.IntRect
import com.damianogiusti.slowdowner.logging.Logger
import com.sun.jna.Memory
import com.sun.jna.Pointer
import com.sun.jna.platform.win32.WinDef.HWND
import com.sun.jna.platform.win32.WinDef.LPARAM
import com.sun.jna.platform.win32.WinDef.LRESULT
import com.sun.jna.platform.win32.WinDef.POINT
import com.sun.jna.platform.win32.WinDef.RECT
import com.sun.jna.platform.win32.WinDef.WPARAM
import com.sun.jna.platform.win32.WinUser.MONITORINFO
import com.sun.jna.platform.win32.WinUser.WNDENUMPROC
import com.sun.jna.platform.win32.WinUser.WindowProc
import java.awt.EventQueue
import java.util.concurrent.ConcurrentHashMap

/**
 * Windows only: the window keeps its native frame (shadow, rounded corners, snap, animations, the
 * invisible resize borders) but loses its caption, so the app's header takes its place. A subclass
 * of the window procedure (and of the procedures of its child windows: Compose draws in one):
 * - WM_NCCALCSIZE gives the caption to the client area ([captionlessClientArea]);
 * - WM_NCHITTEST answers caption, maximize button, top edge or client for the header's parts that
 *   Compose reported ([regions], [titleBarHitTest]); a child window answers "transparent" where the
 *   window should answer, so Windows asks the window;
 * - the maximize button's hover and press, which Windows owns (snap layouts), come from the
 *   non-client mouse messages and go to [listener] on the AWT event thread;
 * - fullscreen ([enterFullscreen]) is a borderless window over the whole monitor, which Windows does
 *   not minimize when another window takes the focus, as AWT's exclusive full-screen mode is.
 *
 * The procedures run on AWT's toolkit thread. They read immutable snapshots only ([regions],
 * [isFullscreen]), never Compose, and post what Compose must know to the event thread.
 */
/** What the window buttons and the header need from the window whose title bar they replace. */
interface CaptionlessWindow {
    /** What the window buttons need from the window, called on the AWT event thread. */
    interface Listener {
        fun onMaximizeButtonHover(hovered: Boolean)

        fun onMaximizeButtonPressed(pressed: Boolean)

        /** The OS tried to maximize or restore the window while it was fullscreen (Win+Up, the system menu). */
        fun onLeaveFullscreenRequested()
    }

    /** The header's parts, as the hit test reads them: replaced whole on every layout change. */
    var regions: TitleBarRegions

    var listener: Listener?

    fun minimize()

    fun toggleMaximized()

    fun close()
}

class WindowsTitleBarHook private constructor(
    private val hwnd: HWND,
    private val logger: Logger,
) : CaptionlessWindow {

    /** The header's parts, replaced whole on every layout change. */
    @Volatile
    override var regions: TitleBarRegions = TitleBarRegions.NONE

    /** In [enterFullscreen]'s borderless fullscreen. Written on the event thread, read by the procedures. */
    @Volatile
    var isFullscreen: Boolean = false
        private set

    @Volatile
    override var listener: CaptionlessWindow.Listener? = null

    private val user32 = User32Api.INSTANCE

    // Strong references: the native side holds only their addresses, and a collected callback crashes.
    private val windowProc = WindowProc { h, message, wParam, lParam -> onWindowMessage(h, message, wParam, lParam) }
    private val childProc = WindowProc { h, message, wParam, lParam -> onChildMessage(h, message, wParam, lParam) }
    private var previousProc: Pointer? = null
    private val childPrevious = ConcurrentHashMap<Pointer, Pointer>()

    /** The child window Compose draws in: its client pixels are Compose's window pixels. */
    @Volatile
    private var content: HWND? = null

    // Toolkit thread only.
    private var maxHovered = false
    private var maxPressed = false
    private var trackingLeave = false

    private var saved: SavedWindow? = null

    private fun install() {
        val previous = user32.SetWindowLongPtrW(hwnd, Win32.GWLP_WNDPROC, procPointer(windowProc))
            ?: error("SetWindowLongPtr failed")
        previousProc = previous
        subclassChildren()
        extendFrame()
        // Windows asks for the client area again: from now on without the caption.
        user32.SetWindowPos(
            hwnd, null, 0, 0, 0, 0,
            Win32.SWP_FRAMECHANGED or Win32.SWP_NOMOVE or Win32.SWP_NOSIZE or Win32.SWP_NOZORDER or Win32.SWP_NOACTIVATE or Win32.SWP_NOOWNERZORDER,
        )
    }

    private fun subclassChildren() {
        user32.EnumChildWindows(hwnd, WNDENUMPROC { child, _ ->
            subclassChild(child)
            true
        }, null)
    }

    private fun subclassChild(child: HWND) {
        val key = child.pointer
        if (childPrevious.containsKey(key)) return
        // Only the window's own children: a grandchild answers to its parent, which is one of them.
        if (user32.GetParent(child)?.pointer != hwnd.pointer) return
        val previous = user32.SetWindowLongPtrW(child, Win32.GWLP_WNDPROC, procPointer(childProc)) ?: return
        childPrevious[key] = previous
        val current = content
        if (current == null || area(child) > area(current)) content = child
    }

    private fun area(window: HWND): Long {
        val rect = RECT()
        if (!user32.GetClientRect(window, rect)) return 0
        return (rect.right - rect.left).toLong() * (rect.bottom - rect.top)
    }

    /** No frame drawn into the client area: the shadow and the borders stay those of the frame. */
    private fun extendFrame() {
        runCatching { DwmApi.INSTANCE.DwmExtendFrameIntoClientArea(hwnd, MARGINS(0, 0, 0, 0)) }
    }

    private fun onWindowMessage(h: HWND, message: Int, wParam: WPARAM, lParam: LPARAM): LRESULT {
        try {
            when (message) {
                Win32.WM_NCCALCSIZE -> if (wParam.toLong() != 0L) return onCalcSize(h, wParam, lParam)
                Win32.WM_NCHITTEST -> {
                    val standard = callPrevious(h, message, wParam, lParam)
                    // The side and bottom borders, and anything else that is not the client area, stay Windows'.
                    if (standard.toInt() != HitCode.CLIENT) return standard
                    return LRESULT(hitTest(lParam).toLong())
                }
                Win32.WM_NCMOUSEMOVE -> {
                    trackNonClientLeave()
                    setMaxHovered(wParam.toInt() == HitCode.MAXBUTTON)
                    if (wParam.toInt() != HitCode.MAXBUTTON) setMaxPressed(false)
                }
                Win32.WM_NCMOUSELEAVE -> {
                    trackingLeave = false
                    setMaxHovered(false)
                    setMaxPressed(false)
                }
                Win32.WM_MOUSEMOVE -> {
                    setMaxHovered(false)
                    setMaxPressed(false)
                }
                // Windows would track the press itself and draw its old caption button over the header.
                Win32.WM_NCLBUTTONDOWN, Win32.WM_NCLBUTTONDBLCLK -> if (wParam.toInt() == HitCode.MAXBUTTON) {
                    setMaxPressed(true)
                    return LRESULT(0)
                }
                Win32.WM_NCLBUTTONUP -> if (wParam.toInt() == HitCode.MAXBUTTON) {
                    if (maxPressed) toggleMaximized()
                    setMaxPressed(false)
                    return LRESULT(0)
                } else {
                    setMaxPressed(false)
                }
                // Windows' own handling does not reach the system menu here: shown as a right-click on a caption shows it.
                Win32.WM_NCRBUTTONUP -> if (wParam.toInt() == HitCode.CAPTION) {
                    showSystemMenu(lParam.toInt().toShort().toInt(), (lParam.toInt() shr 16).toShort().toInt())
                    return LRESULT(0)
                }
                Win32.WM_SYSCOMMAND -> if (isFullscreen) {
                    val command = wParam.toInt() and 0xFFF0
                    if (command == Win32.SC_MAXIMIZE || (command == Win32.SC_RESTORE && !user32.IsIconic(h))) {
                        listener?.let { EventQueue.invokeLater(it::onLeaveFullscreenRequested) }
                        return LRESULT(0)
                    }
                }
                Win32.WM_PARENTNOTIFY -> if (wParam.toInt() and 0xFFFF == Win32.WM_CREATE) {
                    subclassChild(HWND(Pointer(lParam.toLong())))
                }
                Win32.WM_DPICHANGED -> {
                    val result = callPrevious(h, message, wParam, lParam)
                    extendFrame()
                    return result
                }
            }
        } catch (e: Throwable) {
            // Never out of a window procedure: the JVM would crash. Windows' own answer instead.
            logOnce(e)
        }
        return callPrevious(h, message, wParam, lParam)
    }

    private fun onChildMessage(h: HWND, message: Int, wParam: WPARAM, lParam: LPARAM): LRESULT {
        val previous = childPrevious[h.pointer] ?: return user32.DefWindowProcW(h, message, wParam, lParam)
        try {
            when (message) {
                // Where the window answers something else than client (the header, the top edge, the
                // maximize button), the child steps aside so that Windows asks the window.
                Win32.WM_NCHITTEST -> if (hitTest(lParam) != HitCode.CLIENT) return LRESULT(HitCode.TRANSPARENT.toLong())
                Win32.WM_MOUSEMOVE -> {
                    setMaxHovered(false)
                    setMaxPressed(false)
                }
                Win32.WM_NCDESTROY -> {
                    childPrevious.remove(h.pointer)
                    if (content?.pointer == h.pointer) content = null
                    user32.SetWindowLongPtrW(h, Win32.GWLP_WNDPROC, previous)
                    return user32.CallWindowProcW(previous, h, message, wParam, lParam)
                }
            }
        } catch (e: Throwable) {
            logOnce(e)
        }
        return user32.CallWindowProcW(previous, h, message, wParam, lParam)
    }

    private fun onCalcSize(h: HWND, wParam: WPARAM, lParam: LPARAM): LRESULT {
        val params = Pointer(lParam.toLong())
        val window = params.readRect()
        val result = callPrevious(h, Win32.WM_NCCALCSIZE, wParam, lParam)
        val standard = params.readRect()
        val maximized = user32.IsZoomed(h)
        val area = captionlessClientArea(
            window = window,
            standard = standard,
            isMaximized = maximized,
            isFullscreen = isFullscreen,
            maximizedTopInset = resizeBorder(h),
            autoHideEdges = if (maximized && !isFullscreen) autoHideTaskbarEdges(h) else emptySet(),
        )
        params.writeRect(area)
        return result
    }

    /** The hit code for the screen point in [lParam]: see [titleBarHitTest]. */
    private fun hitTest(lParam: LPARAM): Int {
        val x = lParam.toInt().toShort().toInt()
        val y = (lParam.toInt() shr 16).toShort().toInt()
        val inWindow = POINT(x, y)
        if (!user32.ScreenToClient(hwnd, inWindow)) return HitCode.CLIENT
        // Compose's pixels are those of the child it draws in; the window's client area starts at the window's top.
        val inContent = content?.let { child -> POINT(x, y).takeIf { user32.ScreenToClient(child, it) } } ?: inWindow
        val client = RECT()
        user32.GetClientRect(hwnd, client)
        val state = TitleBarWindowState(
            isMaximized = user32.IsZoomed(hwnd),
            isFullscreen = isFullscreen,
            width = client.right - client.left,
            resizeBorder = resizeBorder(hwnd),
        )
        return titleBarHitTest(inContent.x, inContent.y, regions, state, fromTop = inWindow.y, fromLeft = inWindow.x)
    }

    /** The system's resize border at the window's DPI: the top edge's thickness, and a maximized window's overhang. */
    private fun resizeBorder(h: HWND): Int = try {
        val dpi = user32.GetDpiForWindow(h)
        user32.GetSystemMetricsForDpi(Win32.SM_CYFRAME, dpi) + user32.GetSystemMetricsForDpi(Win32.SM_CXPADDEDBORDER, dpi)
    } catch (_: UnsatisfiedLinkError) {
        // Before Windows 10 1607.
        user32.GetSystemMetrics(Win32.SM_CYFRAME) + user32.GetSystemMetrics(Win32.SM_CXPADDEDBORDER)
    }

    /** The edges of the window's monitor where an auto-hide taskbar waits. */
    private fun autoHideTaskbarEdges(h: HWND): Set<ScreenEdge> {
        val shell = Shell32Api.INSTANCE
        val state = shell.SHAppBarMessage(Win32.ABM_GETSTATE, APPBARDATA())?.let { Pointer.nativeValue(it) } ?: 0L
        if (state and Win32.ABS_AUTOHIDE.toLong() == 0L) return emptySet()
        val monitor = user32.MonitorFromWindow(h, Win32.MONITOR_DEFAULTTONEAREST) ?: return emptySet()
        val info = MONITORINFO()
        if (!user32.GetMonitorInfoW(monitor, info)) return emptySet()
        return EDGES.filter { (_, abe) ->
            val data = APPBARDATA().apply {
                uEdge = abe
                rc = info.rcMonitor
            }
            shell.SHAppBarMessage(Win32.ABM_GETAUTOHIDEBAREX, data) != null
        }.mapTo(mutableSetOf()) { it.first }
    }

    private fun trackNonClientLeave() {
        if (trackingLeave) return
        val event = TRACKMOUSEEVENT().apply {
            dwFlags = Win32.TME_LEAVE or Win32.TME_NONCLIENT
            hwndTrack = hwnd
        }
        trackingLeave = user32.TrackMouseEvent(event)
    }

    private fun setMaxHovered(hovered: Boolean) {
        if (maxHovered == hovered) return
        maxHovered = hovered
        listener?.let { EventQueue.invokeLater { it.onMaximizeButtonHover(hovered) } }
    }

    private fun setMaxPressed(pressed: Boolean) {
        if (maxPressed == pressed) return
        maxPressed = pressed
        listener?.let { EventQueue.invokeLater { it.onMaximizeButtonPressed(pressed) } }
    }

    /** The window's system menu at the screen point ([x], [y]), its items enabled as Windows' own are. */
    private fun showSystemMenu(x: Int, y: Int) {
        val menu = user32.GetSystemMenu(hwnd, false) ?: return
        val maximized = user32.IsZoomed(hwnd)
        fun enable(item: Int, enabled: Boolean) =
            user32.EnableMenuItem(menu, item, Win32.MF_BYCOMMAND or if (enabled) Win32.MF_ENABLED else Win32.MF_GRAYED)
        enable(Win32.SC_RESTORE, maximized)
        enable(Win32.SC_MOVE, !maximized)
        enable(Win32.SC_SIZE, !maximized)
        enable(Win32.SC_MINIMIZE, true)
        enable(Win32.SC_MAXIMIZE, !maximized)
        enable(Win32.SC_CLOSE, true)
        user32.SetMenuDefaultItem(menu, Win32.SC_CLOSE, 0)
        val command = user32.TrackPopupMenu(menu, Win32.TPM_RETURNCMD or Win32.TPM_RIGHTBUTTON, x, y, 0, hwnd, null)
        if (command != 0) user32.PostMessageW(hwnd, Win32.WM_SYSCOMMAND, WPARAM(command.toLong()), LPARAM(0))
    }

    /** Through the system commands, as the caption buttons do: Windows animates and AWT follows. */
    override fun toggleMaximized() {
        systemCommand(if (user32.IsZoomed(hwnd)) Win32.SC_RESTORE else Win32.SC_MAXIMIZE)
    }

    override fun minimize() = systemCommand(Win32.SC_MINIMIZE)

    /** As Alt+F4: AWT turns it into the window's closing event. */
    override fun close() = systemCommand(Win32.SC_CLOSE)

    private fun systemCommand(command: Int) {
        user32.PostMessageW(hwnd, Win32.WM_SYSCOMMAND, WPARAM(command.toLong()), LPARAM(0))
    }

    /**
     * Fullscreen as a browser's: the window without its frame, over its whole monitor (taskbar
     * included, which Windows hides for a window that covers the monitor). A maximized window is
     * restored first, and maximized again on [exitFullscreen]. AWT event thread only.
     */
    fun enterFullscreen() {
        if (isFullscreen) return
        val maximized = user32.IsZoomed(hwnd)
        // Still as a window: in fullscreen a restore is the OS asking to leave it.
        if (maximized) user32.SendMessageW(hwnd, Win32.WM_SYSCOMMAND, WPARAM(Win32.SC_RESTORE.toLong()), LPARAM(0))
        // Before the frame changes: the client area and the hit test change with it.
        isFullscreen = true
        val bounds = RECT().also { user32.GetWindowRect(hwnd, it) }
        val style = user32.GetWindowLongW(hwnd, Win32.GWL_STYLE)
        val exStyle = user32.GetWindowLongW(hwnd, Win32.GWL_EXSTYLE)
        saved = SavedWindow(maximized, style, exStyle, IntRect(bounds.left, bounds.top, bounds.right, bounds.bottom))
        user32.SetWindowLongW(hwnd, Win32.GWL_STYLE, style and (Win32.WS_CAPTION or Win32.WS_THICKFRAME).inv())
        user32.SetWindowLongW(
            hwnd, Win32.GWL_EXSTYLE,
            exStyle and (Win32.WS_EX_DLGMODALFRAME or Win32.WS_EX_WINDOWEDGE or Win32.WS_EX_CLIENTEDGE or Win32.WS_EX_STATICEDGE).inv(),
        )
        setCorners(Win32.DWMWCP_DONOTROUND)
        val monitor = monitorBounds() ?: IntRect(bounds.left, bounds.top, bounds.right, bounds.bottom)
        user32.SetWindowPos(
            hwnd, null, monitor.left, monitor.top, monitor.width, monitor.height,
            Win32.SWP_NOZORDER or Win32.SWP_NOACTIVATE or Win32.SWP_FRAMECHANGED or Win32.SWP_NOOWNERZORDER,
        )
    }

    /** Back to the window as it was before [enterFullscreen]: its frame, its bounds, maximized if it was. */
    fun exitFullscreen() {
        val window = saved ?: return
        saved = null
        user32.SetWindowLongW(hwnd, Win32.GWL_STYLE, window.style)
        user32.SetWindowLongW(hwnd, Win32.GWL_EXSTYLE, window.exStyle)
        setCorners(Win32.DWMWCP_DEFAULT)
        isFullscreen = false
        val bounds = window.bounds
        user32.SetWindowPos(
            hwnd, null, bounds.left, bounds.top, bounds.width, bounds.height,
            Win32.SWP_NOZORDER or Win32.SWP_NOACTIVATE or Win32.SWP_FRAMECHANGED or Win32.SWP_NOOWNERZORDER,
        )
        if (window.isMaximized) user32.SendMessageW(hwnd, Win32.WM_SYSCOMMAND, WPARAM(Win32.SC_MAXIMIZE.toLong()), LPARAM(0))
    }

    private fun monitorBounds(): IntRect? {
        val monitor = user32.MonitorFromWindow(hwnd, Win32.MONITOR_DEFAULTTONEAREST) ?: return null
        val info = MONITORINFO()
        if (!user32.GetMonitorInfoW(monitor, info)) return null
        val r = info.rcMonitor
        return IntRect(r.left, r.top, r.right, r.bottom)
    }

    /** Windows 11's rounded corners, off over a whole monitor. Nothing before Windows 11. */
    private fun setCorners(preference: Int) {
        runCatching {
            val value = Memory(4).apply { setInt(0, preference) }
            DwmApi.INSTANCE.DwmSetWindowAttribute(hwnd, Win32.DWMWA_WINDOW_CORNER_PREFERENCE, value, 4)
        }
    }

    private fun callPrevious(h: HWND, message: Int, wParam: WPARAM, lParam: LPARAM): LRESULT {
        val previous = previousProc ?: return user32.DefWindowProcW(h, message, wParam, lParam)
        return user32.CallWindowProcW(previous, h, message, wParam, lParam)
    }

    @Volatile
    private var logged = false

    private fun logOnce(e: Throwable) {
        if (logged) return
        logged = true
        logger.e("Windows title bar: a window message failed", e)
    }

    private data class SavedWindow(val isMaximized: Boolean, val style: Int, val exStyle: Int, val bounds: IntRect)

    companion object {
        private val EDGES = listOf(
            ScreenEdge.Left to Win32.ABE_LEFT,
            ScreenEdge.Top to Win32.ABE_TOP,
            ScreenEdge.Right to Win32.ABE_RIGHT,
            ScreenEdge.Bottom to Win32.ABE_BOTTOM,
        )

        /**
         * Replaces [window]'s title bar with the app's header. Null when it cannot (not Windows, no
         * native window, a failed call): the window then keeps its title bar, and the failure is logged.
         * AWT event thread only, before the window shows if possible (no title bar at all then).
         */
        fun install(window: ComposeWindow, logger: Logger): WindowsTitleBarHook? = runCatching {
            if (!window.isDisplayable) window.addNotify()
            val handle = window.windowHandle
            check(handle != 0L) { "no native window" }
            WindowsTitleBarHook(HWND(Pointer(handle)), logger).apply { install() }
        }.onFailure { logger.e("Windows title bar: cannot replace the title bar, the system one stays", it) }.getOrNull()

        private fun procPointer(proc: WindowProc): Pointer = com.sun.jna.CallbackReference.getFunctionPointer(proc)

        private fun Pointer.readRect() = IntRect(getInt(0), getInt(4), getInt(8), getInt(12))

        private fun Pointer.writeRect(rect: IntRect) {
            setInt(0, rect.left)
            setInt(4, rect.top)
            setInt(8, rect.right)
            setInt(12, rect.bottom)
        }
    }
}
