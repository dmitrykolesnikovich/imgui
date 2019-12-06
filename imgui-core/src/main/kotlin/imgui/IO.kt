package imgui

import com.sun.jdi.VirtualMachine
import glm_.glm
import glm_.i
import glm_.vec2.Vec2
import glm_.vec2.Vec2i
import glm_.vec4.Vec4
import imgui.ImGui.styleColorsClassic
import imgui.api.g
import imgui.internal.*
import imgui.static.getClipboardTextFn_DefaultImpl
import imgui.static.imeSetInputScreenPosFn_Win32
import imgui.static.setClipboardTextFn_DefaultImpl
import kool.Ptr
import org.lwjgl.system.MemoryUtil.NULL
import org.lwjgl.system.Platform
import uno.glfw.HWND
import java.nio.ByteBuffer
import java.util.*
import kotlin.collections.ArrayList
import kotlin.math.max

/** -----------------------------------------------------------------------------
 *  IO
 *  Communicate most settings and inputs/outputs to Dear ImGui using this structure.
 *  Access via ::io. Read 'Programmer guide' section in .cpp file for general usage.
 *  ----------------------------------------------------------------------------- */
class IO(sharedFontAtlas: FontAtlas?) {

    //------------------------------------------------------------------
    // Configuration (fill once)
    //------------------------------------------------------------------

    /** See ConfigFlags enum. Set by user/application. Gamepad/keyboard navigation options, etc. */
    var configFlags: ConfigFlags = ConfigFlag.None.i
    /** Set ImGuiBackendFlags_ enum. Set by imgui_impl_xxx files or custom back-end to communicate features supported by the back-end. */
    var backendFlags: BackendFlags = BackendFlag.None.i
    /** Main display size, in pixels.   */
    var displaySize = Vec2i(-1)
    /** Time elapsed since last frame, in seconds.  */
    var deltaTime = 1f / 60f
    /** Minimum time between saving positions/sizes to .ini file, in seconds.   */
    var iniSavingRate = 5f
    /** Path to .ini file. Set NULL to disable automatic .ini loading/saving, if e.g. you want to manually load/save from memory. */
    var iniFilename: String? = "imgui.ini"
    /** Path to .log file (default parameter to ImGui::LogToFile when no file is specified).    */
    var logFilename = "imgui_log.txt"
    /** Time for a double-click, in seconds.    */
    var mouseDoubleClickTime = 0.3f
    /** Distance threshold to stay in to validate a double-click, in pixels.    */
    var mouseDoubleClickMaxDist = 6f
    /** Distance threshold before considering we are dragging.   */
    var mouseDragThreshold = 6f
    /** Map of indices into the KeysDown[512] entries array which represent your "native" keyboard state.   */
    var keyMap = IntArray(Key.COUNT) { -1 }
    /** When holding a key/button, time before it starts repeating, in seconds (for buttons in Repeat mode, etc.).  */
    var keyRepeatDelay = 0.275f
    /** When holding a key/button, rate at which it repeats, in seconds.    */
    var keyRepeatRate = 0.05f

//    void*         UserData;                 // = NULL               // Store your own data for retrieval by callbacks.

    /** Font atlas: load, rasterize and pack one or more fonts into a single texture.    */
    val fonts = sharedFontAtlas ?: FontAtlas()
    /** Global scale all fonts  */
    var fontGlobalScale = 1f
    /** Allow user scaling text of individual window with CTRL+Wheel.   */
    var fontAllowUserScaling = false
    /** Font to use on NewFrame(). Use NULL to useMouseDragThreshold s Fonts->Fonts[0].    */
    var fontDefault: Font? = null
    /** For retina display or other situations where window coordinates are different from framebuffer coordinates.
     *  This generally ends up in ImDrawData::FramebufferScale. */
    var displayFramebufferScale = Vec2(1f)

    //------------------------------------------------------------------
    // Miscellaneous options
    //------------------------------------------------------------------

    /** Request ImGui to draw a mouse cursor for you (if you are on a platform without a mouse cursor). */
    var mouseDrawCursor = false
    /** = defined(__APPLE__), OS X style: Text editing cursor movement using Alt instead of Ctrl, Shortcuts using
     *  Cmd/Super instead of Ctrl, Line/Text Start and End using Cmd + Arrows instead of Home/End, Double click selects
     *  by word instead of selecting whole text, Multi-selection in lists uses Cmd/Super instead of Ctrl
     *  (was called io.OptMacOSXBehaviors prior to 1.63) */
    var configMacOSXBehaviors = false  // JVM TODO
    /** Set to false to disable blinking cursor, for users who consider it distracting. (was called: io.OptCursorBlink prior to 1.63) */
    var configInputTextCursorBlink = true
    /** Enable resizing of windows from their edges and from the lower-left corner.
     *  This requires (io.backendFlags has BackendFlags.HasMouseCursors) because it needs mouse cursor feedback.
     *  (This used to be WindowFlag.ResizeFromAnySide flag) */
    var configWindowsResizeFromEdges = true
    /** [BETA] Set to true to only allow moving windows when clicked+dragged from the title bar. Windows without a title bar are not affected. */
    var configWindowsMoveFromTitleBarOnly = false
    /** [BETA] Compact window memory usage when unused. Set to -1.0f to disable. */
    var configWindowsMemoryCompactTimer = 60f

    //------------------------------------------------------------------
    // User Functions
    //------------------------------------------------------------------

    // Optional: Platform/Renderer back-end name (informational only! will be displayed in About Window)
    // Optional: Platform/Renderer back-end name (informational only! will be displayed in About Window) + User data for back-end/wrappers to store their own stuff.
    var backendPlatformName: String? = null
    var backendRendererName: String? = null
    var backendPlatformUserData: Any? = null
    var backendRendererUserData: Any? = null
    var backendLanguageUserData: Any? = null

    // Optional: Access OS clipboard
    // (default to use native Win32 clipboard on Windows, otherwise uses a private clipboard. Override to access OS clipboard on other architectures)
    var getClipboardTextFn: (() -> String?)? = getClipboardTextFn_DefaultImpl
    var setClipboardTextFn: ((String) -> Unit)? = setClipboardTextFn_DefaultImpl
    var clipboardUserData: Ptr = NULL

    //    // Optional: override memory allocations. MemFreeFn() may be called with a NULL pointer.
//    // (default to posix malloc/free)
//    void*       (*MemAllocFn)(size_t sz);
//    void        (*MemFreeFn)(void* ptr);
//
    // Optional: Notify OS Input Method Editor of the screen position of your cursor for text input position (e.g. when using Japanese/Chinese IME in Windows)
    // (default to use native imm32 api on Windows)
    val imeSetInputScreenPosFn: ((x: Int, y: Int) -> Unit)? = imeSetInputScreenPosFn_Win32.takeIf { Platform.get() == Platform.WINDOWS }
    /** (Windows) Set this to your HWND to get automatic IME cursor positioning.    */
    var imeWindowHandle: HWND = HWND(NULL)

    //------------------------------------------------------------------
    // Input - Fill before calling NewFrame()
    //------------------------------------------------------------------

    /** Mouse position, in pixels (set to -1,-1 if no mouse / on another screen, etc.)  */
    var mousePos = Vec2(-Float.MAX_VALUE)
    /** Mouse buttons: 0=left, 1=right, 2=middle + extras. ImGui itself mostly only uses left button (BeginPopupContext** are
    using right button). Others buttons allows us to track if the mouse is being used by your application +
    available to user as a convenience via IsMouse** API.   */
    val mouseDown = BooleanArray(5)
    /** Mouse wheel Vertical: 1 unit scrolls about 5 lines text. */
    var mouseWheel = 0f
    /** Mouse wheel Horizontal. Most users don't have a mouse with an horizontal wheel, may not be filled by all back-ends.   */
    var mouseWheelH = 0f
    /** Keyboard modifier pressed: Control  */
    var keyCtrl = false
    /** Keyboard modifier pressed: Shift    */
    var keyShift = false
    /** Keyboard modifier pressed: Alt  */
    var keyAlt = false
    /** Keyboard modifier pressed: Cmd/Super/Windows    */
    var keySuper = false
    /** Keyboard keys that are pressed (ideally left in the "native" order your engine has access to keyboard keys,
     *  so you can use your own defines/enums for keys).   */
    val keysDown = BooleanArray(512)
    /** Gamepad inputs. Cleared back to zero by EndFrame(). Keyboard keys will be auto-mapped and be written here by NewFrame().   */
    val navInputs = FloatArray(NavInput.COUNT)

    // Functions

    /** Queue new character input */
    fun addInputCharacter(c: Char) {
        if (c.i in 1..65535)
            inputQueueCharacters.add(c)
    }

//    IMGUI_API void  AddInputCharactersUTF8(const char* str);    // Queue new characters input from an UTF-8 string

    /** Clear the text input buffer manually */
    fun clearInputCharacters() = inputQueueCharacters.clear()


    //------------------------------------------------------------------
    // Output - Retrieve after calling NewFrame()
    //------------------------------------------------------------------

    /** When io.wantCaptureMouse is true, imgui will use the mouse inputs, do not dispatch them to your main game/application
     *  (in both cases, always pass on mouse inputs to imgui). (e.g. unclicked mouse is hovering over an imgui window,
     *  widget is active, mouse was clicked over an imgui window, etc.). */
    var wantCaptureMouse = false
    /** When io.wantCaptureKeyboard is true, imgui will use the keyboard inputs, do not dispatch them to your main game/application
     *  (in both cases, always pass keyboard inputs to imgui).
     *  (e.g. InputText active, or an imgui window is focused and navigation is enabled, etc.). */
    var wantCaptureKeyboard = false
    /** Mobile/console: when IO.wantTextInput is true, you may display an on-screen keyboard. This is set by ImGui when
     *  it wants textual keyboard input to happen (e.g. when a InputText widget is active). */
    var wantTextInput = false
    /** MousePos has been altered, back-end should reposition mouse on next frame.
     *  Set only when ConfigFlag.NavEnableSetMousePos flag is enabled in IO.configFlags.    */
    var wantSetMousePos = false
    /** When manual .ini load/save is active (io.IniFilename == NULL), this will be set to notify your application that
     *  you can call SaveIniSettingsToMemory() and save yourself. IMPORTANT: You need to clear io.WantSaveIniSettings yourself. */
    var wantSaveIniSettings = false
    /** Directional navigation is currently allowed (will handle KeyNavXXX events) = a window is focused and it doesn't
     *  use the WindowFlag.NoNavInputs flag.   */
    var navActive = false
    /** Directional navigation is visible and allowed (will handle KeyNavXXX events). */
    var navVisible = false
    /** Application framerate estimation, in frame per second. Solely for convenience. Rolling average estimation based
    on IO.DeltaTime over 120 frames */
    var framerate = 0f
    /** Number of active memory allocations */
    val metricsAllocs get() = Debug.instanceCounts
    /** Vertices output during last call to Render()    */
    var metricsRenderVertices = 0
    /** Indices output during last call to Render() = number of triangles * 3   */
    var metricsRenderIndices = 0
    /** Number of visible windows */
    var metricsRenderWindows = 0
    /** Number of active windows */
    var metricsActiveWindows = 0
    /** Number of active allocations, updated by MemAlloc/MemFree based on current context. May be off if you have multiple imgui contexts. */
    var metricsActiveAllocations = 0
    /** Mouse delta. Note that this is zero if either current or previous position are invalid (-FLOAT_MAX_VALUE), so a
    disappearing/reappearing mouse won't have a huge delta.   */
    var mouseDelta = Vec2()


    //------------------------------------------------------------------
    // [Private] ImGui will maintain those fields. Forward compatibility not guaranteed!
    //------------------------------------------------------------------

    /** Previous mouse position (note that MouseDelta is not necessary == MousePos-MousePosPrev, in case either position is invalid)   */
    var mousePosPrev = Vec2(-Float.MAX_VALUE)
    /** Position at time of clicking    */
    val mouseClickedPos = Array(5) { Vec2() }
    /** Time of last click (used to figure out double-click)    */
    val mouseClickedTime = DoubleArray(5)
    /** Mouse button went from !Down to Down    */
    val mouseClicked = BooleanArray(5)
    /** Has mouse button been double-clicked?    */
    val mouseDoubleClicked = BooleanArray(5)
    /** Mouse button went from Down to !Down    */
    val mouseReleased = BooleanArray(5)
    /** Track if button was clicked inside a dear imgui window. We don't request mouse capture from the application if click started outside ImGui bounds.   */
    var mouseDownOwned = BooleanArray(5)
    /** Track if button down was a double-click */
    var mouseDownWasDoubleClick = BooleanArray(5)
    /** Duration the mouse button has been down (0.0f == just clicked)  */
    val mouseDownDuration = FloatArray(5) { -1f }
    /** Previous time the mouse button has been down    */
    val mouseDownDurationPrev = FloatArray(5) { -1f }
    /** Maximum distance, absolute, on each axis, of how much mouse has traveled from the clicking point    */
    val mouseDragMaxDistanceAbs = Array(5) { Vec2() }
    /** Squared maximum distance of how much mouse has traveled from the clicking point */
    val mouseDragMaxDistanceSqr = FloatArray(5)
    /** Duration the keyboard key has been down (0.0f == just pressed)  */
    val keysDownDuration = FloatArray(512) { -1f }
    /** Previous duration the key has been down */
    val keysDownDurationPrev = FloatArray(512) { -1f }

    val navInputsDownDuration = FloatArray(NavInput.COUNT) { -1f }

    val navInputsDownDurationPrev = FloatArray(NavInput.COUNT)
    /** Queue of _characters_ input (obtained by platform back-end). Fill using AddInputCharacter() helper. */
    val inputQueueCharacters = ArrayList<Char>()
}


//-----------------------------------------------------------------------------
// Misc data structures
//-----------------------------------------------------------------------------


/** Shared state of InputText(), passed as an argument to your callback when a ImGuiInputTextFlags_Callback* flag is used.
 *  The callback function should return 0 by default.
 *  Callbacks (follow a flag name and see comments in ImGuiInputTextFlags_ declarations for more details)
 *  - ImGuiInputTextFlags_CallbackCompletion:  Callback on pressing TAB
 *  - ImGuiInputTextFlags_CallbackHistory:     Callback on pressing Up/Down arrows
 *  - ImGuiInputTextFlags_CallbackAlways:      Callback on each iteration
 *  - ImGuiInputTextFlags_CallbackCharFilter:  Callback on character inputs to replace or discard them.
 *                                              Modify 'EventChar' to replace or discard, or return 1 in callback to discard.
 *  - ImGuiInputTextFlags_CallbackResize:      Callback on buffer capacity changes request (beyond 'buf_size' parameter value),
 *                                              allowing the string to grow.
 *
 *  Helper functions for text manipulation.
 *  Use those function to benefit from the CallbackResize behaviors. Calling those function reset the selection. */
class InputTextCallbackData {

    /** One ImGuiInputTextFlags_Callback*    // Read-only */
    var eventFlag: InputTextFlags = 0
    /** What user passed to InputText()      // Read-only */
    var flags: InputTextFlags = 0
    /** What user passed to InputText()      // Read-only */
    var userData: Any? = null

    /*  Arguments for the different callback events
     *  - To modify the text buffer in a callback, prefer using the InsertChars() / DeleteChars() function. InsertChars() will take care of calling the resize callback if necessary.
     *  - If you know your edits are not going to resize the underlying buffer allocation, you may modify the contents of 'Buf[]' directly. You need to update 'BufTextLen' accordingly (0 <= BufTextLen < BufSize) and set 'BufDirty'' to true so InputText can update its internal state. */

    /** Character input                     Read-write   [CharFilter] Replace character with another one, or set to zero to drop.
     *                                      return 1 is equivalent to setting EventChar=0; */
    var eventChar = NUL
    /** Key pressed (Up/Down/TAB)           Read-only    [Completion,History] */
    var eventKey = Key.Tab
    /** Text buffer                 Read-write   [Resize] Can replace pointer / [Completion,History,Always] Only write to pointed data, don't replace the actual pointer! */
    var buf = CharArray(0)
    /** JVM custom, current buf pointer */
    var bufPtr = 0
    /** Text length (in bytes)        Read-write   [Resize,Completion,History,Always] */
    var bufTextLen = 0
    /** Buffer size (in bytes) = capacity + 1    Read-only    [Resize,Completion,History,Always] */
    var bufSize = 0
    /** Set if you modify Buf/BufTextLen!  Write        [Completion,History,Always] */
    var bufDirty = false
    /** Read-write   [Completion,History,Always] */
    var cursorPos = 0
    /** Read-write   [Completion,History,Always] == to SelectionEnd when no selection) */
    var selectionStart = 0
    /** Read-write   [Completion,History,Always] */
    var selectionEnd = 0


    /** Public API to manipulate UTF-8 text
     *  We expose UTF-8 to the user (unlike the STB_TEXTEDIT_* functions which are manipulating wchar)
     *  FIXME: The existence of this rarely exercised code path is a bit of a nuisance. */
    fun deleteChars(pos: Int, bytesCount: Int) {
        assert(pos + bytesCount <= bufTextLen)
        var dst = pos
        var src = pos + bytesCount
        var c = buf[src++]
        while (c != NUL) {
            buf[dst++] = c
            c = buf.getOrElse(src++) { NUL }
        }
        if (cursorPos + bytesCount >= pos)
            cursorPos -= bytesCount
        else if (cursorPos >= pos)
            cursorPos = pos
        selectionEnd = cursorPos
        selectionStart = cursorPos
        bufDirty = true
        bufTextLen -= bytesCount
    }

    fun insertChars(pos: Int, newText: String, newTextEnd: Int? = null) = insertChars(pos, newText.toCharArray(), newTextEnd)

    fun insertChars(pos: Int, newText: CharArray, newTextEnd: Int? = null) {

        val isResizable = flags has InputTextFlag.CallbackResize
        val newTextLen = newTextEnd ?: newText.strlen
        if (newTextLen + bufTextLen >= bufSize) {

            if (!isResizable) return

            // Contrary to STB_TEXTEDIT_INSERTCHARS() this is working in the UTF8 buffer, hence the midly similar code (until we remove the U16 buffer alltogether!)
            val editState = g.inputTextState
            assert(editState.id != 0 && g.activeId == editState.id)
            assert(buf === editState.textA)
            val newBufSize = bufTextLen + glm.clamp(newTextLen * 4, 32, max(256, newTextLen))
            val t = CharArray(newBufSize)
            System.arraycopy(editState.textA, 0, t, 0, editState.textA.size)
            editState.textA = t
            buf = editState.textA
            editState.bufCapacityA = newBufSize
            bufSize = newBufSize
        }

        if (bufTextLen != pos)
            for (i in 0 until bufTextLen - pos)
                buf[pos + newTextLen + i] = buf[pos + i]
        for (i in 0 until newTextLen)
            buf[pos + i] = newText[i]

        if (cursorPos >= pos)
            cursorPos += newTextLen
        selectionEnd = cursorPos
        selectionStart = cursorPos
        bufDirty = true
        bufTextLen += newTextLen
    }

    val hasSelection: Boolean
        get() = selectionStart != selectionEnd
}

class SizeCallbackData(
        /** Read-only.   What user passed to SetNextWindowSizeConstraints() */
        var userData: Any? = null,
        /** Read-only.   Window position, for reference.    */
        val pos: Vec2 = Vec2(),
        /** Read-only.   Current window size.   */
        val currentSize: Vec2 = Vec2(),
        /** Read-write.  Desired size, based on user's mouse position. Write to this field to restrain resizing.    */
        val desiredSize: Vec2 = Vec2())

/** Data payload for Drag and Drop operations: AcceptDragDropPayload(), GetDragDropPayload() */
class Payload {
    // Members

    /** Data (copied and owned by dear imgui) */
    var data: ByteBuffer? = null
    /** Data size */
    var dataSize = 0

    // [Internal]

    /** Source item id */
    var sourceId: ID = 0
    /** Source parent id (if available) */
    var sourceParentId: ID = 0
    /** Data timestamp */
    var dataFrameCount = -1
    /** Data type tag (short user-supplied string, 32 characters max) */
    val dataType = CharArray(32)
    val dataTypeS get() = String(dataType)
    /** Set when AcceptDragDropPayload() was called and mouse has been hovering the target item (nb: handle overlapping drag targets) */
    var preview = false
    /** Set when AcceptDragDropPayload() was called and mouse button is released over the target item. */
    var delivery = false

    fun clear() {
        sourceParentId = 0
        sourceId = 0
        data = null
        dataSize = 0
        dataType.fill(NUL)
        dataFrameCount = -1
        delivery = false
        preview = false
    }

    fun isDataType(type: String) = dataFrameCount != -1 && type cmp dataType
}

// for IO.keyMap

operator fun IntArray.set(index: Key, value: Int) {
    this[index.i] = value
}

operator fun IntArray.get(index: Key) = get(index.i)

// for IO.navInputs

operator fun FloatArray.set(index: NavInput, value: Float) {
    this[index.i] = value
}

operator fun FloatArray.get(index: NavInput) = get(index.i)

/** -----------------------------------------------------------------------------
 *  Style
 *  You may modify the ImGui::GetStyle() main instance during initialization and before NewFrame().
 *  During the frame, use ImGui::PushStyleVar(ImGuiStyleVar_XXXX)/PopStyleVar() to alter the main style values,
 *  and ImGui::PushStyleColor(ImGuiCol_XXX)/PopStyleColor() for colors.
 *  ----------------------------------------------------------------------------- */
class Style {

    /**  Global alpha applies to everything in Dear ImGui.    */
    var alpha = 1f
    /** Padding within a window. */
    var windowPadding = Vec2(8)
    /** Radius of window corners rounding. Set to 0.0f to have rectangular windows.  */
    var windowRounding = 7f
    /** Thickness of border around windows. Generally set to 0f or 1f. (Other values are not well tested and more CPU/GPU costly).  */
    var windowBorderSize = 1f
    /** Minimum window size. This is a global setting. If you want to constraint individual windows, use SetNextWindowSizeConstraints(). */
    var windowMinSize = Vec2i(32)
    /** Alignment for title bar text    */
    var windowTitleAlign = Vec2(0f, 0.5f)
    /** Side of the collapsing/docking button in the title bar (None/Left/Right). Defaults to ImGuiDir_Left. */
    var windowMenuButtonPosition = Dir.Left
    /** Radius of child window corners rounding. Set to 0.0f to have rectangular child windows.  */
    var childRounding = 0f
    /** Thickness of border around child windows. Generally set to 0f or 1f. (Other values are not well tested and more CPU/GPU costly). */
    var childBorderSize = 1f
    /** Radius of popup window corners rounding. (Note that tooltip windows use WindowRounding) */
    var popupRounding = 0f
    /** Thickness of border around popup/tooltip windows. Generally set to 0f or 1f.
     *  (Other values are not well tested and more CPU/GPU costly). */
    var popupBorderSize = 1f
    /** Padding within a framed rectangle (used by most widgets).    */
    var framePadding = Vec2(4, 3)
    /** Radius of frame corners rounding. Set to 0.0f to have rectangular frames (used by most widgets).    */
    var frameRounding = 0f
    /** Thickness of border around frames. Generally set to 0f or 1f. (Other values are not well tested and more CPU/GPU costly).    */
    var frameBorderSize = 0f
    /** Horizontal and vertical spacing between widgets/lines.   */
    var itemSpacing = Vec2(8, 4)
    /** Horizontal and vertical spacing between within elements of a composed widget (e.g. a slider and its label).  */
    var itemInnerSpacing = Vec2(4)
    /** Expand reactive bounding box for touch-based system where touch position is not accurate enough. Unfortunately
     *  we don't sort widgets so priority on overlap will always be given to the first widget. So don't grow this too much!   */
    var touchExtraPadding = Vec2()
    /** Horizontal spacing when e.g. entering a tree node. Generally == (FontSize + FramePadding.x*2).  */
    var indentSpacing = 21f
    /** Minimum horizontal spacing between two columns. Preferably > (FramePadding.x + 1).  */
    var columnsMinSpacing = 6f
    /** Width of the vertical scrollbar, Height of the horizontal scrollbar. */
    var scrollbarSize = 14f
    /** Radius of grab corners rounding for scrollbar.   */
    var scrollbarRounding = 9f
    /** Minimum width/height of a grab box for slider/scrollbar */
    var grabMinSize = 10f
    /** Radius of grabs corners rounding. Set to 0.0f to have rectangular slider grabs. */
    var grabRounding = 0f
    /** Radius of upper corners of a tab. Set to 0.0f to have rectangular tabs. */
    var tabRounding = 4f
    /** Thickness of border around tabs. */
    var tabBorderSize = 0f
    /** Side of the color button in the ColorEdit4 widget (left/right). Defaults to ImGuiDir_Right. */
    var colorButtonPosition = Dir.Right
    /** Alignment of button text when button is larger than text. Defaults to (0.5f, 0.5f) (centered).   */
    var buttonTextAlign = Vec2(0.5f)
    /** Alignment of selectable text when selectable is larger than text. Defaults to (0,0) for top-left alignment. */
    var selectableTextAlign = Vec2()
    /** Window position are clamped to be visible within the display area by at least this amount.
     *  Only applies to regular windows.    */
    var displayWindowPadding = Vec2(19)
    /** If you cannot see the edges of your screen (e.g. on a TV) increase the safe area padding. Apply to popups/tooltips
     *  as well regular windows.  NB: Prefer configuring your TV sets correctly!   */
    var displaySafeAreaPadding = Vec2(3)
    /** Scale software rendered mouse cursor (when io.MouseDrawCursor is enabled). May be removed later.    */
    var mouseCursorScale = 1f
    /** Enable anti-aliasing on lines/borders. Disable if you are really short on CPU/GPU.  */
    var antiAliasedLines = true
    /**  Enable anti-aliasing on filled shapes (rounded rectangles, circles, etc.)  */
    var antiAliasedFill = true
    /** Tessellation tolerance when using pathBezierCurveTo() without a specific number of segments.
     *  Decrease for highly tessellated curves (higher quality, more polygons), increase to reduce quality. */
    var curveTessellationTol = 1.25f

    val colors = ArrayList<Vec4>()

    /** JVM IMGUI   */
    var locale: Locale = Locale.US
//    var locale: Locale = Locale("no", "NO")
//    val locale = Locale.getDefault()

    init {
        styleColorsClassic(this)
    }

    constructor()

    constructor(style: Style) {
        alpha = style.alpha
        windowPadding put style.windowPadding
        windowRounding = style.windowRounding
        windowBorderSize = style.windowBorderSize
        windowMinSize put style.windowMinSize
        windowTitleAlign put style.windowTitleAlign
        childRounding = style.childRounding
        childBorderSize = style.childBorderSize
        popupRounding = style.popupRounding
        popupBorderSize = style.popupBorderSize
        framePadding put style.framePadding
        frameRounding = style.frameRounding
        frameBorderSize = style.frameBorderSize
        itemSpacing put style.itemSpacing
        itemInnerSpacing put style.itemInnerSpacing
        touchExtraPadding put style.touchExtraPadding
        indentSpacing = style.indentSpacing
        columnsMinSpacing = style.columnsMinSpacing
        scrollbarSize = style.scrollbarSize
        scrollbarRounding = style.scrollbarRounding
        grabMinSize = style.grabMinSize
        grabRounding = style.grabRounding
        buttonTextAlign put style.buttonTextAlign
        displayWindowPadding put style.displayWindowPadding
        displaySafeAreaPadding put style.displaySafeAreaPadding
        antiAliasedLines = style.antiAliasedLines
        antiAliasedFill = style.antiAliasedFill
        curveTessellationTol = style.curveTessellationTol
        style.colors.forEach { colors.add(Vec4(it)) }
//        locale = style.locale
    }

    /** To scale your entire UI (e.g. if you want your app to use High DPI or generally be DPI aware) you may use this
     *  helper function. Scaling the fonts is done separately and is up to you.
     *  Tips: if you need to change your scale multiple times, prefer calling this on a freshly initialized Style
     *  structure rather than scaling multiple times (because floating point multiplications are lossy).    */
    fun scaleAllSizes(scaleFactor: Float) {
        windowPadding = floor(windowPadding * scaleFactor)
        windowRounding = floor(windowRounding * scaleFactor)
        windowMinSize.put(floor(windowMinSize.x * scaleFactor), floor(windowMinSize.y * scaleFactor))
        childRounding = floor(childRounding * scaleFactor)
        popupRounding = floor(popupRounding * scaleFactor)
        framePadding = floor(framePadding * scaleFactor)
        frameRounding = floor(frameRounding * scaleFactor)
        itemSpacing = floor(itemSpacing * scaleFactor)
        itemInnerSpacing = floor(itemInnerSpacing * scaleFactor)
        touchExtraPadding = floor(touchExtraPadding * scaleFactor)
        indentSpacing = floor(indentSpacing * scaleFactor)
        columnsMinSpacing = floor(columnsMinSpacing * scaleFactor)
        scrollbarSize = floor(scrollbarSize * scaleFactor)
        scrollbarRounding = floor(scrollbarRounding * scaleFactor)
        grabMinSize = floor(grabMinSize * scaleFactor)
        grabRounding = floor(grabRounding * scaleFactor)
        tabRounding = floor(tabRounding * scaleFactor)
        displayWindowPadding = floor(displayWindowPadding * scaleFactor)
        displaySafeAreaPadding = floor(displaySafeAreaPadding * scaleFactor)
        mouseCursorScale = floor(mouseCursorScale * scaleFactor)
    }
}

object Debug {

    var vm: VirtualMachine? = null
    /** Instance count update interval in seconds   */
    var updateInterval = 5
    private var lastUpdate = System.nanoTime()

    init {
        try {
//            val ac: AttachingConnector = Bootstrap.virtualMachineManager().attachingConnectors().find {
//                it.javaClass.name.toLowerCase().indexOf("socket") != -1
//            } ?: throw Error("No socket attaching connector found")
//            val connectArgs = HashMap<String, Connector.Argument>(ac.defaultArguments())
//            connectArgs["hostname"]!!.setValue("127.0.0.1")
//            connectArgs["port"]!!.setValue(3001.toString())
//            connectArgs["timeout"]!!.setValue("3000")
//            vm = ac.attach(connectArgs)
        } catch (error: Exception) {
            System.err.println("Couldn't retrieve the number of allocations, $error")
        }
    }

    val instanceCounts: Long
        get() {
            val now = System.nanoTime()
            if ((now - lastUpdate) > updateInterval * 1e9) {
                cachedInstanceCounts = countInstances()
                lastUpdate = now
            }
            return cachedInstanceCounts
        }

    private fun countInstances() = vm?.instanceCounts(vm?.allClasses())?.sum() ?: -1

    private var cachedInstanceCounts = countInstances()
}

/** for style.colors    */
operator fun ArrayList<Vec4>.get(idx: Col) = this[idx.i]

operator fun ArrayList<Vec4>.set(idx: Col, vec: Vec4) = this[idx.i] put vec

operator fun MutableMap<Int, Float>.set(key: Int, value: Int) = set(key, glm.intBitsToFloat(value))
operator fun MutableMap<Int, Float>.set(key: Int, value: ColorEditFlag) = set(key, glm.intBitsToFloat(value.i))
//operator fun MutableMap<Int, Float>.getInt(key: Int) = 0 TODO float