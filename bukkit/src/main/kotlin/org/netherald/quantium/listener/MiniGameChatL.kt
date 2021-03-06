
package org.netherald.quantium.listener

import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.AsyncPlayerChatEvent
import org.netherald.quantium.MiniGameInstance
import org.netherald.quantium.data.playingGame

class MiniGameChatL : Listener {

    companion object {
        val targetMiniGame = HashSet<MiniGameInstance>()
    }

    @EventHandler(priority = EventPriority.LOW)
    fun on(event : AsyncPlayerChatEvent) {
        event.player.playingGame?.let {
            if (targetMiniGame.contains(it)) {
                event.recipients.clear()
                event.recipients.addAll(it.players)
            }
        }
    }

}