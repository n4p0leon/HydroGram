package dev.hydrogram.mod

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class HydroDatabase private constructor(context: Context) :
    SQLiteOpenHelper(context, "hydrogram_archive.db", null, 1) {

    companion object {
        @Volatile
        var instance: HydroDatabase? = null
            private set

        fun init(context: Context) {
            if (instance == null) {
                synchronized(this) {
                    if (instance == null) {
                        instance = HydroDatabase(context.applicationContext)
                    }
                }
            }
        }
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS deleted_messages (
                dialog_id INTEGER NOT NULL,
                msg_id INTEGER NOT NULL,
                deleted_at INTEGER NOT NULL,
                PRIMARY KEY (dialog_id, msg_id)
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS edit_history (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                dialog_id INTEGER NOT NULL,
                msg_id INTEGER NOT NULL,
                body TEXT NOT NULL,
                edited_at INTEGER NOT NULL
            )
            """.trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    fun markDeleted(dialogId: Long, ids: List<Int>) {
        val db = writableDatabase
        val now = System.currentTimeMillis()
        db.beginTransaction()
        try {
            for (msgId in ids) {
                val values = ContentValues().apply {
                    put("dialog_id", dialogId)
                    put("msg_id", msgId)
                    put("deleted_at", now)
                }
                db.insertWithOnConflict("deleted_messages", null, values, SQLiteDatabase.CONFLICT_IGNORE)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun appendEditRevision(dialogId: Long, msgId: Int, text: String) {
        val values = ContentValues().apply {
            put("dialog_id", dialogId)
            put("msg_id", msgId)
            put("body", text)
            put("edited_at", System.currentTimeMillis())
        }
        writableDatabase.insert("edit_history", null, values)
    }
}
