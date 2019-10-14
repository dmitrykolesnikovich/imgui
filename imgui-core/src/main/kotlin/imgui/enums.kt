package imgui

import imgui.ImGui.getNavInputAmount
import imgui.ImGui.io
import imgui.ImGui.isKeyDown
import imgui.ImGui.isKeyPressed
import imgui.internal.InputReadMode


//-----------------------------------------------------------------------------
// Flags & Enumerations
//-----------------------------------------------------------------------------


/** Flags: for Begin(), BeginChild()    */
enum class WindowFlag(@JvmField val i: WindowFlags) {

    None(0),
    /** Disable title-bar   */
    NoTitleBar(1 shl 0),
    /** Disable user resizing with the lower-right grip */
    NoResize(1 shl 1),
    /** Disable user moving the window  */
    NoMove(1 shl 2),
    /** Disable scrollbars (window can still scroll with mouse or programmatically)  */
    NoScrollbar(1 shl 3),
    /** Disable user vertically scrolling with mouse wheel. On child window, mouse wheel will be forwarded to the parent
     *  unless noScrollbar is also set.  */
    NoScrollWithMouse(1 shl 4),
    /** Disable user collapsing window by double-clicking on it */
    NoCollapse(1 shl 5),
    /** Resize every window to its content every frame  */
    AlwaysAutoResize(1 shl 6),
    /** Disable drawing background color (WindowBg, etc.) and outside border. Similar as using SetNextWindowBgAlpha(0.0f).(1 shl 7) */
    NoBackground(1 shl 7),
    /** Never load/save settings in .ini file   */
    NoSavedSettings(1 shl 8),
    /** Disable catching mouse or keyboard inputs   */
    NoMouseInputs(1 shl 9),
    /** Has a menu-bar  */
    MenuBar(1 shl 10),
    /** Allow horizontal scrollbar to appear (off by default). You may use SetNextWindowContentSize(ImVec2(width),0.0f));
     *  prior to calling Begin() to specify width. Read code in imgui_demo in the "Horizontal Scrolling" section.    */
    HorizontalScrollbar(1 shl 11),
    /** Disable taking focus when transitioning from hidden to visible state    */
    NoFocusOnAppearing(1 shl 12),
    /** Disable bringing window to front when taking focus (e.g. clicking on it or programmatically giving it focus) */
    NoBringToFrontOnFocus(1 shl 13),
    /** Always show vertical scrollbar (even if ContentSize.y < Size.y) */
    AlwaysVerticalScrollbar(1 shl 14),
    /** Always show horizontal scrollbar (even if ContentSize.x < Size.x)   */
    AlwaysHorizontalScrollbar(1 shl 15),
    /** Ensure child windows without border uses style.WindowPadding (ignored by default for non-bordered child windows),
     *  because more convenient)  */
    AlwaysUseWindowPadding(1 shl 16),
    /** [BETA] Enable resize from any corners and borders. Your back-end needs to honor the different values of io.mouseCursor set by imgui.
     *  Set io.OptResizeWindowsFromEdges and make sure mouse cursors are supported by back-end (io.BackendFlags & ImGuiBackendFlags_HasMouseCursors) */
    // ResizeFromAnySide(1 shl 17),
    /** No gamepad/keyboard navigation within the window    */
    NoNavInputs(1 shl 18),
    /** No focusing toward this window with gamepad/keyboard navigation (e.g. skipped by CTRL+TAB)  */
    NoNavFocus(1 shl 19),
    /** Append '*' to title without affecting the ID, as a convenience to avoid using the ### operator.
     *  When used in a tab/docking context, tab is selected on closure and closure is deferred by one frame
     *  to allow code to cancel the closure (with a confirmation popup, etc.) without flicker. */
    UnsavedDocument(1 shl 20),

    NoNav(NoNavInputs or NoNavFocus),

    NoDecoration(NoTitleBar or NoResize or NoScrollbar or NoCollapse),

    NoInputs(NoMouseInputs or NoNavInputs or NoNavFocus),

    // [Internal]

    /** [BETA] Allow gamepad/keyboard navigation to cross over parent border to this child (only use on child that have no scrolling!)   */
    NavFlattened(1 shl 23),
    /** Don't use! For internal use by BeginChild() */
    ChildWindow(1 shl 24),
    /** Don't use! For internal use by BeginTooltip()   */
    Tooltip(1 shl 25),
    /** Don't use! For internal use by BeginPopup() */
    Popup(1 shl 26),
    /** Don't use! For internal use by BeginPopupModal()    */
    Modal(1 shl 27),
    /** Don't use! For internal use by BeginMenu()  */
    ChildMenu(1 shl 28);

    infix fun or(b: WindowFlag): WindowFlags = i or b.i
    infix fun or(b: Int): WindowFlags = i or b
}

infix fun Int.and(b: WindowFlag): WindowFlags = and(b.i)
infix fun Int.or(b: WindowFlag): WindowFlags = or(b.i)
infix fun Int.has(b: WindowFlag) = and(b.i) != 0
infix fun Int.hasnt(b: WindowFlag) = and(b.i) == 0
infix fun Int.wo(b: WindowFlag): WindowFlags = and(b.i.inv())

/** Flags for ImGui::InputText(), InputTextMultiline()    */
enum class InputTextFlag(@JvmField val i: InputTextFlags) { // TODO Int -> *flags the others enum

    None(0),
    /** Allow 0123456789 . + - * /      */
    CharsDecimal(1 shl 0),
    /** Allow 0123456789ABCDEFabcdef    */
    CharsHexadecimal(1 shl 1),
    /** Turn a..z into A..Z */
    CharsUppercase(1 shl 2),
    /** Filter out spaces), tabs    */
    CharsNoBlank(1 shl 3),
    /** Select entire text when first taking mouse focus    */
    AutoSelectAll(1 shl 4),
    /** Return 'true' when Enter is pressed (as opposed to every time the value was modified). Consider looking at the IsItemDeactivatedAfterEdit() function. */
    EnterReturnsTrue(1 shl 5),
    /** Callback on pressing TAB (for completion handling)    */
    CallbackCompletion(1 shl 6),
    /** Callback on pressing Up/Down arrows (for history handling)    */
    CallbackHistory(1 shl 7),
    /** Callback on each iteration. User code may query cursor position), modify text buffer.    */
    CallbackAlways(1 shl 8),
    /** Callback on character inputs to replace or discard them. Modify 'EventChar' to replace or discard,
     *  or return 1 in callback to discard.  */
    CallbackCharFilter(1 shl 9),
    /** Pressing TAB input a '\t' character into the text field */
    AllowTabInput(1 shl 10),
    /** In multi-line mode), unfocus with Enter), add new line with Ctrl+Enter (default is opposite: unfocus with
     *  Ctrl+Enter), add line with Enter).   */
    CtrlEnterForNewLine(1 shl 11),
    /** Disable following the cursor horizontally   */
    NoHorizontalScroll(1 shl 12),
    /** Insert mode */
    AlwaysInsertMode(1 shl 13),
    /** Read-only mode  */
    ReadOnly(1 shl 14),
    /** Password mode), display all characters as '*'   */
    Password(1 shl 15),
    /** Disable undo/redo. Note that input text owns the text data while active, if you want to provide your own
     *  undo/redo stack you need e.g. to call clearActiveID(). */
    NoUndoRedo(1 shl 16),
    /** Allow 0123456789.+-* /eE (Scientific notation input) */
    CharsScientific(1 shl 17),
    /** Callback on buffer capacity changes request (beyond 'buf_size' parameter value), allowing the string to grow.
     *  Notify when the string wants to be resized (for string types which hold a cache of their Size).
     *  You will be provided a new BufSize in the callback and NEED to honor it. (see misc/cpp/imgui_stl.h for an example of using this) */
    CallbackResize(1 shl 18),

    // [Internal]

    /** For internal use by InputTextMultiline()    */
    Multiline(1 shl 20),
    /** For internal use by functions using InputText() before reformatting data */
    NoMarkEdited(1 shl 21);

    infix fun or(b: InputTextFlag): InputTextFlags = i or b.i
}

infix fun Int.or(b: InputTextFlag): InputTextFlags = this or b.i
infix fun Int.has(b: InputTextFlag) = (this and b.i) != 0
infix fun Int.hasnt(b: InputTextFlag) = (this and b.i) == 0

/** Flags: for TreeNode(), TreeNodeEx(), CollapsingHeader()   */
enum class TreeNodeFlag(@JvmField val i: Int) {

    None(0),
    /** Draw as selected    */
    Selected(1 shl 0),
    /** Full colored frame (e.g. for CollapsingHeader)  */
    Framed(1 shl 1),
    /** Hit testing to allow subsequent widgets to overlap this one */
    AllowItemOverlap(1 shl 2),
    /** Don't do a TreePush() when open (e.g. for CollapsingHeader) ( no extra indent nor pushing on ID stack   */
    NoTreePushOnOpen(1 shl 3),
    /** Don't automatically and temporarily open node when Logging is active (by default logging will automatically open
     *  tree nodes) */
    NoAutoOpenOnLog(1 shl 4),
    /** Default node to be open */
    DefaultOpen(1 shl 5),
    /** Need double-click to open node  */
    OpenOnDoubleClick(1 shl 6),
    /** Only open when clicking on the arrow part. If OpenOnDoubleClick is also set), single-click arrow or double-click
     *  all box to open.    */
    OpenOnArrow(1 shl 7),
    /** No collapsing), no arrow (use as a convenience for leaf nodes). */
    Leaf(1 shl 8),
    /** Display a bullet instead of arrow   */
    Bullet(1 shl 9),
    /** Use FramePadding (even for an unframed text node) to vertically align text baseline to regular widget height.
     *  Equivalent to calling alignTextToFramePadding().    */
    FramePadding(1 shl 10),
    /** Extend hit box to the right-most edge, even if not framed. This is not the default in order to allow adding other items on the same line. In the future we may refactor the hit system to be front-to-back, allowing natural overlaps and then this can become the default. */
    SpanAvailWidth(1 shl 11),
    /** Extend hit box to the left-most and right-most edges (bypass the indented area). */
    SpanFullWidth(1 shl 12),
    /** (WIP) Nav: left direction may move to this TreeNode() from any of its child (items submitted between TreeNode and TreePop)   */
    NavLeftJumpsBackHere(1 shl 13),
    CollapsingHeader(Framed or NoTreePushOnOpen or NoAutoOpenOnLog),
    _ClipLabelForTrailingButton(1 shl 20);

    infix fun or(treeNodeFlag: TreeNodeFlag): TreeNodeFlags = i or treeNodeFlag.i
}

infix fun Int.or(b: TreeNodeFlag): TreeNodeFlags = this or b.i
infix fun Int.has(b: TreeNodeFlag) = (this and b.i) != 0
infix fun Int.hasnt(b: TreeNodeFlag) = (this and b.i) == 0

/** Flags for ImGui::Selectable()   */
enum class SelectableFlag(@JvmField val i: Int) {

    None(0),
    /** Clicking this don't close parent popup window   */
    DontClosePopups(1 shl 0),
    /** Selectable frame can span all columns (text will still fit in current column)   */
    SpanAllColumns(1 shl 1),
    /** Generate press events on double clicks too  */
    AllowDoubleClick(1 shl 2),
    /** Cannot be selected, display grayed out text */
    Disabled(1 shl 3),
    /** private  */
    NoHoldingActiveID(1 shl 20),
    /** private  */
    PressedOnClick(1 shl 21),
    /** private  */
    PressedOnRelease(1 shl 22),
    /** private  */
    DrawFillAvailWidth(1 shl 23),

    AllowItemOverlap(1 shl 24),
    /** Always show active when held, even is not hovered. This concept could probably be renamed/formalized somehow. */
    DrawHoveredWhenHeld(1 shl 25),

    SetNavIdOnHover(1 shl 26);

    infix fun or(other: SelectableFlag): SelectableFlags = i or other.i
}

infix fun Int.or(other: SelectableFlag): SelectableFlags = this or other.i
infix fun Int.has(b: SelectableFlag) = (this and b.i) != 0
infix fun Int.hasnt(b: SelectableFlag) = (this and b.i) == 0

/** Flags: for BeginCombo() */
enum class ComboFlag(@JvmField val i: Int) {
    None(0),
    /** Align the popup toward the left by default */
    PopupAlignLeft(1 shl 0),
    /** Max ~4 items visible */
    HeightSmall(1 shl 1),
    /** Max ~8 items visible (default) */
    HeightRegular(1 shl 2),
    /** Max ~20 items visible */
    HeightLarge(1 shl 3),
    /** As many fitting items as possible */
    HeightLargest(1 shl 4),
    /** Display on the preview box without the square arrow button  */
    NoArrowButton(1 shl 5),
    /** Display only a square arrow button  */
    NoPreview(1 shl 6),
    HeightMask_(HeightSmall or HeightRegular or HeightLarge or HeightLargest)
}

infix fun ComboFlag.or(other: ComboFlag): ComboFlags = i or other.i
infix fun Int.and(other: ComboFlag): ComboFlags = and(other.i)
infix fun Int.wo(other: ComboFlag): ComboFlags = and(other.i.inv())
infix fun Int.or(other: ComboFlag): ComboFlags = or(other.i)
infix fun Int.has(b: ComboFlag) = and(b.i) != 0
infix fun Int.hasnt(b: ComboFlag) = and(b.i) == 0


/** Flags for ImGui::BeginTabBar() */
enum class TabBarFlag(@JvmField val i: Int) {
    None(0),
    /** Allow manually dragging tabs to re-order them + New tabs are appended at the end of list */
    Reorderable(1 shl 0),
    /** Automatically select new tabs when they appear */
    AutoSelectNewTabs(1 shl 1),
    /** Disable buttons to open the tab list popup */
    TabListPopupButton(1 shl 2),
    /** Disable behavior of closing tabs (that are submitted with p_open != NULL) with middle mouse button.
     *  You can still repro this behavior on user's side with if (IsItemHovered() && IsMouseClicked(2)) *p_open = false. */
    NoCloseWithMiddleMouseButton(1 shl 3),
    /** Disable scrolling buttons (apply when fitting policy is ImGuiTabBarFlags_FittingPolicyScroll) */
    NoTabListScrollingButtons(1 shl 4),
    /** Disable tooltips when hovering a tab */
    NoTooltip(1 shl 5),
    /** Resize tabs when they don't fit */
    FittingPolicyResizeDown(1 shl 6),
    /** Add scroll buttons when tabs don't fit */
    FittingPolicyScroll(1 shl 7),
    FittingPolicyMask_(FittingPolicyResizeDown or FittingPolicyScroll),
    FittingPolicyDefault_(FittingPolicyResizeDown.i),

    // Private

    /** Part of a dock node [we don't use this in the master branch but it facilitate branch syncing to keep this around] */
    DockNode(1 shl 20),
    IsFocused(1 shl 21),
    /** FIXME: Settings are handled by the docking system, this only request the tab bar to mark settings dirty when reordering tabs, */
    SaveSettings(1 shl 22);

    infix fun or(tabBarFlag: TabBarFlag) = i or tabBarFlag.i
    infix fun xor(tabBarFlag: TabBarFlag) = i xor tabBarFlag.i
}

infix fun Int.and(other: TabBarFlag): ComboFlags = and(other.i)
infix fun Int.wo(other: TabBarFlag): ComboFlags = and(other.i.inv())
infix fun Int.or(other: TabBarFlag): ComboFlags = or(other.i)
infix fun Int.has(b: TabBarFlag) = and(b.i) != 0
infix fun Int.hasnt(b: TabBarFlag) = and(b.i) == 0

/** Flags for ImGui::BeginTabItem() */
enum class TabItemFlag(@JvmField val i: Int) {
    None(0),
    /** Append '*' to title without affecting the ID, as a convenience to avoid using the ### operator. Also: tab is selected on closure and closure is deferred by one frame to allow code to undo it without flicker. */
    UnsavedDocument(1 shl 0),
    /** Trigger flag to programmatically make the tab selected when calling BeginTabItem() */
    SetSelected(1 shl 1),
    /** Disable behavior of closing tabs (that are submitted with p_open != NULL) with middle mouse button. You can still repro this behavior on user's side with if (IsItemHovered() && IsMouseClicked(2)) *p_open = false. */
    NoCloseWithMiddleMouseButton(1 shl 2),
    /** Don't call PushID(tab->ID)/PopID() on BeginTabItem()/EndTabItem() */
    NoPushId(1 shl 3),
    /** Store whether p_open is set or not, which we need to recompute WidthContents during layout. */
    NoCloseButton(1 shl 20)
}

infix fun Int.or(other: TabItemFlag): TabItemFlags = or(other.i)
infix fun Int.has(b: TabItemFlag) = and(b.i) != 0
infix fun Int.hasnt(b: TabItemFlag) = and(b.i) == 0


/** Flags for ImGui::IsWindowFocused() */
enum class FocusedFlag(@JvmField val i: Int) {
    None(0),
    /** isWindowFocused(): Return true if any children of the window is focused */
    ChildWindows(1 shl 0),
    /** isWindowFocused(): Test from root window (top most parent of the current hierarchy) */
    RootWindow(1 shl 1),
    /** IsWindowFocused(): Return true if any window is focused.
     *  Important: If you are trying to tell how to dispatch your low-level inputs, do NOT use this. Use ImGui::GetIO().WantCaptureMouse instead. */
    AnyWindow(1 shl 2),
    RootAndChildWindows(RootWindow or ChildWindows)
}

infix fun FocusedFlag.or(other: FocusedFlag): FocusedFlags = i or other.i
infix fun Int.and(other: FocusedFlag): FocusedFlags = and(other.i)
infix fun Int.or(other: FocusedFlag): FocusedFlags = or(other.i)
infix fun Int.has(b: FocusedFlag) = and(b.i) != 0
infix fun Int.hasnt(b: FocusedFlag) = and(b.i) == 0

/** Flags: for IsItemHovered(), IsWindowHovered() etc. */
enum class HoveredFlag(@JvmField val i: Int) {
    /** Return true if directly over the item/window, not obstructed by another window, not obstructed by an active
     *  popup or modal blocking inputs under them.  */
    None(0),
    /** isWindowHovered() only: Return true if any children of the window is hovered */
    ChildWindows(1 shl 0),
    /** isWindowHovered() only: Test from root window (top most parent of the current hierarchy) */
    RootWindow(1 shl 1),
    /** IsWindowHovered() only: Return true if any window is hovered    */
    AnyWindow(1 shl 2),
    /** Return true even if a popup window is normally blocking access to this item/window  */
    AllowWhenBlockedByPopup(1 shl 3),
    //AllowWhenBlockedByModal     (1 shl 4),   // Return true even if a modal popup window is normally blocking access to this item/window. FIXME-TODO: Unavailable yet.
    /** Return true even if an active item is blocking access to this item/window. Useful for Drag and Drop patterns.   */
    AllowWhenBlockedByActiveItem(1 shl 5),
    /** Return true even if the position is obstructed or overlapped by another window,   */
    AllowWhenOverlapped(1 shl 6),
    /** Return true even if the item is disabled */
    AllowWhenDisabled(1 shl 7),
    RectOnly(AllowWhenBlockedByPopup.i or AllowWhenBlockedByActiveItem.i or AllowWhenOverlapped.i),
    RootAndChildWindows(RootWindow or ChildWindows)
}

infix fun HoveredFlag.or(other: HoveredFlag): HoveredFlags = i or other.i
infix fun Int.or(other: HoveredFlag): HoveredFlags = or(other.i)
infix fun Int.has(b: HoveredFlag) = and(b.i) != 0
infix fun Int.hasnt(b: HoveredFlag) = and(b.i) == 0

/** Flags for beginDragDropSource(), acceptDragDropPayload() */
enum class DragDropFlag(@JvmField val i: Int) {
    // BeginDragDropSource() flags
    None(0),
    /** By default), a successful call to beginDragDropSource opens a tooltip so you can display a preview or
     *  description of the source contents. This flag disable this behavior. */
    SourceNoPreviewTooltip(1 shl 0),
    /** By default, when dragging we clear data so that IsItemHovered() will return false,
     *  to avoid subsequent user code submitting tooltips.
     *  This flag disable this behavior so you can still call IsItemHovered() on the source item. */
    SourceNoDisableHover(1 shl 1),
    /** Disable the behavior that allows to open tree nodes and collapsing header by holding over them while dragging
     *  a source item. */
    SourceNoHoldToOpenOthers(1 shl 2),
    /** Allow items such as text()), Image() that have no unique identifier to be used as drag source),
     *  by manufacturing a temporary identifier based on their window-relative position.
     *  This is extremely unusual within the dear imgui ecosystem and so we made it explicit. */
    SourceAllowNullID(1 shl 3),
    /** External source (from outside of dear imgui), won't attempt to read current item/window info. Will always return true.
     *  Only one Extern source can be active simultaneously.    */
    SourceExtern(1 shl 4),
    /** Automatically expire the payload if the source cease to be submitted (otherwise payloads are persisting while being dragged) */
    SourceAutoExpirePayload(1 shl 5),
    // AcceptDragDropPayload() flags
    /** AcceptDragDropPayload() will returns true even before the mouse button is released.
     *  You can then call isDelivery() to test if the payload needs to be delivered. */
    AcceptBeforeDelivery(1 shl 10),
    /** Do not draw the default highlight rectangle when hovering over target. */
    AcceptNoDrawDefaultRect(1 shl 11),
    /** Request hiding the BeginDragDropSource tooltip from the BeginDragDropTarget site. */
    AcceptNoPreviewTooltip(1 shl 12),
    /** For peeking ahead and inspecting the payload before delivery. */
    AcceptPeekOnly(AcceptBeforeDelivery or AcceptNoDrawDefaultRect)
}

infix fun DragDropFlag.or(other: DragDropFlag): DragDropFlags = i or other.i
infix fun Int.or(other: DragDropFlag): DragDropFlags = this or other.i
infix fun Int.has(b: DragDropFlag) = (this and b.i) != 0
infix fun Int.hasnt(b: DragDropFlag) = (this and b.i) == 0

// Standard Drag and Drop payload types. Types starting with '_' are defined by Dear ImGui.
/** float[3]: Standard type for colors, without alpha. User code may use this type. */
val PAYLOAD_TYPE_COLOR_3F = "_COL3F"
/** float[4]: Standard type for colors. User code may use this type. */
val PAYLOAD_TYPE_COLOR_4F = "_COL4F"

/** A primary data type */
enum class DataType {
    Byte, Ubyte, Short, Ushort, Int, Uint, Long, Ulong, Float, Double;

    @JvmField
    val i = ordinal
}

/** A cardinal direction */
enum class Dir {
    None, Left, Right, Up, Down;

    @JvmField
    val i = ordinal - 1

    companion object {
        infix fun of(i: Int) = values().first { it.i == i }
        val COUNT = Down.i + 1
    }
}

infix fun Int.shl(b: Dir) = shl(b.i)

/** User fill ImGuiio.KeyMap[] array with indices into the ImGuiio.KeysDown[512] array
 *
 *  A key identifier (ImGui-side enum) */
enum class Key {
    Tab, LeftArrow, RightArrow, UpArrow, DownArrow, PageUp, PageDown, Home, End, Insert, Delete, Backspace,
    Space, Enter, Escape, KeyPadEnter,
    /** for text edit CTRL+A: select all */
    A,
    /** for text edit CTRL+C: copy */
    C,
    /** for text edit CTRL+V: paste */
    V,
    /** for text edit CTRL+X: cut */
    X,
    /** for text edit CTRL+Y: redo */
    Y,
    /** for text edit CTRL+Z: undo */
    Z,
    Count;

    companion object {
        val COUNT = values().size
    }

    @JvmField
    val i = ordinal

    /** JVM implementation of IsKeyPressedMap   */
    fun isPressed(repeat: Boolean = true) = isKeyPressed(io.keyMap[i], repeat)

    val isPressed: Boolean
        get() = isPressed(true)

    val isDown: Boolean
        get() = isKeyDown(io.keyMap[i])

    /** map ImGuiKey_* values into user's key index. == io.KeyMap[key]   */
    val index get() = i
}

/** Gamepad/Keyboard directional navigation
 *  Keyboard: Set io.configFlags |= NavFlags.EnableKeyboard to enable. ::newFrame() will automatically fill io.navInputs[]
 *  based on your io.keysDown[] + io.keyMap[] arrays.
 *  Gamepad:  Set io.configFlags |= NavFlags.EnableGamepad to enable. Fill the io.navInputs[] fields before calling
 *  ::newFrame(). Note that io.navInputs[] is cleared by ::endFrame().
 *  Read instructions in imgui.cpp for more details.
 *
 *  An input identifier for navigation */
enum class NavInput {
    // Gamepad Mapping
    /** activate / open / toggle / tweak value       // e.g. Cross (PS4), A (Xbox), B (Switch), Space (Keyboard)   */
    Activate,
    /** cancel / close / exit                        // e.g. Circle (PS4), B (Xbox), A (Switch), Escape (Keyboard)  */
    Cancel,
    /** text input / on-screen keyboard              // e.g. Triang.(PS4), Y (Xbox), X (Switch), Return (Keyboard)  */
    Input,
    /** tap: toggle menu / hold: focus, move, resize // e.g. Square (PS4), X (Xbox), Y (Switch), Alt (Keyboard) */
    Menu,
    /** move / tweak / resize window (w/ PadMenu)    // e.g. D-pad Left/Right/Up/Down (Gamepads), Arrow keys (Keyboard) */
    DpadLeft,
    /** move / tweak / resize window (w/ PadMenu)    // e.g. D-pad Left/Right/Up/Down (Gamepads), Arrow keys (Keyboard) */
    DpadRight,
    /** move / tweak / resize window (w/ PadMenu)    // e.g. D-pad Left/Right/Up/Down (Gamepads), Arrow keys (Keyboard) */
    DpadUp,
    /** move / tweak / resize window (w/ PadMenu)    // e.g. D-pad Left/Right/Up/Down (Gamepads), Arrow keys (Keyboard) */
    DpadDown,
    /** scroll / move window (w/ PadMenu)            // e.g. Left Analog Stick Left/Right/Up/Down   */
    LStickLeft,
    /** scroll / move window (w/ PadMenu)            // e.g. Left Analog Stick Left/Right/Up/Down   */
    LStickRight,
    /** scroll / move window (w/ PadMenu)            // e.g. Left Analog Stick Left/Right/Up/Down   */
    LStickUp,
    /** scroll / move window (w/ PadMenu)            // e.g. Left Analog Stick Left/Right/Up/Down   */
    LStickDown,
    /** next window (w/ PadMenu)                     // e.g. L1 or L2 (PS4), LB or LT (Xbox), L or ZL (Switch)  */
    FocusPrev,
    /** prev window (w/ PadMenu)                     // e.g. R1 or R2 (PS4), RB or RT (Xbox), R or ZL (Switch)  */
    FocusNext,
    /** slower tweaks                                // e.g. L1 or L2 (PS4), LB or LT (Xbox), L or ZL (Switch)  */
    TweakSlow,
    /** faster tweaks                                // e.g. R1 or R2 (PS4), RB or RT (Xbox), R or ZL (Switch)  */
    TweakFast,

    /*  [Internal] Don't use directly! This is used internally to differentiate keyboard from gamepad inputs for
        behaviors that require to differentiate them.
        Keyboard behavior that have no corresponding gamepad mapping (e.g. CTRL + TAB) will be directly reading from
        io.keysDown[] instead of io.navInputs[]. */

    /** toggle menu = io.keyAlt */
    KeyMenu,
    /** tab = Tab key */
    KeyTab,
    /** move left = Arrow keys  */
    KeyLeft,
    /** move right = Arrow keys  */
    KeyRight,
    /** move up = Arrow keys  */
    KeyUp,
    /** move down = Arrow keys  */
    KeyDown;

    @JvmField
    val i = ordinal

    companion object {
        val COUNT = values().size
        val InternalStart = KeyMenu.i
    }

    /** Equivalent of isKeyDown() for NavInputs[]   */ // JVM TODO check for semantic Key.isPressed/Down
    fun isDown() = io.navInputs[i] > 0f

    /** Equivalent of isKeyPressed() for NavInputs[]    */
    fun isPressed(mode: InputReadMode) = getNavInputAmount(this, mode) > 0f

    fun isPressedAnyOfTwo(n2: NavInput, mode: InputReadMode) = (getNavInputAmount(this, mode) + getNavInputAmount(n2, mode)) > 0f
}

infix fun Int.shl(f: NavInput) = shl(f.i)

/** Configuration flags stored in io.configFlags
 *
 *  Flags: for io.ConfigFlags   */
enum class ConfigFlag(@JvmField val i: Int) {
    None(0),
    /** Master keyboard navigation enable flag. NewFrame() will automatically fill io.NavInputs[] based on io.KeysDown[]. */
    NavEnableKeyboard(1 shl 0),
    /** Master gamepad navigation enable flag. This is mostly to instruct your imgui back-end to fill io.NavInputs[].
     *  Back-end also needs to set ImGuiBackendFlags_HasGamepad. */
    NavEnableGamepad(1 shl 1),
    /** Instruct navigation to move the mouse cursor. May be useful on TV/console systems where moving a virtual mouse is awkward.
     *  Will update io.MousePos and set io.wantSetMousePos=true. If enabled you MUST honor io.wantSetMousePos requests in your binding,
     *  otherwise ImGui will react as if the mouse is jumping around back and forth. */
    NavEnableSetMousePos(1 shl 2),
    /** Instruct navigation to not set the io.WantCaptureKeyboard flag when io.NavActive is set. */
    NavNoCaptureKeyboard(1 shl 3),
    /** Instruct imgui to clear mouse position/buttons in NewFrame(). This allows ignoring the mouse information set by the back-end. */
    NoMouse(1 shl 4),
    /** Request back-end to not alter mouse cursor configuration.
     *  Use if the back-end cursor changes are interfering with yours and you don't want to use setMouseCursor() to change mouse cursor.
     *  You may want to honor requests from imgui by reading ::mouseCursor yourself instead. */
    NoMouseCursorChange(1 shl 5),

    /** JVM custom, request back-end to not read the mouse status allowing you to provide your own custom input */
    NoMouseUpdate(1 shl 12),
    /** JVM custom */
    NoKeyboardUpdate(1 shl 13),

    /*  User storage (to allow your back-end/engine to communicate to code that may be shared between multiple projects.
        Those flags are not used by core Dear ImGui)     */

    /** Application is SRGB-aware. */
    IsSRGB(1 shl 20),
    /** Application is using a touch screen instead of a mouse. */
    IsTouchScreen(1 shl 21);
}

infix fun Int.has(b: ConfigFlag) = and(b.i) != 0
infix fun Int.hasnt(b: ConfigFlag) = and(b.i) == 0
infix fun Int.or(b: ConfigFlag): ConfigFlags = or(b.i)
infix fun Int.wo(b: ConfigFlag): ConfigFlags = and(b.i.inv())
infix fun ConfigFlag.or(b: ConfigFlag): ConfigFlags = i or b.i

typealias BackendFlags = Int

/** Back-end capabilities flags stored in io.BackendFlag. Set by imgui_impl_xxx or custom back-end.
 *
 *  Flags: for io.BackendFlags  */
enum class BackendFlag(@JvmField val i: Int) {
    None(0),
    /** Back-end Platform supports gamepad and currently has one connected. */
    HasGamepad(1 shl 0),
    /** Back-end Platform supports honoring GetMouseCursor() value to change the OS cursor shape. */
    HasMouseCursors(1 shl 1),
    /** Back-end Platform supports io.WantSetMousePos requests to reposition the OS mouse position (only used if ImGuiConfigFlags_NavEnableSetMousePos is set). */
    HasSetMousePos(1 shl 2),
    /** Back-end Platform supports ImDrawCmd::VtxOffset. This enables output of large meshes (64K+ vertices) while still using 16-bits indices. */
    RendererHasVtxOffset(1 shl 3)
}


infix fun Int.has(b: BackendFlag) = and(b.i) != 0
infix fun Int.hasnt(b: BackendFlag) = and(b.i) == 0
infix fun Int.or(b: BackendFlag): BackendFlags = or(b.i)
infix fun Int.wo(b: BackendFlag): BackendFlags = and(b.i.inv())
infix fun BackendFlag.or(b: BackendFlag): BackendFlags = i or b.i

/** Enumeration for PushStyleColor() / PopStyleColor()  */

//
//sealed class Co(i: Int) : Enum(i) {
//    object Text : Co(_i++)
//    object TextDisabled : Co(_i++)
//    /** Background of normal windows    */
//    object WindowBg : Co(_i++)
//
//    /** Background of child windows */
//    object ChildBg : Co(_i++)
//
//    /*-* Background of popups, menus, tooltips windows  */
//    object PopupBg : Co(_i++)
//
//    object Border : Co(_i++)
//    object BorderShadow : Co(_i++)
//    /** Background of checkbox, radio button, plot, slider, text input  */
//    object FrameBg : Co(_i++)
//
//    object FrameBgHovered : Co(_i++)
//    object FrameBgActive : Co(_i++)
//    object TitleBg : Co(_i++)
//    object TitleBgActive : Co(_i++)
//    object TitleBgCollapsed : Co(_i++)
//    object MenuBarBg : Co(_i++)
//    object ScrollbarBg : Co(_i++)
//    object ScrollbarGrab : Co(_i++)
//    object ScrollbarGrabHovered : Co(_i++)
//    object ScrollbarGrabActive : Co(_i++)
//    object CheckMark : Co(_i++)
//    object SliderGrab : Co(_i++)
//    object SliderGrabActive : Co(_i++)
//    object Button : Co(_i++)
//    object ButtonHovered : Co(_i++)
//    object ButtonActive : Co(_i++)
//    object Header : Co(_i++)
//    object HeaderHovered : Co(_i++)
//    object HeaderActive : Co(_i++)
//    object Separator : Co(_i++)
//    object SeparatorHovered : Co(_i++)
//    object SeparatorActive : Co(_i++)
//    object ResizeGrip : Co(_i++)
//    object ResizeGripHovered : Co(_i++)
//    object ResizeGripActive : Co(_i++)
//    object CloseButton : Co(_i++)
//    object CloseButtonHovered : Co(_i++)
//    object CloseButtonActive : Co(_i++)
//    object PlotLines : Co(_i++)
//    object PlotLinesHovered : Co(_i++)
//    object PlotHistogram : Co(_i++)
//    object PlotHistogramHovered : Co(_i++)
//    object TextSelectedBg : Co(_i++)
//    /** darken entire screen when a modal window is active   */
//    object ModalWindowDarkening : Co(_i++)
//
//    object DragDropTarget : Co(_i++)
//    /** gamepad/keyboard: current highlighted item  */
//    object NavHighlight : Co(_i++)
//
//    /** gamepad/keyboard: when holding NavMenu to focus/move/resize windows */
//    object NavWindowingHighlight : Co(_i++)
//
//    init {
//        values += this
//    }
//
//    val count = _i
//
//    val name
//        get() = when (this) {
//            Co.Text -> "Text"
//            Co.TextDisabled -> "TextDisabled"
//            Co.WindowBg -> "WindowBg"
//            Co.ChildBg -> "ChildBg"
//            Co.PopupBg -> "PopupBg"
//            Co.Border -> "Border"
//            Co.BorderShadow -> "BorderShadow"
//            Co.FrameBg -> "FrameBg"
//            Co.FrameBgHovered -> "FrameBgHovered"
//            Co.FrameBgActive -> "FrameBgActive"
//            Co.TitleBg -> "TitleBg"
//            Co.TitleBgActive -> "TitleBgActive"
//            Co.TitleBgCollapsed -> "TitleBgCollapsed"
//            Co.MenuBarBg -> "MenuBarBg"
//            Co.ScrollbarBg -> "ScrollbarBg"
//            Co.ScrollbarGrab -> "ScrollbarGrab"
//            Co.ScrollbarGrabHovered -> "ScrollbarGrabHovered"
//            Co.ScrollbarGrabActive -> "ScrollbarGrabActive"
//            Co.CheckMark -> "CheckMark"
//            Co.SliderGrab -> "SliderGrab"
//            Co.SliderGrabActive -> "SliderGrabActive"
//            Co.Button -> "Button"
//            Co.ButtonHovered -> "ButtonHovered"
//            Co.ButtonActive -> "ButtonActive"
//            Co.Header -> "Header"
//            Co.HeaderHovered -> "HeaderHovered"
//            Co.HeaderActive -> "HeaderActive"
//            Co.Separator -> "Separator"
//            Co.SeparatorHovered -> "SeparatorHovered"
//            Co.SeparatorActive -> "SeparatorActive"
//            Co.ResizeGrip -> "ResizeGrip"
//            Co.ResizeGripHovered -> "ResizeGripHovered"
//            Co.ResizeGripActive -> "ResizeGripActive"
//            Co.CloseButton -> "CloseButton"
//            Co.CloseButtonHovered -> "CloseButtonHovered"
//            Co.CloseButtonActive -> "CloseButtonActive"
//            Co.PlotLines -> "PlotLines"
//            Co.PlotLinesHovered -> "PlotLinesHovered"
//            Co.PlotHistogram -> "PlotHistogram"
//            Co.PlotHistogramHovered -> "PlotHistogramHovered"
//            Co.TextSelectedBg -> "TextSelectedBg"
//            Co.ModalWindowDarkening -> "ModalWindowDarkening"
//            Co.DragDropTarget -> "DragDropTarget"
//            Co.NavHighlight -> "NavHighlight"
//            Co.NavWindowingHighlight -> "NavWindowingHighlight"
//        }
//
//    companion object {
//        var _i = 0
//        val values = ArrayList<Co>()
//    }
//}

/** A color identifier for styling */
enum class Col {

    Text,
    TextDisabled,
    /** Background of normal windows    */
    WindowBg,
    /** Background of child windows */
    ChildBg,
    /*-* Background of popups, menus, tooltips windows  */
    PopupBg,
    Border,
    BorderShadow,
    /** Background of checkbox, radio button, plot, slider, text input  */
    FrameBg,
    FrameBgHovered,
    FrameBgActive,
    TitleBg,
    TitleBgActive,
    TitleBgCollapsed,
    MenuBarBg,
    ScrollbarBg,
    ScrollbarGrab,
    ScrollbarGrabHovered,
    ScrollbarGrabActive,
    CheckMark,
    SliderGrab,
    SliderGrabActive,
    Button,
    ButtonHovered,
    ButtonActive,
    /** Header* colors are used for CollapsingHeader, TreeNode, Selectable, MenuItem */
    Header,
    HeaderHovered,
    HeaderActive,
    Separator,
    SeparatorHovered,
    SeparatorActive,
    ResizeGrip,
    ResizeGripHovered,
    ResizeGripActive,
    Tab,
    TabHovered,
    TabActive,
    TabUnfocused,
    TabUnfocusedActive,
    PlotLines,
    PlotLinesHovered,
    PlotHistogram,
    PlotHistogramHovered,
    TextSelectedBg,
    DragDropTarget,
    /** Gamepad/keyboard: current highlighted item  */
    NavHighlight,
    /** Highlight window when using CTRL+TAB */
    NavWindowingHighlight,
    /** Darken/colorize entire screen behind the CTRL+TAB window list, when active */
    NavWindowingDimBg,
    /** Darken/colorize entire screen behind a modal window, when one is active; */
    ModalWindowDimBg;

    @JvmField
    val i = ordinal

    val u32 get() = ImGui.getColorU32(i, alphaMul = 1f)

    companion object {
        val COUNT = values().size
        fun of(i: Int) = values()[i]
    }
}

/** Enumeration for PushStyleVar() / PopStyleVar() to temporarily modify the ImGuiStyle structure.
 *  NB: the enum only refers to fields of ImGuiStyle which makes sense to be pushed/poped inside UI code.
 *  During initialization, feel free to just poke into ImGuiStyle directly.
 *  NB: if changing this enum, you need to update the associated internal table GStyleVarInfo[] accordingly. This is
 *  where we link enum values to members offset/type.
 *
 *  A variable identifier for styling   */
enum class StyleVar {
    /** float   */
    Alpha,
    /** vec2    */
    WindowPadding,
    /** float   */
    WindowRounding,
    /** float */
    WindowBorderSize,
    /** vec2    */
    WindowMinSize,
    /** Vec2    */
    WindowTitleAlign,
    /** float   */
    ChildRounding,
    /** float */
    ChildBorderSize,
    /** float */
    PopupRounding,
    /** float */
    PopupBorderSize,
    /** vec2    */
    FramePadding,
    /** float   */
    FrameRounding,
    /** float   */
    FrameBorderSize,
    /** vec2    */
    ItemSpacing,
    /** vec2    */
    ItemInnerSpacing,
    /** float   */
    IndentSpacing,
    /** Float   */
    ScrollbarSize,
    /** Float   */
    ScrollbarRounding,
    /** float   */
    GrabMinSize,
    /** float   */
    GrabRounding,
    /** float */
    TabRounding,
    /** vec2  */
    ButtonTextAlign,
    /** vec2  */
    SelectableTextAlign;

    @JvmField
    val i = ordinal
}

/** Flags for ColorEdit3() / ColorEdit4() / ColorPicker3() / ColorPicker4() / ColorButton()
 *
 *  Flags: for ColorEdit4(), ColorPicker4() etc.    */
enum class ColorEditFlag(@JvmField val i: ColorEditFlags) {

    None(0),
    /** ColorEdit, ColorPicker, ColorButton: ignore Alpha component (will only read 3 components from the input pointer). */
    NoAlpha(1 shl 1),
    /** ColorEdit: disable picker when clicking on colored square.  */
    NoPicker(1 shl 2),
    /** ColorEdit: disable toggling options menu when right-clicking on inputs/small preview.   */
    NoOptions(1 shl 3),
    /** ColorEdit, ColorPicker: disable colored square preview next to the inputs. (e.g. to show only the inputs)   */
    NoSmallPreview(1 shl 4),
    /** ColorEdit, ColorPicker: disable inputs sliders/text widgets (e.g. to show only the small preview colored square).   */
    NoInputs(1 shl 5),
    /** ColorEdit, ColorPicker, ColorButton: disable tooltip when hovering the preview. */
    NoTooltip(1 shl 6),
    /** ColorEdit, ColorPicker: disable display of inline text label (the label is still forwarded to the tooltip and picker).  */
    NoLabel(1 shl 7),
    /** ColorPicker: disable bigger color preview on right side of the picker, use small colored square preview instead.    */
    NoSidePreview(1 shl 8),
    /** ColorEdit: disable drag and drop target. ColorButton: disable drag and drop source. */
    NoDragDrop(1 shl 9),

    // User Options (right-click on widget to change some of them).

    /** ColorEdit, ColorPicker: show vertical alpha bar/gradient in picker. */
    AlphaBar(1 shl 16),
    /** ColorEdit, ColorPicker, ColorButton: display preview as a transparent color over a checkerboard, instead of opaque. */
    AlphaPreview(1 shl 17),
    /** ColorEdit, ColorPicker, ColorButton: display half opaque / half checkerboard, instead of opaque.    */
    AlphaPreviewHalf(1 shl 18),
    /** (WIP) ColorEdit: Currently only disable 0.0f..1.0f limits in RGBA edition (note: you probably want to use
     *  ColorEditFlags.Float flag as well). */
    HDR(1 shl 19),
    /** [Display] ColorEdit: override _display_ type among RGB/HSV/Hex. ColorPicker: select any combination using one or more of RGB/HSV/Hex.    */
    DisplayRGB(1 shl 20),
    /** [Display] ColorEdit: override _display_ type among RGB/HSV/Hex. ColorPicker: select any combination using one or more of RGB/HSV/Hex.    */
    DisplayHSV(1 shl 21),
    /** [Display] ColorEdit: override _display_ type among RGB/HSV/Hex. ColorPicker: select any combination using one or more of RGB/HSV/Hex.    */
    DisplayHEX(1 shl 22),
    /** [DataType] ColorEdit, ColorPicker, ColorButton: _display_ values formatted as 0..255.   */
    Uint8(1 shl 23),
    /** [DataType] ColorEdit, ColorPicker, ColorButton: _display_ values formatted as 0.0f..1.0f floats instead of 0..255 integers.
     *  No round-trip of value via integers.    */
    Float(1 shl 24),
    /** [Picker]     // ColorPicker: bar for Hue, rectangle for Sat/Value. */
    PickerHueBar(1 shl 25),
    /** [Picker]     // ColorPicker: wheel for Hue, triangle for Sat/Value. */
    PickerHueWheel(1 shl 26),
    /** [Input]      // ColorEdit, ColorPicker: input and output data in RGB format. */
    InputRGB(1 shl 27),
    /** [Input]      // ColorEdit, ColorPicker: input and output data in HSV format. */
    InputHSV(1 shl 28),

    /** Defaults Options. You can set application defaults using SetColorEditOptions(). The intent is that you probably don't want to
     *  override them in most of your calls. Let the user choose via the option menu and/or call SetColorEditOptions() once during startup. */
    _OptionsDefault(Uint8 or DisplayRGB or InputRGB or PickerHueBar),

    // [Internal] Masks
    _DisplayMask(DisplayRGB or DisplayHSV or DisplayHEX),
    _DataTypeMask(Uint8 or Float),
    _PickerMask(PickerHueWheel or PickerHueBar),
    _InputMask(InputRGB or InputHSV)
}

infix fun ColorEditFlag.and(other: ColorEditFlag): ColorEditFlags = i and other.i
infix fun ColorEditFlag.or(other: ColorEditFlag): ColorEditFlags = i or other.i
infix fun ColorEditFlag.or(other: Int): ColorEditFlags = i or other
infix fun Int.and(other: ColorEditFlag): ColorEditFlags = this and other.i
infix fun Int.or(other: ColorEditFlag): ColorEditFlags = this or other.i
infix fun Int.has(b: ColorEditFlag) = (this and b.i) != 0
infix fun Int.hasnt(b: ColorEditFlag) = (this and b.i) == 0
infix fun Int.wo(b: ColorEditFlag): ColorEditFlags = this and b.i.inv()
infix fun Int.wo(b: Int): ColorEditFlags = this and b.inv()

/** Enumeration for GetMouseCursor()
 *  User code may request binding to display given cursor by calling SetMouseCursor(),
 *  which is why we have some cursors that are marked unused here
 *
 *  A mouse cursor identifier */
enum class MouseCursor {

    None,
    Arrow,
    /** When hovering over InputText, etc.  */
    TextInput,
    /** (Unused by Dear ImGui functions) */
    ResizeAll,
    /** When hovering over an horizontal border  */
    ResizeNS,
    /** When hovering over a vertical border or a column */
    ResizeEW,
    /** When hovering over the bottom-left corner of a window  */
    ResizeNESW,
    /** When hovering over the bottom-right corner of a window  */
    ResizeNWSE,
    /** (Unused by Dear ImGui functions. Use for e.g. hyperlinks) */
    Hand;

    @JvmField
    val i = ordinal - 1

    companion object {
        fun of(i: Int) = values().first { it.i == i }
        val COUNT = Hand.i + 1
    }
}

/** Enumeration for ImGui::SetWindow***(), SetNextWindow***(), SetNextItem***() functions
 *  Represent a condition.
 *  Important: Treat as a regular enum! Do NOT combine multiple values using binary operators!
 *  All the functions above treat 0 as a shortcut to Cond.Always.
 *
 *  Enum: A condition for many Set*() functions */
enum class Cond(@JvmField val i: Int) {

    None(0),
    /** Set the variable    */
    Always(1 shl 0),
    /** Set the variable once per runtime session (only the first call with succeed)    */
    Once(1 shl 1),
    /** Set the variable if the object/window has no persistently saved data (no entry in .ini file)    */
    FirstUseEver(1 shl 2),
    /** Set the variable if the object/window is appearing after being hidden/inactive (or the first time) */
    Appearing(1 shl 3);

//    val isPowerOfTwo = i.isPowerOfTwo // JVM, kind of useless since it's used on cpp to avoid Cond masks

    infix fun or(other: Cond) = i or other.i
}

infix fun Int.or(other: Cond) = or(other.i)
infix fun Int.has(b: Cond) = and(b.i) != 0
infix fun Cond.has(b: Cond) = i.and(b.i) != 0
infix fun Int.hasnt(b: Cond) = and(b.i) == 0
infix fun Int.wo(b: Cond) = and(b.i.inv())