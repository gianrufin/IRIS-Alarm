package com.iris.alarm.ui.challenge

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.iris.alarm.domain.model.Alarm
import com.iris.alarm.domain.model.ChallengeThresholds
import com.iris.alarm.domain.model.DeviceCapabilities
import com.iris.alarm.domain.model.HuntTarget
import com.iris.alarm.domain.model.IrisSettings
import com.iris.alarm.domain.model.MathDifficulty
import com.iris.alarm.domain.model.MathProblem
import com.iris.alarm.domain.model.VisionChallenge
import com.iris.alarm.domain.model.resolveChallenge
import com.iris.alarm.domain.model.resolveWithoutCamera
import com.iris.alarm.domain.repository.SettingsRepository
import com.iris.alarm.vision.ChallengeProgress
import com.iris.alarm.vision.LumenMonitor
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ChallengeUiState(
    val challenge: VisionChallenge = VisionChallenge.SMILE,
    val progress: ChallengeProgress = ChallengeProgress(),
    /** Set when the running challenge is not the one the alarm asked for. */
    val notice: String? = null,
    /**
     * True when no detector can run on this device. The UI must then offer a
     * plain dismiss — an alarm nobody can stop is worse than a skipped challenge.
     */
    val escapeAllowed: Boolean = false,

    /** An anchor alarm whose captured spot is missing, so it cannot be matched. */
    val anchorMissing: Boolean = false,

    /** The object to find, for [VisionChallenge.HUNT]. */
    val huntTarget: HuntTarget? = null,

    /** Swaps left before the user is stuck with what they were given. */
    val huntSwapsLeft: Int = ChallengeThresholds.HUNT_MAX_SWAPS,

    /** The arithmetic in progress, for [VisionChallenge.MATH]. */
    val math: MathState? = null,
)

/**
 * The arithmetic challenge's own state.
 *
 * A wrong answer costs the current problem but never the ones already solved.
 * Resetting the whole run for one fat-fingered keypad press is the kind of
 * punishment that makes people uninstall an alarm clock, and the goal is a
 * person who is awake, not a person who is being tested.
 */
data class MathState(
    val problem: MathProblem,
    val entry: String = "",
    val solvedCount: Int = 0,
    val required: Int,
    /** Bumped on every wrong answer, so the UI can shake without owning state. */
    val wrongAttempts: Int = 0,
) {
    val fraction: Float get() = solvedCount.toFloat() / required.coerceAtLeast(1)
}

/**
 * Holds the live detector readout for the ringing screen, and decides which
 * challenge can actually run here. Camera analysers push into [report] from
 * their executor thread; the lux monitor is collected here because it is a plain
 * sensor stream with no CameraX lifecycle to hang off.
 */
@HiltViewModel
class ChallengeViewModel @Inject constructor(
    private val lumenMonitor: LumenMonitor,
    private val capabilities: DeviceCapabilities,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChallengeUiState())
    val uiState: StateFlow<ChallengeUiState> = _uiState.asStateFlow()

    private var lumenJob: Job? = null

    /** Guards against a recomposition restarting an already-resolved challenge. */
    private var started = false

    /** Targets already offered this morning, so a swap always produces a new one. */
    private val offeredTargets = mutableSetOf<HuntTarget>()

    /** Call once with the ringing alarm; resolves a runnable challenge and starts it. */
    fun start(alarm: Alarm?) {
        if (started) return
        started = true

        val requested = alarm?.challenge ?: VisionChallenge.SMILE

        // An anchor with no captured spot can never be matched — it would ring
        // until the auto-silence timeout. Fall back to something runnable and say
        // so, rather than presenting an impossible challenge.
        if (requested == VisionChallenge.ANCHOR && alarm?.anchorSignature == null) {
            val fallback = resolveChallenge(VisionChallenge.HUNT, capabilities)
                ?: VisionChallenge.MATH
            begin(
                challenge = fallback,
                notice = "NO TARGET SAVED · USING ${fallback.displayName.uppercase()}",
                anchorMissing = true,
            )
            return
        }

        val resolved = resolveChallenge(requested, capabilities)
        if (resolved == null) {
            // Unreachable while MATH ends every fallback order, but the escape
            // stays wired rather than becoming an unproven claim.
            _uiState.value = ChallengeUiState(
                challenge = requested,
                notice = "NO CHALLENGE CAN RUN ON THIS DEVICE",
                escapeAllowed = true,
            )
            return
        }

        begin(challenge = resolved, notice = noticeFor(requested, resolved))
    }

    /** Mounts [challenge], setting up whatever state it needs to run. */
    private fun begin(
        challenge: VisionChallenge,
        notice: String?,
        anchorMissing: Boolean = false,
    ) {
        _uiState.value = ChallengeUiState(
            challenge = challenge,
            notice = notice,
            anchorMissing = anchorMissing,
            huntTarget = if (challenge == VisionChallenge.HUNT) nextTarget() else null,
        )

        when (challenge) {
            VisionChallenge.LUMEN -> startLumenMonitoring()
            VisionChallenge.MATH -> startMath()
            else -> Unit
        }
    }

    private fun nextTarget(): HuntTarget = HuntTarget.random(exclude = offeredTargets)
        .also(offeredTargets::add)

    /**
     * "I don't have one of those." A real risk with a fixed pool of objects, so
     * it is answerable — but only [ChallengeThresholds.HUNT_MAX_SWAPS] times,
     * because an unlimited re-roll is an off switch with extra steps.
     */
    fun swapHuntTarget() {
        val state = _uiState.value
        if (state.challenge != VisionChallenge.HUNT || state.huntSwapsLeft <= 0) return

        _uiState.value = state.copy(
            huntTarget = nextTarget(),
            huntSwapsLeft = state.huntSwapsLeft - 1,
            progress = ChallengeProgress(),
        )
    }

    private fun startMath() {
        viewModelScope.launch {
            val settings = runCatching { settingsRepository.current() }
                .getOrDefault(IrisSettings())
            setMath(
                MathState(
                    problem = MathProblem.generate(settings.mathDifficulty),
                    required = settings.mathProblemCount.coerceAtLeast(1),
                ),
            )
        }
    }

    /** A keypad digit. Capped so the entry cannot run off the screen. */
    fun onMathDigit(digit: Int) {
        val math = _uiState.value.math ?: return
        if (math.entry.length >= MAX_ANSWER_DIGITS) return
        // A leading zero is never part of an answer here, so typing over it is
        // kinder than making the user backspace.
        val entry = if (math.entry == "0") digit.toString() else math.entry + digit
        setMath(math.copy(entry = entry))
    }

    fun onMathBackspace() {
        val math = _uiState.value.math ?: return
        setMath(math.copy(entry = math.entry.dropLast(1)))
    }

    /**
     * Grades the entry. Right answers advance; the last one solves the
     * challenge. Wrong answers clear the entry and re-roll the question, so the
     * same wrong answer cannot be submitted twice by mashing.
     */
    fun onMathSubmit() {
        val state = _uiState.value
        val math = state.math ?: return
        if (math.entry.isEmpty()) return

        if (!math.problem.isCorrect(math.entry)) {
            viewModelScope.launch {
                val difficulty = runCatching { settingsRepository.current().mathDifficulty }
                    .getOrDefault(MathDifficulty.MEDIUM)
                setMath(
                    math.copy(
                        problem = MathProblem.generate(difficulty),
                        entry = "",
                        wrongAttempts = math.wrongAttempts + 1,
                    ),
                )
                report(
                    ChallengeProgress(
                        fraction = math.fraction,
                        readout = "${math.solvedCount}/${math.required}",
                        hint = "NOT QUITE — HERE'S ANOTHER",
                    ),
                )
            }
            return
        }

        val solvedCount = math.solvedCount + 1
        if (solvedCount >= math.required) {
            setMath(math.copy(entry = "", solvedCount = solvedCount))
            report(
                ChallengeProgress(
                    fraction = 1f,
                    readout = "${math.required}/${math.required}",
                    hint = "YOU'RE AWAKE",
                    solved = true,
                ),
            )
            return
        }

        viewModelScope.launch {
            val difficulty = runCatching { settingsRepository.current().mathDifficulty }
                .getOrDefault(MathDifficulty.MEDIUM)
            setMath(
                math.copy(
                    problem = MathProblem.generate(difficulty),
                    entry = "",
                    solvedCount = solvedCount,
                ),
            )
            report(
                ChallengeProgress(
                    fraction = solvedCount.toFloat() / math.required,
                    readout = "$solvedCount/${math.required}",
                    hint = "ONE DOWN",
                ),
            )
        }
    }

    private fun setMath(math: MathState) {
        _uiState.value = _uiState.value.copy(math = math)
    }

    /**
     * The user cannot or will not grant camera access. Switch to a challenge that
     * needs no camera — arithmetic always qualifies, so this can always offer
     * something rather than giving up.
     */
    fun onCameraUnavailable() {
        val fallback = resolveWithoutCamera(capabilities)
        _uiState.value = _uiState.value.copy(
            challenge = fallback,
            progress = ChallengeProgress(),
            huntTarget = null,
            notice = "CAMERA UNAVAILABLE · USING ${fallback.displayName.uppercase()}",
        )
        when (fallback) {
            VisionChallenge.LUMEN -> startLumenMonitoring()
            VisionChallenge.MATH -> startMath()
            else -> Unit
        }
    }

    fun report(progress: ChallengeProgress) {
        // Once solved, ignore trailing frames so the success state cannot flicker
        // back to "no face detected" before the screen finishes closing.
        if (_uiState.value.progress.solved) return
        _uiState.value = _uiState.value.copy(progress = progress)
    }

    private fun startLumenMonitoring() {
        if (lumenJob?.isActive == true) return
        lumenJob = viewModelScope.launch {
            lumenMonitor.readings().collect { report(it) }
        }
    }

    private fun noticeFor(requested: VisionChallenge, resolved: VisionChallenge): String? =
        if (requested == resolved) {
            null
        } else {
            "${requested.displayName.uppercase()} UNAVAILABLE · USING ${resolved.displayName.uppercase()}"
        }

    override fun onCleared() {
        lumenJob?.cancel()
        super.onCleared()
    }

    private companion object {
        const val MAX_ANSWER_DIGITS = 6
    }
}
