package com.example.floatdraw

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.InputType
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.floatdraw.sync.ConnectionState
import com.example.floatdraw.sync.SyncManager
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions

class MainActivity : AppCompatActivity(), SyncManager.SyncEventListener {

    private val overlayRequest = 4102
    private lateinit var syncManager: SyncManager
    private val mainHandler = Handler(Looper.getMainLooper())

    private var isProcessing = false
    private var isQrExpanded = false

    // Refined Design Theme Palette
    private val bgColor = Color.rgb(8, 9, 11)              // #08090B Near Black
    private val cardBg = Color.rgb(19, 20, 22)             // #131416 Dark Charcoal
    private val cardBorder = Color.rgb(38, 40, 46)         // #26282E Border
    private val inputBg = Color.rgb(28, 30, 36)            // #1C1E24 Input
    private val coralPrimary = Color.rgb(255, 104, 71)      // #FF6847 Coral Orange Accent
    private val coralPrimaryDark = Color.rgb(204, 75, 46)
    private val cyanAccent = Color.rgb(53, 213, 223)       // #35D5DF Cyan Accent
    private val textPrimary = Color.rgb(245, 245, 245)     // #F5F5F5 Primary Text
    private val textSecondary = Color.rgb(165, 168, 176)   // #A5A8B0 Secondary Text
    private val textMuted = Color.rgb(107, 114, 128)       // #6B7280 Dim Gray

    private val statusConnected = Color.rgb(16, 185, 129)  // #10B981 Emerald Green
    private val statusConnecting = Color.rgb(245, 158, 11) // #F59E0B Amber
    private val statusDisconnected = Color.rgb(100, 116, 139) // #64748B Slate
    private val dangerRed = Color.rgb(239, 68, 68)          // #EF4444

    private lateinit var statusBadgeContainer: LinearLayout
    private lateinit var statusDot: View
    private lateinit var statusTv: TextView

    private lateinit var activeSessionSection: LinearLayout
    private lateinit var sessionCodeTv: TextView
    private lateinit var qrImageView: ImageView
    private lateinit var toggleQrBtn: Button
    private lateinit var copyCodeBtn: Button
    private lateinit var leaveSessionBtn: Button

    private lateinit var participantSection: LinearLayout
    private lateinit var localNameInput: EditText
    private lateinit var saveNameBtn: Button
    private lateinit var partnerDot: View
    private lateinit var partnerNameTv: TextView
    private lateinit var partnerStatusTv: TextView

    private lateinit var createRoomCard: LinearLayout
    private lateinit var joinRoomCard: LinearLayout
    private lateinit var manualJoinBox: LinearLayout
    private lateinit var joinCodeInput: EditText
    private lateinit var scanQrBtn: Button
    private lateinit var submitJoinBtn: Button

    private val qrScanLauncher = registerForActivityResult(ScanContract()) { result ->
        if (result.contents != null) {
            val raw = result.contents.trim()
            val extractedCode = parsePairingCodeFromQr(raw)
            if (!extractedCode.isNullOrEmpty()) {
                joinCodeInput.setText(extractedCode)
                Toast.makeText(this, "Scanned code: $extractedCode", Toast.LENGTH_SHORT).show()
                onJoinSessionClicked()
            } else {
                Toast.makeText(this, "Invalid QR code format", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        syncManager = SyncManager.getInstance(this)

        val prefs = getSharedPreferences("floatdraw_ui", Context.MODE_PRIVATE)
        val onboardingDone = prefs.getBoolean("onboarding_completed", false)

        if (!onboardingDone) {
            showSplash()
        } else {
            showHome()
        }
    }

    override fun onResume() {
        super.onResume()
        syncManager.registerListener(this)
        if (::statusTv.isInitialized) {
            updateStatusUi()
        }
    }

    override fun onPause() {
        super.onPause()
        syncManager.unregisterListener(this)
    }

    // --- SCREEN 1: SPLASH SCREEN (Screenshot 6) ---
    private fun showSplash() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bgColor)
            gravity = Gravity.CENTER
            setPadding(dp(24), dp(24), dp(24), dp(24))
        }

        val logoCard = ImageView(this).apply {
            val bmp = createSquircleLogoBitmap(dp(110))
            setImageBitmap(bmp)
        }
        root.addView(logoCard, LinearLayout.LayoutParams(dp(110), dp(110)).apply { bottomMargin = dp(24) })

        val titleTv = TextView(this).apply {
            text = "FloatDraw"
            textSize = 32f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(textPrimary)
            gravity = Gravity.CENTER
        }
        root.addView(titleTv, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(8) })

        val subtitleTv = TextView(this).apply {
            text = "Draw together, over anything."
            textSize = 15f
            setTextColor(textSecondary)
            gravity = Gravity.CENTER
        }
        root.addView(subtitleTv, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(60) })

        val statusBox = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        val dot = View(this).apply {
            background = statusDotDrawable(coralPrimary)
        }
        statusBox.addView(dot, LinearLayout.LayoutParams(dp(8), dp(8)).apply { rightMargin = dp(8) })

        val statusText = TextView(this).apply {
            text = "S T A R T I N G"
            textSize = 12f
            typeface = Typeface.MONOSPACE
            setTextColor(textMuted)
            letterSpacing = 0.2f
        }
        statusBox.addView(statusText)
        root.addView(statusBox)

        setContentView(root)

        mainHandler.postDelayed({
            showOnboarding(0)
        }, 1500)
    }

    // --- SCREEN 2: ONBOARDING FLOW (Screenshots 1, 4, 3) ---
    private fun showOnboarding(pageIndex: Int) {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bgColor)
            setPadding(dp(20), dp(16), dp(20), dp(24))
        }

        // Top Row with Skip
        val topRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, dp(16))
        }
        val skipBtn = TextView(this).apply {
            text = "Skip"
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(textSecondary)
            setPadding(dp(12), dp(8), dp(12), dp(8))
            setOnClickListener {
                showPermissionSetup()
            }
        }
        topRow.addView(skipBtn)
        root.addView(topRow)

        val scroll = ScrollView(this).apply {
            isFillViewport = true
            setBackgroundColor(bgColor)
        }
        val contentBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        // Onboarding Card Graphic Container
        val graphicCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(16), dp(24), dp(16), dp(24))
            background = cardBackground(cardBg, cardBorder, dp(24))
        }

        val graphicImg = ImageView(this).apply {
            val bmp = when (pageIndex) {
                0 -> createPairingIllustrationBitmap(dp(280), dp(180))
                1 -> createOverlayIllustrationBitmap(dp(280), dp(180))
                else -> createSyncIllustrationBitmap(dp(280), dp(180))
            }
            setImageBitmap(bmp)
        }
        graphicCard.addView(graphicImg, LinearLayout.LayoutParams(dp(280), dp(180)))
        contentBox.addView(graphicCard, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(24) })

        // Icon Badge
        val iconBadge = TextView(this).apply {
            text = when (pageIndex) {
                0 -> "🔗"
                1 -> "⧉"
                else -> "⚡"
            }
            textSize = 22f
            gravity = Gravity.CENTER
            setTextColor(coralPrimary)
            background = cardBackground(inputBg, cardBorder, dp(22))
        }
        contentBox.addView(iconBadge, LinearLayout.LayoutParams(dp(44), dp(44)).apply { bottomMargin = dp(16) })

        // Title & Description
        val titleText = when (pageIndex) {
            0 -> "Pair with one code"
            1 -> "Draw over any app"
            else -> "Synced in real time"
        }
        val descText = when (pageIndex) {
            0 -> "Create a room and share the code, or enter your partner's. Two devices, one shared canvas."
            1 -> "A transparent canvas floats above whatever is on your screen — maps, photos, chats, games."
            else -> "Every stroke appears on your partner's screen instantly over the internet."
        }

        contentBox.addView(TextView(this).apply {
            text = titleText
            textSize = 28f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(textPrimary)
            setPadding(0, 0, 0, dp(10))
        })

        contentBox.addView(TextView(this).apply {
            text = descText
            textSize = 15f
            setTextColor(textSecondary)
            setLineSpacing(dp(4).toFloat(), 1.1f)
            setPadding(0, 0, 0, dp(24))
        })

        scroll.addView(contentBox)
        root.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        // Bottom Controls Row (Indicators & Next/Start Button)
        val bottomRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(16), 0, 0)
        }

        val indicatorBox = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        for (i in 0..2) {
            val bar = View(this).apply {
                background = if (i == pageIndex) cardBackground(coralPrimary, Color.TRANSPARENT, dp(4)) else cardBackground(cardBorder, Color.TRANSPARENT, dp(4))
            }
            val wDp = if (i == pageIndex) dp(24) else dp(8)
            indicatorBox.addView(bar, LinearLayout.LayoutParams(wDp, dp(8)).apply { rightMargin = dp(6) })
        }
        bottomRow.addView(indicatorBox, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        val nextBtn = createStyledButton(if (pageIndex == 2) "Get started" else "Next", coralPrimary, textPrimary).apply {
            setPadding(dp(28), dp(12), dp(28), dp(12))
            setOnClickListener {
                if (pageIndex < 2) {
                    showOnboarding(pageIndex + 1)
                } else {
                    showPermissionSetup()
                }
            }
        }
        bottomRow.addView(nextBtn, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        root.addView(bottomRow)
        setContentView(root)
    }

    // --- SCREEN 3: PERMISSION SETUP (Screenshot 5) ---
    private fun showPermissionSetup() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bgColor)
            setPadding(dp(20), dp(20), dp(20), dp(24))
        }

        val scroll = ScrollView(this).apply {
            isFillViewport = true
            setBackgroundColor(bgColor)
        }
        val contentBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        // Subheader
        contentBox.addView(TextView(this).apply {
            text = "SETUP  ·  1 OF 1"
            textSize = 12f
            typeface = Typeface.MONOSPACE
            setTextColor(coralPrimary)
            letterSpacing = 0.15f
            setPadding(0, 0, 0, dp(8))
        })

        // Title & Subtitle
        contentBox.addView(TextView(this).apply {
            text = "A few permissions\nto float"
            textSize = 30f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(textPrimary)
            setLineSpacing(dp(2).toFloat(), 1.1f)
            setPadding(0, 0, 0, dp(10))
        })

        contentBox.addView(TextView(this).apply {
            text = "FloatDraw needs these to draw on top of other apps. You can change them later in Android Settings."
            textSize = 14f
            setTextColor(textSecondary)
            setLineSpacing(dp(4).toFloat(), 1.1f)
            setPadding(0, 0, 0, dp(24))
        })

        val overlayGranted = Settings.canDrawOverlays(this)

        // Permission Card 1: Display Over Other Apps
        val card1 = createCardLayout().apply {
            val row = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }

            val icon1 = TextView(this@MainActivity).apply {
                text = "⧉"
                textSize = 20f
                gravity = Gravity.CENTER
                setTextColor(textPrimary)
                background = cardBackground(inputBg, cardBorder, dp(20))
            }
            row.addView(icon1, LinearLayout.LayoutParams(dp(40), dp(40)).apply { rightMargin = dp(12) })

            val col1 = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.VERTICAL }
            col1.addView(TextView(this@MainActivity).apply {
                text = "Display over other apps"
                textSize = 16f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(textPrimary)
            })
            col1.addView(TextView(this@MainActivity).apply {
                text = "Lets the drawing canvas and toolbar float above any app."
                textSize = 12f
                setTextColor(textSecondary)
            })
            row.addView(col1, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { rightMargin = dp(8) })

            val allowBtn1 = createStyledButton(if (overlayGranted) "Allowed ✓" else "Allow", if (overlayGranted) statusConnected else textPrimary, if (overlayGranted) textPrimary else Color.BLACK).apply {
                textSize = 13f
                minHeight = dp(36)
                setPadding(dp(16), 0, dp(16), 0)
                setOnClickListener {
                    enableOverlay()
                }
            }
            row.addView(allowBtn1)
            addView(row)
        }
        contentBox.addView(card1, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(12) })

        // Permission Card 2: Notifications
        val card2 = createCardLayout().apply {
            val row = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }

            val icon2 = TextView(this@MainActivity).apply {
                text = "🔔"
                textSize = 18f
                gravity = Gravity.CENTER
                background = cardBackground(inputBg, cardBorder, dp(20))
            }
            row.addView(icon2, LinearLayout.LayoutParams(dp(40), dp(40)).apply { rightMargin = dp(12) })

            val col2 = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.VERTICAL }
            col2.addView(TextView(this@MainActivity).apply {
                text = "Notifications"
                textSize = 16f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(textPrimary)
            })
            col2.addView(TextView(this@MainActivity).apply {
                text = "Shows the ongoing session notification while you draw."
                textSize = 12f
                setTextColor(textSecondary)
            })
            row.addView(col2, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { rightMargin = dp(8) })

            val allowBtn2 = createStyledButton("Allow", textPrimary, Color.BLACK).apply {
                textSize = 13f
                minHeight = dp(36)
                setPadding(dp(16), 0, dp(16), 0)
                setOnClickListener {
                    Toast.makeText(this@MainActivity, "Notifications enabled", Toast.LENGTH_SHORT).show()
                }
            }
            row.addView(allowBtn2)
            addView(row)
        }
        contentBox.addView(card2, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(12) })

        // Permission Card 3: Battery Optimization
        val card3 = createCardLayout().apply {
            val row = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }

            val icon3 = TextView(this@MainActivity).apply {
                text = "⚡"
                textSize = 18f
                gravity = Gravity.CENTER
                background = cardBackground(inputBg, cardBorder, dp(20))
            }
            row.addView(icon3, LinearLayout.LayoutParams(dp(40), dp(40)).apply { rightMargin = dp(12) })

            val col3 = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.VERTICAL }
            val titleRow = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            titleRow.addView(TextView(this@MainActivity).apply {
                text = "Unrestricted battery"
                textSize = 16f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(textPrimary)
            })
            titleRow.addView(TextView(this@MainActivity).apply {
                text = "Optional"
                textSize = 10f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(textMuted)
                background = cardBackground(inputBg, cardBorder, dp(4))
                setPadding(dp(6), dp(2), dp(6), dp(2))
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { leftMargin = dp(8) })
            col3.addView(titleRow)

            col3.addView(TextView(this@MainActivity).apply {
                text = "Keeps the connection alive when the screen is off."
                textSize = 12f
                setTextColor(textSecondary)
            })
            row.addView(col3, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { rightMargin = dp(8) })

            val allowBtn3 = createStyledButton("Allow", textPrimary, Color.BLACK).apply {
                textSize = 13f
                minHeight = dp(36)
                setPadding(dp(16), 0, dp(16), 0)
                setOnClickListener {
                    Toast.makeText(this@MainActivity, "Battery setting checked", Toast.LENGTH_SHORT).show()
                }
            }
            row.addView(allowBtn3)
            addView(row)
        }
        contentBox.addView(card3, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(24) })

        scroll.addView(contentBox)
        root.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        // Bottom Continue Button
        val continueBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
        }

        val continueBtn = createStyledButton("Continue", if (overlayGranted) coralPrimary else Color.rgb(74, 32, 26), textPrimary).apply {
            textSize = 16f
            setOnClickListener {
                this@MainActivity.getSharedPreferences("floatdraw_ui", Context.MODE_PRIVATE).edit().putBoolean("onboarding_completed", true).apply()
                showHome()
            }
        }
        continueBox.addView(continueBtn, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(8) })

        continueBox.addView(TextView(this).apply {
            text = "Allow the required permissions to continue"
            textSize = 12f
            setTextColor(textMuted)
            gravity = Gravity.CENTER
        })

        root.addView(continueBox)
        setContentView(root)
    }

    // --- SCREEN 4: REDESIGNED & REFINED HOME SCREEN (Screenshot 2) ---
    private fun showHome() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bgColor)
            setPadding(dp(20), dp(16), dp(20), dp(32)) // 32dp bottom padding to avoid controls cutoff
        }

        val scroll = ScrollView(this).apply {
            setBackgroundColor(bgColor)
            isFillViewport = true
            clipToPadding = false
            addView(root)
        }

        // --- Top Bar Section ---
        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, dp(16))
        }

        val brandBox = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val logoIcon = ImageView(this).apply {
            setImageBitmap(createSquircleLogoBitmap(dp(36)))
        }
        brandBox.addView(logoIcon, LinearLayout.LayoutParams(dp(36), dp(36)).apply { rightMargin = dp(10) })

        brandBox.addView(TextView(this).apply {
            text = "FloatDraw"
            textSize = 20f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(textPrimary)
        })
        topBar.addView(brandBox, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        // Settings Icon Gear Button
        val settingsBtn = TextView(this).apply {
            text = "⚙"
            textSize = 20f
            gravity = Gravity.CENTER
            setTextColor(textSecondary)
            background = cardBackground(inputBg, cardBorder, dp(18))
            setOnClickListener {
                showPermissionSetup()
            }
        }
        topBar.addView(settingsBtn, LinearLayout.LayoutParams(dp(36), dp(36)))
        root.addView(topBar)

        // --- Connection Status Pill ---
        statusBadgeContainer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(6), dp(14), dp(6))
            background = cardBackground(inputBg, cardBorder, dp(16))
        }

        statusDot = View(this).apply {
            background = statusDotDrawable(statusDisconnected)
        }
        statusBadgeContainer.addView(statusDot, LinearLayout.LayoutParams(dp(8), dp(8)).apply { rightMargin = dp(8) })

        statusTv = TextView(this).apply {
            text = "Not connected"
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(textSecondary)
        }
        statusBadgeContainer.addView(statusTv)

        val statusRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 0, 0, dp(16))
        }
        statusRow.addView(statusBadgeContainer, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        root.addView(statusRow)

        // --- Main Headline: "Start a shared canvas" ---
        val headlineTv = TextView(this).apply {
            val fullText = "Start a shared canvas"
            val spannable = SpannableString(fullText)
            val canvasStart = fullText.indexOf("canvas")
            if (canvasStart != -1) {
                spannable.setSpan(ForegroundColorSpan(coralPrimary), canvasStart, canvasStart + "canvas".length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                spannable.setSpan(StyleSpan(Typeface.BOLD), canvasStart, canvasStart + "canvas".length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            text = spannable
            textSize = 30f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(textPrimary)
            setPadding(0, 0, 0, dp(6))
        }
        root.addView(headlineTv)

        val subHeadlineTv = TextView(this).apply {
            text = "Pair with another Android device to draw over your screens together."
            textSize = 14f
            setTextColor(textSecondary)
            setLineSpacing(dp(3).toFloat(), 1.1f)
            setPadding(0, 0, 0, dp(20))
        }
        root.addView(subHeadlineTv)

        // --- Card 1: Coral "Create room" Card ---
        createRoomCard = createCardLayout().apply {
            background = cardBackground(coralPrimary, Color.TRANSPARENT, dp(24))
            setPadding(dp(20), dp(20), dp(20), dp(20))

            val topRow = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL or Gravity.END
            }

            val plusIcon = TextView(this@MainActivity).apply {
                text = "+"
                textSize = 22f
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                setTextColor(textPrimary)
                background = cardBackground(coralPrimaryDark, Color.TRANSPARENT, dp(20))
            }
            topRow.addView(plusIcon, LinearLayout.LayoutParams(dp(40), dp(40)))
            addView(topRow, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(28) })

            val bottomRow = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.BOTTOM
            }

            val titleCol = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.VERTICAL }
            titleCol.addView(TextView(this@MainActivity).apply {
                text = "Create room"
                textSize = 22f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(textPrimary)
            })
            titleCol.addView(TextView(this@MainActivity).apply {
                text = "Get a code to share"
                textSize = 13f
                setTextColor(Color.argb(220, 255, 255, 255))
            })
            bottomRow.addView(titleCol, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

            val arrowIcon = TextView(this@MainActivity).apply {
                text = "↗"
                textSize = 22f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(textPrimary)
            }
            bottomRow.addView(arrowIcon)
            addView(bottomRow)

            setOnClickListener {
                onCreateSessionClicked()
            }
        }
        root.addView(createRoomCard, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(14) })

        // --- Card 2: Dark Charcoal "Join room" Card ---
        joinRoomCard = createCardLayout().apply {
            setPadding(dp(18), dp(18), dp(18), dp(18))

            val row = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }

            val keyIcon = TextView(this@MainActivity).apply {
                text = "🔑"
                textSize = 18f
                gravity = Gravity.CENTER
                background = cardBackground(Color.rgb(11, 43, 48), Color.TRANSPARENT, dp(20))
            }
            row.addView(keyIcon, LinearLayout.LayoutParams(dp(40), dp(40)).apply { rightMargin = dp(14) })

            val col = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.VERTICAL }
            col.addView(TextView(this@MainActivity).apply {
                text = "Join room"
                textSize = 18f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(textPrimary)
            })
            col.addView(TextView(this@MainActivity).apply {
                text = "Enter your partner's code"
                textSize = 13f
                setTextColor(textSecondary)
            })
            row.addView(col, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { rightMargin = dp(8) })

            val arrowIcon = TextView(this@MainActivity).apply {
                text = "↗"
                textSize = 20f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(textSecondary)
            }
            row.addView(arrowIcon)
            addView(row)

            setOnClickListener {
                toggleManualJoinBox()
            }
        }
        root.addView(joinRoomCard, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(16) })

        // --- Manual Join & QR Scan Collapsible Box ---
        manualJoinBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
            background = cardBackground(cardBg, cardBorder, dp(16))
            visibility = View.GONE
        }

        scanQrBtn = createOutlineButton("📷 Scan QR Code to Join", cyanAccent).apply {
            setOnClickListener { onScanQrClicked() }
        }
        manualJoinBox.addView(scanQrBtn, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(12) })

        manualJoinBox.addView(TextView(this).apply {
            text = "OR ENTER PAIRING CODE MANUALLY:"
            textSize = 11f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(textMuted)
            setPadding(0, 0, 0, dp(6))
        })

        val joinRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        joinCodeInput = EditText(this).apply {
            hint = "e.g. FD8492"
            textSize = 15f
            typeface = Typeface.MONOSPACE
            inputType = InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
            setTextColor(textPrimary)
            setHintTextColor(textMuted)
            setPadding(dp(14), dp(10), dp(14), dp(10))
            background = cardBackground(inputBg, cardBorder, dp(10))
        }
        joinRow.addView(joinCodeInput, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { rightMargin = dp(8) })

        submitJoinBtn = createStyledButton("Join", coralPrimary, textPrimary).apply {
            setOnClickListener { onJoinSessionClicked() }
        }
        joinRow.addView(submitJoinBtn, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        manualJoinBox.addView(joinRow)

        root.addView(manualJoinBox, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(16) })

        // --- Active Session Container ---
        activeSessionSection = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(18))
            background = cardBackground(cardBg, coralPrimary, dp(20))
            visibility = View.GONE
        }

        activeSessionSection.addView(TextView(this).apply {
            text = "ACTIVE SESSION PAIRING CODE"
            textSize = 11f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(coralPrimary)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(4))
        })

        sessionCodeTv = TextView(this).apply {
            textSize = 30f
            typeface = Typeface.MONOSPACE
            setTextColor(textPrimary)
            gravity = Gravity.CENTER
            setPadding(0, dp(2), 0, dp(8))
        }
        activeSessionSection.addView(sessionCodeTv)

        // QR Code Expand/Collapse Toggle Button
        toggleQrBtn = createOutlineButton("Show QR Code 📷", coralPrimary).apply {
            textSize = 12f
            minHeight = dp(34)
            setOnClickListener {
                isQrExpanded = !isQrExpanded
                if (isQrExpanded) {
                    text = "Hide QR Code ▲"
                    qrImageView.visibility = View.VISIBLE
                } else {
                    text = "Show QR Code 📷"
                    qrImageView.visibility = View.GONE
                }
            }
        }
        activeSessionSection.addView(toggleQrBtn, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(10) })

        qrImageView = ImageView(this).apply {
            setPadding(dp(8), dp(8), dp(8), dp(8))
            background = cardBackground(Color.WHITE, Color.TRANSPARENT, dp(12))
            visibility = View.GONE
        }
        val qrParams = LinearLayout.LayoutParams(dp(140), dp(140)).apply {
            bottomMargin = dp(12)
            gravity = Gravity.CENTER_HORIZONTAL
        }
        activeSessionSection.addView(qrImageView, qrParams)

        val codeActionsRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        copyCodeBtn = createStyledButton("Copy Code", coralPrimary, textPrimary).apply {
            setOnClickListener { onCopyCodeClicked() }
        }
        codeActionsRow.addView(copyCodeBtn, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { rightMargin = dp(6) })

        leaveSessionBtn = createOutlineButton("Leave Session", dangerRed).apply {
            setOnClickListener {
                syncManager.leaveSession()
                Toast.makeText(this@MainActivity, "Disconnected from session", Toast.LENGTH_SHORT).show()
                updateStatusUi()
            }
        }
        codeActionsRow.addView(leaveSessionBtn, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { leftMargin = dp(6) })
        activeSessionSection.addView(codeActionsRow)

        // Session Participants Subcard
        participantSection = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(14), dp(14), dp(14))
            background = cardBackground(inputBg, cardBorder, dp(12))
        }

        participantSection.addView(TextView(this).apply {
            text = "SESSION PARTICIPANTS"
            textSize = 11f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(textMuted)
            setPadding(0, 0, 0, dp(8))
        })

        val localRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0, 0, 0, dp(8)) }
        val localDot = View(this).apply { background = statusDotDrawable(statusConnected) }
        localRow.addView(localDot, LinearLayout.LayoutParams(dp(8), dp(8)).apply { rightMargin = dp(8) })

        localNameInput = EditText(this).apply {
            setText(syncManager.displayName)
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(textPrimary)
            setHintTextColor(textMuted)
            hint = "Your Name"
            setPadding(dp(8), dp(6), dp(8), dp(6))
            background = cardBackground(cardBg, cardBorder, dp(6))
        }
        localRow.addView(localNameInput, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { rightMargin = dp(6) })

        saveNameBtn = createOutlineButton("Save", coralPrimary).apply {
            textSize = 11f
            minHeight = dp(32)
            setOnClickListener {
                val newName = localNameInput.text.toString().trim()
                syncManager.displayName = newName
                Toast.makeText(this@MainActivity, "Display name updated", Toast.LENGTH_SHORT).show()
            }
        }
        localRow.addView(saveNameBtn, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(32)))
        participantSection.addView(localRow)

        val partnerRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        partnerDot = View(this).apply { background = statusDotDrawable(statusDisconnected) }
        partnerRow.addView(partnerDot, LinearLayout.LayoutParams(dp(8), dp(8)).apply { rightMargin = dp(8) })

        partnerNameTv = TextView(this).apply {
            text = "Participant 2"
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(textPrimary)
        }
        partnerRow.addView(partnerNameTv, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        partnerStatusTv = TextView(this).apply {
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(textMuted)
        }
        partnerRow.addView(partnerStatusTv)
        participantSection.addView(partnerRow)

        activeSessionSection.addView(participantSection, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(12)
        })

        val launchOverlayBtn = createStyledButton("Launch Floating Canvas", coralPrimary, textPrimary).apply {
            setOnClickListener { enableOverlay() }
        }
        activeSessionSection.addView(launchOverlayBtn, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(12)
        })

        root.addView(activeSessionSection, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(20) })

        // --- Section: "HOW IT WORKS" (Screenshot 2) ---
        val howItWorksCard = createCardLayout().apply {
            setPadding(dp(18), dp(18), dp(18), dp(18))
        }

        howItWorksCard.addView(TextView(this).apply {
            text = "H O W   I T   W O R K S"
            textSize = 11f
            typeface = Typeface.MONOSPACE
            setTextColor(textMuted)
            letterSpacing = 0.1f
            setPadding(0, 0, 0, dp(14))
        })

        // Step 1
        val step1Row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0, 0, 0, dp(10)) }
        val step1Badge = TextView(this).apply {
            text = "1"
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setTextColor(textPrimary)
            background = cardBackground(inputBg, cardBorder, dp(12))
        }
        step1Row.addView(step1Badge, LinearLayout.LayoutParams(dp(24), dp(24)).apply { rightMargin = dp(12) })
        step1Row.addView(TextView(this).apply {
            text = "One device creates a room"
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(textPrimary)
        })
        howItWorksCard.addView(step1Row)

        // Step 2
        val step2Row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0, 0, 0, dp(10)) }
        val step2Badge = TextView(this).apply {
            text = "2"
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setTextColor(textPrimary)
            background = cardBackground(inputBg, cardBorder, dp(12))
        }
        step2Row.addView(step2Badge, LinearLayout.LayoutParams(dp(24), dp(24)).apply { rightMargin = dp(12) })
        step2Row.addView(TextView(this).apply {
            text = "The other joins with the code"
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(textPrimary)
        })
        howItWorksCard.addView(step2Row)

        // Step 3
        val step3Row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val step3Badge = TextView(this).apply {
            text = "3"
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setTextColor(textPrimary)
            background = cardBackground(inputBg, cardBorder, dp(12))
        }
        step3Row.addView(step3Badge, LinearLayout.LayoutParams(dp(24), dp(24)).apply { rightMargin = dp(12) })
        step3Row.addView(TextView(this).apply {
            text = "Open the overlay and draw"
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(textPrimary)
        })
        howItWorksCard.addView(step3Row)

        root.addView(howItWorksCard, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        setContentView(scroll)
        updateStatusUi()
    }

    private fun toggleManualJoinBox() {
        if (manualJoinBox.visibility == View.VISIBLE) {
            manualJoinBox.visibility = View.GONE
        } else {
            manualJoinBox.visibility = View.VISIBLE
        }
    }

    private fun onCreateSessionClicked() {
        if (isProcessing) return
        setProcessingState(true)

        syncManager.createSession { result ->
            result.onSuccess { res ->
                setProcessingState(false)
                Toast.makeText(this, "Room created! Code: ${res.pairingCode}", Toast.LENGTH_LONG).show()
                updateStatusUi()
            }.onFailure { err ->
                setProcessingState(false)
                Toast.makeText(this, "Failed to create room: ${err.message}", Toast.LENGTH_LONG).show()
                updateStatusUi()
            }
        }
    }

    private fun onJoinSessionClicked() {
        if (isProcessing) return
        val code = joinCodeInput.text.toString().trim()

        if (code.isEmpty()) {
            Toast.makeText(this, "Please enter a 6-character Pairing Code", Toast.LENGTH_SHORT).show()
            return
        }

        setProcessingState(true)

        syncManager.joinSession(code) { result ->
            result.onSuccess {
                setProcessingState(false)
                Toast.makeText(this, "Joined room successfully!", Toast.LENGTH_SHORT).show()
                updateStatusUi()
            }.onFailure { err ->
                setProcessingState(false)
                Toast.makeText(this, "Failed to join room: ${err.message}", Toast.LENGTH_LONG).show()
                updateStatusUi()
            }
        }
    }

    private fun onScanQrClicked() {
        val options = ScanOptions().apply {
            setDesiredBarcodeFormats(ScanOptions.QR_CODE)
            setPrompt("Scan a FloatDraw Session QR Code")
            setBeepEnabled(true)
            setOrientationLocked(false)
        }
        qrScanLauncher.launch(options)
    }

    private fun setProcessingState(processing: Boolean) {
        isProcessing = processing
        if (processing) {
            submitJoinBtn.text = "..."
            submitJoinBtn.isEnabled = false
        } else {
            submitJoinBtn.text = "Join"
            submitJoinBtn.isEnabled = true
        }
    }

    private fun onCopyCodeClicked() {
        val code = syncManager.currentPairingCode
        if (!code.isNullOrEmpty()) {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("Pairing Code", code)
            clipboard.setPrimaryClip(clip)

            copyCodeBtn.text = "Copied! ✓"
            mainHandler.postDelayed({ copyCodeBtn.text = "Copy Code" }, 2000)
            Toast.makeText(this, "Copied code: $code", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "No active pairing code to copy", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateStatusUi() {
        if (!::statusTv.isInitialized) return

        val state = syncManager.webSocketManager.connectionState
        val code = syncManager.currentPairingCode

        val (stateText, dotColor) = when {
            code.isNullOrEmpty() || state == ConnectionState.DISCONNECTED -> Pair("Not connected", statusDisconnected)
            state == ConnectionState.CONNECTING -> Pair("Connecting...", statusConnecting)
            state == ConnectionState.RECONNECTING -> Pair("Reconnecting...", statusConnecting)
            state == ConnectionState.CONNECTED -> {
                if (syncManager.peerConnected) {
                    Pair("Connected (2/2)", statusConnected)
                } else {
                    Pair("Waiting for partner", statusConnecting)
                }
            }
            else -> Pair("Not connected", statusDisconnected)
        }

        statusTv.text = stateText
        statusDot.background = statusDotDrawable(dotColor)

        if (!code.isNullOrEmpty()) {
            sessionCodeTv.text = code
            activeSessionSection.visibility = View.VISIBLE

            val pName = syncManager.partnerDisplayName
            partnerNameTv.text = if (pName != "Partner" && pName != "You") "$pName (Partner)" else "Participant 2 (Partner)"

            if (syncManager.peerConnected) {
                partnerDot.background = statusDotDrawable(statusConnected)
                partnerStatusTv.text = "Connected"
                partnerStatusTv.setTextColor(statusConnected)
            } else {
                partnerDot.background = statusDotDrawable(statusDisconnected)
                partnerStatusTv.text = "Waiting / Offline"
                partnerStatusTv.setTextColor(textMuted)
            }

            val qrBitmap = generateQrCodeBitmap("floatdraw://pair?code=$code", dp(140))
            if (qrBitmap != null) {
                qrImageView.setImageBitmap(qrBitmap)
            }
            qrImageView.visibility = if (isQrExpanded) View.VISIBLE else View.GONE
            toggleQrBtn.text = if (isQrExpanded) "Hide QR Code ▲" else "Show QR Code 📷"
        } else {
            activeSessionSection.visibility = View.GONE
        }
    }

    private fun generateQrCodeBitmap(content: String, sizePx: Int): Bitmap? {
        return try {
            val writer = QRCodeWriter()
            val bitMatrix = writer.encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx)
            val width = bitMatrix.width
            val height = bitMatrix.height
            val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
            for (x in 0 until width) {
                for (y in 0 until height) {
                    bmp.setPixel(x, y, if (bitMatrix.get(x, y)) Color.BLACK else Color.WHITE)
                }
            }
            bmp
        } catch (e: Exception) {
            null
        }
    }

    private fun parsePairingCodeFromQr(raw: String): String? {
        if (raw.isBlank()) return null
        val clean = raw.trim()

        if (clean.startsWith("floatdraw://", ignoreCase = true)) {
            try {
                val uri = Uri.parse(clean)
                val codeParam = uri.getQueryParameter("code")
                if (!codeParam.isNullOrBlank()) return codeParam.trim().uppercase()
            } catch (_: Exception) {}
        }

        if (clean.length == 6 && clean.all { it.isLetterOrDigit() }) {
            return clean.uppercase()
        }

        if (clean.contains("code", ignoreCase = true)) {
            val regex = """"code"\s*:\s*"([A-Za-z0-9]{6})"""".toRegex()
            val match = regex.find(clean)
            if (match != null) {
                return match.groupValues[1].uppercase()
            }
        }
        return null
    }

    private fun enableOverlay() {
        if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
            startActivityForResult(intent, overlayRequest)
            return
        }
        startOverlayService()
    }

    @Deprecated("Deprecated by Android, kept for broad device compatibility")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == overlayRequest) {
            if (Settings.canDrawOverlays(this)) startOverlayService()
            else Toast.makeText(this, "Overlay permission is required", Toast.LENGTH_LONG).show()
        }
    }

    private fun startOverlayService() {
        val intent = Intent(this, OverlayService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent) else startService(intent)
        Toast.makeText(this, "FloatDraw overlay active.", Toast.LENGTH_LONG).show()
    }

    private fun createCardLayout(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(18), dp(18), dp(18), dp(18))
        background = cardBackground(cardBg, cardBorder, dp(20))
    }

    private fun createStyledButton(label: String, bgCol: Int, textCol: Int): Button = Button(this).apply {
        text = label
        isAllCaps = false
        textSize = 14f
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(textCol)
        background = cardBackground(bgCol, Color.TRANSPARENT, dp(14))
        minHeight = dp(44)
    }

    private fun createOutlineButton(label: String, strokeCol: Int): Button = Button(this).apply {
        text = label
        isAllCaps = false
        textSize = 13f
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(strokeCol)
        background = cardBackground(Color.TRANSPARENT, strokeCol, dp(12))
        minHeight = dp(40)
    }

    private fun cardBackground(bgColor: Int, borderColor: Int, cornerRadiusDp: Int): GradientDrawable = GradientDrawable().apply {
        setColor(bgColor)
        cornerRadius = dp(cornerRadiusDp).toFloat()
        if (borderColor != Color.TRANSPARENT) {
            setStroke(dp(1), borderColor)
        }
    }

    private fun statusDotDrawable(color: Int): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(color)
    }

    // Dynamic Bitmap Generators for Illustrations & Branding
    private fun createSquircleLogoBitmap(sizePx: Int): Bitmap {
        val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)

        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = cardBg
            style = Paint.Style.FILL
        }
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = cardBorder
            style = Paint.Style.STROKE
            strokeWidth = dp(2).toFloat()
        }
        val rect = RectF(dp(2).toFloat(), dp(2).toFloat(), (sizePx - dp(2)).toFloat(), (sizePx - dp(2)).toFloat())
        val rx = sizePx * 0.28f
        canvas.drawRoundRect(rect, rx, rx, bgPaint)
        canvas.drawRoundRect(rect, rx, rx, borderPaint)

        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = coralPrimary
            style = Paint.Style.STROKE
            strokeWidth = sizePx * 0.12f
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }

        val path = Path()
        val p0 = sizePx * 0.28f
        val p1 = sizePx * 0.45f
        val p2 = sizePx * 0.55f
        val p3 = sizePx * 0.72f

        path.moveTo(p0, p2)
        path.lineTo(sizePx * 0.38f, p0)
        path.lineTo(sizePx * 0.62f, p3)
        path.lineTo(p3, p1)

        canvas.drawPath(path, strokePaint)

        val cyanDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = cyanAccent
            style = Paint.Style.FILL
        }
        canvas.drawCircle(p3, p1, sizePx * 0.08f, cyanDotPaint)

        return bmp
    }

    private fun createPairingIllustrationBitmap(wPx: Int, hPx: Int): Bitmap {
        val bmp = Bitmap.createBitmap(wPx, hPx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)

        val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(28, 30, 36)
            style = Paint.Style.FILL
        }
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = cardBorder
            style = Paint.Style.STROKE
            strokeWidth = dp(1.5f).toFloat()
        }

        // Phone 1 Left
        val phoneW = wPx * 0.35f
        val phoneH = hPx * 0.75f
        val p1Rect = RectF(wPx * 0.08f, hPx * 0.15f, wPx * 0.08f + phoneW, hPx * 0.15f + phoneH)
        canvas.drawRoundRect(p1Rect, dp(12).toFloat(), dp(12).toFloat(), cardPaint)
        canvas.drawRoundRect(p1Rect, dp(12).toFloat(), dp(12).toFloat(), borderPaint)

        // Phone 2 Right
        val p2Rect = RectF(wPx * 0.57f, hPx * 0.15f, wPx * 0.57f + phoneW, hPx * 0.15f + phoneH)
        canvas.drawRoundRect(p2Rect, dp(12).toFloat(), dp(12).toFloat(), cardPaint)
        canvas.drawRoundRect(p2Rect, dp(12).toFloat(), dp(12).toFloat(), borderPaint)

        // Middle Code Pill
        val codePill = RectF(wPx * 0.36f, hPx * 0.38f, wPx * 0.64f, hPx * 0.58f)
        val pillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = inputBg }
        canvas.drawRoundRect(codePill, dp(10).toFloat(), dp(10).toFloat(), pillPaint)

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = textPrimary
            textSize = dp(11).toFloat()
            typeface = Typeface.MONOSPACE
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("K7Q - 4M2", codePill.centerX(), codePill.centerY() + dp(4), textPaint)

        // Badges "You" and "Maya"
        val badgePaint1 = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = coralPrimary }
        val badgeRect1 = RectF(p1Rect.centerX() - dp(18), p1Rect.bottom - dp(8), p1Rect.centerX() + dp(18), p1Rect.bottom + dp(10))
        canvas.drawRoundRect(badgeRect1, dp(8).toFloat(), dp(8).toFloat(), badgePaint1)

        val bTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = textPrimary
            textSize = dp(9).toFloat()
            typeface = Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("You", badgeRect1.centerX(), badgeRect1.centerY() + dp(3), bTextPaint)

        val badgePaint2 = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = cyanAccent }
        val badgeRect2 = RectF(p2Rect.centerX() - dp(18), p2Rect.bottom - dp(8), p2Rect.centerX() + dp(18), p2Rect.bottom + dp(10))
        canvas.drawRoundRect(badgeRect2, dp(8).toFloat(), dp(8).toFloat(), badgePaint2)

        val bTextPaint2 = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = dp(9).toFloat()
            typeface = Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("Maya", badgeRect2.centerX(), badgeRect2.centerY() + dp(3), bTextPaint2)

        return bmp
    }

    private fun createOverlayIllustrationBitmap(wPx: Int, hPx: Int): Bitmap {
        val bmp = Bitmap.createBitmap(wPx, hPx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)

        val outerRect = RectF(wPx * 0.08f, hPx * 0.1f, wPx * 0.92f, hPx * 0.9f)
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = inputBg }
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = cardBorder; style = Paint.Style.STROKE; strokeWidth = dp(1.5f).toFloat()
        }
        canvas.drawRoundRect(outerRect, dp(16).toFloat(), dp(16).toFloat(), bgPaint)
        canvas.drawRoundRect(outerRect, dp(16).toFloat(), dp(16).toFloat(), borderPaint)

        val innerRect = RectF(wPx * 0.18f, hPx * 0.25f, wPx * 0.82f, hPx * 0.75f)
        val innerBg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = cardBg }
        canvas.drawRoundRect(innerRect, dp(12).toFloat(), dp(12).toFloat(), innerBg)

        val wavePath = Path().apply {
            moveTo(innerRect.left + dp(16), innerRect.centerY() + dp(10))
            cubicTo(
                innerRect.left + dp(40), innerRect.top - dp(10),
                innerRect.right - dp(40), innerRect.bottom + dp(10),
                innerRect.right - dp(16), innerRect.top + dp(10)
            )
        }
        val wavePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = coralPrimary
            style = Paint.Style.STROKE
            strokeWidth = dp(4).toFloat()
            strokeCap = Paint.Cap.ROUND
        }
        canvas.drawPath(wavePath, wavePaint)

        return bmp
    }

    private fun createSyncIllustrationBitmap(wPx: Int, hPx: Int): Bitmap {
        val bmp = Bitmap.createBitmap(wPx, hPx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)

        val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = inputBg }
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = cardBorder; style = Paint.Style.STROKE; strokeWidth = dp(1.5f).toFloat()
        }

        val phoneW = wPx * 0.35f
        val phoneH = hPx * 0.75f
        val p1Rect = RectF(wPx * 0.08f, hPx * 0.15f, wPx * 0.08f + phoneW, hPx * 0.15f + phoneH)
        canvas.drawRoundRect(p1Rect, dp(12).toFloat(), dp(12).toFloat(), cardPaint)
        canvas.drawRoundRect(p1Rect, dp(12).toFloat(), dp(12).toFloat(), borderPaint)

        val p2Rect = RectF(wPx * 0.57f, hPx * 0.15f, wPx * 0.57f + phoneW, hPx * 0.15f + phoneH)
        canvas.drawRoundRect(p2Rect, dp(12).toFloat(), dp(12).toFloat(), cardPaint)
        canvas.drawRoundRect(p2Rect, dp(12).toFloat(), dp(12).toFloat(), borderPaint)

        // Arc in Phone 1
        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = coralPrimary
            style = Paint.Style.STROKE
            strokeWidth = dp(3.5f).toFloat()
            strokeCap = Paint.Cap.ROUND
        }
        canvas.drawArc(RectF(p1Rect.left + dp(8), p1Rect.top + dp(16), p1Rect.right - dp(8), p1Rect.bottom - dp(16)), 200f, 140f, false, strokePaint)
        canvas.drawArc(RectF(p2Rect.left + dp(8), p2Rect.top + dp(16), p2Rect.right - dp(8), p2Rect.bottom - dp(16)), 200f, 140f, false, strokePaint)

        // Lightning icon between
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = coralPrimary
            textSize = dp(20).toFloat()
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("⚡", wPx * 0.5f, hPx * 0.55f, textPaint)

        return bmp
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
    private fun dp(value: Float): Int = (value * resources.displayMetrics.density).toInt()

    override fun onConnectionStateChanged(state: ConnectionState) {
        runOnUiThread { updateStatusUi() }
    }

    override fun onPeerStatusChanged(peerConnected: Boolean, deviceCount: Int) {
        runOnUiThread { updateStatusUi() }
    }

    override fun onPartnerNameChanged(partnerName: String) {
        runOnUiThread {
            partnerNameTv.text = if (partnerName.isNotBlank() && partnerName != "Partner" && partnerName != "You") "$partnerName (Partner)" else "Participant 2 (Partner)"
            updateStatusUi()
        }
    }

    override fun onUserLeft(partnerName: String) {
        runOnUiThread {
            val displayLabel = if (partnerName.isNotBlank() && partnerName != "Partner" && partnerName != "You") partnerName else "Partner"
            Toast.makeText(this, "$displayLabel left the session.", Toast.LENGTH_LONG).show()
            partnerNameTv.text = "Participant 2 (Partner)"
            updateStatusUi()
        }
    }
}
