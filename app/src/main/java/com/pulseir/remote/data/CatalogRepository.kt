package com.pulseir.remote.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import com.pulseir.remote.ir.ButtonRole
import com.pulseir.remote.ir.IrButton
import com.pulseir.remote.ir.IrProtocol
import com.pulseir.remote.ir.IrProtocolEncoder
import com.pulseir.remote.ir.IrSignal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * 100% Offline IR Catalog backed by precompiled SQLite database (Flipper-IRDB CC0-1.0).
 * Extracts assets/pulseir_catalog.db once to internal storage on first use.
 */
class CatalogRepository(context: Context) {
    private val appContext = context.applicationContext
    private val dbFile: File = appContext.getDatabasePath(DB_FILENAME)

    @Volatile
    private var dbInstance: SQLiteDatabase? = null
    private val lock = Any()

    private fun ensureDb(): SQLiteDatabase {
        dbInstance?.let { if (it.isOpen) return it }
        synchronized(lock) {
            dbInstance?.let { if (it.isOpen) return it }
            if (!dbFile.exists() || dbFile.length() < 100_000L) {
                // Clean up older catalog database versions if present
                appContext.getDatabasePath("pulseir_catalog_v1.db").delete()
                dbFile.parentFile?.mkdirs()
                val tmp = File(dbFile.parentFile, "$DB_FILENAME.tmp")
                appContext.assets.open(ASSET_DB_NAME).use { assetIn ->
                    FileOutputStream(tmp).use { out ->
                        assetIn.copyTo(out, bufferSize = 64 * 1024)
                    }
                }
                if (dbFile.exists()) dbFile.delete()
                tmp.renameTo(dbFile)
            }
            val opened = SQLiteDatabase.openDatabase(
                dbFile.absolutePath,
                null,
                SQLiteDatabase.OPEN_READONLY,
            )
            dbInstance = opened
            return opened
        }
    }

    suspend fun warmup() = withContext(Dispatchers.IO) { ensureDb() }

    suspend fun getCatalogStats(): Pair<Int, Int> = withContext(Dispatchers.IO) {
        val db = ensureDb()
        var remotes = 0
        var buttons = 0
        db.rawQuery("SELECT key, value FROM meta WHERE key IN ('total_remotes','total_buttons')", null).use { c ->
            while (c.moveToNext()) {
                when (c.getString(0)) {
                    "total_remotes" -> remotes = c.getString(1).toIntOrNull() ?: 0
                    "total_buttons" -> buttons = c.getString(1).toIntOrNull() ?: 0
                }
            }
        }
        remotes to buttons
    }

    suspend fun getCategories(): List<CatalogCategory> = withContext(Dispatchers.IO) {
        val db = ensureDb()
        val list = ArrayList<CatalogCategory>()
        db.rawQuery(
            "SELECT id, display_en, display_ar, sort_order, remote_count, brand_count FROM categories ORDER BY sort_order ASC",
            null,
        ).use { c ->
            while (c.moveToNext()) {
                list.add(
                    CatalogCategory(
                        id = c.getString(0),
                        displayEn = c.getString(1),
                        displayAr = c.getString(2),
                        sortOrder = c.getInt(3),
                        remoteCount = c.getInt(4),
                        brandCount = c.getInt(5),
                    ),
                )
            }
        }
        list
    }

    suspend fun getBrands(categoryId: String, query: String = ""): List<CatalogBrand> = withContext(Dispatchers.IO) {
        val db = ensureDb()
        val q = query.trim().lowercase()
        val sql: String
        val args: Array<String>
        if (q.isEmpty()) {
            sql = """
                SELECT brand, COUNT(*) AS cnt
                FROM remotes
                WHERE category_id = ?
                GROUP BY brand_norm
                ORDER BY cnt DESC, brand ASC
            """.trimIndent()
            args = arrayOf(categoryId)
        } else {
            sql = """
                SELECT brand, COUNT(*) AS cnt
                FROM remotes
                WHERE category_id = ? AND brand_norm LIKE ?
                GROUP BY brand_norm
                ORDER BY cnt DESC, brand ASC
            """.trimIndent()
            args = arrayOf(categoryId, "%$q%")
        }
        val list = ArrayList<CatalogBrand>()
        db.rawQuery(sql, args).use { c ->
            while (c.moveToNext()) {
                list.add(CatalogBrand(c.getString(0), c.getInt(1)))
            }
        }
        list
    }

    suspend fun getRemotes(categoryId: String, brand: String): List<CatalogRemote> = withContext(Dispatchers.IO) {
        val db = ensureDb()
        val list = ArrayList<CatalogRemote>()
        db.rawQuery(
            """
            SELECT id, category_id, brand, model, button_count, has_power, power_fingerprint
            FROM remotes
            WHERE category_id = ? AND brand_norm = ?
            ORDER BY has_power DESC, button_count DESC, model ASC
            """.trimIndent(),
            arrayOf(categoryId, brand.lowercase()),
        ).use { c ->
            while (c.moveToNext()) {
                list.add(
                    CatalogRemote(
                        id = c.getLong(0),
                        categoryId = c.getString(1),
                        brand = c.getString(2),
                        model = c.getString(3),
                        buttonCount = c.getInt(4),
                        hasPower = c.getInt(5) == 1,
                        powerFingerprint = c.getString(6),
                    ),
                )
            }
        }
        list
    }

    suspend fun searchRemotes(query: String, limit: Int = 50): List<CatalogRemote> = withContext(Dispatchers.IO) {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return@withContext emptyList()
        val db = ensureDb()
        val like = "%$q%"
        val list = ArrayList<CatalogRemote>()
        db.rawQuery(
            """
            SELECT id, category_id, brand, model, button_count, has_power, power_fingerprint
            FROM remotes
            WHERE brand_norm LIKE ? OR model_norm LIKE ?
            ORDER BY has_power DESC, button_count DESC
            LIMIT ?
            """.trimIndent(),
            arrayOf(like, like, limit.toString()),
        ).use { c ->
            while (c.moveToNext()) {
                list.add(
                    CatalogRemote(
                        id = c.getLong(0),
                        categoryId = c.getString(1),
                        brand = c.getString(2),
                        model = c.getString(3),
                        buttonCount = c.getInt(4),
                        hasPower = c.getInt(5) == 1,
                        powerFingerprint = c.getString(6),
                    ),
                )
            }
        }
        list
    }

    fun getButtonsBlocking(remoteId: Long): List<IrButton> {
        val db = ensureDb()
        val list = ArrayList<IrButton>()
        db.rawQuery(
            """
            SELECT b.id, b.name, b.role, b.signal_type, b.protocol, b.address, b.command,
                   r.frequency, r.pattern_blob
            FROM buttons b
            LEFT JOIN raw_blobs r ON b.raw_blob_id = r.id
            WHERE b.remote_id = ?
            ORDER BY b.sort_order ASC
            """.trimIndent(),
            arrayOf(remoteId.toString()),
        ).use { c ->
            while (c.moveToNext()) {
                val id = c.getLong(0)
                val name = c.getString(1)
                val role = ButtonRole.fromString(c.getString(2))
                val type = c.getInt(3)
                val signal: IrSignal? = if (type == 0) {
                    val proto = IrProtocol.fromWireName(c.getString(4))
                    if (proto != null) {
                        IrSignal.Parsed(proto, c.getInt(5), c.getInt(6))
                    } else null
                } else {
                    val freq = c.getInt(7)
                    val blob = c.getBlob(8)
                    if (blob != null && blob.isNotEmpty()) {
                        IrSignal.Raw(freq, IrProtocolEncoder.unpackRawBlob(blob))
                    } else null
                }
                if (signal != null) {
                    list.add(IrButton(id = id, name = name, role = role, signal = signal))
                }
            }
        }
        return list
    }

    suspend fun getButtons(remoteId: Long): List<IrButton> = withContext(Dispatchers.IO) {
        getButtonsBlocking(remoteId)
    }

    /**
     * Smart Scan Mode query: returns deduplicated unique Power signals ranked by how many
     * real remotes share each signal (most popular brands/models tested FIRST!).
     */
    suspend fun getScanCandidates(
        categoryId: String,
        brandFilter: String? = null,
    ): List<ScanCandidate> = withContext(Dispatchers.IO) {
        val db = ensureDb()
        val args = ArrayList<String>()
        args.add(categoryId)
        val brandClause = if (!brandFilter.isNullOrBlank()) {
            args.add(brandFilter.lowercase())
            "AND brand_norm = ?"
        } else {
            ""
        }

        data class RawGroup(val fp: String, val sampleId: Long, val brand: String, val model: String, val cnt: Int)
        val groups = ArrayList<RawGroup>()
        db.rawQuery(
            """
            SELECT power_fingerprint, MIN(id) AS sample_id, brand, model, COUNT(*) AS cnt
            FROM remotes
            WHERE category_id = ? AND has_power = 1 AND power_fingerprint IS NOT NULL $brandClause
            GROUP BY power_fingerprint
            ORDER BY cnt DESC, brand ASC
            """.trimIndent(),
            args.toTypedArray(),
        ).use { c ->
            while (c.moveToNext()) {
                groups.add(
                    RawGroup(
                        fp = c.getString(0),
                        sampleId = c.getLong(1),
                        brand = c.getString(2),
                        model = c.getString(3),
                        cnt = c.getInt(4),
                    ),
                )
            }
        }

        val candidates = ArrayList<ScanCandidate>(groups.size)
        for (g in groups) {
            val powerBtn = findPowerButtonBlocking(db, g.sampleId) ?: continue
            candidates.add(
                ScanCandidate(
                    fingerprint = g.fp,
                    sampleRemoteId = g.sampleId,
                    sampleBrand = g.brand,
                    sampleModel = g.model,
                    matchCount = g.cnt,
                    powerButton = powerBtn,
                ),
            )
        }
        candidates
    }

    suspend fun getRemotesByPowerFingerprint(
        categoryId: String,
        fingerprint: String,
        brandFilter: String? = null,
    ): List<CatalogRemote> = withContext(Dispatchers.IO) {
        val db = ensureDb()
        val args = ArrayList<String>()
        args.add(categoryId)
        args.add(fingerprint)
        val brandClause = if (!brandFilter.isNullOrBlank()) {
            args.add(brandFilter.lowercase())
            "AND brand_norm = ?"
        } else ""

        val list = ArrayList<CatalogRemote>()
        db.rawQuery(
            """
            SELECT id, category_id, brand, model, button_count, has_power, power_fingerprint
            FROM remotes
            WHERE category_id = ? AND power_fingerprint = ? $brandClause
            ORDER BY button_count DESC, brand ASC
            """.trimIndent(),
            args.toTypedArray(),
        ).use { c ->
            while (c.moveToNext()) {
                list.add(
                    CatalogRemote(
                        id = c.getLong(0),
                        categoryId = c.getString(1),
                        brand = c.getString(2),
                        model = c.getString(3),
                        buttonCount = c.getInt(4),
                        hasPower = c.getInt(5) == 1,
                        powerFingerprint = c.getString(6),
                    ),
                )
            }
        }
        list
    }

    private fun findPowerButtonBlocking(db: SQLiteDatabase, remoteId: Long): IrButton? {
        db.rawQuery(
            """
            SELECT b.id, b.name, b.role, b.signal_type, b.protocol, b.address, b.command,
                   r.frequency, r.pattern_blob
            FROM buttons b
            LEFT JOIN raw_blobs r ON b.raw_blob_id = r.id
            WHERE b.remote_id = ? AND b.role IN ('POWER', 'POWER_ON', 'POWER_OFF')
            ORDER BY CASE b.role WHEN 'POWER' THEN 0 WHEN 'POWER_ON' THEN 1 ELSE 2 END, b.sort_order ASC
            LIMIT 1
            """.trimIndent(),
            arrayOf(remoteId.toString()),
        ).use { c ->
            if (c.moveToFirst()) {
                val id = c.getLong(0)
                val name = c.getString(1)
                val role = ButtonRole.fromString(c.getString(2))
                val type = c.getInt(3)
                val signal: IrSignal? = if (type == 0) {
                    IrProtocol.fromWireName(c.getString(4))?.let {
                        IrSignal.Parsed(it, c.getInt(5), c.getInt(6))
                    }
                } else {
                    val freq = c.getInt(7)
                    val blob = c.getBlob(8)
                    if (blob != null && blob.isNotEmpty()) {
                        IrSignal.Raw(freq, IrProtocolEncoder.unpackRawBlob(blob))
                    } else null
                }
                if (signal != null) return IrButton(id, name, role, signal)
            }
        }
        return null
    }

    companion object {
        private const val ASSET_DB_NAME = "pulseir_catalog.db"
        private const val DB_FILENAME = "pulseir_catalog_v2.db"
    }
}
