package com.example.affiliatedeals

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

data class Deal(
    val id: Long = System.currentTimeMillis(),
    val name: String,
    val price: String,
    val oldPrice: String,
    val url: String
) {
    private fun number(value: String): Double? =
        value.trim().replace(",", ".").toDoubleOrNull()

    fun discount(): Int? {
        val p = number(price)
        val old = number(oldPrice)
        return if (p != null && old != null && old > 0 && p < old)
            (((old - p) / old) * 100).toInt() else null
    }

    fun post(): String = buildString {
        append("🔥 عرض مميز على Amazon.eg\\n\\n")
        append(name).append("\\n")
        append("السعر الحالي: ").append(price).append(" جنيه\\n")
        if (oldPrice.isNotBlank()) append("السعر السابق: ").append(oldPrice).append(" جنيه\\n")
        discount()?.let { append("خصم ").append(it).append("% 🎉\\n") }
        append("\\nرابط المنتج:\\n").append(url)
        append("\\n\\nبصفتي مشاركًا في أمازون، أكسب من عمليات الشراء المؤهلة.")
    }
}

private object DealStorage {
    private const val PREFS = "monier_deals"
    private const val KEY = "saved_deals"

    fun load(context: Context): List<Deal> {
        return try {
            val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY, "[]") ?: "[]"
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { i ->
                val obj = array.optJSONObject(i) ?: return@mapNotNull null
                Deal(
                    id = obj.optLong("id", System.currentTimeMillis() + i),
                    name = obj.optString("name"),
                    price = obj.optString("price"),
                    oldPrice = obj.optString("oldPrice"),
                    url = obj.optString("url")
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun save(context: Context, deals: List<Deal>) {
        val array = JSONArray()
        deals.forEach { deal ->
            array.put(JSONObject().apply {
                put("id", deal.id)
                put("name", deal.name)
                put("price", deal.price)
                put("oldPrice", deal.oldPrice)
                put("url", deal.url)
            })
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY, array.toString()).apply()
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { DealsApp() }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DealsApp() {
    val context = LocalContext.current
    var name by remember { mutableStateOf("") }
    var price by remember { mutableStateOf("") }
    var oldPrice by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var deals by remember { mutableStateOf(DealStorage.load(context)) }
    var preview by remember { mutableStateOf<Deal?>(null) }
    var error by remember { mutableStateOf("") }

    fun updateDeals(updated: List<Deal>) {
        deals = updated
        DealStorage.save(context, updated)
    }

    MaterialTheme {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Monier Deals") },
                    actions = {
                        Text("${deals.size} عرض", modifier = Modifier.padding(end = 16.dp))
                    }
                )
            }
        ) { padding ->
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Text("إضافة عرض جديد", style = MaterialTheme.typography.titleLarge)
                    OutlinedTextField(
                        value = name, onValueChange = { name = it; error = "" },
                        label = { Text("اسم المنتج") }, modifier = Modifier.fillMaxWidth(), singleLine = true
                    )
                    OutlinedTextField(
                        value = price, onValueChange = { price = it; error = "" },
                        label = { Text("السعر الحالي بالجنيه") }, modifier = Modifier.fillMaxWidth(), singleLine = true
                    )
                    OutlinedTextField(
                        value = oldPrice, onValueChange = { oldPrice = it; error = "" },
                        label = { Text("السعر قبل الخصم (اختياري)") }, modifier = Modifier.fillMaxWidth(), singleLine = true
                    )
                    OutlinedTextField(
                        value = url, onValueChange = { url = it; error = "" },
                        label = { Text("رابط المنتج") }, modifier = Modifier.fillMaxWidth(), singleLine = true
                    )
                    if (error.isNotBlank()) {
                        Text(error, color = MaterialTheme.colorScheme.error)
                    }
                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            val currentPrice = price.trim().replace(",", ".").toDoubleOrNull()
                            val previousPrice = oldPrice.trim().replace(",", ".").toDoubleOrNull()
                            val normalizedUrl = url.trim()
                            error = when {
                                name.isBlank() -> "اكتب اسم المنتج أولاً"
                                currentPrice == null || currentPrice <= 0 -> "اكتب سعرًا حاليًا صحيحًا أكبر من صفر"
                                oldPrice.isNotBlank() && (previousPrice == null || previousPrice <= 0) -> "اكتب السعر السابق بشكل صحيح"
                                oldPrice.isNotBlank() && previousPrice!! <= currentPrice -> "السعر السابق يجب أن يكون أكبر من السعر الحالي"
                                !normalizedUrl.startsWith("https://") && !normalizedUrl.startsWith("http://") -> "الرابط يجب أن يبدأ بـ https:// أو http://"
                                deals.any { it.url.equals(normalizedUrl, ignoreCase = true) } -> "هذا الرابط مضاف بالفعل"
                                else -> ""
                            }
                            if (error.isBlank()) {
                                val deal = Deal(
                                    name = name.trim(),
                                    price = currentPrice!!.toString(),
                                    oldPrice = previousPrice?.toString() ?: "",
                                    url = normalizedUrl
                                )
                                updateDeals(listOf(deal) + deals)
                                preview = deal
                                name = ""; price = ""; oldPrice = ""; url = ""
                            }
                        }
                    ) { Text("حفظ العرض ومعاينة المنشور") }
                }

                preview?.let { deal ->
                    item {
                        HorizontalDivider()
                        Text("معاينة المنشور", style = MaterialTheme.typography.titleLarge)
                        Card {
                            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(deal.post())
                                Button(
                                    modifier = Modifier.fillMaxWidth(),
                                    onClick = {
                                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        cm.setPrimaryClip(ClipData.newPlainText("Amazon deal", deal.post()))
                                        Toast.makeText(context, "تم نسخ المنشور", Toast.LENGTH_SHORT).show()
                                    }
                                ) { Text("نسخ المنشور") }
                                OutlinedButton(
                                    modifier = Modifier.fillMaxWidth(),
                                    onClick = {
                                        val send = Intent(Intent.ACTION_SEND).apply {
                                            type = "text/plain"
                                            putExtra(Intent.EXTRA_TEXT, deal.post())
                                        }
                                        context.startActivity(Intent.createChooser(send, "مشاركة العرض"))
                                    }
                                ) { Text("مشاركة العرض") }
                            }
                        }
                    }
                }

                item {
                    HorizontalDivider()
                    Text("العروض المحفوظة (${deals.size})", style = MaterialTheme.typography.titleLarge)
                    Text("العروض محفوظة على الهاتف وتظل موجودة بعد إغلاق التطبيق.")
                }

                items(deals, key = { it.id }) { deal ->
                    Card {
                        Column(
                            Modifier.fillMaxWidth().padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(deal.name, style = MaterialTheme.typography.titleMedium)
                            Text("${deal.price} جنيه" + (deal.discount()?.let { " • خصم $it%" } ?: ""))
                            Text(deal.url, style = MaterialTheme.typography.bodySmall)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextButton(onClick = { preview = deal }) { Text("معاينة") }
                                TextButton(onClick = {
                                    updateDeals(deals.filterNot { it.id == deal.id })
                                    if (preview?.id == deal.id) preview = null
                                }) { Text("حذف", color = MaterialTheme.colorScheme.error) }
                            }
                        }
                    }
                }
            }
        }
    }
}
