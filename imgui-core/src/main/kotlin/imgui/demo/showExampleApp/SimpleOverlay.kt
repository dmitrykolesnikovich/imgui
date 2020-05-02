package imgui.demo.showExampleApp

import gli_.has
import glm_.vec2.Vec2
import imgui.Cond
import imgui.ImGui.io
import imgui.ImGui.isMousePosValid
import imgui.ImGui.mainViewport
import imgui.ImGui.menuItem
import imgui.ImGui.separator
import imgui.ImGui.setNextWindowBgAlpha
import imgui.ImGui.setNextWindowPos
import imgui.ImGui.setNextWindowViewport
import imgui.ImGui.text
import imgui.dsl.menuItem
import imgui.dsl.popupContextWindow
import imgui.dsl.window
import imgui.or
import kotlin.reflect.KMutableProperty0
import imgui.WindowFlag as Wf

object SimpleOverlay {

    var corner = 0

    /** Demonstrate creating a simple static window with no decoration + a context-menu to choose which corner
     *  of the screen to use */
    operator fun invoke(open: KMutableProperty0<Boolean>) {

        // FIXME-VIEWPORT: Select a default viewport
        val DISTANCE = 10f

        if (corner != -1) {
            val viewport = mainViewport
            val workAreaPos = viewport.workPos   // Instead of using viewport->Pos we use GetWorkPos() to avoid menu bars, if any!
            val workAreaSize = viewport.workSize
            val windowPos = Vec2(workAreaPos.x + if (corner has 1) workAreaSize.x - DISTANCE else DISTANCE, workAreaPos.y + if (corner has 2) workAreaSize.y - DISTANCE else DISTANCE)
            val windowPosPivot = Vec2(if (corner has 1) 1f else 0f, if (corner has 2) 1f else 0f)
            setNextWindowPos(windowPos, Cond.Always, windowPosPivot)
            setNextWindowViewport(viewport.id)
        }
        setNextWindowBgAlpha(0.35f)  // Transparent background
        val flags = Wf.NoDocking or Wf.NoTitleBar or Wf.NoResize or Wf.AlwaysAutoResize or Wf.NoSavedSettings or Wf.NoFocusOnAppearing or Wf.NoNav
        window("Example: Simple overlay", open, (if (corner != -1) Wf.NoMove else Wf.None) or flags) {
            text("Simple overlay\nin the corner of the screen.\n(right-click to change position)")
            separator()
            text("Mouse Position: " + when {
                isMousePosValid() -> "(%.1f,%.1f)".format(io.mousePos.x, io.mousePos.y)
                else -> "<invalid>"
            })
            popupContextWindow {
                menuItem("Custom", "", corner == -1) { corner = -1 }
                menuItem("Top-left", "", corner == 0) { corner = 0 }
                menuItem("Top-right", "", corner == 1) { corner = 1 }
                menuItem("Bottom-left", "", corner == 2) { corner = 2 }
                menuItem("Bottom-right", "", corner == 3) { corner = 3 }
                if (open() && menuItem("Close")) open.set(false)
            }
        }
    }
}