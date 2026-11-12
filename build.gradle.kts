// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 btm_m

buildscript {
    dependencies { classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.20") }
}

plugins {
    id("com.android.application") version "9.2.1" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.20" apply false
}
