package com.elio.skillroundtable.data

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "characters")
data class Character(
    @PrimaryKey val id: String,
    val name: String,
    val avatar: String, // Emoji 或简短标识
    val tagline: String,
    val systemPrompt: String,
    val skillAssetPath: String = "", // 技能文件在 assets 中的相对路径
    val order: Int,
    val isActive: Boolean = true,
    val skillDescriptionVector: String = "",
    @ColumnInfo(defaultValue = "Aoede") val voiceConfig: String = "Aoede"
)

@Dao
interface CharacterDao {
    @Query("SELECT * FROM characters ORDER BY `order` ASC")
    fun getAllCharacters(): Flow<List<Character>>

    @Query("SELECT * FROM characters WHERE isActive = 1 ORDER BY `order` ASC")
    suspend fun getActiveCharacters(): List<Character>

    @Query("SELECT * FROM characters WHERE id = :id LIMIT 1")
    suspend fun getCharacterById(id: String): Character?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCharacter(character: Character)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(characters: List<Character>)

    @Query("DELETE FROM characters WHERE id = :id")
    suspend fun deleteCharacterById(id: String)
}

class CharacterRepository(private val characterDao: CharacterDao) {
    val allCharacters: Flow<List<Character>> = characterDao.getAllCharacters()

    suspend fun getActiveCharacters() = characterDao.getActiveCharacters()

    suspend fun getCharacterById(id: String) = characterDao.getCharacterById(id)

    suspend fun insert(character: Character) = characterDao.insertCharacter(character)

    suspend fun insertAll(characters: List<Character>) = characterDao.insertAll(characters)

    suspend fun deleteById(id: String) = characterDao.deleteCharacterById(id)
}
