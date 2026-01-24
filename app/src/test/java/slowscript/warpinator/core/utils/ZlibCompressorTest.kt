package slowscript.warpinator.core.utils

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.zip.Deflater
import kotlin.random.Random

class ZlibCompressorTest {

    @Test
    fun `compress and decompress returns identical byte array`() {
        // 1. Arrange: Create some sample input
        val originalString = "Hello, World! This is a test string to verify Zlib compression."
        val inputBytes = originalString.toByteArray(Charsets.UTF_8)

        // 2. Act: Compress the data
        val compressedBytes = ZlibCompressor.compress(
            inputBytes,
            inputBytes.size,
            Deflater.BEST_COMPRESSION,
        )

        // 3. Act: Decompress the result
        val decompressedBytes = ZlibCompressor.decompress(compressedBytes)

        // 4. Assert: The output must match the input
        assertArrayEquals("Decompressed bytes should match original", inputBytes, decompressedBytes)

        // Optional: Check string equality to be sure
        val decompressedString = String(decompressedBytes, Charsets.UTF_8)
        assertEquals(originalString, decompressedString)
    }

    @Test
    fun `handles data larger than buffer size`() {
        // Your class has a buffer of 1024. We need to test inputs larger than that
        // to ensure the 'while' loops in your code function correctly.
        val largeSize = 5000 // ~5KB
        val inputBytes = Random.nextBytes(largeSize)

        val compressed =
            ZlibCompressor.compress(inputBytes, inputBytes.size, Deflater.DEFAULT_COMPRESSION)
        val decompressed = ZlibCompressor.decompress(compressed)

        assertArrayEquals(inputBytes, decompressed)
    }

    @Test
    fun `compress respects the length parameter`() {
        // Arrange: A byte array, but we only want to compress the first part
        val fullArray = "PartToCompress_PartToIgnore".toByteArray()
        val partToCompress = "PartToCompress".toByteArray()
        val length = partToCompress.size

        // Act: Pass the full array but restrict the length
        val compressed = ZlibCompressor.compress(fullArray, length, Deflater.DEFAULT_COMPRESSION)
        val decompressed = ZlibCompressor.decompress(compressed)

        // Assert: The result should ONLY contain the first part
        assertArrayEquals(partToCompress, decompressed)
    }

    @Test
    fun `compress reduces size of repetitive text`() {
        // Arrange: A long string of repeating characters (highly compressible)
        val original = "A".repeat(1000).toByteArray()

        // Act
        val compressed = ZlibCompressor.compress(original, original.size, Deflater.BEST_COMPRESSION)

        // Assert: Compressed size should be significantly smaller than original
        // (Zlib overhead is small, 1000 'A's should compress to ~15-20 bytes)
        assert(compressed.size < original.size) {
            "Compressed data (${compressed.size}) should be smaller than original (${original.size})"
        }
    }
}