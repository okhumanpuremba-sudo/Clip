package com.example.ui

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ai.HighlightDetectionEngine
import com.example.data.local.LocalClipTimelineStore
import com.example.data.local.LocalWhopCampaignStore
import com.example.data.model.AiProviderConfig
import com.example.data.model.ApiUsageLog
import com.example.data.model.CampaignPreset
import com.example.data.model.ClipCandidate
import com.example.data.model.ClipExtendedConfig
import com.example.data.model.ExportQualityConfig
import com.example.data.model.HookCaptionConfig
import com.example.data.model.LogoPlacementConfig
import com.example.data.model.NormalizedRect
import com.example.data.model.ProviderVerificationState
import com.example.data.model.VerticalLayoutType
import com.example.data.model.VideoProject
import com.example.data.model.WhopCampaign
import com.example.data.repository.ClipForgeRepository
import com.example.domain.AutoFramingEngine
import com.example.domain.ClipExportResult
import com.example.domain.UrlVideoIngestEngine
import com.example.domain.VideoProcessingPipeline
import com.example.domain.WhopRuleValidator
import com.example.util.GallerySaveResult
import com.example.util.GalleryVideoSaver
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val TAG = "ClipForgeVM"
private const val STOP_TIMEOUT_MS = 5000L

sealed interface DataStreamState<out T> {
    data object Loading : DataStreamState<Nothing>
    data class Success<T>(val data: T) : DataStreamState<T>
    data class Error(val message: String) : DataStreamState<Nothing>
}

data class StudioUiState(
    val selectedProjectId: String? = null,
    val selectedCampaignId: String? = null,
    val selectedClipId: String? = null,
    val desiredClipCount: Int = 3,
    val includeVideoCaptions: Boolean = true,
    val viralCaptionStyle: String = "HORMOZI_BOLD",
    val localCampaigns: List<WhopCampaign> = emptyList(),
    val localProjects: List<VideoProject> = emptyList(),
    val localClips: List<ClipCandidate> = emptyList(),
    val campaignPresets: List<CampaignPreset> = emptyList(),
    val activePresetId: String? = null,
    val isIngestingUrl: Boolean = false,
    val isAnalyzingHighlights: Boolean = false,
    val pipelineProgress: Float = 0f,
    val pipelineStageLabel: String = "",
    val analysisStatusBanner: String? = null,
    val errorBanner: String? = null,
    val lastExportSummary: String? = null,
    val lastExportFfmpegCommand: String? = null,
    val lastExportSrtPreview: String? = null,
    val lastGallerySaveMessage: String? = null,
    val lastSavedMp4Path: String? = null,
    val isSavingToGallery: Boolean = false,
    val localExtendedConfigs: Map<String, ClipExtendedConfig> = emptyMap(),
    val uploadedPreviewFrames: Map<String, List<Bitmap>> = emptyMap(),
    val singleClipPreviewFrame: Bitmap? = null,
    val dailyTokenSoftLimit: Long = 25_000L,
    val dailyRequestWarnThreshold: Int = 15,
    val maxRetryCount: Int = 2,
    val customWebhookUrl: String = "",
    val testingProviderId: String? = null,
    val providers: List<AiProviderConfig> = listOf(
        AiProviderConfig(
            id = "firebase_ai_primary",
            name = "Firebase AI Logic (Primary)",
            modelOrEndpoint = "gemini-3.8-flash",
            roleLabel = "PRIMARY SERVER-ATTESTED",
            costNotes = "Free Spark tier included; server-attested via App Check (zero client keys).",
            isBuiltInFirebase = true,
            verificationState = ProviderVerificationState.UNTESTED,
            lastTestMessage = "Tap 'Run Live Test' to verify App Check + Gemini 3.8 Flash."
        ),
        AiProviderConfig(
            id = "firebase_ai_backup_lite",
            name = "Firebase AI Logic Lite (Backup #1)",
            modelOrEndpoint = "gemini-3.5-flash-lite",
            roleLabel = "FAILOVER STANDBY",
            costNotes = "Free Spark tier; lower latency & higher quota headroom fallback.",
            isBuiltInFirebase = true,
            verificationState = ProviderVerificationState.UNTESTED,
            lastTestMessage = "UNTESTED — Never assumed working until live test succeeds."
        ),
        AiProviderConfig(
            id = "custom_remote_worker",
            name = "Custom Remote Video/LLM Worker (Backup #2)",
            modelOrEndpoint = "https://your-worker.example.com/health",
            roleLabel = "PLUGGABLE BACKEND",
            costNotes = "Self-hosted FFmpeg + Whisper/LLM worker; cost depends on your cloud host.",
            isBuiltInFirebase = false,
            verificationState = ProviderVerificationState.UNTESTED,
            lastTestMessage = "UNTESTED — Configure HTTPS URL and run live test before use."
        )
    )
)

class ClipForgeViewModel(
    private val repository: ClipForgeRepository,
    private val currentUserId: String,
    private val appContext: Context? = null
) : ViewModel() {

    private val aiEngine = HighlightDetectionEngine()

    private val _studioState = MutableStateFlow(
        StudioUiState(
            localCampaigns = appContext?.let {
                LocalWhopCampaignStore.loadLocalCampaigns(it, currentUserId)
            }.orEmpty(),
            campaignPresets = appContext?.let {
                LocalWhopCampaignStore.loadCampaignPresets(it, currentUserId)
            }.orEmpty(),
            localExtendedConfigs = appContext?.let {
                LocalClipTimelineStore.loadAllConfigs(it)
            }.orEmpty()
        )
    )
    val studioState: StateFlow<StudioUiState> = _studioState.asStateFlow()

    val projectsState: StateFlow<DataStreamState<List<VideoProject>>> =
        combine(
            repository.observeProjects(currentUserId).catch { emit(emptyList()) },
            _studioState.map { it.localProjects }
        ) { remoteList, localList ->
            val remoteIds = remoteList.map { it.id }.toSet()
            val merged = localList.filterNot { it.id in remoteIds } + remoteList
            if (_studioState.value.selectedProjectId == null && merged.isNotEmpty()) {
                _studioState.update { it.copy(selectedProjectId = merged.first().id) }
            }
            DataStreamState.Success(merged) as DataStreamState<List<VideoProject>>
        }
            .catch { error ->
                Log.w(TAG, "Error observing projects", error)
                emit(DataStreamState.Error(error.message ?: "Failed to load projects"))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                initialValue = DataStreamState.Loading
            )

    val campaignsState: StateFlow<DataStreamState<List<WhopCampaign>>> =
        combine(
            repository.observeCampaigns(currentUserId).catch { emit(emptyList()) },
            _studioState.map { it.localCampaigns }
        ) { remoteList, _ ->
            val merged = if (appContext != null) {
                LocalWhopCampaignStore.mergeWithRemoteCampaigns(appContext, currentUserId, remoteList)
            } else {
                remoteList
            }
            if (_studioState.value.selectedCampaignId == null && merged.isNotEmpty()) {
                _studioState.update { it.copy(selectedCampaignId = merged.first().id) }
            }
            DataStreamState.Success(merged) as DataStreamState<List<WhopCampaign>>
        }
            .catch { error ->
                Log.w(TAG, "Error observing campaigns", error)
                emit(DataStreamState.Error(error.message ?: "Failed to load Whop campaigns"))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                initialValue = DataStreamState.Loading
            )

    val clipsState: StateFlow<DataStreamState<List<ClipCandidate>>> =
        combine(
            repository.observeClips(currentUserId).catch { emit(emptyList()) },
            _studioState.map { it.localClips },
            _studioState.map { it.localExtendedConfigs }
        ) { remoteList, localList, extMap ->
            val localById = localList.associateBy { it.id }
            val mergedRaw = (localList + remoteList.filterNot { it.id in localById.keys })
                .distinctBy { "${it.projectId}_${it.title}_${it.startSec}" }
            if (_studioState.value.selectedClipId == null && mergedRaw.isNotEmpty()) {
                val activeProjId = _studioState.value.selectedProjectId
                val preferredClip = mergedRaw.firstOrNull { it.projectId == activeProjId } ?: mergedRaw.first()
                _studioState.update { it.copy(selectedClipId = preferredClip.id) }
            }
            val hydrated = mergedRaw.map { clip ->
                val ensuredEndSec = if (clip.endSec - clip.startSec < 22L) {
                    clip.startSec + 26L
                } else {
                    clip.endSec
                }
                val normalizedClip = clip.copy(endSec = ensuredEndSec)
                val ext = extMap[normalizedClip.id]
                    ?: normalizedClip.extendedConfig
                    ?: LocalClipTimelineStore.loadExtendedConfigForClip(
                        context = appContext,
                        clip = normalizedClip
                    )
                normalizedClip.copy(extendedConfig = ext)
            }
            DataStreamState.Success(hydrated) as DataStreamState<List<ClipCandidate>>
        }
            .catch { error ->
                Log.w(TAG, "Error observing clips", error)
                emit(DataStreamState.Error(error.message ?: "Failed to load clips"))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                initialValue = DataStreamState.Loading
            )

    val apiLogsState: StateFlow<DataStreamState<List<ApiUsageLog>>> =
        repository.observeApiLogs(currentUserId)
            .map<List<ApiUsageLog>, DataStreamState<List<ApiUsageLog>>> { DataStreamState.Success(it) }
            .catch { error ->
                Log.w(TAG, "Error observing API logs", error)
                emit(DataStreamState.Error(error.message ?: "Failed to load API logs"))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                initialValue = DataStreamState.Loading
            )

    fun selectProject(projectId: String) {
        val currentClips = (clipsState.value as? DataStreamState.Success)?.data.orEmpty()
        val firstClipForProject = currentClips.firstOrNull { it.projectId == projectId }
        val projFrame = _studioState.value.uploadedPreviewFrames[projectId]?.firstOrNull()
        _studioState.update {
            it.copy(
                selectedProjectId = projectId,
                selectedClipId = firstClipForProject?.id ?: it.selectedClipId,
                singleClipPreviewFrame = projFrame ?: it.singleClipPreviewFrame,
                errorBanner = null
            )
        }
    }

    fun selectCampaign(campaignId: String) {
        _studioState.update { it.copy(selectedCampaignId = campaignId, errorBanner = null) }
    }

    fun setDesiredClipCount(count: Int) {
        _studioState.update { it.copy(desiredClipCount = count.coerceIn(1, 10)) }
    }

    fun setIncludeVideoCaptions(enabled: Boolean) {
        _studioState.update { it.copy(includeVideoCaptions = enabled) }
    }

    fun setViralCaptionStyle(styleId: String) {
        _studioState.update { it.copy(viralCaptionStyle = styleId) }
    }

    fun selectClip(clipId: String, context: Context? = null, sourceUri: String = "", startSec: Long = 0L) {
        val currentClips = (clipsState.value as? DataStreamState.Success)?.data.orEmpty()
        val targetClip = currentClips.firstOrNull { it.id == clipId }
        val projFrames = targetClip?.projectId?.let { _studioState.value.uploadedPreviewFrames[it] }.orEmpty()
        _studioState.update {
            it.copy(
                selectedClipId = clipId,
                selectedProjectId = targetClip?.projectId?.ifBlank { it.selectedProjectId } ?: it.selectedProjectId,
                singleClipPreviewFrame = projFrames.firstOrNull() ?: it.singleClipPreviewFrame,
                errorBanner = null,
                lastExportSummary = null
            )
        }
        val canExtractLocalVideo = sourceUri.startsWith("content://") ||
            (sourceUri.isNotBlank() && !sourceUri.startsWith("http://") && !sourceUri.startsWith("https://"))
        if (context != null && canExtractLocalVideo) {
            viewModelScope.launch {
                val frame = VideoProcessingPipeline.extractSinglePreviewFrame(context, sourceUri, startSec)
                if (frame != null) {
                    _studioState.update { it.copy(singleClipPreviewFrame = frame) }
                }
            }
        }
    }

    fun ingestYouTubeOrTwitchUrl(
        rawUrl: String,
        userFocusNotes: String,
        campaign: WhopCampaign?,
        autoFindViralClips: Boolean,
        onNavigateToReview: () -> Unit = {}
    ) {
        if (_studioState.value.isIngestingUrl || _studioState.value.isAnalyzingHighlights) return
        viewModelScope.launch {
            _studioState.update {
                it.copy(
                    isIngestingUrl = true,
                    pipelineProgress = 0.12f,
                    pipelineStageLabel = "Step 1/4: Fetching stream metadata, video stream & chapters...",
                    analysisStatusBanner = "Fetching live video stream, chapters, heatmap & captions...",
                    errorBanner = null
                )
            }

            val ingestResult = UrlVideoIngestEngine.ingestStreamUrl(
                rawUrl = rawUrl,
                userFocusNotes = userFocusNotes,
                storageDir = appContext?.filesDir,
                onProgress = { statusMsg, pct ->
                    _studioState.update {
                        it.copy(
                            pipelineProgress = pct.coerceIn(0.12f, 0.40f),
                            pipelineStageLabel = statusMsg,
                            analysisStatusBanner = statusMsg
                        )
                    }
                }
            )
            ingestResult.onSuccess { ingested ->
                val resolvedSourceUri = ingested.downloadedLocalMp4Path.ifBlank { ingested.normalizedUrl }.take(500)
                val newProject = VideoProject(
                    userId = currentUserId,
                    title = ingested.title.take(120),
                    sourceFileName = "${ingested.platform.label} • ${ingested.authorOrChannel} (${ingested.externalId})".take(200),
                    sourceUri = resolvedSourceUri,
                    durationSec = ingested.durationSec.coerceIn(30L, 36000L),
                    width = ingested.width,
                    height = ingested.height,
                    transcriptText = ingested.extractedTranscriptOrContext.take(15000),
                    campaignId = campaign?.id.orEmpty(),
                    status = "READY",
                    clipCount = 0L
                )

                val createRes = repository.createProject(newProject)
                val newProjectId = createRes.getOrNull()?.takeIf { it.isNotBlank() }
                    ?: "local_proj_${System.currentTimeMillis()}"
                val savedProject = newProject.copy(id = newProjectId, userId = currentUserId)
                val extractedSceneFrames = ingested.sceneFrames.ifEmpty {
                    listOfNotNull(ingested.thumbnailBitmap)
                }

                _studioState.update { st ->
                    val updatedFrames = if (extractedSceneFrames.isNotEmpty()) {
                        st.uploadedPreviewFrames + (newProjectId to extractedSceneFrames)
                    } else {
                        st.uploadedPreviewFrames
                    }
                    st.copy(
                        isIngestingUrl = false,
                        localProjects = listOf(savedProject) + st.localProjects.filterNot { it.id == newProjectId },
                        pipelineProgress = if (autoFindViralClips) 0.42f else 1f,
                        pipelineStageLabel = if (autoFindViralClips) {
                            "Step 3/4: Launching AI viral highlight detector (>20s clips)..."
                        } else {
                            "Completed link import (100%)"
                        },
                        selectedProjectId = newProjectId,
                        uploadedPreviewFrames = updatedFrames,
                        singleClipPreviewFrame = extractedSceneFrames.firstOrNull() ?: st.singleClipPreviewFrame,
                        analysisStatusBanner = "Ingested ${ingested.platform.label} '${ingested.title}' (${ingested.extractionSourceLabel}, ${extractedSceneFrames.size} video scene frames)."
                    )
                }

                if (autoFindViralClips) {
                    runAiHighlightPipeline(
                        project = savedProject,
                        campaign = campaign,
                        onCompleteNavigateToReview = onNavigateToReview
                    )
                }
            }.onFailure { err ->
                _studioState.update {
                    it.copy(
                        isIngestingUrl = false,
                        pipelineProgress = 0f,
                        pipelineStageLabel = "",
                        analysisStatusBanner = null,
                        errorBanner = err.localizedMessage ?: "Could not ingest YouTube/Twitch URL."
                    )
                }
            }
        }
    }

    fun dismissBanners() {
        _studioState.update {
            it.copy(
                errorBanner = null,
                analysisStatusBanner = null,
                lastExportSummary = null
            )
        }
    }

    fun updateQuotaSettings(softTokenLimit: Long, requestWarnCount: Int, maxRetries: Int, customUrl: String) {
        _studioState.update { state ->
            val updatedProviders = state.providers.map { prov ->
                if (prov.id == "custom_remote_worker") {
                    prov.copy(
                        modelOrEndpoint = customUrl.ifBlank { "https://your-worker.example.com/health" },
                        verificationState = ProviderVerificationState.UNTESTED,
                        lastTestMessage = "Endpoint updated — run live test to verify."
                    )
                } else prov
            }
            state.copy(
                dailyTokenSoftLimit = softTokenLimit.coerceIn(1000L, 1_000_000L),
                dailyRequestWarnThreshold = requestWarnCount.coerceIn(1, 500),
                maxRetryCount = maxRetries.coerceIn(0, 5),
                customWebhookUrl = customUrl.trim(),
                providers = updatedProviders
            )
        }
    }

    fun uploadVideoFromUri(
        context: Context,
        uri: Uri,
        customTitle: String,
        transcriptNotes: String,
        campaignId: String
    ) {
        viewModelScope.launch {
            _studioState.update { it.copy(analysisStatusBanner = "Extracting real video metadata & keyframes on-device...", errorBanner = null) }
            try {
                val meta = VideoProcessingPipeline.inspectLocalVideo(context, uri)
                val projectTitle = customTitle.ifBlank {
                    meta.fileName.substringBeforeLast(".").replace("_", " ").replaceFirstChar { it.uppercase() }
                }
                val defaultTranscript = transcriptNotes.ifBlank {
                    buildDefaultTranscriptForDuration(projectTitle, meta.durationSec)
                }

                val newProj = VideoProject(
                    userId = currentUserId,
                    title = projectTitle,
                    sourceFileName = meta.fileName,
                    sourceUri = uri.toString(),
                    durationSec = meta.durationSec.coerceAtLeast(30L),
                    width = meta.width,
                    height = meta.height,
                    transcriptText = defaultTranscript,
                    campaignId = campaignId,
                    status = "READY",
                    clipCount = 0L
                )
                val result = repository.createProject(newProj)
                val newId = result.getOrNull()?.takeIf { it.isNotBlank() } ?: "local_proj_${System.currentTimeMillis()}"
                val savedProj = newProj.copy(id = newId, userId = currentUserId)
                _studioState.update { state ->
                    val updatedFrames = if (meta.sampleFrames.isNotEmpty()) {
                        state.uploadedPreviewFrames + (newId to meta.sampleFrames)
                    } else state.uploadedPreviewFrames
                    state.copy(
                        selectedProjectId = newId,
                        localProjects = listOf(savedProj) + state.localProjects.filterNot { it.id == newId },
                        uploadedPreviewFrames = updatedFrames,
                        singleClipPreviewFrame = meta.sampleFrames.firstOrNull() ?: state.singleClipPreviewFrame,
                        analysisStatusBanner = "Imported '${meta.fileName}' (${meta.width}x${meta.height}, ${meta.durationSec}s, ${meta.sampleFrames.size} keyframes extracted)."
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _studioState.update { it.copy(errorBanner = e.localizedMessage ?: "Failed to inspect video", analysisStatusBanner = null) }
            }
        }
    }

    fun createSampleProjectWithTranscript(selectedCampaignId: String) {
        viewModelScope.launch {
            val sampleTranscript = """
                [00:05] Host: Welcome back to the Championship Finals! Look at that crazy rush on A-site!
                [00:18] Player 1 (Funny Moment): Bro I just threw my smoke grenade at a pigeon instead of heaven, I'm actually cooked!
                [00:44] Caster (Clutch Play): It's a 1v4 in overtime! He hits the flick on the first, dodges the flash, double headshot through the smoke—UNBELIEVABLE 1v4 CLUTCH!
                [01:22] Streamer (High Reaction): WAIT WHAT?! NO WAY WE JUST PULLED A 0.001% MYTHIC DROP LIVE ON STREAM! CHAT CLIP THAT RIGHT NOW!
                [02:05] Guest (Memorable Dialogue): If you want to win creator bounties consistently, stop editing for 16:9 monitors. 94% of viewers watch on a vertical phone screen with the first 2 seconds deciding everything.
                [02:48] Caster (Gaming Highlight): Pixel-perfect movement across the gap, zero health remaining, and locks in the world record pace!
            """.trimIndent()

            val sampleProj = VideoProject(
                userId = currentUserId,
                title = "Pro Finals & Streamer Breakdown VOD",
                sourceFileName = "finals_championship_1080p60.mp4",
                sourceUri = "",
                durationSec = 210L,
                width = 1920L,
                height = 1080L,
                transcriptText = sampleTranscript,
                campaignId = selectedCampaignId,
                status = "READY",
                clipCount = 0L
            )
            val result = repository.createProject(sampleProj)
            val id = result.getOrNull()?.takeIf { it.isNotBlank() } ?: "local_proj_${System.currentTimeMillis()}"
            val savedSample = sampleProj.copy(id = id, userId = currentUserId)
            _studioState.update {
                it.copy(
                    selectedProjectId = id,
                    localProjects = listOf(savedSample) + it.localProjects.filterNot { p -> p.id == id },
                    analysisStatusBanner = "Created sample VOD project with timecoded transcript. Ready for AI Highlight Detection."
                )
            }
        }
    }

    fun deleteProject(projectId: String) {
        viewModelScope.launch {
            if (!projectId.startsWith("local_proj_")) {
                repository.deleteProject(projectId)
            }
            _studioState.update {
                it.copy(
                    selectedProjectId = if (it.selectedProjectId == projectId) null else it.selectedProjectId,
                    localProjects = it.localProjects.filterNot { p -> p.id == projectId },
                    localClips = it.localClips.filterNot { c -> c.projectId == projectId }
                )
            }
        }
    }

    fun saveWhopCampaign(campaign: WhopCampaign) {
        viewModelScope.launch {
            // 1. Always save locally on-device first so campaign rules & custom logo path are persisted immediately for validation
            val locallySaved = if (appContext != null) {
                LocalWhopCampaignStore.saveCampaignLocally(appContext, currentUserId, campaign)
            } else {
                campaign
            }

            // 2. Sync to Firestore and update local ID mapping if cloud succeeds
            val res = repository.createCampaign(locallySaved)
            res.onSuccess { cloudId ->
                val syncedCampaign = locallySaved.copy(id = cloudId, userId = currentUserId)
                val updatedLocalList = if (appContext != null) {
                    LocalWhopCampaignStore.deleteCampaignLocally(appContext, locallySaved.id)
                    LocalWhopCampaignStore.saveCampaignLocally(appContext, currentUserId, syncedCampaign)
                    LocalWhopCampaignStore.loadLocalCampaigns(appContext, currentUserId)
                } else {
                    listOf(syncedCampaign)
                }
                _studioState.update {
                    it.copy(
                        selectedCampaignId = cloudId,
                        localCampaigns = updatedLocalList,
                        analysisStatusBanner = "Saved Whop Campaign '${campaign.name}' locally (${campaign.preferredAspectRatio}) & synced to cloud."
                    )
                }
            }.onFailure { err ->
                val updatedLocalList = if (appContext != null) {
                    LocalWhopCampaignStore.loadLocalCampaigns(appContext, currentUserId)
                } else {
                    listOf(locallySaved)
                }
                _studioState.update {
                    it.copy(
                        selectedCampaignId = locallySaved.id,
                        localCampaigns = updatedLocalList,
                        analysisStatusBanner = "Saved Whop Campaign '${campaign.name}' locally on-device for validation (cloud sync pending: ${err.localizedMessage ?: "offline"})."
                    )
                }
            }
        }
    }

    fun createStarterWhopCampaign() {
        saveWhopCampaign(
            WhopCampaign(
                name = "Whop Viral Gaming & Creator Bounty",
                brandName = "Whop Creators Pro",
                payoutPer1kViews = "$2.00 / 1K views",
                targetPlatform = "TIKTOK",
                preferredAspectRatio = "9:16",
                minDurationSec = 22L,
                maxDurationSec = 55L,
                logoPlacement = "TOP_RIGHT",
                requiredLogoAsset = "whop_partner_badge.png",
                safeZoneTopPct = 12L,
                safeZoneBottomPct = 22L,
                requiredHashtags = "#whop #clipping #gaming",
                mandatoryCaptionCta = "Join the bounty via link in bio",
                seoGuidance = "Front-load game or reaction keyword in the first 40 characters",
                minConfidenceScore = 78L
            )
        )
    }

    fun deleteCampaign(campaignId: String) {
        viewModelScope.launch {
            if (appContext != null) {
                LocalWhopCampaignStore.deleteCampaignLocally(appContext, campaignId)
            }
            repository.deleteCampaign(campaignId)
            val refreshedLocal = appContext?.let {
                LocalWhopCampaignStore.loadLocalCampaigns(it, currentUserId)
            }.orEmpty()
            _studioState.update {
                it.copy(
                    selectedCampaignId = if (it.selectedCampaignId == campaignId) null else it.selectedCampaignId,
                    localCampaigns = refreshedLocal
                )
            }
        }
    }

    /**
     * Saves a reusable Campaign Preset (rules, hashtags, aspect ratio, caption style,
     * captions ON/OFF, and default clip count) locally and activates it immediately.
     */
    fun saveCampaignPreset(preset: CampaignPreset) {
        viewModelScope.launch {
            val updatedPresets = if (appContext != null) {
                LocalWhopCampaignStore.saveCampaignPreset(appContext, currentUserId, preset)
            } else {
                listOf(preset) + _studioState.value.campaignPresets.filterNot { it.id == preset.id }
            }
            val savedPreset = updatedPresets.firstOrNull {
                it.presetName.equals(preset.presetName, ignoreCase = true) || it.id == preset.id
            } ?: preset

            _studioState.update {
                it.copy(
                    campaignPresets = updatedPresets,
                    activePresetId = savedPreset.id,
                    viralCaptionStyle = savedPreset.viralCaptionStyle,
                    includeVideoCaptions = savedPreset.includeVideoCaptions,
                    desiredClipCount = savedPreset.defaultClipCount.coerceIn(1, 10),
                    analysisStatusBanner = "Saved Campaign Preset '${savedPreset.presetName}' (${savedPreset.preferredAspectRatio}, ${savedPreset.viralCaptionStyle.replace("_", " ")}, ${savedPreset.defaultClipCount} clips)."
                )
            }
            // Also persist the preset's campaign rules so new projects immediately validate against them
            saveWhopCampaign(savedPreset.toWhopCampaign())
        }
    }

    /**
     * Loads and applies a Campaign Preset in 1 tap: sets recurring Whop rules, hashtags,
     * aspect ratio, viral caption style, video captions ON/OFF, and clip count without manual entry.
     */
    fun applyCampaignPreset(preset: CampaignPreset) {
        viewModelScope.launch {
            _studioState.update {
                it.copy(
                    activePresetId = preset.id,
                    viralCaptionStyle = preset.viralCaptionStyle,
                    includeVideoCaptions = preset.includeVideoCaptions,
                    desiredClipCount = preset.defaultClipCount.coerceIn(1, 10),
                    analysisStatusBanner = "Loaded Preset '${preset.presetName}' — applied ${preset.preferredAspectRatio} rules, ${preset.viralCaptionStyle.replace("_", " ")} captions (${if (preset.includeVideoCaptions) "ON" else "OFF"}), ${preset.defaultClipCount} clips & hashtags (${preset.requiredHashtags})."
                )
            }
            saveWhopCampaign(preset.toWhopCampaign())
        }
    }

    fun deleteCampaignPreset(presetId: String) {
        viewModelScope.launch {
            val updated = if (appContext != null) {
                LocalWhopCampaignStore.deleteCampaignPreset(appContext, currentUserId, presetId)
            } else {
                _studioState.value.campaignPresets.filterNot { it.id == presetId }
            }
            _studioState.update {
                it.copy(
                    campaignPresets = updated,
                    activePresetId = if (it.activePresetId == presetId) null else it.activePresetId
                )
            }
        }
    }

    fun runAiHighlightPipeline(
        project: VideoProject,
        campaign: WhopCampaign?,
        onCompleteNavigateToReview: () -> Unit = {}
    ) {
        if (_studioState.value.isAnalyzingHighlights) return
        viewModelScope.launch {
            val stateSnap = _studioState.value
            val frames = stateSnap.uploadedPreviewFrames[project.id].orEmpty()
            val desiredCount = stateSnap.desiredClipCount.coerceIn(1, 10)
            val currentLogs = (apiLogsState.value as? DataStreamState.Success)?.data.orEmpty()
            val totalTokensUsed = currentLogs.sumOf { it.estimatedTokens }
            val isOverSoftLimit = totalTokensUsed >= stateSnap.dailyTokenSoftLimit

            _studioState.update {
                it.copy(
                    isAnalyzingHighlights = true,
                    pipelineProgress = 0.45f,
                    pipelineStageLabel = "Step 3/4: Analyzing transcript & keyframes for $desiredCount viral clip(s)...",
                    analysisStatusBanner = if (isOverSoftLimit) {
                        "Token limit reached ($totalTokensUsed/${stateSnap.dailyTokenSoftLimit}) — auto-routing $desiredCount clip(s) to Gemini 3.5 Flash-Lite Economy Mode..."
                    } else {
                        "Running Firebase AI Logic streaming pipeline for $desiredCount clip(s) (${stateSnap.viralCaptionStyle} captions: ${if (stateSnap.includeVideoCaptions) "ON" else "OFF"})..."
                    },
                    errorBanner = null
                )
            }
            repository.updateProjectStatus(project.id, "ANALYZING", project.clipCount, campaign?.id)

            try {
                val outcome = aiEngine.detectHighlights(
                    project = project,
                    campaign = campaign,
                    sampleFrames = frames,
                    desiredClipCount = desiredCount,
                    includeVideoCaptions = stateSnap.includeVideoCaptions,
                    viralCaptionStyle = stateSnap.viralCaptionStyle,
                    maxRetries = stateSnap.maxRetryCount.coerceAtLeast(1),
                    allowBackupModelFailover = true,
                    economyTokenMode = isOverSoftLimit,
                    onProgressUpdate = { progressText, progressFraction ->
                        _studioState.update { st ->
                            st.copy(
                                pipelineProgress = progressFraction.coerceIn(0.45f, 0.93f),
                                pipelineStageLabel = progressText,
                                analysisStatusBanner = progressText
                            )
                        }
                    }
                )

                _studioState.update {
                    it.copy(
                        pipelineProgress = 0.95f,
                        pipelineStageLabel = "Step 4/4: Saving ${outcome.clips.size} viral clips & SEO metadata..."
                    )
                }

                val savedClips = persistClipsBatchWithImmediateState(
                    candidates = outcome.clips,
                    project = project,
                    campaign = campaign
                )
                val firstClipId = savedClips.firstOrNull()?.id

                repository.updateProjectStatus(
                    projectId = project.id,
                    status = "CLIPS_GENERATED",
                    clipCount = savedClips.size.toLong(),
                    campaignId = campaign?.id
                )

                repository.recordApiUsage(
                    ApiUsageLog(
                        providerId = outcome.providerId,
                        modelName = outcome.modelUsed,
                        operation = "detect_highlights (${savedClips.size} clips)",
                        latencyMs = outcome.latencyMs,
                        estimatedTokens = outcome.estimatedTokens,
                        retryCount = outcome.retryCount.toLong(),
                        success = true,
                        errorMessage = ""
                    )
                )

                _studioState.update { st ->
                    val updatedProviders = st.providers.map { prov ->
                        if (prov.id == outcome.providerId) {
                            prov.copy(
                                verificationState = ProviderVerificationState.VERIFIED_WORKING,
                                lastTestedLatencyMs = outcome.latencyMs,
                                lastTestMessage = "Active pipeline succeeded in ${outcome.latencyMs}ms."
                            )
                        } else prov
                    }
                    st.copy(
                        isAnalyzingHighlights = false,
                        pipelineProgress = 1.0f,
                        pipelineStageLabel = "Complete (100%) — ${savedClips.size} viral clip(s) (>20s each) ready!",
                        selectedProjectId = project.id,
                        selectedClipId = firstClipId ?: st.selectedClipId,
                        providers = updatedProviders,
                        analysisStatusBanner = "Generated ${savedClips.size} viral clip(s) (>20s each) via ${outcome.modelUsed} (${outcome.latencyMs}ms, ~${outcome.estimatedTokens} tokens)."
                    )
                }
                onCompleteNavigateToReview()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Cloud AI pipeline unavailable (${e.localizedMessage}); auto-failing over to On-Device Viral Clipper", e)
                repository.recordApiUsage(
                    ApiUsageLog(
                        providerId = "firebase_ai_primary",
                        modelName = "gemini-3.8-flash",
                        operation = "detect_highlights_auto_failover ($desiredCount clips)",
                        latencyMs = 0L,
                        estimatedTokens = 0L,
                        retryCount = stateSnap.maxRetryCount.toLong(),
                        success = false,
                        errorMessage = e.localizedMessage ?: "Switched to On-Device Viral Clipper"
                    )
                )

                _studioState.update {
                    it.copy(
                        pipelineProgress = 0.78f,
                        pipelineStageLabel = "Step 4/4: Extracting $desiredCount viral clip(s) (>20s each) via On-Device Rule Engine...",
                        analysisStatusBanner = "Auto-switched to On-Device Viral Clipper — extracting $desiredCount clip(s) (>20s each) with ${stateSnap.viralCaptionStyle.replace("_", " ")} captions...",
                        errorBanner = null
                    )
                }

                val fallbackClips = WhopRuleValidator.extractClipsWithoutAiTokens(
                    project = project,
                    campaign = campaign,
                    desiredClipCount = desiredCount,
                    includeVideoCaptions = stateSnap.includeVideoCaptions,
                    viralCaptionStyle = stateSnap.viralCaptionStyle
                )

                val savedFallbackClips = persistClipsBatchWithImmediateState(
                    candidates = fallbackClips,
                    project = project,
                    campaign = campaign
                )
                val firstFallbackClipId = savedFallbackClips.firstOrNull()?.id

                repository.updateProjectStatus(
                    projectId = project.id,
                    status = "CLIPS_GENERATED",
                    clipCount = savedFallbackClips.size.toLong(),
                    campaignId = campaign?.id
                )

                _studioState.update { st ->
                    st.copy(
                        isAnalyzingHighlights = false,
                        pipelineProgress = 1.0f,
                        pipelineStageLabel = "Complete (100%) — ${savedFallbackClips.size} viral clip(s) (>20s each) ready!",
                        selectedProjectId = project.id,
                        selectedClipId = firstFallbackClipId ?: st.selectedClipId,
                        errorBanner = null,
                        analysisStatusBanner = "Generated ${savedFallbackClips.size} viral clip(s) (>20s each) for '${project.title}' with ${stateSnap.viralCaptionStyle.replace("_", " ")} captions."
                    )
                }
                onCompleteNavigateToReview()
            }
        }
    }

    /**
     * Deterministic 0-Token Local Clipper for when the user reaches their daily token limit
     * or wants instant local rule-based clipping without consuming any API quota.
     */
    fun runZeroTokenLocalClipper(
        project: VideoProject,
        campaign: WhopCampaign?,
        onCompleteNavigateToReview: () -> Unit = {}
    ) {
        if (_studioState.value.isAnalyzingHighlights) return
        viewModelScope.launch {
            val stateSnap = _studioState.value
            val desiredCount = stateSnap.desiredClipCount.coerceIn(1, 10)
            _studioState.update {
                it.copy(
                    isAnalyzingHighlights = true,
                    pipelineProgress = 0.35f,
                    pipelineStageLabel = "Scanning transcript timestamps ($desiredCount clips >20s, 0 API tokens)...",
                    analysisStatusBanner = "Running 0-Token Local Rule Clipper ($desiredCount clips >20s, 0 API tokens)...",
                    errorBanner = null
                )
            }
            delay(180L)
            _studioState.update {
                it.copy(
                    pipelineProgress = 0.72f,
                    pipelineStageLabel = "Applying ${stateSnap.viralCaptionStyle.replace("_", " ")} captions & Whop rules..."
                )
            }
            val generated = WhopRuleValidator.extractClipsWithoutAiTokens(
                project = project,
                campaign = campaign,
                desiredClipCount = desiredCount,
                includeVideoCaptions = stateSnap.includeVideoCaptions,
                viralCaptionStyle = stateSnap.viralCaptionStyle
            )
            val savedClips = persistClipsBatchWithImmediateState(
                candidates = generated,
                project = project,
                campaign = campaign
            )
            val firstClipId = savedClips.firstOrNull()?.id
            repository.updateProjectStatus(
                projectId = project.id,
                status = "CLIPS_GENERATED",
                clipCount = savedClips.size.toLong(),
                campaignId = campaign?.id
            )
            _studioState.update { st ->
                st.copy(
                    isAnalyzingHighlights = false,
                    pipelineProgress = 1.0f,
                    pipelineStageLabel = "Complete (100%) — ${savedClips.size} local clip(s) (>20s each) ready!",
                    selectedProjectId = project.id,
                    selectedClipId = firstClipId ?: st.selectedClipId,
                    analysisStatusBanner = "Generated ${savedClips.size} timestamped clip(s) (>20s each) for '${project.title}' via 0-Token Local Rule Engine."
                )
            }
            onCompleteNavigateToReview()
        }
    }

    private suspend fun persistClipsBatchWithImmediateState(
        candidates: List<ClipCandidate>,
        project: VideoProject,
        campaign: WhopCampaign?
    ): List<ClipCandidate> {
        val savedList = mutableListOf<ClipCandidate>()
        val newExtEntries = mutableMapOf<String, ClipExtendedConfig>()

        candidates.forEachIndexed { index, rawCandidate ->
            val enforcedEnd = if (rawCandidate.endSec - rawCandidate.startSec < 22L) {
                rawCandidate.startSec + 26L
            } else {
                rawCandidate.endSec
            }
            val candidate = rawCandidate.copy(
                projectId = project.id,
                userId = currentUserId,
                endSec = enforcedEnd
            )
            val savedRes = repository.createClip(candidate)
            val resolvedId = savedRes.getOrNull()?.takeIf { it.isNotBlank() }
                ?: "local_clip_${System.currentTimeMillis()}_${index + 1}"

            val ext = (candidate.extendedConfig ?: AutoFramingEngine.generateDefaultExtendedConfig(
                clip = candidate.copy(id = resolvedId),
                project = project,
                campaign = campaign
            )).copy(clipId = resolvedId, projectId = project.id)

            if (appContext != null) {
                LocalClipTimelineStore.saveExtendedConfig(appContext, ext)
            }
            newExtEntries[resolvedId] = ext
            savedList.add(candidate.copy(id = resolvedId, extendedConfig = ext))
        }

        _studioState.update { st ->
            val remainingLocalClips = st.localClips.filterNot { it.projectId == project.id }
            st.copy(
                localClips = savedList + remainingLocalClips,
                localExtendedConfigs = st.localExtendedConfigs + newExtEntries
            )
        }
        return savedList
    }

    fun updateClipCandidate(updatedClip: ClipCandidate) {
        viewModelScope.launch {
            val ensuredEnd = if (updatedClip.endSec - updatedClip.startSec < 22L) {
                updatedClip.startSec + 22L
            } else {
                updatedClip.endSec
            }
            val normalizedClip = updatedClip.copy(endSec = ensuredEnd)
            val ext = normalizedClip.extendedConfig
            if (ext != null) {
                saveExtendedConfigInternal(normalizedClip.id, ext)
            }
            _studioState.update { st ->
                val updatedLocal = st.localClips.map {
                    if (it.id == normalizedClip.id) normalizedClip else it
                }
                st.copy(localClips = updatedLocal)
            }
            if (!normalizedClip.id.startsWith("local_clip_")) {
                val res = repository.updateClip(normalizedClip)
                res.onFailure { err ->
                    _studioState.update { it.copy(errorBanner = err.localizedMessage ?: "Failed to update clip") }
                }
            }
        }
    }

    private fun saveExtendedConfigInternal(clipId: String, config: ClipExtendedConfig) {
        val normalized = config.copy(clipId = clipId)
        if (appContext != null) {
            LocalClipTimelineStore.saveExtendedConfig(appContext, normalized)
        }
        _studioState.update { st ->
            st.copy(localExtendedConfigs = st.localExtendedConfigs + (clipId to normalized))
        }
    }

    fun updateClipExtendedConfig(
        clip: ClipCandidate,
        updatedConfig: ClipExtendedConfig,
        syncLogoToFirestore: Boolean = true
    ) {
        saveExtendedConfigInternal(clip.id, updatedConfig)
        if (syncLogoToFirestore) {
            val resolvedLogo = if (updatedConfig.logoConfig.enabled) {
                updatedConfig.logoConfig.position
            } else {
                "NONE"
            }
            if (clip.logoPlacement != resolvedLogo) {
                updateClipCandidate(clip.copy(logoPlacement = resolvedLogo, extendedConfig = updatedConfig))
            }
        }
    }

    fun overrideTimelineSegment(
        clip: ClipCandidate,
        segmentId: String,
        newLayoutType: VerticalLayoutType? = null,
        newZoomLevel: Float? = null,
        newWebcamRect: NormalizedRect? = null,
        newContentRect: NormalizedRect? = null,
        newActiveSpeakerIds: List<Int>? = null
    ) {
        val currentExt = clip.extendedConfig ?: LocalClipTimelineStore.loadExtendedConfigForClip(appContext, clip)
        val updatedSegments = AutoFramingEngine.overrideSegment(
            segments = currentExt.segments,
            segmentId = segmentId,
            newLayoutType = newLayoutType,
            newZoomLevel = newZoomLevel,
            newWebcamRect = newWebcamRect,
            newContentRect = newContentRect,
            newActiveSpeakerIds = newActiveSpeakerIds
        )
        val updatedExt = currentExt.copy(segments = updatedSegments, checkpointStage = "TIMELINE_EDITED")
        saveExtendedConfigInternal(clip.id, updatedExt)
        _studioState.update {
            it.copy(
                analysisStatusBanner = "Updated timeline segment to ${newLayoutType?.shortBadge ?: "custom override"}."
            )
        }
    }

    fun splitTimelineSegment(clip: ClipCandidate, segmentId: String, splitSec: Long? = null) {
        val currentExt = clip.extendedConfig ?: LocalClipTimelineStore.loadExtendedConfigForClip(appContext, clip)
        val updatedSegments = AutoFramingEngine.splitSegmentAt(currentExt.segments, segmentId, splitSec)
        val updatedExt = currentExt.copy(segments = updatedSegments, checkpointStage = "TIMELINE_SPLIT")
        saveExtendedConfigInternal(clip.id, updatedExt)
        _studioState.update {
            it.copy(analysisStatusBanner = "Split layout segment into ${updatedSegments.size} segments.")
        }
    }

    fun mergeTimelineSegmentWithNext(clip: ClipCandidate, segmentId: String) {
        val currentExt = clip.extendedConfig ?: LocalClipTimelineStore.loadExtendedConfigForClip(appContext, clip)
        val updatedSegments = AutoFramingEngine.mergeSegmentWithNext(currentExt.segments, segmentId)
        val updatedExt = currentExt.copy(segments = updatedSegments, checkpointStage = "TIMELINE_MERGED")
        saveExtendedConfigInternal(clip.id, updatedExt)
        _studioState.update {
            it.copy(analysisStatusBanner = "Merged adjacent layout segments (${updatedSegments.size} remaining).")
        }
    }

    fun updateLogoPlacementToggle(
        clip: ClipCandidate,
        logoConfig: LogoPlacementConfig,
        activeCampaign: WhopCampaign? = null
    ) {
        val currentExt = clip.extendedConfig ?: LocalClipTimelineStore.loadExtendedConfigForClip(appContext, clip)
        val updatedExt = currentExt.copy(logoConfig = logoConfig)
        val newClipLogoField = if (logoConfig.enabled) logoConfig.position else "NONE"
        updateClipExtendedConfig(clip, updatedExt, syncLogoToFirestore = true)

        val conflictWarning = if (activeCampaign != null) {
            when {
                activeCampaign.logoPlacement != "NONE" && !logoConfig.enabled ->
                    "Warning: Campaign '${activeCampaign.name}' requires logo at ${activeCampaign.logoPlacement}, but Logo Placement is now OFF."
                activeCampaign.logoPlacement == "NONE" && logoConfig.enabled ->
                    "Warning: Campaign '${activeCampaign.name}' prohibits brand logos, but Logo Placement is now ON."
                activeCampaign.logoPlacement != "NONE" && logoConfig.position != activeCampaign.logoPlacement ->
                    "Warning: Campaign requires logo at ${activeCampaign.logoPlacement} (currently ${logoConfig.position})."
                else -> null
            }
        } else null

        _studioState.update {
            it.copy(
                analysisStatusBanner = conflictWarning
                    ?: "Logo Placement ${if (logoConfig.enabled) "ON ($newClipLogoField)" else "OFF — Full 9:16 layout space active"}."
            )
        }
    }

    fun updateHookCaptionToggle(
        clip: ClipCandidate,
        hookConfig: HookCaptionConfig
    ) {
        val currentExt = clip.extendedConfig ?: LocalClipTimelineStore.loadExtendedConfigForClip(appContext, clip)
        val updatedExt = currentExt.copy(hookConfig = hookConfig)
        saveExtendedConfigInternal(clip.id, updatedExt)
        _studioState.update {
            it.copy(
                analysisStatusBanner = if (hookConfig.enabled) {
                    "Hook Caption ON at ${hookConfig.position} (${hookConfig.durationSec}s) — independent of dialogue subtitles."
                } else {
                    "Hook Caption OFF — dialogue subtitles remain ${if (clip.subtitlesEnabled) "ON" else "OFF"}."
                }
            )
        }
    }

    fun updateExportQualitySettings(
        clip: ClipCandidate,
        exportConfig: ExportQualityConfig
    ) {
        val currentExt = clip.extendedConfig ?: LocalClipTimelineStore.loadExtendedConfigForClip(appContext, clip)
        val updatedExt = currentExt.copy(exportConfig = exportConfig)
        saveExtendedConfigInternal(clip.id, updatedExt)
    }

    fun renameClip(clip: ClipCandidate, newTitle: String) {
        val clean = newTitle.trim()
        if (clean.isEmpty()) return
        updateClipCandidate(clip.copy(title = clean))
        _studioState.update {
            it.copy(analysisStatusBanner = "Renamed clip to '$clean'.")
        }
    }

    fun deleteClip(clipId: String) {
        viewModelScope.launch {
            if (appContext != null) {
                LocalClipTimelineStore.deleteExtendedConfig(appContext, clipId)
            }
            if (!clipId.startsWith("local_clip_")) {
                repository.deleteClip(clipId)
            }
            _studioState.update { st ->
                st.copy(
                    selectedClipId = if (st.selectedClipId == clipId) null else st.selectedClipId,
                    localClips = st.localClips.filterNot { it.id == clipId },
                    localExtendedConfigs = st.localExtendedConfigs - clipId,
                    analysisStatusBanner = "Deleted clip from library."
                )
            }
        }
    }

    fun autoFixClipForCampaign(clip: ClipCandidate, campaign: WhopCampaign) {
        val fixed = WhopRuleValidator.autoFixClipForCampaign(clip, campaign)
        fixed.extendedConfig?.let { saveExtendedConfigInternal(fixed.id, it) }
        updateClipCandidate(fixed)
        _studioState.update {
            it.copy(analysisStatusBanner = "Applied Whop Campaign compliance fixes (Logo, Hook, CTA & Timeline) to '${clip.title}'.")
        }
    }

    fun exportApprovedClip(
        context: Context,
        clip: ClipCandidate,
        campaign: WhopCampaign?,
        project: VideoProject?
    ) {
        viewModelScope.launch {
            val allProjects = (projectsState.value as? DataStreamState.Success)?.data.orEmpty()
            val resolvedProject = allProjects.firstOrNull { it.id == clip.projectId } ?: project
            val ext = clip.extendedConfig ?: LocalClipTimelineStore.loadExtendedConfigForClip(context, clip, resolvedProject, campaign)
            val exportedClip = clip.copy(
                reviewStatus = "EXPORTED",
                logoPlacement = if (ext.logoConfig.enabled) ext.logoConfig.position else "NONE",
                extendedConfig = ext
            )
            updateClipCandidate(exportedClip)
            if (resolvedProject != null && !resolvedProject.id.startsWith("local_proj_")) {
                repository.updateProjectStatus(resolvedProject.id, "EXPORTED", resolvedProject.clipCount, campaign?.id)
            }

            val result = VideoProcessingPipeline.exportClipPackage(
                context = context,
                clip = exportedClip,
                campaign = campaign,
                sourceUriString = resolvedProject?.sourceUri.orEmpty(),
                sourceFileName = resolvedProject?.sourceFileName ?: "source_video.mp4"
            )

            when (result) {
                is ClipExportResult.NativeMp4AndManifestSuccess -> {
                    val updatedExt = ext.copy(
                        localRenderedMp4Path = result.mp4File.absolutePath,
                        checkpointStage = "EXPORTED_MP4",
                        lastRenderError = ""
                    )
                    saveExtendedConfigInternal(clip.id, updatedExt)
                    _studioState.update {
                        it.copy(
                            lastExportSummary = "EXPORTED WORKING MP4 CLIP (${result.mp4File.name}, ${result.mp4File.length() / 1024} KB) + SRT Subtitles (${result.srtFile.name}) + Dynamic Layout JSON Spec.",
                            lastExportFfmpegCommand = result.ffmpegCommand,
                            lastExportSrtPreview = result.srtFile.readText().take(500),
                            lastSavedMp4Path = result.mp4File.absolutePath
                        )
                    }
                }

                is ClipExportResult.ManifestOnlySuccess -> {
                    _studioState.update {
                        it.copy(
                            lastExportSummary = "EXPORTED SRT SUBTITLES (${result.srtFile.name}) + JSON RENDER SPEC (${result.manifestFile.name}). Note: ${result.reasonForNoMp4}",
                            lastExportFfmpegCommand = result.ffmpegCommand,
                            lastExportSrtPreview = result.srtFile.readText().take(500)
                        )
                    }
                }

                is ClipExportResult.Error -> {
                    val updatedExt = ext.copy(lastRenderError = result.message, checkpointStage = "EXPORT_FAILED")
                    saveExtendedConfigInternal(clip.id, updatedExt)
                    _studioState.update { it.copy(errorBanner = "Export error: ${result.message}") }
                }
            }
        }
    }

    /**
     * Renders and saves the finished >20s 9:16 vertical clip directly to the Android Gallery
     * (`Movies/ClipForgeAI`) and verifies file existence, size > 0, and valid duration.
     */
    fun saveClipToGallery(
        context: Context,
        clip: ClipCandidate,
        campaign: WhopCampaign?,
        project: VideoProject?
    ) {
        if (_studioState.value.isSavingToGallery) return
        viewModelScope.launch {
            val allProjects = (projectsState.value as? DataStreamState.Success)?.data.orEmpty()
            val resolvedProject = allProjects.firstOrNull { it.id == clip.projectId } ?: project
            val ensuredEnd = if (clip.endSec - clip.startSec < 22L) clip.startSec + 26L else clip.endSec
            val normalizedClip = clip.copy(endSec = ensuredEnd)
            val ext = normalizedClip.extendedConfig
                ?: LocalClipTimelineStore.loadExtendedConfigForClip(context, normalizedClip, resolvedProject, campaign)

            _studioState.update {
                it.copy(
                    isSavingToGallery = true,
                    analysisStatusBanner = "Rendering ${normalizedClip.durationSec}s 9:16 vertical video (${ext.exportConfig.resolution} @ ${ext.exportConfig.fps}fps) & saving to Android Gallery...",
                    errorBanner = null
                )
            }

            val projectFrames = resolvedProject?.id?.let { _studioState.value.uploadedPreviewFrames[it] }.orEmpty()
            val previewBmp = projectFrames.firstOrNull()
                ?: _studioState.value.singleClipPreviewFrame

            val saveRes = GalleryVideoSaver.saveClipToAndroidGallery(
                context = context,
                clip = normalizedClip.copy(extendedConfig = ext),
                extendedConfig = ext,
                campaign = campaign,
                sourceFrame = previewBmp,
                sourceFrames = projectFrames,
                sourceVideoUriOrPath = resolvedProject?.sourceUri.orEmpty()
            )

            when (saveRes) {
                is GallerySaveResult.Success -> {
                    val updatedExt = ext.copy(
                        savedGalleryUri = saveRes.galleryUriString,
                        savedGalleryPath = saveRes.folderPath,
                        localRenderedMp4Path = saveRes.localMp4File.absolutePath,
                        lastRenderError = "",
                        checkpointStage = "SAVED_TO_GALLERY"
                    )
                    saveExtendedConfigInternal(normalizedClip.id, updatedExt)
                    val exportedClip = normalizedClip.copy(
                        reviewStatus = "EXPORTED",
                        logoPlacement = if (ext.logoConfig.enabled) ext.logoConfig.position else "NONE",
                        extendedConfig = updatedExt
                    )
                    updateClipCandidate(exportedClip)

                    val ffmpegCmd = VideoProcessingPipeline.buildFfmpegWorkerCommand(
                        clip = exportedClip,
                        campaign = campaign,
                        sourceFileName = resolvedProject?.sourceFileName ?: "source_video.mp4",
                        sourceUriString = resolvedProject?.sourceUri.orEmpty()
                    )

                    _studioState.update {
                        it.copy(
                            isSavingToGallery = false,
                            lastGallerySaveMessage = "SAVED TO ANDROID GALLERY: ${saveRes.folderPath} (${saveRes.verification.detailMessage})",
                            lastSavedMp4Path = saveRes.localMp4File.absolutePath,
                            lastExportSummary = "Saved to Gallery: ${saveRes.folderPath} • Verified ${saveRes.sizeBytes} bytes, ${saveRes.durationSec}s duration.",
                            lastExportFfmpegCommand = ffmpegCmd,
                            analysisStatusBanner = "Saved ${saveRes.durationSec}s clip '${saveRes.fileName}' to Android Gallery (${saveRes.folderPath}) — Ready for TikTok, Reels & Shorts!"
                        )
                    }
                }

                is GallerySaveResult.Error -> {
                    val updatedExt = ext.copy(
                        lastRenderError = saveRes.reason,
                        checkpointStage = "GALLERY_SAVE_FAILED"
                    )
                    saveExtendedConfigInternal(normalizedClip.id, updatedExt)
                    _studioState.update {
                        it.copy(
                            isSavingToGallery = false,
                            errorBanner = "Gallery Save Failed: ${saveRes.reason}"
                        )
                    }
                }
            }
        }
    }

    fun testProviderLive(providerId: String) {
        if (_studioState.value.testingProviderId != null) return
        viewModelScope.launch {
            val customUrl = _studioState.value.customWebhookUrl
            _studioState.update { it.copy(testingProviderId = providerId, errorBanner = null) }

            val outcome = aiEngine.testProviderConnectivity(providerId, customUrl)
            val modelLabel = _studioState.value.providers.firstOrNull { it.id == providerId }?.modelOrEndpoint ?: providerId

            repository.recordApiUsage(
                ApiUsageLog(
                    providerId = providerId,
                    modelName = modelLabel.take(80),
                    operation = "live_connectivity_test",
                    latencyMs = outcome.latencyMs,
                    estimatedTokens = if (outcome.success) 24L else 0L,
                    retryCount = 0L,
                    success = outcome.success,
                    errorMessage = if (outcome.success) "" else outcome.message
                )
            )

            _studioState.update { state ->
                val updated = state.providers.map { prov ->
                    if (prov.id == providerId) {
                        prov.copy(
                            verificationState = if (outcome.success) {
                                ProviderVerificationState.VERIFIED_WORKING
                            } else {
                                ProviderVerificationState.FAILED
                            },
                            lastTestedLatencyMs = outcome.latencyMs,
                            lastTestMessage = outcome.message
                        )
                    } else prov
                }
                state.copy(
                    testingProviderId = null,
                    providers = updated,
                    analysisStatusBanner = if (outcome.success) {
                        "Provider '$providerId' verified LIVE (${outcome.latencyMs}ms)."
                    } else null,
                    errorBanner = if (!outcome.success) {
                        "Provider '$providerId' test failed: ${outcome.message}"
                    } else null
                )
            }
        }
    }

    private fun buildDefaultTranscriptForDuration(title: String, durationSec: Long): String {
        val t1 = (durationSec * 0.12).toLong().coerceAtLeast(2L)
        val t2 = (durationSec * 0.35).toLong().coerceAtLeast(10L)
        val t3 = (durationSec * 0.62).toLong().coerceAtLeast(20L)
        val t4 = (durationSec * 0.82).toLong().coerceAtLeast(30L)
        return """
            [${t1}s] Opening reaction and high-energy intro hook for $title.
            [${t2}s] Unexpected funny moment and rapid squad banter during intense play.
            [${t3}s] High-pressure clutch sequence and crowd/chat reaction spike.
            [${t4}s] Memorable closing takeaway and call to action.
        """.trimIndent()
    }
}
