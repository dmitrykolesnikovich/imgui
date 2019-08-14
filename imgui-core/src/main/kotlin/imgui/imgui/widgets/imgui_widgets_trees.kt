package imgui.imgui.widgets

import gli_.has
import glm_.vec2.Vec2
import imgui.*
import imgui.ImGui.closeButton
import imgui.ImGui.currentWindow
import imgui.ImGui.indent
import imgui.ImGui.navMoveRequestButNoResultYet
import imgui.ImGui.navMoveRequestCancel
import imgui.ImGui.popId
import imgui.ImGui.pushId
import imgui.ImGui.setNavId
import imgui.ImGui.style
import imgui.ImGui.treeNodeBehavior
import imgui.ImGui.unindent
import imgui.imgui.g
import imgui.imgui.max
import imgui.internal.NextItemDataFlag
import imgui.internal.itemHoveredDataBackup
import imgui.internal.or
import kotlin.reflect.KMutableProperty0
import imgui.TreeNodeFlag as Tnf

/** Widgets: Trees
 *  - TreeNode functions return true when the node is open, in which case you need to also call TreePop() when you are finished displaying the tree node contents. */
interface imgui_widgets_trees {

    /** if returning 'true' the node is open and the tree id is pushed into the id stack. user is responsible for
     *  calling TreePop().  */
    fun treeNode(label: String): Boolean {
        val window = currentWindow
        if (window.skipItems) return false
        return treeNodeBehavior(window.getId(label), 0, label)
    }

    /** read the FAQ about why and how to use ID. to align arbitrary text at the same level as a TreeNode() you can use
     *  Bullet().   */
    fun treeNode(strId: String, fmt: String, vararg args: Any): Boolean = treeNodeExV(strId, 0, fmt, *args)

    /** read the FAQ about why and how to use ID. to align arbitrary text at the same level as a TreeNode() you can use
     *  Bullet().   */
    fun treeNode(ptrId: Any, fmt: String, vararg args: Any): Boolean = treeNodeExV(ptrId, 0, fmt, *args)
//    IMGUI_API bool          TreeNodeV(const char* str_id, const char* fmt, va_list args);           // "
//    IMGUI_API bool          TreeNodeV(const void* ptr_id, const char* fmt, va_list args);           // "

    fun treeNodeEx(label: String, flags: TreeNodeFlags = 0): Boolean {
        val window = currentWindow
        if (window.skipItems) return false

        return treeNodeBehavior(window.getId(label), flags, label)
    }

    /** @return isOpen */
    fun treeNodeEx(strId: String, flags: TreeNodeFlags, fmt: String, vararg args: Any): Boolean =
            treeNodeExV(strId, flags, fmt, args)

//    IMGUI_API bool          TreeNodeEx(const void* ptr_id, ImGuiTreeNodeFlags flags, const char* fmt, ...) IM_PRINTFARGS(3);

    fun treeNodeExV(strId: String, flags: TreeNodeFlags, fmt: String, vararg args: Any): Boolean {

        val window = currentWindow
        if (window.skipItems) return false

        val label = fmt.format(style.locale, *args)

        return treeNodeBehavior(window.getId(strId), flags, label)
    }

    fun treeNodeExV(ptrId: Any, flags: TreeNodeFlags, fmt: String, vararg args: Any): Boolean {

        val window = currentWindow
        if (window.skipItems) return false

        val label = fmt.format(style.locale, *args)

        return treeNodeBehavior(window.getId(ptrId), flags, label)
    }
//    IMGUI_API void          TreePush(const char* str_id = NULL);                                    // ~ Indent()+PushId(). Already called by TreeNode() when returning true, but you can call Push/Pop yourself for layout purpose

    /** ~ Indent()+PushId(). Already called by TreeNode() when returning true, but you can call TreePush/TreePop yourself if desired.  */
    fun treePush(ptrId: Any?) {
        val window = currentWindow
        indent()
        window.dc.treeDepth++
        pushId(ptrId ?: "#TreePush")
    }

    /** ~ Unindent()+PopId()    */
    fun treePop() {
        val window = g.currentWindow!!
        unindent()

        currentWindow.dc.treeDepth--
        if (g.navMoveDir == Dir.Left && g.navWindow === window && navMoveRequestButNoResultYet())
            if (g.navIdIsAlive && window.dc.treeStoreMayJumpToParentOnPop has (1 shl window.dc.treeDepth)) {
                setNavId(window.idStack.last(), g.navLayer)
                navMoveRequestCancel()
            }
        window.dc.treeStoreMayJumpToParentOnPop = window.dc.treeStoreMayJumpToParentOnPop and (1 shl window.dc.treeDepth) - 1

        assert(window.idStack.size > 1) { "There should always be 1 element in the idStack (pushed during window creation). If this triggers you called ::treePop/popId() too much." }
        popId()
    }

    /** horizontal distance preceding label when using TreeNode*() or Bullet() == (g.FontSize + style.FramePadding.x*2)
     *  for a regular unframed TreeNode */
    val treeNodeToLabelSpacing: Float
        get() = g.fontSize + style.framePadding.x * 2f

    /** CollapsingHeader returns true when opened but do not indent nor push into the ID stack (because of the
     *  ImGuiTreeNodeFlags_NoTreePushOnOpen flag).
     *  This is basically the same as calling
     *      treeNodeEx(label, TreeNodeFlag.CollapsingHeader)
     *  You can remove the _NoTreePushOnOpen flag if you want behavior closer to normal TreeNode().
     *  If returning 'true' the header is open. doesn't indent nor push on ID stack. user doesn't have to call TreePop().   */
    fun collapsingHeader(label: String, flags: TreeNodeFlags = 0): Boolean {

        val window = currentWindow
        if (window.skipItems) return false

        return treeNodeBehavior(window.getId(label), flags or Tnf.CollapsingHeader, label)
    }

    /** when 'open' isn't NULL, display an additional small close button on upper right of the header */
    fun collapsingHeader(label: String, open: KMutableProperty0<Boolean>?, flags_: TreeNodeFlags = 0): Boolean {

        val window = currentWindow
        if (window.skipItems) return false

        if (open?.get()  == false) return false

        val id = window.getId(label)
        val flags = flags_ or Tnf.CollapsingHeader or if (open != null) Tnf.AllowItemOverlap or Tnf._ClipLabelForTrailingButton else 0
        val isOpen = treeNodeBehavior(id, flags, label)
        if (open != null) {
            /*  Create a small overlapping close button
                FIXME: We can evolve this into user accessible helpers to add extra buttons on title bars, headers, etc.
                FIXME: CloseButton can overlap into text, need find a way to clip the text somehow.
             */
            val buttonSize = g.fontSize
            val buttonPos = Vec2(
                    max(window.dc.lastItemRect.min.x, window.dc.lastItemRect.max.x - style.framePadding.x * 2f - buttonSize),
                    (window.dc.lastItemRect.min.y))
            itemHoveredDataBackup {
                if (closeButton(window.getId(id + 1), buttonPos))
                    open.set(false)
            }
        }
        return isOpen
    }

    /** Set next TreeNode/CollapsingHeader open state.  */
    fun setNextItemOpen(isOpen: Boolean, cond: Cond = Cond.Always) {
        if (g.currentWindow!!.skipItems) return
        g.nextItemData.apply {
            flags = flags or NextItemDataFlag.HasOpen
            openVal = isOpen
            openCond = cond.takeUnless { it == Cond.None } ?: Cond.Always
        }
    }
}