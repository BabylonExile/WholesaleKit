package com.example.wholesale_kit

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.exifinterface.media.ExifInterface

private fun getRotationDegrees(path: String): Int {
    return try {
        val exif = ExifInterface(path)
        when (exif.getAttributeInt(
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.ORIENTATION_NORMAL
        )) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90
            ExifInterface.ORIENTATION_ROTATE_180 -> 180
            ExifInterface.ORIENTATION_ROTATE_270 -> 270
            else -> 0
        }
    } catch (e: Exception) {
        0
    }
}
class MainActivity : AppCompatActivity() {

    private lateinit var btnTakePhoto: Button
    private lateinit var imageView: ImageView
    private lateinit var tvResult: TextView
    private lateinit var flavors: List<Flavor>
    private lateinit var requestCameraPermissionLauncher: ActivityResultLauncher<String>
    private lateinit var takePictureResultLauncher: ActivityResultLauncher<Intent>

    private var currentPhotoPath: String? = null  // путь к сохранённому фото

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        btnTakePhoto = findViewById(R.id.btnTakePhoto)
        imageView = findViewById(R.id.imageView)
        tvResult = findViewById(R.id.tvResult)
        flavors = FlavorRepository.loadFlavors(this)

        // Лаунчер для результата камеры
        takePictureResultLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result: ActivityResult ->
            if (result.resultCode == Activity.RESULT_OK) {
                val path = currentPhotoPath
                if (path != null) {
                    // Загружаем фото из файла, уменьшая до ~1600x1600
                    val bitmap = decodeSampledBitmapFromFile(path, 1600, 1600)
                    if (bitmap != null) {
                        imageView.setImageBitmap(bitmap)
                        recognizeTextFromImage(bitmap, path)
                    } else {
                        tvResult.text = "Не удалось загрузить фото"
                    }
                } else {
                    tvResult.text = "Путь к фото не найден"
                }
            } else {
                tvResult.text = "Фото не было сделано"
            }
        }

        // Лаунчер для запроса разрешения камеры
        requestCameraPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted: Boolean ->
            if (isGranted) {
                openCamera()
            } else {
                tvResult.text = "Разрешение на использование камеры не предоставлено"
            }
        }

        btnTakePhoto.setOnClickListener {
            checkCameraPermissionAndOpen()
        }
    }

    private fun checkCameraPermissionAndOpen() {
        val permission = Manifest.permission.CAMERA

        when {
            ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED -> {
                // Разрешение уже есть
                openCamera()
            }

            shouldShowRequestPermissionRationale(permission) -> {
                // Можно показать объяснение, почему нужно разрешение
                tvResult.text = "Для съёмки полки нужно разрешение на камеру"
                requestCameraPermissionLauncher.launch(permission)
            }

            else -> {
                // Запросить разрешение
                requestCameraPermissionLauncher.launch(permission)
            }
        }
    }

    private fun openCamera() {
        val cameraIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        if (cameraIntent.resolveActivity(packageManager) != null) {

            val photoFile: File? = createImageFile()
            if (photoFile != null) {
                val photoURI: Uri = FileProvider.getUriForFile(
                    this,
                    "${applicationContext.packageName}.fileprovider",
                    photoFile
                )
                cameraIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI)
                takePictureResultLauncher.launch(cameraIntent)
            } else {
                tvResult.text = "Не удалось создать файл для фото"
            }

        } else {
            tvResult.text = "Приложение камеры не найдено"
        }
    }



    // Создаём временный файл для фото
    private fun createImageFile(): File? {
        return try {
            val timeStamp: String =
                SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val storageDir: File? = getExternalFilesDir(Environment.DIRECTORY_PICTURES)
            val image = File.createTempFile(
                "JPEG_${timeStamp}_",
                ".jpg",
                storageDir
            )
            currentPhotoPath = image.absolutePath
            image
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    // Декодируем изображение из файла с уменьшением (чтобы не убить память)
    private fun decodeSampledBitmapFromFile(path: String, reqWidth: Int, reqHeight: Int): Bitmap? {
        // Сначала читаем только размеры
        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeFile(path, options)

        // Вычисляем коэффициент уменьшения
        options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight)

        // Теперь декодируем с учётом уменьшения
        options.inJustDecodeBounds = false
        return BitmapFactory.decodeFile(path, options)
    }

    private fun calculateInSampleSize(
        options: BitmapFactory.Options,
        reqWidth: Int,
        reqHeight: Int
    ): Int {
        val height = options.outHeight
        val width = options.outWidth
        var inSampleSize = 1

        if (height > reqHeight || width > reqWidth) {
            val halfHeight: Int = height / 2
            val halfWidth: Int = width / 2

            while ((halfHeight / inSampleSize) >= reqHeight &&
                (halfWidth / inSampleSize) >= reqWidth
            ) {
                inSampleSize *= 2
            }
        }

        return inSampleSize
    }

    // ---- OCR (ML Kit) ----

    private fun recognizeTextFromImage(bitmap: Bitmap, photoPath: String) {
        tvResult.text = "Распознавание текста..."

        val rotation = getRotationDegrees(photoPath)
        val image = InputImage.fromBitmap(bitmap, rotation)
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

        recognizer.process(image)
            .addOnSuccessListener { visionText: Text ->
                val rawText = visionText.text
                if (rawText.isBlank()) {
                    tvResult.text = "Текст не найден"
                } else {
                    val cleaned = cleanRecognizedText(rawText)
                    val (bestFlavor, score) = findBestMatchingFlavor(rawText)

                    val sb = StringBuilder()
                    sb.append("Сырой текст OCR:\n")
                    sb.append(rawText)
                    sb.append("\n\nОчищенный текст:\n")
                    sb.append(cleaned)
                    sb.append("\n\nОпределённый вкус:\n")

                    val threshold = 0.3  // порог уверенности

                    if (bestFlavor != null && score >= threshold) {
                        sb.append("${bestFlavor.name_ru} / ${bestFlavor.name_en}")
                        sb.append("\n(уверенность: ${(score * 100).toInt()}%)")
                    } else {
                        sb.append("Не удалось уверенно определить вкус")
                        sb.append("\n(лучшая похожесть: ${(score * 100).toInt()}%)")
                    }

                    tvResult.text = sb.toString()
                }
            }
            .addOnFailureListener { e ->
                tvResult.text = "Ошибка распознавания: ${e.message}"
            }
    }

    private val stopWords = listOf(
        "blackburn", "black burn", "black-burn",
        "tabak", "tabaka", "tabakana", "tabakom",
        "tabac", "hookah", "premier", "премии", "табака", "табак"
    )

    private fun cleanRecognizedText(raw: String): String {
        var text = raw.lowercase()

        // заменяем любые пробельные символы на пробел
        text = text.replace(Regex("\\s+"), " ")

        // убираем стоп-слова
        for (word in stopWords) {
            text = text.replace(word, " ")
        }

        // оставляем только буквы/цифры/пробелы
        text = text.replace(Regex("[^a-zа-я0-9\\s]"), " ")

        // ещё раз сжимаем пробелы
        text = text.replace(Regex("\\s+"), " ").trim()

        return text
    }

    // Расстояние Левенштейна между двумя строками
    private fun levenshtein(a: String, b: String): Int {
        val m = a.length
        val n = b.length
        if (m == 0) return n
        if (n == 0) return m

        val dp = Array(m + 1) { IntArray(n + 1) }

        for (i in 0..m) dp[i][0] = i
        for (j in 0..n) dp[0][j] = j

        for (i in 1..m) {
            for (j in 1..n) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                dp[i][j] = minOf(
                    dp[i - 1][j] + 1,     // удаление
                    dp[i][j - 1] + 1,     // вставка
                    dp[i - 1][j - 1] + cost  // замена
                )
            }
        }

        return dp[m][n]
    }

    // Нормированная похожесть: 1.0 = идеально, 0.0 = совсем не похоже
    private fun similarity(a: String, b: String): Double {
        if (a.isBlank() || b.isBlank()) return 0.0
        val dist = levenshtein(a, b)
        val maxLen = maxOf(a.length, b.length)
        return 1.0 - dist.toDouble() / maxLen.toDouble()
    }

    private fun bestWordSimilarity(text: String, flavorName: String): Double {
        if (text.isBlank() || flavorName.isBlank()) return 0.0

        val textWords = text.split(" ").filter { it.length >= 3 }
        val flavorWords = flavorName.lowercase().split(" ").filter { it.length >= 3 }

        if (textWords.isEmpty() || flavorWords.isEmpty()) return 0.0

        var best = 0.0

        for (tw in textWords) {
            for (fw in flavorWords) {
                val s = similarity(tw, fw)
                if (s > best) best = s
            }
        }

        return best
    }
    private fun findBestMatchingFlavor(recognizedText: String): Pair<Flavor?, Double> {
        val cleaned = cleanRecognizedText(recognizedText)
        if (cleaned.isBlank()) return null to 0.0

        var bestFlavor: Flavor? = null
        var bestScore = 0.0

        for (flavor in flavors) {
            val nameRu = flavor.name_ru.lowercase()
            val nameEn = flavor.name_en.lowercase()

            val scoreRu = bestWordSimilarity(cleaned, nameRu)
            val scoreEn = bestWordSimilarity(cleaned, nameEn)

            val score = maxOf(scoreRu, scoreEn)

            if (score > bestScore) {
                bestScore = score
                bestFlavor = flavor
            }
        }

        return bestFlavor to bestScore
    }
}