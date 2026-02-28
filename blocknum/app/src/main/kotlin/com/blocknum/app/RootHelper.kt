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
    
    // 全局日志回调，用于将底层错误输出到 UI
    var logger: ((String) -> Unit)? = null

    /**
     * 已知的 blocked_numbers.db 路径列表（按优先级）
     * 不同厂商/AOSP 版本可能使用不同目录
     */
    val KNOWN_DB_PATHS = listOf(
        // 高版本 Android (14/15/16) 的专用系统库
        "/data/user_de/0/com.android.providers.telephony/databases/bdata.db",
        "/data/data/com.android.providers.telephony/databases/bdata.db",
        // 传统/旧版本的库
        "/data/data/com.android.providers.contacts/databases/blocked_numbers.db",
        "/data/user/0/com.android.providers.contacts/databases/blocked_numbers.db",
        "/data/data/com.google.android.dialer/databases/blocked_numbers.db",
        "/data/user/0/com.google.android.dialer/databases/blocked_numbers.db"
    )

    /** 根据数据库的文件名判断应该使用哪个表名 */
    private fun getTableName(dbPath: String): String {
        return if (dbPath.endsWith("bdata.db")) "blocked" else "blocked_numbers"
    }

    // ── 公开 API ────────────────────────────────────────────────

    /**
     * 检测设备是否具有 Root 权限。
     * 为了避免在 App 启动时就立刻弹出烦人的 su 授权弹窗，这里仅执行静态的二进制文件检查。
     * 真正的授权弹窗会在后续第一次实际执行 Root 挂载时触发。
     */
    fun isRootAvailable(): Boolean {
        return isSuBinaryPresent()
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
                // 使用 cat 探测以绕过部分 Android 14+ 系统的 ls 权限限制
                val result = execAsRoot("cat \"$path\" > /dev/null 2>&1 && echo EXISTS")
                if (result.contains("EXISTS")) {
                    logger?.invoke("Found DB at: $path")
                    return path
                }
            } catch (e: Exception) {
                continue
            }
        }
        logger?.invoke("Blocked numbers DB not found in known paths.")
        return null
    }

    /**
     * 通过 sqlite3 读取拦截号码列表（需 sqlite3 可用）
     */
    fun readBlockedNumbersViaSqlite(dbPath: String): List<String> {
        val tableName = getTableName(dbPath)
        val result = execAsRoot("sqlite3 \"$dbPath\" 'SELECT original_number FROM $tableName;'")
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
        val tableName = getTableName(dbPath)
        numbers.forEach { number ->
            val sanitized = number.replace("'", "''") // SQL 转义
            try {
                execAsRoot(
                    "sqlite3 \"$dbPath\" " +
                    "\"INSERT OR IGNORE INTO $tableName(original_number,e164_number) " +
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
        val tableName = getTableName(dbPath)
        execAsRoot("sqlite3 \"$dbPath\" 'DELETE FROM $tableName;'")
    }

    /**
     * 将 DB 文件复制到 App 缓存目录（当 sqlite3 不可用时的备用方案）
     * @return 复制后的本地 File，失败返回 null
     */
    fun copyDbToCache(dbPath: String, cacheDir: File): File? {
        val dest = File(cacheDir, "blocked_numbers_copy.db")
        return try {
            // Android 14+ SELinux 会拦截普通的 cp，使用 cat 绕过
            execAsRoot("cat \"$dbPath\" > \"${dest.absolutePath}\" && chmod 644 \"${dest.absolutePath}\"")
            if (dest.exists() && dest.length() > 0) {
                logger?.invoke("Successfully copied DB to cache using cat.")
                dest
            } else {
                logger?.invoke("DB copy to cache is empty or missing.")
                null
            }
        } catch (e: Exception) {
            val msg = "copyDbToCache failed: ${e.message}"
            Log.e(TAG, msg)
            logger?.invoke(msg)
            null
        }
    }

    /**
     * 将修改后的 DB 复制回系统目录（sqlite3 不可用时的备用写入）
     */
    fun copyDbBackToSystem(localDb: File, dbPath: String): Boolean {
        return try {
            // Android 14+ SELinux，同样使用 cat 回写
            execAsRoot(
                "cat \"${localDb.absolutePath}\" > \"$dbPath\" && " +
                "chmod 660 \"$dbPath\" && " +
                "chown system:system \"$dbPath\" || chown radio:radio \"$dbPath\""
            )
            logger?.invoke("Successfully copied DB back to system.")
            true
        } catch (e: Exception) {
            val msg = "copyDbBack failed: ${e.message}"
            Log.e(TAG, msg)
            logger?.invoke(msg)
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
            val errStr = "Root cmd stderr: $stderr (Code: $exitCode, Cmd: $command)"
            Log.w(TAG, errStr)
            logger?.invoke(errStr)
            throw RuntimeException(errStr)
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
            } catch (e: IllegalThreadStateException) {
                Thread.sleep(50)
            }
        }
        return false
    }
}
