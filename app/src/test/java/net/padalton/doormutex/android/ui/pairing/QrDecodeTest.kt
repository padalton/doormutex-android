package net.padalton.doormutex.android.ui.pairing

import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import net.padalton.doormutex.android.data.PairLink
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class QrDecodeTest {
    private val link =
        "doormutex://pair?hub=https%3A%2F%2Fdoormutex.home.example" +
            "&token=dm_0123456789abcdefghijklmnopqrstuvwxyzABCDE&name=couch"

    /** A camera-like frame: grey background, the code in the middle, rows padded like a real Y plane. */
    private fun frame(
        text: String,
        width: Int = 640,
        height: Int = 480,
        stride: Int = 704,
    ): ByteArray {
        val code = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 300, 300)
        val data = ByteArray(stride * height) { 0xB0.toByte() }
        val left = (width - code.width) / 2
        val top = (height - code.height) / 2
        for (y in 0 until code.height) {
            for (x in 0 until code.width) {
                data[(top + y) * stride + left + x] = if (code[x, y]) 0x10 else 0xF0.toByte()
            }
        }
        return data
    }

    @Test
    fun `the web UI's pairing code is read`() {
        val text = decodeLuminance(qrReader(), frame(link), 704, 640, 480)
        assertEquals(link, text)
        assertEquals("couch", PairLink.parse(text!!)?.name)
    }

    @Test
    fun `no code, no result`() {
        assertNull(decodeLuminance(qrReader(), ByteArray(704 * 480) { 0x80.toByte() }, 704, 640, 480))
    }
}
