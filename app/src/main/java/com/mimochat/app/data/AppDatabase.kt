package com.mimochat.app.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Dao
interface ConversationDao {
    @Query("SELECT * FROM conversations ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<Conversation>>

    /** 只带消息的会话（空会话不出现在列表） */
    @Query(
        """
        SELECT * FROM conversations
        WHERE id IN (SELECT DISTINCT conversationId FROM messages)
        ORDER BY updatedAt DESC
        """
    )
    fun observeAllWithMessages(): Flow<List<Conversation>>

    @Query("SELECT * FROM conversations ORDER BY updatedAt DESC")
    suspend fun getAll(): List<Conversation>

    @Query("SELECT * FROM conversations WHERE id = :id")
    fun observeById(id: Long): Flow<Conversation?>

    @Query("SELECT * FROM conversations WHERE id = :id")
    suspend fun getById(id: Long): Conversation?

    @Insert
    suspend fun insert(conversation: Conversation): Long

    @Update
    suspend fun update(conversation: Conversation)

    @Query("UPDATE conversations SET title = :title, updatedAt = :ts WHERE id = :id")
    suspend fun updateTitle(id: Long, title: String, ts: Long)

    @Query("DELETE FROM conversations WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM messages WHERE conversationId = :id")
    suspend fun deleteMessages(id: Long)
}

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages WHERE conversationId = :convId ORDER BY createdAt ASC, id ASC")
    fun observeByConversation(convId: Long): Flow<List<ChatMessage>>

    @Query("SELECT * FROM messages WHERE conversationId = :convId ORDER BY createdAt ASC, id ASC")
    suspend fun getByConversation(convId: Long): List<ChatMessage>

    @Query("SELECT * FROM messages WHERE id = :id")
    suspend fun getById(id: Long): ChatMessage?

    @Insert
    suspend fun insert(message: ChatMessage): Long

    @Update
    suspend fun update(message: ChatMessage)

    @Query("DELETE FROM messages WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM messages WHERE conversationId = :convId AND id >= :fromId")
    suspend fun deleteFromId(convId: Long, fromId: Long)

    @Query("SELECT * FROM messages WHERE content LIKE '%' || :query || '%' ORDER BY createdAt DESC, id DESC LIMIT 200")
    suspend fun searchMessages(query: String): List<ChatMessage>

    /** 每个会话的首条用户消息（对话列表两行预览用，一次查询） */
    @Query(
        """
        SELECT m.* FROM messages m
        WHERE m.role = 'user' AND m.id = (
            SELECT MIN(m2.id) FROM messages m2
            WHERE m2.conversationId = m.conversationId AND m2.role = 'user'
        )
        """
    )
    fun observeFirstUserMessages(): Flow<List<ChatMessage>>

    /** 每个会话的首条 AI 回复 */
    @Query(
        """
        SELECT m.* FROM messages m
        WHERE m.role = 'assistant' AND m.id = (
            SELECT MIN(m2.id) FROM messages m2
            WHERE m2.conversationId = m.conversationId AND m2.role = 'assistant'
        )
        """
    )
    fun observeFirstAssistantMessages(): Flow<List<ChatMessage>>

    /** 首问+首答一次查出（列表两行预览用，减少一次全表刷新） */
    @Query(
        """
        SELECT * FROM messages WHERE id IN (
          SELECT MIN(id) FROM messages WHERE role='user' GROUP BY conversationId
          UNION
          SELECT MIN(id) FROM messages WHERE role='assistant' GROUP BY conversationId
        )
        """
    )
    fun observePreviews(): Flow<List<ChatMessage>>
}

@Database(
    entities = [Conversation::class, ChatMessage::class],
    version = 5,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun conversationDao(): ConversationDao
    abstract fun messageDao(): MessageDao

    companion object {
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE messages ADD COLUMN reasoning TEXT NOT NULL DEFAULT ''")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE messages ADD COLUMN thinkingMs INTEGER NOT NULL DEFAULT 0")
            }
        }

        
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE conversations ADD COLUMN contextSummary TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE conversations ADD COLUMN summaryUpTo INTEGER NOT NULL DEFAULT 0")
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE messages ADD COLUMN citationsJson TEXT NOT NULL DEFAULT ''")
            }
        }

        @Volatile
        private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "mimo_chat.db"
                ).addMigrations(MIGRATION_4_5, MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5).build().also { instance = it }
            }
    }
}
