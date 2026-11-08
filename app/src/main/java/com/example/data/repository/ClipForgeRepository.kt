package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.model.ApiUsageLog
import com.example.data.model.ClipCandidate
import com.example.data.model.VideoProject
import com.example.data.model.WhopCampaign
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class ClipForgeRepository(private val db: FirebaseFirestore) {

    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private val auth get() = Firebase.auth

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
    }

    // --- Video Projects ---

    fun observeProjects(userId: String = requireUserId()): Flow<List<VideoProject>> = flow {
        val path = "users/$userId/projects"
        emitAll(
            db.collection("users").document(userId).collection("projects")
                .whereEqualTo("userId", userId)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        VideoProject::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
                }
                .catch { error ->
                    if (error is Exception) handleFirestoreError(error, OperationType.LIST, path)
                    throw error
                }
        )
    }

    suspend fun createProject(project: VideoProject): Result<String> = runCatching {
        val uid = requireUserId()
        val colRef = db.collection("users").document(uid).collection("projects")
        val docRef = if (project.id.isNotBlank()) colRef.document(project.id) else colRef.document()
        val payload = mapOf(
            "userId" to uid,
            "title" to project.title.take(120).ifBlank { "Untitled Project" },
            "sourceFileName" to project.sourceFileName.take(200).ifBlank { "source_video.mp4" },
            "sourceUri" to project.sourceUri.take(500),
            "durationSec" to project.durationSec.coerceIn(1L, 36000L),
            "width" to project.width.coerceIn(0L, 7680L),
            "height" to project.height.coerceIn(0L, 7680L),
            "transcriptText" to project.transcriptText.take(15000),
            "campaignId" to project.campaignId.take(128),
            "status" to project.status,
            "clipCount" to project.clipCount.coerceIn(0L, 200L),
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        try {
            docRef.set(payload).await()
            docRef.id
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            throw e
        }
    }

    suspend fun getProjectById(ownerId: String, projectId: String): Result<VideoProject?> = runCatching {
        val docRef = db.collection("users").document(ownerId).collection("projects").document(projectId)
        try {
            val snap = docRef.get().await()
            snap.toObject(VideoProject::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, docRef.path)
            throw e
        }
    }

    suspend fun updateProjectStatus(projectId: String, status: String, clipCount: Long, campaignId: String? = null): Result<Unit> = runCatching {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid).collection("projects").document(projectId)
        val updates = mutableMapOf<String, Any>(
            "status" to status,
            "clipCount" to clipCount.coerceIn(0L, 200L),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        if (campaignId != null) {
            updates["campaignId"] = campaignId.take(128)
        }
        try {
            docRef.update(updates).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            throw e
        }
    }

    suspend fun deleteProject(projectId: String): Result<Unit> = runCatching {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid).collection("projects").document(projectId)
        try {
            docRef.delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            throw e
        }
    }

    // --- Whop Campaigns ---

    fun observeCampaigns(userId: String = requireUserId()): Flow<List<WhopCampaign>> = flow {
        val path = "users/$userId/campaigns"
        emitAll(
            db.collection("users").document(userId).collection("campaigns")
                .whereEqualTo("userId", userId)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        WhopCampaign::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
                }
                .catch { error ->
                    if (error is Exception) handleFirestoreError(error, OperationType.LIST, path)
                    throw error
                }
        )
    }

    suspend fun createCampaign(campaign: WhopCampaign): Result<String> = runCatching {
        val uid = requireUserId()
        val colRef = db.collection("users").document(uid).collection("campaigns")
        val docRef = if (campaign.id.isNotBlank()) colRef.document(campaign.id) else colRef.document()
        val minDur = campaign.minDurationSec.coerceIn(3L, 600L)
        val maxDur = campaign.maxDurationSec.coerceIn(minDur.coerceAtLeast(5L), 600L)
        val payload = mapOf(
            "userId" to uid,
            "name" to campaign.name.take(120).ifBlank { "Whop Campaign" },
            "brandName" to campaign.brandName.take(100).ifBlank { "Creator Brand" },
            "payoutPer1kViews" to campaign.payoutPer1kViews.take(40).ifBlank { "$1.50 / 1K views" },
            "targetPlatform" to campaign.targetPlatform,
            "minDurationSec" to minDur,
            "maxDurationSec" to maxDur,
            "logoPlacement" to campaign.logoPlacement,
            "requiredLogoAsset" to campaign.requiredLogoAsset.take(150),
            "safeZoneTopPct" to campaign.safeZoneTopPct.coerceIn(0L, 40L),
            "safeZoneBottomPct" to campaign.safeZoneBottomPct.coerceIn(0L, 40L),
            "requiredHashtags" to campaign.requiredHashtags.take(300),
            "mandatoryCaptionCta" to campaign.mandatoryCaptionCta.take(200),
            "seoGuidance" to campaign.seoGuidance.take(500),
            "minConfidenceScore" to campaign.minConfidenceScore.coerceIn(0L, 100L),
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        try {
            docRef.set(payload).await()
            docRef.id
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            throw e
        }
    }

    suspend fun deleteCampaign(campaignId: String): Result<Unit> = runCatching {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid).collection("campaigns").document(campaignId)
        try {
            docRef.delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            throw e
        }
    }

    // --- Clip Candidates ---

    fun observeClips(userId: String = requireUserId()): Flow<List<ClipCandidate>> = flow {
        val path = "users/$userId/clips"
        emitAll(
            db.collection("users").document(userId).collection("clips")
                .whereEqualTo("userId", userId)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        ClipCandidate::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.confidenceScore }
                }
                .catch { error ->
                    if (error is Exception) handleFirestoreError(error, OperationType.LIST, path)
                    throw error
                }
        )
    }

    suspend fun createClip(clip: ClipCandidate): Result<String> = runCatching {
        val uid = requireUserId()
        val colRef = db.collection("users").document(uid).collection("clips")
        val docRef = if (clip.id.isNotBlank()) colRef.document(clip.id) else colRef.document()
        val start = clip.startSec.coerceIn(0L, 35999L)
        val end = clip.endSec.coerceIn(start + 1L, 36000L)
        val payload = mapOf(
            "userId" to uid,
            "projectId" to clip.projectId.take(128).ifBlank { "default_proj" },
            "campaignId" to clip.campaignId.take(128),
            "title" to clip.title.take(150).ifBlank { "Highlight Clip" },
            "category" to clip.category,
            "reasonForSelection" to clip.reasonForSelection.take(600).ifBlank { "Detected high engagement segment." },
            "confidenceScore" to clip.confidenceScore.coerceIn(0L, 100L),
            "startSec" to start,
            "endSec" to end,
            "aspectRatio" to clip.aspectRatio,
            "reframeFocusX" to clip.reframeFocusX.coerceIn(0L, 100L),
            "subtitlesEnabled" to clip.subtitlesEnabled,
            "subtitleText" to clip.subtitleText.take(1000),
            "captionText" to clip.captionText.take(1000),
            "hashtags" to clip.hashtags.take(400),
            "seoTitle" to clip.seoTitle.take(200),
            "seoKeywords" to clip.seoKeywords.take(300),
            "logoPlacement" to clip.logoPlacement,
            "reviewStatus" to clip.reviewStatus,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        try {
            docRef.set(payload).await()
            docRef.id
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            throw e
        }
    }

    suspend fun updateClip(clip: ClipCandidate): Result<Unit> = runCatching {
        val uid = requireUserId()
        require(clip.id.isNotBlank()) { "Clip ID must not be blank for update" }
        val docRef = db.collection("users").document(uid).collection("clips").document(clip.id)
        val start = clip.startSec.coerceIn(0L, 35999L)
        val end = clip.endSec.coerceIn(start + 1L, 36000L)
        val updates = mapOf(
            "campaignId" to clip.campaignId.take(128),
            "title" to clip.title.take(150).ifBlank { "Highlight Clip" },
            "category" to clip.category,
            "reasonForSelection" to clip.reasonForSelection.take(600).ifBlank { "Detected highlight." },
            "confidenceScore" to clip.confidenceScore.coerceIn(0L, 100L),
            "startSec" to start,
            "endSec" to end,
            "aspectRatio" to clip.aspectRatio,
            "reframeFocusX" to clip.reframeFocusX.coerceIn(0L, 100L),
            "subtitlesEnabled" to clip.subtitlesEnabled,
            "subtitleText" to clip.subtitleText.take(1000),
            "captionText" to clip.captionText.take(1000),
            "hashtags" to clip.hashtags.take(400),
            "seoTitle" to clip.seoTitle.take(200),
            "seoKeywords" to clip.seoKeywords.take(300),
            "logoPlacement" to clip.logoPlacement,
            "reviewStatus" to clip.reviewStatus,
            "updatedAt" to FieldValue.serverTimestamp()
        )
        try {
            docRef.update(updates).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            throw e
        }
    }

    suspend fun deleteClip(clipId: String): Result<Unit> = runCatching {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid).collection("clips").document(clipId)
        try {
            docRef.delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            throw e
        }
    }

    // --- API Usage Logs ---

    fun observeApiLogs(userId: String = requireUserId()): Flow<List<ApiUsageLog>> = flow {
        val path = "users/$userId/api_logs"
        emitAll(
            db.collection("users").document(userId).collection("api_logs")
                .whereEqualTo("userId", userId)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        ApiUsageLog::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
                }
                .catch { error ->
                    if (error is Exception) handleFirestoreError(error, OperationType.LIST, path)
                    throw error
                }
        )
    }

    suspend fun recordApiUsage(log: ApiUsageLog): Result<String> = runCatching {
        val uid = requireUserId()
        val colRef = db.collection("users").document(uid).collection("api_logs")
        val docRef = colRef.document()
        val payload = mapOf(
            "userId" to uid,
            "providerId" to log.providerId.take(80).ifBlank { "firebase_ai" },
            "modelName" to log.modelName.take(80).ifBlank { "gemini-3.8-flash" },
            "operation" to log.operation.take(100).ifBlank { "highlight_detection" },
            "latencyMs" to log.latencyMs.coerceIn(0L, 300000L),
            "estimatedTokens" to log.estimatedTokens.coerceIn(0L, 1000000L),
            "retryCount" to log.retryCount.coerceIn(0L, 10L),
            "success" to log.success,
            "errorMessage" to log.errorMessage.take(500),
            "createdAt" to FieldValue.serverTimestamp()
        )
        try {
            docRef.set(payload).await()
            docRef.id
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            throw e
        }
    }
}
