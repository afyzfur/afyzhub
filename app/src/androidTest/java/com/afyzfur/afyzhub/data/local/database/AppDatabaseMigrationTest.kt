package com.afyzfur.afyzhub.data.local.database

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppDatabaseMigrationTest {
    @get:Rule val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(), AppDatabase::class.java
    )
    private val migrations = arrayOf(
        AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3,
        AppDatabase.MIGRATION_3_4, AppDatabase.MIGRATION_4_5, AppDatabase.MIGRATION_5_6
    )
    @Test fun everyHistoricalVersionPreservesMessages() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        for (start in 1..5) {
            val name = "migration-$start-to-6"
            context.deleteDatabase(name)
            helper.createDatabase(name, start).apply {
                execSQL("INSERT INTO conversations(id,title,createdAt,updatedAt) VALUES(1,'保留会话',10,20)")
                execSQL("INSERT INTO messages(id,conversationId,content,role,createdAt) VALUES(1,1,'历史正文 😀','user',30)")
                close()
            }
            helper.runMigrationsAndValidate(name, 6, true, *migrations).apply {
                query("SELECT content,status,cachedTokens FROM messages WHERE id=1").use { cursor ->
                    assertTrue(cursor.moveToFirst())
                    assertEquals("历史正文 😀", cursor.getString(0))
                    assertEquals("success", cursor.getString(1))
                    assertTrue(cursor.isNull(2))
                }
                query("SELECT title,pinned,starred,`group` FROM conversations WHERE id=1").use { cursor ->
                    assertTrue(cursor.moveToFirst())
                    assertEquals("保留会话",cursor.getString(0))
                    assertEquals(0,cursor.getInt(1)); assertEquals(0,cursor.getInt(2))
                    assertEquals("",cursor.getString(3))
                }
                close()
            }
            val room = Room.databaseBuilder(context, AppDatabase::class.java, name)
                .addMigrations(*migrations).build()
            try {
                    val db = room.openHelper.writableDatabase
                    db.execSQL("DELETE FROM conversations WHERE id=1")
                    db.query("SELECT COUNT(*) FROM messages").use { cursor ->
                        assertTrue(cursor.moveToFirst()); assertEquals(0,cursor.getInt(0))
                    }
                }
            finally { room.close() }
            context.deleteDatabase(name)
        }
    }
    @Test fun everyIndividualMigrationValidates() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        for (start in 1..5) {
            val name="migration-step-$start"
            context.deleteDatabase(name)
            helper.createDatabase(name,start).close()
            helper.runMigrationsAndValidate(name,start+1,true,*migrations).close()
            context.deleteDatabase(name)
        }
    }
}