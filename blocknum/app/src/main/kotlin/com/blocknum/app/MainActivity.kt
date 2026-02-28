package com.blocknum.app

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.view.View
import android.os.Bundle
import android.telecom.TelecomManager
import android.content.ClipboardManager
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.blocknum.app.databinding.ActivityMainBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 主界面
 *
 * 功能：
 *  - 显示当前 Android 版本和访问模式
 *  - 导出黑名单到 CSV（使用 SAF）
 *  - 从 CSV 导入黑名单（合并或替换）
 *  - 顶部语言切换按钮（中文 ↔ English）
 *  - 引导用户成为默认拨号器（以获取标准 API 权限）
 *
 * 安卓版本说明：
 *  - API 26+: ActivityResultContracts 由 AndroidX 提供，统一支持
 *  - API 26+: SAF (ACTION_CREATE_DOCUMENT / ACTION_OPEN_DOCUMENT) 完整支持
 *  - API 26+: createConfigurationContext 用于语言切换，无需 deprecated API
 *  - API 29+: 分区存储不影响 SAF 路径
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var manager: BlockedNumbersManager
    private var currentMode = BlockedNumbersManager.AccessMode.UNAVAILABLE

    // ── Activity Result Launchers（需在 onCreate 之前注册） ──────

    /** SAF 文件选择（导入用） */
    private val importLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { showImportDialog(it) } }

    /** SAF 文件创建（导出用） */
    private val exportLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri -> uri?.let { performExport(it) } }

    /** 请求成为默认拨号器 */
    private val defaultDialerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { detectAndUpdateMode() }  // 返回后重新检测权限

    // ── Lifecycle ───────────────────────────────────────────────

    /**
     * 在 attachBaseContext 阶段应用语言设置（语言切换核心逻辑）
     * Android 7+ (API 24+) 支持 createConfigurationContext，minSdk=26 确保可用
     */
    override fun attachBaseContext(newBase: Context) {
        val lang = newBase.getSharedPreferences("prefs", MODE_PRIVATE)
            .getString("language", "zh") ?: "zh"
        val locale = Locale(lang)
        Locale.setDefault(locale)
        val config = Configuration(newBase.resources.configuration)
        config.setLocale(locale)
        super.attachBaseContext(newBase.createConfigurationContext(config))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        manager = BlockedNumbersManager(this)
        setupToolbar()
        setupLanguageButton()
        setupActionButtons()
        setupLogCopyFeature()

        // 为 RootHelper 挂载此 UI 日志回调，这样底层的库查找、失败等信息就能打印到屏幕
        RootHelper.logger = { msg ->
            lifecycleScope.launch(Dispatchers.Main) {
                appendLog(msg)
            }
        }

        // 显示 Android 版本信息（Android 版本检测）
        showAndroidVersionInfo()

        // 启动时自动检测访问模式
        detectAndUpdateMode()
    }

    // ── UI 初始化 ────────────────────────────────────────────────

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
    }

    private fun setupLanguageButton() {
        val prefs = getSharedPreferences("prefs", MODE_PRIVATE)
        val lang = prefs.getString("language", "zh") ?: "zh"
        // 显示"切换到另一种语言"的标签
        binding.btnLanguage.text = if (lang == "zh") "EN" else "中"
        binding.btnLanguage.setOnClickListener {
            val current = prefs.getString("language", "zh") ?: "zh"
            prefs.edit().putString("language", if (current == "zh") "en" else "zh").apply()
            recreate()  // 重建 Activity 应用新语言（attachBaseContext 会重新读取）
        }
    }

    private fun setupActionButtons() {
        binding.btnExport.setOnClickListener { startExport() }
        binding.btnImport.setOnClickListener { startImport() }
        binding.btnRefresh.setOnClickListener { detectAndUpdateMode() }
        binding.btnSetDefaultDialer.setOnClickListener { requestBecomeDefaultDialer() }
    }

    private fun setupLogCopyFeature() {
        val copyAction = {
            val logText = binding.tvLog.text.toString()
            if (logText != getString(R.string.log_empty) && logText.isNotBlank()) {
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = android.content.ClipData.newPlainText("BlockNum Log", logText)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(this, "Log copied", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnCopyLog.setOnClickListener { copyAction() }
        binding.tvLog.setOnLongClickListener { 
            copyAction()
            true 
        }
    }

    /**
     * 显示设备 Android 版本信息
     * Build.VERSION.SDK_INT：API 1+ 可用，始终兼容
     * Build.VERSION.RELEASE：友好版本号字符串（如 "14"、"15"）
     */
    private fun showAndroidVersionInfo() {
        val apiLevel = Build.VERSION.SDK_INT
        val release  = Build.VERSION.RELEASE
        // Build.VERSION_CODES 常量对应关系（部分）：
        //   O=26, P=28, Q=29, R=30, S=31, Sv2=32, T=33, U=34, V=35, BAKLAVA=36
        val codeName = when {
            apiLevel >= 36 -> "Baklava"
            apiLevel == 35 -> "Vanilla Ice Cream"
            apiLevel == 34 -> "Upside Down Cake"
            apiLevel == 33 -> "Tiramisu"
            apiLevel == 32 -> "Snow Cone v2"
            apiLevel == 31 -> "Snow Cone"
            apiLevel == 30 -> "Red Velvet Cake"
            apiLevel == 29 -> "Pie (Q)"
            apiLevel == 28 -> "Pie (P)"
            apiLevel == 27 -> "Oreo MR1"
            apiLevel == 26 -> "Oreo"
            else            -> "Unknown"
        }
        binding.tvAndroidVersion.text = "Android $release ($codeName, API $apiLevel)"
    }

    // ── 访问模式检测 ────────────────────────────────────────────

    private fun detectAndUpdateMode() {
        setLoadingState(true)
        lifecycleScope.launch(Dispatchers.IO) {
            currentMode = manager.detectAccessMode()
            val count = runCatching { manager.getCount(currentMode) }.getOrDefault(-1)
            
            withContext(Dispatchers.Main) {
                updateModeUI(currentMode, count)
                setLoadingState(false)
                appendLog("Mode detected: $currentMode, Count: $count")
            }
        }
    }

    private fun updateModeUI(mode: BlockedNumbersManager.AccessMode, count: Int) {
        val (modeText, modeColor) = when (mode) {
            BlockedNumbersManager.AccessMode.STANDARD_API ->
                getString(R.string.mode_standard) to getColor(R.color.success)
            BlockedNumbersManager.AccessMode.ROOT ->
                getString(R.string.mode_root) to getColor(R.color.warning)
            BlockedNumbersManager.AccessMode.UNAVAILABLE ->
                getString(R.string.mode_unavailable) to getColor(R.color.error)
        }
        binding.tvAccessMode.text = modeText
        binding.tvAccessMode.setTextColor(modeColor)

        binding.tvCount.text = if (count >= 0)
            getString(R.string.blocked_count, count)
        else
            getString(R.string.count_unavailable)

        val hasAccess = mode != BlockedNumbersManager.AccessMode.UNAVAILABLE
        binding.btnExport.isEnabled = hasAccess
        binding.btnImport.isEnabled = hasAccess
        // 只要不是 STANDARD_API，就始终显示「设为默认拨号器」引导卡片（推荐官方做法）
        binding.cardDefaultDialer.visibility =
            if (mode != BlockedNumbersManager.AccessMode.STANDARD_API) View.VISIBLE else View.GONE
    }

    // ── 默认拨号器引导 ───────────────────────────────────────────

    private fun requestBecomeDefaultDialer() {
        // TelecomManager.ACTION_CHANGE_DEFAULT_DIALER 在 API 23+ 可用，minSdk=26 无需额外判断
        val intent = Intent(TelecomManager.ACTION_CHANGE_DEFAULT_DIALER)
            .putExtra(TelecomManager.EXTRA_CHANGE_DEFAULT_DIALER_PACKAGE_NAME, packageName)
        try {
            defaultDialerLauncher.launch(intent)
        } catch (e: Exception) {
            appendLog(getString(R.string.error_default_dialer))
        }
    }

    // ── 导出流程 ─────────────────────────────────────────────────

    private fun startExport() {
        val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        exportLauncher.launch("blocked_numbers_$ts.csv")
    }

    private fun performExport(uri: Uri) {
        setLoadingState(true)
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val numbers = manager.readBlockedNumbers(currentMode)
                if (numbers.isEmpty()) {
                    withContext(Dispatchers.Main) {
                        setLoadingState(false)
                        appendLog(getString(R.string.no_numbers_to_export))
                    }
                    return@launch
                }
                contentResolver.openOutputStream(uri)?.use { FileUtils.exportToCsv(numbers, it) }
                withContext(Dispatchers.Main) {
                    setLoadingState(false)
                    appendLog(getString(R.string.export_success, numbers.size))
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    setLoadingState(false)
                    appendLog(getString(R.string.export_error, e.message ?: ""))
                }
            }
        }
    }

    // ── 导入流程 ─────────────────────────────────────────────────

    private fun startImport() {
        // 支持 text/csv、text/plain 和 application/octet-stream（文件管理器有时返回后者）
        importLauncher.launch(arrayOf("text/csv", "text/plain", "application/octet-stream"))
    }

    private fun showImportDialog(uri: Uri) {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.import_mode_title))
            .setMessage(getString(R.string.import_mode_message))
            .setPositiveButton(getString(R.string.import_merge)) { _, _ ->
                performImport(uri, replace = false)
            }
            .setNegativeButton(getString(R.string.import_replace)) { _, _ ->
                confirmReplaceAndImport(uri)
            }
            .setNeutralButton(getString(R.string.cancel), null)
            .show()
    }

    private fun confirmReplaceAndImport(uri: Uri) {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.confirm_replace_title))
            .setMessage(getString(R.string.confirm_replace_message))
            .setPositiveButton(getString(R.string.confirm)) { _, _ ->
                performImport(uri, replace = true)
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun performImport(uri: Uri, replace: Boolean) {
        setLoadingState(true)
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val numbers = contentResolver.openInputStream(uri)
                    ?.use { FileUtils.importFromCsv(it) } ?: emptyList()
                val result = manager.importBlockedNumbers(numbers, currentMode, replace)
                withContext(Dispatchers.Main) {
                    setLoadingState(false)
                    appendLog(getString(R.string.import_success, result.added, result.skipped, result.failed))
                    detectAndUpdateMode()  // 刷新计数
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    setLoadingState(false)
                    appendLog(getString(R.string.import_error, e.message ?: ""))
                }
            }
        }
    }

    // ── 工具方法 ─────────────────────────────────────────────────

    private fun setLoadingState(loading: Boolean) {
        binding.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
        // 加载中时同时禁用操作按钮，防止重复触发
        if (loading) {
            binding.btnExport.isEnabled = false
            binding.btnImport.isEnabled = false
        }
    }

    fun appendLogPublic(message: String) {
        appendLog(message)
    }

    private fun appendLog(message: String) {
        val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        val existing = binding.tvLog.text.toString()
        val newEntry = "[$time] $message"
        // 新日志插入最顶部，方便用户看最新结果
        binding.tvLog.text = if (existing == getString(R.string.log_empty))
            newEntry
        else
            "$newEntry\n$existing"
    }
}
