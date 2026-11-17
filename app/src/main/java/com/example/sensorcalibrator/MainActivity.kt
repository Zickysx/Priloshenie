package com.example.sensorcalibrator
import android.app.AlertDialog
import android.app.Activity
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.widget.*
import java.util.Locale
import kotlin.math.roundToInt

class MainActivity : Activity(), SensorEventListener {
    private lateinit var manager: SensorManager
    private lateinit var list: LinearLayout
    private lateinit var status: TextView
    private lateinit var compassValue: TextView
    private lateinit var accelValue: TextView
    private lateinit var gyroValue: TextView
    private lateinit var lightValue: TextView
    private lateinit var proximityValue: TextView
    private val labels = mutableMapOf<Int, TextView>()
    private var rotationMatrix = FloatArray(9)
    private var orientation = FloatArray(3)
    private var latestAzimuth = 0f
    private var accel: Sensor? = null
    private var magnetic: Sensor? = null
    private var gravity: FloatArray? = null
    private var geomagnetic: FloatArray? = null

    private val bg = Color.rgb(16, 23, 19)
    private val panel = Color.rgb(28, 39, 32)
    private val green = Color.rgb(49, 196, 108)
    private val muted = Color.rgb(174, 190, 179)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        manager = getSystemService(SENSOR_SERVICE) as SensorManager
        accel = manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        magnetic = manager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
        buildUi()
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(18), dp(20), dp(18))
            setBackgroundColor(bg)
        }
        val scroll = ScrollView(this)
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(content)

        content.addView(text("SENSOR CALIBRATOR", 23, green, true))
        content.addView(text("Диагностика датчиков • Android", 13, muted))
        content.addView(space(18))

        val header = card()
        header.addView(text("SAMSUNG GALAXY S24 FE", 14, Color.WHITE, true))
        header.addView(text("Проверка доступных аппаратных датчиков", 12, muted))
        status = text("Готово к диагностике", 13, green, true)
        header.addView(status)
        content.addView(header)
        content.addView(space(12))

        content.addView(section("КОМПАС"))
        val compass = card()
        compassValue = text("Ожидание данных…", 22, Color.WHITE, true)
        compass.addView(compassValue)
        compass.addView(text("Поворачивайте телефон плавно. Если направление скачет, выполните движение «восьмёркой» вдали от магнитов.", 12, muted))
        val compassButton = button("Инструкция по калибровке")
        compassButton.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Калибровка компаса")
                .setMessage("1. Отойдите от магнитов, чехлов с магнитами и металлических предметов.\n\n2. Плавно перемещайте телефон в воздухе, описывая горизонтальную восьмёрку.\n\n3. Повторите движение в нескольких ориентациях.\n\nAndroid не предоставляет обычным приложениям команду для изменения заводской калибровки магнитометра. Эта процедура помогает датчику/системе уточнить направление.")
                .setPositiveButton("Понятно", null).show()
        }
        compass.addView(compassButton)
        content.addView(compass)
        content.addView(space(12))

        content.addView(section("ДАТЧИКИ"))
        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        content.addView(list)

        accelValue = text("—", 13, Color.WHITE)
        gyroValue = text("—", 13, Color.WHITE)
        lightValue = text("—", 13, Color.WHITE)
        proximityValue = text("—", 13, Color.WHITE)

        val types = listOf(
            Sensor.TYPE_ACCELEROMETER to "Акселерометр",
            Sensor.TYPE_GYROSCOPE to "Гироскоп",
            Sensor.TYPE_MAGNETIC_FIELD to "Магнитометр",
            Sensor.TYPE_PROXIMITY to "Приближение",
            Sensor.TYPE_LIGHT to "Освещённость",
            Sensor.TYPE_PRESSURE to "Барометр"
        )
        types.forEach { (type, name) ->
            val s = manager.getDefaultSensor(type)
            val c = card()
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            val left = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            left.addView(text(name, 15, Color.WHITE, true))
            left.addView(text(s?.name ?: "Не обнаружен на устройстве", 11, muted))
            row.addView(left, LinearLayout.LayoutParams(0, -2, 1f))
            val badge = text(if (s != null) "ДОСТУПЕН" else "НЕТ", 11, if (s != null) green else muted, true)
            row.addView(badge)
            c.addView(row)
            val value = text(if (s != null) "Ожидание показаний…" else "Этот датчик не найден", 12, muted)
            c.addView(value)
            labels[type] = value
            list.addView(c)
            list.addView(space(8))
        }

        content.addView(space(6))
        val diag = button("Запустить диагностику")
        diag.setOnClickListener { runDiagnostic() }
        content.addView(diag)
        content.addView(space(8))
        content.addView(text("Примечание: приложение показывает данные и выполняет базовую проверку. Оно не может перепрошивать или менять заводские коэффициенты калибровки датчиков Samsung.", 11, muted))
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
    }

    private fun runDiagnostic() {
        val all = listOf(
            Sensor.TYPE_ACCELEROMETER, Sensor.TYPE_GYROSCOPE, Sensor.TYPE_MAGNETIC_FIELD,
            Sensor.TYPE_PROXIMITY, Sensor.TYPE_LIGHT, Sensor.TYPE_PRESSURE
        )
        val found = all.count { manager.getDefaultSensor(it) != null }
        status.text = "Обнаружено датчиков: $found из ${all.size}. Проверка показаний идёт в реальном времени."
        Toast.makeText(this, "Диагностика запущена", Toast.LENGTH_SHORT).show()
    }

    override fun onResume() {
        super.onResume()
        val types = intArrayOf(
            Sensor.TYPE_ACCELEROMETER, Sensor.TYPE_GYROSCOPE, Sensor.TYPE_MAGNETIC_FIELD,
            Sensor.TYPE_PROXIMITY, Sensor.TYPE_LIGHT, Sensor.TYPE_PRESSURE
        )
        types.forEach { type ->
            manager.getDefaultSensor(type)?.let {
                manager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
            }
        }
    }

    override fun onPause() {
        super.onPause()
        manager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent) {
        val v = event.values
        val formatted = when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> "X ${f(v[0])}  Y ${f(v[1])}  Z ${f(v[2])} м/с²"
            Sensor.TYPE_GYROSCOPE -> "X ${f(v[0])}  Y ${f(v[1])}  Z ${f(v[2])} рад/с"
            Sensor.TYPE_MAGNETIC_FIELD -> "X ${f(v[0])}  Y ${f(v[1])}  Z ${f(v[2])} мкТл"
            Sensor.TYPE_PROXIMITY -> "${f(v[0])} см"
            Sensor.TYPE_LIGHT -> "${f(v[0])} лк"
            Sensor.TYPE_PRESSURE -> "${f(v[0])} гПа"
            else -> v.joinToString()
        }
        labels[event.sensor.type]?.text = formatted

        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> {
                gravity = v.clone()
                accelValue.text = formatted
            }
            Sensor.TYPE_MAGNETIC_FIELD -> geomagnetic = v.clone()
            Sensor.TYPE_GYROSCOPE -> gyroValue.text = formatted
            Sensor.TYPE_LIGHT -> lightValue.text = formatted
            Sensor.TYPE_PROXIMITY -> proximityValue.text = formatted
        }
        val g = gravity
        val m = geomagnetic
        if (g != null && m != null && SensorManager.getRotationMatrix(rotationMatrix, null, g, m)) {
            SensorManager.getOrientation(rotationMatrix, orientation)
            latestAzimuth = ((Math.toDegrees(orientation[0].toDouble()).toFloat() + 360f) % 360f)
            compassValue.text = "${latestAzimuth.roundToInt()}°  •  ${direction(latestAzimuth)}"
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        if (sensor?.type == Sensor.TYPE_MAGNETIC_FIELD) {
            val message = when (accuracy) {
                SensorManager.SENSOR_STATUS_ACCURACY_HIGH -> "Точность компаса: высокая"
                SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM -> "Точность компаса: средняя"
                SensorManager.SENSOR_STATUS_ACCURACY_LOW -> "Точность компаса: низкая — попробуйте движение восьмёркой"
                else -> "Точность компаса: ненадёжная"
            }
            status.text = message
        }
    }

    private fun direction(deg: Float): String {
        val dirs = arrayOf("С", "СВ", "В", "ЮВ", "Ю", "ЮЗ", "З", "СЗ")
        return dirs[((deg + 22.5f) / 45f).toInt() % 8]
    }
    private fun f(x: Float) = String.format(Locale.getDefault(), "%.2f", x)
    private fun dp(v: Int) = (v * resources.displayMetrics.density).roundToInt()
    private fun text(s: String, size: Int, color: Int, bold: Boolean = false) = TextView(this).apply {
        text = s; textSize = size.toFloat(); setTextColor(color)
        if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
        setPadding(0, dp(4), 0, dp(4))
    }
    private fun section(s: String) = text(s, 12, green, true).apply { setPadding(2, dp(5), 0, dp(7)) }
    private fun space(h: Int) = View(this).apply { layoutParams = LinearLayout.LayoutParams(1, dp(h)) }
    private fun card() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL; setPadding(dp(14), dp(12), dp(14), dp(12))
        setBackgroundColor(panel)
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(2) }
    }
    private fun button(label: String) = Button(this).apply {
        text = label; setTextColor(Color.WHITE); setBackgroundColor(Color.rgb(32, 145, 78))
        isAllCaps = false
    }
}
