package com.arunet.otpforwarder

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

val Context.dataStore by preferencesDataStore(name = "settings")

class DataStoreManager(private val context: Context) {

    private object PreferencesKeys {
        val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
    }

    // Flow to check status
    val onboardingCompletedFlow: Flow<Boolean> = context.dataStore.data
        .map { preferences -> preferences[PreferencesKeys.ONBOARDING_COMPLETED] ?: false }

    // Function to mark it as done
    suspend fun setOnboardingCompleted() {
        context.dataStore.edit { it[PreferencesKeys.ONBOARDING_COMPLETED] = true }
    }

    companion object {
        val RULES_KEY = stringPreferencesKey("forwarding_rules")
        val LOGS_KEY = stringPreferencesKey("forwarding_logs")
    }

    // --- RULE MANAGEMENT ---
    val rulesFlow: Flow<List<ForwardingRule>> = context.dataStore.data.map { pref ->
        val json = pref[RULES_KEY] ?: "[]"
        try {
            Json.decodeFromString<List<ForwardingRule>>(json)
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun addRule(newRule: ForwardingRule) {
        context.dataStore.edit { pref ->
            val currentJson = pref[RULES_KEY] ?: "[]"
            val currentRules = Json.decodeFromString<List<ForwardingRule>>(currentJson)
            val updatedRules = currentRules + newRule
            pref[RULES_KEY] = Json.encodeToString(updatedRules)
        }
    }

    suspend fun updateRule(updatedRule: ForwardingRule) {
        context.dataStore.edit { pref ->
            val currentJson = pref[RULES_KEY] ?: "[]"
            val currentRules = Json.decodeFromString<List<ForwardingRule>>(currentJson)
            // Find the rule with the same ID and replace it
            val updatedRules = currentRules.map {
                if (it.id == updatedRule.id) updatedRule else it
            }
            pref[RULES_KEY] = Json.encodeToString(updatedRules)
        }
    }

    suspend fun deleteRule(ruleId: String) {
        context.dataStore.edit { pref ->
            val currentJson = pref[RULES_KEY] ?: "[]"
            val currentRules = Json.decodeFromString<List<ForwardingRule>>(currentJson)
            val updatedRules = currentRules.filter { it.id != ruleId }
            pref[RULES_KEY] = Json.encodeToString(updatedRules)
        }
    }

    // --- LOG MANAGEMENT ---
    val logsFlow: Flow<String> = context.dataStore.data.map { it[LOGS_KEY] ?: "No logs yet." }

    suspend fun addLog(message: String) {
        context.dataStore.edit { pref ->
            val currentLogs = pref[LOGS_KEY] ?: ""
            val timestamp = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date())
            val newLog = "[$timestamp] $message"
            pref[LOGS_KEY] = (listOf(newLog) + currentLogs.split("\n")).take(15).joinToString("\n")
        }
    }
}