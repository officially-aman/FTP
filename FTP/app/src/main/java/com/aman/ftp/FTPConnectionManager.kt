package com.aman.ftp

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.commons.net.ftp.FTP
import org.apache.commons.net.ftp.FTPClient
import java.io.IOException

object FTPConnectionManager {
    private val ftpClient = FTPClient()

    suspend fun connect(ipAddress: String, username: String, password: String, port: Int): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                Log.d("FTP", "Attempting to connect to $ipAddress on port $port")
                ftpClient.connect(ipAddress, port)
                val loginResult = ftpClient.login(username, password)
                ftpClient.enterLocalPassiveMode()
                ftpClient.setFileType(FTP.BINARY_FILE_TYPE)
                Log.d("FTP", "Login result: $loginResult")
                loginResult
            } catch (e: IOException) {
                Log.e("FTP", "Connection failed: ${e.message}")
                e.printStackTrace()
                false
            }
        }
    }

    fun disconnect() {
        try {
            if (ftpClient.isConnected) {
                ftpClient.logout()
                ftpClient.disconnect()
                Log.d("FTP", "Disconnected from server")
            }
        } catch (e: IOException) {
            Log.e("FTP", "Disconnection failed: ${e.message}")
            e.printStackTrace()
        }
    }

    fun isConnected(): Boolean {
        return ftpClient.isConnected
    }

    fun getClient(): FTPClient {
        return ftpClient
    }
}