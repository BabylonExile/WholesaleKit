package com.example.wholesale_kit

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

class MainActivity : AppCompatActivity() {

    private lateinit var btnTakePhoto: Button
    private lateinit var imageView: ImageView
    private lateinit var tvResult: TextView

    private lateinit var requestCameraPermissionLauncher: ActivityResultLauncher<String>
    private lateinit var takePictureResultLauncher: ActivityResultLauncher<Intent>

    private var lastPhoto: Bitmap? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        btnTakePhoto = findViewById(R.id.btnTakePhoto)
        imageView = findViewById(R.id.imageView)
        tvResult = findViewById(R.id.tvResult)

        // Лаунчер для результата камеры
        takePictureResultLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result: ActivityResult ->
            if (result.resultCode == Activity.RESULT_OK) {
                val data: Intent? = result.data
                val photo = data?.extras?.get("data") as? Bitmap
                if (photo != null) {
                    lastPhoto = photo
                    imageView.setImageBitmap(photo)
                    recognizeTextFromImage(photo)   // <-- здесь запускаем OCR
                } else {
                    tvResult.text = "Не удалось получить фото"
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
            takePictureResultLauncher.launch(cameraIntent)
        } else {
            tvResult.text = "Приложение камеры не найдено"
        }
    }

    // ---- OCR (ML Kit) ----

    private fun recognizeTextFromImage(bitmap: Bitmap) {
        tvResult.text = "Распознавание текста..."

        val image = InputImage.fromBitmap(bitmap, 0)
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

        recognizer.process(image)
            .addOnSuccessListener { visionText: Text ->
                val recognizedText = visionText.text
                if (recognizedText.isBlank()) {
                    tvResult.text = "Текст не найден"
                } else {
                    tvResult.text = "Распознанный текст:\n\n$recognizedText"
                }
            }
            .addOnFailureListener { e ->
                tvResult.text = "Ошибка распознавания: ${e.message}"
            }
    }
}