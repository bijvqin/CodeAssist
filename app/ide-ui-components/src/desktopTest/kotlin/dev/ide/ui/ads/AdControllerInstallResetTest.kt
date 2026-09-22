package dev.ide.ui.ads

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.ide.ui.StubBackend
import dev.ide.ui.backend.AdHost
import dev.ide.ui.backend.AdPlacement
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** A backend whose app-global preferences live in a map, so a test can outlive several controllers. */
private class PrefBackend : StubBackend() {
    val prefs = mutableMapOf<String, String>()
    override fun preference(key: String): String? = prefs[key]
    override fun setPreference(key: String, value: String) { prefs[key] = value }
}

/** An ads-capable host reporting a caller-chosen install identity. */
private class StampedAdHost(override val installStamp: String?) : AdHost {
    override val available: Boolean = true

    @Composable
    override fun NativeAd(placement: AdPlacement, modifier: Modifier) = Unit
}

/**
 * AD-FREE FORK: ads default to off and an install or update never turns them back on — the inverse of
 * upstream, which resets the choice to "on" once per [AdHost.installStamp] (see [AdController]). An explicit
 * opt-in through the Settings toggle is still honoured and still survives updates.
 */
class AdControllerInstallResetTest {

    @Test
    fun adsAreOffOnAFreshInstall() {
        val backend = PrefBackend()

        assertFalse(AdController(backend, StampedAdHost("build-1")).adsEnabled)
        // The stamp bookkeeping only existed to drive the reset, so nothing is written any more.
        assertFalse(backend.prefs.containsKey(ADS_ENABLED_STAMP_PREF))
    }

    @Test
    fun anUpdateDoesNotTurnAdsBackOn() {
        val backend = PrefBackend()
        AdController(backend, StampedAdHost("build-1")).updateAdsEnabled(false)

        assertFalse(AdController(backend, StampedAdHost("build-2")).adsEnabled, "an update must not re-enable ads")
        assertFalse(AdController(backend, StampedAdHost("build-3")).adsEnabled)
    }

    @Test
    fun anExplicitOptInSurvivesRelaunchesAndUpdates() {
        val backend = PrefBackend()
        AdController(backend, StampedAdHost("build-1")).updateAdsEnabled(true)

        repeat(3) { assertTrue(AdController(backend, StampedAdHost("build-1")).adsEnabled) }
        assertTrue(AdController(backend, StampedAdHost("build-2")).adsEnabled, "opting in must stick across updates")
    }

    @Test
    fun aHostWithoutAnInstallIdentityStaysOff() {
        val backend = PrefBackend()

        assertFalse(AdController(backend, StampedAdHost(null)).adsEnabled)
    }
}
