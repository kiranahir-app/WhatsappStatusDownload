package com.example.statussaver

import android.app.Activity
import android.app.AlertDialog
import android.content.ContentValues
import android.content.Intent
import android.net.Uri
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
import com.bumptech.glide.Glide
import java.io.InputStream
import java.io.OutputStream

class MainActivity : AppCompatActivity() {

    private lateinit var gridLayout: GridLayout
    private lateinit var tabImages: TextView
    private lateinit var tabVideos: TextView
    private lateinit var tabAudio: TextView
    private var allFiles: List<DocumentFile> = listOf()
    private var currentTab = "images"
    private var sortCriteria = "time_desc"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val rootLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFFF0F2F5.toInt())
        }

        // Header
        val header = TextView(this).apply {
            text = "Status Saver Pro"
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
            setMargins(24, 24, 24, 12)
        }
        rootLayout.addView(btnGrant, grantParams)

        // Sorting Bar
        val sortLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(24, 0, 24, 12)
            gravity = Gravity.CENTER_VERTICAL
        }
        val sortLabel = TextView(this).apply { text = "Sort by: "; textSize = 13f }
        val sortBtn = Button(this).apply {
            text = "Newest First ▾"
            textSize = 12f
            setOnClickListener { showSortDialog() }
        }
        sortLayout.addView(sortLabel)
        sortLayout.addView(sortBtn)
        rootLayout.addView(sortLayout)

        // Tabs Bar
        val tabsLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(0xFF128C7E.toInt())
        }
        
        tabImages = createTab("IMAGES", "images")
        tabVideos = createTab("VIDEOS", "videos")
        tabAudio = createTab("AUDIO", "audio")

        tabsLayout.addView(tabImages)
        tabsLayout.addView(tabVideos)
        tabsLayout.addView(tabAudio)
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

    private fun createTab(title: String, tabKey: String): TextView {
        return TextView(this).apply {
            text = title
            textSize = 13f
            gravity = Gravity.CENTER
            setTextColor(if (tabKey == "images") 0xFFFFFFFF.toInt() else 0xAAFFFFFF.toInt())
            setPadding(0, 24, 0, 24)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setOnClickListener { switchTab(tabKey) }
        }
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
        tabImages.setTextColor(if (tab == "images") 0xFFFFFFFF.toInt() else 0xAAFFFFFF.toInt())
        tabVideos.setTextColor(if (tab == "videos") 0xFFFFFFFF.toInt() else 0xAAFFFFFF.toInt())
        tabAudio.setTextColor(if (tab == "audio") 0xFFFFFFFF.toInt() else 0xAAFFFFFF.toInt())
        renderGrid()
    }

    private fun showSortDialog() {
        val options = arrayOf("Newest First", "Oldest First", "Name (A-Z)", "Name (Z-A)")
        AlertDialog.Builder(this)
            .setTitle("Sort Statuses")
            .setItems(options) { _, which ->
                sortCriteria = when (which) {
                    0 -> "time_desc"
                    1 -> "time_asc"
                    2 -> "name_asc"
                    else -> "name_desc"
                }
                renderGrid()
            }
            .show()
    }

    private fun renderGrid() {
        gridLayout.removeAllViews()

        val filtered = allFiles.filter { file ->
            val name = file.name?.lowercase() ?: ""
            when (currentTab) {
                "images" -> name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".png")
                "videos" -> name.endsWith(".mp4") || name.endsWith(".mkv") || name.endsWith(".avi")
                "audio" -> name.endsWith(".mp3") || name.endsWith(".aac") || name.endsWith(".ogg") || name.endsWith(".wav")
                else -> false
            }
        }.sortedWith(Comparator { f1, f2 ->
            when (sortCriteria) {
                "time_desc" -> f2.lastModified().compareTo(f1.lastModified())
                "time_asc" -> f1.lastModified().compareTo(f2.lastModified())
                "name_asc" -> (f1.name ?: "").compareTo(f2.name ?: "")
                "name_desc" -> (f2.name ?: "").compareTo(f1.name ?: "")
                else -> 0
            }
        })

        if (filtered.isEmpty()) {
            val emptyTv = TextView(this).apply {
                text = "\nNo $currentTab files found."
                textSize = 15f
                gravity = Gravity.CENTER
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
                    height = (cardWidth * 1.3).toInt()
                    setMargins(8, 8, 8, 8)
                }
            }

            val previewView = ImageView(this).apply {
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
                scaleType = ImageView.ScaleType.CENTER_CROP
                setBackgroundColor(0xFFE0E0E0.toInt())
            }

            // Lazy loading implementation using Glide library
            if (currentTab == "images") {
                Glide.with(this)
                    .load(file.uri)
                    .placeholder(android.R.drawable.ic_menu_gallery)
                    .into(previewView)

                // Tap thumbnail to view full-screen image
                previewView.setOnClickListener { showImagePreview(file.uri) }
            } else if (currentTab == "videos") {
                // Glide automatically extracts and caches video thumbnail frames efficiently!
                Glide.with(this)
                    .asBitmap()
                    .load(file.uri)
                    .placeholder(android.R.drawable.ic_media_play)
                    .into(previewView)

                previewView.setOnClickListener {
                    val intent = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(file.uri, "video/*")
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    startActivity(intent)
                }
            } else {
                previewView.setBackgroundColor(0xFF3F51B5.toInt())
                previewView.setOnClickListener {
                    val intent = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(file.uri, "audio/*")
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    startActivity(intent)
                }
            }

            val saveBtn = Button(this).apply {
                text = "Download ⬇"
                textSize = 11f
                setBackgroundColor(0xFF128C7E.toInt())
                setTextColor(0xFFFFFFFF.toInt())
                setOnClickListener { saveToGallery(file) }
            }

            card.addView(previewView)
            card.addView(saveBtn)
            gridLayout.addView(card)
        }
    }

    private fun showImagePreview(uri: Uri) {
        val imageView = ImageView(this).apply {
            adjustViewBounds = true
            maxHeight = 1500
        }
        // Lazy load full image into preview dialog
        Glide.with(this).load(uri).into(imageView)

        AlertDialog.Builder(this)
            .setView(imageView)
            .setPositiveButton("Close") { dialog, _ -> dialog.dismiss() }
            .show()
    }

    private fun saveToGallery(file: DocumentFile) {
        try {
            val inputStream = contentResolver.openInputStream(file.uri) ?: return
            val mimeType = when (currentTab) {
                "images" -> "image/jpeg"
                "videos" -> "video/mp4"
                else -> "audio/mpeg"
            }
            val folderDir = when (currentTab) {
                "images" -> Environment.DIRECTORY_PICTURES
                "videos" -> Environment.DIRECTORY_MOVIES
                else -> Environment.DIRECTORY_MUSIC
            }

            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, file.name ?: "media_${System.currentTimeMillis()}")
                put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                put(MediaStore.MediaColumns.RELATIVE_PATH, folderDir)
            }

            val collection = when (currentTab) {
                "images" -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                "videos" -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                else -> MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            }

            val targetUri = contentResolver.insert(collection, values)
            if (targetUri != null) {
                val outputStream: OutputStream? = contentResolver.openOutputStream(targetUri)
                if (outputStream != null) {
                    inputStream.copyTo(outputStream)
                    outputStream.close()
                }
                inputStream.close()
                Toast.makeText(this, "Successfully Downloaded to Gallery!", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Download failed", Toast.LENGTH_SHORT).show()
        }
    }
}
