package com.quizmaster.util

import java.io.BufferedReader
import java.io.DataOutputStream
import java.io.InputStreamReader

object RootUtil {

    private var isRootGranted: Boolean? = null

    /**
     * 检查设备是否已root
     */
    fun isDeviceRooted(): Boolean {
        val paths = arrayOf(
            "/system/app/Superuser.apk",
            "/sbin/su",
            "/system/bin/su",
            "/system/xbin/su",
            "/data/local/xbin/su",
            "/data/local/bin/su",
            "/system/sd/xbin/su",
            "/system/bin/failsafe/su",
            "/data/local/su",
            "/su/bin/su"
        )
        for (path in paths) {
            if (java.io.File(path).exists()) return true
        }
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("which", "su"))
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val result = reader.readLine()
            process.destroy()
            result != null
        } catch (e: Exception) {
            false
        }
    }

    /**
     * 请求root权限并执行命令
     */
    fun requestRoot(): Boolean {
        if (isRootGranted != null) return isRootGranted!!
        return try {
            val process = Runtime.getRuntime().exec("su")
            val os = DataOutputStream(process.outputStream)
            os.writeBytes("id\n")
            os.writeBytes("exit\n")
            os.flush()
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val output = reader.readLine()
            process.waitFor()
            val granted = output != null && output.contains("uid=0")
            isRootGranted = granted
            granted
        } catch (e: Exception) {
            isRootGranted = false
            false
        }
    }

    /**
     * 以root权限执行命令
     */
    fun executeCommand(command: String): CommandResult {
        return try {
            val process = Runtime.getRuntime().exec("su")
            val os = DataOutputStream(process.outputStream)
            os.writeBytes("$command\n")
            os.writeBytes("echo \"EXIT_CODE:$?\"\n")
            os.writeBytes("exit\n")
            os.flush()

            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val output = StringBuilder()
            var line: String?
            var exitCode = -1
            while (reader.readLine().also { line = it } != null) {
                if (line!!.startsWith("EXIT_CODE:")) {
                    exitCode = line!!.substring(10).toIntOrNull() ?: -1
                } else {
                    output.appendLine(line)
                }
            }
            process.waitFor()
            CommandResult(exitCode, output.toString(), "")
        } catch (e: Exception) {
            CommandResult(-1, "", e.message ?: "Unknown error")
        }
    }

    /**
     * 以root权限读取文件内容
     */
    fun readFileWithRoot(path: String): String? {
        val result = executeCommand("cat \"$path\"")
        return if (result.exitCode == 0) result.output else null
    }

    /**
     * 列出目录内容（root权限）
     */
    fun listDirWithRoot(path: String): List<String> {
        val result = executeCommand("ls -la \"$path\"")
        return if (result.exitCode == 0) {
            result.output.lines().filter { it.isNotBlank() }
        } else emptyList()
    }

    data class CommandResult(
        val exitCode: Int,
        val output: String,
        val error: String
    )
}
