package com.example.floatdraw

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import com.example.floatdraw.sync.ConnectionState
import com.example.floatdraw.sync.RemoteStroke
import com.example.floatdraw.sync.SyncManager

class OverlayService : Service(), SyncManager.SyncEventListener {

    private lateinit var windowManager: WindowManager
    private lateinit var syncManager: SyncManager

    private var drawingView: DrawingView? = null
    private var bubble: TextView? = null
    private var menu: LinearLayout? = null
    private var colorPickerMenu: LinearLayout? = null
    private var dismissTargetView: TextView? = null

    private var drawingMode: Boolean = true
    private var brushColorIndex: Int = 0
    private var customColor: Int? = null

    private val colors = intArrayOf(
        Color.rgb(239, 68, 68),   // Red
        Color.rgb(249, 115, 22),  // Orange
        Color.rgb(234, 179, 8),   // Yellow
        Color.rgb(34, 197, 94),   // Green
        Color.rgb(59, 130, 246),  // Blue
        Color.rgb(168, 85, 247),  // Purple
        Color.rgb(255, 255, 255)  // White
    )

    private var brushSize: Float = 8f
    private var brushOpacity: Float = 1.0f

    private val cardBg = Color.rgb(24, 26, 33)
    private val cardBorder = Color.rgb(46, 52, 64)
    private val primaryAccent = Color.rgb(249, 115, 22)
    private val primaryAccentText = Color.rgb(255, 237, 213)
    private val textPrimary = Color.rgb(243, 244, 246)
    private val textSecondary = Color.rgb(156, 163, 175)
    private val textMuted = Color.rgb(107, 114, 128)
    private val statusConnected = Color.rgb(34, 197, 94)
    private val statusConnecting = Color.rgb(234, 179, 8)
    private val statusDisconnected = Color.rgb(107, 114, 128)
    private val dangerRed = Color.rgb(239, 68, 68)

    companion object {
        const val ACTION_RESTORE_ICON = "com.example.floatdraw.ACTION_RESTORE_ICON"
    }

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        syncManager = SyncManager.getInstance(this)
        syncManager.registerListener(this)

        createNotificationChannel()
        startForeground(77, buildNotification(dismissed = false))
        createDrawingLayer()
        createBubble()

        if (syncManager.currentSessionId != null && syncManager.webSocketManager.connectionState == ConnectionState.DISCONNECTED) {
            syncManager.reconnectExistingSession()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_RESTORE_ICON || bubble == null) {
            restoreOverlay()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        syncManager.unregisterListener(this)
        removeView(colorPickerMenu)
        removeView(menu)
        removeView(bubble)
        removeView(dismissTargetView)
        removeView(drawingView)
        super.onDestroy()
    }

    private fun createDrawingLayer() {
        val view = DrawingView(this).apply {
            drawingEnabled = drawingMode
            syncManager = this@OverlayService.syncManager
        }
        val flags = if (drawingMode) {
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
        } else {
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            flags,
            PixelFormat.TRANSLUCENT
        )
        drawingView = view
        windowManager.addView(view, params)
    }

    private fun getActiveColor(): Int = customColor ?: colors[brushColorIndex]

    private fun createBubble() {
        val button = TextView(this).apply {
            text = "✎"
            textSize = 24f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(cardBg)
                setStroke(dp(2), primaryAccent)
            }
            elevation = dp(10).toFloat()
        }
        val size = dp(56)
        val params = WindowManager.LayoutParams(
            size, size,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = dp(16); y = dp(180)
        }

        var downX = 0f; var downY = 0f; var startX = 0; var startY = 0; var dragged = false
        button.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX; downY = event.rawY
                    startX = params.x; startY = params.y; dragged = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - downX).toInt(); val dy = (event.rawY - downY).toInt()
                    if (kotlin.math.abs(dx) > dp(5) || kotlin.math.abs(dy) > dp(5)) dragged = true
                    if (dragged) {
                        params.x = (startX + dx).coerceAtLeast(0)
                        params.y = (startY + dy).coerceAtLeast(0)
                        try { windowManager.updateViewLayout(button, params) } catch (_: Exception) {}

                        val dm = resources.displayMetrics
                        val screenHeight = dm.heightPixels
                        val screenWidth = dm.widthPixels
                        val bubbleCenterX = params.x + size / 2
                        val bubbleCenterY = params.y + size / 2
                        val targetX = screenWidth / 2
                        val targetY = screenHeight - dp(70)
                        val distance = kotlin.math.hypot((bubbleCenterX - targetX).toDouble(), (bubbleCenterY - targetY).toDouble())
                        val inTarget = distance < dp(80)
                        showDismissTarget(show = true, highlight = inTarget)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    showDismissTarget(show = false, highlight = false)
                    if (dragged) {
                        val dm = resources.displayMetrics
                        val screenHeight = dm.heightPixels
                        val screenWidth = dm.widthPixels
                        val bubbleCenterX = params.x + size / 2
                        val bubbleCenterY = params.y + size / 2
                        val targetX = screenWidth / 2
                        val targetY = screenHeight - dp(70)
                        val distance = kotlin.math.hypot((bubbleCenterX - targetX).toDouble(), (bubbleCenterY - targetY).toDouble())
                        if (distance < dp(80)) {
                            try { windowManager.removeView(button); bubble = null } catch (_: Exception) {}
                            drawingMode = false
                            if (drawingView == null) {
                                createDrawingLayer()
                            } else {
                                drawingView?.visibility = View.VISIBLE
                            }
                            updateMode()
                            removeView(menu); menu = null
                            removeView(colorPickerMenu); colorPickerMenu = null
                            val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
                            notificationManager.notify(77, buildNotification(dismissed = true))
                        }
                    } else {
                        toggleMenu()
                    }
                    true
                }
                else -> true
            }
        }
        bubble = button
        windowManager.addView(button, params)
    }

    private fun showDismissTarget(show: Boolean, highlight: Boolean) {
        if (dismissTargetView == null && show) {
            val tv = TextView(this).apply {
                text = "✕"
                textSize = 22f
                gravity = Gravity.CENTER
                setTextColor(Color.WHITE)
                elevation = dp(20).toFloat()
            }
            val size = dp(56)
            val params = WindowManager.LayoutParams(
                size, size,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                y = dp(40)
            }
            dismissTargetView = tv
            try { windowManager.addView(tv, params) } catch (_: Exception) {}
        }
        dismissTargetView?.let { tv ->
            tv.visibility = if (show) View.VISIBLE else View.GONE
            if (highlight) {
                tv.background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(Color.rgb(220, 38, 38))
                    setStroke(dp(3), Color.WHITE)
                }
            } else {
                tv.background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(Color.argb(180, 239, 68, 68))
                }
            }
        }
    }

    private fun vanishOverlayAndIcon() {
        removeView(bubble); bubble = null
        removeView(drawingView); drawingView = null
        removeView(menu); menu = null
        removeView(colorPickerMenu); colorPickerMenu = null
        removeView(dismissTargetView); dismissTargetView = null
    }

    private fun restoreOverlay() {
        drawingMode = true
        updateMode()
        if (drawingView == null) {
            createDrawingLayer()
        } else {
            try { drawingView?.visibility = View.VISIBLE } catch (_: Exception) {}
        }
        if (bubble == null) {
            createBubble()
        } else {
            try { bubble?.visibility = View.VISIBLE } catch (_: Exception) {}
        }
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(77, buildNotification(dismissed = false))
        Toast.makeText(this, "Floating icon restored", Toast.LENGTH_SHORT).show()
    }

    private fun toggleMenu() {
        if (menu != null) {
            removeView(colorPickerMenu); colorPickerMenu = null
            removeView(menu); menu = null
            return
        }

        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
            background = GradientDrawable().apply {
                setColor(cardBg)
                cornerRadius = dp(16).toFloat()
                setStroke(dp(1), cardBorder)
            }
            elevation = dp(14).toFloat()
        }

        // --- Header Row ---
        val headerRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, dp(10))
        }

        headerRow.addView(TextView(this).apply {
            text = "FloatDraw Tools"
            textSize = 17f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(textPrimary)
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        val hasSession = syncManager.currentSessionId != null
        val isConnected2Of2 = syncManager.peerConnected && syncManager.deviceCount >= 2
        val isReconnecting = syncManager.webSocketManager.connectionState == ConnectionState.RECONNECTING || syncManager.webSocketManager.connectionState == ConnectionState.CONNECTING

        drawingView?.drawingEnabled = hasSession && isConnected2Of2

        val (stateLabel, dotColor) = when {
            !hasSession -> Pair("No Session", statusDisconnected)
            isReconnecting -> Pair("Reconnecting", statusConnecting)
            !isConnected2Of2 -> Pair("Waiting (1/2)", statusConnecting)
            else -> Pair("Connected (2/2)", statusConnected)
        }

        val badgeBox = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(4), dp(8), dp(4))
            background = GradientDrawable().apply {
                setColor(Color.rgb(38, 41, 51))
                cornerRadius = dp(12).toFloat()
            }
        }

        val dotView = View(this).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(dotColor)
            }
        }
        badgeBox.addView(dotView, LinearLayout.LayoutParams(dp(8), dp(8)).apply { rightMargin = dp(6) })

        badgeBox.addView(TextView(this).apply {
            text = stateLabel
            textSize = 11f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(textSecondary)
        })
        headerRow.addView(badgeBox)
        panel.addView(headerRow)

        if (!hasSession) {
            panel.addView(TextView(this).apply {
                text = "No active session.\nJoin or create a session in the FloatDraw app."
                setTextColor(textSecondary)
                setPadding(0, dp(10), 0, dp(10))
            })
        } else if (isReconnecting) {
            panel.addView(TextView(this).apply {
                text = "Reconnecting to session..."
                setTextColor(dangerRed)
                setPadding(0, dp(10), 0, dp(10))
            })
        } else if (!isConnected2Of2) {
            panel.addView(TextView(this).apply {
                text = "Waiting for partner to join (1/2).\nCollaborative drawing is locked until both devices are connected."
                setTextColor(textSecondary)
                setPadding(0, dp(10), 0, dp(10))
            })
        } else {
            // State C — Both devices connected (2/2), show full controls
            val codeStr = syncManager.currentPairingCode
            if (!codeStr.isNullOrEmpty()) {
                val localName = syncManager.displayName
                panel.addView(TextView(this).apply {
                    text = "$localName: Online • Partner: Connected"
                    textSize = 11f
                    typeface = Typeface.DEFAULT_BOLD
                    setTextColor(textMuted)
                    setPadding(0, 0, 0, dp(10))
                })
            }

            // Mode Selector Pills (Draw vs Touch)
            val modeRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, 0, 0, dp(10))
            }

            val drawPill = createPillButton("✎ Draw Mode", isSelected = drawingMode) {
                drawingMode = true
                updateMode()
                refreshMenu()
            }
            val touchPill = createPillButton("👆 Touch Mode", isSelected = !drawingMode) {
                drawingMode = false
                updateMode()
                refreshMenu()
            }
            modeRow.addView(drawPill, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { rightMargin = dp(4) })
            modeRow.addView(touchPill, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { leftMargin = dp(4) })
            panel.addView(modeRow)

            // Tool Selection Grid
            panel.addView(TextView(this).apply {
                text = "TOOL SELECTION"
                textSize = 11f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(textMuted)
                setPadding(0, 0, 0, dp(6))
            })

            val currentTool = drawingView?.currentTool ?: DrawingTool.PEN

            val toolRow1 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            val penBtn = createToolButton("✒ Pen", isSelected = (currentTool == DrawingTool.PEN)) {
                drawingView?.currentTool = DrawingTool.PEN
                refreshMenu()
            }
            val pencilBtn = createToolButton("✏ Pencil", isSelected = (currentTool == DrawingTool.PENCIL)) {
                drawingView?.currentTool = DrawingTool.PENCIL
                refreshMenu()
            }
            toolRow1.addView(penBtn, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { rightMargin = dp(4) })
            toolRow1.addView(pencilBtn, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { leftMargin = dp(4) })
            panel.addView(toolRow1)

            val toolRow2 = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, dp(4), 0, dp(12))
            }
            val hlBtn = createToolButton("🖍 Highlighter", isSelected = (currentTool == DrawingTool.HIGHLIGHTER)) {
                drawingView?.currentTool = DrawingTool.HIGHLIGHTER
                refreshMenu()
            }
            val eraserBtn = createToolButton("🧹 Eraser", isSelected = (currentTool == DrawingTool.ERASER)) {
                drawingView?.currentTool = DrawingTool.ERASER
                refreshMenu()
            }
            toolRow2.addView(hlBtn, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { rightMargin = dp(4) })
            toolRow2.addView(eraserBtn, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { leftMargin = dp(4) })
            panel.addView(toolRow2)

            // Color Palette
            panel.addView(TextView(this).apply {
                text = "COLOR PALETTE"
                textSize = 11f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(textMuted)
                setPadding(0, 0, 0, dp(6))
            })

            val swatchesRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, 0, 0, dp(8))
            }

            val colorArray = colors
            for (i in colorArray.indices) {
                val isSelected = (customColor == null && i == brushColorIndex)
                val swatchColor = colorArray[i]
                val swatch = View(this).apply {
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(swatchColor)
                        if (isSelected) {
                            setStroke(dp(3), Color.WHITE)
                        } else {
                            setStroke(dp(1), cardBorder)
                        }
                    }
                    setOnClickListener {
                        customColor = null
                        brushColorIndex = i
                        drawingView?.brushColor = swatchColor
                        refreshMenu()
                    }
                }
                val sizeDp = if (isSelected) dp(24) else dp(20)
                swatchesRow.addView(swatch, LinearLayout.LayoutParams(sizeDp, sizeDp).apply {
                    leftMargin = dp(2)
                    rightMargin = dp(2)
                })
            }

            val customSwatchBtn = Button(this).apply {
                text = "🎨 Custom..."
                isAllCaps = false
                textSize = 11f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(if (customColor != null) primaryAccentText else textSecondary)
                background = GradientDrawable().apply {
                    setColor(customColor ?: Color.rgb(38, 41, 51))
                    cornerRadius = dp(12).toFloat()
                    setStroke(dp(1), if (customColor != null) Color.WHITE else cardBorder)
                }
                minHeight = dp(32)
                setOnClickListener { showCustomColorPickerDialog() }
            }
            swatchesRow.addView(customSwatchBtn, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(32)).apply {
                leftMargin = dp(6)
            })

            panel.addView(swatchesRow)

            // Opacity Slider
            val opacityLabel = TextView(this).apply {
                text = "Opacity: ${(brushOpacity * 100).toInt()}%"
                textSize = 11f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(textSecondary)
                setPadding(0, dp(4), 0, dp(2))
            }
            panel.addView(opacityLabel)

            panel.addView(SeekBar(this).apply {
                max = 90
                progress = ((brushOpacity - 0.1f) * 100).toInt().coerceIn(0, 90)
                setPadding(dp(4), dp(2), dp(4), dp(8))
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                        brushOpacity = (progress + 10) / 100f
                        drawingView?.currentOpacity = brushOpacity
                        opacityLabel.text = "Opacity: ${(brushOpacity * 100).toInt()}%"
                    }
                    override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                    override fun onStopTrackingTouch(seekBar: SeekBar?) {}
                })
            })

            // Thickness Slider
            val sizeLabel = TextView(this).apply {
                text = "Thickness: ${brushSize.toInt()}px"
                textSize = 11f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(textSecondary)
                setPadding(0, 0, 0, dp(2))
            }
            panel.addView(sizeLabel)

            panel.addView(SeekBar(this).apply {
                max = 39
                progress = (brushSize - 1).toInt().coerceIn(0, 39)
                setPadding(dp(4), dp(2), dp(4), dp(10))
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                        brushSize = (progress + 1).toFloat()
                        drawingView?.brushWidth = brushSize
                        sizeLabel.text = "Thickness: ${brushSize.toInt()}px"
                    }
                    override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                    override fun onStopTrackingTouch(seekBar: SeekBar?) {}
                })
            })

            // Clear Button
            val clearBtn = createActionButton("🗑 Clear Canvas", cardBorder, textPrimary) {
                drawingView?.clearCanvas()
                Toast.makeText(this, "Canvas cleared", Toast.LENGTH_SHORT).show()
            }
            panel.addView(clearBtn, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                setMargins(0, 0, 0, dp(6))
            })
        }

        if (syncManager.currentSessionId != null) {
            panel.addView(createActionButton("Leave Session", Color.TRANSPARENT, dangerRed) {
                syncManager.leaveSession()
                vanishOverlayAndIcon()
                Toast.makeText(this, "Session disconnected", Toast.LENGTH_SHORT).show()
            })
        }

        panel.addView(createActionButton("Close Menu", Color.TRANSPARENT, textMuted) {
            removeView(colorPickerMenu); colorPickerMenu = null
            removeView(menu); menu = null
        })

        val params = WindowManager.LayoutParams(
            dp(260), WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START; x = dp(16); y = dp(120) }
        menu = panel
        windowManager.addView(panel, params)
    }

    private fun showCustomColorPickerDialog() {
        // ... (existing custom color picker implementation)
    }

    private fun createToolButton(title: String, isSelected: Boolean, onClick: () -> Unit): Button {
        return Button(this).apply {
            text = title
            isAllCaps = false
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(if (isSelected) primaryAccentText else textSecondary)
            background = GradientDrawable().apply {
                setColor(if (isSelected) primaryAccent else Color.rgb(38, 41, 51))
                cornerRadius = dp(10).toFloat()
                setStroke(dp(1), if (isSelected) Color.WHITE else cardBorder)
            }
            minHeight = dp(36)
            setOnClickListener { onClick() }
        }
    }

    private fun createPillButton(title: String, isSelected: Boolean, onClick: () -> Unit): Button {
        return Button(this).apply {
            text = title
            isAllCaps = false
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(if (isSelected) Color.WHITE else textSecondary)
            background = GradientDrawable().apply {
                setColor(if (isSelected) Color.rgb(59, 130, 246) else Color.rgb(38, 41, 51))
                cornerRadius = dp(12).toFloat()
            }
            minHeight = dp(36)
            setOnClickListener { onClick() }
        }
    }

    private fun createActionButton(title: String, bgColor: Int, textColor: Int, onClick: () -> Unit): Button {
        return Button(this).apply {
            text = title
            isAllCaps = false
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(textColor)
            background = GradientDrawable().apply {
                setColor(bgColor)
                cornerRadius = dp(12).toFloat()
                setStroke(dp(1), cardBorder)
            }
            minHeight = dp(40)
            setOnClickListener { onClick() }
        }
    }

    private fun refreshMenu() {
        if (menu != null) {
            val m = menu
            removeView(m)
            menu = null
            toggleMenu()
        }
    }

    private fun updateMode() {
        val view = drawingView ?: return
        view.drawingEnabled = drawingMode

        val params = view.layoutParams as? WindowManager.LayoutParams ?: return
        val flags = if (drawingMode) {
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
        } else {
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        }
        if (params.flags != flags) {
            params.flags = flags
            try { windowManager.updateViewLayout(view, params) } catch (_: Exception) {}
        }
    }

    private fun removeView(view: View?) {
        if (view != null) {
            try { windowManager.removeView(view) } catch (_: Exception) {}
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel("floatdraw_overlay_v2", "FloatDraw Overlay", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Keeps the user-enabled FloatDraw overlay running"
                setShowBadge(true)
            }
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(dismissed: Boolean = false): Notification {
        val pendingFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }

        val restoreIntent = Intent(this, OverlayService::class.java).apply {
            action = ACTION_RESTORE_ICON
        }
        val restorePendingIntent = PendingIntent.getService(this, 101, restoreIntent, pendingFlags)

        val contentPendingIntent = if (dismissed) {
            restorePendingIntent
        } else {
            val intent = Intent(this, MainActivity::class.java)
            PendingIntent.getActivity(this, 100, intent, pendingFlags)
        }

        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            Notification.Builder(this, "floatdraw_overlay_v2") else Notification.Builder(this)

        val title = if (dismissed) "FloatDraw — Icon Hidden" else "FloatDraw is running"
        val text = if (dismissed) "Overlay active. Press 'Show Icon' to restore floating icon." else "Tap the floating pencil to draw or switch to touch mode"

        builder.setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_edit)
            .setContentIntent(contentPendingIntent)
            .setOngoing(true)

        if (dismissed) {
            val action = Notification.Action.Builder(
                android.R.drawable.ic_menu_revert,
                "Show Icon",
                restorePendingIntent
            ).build()
            builder.addAction(action)
        }

        return builder.build()
    }

    // SyncEventListener Callbacks

    override fun onRemoteStrokeStart(
        strokeId: String,
        deviceId: String,
        color: Int,
        width: Float,
        alpha: Float,
        tool: String,
        normX: Float,
        normY: Float
    ) {
        drawingView?.onRemoteStrokeStart(strokeId, deviceId, color, width, alpha, tool, normX, normY)
    }

    override fun onRemoteStrokeStart(strokeId: String, deviceId: String, color: Int, width: Float, normX: Float, normY: Float) {
        drawingView?.onRemoteStrokeStart(strokeId, deviceId, color, width, normX, normY)
    }

    override fun onRemoteStrokeUpdate(strokeId: String, normX: Float, normY: Float) {
        drawingView?.onRemoteStrokeUpdate(strokeId, normX, normY)
    }

    override fun onRemoteStrokeEnd(strokeId: String, normX: Float, normY: Float, expiresAt: Long) {
        drawingView?.onRemoteStrokeEnd(strokeId, normX, normY, expiresAt)
    }

    override fun onRemoteStrokeEnd(strokeId: String, normX: Float, normY: Float) {
        drawingView?.onRemoteStrokeEnd(strokeId, normX, normY, 0L)
    }

    override fun onRemoteClear() {
        drawingView?.onRemoteClear()
    }

    override fun onSnapshotReceived(strokes: List<RemoteStroke>) {
        drawingView?.applySnapshot(strokes)
    }

    override fun onConnectionStateChanged(state: ConnectionState) {
        refreshMenu()
    }

    override fun onPeerStatusChanged(peerConnected: Boolean, deviceCount: Int) {
        refreshMenu()
    }

    override fun onSessionEnded(reason: String) {
        Toast.makeText(this, reason, Toast.LENGTH_LONG).show()
        vanishOverlayAndIcon()
    }
}
