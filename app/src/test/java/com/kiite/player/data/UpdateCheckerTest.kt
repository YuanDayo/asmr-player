package com.kiite.player.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateCheckerTest {
    @Test fun newerPatch() = assertTrue(UpdateChecker.isNewer("1.5.3", "1.5.2"))
    @Test fun olderIsNotNewer() = assertFalse(UpdateChecker.isNewer("1.5.1", "1.5.2"))
    @Test fun sameIsNotNewer() = assertFalse(UpdateChecker.isNewer("1.5.2", "1.5.2"))
    @Test fun minorBeatsPatch() = assertTrue(UpdateChecker.isNewer("1.6", "1.5.9"))
    @Test fun majorBeatsMinor() = assertTrue(UpdateChecker.isNewer("2.0.0", "1.9.9"))
    @Test fun shorterVersionPadsWithZero() = assertFalse(UpdateChecker.isNewer("1.5", "1.5.0"))
}
