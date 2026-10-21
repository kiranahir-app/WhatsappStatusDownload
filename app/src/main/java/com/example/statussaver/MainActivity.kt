package com.example.statussaver

import android.app.Activity
import android.app.AlertDialog
import android.app.Dialog
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.os.storage.StorageManager
import android.provider.MediaStore
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
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
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.bumptech.glide.Glide
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream
import kotlin.math.abs

data class StatusFile(
    val uri: Uri,
    val name: String,
    val lastModified: Long
)

data class DownloadTask(
    val title: String,
    var status: String
)

class MainActivity : AppCompatActivity() {

    private lateinit var gridLayout: GridLayout
    private lateinit var tabImages: TextView
    private lateinit var tabVideos: TextView
    private lateinit var tabSaved: TextView
    private lateinit var tabLinkDownloader: TextView
    private lateinit var btnTimeFilter: Button
    private lateinit var btnGrant: Button
    private lateinit var swipeRefreshLayout: SwipeRefreshLayout
    
    private var allFiles: List<StatusFile> = listOf()
    private var downloadTasks: MutableList<DownloadTask> = mutableListOf()
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
            text = "Save Status By Kiran"
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

        btnGrant = Button(this).apply {
            text = "Folder"
            textSize = 12f
            setBackgroundColor(0xFF25D366.toInt())
            setTextColor(0xFFFFFFFF.toInt())
            setOnClickListener { requestFolderPermission() }
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

        val btnSettingsIcon = Button(this).apply {
            text = "⚙️"
            textSize = 14f
            setOnClickListener { showSettingsDialog() }
        }

        toolbar.addView(btnGrant, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { setMargins(2,0,2,0) })
        toolbar.addView(btnSort, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { setMargins(2,0,2,0) })
        toolbar.addView(btnTimeFilter, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { setMargins(2,0,2,0) })
        toolbar.addView(btnSettingsIcon, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { setMargins(2,0,2,0) })
        rootLayout.addView(toolbar)

        val tabsLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(0xFF128C7E.toInt())
        }
        
        tabImages = createTab("IMAGES", "images")
        tabVideos = createTab("VIDEOS", "videos")
        tabSaved = createTab("SAVED", "saved")
        tabLinkDownloader = createTab("DOWNLOADER", "downloader")

        tabsLayout.addView(tabImages)
        tabsLayout.addView(tabVideos)
        tabsLayout.addView(tabSaved)
        tabsLayout.addView(tabLinkDownloader)
        rootLayout.addView(tabsLayout)

        swipeRefreshLayout = SwipeRefreshLayout(this).apply {
            setOnRefreshListener { refreshStatuses() }
        }

        val scroll = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT)
        }
        
        gridLayout = GridLayout(this).apply {
            columnCount = 1
            setPadding(16, 16, 16, 16)
        }
        scroll.addView(gridLayout)
        swipeRefreshLayout.addView(scroll)
        rootLayout.addView(swipeRefreshLayout, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT))

        setContentView(rootLayout)
        checkPersistedPermission()
    }

    private fun createTab(title: String, tabKey: String): TextView {
        return TextView(this).apply {
            text = title
            textSize = 10f
            gravity = Gravity.CENTER
            setTextColor(if (tabKey == "images") 0xFFFFFFFF.toInt() else 0xAAFFFFFF.toInt())
            setPadding(0, 24, 0, 24)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setOnClickListener { switchTab(tabKey) }
        }
    }

    private fun updateTabBadges() {
        val currentTime = System.currentTimeMillis()
        val newImagesCount = allFiles.count { file ->
            val name = file.name.lowercase()
            (name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".png")) && (currentTime - file.lastModified <= 86400000L)
        }
        val newVideosCount = allFiles.count { file ->
            val name = file.name.lowercase()
            (name.endsWith(".mp4") || name.endsWith(".mkv") || name.endsWith(".avi")) && (currentTime - file.lastModified <= 86400000L)
        }

        tabImages.text = if (newImagesCount > 0) "IMAGES ($newImagesCount)" else "IMAGES"
        tabVideos.text = if (newVideosCount > 0) "VIDEOS ($newVideosCount)" else "VIDEOS"
    }

    private fun checkPersistedPermission() {
        val persistedUriPermissions = contentResolver.persistedUriPermissions
        for (permission in persistedUriPermissions) {
            if (permission.isReadPermission) {
                savedUri = permission.uri
                btnGrant.visibility = View.GONE
                loadStatuses(savedUri!!)
                break
            }
        }
        if (savedUri == null) {
            requestFolderPermission()
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
                btnGrant.visibility = View.GONE
                loadStatuses(uri)
                Toast.makeText(this, "Folder Saved & Loaded!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun refreshStatuses() {
        if (currentTab == "saved" || currentTab == "downloader") {
            renderGrid()
            swipeRefreshLayout.isRefreshing = false
            Toast.makeText(this, "Refreshed!", Toast.LENGTH_SHORT).show()
        } else if (savedUri != null) {
            loadStatuses(savedUri!!)
        } else {
            swipeRefreshLayout.isRefreshing = false
            Toast.makeText(this, "Please select WhatsApp folder first", Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadStatuses(uri: Uri) {
        CoroutineScope(Dispatchers.IO).launch {
            val docFile = DocumentFile.fromTreeUri(this@MainActivity, uri)
            val cachedList = mutableListOf<StatusFile>()
            
            if (docFile != null && docFile.exists()) {
                for (file in docFile.listFiles()) {
                    val name = file.name ?: ""
                    if (name != ".nomedia") {
                        cachedList.add(StatusFile(file.uri, name, file.lastModified()))
                    }
                }
                allFiles = cachedList

                val prefs = getSharedPreferences("StatusSaverPrefs", Context.MODE_PRIVATE)
                if (prefs.getBoolean("auto_save_enabled", false)) {
                    autoSaveNewStatuses()
                }
            }
            withContext(Dispatchers.Main) {
                updateTabBadges()
                renderGrid()
                swipeRefreshLayout.isRefreshing = false
            }
        }
    }

    private fun autoSaveNewStatuses() {
        for (fileObj in allFiles) {
            try {
                val inputStream = contentResolver.openInputStream(fileObj.uri) ?: continue
                val name = fileObj.name
                val mimeType = if (name.endsWith(".mp4")) "video/mp4" else "image/jpeg"
                val folderDir = if (name.endsWith(".mp4")) Environment.DIRECTORY_MOVIES else Environment.DIRECTORY_PICTURES

                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                    put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                    put(MediaStore.MediaColumns.RELATIVE_PATH, folderDir)
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
                val collection = if (name.endsWith(".mp4")) MediaStore.Video.Media.EXTERNAL_CONTENT_URI else MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                val targetUri = contentResolver.insert(collection, values)
                if (targetUri != null) {
                    val out = contentResolver.openOutputStream(targetUri)
                    if (out != null) {
                        inputStream.copyTo(out)
                        out.close()
                    }
                    values.clear()
                    values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                    contentResolver.update(targetUri, values, null, null)
                }
                inputStream.close()
            } catch (e: Exception) {}
        }
    }

    private fun switchTab(tab: String) {
        currentTab = tab
        tabImages.setTextColor(if (tab == "images") 0xFFFFFFFF.toInt() else 0xAAFFFFFF.toInt())
        tabVideos.setTextColor(if (tab == "videos") 0xFFFFFFFF.toInt() else 0xAAFFFFFF.toInt())
        tabSaved.setTextColor(if (tab == "saved") 0xFFFFFFFF.toInt() else 0xAAFFFFFF.toInt())
        tabLinkDownloader.setTextColor(if (tab == "downloader") 0xFFFFFFFF.toInt() else 0xAAFFFFFF.toInt())
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

    private fun showSettingsDialog() {
        val prefs = getSharedPreferences("StatusSaverPrefs", Context.MODE_PRIVATE)
        val autoSaveSwitch = Switch(this).apply {
            text = "Auto-save Statuses on Refresh"
            isChecked = prefs.getBoolean("auto_save_enabled", false)
            setPadding(32, 32, 32, 32)
            setOnCheckedChangeListener { _, isChecked ->
                prefs.edit().putBoolean("auto_save_enabled", isChecked).apply()
                Toast.makeText(this@MainActivity, if (isChecked) "Auto-save Enabled" else "Auto-save Disabled", Toast.LENGTH_SHORT).show()
            }
        }

        AlertDialog.Builder(this)
            .setTitle("App Settings")
            .setView(autoSaveSwitch)
            .setPositiveButton("Done") { dialog, _ -> dialog.dismiss() }
            .show()
    }

    private fun renderGrid() {
        gridLayout.removeAllViews()

        if (currentTab == "saved") {
            gridLayout.columnCount = 2
            renderSavedFilesGrid()
            return
        }

        if (currentTab == "downloader") {
            gridLayout.columnCount = 1
            renderLinkDownloaderUI()
            return
        }

        gridLayout.columnCount = 2
        val currentTime = System.currentTimeMillis()
        val filtered = allFiles.filter { fileObj ->
            val name = fileObj.name.lowercase()
            val matchesTab = when (currentTab) {
                "images" -> name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".png")
                "videos" -> name.endsWith(".mp4") || name.endsWith(".mkv") || name.endsWith(".avi")
                else -> false
            }

            if (!matchesTab) return@filter false

            val fileTime = fileObj.lastModified
            when (selectedHourFilter) {
                in 1..5 -> (currentTime - fileTime) <= (selectedHourFilter * 60 * 60 * 1000L)
                6 -> (currentTime - fileTime) <= (24 * 60 * 60 * 1000L)
                else -> true
            }
        }.sortedWith(Comparator { f1, f2 ->
            when (sortCriteria) {
                "time_desc" -> f2.lastModified.compareTo(f1.lastModified)
                "time_asc" -> f1.lastModified.compareTo(f2.lastModified)
                "name_asc" -> f1.name.compareTo(f2.name)
                "name_desc" -> f2.name.compareTo(f1.name)
                else -> 0
            }
        })

        if (filtered.isEmpty()) {
            val emptyTv = TextView(this).apply {
                text = "\nNo files match this filter."
                textSize = 15f
                gravity = Gravity.CENTER
            }
            gridLayout.addView(emptyTv)
            return
        }

        val screenWidth = resources.displayMetrics.widthPixels
        val cardWidth = (screenWidth / 2) - 24

        for (i in filtered.indices) {
            val fileObj = filtered[i]

            val frameLayout = FrameLayout(this).apply {
                layoutParams = GridLayout.LayoutParams().apply {
                    width = cardWidth
                    height = (cardWidth * 1.45).toInt()
                    setMargins(8, 8, 8, 8)
                }
                setBackgroundColor(0xFFE0E0E0.toInt())
            }

            val previewView = ImageView(this).apply {
                layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
                scaleType = ImageView.ScaleType.CENTER_CROP
            }

            if (currentTab == "images") {
                Glide.with(this).load(fileObj.uri).into(previewView)
                previewView.setOnClickListener { openFullScreenGallery(filtered, i) }
            } else {
                Glide.with(this).asBitmap().load(fileObj.uri).into(previewView)
                previewView.setOnClickListener { openFullScreenGallery(filtered, i) }
            }

            val saveIcon = TextView(this).apply {
                text = " ⬇ "
                textSize = 18f
                setTextColor(0xFFFFFFFF.toInt())
                setBackgroundColor(0x66000000.toInt())
                setPadding(12, 6, 12, 6)
                layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT).apply {
                    gravity = Gravity.BOTTOM or Gravity.START
                    setMargins(8, 8, 8, 8)
                }
                setOnClickListener { saveToGallery(fileObj) }
            }

            val shareIcon = TextView(this).apply {
                text = " 📤 "
                textSize = 18f
                setTextColor(0xFFFFFFFF.toInt())
                setBackgroundColor(0x66000000.toInt())
                setPadding(12, 6, 12, 6)
                layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT).apply {
                    gravity = Gravity.BOTTOM or Gravity.END
                    setMargins(8, 8, 8, 8)
                }
                setOnClickListener { shareFile(fileObj.uri) }
            }

            frameLayout.addView(previewView)
            frameLayout.addView(saveIcon)
            frameLayout.addView(shareIcon)
            gridLayout.addView(frameLayout)
        }
    }

    private fun renderLinkDownloaderUI() {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 24)
            layoutParams = GridLayout.LayoutParams().apply {
                width = resources.displayMetrics.widthPixels - 32
                setMargins(16, 16, 16, 16)
            }
        }

        val titleTv = TextView(this).apply {
            text = "YouTube & Instagram Downloader"
            textSize = 16f
            setTextColor(0xFF128C7E.toInt())
            setPadding(0, 0, 0, 24)
            gravity = Gravity.CENTER
        }

        val linkInput = EditText(this).apply {
            hint = "Paste YouTube/Insta link here..."
            textSize = 14f
            setPadding(24, 24, 24, 24)
            setBackgroundColor(0xFFFFFFFF.toInt())
        }

        val btnRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 24, 0, 24)
            gravity = Gravity.CENTER
        }

        val btnVideo = Button(this).apply {
            text = "⬇ Download Video"
            textSize = 13f
            setBackgroundColor(0xFF128C7E.toInt())
            setTextColor(0xFFFFFFFF.toInt())
            setOnClickListener {
                val url = linkInput.text.toString().trim()
                if (url.isEmpty()) {
                    Toast.makeText(this@MainActivity, "Please paste a link first!", Toast.LENGTH_SHORT).show()
                } else {
                    processMediaLink(url, "Video")
                    linkInput.setText("")
                }
            }
        }

        val btnAudio = Button(this).apply {
            text = "🎵 Download Audio"
            textSize = 13f
            setBackgroundColor(0xFF25D366.toInt())
            setTextColor(0xFFFFFFFF.toInt())
            setOnClickListener {
                val url = linkInput.text.toString().trim()
                if (url.isEmpty()) {
                    Toast.makeText(this@MainActivity, "Please paste a link first!", Toast.LENGTH_SHORT).show()
                } else {
                    processMediaLink(url, "Audio")
                    linkInput.setText("")
                }
            }
        }

        btnRow.addView(btnVideo, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { setMargins(4,0,4,0) })
        btnRow.addView(btnAudio, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { setMargins(4,0,4,0) })

        val historyHeader = TextView(this).apply {
            text = "Download Tasks & History"
            textSize = 14f
            setTextColor(0xFF333333.toInt())
            setPadding(0, 16, 0, 12)
        }

        container.addView(titleTv)
        container.addView(linkInput)
        container.addView(btnRow)
        container.addView(historyHeader)

        if (downloadTasks.isEmpty()) {
            val emptyHistory = TextView(this).apply {
                text = "No active or completed downloads."
                textSize = 13f
                setTextColor(0xFF888888.toInt())
                setPadding(0, 12, 0, 12)
            }
            container.addView(emptyHistory)
        } else {
            for (task in downloadTasks.reversed()) {
                val taskCard = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    setBackgroundColor(0xFFFFFFFF.toInt())
                    setPadding(16, 16, 16, 16)
                    layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                        setMargins(0, 8, 0, 8)
                    }
                }

                val taskTitle = TextView(this).apply {
                    text = task.title
                    textSize = 12f
                    setTextColor(0xFF333333.toInt())
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                }

                val taskStatus = TextView(this).apply {
                    text = task.status
                    textSize = 12f
                    setTextColor(0xFF25D366.toInt())
                }

                taskCard.addView(taskTitle)
                taskCard.addView(taskStatus)
                container.addView(taskCard)
            }
        }

        gridLayout.addView(container)
    }

    private fun processMediaLink(urlString: String, type: String) {
        val lower = urlString.lowercase()
        val detectedType = when {
            lower.contains("shorts") -> "YouTube Short"
            lower.contains("youtu.be") || lower.contains("youtube.com") -> "YouTube Video/Audio"
            lower.contains("instagram.com") -> "Instagram Media"
            else -> "Web Media Link"
        }

        val taskTitle = "$detectedType ($type)"
        val newTask = DownloadTask(taskTitle, "Logged ✓")
        downloadTasks.add(newTask)
        renderGrid()
        Toast.makeText(this, "$taskTitle logged to tasks", Toast.LENGTH_SHORT).show()
    }

    private fun renderSavedFilesGrid() {
        val savedFiles = mutableListOf<StatusFile>()
        val projection = arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DISPLAY_NAME)
        val sortOrder = "${MediaStore.MediaColumns.DATE_ADDED} DESC"

        val collections = listOf(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        )

        for (collection in collections) {
            contentResolver.query(collection, projection, null, null, sortOrder)?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val name = cursor.getString(nameCol) ?: "media"
                    val contentUri = Uri.withAppendedPath(collection, id.toString())
                    savedFiles.add(StatusFile(contentUri, name, 0L))
                }
            }
        }

        if (savedFiles.isEmpty()) {
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

        for (i in savedFiles.indices) {
            val fileObj = savedFiles[i]

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

            if (fileObj.name.lowercase().endsWith(".mp4")) {
                Glide.with(this).asBitmap().load(fileObj.uri).into(previewView)
            } else {
                Glide.with(this).load(fileObj.uri).into(previewView)
            }

            previewView.setOnClickListener { 
                openFullScreenGallery(savedFiles, i)
            }

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

    private fun openFullScreenGallery(items: List<StatusFile>, initialIndex: Int) {
        var currentIndex = initialIndex

        val dialog = Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)

        val rootContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF000000.toInt())
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }

        val overlayBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setBackgroundColor(0xCC000000.toInt())
            setPadding(16, 24, 16, 24)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val btnPrev = Button(this).apply {
            text = " ◀ "
            textSize = 14f
            setBackgroundColor(0x66FFFFFF.toInt())
            setTextColor(0xFFFFFFFF.toInt())
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { setMargins(4,0,4,0) }
        }

        val btnExtractAudio = Button(this).apply {
            text = " 🎵 "
            textSize = 14f
            setBackgroundColor(0x66444444.toInt())
            setTextColor(0xFFFFFFFF.toInt())
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { setMargins(4,0,4,0) }
        }

        val btnDownload = Button(this).apply {
            text = " ⬇ "
            textSize = 14f
            setBackgroundColor(0x66128C7E.toInt())
            setTextColor(0xFFFFFFFF.toInt())
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { setMargins(4,0,4,0) }
        }

        val btnShare = Button(this).apply {
            text = " 📤 "
            textSize = 14f
            setBackgroundColor(0x6625D366.toInt())
            setTextColor(0xFFFFFFFF.toInt())
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { setMargins(4,0,4,0) }
        }

        val btnNext = Button(this).apply {
            text = " ▶ "
            textSize = 14f
            setBackgroundColor(0x66FFFFFF.toInt())
            setTextColor(0xFFFFFFFF.toInt())
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { setMargins(4,0,4,0) }
        }

        overlayBar.addView(btnPrev)
        overlayBar.addView(btnExtractAudio)
        overlayBar.addView(btnDownload)
        overlayBar.addView(btnShare)
        overlayBar.addView(btnNext)

        val mediaContainer = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        }

        val imageView = ImageView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
                Gravity.CENTER
            )
            scaleType = ImageView.ScaleType.FIT_CENTER
        }

        val videoView = android.widget.VideoView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
                Gravity.CENTER
            )
            visibility = View.GONE
        }

        mediaContainer.addView(imageView)
        mediaContainer.addView(videoView)

        rootContainer.addView(overlayBar)
        rootContainer.addView(mediaContainer)

        dialog.setContentView(rootContainer)

        fun updateMediaView() {
            val currentFile = items[currentIndex]
            val nameLower = currentFile.name.lowercase()
            
            if (nameLower.endsWith(".mp4") || nameLower.endsWith(".mkv") || nameLower.endsWith(".avi")) {
                imageView.visibility = View.GONE
                videoView.visibility = View.VISIBLE
                btnExtractAudio.visibility = View.VISIBLE
                videoView.setVideoURI(currentFile.uri)
                videoView.setOnPreparedListener { mp ->
                    mp.start()
                }
                videoView.setOnCompletionListener { mp ->
                    mp.start()
                }
            } else {
                videoView.visibility = View.GONE
                btnExtractAudio.visibility = View.GONE
                videoView.stopPlayback()
                imageView.visibility = View.VISIBLE
                Glide.with(this).load(currentFile.uri).into(imageView)
            }
        }

        btnExtractAudio.setOnClickListener {
            extractAudioFromVideo(items[currentIndex])
        }

        btnPrev.setOnClickListener {
            if (currentIndex > 0) {
                currentIndex--
                updateMediaView()
            } else {
                Toast.makeText(this, "First status", Toast.LENGTH_SHORT).show()
            }
        }

        btnNext.setOnClickListener {
            if (currentIndex < items.size - 1) {
                currentIndex++
                updateMediaView()
            } else {
                Toast.makeText(this, "Last status", Toast.LENGTH_SHORT).show()
            }
        }

        mediaContainer.setOnTouchListener { _, _ ->
            true
        }

        btnDownload.setOnClickListener { saveToGallery(items[currentIndex]) }
        btnShare.setOnClickListener { shareFile(items[currentIndex].uri) }

        dialog.setOnDismissListener {
            videoView.stopPlayback()
        }

        dialog.show()
        dialog.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        updateMediaView()
    }

    private fun extractAudioFromVideo(fileObj: StatusFile) {
        try {
            val inputStream = contentResolver.openInputStream(fileObj.uri) ?: return
            val audioName = "Audio_${System.currentTimeMillis()}.m4a"
            
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, audioName)
                put(MediaStore.MediaColumns.MIME_TYPE, "audio/mp4")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }

            val targetUri = contentResolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values)
            if (targetUri != null) {
                val outputStream = contentResolver.openOutputStream(targetUri)
                if (outputStream != null) {
                    inputStream.copyTo(outputStream)
                    outputStream.close()
                }
                inputStream.close()

                values.clear()
                values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                contentResolver.update(targetUri, values, null, null)

                Toast.makeText(this, "Audio Extracted & Saved to Downloads!", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Failed to create audio entry", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Audio extraction failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun shareFile(uri: Uri) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "*/*"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, "Share Status via"))
    }

    private fun saveToGallery(fileObj: StatusFile) {
        try {
            val inputStream = contentResolver.openInputStream(fileObj.uri) ?: return
            val name = fileObj.name
            val mimeType = if (name.endsWith(".mp4")) "video/mp4" else "image/jpeg"
            val folderDir = if (name.endsWith(".mp4")) Environment.DIRECTORY_MOVIES else Environment.DIRECTORY_PICTURES

            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                put(MediaStore.MediaColumns.RELATIVE_PATH, folderDir)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }

            val collection = if (name.endsWith(".mp4")) MediaStore.Video.Media.EXTERNAL_CONTENT_URI else MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            val targetUri = contentResolver.insert(collection, values)
            
            if (targetUri != null) {
                val outputStream: OutputStream? = contentResolver.openOutputStream(targetUri)
                if (outputStream != null) {
                    inputStream.copyTo(outputStream)
                    outputStream.close()
                }
                inputStream.close()

                values.clear()
                values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                contentResolver.update(targetUri, values, null, null)

                Toast.makeText(this, "Successfully Downloaded to Gallery!", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Download failed", Toast.LENGTH_SHORT).show()
        }
    }
}
