package com.zhuhuibin.hyperchange.lite

import org.junit.Assert.*
import org.junit.Test

class SettingsCodecTest {
    private val specs = listOf(
        LiteSetting.toggle("on", "On", "Test"),
        LiteSetting.number("radius", "Radius", "Test", 12f, 0f, 100f, .5f),
    )

    @Test fun roundTripPreservesExplicitDisabledAndZero() {
        val input = mapOf("on" to false, "radius" to 0f)
        assertEquals(input, SettingsCodec.decode(specs, SettingsCodec.encode(specs, input)))
    }

    @Test fun invalidPresetIsRejectedBeforeReturningAnyMutation() {
        val invalid = """{"format":"hyperchange-lite","version":1,"settings":{"on":true,"radius":1000}}"""
        assertThrows(IllegalArgumentException::class.java) { SettingsCodec.decode(specs, invalid) }
    }

    @Test fun partialImportKeepsOnlyValidatedKnownKeys() {
        val input = """{"format":"hyperchange-lite","version":1,"settings":{"radius":12.25,"old_feature":true}}"""
        val decoded = SettingsCodec.decode(specs, input)
        assertEquals(setOf("radius"), decoded.keys)
        assertEquals(12.5f, decoded["radius"])
    }

    @Test fun wrongFormatsAndNonFiniteValuesAreRejected() {
        assertThrows(IllegalArgumentException::class.java) { SettingsCodec.decode(specs, """{"format":"hyperlight","version":1,"settings":{}}""") }
        assertThrows(IllegalArgumentException::class.java) { specs[1].validate(Float.NaN) }
        assertThrows(IllegalArgumentException::class.java) { specs[0].validate(1) }
    }
}
