package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.model.ComplaintStatus
import com.example.data.model.NoiseComplaint
import com.example.data.model.NoiseLog
import com.example.data.model.NoiseSeverity
import com.example.data.model.NoiseSourceType
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class NoiseRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
    }

    fun observeComplaints(userId: String): Flow<List<NoiseComplaint>> = flow {
        val path = "users/$userId/complaints"
        emitAll(
            db.collection("users")
                .document(userId)
                .collection("complaints")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        NoiseComplaint::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observeNoiseLogs(userId: String): Flow<List<NoiseLog>> = flow {
        val path = "users/$userId/noiseLogs"
        emitAll(
            db.collection("users")
                .document(userId)
                .collection("noiseLogs")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        NoiseLog::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun createComplaint(
        reporterName: String,
        facilityName: String,
        sourceType: NoiseSourceType,
        measuredDb: Int,
        peakDb: Int,
        description: String
    ): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/complaints"
        return try {
            val uid = requireUserId()
            val cleanReporter = reporterName.trim().ifBlank { "Healthcare Staff / Citizen" }.take(80)
            val cleanFacility = facilityName.trim().take(120)
            val cleanDesc = description.trim().take(1000)
            require(cleanFacility.isNotEmpty()) { "ဆေးရုံ သို့မဟုတ် ဌာနအမည် ထည့်သွင်းပါ" }
            require(cleanDesc.isNotEmpty()) { "တိုင်ကြားလိုသည့် အကြောင်းအရာ ထည့်သွင်းပါ" }

            val clampedMeasured = measuredDb.coerceIn(0, 160).toLong()
            val clampedPeak = peakDb.coerceIn(0, 160).toLong()
            val severity = NoiseSeverity.fromDb(peakDb.coerceIn(0, 160))

            val docRef = db.collection("users").document(uid).collection("complaints").document()
            docPath = docRef.path
            val payload = mapOf(
                "userId" to uid,
                "reporterName" to cleanReporter,
                "facilityName" to cleanFacility,
                "sourceType" to sourceType.name,
                "measuredDb" to clampedMeasured,
                "peakDb" to clampedPeak,
                "severity" to severity.name,
                "status" to ComplaintStatus.SUBMITTED.name,
                "description" to cleanDesc,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun updateComplaintStatus(
        complaintId: String,
        newStatus: ComplaintStatus,
        authorityResponse: String?
    ): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/complaints/$complaintId"
        return try {
            val uid = requireUserId()
            require(complaintId.isNotBlank()) { "Complaint ID is required" }
            val docRef = db.collection("users").document(uid)
                .collection("complaints").document(complaintId)
            docPath = docRef.path
            val trimmedNote = authorityResponse?.trim()?.take(500)?.ifBlank { null }
            val updates = mutableMapOf<String, Any>(
                "status" to newStatus.name,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (trimmedNote != null) {
                updates["authorityResponse"] = trimmedNote
            }
            docRef.update(updates).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteComplaint(complaintId: String): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/complaints/$complaintId"
        return try {
            val uid = requireUserId()
            require(complaintId.isNotBlank()) { "Complaint ID is required" }
            val docRef = db.collection("users").document(uid)
                .collection("complaints").document(complaintId)
            docPath = docRef.path
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    suspend fun recordNoiseLog(
        zoneName: String,
        avgDb: Int,
        peakDb: Int,
        thresholdDb: Int,
        autoGuarded: Boolean
    ): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/noiseLogs"
        return try {
            val uid = requireUserId()
            val cleanZone = zoneName.trim().ifBlank { "Hospital Quiet Zone" }.take(100)
            val docRef = db.collection("users").document(uid).collection("noiseLogs").document()
            docPath = docRef.path
            val payload = mapOf(
                "userId" to uid,
                "zoneName" to cleanZone,
                "avgDb" to avgDb.coerceIn(0, 160).toLong(),
                "peakDb" to peakDb.coerceIn(0, 160).toLong(),
                "thresholdDb" to thresholdDb.coerceIn(20, 120).toLong(),
                "autoGuarded" to autoGuarded,
                "createdAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteNoiseLog(logId: String): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/noiseLogs/$logId"
        return try {
            val uid = requireUserId()
            require(logId.isNotBlank()) { "Log ID is required" }
            val docRef = db.collection("users").document(uid)
                .collection("noiseLogs").document(logId)
            docPath = docRef.path
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }
}
