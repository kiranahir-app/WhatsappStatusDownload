package com.example.statussaver

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.storage.StorageManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Build the layout programmatically so we do not need XML layout files
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(64, 128, 64, 64)
        }
        
        val title = TextView(this).apply {
            text = "Status Saver"
            textSize = 28f
            setPadding(0, 0, 0, 64)
        }
        layout.addView(title)
        
        val btnGrant = Button(this).apply {
            text = "1. Grant WhatsApp Folder Permission"
            setOnClickListener { requestFolderPermission() }
        }
        layout.addView(btnGrant)
        
        setContentView(layout)
    }

    private fun requestFolderPermission() {
        val sm = getSystemService(STORAGE_SERVICE) as StorageManager
        val intent = sm.primaryStorageVolume.createOpenDocumentTreeIntent()
        
        // Automatically navigate to the hidden WhatsApp Statuses folder for the user
        var uri = intent.getParcelableExtra<Uri>("android.provider.extra.INITIAL_URI")
        var scheme = uri.toString()
        scheme = scheme.replace("/root/", "/document/")
        scheme += "%3AAndroid%2Fmedia%2Fcom.whatsapp%2FWhatsApp%2FMedia%2F.Statuses"
        
        intent.putExtra("android.provider.extra.INITIAL_URI", Uri.parse(scheme))
        folderPickerLauncher.launch(intent)
    }

    private val folderPickerLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri = result.data?.data
            if (uri != null) {
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                Toast.makeText(this, "Success! You can now read statuses.", Toast.LENGTH_LONG).show()
            }
        }
    }
}
