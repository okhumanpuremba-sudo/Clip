package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.AiProviderConfig
import com.example.data.model.ApiUsageLog
import com.example.data.model.ProviderVerificationState
import com.example.ui.theme.ForgeAmber
import com.example.ui.theme.StudioBorder
import com.example.ui.theme.StudioObsidian
import com.example.ui.theme.StudioSurface
import com.example.ui.theme.StudioSurfaceElevated
import com.example.ui.theme.TechnicalCyan
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.VerifiedEmerald
import com.example.ui.theme.WarningCoral

@Composable
fun ApiAndArchitectureScreen(
    apiLogs: List<ApiUsageLog>,
    providers: List<AiProviderConfig>,
    testingProviderId: String?,
    dailyTokenSoftLimit: Long,
    dailyRequestWarnThreshold: Int,
    maxRetryCount: Int,
    customWebhookUrl: String,
    onUpdateQuotaSettings: (Long, Int, Int, String) -> Unit,
    onTestProviderLive: (String) -> Unit
) {
    val totalRequests = apiLogs.size
    val totalTokens = apiLogs.sumOf { it.estimatedTokens }
    val totalRetries = apiLogs.sumOf { it.retryCount }
    val avgLatency = if (apiLogs.isNotEmpty()) apiLogs.map { it.latencyMs }.average().toLong() else 0L
    val isOverWarning = totalTokens >= (dailyTokenSoftLimit * 0.8) || totalRequests >= dailyRequestWarnThreshold

    var tokenLimitInput by rememberSaveable(dailyTokenSoftLimit) { mutableStateOf(dailyTokenSoftLimit.toString()) }
    var reqThresholdInput by rememberSaveable(dailyRequestWarnThreshold) { mutableStateOf(dailyRequestWarnThreshold.toString()) }
    var retryCountInput by rememberSaveable(maxRetryCount) { mutableStateOf(maxRetryCount.toString()) }
    var webhookInput by rememberSaveable(customWebhookUrl) { mutableStateOf(customWebhookUrl) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Live Telemetry KPI Strip
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                MetricStatCard(
                    modifier = Modifier.weight(1f),
                    label = "API CALLS",
                    value = totalRequests.toString(),
                    badge = "WARN AT $dailyRequestWarnThreshold",
                    badgeColor = if (totalRequests >= dailyRequestWarnThreshold) WarningCoral else VerifiedEmerald
                )
                MetricStatCard(
                    modifier = Modifier.weight(1f),
                    label = "EST. TOKENS",
                    value = totalTokens.toString(),
                    badge = "LIMIT $dailyTokenSoftLimit",
                    badgeColor = if (totalTokens >= dailyTokenSoftLimit) WarningCoral else ForgeAmber
                )
                MetricStatCard(
                    modifier = Modifier.weight(1f),
                    label = "AVG LATENCY",
                    value = "${avgLatency}ms",
                    badge = "$totalRetries RETRIES",
                    badgeColor = TechnicalCyan
                )
            }
        }

        // 2. Usage Quota Progress & Warning Banner
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (isOverWarning) StudioSurfaceElevated else StudioSurface
                ),
                border = BorderStroke(
                    width = 1.dp,
                    color = if (isOverWarning) WarningCoral else StudioBorder
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = if (isOverWarning) Icons.Default.WarningAmber else Icons.Default.CloudSync,
                                contentDescription = null,
                                tint = if (isOverWarning) WarningCoral else VerifiedEmerald
                            )
                            Text(
                                text = if (isOverWarning) {
                                    "USAGE WARNING: APPROACHING CONFIGURED QUOTA"
                                } else {
                                    "SERVER-ATTESTED QUOTA & RETRY HEALTH"
                                },
                                style = MaterialTheme.typography.titleMedium,
                                color = if (isOverWarning) WarningCoral else TextPrimary
                            )
                        }
                        StatusPillBadge(text = "ZERO CLIENT KEYS", color = VerifiedEmerald)
                    }

                    val tokenProgress = (totalTokens.toFloat() / dailyTokenSoftLimit.coerceAtLeast(1L)).coerceIn(0f, 1f)
                    LinearProgressIndicator(
                        progress = { tokenProgress },
                        color = if (isOverWarning) WarningCoral else ForgeAmber,
                        trackColor = StudioObsidian,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                    )

                    Text(
                        text = "Token Usage: $totalTokens / $dailyTokenSoftLimit (${(tokenProgress * 100).toInt()}%) • Requests: $totalRequests / $dailyRequestWarnThreshold • Exponential Backoff Retries: $maxRetryCount",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary
                    )
                }
            }
        }

        // 3. Configurable Backup Providers & Live Verification Tests
        item {
            Text(
                text = "PRIMARY & BACKUP PROVIDERS (LIVE VERIFICATION REQUIRED)",
                style = MaterialTheme.typography.labelLarge,
                color = TextSecondary
            )
        }

        items(providers, key = { it.id }) { provider ->
            val stateColor = when (provider.verificationState) {
                ProviderVerificationState.VERIFIED_WORKING -> VerifiedEmerald
                ProviderVerificationState.UNTESTED -> ForgeAmber
                ProviderVerificationState.FAILED -> WarningCoral
            }
            val isTestingThis = testingProviderId == provider.id

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("provider_card_${provider.id}"),
                colors = CardDefaults.cardColors(containerColor = StudioSurface),
                border = BorderStroke(1.dp, stateColor.copy(alpha = 0.6f))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = provider.name,
                                style = MaterialTheme.typography.titleMedium,
                                color = TextPrimary
                            )
                            Text(
                                text = "Target: ${provider.modelOrEndpoint}",
                                style = MaterialTheme.typography.labelSmall,
                                color = TechnicalCyan
                            )
                        }
                        StatusPillBadge(text = provider.roleLabel, color = stateColor)
                    }

                    Surface(
                        color = StudioObsidian,
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, StudioBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = when (provider.verificationState) {
                                    ProviderVerificationState.VERIFIED_WORKING -> Icons.Default.CheckCircle
                                    ProviderVerificationState.UNTESTED -> Icons.Default.WarningAmber
                                    ProviderVerificationState.FAILED -> Icons.Default.ErrorOutline
                                },
                                contentDescription = null,
                                tint = stateColor,
                                modifier = Modifier.size(18.dp)
                            )
                            Column {
                                Text(
                                    text = provider.verificationState.label,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = stateColor
                                )
                                Text(
                                    text = provider.lastTestMessage,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary
                                )
                            }
                        }
                    }

                    Text(
                        text = "Cost & Quota Notes: ${provider.costNotes}",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )

                    Button(
                        onClick = { onTestProviderLive(provider.id) },
                        enabled = testingProviderId == null,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ForgeAmber,
                            contentColor = StudioObsidian
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("test_provider_${provider.id}")
                    ) {
                        if (isTestingThis) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = StudioObsidian,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Running Live Round-Trip Test...", fontWeight = FontWeight.Bold)
                        } else {
                            Icon(Icons.Default.NetworkCheck, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Run Live Verification Test", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // 4. Quota, Retry & Custom Worker Endpoint Configuration
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = StudioSurface),
                border = BorderStroke(1.dp, StudioBorder)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "CONFIGURE QUOTA WARNINGS, RETRIES & BACKUP WORKER URL",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = tokenLimitInput,
                            onValueChange = { tokenLimitInput = it },
                            label = { Text("Soft Token Cap") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = reqThresholdInput,
                            onValueChange = { reqThresholdInput = it },
                            label = { Text("Request Warn") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = retryCountInput,
                            onValueChange = { retryCountInput = it },
                            label = { Text("Max Retries") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    OutlinedTextField(
                        value = webhookInput,
                        onValueChange = { webhookInput = it },
                        label = { Text("Custom Remote Video/LLM Worker HTTPS Health URL") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Button(
                        onClick = {
                            onUpdateQuotaSettings(
                                tokenLimitInput.toLongOrNull() ?: 25_000L,
                                reqThresholdInput.toIntOrNull() ?: 15,
                                retryCountInput.toIntOrNull() ?: 2,
                                webhookInput
                            )
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = TechnicalCyan,
                            contentColor = StudioObsidian
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Save Quota, Retry & Backup Settings", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // 5. Architectural Honesty Matrix: Working Features vs Prototypes/Adapters
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = StudioSurfaceElevated),
                border = BorderStroke(1.dp, TechnicalCyan)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "SYSTEM ARCHITECTURE MATRIX (WORKING VS PLUGGABLE ADAPTERS)",
                        style = MaterialTheme.typography.titleMedium,
                        color = TechnicalCyan
                    )

                    ArchitectureStatusRow(
                        feature = "AI Highlight Detection & Structured Scoring",
                        badge = "WORKING (LIVE)",
                        badgeColor = VerifiedEmerald,
                        details = "Uses Firebase AI Logic (gemini-3.8-flash) with App Check attestation. Identifies Funny Moments, Reactions, Gaming Highlights, Clutch Plays, Exciting Moments, and Memorable Dialogue."
                    )
                    ArchitectureStatusRow(
                        feature = "Whop Campaign Mode & Compliance Validator",
                        badge = "WORKING (LIVE)",
                        badgeColor = VerifiedEmerald,
                        details = "Persists campaign rules in Cloud Firestore and deterministically validates duration, 9:16 aspect ratio, watermark logo zone, safe zones, hashtags, CTA, and SEO rules."
                    )
                    ArchitectureStatusRow(
                        feature = "On-Device Video Inspection & MP4 Stream Remuxer",
                        badge = "WORKING (LOCAL)",
                        badgeColor = VerifiedEmerald,
                        details = "Uses Android MediaMetadataRetriever for keyframe extraction and MediaMuxer for lossless MP4 segment slicing when a local video file is selected."
                    )
                    ArchitectureStatusRow(
                        feature = "Cloud GPU 9:16 Pixel Re-Encode & Subtitle Burn-In Worker",
                        badge = "PLUGGABLE BACKEND SPEC",
                        badgeColor = ForgeAmber,
                        details = "Generates verified .SRT subtitle files, JSON Edit Decision Lists (EDL), and the exact FFmpeg filtergraph command so a remote FFmpeg/GPU worker can be connected without refactoring."
                    )
                }
            }
        }

        // 6. Immutable Firestore API Usage Telemetry Log
        item {
            Text(
                text = "RECENT API TELEMETRY LOGS (${apiLogs.size})",
                style = MaterialTheme.typography.labelLarge,
                color = TextSecondary
            )
        }

        items(apiLogs.take(15), key = { it.id }) { log ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = StudioSurface),
                border = BorderStroke(1.dp, StudioBorder)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "${log.operation} • ${log.modelName}",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary
                        )
                        Text(
                            text = "Provider: ${log.providerId} • Latency: ${log.latencyMs}ms • Est. Tokens: ${log.estimatedTokens} • Retries: ${log.retryCount}",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary
                        )
                        if (log.errorMessage.isNotBlank()) {
                            Text(
                                text = log.errorMessage,
                                style = MaterialTheme.typography.bodySmall,
                                color = WarningCoral
                            )
                        }
                    }
                    StatusPillBadge(
                        text = if (log.success) "OK" else "ERR",
                        color = if (log.success) VerifiedEmerald else WarningCoral
                    )
                }
            }
        }
    }
}

@Composable
private fun ArchitectureStatusRow(
    feature: String,
    badge: String,
    badgeColor: androidx.compose.ui.graphics.Color,
    details: String
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = feature,
                style = MaterialTheme.typography.labelLarge,
                color = TextPrimary,
                modifier = Modifier.weight(1f)
            )
            StatusPillBadge(text = badge, color = badgeColor)
        }
        Text(
            text = details,
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary
        )
    }
}
