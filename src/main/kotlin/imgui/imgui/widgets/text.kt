package imgui.imgui.widgets

import glm_.glm
import glm_.vec2.Vec2
import glm_.vec4.Vec4
import imgui.*
import imgui.ImGui.calcTextSize
import imgui.ImGui.currentWindow
import imgui.ImGui.itemAdd
import imgui.ImGui.itemSize
import imgui.ImGui.nextItemWidth
import imgui.ImGui.popStyleColor
import imgui.ImGui.popTextWrapPos
import imgui.ImGui.pushStyleColor
import imgui.ImGui.pushTextWrapPos
import imgui.ImGui.renderArrowPointingAt
import imgui.ImGui.renderBullet
import imgui.ImGui.renderText
import imgui.ImGui.renderTextClipped
import imgui.ImGui.style
import imgui.ImGui.textEx
import imgui.imgui.g
import imgui.internal.Rect
import imgui.internal.TextFlag
import imgui.ColorEditFlag as Cef

interface text {

    fun textUnformatted(text: String, textEnd: Int) = textEx(text, textEnd, TextFlag.NoWidthForLargeClippedText.i)

    fun text(fmt: String, vararg args: Any) = textV(fmt, args)

    fun textV(fmt_: String, args: Array<out Any>) {

        val window = currentWindow
        if (window.skipItems) return

        val fmt = if (args.isEmpty()) fmt_ else fmt_.format(style.locale, *args)

        val textEnd = fmt.length
        textEx(fmt, textEnd, TextFlag.NoWidthForLargeClippedText.i)
    }

    /** shortcut for PushStyleColor(ImGuiCol_Text, col); Text(fmt, ...); PopStyleColor();   */
    fun textColored(col: Vec4, fmt: String, vararg args: Any) {
        pushStyleColor(Col.Text, col)
        text(fmt, *args)
        popStyleColor()
    }

    /** shortcut for:
     *      pushStyleColor(Col.Text, style.colors[Col.TextDisabled])
     *      text(fmt, ...)
     *      popStyleColor() */
    fun textDisabled(fmt: String, vararg args: Any) = textDisabledV(fmt, args)

    fun textDisabledV(fmt: String, args: Array<out Any>) {
        pushStyleColor(Col.Text, style.colors[Col.TextDisabled])
        textV(fmt, args)
        popStyleColor()
    }

    /** shortcut for PushTextWrapPos(0.0f); Text(fmt, ...); PopTextWrapPos();. Note that this won't work on an
     *  auto-resizing window if there's no other widgets to extend the window width, yoy may need to set a size using
     *  SetNextWindowSize().    */
    fun textWrapped(fmt: String, vararg args: Any) {

        val needBackup = g.currentWindow!!.dc.textWrapPos < 0f  // Keep existing wrap position is one ia already set
        if (needBackup)
            pushTextWrapPos(0f)
        text(fmt, *args)
        if (needBackup)
            popTextWrapPos()
    }

    /** Display text+label aligned the same way as value+label widgets  */
    fun labelText(label: String, fmt: String, vararg args: Any) = labelTextV(label, fmt, args)

    /** Add a label+text combo aligned to other label+value widgets */
    fun labelTextV(label: String, fmt: String, args: Array<out Any>) {

        val window = currentWindow
        if (window.skipItems) return
        val w = nextItemWidth

        val labelSize = calcTextSize(label, 0, true)
        val valueBb = Rect(window.dc.cursorPos, window.dc.cursorPos + Vec2(w, labelSize.y + style.framePadding.y * 2))
        val totalBb = Rect(window.dc.cursorPos, window.dc.cursorPos + Vec2(w + if (labelSize.x > 0f) style.itemInnerSpacing.x else 0f, style.framePadding.y * 2) + labelSize)
        itemSize(totalBb, style.framePadding.y)
        if (!itemAdd(totalBb, 0)) return
        // Render
        val text = fmt.format(style.locale, *args)
        renderTextClipped(valueBb.min, valueBb.max, text, text.length, null, Vec2(0f, 0.5f))
        if (labelSize.x > 0f)
            renderText(Vec2(valueBb.max.x + style.itemInnerSpacing.x, valueBb.min.y + style.framePadding.y), label)
    }

    /** shortcut for Bullet()+Text()    */
    fun bulletText(fmt: String, vararg args: Any) = bulletTextV(fmt, args)

    /** Text with a little bullet aligned to the typical tree node. */
    fun bulletTextV(fmt: String, args: Array<out Any>) {

        val window = currentWindow
        if (window.skipItems) return

        val text = fmt.format(style.locale, *args)
        val labelSize = calcTextSize(text, false)
        val textBaseOffsetY = glm.max(0f, window.dc.currentLineTextBaseOffset) // Latch before ItemSize changes it
        val lineHeight = glm.max(glm.min(window.dc.currentLineSize.y, g.fontSize + style.framePadding.y * 2), g.fontSize)
        val x = g.fontSize + if (labelSize.x > 0f) labelSize.x + style.framePadding.x * 2 else 0f
        // Empty text doesn't add padding
        val bb = Rect(window.dc.cursorPos, window.dc.cursorPos + Vec2(x, glm.max(lineHeight, labelSize.y)))
        itemSize(bb)
        if (!itemAdd(bb, 0)) return

        // Render
        renderBullet(bb.min + Vec2(style.framePadding.x + g.fontSize * 0.5f, lineHeight * 0.5f))
        renderText(bb.min + Vec2(g.fontSize + style.framePadding.x * 2, textBaseOffsetY), text, text.length, false)
    }

    // TODO, lambdas are so short, consider removing it
    object Items {
        // FIXME-OPT: we could pre-compute the indices to fasten this. But only 1 active combo means the waste is limited.
        val singleStringGetter = { data: String, idx: Int -> data.split(NUL)[idx] }
        val arrayGetter = { data: Array<String>, idx: Int, outText: Array<String> -> outText[0] = data[idx]; true }
    }

    companion object {

        fun renderArrowsForVerticalBar(drawList: DrawList, pos: Vec2, halfSz: Vec2, barW: Float) {
            renderArrowPointingAt(drawList, Vec2(pos.x + halfSz.x + 1, pos.y), Vec2(halfSz.x + 2, halfSz.y + 1), Dir.Right, COL32_BLACK)
            renderArrowPointingAt(drawList, Vec2(pos.x + halfSz.x, pos.y), halfSz, Dir.Right, COL32_WHITE)
            renderArrowPointingAt(drawList, Vec2(pos.x + barW - halfSz.x - 1, pos.y), Vec2(halfSz.x + 2, halfSz.y + 1), Dir.Left, COL32_BLACK)
            renderArrowPointingAt(drawList, Vec2(pos.x + barW - halfSz.x, pos.y), halfSz, Dir.Left, COL32_WHITE)
        }
    }
}