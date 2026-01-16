package com.arunet.otpforwarder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.SmsManager
import android.telephony.SmsMessage
import android.util.Log
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

class SMSReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // 1. Check if the intent is actually an SMS
        if (intent.action == "android.provider.Telephony.SMS_RECEIVED") {
            val bundle = intent.extras
            val pdus = bundle?.get("pdus") as? Array<*> ?: return
            val format = bundle.getString("format")

            for (pdu in pdus) {
                // 2. Extract the 'body' from the PDU (Protocol Data Unit)
                val message = SmsMessage.createFromPdu(pdu as ByteArray, format)
                val body = message.displayMessageBody // This defines 'body'
                val sender = message.displayOriginatingAddress

                val dataStore = DataStoreManager(context)

                runBlocking {
                    val rules = dataStore.rulesFlow.first()
                    val bodyText = body.trim()

                    for (rule in rules) {// Change: Use the full keyword string for comparison
                        val requiredKeyword = rule.keywords.trim()

                        // Use equals() for an exact match, or contains() if the keyword
                        // must be the ONLY thing in the message.
                        // If you want it to be EXACT (start to finish), use:
                        if (requiredKeyword.isNotEmpty() && bodyText.contains(requiredKeyword, ignoreCase = true)) {
                            val phoneList = rule.target.split(",").map { it.trim() }
                            // phoneList.forEach { phone -> forwardSMS(context, phone, body) }

                            for (phone in phoneList) {
                                if (rule.isTelegram) {
                                    forwardToTelegram(BuildConfig.BOT_TOKEN, phone, body)
                                } else {
                                    forwardSMS(context, phone, body)
                                }
                            }

                            dataStore.addLog("Forwarded ${rule.keywords} to ${phoneList.size} numbers.")
                        }
                    }
                }
            }
        }
    }

    private fun forwardSMS(context: Context, targetPhone: String, messageBody: String) {
        try {
            val smsManager: SmsManager = context.getSystemService(SmsManager::class.java)

            // We use divideMessage in case the OTP text is very long (rare for OTPs, but safe)
            val parts = smsManager.divideMessage("Forwarded: $messageBody")

            smsManager.sendMultipartTextMessage(
                targetPhone,
                null,
                parts,
                null,
                null
            )
            Log.d("OTPForwarder", "Successfully forwarded to $targetPhone")
        } catch (e: Exception) {
            Log.e("OTPForwarder", "Failed to forward SMS: ${e.message}")
        }
    }

    private val client = okhttp3.OkHttpClient()

    private fun forwardToTelegram(botToken: String, chatId: String, message: String) {
        val url = "https://api.telegram.org/bot$botToken/sendMessage"

        val formBody = okhttp3.FormBody.Builder()
            .add("chat_id", chatId)
            .add("text", "🔔 *New OTP Received* \n\n$message")
            .add("parse_mode", "Markdown")
            .build()

        val request = okhttp3.Request.Builder()
            .url(url)
            .post(formBody)
            .build()

        // We use a background thread for networking
        client.newCall(request).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {
                Log.e("Telegram", "Failed: ${e.message}")
            }
            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                Log.d("Telegram", "Success: ${response.body?.string()}")
            }
        })
    }
}