package com.damianogiusti.slowdowner.desktop.windows

import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.Structure
import com.sun.jna.platform.win32.WinDef.HWND
import com.sun.jna.platform.win32.WinDef.LPARAM
import com.sun.jna.platform.win32.WinDef.LRESULT
import com.sun.jna.platform.win32.WinDef.POINT
import com.sun.jna.platform.win32.WinDef.RECT
import com.sun.jna.platform.win32.WinDef.WPARAM
import com.sun.jna.platform.win32.WinUser.MONITORINFO
import com.sun.jna.platform.win32.WinUser.WNDENUMPROC
import com.sun.jna.win32.StdCallLibrary

/** The few user32 calls the title bar needs. 64-bit Windows only, as the app is. */
@Suppress("FunctionName")
internal interface User32Api : StdCallLibrary {
    fun SetWindowLongPtrW(hwnd: HWND, index: Int, newLong: Pointer?): Pointer?
    fun GetWindowLongPtrW(hwnd: HWND, index: Int): Pointer?
    fun CallWindowProcW(previous: Pointer, hwnd: HWND, message: Int, wParam: WPARAM, lParam: LPARAM): LRESULT
    fun DefWindowProcW(hwnd: HWND, message: Int, wParam: WPARAM, lParam: LPARAM): LRESULT
    fun SetWindowPos(hwnd: HWND, insertAfter: HWND?, x: Int, y: Int, cx: Int, cy: Int, flags: Int): Boolean
    fun GetWindowRect(hwnd: HWND, rect: RECT): Boolean
    fun GetClientRect(hwnd: HWND, rect: RECT): Boolean
    fun ScreenToClient(hwnd: HWND, point: POINT): Boolean
    fun IsZoomed(hwnd: HWND): Boolean
    fun IsIconic(hwnd: HWND): Boolean
    fun GetDpiForWindow(hwnd: HWND): Int
    fun GetSystemMetrics(index: Int): Int
    fun GetSystemMetricsForDpi(index: Int, dpi: Int): Int
    fun MonitorFromWindow(hwnd: HWND, flags: Int): Pointer?
    fun MonitorFromRect(rect: RECT, flags: Int): Pointer?
    fun GetCursorPos(point: POINT): Boolean
    fun SetTimer(hwnd: HWND, id: Pointer, elapse: Int, timerProc: Pointer?): Pointer?
    fun KillTimer(hwnd: HWND, id: Pointer): Boolean
    fun GetMonitorInfoW(monitor: Pointer, info: MONITORINFO): Boolean
    fun TrackMouseEvent(event: TRACKMOUSEEVENT): Boolean
    fun PostMessageW(hwnd: HWND, message: Int, wParam: WPARAM, lParam: LPARAM): Boolean
    fun SendMessageW(hwnd: HWND, message: Int, wParam: WPARAM, lParam: LPARAM): LRESULT
    fun EnumChildWindows(parent: HWND, callback: WNDENUMPROC, data: Pointer?): Boolean
    fun GetParent(hwnd: HWND): HWND?
    fun GetWindowLongW(hwnd: HWND, index: Int): Int
    fun SetWindowLongW(hwnd: HWND, index: Int, value: Int): Int
    fun GetSystemMenu(hwnd: HWND, revert: Boolean): Pointer?
    fun EnableMenuItem(menu: Pointer, item: Int, enable: Int): Int
    fun SetMenuDefaultItem(menu: Pointer, item: Int, byPosition: Int): Boolean
    fun TrackPopupMenu(menu: Pointer, flags: Int, x: Int, y: Int, reserved: Int, hwnd: HWND, rect: Pointer?): Int

    companion object {
        val INSTANCE: User32Api by lazy { Native.load("user32", User32Api::class.java) }
    }
}

@Suppress("FunctionName")
internal interface DwmApi : StdCallLibrary {
    fun DwmExtendFrameIntoClientArea(hwnd: HWND, margins: MARGINS): Int
    fun DwmSetWindowAttribute(hwnd: HWND, attribute: Int, value: Pointer, size: Int): Int

    companion object {
        val INSTANCE: DwmApi by lazy { Native.load("dwmapi", DwmApi::class.java) }
    }
}

@Suppress("FunctionName")
internal interface Shell32Api : StdCallLibrary {
    fun SHAppBarMessage(message: Int, data: APPBARDATA): Pointer?

    companion object {
        val INSTANCE: Shell32Api by lazy { Native.load("shell32", Shell32Api::class.java) }
    }
}

@Suppress("PropertyName")
@Structure.FieldOrder("cxLeftWidth", "cxRightWidth", "cyTopHeight", "cyBottomHeight")
internal class MARGINS(left: Int = 0, right: Int = 0, top: Int = 0, bottom: Int = 0) : Structure() {
    @JvmField var cxLeftWidth = left
    @JvmField var cxRightWidth = right
    @JvmField var cyTopHeight = top
    @JvmField var cyBottomHeight = bottom
}

@Structure.FieldOrder("cbSize", "dwFlags", "hwndTrack", "dwHoverTime")
internal class TRACKMOUSEEVENT : Structure() {
    @JvmField var cbSize = 0
    @JvmField var dwFlags = 0
    @JvmField var hwndTrack: HWND? = null
    @JvmField var dwHoverTime = 0

    init {
        cbSize = size()
    }
}

@Structure.FieldOrder("cbSize", "hWnd", "uCallbackMessage", "uEdge", "rc", "lParam")
internal class APPBARDATA : Structure() {
    @JvmField var cbSize = 0
    @JvmField var hWnd: HWND? = null
    @JvmField var uCallbackMessage = 0
    @JvmField var uEdge = 0
    @JvmField var rc = RECT()
    @JvmField var lParam: LPARAM = LPARAM(0)

    init {
        cbSize = size()
    }
}

/** Messages, styles and flags (winuser.h, dwmapi.h, shellapi.h). */
internal object Win32 {
    const val GWLP_WNDPROC = -4
    const val GWL_STYLE = -16
    const val GWL_EXSTYLE = -20

    const val WM_SIZE = 0x0005
    const val WM_ACTIVATE = 0x0006
    const val WM_PARENTNOTIFY = 0x0210
    const val WM_CREATE = 0x0001
    const val WM_NCDESTROY = 0x0082
    const val WM_NCCALCSIZE = 0x0083
    const val WM_NCHITTEST = 0x0084
    const val WM_NCMOUSEMOVE = 0x00A0
    const val WM_NCLBUTTONDOWN = 0x00A1
    const val WM_NCLBUTTONUP = 0x00A2
    const val WM_NCLBUTTONDBLCLK = 0x00A3
    const val WM_NCRBUTTONUP = 0x00A5
    const val WM_NCMOUSELEAVE = 0x02A2
    const val WM_MOUSEMOVE = 0x0200
    const val WM_SYSCOMMAND = 0x0112
    const val WM_DPICHANGED = 0x02E0
    const val WM_WINDOWPOSCHANGING = 0x0046
    const val WM_DISPLAYCHANGE = 0x007E
    const val WM_TIMER = 0x0113
    const val WM_APP = 0x8000

    const val SC_SIZE = 0xF000
    const val SC_MOVE = 0xF010
    const val SC_MINIMIZE = 0xF020
    const val SC_MAXIMIZE = 0xF030
    const val SC_CLOSE = 0xF060
    const val SC_RESTORE = 0xF120

    const val WS_CAPTION = 0x00C00000
    const val WS_THICKFRAME = 0x00040000
    const val WS_EX_DLGMODALFRAME = 0x00000001
    const val WS_EX_WINDOWEDGE = 0x00000100
    const val WS_EX_CLIENTEDGE = 0x00000200
    const val WS_EX_STATICEDGE = 0x00020000

    const val SWP_NOSIZE = 0x0001
    const val SWP_NOMOVE = 0x0002
    const val SWP_NOZORDER = 0x0004
    const val SWP_NOACTIVATE = 0x0010
    const val SWP_FRAMECHANGED = 0x0020
    const val SWP_NOOWNERZORDER = 0x0200

    const val SM_CXFRAME = 32
    const val SM_CYFRAME = 33
    const val SM_CXPADDEDBORDER = 92

    const val MONITOR_DEFAULTTONULL = 0
    const val MONITOR_DEFAULTTONEAREST = 2

    const val MF_BYCOMMAND = 0x0000
    const val MF_ENABLED = 0x0000
    const val MF_GRAYED = 0x0001
    const val TPM_RIGHTBUTTON = 0x0002
    const val TPM_RETURNCMD = 0x0100

    const val TME_LEAVE = 0x00000002
    const val TME_NONCLIENT = 0x00000010

    const val ABM_GETSTATE = 0x00000004
    const val ABM_GETAUTOHIDEBAREX = 0x0000000b
    const val ABS_AUTOHIDE = 0x0000001
    const val ABE_LEFT = 0
    const val ABE_TOP = 1
    const val ABE_RIGHT = 2
    const val ABE_BOTTOM = 3

    const val DWMWA_WINDOW_CORNER_PREFERENCE = 33
    const val DWMWCP_DEFAULT = 0
    const val DWMWCP_DONOTROUND = 1
}
