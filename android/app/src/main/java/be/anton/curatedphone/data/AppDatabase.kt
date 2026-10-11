package be.anton.curatedphone.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "active_policy") data class PolicyEntity(@PrimaryKey val slot: Int = 1, val version: Long, val guardianVersion: Long, val envelope: String, val snapshot: String)
@Entity(tableName = "draft") data class DraftEntity(@PrimaryKey val slot: Int = 1, val snapshot: String)
@Entity(tableName = "requests") data class RequestEntity(@PrimaryKey val requestId: String, val body: String)
@Entity(tableName = "apply_journal") data class ApplyJournalEntity(@PrimaryKey val approvalId: String, val envelope: String, val payload: String, val status: String, val stage: String, val failure: String? = null, val acknowledged: Boolean = false)
@Entity(tableName = "history") data class HistoryEntity(@PrimaryKey val id: String, val at: Long, val event: String, val detail: String)
@Entity(tableName = "metadata") data class MetadataEntity(@PrimaryKey val key: String, val value: String)
@Dao interface PolicyDao {
    @Query("SELECT * FROM active_policy WHERE slot=1") suspend fun active(): PolicyEntity?
    @Query("SELECT * FROM active_policy WHERE slot=1") fun observeActive(): Flow<PolicyEntity?>
    @Upsert suspend fun saveActive(value: PolicyEntity)
    @Query("SELECT * FROM draft WHERE slot=1") suspend fun draft(): DraftEntity?
    @Upsert suspend fun saveDraft(value: DraftEntity)
    @Upsert suspend fun saveRequest(value: RequestEntity)
    @Query("SELECT * FROM requests WHERE requestId=:id") suspend fun request(id: String): RequestEntity?
    @Query("SELECT * FROM apply_journal WHERE status!='applied' OR acknowledged=0 ORDER BY rowid DESC") suspend fun unfinished(): List<ApplyJournalEntity>
    @Query("SELECT * FROM apply_journal WHERE approvalId=:id") suspend fun journal(id: String): ApplyJournalEntity?
    @Upsert suspend fun saveJournal(value: ApplyJournalEntity)
    @Upsert suspend fun saveHistory(value: HistoryEntity)
    @Query("SELECT * FROM history ORDER BY at DESC LIMIT 100") suspend fun history(): List<HistoryEntity>
    @Query("SELECT value FROM metadata WHERE `key`=:key") suspend fun metadata(key: String): String?
    @Upsert suspend fun saveMetadata(value: MetadataEntity)
}
@Database(entities = [PolicyEntity::class, DraftEntity::class, RequestEntity::class, ApplyJournalEntity::class, HistoryEntity::class, MetadataEntity::class], version = 1, exportSchema = true)
abstract class AppDatabase : RoomDatabase() { abstract fun policies(): PolicyDao }
