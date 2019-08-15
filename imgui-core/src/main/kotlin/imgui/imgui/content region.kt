package imgui.imgui

import glm_.vec2.Vec2
import imgui.ImGui.contentRegionMaxAbs
import imgui.ImGui.currentWindowRead
import imgui.ImGui.getColumnOffset

/** Content region
 *  - Those functions are bound to be redesigned soon (they are confusing, incomplete and return values in local window coordinates which increases confusion) */
interface imgui_contentRegion {

    /** current content boundaries (typically window boundaries including scrolling, or current column boundaries), in
     *  windows coordinates
     *  FIXME: This is in window space (not screen space!). We should try to obsolete all those functions. */
    val contentRegionMax: Vec2
        /** ~GetContentRegionMax */
        get() = g.currentWindow!!.run {
            val mx = contentsRegionRect.max - pos
            dc.currentColumns?.let { mx.x = workRect.max.x - pos.x }
            mx
        }

    /** == GetContentRegionMax() - GetCursorPos()
     *
     *  ~GetContentRegionAvail  */
    val contentRegionAvail: Vec2
        get() = g.currentWindow!!.run { contentRegionMaxAbs - dc.cursorPos }

    /** content boundaries min (roughly (0,0)-Scroll), in window coordinates    */
    val windowContentRegionMin: Vec2
        get() = g.currentWindow!!.run { contentsRegionRect.min - pos }

    /** content boundaries max (roughly (0,0)+Size-Scroll) where Size can be override with SetNextWindowContentSize(),
     * in window coordinates    */
    val windowContentRegionMax: Vec2
        get() = g.currentWindow!!.run { contentsRegionRect.max - pos }

    val windowContentRegionWidth: Float
        get() = g.currentWindow!!.contentsRegionRect.width
}