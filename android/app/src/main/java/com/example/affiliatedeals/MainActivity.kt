package com.example.affiliatedeals

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
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
import java.util.Locale

data class Deal(
    val name: String,
    val price: String,
    val oldPrice: String,
    val url: String
) {
    fun discount(): Int? {
        val p = price.toDoubleOrNull()
        val old = oldPrice.toDoubleOrNull()
        return if (p != null && old != null && old > 0 && p < old)
            (((old - p) / old) * 100).toInt() else null
    }
    fun post(): String {
        val d = discount()
        return buildString {
            append("🔥 عرض مميز على Amazon.eg\n\n")
            append(name).append("\n")
            append("السعر الحالي: ").append(price).append(" جنيه\n")
            if (oldPrice.isNotBlank()) append("السعر السابق: ").append(oldPrice).append(" جنيه\n")
            if (d != null) append("خصم ").append(d).append("% 🎉\n")
            append("\nرابط المنتج:\n").append(url)
            append("\n\nقد أحصل على عمولة من عمليات الشراء المؤهلة.")
        }
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
    var deals by remember { mutableStateOf(listOf<Deal>()) }
    var preview by remember { mutableStateOf<Deal?>(null) }
    var error by remember { mutableStateOf("") }

    MaterialTheme {
        Scaffold(
            topBar = { TopAppBar(title = { Text("Amazon Deals Egypt") }) }
        ) { padding ->
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Text("إضافة عرض جديد", style = MaterialTheme.typography.titleLarge)
                    OutlinedTextField(name, { name = it }, label = { Text("اسم المنتج") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    OutlinedTextField(price, { price = it }, label = { Text("السعر الحالي بالجنيه") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    OutlinedTextField(oldPrice, { oldPrice = it }, label = { Text("السعر قبل الخصم (اختياري)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    OutlinedTextField(url, { url = it }, label = { Text("رابط المنتج") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            error = when {
                                name.isBlank() -> "اكتب اسم المنتج أولاً"
                                price.toDoubleOrNull() == null -> "اكتب السعر الحالي كرقم"
                                url.isBlank() -> "أضف رابط المنتج"
                                else -> ""
                            }
                            if (error.isBlank()) {
                                val deal = Deal(name.trim(), price.trim(), oldPrice.trim(), url.trim())
                                deals = listOf(deal) + deals
                                preview = deal
                                name = ""; price = ""; oldPrice = ""; url = ""
                            }
                        }
                    ) { Text("إضافة العرض ومعاينة المنشور") }
                }
                preview?.let { deal ->
                    item {
                        HorizontalDivider()
                        Text("معاينة المنشور", style = MaterialTheme.typography.titleLarge)
                        Card {
                            Column(Modifier.padding(14.dp)) {
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
                                        val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                            type = "text/plain"; putExtra(android.content.Intent.EXTRA_TEXT, deal.post())
                                        }
                                        context.startActivity(android.content.Intent.createChooser(send, "مشاركة العرض"))
                                    }
                                ) { Text("مشاركة المنشور") }
                            }
                        }
                    }
                }
                item {
                    HorizontalDivider()
                    Text("العروض المضافة (${deals.size})", style = MaterialTheme.typography.titleLarge)
                    Text("ملاحظة: هذه نسخة أولية؛ القائمة مؤقتة وتُمسح عند إغلاق التطبيق.")
                }
                items(deals) { deal ->
                    Card {
                        Column(Modifier.fillMaxWidth().padding(12.dp)) {
                            Text(deal.name, style = MaterialTheme.typography.titleMedium)
                            Text("${deal.price} جنيه" + (deal.discount()?.let { " • خصم $it%" } ?: ""))
                            Text(deal.url, style = MaterialTheme.typography.bodySmall)
                            TextButton(onClick = { preview = deal }) { Text("عرض المنشور") }
                        }
                    }
                }
            }
        }
    }
}
