// SPDX-License-Identifier: AGPL-3.0-or-later
package com.zhuhuibin.hyperchange.lite

import com.zhuhuibin.hyperchange.lite.ui.EditorItem
import com.zhuhuibin.hyperchange.lite.ui.editorItems
import com.zhuhuibin.hyperchange.lite.ui.settingDisabledReason
import com.zhuhuibin.hyperchange.lite.ui.Feature
import com.zhuhuibin.hyperchange.lite.ui.MaterialCategory
import com.zhuhuibin.hyperchange.lite.ui.UiTextCatalog
import com.zhuhuibin.hyperchange.lite.ui.settingDisplayTitle
import org.junit.Assert.*
import org.junit.Test
import org.json.JSONArray
import java.io.File
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory

class UiTextCatalogTest {
    private val main = listOf(File("src/main"), File("app/src/main")).first { it.isDirectory }
    private val source = File(main, "java/com/zhuhuibin/hyperchange/lite")
    private val han = Regex("[\\p{IsHan}]")
    private val placeholder = Regex("%(?:(\\d+)\\$)?([sd])")

    private fun resources(folder: String): Map<String, String> {
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder()
            .parse(File(main, "res/$folder/ui_text.xml"))
        val strings = document.getElementsByTagName("string")
        val names = (0 until strings.length).map { strings.item(it).attributes.getNamedItem("name").nodeValue }
        assertEquals("Duplicate resource name in $folder", names.size, names.toSet().size)
        return (0 until strings.length).associate { index ->
            val node = strings.item(index)
            node.attributes.getNamedItem("name").nodeValue to node.textContent
        }
    }

    private fun assertCovered(text: String) = assertNotNull("Missing localized source: $text", UiTextCatalog.resourceFor(text))

    @Test fun allVisibleSettingFeatureCategoryAndPresetTitlesHaveResources() {
        LiteCatalog.entries.filter { it.key !in DeferredSettings.hiddenKeys }.forEach {
            assertCovered(it.title)
            assertCovered(settingDisplayTitle(it))
            assertCovered(it.group)
            if (it.summary.isNotBlank()) assertCovered(it.summary)
        }
        Feature.entries.forEach {
            assertCovered(it.title)
            assertTrue("Missing feature explanation: ${it.name}", it.summary.isNotBlank())
            assertCovered(it.summary)
        }
        MaterialCategory.entries.forEach { assertCovered(it.title) }
        builtInGlassPresets.forEachIndexed { index, preset ->
            assertCovered(preset.title)
            assertCovered("${'A' + index} · ${preset.title}")
            assertTrue("Missing preset explanation: ${preset.id}", preset.description.isNotBlank())
            assertCovered(preset.description)
        }
    }

    @Test fun currentComposePageLiteralsAndActivityToastTemplatesHaveResources() {
        val composeLiteral = Regex("uiText\\(\"([^\"\\n]*)\"\\)")
        File(source, "ui").listFiles().orEmpty().filter { it.extension == "kt" }.forEach { file ->
            composeLiteral.findAll(file.readText()).forEach { assertCovered(it.groupValues[1]) }
        }
        val reportLiteral = Regex("(?:report|reportSettingsFailure)\\(\"([^\"\\n]*)\"")
        reportLiteral.findAll(File(source, "MainActivity.kt").readText()).forEach { assertCovered(it.groupValues[1]) }
    }

    @Test fun structuredEditorGroupTitlesAndNotesHaveResources() {
        fun cover(items: List<EditorItem>) {
            items.forEach { item -> when (item) {
                is EditorItem.Group -> {
                    if (item.title.isNotBlank()) assertCovered(item.title)
                    cover(item.children)
                }
                is EditorItem.Note -> assertCovered(item.text)
                is EditorItem.Setting -> assertTrue("Unknown mirrored key: ${item.key}",
                    LiteCatalog.entries.any { it.key == item.key })
            } }
        }
        MaterialCategory.entries.forEach { category ->
            cover(editorItems(category, category.settings.filter { it.key !in DeferredSettings.hiddenKeys }))
        }
        Feature.entries.forEach { feature ->
            feature.visibleSettings.groupBy { it.group }.values.forEach { cover(editorItems(null, it)) }
        }
    }

    @Test fun dependencyTemplatesAndTheirDynamicTitlesHaveResources() {
        val toggles = LiteCatalog.entries.filter { it.isToggle }.map { it.key }
        val allOn = toggles.associateWith { true }
        val allOff = toggles.associateWith { false }
        val states = listOf(emptyMap(), allOn, allOff) +
            toggles.map { allOn + (it to false) } + toggles.map { allOff + (it to true) }
        val seen = mutableSetOf<String>()
        states.forEach { state ->
            LiteCatalog.entries.filter { it.key !in DeferredSettings.hiddenKeys }.forEach { setting ->
                settingDisabledReason(setting, state) { text ->
                    seen += text
                    assertCovered(text)
                    text
                }
            }
        }
        assertTrue(seen.contains("需先开启“%s”（原数值已保留）"))
        assertTrue(seen.contains("需先关闭“%s”（原数值已保留）"))
    }

    @Test fun currentStructuredUiLiteralsHaveResources() {
        val quoted = Regex("\"((?:[^\"\\\\]|\\\\.)*)\"")
        val paths = listOf("ui/LiteApp.kt", "ui/AppPages.kt", "ui/GlassPresetControls.kt",
            "ui/SettingEditorPlan.kt", "ui/SettingPresentation.kt", "ui/DonationDialog.kt")
        paths.forEach { path ->
            quoted.findAll(File(source, path).readText()).forEach { match ->
                val raw = match.groupValues[1]
                if ('$' in raw || '\n' in raw) return@forEach
                val text = JSONArray("[\"$raw\"]").getString(0)
                if (han.containsMatchIn(text)) assertCovered(text)
            }
        }
    }

    @Test fun englishFallbackAndChineseHaveTheSameCompleteResourceSet() {
        val fallback = resources("values")
        val english = resources("values-en")
        val chinese = resources("values-zh")
        assertEquals(fallback, english)
        assertEquals(fallback.keys, chinese.keys)
        val referenced = Regex("R\\.string\\.([a-z0-9_]+)").findAll(File(source, "ui/UiText.kt").readText())
            .map { it.groupValues[1] }.toSet()
        assertEquals("Resource catalog and XML keys must match", referenced, fallback.keys)
        assertEquals(UiTextCatalog.resourceIds.size, fallback.size)
        assertEquals(UiTextCatalog.resourceIds.size, UiTextCatalog.resourceIds.values.toSet().size)
        fallback.forEach { (id, text) ->
            assertTrue("Empty English resource: $id", text.isNotBlank())
            assertFalse("Chinese leaked into English fallback: $id", han.containsMatchIn(text))
            assertTrue("Empty Chinese resource: $id", chinese.getValue(id).isNotBlank())
        }
    }

    @Test fun currentAndroidViewSampleLabelsAndStatusLiteralsHaveResources() {
        val preview = File(source, "preview/GlassPlayground.kt").readText()
        val sample = preview.substringAfter("private fun buildScene()").substringBefore("private fun addCard(")
        val status = preview.substringAfter("private fun updateCaption()").substringBefore("override fun onDetachedFromWindow")
        val quoted = Regex("\"((?:[^\"\\\\]|\\\\.)*)\"")
        for (body in listOf(sample, status)) quoted.findAll(body).forEach { match ->
            val raw = match.groupValues[1]
            // Interpolated composition is checked by its fixed template/parts separately.
            if ('$' in raw || '\n' in raw) return@forEach
            val text = JSONArray("[\"$raw\"]").getString(0)
            if (han.containsMatchIn(text)) assertCovered(text)
        }
    }

    @Test fun placeholdersPreserveArgumentPositionsAndTypesInBothLanguages() {
        val english = resources("values")
        val chinese = resources("values-zh")
        fun signature(text: String) = placeholder.findAll(text).mapIndexed { index, match ->
            (match.groupValues[1].toIntOrNull() ?: index + 1) to match.groupValues[2]
        }.toList()
        english.forEach { (id, template) ->
            val args = signature(template)
            assertEquals(id, signature(chinese.getValue(id)), args)
            if (args.isNotEmpty()) {
                val values = (1..args.maxOf { it.first }).map<Int, Any> { position ->
                    if (args.first { it.first == position }.second == "d") 3 else "sample"
                }.toTypedArray()
                val output = String.format(Locale.ENGLISH, template, *values)
                assertFalse(id, placeholder.containsMatchIn(output))
                String.format(Locale.SIMPLIFIED_CHINESE, chinese.getValue(id), *values)
            }
        }
    }

    @Test fun generatedMotionTitlesUnitsAndCompatibilityMarkersResolveTheBaseTitle() {
        for (phase in listOf("进入", "退出")) for (axis in listOf("X1", "Y1", "X2", "Y2"))
            assertCovered("$phase 曲线 $axis")
        for (phase in listOf("显示", "消失")) for (property in listOf("缩放", "透明度", "模糊", "时长（毫秒）"))
            assertCovered("$phase $property")
        assertEquals("进入时长", UiTextCatalog.canonicalSource("进入时长（毫秒）"))
        assertEquals("音乐卡片材质", UiTextCatalog.canonicalSource("音乐卡片材质（注意兼容）"))
        assertEquals("澄光", UiTextCatalog.canonicalSource("A · 澄光"))
        assertEquals("A · " to "澄光", UiTextCatalog.presetParts("A · 澄光"))
    }

    @Test fun previewNotesResolveDynamicFlagsAndKeepWholeLayerNamingSeparate() {
        for (unified in listOf("开", "关")) for (locked in listOf("开", "关")) {
            val resolved = requireNotNull(UiTextCatalog.resolve("统一材质$unified；锁屏范围$locked（系统切换未模拟）"))
            assertEquals(listOf(unified, locked), resolved.arguments)
        }
        for (enabled in listOf("开", "关"))
            assertEquals(listOf(enabled), UiTextCatalog.resolve("统一侧栏$enabled；系统侧栏配方未还原")?.arguments)
        assertCovered("整屏背景样例")
        assertCovered("通知配方样例")
        assertNotEquals(UiTextCatalog.resourceFor("整屏背景样例"), UiTextCatalog.resourceFor("通知配方样例"))
        assertCovered("还有 %d 条通知")
        assertCovered("%s。%s。仅本地预览。")
    }
}
