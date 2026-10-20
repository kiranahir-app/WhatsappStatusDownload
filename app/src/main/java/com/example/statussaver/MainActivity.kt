package com.example.statussaver

import android.app.Activity
import android.app.AlertDialog
import android.content.ContentValues
import android.content.Context
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
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.documentfile.provider.DocumentFile
import com.bumptech.glide.Glide
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream

class MainActivity : AppCompatActivity() {

    private lateinit var gridLayout: GridLayout
    private lateinit var tabImages: TextView
    private lateinit var tabVideos: TextView
    private lateinit var tabAudio: TextView
    private lateinit var tabSaved: TextView
    private lateinit var tabSettings: TextView
    private lateinit var btnTimeFilter: Button
    private var allFiles: List<DocumentFile> = listOf()
    private var currentTab = "images"
    private var sortCriteria = "time_desc"
    private var selectedHourFilter = 0 
    private var savedUri: Uri? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val rootLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFFF0F2F5.toInt())
        }

        val header = TextView(this).apply {
            text = "Status Saver Pro"
            textSize = 20f
            setTextColor(0xFFFFFFFF.toInt())
            setBackgroundColor(0xFF128C7E.toInt())
            setPadding(32, 32, 32, 32)
            gravity = Gravity.CENTER
        }
        rootLayout.addView(header)

        val toolbar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(16, 16, 16, 8)
            gravity = Gravity.CENTER_VERTICAL
        }

        val btnGrant = Button(this).apply {
            text = "Folder"
            textSize = 12f
            setBackgroundColor(0xFF25D366.toInt())
            setTextColor(0xFFFFFFFF.toInt())
            setOnClickListener { requestFolderPermission() }
        }

        val btnRefresh = Button(this).apply {
            text = "🔄 Refresh"
            textSize = 12f
            setBackgroundColor(0xFF075E54.toInt())
            setTextColor(0xFFFFFFFF.toInt())
            setOnClickListener { refreshStatuses() }
        }

        val btnSort = Button(this).apply {
            text = "Sort ▾"
            textSize = 12f
            setOnClickListener { showSortDialog() }
        }

        btnTimeFilter = Button(this).apply {
            text = "Time: All ▾"
            textSize = 12f
            setOnClickListener { showTimeFilterDialog() }
        }

        toolbar.addView(btnGrant, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { setMargins(2,0,2,0) })
        toolbar.addView(btnRefresh, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { setMargins(2,0,2,0) })
        toolbar.addView(btnSort, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { setMargins(2,0,2,0) })
        toolbar.addView(btnTimeFilter, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { setMargins(2,0,2,0) })
        rootLayout.addView(toolbar)

        val tabsLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(0xFF128C7E.toInt())
        }
        
        tabImages = createTab("IMG", "images")
        tabVideos = createTab("VID", "videos")
        tabAudio = createTab("AUD", "audio")
        tabSaved = createTab("SAVED", "saved")
        tabSettings = createTab("SETTING", "settings")

        tabsLayout.addView(tabImages)
        tabsLayout.addView(tabVideos)
        tabsLayout.addView(tabAudio)
        tabsLayout.addView(tabSaved)
        tabsLayout.addView(tabSettings)
        rootLayout.addView(tabsLayout)

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
        checkPersistedPermission()
    }

    private fun createTab(title: String, tabKey: String): TextView {
        return TextView(this).apply {
            text = title
            textSize = 11f
            gravity = Gravity.CENTER
            setTextColor(if (tabKey == "images") 0xFFFFFFFF.toInt() else 0xAAFFFFFF.toInt())
            setPadding(0, 24, 0, 24)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setOnClickListener { switchTab(tabKey) }
        }
    }

    private fun checkPersistedPermission() {
        val persistedUriPermissions = contentResolver.persistedUriPermissions
        for (permission in persistedUriPermissions) {
            if (permission.isReadPermission) {
                savedUri = permission.uri
                loadStatuses(savedUri!!)
                break
            }
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
                savedUri = uri
                loadStatuses(uri)
                Toast.makeText(this, "Folder Saved & Loaded!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun refreshStatuses() {
        if (currentTab == "saved" || currentTab == "settings") {
            renderGrid()
            Toast.makeText(this, "Refreshed!", Toast.LENGTH_SHORT).show()
        } else if (savedUri != null) {
            loadStatuses(savedUri!!)
            Toast.makeText(this, "Refreshed statuses!", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Please select WhatsApp folder first", Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadStatuses(uri: Uri) {
        CoroutineScope(Dispatchers.IO).launch {
            val docFile = DocumentFile.fromTreeUri(this@MainActivity, uri)
            if (docFile != null && docFile.exists()) {
                allFiles = docFile.listFiles().filter { it.name != ".nomedia" }
                
                val prefs = getSharedPreferences("StatusSaverPrefs", Context.MODE_PRIVATE)
                if (prefs.getBoolean("auto_save_enabled", false)) {
                    autoSaveNewStatuses()
                }
            }
            withContext(Dispatchers.Main) {
                renderGrid()
            }
        }
    }

    private fun autoSaveNewStatuses() {
        for (file in allFiles) {
            try {
                val inputStream = contentResolver.openInputStream(file.uri) ?: continue
                val name = file.name ?: "autosave_${System.currentTimeMillis()}"
                val mimeType = if (name.endsWith(".mp4")) "video/mp4" else "image/jpeg"
                val folderDir = if (name.endsWith(".mp4")) Environment.DIRECTORY_MOVIES else Environment.DIRECTORY_PICTURES

                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                    put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                    put(MediaStore.MediaColumns.RELATIVE_PATH, folderDir)
                }
                val collection = if (name.endsWith(".mp4")) MediaStore.Video.Media.EXTERNAL_CONTENT_URI else MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                val targetUri = contentResolver.insert(collection, values)
                if (targetUri != null) {
                    val out = contentResolver.openOutputStream(targetUri)
                    if (out != null) {
                        inputStream.copyTo(out)
                        out.close()
                    }
                }
                inputStream.close()
            } catch (e: Exception) {}
        }
    }

    private fun switchTab(tab: String) {
        currentTab = tab
        tabImages.setTextColor(if (tab == "images") 0xFFFFFFFF.toInt() else 0xAAFFFFFF.toInt())
        tabVideos.setTextColor(if (tab == "videos") 0xFFFFFFFF.toInt() else 0xAAFFFFFF.toInt())
        tabAudio.setTextColor(if (tab == "audio") 0xFFFFFFFF.toInt() else 0xAAFFFFFF.toInt())
        tabSaved.setTextColor(if (tab == "saved") 0xFFFFFFFF.toInt() else 0xAAFFFFFF.toInt())
        tabSettings.setTextColor(if (tab == "settings") 0xFFFFFFFF.toInt() else 0xAAFFFFFF.toInt())
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

    private fun showTimeFilterDialog() {
        val options = arrayOf("All Statuses", "Last 1 Hour", "Last 2 Hours", "Last 3 Hours", "Last 4 Hours", "Last 5 Hours", "Today Only")
        AlertDialog.Builder(this)
            .setTitle("Filter by Time")
            .setItems(options) { _, which ->
                selectedHourFilter = which 
                btnTimeFilter.text = when(which) {
                    0 -> "Time: All ▾"
                    6 -> "Time: Today ▾"
                    else -> "Time: ${which}h ▾"
                }
                renderGrid()
            }
            .show()
    }

    private fun renderGrid() {
        gridLayout.removeAllViews()

        if (currentTab == "settings") {
            renderSettingsView()
            return
        }

        if (currentTab == "saved") {
            renderSavedFilesGrid()
            return
        }

        val currentTime = System.currentTimeMillis()
        val filtered = allFiles.filter { file ->
            val name = file.name?.lowercase() ?: ""
            val matchesTab = when (currentTab) {
                "images" -> name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".png")
                "videos" -> name.endsWith(".mp4") || name.endsWith(".mkv") || name.endsWith(".avi")
                "audio" -> name.endsWith(".mp3") || name.endsWith(".aac") || name.endsWith(".ogg") || name.endsWith(".wav")
                else -> false
            }

            if (!matchesTab) return@filter false

            val fileTime = file.lastModified()
            when (selectedHourFilter) {
                in 1..5 -> (currentTime - fileTime) <= (selectedHourFilter * 60 * 60 * 1000L)
                6 -> (currentTime - fileTime) <= (24 * 60 * 60 * 1000L)
                else -> true
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
                text = "\nNo files match this time filter."
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
                    height = (cardWidth * 1.45).toInt()
                    setMargins(8, 8, 8, 8)
                }
            }

            val previewView = ImageView(this).apply {
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
                scaleType = ImageView.ScaleType.CENTER_CROP
                setBackgroundColor(0xFFE0E0E0.toInt())
            }

            if (currentTab == "images") {
                Glide.with(this).load(file.uri).into(previewView)
                previewView.setOnClickListener { showImagePreview(file.uri) }
            } else if (currentTab == "videos") {
                Glide.with(this).asBitmap().load(file.uri).into(previewView)
                previewView.setOnClickListener { showVideoPreview(file.uri) }
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

            val btnLayout = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, 4, 0, 0)
            }

            val saveBtn = Button(this).apply {
                text = "Download"
                textSize = 10f
                setBackgroundColor(0xFF128C7E.toInt())
                setTextColor(0xFFFFFFFF.toInt())
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { setMargins(2,0,2,0) }
                setOnClickListener { saveToGallery(file) }
            }

            val shareBtn = Button(this).apply {
                text = "Share"
                textSize = 10f
                setBackgroundColor(0xFF25D366.toInt())
                setTextColor(0xFFFFFFFF.toInt())
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { setMargins(2,0,2,0) }
                setOnClickListener { shareFile(file.uri) }
            }

            btnLayout.addView(saveBtn)
            btnLayout.addView(shareBtn)

            card.addView(previewView)
            card.addView(btnLayout)
            gridLayout.addView(card)
        }
    }

    private fun renderSettingsView() {
        val settingsLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }

        val title = TextView(this).apply {
            text = "App Settings"
            textSize = 18f
            setPadding(0, 0, 0, 32)
        }
        settingsLayout.addView(title)

        val prefs = getSharedPreferences("StatusSaverPrefs", Context.MODE_PRIVATE)
        val autoSaveSwitch = Switch(this).apply {
            text = "Auto-save Statuses on Refresh"
            isChecked = prefs.getBoolean("auto_save_enabled", false)
            setOnCheckedChangeListener { _, isChecked ->
                prefs.edit().putBoolean("auto_save_enabled", isChecked).apply()
                Toast.makeText(this@MainActivity, if (isChecked) "Auto-save Enabled" else "Auto-save Disabled", Toast.LENGTH_SHORT).show()
            }
        }
        settingsLayout.addView(autoSaveSwitch)
        gridLayout.addView(settingsLayout)
    }

    private fun renderSavedFilesGrid() {
        val savedUris = mutableListOf<Uri>()
        val projection = arrayOf(MediaStore.MediaColumns._ID)
        val sortOrder = "${MediaStore.MediaColumns.DATE_ADDED} DESC"

        contentResolver.query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, projection, null, null, sortOrder)?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                savedUris.add(Uri.withAppendedPath(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id.toString()))
            }
        }

        if (savedUris.isEmpty()) {
            val emptyTv = TextView(this).apply {
                text = "\nNo downloaded statuses yet."
                textSize = 15f
                gravity = Gravity.CENTER
            }
            gridLayout.addView(emptyTv)
            return
        }

        val screenWidth = resources.displayMetrics.widthPixels
        val cardWidth = (screenWidth / 2) - 24

        for (uri in savedUris) {
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

            Glide.with(this).load(uri).into(previewView)
            previewView.setOnClickListener { showImagePreview(uri) }

            val statusBtn = Button(this).apply {
                text = "Saved ✓"
                textSize = 11f
                isEnabled = false
                setBackgroundColor(0xFF888888.toInt())
                setTextColor(0xFFFFFFFF.toInt())
            }

            card.addView(previewView)
            card.addView(statusBtn)
            gridLayout.addView(card)
        }
    }

    private fun showImagePreview(uri: Uri) {
        val imageView = ImageView(this).apply {
            adjustViewBounds = true
            maxHeight = 1500
        }
        Glide.with(this).load(uri).into(imageView)
        AlertDialog.Builder(this)
            .setView(imageView)
            .setPositiveButton("Close") { dialog, _ -> dialog.dismiss() }
            .show()
    }

    private fun showVideoPreview(uri: Uri) {
        val videoView = android.widget.VideoView(this).apply {
            setVideoURI(uri)
            setMediaController(android.widget.MediaController(this@MainActivity))
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 800)
        }
        val dialog = AlertDialog.Builder(this)
            .setView(videoView)
            .setPositiveButton("Close") { d, _ -> 
                videoView.stopPlayback()
                d.dismiss() 
            }
            .create()
        dialog.show()
        videoView.start()
    }

    private fun shareFile(uri: Uri) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "*/*"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, "Share Status via"))
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
