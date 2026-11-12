// SPDX-License-Identifier: AGPL-3.0-or-later
package com.zhuhuibin.hyperchange.lite

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import com.zhuhuibin.hyperchange.lite.preview.GlassPlayground
import java.util.Locale
import kotlin.math.roundToInt

/** A small settings app: local, inspectable previews, and no privileged UI actions. */
class MainActivity : Activity() {
    private lateinit var repository: SettingsRepository
    private lateinit var playground: GlassPlayground
    private lateinit var status: TextView
    private lateinit var settingsList: LinearLayout
    private lateinit var tabs: LinearLayout
    private lateinit var sceneTabs: LinearLayout
    private val refreshers = mutableListOf<() -> Unit>()
    private val handler = Handler(Looper.getMainLooper())
    private var selectedGroup = ""
    private var exportText = ""
    private var darkPreview = false
    private var uiReady = false
    private val ink = Color.rgb(30, 45, 44)
    private val muted = Color.rgb(111, 124, 121)
    private val accent = Color.rgb(29, 115, 102)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(244, 246, 242)
        window.navigationBarColor = Color.rgb(244, 246, 242)
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        repository = SettingsRepository(this)
        selectedGroup = savedInstanceState?.getString("group") ?: LiteCatalog.entries.firstOrNull()?.group.orEmpty()
        darkPreview = savedInstanceState?.getBoolean("preview_dark") ?: false
        buildPage()
        repository.onChange = {
            handler.post {
                if (uiReady && !isFinishing && !isDestroyed) {
                    updateStatus()
                    refreshers.forEach { it() }
                    playground.refresh()
                }
            }
        }
        updateStatus()
        val savedWallpaper = getSharedPreferences("preview_ui", MODE_PRIVATE).getString("wallpaper_uri", null)
        if (savedWallpaper != null) playground.loadWallpaper(Uri.parse(savedWallpaper))
    }

    private fun buildPage() {
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(244, 246, 242))
            setPadding(dp(18), dp(8), dp(18), dp(8))
            fitsSystemWindows = true
        }
        val titleRow = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        val names = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        names.addView(label("HyperChange Lite", 24f, ink, true))
        status = label("本地设置", 12f, muted).apply { setPadding(0, dp(4), 0, 0) }
        names.addView(status)
        titleRow.addView(names, LinearLayout.LayoutParams(0, dp(66), 1f))
        titleRow.addView(chip("配置") { showPresetMenu() })
        page.addView(titleRow)

        val configuration = resources.configuration
        val sideBySide = configuration.screenWidthDp >= 720 ||
            (configuration.screenWidthDp >= 560 && configuration.screenWidthDp > configuration.screenHeightDp)
        val body = LinearLayout(this).apply {
            orientation = if (sideBySide) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
        }
        val previewPanel = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val previewHeader = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        previewHeader.addView(label("场景预览", 15f, ink, true), LinearLayout.LayoutParams(0, dp(38), 1f))
        previewHeader.addView(chip("背景") { showBackgroundMenu() })
        previewHeader.addView(chip(if (darkPreview) "夜间" else "日间") {
            darkPreview = !darkPreview
            playground.setNight(darkPreview)
            (previewHeader.getChildAt(2) as TextView).text = if (darkPreview) "夜间" else "日间"
        })
        previewHeader.addView(chip("重播") { playground.replay() })
        previewPanel.addView(previewHeader)
        playground = GlassPlayground(this, repository.preferences)
        playground.setNight(darkPreview)
        // Seed from the current window, then use its measured height below. Folded
        // landscape and split-screen windows must keep the scene tabs reachable.
        val initialPreviewDp = if (sideBySide) (configuration.screenHeightDp - 190).coerceIn(0, 360)
            else (configuration.screenHeightDp - 365).coerceIn(0, 236)
        previewPanel.addView(playground, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(initialPreviewDp)).apply { bottomMargin = dp(9) })
        sceneTabs = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        val sceneScroll = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false; addView(sceneTabs) }
        previewPanel.addView(sceneScroll, LinearLayout.LayoutParams(-1, dp(42)))
        rebuildSceneTabs()

        val controls = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        tabs = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        val tabScroll = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false; addView(tabs) }
        controls.addView(tabScroll, LinearLayout.LayoutParams(-1, dp(52)))
        settingsList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, dp(2), 0, dp(24)) }
        val scroll = ScrollView(this).apply {
            isVerticalScrollBarEnabled = false
            isFillViewport = false
            clipToPadding = false
            addView(settingsList)
        }
        controls.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        if (body.orientation == LinearLayout.HORIZONTAL) {
            body.addView(previewPanel, LinearLayout.LayoutParams(0, -1, 0.45f).apply { rightMargin = dp(24) })
            body.addView(controls, LinearLayout.LayoutParams(0, -1, 0.55f))
        } else {
            body.addView(previewPanel, LinearLayout.LayoutParams(-1, -2))
            body.addView(controls, LinearLayout.LayoutParams(-1, 0, 1f))
        }
        page.addView(body, LinearLayout.LayoutParams(-1, 0, 1f))
        body.addOnLayoutChangeListener { _, _, top, _, bottom, _, oldTop, _, oldBottom ->
            if (bottom - top != oldBottom - oldTop) {
                val previewChrome = previewHeader.measuredHeight.coerceAtLeast(dp(40)) + dp(42 + 9)
                val settingsReserve = if (sideBySide) 0 else dp(52 + 120)
                val available = (bottom - top - previewChrome - settingsReserve).coerceAtLeast(0)
                val targetHeight = available.coerceAtMost(dp(if (sideBySide) 360 else 236))
                val params = playground.layoutParams
                if (params.height != targetHeight) { params.height = targetHeight; playground.layoutParams = params }
            }
        }
        setContentView(page)
        rebuildGroups()
        uiReady = true
    }

    private fun rebuildSceneTabs() {
        sceneTabs.removeAllViews()
        GlassPlayground.Scene.entries.forEach { scene ->
            sceneTabs.addView(chip(scene.title, playground.scene == scene) {
                playground.setScene(scene)
                rebuildSceneTabs()
            })
        }
    }

    private fun rebuildGroups() {
        tabs.removeAllViews()
        LiteCatalog.entries.map { it.group }.distinct().forEach { group ->
            tabs.addView(chip(group, group == selectedGroup) {
                selectedGroup = group
                rebuildGroups()
            })
        }
        settingsList.removeAllViews()
        refreshers.clear()
        LiteCatalog.entries.filter { it.group == selectedGroup }.forEach { spec ->
            if (spec.isToggle) addSwitch(spec) else addSlider(spec)
        }
        playground.setFocusGroup(selectedGroup)
        rebuildSceneTabs()
    }

    private fun addSwitch(spec: LiteSetting) {
        val box = panel()
        val row = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        val text = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, 0, dp(12), 0) }
        text.addView(label(spec.title, 15f, ink, true))
        if (spec.summary.isNotBlank()) text.addView(label(spec.summary, 12f, muted).apply { setPadding(0, dp(6), 0, 0) })
        row.addView(text, LinearLayout.LayoutParams(0, -2, 1f))
        val toggle = Switch(this).apply {
            contentDescription = spec.title
            isChecked = booleanValue(spec)
            buttonTintList = null
            thumbTintList = android.content.res.ColorStateList.valueOf(Color.WHITE)
            trackTintList = android.content.res.ColorStateList(arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()), intArrayOf(accent, Color.rgb(209, 217, 211)))
        }
        var refreshing = false
        toggle.setOnCheckedChangeListener { _, enabled ->
            if (!refreshing) writeSetting(spec, enabled)
        }
        refreshers += {
            refreshing = true
            toggle.isChecked = booleanValue(spec)
            refreshing = false
        }
        row.addView(toggle, LinearLayout.LayoutParams(-2, dp(48)))
        row.setOnClickListener { toggle.isChecked = !toggle.isChecked }
        box.addView(row)
        settingsList.addView(box, blockParams())
    }

    private fun addSlider(spec: LiteSetting) {
        val box = panel()
        val titleRow = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        titleRow.addView(label(spec.title, 15f, ink, true), LinearLayout.LayoutParams(0, -2, 1f))
        val valueLabel = label(format(numberValue(spec)), 14f, accent, true).apply {
            gravity = Gravity.CENTER
            minWidth = dp(56)
            minHeight = dp(44)
            contentDescription = "${spec.title}，点击输入数值"
            setOnClickListener { editNumber(spec) }
        }
        titleRow.addView(valueLabel)
        box.addView(titleRow)
        if (spec.summary.isNotBlank()) box.addView(label(spec.summary, 12f, muted).apply { setPadding(0, 0, 0, dp(5)) })
        val steps = ((spec.max - spec.min) / spec.step).roundToInt().coerceIn(1, 10000)
        val seek = SeekBar(this).apply {
            max = steps
            progress = ((numberValue(spec) - spec.min) / spec.step).roundToInt().coerceIn(0, steps)
            progressTintList = android.content.res.ColorStateList.valueOf(accent)
            thumbTintList = android.content.res.ColorStateList.valueOf(accent)
            contentDescription = spec.title
            minimumHeight = dp(44)
            setPadding(dp(4), 0, dp(4), 0)
        }
        var queued: Runnable? = null
        var moving = false
        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onStartTrackingTouch(seekBar: SeekBar?) { moving = true }
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (!fromUser) return
                val value = (spec.min + progress * spec.step).coerceIn(spec.min, spec.max)
                valueLabel.text = format(value)
                queued?.let(handler::removeCallbacks)
                queued = Runnable { writeSetting(spec, value) }.also { handler.postDelayed(it, 40L) }
            }
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                moving = false
                queued?.let { handler.removeCallbacks(it); it.run() }
                queued = null
            }
        })
        refreshers += {
            if (!moving) {
                val value = numberValue(spec)
                valueLabel.text = format(value)
                seek.progress = ((value - spec.min) / spec.step).roundToInt().coerceIn(0, steps)
            }
        }
        box.addView(seek, LinearLayout.LayoutParams(-1, dp(42)))
        val range = LinearLayout(this)
        range.addView(label(format(spec.min), 11f, muted), LinearLayout.LayoutParams(0, -2, 1f))
        range.addView(label(format(spec.max), 11f, muted).apply { gravity = Gravity.END }, LinearLayout.LayoutParams(0, -2, 1f))
        box.addView(range)
        settingsList.addView(box, blockParams())
    }

    private fun editNumber(spec: LiteSetting) {
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL or InputType.TYPE_NUMBER_FLAG_SIGNED
            setText(format(numberValue(spec)))
            selectAll()
            setPadding(dp(24), dp(18), dp(24), dp(18))
        }
        val dialog = AlertDialog.Builder(this).setTitle(spec.title).setMessage("范围 ${format(spec.min)} — ${format(spec.max)}")
            .setView(input).setNegativeButton("取消", null).setPositiveButton("保存", null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val value = input.text.toString().toFloatOrNull()
                if (value == null || !value.isFinite() || value !in spec.min..spec.max) input.error = "请输入范围内的数值"
                else { writeSetting(spec, value); dialog.dismiss() }
            }
        }
        dialog.show()
    }

    private fun showPresetMenu() {
        AlertDialog.Builder(this).setTitle("配置").setItems(arrayOf("导出当前配置", "导入配置", "恢复默认")) { _, item ->
            when (item) {
                0 -> {
                    val encoded = runCatching { repository.exportJson() }
                    if (encoded.isFailure) { toast("导出失败：${encoded.exceptionOrNull()?.message}"); return@setItems }
                    exportText = encoded.getOrThrow()
                    startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                        addCategory(Intent.CATEGORY_OPENABLE)
                        type = "application/json"
                        putExtra(Intent.EXTRA_TITLE, "hyperchange-lite-preset.json")
                    }, EXPORT)
                }
                1 -> startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "*/*"
                    putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("application/json", "text/plain"))
                }, IMPORT)
                2 -> AlertDialog.Builder(this).setTitle("恢复默认配置？").setMessage("所有功能开关与参数恢复为初始值；预览背景保留。")
                    .setNegativeButton("取消", null).setPositiveButton("恢复") { _, _ -> runCatching { repository.reset() }.onFailure { toast("恢复失败：${it.message}") } }.show()
            }
        }.show()
    }

    private fun showBackgroundMenu() {
        AlertDialog.Builder(this).setTitle("预览背景").setItems(arrayOf("海岸", "暮色", "晨光", "选择图片")) { _, item ->
            if (item == 3) startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                type = "image/*"; addCategory(Intent.CATEGORY_OPENABLE)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
            }, WALLPAPER)
            else {
                playground.setWallpaperStyle(item)
                getSharedPreferences("preview_ui", MODE_PRIVATE).edit().remove("wallpaper_uri").apply()
            }
        }.show()
    }

    @Deprecated("Platform result API is sufficient for the dependency-free settings activity")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val uri = data?.data ?: return
        if (resultCode != RESULT_OK) return
        when (requestCode) {
            WALLPAPER -> {
                runCatching { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
                playground.loadWallpaper(uri)
                getSharedPreferences("preview_ui", MODE_PRIVATE).edit().putString("wallpaper_uri", uri.toString()).apply()
            }
            EXPORT -> {
                val snapshot = exportText
                Thread {
                    val result = runCatching { contentResolver.openOutputStream(uri, "wt")?.bufferedWriter()?.use { it.write(snapshot) } ?: error("无法写入文件") }
                    runOnUiThread { toast(if (result.isSuccess) "配置已导出" else "导出失败：${result.exceptionOrNull()?.message}") }
                }.start()
            }
            IMPORT -> Thread {
                val result = runCatching {
                    val bytes = contentResolver.openInputStream(uri)?.use { it.readNBytes(1_048_577) } ?: error("无法读取文件")
                    require(bytes.size <= 1_048_576) { "配置文件过大" }
                    repository.importJson(bytes.toString(Charsets.UTF_8))
                }
                runOnUiThread { toast(result.fold({ "已导入 $it 项设置" }, { "导入失败：${it.message}" })) }
            }.start()
        }
    }

    private fun updateStatus() {
        status.text = when {
            repository.syncFailed -> "已保存在本地 · 模块同步失败"
            repository.connected -> "模块已连接 · 设置自动同步"
            else -> "本地设置 · 等待模块连接"
        }
        status.setTextColor(if (repository.connected && !repository.syncFailed) accent else muted)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("group", selectedGroup)
        outState.putBoolean("preview_dark", darkPreview)
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        uiReady = false
        repository.onChange = null
        repository.close()
        handler.removeCallbacksAndMessages(null)
        playground.release()
        super.onDestroy()
    }

    private fun writeSetting(spec: LiteSetting, value: Any) {
        runCatching { repository.set(spec, value) }.onFailure { toast("设置同步失败：${it.message}") }
    }

    private fun numberValue(spec: LiteSetting): Float = (repository.preferences.all[spec.key] as? Number)?.toFloat() ?: (spec.defaultValue as Number).toFloat()
    private fun booleanValue(spec: LiteSetting): Boolean = repository.preferences.all[spec.key] as? Boolean ?: spec.defaultValue as Boolean
    private fun toast(message: String) { if (!isDestroyed) Toast.makeText(this, message, Toast.LENGTH_LONG).show() }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).roundToInt()
    private fun format(value: Float) = if (value % 1f == 0f) value.roundToInt().toString() else String.format(Locale.ROOT, "%.2f", value).trimEnd('0').trimEnd('.')
    private fun label(value: String, size: Float, color: Int, bold: Boolean = false) = TextView(this).apply {
        text = value; textSize = size; setTextColor(color)
        if (bold) setTypeface(typeface, Typeface.BOLD)
        setLineSpacing(dp(2).toFloat(), 1f)
    }
    private fun panel() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(16), dp(10), dp(16), dp(11))
        background = GradientDrawable().apply { setColor(Color.WHITE); cornerRadius = dp(21).toFloat() }
    }
    private fun blockParams() = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(9) }
    private fun chip(text: String, selected: Boolean = false, action: () -> Unit) = label(text, 12f, if (selected) Color.WHITE else accent, true).apply {
        gravity = Gravity.CENTER
        setPadding(dp(12), dp(6), dp(12), dp(6))
        minHeight = dp(40)
        background = GradientDrawable().apply { setColor(if (selected) accent else Color.TRANSPARENT); cornerRadius = dp(18).toFloat() }
        isClickable = true
        isFocusable = true
        setOnClickListener { action() }
    }
    companion object { private const val EXPORT = 41; private const val IMPORT = 42; private const val WALLPAPER = 43 }
}
