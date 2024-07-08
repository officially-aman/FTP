package com.aman.ftp

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.ProgressDialog
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Alignment
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.KeyboardType
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.aman.ftp.ui.theme.FTPTheme
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.apache.commons.net.ftp.FTPClient
import java.io.IOException
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

@Suppress("DEPRECATION")
class MainActivity : ComponentActivity() {
    private lateinit var progressDialog: ProgressDialog
    private lateinit var sharedPreferences: SharedPreferences
    private var isTransferCancelled = false
    private val connectionStatus = MutableStateFlow("Not Connected")

    companion object {
        private const val CHANNEL_ID = "FTP_CHANNEL"
        private const val NOTIFICATION_ID = 1
    }

    private val requestNotificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (!isGranted) {
                Toast.makeText(this, "Notification permission denied", Toast.LENGTH_SHORT).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        sharedPreferences = getSharedPreferences("FTP_Prefs", Context.MODE_PRIVATE)

        createNotificationChannel()

        setContent {
            FTPTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    MainContent(modifier = Modifier.padding(innerPadding))
                }
            }
        }

        CoroutineScope(Dispatchers.IO).launch {
            monitorConnectionStatus()
        }

        checkAndRequestNotificationPermission()
    }

    private val selectFilesLauncher = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        handleFileTransfer(uris)
    }

    private fun checkAndRequestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            when {
                ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED -> {
                    // Permission is granted
                }
                else -> {
                    // Directly request permission
                    requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        }
    }

    private fun handleFileTransfer(uris: List<Uri>) {
        val context = this
        val scope = CoroutineScope(Dispatchers.Main)

        progressDialog = ProgressDialog(context).apply {
            setTitle("File Transfer")
            setProgressStyle(ProgressDialog.STYLE_HORIZONTAL)
            setCancelable(false)
            setButton(ProgressDialog.BUTTON_NEGATIVE, "Cancel") { dialog, _ ->
                isTransferCancelled = true
                dialog.dismiss()
            }
            show()
        }

        scope.launch {
            val result = withContext(Dispatchers.IO) {
                transferFilesToFTP(uris)
            }
            progressDialog.dismiss()
            showCompletionNotification(result)
            Toast.makeText(context, if (result) "File Transfer Successful" else "File Transfer Failed", Toast.LENGTH_LONG).show()
        }
    }

    @SuppressLint("DefaultLocale")
    private suspend fun transferFilesToFTP(uris: List<Uri>): Boolean {
        val ftpClient = FTPConnectionManager.getClient()
        try {
            var success = true
            val totalFiles = uris.size
            val startTime = System.currentTimeMillis()
            uris.forEachIndexed { index, uri ->
                if (isTransferCancelled) {
                    success = false
                    return@forEachIndexed
                }
                val originalFileName = getFileName(uri)
                val sanitizedFileName = sanitizeFileName(originalFileName)
                val fileName = getUniqueFileName(ftpClient, sanitizedFileName)
                contentResolver.openInputStream(uri)?.use { inputStream ->
                    val elapsedTime = System.currentTimeMillis() - startTime
                    val remainingTime = (elapsedTime / (index + 1)) * (totalFiles - index - 1)
                    val progress = ((index + 1) * 100) / totalFiles

                    updateProgressNotification(
                        fileName,
                        index + 1,
                        totalFiles,
                        elapsedTime.formatTime(),
                        remainingTime.formatTime()
                    )

                    withContext(Dispatchers.Main) {
                        progressDialog.setMessage(
                            "Uploading: $fileName\n" +
                                    "Progress: $progress%\n" +
                                    "Elapsed Time: ${elapsedTime.formatTime()}\n" +
                                    "Remaining Time: ${remainingTime.formatTime()}"
                        )
                        progressDialog.max = totalFiles
                        progressDialog.progress = index + 1
                    }

                    if (!ftpClient.storeFile(fileName, inputStream)) {
                        success = false
                    }
                }
            }
            return success
        } catch (e: IOException) {
            e.printStackTrace()
            return false
        }
    }

    private fun sanitizeFileName(fileName: String): String {
        return URLEncoder.encode(fileName, StandardCharsets.UTF_8.toString())
    }

    private fun getUniqueFileName(ftpClient: FTPClient, fileName: String): String {
        var uniqueFileName = fileName
        var copyNumber = 1
        while (ftpClient.listFiles(uniqueFileName).isNotEmpty()) {
            val extensionIndex = fileName.lastIndexOf('.')
            uniqueFileName = if (extensionIndex != -1) {
                "${fileName.substring(0, extensionIndex)}_copy$copyNumber${fileName.substring(extensionIndex)}"
            } else {
                "${fileName}_copy$copyNumber"
            }
            uniqueFileName = URLEncoder.encode(uniqueFileName, StandardCharsets.UTF_8.toString())
            copyNumber++
        }
        return uniqueFileName
    }

    @SuppressLint("Range")
    private fun getFileName(uri: Uri): String {
        var result: String? = null
        if (uri.scheme == "content") {
            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    result = cursor.getString(cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME))
                }
            }
        }
        return result ?: uri.path?.substringAfterLast('/') ?: "unknown"
    }

    private fun Long.formatTime(): String {
        val minutes = TimeUnit.MILLISECONDS.toMinutes(this)
        val seconds = TimeUnit.MILLISECONDS.toSeconds(this) % 60
        return String.format("%02d:%02d", minutes, seconds)
    }

    private fun updateProgressNotification(fileName: String, currentFile: Int, totalFiles: Int, elapsedTime: String, remainingTime: String) {
        val progress = (currentFile * 100) / totalFiles
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("File Transfer Progress")
            .setContentText("Uploading ${URLDecoder.decode(fileName, StandardCharsets.UTF_8.toString())} ($currentFile of $totalFiles)\nElapsed Time: $elapsedTime\nRemaining Time: $remainingTime")
            .setProgress(100, progress, false)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true) // Make the notification non-removable

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            checkAndRequestNotificationPermission()
            return
        }
        NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, builder.build())
    }

    private fun showCompletionNotification(success: Boolean) {
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("File Transfer Completed")
            .setContentText(if (success) "All files transferred successfully." else "File transfer failed.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setOngoing(false) // Make the notification removable

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            checkAndRequestNotificationPermission()
            return
        }
        NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, builder.build())
    }

    @Composable
    fun MainContent(modifier: Modifier = Modifier) {
        var ipAddress by remember { mutableStateOf(sharedPreferences.getString("ftp_ip", "") ?: "") }
        var username by remember { mutableStateOf(sharedPreferences.getString("ftp_username", "") ?: "") }
        var password by remember { mutableStateOf(sharedPreferences.getString("ftp_password", "") ?: "") }
        var port by remember { mutableStateOf(sharedPreferences.getString("ftp_port", "") ?: "") }
        val context = LocalContext.current
        val scope = rememberCoroutineScope()

        val connectionStatusState by connectionStatus.collectAsState()

        Box(modifier = modifier.fillMaxSize()) {
            Image(
                painter = painterResource(id = R.drawable.bg__),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                contentAlignment = Alignment.Center
            ) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        FTPTextInputLayout(label = "FTP IP Address", value = ipAddress, onValueChange = { ipAddress = it }, isNumeric = true)
                        Spacer(modifier = Modifier.height(8.dp))
                        FTPTextInputLayout(label = "FTP Username", value = username, onValueChange = { username = it })
                        Spacer(modifier = Modifier.height(8.dp))
                        FTPTextInputLayout(label = "FTP Password", value = password, onValueChange = { password = it })
                        Spacer(modifier = Modifier.height(8.dp))
                        FTPTextInputLayout(label = "FTP Port Number", value = port, onValueChange = { port = it }, isNumeric = true)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = connectionStatusState,
                            color = if (connectionStatusState == "Connected") Color.Green else Color.Red,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                            Button(onClick = {
                                scope.launch {
                                    val status = FTPConnectionManager.connect(ipAddress, username, password, port.toIntOrNull() ?: 21)
                                    connectionStatus.value = if (status) "Connected" else "Not Connected"
                                    if (status) {
                                        saveFTPDetails(ipAddress, username, password, port)
                                    }
                                    Toast.makeText(
                                        context,
                                        if (status) "Connection Established" else "Not Connected",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            }) {
                                Text(text = "Connect")
                            }
                            Button(onClick = {
                                selectFilesLauncher.launch(arrayOf("*/*"))
                            }) {
                                Text(text = "File Transfer")
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "IP: 0.0.0.0",
                            color = Color.Gray,
                            modifier = Modifier.padding(top = 16.dp)
                        )
                    }
                }
            }
        }
    }

    @Composable
    fun FTPTextInputLayout(label: String, value: String, onValueChange: (String) -> Unit, isNumeric: Boolean = false) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(text = label) },
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = if (isNumeric) KeyboardOptions(keyboardType = KeyboardType.Phone) else KeyboardOptions.Default
        )
    }

    private fun saveFTPDetails(ip: String, username: String, password: String, port: String) {
        with(sharedPreferences.edit()) {
            putString("ftp_ip", ip)
            putString("ftp_username", username)
            putString("ftp_password", password)
            putString("ftp_port", port)
            apply()
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "FTP Channel"
            val descriptionText = "Notifications for FTP file transfers"
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val notificationManager: NotificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private suspend fun monitorConnectionStatus() {
        while (true) {
            delay(5000) // Check every 5 seconds
            val connected = FTPConnectionManager.isConnected()
            connectionStatus.value = if (connected) "Connected" else "Not Connected"
        }
    }
}
