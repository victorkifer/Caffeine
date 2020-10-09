package tr.edu.iyte.caffeine

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.telephony.PhoneStateListener
import android.telephony.TelephonyManager
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import tr.edu.iyte.caffeine.util.*

class TimerService : Service(), Loggable, Caffeine.ModeListener, Caffeine.TimerListener {
    companion object {
        var isCaffeineRunning: Boolean = false
            private set
    }

    override fun onBind(intent: Intent?) = null

    val caffeine = Caffeine

    private var wakelock: PowerManager.WakeLock? = null

    private val callListener = object : PhoneStateListener() {
        override fun onCallStateChanged(state: Int, incomingNumber: String?) {
            super.onCallStateChanged(state, incomingNumber)
            if (state == TelephonyManager.CALL_STATE_OFFHOOK)
                caffeine.onReset()
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun createNotificationChannel() {
        if (notificationManager.notificationChannels.any { it.id == NOTIFICATION_CHANNEL_ID }) {
            notificationManager.deleteNotificationChannel(NOTIFICATION_CHANNEL_ID)
        }

        val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                getString(R.string.notif_channel),
                NotificationManager.IMPORTANCE_LOW
        )
        channel.enableLights(false)
        channel.enableVibration(false)
        channel.setSound(null, null)
        notificationManager.createNotificationChannel(channel)
    }

    override fun onCreate() {
        super.onCreate()
        info("Service onCreate")
        doIfAndroidO {
            createNotificationChannel()
            sendNotification(getString(R.string.notif_running))
        }

        caffeine.modeListener = this
        caffeine.addTimerListener(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        info("Service onStartCommand")
        caffeine.onModeChange()
        return START_NOT_STICKY
    }

    private fun sendNotification(title: String) {
        doIfAndroidO {
            val notif = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
                    .setOnlyAlertOnce(true)
                    .setSmallIcon(R.drawable.ic_caffeine_full)
                    .setContentTitle(title)
                    .setOngoing(true)
                    .setPriority(NotificationCompat.PRIORITY_LOW)
                    .setCategory(Notification.CATEGORY_SERVICE)
                    .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                    .build()
            startForeground(85, notif)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        info("Service onDestroy")
        caffeine.removeTimerListener(this)
        caffeine.modeListener = null
        releaseWakelock()
        unregisterInterruptionListeners()
        isCaffeineRunning = false
        doIfAndroidO {
            stopForeground(true)
        }
    }

    private fun registerInterruptionListeners() {
        if (!isCaffeineRunning) {
            telephonyManager.listen(callListener, PhoneStateListener.LISTEN_CALL_STATE)
            info("Screen off receiver and call listener registered")
        }
    }

    private fun unregisterInterruptionListeners() {
        if (isCaffeineRunning) {
            telephonyManager.listen(callListener, PhoneStateListener.LISTEN_NONE)
            info("Screen off receiver and call listener unregistered")
        }
    }

    @Suppress("deprecation")
    private fun acquireWakelock(secs: Long) {
        releaseWakelock()

        info("Acquiring wakelock for $secs seconds...")
        wakelock = powerManager.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK, WAKE_LOCK_TAG)
        wakelock?.acquire(secs.toMillis())
    }

    private fun releaseWakelock() {
        if (wakelock == null || !wakelock!!.isHeld)
            return
        info("Releasing wakelock..")
        wakelock?.release()
        wakelock = null
    }

    override fun onModeChanged(mode: CaffeineMode) {
        when (mode) {
            CaffeineMode.INACTIVE -> {
                unregisterInterruptionListeners()
                releaseWakelock()
                isCaffeineRunning = false
                stopSelf()
            }
            else -> {
                acquireWakelock(mode.min.toSeconds())
                registerInterruptionListeners()
                isCaffeineRunning = true
            }
        }
    }

    override fun onTick(label: String, percentage: Float) {
        sendNotification(label)
    }

    override fun onFinish() {
        // does nothing
    }
}