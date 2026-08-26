package com.afyzfur.afyzhub.data.local.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.afyzfur.afyzhub.data.local.dao.ConversationDao
import com.afyzfur.afyzhub.data.local.dao.MessageDao
import com.afyzfur.afyzhub.data.local.entity.ConversationEntity
import com.afyzfur.afyzhub.data.local.entity.MessageEntity

@Database(
    entities = [ConversationEntity::class, MessageEntity::class],
    version = 4,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun conversationDao(): ConversationDao
    abstract fun messageDao(): MessageDao

    companion object {
        /** v1 -> v2：消息表新增发送状态与错误信息，并补上会话外键索引。 */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE messages ADD COLUMN status TEXT NOT NULL DEFAULT 'success'"
                )
                db.execSQL(
                    "ALTER TABLE messages ADD COLUMN errorMessage TEXT"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_messages_conversationId ON messages(conversationId)"
                )
            }
        }

        /** v2 -> v3：消息表新增模型名、token 用量与耗时元信息，全部可空。 */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE messages ADD COLUMN model TEXT")
                db.execSQL("ALTER TABLE messages ADD COLUMN promptTokens INTEGER")
                db.execSQL("ALTER TABLE messages ADD COLUMN completionTokens INTEGER")
                db.execSQL("ALTER TABLE messages ADD COLUMN latencyMs INTEGER")
            }
        }

        /**
         * v3 -> v4：会话表新增模型生成的总结，可空。
         *
         * 已有会话的该列为 null，界面会退回显示末条消息，
         * 不需要为历史数据补生成——那要为每个会话各发一次请求。
         */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE conversations ADD COLUMN summary TEXT")
            }
        }
    }
}
