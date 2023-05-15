package it.matteoleggio.gallerydl.database.dao

import androidx.lifecycle.LiveData
import androidx.room.*
import it.matteoleggio.gallerydl.database.models.CommandTemplate
import it.matteoleggio.gallerydl.database.models.TemplateShortcut

@Dao
interface CommandTemplateDao {
    @Query("SELECT * FROM commandTemplates ORDER BY id DESC")
    fun getAllTemplates() : List<CommandTemplate>

    @Query("SELECT * FROM commandTemplates ORDER BY id DESC")
    fun getAllTemplatesLiveData() : LiveData<List<CommandTemplate>>

    @Query("SELECT * FROM templateShortcuts ORDER BY id DESC")
    fun getAllShortcutsLiveData() : LiveData<List<TemplateShortcut>>

    @Query("SELECT * FROM templateShortcuts ORDER BY id DESC")
    fun getAllShortcuts() : List<TemplateShortcut>

    @Query("SELECT COUNT(id) FROM commandTemplates")
    fun getTotalNumber() : Int

    @Query("SELECT COUNT(id) FROM templateShortcuts")
    fun getTotalShortcutNumber() : Int

    @Query("SELECT * FROM commandTemplates WHERE id=:id LIMIT 1")
    fun getTemplate(id: Long) : CommandTemplate

    @Query("SELECT * FROM commandTemplates ORDER BY id DESC LIMIT 1")
    fun getFirst() : CommandTemplate

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(item: CommandTemplate)

    @Query("DELETE FROM commandTemplates")
    suspend fun deleteAll()

    @Query("DELETE FROM commandTemplates WHERE id=:itemId")
    suspend fun delete(itemId: Long)

    @Insert(TemplateShortcut::class, OnConflictStrategy.REPLACE)
    suspend fun insertShortcut(shortcut: TemplateShortcut)

    @Query("SELECT COUNT(id) FROM templateShortcuts WHERE content=:content")
    suspend fun checkExistingShortcut(content: String) : Int

    @Query("DELETE FROM templateShortcuts WHERE id=:itemId")
    suspend fun deleteShortcut(itemId: Long)

    @Update
    suspend fun update(item: CommandTemplate)
}