package com.arunet.otpforwarder

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import com.arunet.otpforwarder.ui.theme.OTPHopperTheme
import kotlinx.coroutines.launch
import android.os.Build.VERSION.SDK_INT
import android.webkit.WebSettings
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.unit.TextUnit
import org.w3c.dom.Text
import java.time.format.TextStyle

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Request Permissions on Launch
        val permissions = arrayOf(
            android.Manifest.permission.RECEIVE_SMS,
            android.Manifest.permission.SEND_SMS,
            android.Manifest.permission.READ_SMS
        )
        requestPermissions(permissions, 101)

        // Initialize the manager
        val dataStoreManager = DataStoreManager(this)

        setContent {
            val onboardingCompleted by dataStoreManager.onboardingCompletedFlow.collectAsState(initial = null)
            // Define 'scope' here if you need it inside Composables
            val scope = rememberCoroutineScope()

// Show nothing or a splash until we know the state
            if (onboardingCompleted == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (onboardingCompleted == false) {
                OnboardingScreen(onFinished = {
                    scope.launch { dataStoreManager.setOnboardingCompleted() }
                })
            } else {
                // Use your App's Theme wrapper here
                MaterialTheme {
                    Surface(color = MaterialTheme.colorScheme.background) {
                        SettingsScreen(dataStoreManager)
                    }
                }
            }
        }
    }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Hello $name!",
        modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    OTPHopperTheme {
        Greeting("Android")
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(dataStoreManager: DataStoreManager) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val rules by dataStoreManager.rulesFlow.collectAsState(initial = emptyList())

    var isTelegram by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf(false) }
    val options = listOf("SMS", "Telegram")

    fun isValidIndianPhoneList(input: String): Boolean {
        if (input.isBlank()) return false
        // Regex for +91 followed by 10 digits
        val pattern = Regex("""^\+91[6-9]\d{9}$""")

        return input.split(",")
            .map { it.trim() }
            .all { it.matches(pattern) }
    }

    var keywordsInput by remember { mutableStateOf("") }
    var phonesInput by remember { mutableStateOf("") }
    val isPhoneValid = remember(phonesInput) { isValidIndianPhoneList(phonesInput) }

    var editingRule by remember { mutableStateOf<ForwardingRule?>(null) }

    val scrollState = rememberScrollState()

    fun fetchTelegramId(botToken: String) {
        val TAG = "TelegramFetch"
        Log.d(TAG, "Starting fetch with token: $botToken")

        val client = okhttp3.OkHttpClient()
        val request = okhttp3.Request.Builder()
            .url("https://api.telegram.org/bot$botToken/getUpdates")
            .build()

        client.newCall(request).enqueue(object : okhttp3.Callback {
            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                val body = response.body?.string() ?: ""
                Log.d(TAG, "Raw Response: $body") // See the full JSON from Telegram

                if (!response.isSuccessful) {
                    Log.e(TAG, "Server returned error: ${response.code}")
                    return
                }

                // Regex to find the most recent chat ID
                val match = Regex("""\"chat\":\{"id":(-?\d+)""").findAll(body).lastOrNull()
                val chatId = match?.groupValues?.get(1) ?: ""

                if (chatId.isNotEmpty()) {
                    Log.i(TAG, "Successfully found Chat ID: $chatId")
                    phonesInput = chatId
                } else {
                    Log.w(TAG, "Request successful, but no Chat ID found in JSON. Did you send a message to the bot?")
                }
            }

            override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {
                Log.e(TAG, "Network Failure: ${e.message}")
                phonesInput = "error"
            }
        })
    }

    Column(modifier = Modifier
        .padding(16.dp)
        .fillMaxSize()
        .verticalScroll(scrollState), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        StatusSection(LocalContext.current, dataStoreManager)

        Text("Add New Routing Rule", style = MaterialTheme.typography.titleLarge)

        OutlinedTextField(
            value = keywordsInput,
            onValueChange = { keywordsInput = it },
            label = { Text("Keywords (e.g., Hi, your JioHotstar verification code is)") },
            modifier = Modifier.fillMaxWidth()
        )

        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = !expanded }
        ) {
            OutlinedTextField(
                value = if (isTelegram) "Telegram" else "SMS",
                onValueChange = {},
                readOnly = true,
                label = { Text("Telegram / SMS") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier
                    .menuAnchor()
                    .fillMaxWidth()
            )

            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                DropdownMenuItem(
                    text = { Text("Telegram") },
                    onClick = { isTelegram = true; expanded = false; phonesInput = "" }
                )
                DropdownMenuItem(
                    text = { Text("SMS") },
                    onClick = { isTelegram = false; expanded = false; phonesInput = "" }
                )
            }
        }


        var isphonesInputValid = phonesInput.isNotBlank()
        var phonesInputLabel = "Receivers (e.g., -123...)"
        if (!isTelegram) {
            phonesInputLabel = "Receivers (e.g., +9198..., +9188...)"
            isphonesInputValid = !isPhoneValid && phonesInput.isNotBlank()
        }
        OutlinedTextField(
            value = phonesInput,
            onValueChange = { phonesInput = it },
            isError = isphonesInputValid,
            label = { Text(phonesInputLabel) },
            modifier = Modifier.fillMaxWidth()
        )


        if (isTelegram) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Spacer(modifier = Modifier.width(8.dp))
                Button(onClick = {
                    val botUsername = "OTPHopperbot"
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/$botUsername?startgroup=true"))
                    context.startActivity(intent)
                }) {
                    Text("Choose a telegram group")
                }

                Text("And", Modifier.padding(horizontal = 5.dp))

                Button(onClick = {
                    fetchTelegramId(BuildConfig.BOT_TOKEN)
                }) {
                    Icon(Icons.Default.Refresh, contentDescription = null)
                    Text("Refresh", Modifier.padding(horizontal = 1.dp))
                }
            }
        }

        Button(
            onClick = {
                // Ensure both fields have data before saving
                if (keywordsInput.isNotBlank() && phonesInput.isNotBlank()) {
                    scope.launch {
                        // Create a new rule using the isTelegram flag from your state
                        val newRule = ForwardingRule(
                            keywords = keywordsInput,
                            target = phonesInput,
                            isTelegram = isTelegram // This is the boolean from your dropdown
                        )

                        dataStoreManager.addRule(newRule)

                        // Clear inputs after successful save
                        keywordsInput = ""
                        phonesInput = ""
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            // Disable button if inputs are empty for better UX
            enabled = keywordsInput.isNotBlank() && phonesInput.isNotBlank()
        ) {
            Text("Add Rule")
        }

//        Button(
//            onClick = {
//                if (keywordsInput.isNotBlank() && phonesInput.isNotBlank()) {
//                    scope.launch {
//                        dataStoreManager.addRule(ForwardingRule(keywords = keywordsInput, target = phonesInput))
//                        keywordsInput = ""
//                        phonesInput = ""
//                    }
//                }
//            },
//            modifier = Modifier.fillMaxWidth()
//        ) {
//            Text("Add Rule")
//        }

        Divider()

        Text("Active Rules", style = MaterialTheme.typography.titleMedium)

        // If editingRule is not null, show the Edit Dialog
        editingRule?.let { rule ->
            EditRuleDialog(
                rule = rule,
                onDismiss = { editingRule = null },
                onConfirm = { updatedRule ->
                    scope.launch {
                        dataStoreManager.updateRule(updatedRule)
                        editingRule = null
                    }
                }
            )
        }

        LazyColumn(modifier = Modifier
            .weight(1f)
            .heightIn(min = 200.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(rules) { rule ->
                RuleCard(rule, onEdit =  { editingRule = rule },onDelete = { scope.launch { dataStoreManager.deleteRule(rule.id) } })
            }
        }

        LogSection(dataStoreManager)
    }
}

@Composable
fun RuleCard(rule: ForwardingRule, onEdit: () -> Unit, onDelete: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(2.dp)) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
//                Text("Keywords: ${rule.keywords}", fontWeight = FontWeight.Bold)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Keywords: ${rule.keywords}", fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(8.dp))
                }
                // Visual Indicator
                SuggestionChip(
                    onClick = { },
                    label = { Text(if (rule.isTelegram) "Telegram" else "SMS") }
                )
                Text("To: ${rule.target}", style = MaterialTheme.typography.bodySmall)
            }
            IconButton(onClick = onEdit) {
                Icon(Icons.Default.Edit, contentDescription = "Edit", tint = MaterialTheme.colorScheme.primary)
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.Red)
            }
        }
    }
}

@Composable
fun StatusSection(context: Context, dataStoreManager: DataStoreManager) {
    // Change this line to collect rulesFlow instead of phoneFlow
    val rules by dataStoreManager.rulesFlow.collectAsState(initial = emptyList())

    val hasSmsPermission = remember {
        mutableStateOf(
            context.checkSelfPermission(android.Manifest.permission.RECEIVE_SMS) == android.content.pm.PackageManager.PERMISSION_GRANTED
        )
    }

    // Now it's ready if we have at least one rule and permission
    val isReady = rules.isNotEmpty() && hasSmsPermission.value

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isReady) Color(0xFFE8F5E9) else Color(0xFFFFEBEE)
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .background(
                        color = if (isReady) Color.Green else Color.Red,
                        shape = CircleShape
                    )
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = if (isReady) "Service Active (${rules.size} Rules)" else "Service Inactive (Add a Rule)",
                style = MaterialTheme.typography.bodyMedium,
                color = if (isReady) Color(0xFF2E7D32) else Color(0xFFC62828)
            )
        }
    }
}

//@Composable
//fun LogSection(dataStoreManager: DataStoreManager) {
//    val logs by dataStoreManager.logsFlow.collectAsState(initial = "No logs yet.")
//
//    Text("Recent Activity", style = MaterialTheme.typography.titleMedium)
//
//    Card(
//        modifier = Modifier
//            .fillMaxWidth()
//            .height(100.dp),
//        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
//    ) {
//        // Use a lazy column or basic scrollable text
//        Column(modifier = Modifier
//            .padding(12.dp)
//            .verticalScroll(rememberScrollState())) {
//            Text(
//                text = logs,
//                style = MaterialTheme.typography.bodySmall,
//                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
//            )
//        }
//    }
//}

@Composable
fun LogSection(dataStoreManager: DataStoreManager) {
    val logs by dataStoreManager.logsFlow.collectAsState(initial = "No logs yet.")

    // 1. Create a state to track if the logs are expanded
    var isExpanded by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        // 2. Clickable Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { isExpanded = !isExpanded } // Toggles the state
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Recent Activity", style = MaterialTheme.typography.titleMedium)

            // 3. Arrow Icon that rotates based on state
            Icon(
                imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = if (isExpanded) "Collapse" else "Expand"
            )
        }

        // 4. Smooth Animation wrapper
        AnimatedVisibility(visible = isExpanded) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp), // Increased height slightly for better readability when open
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(
                    modifier = Modifier
                        .padding(12.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = logs,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                    )
                }
            }
        }
    }
}
@Composable
fun EditRuleDialog(
    rule: ForwardingRule,
    onDismiss: () -> Unit,
    onConfirm: (ForwardingRule) -> Unit
) {
    var keywords by remember { mutableStateOf(rule.keywords) }
    var phones by remember { mutableStateOf(rule.target) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Routing Rule") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = keywords, onValueChange = { keywords = it }, label = { Text("Keywords") })
                OutlinedTextField(value = phones, onValueChange = { phones = it }, label = { Text("Receivers") })
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(rule.copy(keywords = keywords, target = phones)) }) {
                Text("Update")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun OnboardingScreen(onFinished: () -> Unit) {
    val pagerState = rememberPagerState(pageCount = { 3 })
    val scope = rememberCoroutineScope()

    Column(modifier = Modifier.fillMaxSize()) {
        HorizontalPager(state = pagerState, modifier = Modifier.weight(1f)) { page ->
            OnboardingPage(page)
        }

        Button(
            onClick = {
                if (pagerState.currentPage < 2) {
                    scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                } else {
                    onFinished()
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (pagerState.currentPage == 2) "Get Started" else "Next")
        }
    }
}

@Composable
fun OnboardingPage(page: Int) {
    val context = LocalContext.current

    // Configure Coil to handle GIFs
    val imageLoader = ImageLoader.Builder(context)
        .components {
            if (SDK_INT >= 28) add(ImageDecoderDecoder.Factory()) else add(GifDecoder.Factory())
        }
        .build()

    data class OnboardData(
        val title: String,
        val desc: String,
        val image: Int?,
        val bgColor: Color
    )

    val onboardingData = listOf(
        OnboardData("Welcome", "Automatically forward your OTPs.", R.drawable.welcome, Color.White),
        OnboardData("All Set!", "Your messages stay private.", R.drawable.sharing, Color.White),
        OnboardData("Welcome", "Add the rules, share the Joy!", null, Color(0xFFDFF2E1))
    )

    val data = onboardingData[page]

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(data.bgColor) // Your Telegram Blue or Purple
    ) {
        // 1. BACKGROUND LAYER: Only show SVG on the 3rd page (index 2)
        if (data.image == null) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center // Centers everything inside the Box
            ) {
                Image(
                    painter = painterResource(id = R.drawable.celebration), // Your SVG
                    contentDescription = null,
                    modifier = Modifier.fillMaxWidth(),
                    contentScale = ContentScale.Crop, // Or ContentScale.Inside if you want it centered
                    alpha = 1f // Lower opacity so text is readable
                )
            }
        }

        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = if (data.image == null) Arrangement.Center else Arrangement.Top
        ) {

            val image = data.image;

            if (image != null) {
                // The Visual Element
                AsyncImage(
                    model = image,
                    contentDescription = null,
                    imageLoader = imageLoader,
                    modifier = Modifier
                        .fillMaxWidth(),
                    contentScale = ContentScale.FillBounds
                )
            }
        }
    }
}