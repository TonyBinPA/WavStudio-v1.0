package com.wavstudio.app

import com.wavstudio.app.export.OutputGeometry
import com.wavstudio.app.export.Resolution
import com.wavstudio.app.export.WavHeader
import com.wavstudio.app.ui.formatTime
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class ExportLogicTest {

    @Test
    fun outputSize_originalKeepsSourceDimensions() {
        assertEquals(1920 to 1080, OutputGeometry.outputSize(1920, 1080, Resolution.ORIGINAL))
        assertEquals(1080 to 1920, OutputGeometry.outputSize(1080, 1920, Resolution.ORIGINAL))
    }

    @Test
    fun outputSize_scalesShortSideAndStaysEven() {
        assertEquals(1280 to 720, OutputGeometry.outputSize(1920, 1080, Resolution.HD))
        assertEquals(720 to 1280, OutputGeometry.outputSize(1080, 1920, Resolution.HD)) // portrait
        val (w, h) = OutputGeometry.outputSize(1440, 1080, Resolution.SD)
        assertEquals(480, h)
        assertEquals(0, w % 2)
    }

    @Test
    fun outputSize_unknownSourceFallsBackTo1080p() {
        assertEquals(1920 to 1080, OutputGeometry.outputSize(0, 0, Resolution.ORIGINAL))
    }

    @Test
    fun wavHeader_hasCorrectFields() {
        val header = WavHeader.build(sampleRate = 48_000, channels = 2, bitsPerSample = 16, dataBytes = 192_000)
        assertEquals(44, header.size)
        val b = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals("RIFF", String(header, 0, 4))
        assertEquals(36 + 192_000, b.getInt(4))
        assertEquals("WAVE", String(header, 8, 4))
        assertEquals("fmt ", String(header, 12, 4))
        assertEquals(16, b.getInt(16))
        assertEquals(1, b.getShort(20).toInt()) // PCM
        assertEquals(2, b.getShort(22).toInt())
        assertEquals(48_000, b.getInt(24))
        assertEquals(48_000 * 4, b.getInt(28)) // byte rate
        assertEquals(4, b.getShort(32).toInt()) // block align
        assertEquals(16, b.getShort(34).toInt())
        assertEquals("data", String(header, 36, 4))
        assertEquals(192_000, b.getInt(40))
    }

    @Test
    fun floatToPcm16_convertsAndClips() {
        val floats = ByteBuffer.allocate(16).order(ByteOrder.nativeOrder())
        floats.putFloat(0f).putFloat(1f).putFloat(-1f).putFloat(2f).flip()
        val pcm = WavHeader.floatToPcm16(floats)
        val expected = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN)
            .putShort(0).putShort(Short.MAX_VALUE).putShort((-Short.MAX_VALUE).toShort()).putShort(Short.MAX_VALUE).array()
        assertArrayEquals(expected, pcm)
    }

    @Test
    fun formatTime_formatsMinutesAndHours() {
        assertEquals("0:00.00", formatTime(0))
        assertEquals("1:05.25", formatTime(65_250))
        assertEquals("1:01:01.00", formatTime(3_661_000))
    }
}
