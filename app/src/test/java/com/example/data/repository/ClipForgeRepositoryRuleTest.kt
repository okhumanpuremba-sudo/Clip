package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
import com.example.data.model.ApiUsageLog
import com.example.data.model.ClipCandidate
import com.example.data.model.VideoProject
import com.example.data.model.WhopCampaign
import com.google.firebase.firestore.FirebaseFirestoreException
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class ClipForgeRepositoryRuleTest : FirestoreEmulatorTestBase() {

    @Test
    fun createAndObserveProject_authenticatedOwner_succeeds() = runBlocking {
        val uid = signInTestUser(ALICE_EMAIL)
        val repository = ClipForgeRepository(firestore)
        val uniqueId = "proj_${UUID.randomUUID().toString().replace("-", "").take(12)}"

        val createResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.createProject(
                VideoProject(
                    id = uniqueId,
                    title = "Apex Finals VOD",
                    sourceFileName = "apex_finals.mp4",
                    durationSec = 900L,
                    transcriptText = "[01:15] Huge 1v3 clutch!"
                )
            )
        }
        assertTrue("Expected project creation success: ${createResult.exceptionOrNull()}", createResult.isSuccess)
        val createdId = createResult.getOrThrow()
        assertEquals(uniqueId, createdId)

        val emittedProjects = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observeProjects(uid).first { list -> list.any { it.id == createdId } }
        }
        assertTrue(emittedProjects.any { it.id == createdId && it.title == "Apex Finals VOD" })
    }

    @Test
    fun createCampaignClipAndLog_authenticatedOwner_succeeds() = runBlocking {
        val uid = signInTestUser(ALICE_EMAIL)
        val repository = ClipForgeRepository(firestore)

        val campResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.createCampaign(
                WhopCampaign(
                    name = "Whop Creator Bounty",
                    brandName = "ClipForge Studio",
                    minDurationSec = 15L,
                    maxDurationSec = 60L
                )
            )
        }
        assertTrue(campResult.isSuccess)
        val campId = campResult.getOrThrow()

        val clipResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.createClip(
                ClipCandidate(
                    projectId = "proj_test_1",
                    campaignId = campId,
                    title = "Unbelievable Final Circle Clutch",
                    category = "CLUTCH_PLAY",
                    reasonForSelection = "High vocal energy and game-winning play.",
                    confidenceScore = 95L,
                    startSec = 10L,
                    endSec = 42L
                )
            )
        }
        assertTrue(clipResult.isSuccess)

        val logResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.recordApiUsage(
                ApiUsageLog(
                    providerId = "firebase_ai_primary",
                    modelName = "gemini-3.8-flash",
                    operation = "detect_highlights",
                    latencyMs = 1200L,
                    estimatedTokens = 650L
                )
            )
        }
        assertTrue(logResult.isSuccess)

        val clips = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observeClips(uid).first { it.isNotEmpty() }
        }
        assertTrue(clips.any { it.id == clipResult.getOrThrow() })
    }

    @Test
    fun getProjectById_crossUserAccess_failsWithPermissionDenied() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val aliceRepo = ClipForgeRepository(firestore)
        val projectId = withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.createProject(
                VideoProject(
                    title = "Alice Private Stream",
                    sourceFileName = "private.mp4"
                )
            ).getOrThrow()
        }

        signInTestUser(BOB_EMAIL)
        val bobRepo = ClipForgeRepository(firestore)
        val bobReadResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            bobRepo.getProjectById(aliceUid, projectId)
        }
        assertTrue("Expected cross-user read to fail", bobReadResult.isFailure)
        val firestoreEx = bobReadResult.exceptionOrNull()?.unwrapFirestoreException()
        assertNotNull("Expected FirebaseFirestoreException", firestoreEx)
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreEx?.code)
    }

    @Test
    fun observeProjects_unauthenticatedUser_failsWithPermissionDenied() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        auth.signOut()
        val repository = ClipForgeRepository(firestore)

        var caughtException: Throwable? = null
        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                repository.observeProjects(aliceUid).first()
            }
            fail("Expected observeProjects to fail when unauthenticated")
        } catch (e: Exception) {
            caughtException = e
        }
        val firestoreException = checkNotNull(caughtException?.unwrapFirestoreException()) {
            "Expected nested FirebaseFirestoreException, got $caughtException"
        }
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreException.code)
    }

    private companion object {
        const val ALICE_EMAIL = "alice@clipforge.test"
        const val BOB_EMAIL = "bob@clipforge.test"
        const val DEFAULT_TIMEOUT_MS = 5000L
        const val FLOW_TIMEOUT_MS = 3000L
    }
}
