package com.blocknum.app

import android.util.Log
import java.io.DataOutputStream
import java.io.File

/**
 * Root 权限辅助类
 * 通过 su 执行 shell 命令，读写系统拦截号码数据库。
 *
 * 兼容性说明：
 *   - Android 8-16 (API 26-36): su 二进制路径与行为基本一致
 *   - sqlite3 二进制在部分设备上不存在（Android 10+ ROM 精简后消失）
 *     此时退回到"复制DB文件到缓存目录再用 Room/SQLiteDatabase 读取"策略
 */
object RootHelper {

    private const val TAG = "RootHelper"
    private const val TIMEOUT_MS = 8000L

    /**
     * 已知的 blocked_numbers.db 路径列表（按优先级）
     * 不同厂商/AOSP 版本可能使用不同目录
     */
    val KNOWN_DB_PATHS = listOf(
        "/data/data/com.android.providers.contacts/databases/blocked_numbers.db",
        "/data/user/0/com.android.providers.contacts/databases/blocked_numbers.db",
        "/data/data/com.google.android.dialer/databases/blocked_numbers.db",
        "/data/user/0/com.google.android.dialer/databases/blocked_numbers.db"
    )

    // ── 公开 API ────────────────────────────────────────────────

    /**
     * 检测设备是否具有 Root 权限。
     * 先确认 su 二进制存在，再实际执行一条命令验证授权。
     */
    fun isRootAvailable(): Boolean {
        if (!isSuBinaryPresent()) return false
        return try {
            val result = execAsRoot("id")
            result.contains("uid=0")
        } catch (e: Exception) {
            Log.w(TAG, "Root check failed: ${e.message}")
            false
        }
    }

    /**
     * 检测 sqlite3 二进制是否可用（部分精简 ROM 不含）
     */
    fun isSqlite3Available(): Boolean {
        return try {
            val result = execAsRoot("which sqlite3 || ls /system/bin/sqlite3 2>/dev/null || ls /system/xbin/sqlite3 2>/dev/null")
            result.contains("sqlite3")
        } catch (e: Exception) {
            false
        }
    }

    /**
     * 寻找设备上实际存在的 blocked_numbers.db 路径
     */
    fun findBlockedNumbersDbPath(): String? {
        for (path in KNOWN_DB_PATHS) {
            try {
                val result = execAsRoot("ls \"$path\" 2>/dev/null && echo EXISTS")
                if (result.contains("EXISTS")) return path
            } catch (e: Exception) {
                continue
            }
        }
        return null
    }

    /**
     * 通过 sqlite3 读取拦截号码列表（需 sqlite3 可用）
     */
    fun readBlockedNumbersViaSqlite(dbPath: String): List<String> {
        val result = execAsRoot("sqlite3 \"$dbPath\" 'SELECT original_number FROM blocked_numbers;'")
        return result.lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
    }

    /**
     * 通过 sqlite3 插入号码（需 sqlite3 可用）
     * @return 成功插入的数量
     */
    fun insertBlockedNumbersViaSqlite(dbPath: String, numbers: List<String>): Int {
        var count = 0
        numbers.forEach { number ->
            val sanitized = number.replace("'", "''") // SQL 转义
            try {
                execAsRoot(
                    "sqlite3 \"$dbPath\" " +
                    "\"INSERT OR IGNORE INTO blocked_numbers(original_number,e164_number) " +
                    "VALUES('$sanitized','$sanitized');\""
                )
                count++
            } catch (e: Exception) {
                Log.w(TAG, "Insert failed for $number: ${e.message}")
            }
        }
        return count
    }

    /**
     * 通过 sqlite3 删除所有拦截号码（清空表）
     */
    fun clearBlockedNumbersViaSqlite(dbPath: String) {
        execAsRoot("sqlite3 \"$dbPath\" 'DELETE FROM blocked_numbers;'")
    }

    /**
     * 将 DB 文件复制到 App 缓存目录（当 sqlite3 不可用时的备用方案）
     * @return 复制后的本地 File，失败返回 null
     */
    fun copyDbToCache(dbPath: String, cacheDir: File): File? {
        val dest = File(cacheDir, "blocked_numbers_copy.db")
        return try {
            execAsRoot("cp \"$dbPath\" \"${dest.absolutePath}\" && chmod 644 \"${dest.absolutePath}\"")
            if (dest.exists() && dest.length() > 0) dest else null
        } catch (e: Exception) {
            Log.e(TAG, "copyDbToCache failed: ${e.message}")
            null
        }
    }

    /**
     * 将修改后的 DB 复制回系统目录（sqlite3 不可用时的备用写入）
     */
    fun copyDbBackToSystem(localDb: File, dbPath: String): Boolean {
        return try {
            execAsRoot(
                "cp \"${localDb.absolutePath}\" \"$dbPath\" && " +
                "chmod 660 \"$dbPath\" && " +
                "chown system:system \"$dbPath\""
            )
            true
        } catch (e: Exception) {
            Log.e(TAG, "copyDbBack failed: ${e.message}")
            false
        }
    }

    /**
     * 以 root 身份执行命令，返回 stdout 内容
     */
    fun execAsRoot(command: String): String {
        val process = Runtime.getRuntime().exec("su")
        val os = DataOutputStream(process.outputStream)
        os.writeBytes("$command\n")
        os.writeBytes("exit\n")
        os.flush()

        // 设置超时
        val finished = process.waitForWithTimeout(TIMEOUT_MS)
        if (!finished) {
            process.destroy()
            throw RuntimeException("Root command timed out: $command")
        }

        val stdout = process.inputStream.bufferedReader().readText()
        val stderr = process.errorStream.bufferedReader().readText()
        val exitCode = process.exitValue()

        Log.d(TAG, "execAsRoot: exit=$exitCode  stdout='${stdout.take(200)}'  stderr='${stderr.take(200)}'")

        if (exitCode != 0 && stderr.isNotBlank()) {
            Log.w(TAG, "Root cmd stderr: $stderr")
        }
        return stdout.trim()
    }

    // ── 私有方法 ────────────────────────────────────────────────

    private fun isSuBinaryPresent(): Boolean {
        val suPaths = listOf("/system/bin/su", "/system/xbin/su", "/sbin/su", "/su/bin/su")
        return suPaths.any { File(it).exists() }
    }

    /** Process.waitFor() 的超时版本，兼容 API 26 */
    private fun Process.waitForWithTimeout(timeoutMs: Long): Boolean {
        val start = System.currentTimeMillis()
        while (System.currentTimeMillis() - start < timeoutMs) {
            try {
                exitValue() // 不抛异常说明进程已结束
                return true
            } catch (_: IllegalThreadStateException) {
                Thread.sleep(50)
            }
        }
        return false
    }
}
