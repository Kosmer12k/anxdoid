package com.anxdoid.runtime

import android.annotation.SuppressLint
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.util.Log
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.anxdoid.runtime.core.NativeBridge
import java.io.File

class MainActivity : AppCompatActivity(), SurfaceHolder.Callback {

    companion object {
        private const val TAG = "AnxdoidHost"
        private const val PREFS = "image_selection"
        private const val PREF_IMAGE_URI = "selected_image_uri"
    }

    private lateinit var guestSurfaceView: SurfaceView
    private lateinit var statusText: TextView
    private lateinit var selectedImageText: TextView
    private lateinit var btnBoot: Button
    private lateinit var btnReset: Button
    private lateinit var imagePicker: ActivityResultLauncher<Array<String>>

    private var isSurfaceReady = false
    private var isGuestRunning = false
    private var selectedImageUri: Uri? = null

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        guestSurfaceView = findViewById(R.id.guestSurfaceView)
        statusText = findViewById(R.id.statusText)
        selectedImageText = findViewById(R.id.selectedImageText)
        btnBoot = findViewById(R.id.btnBoot)
        btnReset = findViewById(R.id.btnReset)
        guestSurfaceView.holder.addCallback(this)

        imagePicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) rememberImage(uri)
        }
        findViewById<Button>(R.id.btnSelectImage).setOnClickListener {
            // SAF grants this app read access to the chosen document; no broad storage permission
            // and no copy of the source image are needed.
            imagePicker.launch(arrayOf("*/*"))
        }
        restoreImageSelection()

        guestSurfaceView.setOnTouchListener { _, _ ->
            // Input forwarding is only meaningful once a compatible guest runtime is connected.
            isGuestRunning
        }

        val baseDir = filesDir.absolutePath
        val rootfsDir = File(filesDir, "rootfs").absolutePath
        File(rootfsDir).mkdirs()
        if (NativeBridge.initRuntime(baseDir, rootfsDir)) {
            if (selectedImageUri == null) statusText.setText(R.string.status_ready)
            Log.i(TAG, "Native runtime initialized. Rootfs: $rootfsDir")
        } else {
            statusText.text = "Не удалось инициализировать native runtime."
            Log.e(TAG, "Failed to initialize native runtime.")
        }

        // The current anxdoid native launcher expects an already prepared rootfs directory.
        // A selected SAF URI is not a POSIX path and is not yet consumed by that launcher.
        btnBoot.isEnabled = false
        btnBoot.setOnClickListener {
            Toast.makeText(this, R.string.raw_image_not_supported, Toast.LENGTH_LONG).show()
        }

        btnReset.setOnClickListener {
            val dataDir = File(filesDir, "rootfs/data")
            if (dataDir.exists()) {
                dataDir.deleteRecursively()
                Toast.makeText(this, "Данные гостя очищены.", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Данных гостя пока нет.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun rememberImage(uri: Uri) {
        try {
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString(PREF_IMAGE_URI, uri.toString()).apply()
            selectedImageUri = uri
            showImageDetails(uri)
            statusText.setText(R.string.status_image_selected)
        } catch (exception: SecurityException) {
            selectedImageUri = null
            selectedImageText.setText(R.string.no_image_selected)
            statusText.text = "Провайдер файлов не разрешил постоянный доступ. Выберите файл из другого расположения."
            Log.w(TAG, "Could not persist SAF read permission", exception)
        }
    }

    private fun restoreImageSelection() {
        val saved = getSharedPreferences(PREFS, MODE_PRIVATE).getString(PREF_IMAGE_URI, null) ?: run {
            selectedImageText.setText(R.string.no_image_selected)
            return
        }
        val uri = Uri.parse(saved)
        val hasPersistedGrant = contentResolver.persistedUriPermissions.any {
            it.uri == uri && it.isReadPermission
        }
        if (!hasPersistedGrant) {
            getSharedPreferences(PREFS, MODE_PRIVATE).edit().remove(PREF_IMAGE_URI).apply()
            selectedImageText.setText(R.string.no_image_selected)
            statusText.setText(R.string.status_image_access_lost)
            return
        }
        try {
            contentResolver.openFileDescriptor(uri, "r")?.use { }
                ?: throw IllegalStateException("Document provider returned no file descriptor")
            selectedImageUri = uri
            showImageDetails(uri)
            statusText.setText(R.string.status_image_selected)
        } catch (exception: Exception) {
            getSharedPreferences(PREFS, MODE_PRIVATE).edit().remove(PREF_IMAGE_URI).apply()
            selectedImageUri = null
            selectedImageText.setText(R.string.status_image_access_lost)
            statusText.setText(R.string.status_image_access_lost)
            Log.w(TAG, "Previously selected document is no longer available", exception)
        }
    }

    private fun showImageDetails(uri: Uri) {
        var name = uri.lastPathSegment ?: "Выбранный файл"
        var size: Long? = null
        try {
            contentResolver.query(uri, null, null, null, null)?.use { cursor: Cursor ->
                if (cursor.moveToFirst()) {
                    val nameColumn = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeColumn = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (nameColumn >= 0) name = cursor.getString(nameColumn) ?: name
                    if (sizeColumn >= 0 && !cursor.isNull(sizeColumn)) size = cursor.getLong(sizeColumn)
                }
            }
        } catch (exception: Exception) {
            Log.w(TAG, "Could not read document metadata", exception)
        }
        val sizeText = size?.let { String.format("%.1f MiB", it / (1024.0 * 1024.0)) } ?: "размер неизвестен"
        selectedImageText.text = "$name ($sizeText)\nИсходный файл не копируется."
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        isSurfaceReady = NativeBridge.setSurface(holder.surface)
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        isSurfaceReady = NativeBridge.setSurface(holder.surface)
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        isSurfaceReady = false
        NativeBridge.clearSurface()
    }

    override fun onDestroy() {
        if (isGuestRunning) NativeBridge.stopGuestRuntime()
        super.onDestroy()
    }
}
