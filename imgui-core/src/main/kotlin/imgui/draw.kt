package imgui

import gli_.hasnt
import glm_.BYTES
import glm_.f
import glm_.func.common.max
import glm_.glm
import glm_.vec2.Vec2
import glm_.vec4.Vec4
import imgui.ImGui.io
import imgui.ImGui.shadeVertsLinearUV
import imgui.imgui.g
import imgui.internal.*
import kool.*
import kool.lib.indices
import java.nio.ByteBuffer
import java.util.Stack
import kotlin.math.sqrt
import imgui.internal.DrawCornerFlag as Dcf
import uno.kotlin.plusAssign

/** Draw callbacks for advanced uses.
 *  NB: You most likely do NOT need to use draw callbacks just to create your own widget or customized UI rendering,
 *  you can poke into the draw list for that! Draw callback may be useful for example to:
 *      A) Change your GPU render state,
 *      B) render a complex 3D scene inside a UI element without an intermediate texture/render target, etc.
 *  The expected behavior from your rendering function is 'if (cmd.UserCallback != NULL) { cmd.UserCallback(parent_list, cmd); } else { RenderTriangles() }'
 *  If you want to override the signature of ImDrawCallback, you can simply use e.g. '#define ImDrawCallback MyDrawCallback' (in imconfig.h) + update rendering back-end accordingly. */
typealias DrawCallback = (DrawList, DrawCmd) -> Unit

/** A single draw command within a parent ImDrawList (generally maps to 1 GPU draw call, unless it is a callback)
 *
 *  Typically, 1 command = 1 gpu draw call (unless command is a callback)
 *
 *  Pre 1.71 back-ends will typically ignore the VtxOffset/IdxOffset fields. When 'io.BackendFlags & ImGuiBackendFlags_RendererHasVtxOffset'
 *  is enabled, those fields allow us to render meshes larger than 64K vertices while keeping 16-bits indices.*/
class DrawCmd {

    constructor()

    constructor(drawCmd: DrawCmd) {
        put(drawCmd)
    }

    /** Number of indices (multiple of 3) to be rendered as triangles. Vertices are stored in the callee ImDrawList's
     *  vtx_buffer[] array, indices in idx_buffer[].    */
    var elemCount = 0
    /** Clipping rectangle (x1, y1, x2, y2). Subtract ImDrawData->DisplayPos to get clipping rectangle in "viewport" coordinates */
    var clipRect = Vec4()
    /** User-provided texture ID. Set by user in ImfontAtlas::SetTexID() for fonts or passed to Image*() functions.
    Ignore if never using images or multiple fonts atlas.   */
    var textureId: TextureID? = null
    /** Start offset in vertex buffer. Pre-1.71 or without ImGuiBackendFlags_RendererHasVtxOffset: always 0.
     *  With ImGuiBackendFlags_RendererHasVtxOffset: may be >0 to support meshes larger than 64K vertices with 16-bits indices. */
    var vtxOffset = 0
    /** Start offset in index buffer. Always equal to sum of ElemCount drawn so far. */
    var idxOffset = 0
    /** If != NULL, call the function instead of rendering the vertices. clip_rect and texture_id will be set normally. */
    var userCallback: DrawCallback? = null

    /** Special Draw callback value to request renderer back-end to reset the graphics/render state.
     *  The renderer back-end needs to handle this special value, otherwise it will crash trying to call a function at this address.
     *  This is useful for example if you submitted callbacks which you know have altered the render state and you want it to be restored.
     *  It is not done by default because they are many perfectly useful way of altering render state for imgui contents
     *  (e.g. changing shader/blending settings before an Image call). */
    var resetRenderState = false

    var userCallbackData: ByteBuffer? = null
//    void*           UserCallbackData;       // The draw callback code can access this.

    infix fun put(drawCmd: DrawCmd) {
        elemCount = drawCmd.elemCount
        clipRect put drawCmd.clipRect
        textureId = drawCmd.textureId
        vtxOffset = drawCmd.vtxOffset
        idxOffset = drawCmd.idxOffset
        userCallback = drawCmd.userCallback
        resetRenderState = drawCmd.resetRenderState
        userCallbackData = drawCmd.userCallbackData
    }
}

/** Vertex index
 *  (to allow large meshes with 16-bits indices: set 'io.BackendFlags |= ImGuiBackendFlags_RendererHasVtxOffset' and handle ImDrawCmd::VtxOffset in the renderer back-end)
 *  (to use 32-bits indices: override with '#define ImDrawIdx unsigned int' in imconfig.h) */
typealias DrawIdx = Int

/** Vertex layout
 *
 *  A single vertex (pos + uv + col = 20 bytes by default. Override layout with IMGUI_OVERRIDE_DRAWVERT_STRUCT_LAYOUT) */
class DrawVert {

    var pos = Vec2()
    var uv = Vec2()
    var col = 0

    companion object {
        val size = 2 * Vec2.size + Int.BYTES
        val ofsPos = 0f
        val ofsUv = Vec2.size
        val ofsCol = Vec2.size * 2
    }

    override fun toString() = "pos: $pos, uv: $uv, col: $col"
}

/** Temporary storage to output draw commands out of order, used by ImDrawListSplitter and ImDrawList::ChannelsSplit()
 *
 *  Draw channels are used by the Columns API to "split" the render list into different channels while building, so
 *  items of each column can be batched together.
 *  You can also use them to simulate drawing layers and submit primitives in a different order than how they will be
 *  rendered.   */
class DrawChannel {

    val _cmdBuffer = Stack<DrawCmd>()
    var _idxBuffer = IntBuffer(0)

    fun memset0() {
        _cmdBuffer.clear()
        _idxBuffer.free()
        _idxBuffer = IntBuffer(0)
    }

    fun resize0() {
        _cmdBuffer.clear()
        _idxBuffer.lim = 0
    }
}

//-----------------------------------------------------------------------------
// ImDrawListSplitter
//-----------------------------------------------------------------------------
// FIXME: This may be a little confusing, trying to be a little too low-level/optimal instead of just doing vector swap..
//-----------------------------------------------------------------------------
/** Split/Merge functions are used to split the draw list into different layers which can be drawn into out of order.
 *  This is used by the Columns api, so items of each column can be batched together in a same draw call. */
class DrawListSplitter {
    /** Current channel number (0) */
    var _current = 0
    /** Number of active channels (1+) */
    var _count = 0
    /** Draw channels (not resized down so _Count might be < Channels.Size) */
    val _channels = Stack<DrawChannel>()

    init {
        clear()
    }

    fun clear() {
        _current = 0
        _count = 1
    } // Do not clear Channels[] so our allocations are reused next frame

    fun clearFreeMemory() {
        _channels.forEachIndexed { i, c ->
            //            if (i == _current)
//                memset(&_Channels[i], 0, sizeof(_Channels[i]));  // Current channel is a copy of CmdBuffer/IdxBuffer, don't destruct again
            c._cmdBuffer.clear()
            c._idxBuffer.clear()
        }
        _current = 0
        _count = 1
        _channels.clear()
    }

    fun split(drawList: DrawList, channelsCount: Int) {
        assert(_current == 0 && _count <= 1)
        val oldChannelsCount = _channels.size
        if (oldChannelsCount < channelsCount)
            for (i in oldChannelsCount until channelsCount)
                _channels += DrawChannel()
        _count = channelsCount

        // Channels[] (24/32 bytes each) hold storage that we'll swap with draw_list->_CmdBuffer/_IdxBuffer
        // The content of Channels[0] at this point doesn't matter. We clear it to make state tidy in a debugger but we don't strictly need to.
        // When we switch to the next channel, we'll copy draw_list->_CmdBuffer/_IdxBuffer into Channels[0] and then Channels[1] into draw_list->CmdBuffer/_IdxBuffer
        _channels[0].memset0()
        for (i in 1 until channelsCount) {
            if (i < oldChannelsCount)
                _channels[i].resize0()
            if (_channels[i]._cmdBuffer.isEmpty())
                _channels[i]._cmdBuffer.add(DrawCmd().apply {
                    clipRect put drawList._clipRectStack.last()
                    textureId = drawList._textureIdStack.last()
                })
        }
    }

    companion object {
        fun canMergeDrawCommands(a: DrawCmd, b: DrawCmd): Boolean = a.clipRect == b.clipRect &&
                a.textureId == b.textureId && a.vtxOffset == b.vtxOffset && a.userCallback == null && b.userCallback == null
    }

    fun merge(drawList: DrawList) {

        // Note that we never use or rely on channels.Size because it is merely a buffer that we never shrink back to 0 to keep all sub-buffers ready for use.
        if (_count <= 1) return

        setCurrentChannel(drawList, 0)
        if (drawList.cmdBuffer.lastOrNull()?.elemCount == 0)
            drawList.cmdBuffer.pop()

        // Calculate our final buffer sizes. Also fix the incorrect IdxOffset values in each command.
        var newCmdBufferCount = 0
        var newIdxBufferCount = 0
        var lastCmd = if (_count > 0 && drawList.cmdBuffer.isNotEmpty()) drawList.cmdBuffer.last() else null
        var idxOffset = lastCmd?.run { idxOffset + elemCount } ?: 0
        for (i in 1 until _count) {
            val ch = _channels[i]
            if (ch._cmdBuffer.lastOrNull()?.elemCount == 0)
                ch._cmdBuffer.pop()
            if (ch._cmdBuffer.isNotEmpty() && lastCmd != null && canMergeDrawCommands(lastCmd, ch._cmdBuffer[0])) {
                // Merge previous channel last draw command with current channel first draw command if matching.
                lastCmd.elemCount += ch._cmdBuffer[0].elemCount
                idxOffset += ch._cmdBuffer[0].elemCount
                TODO()
//                ch.cmdBuffer.erase(ch.CmdBuffer.Data);
            }
            if (ch._cmdBuffer.isNotEmpty())
                lastCmd = ch._cmdBuffer.last()
            newCmdBufferCount += ch._cmdBuffer.size
            newIdxBufferCount += ch._idxBuffer.lim
            ch._cmdBuffer.forEach {
                it.idxOffset = idxOffset
                idxOffset += it.elemCount
            }
        }
        for (i in 0 until newCmdBufferCount)
            drawList.cmdBuffer.push(DrawCmd())
        drawList.idxBuffer = drawList.idxBuffer.resize(drawList.idxBuffer.rem + newIdxBufferCount)

        // Write commands and indices in order (they are fairly small structures, we don't copy vertices only indices)
        var cmdWrite = drawList.cmdBuffer.size - newCmdBufferCount
        var idxWrite = drawList.idxBuffer.rem - newIdxBufferCount
        for (i in 1 until _count) {
            val ch = _channels[i]
            for (j in ch._cmdBuffer.indices)
                drawList.cmdBuffer[cmdWrite++] put ch._cmdBuffer[j]
            for (j in ch._idxBuffer.indices)
                drawList.idxBuffer[idxWrite++] = ch._idxBuffer[j]
        }
        drawList._idxWritePtr = idxWrite
        drawList.updateClipRect() // We call this instead of AddDrawCmd(), so that empty channels won't produce an extra draw call.
        _count = 1
    }

    fun setCurrentChannel(drawList: DrawList, idx: Int) {
        assert(idx < _count)
        if (_current == idx) return
        // Overwrite ImVector (12/16 bytes), four times. This is merely a silly optimization instead of doing .swap()
        _channels[_current]._cmdBuffer.clear()
        for (cmd in drawList.cmdBuffer)
            _channels[_current]._cmdBuffer.push(cmd)
        _channels[_current]._idxBuffer.free()
        _channels[_current]._idxBuffer = IntBuffer(drawList.idxBuffer)
        _current = idx
        drawList.cmdBuffer.clear()
        for (cmd in _channels[idx]._cmdBuffer)
            drawList.cmdBuffer.push(cmd)
        drawList.idxBuffer.free()
        drawList.idxBuffer = IntBuffer(_channels[idx]._idxBuffer)
        drawList._idxWritePtr = drawList.idxBuffer.lim
    }
}


/** A single draw command list (generally one per window, conceptually you may see this as a dynamic "mesh" builder)
 *
 *  Draw command list
 *  This is the low-level list of polygons that ImGui:: functions are filling. At the end of the frame,
 *  all command lists are passed to your ImGuiIO::RenderDrawListFn function for rendering.
 *  Each dear imgui window contains its own ImDrawList. You can use ImGui::GetWindowDrawList() to
 *  access the current window draw list and draw custom primitives.
 *  You can interleave normal ImGui:: calls and adding primitives to the current draw list.
 *  All positions are generally in pixel coordinates (top-left at (0,0), bottom-right at io.DisplaySize),
 *  but you are totally free to apply whatever transformation matrix to want to the data
 *  (if you apply such transformation you'll want to apply it to ClipRect as well)
 *  Important: Primitives are always added to the list and not culled (culling is done at render time and
 *  at a higher-level by ImGui::functions), if you use this API a lot consider coarse culling your drawn objects.
 *
 *  If you want to create ImDrawList instances, pass them ImGui::GetDrawListSharedData() or create and use your own
 *  DrawListSharedData (so you can use ImDrawList without ImGui)    */
class DrawList(sharedData: DrawListSharedData?) {

    // -----------------------------------------------------------------------------------------------------------------
    // This is what you have to render
    // -----------------------------------------------------------------------------------------------------------------

    /** Draw commands. Typically 1 command = 1 GPU draw call, unless the command is a callback.    */
    var cmdBuffer = Stack<DrawCmd>()
    /** Index buffer. Each command consume ImDrawCmd::ElemCount of those    */
    var idxBuffer = IntBuffer(0)
    /** Vertex buffer.  */
    var vtxBuffer = DrawVert_Buffer(0)


    // -----------------------------------------------------------------------------------------------------------------
    // [Internal, used while building lists]
    // -----------------------------------------------------------------------------------------------------------------

    /** Flags, you may poke into these to adjust anti-aliasing settings per-primitive. */
    var flags: DrawListFlags = 0
    /** Pointer to shared draw data (you can use ImGui::drawListSharedData to get the one from current ImGui context) */
    var _data: DrawListSharedData = sharedData ?: DrawListSharedData()
    /** Pointer to owner window's name for debugging    */
    var _ownerName = ""
    /** [Internal] Always 0 unless 'Flags & ImDrawListFlags_AllowVtxOffset'. */
    var _vtxCurrentOffset = 0
    /** [Internal] Generally == VtxBuffer.Size unless we are past 64K vertices, in which case this gets reset to 0. */
    var _vtxCurrentIdx = 0
    /** [Internal] point within VtxBuffer.Data after each add command (to avoid using the ImVector<> operators too much)    */
    var _vtxWritePtr = 0
    /** [Internal] point within IdxBuffer.Data after each add command (to avoid using the ImVector<> operators too much)    */
    var _idxWritePtr = 0

    val _clipRectStack = Stack<Vec4>()
    /** [Internal]  */
    val _textureIdStack = Stack<TextureID>()
    /** [Internal] current path building    */
    val _path = ArrayList<Vec2>()
    /** [Internal] for channels api */
    val _splitter = DrawListSplitter()

    fun destroy() = clearFreeMemory()

    /** Render-level scissoring. This is passed down to your render function but not used for CPU-side coarse clipping.
     *  Prefer using higher-level ImGui::PushClipRect() to affect logic (hit-testing and widget culling)    */
    fun pushClipRect(rect: Rect, intersectWithCurrentClipRect: Boolean = false) = pushClipRect(rect.min, rect.max, intersectWithCurrentClipRect)

    fun pushClipRect(crMin: Vec2, crMax: Vec2, intersectWithCurrentClipRect: Boolean = false) {

        val cr = Vec4(crMin, crMax)
        if (intersectWithCurrentClipRect && _clipRectStack.isNotEmpty()) {
            val current = _clipRectStack.last()
            if (cr.x < current.x) cr.x = current.x
            if (cr.y < current.y) cr.y = current.y
            if (cr.z > current.z) cr.z = current.z
            if (cr.w > current.w) cr.w = current.w
        }
        cr.z = cr.x max cr.z
        cr.w = cr.y max cr.w

        _clipRectStack += cr
        updateClipRect()
    }

    fun pushClipRectFullScreen() = pushClipRect(Vec2(_data.clipRectFullscreen), Vec2(_data.clipRectFullscreen.z, _data.clipRectFullscreen.w))

    fun popClipRect() {
        assert(_clipRectStack.isNotEmpty())
        _clipRectStack.pop()
        updateClipRect()
    }

    fun pushTextureId(textureId: TextureID) = _textureIdStack.push(textureId).run { updateTextureID() }

    fun popTextureId() = _textureIdStack.pop().also { updateTextureID() }


    // -----------------------------------------------------------------------------------------------------------------
    // Primitives
    // -----------------------------------------------------------------------------------------------------------------

    /** JVM it's safe to pass directly Vec2 istances, they wont be modified */
    fun addLine(a: Vec2, b: Vec2, col: Int, thickness: Float = 1f) {
        if (col hasnt COL32_A_MASK) return
        pathLineTo(a + Vec2(0.5f))
        pathLineTo(b + Vec2(0.5f))
        pathStroke(col, false, thickness)
    }

    /** we don't render 1 px sized rectangles properly.
     * @param a: upper-left
     * @param b: b: lower-right
     * (== upper-left + size)   */
    fun addRect(a: Vec2, b: Vec2, col: Int, rounding: Float = 0f, roundingCornersFlags: Int = Dcf.All.i, thickness: Float = 1f) {
        if (col hasnt COL32_A_MASK) return
        if (flags has DrawListFlag.AntiAliasedLines)
            pathRect(a + 0.5f, b - 0.5f, rounding, roundingCornersFlags)
        else    // Better looking lower-right corner and rounded non-AA shapes.
            pathRect(a + 0.5f, b - 0.49f, rounding, roundingCornersFlags)
        pathStroke(col, true, thickness)
    }

    /** @param a: upper-left
     *  @param b: lower-right
     *  (== upper-left + size) */
    fun addRectFilled(a: Vec2, b: Vec2, col: Int, rounding: Float = 0f, roundingCornersFlags: Int = Dcf.All.i) {
        if (col hasnt COL32_A_MASK) return
        if (rounding > 0f) {
            pathRect(a, b, rounding, roundingCornersFlags)
            pathFillConvex(col)
        } else {
            primReserve(6, 4)
            primRect(a, b, col)
        }
    }

    fun addRectFilledMultiColor(a: Vec2, c: Vec2, colUprLeft: Int, colUprRight: Int, colBotRight: Int, colBotLeft: Int) {

        if ((colUprLeft or colUprRight or colBotRight or colBotLeft) hasnt COL32_A_MASK) return

        val uv = _data.texUvWhitePixel
        primReserve(6, 4)
        primWriteIdx(_vtxCurrentIdx); primWriteIdx(_vtxCurrentIdx + 1); primWriteIdx(_vtxCurrentIdx + 2)
        primWriteIdx(_vtxCurrentIdx); primWriteIdx(_vtxCurrentIdx + 2); primWriteIdx(_vtxCurrentIdx + 3)
        primWriteVtx(a, uv, colUprLeft)
        primWriteVtx(Vec2(c.x, a.y), uv, colUprRight)
        primWriteVtx(c, uv, colBotRight)
        primWriteVtx(Vec2(a.x, c.y), uv, colBotLeft)
    }

    fun addQuad(a: Vec2, b: Vec2, c: Vec2, d: Vec2, col: Int, thickness: Float = 1f) {

        if (col hasnt COL32_A_MASK) return

        pathLineTo(a)
        pathLineTo(b)
        pathLineTo(c)
        pathLineTo(d)
        pathStroke(col, true, thickness)
    }

    fun addQuadFilled(a: Vec2, b: Vec2, c: Vec2, d: Vec2, col: Int) {

        if (col hasnt COL32_A_MASK) return

        pathLineTo(a)
        pathLineTo(b)
        pathLineTo(c)
        pathLineTo(d)
        pathFillConvex(col)
    }

    fun addTriangle(a: Vec2, b: Vec2, c: Vec2, col: Int, thickness: Float = 1f) {

        if (col hasnt COL32_A_MASK) return

        pathLineTo(a)
        pathLineTo(b)
        pathLineTo(c)
        pathStroke(col, true, thickness)
    }

    fun addTriangleFilled(a: Vec2, b: Vec2, c: Vec2, col: Int) {

        if (col hasnt COL32_A_MASK) return

        pathLineTo(a)
        pathLineTo(b)
        pathLineTo(c)
        pathFillConvex(col)
    }

    fun addCircle(centre: Vec2, radius: Float, col: Int, numSegments: Int = 12, thickness: Float = 1f) {

        if (col hasnt COL32_A_MASK || numSegments <= 2) return

        // Because we are filling a closed shape we remove 1 from the count of segments/points
        val aMax = glm.PIf * 2f * (numSegments - 1f) / numSegments
        pathArcTo(centre, radius - 0.5f, 0f, aMax, numSegments - 1)
        pathStroke(col, true, thickness)
    }

    fun addCircleFilled(centre: Vec2, radius: Float, col: Int, numSegments: Int = 12) {

        if (col hasnt COL32_A_MASK || numSegments <= 2) return

        // Because we are filling a closed shape we remove 1 from the count of segments/points
        val aMax = glm.PIf * 2f * (numSegments - 1f) / numSegments
        pathArcTo(centre, radius, 0f, aMax, numSegments - 1)
        pathFillConvex(col)
    }

    fun addText(pos: Vec2, col: Int, text: CharArray, textEnd: Int = text.size) = addText(g.font, g.fontSize, pos, col, text, textEnd)

    fun addText(font_: Font?, fontSize_: Float, pos: Vec2, col: Int, text: CharArray, textEnd_: Int = text.size, wrapWidth: Float = 0f,
                cpuFineClipRect: Vec4? = null) {

        if ((col and COL32_A_MASK) == 0) return

        var textEnd = textEnd_
        if (textEnd == 0)
            textEnd = text.strlen
        if (textEnd == 0)
            return

        // Pull default font/size from the shared ImDrawListSharedData instance
        val font = font_ ?: _data.font!!
        val fontSize = if (fontSize_ == 0f) _data.fontSize else fontSize_

        assert(font.containerAtlas.texId == _textureIdStack.last()) {
            "Use high-level ImGui::pushFont() or low-level DrawList::pushTextureId() to change font_"
        }

        val clipRect = Vec4(_clipRectStack.last())
        cpuFineClipRect?.let {
            clipRect.x = glm.max(clipRect.x, cpuFineClipRect.x)
            clipRect.y = glm.max(clipRect.y, cpuFineClipRect.y)
            clipRect.z = glm.min(clipRect.z, cpuFineClipRect.z)
            clipRect.w = glm.min(clipRect.w, cpuFineClipRect.w)
        }
        font.renderText(this, fontSize, pos, col, clipRect, text, textEnd, wrapWidth, cpuFineClipRect != null)
    }

    fun addImage(userTextureId: TextureID, a: Vec2, b: Vec2, uvA: Vec2 = Vec2(0), uvB: Vec2 = Vec2(1), col: Int = COL32_WHITE) {

        if (col hasnt COL32_A_MASK) return

        val pushTextureId = _textureIdStack.isEmpty() || userTextureId != _textureIdStack.last()
        if (pushTextureId) pushTextureId(userTextureId)

        primReserve(6, 4)
        primRectUV(a, b, uvA, uvB, col)

        if (pushTextureId) popTextureId()
    }

    fun addImageQuad(userTextureId: TextureID, a: Vec2, b: Vec2, c: Vec2, d: Vec2, uvA: Vec2 = Vec2(0), uvB: Vec2 = Vec2(1, 0),
                     uvC: Vec2 = Vec2(1), uvD: Vec2 = Vec2(0, 1), col: Int = COL32_WHITE) {

        if (col hasnt COL32_A_MASK) return

        val pushTextureId = _textureIdStack.isEmpty() || userTextureId != _textureIdStack.last()
        if (pushTextureId)
            pushTextureId(userTextureId)

        primReserve(6, 4)
        primQuadUV(a, b, c, d, uvA, uvB, uvC, uvD, col)

        if (pushTextureId)
            popTextureId()
    }

    fun addImageRounded(userTextureId: TextureID, a: Vec2, b: Vec2, uvA: Vec2, uvB: Vec2, col: Int, rounding: Float,
                        roundingCorners: Int = Dcf.All.i) {
        if (col hasnt COL32_A_MASK) return

        if (rounding <= 0f || roundingCorners hasnt Dcf.All) {
            addImage(userTextureId, a, b, uvA, uvB, col)
            return
        }

        val pushTextureId = _textureIdStack.isEmpty() || userTextureId != _textureIdStack.last()
        if (pushTextureId) pushTextureId(userTextureId)

        val vertStartIdx = vtxBuffer.size
        pathRect(a, b, rounding, roundingCorners)
        pathFillConvex(col)
        val vertEndIdx = vtxBuffer.size
        shadeVertsLinearUV(this, vertStartIdx, vertEndIdx, a, b, uvA, uvB, true)

        if (pushTextureId) popTextureId()
    }

    /** TODO: Thickness anti-aliased lines cap are missing their AA fringe.
     *  We avoid using the ImVec2 math operators here to reduce cost to a minimum for debug/non-inlined builds. */
    fun addPolyline(points: ArrayList<Vec2>, col: Int, closed: Boolean, thickness: Float) {

        if (points.size < 2) return

        val uv = Vec2(_data.texUvWhitePixel)

        var count = points.size
        if (!closed)
            count = points.lastIndex

        val thickLine = thickness > 1f

        if (flags has DrawListFlag.AntiAliasedLines) {
            // Anti-aliased stroke
            val AA_SIZE = 1f
            val colTrans = col wo COL32_A_MASK

            val idxCount = count * if (thickLine) 18 else 12
            val vtxCount = points.size * if (thickLine) 4 else 3
            primReserve(idxCount, vtxCount)
            vtxBuffer.pos = _vtxWritePtr
            idxBuffer.pos = _idxWritePtr

            // Temporary buffer
            val temp = Array(points.size * if (thickLine) 5 else 3) { Vec2() }
            val tempPointsIdx = points.size

            for (i1 in 0 until count) {
                val i2 = if (i1 + 1 == points.size) 0 else i1 + 1
                var dx = points[i2].x - points[i1].x
                var dy = points[i2].y - points[i1].y
                //IM_NORMALIZE2F_OVER_ZERO(dx, dy);
                val d2 = dx * dx + dy * dy
                if (d2 > 0f) {
                    val invLen = 1f / sqrt(d2)
                    dx *= invLen
                    dy *= invLen
                }
                temp[i1].x = dy
                temp[i1].y = -dx
            }
            if (!closed) temp[points.size - 1] = temp[points.size - 2]

            if (!thickLine) {
                if (!closed) {
                    temp[tempPointsIdx + 0] = points[0] + temp[0] * AA_SIZE
                    temp[tempPointsIdx + 1] = points[0] - temp[0] * AA_SIZE
                    temp[tempPointsIdx + (points.size - 1) * 2 + 0] = points[points.size - 1] + temp[points.size - 1] * AA_SIZE
                    temp[tempPointsIdx + (points.size - 1) * 2 + 1] = points[points.size - 1] - temp[points.size - 1] * AA_SIZE
                }

                // FIXME-OPT: Merge the different loops, possibly remove the temporary buffer.
                var idx1 = _vtxCurrentIdx
                for (i1 in 0 until count) {
                    val i2 = if ((i1 + 1) == points.size) 0 else i1 + 1
                    val idx2 = if ((i1 + 1) == points.size) _vtxCurrentIdx else idx1 + 3

                    // Average normals
                    var dmX = (temp[i1].x + temp[i2].x) * 0.5f
                    var dmY = (temp[i1].y + temp[i2].y) * 0.5f
//                    IM_FIXNORMAL2F(dm_x, dm_y)
                    run {
                        var d2 = dmX * dmX + dmY * dmY
                        if (d2 < 0.5f)
                            d2 = 0.5f
                        val invLensq = 1f / d2
                        dmX *= invLensq
                        dmY *= invLensq
                    }
                    dmX *= AA_SIZE
                    dmY *= AA_SIZE

                    // Add temporary vertexes
                    val outVtxIdx = tempPointsIdx + i2 * 2
                    temp[outVtxIdx + 0].x = points[i2].x + dmX
                    temp[outVtxIdx + 0].y = points[i2].y + dmY
                    temp[outVtxIdx + 1].x = points[i2].x - dmX
                    temp[outVtxIdx + 1].y = points[i2].y - dmY

                    // Add indexes
                    idxBuffer += idx2 + 0; idxBuffer += idx1 + 0; idxBuffer += idx1 + 2
                    idxBuffer += idx1 + 2; idxBuffer += idx2 + 2; idxBuffer += idx2 + 0
                    idxBuffer += idx2 + 1; idxBuffer += idx1 + 1; idxBuffer += idx1 + 0
                    idxBuffer += idx1 + 0; idxBuffer += idx2 + 0; idxBuffer += idx2 + 1
                    _idxWritePtr += 12

                    idx1 = idx2
                }

                // Add vertexes
                for (i in 0 until points.size) {
                    vtxBuffer += points[i]; vtxBuffer += uv; vtxBuffer += col
                    vtxBuffer += temp[tempPointsIdx + i * 2 + 0]; vtxBuffer += uv; vtxBuffer += colTrans
                    vtxBuffer += temp[tempPointsIdx + i * 2 + 1]; vtxBuffer += uv; vtxBuffer += colTrans
                    _vtxWritePtr += 3
                }
            } else {
                val halfInnerThickness = (thickness - AA_SIZE) * 0.5f
                if (!closed) {
                    temp[tempPointsIdx + 0] = points[0] + temp[0] * (halfInnerThickness + AA_SIZE)
                    temp[tempPointsIdx + 1] = points[0] + temp[0] * (halfInnerThickness)
                    temp[tempPointsIdx + 2] = points[0] - temp[0] * (halfInnerThickness)
                    temp[tempPointsIdx + 3] = points[0] - temp[0] * (halfInnerThickness + AA_SIZE)
                    temp[tempPointsIdx + (points.size - 1) * 4 + 0] = points[points.size - 1] + temp[points.size - 1] * (halfInnerThickness + AA_SIZE)
                    temp[tempPointsIdx + (points.size - 1) * 4 + 1] = points[points.size - 1] + temp[points.size - 1] * halfInnerThickness
                    temp[tempPointsIdx + (points.size - 1) * 4 + 2] = points[points.size - 1] - temp[points.size - 1] * halfInnerThickness
                    temp[tempPointsIdx + (points.size - 1) * 4 + 3] = points[points.size - 1] - temp[points.size - 1] * (halfInnerThickness + AA_SIZE)
                }

                // FIXME-OPT: Merge the different loops, possibly remove the temporary buffer.
                var idx1 = _vtxCurrentIdx
                for (i1 in 0 until count) {
                    val i2 = if ((i1 + 1) == points.size) 0 else i1 + 1
                    val idx2 = if ((i1 + 1) == points.size) _vtxCurrentIdx else idx1 + 4

                    // Average normals
                    var dmX = (temp[i1].x + temp[i2].x) * 0.5f
                    var dmY = (temp[i1].y + temp[i2].y) * 0.5f
//                    IM_FIXNORMAL2F(dm_x, dm_y)
                    run {
                        var d2 = dmX * dmX + dmY * dmY
                        if (d2 < 0.5f)
                            d2 = 0.5f
                        val invLensq = 1f / d2
                        dmX *= invLensq
                        dmY *= invLensq
                    }
                    val dmOutX = dmX * (halfInnerThickness + AA_SIZE)
                    val dmOutY = dmY * (halfInnerThickness + AA_SIZE)
                    val dmInX = dmX * halfInnerThickness
                    val dmInY = dmY * halfInnerThickness

                    // Add temporary vertexes
                    val outVtxIdx = tempPointsIdx + i2 * 4
                    temp[outVtxIdx + 0].x = points[i2].x + dmOutX
                    temp[outVtxIdx + 0].y = points[i2].y + dmOutY
                    temp[outVtxIdx + 1].x = points[i2].x + dmInX
                    temp[outVtxIdx + 1].y = points[i2].y + dmInY
                    temp[outVtxIdx + 2].x = points[i2].x - dmInX
                    temp[outVtxIdx + 2].y = points[i2].y - dmInY
                    temp[outVtxIdx + 3].x = points[i2].x - dmOutX
                    temp[outVtxIdx + 3].y = points[i2].y - dmOutY

                    // Add indexes
                    idxBuffer += idx2 + 1; idxBuffer += idx1 + 1; idxBuffer += idx1 + 2
                    idxBuffer += idx1 + 2; idxBuffer += idx2 + 2; idxBuffer += idx2 + 1
                    idxBuffer += idx2 + 1; idxBuffer += idx1 + 1; idxBuffer += idx1 + 0
                    idxBuffer += idx1 + 0; idxBuffer += idx2 + 0; idxBuffer += idx2 + 1
                    idxBuffer += idx2 + 2; idxBuffer += idx1 + 2; idxBuffer += idx1 + 3
                    idxBuffer += idx1 + 3; idxBuffer += idx2 + 3; idxBuffer += idx2 + 2
                    _idxWritePtr += 18

                    idx1 = idx2
                }

                // Add vertexes
                for (i in 0 until points.size) {
                    vtxBuffer += temp[tempPointsIdx + i * 4 + 0]; vtxBuffer += uv; vtxBuffer += colTrans
                    vtxBuffer += temp[tempPointsIdx + i * 4 + 1]; vtxBuffer += uv; vtxBuffer += col
                    vtxBuffer += temp[tempPointsIdx + i * 4 + 2]; vtxBuffer += uv; vtxBuffer += col
                    vtxBuffer += temp[tempPointsIdx + i * 4 + 3]; vtxBuffer += uv; vtxBuffer += colTrans
                    _vtxWritePtr += 4
                }
            }
            _vtxCurrentIdx += vtxCount
        } else {
            // Non Anti-aliased Stroke
            val idxCount = count * 6
            val vtxCount = count * 4      // FIXME-OPT: Not sharing edges
            primReserve(idxCount, vtxCount)
            vtxBuffer.pos = _vtxWritePtr
            idxBuffer.pos = _idxWritePtr

            for (i1 in 0 until count) {
                val i2 = if ((i1 + 1) == points.size) 0 else i1 + 1
                val p1 = points[i1]
                val p2 = points[i2]

                var dX = p2.x - p1.x
                var dY = p2.y - p1.y
//                IM_NORMALIZE2F_OVER_ZERO(dX, dY)
                val d2 = dX * dX + dY * dY
                if (d2 > 0f) {
                    val invLen = 1f / sqrt(d2)
                    dX *= invLen
                    dY *= invLen
                }
                dX *= thickness * 0.5f
                dY *= thickness * 0.5f

                vtxBuffer += p1.x + dY; vtxBuffer += p1.y - dX; vtxBuffer += uv; vtxBuffer += col
                vtxBuffer += p2.x + dY; vtxBuffer += p2.y - dX; vtxBuffer += uv; vtxBuffer += col
                vtxBuffer += p2.x - dY; vtxBuffer += p2.y + dX; vtxBuffer += uv; vtxBuffer += col
                vtxBuffer += p1.x - dY; vtxBuffer += p1.y + dX; vtxBuffer += uv; vtxBuffer += col
                _vtxWritePtr += 4

                idxBuffer += _vtxCurrentIdx; idxBuffer += _vtxCurrentIdx + 1; idxBuffer += _vtxCurrentIdx + 2
                idxBuffer += _vtxCurrentIdx; idxBuffer += _vtxCurrentIdx + 2; idxBuffer += _vtxCurrentIdx + 3
                _idxWritePtr += 6
                _vtxCurrentIdx += 4
            }
        }
        vtxBuffer.pos = 0
        idxBuffer.pos = 0
    }

    /** We intentionally avoid using ImVec2 and its math operators here to reduce cost to a minimum for debug/non-inlined builds.
     *
     *  Note: Anti-aliased filling requires points to be in clockwise order. */
    fun addConvexPolyFilled(points: ArrayList<Vec2>, col: Int) {

        if (points.size < 3)
            return

        val uv = Vec2(_data.texUvWhitePixel)

        if (flags has DrawListFlag.AntiAliasedFill) {
            // Anti-aliased Fill
            val AA_SIZE = 1f
            val colTrans = col wo COL32_A_MASK
            val idxCount = (points.size - 2) * 3 + points.size * 6
            val vtxCount = points.size * 2
            primReserve(idxCount, vtxCount)
            vtxBuffer.pos = _vtxWritePtr
            idxBuffer.pos = _idxWritePtr

            // Add indexes for fill
            val vtxInnerIdx = _vtxCurrentIdx
            val vtxOuterIdx = _vtxCurrentIdx + 1
            for (i in 2 until points.size) {
                idxBuffer += vtxInnerIdx; idxBuffer += vtxInnerIdx + ((i - 1) shl 1); idxBuffer += vtxInnerIdx + (i shl 1)
                _idxWritePtr += 3
            }

            // Compute normals
            val tempNormals = Array(points.size) { Vec2() }
            var i0 = points.lastIndex
            var i1 = 0
            while (i1 < points.size) {
                val p0 = points[i0]
                val p1 = points[i1]
                var dX = p1.x - p0.x
                var dY = p1.y - p0.y
//                IM_NORMALIZE2F_OVER_ZERO(dx, dy)
                val d2 = dX * dX + dY * dY
                if (d2 > 0f) {
                    val invLen = 1f / sqrt(d2)
                    dX *= invLen
                    dY *= invLen
                }
                tempNormals[i0].x = dY
                tempNormals[i0].y = -dX
                i0 = i1++
            }

            i0 = points.lastIndex
            i1 = 0
            while (i1 < points.size) {
                // Average normals
                val n0 = tempNormals[i0]
                val n1 = tempNormals[i1]
                var dmX = (n0.x + n1.x) * 0.5f
                var dmY = (n0.y + n1.y) * 0.5f
//                    IM_FIXNORMAL2F(dm_x, dm_y)
                run {
                    var d2 = dmX * dmX + dmY * dmY
                    if (d2 < 0.5f)
                        d2 = 0.5f
                    val invLensq = 1f / d2
                    dmX *= invLensq
                    dmY *= invLensq
                }
                dmX *= AA_SIZE * 0.5f
                dmY *= AA_SIZE * 0.5f

                // Add vertices
                vtxBuffer += points[i1].x - dmX; vtxBuffer += points[i1].y - dmY; vtxBuffer += uv; vtxBuffer += col      // Inner
                vtxBuffer += points[i1].x + dmX; vtxBuffer += points[i1].y + dmY; vtxBuffer += uv; vtxBuffer += colTrans // Outer
                _vtxWritePtr += 2

                // Add indexes for fringes
                idxBuffer += vtxInnerIdx + (i1 shl 1); idxBuffer += vtxInnerIdx + (i0 shl 1); idxBuffer += vtxOuterIdx + (i0 shl 1)
                idxBuffer += vtxOuterIdx + (i0 shl 1); idxBuffer += vtxOuterIdx + (i1 shl 1); idxBuffer += vtxInnerIdx + (i1 shl 1)
                _idxWritePtr += 6

                i0 = i1++
            }
            _vtxCurrentIdx += vtxCount
        } else {
            // Non Anti-aliased Fill
            val idxCount = (points.size - 2) * 3
            val vtxCount = points.size
            primReserve(idxCount, vtxCount)
            vtxBuffer.pos = _vtxWritePtr
            idxBuffer.pos = _idxWritePtr
            for (i in 0 until vtxCount) {
                vtxBuffer += points[i]; vtxBuffer += uv; vtxBuffer += col
                _vtxWritePtr++
            }
            for (i in 2 until points.size) {
                idxBuffer += _vtxCurrentIdx; idxBuffer += _vtxCurrentIdx + i - 1; idxBuffer[_idxWritePtr + 2] = _vtxCurrentIdx + i
                _idxWritePtr += 3
            }
            _vtxCurrentIdx += vtxCount
        }
        vtxBuffer.pos = 0
        idxBuffer.pos = 0
    }

    fun addBezierCurve(pos0: Vec2, cp0: Vec2, cp1: Vec2, pos1: Vec2, col: Int, thickness: Float, numSegments: Int = 0) {
        if (col hasnt COL32_A_MASK) return

        pathLineTo(pos0)
        pathBezierCurveTo(cp0, cp1, pos1, numSegments)
        pathStroke(col, false, thickness)
    }


    // -----------------------------------------------------------------------------------------------------------------
    // Stateful path API, add points then finish with PathFillConvex() or PathStroke()
    // -----------------------------------------------------------------------------------------------------------------

    fun pathClear() = _path.clear()

    fun pathLineTo(pos: Vec2) = _path.add(pos)

    fun pathLineToMergeDuplicate(pos: Vec2) {
        if (_path.isEmpty() || _path.last() != pos) _path += pos
    }

    /** Note: Anti-aliased filling requires points to be in clockwise order. */
    fun pathFillConvex(col: Int) = addConvexPolyFilled(_path, col).also { pathClear() }

    /** rounding_corners_flags: 4-bits corresponding to which corner to round   */
    fun pathStroke(col: Int, closed: Boolean, thickness: Float = 1.0f) = addPolyline(_path, col, closed, thickness).also { pathClear() }

    fun pathArcTo(centre: Vec2, radius: Float, aMin: Float, aMax: Float, numSegments: Int = 10) {
        if (radius == 0f) {
            _path += centre
            return
        }
        // Note that we are adding a point at both a_min and a_max.
        // If you are trying to draw a full closed circle you don't want the overlapping points!
        for (i in 0..numSegments) {
            val a = aMin + (i.f / numSegments) * (aMax - aMin)
            _path += Vec2(centre.x + glm.cos(a) * radius, centre.y + glm.sin(a) * radius)
        }
    }

    /** Use precomputed angles for a 12 steps circle    */
    fun pathArcToFast(centre: Vec2, radius: Float, aMinOf12: Int, aMaxOf12: Int) {

        if (radius == 0f || aMinOf12 > aMaxOf12) {
            _path += centre
            return
        }
        for (a in aMinOf12..aMaxOf12) {
            val c = _data.circleVtx12[a % _data.circleVtx12.size]
            _path += Vec2(centre.x + c.x * radius, centre.y + c.y * radius)
        }
    }

    fun pathBezierCurveTo(p2: Vec2, p3: Vec2, p4: Vec2, numSegments: Int = 0) {

        val p1 = _path.last()
        if (numSegments == 0)
        // Auto-tessellated
            pathBezierToCasteljau(_path, p1.x, p1.y, p2.x, p2.y, p3.x, p3.y, p4.x, p4.y, ImGui.style.curveTessellationTol, 0)
        else {
            val t_step = 1.0f / numSegments.f
            for (i_step in 1 until numSegments + 1) {
                val t = t_step * i_step
                val u = 1.0f - t
                val w1 = u * u * u
                val w2 = 3 * u * u * t
                val w3 = 3 * u * t * t
                val w4 = t * t * t
                _path.add(Vec2(w1 * p1.x + w2 * p2.x + w3 * p3.x + w4 * p4.x, w1 * p1.y + w2 * p2.y + w3 * p3.y + w4 * p4.y))
            }
        }
    }

    private fun pathBezierToCasteljau(path: ArrayList<Vec2>, x1: Float, y1: Float, x2: Float, y2: Float, x3: Float, y3: Float, x4: Float, y4: Float, tess_tol: Float, level: Int) {
        val dx = x4 - x1
        val dy = y4 - y1
        var d2 = ((x2 - x4) * dy - (y2 - y4) * dx)
        var d3 = ((x3 - x4) * dy - (y3 - y4) * dx)
        d2 = if (d2 >= 0) d2 else -d2
        d3 = if (d3 >= 0) d3 else -d3
        if ((d2 + d3) * (d2 + d3) < tess_tol * (dx * dx + dy * dy)) {
            path.add(Vec2(x4, y4))
        } else if (level < 10) {
            val x12 = (x1 + x2) * 0.5f
            val y12 = (y1 + y2) * 0.5f
            val x23 = (x2 + x3) * 0.5f
            val y23 = (y2 + y3) * 0.5f
            val x34 = (x3 + x4) * 0.5f
            val y34 = (y3 + y4) * 0.5f
            val x123 = (x12 + x23) * 0.5f
            val y123 = (y12 + y23) * 0.5f
            val x234 = (x23 + x34) * 0.5f
            val y234 = (y23 + y34) * 0.5f
            val x1234 = (x123 + x234) * 0.5f
            val y1234 = (y123 + y234) * 0.5f

            pathBezierToCasteljau(path, x1, y1, x12, y12, x123, y123, x1234, y1234, tess_tol, level + 1)
            pathBezierToCasteljau(path, x1234, y1234, x234, y234, x34, y34, x4, y4, tess_tol, level + 1)
        }
    }

    fun pathRect(a: Vec2, b: Vec2, rounding_: Float = 0f, roundingCorners: Int = Dcf.All.i) {

        var cond = ((roundingCorners and Dcf.Top) == Dcf.Top.i) || ((roundingCorners and Dcf.Bot) == Dcf.Bot.i) // TODO consider simplyfing
        var rounding = glm.min(rounding_, glm.abs(b.x - a.x) * (if (cond) 0.5f else 1f) - 1f)
        cond = ((roundingCorners and Dcf.Left) == Dcf.Left.i) || ((roundingCorners and Dcf.Right) == Dcf.Right.i)
        rounding = glm.min(rounding, glm.abs(b.y - a.y) * (if (cond) 0.5f else 1f) - 1f)

        if (rounding <= 0f || roundingCorners == 0) {
            pathLineTo(a)
            pathLineTo(Vec2(b.x, a.y))
            pathLineTo(b)
            pathLineTo(Vec2(a.x, b.y))
        } else {
            val roundingTL = if (roundingCorners has Dcf.TopLeft) rounding else 0f
            val roundingTR = if (roundingCorners has Dcf.TopRight) rounding else 0f
            val roundingBR = if (roundingCorners has Dcf.BotRight) rounding else 0f
            val roundingBL = if (roundingCorners has Dcf.BotLeft) rounding else 0f
            pathArcToFast(Vec2(a.x + roundingTL, a.y + roundingTL), roundingTL, 6, 9)
            pathArcToFast(Vec2(b.x - roundingTR, a.y + roundingTR), roundingTR, 9, 12)
            pathArcToFast(Vec2(b.x - roundingBR, b.y - roundingBR), roundingBR, 0, 3)
            pathArcToFast(Vec2(a.x + roundingBL, b.y - roundingBL), roundingBL, 3, 6)
        }
    }

    // -----------------------------------------------------------------------------------------------------------------
    // Advanced
    // -----------------------------------------------------------------------------------------------------------------
    /** Your rendering function must check for 'UserCallback' in ImDrawCmd and call the function instead of rendering
    triangles.  */
    fun addCallback(/*ImDrawCallback callback, void* callback_data*/): Nothing = TODO()

    /** This is useful if you need to forcefully create a new draw call (to allow for dependent rendering / blending).
    Otherwise primitives are merged into the same draw-call as much as possible */
    fun addDrawCmd() {
        val drawCmd = DrawCmd().apply {
            clipRect put currentClipRect
            textureId = currentTextureId
            vtxOffset = _vtxCurrentOffset
            idxOffset = idxBuffer.rem
        }
        assert(drawCmd.clipRect.x <= drawCmd.clipRect.z && drawCmd.clipRect.y <= drawCmd.clipRect.w)
        cmdBuffer.add(drawCmd)
    }

    /** Create a clone of the CmdBuffer/IdxBuffer/VtxBuffer. */
    fun cloneOutput(): DrawData? {
        val drawData = ImGui.drawData ?: return null
        return drawData.clone()
    }

    // -----------------------------------------------------------------------------------------------------------------
    // Advanced: Channels
    // - Use to split render into layers. By switching channels to can render out-of-order (e.g. submit foreground primitives before background primitives)
    // - Use to minimize draw calls (e.g. if going back-and-forth between multiple non-overlapping clipping rectangles, prefer to append into separate channels then merge at the end)
    // -----------------------------------------------------------------------------------------------------------------

    fun channelsSplit(count: Int) = _splitter.split(this, count)

    fun channelsMerge() = _splitter.merge(this)

    fun channelsSetCurrent(idx: Int) = _splitter.setCurrentChannel(this, idx)

    // -----------------------------------------------------------------------------------------------------------------
    // Internal helpers
    // NB: all primitives needs to be reserved via PrimReserve() beforehand!
    // -----------------------------------------------------------------------------------------------------------------
    fun clear() {
        cmdBuffer.clear()
        idxBuffer.resize(0) // we dont assign because it wont create a new istance for sure
        vtxBuffer.resize(0)
        flags = _data.initialFlags
        _vtxCurrentOffset = 0
        _vtxCurrentIdx = 0
        _vtxWritePtr = 0
        _idxWritePtr = 0
        _clipRectStack.clear()
        _textureIdStack.clear()
        _path.clear()
        _splitter.clear()
    }

    fun clearFreeMemory() {
        clear()
        _splitter.clearFreeMemory()
        vtxBuffer.data.free()
        idxBuffer.free()
    }

    /** NB: this can be called with negative count for removing primitives (as long as the result does not underflow)    */
    fun primReserve(idxCount: Int, vtxCount: Int) {

        // Large mesh support (when enabled)
        if (DrawIdx.BYTES == 2 && _vtxCurrentIdx + vtxCount >= (1 shl 16) && flags has DrawListFlag.AllowVtxOffset) {
            _vtxCurrentOffset = vtxBuffer.rem
            _vtxCurrentIdx = 0
            addDrawCmd()
        }

        cmdBuffer.last().elemCount += idxCount

        val vtxBufferOldSize = vtxBuffer.size
        vtxBuffer = vtxBuffer.resize(vtxBufferOldSize + vtxCount)
        _vtxWritePtr = vtxBufferOldSize

        val idxBufferOldSize = idxBuffer.lim
        idxBuffer = idxBuffer.resize(idxBufferOldSize + idxCount)
        _idxWritePtr = idxBufferOldSize
    }

    /** Fully unrolled with inline call to keep our debug builds decently fast.
    Axis aligned rectangle (composed of two triangles)  */
    fun primRect(a: Vec2, c: Vec2, col: Int) {

        vtxBuffer.pos = _vtxWritePtr
        idxBuffer.pos = _idxWritePtr

        val b = Vec2(c.x, a.y)
        val d = Vec2(a.x, c.y)
        val uv = Vec2(_data.texUvWhitePixel)
        val idx = _vtxCurrentIdx
        idxBuffer += idx; idxBuffer += idx + 1; idxBuffer += idx + 2
        idxBuffer += idx; idxBuffer += idx + 2; idxBuffer += idx + 3
        vtxBuffer += a; vtxBuffer += uv; vtxBuffer += col
        vtxBuffer += b; vtxBuffer += uv; vtxBuffer += col
        vtxBuffer += c; vtxBuffer += uv; vtxBuffer += col
        vtxBuffer += d; vtxBuffer += uv; vtxBuffer += col
        _vtxWritePtr += 4
        _vtxCurrentIdx += 4
        _idxWritePtr += 6

        vtxBuffer.pos = 0
        idxBuffer.pos = 0
    }

    fun primRectUV(a: Vec2, c: Vec2, uvA: Vec2, uvC: Vec2, col: Int) {

        vtxBuffer.pos = _vtxWritePtr
        idxBuffer.pos = _idxWritePtr

        val b = Vec2(c.x, a.y)
        val d = Vec2(a.x, c.y)
        val uvB = Vec2(uvC.x, uvA.y)
        val uvD = Vec2(uvA.x, uvC.y)
        val idx = _vtxCurrentIdx
        idxBuffer += idx; idxBuffer += idx + 1; idxBuffer += idx + 2
        idxBuffer += idx; idxBuffer += idx + 2; idxBuffer += idx + 3
        vtxBuffer += a; vtxBuffer += uvA; vtxBuffer += col
        vtxBuffer += b; vtxBuffer += uvB; vtxBuffer += col
        vtxBuffer += c; vtxBuffer += uvC; vtxBuffer += col
        vtxBuffer += d; vtxBuffer += uvD; vtxBuffer += col
        _vtxWritePtr += 4
        _vtxCurrentIdx += 4
        _idxWritePtr += 6

        vtxBuffer.pos = 0
        idxBuffer.pos = 0
    }

// On AddPolyline() and AddConvexPolyFilled() we intentionally avoid using ImVec2 and superflous function calls to optimize debug/non-inlined builds.
// Those macros expects l-values.
//    fun NORMALIZE2F_OVER_ZERO(vX: Float, vY: Float) {
//        val d2 = vX * vX + vY * vY
//        if (d2 > 0.0f) {
//            val invLen = 1f / sqrt(d2)
//            vX *= invLen
//            vY *= invLen
//        }
//    }
//
//    fun NORMALIZE2F_OVER_EPSILON_CLAMP(vX: Float, vY: Float, eps: Float, invLenMax: Float) {
//        val d2 = vX * vX + vY * vY
//        if (d2 > eps) {
//            var invLen = 1f / sqrt(d2)
//            if (invLen > invLenMax)
//                invLen = invLenMax
//            vX *= invLen
//            vY *= invLen
//        }
//    }

    fun primQuadUV(a: Vec2, b: Vec2, c: Vec2, d: Vec2, uvA: Vec2, uvB: Vec2, uvC: Vec2, uvD: Vec2, col: Int) {

        vtxBuffer.pos = _vtxWritePtr
        idxBuffer.pos = _idxWritePtr

        val idx = _vtxCurrentIdx
        idxBuffer += idx; idxBuffer += idx + 1; idxBuffer += idx + 2
        idxBuffer += idx; idxBuffer += idx + 2; idxBuffer += idx + 3
        vtxBuffer += a; vtxBuffer += uvA; vtxBuffer += col
        vtxBuffer += b; vtxBuffer += uvB; vtxBuffer += col
        vtxBuffer += c; vtxBuffer += uvC; vtxBuffer += col
        vtxBuffer += d; vtxBuffer += uvD; vtxBuffer += col
        _vtxWritePtr += 4
        _vtxCurrentIdx += 4
        _idxWritePtr += 6

        vtxBuffer.pos = 0
        idxBuffer.pos = 0
    }

    fun primWriteVtx(pos: Vec2, uv: Vec2, col: Int) {

        vtxBuffer.pos = _vtxWritePtr

        vtxBuffer += pos; vtxBuffer += uv; vtxBuffer += col
        _vtxWritePtr++
        _vtxCurrentIdx++

        vtxBuffer.pos = 0
    }

    fun primWriteIdx(idx: DrawIdx) = idxBuffer.set(_idxWritePtr++, idx)

    fun primVtx(pos: Vec2, uv: Vec2, col: Int) {
        primWriteIdx(_vtxCurrentIdx)
        primWriteVtx(pos, uv, col)
    }

    /** Our scheme may appears a bit unusual, basically we want the most-common calls AddLine AddRect etc. to not have
    to perform any check so we always have a command ready in the stack.
    The cost of figuring out if a new command has to be added or if we can merge is paid in those Update**
    functions only. */
    fun updateClipRect() {
        // If current command is used with different settings we need to add a new command
        val currClipRect = Vec4(currentClipRect)
        val currCmd = cmdBuffer.lastOrNull()
        if (currCmd == null || (currCmd.elemCount != 0 && currCmd.clipRect != currClipRect) || currCmd.userCallback != null) {
            addDrawCmd()
            return
        }
        // Try to merge with previous command if it matches, else use current command
        val prevCmd = if (cmdBuffer.size > 1) cmdBuffer[cmdBuffer.lastIndex - 1] else null
        if (currCmd.elemCount == 0 && prevCmd != null && prevCmd.clipRect == currClipRect &&
                prevCmd.textureId == currentTextureId!! && prevCmd.userCallback == null)
            cmdBuffer.pop()
        else
            currCmd.clipRect put currClipRect
    }

    fun updateTextureID() {

        // If current command is used with different settings we need to add a new command
        val currCmd = if (cmdBuffer.isNotEmpty()) cmdBuffer.last() else null
        if (currCmd == null || (currCmd.elemCount != 0 && currCmd.textureId != currentTextureId!!) || currCmd.userCallback != null) {
            addDrawCmd()
            return
        }

        // Try to merge with previous command if it matches, else use current command
        val prevCmd = if (cmdBuffer.size > 1) cmdBuffer[cmdBuffer.lastIndex - 1] else null
        if (currCmd.elemCount == 0 && prevCmd != null && prevCmd.textureId == currentTextureId!! && prevCmd.clipRect == currentClipRect &&
                prevCmd.userCallback == null)
            cmdBuffer.pop()
        else
            currCmd.textureId = currentTextureId!!
    }


    // Macros
    val currentClipRect: Vec4
        get() = _clipRectStack.lastOrNull() ?: _data.clipRectFullscreen
    val currentTextureId: TextureID?
        get() = _textureIdStack.lastOrNull()

    /** AddDrawListToDrawData */
    infix fun addTo(outList: ArrayList<DrawList>) {

        if (cmdBuffer.empty()) return

        // Remove trailing command if unused
        val lastCmd = cmdBuffer.last()
        if (lastCmd.elemCount == 0 && lastCmd.userCallback == null) {
            cmdBuffer.pop()
            if (cmdBuffer.empty()) return
        }

        /*  Draw list sanity check. Detect mismatch between PrimReserve() calls and incrementing _VtxCurrentIdx, _VtxWritePtr etc.
            May trigger for you if you are using PrimXXX functions incorrectly.   */
        assert(vtxBuffer.rem == 0 || _vtxWritePtr == vtxBuffer.rem)
        assert(idxBuffer.rem == 0 || _idxWritePtr == idxBuffer.rem)
        if (flags hasnt DrawListFlag.AllowVtxOffset)
            assert(_vtxCurrentIdx == vtxBuffer.rem)

        // JVM ImGui, this doesnt apply, we use Ints by default
        /*  Check that drawList doesn't use more vertices than indexable
            (default DrawIdx = unsigned short = 2 bytes = 64K vertices per DrawList = per window)
            If this assert triggers because you are drawing lots of stuff manually:
            - First, make sure you are coarse clipping yourself and not trying to draw many things outside visible bounds.
              Be mindful that the ImDrawList API doesn't filter vertices. Use the Metrics window to inspect draw list contents.
            - If you want large meshes with more than 64K vertices, you can either:
              (A) Handle the ImDrawCmd::VtxOffset value in your renderer back-end, and set 'io.BackendFlags |= ImGuiBackendFlags_RendererHasVtxOffset'.
                  Most example back-ends already support this from 1.71. Pre-1.71 back-ends won't.
                  Some graphics API such as GL ES 1/2 don't have a way to offset the starting vertex so it is not supported for them.
              (B) Or handle 32-bits indices in your renderer back-end, and uncomment '#define ImDrawIdx unsigned int' line in imconfig.h.
                  Most example back-ends already support this. For example, the OpenGL example code detect index size at compile-time:
                    glDrawElements(GL_TRIANGLES, (GLsizei)pcmd->ElemCount, sizeof(ImDrawIdx) == 2 ? GL_UNSIGNED_SHORT : GL_UNSIGNED_INT, idx_buffer_offset);
                  Your own engine or render API may use different parameters or function calls to specify index sizes.
                  2 and 4 bytes indices are generally supported by most graphics API.
            - If for some reason neither of those solutions works for you, a workaround is to call BeginChild()/EndChild() before reaching
              the 64K limit to split your draw commands in multiple draw lists.         */
        outList += this
        io.metricsRenderVertices += vtxBuffer.rem
        io.metricsRenderIndices += idxBuffer.rem
    }
}


/** -----------------------------------------------------------------------------
 *  All draw command lists required to render the frame + pos/size coordinates to use for the projection matrix.
 *
 *  Draw List API (ImDrawCmd, ImDrawIdx, ImDrawVert, ImDrawChannel, ImDrawListFlags, ImDrawList, ImDrawData)
 *  Hold a series of drawing commands. The user provides a renderer for ImDrawData which essentially contains an array of ImDrawList.
 *
 *  All draw data to render a Dear ImGui frame
 *  (NB: the style and the naming convention here is a little inconsistent, we currently preserve them for backward compatibility purpose,
 *  as this is one of the oldest structure exposed by the library! Basically, ImDrawList == CmdList)
 *  ----------------------------------------------------------------------------- */
class DrawData {

    /** Only valid after Render() is called and before the next NewFrame() is called.   */
    var valid = false
    /** Array of ImDrawList* to render. The ImDrawList are owned by ImGuiContext and only pointed to from here. */
    val cmdLists = ArrayList<DrawList>()
    /** For convenience, sum of all DrawList's IdxBuffer.Size   */
    var totalIdxCount = 0
    /** For convenience, sum of all DrawList's VtxBuffer.Size   */
    var totalVtxCount = 0
    /** Upper-left position of the viewport to render (== upper-left of the orthogonal projection matrix to use) */
    var displayPos = Vec2()
    /** Size of the viewport to render (== io.displaySize for the main viewport) (displayPos + displaySize == lower-right of the orthogonal projection matrix to use) */
    var displaySize = Vec2()
    /** Amount of pixels for each unit of DisplaySize. Based on io.DisplayFramebufferScale. Generally (1,1) on normal display, (2,2) on OSX with Retina display. */
    var framebufferScale = Vec2()

    // Functions

    /** The ImDrawList are owned by ImGuiContext! */
    fun clear() {
        valid = false
        cmdLists.clear()
        totalIdxCount = 0
        totalVtxCount = 0
        displayPos put 0f
        displaySize put 0f
        framebufferScale put 0f
    }

    /** Helper to convert all buffers from indexed to non-indexed, in case you cannot render indexed.
     *  Note: this is slow and most likely a waste of resources. Always prefer indexed rendering!   */
    fun deIndexAllBuffers() {
        TODO()
//        val newVtxBuffer = mutableListOf<DrawVert>()
//        totalVtxCount = 0
//        totalIdxCount = 0
//        cmdLists.filter { it.idxBuffer.isNotEmpty() }.forEach { cmdList ->
//            for (j in cmdList.idxBuffer.indices)
//                newVtxBuffer[j] = cmdList.vtxBuffer[cmdList.idxBuffer[j]]
//            cmdList.vtxBuffer.clear()
//            cmdList.vtxBuffer.addAll(newVtxBuffer)
//            cmdList.idxBuffer.clear()
//            totalVtxCount += cmdList.vtxBuffer.size
//        }
    }

    /** Helper to scale the ClipRect field of each ImDrawCmd.
     *  Use if your final output buffer is at a different scale than draw Dear ImGui expects,
     *  or if there is a difference between your window resolution and framebuffer resolution.  */
    infix fun scaleClipRects(fbScale: Vec2) {
        cmdLists.forEach {
            it.cmdBuffer.forEach { cmd ->
                cmd.clipRect.timesAssign(fbScale.x, fbScale.y, fbScale.x, fbScale.y)
            }
        }
    }

    fun clone(): DrawData {
        val ret = DrawData()

        ret.cmdLists.addAll(cmdLists)
        ret.displayPos = displayPos
        ret.displaySize = displaySize
        ret.totalIdxCount = totalIdxCount
        ret.totalVtxCount = totalVtxCount
        ret.valid = valid

        return ret
    }
}