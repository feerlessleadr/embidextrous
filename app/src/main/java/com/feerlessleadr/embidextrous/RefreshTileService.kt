package com.feerlessleadr.embidextrous

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/** A quick-settings tile: pull the shade down, tap, and every smart list refreshes. */
class RefreshTileService : TileService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onStartListening() {
        qsTile?.apply {
            state = Tile.STATE_INACTIVE
            label = getString(R.string.tile_label)
            updateTile()
        }
    }

    override fun onClick() {
        qsTile?.apply {
            state = Tile.STATE_ACTIVE
            label = "Refreshing…"
            updateTile()
        }
        scope.launch {
            val message = QuickRefresh.run(applicationContext)
            qsTile?.apply {
                state = Tile.STATE_INACTIVE
                label = message
                updateTile()
            }
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}