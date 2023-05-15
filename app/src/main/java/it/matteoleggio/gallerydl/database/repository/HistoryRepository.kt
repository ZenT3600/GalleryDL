package it.matteoleggio.gallerydl.database.repository

import androidx.lifecycle.LiveData
import it.matteoleggio.gallerydl.database.dao.HistoryDao
import it.matteoleggio.gallerydl.database.models.HistoryItem
import it.matteoleggio.gallerydl.util.FileUtil

class HistoryRepository(private val historyDao: HistoryDao) {
    val items : LiveData<List<HistoryItem>> = historyDao.getAllHistory()
    enum class HistorySort{
        DESC, ASC
    }

    enum class HistorySortType {
        DATE, TITLE, AUTHOR, FILESIZE
    }

    suspend fun getItem(id: Int) : HistoryItem {
        return historyDao.getHistoryItem(id)
    }

    fun getAll() : List<HistoryItem> {
        return historyDao.getAllHistoryList()
    }

    fun getFiltered(query : String, format : String, site : String, sortType: HistorySortType, sort: HistorySort) : List<HistoryItem> {
        return when(sortType){
            HistorySortType.DATE ->  historyDao.getHistorySortedByID(query, format, site, sort.toString())
            HistorySortType.TITLE ->  historyDao.getHistorySortedByTitle(query, format, site, sort.toString())
            HistorySortType.AUTHOR ->  historyDao.getHistorySortedByAuthor(query, format, site, sort.toString())
            HistorySortType.FILESIZE ->  {
                val items = historyDao.getHistorySortedByID(query, format, site, sort.toString())
                when(sort){
                    HistorySort.DESC -> items.sortedByDescending { it.format.filesize }
                    HistorySort.ASC -> items.sortedBy { it.format.filesize }
                }
            }
        }
    }

    suspend fun insert(item: HistoryItem){
        historyDao.insert(item)
    }

    suspend fun delete(item: HistoryItem){
        historyDao.delete(item.id)
    }

    suspend fun deleteAll(){
        historyDao.deleteAll()
    }

    suspend fun deleteDuplicates(){
        historyDao.deleteDuplicates()
    }

    suspend fun update(item: HistoryItem){
        historyDao.update(item)
    }

    suspend fun clearDeletedHistory(){
        val fileUtil = FileUtil()
        items.value?.forEach { item ->
            if (!fileUtil.exists(item.downloadPath)){
                historyDao.delete(item.id)
            }
        }
    }

}