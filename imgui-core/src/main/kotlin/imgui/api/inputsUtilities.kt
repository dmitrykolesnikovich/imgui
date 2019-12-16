package imgui.api

import glm_.i
import glm_.vec2.Vec2
import imgui.ImGui.calcTypematicRepeatAmount
import imgui.ImGui.io
import imgui.ImGui.isMouseDragPastThreshold
import imgui.ImGui.style
import imgui.MOUSE_INVALID
import imgui.MouseCursor
import imgui.internal.classes.Rect


/** Inputs Utilities */
interface inputsUtilities {

    fun getKeyIndex(imguiKey: Int): Int = io.keyMap[imguiKey]

    /** is key being held. == io.KeysDown[user_key_index]. note that imgui doesn't know the semantic of each entry of io.KeysDown[].
     *  Use your own indices/enums according to how your back-end/engine stored them into io.KeysDown[]! */
    fun isKeyDown(userKeyIndex: Int): Boolean = if (userKeyIndex < 0) false else io.keysDown[userKeyIndex]

    /** uses user's key indices as stored in the keys_down[] array. if repeat=true.
     *  uses io.KeyRepeatDelay / KeyRepeatRate  */
    fun isKeyPressed(userKeyIndex: Int, repeat: Boolean = true): Boolean = if (userKeyIndex < 0) false
    else {
        val t = io.keysDownDuration[userKeyIndex]
        when {
            t == 0f -> true
            repeat && t > io.keyRepeatDelay -> getKeyPressedAmount(userKeyIndex, io.keyRepeatDelay, io.keyRepeatRate) > 0
            else -> false
        }
    }

    /** was key released (went from Down to !Down)..    */
    fun isKeyReleased(userKeyIndex: Int): Boolean = if (userKeyIndex < 0) false else io.keysDownDurationPrev[userKeyIndex] >= 0f && !io.keysDown[userKeyIndex]

    /** Uses provided repeat rate/delay. return a count, most often 0 or 1 but might be >1 if RepeatRate is small enough
     *  that DeltaTime > RepeatRate */
    fun getKeyPressedAmount(keyIndex: Int, repeatDelay: Float, repeatRate: Float): Int {
        if (keyIndex < 0) return 0
        assert(keyIndex in 0 until io.keysDown.size)
        val t = io.keysDownDuration[keyIndex]
        return calcTypematicRepeatAmount(t - io.deltaTime, t, repeatDelay, repeatRate)
    }

    /** is mouse button held  (0=left, 1=right, 2=middle) */
    fun isMouseDown(button: Int): Boolean {
        assert(button in io.mouseDown.indices)
        return io.mouseDown[button]
    }

    /** is any mouse button held    */
    val isAnyMouseDown: Boolean
        get() = io.mouseDown.any()

    /** did mouse button clicked (went from !Down to Down)  (0=left, 1=right, 2=middle) */
    fun isMouseClicked(button: Int, repeat: Boolean = false): Boolean {

        assert(button >= 0 && button < io.mouseDown.size)
        val t = io.mouseDownDuration[button]
        if (t == 0f)
            return true

        if (repeat && t > io.keyRepeatDelay) {
            // FIXME: 2019/05/03: Our old repeat code was wrong here and led to doubling the repeat rate, which made it an ok rate for repeat on mouse hold.
            val amount = calcTypematicRepeatAmount(t - io.deltaTime, t, io.keyRepeatDelay, io.keyRepeatRate * 0.5f)
            if (amount > 0)
                return true
        }
        return false
    }

    /** did mouse button double-clicked. a double-click returns false in IsMouseClicked(). uses io.MouseDoubleClickTime.    */
    fun isMouseDoubleClicked(button: Int): Boolean = io.mouseDoubleClicked[button]

    /** did mouse button released (went from Down to !Down) */
    fun isMouseReleased(button: Int): Boolean = io.mouseReleased[button]

    /** is mouse dragging. if lock_threshold < -1.0f uses io.MouseDraggingThreshold */
    fun isMouseDragging(button: Int, lockThreshold: Float = -1f): Boolean {
        assert(button in io.mouseDown.indices)
        if (!io.mouseDown[button])
            return false
        return isMouseDragPastThreshold(button, lockThreshold)
    }

    /** Test if mouse cursor is hovering given rectangle
     *  NB- Rectangle is clipped by our current clip setting
     *  NB- Expand the rectangle to be generous on imprecise inputs systems (g.style.TouchExtraPadding)
     *  is mouse hovering given bounding rect (in screen space). clipped by current clipping settings, but disregarding
     *  of other consideration of focus/window ordering/popup-block.  */
    fun isMouseHoveringRect(r: Rect, clip: Boolean = true): Boolean = isMouseHoveringRect(r.min, r.max, clip)

    fun isMouseHoveringRect(rMin: Vec2, rMax: Vec2, clip: Boolean = true): Boolean {

        // Clip
        val rectClipped = Rect(rMin, rMax)
        if (clip)
            rectClipped clipWith g.currentWindow!!.clipRect

        // Expand for touch input
        val rectForTouch = Rect(rectClipped.min - style.touchExtraPadding, rectClipped.max + style.touchExtraPadding)
        return io.mousePos in rectForTouch
    }

    /** We typically use ImVec2(-FLT_MAX,-FLT_MAX) to denote an invalid mouse position.  */
    fun isMousePosValid(mousePos: Vec2? = null): Boolean = (mousePos ?: io.mousePos) allGreaterThan MOUSE_INVALID

    /** shortcut to io.mousePos provided by user, to be consistent with other calls
     *  ~GetMousePos    */
    val mousePos: Vec2
        get() = io.mousePos

    /** retrieve backup of mouse position at the time of opening popup we have BeginPopup() into
     *  ~GetMousePosOnOpeningCurrentPopup   */
    val mousePosOnOpeningCurrentPopup: Vec2
        get() = Vec2(g.beginPopupStack.lastOrNull()?.openMousePos ?: io.mousePos)

    /** return the delta from the initial clicking position while the mouse button is clicked or was just released.
     *  This is locked and return 0.0f until the mouse moves past a distance threshold at least once.
     *  If lock_threshold < -1.0f uses io.MouseDraggingThreshold
     *
     *  Back-ends in theory should always keep mouse position valid when dragging even outside the client window. */
    fun getMouseDragDelta(button: Int = 0, lockThreshold_: Float = -1f): Vec2 {

        assert(button >= 0 && button < io.mouseDown.size)
        var lockThreshold = lockThreshold_
        if (lockThreshold < 0f)
            lockThreshold = io.mouseDragThreshold
        if (io.mouseDown[button] || io.mouseReleased[button])
            if (io.mouseDragMaxDistanceSqr[button] >= lockThreshold * lockThreshold)
                if (isMousePosValid(io.mousePos) && isMousePosValid(io.mouseClickedPos[button]))
                    return io.mousePos - io.mouseClickedPos[button]
        return Vec2()
    }

    fun resetMouseDragDelta(button: Int = 0) {
        assert(button in io.mouseDown.indices)
        // NB: We don't need to reset g.IO.MouseDragMaxDistanceSqr
        io.mouseClickedPos[button] = io.mousePos
    }

    var mouseCursor: MouseCursor
        /** Get desired cursor type, reset in newFrame(), this is updated during the frame. valid before render().
         *  If you use software rendering by setting io.mouseDrawCursor ImGui will render those for you
         *
         *  ~getMouseCursor  */
        get() = g.mouseCursor
        /** set desired cursor type
         *
         *  ~setMouseCursor */
        set(value) {
            g.mouseCursor = value
        }

    /** Manually override io.wantCaptureKeyboard flag next frame (said flag is entirely left for your application to handle).
     *  e.g. force capture keyboard when your widget is being hovered.  */
    fun captureKeyboardFromApp(capture: Boolean = true) {
        g.wantCaptureKeyboardNextFrame = capture.i
    }

    /** Manually override io.WantCaptureMouse flag next frame (said flag is entirely left for your application to handle). */
    fun captureMouseFromApp(capture: Boolean = true) {
        g.wantCaptureMouseNextFrame = capture.i
    }
}