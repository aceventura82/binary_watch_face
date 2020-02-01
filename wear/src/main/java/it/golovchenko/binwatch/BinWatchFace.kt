package it.golovchenko.binwatch

import android.Manifest
import android.content.*
import android.content.res.Resources
import android.graphics.*
import android.graphics.Color.argb
import android.graphics.Typeface.NORMAL
import android.os.*
import android.support.v4.content.ContextCompat
import android.support.v4.content.res.ResourcesCompat
import android.support.wearable.watchface.CanvasWatchFaceService
import android.support.wearable.watchface.WatchFaceService
import android.support.wearable.watchface.WatchFaceStyle
import android.util.Log
import android.view.SurfaceHolder
import android.view.WindowInsets
import android.view.WindowManager
import it.golovchenko.binwatch.DigitalWatchFaceWearableConfigActivity.Companion.BATTERY
import it.golovchenko.binwatch.DigitalWatchFaceWearableConfigActivity.Companion.BCD
import it.golovchenko.binwatch.DigitalWatchFaceWearableConfigActivity.Companion.DOTS
import it.golovchenko.binwatch.DigitalWatchFaceWearableConfigActivity.Companion.HORIZONTAL
import it.golovchenko.binwatch.DigitalWatchFaceWearableConfigActivity.Companion.PREF
import it.golovchenko.binwatch.DigitalWatchFaceWearableConfigActivity.Companion.SECONDS
import it.golovchenko.binwatch.DigitalWatchFaceWearableConfigActivity.Companion.THEMECOLOR
import java.io.File
import java.lang.ref.WeakReference
import java.util.*
import java.util.Calendar.*
import kotlin.math.abs


class BinWatchFace : CanvasWatchFaceService() {

    companion object {
        private val TAG = BinWatchFace::class.java.simpleName
        private const val INTERACTIVE_UPDATE_RATE_MS = 1000
        private const val MSG_UPDATE_TIME = 0

        private fun convertToBin(n: Int): String {
            var num = n
            var binaryNumber: Long = 0
            var remainder: Int
            var i = 1

            while (num != 0) {
                remainder = num % 2
                num /= 2
                binaryNumber += (remainder * i).toLong()
                i *= 10
            }

            return binaryNumber.toString()
        }
    }


    override fun onCreateEngine(): Engine {
        return Engine()
    }

    private class EngineHandler(reference: BinWatchFace.Engine) : Handler() {
        private val mWeakReference: WeakReference<BinWatchFace.Engine> = WeakReference(reference)

        override fun handleMessage(msg: Message) {
            mWeakReference.get()?.apply {
                if (msg.what == MSG_UPDATE_TIME) handleUpdateTimeMessage()
            }
        }
    }

    var humanTimeCount = 0
    private lateinit var fontFace: Typeface

    inner class Engine : CanvasWatchFaceService.Engine() {
        private lateinit var mCalendar: Calendar
        private var mRegisteredTimeZoneReceiver = false
        private var mXOffset: Float = 0F
        private var mYOffset: Float = 0F
        private lateinit var mBackgroundPaint: Paint
        private lateinit var mBackgroundBitmap: Bitmap
        private lateinit var mRow1: Paint
        private lateinit var mRow2: Paint
        private lateinit var mRow3: Paint
        private lateinit var mRow4: Paint
        private lateinit var mBatteryPaint: Paint
        private lateinit var fields: List<Paint>
        private var mLowBitAmbient: Boolean = false
        private var mBurnInProtection: Boolean = false
        private var mAmbient: Boolean = false
        private var mShowBatt: Boolean = true
        private var mSeconds: Boolean = true
        private var mBCD: Boolean = false
        private var dots: Boolean= false
        private var viewHorizontal: Boolean = true
        private var mTheme: String = ""
        private var width =0
        private var height =0
        private var fileBg: Boolean= false

        //        val fieldsConf = mapOf(true to arrayOf(4, 6, 6, 7), false to arrayOf(3, 5, 5, 5))
        private val mUpdateTimeHandler: Handler = EngineHandler(this)

        private val mTimeZoneReceiver: BroadcastReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                mCalendar.timeZone = TimeZone.getDefault()
                invalidate()
            }
        }

        private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { pref, key ->
            when(key){
                THEMECOLOR ->{ mTheme = pref?.getString(THEMECOLOR, "WHITE") ?: "WHITE"
                    fields.forEach {
                        it.color = when (mTheme) {
                            "RED" -> Color.RED
                            "DARK" -> Color.DKGRAY
                            "BLUE" -> Color.BLUE
                            "GRAY" -> Color.GRAY
                            "GREEN" -> Color.GREEN
                            "MAGENTA" -> Color.MAGENTA
                            "YELLOW" -> Color.YELLOW
                            "PINK" -> argb(100,255,0,255)
                            "CYAN" -> Color.CYAN
                            else -> Color.WHITE
                        }
                    }
                }
                BATTERY -> {
                    mShowBatt = pref?.getBoolean(BATTERY, true) ?: false
                }
                SECONDS -> {
                    mSeconds = pref?.getBoolean(SECONDS, true) ?: false
                }
                BCD -> {
                    mBCD = pref?.getBoolean(BCD, false) ?: false
                }
                DOTS -> {
                    dots = pref?.getBoolean(DOTS, false) ?: false
                }
                HORIZONTAL -> {
                    viewHorizontal = pref?.getBoolean(HORIZONTAL, true) ?: false
                }
            }
        }

        override fun onCreate(holder: SurfaceHolder) {
            super.onCreate(holder)
            with(getSharedPreferences(PREF, Context.MODE_PRIVATE)) {
                mShowBatt = getBoolean(BATTERY, true)
                mSeconds = getBoolean(SECONDS, true)
                mBCD = getBoolean(BCD, false)
                dots = getBoolean(DOTS, false)
                mTheme = getString(THEMECOLOR, "WHITE") ?: "WHITE"
                viewHorizontal = getBoolean(HORIZONTAL, true)
                registerOnSharedPreferenceChangeListener(prefListener)
            }
            val wm = baseContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val display = wm.defaultDisplay
            val size = Point()
            display.getSize(size)
            width = size.x
            height = size.y
            if(File(Environment.getExternalStorageDirectory().path +"/Download/bg.png").exists() &&
                    ContextCompat.checkSelfPermission(baseContext, Manifest.permission.READ_EXTERNAL_STORAGE)==0) {
                mBackgroundBitmap = Bitmap.createScaledBitmap(BitmapFactory.decodeFile(Environment.getExternalStorageDirectory().path + "/Download/bg.png"), width, height, false)
                fileBg=true
            }
            setWatchFaceStyle(WatchFaceStyle.Builder(this@BinWatchFace).setAcceptsTapEvents(true).build())
            mCalendar = Calendar.getInstance()
            val resources = this@BinWatchFace.resources
            mYOffset = resources.getDimension(R.dimen.digital_y_offset)
            // Initializes background.
            mBackgroundPaint = Paint().apply { color = ContextCompat.getColor(applicationContext, R.color.background) }
            fontFace = Typeface.create(ResourcesCompat.getFont(applicationContext, R.font.dig), NORMAL)
            mRow1 = initTextPaint()
            mRow2 = initTextPaint()
            mRow3 = initTextPaint()
            mRow4 = initTextPaint()
            mBatteryPaint = initTextPaint()
            fields = listOf(mRow1, mRow2, mRow3, mRow4, mBatteryPaint)
        }
        
        private fun initTextPaint(): Paint = Paint().apply {
            typeface = Typeface.MONOSPACE
            typeface = fontFace
            isAntiAlias = true
            color = ContextCompat.getColor(applicationContext, R.color.digital_text)
        }

        override fun onDestroy() {
            try {
                getSharedPreferences(PREF, Context.MODE_PRIVATE)
                    .unregisterOnSharedPreferenceChangeListener(prefListener)
            } catch (e: Exception) {
                Log.e(TAG, "onDestroy: ", e)
            }
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME)
            super.onDestroy()
        }

        override fun onPropertiesChanged(properties: Bundle) {
            super.onPropertiesChanged(properties)
            mLowBitAmbient = properties.getBoolean(WatchFaceService.PROPERTY_LOW_BIT_AMBIENT, false)
            mBurnInProtection = properties.getBoolean(WatchFaceService.PROPERTY_BURN_IN_PROTECTION, false)
        }

        override fun onTimeTick() {
            super.onTimeTick()
            invalidate()
        }

        override fun onAmbientModeChanged(inAmbientMode: Boolean) {
            super.onAmbientModeChanged(inAmbientMode)
            mAmbient = inAmbientMode

            if (mLowBitAmbient) {
                mRow1.isAntiAlias = !inAmbientMode
                mRow2.isAntiAlias = !inAmbientMode
                mRow3.isAntiAlias = !inAmbientMode
                mRow4.isAntiAlias = !inAmbientMode
                mBatteryPaint.isAntiAlias = !inAmbientMode
            }

            fields.forEach { it.color = if (mAmbient) Color.WHITE else when (mTheme) {
                "RED" -> Color.RED
                "DARK" -> Color.DKGRAY
                "BLUE" -> Color.BLUE
                "GRAY" -> Color.GRAY
                "GREEN" -> Color.GREEN
                "MAGENTA" -> Color.MAGENTA
                "YELLOW" -> Color.YELLOW
                "PINK" -> argb(100,255,0,255)
                "CYAN" -> Color.CYAN
                else -> Color.WHITE}
            }
            updateTimer()
        }

        override fun onTapCommand(tapType: Int, x: Int, y: Int, eventTime: Long) {
            if (tapType == WatchFaceService.TAP_TYPE_TAP && humanTimeCount>0) {
                humanTimeCount = 6
            }else if (tapType == WatchFaceService.TAP_TYPE_TAP){
                humanTimeCount = 3
            }
            invalidate()
        }

        private fun getBatteryLevel(): Int {
            val batteryStatus: Intent? = IntentFilter(Intent.ACTION_BATTERY_CHANGED).let { iFilter ->
                this@BinWatchFace.registerReceiver(null, iFilter)
            }

            val level = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: 0
            val scale = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: 1
            return (level / scale.toFloat() * 100F).toInt()
        }

        private fun drawField(canvas: Canvas, bounds: Rect, textPaint: Paint, text: String, amb: Float, nAmb: Float, drawDots:Boolean=true) {
            val textBounds = Rect()
            textPaint.getTextBounds("".padStart(text.length, '0'), 0, text.length, textBounds)
            val textX = abs(bounds.centerX() - textBounds.centerX()).toFloat()
            val textY = if (!mAmbient)
                abs(bounds.centerY() - textBounds.centerY() + nAmb * textBounds.centerY())
            else
                abs(bounds.centerY() - textBounds.centerY() + amb * textBounds.centerY())
            if(!dots || !drawDots) canvas.drawText(text, textX, textY, textPaint)
            else drawDot(canvas,textX,textY,text, textPaint)
        }

        private fun drawDot(canvas: Canvas, posX: Float, posY: Float, text: String, textPaint: Paint ){
            var c=0
            val inc = if(textPaint == mBatteryPaint)10f else 15f
            val rad = if(textPaint == mBatteryPaint)24 else 35
            for(i in text){
                if (i == '0'){
                    mBatteryPaint.style = Paint.Style.STROKE
                }else if(i == ' '){
                    c+=rad
                    continue
                }else if(i == '%'){
                    canvas.drawText("%", posX+c, posY-5f, textPaint)
                    continue
                }else{
                    mBatteryPaint.style = Paint.Style.FILL
                }
                canvas.drawCircle(posX+c,posY-10f,inc,mBatteryPaint)
                c+=rad
            }
        }

        override fun onDraw(canvas: Canvas, bounds: Rect) {
            canvas.drawColor(Color.BLACK)
            if (!mAmbient && fileBg) canvas.drawBitmap(mBackgroundBitmap, 0f, 0f, mBackgroundPaint)
            val now = System.currentTimeMillis()
            mCalendar.timeInMillis = now
            if (humanTimeCount > 3) {
                //decimal time
                drawField(canvas, bounds, mRow2, String.format("%d:%02d:%02d", mCalendar.get(HOUR_OF_DAY), mCalendar.get(MINUTE), mCalendar.get(SECOND)), -1.5F, 0F, false)
            }else{
                //binary time
                val data = getDrawData()
                val startPos=if(!viewHorizontal){ 5F }else if(!viewHorizontal && !mBCD){ 7.5F } else if(mSeconds)2.5F else 1F
                //visual help
                drawHelp(canvas,bounds)
                drawField(canvas, bounds, mRow1, data[0], 1.5F, startPos)
                drawField(canvas, bounds, mRow2, data[1], 1.5F, startPos-2.5F)
                drawField(canvas, bounds, mRow3, data[2], 1.5F, startPos-5F)
                if(!viewHorizontal && mBCD) drawField(canvas, bounds, mRow4, data[3], 1.5F, startPos-7.5F)
                if(!viewHorizontal && !mBCD){
                    drawField(canvas, bounds, mRow4, data[3], 1.5F, startPos-7.5F)
                    drawField(canvas, bounds, mRow4, data[4], 1.5F, startPos-10F)
                    drawField(canvas, bounds, mRow4, data[5], 1.5F, startPos-12.5F)
                }
            }
            drawBattery(canvas, bounds)
            if (mAmbient || humanTimeCount > 0) {
                humanTimeCount--
            }
        }

        private fun drawHelp(canvas: Canvas, bounds: Rect){
            if (humanTimeCount in 1..3) {
                if (!viewHorizontal && !mBCD) { //vertical Binary
                    drawField(canvas, bounds, mBatteryPaint, "32".padEnd(10, ' '), 1.5F, 13F, false)
                    drawField(canvas, bounds, mBatteryPaint, "16".padEnd(10, ' '), 1.5F, 6F, false)
                    drawField(canvas, bounds, mBatteryPaint, "8".padEnd(10, ' '), 1.5F, -0F, false)
                    drawField(canvas, bounds, mBatteryPaint, "4".padEnd(10, ' '), 1.5F, -6F, false)
                    drawField(canvas, bounds, mBatteryPaint, "2".padEnd(10, ' '), 1.5F, -13F, false)
                    drawField(canvas, bounds, mBatteryPaint, "1".padEnd(10, ' '), 1.5F, -19F, false)
                    if(mSeconds){
                        drawField(canvas, bounds, mBatteryPaint, "H M S", 1.5F, 18F, false)
                    }else{
                        drawField(canvas, bounds, mBatteryPaint, "H M", 1.5F, 18F, false)
                    }
                } else if (viewHorizontal && !mBCD) { // Horizontal Binary
                    drawField(canvas, bounds, mBatteryPaint, "32 16 8 4 2 1", 1.5F, 11.5F, false)
                    if(mSeconds){
                        drawField(canvas, bounds, mBatteryPaint, "H".padEnd(20, ' '), 1.5F, 6.5F, false)
                        drawField(canvas, bounds, mBatteryPaint, "M".padEnd(20, ' '), 1.5F, 0.5F, false)
                        drawField(canvas, bounds, mBatteryPaint, "S".padEnd(20, ' '), 1.5F, -6.5F, false)
                    }else{
                        drawField(canvas, bounds, mBatteryPaint, "H".padEnd(20, ' '), 1.5F, 2.5F, false)
                        drawField(canvas, bounds, mBatteryPaint, "M".padEnd(20, ' '), 1.5F, -4.5F, false)
                    }
                } else if (!viewHorizontal && mBCD) { //Vertical BCD
                    drawField(canvas, bounds, mBatteryPaint, "8".padEnd(if (mSeconds) 20 else 15, ' '), 1.5F, 12.5F, false)
                    drawField(canvas, bounds, mBatteryPaint, "4".padEnd(if (mSeconds) 20 else 15, ' '), 1.5F, 6.5F, false)
                    drawField(canvas, bounds, mBatteryPaint, "2".padEnd(if (mSeconds) 20 else 15, ' '), 1.5F, 0.5F, false)
                    drawField(canvas, bounds, mBatteryPaint, "1".padEnd(if (mSeconds) 20 else 15, ' '), 1.5F, -6.5F, false)
                    if(mSeconds){
                        drawField(canvas, bounds, mBatteryPaint, "H  H  M  M  S  S", 1.5F, 18F, false)
                    }else{
                        drawField(canvas, bounds, mBatteryPaint, "H  H  M  M", 1.5F, 18F, false)
                    }
                } else if (viewHorizontal && mBCD) { //Horizontal BCD
                    drawField(canvas, bounds, mBatteryPaint, " 4  2 1   8  4  2 1", 1.5F, 11.5F, false)
                    if(mSeconds){
                        drawField(canvas, bounds, mBatteryPaint, "H".padEnd(25, ' '), 1.5F, 6.5F, false)
                        drawField(canvas, bounds, mBatteryPaint, "M".padEnd(25, ' '), 1.5F, 0.5F, false)
                        drawField(canvas, bounds, mBatteryPaint, "S".padEnd(25, ' '), 1.5F, -6.5F, false)
                    }else{
                        drawField(canvas, bounds, mBatteryPaint, "H".padEnd(25, ' '), 1.5F, 2.5F, false)
                        drawField(canvas, bounds, mBatteryPaint, "M".padEnd(25, ' '), 1.5F, -4.5F, false)
                    }
                }
            }
        }

        private fun getDrawData(): ArrayList<String>{
            // convert time to Binary
            val time = if(mBCD) arrayListOf<String>( //BCD
                    convertToBin(mCalendar.get(HOUR_OF_DAY).div(10)).padStart(2, '0'),
                    convertToBin(mCalendar.get(HOUR_OF_DAY).rem(10)).padStart(4, '0'),
                    convertToBin(mCalendar.get(MINUTE).div(10)).padStart(3, '0'),
                    convertToBin(mCalendar.get(MINUTE).rem(10)).padStart(4, '0'),
                    convertToBin(mCalendar.get(SECOND).div(10)).padStart(3, '0'),
                    convertToBin(mCalendar.get(SECOND).rem(10)).padStart(4, '0')
                )
            else
                arrayListOf<String>( //Binary
                    convertToBin(mCalendar.get(HOUR_OF_DAY)).padStart(4, '0'),
                    convertToBin(mCalendar.get(MINUTE)).padStart(6, '0'),
                    convertToBin(mCalendar.get(SECOND)).padStart(6, '0')
                )
            // format data Vertical/Horizontal Binary/BCD
            val data = if (!viewHorizontal && mBCD) //Vertical BCD
                arrayListOf(
                        " ${time[1][0]} ${time[3][0]} ${time[5][0]}",
                        " ${time[1][1]}${time[2][0]}${time[3][1]}${time[4][0]}${time[5][1]}",
                        "${time[0][0]}${time[1][2]}${time[2][1]}${time[3][2]}${time[4][1]}${time[5][2]}",
                        "${time[0][1]}${time[1][3]}${time[2][2]}${time[3][3]}${time[4][2]}${time[5][3]}"
                )
            else if (viewHorizontal && !mBCD) //Horizontal Binary
                arrayListOf(time[0], time[1], time[2])
            else if (viewHorizontal && mBCD) //Horizontal BCD
                arrayListOf(" ${time[0]} ${time[1]}", "${time[2]} ${time[3]}", "${time[4]} ${time[5]}")
            else  //Vertical Binary
                arrayListOf(
                        " ${time[1][0]}${time[2][0]}",
                        " ${time[1][1]}${time[2][1]}",
                        "${time[0][0]}${time[1][2]}${time[2][2]}",
                        "${time[0][1]}${time[1][3]}${time[2][3]}",
                        "${time[0][2]}${time[1][4]}${time[2][4]}",
                        "${time[0][3]}${time[1][5]}${time[2][5]}"
                )
            return checkSeconds(data)
        }

        private fun checkSeconds(data: ArrayList<String>): ArrayList<String>{
            if(!mSeconds){ // if seconds off
                if(!viewHorizontal && mBCD){ //Vertical BCD
                    data[0]=data[0].dropLast(2)
                    data[1]=data[1].dropLast(2)
                    data[2]=data[2].dropLast(2)
                    data[3]=data[3].dropLast(2)
                }else if(!viewHorizontal && !mBCD){ // Vertical Binary
                    data[0]=data[0].dropLast(1)
                    data[1]=data[1].dropLast(1)
                    data[2]=data[2].dropLast(1)
                    data[3]=data[3].dropLast(1)
                    data[4]=data[4].dropLast(1)
                    data[5]=data[5].dropLast(1)
                }else { // Horizontal BCD or Binary
                    data[2]=""
                }
            }
            return data
        }

        private fun drawBattery(canvas: Canvas, bounds: Rect){
            if(mAmbient || mShowBatt){
                val batteryText = if (getBatteryLevel()==100){
                    "1 0 0%"
                }else{
                    val bat1 = convertToBin(getBatteryLevel().div(10)).padStart(4, '0')
                    val bat2 = convertToBin(getBatteryLevel().rem(10)).padStart(4, '0')
                    "$bat1 $bat2%"
                }
                if(humanTimeCount > 3){
                    drawField(canvas, bounds, mBatteryPaint, getBatteryLevel().toString()+"%", 0F, -4.5F,false)
                }else if(!mBCD && !viewHorizontal){
                    drawField(canvas, bounds, mBatteryPaint, batteryText.padEnd(30,' '), 0F, 0F)
                }else{
                    drawField(canvas, bounds, mBatteryPaint, batteryText, 0F, -12.5F)
                }
            }
        }

        override fun onVisibilityChanged(visible: Boolean) {
            super.onVisibilityChanged(visible)

            if (visible) {
                registerReceiver()
                mCalendar.timeZone = TimeZone.getDefault()
                invalidate()
            } else {
                unregisterReceiver()
            }
            updateTimer()
        }

        private fun registerReceiver() {
            if (mRegisteredTimeZoneReceiver) {
                return
            }
            mRegisteredTimeZoneReceiver = true
            val filter = IntentFilter(Intent.ACTION_TIMEZONE_CHANGED)
            this@BinWatchFace.registerReceiver(mTimeZoneReceiver, filter)
        }

        private fun unregisterReceiver() {
            if (!mRegisteredTimeZoneReceiver) {
                return
            }
            mRegisteredTimeZoneReceiver = false
            this@BinWatchFace.unregisterReceiver(mTimeZoneReceiver)
        }

        override fun onApplyWindowInsets(insets: WindowInsets) {
            super.onApplyWindowInsets(insets)
            val resources = this@BinWatchFace.resources
            val isRound = insets.isRound
            mXOffset = resources.getDimension(if (isRound) R.dimen.digital_x_offset_round else R.dimen.digital_x_offset)

            val textSize =
                resources.getDimension(if (isRound) R.dimen.digital_text_size_round else R.dimen.digital_text_size)

            mRow1.textSize = textSize
            mRow2.textSize = textSize
            mRow3.textSize = textSize
            mRow4.textSize = textSize
            mBatteryPaint.textSize = resources.getDimension(if (isRound) R.dimen.digital_text_size_round_small else R.dimen.digital_text_size_small)
            fields.forEach { it.color = when (mTheme) {
                "RED" -> Color.RED
                "DARK" -> Color.DKGRAY
                "BLUE" -> Color.BLUE
                "GRAY" -> Color.GRAY
                "GREEN" -> Color.GREEN
                "MAGENTA" -> Color.MAGENTA
                "YELLOW" -> Color.YELLOW
                "PINK" -> argb(100,255,0,255)
                "CYAN" -> Color.CYAN
                else -> Color.WHITE
            } }
        }

        private fun updateTimer() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME)
            if (shouldTimerBeRunning()) {
                mUpdateTimeHandler.sendEmptyMessage(MSG_UPDATE_TIME)
            }
        }

        private fun shouldTimerBeRunning(): Boolean {
            return isVisible && !isInAmbientMode
        }

        fun handleUpdateTimeMessage() {
            invalidate()
            if (shouldTimerBeRunning()) {
                val timeMs = System.currentTimeMillis()
                val delayMs = INTERACTIVE_UPDATE_RATE_MS - timeMs % INTERACTIVE_UPDATE_RATE_MS
                mUpdateTimeHandler.sendEmptyMessageDelayed(MSG_UPDATE_TIME, delayMs)
            }
        }
    }
}
