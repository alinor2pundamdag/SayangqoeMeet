package com.sayangqoemeet.app
import android.app.Service
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import android.widget.TextView
import android.view.WindowManager.LayoutParams
/** Explicitly enabled overlay bubble; opens the app and never inspects other apps. */
class FloatingChatBubbleService : Service() {
    private var manager: WindowManager? = null
    private var bubble: TextView? = null
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) { stopSelf(); return }
        manager = getSystemService(WINDOW_SERVICE) as WindowManager
        val view = TextView(this).apply {
            text = "S"; textSize = 21f; setTextColor(Color.rgb(7,11,24)); typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Color.rgb(85,244,255)); setStroke((2 * resources.displayMetrics.density).toInt(), Color.WHITE) }
            contentDescription = "Open SayangqoeMeet chats"
        }
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) LayoutParams.TYPE_APPLICATION_OVERLAY else {
            @Suppress("DEPRECATION") LayoutParams.TYPE_PHONE
        }
        val params = LayoutParams((56 * resources.displayMetrics.density).toInt(), (56 * resources.displayMetrics.density).toInt(), type,
            LayoutParams.FLAG_NOT_FOCUSABLE or LayoutParams.FLAG_LAYOUT_IN_SCREEN, PixelFormat.TRANSLUCENT).apply {
            gravity = Gravity.TOP or Gravity.START; x = (resources.displayMetrics.widthPixels * .78f).toInt(); y = (resources.displayMetrics.heightPixels * .45f).toInt()
        }
        var startX=0f; var startY=0f; var originX=0; var originY=0
        view.setOnTouchListener { _, event -> when(event.action) {
            MotionEvent.ACTION_DOWN -> { startX=event.rawX; startY=event.rawY; originX=params.x; originY=params.y; true }
            MotionEvent.ACTION_MOVE -> {
                val maxX = (resources.displayMetrics.widthPixels - view.width).coerceAtLeast(0)
                val maxY = (resources.displayMetrics.heightPixels - view.height).coerceAtLeast(0)
                params.x = (originX + (event.rawX - startX).toInt()).coerceIn(0, maxX)
                params.y = (originY + (event.rawY - startY).toInt()).coerceIn(0, maxY)
                val dismissing = params.y >= (maxY - (72 * resources.displayMetrics.density).toInt()).coerceAtLeast(0)
                view.text = if (dismissing) "×" else "S"
                view.background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(if (dismissing) Color.rgb(255, 83, 106) else Color.rgb(85, 244, 255))
                    setStroke((2 * resources.displayMetrics.density).toInt(), Color.WHITE)
                }
                runCatching { manager?.updateViewLayout(view, params) }
                true
            }
            MotionEvent.ACTION_UP -> {
                val maxY = (resources.displayMetrics.heightPixels - view.height).coerceAtLeast(0)
                val dismissing = params.y >= (maxY - (72 * resources.displayMetrics.density).toInt()).coerceAtLeast(0)
                if (dismissing && (kotlin.math.abs(event.rawX - startX) >= 8 || kotlin.math.abs(event.rawY - startY) >= 8)) {
                    stopSelf()
                } else if (kotlin.math.abs(event.rawX-startX)<8 && kotlin.math.abs(event.rawY-startY)<8) {
                    packageManager.getLaunchIntentForPackage(packageName)?.let { it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP); it.putExtra("open_chats",true); startActivity(it) }
                }
                true
            }
            else -> false
        } }
        bubble=view; manager?.addView(view,params)
    }
    override fun onDestroy() { bubble?.let { runCatching { manager?.removeView(it) } }; bubble=null; manager=null; super.onDestroy() }
}
