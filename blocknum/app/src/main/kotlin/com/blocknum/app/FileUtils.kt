package com.blocknum.app

import java.io.InputStream
import java.io.OutputStream

/**
 * 文件读写工具
 *
 * 格式说明：
 *   CSV 格式，每行一个号码，UTF-8 编码，无标题行。
 *   与 JSON 相比，纯号码列表 CSV 体积更小、解析更快——
 *   例如 1000 条号码 CSV ≈ 14 KB，JSON 约 18 KB。
 *
 *   支持导入带 BOM 的 UTF-8 文件（Windows Excel 常见）。
 *   导入时忽略空行和注释行（以 # 开头）。
 */
object FileUtils {

    private const val BOM = "\uFEFF"

    /**
     * 将号码列表写出到 OutputStream（CSV 格式，UTF-8，每行一个号码）
     */
    fun exportToCsv(numbers: List<String>, outputStream: OutputStream) {
        outputStream.bufferedWriter(Charsets.UTF_8).use { writer ->
            // 写注释头，方便用户识别文件
            writer.write("# BlockNum export - ${numbers.size} numbers")
            writer.newLine()
            numbers.forEach { number ->
                writer.write(number)
                writer.newLine()
            }
        }
    }

    /**
     * 从 InputStream 读取号码列表（支持 CSV/TXT，UTF-8 或 UTF-8 BOM）
     * @return 去重后的有效号码列表
     */
    fun importFromCsv(inputStream: InputStream): List<String> {
        val numbers = LinkedHashSet<String>() // 保序去重
        inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
            reader.lineSequence().forEach { rawLine ->
                var line = rawLine.trim()
                // 去除 UTF-8 BOM
                if (line.startsWith(BOM)) line = line.removePrefix(BOM).trim()
                // 跳过空行和注释行
                if (line.isEmpty() || line.startsWith("#")) return@forEach
                // 去除行内注释（逗号后的内容），取第一列
                val number = line.split(",").first().trim()
                if (number.isNotEmpty()) {
                    numbers.add(number)
                }
            }
        }
        return numbers.toList()
    }
}
