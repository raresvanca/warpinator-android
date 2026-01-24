package slowscript.warpinator.core.utils

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.zip.Deflater
import kotlin.random.Random

class ZlibCompressorTest {

    @Test
    fun `compress and decompress returns identical byte array`() {
        val originalString = "Hello, World! This is a test string to verify Zlib compression."
        val inputBytes = originalString.toByteArray(Charsets.UTF_8)

        val compressedBytes = ZlibCompressor.compress(
            inputBytes,
            inputBytes.size,
            Deflater.BEST_COMPRESSION,
        )
        val decompressedBytes = ZlibCompressor.decompress(compressedBytes)

        assertArrayEquals("Decompressed bytes should match original", inputBytes, decompressedBytes)

        val decompressedString = String(decompressedBytes, Charsets.UTF_8)
        assertEquals(originalString, decompressedString)
    }

    @Test
    fun `handles data larger than buffer size`() {
        val largeSize = 5000 // ~5KB
        val inputBytes = Random.nextBytes(largeSize)

        val compressed =
            ZlibCompressor.compress(inputBytes, inputBytes.size, Deflater.DEFAULT_COMPRESSION)
        val decompressed = ZlibCompressor.decompress(compressed)

        assertArrayEquals(inputBytes, decompressed)
    }

    @Test
    fun `compress respects the length parameter`() {
        val fullArray = "PartToCompress_PartToIgnore".toByteArray()
        val partToCompress = "PartToCompress".toByteArray()
        val length = partToCompress.size

        val compressed = ZlibCompressor.compress(fullArray, length, Deflater.DEFAULT_COMPRESSION)
        val decompressed = ZlibCompressor.decompress(compressed)

        assertArrayEquals(partToCompress, decompressed)
    }

    @Test
    fun `compress reduces size of repetitive text`() {
        val original = "A".repeat(1000).toByteArray()

        val compressed = ZlibCompressor.compress(original, original.size, Deflater.BEST_COMPRESSION)

        assert(compressed.size < original.size) {
            "Compressed data (${compressed.size}) should be smaller than original (${original.size})"
        }
    }
}