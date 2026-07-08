package com.techducat.macrotrack.di

import android.content.Context
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import com.techducat.macrotrack.BuildConfig
import com.techducat.macrotrack.data.remote.OpenFoodFactsApi
import com.techducat.macrotrack.network.EmbeddedI2PRouter
import com.techducat.macrotrack.network.I2POutproxyTunnel
import com.techducat.macrotrack.network.I2PReadinessInterceptor
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

/**
 * NetworkModule — I2P-routed
 *
 * MacroTrack's only outbound dependency (Open Food Facts) is routed through a
 * local HTTP forward proxy ([I2POutproxyTunnel]) that tunnels every request
 * over the embedded I2P router ([EmbeddedI2PRouter]) to a known I2P clearnet
 * outproxy, rather than hitting world.openfoodfacts.org directly. See both
 * classes' kdoc for the full reasoning and the accepted tradeoffs (the
 * outproxy itself relays through Tor -- extra latency in exchange for not
 * exposing the device's real IP to OFF or a network observer on every
 * barcode scan / search query).
 *
 * Bootstrapping (starting the router, waiting for tunnels, starting the local
 * proxy listener, calling I2POutproxyTunnel.markReady()) happens in
 * MacroTrackApp.onCreate(), not here -- Hilt modules should stay side-effect
 * free at provision time. I2PReadinessInterceptor is what makes OkHttp calls
 * block (off the main thread) until that bootstrap finishes, rather than
 * failing immediately on app cold start.
 *
 * If BuildConfig.I2P_OUTPROXY_ENABLED is false -- e.g. the JARs under
 * app/libs are missing at build time, see app/libs/README.md -- this falls back to a plain
 * direct OkHttpClient so the app still builds and runs, just without the
 * outproxy.
 */
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideMoshi(): Moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    @Provides
    @Singleton
    fun provideEmbeddedI2PRouter(@ApplicationContext context: Context): EmbeddedI2PRouter =
        EmbeddedI2PRouter.getInstance(context, deviceId = "macrotrack")

    @Provides
    @Singleton
    fun provideI2POutproxyTunnel(): I2POutproxyTunnel = I2POutproxyTunnel()

    @Provides
    @Singleton
    fun provideOkHttpClient(tunnel: I2POutproxyTunnel): OkHttpClient {
        // I2P-routed requests still go over loopback to reach the local proxy
        // instantly, but the actual round trip from there — several I2P garlic
        // hops, then the outproxy's own Tor exit hop to reach OFF's clearnet
        // servers — is much slower than a direct connection. Direct-fallback
        // timeouts (when the I2P libs aren't present) stay tight since that path
        // really is just one plain HTTPS hop.
        val connectTimeoutSec = if (BuildConfig.I2P_OUTPROXY_ENABLED) 60L else 15L
        val readTimeoutSec = if (BuildConfig.I2P_OUTPROXY_ENABLED) 120L else 30L

        val builder = OkHttpClient.Builder()
            .connectTimeout(connectTimeoutSec, TimeUnit.SECONDS)
            .readTimeout(readTimeoutSec, TimeUnit.SECONDS)

        if (BuildConfig.I2P_OUTPROXY_ENABLED) {
            builder
                .proxy(Proxy(Proxy.Type.HTTP, InetSocketAddress("127.0.0.1", I2POutproxyTunnel.DEFAULT_LOCAL_PROXY_PORT)))
                .addInterceptor(I2PReadinessInterceptor(tunnel))
        }

        if (BuildConfig.DEBUG) {
            builder.addInterceptor(
                HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC }
            )
        }
        return builder.build()
    }

    @Provides
    @Singleton
    fun provideOpenFoodFactsApi(client: OkHttpClient, moshi: Moshi): OpenFoodFactsApi =
        Retrofit.Builder()
            .baseUrl(BuildConfig.OFF_BASE_URL)
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(OpenFoodFactsApi::class.java)
}
