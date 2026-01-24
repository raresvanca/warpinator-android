package slowscript.warpinator.core.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BlendMode
import android.graphics.BlendModeColorFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.Paint
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.createBitmap
import androidx.core.net.toUri
import slowscript.warpinator.R

/**
 * A utility object responsible for generating and retrieving user profile pictures.
 *
 * This class handles various sources for profile pictures:
 * - Legacy content URIs.
 * - Local files (specifically "profilePic.png").
 * - Procedurally generated avatars based on an index (color selection) and an icon.
 */
internal object ProfilePicturePainter {
    private const val TAG = "PicPainter"
    fun generatePairs(steps: Int = 14, reversed: Boolean = false): List<Pair<Int, Int>> {
        val pairs = mutableListOf<Pair<Int, Int>>()
        val stepSize = 360f / (steps + 1)

        for (i in 0 until steps) {
            val hue = i * stepSize

            val containerInt = Color.HSVToColor(floatArrayOf(hue, 0.20f, 0.90f))
            val onContainerInt = Color.HSVToColor(floatArrayOf(hue, 0.90f, 0.30f))

            pairs.add(
                if (reversed) onContainerInt to containerInt
                else containerInt to onContainerInt
            )
        }
        return pairs
    }

    val colors = generatePairs() + generatePairs(reversed = true)
    val colorsLength = colors.size


    fun getProfilePicture(picture: String, ctx: Context, highRes: Boolean = false): Bitmap {
        var picture = picture
        if (picture.startsWith("content")) {
            // Legacy: load from persisted uri
            try {
                val uri = picture.toUri()
                return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    val source = ImageDecoder.createSource(ctx.contentResolver, uri)
                    ImageDecoder.decodeBitmap(source)
                } else {
                    @Suppress("DEPRECATION") MediaStore.Images.Media.getBitmap(
                        ctx.contentResolver, picture.toUri()
                    )
                }
            } catch (_: Exception) {
                picture = "0"
            }
        } else if ("profilePic.png" == picture) {
            try {
                ctx.openFileInput("profilePic.png").use { inputStream ->
                    return BitmapFactory.decodeStream(inputStream)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Could not load profile pic", e)
                picture = "0"
            }
        }
        val index = picture.toIntOrNull() ?: 0
        val safeIndex = index % colors.size
        val pictureSize = if (highRes) 256 else 128

        val foreground =
            ResourcesCompat.getDrawable(ctx.resources, R.drawable.ic_warpinator, null)!!

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            foreground.colorFilter =
                BlendModeColorFilter(colors[safeIndex].second, BlendMode.SRC_IN)
        } else {
            foreground.colorFilter = android.graphics.PorterDuffColorFilter(
                colors[safeIndex].second, android.graphics.PorterDuff.Mode.SRC_IN
            )
        }
        val bmp = createBitmap(pictureSize, pictureSize)
        val canvas = Canvas(bmp)
        val paint = Paint()
        paint.color = colors[safeIndex].first

        // Fill background
        canvas.drawRect(0f, 0f, pictureSize.toFloat(), pictureSize.toFloat(), paint)
        val padding = if (highRes) 32 else 16
        foreground.setBounds(padding, padding, pictureSize - padding, pictureSize - padding)
        foreground.draw(canvas)
        return bmp
    }
}