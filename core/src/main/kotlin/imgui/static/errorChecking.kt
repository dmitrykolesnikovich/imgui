package imgui.static

import imgui.*
import imgui.ImGui.end
import imgui.ImGui.io
import imgui.ImGui.mergedKeyModFlags
import imgui.ImGui.style
import imgui.api.g
import imgui.internal.classes.Window


//-----------------------------------------------------------------------------
// [SECTION] ERROR CHECKING
//-----------------------------------------------------------------------------

fun errorCheckNewFrameSanityChecks() {

    // Check user data
    // (We pass an error message in the assert expression to make it visible to programmers who are not using a debugger, as most assert handlers display their argument)
    assert(g.initialized)
    assert(io.deltaTime > 0f || g.frameCount == 0) { "Need a positive DeltaTime!" }
    assert(g.frameCount == 0 || g.frameCountEnded == g.frameCount) { "Forgot to call Render() or EndFrame() at the end of the previous frame?" }
    assert(io.displaySize.x >= 0f && io.displaySize.y >= 0f) { "Invalid DisplaySize value!" } // TODO glm
    assert(io.fonts.fonts.size > 0) { "Font Atlas not built. Did you call io.Fonts->GetTexDataAsRGBA32() / GetTexDataAsAlpha8()?" }
    assert(io.fonts.fonts[0].isLoaded) { "Font Atlas not built. Did you call io.Fonts->GetTexDataAsRGBA32() / GetTexDataAsAlpha8()?" }
    assert(style.curveTessellationTol > 0f) { "Invalid style setting!" }
    assert(style.circleSegmentMaxError > 0f) { "Invalid style setting!" }
    assert(style.alpha in 0f..1f) { "Invalid style setting!" } // Allows us to avoid a few clamps in color computations
    assert(style.windowMinSize.x >= 1f && style.windowMinSize.y >= 1f) { "Invalid style setting." } // TODO glm
    assert(style.windowMenuButtonPosition == Dir.None || style.windowMenuButtonPosition == Dir.Left || style.windowMenuButtonPosition == Dir.Right)
    for (n in 0 until Key.COUNT)
        assert(io.keyMap[n] >= -1 && io.keyMap[n] < io.keysDown.size) { "io.KeyMap[] contains an out of bound value (need to be 0..512, or -1 for unmapped key)" }

    // Check: required key mapping (we intentionally do NOT check all keys to not pressure user into setting up everything, but Space is required and was only added in 1.60 WIP)
    if (io.configFlags has ConfigFlag.NavEnableKeyboard)
        assert(io.keyMap[Key.Space.i] != -1) { "ImGuiKey_Space is not mapped, required for keyboard navigation." }

    // Check: the beta io.ConfigWindowsResizeFromEdges option requires backend to honor mouse cursor changes and set the ImGuiBackendFlags_HasMouseCursors flag accordingly.
    if (io.configWindowsResizeFromEdges && io.backendFlags hasnt BackendFlag.HasMouseCursors)
        io.configWindowsResizeFromEdges = false
}

fun errorCheckEndFrameSanityChecks() {

    // Verify that io.KeyXXX fields haven't been tampered with. Key mods should not be modified between NewFrame() and EndFrame()
    // One possible reason leading to this assert is that your backends update inputs _AFTER_ NewFrame().
    // It is known that when some modal native windows called mid-frame takes focus away, some backends such as GLFW will
    // send key release events mid-frame. This would normally trigger this assertion and lead to sheared inputs.
    // We silently accommodate for this case by ignoring/ the case where all io.KeyXXX modifiers were released (aka key_mod_flags == 0),
    // while still correctly asserting on mid-frame key press events.
    val keyModFlags = mergedKeyModFlags
    assert(keyModFlags == 0 || g.io.keyMods == keyModFlags) { "Mismatching io.KeyCtrl/io.KeyShift/io.KeyAlt/io.KeySuper vs io.KeyMods" }

    // Recover from errors
    //ErrorCheckEndFrameRecover();

    // Report when there is a mismatch of Begin/BeginChild vs End/EndChild calls. Important: Remember that the Begin/BeginChild API requires you
    // to always call End/EndChild even if Begin/BeginChild returns false! (this is unfortunately inconsistent with most other Begin* API).
    if (g.currentWindowStack.size != 1)
        if (g.currentWindowStack.size > 1) {
            assert(g.currentWindowStack.size == 1) { "Mismatched Begin/BeginChild vs End/EndChild calls: did you forget to call End/EndChild?" }
            while (g.currentWindowStack.size > 1)
                end()
        } else
            assert(g.currentWindowStack.size == 1) { "Mismatched Begin/BeginChild vs End/EndChild calls: did you call End/EndChild too much?" }

    assert(g.groupStack.isEmpty()) { "Missing EndGroup call!" }
}