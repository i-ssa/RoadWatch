package com.example.roadwatch.data.reports

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.json.JSONArray
import java.io.File
import java.util.UUID

data class ScanReport(val id: String, val owner: String, val started: Long, val status: String, val detections: Int, val syncError: String?)
data class Capture(val id: String, val reportId: String, val path: String, val boxes: String, val latitude: Double? = null, val longitude: Double? = null, val accuracy: Float? = null, val capturedAt: Long = 0)

/** SQLite transactions and private image files survive process death and device restart. */
class ReportStore internal constructor(private val context: Context, databaseName: String = "roadwatch_reports.db") : SQLiteOpenHelper(context, databaseName, null, 3) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE reports(id TEXT PRIMARY KEY, owner TEXT NOT NULL, started INTEGER NOT NULL, status TEXT NOT NULL, sync_error TEXT)")
        db.execSQL("CREATE TABLE captures(id TEXT PRIMARY KEY, report_id TEXT NOT NULL, path TEXT NOT NULL, boxes TEXT NOT NULL, latitude REAL, longitude REAL, accuracy REAL, captured_at INTEGER NOT NULL DEFAULT 0)")
        db.execSQL("CREATE INDEX report_owner ON reports(owner, status)")
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Legacy pending reports were queued without review. Require approval after upgrading.
        if (oldVersion < 3) {
            db.execSQL("ALTER TABLE captures ADD COLUMN latitude REAL")
            db.execSQL("ALTER TABLE captures ADD COLUMN longitude REAL")
            db.execSQL("ALTER TABLE captures ADD COLUMN accuracy REAL")
            db.execSQL("ALTER TABLE captures ADD COLUMN captured_at INTEGER NOT NULL DEFAULT 0")
        }
        if (oldVersion < 2) db.execSQL("UPDATE reports SET status='draft', sync_error=NULL WHERE status='pending'")
    }
    fun start(owner: String): String {
        val id = UUID.randomUUID().toString()
        writableDatabase.insertOrThrow("reports", null, ContentValues().apply {
            put("id", id); put("owner", owner); put("started", System.currentTimeMillis()); put("status", "recording")
        })
        return id
    }
    fun finish(id: String) { writableDatabase.execSQL("UPDATE reports SET status='draft' WHERE id=? AND status='recording'", arrayOf(id)) }
    @Synchronized
    fun submit(id: String, owner: String): Boolean = writableDatabase.update(
        "reports", ContentValues().apply { put("status", "pending"); putNull("sync_error") },
        "id=? AND owner=? AND status='draft'", arrayOf(id, owner)
    ) == 1
    private val recoveredOwners = mutableSetOf<String>()
    fun recoverOncePerProcess(owner: String) = synchronized(recoveredOwners) {
        if (owner !in recoveredOwners) {
            recoverInterrupted(owner)
            recoveredOwners.add(owner)
        }
    }
    fun recoverInterrupted(owner: String) {
        writableDatabase.execSQL("UPDATE reports SET status='draft' WHERE owner=? AND status='recording'", arrayOf(owner))
    }
    fun markSynced(id: String) { writableDatabase.execSQL("UPDATE reports SET status='synced', sync_error=NULL WHERE id=?", arrayOf(id)) }
    fun syncError(owner: String, message: String) {
        writableDatabase.execSQL("UPDATE reports SET sync_error=? WHERE owner=? AND status='pending'", arrayOf(message,owner))
    }
    fun reports(owner: String): List<ScanReport> = readableDatabase.rawQuery(
        "SELECT r.id,r.owner,r.started,r.status,(SELECT count(*) FROM captures c WHERE c.report_id=r.id),r.sync_error FROM reports r WHERE r.owner=? ORDER BY r.started DESC", arrayOf(owner)
    ).use { c -> buildList { while(c.moveToNext()) add(ScanReport(c.getString(0),c.getString(1),c.getLong(2),c.getString(3),c.getInt(4),c.getString(5))) } }
    fun captures(id: String): List<Capture> = readableDatabase.rawQuery("SELECT id,report_id,path,boxes,latitude,longitude,accuracy,captured_at FROM captures WHERE report_id=?", arrayOf(id)).use { c ->
        buildList { while(c.moveToNext()) add(Capture(c.getString(0),c.getString(1),c.getString(2),c.getString(3),if(c.isNull(4)) null else c.getDouble(4),if(c.isNull(5)) null else c.getDouble(5),if(c.isNull(6)) null else c.getFloat(6),c.getLong(7))) }
    }
    fun addCapture(reportId: String, jpeg: ByteArray, boxes: JSONArray, location: android.location.Location? = null) {
        val id = UUID.randomUUID().toString()
        val directory = File(context.filesDir, "scans/$reportId").apply { check(mkdirs() || isDirectory) }
        val file = File(directory, "$id.jpg")
        file.outputStream().use { it.write(jpeg); it.fd.sync() }
        try {
            writableDatabase.insertOrThrow("captures", null, ContentValues().apply {
                put("id", id); put("report_id", reportId); put("path", file.absolutePath); put("boxes", boxes.toString()); put("captured_at", System.currentTimeMillis())
                location?.let { put("latitude",it.latitude); put("longitude",it.longitude); put("accuracy",it.accuracy) }
            })
        } catch(e: Exception) { file.delete(); throw e }
    }
    @Synchronized
    fun removeCapture(id: String, reportId: String, owner: String): Boolean {
        val report = reports(owner).firstOrNull { it.id == reportId && it.status == "draft" } ?: return false
        val capture = captures(report.id).firstOrNull { it.id == id } ?: return false
        val removed = writableDatabase.delete("captures", "id=? AND report_id=?", arrayOf(id,reportId)) == 1
        if (removed) File(capture.path).delete()
        return removed
    }
    @Synchronized
    fun deleteLocalReport(id: String, owner: String): Boolean {
        val report = reports(owner).firstOrNull { it.id == id && it.status in listOf("draft","synced") } ?: return false
        val files = captures(report.id).map { File(it.path) }
        writableDatabase.beginTransaction()
        try {
            writableDatabase.delete("captures", "report_id=?", arrayOf(id))
            writableDatabase.delete("reports", "id=? AND owner=?", arrayOf(id,owner))
            writableDatabase.setTransactionSuccessful()
        } finally { writableDatabase.endTransaction() }
        files.forEach { it.delete() }
        File(context.filesDir,"scans/$id").delete()
        return true
    }
    companion object {
        @Volatile private var instance: ReportStore? = null
        fun get(context: Context): ReportStore = instance ?: synchronized(this) {
            instance ?: ReportStore(context.applicationContext).also { instance = it }
        }
    }
}
