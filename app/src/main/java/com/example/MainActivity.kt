package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.RateReview
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.auth.authStateFlow
import com.example.auth.signOutUser
import com.example.data.repository.ClipForgeRepository
import com.example.ui.ClipForgeViewModel
import com.example.ui.DataStreamState
import com.example.ui.screens.ApiAndArchitectureScreen
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.ClipReviewStudioScreen
import com.example.ui.screens.DashboardAndProjectsScreen
import com.example.ui.screens.WhopCampaignScreen
import com.example.ui.theme.ClipForgeTheme
import com.example.ui.theme.ForgeAmber
import com.example.ui.theme.StudioBorder
import com.example.ui.theme.StudioObsidian
import com.example.ui.theme.StudioSurface
import com.example.ui.theme.TechnicalCyan
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.VerifiedEmerald
import com.example.ui.theme.WarningCoral
import com.example.util.configureAppCheck
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // STEP 1: Mandatory App Check configuration with launch intent before any UI or Firebase calls
        configureAppCheck(this, intent)

        enableEdgeToEdge()

        setContent {
            ClipForgeTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavigation()
                }
            }
        }
    }
}

enum class StudioDestination(val route: String, val label: String, val icon: ImageVector) {
    PROJECTS("projects", "Projects", Icons.Default.Dashboard),
    CAMPAIGNS("campaigns", "Whop Rules", Icons.Default.Campaign),
    REVIEW("review", "Review & 9:16", Icons.Default.RateReview),
    API_HEALTH("api_health", "API & System", Icons.Default.Analytics)
}

@Composable
fun AppNavigation(auth: FirebaseAuth = Firebase.auth) {
    val currentUser by auth.authStateFlow().collectAsStateWithLifecycle(initialValue = auth.currentUser)
    val user = currentUser

    if (user == null) {
        AuthScreen(onAuthSuccess = { })
    } else {
        ClipForgeMainShell(
            currentUserId = user.uid,
            userEmail = user.email ?: "Authenticated Creator"
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClipForgeMainShell(
    currentUserId: String,
    userEmail: String
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember { CredentialManager.create(context) }

    val viewModel: ClipForgeViewModel = viewModel(
        key = currentUserId,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) {
                    "APPLICATION_KEY missing from CreationExtras"
                }
                val databaseId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(databaseId)
                ClipForgeViewModel(
                    repository = ClipForgeRepository(db),
                    currentUserId = currentUserId,
                    appContext = app.applicationContext
                )
            }
        }
    )

    var currentTab by rememberSaveable { mutableStateOf(StudioDestination.PROJECTS) }

    if (currentTab != StudioDestination.PROJECTS) {
        BackHandler {
            currentTab = StudioDestination.PROJECTS
        }
    }

    val studioState by viewModel.studioState.collectAsStateWithLifecycle()
    val projectsStream by viewModel.projectsState.collectAsStateWithLifecycle()
    val campaignsStream by viewModel.campaignsState.collectAsStateWithLifecycle()
    val clipsStream by viewModel.clipsState.collectAsStateWithLifecycle()
    val apiLogsStream by viewModel.apiLogsState.collectAsStateWithLifecycle()

    val projects = (projectsStream as? DataStreamState.Success)?.data.orEmpty()
    val campaigns = (campaignsStream as? DataStreamState.Success)?.data.orEmpty()
    val clips = (clipsStream as? DataStreamState.Success)?.data.orEmpty()
    val apiLogs = (apiLogsStream as? DataStreamState.Success)?.data.orEmpty()

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isWideScreen = maxWidth >= 640.dp

        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            topBar = {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = StudioSurface,
                        titleContentColor = TextPrimary
                    ),
                    title = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCut,
                                contentDescription = null,
                                tint = ForgeAmber,
                                modifier = Modifier.size(22.dp)
                            )
                            Column {
                                Text(
                                    text = "CLIPFORGE AI",
                                    style = MaterialTheme.typography.titleLarge,
                                    color = TextPrimary
                                )
                                Text(
                                    text = userEmail,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextSecondary
                                )
                            }
                        }
                    },
                    actions = {
                        Surface(
                            color = VerifiedEmerald.copy(alpha = 0.15f),
                            border = BorderStroke(1.dp, VerifiedEmerald.copy(alpha = 0.6f)),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = "APP CHECK LIVE",
                                style = MaterialTheme.typography.labelSmall,
                                color = VerifiedEmerald,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        IconButton(
                            onClick = {
                                signOutUser(
                                    credentialManager = credentialManager,
                                    onSignOutComplete = { },
                                    scope = scope
                                )
                            },
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("sign_out_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Logout,
                                contentDescription = "Sign Out",
                                tint = TextSecondary
                            )
                        }
                    }
                )
            },
            bottomBar = {
                if (!isWideScreen) {
                    NavigationBar(
                        containerColor = StudioSurface,
                        tonalElevation = 0.dp
                    ) {
                        StudioDestination.entries.forEach { dest ->
                            NavigationBarItem(
                                selected = currentTab == dest,
                                onClick = { currentTab = dest },
                                icon = {
                                    Icon(
                                        imageVector = dest.icon,
                                        contentDescription = dest.label
                                    )
                                },
                                label = {
                                    Text(
                                        text = dest.label,
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = StudioObsidian,
                                    selectedTextColor = ForgeAmber,
                                    indicatorColor = ForgeAmber,
                                    unselectedIconColor = TextSecondary,
                                    unselectedTextColor = TextSecondary
                                ),
                                modifier = Modifier.testTag("nav_${dest.route}")
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .background(StudioObsidian)
                    .padding(innerPadding)
            ) {
                if (isWideScreen) {
                    NavigationRail(
                        containerColor = StudioSurface,
                        modifier = Modifier.fillMaxHeight()
                    ) {
                        StudioDestination.entries.forEach { dest ->
                            NavigationRailItem(
                                selected = currentTab == dest,
                                onClick = { currentTab = dest },
                                icon = { Icon(dest.icon, contentDescription = dest.label) },
                                label = { Text(dest.label, style = MaterialTheme.typography.labelSmall) },
                                modifier = Modifier.testTag("nav_rail_${dest.route}")
                            )
                        }
                    }
                }

                Column(modifier = Modifier.fillMaxSize()) {
                    // Status / Error Banner Strip with Live Progress Bar
                    AnimatedVisibility(visible = studioState.errorBanner != null || studioState.analysisStatusBanner != null) {
                        val isError = studioState.errorBanner != null
                        val isPipelineBusy = studioState.isIngestingUrl || studioState.isAnalyzingHighlights
                        val bannerColor = if (isError) WarningCoral else TechnicalCyan
                        Surface(
                            color = bannerColor.copy(alpha = 0.15f),
                            border = BorderStroke(1.dp, bannerColor),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 6.dp),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = studioState.errorBanner ?: studioState.analysisStatusBanner.orEmpty(),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = bannerColor,
                                        modifier = Modifier.weight(1f)
                                    )
                                    if (isPipelineBusy) {
                                        Text(
                                            text = "${(studioState.pipelineProgress * 100).toInt()}%",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = ForgeAmber,
                                            modifier = Modifier.padding(horizontal = 8.dp)
                                        )
                                    }
                                    TextButton(onClick = { viewModel.dismissBanners() }) {
                                        Text("DISMISS", style = MaterialTheme.typography.labelSmall, color = TextPrimary)
                                    }
                                }
                                if (isPipelineBusy) {
                                    LinearProgressIndicator(
                                        progress = { studioState.pipelineProgress.coerceIn(0.05f, 1f) },
                                        color = ForgeAmber,
                                        trackColor = StudioObsidian,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 4.dp)
                                            .testTag("global_pipeline_progress_bar")
                                    )
                                }
                            }
                        }
                    }

                    Box(modifier = Modifier.fillMaxSize()) {
                        when (currentTab) {
                            StudioDestination.PROJECTS -> DashboardAndProjectsScreen(
                                projects = projects,
                                campaigns = campaigns,
                                campaignPresets = studioState.campaignPresets,
                                activePresetId = studioState.activePresetId,
                                clips = clips,
                                selectedProjectId = studioState.selectedProjectId,
                                selectedCampaignId = studioState.selectedCampaignId,
                                desiredClipCount = studioState.desiredClipCount,
                                includeVideoCaptions = studioState.includeVideoCaptions,
                                viralCaptionStyle = studioState.viralCaptionStyle,
                                isIngestingUrl = studioState.isIngestingUrl,
                                isAnalyzing = studioState.isAnalyzingHighlights,
                                pipelineProgress = studioState.pipelineProgress,
                                pipelineStageLabel = studioState.pipelineStageLabel,
                                previewFrames = studioState.uploadedPreviewFrames,
                                onSelectProject = viewModel::selectProject,
                                onSelectCampaign = viewModel::selectCampaign,
                                onSetDesiredClipCount = viewModel::setDesiredClipCount,
                                onSetIncludeVideoCaptions = viewModel::setIncludeVideoCaptions,
                                onSetViralCaptionStyle = viewModel::setViralCaptionStyle,
                                onApplyCampaignPreset = viewModel::applyCampaignPreset,
                                onSaveCampaignPreset = viewModel::saveCampaignPreset,
                                onIngestStreamUrl = { url, notes, camp, autoFind ->
                                    viewModel.ingestYouTubeOrTwitchUrl(url, notes, camp, autoFind) {
                                        currentTab = StudioDestination.REVIEW
                                    }
                                },
                                onUploadVideoUri = { uri, title, transcript, campId ->
                                    viewModel.uploadVideoFromUri(context, uri, title, transcript, campId)
                                },
                                onCreateSampleProject = viewModel::createSampleProjectWithTranscript,
                                onDeleteProject = viewModel::deleteProject,
                                onRunAiDetection = { proj, camp ->
                                    viewModel.runAiHighlightPipeline(proj, camp) {
                                        currentTab = StudioDestination.REVIEW
                                    }
                                },
                                onRunZeroTokenLocalClipper = { proj, camp ->
                                    viewModel.runZeroTokenLocalClipper(proj, camp) {
                                        currentTab = StudioDestination.REVIEW
                                    }
                                },
                                onNavigateToCampaigns = { currentTab = StudioDestination.CAMPAIGNS },
                                onNavigateToReview = { currentTab = StudioDestination.REVIEW }
                            )

                            StudioDestination.CAMPAIGNS -> WhopCampaignScreen(
                                campaigns = campaigns,
                                campaignPresets = studioState.campaignPresets,
                                activePresetId = studioState.activePresetId,
                                selectedCampaignId = studioState.selectedCampaignId,
                                currentViralCaptionStyle = studioState.viralCaptionStyle,
                                currentIncludeVideoCaptions = studioState.includeVideoCaptions,
                                currentDesiredClipCount = studioState.desiredClipCount,
                                onSelectCampaign = viewModel::selectCampaign,
                                onSaveCampaign = viewModel::saveWhopCampaign,
                                onSaveCampaignPreset = viewModel::saveCampaignPreset,
                                onApplyCampaignPreset = viewModel::applyCampaignPreset,
                                onDeleteCampaignPreset = viewModel::deleteCampaignPreset,
                                onCreateStarterCampaign = viewModel::createStarterWhopCampaign,
                                onDeleteCampaign = viewModel::deleteCampaign
                            )

                            StudioDestination.REVIEW -> {
                                val fallbackThumb = studioState.selectedProjectId?.let {
                                    studioState.uploadedPreviewFrames[it]?.firstOrNull()
                                }
                                ClipReviewStudioScreen(
                                    clips = clips,
                                    projects = projects,
                                    campaigns = campaigns,
                                    selectedClipId = studioState.selectedClipId,
                                    selectedProjectId = studioState.selectedProjectId,
                                    selectedCampaignId = studioState.selectedCampaignId,
                                    viralCaptionStyle = studioState.viralCaptionStyle,
                                    onSetViralCaptionStyle = viewModel::setViralCaptionStyle,
                                    singlePreviewFrame = studioState.singleClipPreviewFrame ?: fallbackThumb,
                                    projectPreviewFramesMap = studioState.uploadedPreviewFrames,
                                    lastExportSummary = studioState.lastExportSummary,
                                    lastExportFfmpegCommand = studioState.lastExportFfmpegCommand,
                                    lastExportSrtPreview = studioState.lastExportSrtPreview,
                                    lastGallerySaveMessage = studioState.lastGallerySaveMessage,
                                    isSavingToGallery = studioState.isSavingToGallery,
                                    onSelectClip = { clipId, sourceUri, startSec ->
                                        viewModel.selectClip(clipId, context, sourceUri, startSec)
                                    },
                                    onUpdateClip = viewModel::updateClipCandidate,
                                    onAutoFixForCampaign = viewModel::autoFixClipForCampaign,
                                    onExportClip = { clip, camp, proj ->
                                        viewModel.exportApprovedClip(context, clip, camp, proj)
                                    },
                                    onOverrideSegment = { clip, segId, newLayout, newZoom, activeSpk ->
                                        viewModel.overrideTimelineSegment(
                                            clip = clip,
                                            segmentId = segId,
                                            newLayoutType = newLayout,
                                            newZoomLevel = newZoom,
                                            newActiveSpeakerIds = activeSpk
                                        )
                                    },
                                    onSplitSegment = { clip, segId ->
                                        viewModel.splitTimelineSegment(clip, segId)
                                    },
                                    onMergeSegmentWithNext = { clip, segId ->
                                        viewModel.mergeTimelineSegmentWithNext(clip, segId)
                                    },
                                    onUpdateLogoToggle = { clip, logoCfg, camp ->
                                        viewModel.updateLogoPlacementToggle(clip, logoCfg, camp)
                                    },
                                    onUpdateHookToggle = { clip, hookCfg ->
                                        viewModel.updateHookCaptionToggle(clip, hookCfg)
                                    },
                                    onUpdateExportConfig = { clip, exportCfg ->
                                        viewModel.updateExportQualitySettings(clip, exportCfg)
                                    },
                                    onSaveToGallery = { clip, camp, proj ->
                                        viewModel.saveClipToGallery(context, clip, camp, proj)
                                    },
                                    onRenameClip = { clip, newTitle ->
                                        viewModel.renameClip(clip, newTitle)
                                    },
                                    onDeleteClip = { clipId ->
                                        viewModel.deleteClip(clipId)
                                    }
                                )
                            }

                            StudioDestination.API_HEALTH -> ApiAndArchitectureScreen(
                                apiLogs = apiLogs,
                                providers = studioState.providers,
                                testingProviderId = studioState.testingProviderId,
                                dailyTokenSoftLimit = studioState.dailyTokenSoftLimit,
                                dailyRequestWarnThreshold = studioState.dailyRequestWarnThreshold,
                                maxRetryCount = studioState.maxRetryCount,
                                customWebhookUrl = studioState.customWebhookUrl,
                                onUpdateQuotaSettings = viewModel::updateQuotaSettings,
                                onTestProviderLive = viewModel::testProviderLive
                            )
                        }
                    }
                }
            }
        }
    }
}
