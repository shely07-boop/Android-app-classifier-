package com.example.sheyla_arteaga_lab15

import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.provider.OpenableColumns
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.chip.Chip
import com.google.android.material.color.MaterialColors
import com.google.android.material.progressindicator.LinearProgressIndicator
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import java.util.Locale
import kotlin.math.roundToInt

class MainActivity : AppCompatActivity() {

    // ---------- Model constants ----------
    companion object {
        const val MODEL_FILE = "model.tflite"
        const val LABELS_FILE = "labels.txt"
        const val INPUT_SIZE = 224          // the model expects 224 x 224 images
        const val CHANNELS = 3              // R, G, B
        const val BYTES_PER_FLOAT = 4       // a float32 takes 4 bytes
        const val MAX_GALLERY_SIDE = 2048   // limit so huge photos are not loaded into memory
    }

    // The 4 test images stored in app/src/main/assets/
    private val assetImages = listOf(
        "IM-0001-0001.jpeg",            // normal
        "IM-0003-0001.jpeg",            // normal
        "person100_bacteria_475.jpeg",  // pneumonia
        "person100_bacteria_477.jpeg"   // pneumonia
    )
    private var currentIndex = 0

    // Image currently shown (from assets or from the gallery).
    // "Label Image" always classifies this image.
    private var currentBitmap: Bitmap? = null

    // LiteRT/TensorFlow Lite interpreter and labels (NORMAL, PNEUMONIA)
    private lateinit var interpreter: Interpreter
    private lateinit var labels: List<String>

    // Views
    private lateinit var imageView: ImageView
    private lateinit var txtFileName: TextView
    private lateinit var txtHint: TextView
    private lateinit var resultContent: View
    private lateinit var chipPrediction: Chip
    private lateinit var classBarsContainer: LinearLayout
    private lateinit var txtResult: TextView
    private lateinit var txtInferenceTime: TextView

    // Photo Picker: opens the gallery WITHOUT asking for permissions.
    // When the user picks a photo we receive its Uri (or null if they canceled).
    private val photoPicker =
        registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            if (uri != null) {
                showGalleryImage(uri)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        // Keeps the content from going under the status / navigation bars.
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        // References to the layout views
        imageView = findViewById(R.id.imageView)
        txtFileName = findViewById(R.id.txtFileName)
        txtHint = findViewById(R.id.txtHint)
        resultContent = findViewById(R.id.resultContent)
        chipPrediction = findViewById(R.id.chipPrediction)
        classBarsContainer = findViewById(R.id.classBarsContainer)
        txtResult = findViewById(R.id.txtResult)
        txtInferenceTime = findViewById(R.id.txtInferenceTime)

        // 1) Load the model and the labels only once at startup
        interpreter = Interpreter(loadModel())
        labels = loadLabels()

        // 2) Show the first image from assets
        showAssetImage()

        // 3) Buttons
        findViewById<Button>(R.id.btnPrevious).setOnClickListener {
            // Go back circularly: from image 1 it jumps to image 4
            currentIndex = (currentIndex - 1 + assetImages.size) % assetImages.size
            showAssetImage()
        }
        findViewById<Button>(R.id.btnNext).setOnClickListener {
            // Go forward circularly: from image 4 it goes back to image 1
            currentIndex = (currentIndex + 1) % assetImages.size
            showAssetImage()
        }
        findViewById<Button>(R.id.btnChoose).setOnClickListener {
            // Open the Photo Picker showing images only
            photoPicker.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
        }
        findViewById<Button>(R.id.btnLabel).setOnClickListener {
            classify()
        }
    }

    // ================== Loading the model and labels ==================

    /** Opens model.tflite from assets as a MappedByteBuffer (that is why it must not be compressed). */
    private fun loadModel(): MappedByteBuffer {
        val descriptor = assets.openFd(MODEL_FILE)
        FileInputStream(descriptor.fileDescriptor).use { input ->
            return input.channel.map(
                FileChannel.MapMode.READ_ONLY,
                descriptor.startOffset,
                descriptor.declaredLength
            )
        }
    }

    /** Reads labels.txt: one label per line (index 0 = NORMAL, index 1 = PNEUMONIA). */
    private fun loadLabels(): List<String> {
        return assets.open(LABELS_FILE).bufferedReader().useLines { lines ->
            lines.map { it.trim() }.filter { it.isNotEmpty() }.toList()
        }
    }

    // ================== Showing images ==================

    /** Shows the asset image that matches currentIndex. */
    private fun showAssetImage() {
        val name = assetImages[currentIndex]
        val bitmap = assets.open(name).use { BitmapFactory.decodeStream(it) }
        txtFileName.text = getString(R.string.asset_image_name, currentIndex + 1, assetImages.size, name)
        showImage(bitmap)
    }

    /** Shows the image picked from the gallery. */
    private fun showGalleryImage(uri: Uri) {
        val bitmap = decodeFromUri(uri)
        if (bitmap == null) {
            Toast.makeText(this, R.string.error_open_image, Toast.LENGTH_SHORT).show()
            return
        }
        txtFileName.text = getString(R.string.gallery_image_name, getFileName(uri))
        showImage(bitmap)
    }

    /** Stores the bitmap as the current image, displays it and resets the result card. */
    private fun showImage(bitmap: Bitmap) {
        currentBitmap = bitmap
        imageView.setImageBitmap(bitmap)
        // Show the hint again and hide the previous result
        txtHint.visibility = View.VISIBLE
        resultContent.visibility = View.GONE
    }

    /**
     * Reads a photo from the gallery. If it is very large it is downscaled while
     * decoding (inSampleSize) to avoid running out of memory. It is resized to 224x224 later anyway.
     */
    private fun decodeFromUri(uri: Uri): Bitmap? {
        // First pass: only read the size, without loading the pixels
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }

        // Work out how much to downscale (powers of 2) so the longest side is <= MAX_GALLERY_SIDE
        var factor = 1
        while (maxOf(options.outWidth, options.outHeight) / factor > MAX_GALLERY_SIDE) {
            factor *= 2
        }

        // Second pass: actually decode the image
        val finalOptions = BitmapFactory.Options().apply {
            inSampleSize = factor
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, finalOptions)
        }
    }

    /** Gets the name of the file picked from the gallery (only to display it). */
    private fun getFileName(uri: Uri): String {
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) return cursor.getString(0)
            }
        return getString(R.string.selected_image)
    }

    // ================== Classification ==================

    /** Runs when "Label Image" is pressed. */
    private fun classify() {
        val bitmap = currentBitmap ?: return

        // 1) Preprocess the image -> float32 ByteBuffer [1, 224, 224, 3]
        val input = bitmapToBuffer(bitmap)

        // 2) Prepare the output: float32 [1, 2] (softmax probabilities)
        val output = Array(1) { FloatArray(labels.size) }

        // 3) Run the model and measure only the inference time
        val start = SystemClock.elapsedRealtime()
        interpreter.run(input, output)
        val inferenceMs = SystemClock.elapsedRealtime() - start
        val probabilities = output[0]

        // 4) Class indices sorted from highest to lowest probability
        val sorted = labels.indices.sortedByDescending { probabilities[it] }

        showResult(sorted, probabilities, inferenceMs)
    }

    /** Fills the result card: chip, one progress bar per class, text lines and time. */
    private fun showResult(sorted: List<Int>, probabilities: FloatArray, inferenceMs: Long) {
        // a) Chip with the predicted class (the first one after sorting)
        val best = sorted.first()
        chipPrediction.text = labels[best]
        chipPrediction.chipBackgroundColor = ColorStateList.valueOf(colorForLabel(labels[best]))
        chipPrediction.setTextColor(getColor(R.color.on_class_chip))

        // b) One progress bar per class, highest first
        classBarsContainer.removeAllViews()
        for (i in sorted) {
            val row = layoutInflater.inflate(R.layout.item_class_row, classBarsContainer, false)
            val percent = probabilities[i] * 100f
            row.findViewById<TextView>(R.id.txtClassName).text = labels[i]
            row.findViewById<TextView>(R.id.txtClassPercent).text =
                String.format(Locale.US, "%.2f%%", percent)
            row.findViewById<LinearProgressIndicator>(R.id.progressClass).apply {
                setIndicatorColor(colorForLabel(labels[i]))
                setProgressCompat(percent.roundToInt(), true)
            }
            classBarsContainer.addView(row)
        }

        // c) Same text lines as Colab: "LABEL : 0.9731" (4 decimals, highest first)
        txtResult.text = sorted.joinToString("\n") { i ->
            String.format(Locale.US, "%s : %.4f", labels[i], probabilities[i])
        }

        // d) Inference time
        txtInferenceTime.text = getString(R.string.inference_time, inferenceMs)

        // Hide the hint and show the result
        txtHint.visibility = View.GONE
        resultContent.visibility = View.VISIBLE
    }

    /** Green for NORMAL, red for PNEUMONIA, theme primary color for anything else. */
    private fun colorForLabel(label: String): Int = when (label.uppercase(Locale.US)) {
        "NORMAL" -> getColor(R.color.class_normal)
        "PNEUMONIA" -> getColor(R.color.class_pneumonia)
        else -> MaterialColors.getColor(txtResult, androidx.appcompat.R.attr.colorPrimary)
    }

    /**
     * Converts the bitmap to the model's input format:
     *  - Resizes to 224x224 with a bilinear filter (filter = true), like Keras.
     *  - Uses ARGB_8888 to read the colors reliably.
     *  - Writes R, G, B (in that order) as floats from 0 to 255.
     *  - Does NOT normalize: the model already has a Rescaling layer inside.
     */
    private fun bitmapToBuffer(original: Bitmap): ByteBuffer {
        // Make sure the format is ARGB_8888 (4 bytes per pixel: alpha, red, green, blue)
        val argb = if (original.config == Bitmap.Config.ARGB_8888) original
                   else original.copy(Bitmap.Config.ARGB_8888, false)

        // Resize to 224x224 with bilinear interpolation
        val resized = Bitmap.createScaledBitmap(argb, INPUT_SIZE, INPUT_SIZE, true)

        // Buffer of 1 * 224 * 224 * 3 floats = 602,112 bytes
        val buffer = ByteBuffer.allocateDirect(
            1 * INPUT_SIZE * INPUT_SIZE * CHANNELS * BYTES_PER_FLOAT
        )
        buffer.order(ByteOrder.nativeOrder())

        // Read all the pixels. Each Int has the format 0xAARRGGBB
        val pixels = IntArray(INPUT_SIZE * INPUT_SIZE)
        resized.getPixels(pixels, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE)

        // Go through the pixels row by row (NHWC order expected by the model)
        for (pixel in pixels) {
            val r = (pixel shr 16) and 0xFF
            val g = (pixel shr 8) and 0xFF
            val b = pixel and 0xFF
            buffer.putFloat(r.toFloat())   // 0..255, not divided
            buffer.putFloat(g.toFloat())
            buffer.putFloat(b.toFloat())
        }

        buffer.rewind() // go back to the start so the interpreter reads the whole buffer
        return buffer
    }

    override fun onDestroy() {
        super.onDestroy()
        // Free the interpreter's native memory
        if (::interpreter.isInitialized) interpreter.close()
    }
}
