package com.arunet.otpforwarder

import kotlinx.serialization.Serializable

//@Serializable
//data class ForwardingRule(
//    val id: String = java.util.UUID.randomUUID().toString(), // Unique ID for deleting
//    val keywords: String,
//    val targetPhones: String
//)

@Serializable
data class ForwardingRule(
    val id: String = java.util.UUID.randomUUID().toString(),
    val keywords: String,
    val target: String, // This will be either the Phone Number OR the Telegram Chat ID
    val isTelegram: Boolean = false
)