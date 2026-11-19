package com.example.affiliatedeals

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalLayoutDirection
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

data class Deal(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val price: String,
    val oldPrice: String,
    val url: String,
    val createdAt: Long = System.currentTimeMillis(),
    val published: Boolean = false
) {
    private fun number(value: String): Double? = value.trim()
        .replace(',', '.').replace('٫', '.').replace("٠", "0").replace("١", "1")
        .replace("٢", "2").replace("٣", "3").replace("٤", "4").replace("٥", "5")
        .replace("٦", "6").replace("٧", "7").replace("٨", "8").replace("٩", "9")
        .toDoubleOrNull()

    fun currentValue() = number(price)
    fun discount(): Int? {
        val p = number(price); val old = number(oldPrice)
        return if (p != null && old != null && old > 0 && p > 0 && p < old)
            (((old - p) / old) * 100).toInt() else null
    }

    fun post(): String = buildString {
        append("🔥 عرض مميز على Amazon.eg\n\n")
        append(name).append("\n")
        append("السعر الحالي: ").append(price).append(" جنيه\n")
        if (oldPrice.isNotBlank()) append("السعر السابق: ").append(oldPrice).append(" جنيه\n")
        discount()?.let { append("خصم ").append(it).append("% 🎉\n") }
        append("\nاطلب المنتج من هنا:\n").append(url)
        append("\n\nبصفتي مشاركًا في أمازون، أكسب من عمليات الشراء المؤهلة.")
    }
}

private object DealStore {
    private const val PREFS = "monier_deals_store"
    private const val KEY = "deals_v1"

    fun load(context: Context): List<Deal> {
        return try {
            val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "[]") ?: "[]"
            val array = JSONArray(raw)
            (0 until array.length()).map { i ->
                val o = array.getJSONObject(i)
                Deal(
                    id = o.optString("id", UUID.randomUUID().toString()),
                    name = o.optString("name"), price = o.optString("price"),
                    oldPrice = o.optString("oldPrice"), url = o.optString("url"),
                    createdAt = o.optLong("createdAt", System.currentTimeMillis()),
                    published = o.optBoolean("published", false)
                )
            }
        } catch (_: Exception) { emptyList() }
    }

    fun save(context: Context, deals: List<Deal>) {
        val array = JSONArray()
        deals.forEach { d ->
            array.put(JSONObject().apply {
                put("id", d.id); put("name", d.name); put("price", d.price)
                put("oldPrice", d.oldPrice); put("url", d.url)
                put("createdAt", d.createdAt); put("published", d.published)
            })
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, array.toString()).apply()
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) { DealsApp() } }
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
    var deals by remember { mutableStateOf(DealStore.load(context)) }
    var preview by remember { mutableStateOf<Deal?>(null) }
    var error by remember { mutableStateOf("") }
    var search by remember { mutableStateOf("") }
    var minDiscount by remember { mutableStateOf("0") }
    var onlyUnpublished by remember { mutableStateOf(false) }

    fun updateDeals(next: List<Deal>) { deals = next; DealStore.save(context, next) }
    val threshold = minDiscount.toIntOrNull()?.coerceIn(0, 100) ?: 0
    val filtered = deals.filter { d ->
        (search.isBlank() || d.name.contains(search, true)) &&
            (!onlyUnpublished || !d.published) && (d.discount() ?: 0) >= threshold
    }.sortedByDescending { it.discount() ?: 0 }

    MaterialTheme {
        Scaffold(topBar = { TopAppBar(title = { Text("Monier Deals | عروض مصر") }) }) { padding ->
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    Card {
                        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("إضافة عرض جديد", style = MaterialTheme.typography.titleLarge)
                            Text("أضف رابط المنتج والسعرين المتاحين لديك. التطبيق يحسب الخصم ويجهز المنشور ويحفظ العرض على هاتفك.", style = MaterialTheme.typography.bodySmall)
                            OutlinedTextField(name, { name = it }, label = { Text("اسم المنتج") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                            OutlinedTextField(price, { price = it }, label = { Text("السعر الحالي بالجنيه") }, modifier = Modifier.fillMaxWidth(), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                            OutlinedTextField(oldPrice, { oldPrice = it }, label = { Text("السعر قبل الخصم (اختياري)") }, modifier = Modifier.fillMaxWidth(), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                            OutlinedTextField(url, { url = it }, label = { Text("رابط المنتج أو رابط العمولة") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                            if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
                            Button(modifier = Modifier.fillMaxWidth(), onClick = {
                                val normalizedUrl = url.trim()
                                val validUrl = normalizedUrl.startsWith("https://", true) &&
                                    (normalizedUrl.contains("amazon.", true) || normalizedUrl.contains("amzn.to", true))
                                val current = price.trim().replace(',', '.')
                                val old = oldPrice.trim().replace(',', '.')
                                error = when {
                                    name.isBlank() -> "اكتب اسم المنتج أولاً"
                                    current.toDoubleOrNull() == null || current.toDoubleOrNull()!! <= 0 -> "اكتب سعرًا حاليًا صحيحًا"
                                    old.isNotBlank() && (old.toDoubleOrNull() == null || old.toDoubleOrNull()!! <= 0) -> "راجع السعر السابق"
                                    normalizedUrl.isBlank() || !validUrl -> "أدخل رابط Amazon أو amzn.to يبدأ بـ https"
                                    deals.any { it.url.trim().trimEnd('/') == normalizedUrl.trimEnd('/') } -> "هذا الرابط موجود بالفعل؛ لن نضيفه مرتين"
                                    else -> ""
                                }
                                if (error.isBlank()) {
                                    val deal = Deal(name = name.trim(), price = current, oldPrice = old, url = normalizedUrl)
                                    updateDeals(listOf(deal) + deals); preview = deal
                                    name = ""; price = ""; oldPrice = ""; url = ""
                                    Toast.makeText(context, "تم حفظ العرض على الجهاز", Toast.LENGTH_SHORT).show()
                                }
                            }) { Text("حفظ العرض وتجهيز المنشور") }
                        }
                    }
                }

                preview?.let { deal ->
                    item {
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                            Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("معاينة المنشور", style = MaterialTheme.typography.titleLarge)
                                Text(deal.post())
                                Button(modifier = Modifier.fillMaxWidth(), onClick = {
                                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    cm.setPrimaryClip(ClipData.newPlainText("Monier Deals post", deal.post()))
                                    Toast.makeText(context, "تم نسخ المنشور", Toast.LENGTH_SHORT).show()
                                }) { Text("نسخ المنشور") }
                                OutlinedButton(modifier = Modifier.fillMaxWidth(), onClick = {
                                    val send = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, deal.post()) }
                                    context.startActivity(Intent.createChooser(send, "مشاركة العرض"))
                                }) { Text("مشاركة المنشور") }
                                TextButton(onClick = { preview = null }) { Text("إغلاق المعاينة") }
                            }
                        }
                    }
                }

                item {
                    HorizontalDivider()
                    Text("إدارة العروض (${deals.size})", style = MaterialTheme.typography.titleLarge)
                    OutlinedTextField(search, { search = it }, label = { Text("بحث باسم المنتج") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    OutlinedTextField(minDiscount, { minDiscount = it.filter(Char::isDigit).take(3) }, label = { Text("أقل خصم لإظهاره (%) — صفر لعرض الكل") }, modifier = Modifier.fillMaxWidth(), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Checkbox(checked = onlyUnpublished, onCheckedChange = { onlyUnpublished = it })
                        Text("إظهار العروض غير المنشورة فقط")
                    }
                    Text("المعروض: ${filtered.size} • إجمالي المحفوظ: ${deals.size}", style = MaterialTheme.typography.bodySmall)
                    Text("تنبيه: السعر والخصم يعتمدان على البيانات التي أدخلتها؛ لا يتم تحديثهما تلقائيًا من أمازون في هذه النسخة.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                }

                items(filtered, key = { it.id }) { deal ->
                    Card {
                        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Text(deal.name, style = MaterialTheme.typography.titleMedium)
                            Text("السعر: ${deal.price} جنيه" + (deal.discount()?.let { " • خصم $it%" } ?: " • لا يوجد خصم محسوب"))
                            Text(if (deal.published) "الحالة: تم وضع علامة منشور" else "الحالة: لم يُنشر بعد", style = MaterialTheme.typography.bodySmall)
                            Text("أضيف: ${SimpleDateFormat("yyyy/MM/dd HH:mm", Locale("ar", "EG")).format(Date(deal.createdAt))}", style = MaterialTheme.typography.bodySmall)
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                TextButton(onClick = { preview = deal }) { Text("معاينة") }
                                TextButton(onClick = {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(deal.url))
                                    context.startActivity(intent)
                                }) { Text("فتح الرابط") }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                TextButton(onClick = { updateDeals(deals.map { if (it.id == deal.id) it.copy(published = !it.published) else it }) }) {
                                    Text(if (deal.published) "إلغاء علامة النشر" else "تحديد كمنشور")
                                }
                                TextButton(onClick = {
                                    updateDeals(deals.filterNot { it.id == deal.id })
                                    if (preview?.id == deal.id) preview = null
                                }) { Text("حذف", color = MaterialTheme.colorScheme.error) }
                            }
                        }
                    }
                }
                if (filtered.isEmpty()) item { Text("لا توجد عروض مطابقة للبحث أو الفلتر.") }
            }
        }
    }
}
