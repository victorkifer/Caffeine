package tr.edu.iyte.caffeine

import android.os.CountDownTimer
import tr.edu.iyte.caffeine.util.toMillis
import tr.edu.iyte.caffeine.util.toSeconds

object Caffeine {
    interface TimerListener {
        fun onTick(label: String, percentage: Float)
        fun onFinish()
    }

    interface ModeListener {
        fun onModeChanged(mode: CaffeineMode)
    }

    private val timerListeners = mutableListOf<TimerListener>()
    var modeListener: ModeListener? = null
    var mode = CaffeineMode.INACTIVE
        private set
    private var currentTimer: Timer? = null

    fun addTimerListener(timerListener: TimerListener) {
        timerListeners.add(timerListener)
    }

    fun removeTimerListener(timerListener: TimerListener) {
        timerListeners.remove(timerListener)
    }

    private class Timer(private val secs: Long) :
            CountDownTimer(secs.toMillis() + 300, 500) {
        override fun onTick(millisUntilFinished: Long) {
            val sec = (millisUntilFinished / 1000).toInt()
            val min = sec / 60
            val percentage = sec / secs.toFloat()

            if (secs >= Int.MAX_VALUE)
                return

            val timeFormatted = String.format("%d:%02d", min, sec % 60)
            timerListeners.forEach {
                it.onTick(timeFormatted, percentage)
            }
        }

        override fun onFinish() {
            onReset()
            timerListeners.forEach {
                it.onFinish()
            }
        }
    }

    fun onModeChange() {
        when (mode) {
            CaffeineMode.INFINITE_MINS -> {
                onReset()
                timerListeners.forEach {
                    it.onFinish()
                }
            }
            else -> {
                mode = mode.next()
                currentTimer?.cancel()
                currentTimer = Timer(mode.min.toSeconds())
                timerListeners.forEach {
                    it.onTick(mode.label, 1f)
                }
                currentTimer?.start()
                modeListener?.onModeChanged(mode)
            }
        }
    }

    fun onReset() {
        mode = CaffeineMode.INACTIVE
        currentTimer?.cancel()
        currentTimer = null
        modeListener?.onModeChanged(mode)
    }
}