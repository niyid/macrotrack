package com.techducat.macrotrack.network

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * I2POutproxyTunnel
 *
 * MacroTrack's only outbound network dependency is Open Food Facts — a single
 * public, unauthenticated clearnet REST API. Unlike buzzr-p2p/verzus-p2p/
 * kabu-kabu-p2p/6°Net, which each open SAM STREAM sessions *between two I2P
 * destinations they both control* (a peer-to-peer messenger channel), there is
 * no existing pattern in any of those apps for reaching a fixed clearnet
 * endpoint through I2P — none of them need to, because they never talk to
 * anything outside the I2P network. This class is new code, not a port.
 *
 * What it does: runs a local plain-HTTP forward-proxy server on
 * 127.0.0.1:[DEFAULT_LOCAL_PROXY_PORT]. OkHttp is configured (see
 * di/NetworkModule.kt) with `Proxy(Proxy.Type.HTTP, ...)` pointing at that
 * address, so every OFF request/response is just a normal HTTP proxy
 * conversation from OkHttp's point of view:
 *   - Plain HTTP requests arrive here as absolute-form request lines
 *     ("GET http://host/path HTTP/1.1").
 *   - HTTPS requests arrive as "CONNECT host:port HTTP/1.1"; once we relay
 *     back "200 Connection Established", the raw TLS bytes for that
 *     connection flow through unmodified.
 *
 * For each accepted local connection, this class opens ONE SAM v3 session
 * (control socket: HELLO + SESSION CREATE STYLE=STREAM DESTINATION=TRANSIENT)
 * plus one SAM data socket (HELLO + STREAM CONNECT to the outproxy
 * destination), then simply relays raw bytes bidirectionally between the
 * local client socket and the SAM data socket. This class never parses the
 * HTTP inside the tunnel — the outproxy (a Squid instance on the far end of
 * the I2P destination) is the one that understands absolute-form GETs and
 * CONNECT tunneling; here we are purely a byte pipe.
 *
 * NOTE ON THE SAM PROTOCOL SEQUENCE: this two-socket-per-connection flow
 * (control socket creates+holds the STREAM session, a second socket issues
 * STREAM CONNECT against that session ID and becomes the data channel) is the
 * documented SAM v3 shape, but none of techducat's existing apps exercise a
 * STREAM CONNECT to an external destination — they only ever probe
 * SESSION CREATE (see EmbeddedI2PRouter.testTunnelReady) or talk to the local
 * Messenger Service. Treat this as unverified against real router logs until
 * it's been exercised against a live SAM bridge.
 *
 * Known public outproxy destinations (volunteer-run; StormyCloud additionally
 * relays through Tor to reach the clearnet — see EmbeddedI2PRouter kdoc for
 * the privacy/latency tradeoffs this implies):
 *   - exit.stormycloud.i2p — I2P's official default outproxy since Aug 2022.
 *     Used here by its b32 form (see [DEFAULT_OUTPROXY]) rather than the
 *     friendly hostname: SAM's DESTINATION resolves friendly ".i2p" names via
 *     the local hosts.txt addressbook, which EmbeddedI2PRouter only ever
 *     creates empty (no subscription/jump-service fetch is wired up). A
 *     ".b32.i2p" address is self-certifying — its hash *is* the destination
 *     — so it resolves directly via netDb lookup with no addressbook entry
 *     needed. (The old default here, false.i2p, is additionally long dead —
 *     degrading for years before StormyCloud replaced it as the project's
 *     default; see https://geti2p.net/en/blog/post/2022/8/4/Enable-StormyCloud.)
 */
class I2POutproxyTunnel(
    private val samHost: String = "127.0.0.1",
    private val samPort: Int = 7656,
    private val outproxyDestination: String = DEFAULT_OUTPROXY
) {
    companion object {
        private const val TAG = "I2POutproxyTunnel"

        // exit.stormycloud.i2p's b32 address (verified against StormyCloud's own
        // docs: https://www.stormycloud.org/updating-i2p-outproxy/). Volunteer
        // infrastructure can be retired or rotated without notice — if this starts
        // failing, check that page for a current address before assuming a bug
        // elsewhere in the tunnel/SAM code.
        const val DEFAULT_OUTPROXY = "5d4s7pcvfdpftfk7npc7hllyujhufsdprtrf4o53i44rgsa2xbwa.b32.i2p"
        const val DEFAULT_LOCAL_PROXY_PORT = 8118

        private const val SAM_SOCKET_TIMEOUT_MS = 90_000
        private const val RELAY_BUFFER_SIZE = 8192
        const val READINESS_TIMEOUT_MS = 90_000L
    }

    @Volatile private var serverSocket: ServerSocket? = null
    @Volatile private var acceptJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val running = AtomicBoolean(false)
    private val connectionCounter = AtomicInteger(0)
    private val readyLatch = CountDownLatch(1)

    val localPort: Int get() = serverSocket?.localPort ?: -1

    /**
     * Starts the local proxy listener. This does NOT itself gate on I2P
     * readiness — the caller (MacroTrackApp's bootstrap coroutine) is expected
     * to call this only after [EmbeddedI2PRouter.awaitTunnelsReady] returns,
     * then call [markReady] once satisfied the tunnel should accept traffic.
     */
    suspend fun start(port: Int = DEFAULT_LOCAL_PROXY_PORT) = withContext(Dispatchers.IO) {
        if (running.get()) return@withContext
        val ss = ServerSocket()
        ss.reuseAddress = true
        ss.bind(InetSocketAddress("127.0.0.1", port))
        serverSocket = ss
        running.set(true)
        Log.i(TAG, "Outproxy tunnel listening on 127.0.0.1:${ss.localPort} -> $outproxyDestination")

        acceptJob = scope.launch {
            while (running.get() && !ss.isClosed) {
                try {
                    val client = ss.accept()
                    val connId = connectionCounter.incrementAndGet()
                    launch { handleClient(client, connId) }
                } catch (e: IOException) {
                    if (running.get()) Log.w(TAG, "Accept loop error: ${e.message}")
                }
            }
        }
    }

    fun stop() {
        running.set(false)
        acceptJob?.cancel()
        try { serverSocket?.close() } catch (_: Exception) {}
        serverSocket = null
    }

    /** Called by app bootstrap once the router + tunnel are considered ready for traffic. */
    fun markReady() = readyLatch.countDown()

    fun isReady(): Boolean = readyLatch.count == 0L

    /** Blocks the calling (OkHttp dispatcher) thread until ready or timeout. */
    fun awaitReady(timeoutMs: Long = READINESS_TIMEOUT_MS): Boolean =
        readyLatch.await(timeoutMs, TimeUnit.MILLISECONDS)

    // ── Connection handling ──────────────────────────────────────────────────

    private suspend fun handleClient(client: Socket, connId: Int) {
        var controlSocket: Socket? = null
        var dataSocket: Socket? = null
        try {
            client.soTimeout = 0
            val session = openSamStreamToOutproxy()
            if (session == null) {
                Log.w(TAG, "#$connId: SAM CONNECT to $outproxyDestination failed")
                client.close()
                return
            }
            controlSocket = session.first
            dataSocket = session.second
            relayBidirectional(client, dataSocket, connId)
        } catch (e: Exception) {
            Log.w(TAG, "#$connId: relay failed: ${e.message}")
        } finally {
            try { client.close() } catch (_: Exception) {}
            try { dataSocket?.close() } catch (_: Exception) {}
            try { controlSocket?.close() } catch (_: Exception) {}
        }
    }

    /**
     * Opens a SAM v3 STREAM session against [outproxyDestination] and returns
     * (controlSocket, dataSocket) once "STREAM STATUS RESULT=OK" has been
     * received on the data socket — i.e. dataSocket is positioned to relay raw
     * bytes immediately. Returns null on any protocol failure.
     */
    private fun openSamStreamToOutproxy(): Pair<Socket, Socket>? {
        var control: Socket? = null
        var data: Socket? = null
        try {
            control = Socket().apply {
                connect(InetSocketAddress(samHost, samPort), SAM_SOCKET_TIMEOUT_MS)
                soTimeout = SAM_SOCKET_TIMEOUT_MS
            }
            val controlOut = control.getOutputStream()
            val controlIn = control.getInputStream().bufferedReader()

            fun sendControl(line: String) {
                controlOut.write((line + "\n").toByteArray()); controlOut.flush()
            }

            sendControl("HELLO VERSION MIN=3.1 MAX=3.1")
            val hello = controlIn.readLine()
            if (hello == null || !hello.startsWith("HELLO REPLY RESULT=OK")) {
                Log.w(TAG, "SAM HELLO (control) failed: $hello")
                control.close(); return null
            }

            val sessionId = "mt_off_${System.nanoTime()}"
            sendControl(
                "SESSION CREATE STYLE=STREAM ID=$sessionId DESTINATION=TRANSIENT " +
                    "SIGNATURE_TYPE=EdDSA_SHA512_Ed25519"
            )
            val sessionReply = controlIn.readLine()
            if (sessionReply == null || !sessionReply.startsWith("SESSION STATUS RESULT=OK")) {
                Log.w(TAG, "SAM SESSION CREATE failed: $sessionReply")
                control.close(); return null
            }

            data = Socket().apply {
                connect(InetSocketAddress(samHost, samPort), SAM_SOCKET_TIMEOUT_MS)
                soTimeout = SAM_SOCKET_TIMEOUT_MS
            }
            val dataOut = data.getOutputStream()
            val dataIn = data.getInputStream().bufferedReader()

            fun sendData(line: String) {
                dataOut.write((line + "\n").toByteArray()); dataOut.flush()
            }

            sendData("HELLO VERSION MIN=3.1 MAX=3.1")
            val dataHello = dataIn.readLine()
            if (dataHello == null || !dataHello.startsWith("HELLO REPLY RESULT=OK")) {
                Log.w(TAG, "SAM HELLO (data) failed: $dataHello")
                control.close(); data.close(); return null
            }

            sendData("STREAM CONNECT ID=$sessionId DESTINATION=$outproxyDestination SILENT=false")
            val connectReply = dataIn.readLine()
            if (connectReply == null || !connectReply.startsWith("STREAM STATUS RESULT=OK")) {
                Log.w(TAG, "SAM STREAM CONNECT to $outproxyDestination failed: $connectReply")
                control.close(); data.close(); return null
            }

            // Data socket's soTimeout was only for the handshake; clear it now
            // so long-lived idle relaying isn't cut off mid-request.
            data.soTimeout = 0
            return control to data
        } catch (e: Exception) {
            Log.w(TAG, "openSamStreamToOutproxy error: ${e.message}")
            try { data?.close() } catch (_: Exception) {}
            try { control?.close() } catch (_: Exception) {}
            return null
        }
    }

    private suspend fun relayBidirectional(client: Socket, sam: Socket, connId: Int) = coroutineScope {
        val toSam = launch(Dispatchers.IO) { pipe(client, sam) }
        val toClient = launch(Dispatchers.IO) { pipe(sam, client) }
        toSam.join()
        toClient.join()
        Log.d(TAG, "#$connId: relay finished")
    }

    private fun pipe(from: Socket, to: Socket) {
        try {
            val input = from.getInputStream()
            val output = to.getOutputStream()
            val buffer = ByteArray(RELAY_BUFFER_SIZE)
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                output.write(buffer, 0, n)
                output.flush()
            }
        } catch (_: IOException) {
            // Normal on peer close/reset.
        } finally {
            try { to.shutdownOutput() } catch (_: Exception) {}
        }
    }
}

/**
 * Blocks the OkHttp dispatcher thread (never the main thread — OkHttp already
 * runs interceptors off the UI thread) until the outproxy tunnel is ready,
 * or fails fast with an IOException so the caller sees a normal network
 * error ("connecting to network…") rather than hanging indefinitely.
 */
class I2PReadinessInterceptor(private val tunnel: I2POutproxyTunnel) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        if (!tunnel.isReady() && !tunnel.awaitReady(I2POutproxyTunnel.READINESS_TIMEOUT_MS)) {
            throw IOException("I2P outproxy tunnel not ready — network still bootstrapping")
        }
        return chain.proceed(chain.request())
    }
}
