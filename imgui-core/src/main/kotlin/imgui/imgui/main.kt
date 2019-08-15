package imgui.imgui

import gli_.has
import glm_.*
import glm_.vec2.Vec2
import glm_.vec4.Vec4
import imgui.*
import imgui.ImGui.F32_TO_INT8_SAT
import imgui.ImGui.begin
import imgui.ImGui.buttonBehavior
import imgui.ImGui.calcTextSize
import imgui.ImGui.clearActiveId
import imgui.ImGui.clearDragDrop
import imgui.ImGui.closeButton
import imgui.ImGui.closePopupsOverWindow
import imgui.ImGui.collapseButton
import imgui.ImGui.defaultFont
import imgui.ImGui.end
import imgui.ImGui.topMostPopupModal
import imgui.ImGui.getNavInputAmount2d
import imgui.ImGui.getStyleColorVec4
import imgui.ImGui.io
import imgui.ImGui.isMouseClicked
import imgui.ImGui.isMouseDown
import imgui.ImGui.isMousePosValid
import imgui.ImGui.keepAliveID
import imgui.ImGui.loadIniSettingsFromDisk
import imgui.ImGui.mouseCursor
import imgui.ImGui.popId
import imgui.ImGui.pushId
import imgui.ImGui.renderFrame
import imgui.ImGui.renderTextClipped
import imgui.ImGui.saveIniSettingsToDisk
import imgui.ImGui.scrollbar
import imgui.ImGui.setCurrentFont
import imgui.ImGui.setNextWindowBgAlpha
import imgui.ImGui.setNextWindowSize
import imgui.ImGui.setTooltip
import imgui.ImGui.style
import imgui.ImGui.text
import imgui.ImGui.textColored
import imgui.ImGui.updateHoveredWindowAndCaptureFlags
import imgui.ImGui.updateMouseMovingWindowEndFrame
import imgui.ImGui.updateMouseMovingWindowNewFrame
import imgui.dsl.tooltip
import imgui.imgui.imgui_windows.Companion.getWindowBgColorIdxFromFlags
import imgui.internal.*
import org.lwjgl.system.Platform
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.reflect.KMutableProperty0
import imgui.ConfigFlag as Cf
import imgui.WindowFlag as Wf
import imgui.internal.DrawCornerFlag as Dcf
import imgui.internal.DrawListFlag as Dlf

@Suppress("UNCHECKED_CAST")

/** Main */
interface imgui_main {

    /** access the IO structure (mouse/keyboard/gamepad inputs, time, various configuration options/flags) */
    val io: IO
        get() = gImGui?.io
                ?: throw Error("No current context. Did you call ::Context() or Context::setCurrent()?")

    /** access the Style structure (colors, sizes). Always use PushStyleCol(), PushStyleVar() to modify style mid-frame. */
    val style: Style
        get() = gImGui?.style
                ?: throw Error("No current context. Did you call ::Context() or Context::setCurrent()?")

    /** start a new Dear ImGui frame, you can submit any command from this point until NewFrame()/Render().  */
    fun newFrameSanityChecks() { // TODO static?

        /*  Check user data
            (We pass an error message in the assert expression as a trick to get it visible to programmers who are not using a debugger,
            as most assert handlers display their argument)         */
        assert(g.initialized)
        assert(io.deltaTime > 0f || g.frameCount == 0) { "Need a positive DeltaTime!" }
        assert(g.frameCount == 0 || g.frameCountEnded == g.frameCount) { "Forgot to call Render() or EndFrame() at the end of the previous frame?" }
        assert(io.displaySize.x >= 0f && io.displaySize.y >= 0f) { "Invalid DisplaySize value!" }
        assert(io.fonts.fonts.isNotEmpty()) { "Font Atlas not built. Did you call io.Fonts->GetTexDataAsRGBA32() / GetTexDataAsAlpha8() ?" }
        assert(io.fonts.fonts[0].isLoaded) { "Font Atlas not built. Did you call io.Fonts->GetTexDataAsRGBA32() / GetTexDataAsAlpha8() ?" }
        assert(style.curveTessellationTol > 0f) { "Invalid style setting!" }
        assert(style.alpha in 0f..1f) { "Invalid style setting. Alpha cannot be negative (allows us to avoid a few clamps in color computations)!" }
        assert(style.windowMinSize allGreaterThanEqual 1) { "Invalid style setting." }
        assert(style.windowMenuButtonPosition == Dir.Left || style.windowMenuButtonPosition == Dir.Right)
        for (n in 0 until Key.COUNT)
            assert(io.keyMap[n] >= -1 && io.keyMap[n] < io.keysDown.size) { "io.KeyMap[] contains an out of bound value (need to be 0..512, or -1 for unmapped key)" }

        // Perform simple check: required key mapping (we intentionally do NOT check all keys to not pressure user into setting up everything, but Space is required and was only recently added in 1.60 WIP)
        if (io.configFlags has Cf.NavEnableKeyboard)
            assert(io.keyMap[Key.Space] != -1) { "ImGuiKey_Space is not mapped, required for keyboard navigation." }

        // Perform simple check: the beta io.configWindowsResizeFromEdges option requires back-end to honor mouse cursor changes and set the ImGuiBackendFlags_HasMouseCursors flag accordingly.
        if (io.configWindowsResizeFromEdges && io.backendFlags hasnt BackendFlag.HasMouseCursors)
            io.configWindowsResizeFromEdges = false
    }

    fun newFrame() {

        ptrIndices = 0

        assert(gImGui != null) { "No current context. Did you call ImGui::CreateContext() and ImGui::SetCurrentContext()?" }

        if (IMGUI_ENABLE_TEST_ENGINE)
            ImGuiTestEngineHook_PreNewFrame()

        // Check and assert for various common IO and Configuration mistakes
        newFrameSanityChecks()

        // Load settings on first frame (if not explicitly loaded manually before)
        if (!g.settingsLoaded) {
            assert(g.settingsWindows.isEmpty())
            io.iniFilename?.let(::loadIniSettingsFromDisk)
            g.settingsLoaded = true
        }

        // Save settings (with a delay so we don't spam disk too much)
        if (g.settingsDirtyTimer > 0f) {
            g.settingsDirtyTimer -= io.deltaTime
            if (g.settingsDirtyTimer <= 0f) {
                val ini = io.iniFilename
                if (ini != null)
                    saveIniSettingsToDisk(ini)
                else
                    io.wantSaveIniSettings = true  // Let user know they can call SaveIniSettingsToMemory(). user will need to clear io.WantSaveIniSettings themselves.
                g.settingsDirtyTimer = 0f
            }
        }

        g.time += io.deltaTime
        g.frameScopeActive = true
        g.frameCount += 1
        g.tooltipOverrideCount = 0
        g.windowsActiveCount = 0

        // Setup current font and draw list shared data
        io.fonts.locked = true
        setCurrentFont(defaultFont)
        assert(g.font.isLoaded)
        g.drawListSharedData.clipRectFullscreen = Vec4(0f, 0f, io.displaySize.x, io.displaySize.y)
        g.drawListSharedData.curveTessellationTol = style.curveTessellationTol
        g.drawListSharedData.initialFlags = Dlf.None.i
        if (style.antiAliasedLines)
            g.drawListSharedData.initialFlags = g.drawListSharedData.initialFlags or Dlf.AntiAliasedLines
        if (style.antiAliasedFill)
            g.drawListSharedData.initialFlags = g.drawListSharedData.initialFlags or Dlf.AntiAliasedFill
        if (io.backendFlags has BackendFlag.RendererHasVtxOffset)
            g.drawListSharedData.initialFlags = g.drawListSharedData.initialFlags or Dlf.AllowVtxOffset

        g.backgroundDrawList.clear()
        g.backgroundDrawList.pushTextureId(io.fonts.texId)
        g.backgroundDrawList.pushClipRectFullScreen()

        g.foregroundDrawList.clear()
        g.foregroundDrawList.pushTextureId(io.fonts.texId)
        g.foregroundDrawList.pushClipRectFullScreen()

        // Mark rendering data as invalid to prevent user who may have a handle on it to use it.
        g.drawData.clear()

        // Drag and drop keep the source ID alive so even if the source disappear our state is consistent
        if (g.dragDropActive && g.dragDropPayload.sourceId == g.activeId)
            keepAliveID(g.dragDropPayload.sourceId)

        // Clear reference to active widget if the widget isn't alive anymore
        if (g.hoveredIdPreviousFrame == 0)
            g.hoveredIdTimer = 0f
        if (g.hoveredIdPreviousFrame == 0 || (g.hoveredId != 0 && g.activeId == g.hoveredId))
            g.hoveredIdNotActiveTimer = 0f
        if (g.hoveredId != 0)
            g.hoveredIdTimer += io.deltaTime
        if (g.hoveredId != 0 && g.activeId != g.hoveredId)
            g.hoveredIdNotActiveTimer += io.deltaTime
        g.hoveredIdPreviousFrame = g.hoveredId
        g.hoveredId = 0
        g.hoveredIdAllowOverlap = false
        if (g.activeIdIsAlive != g.activeId && g.activeIdPreviousFrame == g.activeId && g.activeId != 0)
            clearActiveId()
        if (g.activeId != 0)
            g.activeIdTimer += io.deltaTime
        g.lastActiveIdTimer += io.deltaTime
        g.activeIdPreviousFrame = g.activeId
        g.activeIdPreviousFrameWindow = g.activeIdWindow
        g.activeIdPreviousFrameHasBeenEdited = g.activeIdHasBeenEditedBefore
        g.activeIdIsAlive = 0
        g.activeIdHasBeenEditedThisFrame = false
        g.activeIdPreviousFrameIsAlive = false
        g.activeIdIsJustActivated = false
        if (g.tempInputTextId != 0 && g.activeId != g.tempInputTextId)
            g.tempInputTextId = 0

        // Drag and drop
        g.dragDropAcceptIdPrev = g.dragDropAcceptIdCurr
        g.dragDropAcceptIdCurr = 0
        g.dragDropAcceptIdCurrRectSurface = Float.MAX_VALUE

        // Update keyboard input state
        for (i in io.keysDownDuration.indices)
            io.keysDownDurationPrev[i] = io.keysDownDuration[i]
        for (i in io.keysDown.indices)
            io.keysDownDuration[i] = when {
                io.keysDown[i] -> when {
                    io.keysDownDuration[i] < 0f -> 0f
                    else -> io.keysDownDuration[i] + io.deltaTime
                }
                else -> -1f
            }
        // Update gamepad/keyboard directional navigation
        navUpdate()

        // Update mouse input state
        updateMouseInputs()

        // Calculate frame-rate for the user, as a purely luxurious feature
        g.framerateSecPerFrameAccum += io.deltaTime - g.framerateSecPerFrame[g.framerateSecPerFrameIdx]
        g.framerateSecPerFrame[g.framerateSecPerFrameIdx] = io.deltaTime
        g.framerateSecPerFrameIdx = (g.framerateSecPerFrameIdx + 1) % g.framerateSecPerFrame.size
        io.framerate = when {
            g.framerateSecPerFrameAccum > 0f -> 1f / (g.framerateSecPerFrameAccum / g.framerateSecPerFrame.size)
            else -> Float.MAX_VALUE
        }

        // Find hovered window
        // (needs to be before UpdateMouseMovingWindowNewFrame so we fill g.HoveredWindowUnderMovingWindow on the mouse release frame)
        updateHoveredWindowAndCaptureFlags()

        // Handle user moving window with mouse (at the beginning of the frame to avoid input lag or sheering)
        updateMouseMovingWindowNewFrame()

        // Background darkening/whitening
        g.dimBgRatio = when {
            topMostPopupModal != null || (g.navWindowingTarget != null && g.navWindowingHighlightAlpha > 0f) -> (g.dimBgRatio + io.deltaTime * 6f) min 1f
            else -> (g.dimBgRatio - io.deltaTime * 10f) max 0f
        }
        g.mouseCursor = MouseCursor.Arrow
        g.wantTextInputNextFrame = -1
        g.wantCaptureKeyboardNextFrame = -1
        g.wantCaptureMouseNextFrame = -1
        g.platformImePos put 1f // OS Input Method Editor showing on top-left of our window by default

        // Mouse wheel scrolling, scale
        updateMouseWheel()

        // Pressing TAB activate widget focus
        g.focusTabPressed = g.navWindow?.let { it.active && it.flags hasnt Wf.NoNavInputs && !io.keyCtrl && Key.Tab.isPressed } == true
        if (g.activeId == 0 && g.focusTabPressed) {
            // Note that SetKeyboardFocusHere() sets the Next fields mid-frame. To be consistent we also
            // manipulate the Next fields even, even though they will be turned into Curr fields by the code below.
            g.focusRequestNextWindow = g.navWindow
            g.focusRequestNextCounterAll = Int.MAX_VALUE
            g.focusRequestNextCounterTab = when {
                g.navId != 0 && g.navIdTabCounter != Int.MAX_VALUE -> g.navIdTabCounter + 1 + if (io.keyShift) -1 else 1
                else -> if (io.keyShift) -1 else 0
            }
        }

        // Turn queued focus request into current one
        g.focusRequestCurrWindow = null
        g.focusRequestCurrCounterTab = Int.MAX_VALUE
        g.focusRequestCurrCounterAll = Int.MAX_VALUE
        g.focusRequestNextWindow?.let { window ->
            g.focusRequestCurrWindow = window
            if (g.focusRequestNextCounterAll != Int.MAX_VALUE && window.dc.focusCounterAll != -1)
                g.focusRequestCurrCounterAll = modPositive(g.focusRequestNextCounterAll, window.dc.focusCounterAll + 1)
            if (g.focusRequestNextCounterTab != Int.MAX_VALUE && window.dc.focusCounterTab != -1)
                g.focusRequestCurrCounterTab = modPositive(g.focusRequestNextCounterTab, window.dc.focusCounterTab + 1)
            g.focusRequestNextWindow = null
            g.focusRequestNextCounterTab = Int.MAX_VALUE
            g.focusRequestNextCounterAll = Int.MAX_VALUE
        }

        g.navIdTabCounter = Int.MAX_VALUE

        // Mark all windows as not visible
        assert(g.windowsFocusOrder.size == g.windows.size)
        g.windows.forEach {
            it.wasActive = it.active
            it.beginCount = 0
            it.active = false
            it.writeAccessed = false
        }

        // Closing the focused window restore focus to the first active root window in descending z-order
        if (g.navWindow?.wasActive == false)
            focusTopMostWindowUnderOne(null, null)

        // No window should be open at the beginning of the frame.
        // But in order to allow the user to call NewFrame() multiple times without calling Render(), we are doing an explicit clear.
        g.currentWindowStack.clear()
        g.beginPopupStack.clear()
        closePopupsOverWindow(g.navWindow, false)

        // [DEBUG] Item picker tool - start with DebugStartItemPicker() - useful to visually select an item and break into its call-stack.
        updateDebugToolItemPicker()

        // Create implicit/fallback window - which we will only render it if the user has added something to it.
        // We don't use "Debug" to avoid colliding with user trying to create a "Debug" window with custom flags.
        // This fallback is particularly important as it avoid ImGui:: calls from crashing.
        setNextWindowSize(Vec2(400), Cond.FirstUseEver)
        begin("Debug##Default")
        g.frameScopePushedImplicitWindow = true

        if (IMGUI_ENABLE_TEST_ENGINE)
            ImGuiTestEngineHook_PostNewFrame()
    }

    /** [DEBUG] Item picker tool - start with DebugStartItemPicker() - useful to visually select an item and break into its call-stack. */
    fun updateDebugToolItemPicker() {

        g.debugItemPickerBreakId = 0
        if (g.debugItemPickerActive) {

            val hoveredId = g.hoveredIdPreviousFrame
            mouseCursor = MouseCursor.Hand
            if (Key.Escape.isPressed)
                g.debugItemPickerActive = false
            if (isMouseClicked(0) && hoveredId != 0) {
                g.debugItemPickerBreakId = hoveredId
                g.debugItemPickerActive = false
            }
            setNextWindowBgAlpha(0.6f)
            tooltip {
                text("HoveredId: 0x%08X", hoveredId)
                text("Press ESC to abort picking.");
                textColored(getStyleColorVec4(if (hoveredId != 0) Col.Text else Col.TextDisabled), "Click to break in debugger!")
            }
        }
    }

    /** Ends the Dear ImGui frame. automatically called by ::render(), you likely don't need to call that yourself directly.
     *  If you don't need to render data (skipping rendering) you may call ::endFrame() but you'll have wasted CPU already!
     *  If you don't need to render, better to not create any imgui windows and not call ::newFrame() at all!  */
    fun endFrame() {

        assert(g.initialized)
        if (g.frameCountEnded == g.frameCount) return   // Don't process endFrame() multiple times.
        assert(g.frameScopeActive) { "Forgot to call ImGui::newFrame()?" }

        // Notify OS when our Input Method Editor cursor has moved (e.g. CJK inputs using Microsoft IME)
        if (io.imeSetInputScreenPosFn != null && (g.platformImeLastPos.x == Float.MAX_VALUE || (g.platformImeLastPos - g.platformImePos).lengthSqr > 0.0001f)) {
            if (DEBUG)
                println("in (${g.platformImePos.x}, ${g.platformImePos.y}) (${g.platformImeLastPos.x}, ${g.platformImeLastPos.y})")
//            io.imeSetInputScreenPosFn!!(g.platformImePos.x.i, g.platformImePos.y.i)
            io.imeSetInputScreenPosFn!!(1000, 1000)
            g.platformImeLastPos put g.platformImePos
        }

        // Report when there is a mismatch of Begin/BeginChild vs End/EndChild calls. Important: Remember that the Begin/BeginChild API requires you
        // to always call End/EndChild even if Begin/BeginChild returns false! (this is unfortunately inconsistent with most other Begin* API).
        if (g.currentWindowStack.size != 1)
            if (g.currentWindowStack.size > 1) {
                assert(g.currentWindowStack.size == 1) { "Mismatched Begin/BeginChild vs End/EndChild calls: did you forget to call End/EndChild?" }
                while (g.currentWindowStack.size > 1) // FIXME-ERRORHANDLING
                    end()
            } else
                assert(g.currentWindowStack.size == 1) { "Mismatched Begin/BeginChild vs End/EndChild calls: did you call End/EndChild too much?" }

        // Hide implicit/fallback "Debug" window if it hasn't been used
        g.frameScopePushedImplicitWindow = false
        g.currentWindow?.let {
            if (!it.writeAccessed) it.active = false
        }

        end()

        // Show CTRL+TAB list window
        if (g.navWindowingTarget != null)
            navUpdateWindowingList()

        // Drag and Drop: Elapse payload (if delivered, or if source stops being submitted)
        if (g.dragDropActive) {
            val isDelivered = g.dragDropPayload.delivery
            val isElapsed = g.dragDropPayload.dataFrameCount + 1 < g.frameCount &&
                    (g.dragDropSourceFlags has DragDropFlag.SourceAutoExpirePayload || !isMouseDown(g.dragDropMouseButton))
            if (isDelivered || isElapsed)
                clearDragDrop()
        }

        // Drag and Drop: Fallback for source tooltip. This is not ideal but better than nothing.
        if (g.dragDropActive && g.dragDropSourceFrameCount < g.frameCount) {
            g.dragDropWithinSourceOrTarget = true
            setTooltip("...")
            g.dragDropWithinSourceOrTarget = false
        }

        // End frame
        g.frameScopeActive = false
        g.frameCountEnded = g.frameCount

        // Initiate moving window + handle left-click and right-click focus
        updateMouseMovingWindowEndFrame()

        /*  Sort the window list so that all child windows are after their parent
            We cannot do that on FocusWindow() because childs may not exist yet         */
        g.windowsSortBuffer.clear()
        g.windowsSortBuffer.ensureCapacity(g.windows.size)
        g.windows.filter { !it.active || it.flags hasnt Wf.ChildWindow }  // if a child is active its parent will add it
                .forEach { it addToSortBuffer g.windowsSortBuffer }
        assert(g.windows.size == g.windowsSortBuffer.size) { "This usually assert if there is a mismatch between the ImGuiWindowFlags_ChildWindow / ParentWindow values and DC.ChildWindows[] in parents, aka we've done something wrong." }
        g.windows.clear()
        g.windows += g.windowsSortBuffer
        io.metricsActiveWindows = g.windowsActiveCount

        // Unlock font atlas
        io.fonts.locked = false

        // Clear Input data for next frame
        io.mouseWheel = 0f
        io.mouseWheelH = 0f
        io.inputQueueCharacters.clear()
        io.navInputs.fill(0f)
    }

    /** ends the Dear ImGui frame, finalize the draw data. You can get call GetDrawData() to obtain it and run your rendering function.
     *  (Obsolete: this used to call io.RenderDrawListsFn(). Nowadays, we allow and prefer calling your render function yourself.)   */
    fun render() {

        assert(g.initialized)

        if (g.frameCountEnded != g.frameCount) endFrame()
        g.frameCountRendered = g.frameCount

        // Gather DrawList to render (for each active window)
        io.metricsRenderWindows = 0
        io.metricsRenderIndices = 0
        io.metricsRenderVertices = 0
        g.drawDataBuilder.clear()
        if (g.backgroundDrawList.vtxBuffer.hasRemaining())
            g.backgroundDrawList addTo g.drawDataBuilder.layers[0]

        val windowsToRenderTopMost = arrayOf(
                g.navWindowingTarget?.rootWindow?.takeIf { it.flags has Wf.NoBringToFrontOnFocus },
                g.navWindowingList.getOrNull(0).takeIf { g.navWindowingTarget != null })
        g.windows
                .filter { it.isActiveAndVisible && it.flags hasnt Wf.ChildWindow && it !== windowsToRenderTopMost[0] && it !== windowsToRenderTopMost[1] }
                .forEach { it.addRootWindowToDrawData() }
        windowsToRenderTopMost
                .filterNotNull()
                .filter { it.isActiveAndVisible } // NavWindowingTarget is always temporarily displayed as the top-most window
                .forEach { it.addRootWindowToDrawData() }
        g.drawDataBuilder.flattenIntoSingleLayer()

        // Draw software mouse cursor if requested
        val offset = Vec2()
        val size = Vec2()
        val uv = Array(4) { Vec2() }
        if (io.mouseDrawCursor && io.fonts.getMouseCursorTexData(g.mouseCursor, offset, size, uv)) {
            val pos = io.mousePos - offset
            val texId = io.fonts.texId
            val sc = style.mouseCursorScale
            g.foregroundDrawList.apply {
                pushTextureId(texId)
                addImage(texId, pos + Vec2(1, 0) * sc, pos + Vec2(1, 0) * sc + size * sc, uv[2], uv[3], COL32(0, 0, 0, 48))        // Shadow
                addImage(texId, pos + Vec2(2, 0) * sc, pos + Vec2(2, 0) * sc + size * sc, uv[2], uv[3], COL32(0, 0, 0, 48))        // Shadow
                addImage(texId, pos, pos + size * sc, uv[2], uv[3], COL32(0, 0, 0, 255))       // Black border
                addImage(texId, pos, pos + size * sc, uv[0], uv[1], COL32(255, 255, 255, 255)) // White fill
                popTextureId()
            }
        }
        if (g.foregroundDrawList.vtxBuffer.hasRemaining())
            g.foregroundDrawList addTo g.drawDataBuilder.layers[0]

        // Setup ImDrawData structure for end-user
        g.drawData setup g.drawDataBuilder.layers[0]
        io.metricsRenderVertices = g.drawData.totalVtxCount
        io.metricsRenderIndices = g.drawData.totalIdxCount
    }

    /** Same value as passed to the old io.renderDrawListsFn function. Valid after ::render() and until the next call to
     *  ::newFrame()   */
    val drawData: DrawData?
        get() = when (Platform.get()) {
            Platform.MACOSX -> g.drawData.clone()
            else -> g.drawData
        }.takeIf { it.valid }

    companion object {

        // Misc
        fun updateMouseInputs() {

            with(io) {

                // Round mouse position to avoid spreading non-rounded position (e.g. UpdateManualResize doesn't support them well)
                if (isMousePosValid(mousePos)) {
                    g.lastValidMousePos = floor(mousePos)
                    mousePos = Vec2(g.lastValidMousePos)
                }

                // If mouse just appeared or disappeared (usually denoted by -FLT_MAX component) we cancel out movement in MouseDelta
                if (isMousePosValid(mousePos) && isMousePosValid(mousePosPrev))
                    mouseDelta = mousePos - mousePosPrev
                else
                    mouseDelta put 0f
                if (mouseDelta.x != 0f || mouseDelta.y != 0f)
                    g.navDisableMouseHover = false

                mousePosPrev put mousePos
                for (i in mouseDown.indices) {
                    mouseClicked[i] = mouseDown[i] && mouseDownDuration[i] < 0f
                    mouseReleased[i] = !mouseDown[i] && mouseDownDuration[i] >= 0f
                    mouseDownDurationPrev[i] = mouseDownDuration[i]
                    mouseDownDuration[i] = when {
                        mouseDown[i] -> when {
                            mouseDownDuration[i] < 0f -> 0f
                            else -> mouseDownDuration[i] + deltaTime
                        }
                        else -> -1f
                    }
                    mouseDoubleClicked[i] = false
                    if (mouseClicked[i]) {
                        if (g.time - mouseClickedTime[i] < mouseDoubleClickTime) {
                            val deltaFromClickPos = when {
                                isMousePosValid(io.mousePos) -> io.mousePos - io.mouseClickedPos[i]
                                else -> Vec2()
                            }
                            if (deltaFromClickPos.lengthSqr < io.mouseDoubleClickMaxDist * io.mouseDoubleClickMaxDist)
                                mouseDoubleClicked[i] = true
                            mouseClickedTime[i] = -Double.MAX_VALUE    // so the third click isn't turned into a double-click
                        } else
                            mouseClickedTime[i] = g.time
                        mouseClickedPos[i] put mousePos
                        mouseDownWasDoubleClick[i] = mouseDoubleClicked[i]
                        mouseDragMaxDistanceAbs[i] put 0f
                        mouseDragMaxDistanceSqr[i] = 0f
                    } else if (mouseDown[i]) {
                        // Maintain the maximum distance we reaching from the initial click position, which is used with dragging threshold
                        val deltaFromClickPos = when {
                            isMousePosValid(io.mousePos) -> io.mousePos - io.mouseClickedPos[i]
                            else -> Vec2()
                        }
                        io.mouseDragMaxDistanceSqr[i] = io.mouseDragMaxDistanceSqr[i] max deltaFromClickPos.lengthSqr
                        io.mouseDragMaxDistanceAbs[i].x = io.mouseDragMaxDistanceAbs[i].x max when {
                            deltaFromClickPos.x < 0f -> -deltaFromClickPos.x
                            else -> deltaFromClickPos.x
                        }
                        io.mouseDragMaxDistanceAbs[i].y = io.mouseDragMaxDistanceAbs[i].y max when {
                            deltaFromClickPos.y < 0f -> -deltaFromClickPos.y
                            else -> deltaFromClickPos.y
                        }
                        val mouseDelta = mousePos - mouseClickedPos[i]
                        mouseDragMaxDistanceAbs[i].x = mouseDragMaxDistanceAbs[i].x max if (mouseDelta.x < 0f) -mouseDelta.x else mouseDelta.x
                        mouseDragMaxDistanceAbs[i].y = mouseDragMaxDistanceAbs[i].y max if (mouseDelta.y < 0f) -mouseDelta.y else mouseDelta.y
                        mouseDragMaxDistanceSqr[i] = mouseDragMaxDistanceSqr[i] max mouseDelta.lengthSqr
                    }
                    if (!mouseDown[i] && !mouseReleased[i])
                        mouseDownWasDoubleClick[i] = false
                    // Clicking any mouse button reactivate mouse hovering which may have been deactivated by gamepad/keyboard navigation
                    if (mouseClicked[i])
                        g.navDisableMouseHover = false
                }
            }
        }

        fun updateMouseWheel() {

            // Reset the locked window if we move the mouse or after the timer elapses
            if (g.wheelingWindow != null) {
                g.wheelingWindowTimer -= io.deltaTime
                if (isMousePosValid() && (io.mousePos - g.wheelingWindowRefMousePos).lengthSqr > io.mouseDragThreshold * io.mouseDragThreshold)
                    g.wheelingWindowTimer = 0f
                if (g.wheelingWindowTimer <= 0f) {
                    g.wheelingWindow = null
                    g.wheelingWindowTimer = 0f
                }
            }

            if (io.mouseWheel == 0f && io.mouseWheelH == 0f)
                return

            var window = g.wheelingWindow ?: g.hoveredWindow
            if (window == null || window.collapsed)
                return

            // Zoom / Scale window
            // FIXME-OBSOLETE: This is an old feature, it still works but pretty much nobody is using it and may be best redesigned.
            if (io.mouseWheel != 0f && io.keyCtrl && io.fontAllowUserScaling) {
                window.startLockWheeling()
                val newFontScale = glm.clamp(window.fontWindowScale + io.mouseWheel * 0.1f, 0.5f, 2.5f)
                val scale = newFontScale / window.fontWindowScale
                window.fontWindowScale = newFontScale
                if (window.flags hasnt Wf.ChildWindow) {
                    val offset = window.size * (1f - scale) * (io.mousePos - window.pos) / window.size
                    window.setPos(window.pos + offset)
                    window.size = floor(window.size * scale)
                    window.sizeFull = floor(window.sizeFull * scale)
                }
                return
            }

            // Mouse wheel scrolling
            // If a child window has the ImGuiWindowFlags_NoScrollWithMouse flag, we give a chance to scroll its parent

            // Vertical Mouse Wheel scrolling
            val wheelY = if (io.mouseWheel != 0f && !io.keyShift) io.mouseWheel else 0f
            if (wheelY != 0f && !io.keyCtrl) {
                window.startLockWheeling()
                tailrec fun Window.getParent(): Window = when {
                    flags has Wf.ChildWindow && (scrollMax.y == 0f || (flags has Wf.NoScrollWithMouse && flags hasnt Wf.NoMouseInputs)) -> parentWindow!!.getParent()
                    else -> this
                }
                window = g.hoveredWindow!!.getParent()
                if (window.flags hasnt Wf.NoScrollWithMouse && window.flags hasnt Wf.NoMouseInputs) {
                    val maxStep = window.innerRect.height * 0.67f
                    val scrollStep = floor((5 * window.calcFontSize()) min maxStep)
                    window.setScrollY(window.scroll.y - wheelY * scrollStep)
                }
            }

            // Horizontal Mouse Wheel scrolling, or Vertical Mouse Wheel w/ Shift held
            val wheelX = when {
                io.mouseWheelH != 0f && !io.keyShift -> io.mouseWheelH
                io.mouseWheel != 0f && io.keyShift -> io.mouseWheel
                else -> 0f
            }
            if (wheelX != 0f && !io.keyCtrl) {
                window.startLockWheeling()
                tailrec fun Window.getParent(): Window = when {
                    flags has Wf.ChildWindow && (scrollMax.x == 0f || (flags has Wf.NoScrollWithMouse && flags hasnt Wf.NoMouseInputs)) -> parentWindow!!.getParent()
                    else -> this
                }
                window = g.hoveredWindow!!.getParent()
                if (window.flags hasnt Wf.NoScrollWithMouse && window.flags hasnt Wf.NoMouseInputs) {
                    val maxStep = window.innerRect.width * 0.67f
                    val scrollStep = floor((2 * window.calcFontSize()) min maxStep)
                    window.setScrollX(window.scroll.x - wheelX * scrollStep)
                }
            }
        }

        /** Handle resize for: Resize Grips, Borders, Gamepad
         * @return [JVM] borderHelf to Boolean   */
        fun updateManualResize(window: Window, sizeAutoFit: Vec2, borderHeld_: Int, resizeGripCount: Int, resizeGripCol: IntArray): Pair<Int, Boolean> {

            var borderHeld = borderHeld_

            val flags = window.flags

            if (flags has Wf.NoResize || flags has Wf.AlwaysAutoResize || window.autoFitFrames anyGreaterThan 0)
                return borderHeld to false
            if (!window.wasActive) // Early out to avoid running this code for e.g. an hidden implicit/fallback Debug window.
                return borderHeld to false

            var retAutoFit = false
            val resizeBorderCount = if (io.configWindowsResizeFromEdges) 4 else 0
            val gripDrawSize = max(g.fontSize * 1.35f, window.windowRounding + 1f + g.fontSize * 0.2f).i.f
            val gripHoverInnerSize = (gripDrawSize * 0.75f).i.f
            val gripHoverOuterSize = if (io.configWindowsResizeFromEdges) WINDOWS_RESIZE_FROM_EDGES_HALF_THICKNESS else 0f

            val posTarget = Vec2(Float.MAX_VALUE)
            val sizeTarget = Vec2(Float.MAX_VALUE)

            // Resize grips and borders are on layer 1
            window.dc.navLayerCurrent = NavLayer.Menu
            window.dc.navLayerCurrentMask = 1 shl NavLayer.Menu

            // Manual resize grips
            pushId("#RESIZE")
            for (resizeGripN in 0 until resizeGripCount) {

                val grip = resizeGripDef[resizeGripN]
                val corner = window.pos.lerp(window.pos + window.size, grip.cornerPosN)

                // Using the FlattenChilds button flag we make the resize button accessible even if we are hovering over a child window
                val resizeRect = Rect(corner - grip.innerDir * gripHoverOuterSize, corner + grip.innerDir * gripHoverInnerSize)
                if (resizeRect.min.x > resizeRect.max.x) swap(resizeRect.min::x, resizeRect.max::x)
                if (resizeRect.min.y > resizeRect.max.y) swap(resizeRect.min::y, resizeRect.max::y)

                val f = ButtonFlag.FlattenChildren or ButtonFlag.NoNavFocus
                val (_, hovered, held) = buttonBehavior(resizeRect, window.getId(resizeGripN), f)
                //GetOverlayDrawList(window)->AddRect(resize_rect.Min, resize_rect.Max, IM_COL32(255, 255, 0, 255));
                if (hovered || held)
                    g.mouseCursor = if (resizeGripN has 1) MouseCursor.ResizeNESW else MouseCursor.ResizeNWSE

                if (held && g.io.mouseDoubleClicked[0] && resizeGripN == 0) {
                    // Manual auto-fit when double-clicking
                    sizeTarget put window.calcSizeAfterConstraint(sizeAutoFit)
                    retAutoFit = true
                    clearActiveId()
                } else if (held) {
                    // Resize from any of the four corners
                    // We don't use an incremental MouseDelta but rather compute an absolute target size based on mouse position
                    // Corner of the window corresponding to our corner grip
                    val cornerTarget = g.io.mousePos - g.activeIdClickOffset + (grip.innerDir * gripHoverOuterSize).lerp(grip.innerDir * -gripHoverInnerSize, grip.cornerPosN)
                    window.calcResizePosSizeFromAnyCorner(cornerTarget, grip.cornerPosN, posTarget, sizeTarget)
                }
                if (resizeGripN == 0 || held || hovered)
                    resizeGripCol[resizeGripN] = (if (held) Col.ResizeGripActive else if (hovered) Col.ResizeGripHovered else Col.ResizeGrip).u32
            }
            for (borderN in 0 until resizeBorderCount) {
                val borderRect = window.getResizeBorderRect(borderN, gripHoverInnerSize, WINDOWS_RESIZE_FROM_EDGES_HALF_THICKNESS)
                val (_, hovered, held) = buttonBehavior(borderRect, window.getId((borderN + 4)), ButtonFlag.FlattenChildren)
                //GetOverlayDrawList(window)->AddRect(border_rect.Min, border_rect.Max, IM_COL32(255, 255, 0, 255));
                if ((hovered && g.hoveredIdTimer > WINDOWS_RESIZE_FROM_EDGES_FEEDBACK_TIMER) || held) {
                    g.mouseCursor = if (borderN has 1) MouseCursor.ResizeEW else MouseCursor.ResizeNS
                    if (held)
                        borderHeld = borderN
                }
                if (held) {
                    val borderTarget = Vec2(window.pos)
                    val borderPosN = when (borderN) {
                        0 -> {
                            borderTarget.y = g.io.mousePos.y - g.activeIdClickOffset.y + WINDOWS_RESIZE_FROM_EDGES_HALF_THICKNESS
                            Vec2(0, 0)
                        }
                        1 -> {
                            borderTarget.x = g.io.mousePos.x - g.activeIdClickOffset.x + WINDOWS_RESIZE_FROM_EDGES_HALF_THICKNESS
                            Vec2(1, 0)
                        }
                        2 -> {
                            borderTarget.y = g.io.mousePos.y - g.activeIdClickOffset.y + WINDOWS_RESIZE_FROM_EDGES_HALF_THICKNESS
                            Vec2(0, 1)
                        }
                        3 -> {
                            borderTarget.x = g.io.mousePos.x - g.activeIdClickOffset.x + WINDOWS_RESIZE_FROM_EDGES_HALF_THICKNESS
                            Vec2(0, 0)
                        }
                        else -> Vec2(0, 0)
                    }
                    window.calcResizePosSizeFromAnyCorner(borderTarget, borderPosN, posTarget, sizeTarget)
                }
            }
            popId()

            // Navigation resize (keyboard/gamepad)
            if (g.navWindowingTarget?.rootWindow === window) {
                val navResizeDelta = Vec2()
                if (g.navInputSource == InputSource.NavKeyboard && g.io.keyShift)
                    navResizeDelta put getNavInputAmount2d(NavDirSourceFlag.Keyboard.i, InputReadMode.Down)
                if (g.navInputSource == InputSource.NavGamepad)
                    navResizeDelta put getNavInputAmount2d(NavDirSourceFlag.PadDPad.i, InputReadMode.Down)
                if (navResizeDelta.x != 0f || navResizeDelta.y != 0f) {
                    val NAV_RESIZE_SPEED = 600f
                    navResizeDelta *= floor(NAV_RESIZE_SPEED * g.io.deltaTime * min(g.io.displayFramebufferScale.x, g.io.displayFramebufferScale.y))
                    g.navWindowingToggleLayer = false
                    g.navDisableMouseHover = true
                    resizeGripCol[0] = Col.ResizeGripActive.u32
                    // FIXME-NAV: Should store and accumulate into a separate size buffer to handle sizing constraints properly, right now a constraint will make us stuck.
                    sizeTarget put window.calcSizeAfterConstraint(window.sizeFull + navResizeDelta)
                }
            }

            // Apply back modified position/size to window
            if (sizeTarget.x != Float.MAX_VALUE) {
                window.sizeFull put sizeTarget
                window.markIniSettingsDirty()
            }
            if (posTarget.x != Float.MAX_VALUE) {
                window.pos = floor(posTarget)
                window.markIniSettingsDirty()
            }

            // Resize nav layer
            window.dc.navLayerCurrent = NavLayer.Main
            window.dc.navLayerCurrentMask = 1 shl NavLayer.Main

            window.size put window.sizeFull

            return borderHeld to retAutoFit
        }

        fun clampWindowRect(window: Window, rect: Rect, padding: Vec2) {
            val sizeForClamping = when {
                io.configWindowsMoveFromTitleBarOnly && window.flags hasnt Wf.NoTitleBar -> Vec2(window.size.x, window.titleBarHeight)
                else -> window.size
            }
            window.pos = glm.min(rect.max - padding, glm.max(window.pos + sizeForClamping, rect.min + padding) - sizeForClamping)
        }

        fun renderWindowOuterBorders(window: Window) {

            val rounding = window.windowRounding
            val borderSize = window.windowBorderSize
            if (borderSize > 0f && window.flags hasnt Wf.NoBackground)
                window.drawList.addRect(window.pos, window.pos + window.size, Col.Border.u32, rounding, Dcf.All.i, borderSize)

            val borderHeld = window.resizeBorderHeld
            if (borderHeld != -1) {
                val def = resizeBorderDef[borderHeld]
                val borderR = window.getResizeBorderRect(borderHeld, rounding, 0f)
                window.drawList.apply {
                    pathArcTo(borderR.min.lerp(borderR.max, def.cornerPosN1) + Vec2(0.5f) + def.innerDir * rounding, rounding, def.outerAngle - glm.PIf * 0.25f, def.outerAngle)
                    pathArcTo(borderR.min.lerp(borderR.max, def.cornerPosN2) + Vec2(0.5f) + def.innerDir * rounding, rounding, def.outerAngle, def.outerAngle + glm.PIf * 0.25f)
                    pathStroke(Col.SeparatorActive.u32, false, 2f max borderSize) // Thicker than usual
                }
            }
            if (style.frameBorderSize > 0f && window.flags hasnt Wf.NoTitleBar) {
                val y = window.pos.y + window.titleBarHeight - 1
                window.drawList.addLine(Vec2(window.pos.x + borderSize, y), Vec2(window.pos.x + window.size.x - borderSize, y), Col.Border.u32, style.frameBorderSize)
            }
        }

        fun renderWindowDecorations(window: Window, titleBarRect: Rect, titleBarIsHighlight: Boolean, resizeGripCount: Int, resizeGripCol: IntArray, resizeGripDrawSize: Float) {

            val flags = window.flags

            // Draw window + handle manual resize
            // As we highlight the title bar when want_focus is set, multiple reappearing windows will have have their title bar highlighted on their reappearing frame.
            val windowRounding = window.windowRounding
            val windowBorderSize = window.windowBorderSize
            if (window.collapsed) {
                // Title bar only
                val backupBorderSize = style.frameBorderSize
                g.style.frameBorderSize = window.windowBorderSize
                val titleBarCol = if (titleBarIsHighlight && !g.navDisableHighlight) Col.TitleBgActive else Col.TitleBgCollapsed
                renderFrame(titleBarRect.min, titleBarRect.max, titleBarCol.u32, true, windowRounding)
                style.frameBorderSize = backupBorderSize
            } else {
                // Window background
                if (flags hasnt Wf.NoBackground) {
                    var bgCol = getWindowBgColorIdxFromFlags(flags).u32
                    val alpha = when {
                        g.nextWindowData.flags has NextWindowDataFlag.HasBgAlpha -> g.nextWindowData.bgAlphaVal
                        else -> 1f
                    }
                    if (alpha != 1f)
                        bgCol = (bgCol and COL32_A_MASK.inv()) or (F32_TO_INT8_SAT(alpha) shl COL32_A_SHIFT)
                    window.drawList.addRectFilled(window.pos + Vec2(0f, window.titleBarHeight), window.pos + window.size, bgCol, windowRounding,
                            if (flags has Wf.NoTitleBar) Dcf.All.i else Dcf.Bot.i)
                }

                // Title bar
                if (flags hasnt Wf.NoTitleBar) {
                    val titleBarCol = if (titleBarIsHighlight) Col.TitleBgActive else Col.TitleBg
                    window.drawList.addRectFilled(titleBarRect.min, titleBarRect.max, titleBarCol.u32, windowRounding, Dcf.Top.i)
                }

                // Menu bar
                if (flags has Wf.MenuBar) {
                    val menuBarRect = window.menuBarRect()
                    menuBarRect clipWith window.rect() // Soft clipping, in particular child window don't have minimum size covering the menu bar so this is useful for them.
                    val rounding = if (flags has Wf.NoTitleBar) windowRounding else 0f
                    window.drawList.addRectFilled(menuBarRect.min + Vec2(windowBorderSize, 0f), menuBarRect.max - Vec2(windowBorderSize, 0f), Col.MenuBarBg.u32, rounding, Dcf.Top.i)
                    if (style.frameBorderSize > 0f && menuBarRect.max.y < window.pos.y + window.size.y)
                        window.drawList.addLine(menuBarRect.bl, menuBarRect.br, Col.Border.u32, style.frameBorderSize)
                }

                // Scrollbars
                if (window.scrollbar.x) scrollbar(Axis.X)
                if (window.scrollbar.y) scrollbar(Axis.Y)

                // Render resize grips (after their input handling so we don't have a frame of latency)
                if (flags hasnt Wf.NoResize)
                    repeat(resizeGripCount) { resizeGripN ->
                        val grip = resizeGripDef[resizeGripN]
                        val corner = window.pos.lerp(window.pos + window.size, grip.cornerPosN)
                        with(window.drawList) {
                            pathLineTo(corner + grip.innerDir * (if (resizeGripN has 1) Vec2(windowBorderSize, resizeGripDrawSize) else Vec2(resizeGripDrawSize, windowBorderSize)))
                            pathLineTo(corner + grip.innerDir * (if (resizeGripN has 1) Vec2(resizeGripDrawSize, windowBorderSize) else Vec2(windowBorderSize, resizeGripDrawSize)))
                            pathArcToFast(Vec2(corner.x + grip.innerDir.x * (windowRounding + windowBorderSize), corner.y + grip.innerDir.y * (windowRounding + windowBorderSize)), windowRounding, grip.angleMin12, grip.angleMax12)
                            pathFillConvex(resizeGripCol[resizeGripN])
                        }
                    }

                // Borders
                renderWindowOuterBorders(window)
            }
        }

        /** Render title text, collapse button, close button */
        fun renderWindowTitleBarContents(window: Window, titleBarRect: Rect, name: String, pOpen: KMutableProperty0<Boolean>?) {

            val flags = window.flags

            val hasCloseButton = pOpen != null
            val hasCollapseButton = flags hasnt Wf.NoCollapse

            // Close & Collapse button are on the Menu NavLayer and don't default focus (unless there's nothing else on that layer)
            val itemFlagsBackup = window.dc.itemFlags
            window.dc.itemFlags = window.dc.itemFlags or ItemFlag.NoNavDefaultFocus
            window.dc.navLayerCurrent = NavLayer.Menu
            window.dc.navLayerCurrentMask = 1 shl NavLayer.Menu

            // Layout buttons
            // FIXME: Would be nice to generalize the subtleties expressed here into reusable code.
            var padL = style.framePadding.x
            var padR = style.framePadding.x
            val buttonSz = g.fontSize
            val closeButtonPos = Vec2()
            val collapseButtonPos = Vec2()
            if (hasCloseButton) {
                padR += buttonSz
                closeButtonPos.put(titleBarRect.max.x - padR - style.framePadding.x, titleBarRect.min.y)
            }
            if (hasCollapseButton && style.windowMenuButtonPosition == Dir.Right) {
                padR += buttonSz
                collapseButtonPos.put(titleBarRect.max.x - padR - style.framePadding.x, titleBarRect.min.y)
            }
            if (hasCollapseButton && style.windowMenuButtonPosition == Dir.Left) {
                collapseButtonPos.put(titleBarRect.min.x + padL - style.framePadding.x, titleBarRect.min.y)
                padL += buttonSz
            }

            // Collapse button (submitting first so it gets priority when choosing a navigation init fallback)
            if (hasCollapseButton)
                if (collapseButton(window.getId("#COLLAPSE"), collapseButtonPos))
                    window.wantCollapseToggle = true // Defer actual collapsing to next frame as we are too far in the Begin() function

            // Close button
            if (hasCloseButton)
                if (closeButton(window.getId("#CLOSE"), closeButtonPos))
                    pOpen!!.set(false)

            window.dc.navLayerCurrent = NavLayer.Main
            window.dc.navLayerCurrentMask = 1 shl NavLayer.Main
            window.dc.itemFlags = itemFlagsBackup

            // Title bar text (with: horizontal alignment, avoiding collapse/close button, optional "unsaved document" marker)
            // FIXME: Refactor text alignment facilities along with RenderText helpers, this is too much code..
            val UNSAVED_DOCUMENT_MARKER = "*"
            val markerSizeX = if (flags has Wf.UnsavedDocument) calcTextSize(UNSAVED_DOCUMENT_MARKER, -1, false).x else 0f
            val textSize = calcTextSize(name, -1, true) + Vec2(markerSizeX, 0f)

            // As a nice touch we try to ensure that centered title text doesn't get affected by visibility of Close/Collapse button,
            // while uncentered title text will still reach edges correct.
            if (padL > style.framePadding.x)
                padL += style.itemInnerSpacing.x
            if (padR > style.framePadding.x)
                padR += style.itemInnerSpacing.x
            if (style.windowTitleAlign.x > 0f && style.windowTitleAlign.x < 1f) {
                val centerness = saturate(1f - abs(style.windowTitleAlign.x - 0.5f) * 2f) // 0.0f on either edges, 1.0f on center
                val padExtend = min(max(padL, padR), titleBarRect.width - padL - padR - textSize.x)
                padL = padL max (padExtend * centerness)
                padR = padR max (padExtend * centerness)
            }

            val layoutR = Rect(titleBarRect.min.x + padL, titleBarRect.min.y, titleBarRect.max.x - padR, titleBarRect.max.y)
            val clipR = Rect(layoutR.min.x, layoutR.min.y, layoutR.max.x + style.itemInnerSpacing.x, layoutR.max.y)
            //if (g.IO.KeyCtrl) window->DrawList->AddRect(layout_r.Min, layout_r.Max, IM_COL32(255, 128, 0, 255)); // [DEBUG]
            renderTextClipped(layoutR.min, layoutR.max, name, -1, textSize, style.windowTitleAlign, clipR)

            if (flags has Wf.UnsavedDocument) {
                val markerPos = Vec2(max(layoutR.min.x, layoutR.min.x + (layoutR.width - textSize.x) * style.windowTitleAlign.x) + textSize.x, layoutR.min.y) + Vec2(2 - markerSizeX, 0f)
                val off = Vec2(0f, (-g.fontSize * 0.25f).i.f)
                renderTextClipped(markerPos + off, layoutR.max + off, UNSAVED_DOCUMENT_MARKER, -1, null, Vec2(0, style.windowTitleAlign.y), clipR)
            }
        }

        class ResizeGripDef(val cornerPosN: Vec2, val innerDir: Vec2, val angleMin12: Int, val angleMax12: Int)

        val resizeGripDef = arrayOf(
                ResizeGripDef(Vec2(1, 1), Vec2(-1, -1), 0, 3),  // Lower right
                ResizeGripDef(Vec2(0, 1), Vec2(+1, -1), 3, 6),  // Lower left
                ResizeGripDef(Vec2(0, 0), Vec2(+1, +1), 6, 9),  // Upper left
                ResizeGripDef(Vec2(1, 0), Vec2(-1, +1), 9, 12)) // Upper right

        class ResizeBorderDef(val innerDir: Vec2, val cornerPosN1: Vec2, val cornerPosN2: Vec2, val outerAngle: Float)

        val resizeBorderDef = arrayOf(
                ResizeBorderDef(Vec2(0, +1), Vec2(0, 0), Vec2(1, 0), glm.PIf * 1.5f), // Top
                ResizeBorderDef(Vec2(-1, 0), Vec2(1, 0), Vec2(1, 1), glm.PIf * 0.0f), // Right
                ResizeBorderDef(Vec2(0, -1), Vec2(1, 1), Vec2(0, 1), glm.PIf * 0.5f), // Bottom
                ResizeBorderDef(Vec2(+1, 0), Vec2(0, 1), Vec2(0, 0), glm.PIf * 1.0f))  // Left
    }
}