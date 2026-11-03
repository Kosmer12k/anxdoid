package com.anxdoid.runtime

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.anxdoid.runtime.core.NativeBridge
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {

    private lateinit var statusTextView: TextView
    private lateinit var selectFirmwareButton: Button
    
    // Регистрация обработчика SAF для выбора файлов с расширением .img
    private val filePickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                handleSelectedFirmware(uri)
            }
        } else {
            updateStatus("Выбор прошивки отменен.")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Динамическое создание UI, чтобы не зависеть от изменений в xml
        val layout = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            gravity = android.view.Gravity.CENTER
            padding = 32
        }

        statusTextView = TextView(this).apply {
            text = "Рантайм готов. Выберите образ прошивки (.img)"
            textSize = 16f
            gravity = android.view.Gravity.CENTER
            setPadding(0, 0, 0, 48)
        }

        selectFirmwareButton = Button(this).apply {
            text = "Выбрать system.img через SAF"
            setOnClickListener {
                openFilePicker()
            }
        }

        layout.addView(statusTextView)
        layout.addView(selectFirmwareButton)
        setContentView(layout)
    }

    private fun openFilePicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*" // Запрашиваем все типы, фильтрацию по имени сделаем при получении
        }
        filePickerLauncher.launch(intent)
    }

    private fun handleSelectedFirmware(uri: Uri) {
        updateStatus("Копирование образа во внутреннюю память...")
        selectFirmwareButton.isEnabled = false

        // Запуск в фоновом потоке, так как system.img может весить несколько гигабайт
        thread(start = true) {
            try {
                val targetDir = File(filesDir, "rootfs")
                if (!targetDir.exists()) {
                    targetDir.mkdirs()
                }

                // Целевой файл внутри приватного хранилища приложения
                val targetFile = File(targetDir, "system.img")
                
                contentResolver.openInputStream(uri).use { inputStream ->
                    if (inputStream == null) throw Exception("Не удалось открыть выбранный файл")
                    
                    FileOutputStream(targetFile).use { outputStream ->
                        val buffer = ByteArray(1024 * 1024) // Буфер 1 МБ для быстрой скорости копирования
                        var bytesRead: Int
                        while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                            outputStream.write(buffer, 0, bytesRead)
                        }
                    }
                }

                // Файл успешно скопирован, запускаем рантайм
                runOnUiThread {
                    updateStatus("Образ успешно загружен! Запуск Anxdoid...")
                    bootAnxdoidRuntime(targetFile.absolutePath)
                }

            } catch (e: Exception) {
                runOnUiThread {
                    selectFirmwareButton.isEnabled = true
                    updateStatus("Ошибка при обработке файла: ${e.localizedMessage}")
                    Toast.makeText(this@MainActivity, "Ошибка копирования", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun bootAnxdoidRuntime(firmwarePath: String) {
        try {
            // Передаем путь к образу в наш JNI слой
            val result = NativeBridge.initializeRuntime(firmwarePath)
            if (result == 0) {
                updateStatus("Anxdoid запущен из: $firmwarePath")
            } else {
                updateStatus("Ошибка запуска рантайма. Код ошибки: $result")
                selectFirmwareButton.isEnabled = true
            }
        } catch (e: Exception) {
            updateStatus("Критическая ошибка JNI: ${e.localizedMessage}")
            selectFirmwareButton.isEnabled = true
        }
    }

    private fun updateStatus(message: String) {
        statusTextView.text = message
    }
}
