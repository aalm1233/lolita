package com.lolita.app.data

import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.net.HttpURLConnection
import java.net.Socket
import java.net.URL

/** Test-only raw HTTP over a real local socket. Avoids changing the app's HTTPS policy. */
internal class ControlledImageHttpConnection(private val host: String, private val port: Int) : HttpURLConnection(URL("http://$host:$port/controlled")) {
    private val uploaded = ByteArrayOutputStream()
    @Volatile private var socket: Socket? = null
    override fun connect() = Unit
    override fun usingProxy() = false
    override fun disconnect() { socket?.close() }
    override fun getOutputStream() = uploaded
    override fun getResponseCode(): Int {
        val connection = Socket(host, port).also { socket = it; it.soTimeout = readTimeout }
        connection.getOutputStream().apply {
            write("POST /controlled HTTP/1.1\r\nHost: $host\r\nContent-Length: ${uploaded.size()}\r\nConnection: close\r\n\r\n".toByteArray())
            uploaded.writeTo(this); flush()
        }
        val input = connection.getInputStream()
        fun line(): String {
            val text = StringBuilder()
            while (true) {
                val byte = input.read()
                if (byte < 0) throw EOFException("controlled response lost")
                if (byte == 10) return text.toString().trimEnd('\r')
                text.append(byte.toChar())
            }
        }
        val status = line().split(' ')[1].toInt()
        while (line().isNotEmpty()) { }
        return status
    }
    override fun getInputStream() = requireNotNull(socket).getInputStream()
}
