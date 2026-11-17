package com.kiite.player.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SemVerTest {
    @Test fun stableBeatsSameCorePrerelease() {
        assertTrue(SemVer.compare("2.5.2", "2.5.2-beta1") > 0)
        assertTrue(SemVer.compare("2.5.2-beta1", "2.5.2") < 0)
    }

    @Test fun betaSequence() {
        assertTrue(SemVer.compare("2.5.2-beta2", "2.5.2-beta1") > 0)
        assertTrue(SemVer.compare("2.5.2-beta1", "2.5.2-beta10") < 0)
    }

    @Test fun rcAfterBeta() {
        assertTrue(SemVer.compare("2.5.2-rc1", "2.5.2-beta9") > 0)
    }

    @Test fun numericPrereleaseParts() {
        assertTrue(SemVer.compare("2.5.2-beta.10", "2.5.2-beta.2") > 0)
        // 数字标识符小于字母标识符
        assertTrue(SemVer.compare("2.5.2-1", "2.5.2-alpha") < 0)
        // 段数少的更小
        assertTrue(SemVer.compare("2.5.2-beta", "2.5.2-beta.1") < 0)
    }

    @Test fun coreCompare() {
        assertTrue(SemVer.compare("2.5.3-beta1", "2.5.2") > 0)
        assertTrue(SemVer.compare("2.6.0", "2.5.9") > 0)
        assertTrue(SemVer.compare("3.0.0", "2.99.99") > 0)
    }

    @Test fun vPrefixAndEqual() {
        assertEquals(0, SemVer.compare("2.5.2-beta1", "v2.5.2-beta1"))
        assertEquals(0, SemVer.compare("v2.5.2", "2.5.2"))
    }

    @Test fun isPrerelease() {
        assertTrue(SemVer.isPrerelease("2.5.2-beta1"))
        assertTrue(SemVer.isPrerelease("2.5.2-rc2"))
        assertFalse(SemVer.isPrerelease("2.5.2"))
        // 历史写法也要认出来
        assertTrue(SemVer.isPrerelease("2.0beta1"))
    }

    @Test fun legacyVersionsFallBackToLenientCompare() {
        assertTrue(SemVer.compare("1.6", "1.5.9") > 0)
        assertEquals(0, SemVer.compare("1.5", "1.5.0"))
        assertTrue(SemVer.compare("2.6", "2.5.2-beta1") > 0)
    }

    @Test fun parseStrict() {
        val v = SemVer.parse("v2.5.2-beta1")
        assertEquals(listOf(2, 5, 2), v?.core)
        assertEquals(listOf("beta1"), v?.prerelease)
        assertNull(SemVer.parse("2.5.2.1"))
        assertNull(SemVer.parse(""))
    }
}
