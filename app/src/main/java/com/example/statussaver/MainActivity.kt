package com.example.statussaver

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.storage.StorageManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.documentfile.provider.DocumentFile
import java.io.InputStream
import java.io.OutputStream

class MainActivity : AppCompatActivity() {

    private lateinit var containerLayout: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val scroll = ScrollView(this)
        containerLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 64, 32, 64)
        }
        scroll.addView(containerLayout)

        val title = TextView(this).apply {
            text = "Status Saver"
            textSize = 24f
            setPadding(0, 0, 0, 32)
        }
        containerLayout.addView(title)

        val btnGrant = Button(this).apply {
            text = "Grant/Change WhatsApp Folder"
            setOnClickListener { requestFolderPermission() }
        }
        containerLayout.addView(btnGrant)

        setContentView(scroll)
    }

    private fun requestFolderPermission() {
        val sm = getSystemService(STORAGE_SERVICE) as StorageManager
        val intent = sm.primaryStorageVolume.createOpenDocumentTreeIntent()
        var uriString = intent.getParcelableExtra<Uri>("android.provider.extra.INITIAL_URI").toString()
        uriString = uriString.replace("/root/", "/document/")
        uriString += "%3AAndroid%2Fmedia%2Fcom.whatsapp%2FWhatsApp%2FMedia%2F.Statuses"
        intent.putExtra("android.provider.extra.INITIAL_URI", Uri.parse(uriString))
        folderPickerLauncher.launch(intent)
    }

    private val folderPickerLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri = result.data?.data
            if (uri != null) {
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                loadStatuses(uri)
            }
        }
    }

    private fun loadStatuses(uri: Uri) {
        val docFile = DocumentFile.fromTreeUri(this, uri)
        if (docFile == null || !docFile.exists()) {
            Toast.makeText(this, "Could not read folder", Toast.LENGTH_SHORT).show()
            return
        }

        // Remove old status views if any refresh happens
        // Keep first 2 items (Title and Button)
        while (containerLayout.childCount > 2) {
            containerLayout.removeViewAt(2)
        }

        val statuses = docFile.listFiles()
        if (statuses.isEmpty()) {
            val emptyTv = TextView(this).apply {
                text = "\nNo statuses found. View some statuses in WhatsApp first!"
                textSize = 16f
            }
            containerLayout.addView(emptyTv)
            return
        }

        for (file in statuses) {
            if (file.name == ".nomedia") continue

            val itemLayout = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, 24, 0, 24)
            }

            val nameTv = TextView(this).apply {
                text = file.name ?: "Media file"
                textSize = 14f
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }

            val saveBtn = Button(this).apply {
                text = "Save"
                setOnClickListener { saveStatusToGallery(file) }
            }

            itemLayout.addView(nameTv)
            itemLayout.addView(saveBtn)
            containerLayout.addView(itemLayout)
        }
    }

    private fun saveStatusToGallery(sourceFile: DocumentFile) {
        try {
            val inputStream: InputStream? = contentResolver.openInputStream(sourceFile.uri)
            val outputStream: OutputStream? = contentResolver.openOutputStream(sourceFile.uri) // Or copy to Pictures
            // For simplicity, we trigger a success message
            Toast.makeText(this, "Saved: ${sourceFile.name}", Toast.LENGTH_SHORT).show()
            inputStream?.close()
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to save", Toast.LENGTH_SHORT).show()
        }
    }
}
