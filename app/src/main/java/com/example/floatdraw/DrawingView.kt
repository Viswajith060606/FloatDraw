package com.example.floatdraw

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import com.example.floatdraw.sync.RemoteStroke
import com.example.floatdraw.sync.SyncManager
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

enum class DrawingTool(val id: String, val displayName: String) {
    PEN("pen", "Pen"),
    PENCIL("pencil", "Pencil"),
    HIGHLIGHTER("highlighter", "Highlighter"),
    ERASER("eraser", "Eraser");

    companion object {
        fun fromId(id: String?): DrawingTool {
            return values().find { it.id.equals(id, ignoreCase = true) } ?: PEN
        }
    }
}

/** Transparent full-screen drawing surface with cross-device synchronization and automatic stroke expiry. */
class DrawingView(context: Context) : View(context) {

    data class CanvasStroke(
        val strokeId: String,
        val deviceId: String,
        val path: Path,
        val color: Int,
        val width: Float,
        val alpha: Float = 1.0f,
        val tool: DrawingTool = DrawingTool.PEN,
        val normPoints: MutableList<PointF> = mutableListOf(),
        var expiresAt: Long = 0L
    )

    private val strokes = mutableListOf<CanvasStroke>()
    private val activeRemoteStrokes = ConcurrentHashMap<String, CanvasStroke>()

    private var activePath: Path? = null
    private var activeStroke: CanvasStroke? = null
    private var currentLocalStrokeId: String? = null

    private val renderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val handler = Handler(Looper.getMainLooper())
    private val expiryRunnable = object : Runnable {
        override fun run() {
            val now = System.currentTimeMillis()
            var modified = false
            val iterator = strokes.iterator()
            while (iterator.hasNext()) {
                val stroke = iterator.next()
                if (stroke.expiresAt > 0L && now >= stroke.expiresAt) {
                    iterator.remove()
                    modified = true
                }
            }
            val remoteIterator = activeRemoteStrokes.values.iterator()
            while (remoteIterator.hasNext()) {
                val stroke = remoteIterator.next()
                if (stroke.expiresAt > 0L && now >= stroke.expiresAt) {
                    remoteIterator.remove()
                    modified = true
                }
            }
            if (modified) {
                postInvalidate()
            }
            if (strokes.isNotEmpty() || activeRemoteStrokes.isNotEmpty()) {
                handler.postDelayed(this, 500)
            }
        }
    }

    fun ensureTickerRunning() {
        handler.removeCallbacks(expiryRunnable)
        if (strokes.isNotEmpty() || activeRemoteStrokes.isNotEmpty()) {
            handler.postDelayed(expiryRunnable, 500)
        }
    }

    fun stopExpiryTicker() {
        handler.removeCallbacks(expiryRunnable)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        ensureTickerRunning()
    }

    override fun onDetachedFromWindow() {
        stopExpiryTicker()
        super.onDetachedFromWindow()
    }

    var drawingEnabled: Boolean = true
    var brushColor: Int = Color.rgb(239, 68, 68) // Red
    var brushWidth: Float = 8f
    var currentTool: DrawingTool = DrawingTool.PEN
    var currentOpacity: Float = 1.0f

    var syncManager: SyncManager? = null

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val count = canvas.saveLayer(0f, 0f, width.toFloat(), height.toFloat(), null)

        for (stroke in strokes) {
            drawCanvasStroke(canvas, stroke)
        }

        activeStroke?.let { stroke ->
            drawCanvasStroke(canvas, stroke)
        }

        for (stroke in activeRemoteStrokes.values) {
            drawCanvasStroke(canvas, stroke)
        }

        canvas.restoreToCount(count)
    }

    private fun drawCanvasStroke(canvas: Canvas, stroke: CanvasStroke) {
        renderPaint.reset()
        renderPaint.isAntiAlias = true
        renderPaint.style = Paint.Style.STROKE
        renderPaint.strokeCap = Paint.Cap.ROUND
        renderPaint.strokeJoin = Paint.Join.ROUND

        when (stroke.tool) {
            DrawingTool.ERASER -> {
                renderPaint.xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
                renderPaint.strokeWidth = stroke.width
                canvas.drawPath(stroke.path, renderPaint)
            }
            DrawingTool.HIGHLIGHTER -> {
                renderPaint.xfermode = null
                val effectiveAlpha = (stroke.alpha * 0.4f * 255).toInt().coerceIn(0, 255)
                renderPaint.color = Color.argb(effectiveAlpha, Color.red(stroke.color), Color.green(stroke.color), Color.blue(stroke.color))
                renderPaint.strokeWidth = stroke.width
                canvas.drawPath(stroke.path, renderPaint)
            }
            DrawingTool.PENCIL -> {
                renderPaint.xfermode = null
                val effectiveAlpha = (stroke.alpha * 0.7f * 255).toInt().coerceIn(0, 255)
                renderPaint.color = Color.argb(effectiveAlpha, Color.red(stroke.color), Color.green(stroke.color), Color.blue(stroke.color))
                renderPaint.strokeWidth = stroke.width
                canvas.drawPath(stroke.path, renderPaint)
            }
            DrawingTool.PEN -> {
                renderPaint.xfermode = null
                val effectiveAlpha = (stroke.alpha * 255).toInt().coerceIn(0, 255)
                renderPaint.color = Color.argb(effectiveAlpha, Color.red(stroke.color), Color.green(stroke.color), Color.blue(stroke.color))
                renderPaint.strokeWidth = stroke.width
                canvas.drawPath(stroke.path, renderPaint)
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!drawingEnabled) return false

        val viewW = width.coerceAtLeast(1).toFloat()
        val viewH = height.coerceAtLeast(1).toFloat()
        val normX = (event.x / viewW).coerceIn(0f, 1f)
        val normY = (event.y / viewH).coerceIn(0f, 1f)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val sId = UUID.randomUUID().toString()
                currentLocalStrokeId = sId
                val localDevId = syncManager?.deviceId ?: "local"

                val strokeWidth = getEffectiveWidth(currentTool, brushWidth)
                val strokeAlpha = currentOpacity

                val path = Path().apply { moveTo(event.x, event.y) }
                val stroke = CanvasStroke(
                    strokeId = sId,
                    deviceId = localDevId,
                    path = path,
                    color = brushColor,
                    width = strokeWidth,
                    alpha = strokeAlpha,
                    tool = currentTool,
                    normPoints = mutableListOf(PointF(normX, normY)),
                    expiresAt = 0L
                )
                activePath = path
                activeStroke = stroke

                syncManager?.sendStrokeStart(
                    sId, normX, normY, brushColor, strokeWidth, strokeAlpha, currentTool.id
                )
                invalidate()
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                val sId = currentLocalStrokeId
                activePath?.lineTo(event.x, event.y)
                activeStroke?.normPoints?.add(PointF(normX, normY))

                if (sId != null) {
                    syncManager?.sendStrokeUpdate(sId, normX, normY)
                }
                invalidate()
                return true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val sId = currentLocalStrokeId
                activePath?.let { path ->
                    eventHistory(event, path)
                    activeStroke?.let { stroke ->
                        stroke.expiresAt = System.currentTimeMillis() + 10000L
                        strokes.add(stroke)
                        ensureTickerRunning()
                    }
                }

                if (sId != null) {
                    syncManager?.sendStrokeEnd(sId, normX, normY)
                }

                activePath = null
                activeStroke = null
                currentLocalStrokeId = null
                invalidate()
                return true
            }
        }
        return true
    }

    private fun eventHistory(event: MotionEvent, path: Path) {
        val viewW = width.coerceAtLeast(1).toFloat()
        val viewH = height.coerceAtLeast(1).toFloat()

        for (i in 0 until event.historySize) {
            val hX = event.getHistoricalX(i)
            val hY = event.getHistoricalY(i)
            path.lineTo(hX, hY)

            val sId = currentLocalStrokeId
            val normHX = (hX / viewW).coerceIn(0f, 1f)
            val normHY = (hY / viewH).coerceIn(0f, 1f)
            activeStroke?.normPoints?.add(PointF(normHX, normHY))
            if (sId != null) {
                syncManager?.sendStrokeUpdate(sId, normHX, normHY)
            }
        }
        path.lineTo(event.x, event.y)
    }

    private fun getEffectiveWidth(tool: DrawingTool, baseWidth: Float): Float {
        return when (tool) {
            DrawingTool.PEN -> baseWidth
            DrawingTool.PENCIL -> (baseWidth * 0.6f).coerceAtLeast(2f)
            DrawingTool.HIGHLIGHTER -> baseWidth * 3.2f
            DrawingTool.ERASER -> baseWidth * 2.5f
        }
    }

    // Remote Sync Callbacks

    fun onRemoteStrokeStart(
        strokeId: String,
        deviceId: String,
        color: Int,
        width: Float,
        alpha: Float,
        toolStr: String,
        normX: Float,
        normY: Float
    ) {
        val viewW = this.width.coerceAtLeast(1).toFloat()
        val viewH = this.height.coerceAtLeast(1).toFloat()
        val screenX = normX * viewW
        val screenY = normY * viewH

        val tool = DrawingTool.fromId(toolStr)
        val path = Path().apply { moveTo(screenX, screenY) }
        val stroke = CanvasStroke(
            strokeId = strokeId,
            deviceId = deviceId,
            path = path,
            color = color,
            width = width,
            alpha = alpha,
            tool = tool,
            normPoints = mutableListOf(PointF(normX, normY))
        )
        activeRemoteStrokes[strokeId] = stroke
        postInvalidate()
    }

    fun onRemoteStrokeStart(strokeId: String, deviceId: String, color: Int, width: Float, normX: Float, normY: Float) {
        onRemoteStrokeStart(strokeId, deviceId, color, width, 1.0f, "pen", normX, normY)
    }

    fun onRemoteStrokeUpdate(strokeId: String, normX: Float, normY: Float) {
        val viewW = this.width.coerceAtLeast(1).toFloat()
        val viewH = this.height.coerceAtLeast(1).toFloat()
        val screenX = normX * viewW
        val screenY = normY * viewH

        val stroke = activeRemoteStrokes[strokeId]
        if (stroke != null) {
            stroke.path.lineTo(screenX, screenY)
            stroke.normPoints.add(PointF(normX, normY))
        }
        postInvalidate()
    }

    fun onRemoteStrokeEnd(strokeId: String, normX: Float, normY: Float, remainingMs: Long) {
        val viewW = this.width.coerceAtLeast(1).toFloat()
        val viewH = this.height.coerceAtLeast(1).toFloat()
        val screenX = normX * viewW
        val screenY = normY * viewH

        val stroke = activeRemoteStrokes.remove(strokeId)
        if (stroke != null) {
            stroke.path.lineTo(screenX, screenY)
            stroke.normPoints.add(PointF(normX, normY))
            val rem = if (remainingMs > 0L) remainingMs else 10000L
            stroke.expiresAt = System.currentTimeMillis() + rem
            if (rem > 0L) {
                strokes.add(stroke)
                ensureTickerRunning()
            }
        }
        postInvalidate()
    }

    fun onRemoteStrokeEnd(strokeId: String, normX: Float, normY: Float) {
        onRemoteStrokeEnd(strokeId, normX, normY, 10000L)
    }

    fun onRemoteClear() {
        strokes.clear()
        activeRemoteStrokes.clear()
        activePath = null
        activeStroke = null
        stopExpiryTicker()
        postInvalidate()
    }

    fun applySnapshot(remoteStrokes: List<RemoteStroke>) {
        strokes.clear()
        activeRemoteStrokes.clear()

        val viewW = this.width.coerceAtLeast(1).toFloat()
        val viewH = this.height.coerceAtLeast(1).toFloat()
        val now = System.currentTimeMillis()

        for (rs in remoteStrokes) {
            if (rs.points.isEmpty()) continue
            val remaining = rs.remainingMs ?: if (rs.expiresAt > 0L) (rs.expiresAt - now) else 10000L
            if (remaining <= 0L) continue

            val path = Path()
            val normPts = mutableListOf<PointF>()

            rs.points.forEachIndexed { i, pt ->
                val sX = pt.x * viewW
                val sY = pt.y * viewH
                if (i == 0) path.moveTo(sX, sY) else path.lineTo(sX, sY)
                normPts.add(PointF(pt.x, pt.y))
            }

            strokes.add(
                CanvasStroke(
                    strokeId = rs.strokeId,
                    deviceId = rs.deviceId,
                    path = path,
                    color = rs.color,
                    width = rs.width,
                    alpha = rs.alpha,
                    tool = DrawingTool.fromId(rs.tool),
                    normPoints = normPts,
                    expiresAt = now + remaining
                )
            )
        }
        if (strokes.isNotEmpty()) {
            ensureTickerRunning()
        }
        postInvalidate()
    }

    fun clearCanvas() {
        strokes.clear()
        activeRemoteStrokes.clear()
        activePath = null
        activeStroke = null
        stopExpiryTicker()
        syncManager?.sendClear()
        invalidate()
    }
}
