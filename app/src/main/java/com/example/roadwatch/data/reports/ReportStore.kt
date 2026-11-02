package com.example.roadwatch.data.reports

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.json.JSONArray
import java.io.File
import java.util.UUID

data class ScanReport(val id: String, val owner: String, val started: Long, val status: String, val detections: Int, val syncError: String?)
data class Capture(val id: String, val reportId: String, val path: String, val boxes: String)

/** SQLite transactions and private image files survive process death and device restart. */
class ReportStore internal constructor(private val context: Context, databaseName: String = "roadwatch_reports.db") : SQLiteOpenHelper(context, databaseName, null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE reports(id TEXT PRIMARY KEY, owner TEXT NOT NULL, started INTEGER NOT NULL, status TEXT NOT NULL, sync_error TEXT)")
        db.execSQL("CREATE TABLE captures(id TEXT PRIMARY KEY, report_id TEXT NOT NULL, path TEXT NOT NULL, boxes TEXT NOT NULL)")
        db.execSQL("CREATE INDEX report_owner ON reports(owner, status)")
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
    fun start(owner: String): String {
        val id = UUID.randomUUID().toString()
        writableDatabase.insertOrThrow("reports", null, ContentValues().apply {
            put("id", id); put("owner", owner); put("started", System.currentTimeMillis()); put("status", "recording")
        })
        return id
    }
    fun finish(id: String) { writableDatabase.execSQL("UPDATE reports SET status='pending' WHERE id=? AND status='recording'", arrayOf(id)) }
    private val recoveredOwners = mutableSetOf<String>()
    fun recoverOncePerProcess(owner: String) = synchronized(recoveredOwners) {
        if (owner !in recoveredOwners) {
            recoverInterrupted(owner)
            recoveredOwners.add(owner)
        }
    }
    fun recoverInterrupted(owner: String) {
        writableDatabase.execSQL("UPDATE reports SET status='pending' WHERE owner=? AND status='recording'", arrayOf(owner))
    }
    fun markSynced(id: String) { writableDatabase.execSQL("UPDATE reports SET status='synced', sync_error=NULL WHERE id=?", arrayOf(id)) }
    fun syncError(owner: String, message: String) {
        writableDatabase.execSQL("UPDATE reports SET sync_error=? WHERE owner=? AND status='pending'", arrayOf(message,owner))
    }
    fun reports(owner: String): List<ScanReport> = readableDatabase.rawQuery(
        "SELECT r.id,r.owner,r.started,r.status,(SELECT count(*) FROM captures c WHERE c.report_id=r.id),r.sync_error FROM reports r WHERE r.owner=? ORDER BY r.started DESC", arrayOf(owner)
    ).use { c -> buildList { while(c.moveToNext()) add(ScanReport(c.getString(0),c.getString(1),c.getLong(2),c.getString(3),c.getInt(4),c.getString(5))) } }
    fun captures(id: String): List<Capture> = readableDatabase.rawQuery("SELECT id,report_id,path,boxes FROM captures WHERE report_id=?", arrayOf(id)).use { c ->
        buildList { while(c.moveToNext()) add(Capture(c.getString(0),c.getString(1),c.getString(2),c.getString(3))) }
    }
    fun addCapture(reportId: String, jpeg: ByteArray, boxes: JSONArray) {
        val id = UUID.randomUUID().toString()
        val directory = File(context.filesDir, "scans/$reportId").apply { check(mkdirs() || isDirectory) }
        val file = File(directory, "$id.jpg")
        file.outputStream().use { it.write(jpeg); it.fd.sync() }
        try {
            writableDatabase.insertOrThrow("captures", null, ContentValues().apply {
                put("id", id); put("report_id", reportId); put("path", file.absolutePath); put("boxes", boxes.toString())
            })
        } catch(e: Exception) { file.delete(); throw e }
    }
    companion object {
        @Volatile private var instance: ReportStore? = null
        fun get(context: Context): ReportStore = instance ?: synchronized(this) {
            instance ?: ReportStore(context.applicationContext).also { instance = it }
        }
    }
}
