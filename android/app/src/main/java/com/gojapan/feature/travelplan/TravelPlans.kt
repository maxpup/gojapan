package com.gojapan.feature.travelplan

import android.content.Context
import androidx.room.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

@Entity(primaryKeys = ["owner", "id"], indices = [Index(value = ["owner", "request"], unique = true)])
data class TravelPlan(val owner: String, val id: Long, val request: String, val created: Long,
    val name: String, val destination: String, val zone: String, val language: String,
    val start: String?, val end: String?, @ColumnInfo(defaultValue = "''") val wish: String = "")

@Entity(foreignKeys = [ForeignKey(entity = TravelPlan::class, parentColumns = ["owner","id"], childColumns = ["owner","id"])])
data class CurrentPlan(@PrimaryKey val owner: String, val id: Long)

data class PlanRow(@Embedded val plan: TravelPlan, val selected: Boolean)

@Dao
interface PlanDao {
    @Query("SELECT p.*, EXISTS(SELECT 1 FROM CurrentPlan c WHERE c.owner=p.owner AND c.id=p.id) AS selected FROM TravelPlan p WHERE owner=:owner ORDER BY created DESC,id DESC")
    fun observe(owner: String): Flow<List<PlanRow>>
    @Query("SELECT COALESCE(MAX(id),-1)+1 FROM TravelPlan WHERE owner=:owner")
    suspend fun next(owner: String): Long
    @Query("SELECT id FROM TravelPlan WHERE owner=:owner AND request=:request")
    suspend fun existing(owner: String, request: String): Long?
    @Query("SELECT * FROM TravelPlan WHERE owner=:owner AND id=:id")
    suspend fun get(owner: String, id: Long): TravelPlan?
    @Insert suspend fun insert(plan: TravelPlan)
    @Update suspend fun update(plan: TravelPlan)
    @Upsert suspend fun select(current: CurrentPlan)
}

@Database(entities = [TravelPlan::class, CurrentPlan::class, com.gojapan.feature.expense.Expense::class], version = 3, autoMigrations = [AutoMigration(from = 1, to = 2), AutoMigration(from = 2, to = 3)])
abstract class TravelDatabase : RoomDatabase() { abstract fun plans(): PlanDao
    abstract fun expenses(): com.gojapan.feature.expense.ExpenseDao
}

@Module @InstallIn(SingletonComponent::class)
object TravelModule {
    @Provides @Singleton fun database(@ApplicationContext context: Context): TravelDatabase =
        Room.databaseBuilder(context, TravelDatabase::class.java, "travel-plans.db").build()
}

val destinations = mapOf("日本" to "Asia/Tokyo", "巴黎" to "Europe/Paris", "中国" to "Asia/Shanghai",
    "美国（纽约）" to "America/New_York", "美国（洛杉矶）" to "America/Los_Angeles")
val languages = mapOf("ja" to "日语", "en" to "英语", "fr" to "法语", "zh" to "中文")

fun validate(plan: TravelPlan) {
    require(plan.name.isNotBlank()) { "请填写计划名称" }
    require(plan.destination.isNotBlank()) { "请选择目的地" }
    require(plan.zone in ZoneId.getAvailableZoneIds() && (destinations[plan.destination] ?: plan.zone) == plan.zone) { "请选择目的地时区" }
    require(plan.language in languages) { "请选择目标语言" }
    val dates = listOf(plan.start, plan.end).map { value -> value?.let {
        runCatching { LocalDate.parse(it) }.getOrNull() ?: throw IllegalArgumentException("日期格式应为 YYYY-MM-DD")
    } }
    require(dates[0] == null || dates[1] == null || !dates[1]!!.isBefore(dates[0])) { "结束日不能早于出发日" }
}

fun TravelPlan.summary(clock: Clock): String {
    val today = LocalDate.now(clock.withZone(ZoneId.of(zone))).toString()
    val status = when {
        start == null || end == null || today < start -> "期待中"
        today > end -> "已结束"
        else -> "旅行中"
    }
    val dates = if (start == null && end == null) "日期待定" else "${start ?: "出发日待补"} ~ ${end ?: "结束日待补"}"
    return "$name · $dates · $status"
}

@Singleton class TravelRepository @Inject constructor(private val db: TravelDatabase) {
    private val owner = "local"
    private val dao = db.plans()
    fun observe() = dao.observe(owner)
    suspend fun save(draft: TravelPlan, editId: Long?) = db.withTransaction {
        validate(draft)
        val value = draft.copy(owner = owner, name = draft.name.trim())
        if (editId != null) {
            val old = requireNotNull(dao.get(owner, editId)) { "计划已失效，请重新选择" }
            dao.update(value.copy(id = old.id, created = old.created, request = old.request))
        } else if (dao.existing(owner, value.request) == null) {
            val id = dao.next(owner)
            dao.insert(value.copy(id = id, created = System.currentTimeMillis()))
            dao.select(CurrentPlan(owner, id))
        }
    }
    suspend fun select(id: Long) = db.withTransaction {
        requireNotNull(dao.get(owner, id)) { "计划已失效，请重新选择" }
        dao.select(CurrentPlan(owner,id))
    }
}
