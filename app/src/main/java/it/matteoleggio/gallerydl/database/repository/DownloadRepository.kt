package it.matteoleggio.gallerydl.database.repository

import androidx.lifecycle.LiveData
import it.matteoleggio.gallerydl.database.Converters
import it.matteoleggio.gallerydl.database.dao.DownloadDao
import it.matteoleggio.gallerydl.database.models.DownloadItem
import it.matteoleggio.gallerydl.database.models.ResultItem
import kotlinx.coroutines.flow.Flow

class DownloadRepository(private val downloadDao: DownloadDao) {
    val allDownloads : Flow<List<DownloadItem>> = downloadDao.getAllDownloads()
    val activeDownloads : Flow<List<DownloadItem>> = downloadDao.getActiveDownloads()
    val activeDownloadsCount : Flow<Int> = downloadDao.getActiveDownloadsCount()
    val queuedDownloads : Flow<List<DownloadItem>> = downloadDao.getQueuedDownloads()
    val cancelledDownloads : Flow<List<DownloadItem>> = downloadDao.getCancelledDownloads()
    val erroredDownloads : Flow<List<DownloadItem>> = downloadDao.getErroredDownloads()
    val processingDownloads : Flow<List<DownloadItem>> = downloadDao.getProcessingDownloads()

    enum class Status {
        Active, Queued, Error, Processing, Cancelled
    }

    suspend fun insert(item: DownloadItem) : Long {
        return downloadDao.insert(item)
    }

    suspend fun insertAll(items: List<DownloadItem>) : List<Long> {
        return downloadDao.insertAll(items)
    }

    suspend fun delete(item: DownloadItem){
        downloadDao.delete(item.id)
    }

    suspend fun update(item: DownloadItem){
        downloadDao.update(item)
    }


    suspend fun setDownloadStatus(item: DownloadItem, status: Status){
        item.status = status.toString()
        update(item)
    }

    fun getItemByID(id: Long) : DownloadItem {
        return downloadDao.getDownloadById(id)
    }

    fun getActiveDownloads() : List<DownloadItem> {
        return downloadDao.getActiveDownloadsList()
    }

    fun getActiveAndQueuedDownloads() : List<DownloadItem> {
        return downloadDao.getActiveAndQueuedDownloadsList()
    }

    fun getQueuedDownloads() : List<DownloadItem> {
        return downloadDao.getQueuedDownloadsList();
    }

    fun getCancelledDownloads() : List<DownloadItem> {
        return downloadDao.getCancelledDownloadsList()
    }

    suspend fun deleteCancelled(){
        downloadDao.deleteCancelled()
    }

    suspend fun deleteErrored(){
        downloadDao.deleteErrored()
    }

    suspend fun cancelQueued(){
        downloadDao.cancelQueued()
    }


    fun checkIfReDownloadingErroredOrCancelled(item: DownloadItem) : Long {
        val converters = Converters()
        val format = converters.formatToString(item.format)
        return try {
            val i = downloadDao.getUnfinishedByURLAndFormat(item.url, format)
            i.id
        }catch (e: Exception){
            0L
        }
    }

}