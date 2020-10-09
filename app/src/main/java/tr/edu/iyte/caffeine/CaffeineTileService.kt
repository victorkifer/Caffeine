package tr.edu.iyte.caffeine

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.drawable.Icon
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import tr.edu.iyte.caffeine.util.Loggable
import tr.edu.iyte.caffeine.util.info
import tr.edu.iyte.caffeine.util.startForegroundService
import tr.edu.iyte.caffeine.util.stopService

class CaffeineTileService : TileService(), Loggable, Caffeine.TimerListener {
    private val icCaffeineEmpty by lazy { Icon.createWithResource(this, R.drawable.ic_caffeine_empty) }
    private val icCaffeineFull by lazy { Icon.createWithResource(this, R.drawable.ic_caffeine_full) }
    private val icCaffeine66percent by lazy { Icon.createWithResource(this, R.drawable.ic_caffeine_66percent) }
    private val icCaffeine33percent by lazy { Icon.createWithResource(this, R.drawable.ic_caffeine_33percent) }

    private val screenOnOffReceiver = ScreenOnOffReceiver()

    override fun onTileAdded() {
        super.onTileAdded()
        info("tile added")
    }

    private inner class ScreenOnOffReceiver : BroadcastReceiver(), Loggable {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != Intent.ACTION_SCREEN_OFF)
                return
            info("Received ${Intent.ACTION_SCREEN_OFF}, intent: $intent")
            if (intent.action == Intent.ACTION_SCREEN_ON) {
                updateTile()
            } else {
                updateTile(state = Tile.STATE_UNAVAILABLE)
                Caffeine.onReset()
            }
        }
    }

    override fun onStartListening() {
        super.onStartListening()
        info("started listening")

        registerReceiver(screenOnOffReceiver, IntentFilter(Intent.ACTION_SCREEN_OFF))
        registerReceiver(screenOnOffReceiver, IntentFilter(Intent.ACTION_SCREEN_ON))

        if (isLocked) {
            info("phone is locked, caffeine won't operate")
            updateTile(state = Tile.STATE_UNAVAILABLE)
            return
        }

        updateTile()
        Caffeine.addTimerListener(this)
    }

    override fun onClick() {
        super.onClick()
        info("tile clicked")
        startForegroundService<TimerService>()
    }

    override fun onStopListening() {
        unregisterReceiver(screenOnOffReceiver)
        Caffeine.removeTimerListener(this)
        info("stopped listening")
        super.onStopListening()
    }

    override fun onTileRemoved() {
        info("tile removed")
        Caffeine.onReset()
        stopService<TimerService>()
        super.onTileRemoved()
    }

    override fun onTick(label: String, percentage: Float) {
        updateTile(state = Tile.STATE_ACTIVE,
                label = label,
                icon = when {
                    percentage > .66 -> icCaffeineFull
                    percentage > .33 -> icCaffeine66percent
                    else -> icCaffeine33percent
                })
    }

    override fun onFinish() {
        updateTile()
    }

    private fun updateTile(
            state: Int = Tile.STATE_INACTIVE,
            label: String = getString(R.string.tile_name),
            icon: Icon = icCaffeineEmpty) {
        qsTile ?: return
        qsTile.state = state
        qsTile.label = label
        qsTile.icon = icon
        info("updating label: $label")
        qsTile.updateTile()
    }
}