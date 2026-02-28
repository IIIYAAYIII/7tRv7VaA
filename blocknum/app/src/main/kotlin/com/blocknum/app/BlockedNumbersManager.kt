package com.blocknum.app

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.os.Build
import android.provider.BlockedNumberContract
import android.util.Log
import java.io.File

/**
 * 拦截号码核心管理类
 *
 * 访问模式（三层回退策略）：
 *   1. STANDARD_API — 通过 Android BlockedNumberContract ContentProvider
 *      要求：App 为默认拨号器，或系统授权
 *   2. ROOT — 通过 su + sqlite3（或复制DB文件）直接操作系统数据库
 *      要求：设备已 Root
 *   3. UNAVAILABLE — 无可用方案
 *
 * 安卓版本兼容：
 *   - API 26 (Android 8): BlockedNumberContract 已存在，使用标准 API
 *   - API 29 (Android 10): 存储权限收紧，文件操作转 SAF，DB操作不受影响
 *   - API 30 (Android 11): 分区存储强制，本类不使用外部存储所以无影响
 *   - API 33+ (Android 13+): 权限体系更新，本类通过 ContentProvider 操作不受影响
 */
class BlockedNumbersManager(private val context: Context) {

    enum class AccessMode { STANDARD_API, ROOT, UNAVAILABLE }

    data class ImportResult(val added: Int, val skipped: Int, val failed: Int)

    companion object {
        private const val TAG = "BlockedNumbersMgr"
    }

    // ── 访问模式检测 ────────────────────────────────────────────

    /**
     * 检测当前可用的最佳访问模式
     * 此方法应在后台线程调用
     */
    fun detectAccessMode(): AccessMode {
        // Step 1: 检测当前用户是否为主账户（多用户系统下，非主用户无法操作黑名单）
        // canCurrentUserBlockNumbers 在 API 24+ 可用，minSdk=26 无需额外版本判断
        if (!BlockedNumberContract.canCurrentUserBlockNumbers(context)) {
            Log.i(TAG, "Not primary user, cannot use BlockedNumberContract")
            return if (RootHelper.isRootAvailable()) AccessMode.ROOT else AccessMode.UNAVAILABLE
        }

        // Step 2: 尝试标准 ContentProvider 访问（会因无权限抛 SecurityException）
        return try {
            context.contentResolver.query(
                BlockedNumberContract.BlockedNumbers.CONTENT_URI,
                arrayOf(BlockedNumberContract.BlockedNumbers.COLUMN_ID),
                null, null, null
            )?.close()
            Log.i(TAG, "Standard API available (default dialer or system permission)")
            AccessMode.STANDARD_API
        } catch (e: SecurityException) {
            Log.i(TAG, "Standard API denied (not default dialer): ${e.message}")
            if (RootHelper.isRootAvailable()) AccessMode.ROOT else AccessMode.UNAVAILABLE
        } catch (e: Exception) {
            Log.w(TAG, "Standard API unexpected error: ${e.message}")
            if (RootHelper.isRootAvailable()) AccessMode.ROOT else AccessMode.UNAVAILABLE
        }
    }

    // ── 公开操作 API ────────────────────────────────────────────

    fun getCount(mode: AccessMode): Int = when (mode) {
        AccessMode.STANDARD_API -> getCountStandard()
        AccessMode.ROOT         -> getCountRoot()
        AccessMode.UNAVAILABLE  -> -1
    }

    fun readBlockedNumbers(mode: AccessMode): List<String> = when (mode) {
        AccessMode.STANDARD_API -> readViaStandard()
        AccessMode.ROOT         -> readViaRoot()
        AccessMode.UNAVAILABLE  -> emptyList()
    }

    /**
     * 批量导入号码
     * @param replace true=先清空再导入（替换），false=合并（跳过已有号码）
     */
    fun importBlockedNumbers(
        numbers: List<String>,
        mode: AccessMode,
        replace: Boolean
    ): ImportResult = when (mode) {
        AccessMode.STANDARD_API -> importViaStandard(numbers, replace)
        AccessMode.ROOT         -> importViaRoot(numbers, replace)
        AccessMode.UNAVAILABLE  -> ImportResult(0, 0, numbers.size)
    }

    // ── 标准 API 实现 ───────────────────────────────────────────

    private fun getCountStandard(): Int {
        return try {
            context.contentResolver.query(
                BlockedNumberContract.BlockedNumbers.CONTENT_URI,
                arrayOf(BlockedNumberContract.BlockedNumbers.COLUMN_ID),
                null, null, null
            )?.use { it.count } ?: 0
        } catch (e: Exception) {
            Log.e(TAG, "getCountStandard: ${e.message}"); -1
        }
    }

    private fun readViaStandard(): List<String> {
        val numbers = mutableListOf<String>()
        try {
            context.contentResolver.query(
                BlockedNumberContract.BlockedNumbers.CONTENT_URI,
                arrayOf(BlockedNumberContract.BlockedNumbers.COLUMN_ORIGINAL_NUMBER),
                null, null, null
            )?.use { cursor ->
                val col = cursor.getColumnIndexOrThrow(
                    BlockedNumberContract.BlockedNumbers.COLUMN_ORIGINAL_NUMBER
                )
                while (cursor.moveToNext()) {
                    cursor.getString(col)?.let { numbers.add(it) }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "readViaStandard: ${e.message}")
        }
        return numbers
    }

    private fun importViaStandard(numbers: List<String>, replace: Boolean): ImportResult {
        var added = 0; var skipped = 0; var failed = 0

        if (replace) {
            // 删除全部现有记录
            try {
                context.contentResolver.query(
                    BlockedNumberContract.BlockedNumbers.CONTENT_URI,
                    arrayOf(BlockedNumberContract.BlockedNumbers.COLUMN_ID),
                    null, null, null
                )?.use { cursor ->
                    val colId = cursor.getColumnIndexOrThrow(BlockedNumberContract.BlockedNumbers.COLUMN_ID)
                    val ids = mutableListOf<Long>()
                    while (cursor.moveToNext()) ids.add(cursor.getLong(colId))
                    ids.forEach { id ->
                        context.contentResolver.delete(
                            android.net.Uri.withAppendedPath(
                                BlockedNumberContract.BlockedNumbers.CONTENT_URI, id.toString()
                            ), null, null
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "clearStandard: ${e.message}")
            }
        }

        val existing = if (!replace) readViaStandard().toHashSet() else hashSetOf()
        numbers.forEach { number ->
            val trimmed = number.trim()
            if (trimmed.isEmpty()) { skipped++; return@forEach }
            if (!replace && existing.contains(trimmed)) { skipped++; return@forEach }
            try {
                val values = ContentValues().apply {
                    put(BlockedNumberContract.BlockedNumbers.COLUMN_ORIGINAL_NUMBER, trimmed)
                }
                context.contentResolver.insert(
                    BlockedNumberContract.BlockedNumbers.CONTENT_URI, values
                )
                added++
            } catch (e: Exception) {
                Log.w(TAG, "insert failed ($trimmed): ${e.message}")
                failed++
            }
        }
        return ImportResult(added, skipped, failed)
    }

    // ── Root 模式实现 ───────────────────────────────────────────

    private fun getCountRoot(): Int {
        return try {
            readViaRoot().size
        } catch (e: Exception) {
            Log.e(TAG, "getCountRoot: ${e.message}"); -1
        }
    }

    private fun readViaRoot(): List<String> {
        val dbPath = RootHelper.findBlockedNumbersDbPath()
            ?: return emptyList<String>().also { Log.w(TAG, "DB not found") }

        return if (RootHelper.isSqlite3Available()) {
            // 优先路径：sqlite3 命令直接查询
            RootHelper.readBlockedNumbersViaSqlite(dbPath)
        } else {
            // 备用路径：复制 DB 文件到缓存目录，用 SQLiteDatabase API 读取
            readViaDbCopy(dbPath)
        }
    }

    private fun readViaDbCopy(dbPath: String): List<String> {
        val numbers = mutableListOf<String>()
        val localDb = RootHelper.copyDbToCache(dbPath, context.cacheDir) ?: return numbers
        try {
            // 使用只读模式打开，避免触发 WAL 日志写入
            val db = SQLiteDatabase.openDatabase(
                localDb.absolutePath, null, SQLiteDatabase.OPEN_READONLY
            )
            db.use { database ->
                database.rawQuery("SELECT original_number FROM blocked_numbers", null)
                    ?.use { cursor ->
                        while (cursor.moveToNext()) {
                            cursor.getString(0)?.let { numbers.add(it) }
                        }
                    }
            }
        } catch (e: Exception) {
            Log.e(TAG, "readViaDbCopy: ${e.message}")
        } finally {
            localDb.delete()
        }
        return numbers
    }

    private fun importViaRoot(numbers: List<String>, replace: Boolean): ImportResult {
        val dbPath = RootHelper.findBlockedNumbersDbPath()
            ?: return ImportResult(0, 0, numbers.size)

        return if (RootHelper.isSqlite3Available()) {
            importViaRootSqlite(dbPath, numbers, replace)
        } else {
            importViaDbCopy(dbPath, numbers, replace)
        }
    }

    /** Root + sqlite3 方式写入 */
    private fun importViaRootSqlite(
        dbPath: String, numbers: List<String>, replace: Boolean
    ): ImportResult {
        if (replace) {
            RootHelper.clearBlockedNumbersViaSqlite(dbPath)
        }
        val existing = if (!replace) {
            RootHelper.readBlockedNumbersViaSqlite(dbPath).toHashSet()
        } else hashSetOf()

        val toAdd = numbers.filter { it.isNotBlank() && (replace || !existing.contains(it.trim())) }
            .map { it.trim() }
        val skipped = numbers.size - toAdd.size
        val added = RootHelper.insertBlockedNumbersViaSqlite(dbPath, toAdd)
        return ImportResult(added, skipped, toAdd.size - added)
    }

    /** Root + DB文件复制 方式写入（无 sqlite3 时回退） */
    private fun importViaDbCopy(
        dbPath: String, numbers: List<String>, replace: Boolean
    ): ImportResult {
        val localDb = RootHelper.copyDbToCache(dbPath, context.cacheDir)
            ?: return ImportResult(0, 0, numbers.size)
        var added = 0; var skipped = 0; var failed = 0
        try {
            val db = SQLiteDatabase.openDatabase(
                localDb.absolutePath, null, SQLiteDatabase.OPEN_READWRITE
            )
            db.use { database ->
                if (replace) {
                    database.execSQL("DELETE FROM blocked_numbers")
                }
                val existing = if (!replace) {
                    val set = hashSetOf<String>()
                    database.rawQuery("SELECT original_number FROM blocked_numbers", null)
                        ?.use { c -> while (c.moveToNext()) c.getString(0)?.let { set.add(it) } }
                    set
                } else hashSetOf()

                numbers.forEach { number ->
                    val trimmed = number.trim()
                    if (trimmed.isEmpty()) { skipped++; return@forEach }
                    if (!replace && existing.contains(trimmed)) { skipped++; return@forEach }
                    try {
                        val cv = ContentValues().apply {
                            put("original_number", trimmed)
                            put("e164_number", trimmed)
                        }
                        database.insertWithOnConflict(
                            "blocked_numbers", null, cv,
                            SQLiteDatabase.CONFLICT_IGNORE
                        )
                        added++
                    } catch (e: Exception) {
                        failed++
                    }
                }
            }
            // 写回系统路径（需要 su）
            if (!RootHelper.copyDbBackToSystem(localDb, dbPath)) {
                Log.e(TAG, "Failed to copy DB back")
                return ImportResult(0, 0, numbers.size)
            }
        } catch (e: Exception) {
            Log.e(TAG, "importViaDbCopy: ${e.message}")
            return ImportResult(0, 0, numbers.size)
        } finally {
            localDb.delete()
        }
        return ImportResult(added, skipped, failed)
    }
}
