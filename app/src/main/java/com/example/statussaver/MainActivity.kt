package com.example.statussaver

import android.app.Activity
import android.content.ContentValues
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.storage.StorageManager
import android.provider.MediaStore
import android.view.Gravity
import android.widget.Button
import android.widget.GridLayout
import android.widget.ImageView
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

    private lateinit var gridLayout: GridLayout
    private lateinit var tabImages: TextView
    private lateinit var tabVideos: TextView
    private var allFiles: List<DocumentFile> = listOf()
    private var currentTab = "images"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val rootLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFFF0F2F5.toInt())
        }

        // Header
        val header = TextView(this).apply {
            text = "Status Saver"
            textSize = 20f
            setTextColor(0xFFFFFFFF.toInt())
            setBackgroundColor(0xFF128C7E.toInt())
            setPadding(32, 32, 32, 32)
            gravity = Gravity.CENTER
        }
        rootLayout.addView(header)

        // Grant Permission Button
        val btnGrant = Button(this).apply {
            text = "Select WhatsApp Folder"
            setOnClickListener { requestFolderPermission() }
            setBackgroundColor(0xFF25D366.toInt())
            setTextColor(0xFFFFFFFF.toInt())
        }
        val grantParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            setMargins(24, 24, 24, 16)
        }
        rootLayout.addView(btnGrant, grantParams)

        // Tabs Bar
        val tabsLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(0xFF128C7E.toInt())
        }
        
        tabImages = TextView(this).apply {
            text = "IMAGES"
            textSize = 14f
            gravity = Gravity.CENTER
            setTextColor(0xFFFFFFFF.toInt())
            setPadding(0, 24, 0, 24)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setOnClickListener { switchTab("images") }
        }
        
        tabVideos = TextView(this).apply {
            text = "VIDEOS"
            textSize = 14f
            gravity = Gravity.CENTER
            setTextColor(0xAAFFFFFF.toInt())
            setPadding(0, 24, 0, 24)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setOnClickListener { switchTab("videos") }
        }

        tabsLayout.addView(tabImages)
        tabsLayout.addView(tabVideos)
        rootLayout.addView(tabsLayout)

        // Scrollable Grid View
        val scroll = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT)
        }
        
        gridLayout = GridLayout(this).apply {
            columnCount = 2
            setPadding(16, 16, 16, 16)
        }
        scroll.addView(gridLayout)
        rootLayout.addView(scroll)

        setContentView(rootLayout)
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

        allFiles = docFile.listFiles().filter { it.name != ".nomedia" }
        renderGrid()
    }

    private fun switchTab(tab: String) {
        currentTab = tab
        if (tab == "images") {
            tabImages.setTextColor(0xFFFFFFFF.toInt())
            tabVideos.setTextColor(0xAAFFFFFF.toInt())
        } else {
            tabVideos.setTextColor(0xFFFFFFFF.toInt())
            tabImages.setTextColor(0xAAFFFFFF.toInt())
        }
        renderGrid()
    }

    private fun renderGrid() {
        gridLayout.removeAllViews()

        val filtered = allFiles.filter { file ->
            val name = file.name?.lowercase() ?: ""
            if (currentTab == "images") {
                name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".png")
            } else {
                name.endsWith(".mp4") || name.endsWith(".mkv")
            }
        }

        if (filtered.isEmpty()) {
            val emptyTv = TextView(this).apply {
                text = "\nNo $currentTab statuses found."
                textSize = 16f
            }
            gridLayout.addView(emptyTv)
            return
        }

        val screenWidth = resources.displayMetrics.widthPixels
        val cardWidth = (screenWidth / 2) - 24

        for (file in filtered) {
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundColor(0xFFFFFFFF.toInt())
                setPadding(8, 8, 8, 8)
                layoutParams = GridLayout.LayoutParams().apply {
                    width = cardWidth
                    height = (cardWidth * 1.4).toInt()
                    setMargins(8, 8, 8, 8)
                }
            }

            val thumb = ImageView(this).apply {
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
                scaleType = ImageView.ScaleType.CENTER_CROP
            }

            // Load thumbnail preview
            try {
                val inputStream: InputStream? = contentResolver.openInputStream(file.uri)
                if (currentTab == "images") {
                    val bitmap = BitmapFactory.decodeStream(inputStream)
                    thumb.setImageBitmap(bitmap)
                } else {
                    thumb.setBackgroundColor(0xFF000000.toInt()) // Video placeholder background
                }
                inputStream?.close()
            } catch (e: Exception) {
                thumb.setBackgroundColor(0xFFCCCCCC.toInt())
            }

            val saveBtn = Button(this).apply {
                text = "Download ⬇"
                textSize = 12f
                setBackgroundColor(0xFF128C7E.toInt())
                setTextColor(0xFFFFFFFF.toInt())
                setOnClickListener { saveToGallery(file) }
            }

            card.addView(thumb)
            card.addView(saveBtn)
            gridLayout.addView(card)
        }
    }

    private fun saveToGallery(file: DocumentFile) {
        try {
            val inputStream = contentResolver.openInputStream(file.uri) ?: return
            val mimeType = if (currentTab == "images") "image/jpeg" else "video/mp4"
            
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, file.name ?: "status_${System.currentTimeMillis()}")
                put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                put(MediaStore.MediaColumns.RELATIVE_PATH, if (currentTab == "images") Environment.DIRECTORY_PICTURES else Environment.DIRECTORY_MOVIES)
            }

            val collection = if (currentTab == "images") MediaStore.Images.Media.EXTERNAL_CONTENT_URI else MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            val targetUri = contentResolver.insert(collection, values)

            if (targetUri != null) {
                val outputStream: OutputStream? = contentResolver.openOutputStream(targetUri)
                if (outputStream != null) {
                    inputStream.copyTo(outputStream)
                    outputStream.close()
                }
                inputStream.close()
                Toast.makeText(this, "Saved to Gallery!", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to save file", Toast.LENGTH_SHORT).show()
        }
    }
}
