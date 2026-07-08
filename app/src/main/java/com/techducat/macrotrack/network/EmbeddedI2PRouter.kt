package com.techducat.macrotrack.network

import android.content.Context
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.*
import java.io.*
import java.net.InetSocketAddress
import java.net.Socket
import java.security.KeyStore
import java.security.cert.X509Certificate
import java.util.Base64
import java.util.Properties
import java.util.concurrent.atomic.AtomicBoolean

/**
 * EmbeddedI2PRouter — ported from buzzr-p2p/network/EmbeddedI2PRouter.kt
 *
 * Starts the bundled I2P router JAR + SAMBridge inside the MacroTrack process.
 * The router lifecycle, SAM startup, CA export, and reseed logic are byte-for-byte
 * identical to buzzr-p2p/verzus-p2p — this is deliberately NOT reinvented, since
 * that's exactly the class of bug (reseed ordering, stale netDb, SAM-timing) that
 * took several passes to get right in those apps the first time.
 *
 * Unlike buzzr/verzus/kabu-kabu/6°Net, MacroTrack has no I2P *peer* — it never
 * accepts inbound I2P connections and never registers a destination anyone else
 * looks up. It only ever originates outbound SAM STREAM connections (see
 * [I2POutproxyTunnel]) to reach a single clearnet REST API (Open Food Facts)
 * anonymously. The router + SAM bridge are still required for that: SAM STREAM
 * CONNECT only works once the embedded router has bootstrapped and the SAM
 * bridge is listening, exactly as in the P2P apps.
 *
 * Singleton per application process — call [getInstance] / [releaseInstance].
 *
 * Required artifacts in app/libs/:
 *   router.jar              (from i2p.i2p `ant pkg`)
 *   sam.jar                 (from i2p.i2p `ant pkg`)
 *   i2p-android-client.aar  (from i2p.android.base `./gradlew assembleRelease`)
 *
 * See app/libs/README.md for the full build + copy-script instructions.
 */
class EmbeddedI2PRouter private constructor(
    private val context: Context,
    private val deviceId: String
) {
    companion object {
        private const val TAG = "EmbeddedI2PRouter"

        // SAM bridge port — single device identity, no per-user offset needed.
        private const val SAM_HOST = "127.0.0.1"
        private const val SAM_PORT = 7656

        private const val I2P_CONFIG_DIR = "i2p/macrotrack"

        private const val SAM_WAIT_TIMEOUT_MS    = 600_000L
        private const val SAM_CHECK_INTERVAL_MS  =   5_000L

        private const val NAMING_IMPL_PROP = "i2p.naming.impl"
        private const val HOSTS_TXT_NAMING = "net.i2p.client.naming.HostsTxtNamingService"

        private const val RESEED_URLS =
            "https://reseed-fr.i2pd.xyz/," +
            "https://reseed-pl.i2pd.xyz/," +
            "https://reseed.memcpy.io/," +
            "https://banana.incognet.io/," +
            "https://netdb.i2p2.no/," +
            "https://i2p.mooo.com/netDb/," +
            "https://reseed.onion.im/," +
            "https://reseed.i2p-projekt.de/"

        @Volatile private var _instance: EmbeddedI2PRouter? = null

        fun getInstance(context: Context, deviceId: String): EmbeddedI2PRouter =
            _instance ?: synchronized(this) {
                _instance ?: EmbeddedI2PRouter(context.applicationContext, deviceId)
                    .also { _instance = it }
            }

        fun releaseInstance() { _instance = null }
    }

    private val isRunning     = AtomicBoolean(false)
    private val isInitialized = AtomicBoolean(false)
    private val samReady      = AtomicBoolean(false)
    private val tunnelReady   = AtomicBoolean(false)

    private var routerInstance:    Any? = null
    private var samBridgeInstance: Any? = null

    // ── Public API ────────────────────────────────────────────────────────────

    suspend fun startRouter() = withContext(Dispatchers.IO) {
        if (isRunning.get()) { Log.i(TAG, "Router already running"); return@withContext }
        try {
            Log.i(TAG, "Starting embedded I2P router…")
            val configDir = initializeConfig()
            startI2PRouter(configDir)
            waitForSAM()
            isRunning.set(true)
            isInitialized.set(true)
            Log.i(TAG, "I2P router + SAM ready")
        } catch (e: Exception) {
            Log.e(TAG, "I2P router startup failed: ${e.message}", e)
            isRunning.set(false)
            throw IOException("I2P router startup failed: ${e.message}", e)
        }
    }

    suspend fun stopRouter() = withContext(Dispatchers.IO) {
        if (!isRunning.get()) return@withContext
        isRunning.set(false); samReady.set(false); tunnelReady.set(false); isInitialized.set(false)
        cleanup()
        Log.i(TAG, "I2P router stopped")
    }

    fun isSAMReady()    = samReady.get()
    fun isTunnelReady() = tunnelReady.get()

    fun getConfigDir() = File(context.filesDir, I2P_CONFIG_DIR)
    fun getDestinationKeyFile() = File(getConfigDir(), "macrotrack_destination.keys")

    suspend fun awaitTunnelsReady(
        timeoutMs: Long = SAM_WAIT_TIMEOUT_MS,
        checkIntervalMs: Long = SAM_CHECK_INTERVAL_MS
    ) = withContext(Dispatchers.IO) {
        if (tunnelReady.get()) return@withContext
        val start = System.currentTimeMillis()
        Log.i(TAG, "Waiting for I2P tunnels (SESSION CREATE probe)…")
        while (System.currentTimeMillis() - start < timeoutMs) {
            if (testTunnelReady()) { tunnelReady.set(true); return@withContext }
            delay(checkIntervalMs)
        }
        Log.w(TAG, "Tunnels did not become ready within ${timeoutMs / 1000}s")
    }

    // ── Config ────────────────────────────────────────────────────────────────

    private fun initializeConfig(): File {
        val configDir = getConfigDir()
        val netDbCount = File(configDir, "netDb")
            .listFiles { f -> f.name.startsWith("routerInfo-") }?.size ?: 0
        val isFirstBoot = !File(configDir, "router.config").exists() || netDbCount < 20

        if (isFirstBoot) {
            if (configDir.exists()) configDir.deleteRecursively()
            configDir.mkdirs()
        }

        listOf("certificates", "peerProfiles", "keyBackup", "netDb").forEach {
            File(configDir, it).mkdirs()
        }

        copyI2PCertsFromAssets(configDir)
        File(configDir, "hosts.txt").apply { if (!exists()) createNewFile() }
        exportAndroidCAs(configDir)
        writeRouterConfig(configDir)
        return configDir
    }

    private fun writeRouterConfig(configDir: File) {
        Properties().apply {
            setProperty("i2np.udp.port", "0")
            setProperty("i2np.ntcp.port", "0")
            setProperty("i2np.ntcp.autoip", "true")
            setProperty("i2np.udp.autoip", "true")
            setProperty("router.sharePercentage", "50")
            setProperty("i2np.bandwidth.inboundKBytesPerSecond", "100")
            setProperty("i2np.bandwidth.outboundKBytesPerSecond", "100")
            setProperty("router.updateDisabled", "true")
            setProperty("eepget.useDNSOverHTTPS", "false")
            setProperty("router.reseedDisable", "false")
            setProperty("logger.defaultLevel", "WARN")
            setProperty(NAMING_IMPL_PROP, HOSTS_TXT_NAMING)
        }.let { FileOutputStream(File(configDir, "router.config")).use { os -> it.store(os, "MacroTrack I2P Router") } }
    }

    private fun copyI2PCertsFromAssets(configDir: File) {
        for (sub in listOf("reseed", "router", "ssl")) {
            val destDir = File(configDir, "certificates/$sub").also { it.mkdirs() }
            try {
                for (fn in (context.assets.list("i2p/certificates/$sub") ?: emptyArray())) {
                    val dest = File(destDir, fn)
                    if (!dest.exists()) {
                        context.assets.open("i2p/certificates/$sub/$fn")
                            .use { inp -> dest.outputStream().use { inp.copyTo(it) } }
                    }
                }
            } catch (_: Exception) {}
        }
    }

    private fun exportAndroidCAs(configDir: File) {
        val sslDir = File(configDir, "certificates/ssl").also { it.mkdirs() }
        if ((sslDir.listFiles()?.size ?: 0) > 10) return
        try {
            val store = KeyStore.getInstance("AndroidCAStore").apply { load(null, null) }
            val aliases = store.aliases()
            while (aliases.hasMoreElements()) {
                val alias = aliases.nextElement()
                try {
                    val cert = store.getCertificate(alias) as? X509Certificate ?: continue
                    val name = alias.replace(Regex("[^a-zA-Z0-9._-]"), "_").take(80)
                    File(sslDir, "$name.pem").bufferedWriter().use { out ->
                        out.write("-----BEGIN CERTIFICATE-----\n")
                        Base64.getEncoder().encodeToString(cert.encoded).chunked(64)
                            .forEach { out.write(it); out.write("\n") }
                        out.write("-----END CERTIFICATE-----\n")
                    }
                } catch (_: Exception) {}
            }
        } catch (e: Exception) {
            Log.e(TAG, "AndroidCAStore export failed: ${e.message}")
        }
    }

    // ── Router startup ────────────────────────────────────────────────────────

    private fun startI2PRouter(configDir: File) {
        System.setProperty(NAMING_IMPL_PROP, HOSTS_TXT_NAMING)
        System.setProperty("i2p.dir.base",   configDir.absolutePath)
        System.setProperty("i2p.dir.config", configDir.absolutePath)
        System.setProperty("i2p.disableSSLHostnameVerification", "true")
        System.setProperty("i2p.reseedURL",  RESEED_URLS)

        try {
            val routerClass = Class.forName("net.i2p.router.Router")
            val router      = createRouter(routerClass, configDir)
            routerInstance  = router

            try {
                routerClass.getMethod("setKillVMOnEnd", Boolean::class.javaPrimitiveType)
                    .invoke(router, false)
            } catch (_: NoSuchMethodException) {}

            val isAliveMethod   = routerClass.getMethod("isAlive")
            val isRunningMethod = try { routerClass.getMethod("isRunning") } catch (_: Exception) { null }

            Thread({
                try { routerClass.getMethod("runRouter").invoke(router) }
                catch (e: Exception) { Log.e(TAG, "runRouter() threw: ${e.message}"); isRunning.set(false) }
            }, "I2P-Router").apply { isDaemon = true; start() }

            var n = 0
            while (!(isAliveMethod.invoke(router) as Boolean) && n++ < 60) Thread.sleep(1000)
            if (!(isAliveMethod.invoke(router) as Boolean)) throw IOException("Router did not become alive within 60s")

            if (isRunningMethod != null) {
                n = 0
                while (!(isRunningMethod.invoke(router) as Boolean) && n++ < 300) Thread.sleep(1000)
            }

            startSAM(routerClass, isRunningMethod, router, isRunningMethod?.invoke(router) as? Boolean ?: true)
        } catch (e: ClassNotFoundException) {
            throw IOException("I2P Router classes not found — check router.jar in app/libs", e)
        }
    }

    private fun startSAM(routerClass: Class<*>, isRunningMethod: java.lang.reflect.Method?, router: Any, routerReady: Boolean) {
        val isAliveMethod = routerClass.getMethod("isAlive")
        Thread({
            if (routerReady) {
                var n = 0
                while (true) {
                    if (!(isAliveMethod.invoke(router) as Boolean)) { Log.e(TAG, "Router stopped before SAM"); return@Thread }
                    if (isRunningMethod?.invoke(router) as? Boolean != false) break
                    if (n++ % 10 == 0) Log.d(TAG, "SAM waiting for isRunning(): ${n}s")
                    Thread.sleep(1000)
                }
            }

            // Check if SAM port already bound.
            if (try { Socket().use { it.connect(InetSocketAddress(SAM_HOST, SAM_PORT), 2000); true } } catch (_: Exception) { false }) {
                Log.i(TAG, "SAM port $SAM_PORT already in use — reusing")
                samReady.set(true); return@Thread
            }

            try {
                try { Looper.prepare() } catch (_: RuntimeException) {}

                val samClass     = Class.forName("net.i2p.sam.SAMBridge")
                val configDir    = getConfigDir()
                val samKeysFile  = File(configDir, "sam.keys").absolutePath
                val samConfigDir = File(configDir, "sam_config").also { it.mkdirs() }
                val samProps     = Properties()

                val bridge = try {
                    samClass.getConstructor(String::class.java, Int::class.javaPrimitiveType,
                        Boolean::class.javaPrimitiveType, Properties::class.java,
                        String::class.java, File::class.java,
                        Class.forName("net.i2p.sam.SAMSecureSessionInterface"))
                        .newInstance(SAM_HOST, SAM_PORT, false, samProps, samKeysFile, samConfigDir, null)
                } catch (_: NoSuchMethodException) {
                    try {
                        samClass.getConstructor(String::class.java, Int::class.javaPrimitiveType, Properties::class.java)
                            .newInstance(SAM_HOST, SAM_PORT, samProps)
                    } catch (_: NoSuchMethodException) {
                        samClass.getConstructor().newInstance()
                    }
                }

                samBridgeInstance = bridge
                samClass.getMethod("run").invoke(bridge)
            } catch (e: Exception) {
                Log.e(TAG, "SAMBridge failed: ${e.message}", e)
            }
        }, "I2P-SAM").apply { isDaemon = true; start() }
    }

    private fun createRouter(routerClass: Class<*>, configDir: File): Any {
        val path  = File(configDir, "router.config").absolutePath
        val props = Properties().apply {
            setProperty("i2p.dir.base",   configDir.absolutePath)
            setProperty("i2p.dir.config", configDir.absolutePath)
            setProperty(NAMING_IMPL_PROP, HOSTS_TXT_NAMING)
        }
        return try {
            routerClass.getConstructor(String::class.java, Properties::class.java).newInstance(path, props)
        } catch (_: NoSuchMethodException) {
            try { routerClass.getConstructor(String::class.java).newInstance(path) }
            catch (_: NoSuchMethodException) { routerClass.getConstructor().newInstance() }
        }
    }

    // ── SAM readiness ─────────────────────────────────────────────────────────

    private suspend fun waitForSAM() = withContext(Dispatchers.IO) {
        val start = System.currentTimeMillis()
        while (System.currentTimeMillis() - start < SAM_WAIT_TIMEOUT_MS) {
            if (testSAMHello()) { samReady.set(true); return@withContext }
            delay(SAM_CHECK_INTERVAL_MS)
        }
        throw IOException("SAM bridge did not respond within ${SAM_WAIT_TIMEOUT_MS / 1000}s")
    }

    private fun testSAMHello(): Boolean = try {
        Socket().use { s ->
            s.connect(InetSocketAddress(SAM_HOST, SAM_PORT), 5000)
            val w = PrintWriter(s.getOutputStream(), true)
            val r = BufferedReader(InputStreamReader(s.getInputStream()))
            w.println("HELLO VERSION MIN=3.1 MAX=3.1")
            r.readLine()?.startsWith("HELLO REPLY RESULT=OK") == true
        }
    } catch (_: Exception) { false }

    private fun testTunnelReady(): Boolean = try {
        Socket().use { s ->
            s.connect(InetSocketAddress(SAM_HOST, SAM_PORT), 5000)
            s.soTimeout = 90_000
            val w = PrintWriter(s.getOutputStream(), true)
            val r = BufferedReader(InputStreamReader(s.getInputStream()))
            w.println("HELLO VERSION MIN=3.1 MAX=3.1")
            if (r.readLine()?.startsWith("HELLO REPLY RESULT=OK") != true) return false
            val id = "macrotrack_probe_${System.currentTimeMillis()}"
            w.println("SESSION CREATE STYLE=STREAM ID=$id DESTINATION=TRANSIENT SIGNATURE_TYPE=EdDSA_SHA512_Ed25519")
            r.readLine()?.startsWith("SESSION STATUS RESULT=OK") == true
        }
    } catch (_: Exception) { false }

    // ── Cleanup ───────────────────────────────────────────────────────────────

    private fun cleanup() {
        samBridgeInstance?.let { bridge ->
            try {
                val cls = Class.forName("net.i2p.sam.SAMBridge")
                (try { cls.getMethod("shutdown") } catch (_: Exception) {
                    try { cls.getMethod("stopRunning") } catch (_: Exception) { null }
                })?.invoke(bridge)
            } catch (_: Exception) {}
            samBridgeInstance = null
        }
        routerInstance?.let { router ->
            try {
                Class.forName("net.i2p.router.Router")
                    .getMethod("shutdown", Int::class.javaPrimitiveType)
                    .invoke(router, 0)
            } catch (_: Exception) {}
            routerInstance = null
        }
    }
}
