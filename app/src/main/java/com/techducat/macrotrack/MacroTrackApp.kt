package com.techducat.macrotrack

import android.app.Application
import com.techducat.macrotrack.data.repository.FoodRepository
import com.techducat.macrotrack.network.EmbeddedI2PRouter
import com.techducat.macrotrack.network.I2POutproxyTunnel
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/**
 * MacroTrackApp
 *
 * Bootstraps the embedded I2P router + outproxy tunnel on process start when
 * BuildConfig.I2P_OUTPROXY_ENABLED (i.e. the I2P jars in app/libs/ were
 * present at build time -- see app/libs/README.md). If disabled, Open Food
 * Facts calls just go direct (see di/NetworkModule.kt), so the app still
 * works without the I2P dependency, just without that privacy layer.
 *
 * Bootstrap order matters here, same as buzzr/verzus:
 *   1. Start the embedded router + wait for the SAM bridge to answer HELLO.
 *   2. Wait for tunnels to actually be usable (SESSION CREATE probe), not
 *      just for SAM to be listening -- a router can accept SAM connections
 *      before it has enough peers/netDb entries to build a working tunnel.
 *   3. Start the local outproxy proxy listener.
 *   4. Only then mark the tunnel ready, unblocking any OkHttp calls that
 *      were parked in I2PReadinessInterceptor.
 */
@HiltAndroidApp
class MacroTrackApp : Application() {

    @Inject lateinit var i2pRouter: EmbeddedI2PRouter
    @Inject lateinit var outproxyTunnel: I2POutproxyTunnel
    @Inject lateinit var foodRepository: FoodRepository

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }

        if (BuildConfig.I2P_OUTPROXY_ENABLED) {
            bootstrapI2P()
        } else {
            Timber.w("I2P outproxy disabled at build time (missing app/libs/*.jar) -- OFF calls will go direct.")
        }

        pruneStaleFoodCache()
    }

    /**
     * Opportunistically trims Open Food Facts cache rows older than 90 days on every
     * cold start. Independent of I2P bootstrap -- the `foods` table is a plain local
     * cache (see FoodEntity's kdoc) so this has nothing to do with network readiness.
     */
    private fun pruneStaleFoodCache() {
        appScope.launch {
            try {
                foodRepository.pruneStaleCache(TimeUnit.DAYS.toMillis(90))
            } catch (e: Exception) {
                Timber.w(e, "Stale food cache prune failed (non-fatal)")
            }
        }
    }

    private fun bootstrapI2P() {
        appScope.launch {
            try {
                Timber.i("Starting embedded I2P router...")
                i2pRouter.startRouter()
                i2pRouter.awaitTunnelsReady()
                outproxyTunnel.start(I2POutproxyTunnel.DEFAULT_LOCAL_PROXY_PORT)
                outproxyTunnel.markReady()
                Timber.i("I2P outproxy tunnel ready on 127.0.0.1:${I2POutproxyTunnel.DEFAULT_LOCAL_PROXY_PORT}")
            } catch (e: Exception) {
                Timber.e(e, "I2P bootstrap failed -- OFF lookups will fail until this succeeds or the app restarts")
            }
        }
    }
}
