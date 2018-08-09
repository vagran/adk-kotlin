package com.ast.adk.log

import java.io.BufferedWriter
import java.io.PrintWriter
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.nio.file.attribute.BasicFileAttributes
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.zip.GZIPOutputStream
import kotlin.concurrent.thread


class FileAppender(private val config: Configuration.Appender):
    Appender(GetPattern(config), config.level) {

    override fun AppendMessageImpl(msg: LogMessage)
    {
        file.write(pattern!!.FormatMessage(msg))
        file.newLine()
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

    private companion object {
        val ROLL_CHECK_INTERVAL: Duration = Duration.ofSeconds(30)

        fun GetPattern(config: Configuration.Appender): Pattern
        {
            return if (config.pattern == null) {
                Pattern(Configuration.DEFAULT_PATTERN)
            } else {
                Pattern(config.pattern!!)
            }
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
                                           StandardOpenOption.CREATE)
        }
    }

    private fun CheckRoll(fileParams: Configuration.Appender.FileParams, curTime: Instant)
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
        val newName = DateTimeFormatter.ofPattern("YYYY-MM-dd_HH-mm-ss_").format(
            LocalDateTime.ofInstant(creationTime, ZoneId.systemDefault())) +
            path.fileName
        val newPath = path.resolveSibling(newName)
        Files.move(path, newPath)

        if (config.fileParams!!.compressOld) {
            CompressInThread(newPath)
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
        compressThread = thread { Compress(path) }
    }
}
