package com.example.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.audio.DecibelMonitorEngine
import com.example.audio.SpeakerControlManager
import com.example.audio.SpeakerSnapshot
import com.example.data.model.ComplaintStatus
import com.example.data.model.NoiseComplaint
import com.example.data.model.NoiseLog
import com.example.data.model.NoiseSourceType
import com.example.data.model.QuietZonePreset
import com.example.data.model.UiState
import com.example.data.preferences.QuietPreferences
import com.example.data.preferences.QuietSettingsStore
import com.example.data.repository.NoiseRepository
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class LiveMonitorState(
    val isMonitoring: Boolean = false,
    val currentDb: Int = 0,
    val peakDb: Int = 0,
    val avgDb: Int = 0,
    val bars: List<Float> = List(24) { 0.08f },
    val isViolationActive: Boolean = false,
    val autoGuardTriggeredCount: Int = 0,
    val errorMessage: String? = null
)

class QuietCareViewModel(
    private val repository: NoiseRepository,
    private val settingsStore: QuietSettingsStore,
    private val speakerControlManager: SpeakerControlManager,
    private val decibelEngine: DecibelMonitorEngine,
    private val currentUserId: String,
    val currentUserDisplayName: String
) : ViewModel() {

    val preferences: StateFlow<QuietPreferences> = settingsStore.preferences
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = QuietPreferences()
        )

    val complaintsState: StateFlow<UiState<List<NoiseComplaint>>> =
        repository.observeComplaints(currentUserId)
            .map<List<NoiseComplaint>, UiState<List<NoiseComplaint>>> { UiState.Success(it) }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val rootError = firestoreError ?: error
                Log.w(TAG, "Error observing complaints", rootError)
                emit(UiState.Error(rootError.localizedMessage ?: "Failed to load complaints"))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = UiState.Loading
            )

    val noiseLogsState: StateFlow<UiState<List<NoiseLog>>> =
        repository.observeNoiseLogs(currentUserId)
            .map<List<NoiseLog>, UiState<List<NoiseLog>>> { UiState.Success(it) }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val rootError = firestoreError ?: error
                Log.w(TAG, "Error observing noise logs", rootError)
                emit(UiState.Error(rootError.localizedMessage ?: "Failed to load noise logs"))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = UiState.Loading
            )

    private val _monitorState = MutableStateFlow(LiveMonitorState())
    val monitorState: StateFlow<LiveMonitorState> = _monitorState.asStateFlow()

    private val _speakerSnapshot = MutableStateFlow(speakerControlManager.readSnapshot())
    val speakerSnapshot: StateFlow<SpeakerSnapshot> = _speakerSnapshot.asStateFlow()

    private val _bannerMessage = MutableStateFlow<String?>(null)
    val bannerMessage: StateFlow<String?> = _bannerMessage.asStateFlow()

    private var monitorJob: Job? = null
    private var sampleSum = 0L
    private var sampleCount = 0
    private var lastViolationLogMillis = 0L

    fun startMonitoring() {
        if (monitorJob?.isActive == true) return
        sampleSum = 0L
        sampleCount = 0
        _monitorState.value = LiveMonitorState(isMonitoring = true)

        monitorJob = viewModelScope.launch {
            decibelEngine.observeDecibels().collect { sample ->
                if (!sample.isActive) {
                    _monitorState.value = _monitorState.value.copy(
                        isMonitoring = false,
                        errorMessage = sample.errorMessage
                    )
                    return@collect
                }

                sampleSum += sample.currentDb
                sampleCount++
                val avg = (sampleSum / sampleCount).toInt()
                val prevPeak = _monitorState.value.peakDb
                val peak = maxOf(prevPeak, sample.currentDb)
                val prefs = preferences.value
                val violation = sample.currentDb >= prefs.thresholdDb

                var guardCount = _monitorState.value.autoGuardTriggeredCount
                if (violation) {
                    val now = System.currentTimeMillis()
                    var didAutoGuard = false
                    if (prefs.autoGuardEnabled) {
                        didAutoGuard = speakerControlManager.applyAutoGuardReduction()
                        if (didAutoGuard) {
                            guardCount++
                            refreshSpeakers()
                        }
                    }
                    if (prefs.autoLogViolations && (now - lastViolationLogMillis >= 20_000L)) {
                        lastViolationLogMillis = now
                        viewModelScope.launch {
                            repository.recordNoiseLog(
                                zoneName = prefs.zoneName,
                                avgDb = avg,
                                peakDb = peak,
                                thresholdDb = prefs.thresholdDb,
                                autoGuarded = didAutoGuard
                            )
                        }
                    }
                }

                _monitorState.value = _monitorState.value.copy(
                    isMonitoring = true,
                    currentDb = sample.currentDb,
                    peakDb = peak,
                    avgDb = avg,
                    bars = sample.bars,
                    isViolationActive = violation,
                    autoGuardTriggeredCount = guardCount,
                    errorMessage = null
                )
            }
        }
    }

    fun stopMonitoring() {
        monitorJob?.cancel()
        monitorJob = null
        _monitorState.value = _monitorState.value.copy(
            isMonitoring = false,
            bars = List(24) { 0.08f },
            isViolationActive = false
        )
    }

    fun captureCurrentNoiseSnapshot() {
        val current = _monitorState.value
        val prefs = preferences.value
        val avg = if (current.avgDb > 0) current.avgDb else current.currentDb
        val peak = if (current.peakDb > 0) current.peakDb else current.currentDb
        if (peak <= 0) {
            _bannerMessage.value = "တိုင်းတာချက်မရှိသေးပါ — မိုက်ခရိုဖုန်းဖြင့် အရင်တိုင်းတာပါ"
            return
        }
        viewModelScope.launch {
            val result = repository.recordNoiseLog(
                zoneName = prefs.zoneName,
                avgDb = avg,
                peakDb = peak,
                thresholdDb = prefs.thresholdDb,
                autoGuarded = prefs.autoGuardEnabled
            )
            _bannerMessage.value = result.fold(
                onSuccess = { "ဆူညံသံမှတ်တမ်း ($peak dB) ကို Cloud တွင် သိမ်းဆည်းပြီးပါပြီ" },
                onFailure = { err -> err.localizedMessage ?: "Failed to save log" }
            )
        }
    }

    fun deleteNoiseLog(logId: String) {
        viewModelScope.launch {
            repository.deleteNoiseLog(logId).onFailure { err ->
                _bannerMessage.value = err.localizedMessage ?: "Failed to delete log"
            }
        }
    }

    fun selectZonePreset(preset: QuietZonePreset) {
        viewModelScope.launch {
            settingsStore.setZonePreset(preset)
        }
    }

    fun updateCustomThreshold(db: Int) {
        viewModelScope.launch {
            settingsStore.setCustomThreshold(db)
        }
    }

    fun setAutoGuardEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsStore.setAutoGuardEnabled(enabled)
        }
    }

    fun setAutoLogViolations(enabled: Boolean) {
        viewModelScope.launch {
            settingsStore.setAutoLogViolations(enabled)
        }
    }

    fun refreshSpeakers() {
        _speakerSnapshot.value = speakerControlManager.readSnapshot()
    }

    fun setStreamVolume(streamType: Int, value: Int) {
        speakerControlManager.setStreamVolume(streamType, value)
        refreshSpeakers()
    }

    fun toggleMediaMute() {
        speakerControlManager.toggleMediaMute()
        refreshSpeakers()
    }

    fun routeToBuiltInSpeaker() {
        speakerControlManager.routeToBuiltInSpeaker()
        refreshSpeakers()
        _bannerMessage.value = "ဖုန်းအတွင်းစပီကာသို့ ပြန်လည်ချိတ်ဆက်ပေးလိုက်ပါပြီ"
    }

    fun openBluetoothSettings() {
        speakerControlManager.openSystemBluetoothSettings()
    }

    fun submitComplaint(
        reporterName: String,
        facilityName: String,
        sourceType: NoiseSourceType,
        measuredDb: Int,
        peakDb: Int,
        description: String,
        onSuccess: () -> Unit
    ) {
        viewModelScope.launch {
            settingsStore.setDefaultFacilityName(facilityName)
            val result = repository.createComplaint(
                reporterName = reporterName,
                facilityName = facilityName,
                sourceType = sourceType,
                measuredDb = measuredDb,
                peakDb = peakDb,
                description = description
            )
            result.fold(
                onSuccess = {
                    _bannerMessage.value = "တိုင်ကြားချက်ကို အောင်မြင်စွာ သိမ်းဆည်းတင်သွင်းပြီးပါပြီ"
                    onSuccess()
                },
                onFailure = { err ->
                    _bannerMessage.value = err.localizedMessage ?: "Failed to submit complaint"
                }
            )
        }
    }

    fun updateComplaintStatus(
        complaintId: String,
        newStatus: ComplaintStatus,
        authorityResponse: String?
    ) {
        viewModelScope.launch {
            val result = repository.updateComplaintStatus(complaintId, newStatus, authorityResponse)
            result.onFailure { err ->
                _bannerMessage.value = err.localizedMessage ?: "Failed to update complaint"
            }
        }
    }

    fun deleteComplaint(complaintId: String) {
        viewModelScope.launch {
            repository.deleteComplaint(complaintId).onFailure { err ->
                _bannerMessage.value = err.localizedMessage ?: "Failed to delete complaint"
            }
        }
    }

    fun clearBannerMessage() {
        _bannerMessage.value = null
    }

    override fun onCleared() {
        super.onCleared()
        monitorJob?.cancel()
    }

    private companion object {
        const val TAG = "QuietCareVM"
        const val STOP_TIMEOUT_MILLIS = 5000L
    }
}
