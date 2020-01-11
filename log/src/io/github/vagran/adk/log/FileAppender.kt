package io.github.vagran.adk.log

import java.io.BufferedWriter
import java.io.PrintWriter
import java.nio.file.FileVisitOption
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.nio.file.attribute.BasicFileAttributes
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.*
import java.util.zip.GZIPOutputStream
import kotlin.concurrent.thread


class FileAppender(private val config: LogConfiguration.Appender):
    Appender(GetPattern(config), config.level) {

    override fun AppendMessageImpl(msg: LogMessage)
    {
        file.write(pattern!!.FormatMessage(msg))
        file.newLine()
        file.flush()
        msg.exception?.also {
            it.printStackTrace(printWriter)
        }

        if (checkRolling) {
            val curTime = Instant.now()
            if (nextCheck == null || nextCheck!! <= curTime) {
                CheckRoll(config.fileParams!!, curTime)
            }
        }
    }

    override fun Close()
    {
        if (checkRolling) {
            CheckRoll(config.fileParams!!, Instant.now())
        }
        file.close()
        compressThread?.join()
    }

    // /////////////////////////////////////////////////////////////////////////////////////////////

    private var file = OpenFile(config.fileParams!!.path)
    private var printWriter = PrintWriter(file)
    private val checkRolling = config.fileParams!!.maxSize != null || config.fileParams!!.maxTime != null
    private var nextCheck: Instant? = null
    private var creationTime = GetCreationTime(config.fileParams!!.path)
    private var compressThread: Thread? = null
    private val oldFilesPat = GetOldPattern(config.fileParams!!)

    private companion object {
        val ROLL_CHECK_INTERVAL: Duration = Duration.ofSeconds(30)

        fun GetPattern(config: LogConfiguration.Appender): Pattern
        {
            return Pattern(config.pattern ?: LogConfiguration.DEFAULT_PATTERN)
        }

        fun GetCreationTime(path: Path): Instant
        {
            val attrs = Files.readAttributes(path, BasicFileAttributes::class.java)
            return attrs.creationTime().toInstant()
        }

        fun OpenFile(path: Path): BufferedWriter
        {
            return Files.newBufferedWriter(path,
                                           StandardOpenOption.WRITE,
                                           StandardOpenOption.APPEND,
                                           StandardOpenOption.CREATE,
                                           StandardOpenOption.DSYNC)
        }

        fun GetOldPattern(config: LogConfiguration.Appender.FileParams): RegExp
        {
            val pat = StringBuilder()
            pat.append(EscapeRegExp(config.path.fileName.toString()))
            pat.append("_\\d{4}\\-\\d{2}\\-\\d{2}_\\d{2}\\-\\d{2}\\-\\d{2}")
            if (config.compressOld) {
                pat.append("\\.gz")
            }
            return RegExp.compile(pat.toString())
        }

        fun EscapeRegExp(s: String): String
        {
            val sb = StringBuilder()
            for (c in s) {
                if (c in "-.?*+{}()[]\\") {
                    sb.append('\\')
                }
                sb.append(c)
            }
            return sb.toString()
        }
    }

    private fun CheckRoll(fileParams: LogConfiguration.Appender.FileParams, curTime: Instant)
    {
        var doRoll = if (fileParams.maxSize != null) {
            val size = Files.size(fileParams.path)
            size >= fileParams.maxSize!!
        } else {
            false
        }

        if (!doRoll && fileParams.maxTime != null) {
            doRoll = creationTime.plus(fileParams.maxTime!!) <= curTime
        }

        if (doRoll) {
            Roll()
        }

        nextCheck = if (fileParams.maxSize != null) {
            curTime.plus(ROLL_CHECK_INTERVAL)
        } else {
            creationTime.plus(fileParams.maxTime).plus(Duration.ofSeconds(3))
        }
    }

    private fun Roll()
    {
        file.close()
        val path = config.fileParams!!.path
        val newName = path.fileName.toString() +
            DateTimeFormatter.ofPattern("_YYYY-MM-dd_HH-mm-ss").format(
            LocalDateTime.ofInstant(creationTime, ZoneId.systemDefault()))

        val newPath = path.resolveSibling(newName)
        Files.move(path, newPath)

        if (config.fileParams!!.compressOld) {
            CompressInThread(newPath)
        }

        if (config.fileParams!!.preserveNum != null && !config.fileParams!!.compressOld) {
            PreserveOld(config.fileParams!!.preserveNum!!)
        }

        file = OpenFile(path)
        printWriter = PrintWriter(file)
        creationTime = GetCreationTime(path)
    }

    private fun Compress(path: Path)
    {
        val outPath = path.resolveSibling(path.fileName.toString() + ".gz")
        Files.newInputStream(path).use {
            ins ->
            Files.newOutputStream(outPath).use {
                GZIPOutputStream(it).use {
                    outs ->
                    ins.transferTo(outs)
                }
            }
        }
        Files.delete(path)
    }

    private fun CompressInThread(path: Path)
    {
        /* Block if previous compression still in progress. */
        compressThread?.join()
        compressThread = thread {
            Compress(path)
            config.fileParams?.preserveNum?.also {
                PreserveOld(it)
            }
        }
    }

    private fun PreserveOld(preserveNum: Int)
    {
        val dirPath = config.fileParams!!.path.parent
        val foundFiles = TreeMap<Instant, Path>()
        Files.walk(dirPath, 1, FileVisitOption.FOLLOW_LINKS).forEach {
            path ->
            if (path == dirPath) {
                return@forEach
            }
            val fileName = path.fileName.toString()
            val matcher = oldFilesPat.matcher(fileName)
            if (matcher.matches()) {
                foundFiles[GetCreationTime(path)] = path
            }
        }

        val it = foundFiles.iterator()
        while (foundFiles.size > preserveNum) {
            Files.delete(it.next().value)
            it.remove()
        }
    }
}
