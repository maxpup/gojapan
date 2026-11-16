package com.gojapan.feature.expense

import androidx.lifecycle.ViewModel
import androidx.room.*
import com.gojapan.core.ai.*
import com.gojapan.feature.travelplan.TravelDatabase
import com.gojapan.feature.travelplan.TravelPlan
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.*
import java.math.BigDecimal
import java.time.LocalDate
import java.util.Currency
import java.util.Locale
import java.util.UUID
import javax.inject.Inject

val categories = listOf("餐饮", "交通", "住宿", "购物", "门票娱乐", "其他")

@Entity(primaryKeys = ["owner", "planId", "id"], foreignKeys = [ForeignKey(
    entity = TravelPlan::class, parentColumns = ["owner","id"], childColumns = ["owner","planId"],
    onDelete = ForeignKey.CASCADE)])
data class Expense(val owner: String, val planId: Long, val id: String, val day: String,
    val purpose: String, val category: String, val currency: String, val minor: Long) {
    fun draft() = Draft(id, day, purpose, category, currency, major(minor, currency).toPlainString())
}

fun major(minor: Long, currency: String): BigDecimal =
    BigDecimal.valueOf(minor, Currency.getInstance(currency).defaultFractionDigits)

data class Draft(val id: String = UUID.randomUUID().toString(), val day: String = LocalDate.now().toString(),
    val purpose: String = "", val category: String = "其他", val currency: String = "", val amount: String = "") {
    fun record(plan: TravelPlan): Expense {
        require(runCatching { LocalDate.parse(day) }.isSuccess) { "请输入有效日期 YYYY-MM-DD" }
        require(purpose.isNotBlank() && category in categories) { "请填写用途和类别" }
        val code = currency.trim().uppercase(Locale.ROOT)
        val digits = runCatching { Currency.getInstance(code).defaultFractionDigits }.getOrDefault(-1)
        require(digits >= 0) { "请填写有效币种代码，如 JPY 或 CNY" }
        val minor = runCatching { amount.trim().toBigDecimal().movePointRight(digits).longValueExact() }.getOrNull()
        require(minor != null && minor > 0) { "请输入有效正金额，${code}最多支持 ${digits} 位小数" }
        return Expense(plan.owner, plan.id, id, day, purpose.trim(), category, code, minor)
    }
}

@Dao interface ExpenseDao {
    @Query("SELECT * FROM Expense WHERE owner=:owner AND planId=:plan ORDER BY day DESC, rowid DESC")
    fun observe(owner: String, plan: Long): Flow<List<Expense>>
    @Upsert suspend fun save(rows: List<Expense>)
    @Delete suspend fun delete(row: Expense)
}

@HiltViewModel class ExpenseModel @Inject constructor(db: TravelDatabase, private val client: OpenAiCompatibleChatClient): ViewModel() {
    val dao = db.expenses()
    suspend fun request(turns: List<AiChatTurn>): String {
        val today = LocalDate.now().toString()
        return client.complete(turns,
            "从用户文字提取消费。今天是 $today。只返回 JSON 数组，每笔字段 day(YYYY-MM-DD，未提及用今天)、purpose、category（从 $categories 选）、currency（明确的 ISO 4217 代码）、amount（十进制金额）。金额或币种不明确时填空字符串，不猜测。无消费返回 []。", 2048)
    }
    fun parseAnswer(answer: String): List<Draft> {
        val today = LocalDate.now().toString()
        val body = answer.removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        return Json.parseToJsonElement(body).jsonArray.map { item ->
            fun field(name: String) = item.jsonObject[name]?.jsonPrimitive?.contentOrNull.orEmpty()
            Draft(day = field("day").ifBlank { today }, purpose = field("purpose"), category = field("category").takeIf { it in categories } ?: "其他",
                currency = field("currency"), amount = field("amount"))
        }.also { require(it.isNotEmpty()) { "未识别到支出，请补充消费内容" } }
    }
}
