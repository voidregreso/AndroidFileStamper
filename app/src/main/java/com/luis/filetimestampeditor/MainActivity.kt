package com.luis.filetimestampeditor

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.lifecycle.MutableLiveData
import com.luis.filetimestampeditor.databinding.ActivityMainBinding
import java.io.IOException

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private val fileUriLiveData = MutableLiveData<Uri?>(null)
    private val timestampModifiedLiveData = MutableLiveData<Boolean>()
    private val hasPermLiveData = MutableLiveData(false)
    private val errMsg = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        enableEdgeToEdge()

        setupPermissions()
        setupFileSelection()
        setupTimestampModification()
        setupObservers()
    }

    private fun setupPermissions() {
        if (Build.VERSION.SDK_INT >= 30) {
            if (!Environment.isExternalStorageManager()) {
                val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                startActivityForResult(intent, REQ_CODE)
            } else hasPermLiveData.postValue(true)
        } else {
            if(ActivityCompat.checkSelfPermission(this,
                    android.Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(
                        android.Manifest.permission.READ_EXTERNAL_STORAGE,
                        android.Manifest.permission.WRITE_EXTERNAL_STORAGE
                    ),
                    REQ_CODE
                )
            } else hasPermLiveData.postValue(true)
        }
    }

    private fun setupFileSelection() {
        binding.btnBrowse.setOnClickListener {
            openFileChooser()
        }
    }

    private fun setupTimestampModification() {
        binding.timePicker.setIs24HourView(true)
        binding.btnModify.setOnClickListener {
            val fileUri = fileUriLiveData.value ?: return@setOnClickListener
            val year = binding.datePicker.year
            val month = binding.datePicker.month
            val day = binding.datePicker.dayOfMonth
            val hour = binding.timePicker.hour
            val minute = binding.timePicker.minute

            try {
                contentResolver.openFileDescriptor(fileUri, "rw")?.use { pfd ->
                    val fd = pfd.detachFd()
                    val success = modifyTimestamp(fd, year, month + 1, day, hour, minute)
                    timestampModifiedLiveData.postValue(success)
                }
            } catch (e: IOException) {
                e.printStackTrace()
                Toast.makeText(this, "Error accessing file: $errMsg", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupObservers() {
        fileUriLiveData.observe(this) { uri ->
            binding.txtPath.text = uri?.toString() ?: getString(R.string.file_not_selected_yet)
            binding.btnModify.isEnabled = uri != null
        }

        timestampModifiedLiveData.observe(this) { success ->
            val message =
                if (success) "Timestamp modified successfully" else "Failed to modify timestamp"
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
            fileUriLiveData.postValue(null)
        }

        hasPermLiveData.observe(this) { hasPerm ->
            binding.btnBrowse.isEnabled = hasPerm
        }
    }

    private fun openFileChooser() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
        }
        startActivityForResult(intent, CHOOSE_CODE)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == REQ_CODE) {
            if (Build.VERSION.SDK_INT >= 30 && Environment.isExternalStorageManager()) {
                hasPermLiveData.postValue(true)
            } else {
                hasPermLiveData.postValue(false)
                Toast.makeText(this, "Permission Denied", Toast.LENGTH_SHORT).show()
                finish()
            }
            return
        }

        if (requestCode == CHOOSE_CODE && resultCode == RESULT_OK) {
            data?.data?.also { uri ->
                fileUriLiveData.value = uri
            }
        }
    }

    private external fun modifyTimestamp(fd: Int, year: Int, month: Int, day: Int, hour: Int, minute: Int): Boolean

    companion object {
        private const val CHOOSE_CODE = 0xa0
        private const val REQ_CODE = 0xa1
        init {
            System.loadLibrary("fileutil")
        }
    }
}