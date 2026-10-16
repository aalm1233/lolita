package com.lolita.app.data.remote

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.ServerSocket
import java.net.URL
import java.util.concurrent.Executors

/** Real loopback HTTP, no official endpoint, account, or paid request. */
class ImageTransportFailureTest {
    @Test fun droppedResponseAndReadTimeoutRemainDistinguishableWithoutRetry() = runBlocking {
        for (seedream in listOf(false, true)) for (timeout in listOf(false, true)) {
            ServerSocket(0, 1, InetAddress.getLoopbackAddress()).use { server ->
                val worker = Executors.newSingleThreadExecutor()
                val received = worker.submit<Int> {
                    server.accept().use { socket ->
                        // Consume a full chunked request before simulating the response loss.
                        var tail = ""
                        var count = 0
                        while (!tail.endsWith("\r\n0\r\n\r\n")) {
                            val byte = socket.getInputStream().read()
                            if (byte < 0) break
                            count++
                            tail = (tail + byte.toChar()).takeLast(7)
                        }
                        if (timeout) Thread.sleep(500) else socket.setSoLinger(true, 0)
                        count
                    }
                }
                var calls = 0
                val factory: (URL) -> HttpURLConnection = {
                    calls++
                    object : HttpURLConnection(URL("http://127.0.0.1:${server.localPort}/generate")) {
                        val actual = url.openConnection() as HttpURLConnection
                        override fun connect() = actual.connect()
                        override fun disconnect() = actual.disconnect()
                        override fun usingProxy() = false
                        override fun getOutputStream(): java.io.OutputStream {
                            actual.requestMethod = requestMethod; actual.doOutput = true
                            actual.setChunkedStreamingMode(1024); actual.readTimeout = 120
                            actual.instanceFollowRedirects = false
                            return actual.outputStream
                        }
                        override fun getResponseCode() = actual.responseCode
                    }
                }
                try {
                    val failure = runCatching {
                        if (seedream) SeedreamImageApi(factory).generate("controlled-key", listOf(byteArrayOf(1), byteArrayOf(2)), "p")
                        else TryOnImageApi(factory).generate("controlled-key", listOf(byteArrayOf(1), byteArrayOf(2)), "p")
                    }.exceptionOrNull()!!
                    assertTrue(received.get() > 100)
                    assertEquals(1, calls)
                    assertEquals("ImageGenerationFailure", failure.javaClass.simpleName)
                    assertTrue(failure.message.orEmpty().contains(if (timeout) "超时" else "网络"))
                    assertFalse(failure.toString().contains("controlled-key"))
                } finally { worker.shutdownNow() }
            }
        }
    }
}
