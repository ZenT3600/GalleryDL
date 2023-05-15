package it.matteoleggio.gallerydl.work

import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.preference.PreferenceManager
import androidx.work.*
import it.matteoleggio.gallerydl.MainActivity
import it.matteoleggio.gallerydl.R
import it.matteoleggio.gallerydl.database.DBManager
import it.matteoleggio.gallerydl.database.models.DownloadItem
import it.matteoleggio.gallerydl.database.models.HistoryItem
import it.matteoleggio.gallerydl.database.repository.DownloadRepository
import it.matteoleggio.gallerydl.database.viewmodel.DownloadViewModel
import it.matteoleggio.gallerydl.util.FileUtil
import it.matteoleggio.gallerydl.util.InfoUtil
import it.matteoleggio.gallerydl.util.NotificationUtil
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.io.File
import java.util.*
import kotlin.collections.ArrayList


class DownloadWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : Worker(context, workerParams) {
    override fun doWork(): Result {
        itemId = inputData.getLong("id", 0)
        if (itemId == 0L) return Result.failure()

        val notificationUtil = NotificationUtil(context)
        val fileUtil = FileUtil()
        val infoUtil = InfoUtil(context)
        val dbManager = DBManager.getInstance(context)
        val dao = dbManager.downloadDao
        val repository = DownloadRepository(dao)
        val historyDao = dbManager.historyDao
        val resultDao = dbManager.resultDao
        val handler = Handler(Looper.getMainLooper())


        val downloadItem: DownloadItem?
        try {
            downloadItem = repository.getItemByID(itemId)
        }catch (e: Exception){
            e.printStackTrace()
            return Result.failure()
        }
        if (downloadItem.status != DownloadRepository.Status.Queued.toString()) return Result.failure()

        Log.e(TAG, downloadItem.toString())

        runBlocking{
            repository.setDownloadStatus(downloadItem, DownloadRepository.Status.Active)
        }

        var wasQuickDownloaded = false
        //update item if its incomplete
        if (downloadItem.title.isEmpty() || downloadItem.author.isEmpty() || downloadItem.thumb.isEmpty()){
            notificationUtil.createUpdatingItemNotification(NotificationUtil.DOWNLOAD_SERVICE_CHANNEL_ID);

            runCatching {
                setProgressAsync(workDataOf("progress" to 0, "output" to context.getString(R.string.updating_download_data), "id" to downloadItem.id, "log" to false))
                val info = infoUtil.getMissingInfo(downloadItem.url)
                if (downloadItem.title.isEmpty()) downloadItem.title = info?.title.toString()
                if (downloadItem.author.isEmpty()) downloadItem.author = info?.author.toString()
                downloadItem.duration = info?.duration.toString()
                downloadItem.website = info?.website.toString()
                if (downloadItem.thumb.isEmpty()) downloadItem.thumb = info?.thumb.toString()
                runBlocking {
                    wasQuickDownloaded = resultDao.getCountInt() == 0
                    dao.update(downloadItem)
                }
            }
            notificationUtil.cancelDownloadNotification(NotificationUtil.DOWNLOAD_UPDATING_NOTIFICATION_ID)
        }

        val intent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE)
        val notification = notificationUtil.createDownloadServiceNotification(pendingIntent, downloadItem.title, downloadItem.id.toInt(), NotificationUtil.DOWNLOAD_SERVICE_CHANNEL_ID)
        val foregroundInfo = ForegroundInfo(downloadItem.id.toInt(), notification)
        setForegroundAsync(foregroundInfo)

        
        val url = downloadItem.url
        val request = YoutubeDLRequest(url)
        val type = downloadItem.type
        val downloadLocation = downloadItem.downloadPath

        val tempFileDir = File(context.cacheDir.absolutePath + "/downloads/" + downloadItem.id)
        tempFileDir.delete()
        tempFileDir.mkdirs()

        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
        val aria2 = sharedPreferences.getBoolean("aria2", false)
        if (aria2) {
            request.addOption("--downloader", "libaria2c.so")
            request.addOption("--external-downloader-args", "aria2c:\"--summary-interval=1\"")
        } else {
            val concurrentFragments = sharedPreferences.getInt("concurrent_fragments", 1)
            if (concurrentFragments > 1) request.addOption("-N", concurrentFragments)
        }
        val limitRate = sharedPreferences.getString("limit_rate", "")
        if (limitRate != "") request.addOption("-r", limitRate!!)
        if(downloadItem.type != DownloadViewModel.Type.command){
            if (downloadItem.SaveThumb) {
                request.addOption("--write-thumbnail")
                request.addOption("--convert-thumbnails", "png")
            }
            if (!sharedPreferences.getBoolean("mtime", false)){
                request.addOption("--no-mtime")
            }

            val sponsorBlockFilters : ArrayList<String> = downloadItem.videoPreferences.sponsorBlockFilters

            if (sponsorBlockFilters.isNotEmpty()) {
                val filters = java.lang.String.join(",", sponsorBlockFilters)
                request.addOption("--sponsorblock-remove", filters)
            }

            if(downloadItem.title.isNotBlank()){
                request.addCommands(listOf("--replace-in-metadata","title",".*.",downloadItem.title.take(200)))
            }
            if (downloadItem.author.isNotBlank()){
                request.addCommands(listOf("--replace-in-metadata","uploader",".*.",downloadItem.author.take(25)))
            }
            if (downloadItem.customFileNameTemplate.isBlank()) downloadItem.customFileNameTemplate = "%(uploader)s - %(title)s"

            if (downloadItem.downloadSections.isNotBlank()){
                downloadItem.downloadSections.split(";").forEach {
                    if (it.isBlank()) return@forEach
                    if (it.contains(":"))
                        request.addOption("--download-sections", "*$it")
                    else
                        request.addOption("--download-sections", it)
                }
                downloadItem.customFileNameTemplate += " %(section_title)s %(autonumber)s"
                request.addOption("--output-na-placeholder", " ")
            }
        }

        if (sharedPreferences.getBoolean("restrict_filenames", true)) {
            request.addOption("--restrict-filenames")
        }

        val cookiesFile = File(context.cacheDir, "cookies.txt")
        if (cookiesFile.exists()){
            request.addOption("--cookies", cookiesFile.absolutePath)
        }

        val keepCache = sharedPreferences.getBoolean("keep_cache", false)
        if(keepCache){
            request.addOption("--part")
            request.addOption("--keep-fragments")
        }

        when(type){
            DownloadViewModel.Type.image -> {
                if (downloadItem.videoPreferences.addChapters) {
                    request.addOption("--sponsorblock-mark", "all")
                    request.addOption("--embed-chapters")
                }
                if (downloadItem.videoPreferences.embedSubs) {
                    request.addOption("--embed-subs", "")
                }
                val defaultFormats = context.resources.getStringArray(R.array.video_formats)

                var videoFormatID = downloadItem.format.format_id
                Log.e(TAG, videoFormatID)
                var formatArgument = if (downloadItem.videoPreferences.removeAudio) "bestvideo" else "bestvideo+bestaudio/best"
                if (videoFormatID.isNotEmpty()) {
                    if (videoFormatID == context.resources.getString(R.string.best_quality) || videoFormatID == "best") videoFormatID = "bestvideo"
                    else if (videoFormatID == context.resources.getString(R.string.worst_quality) || videoFormatID == "worst") videoFormatID = "worst"
                    else if (defaultFormats.contains(videoFormatID)) videoFormatID = "bestvideo[height<="+videoFormatID.substring(0, videoFormatID.length -1)+"]"
                    if (!downloadItem.videoPreferences.removeAudio) formatArgument = "$videoFormatID+bestaudio/best/$videoFormatID"
                }
                Log.e(TAG, formatArgument)
                request.addOption("-f", formatArgument)
                val format = downloadItem.format.container
                if(format.isNotEmpty() && format != "Default" && format != context.getString(R.string.defaultValue)){
                    request.addOption("--merge-output-format", format)
                    if (format != "webm") {
                        val embedThumb = sharedPreferences.getBoolean("embed_thumbnail", false)
                        if (embedThumb) {
                            request.addOption("--embed-thumbnail")
                        }
                    }
                }

                if (downloadItem.videoPreferences.writeSubs){
                    request.addOption("--write-subs")
                    request.addOption("--write-auto-subs")
                    request.addOption("--sub-format", "str/ass/best")
                    request.addOption("--convert-subtitles", "srt")
                    request.addOption("--sub-langs", downloadItem.videoPreferences.subsLanguages)
                }

                if (downloadItem.videoPreferences.removeAudio &&
                    (downloadItem.format.acodec.isNotEmpty() && downloadItem.format.acodec != "none")){
                    request.addOption("--ppa", "ffmpeg:-an")
                }

                if (downloadItem.videoPreferences.splitByChapters  && downloadItem.downloadSections.isBlank()){
                    request.addOption("--split-chapters")
                    request.addOption("-P", tempFileDir.absolutePath)
                }else{
                    request.addOption("-o", tempFileDir.absolutePath + "/${downloadItem.customFileNameTemplate}.%(ext)s")
                }

            }
            DownloadViewModel.Type.command -> {
                request.addOption(
                    "--config-locations",
                    File(context.cacheDir.absolutePath + "/downloads/config${System.currentTimeMillis()}.txt").apply {
                        writeText(downloadItem.format.format_note)
                    }.absolutePath
                )

                request.addOption("-P", tempFileDir.absolutePath)

            }
        }

        val logDownloads = sharedPreferences.getBoolean("log_downloads", false) && !sharedPreferences.getBoolean("incognito", false)
        val logFolder = File(context.filesDir.absolutePath + "/logs")
        val logFile = fileUtil.getLogFile(context, downloadItem)

        if (logDownloads){
            logFolder.mkdirs()
            logFile.createNewFile()
            logFile.writeText("Downloading:\n" +
                    "Title: ${downloadItem.title}\n" +
                    "URL: ${downloadItem.url}\n" +
                    "Type: ${downloadItem.type}\n" +
                    "Format: ${downloadItem.format}\n\n" +
                    "Command: ${java.lang.String.join(" ", request.buildCommand())}\n\n")
        }

        runCatching {
            YoutubeDL.getInstance().execute(request, downloadItem.id.toString()){ progress, _, line ->
                setProgressAsync(workDataOf("progress" to progress.toInt(), "output" to line.chunked(5000).first().toString(), "id" to downloadItem.id, "log" to logDownloads))
                val title: String = downloadItem.title
                notificationUtil.updateDownloadNotification(
                    downloadItem.id.toInt(),
                    line, progress.toInt(), 0, title,
                    NotificationUtil.DOWNLOAD_SERVICE_CHANNEL_ID
                )
                if (logDownloads && logFile.exists()){
                    logFile.appendText("${line}\n")
                }
            }
        }.onSuccess {
            //move file from internal to set download directory
            setProgressAsync(workDataOf("progress" to 100, "output" to "Moving file to ${fileUtil.formatPath(downloadLocation)}", "id" to downloadItem.id, "log" to logDownloads))
            var finalPath : String?
            try {
                finalPath = fileUtil.moveFile(tempFileDir.absoluteFile,context, downloadLocation, keepCache){ p ->
                    setProgressAsync(workDataOf("progress" to p, "output" to "Moving file to ${fileUtil.formatPath(downloadLocation)}", "id" to downloadItem.id, "log" to logDownloads))
                }
                setProgressAsync(workDataOf("progress" to 100, "output" to "Moved file to $finalPath", "id" to downloadItem.id, "log" to logDownloads))
            }catch (e: Exception){
                finalPath = context.getString(R.string.unfound_file)
                e.printStackTrace()
                handler.postDelayed({
                    Toast.makeText(context, e.message, Toast.LENGTH_SHORT).show()
                }, 1000)
            }
            //put download in history
            val incognito = sharedPreferences.getBoolean("incognito", false)
            if (!incognito) {
                val unixtime = System.currentTimeMillis() / 1000
                val file = File(finalPath!!)
                downloadItem.format.filesize = if (file.exists()) file.length() else 0L
                val historyItem = HistoryItem(0, downloadItem.url, downloadItem.title, downloadItem.author, downloadItem.duration, downloadItem.thumb, downloadItem.type, unixtime, finalPath, downloadItem.website, downloadItem.format, downloadItem.id)
                runBlocking {
                    historyDao.insert(historyItem)
                }
            }
            notificationUtil.cancelDownloadNotification(downloadItem.id.toInt())

            notificationUtil.createDownloadFinished(
                downloadItem.title,  if (finalPath.equals(context.getString(R.string.unfound_file))) null else finalPath,
                NotificationUtil.DOWNLOAD_FINISHED_CHANNEL_ID
            )

            if (wasQuickDownloaded){
                runCatching {
                    setProgressAsync(workDataOf("progress" to 100, "output" to "Creating Result Items", "id" to downloadItem.id, "log" to false))
                    runBlocking {
                        infoUtil.getFromYTDL(downloadItem.url).forEach { res ->
                            if (res != null) {
                                resultDao.insert(res)
                            }
                        }
                    }
                }
            }

            runBlocking {
                dao.delete(downloadItem.id)
            }
        }.onFailure {
            if (it is YoutubeDL.CanceledException) {
                downloadItem.status = DownloadRepository.Status.Cancelled.toString()
                runBlocking {
                    dao.update(downloadItem)
                }
                return Result.failure(
                    Data.Builder().putString("output", "Download has been cancelled!").build()
                )
            }else{
                if (logDownloads && logFile.exists()){
                    logFile.appendText("${it.message}\n")
                }

                tempFileDir.delete()
                handler.postDelayed({
                    Toast.makeText(context, it.message, Toast.LENGTH_LONG).show()
                }, 1000)

                Log.e(TAG, context.getString(R.string.failed_download), it)
                notificationUtil.cancelDownloadNotification(downloadItem.id.toInt())

                downloadItem.status = DownloadRepository.Status.Error.toString()
                runBlocking {
                    dao.update(downloadItem)
                }

                notificationUtil.createDownloadErrored(
                    downloadItem.title, it.message,
                    if (logDownloads) logFile else null,
                    NotificationUtil.DOWNLOAD_FINISHED_CHANNEL_ID
                )

                return Result.failure(
                    Data.Builder().putString("output", it.toString()).build()
                )
            }
        }
        
        return Result.success()
    }

    override fun onStopped() {
        YoutubeDL.getInstance().destroyProcessById(itemId.toInt().toString())
        super.onStopped()
    }


    companion object {
        var itemId: Long = 0
        const val TAG = "DownloadWorker"
    }

}