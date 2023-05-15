package it.matteoleggio.gallerydl.database.models

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
@Entity(tableName = "commandTemplates")
data class CommandTemplate(
    @PrimaryKey(autoGenerate = true)
    var id: Long,
    var title: String,
    var content: String
)