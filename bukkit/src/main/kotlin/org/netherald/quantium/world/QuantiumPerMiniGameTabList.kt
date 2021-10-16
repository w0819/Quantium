package org.netherald.quantium.world

import org.netherald.quantium.MiniGame
import org.netherald.quantium.MiniGameInstance
import org.netherald.quantium.listener.TabListUtilL

class QuantiumPerMiniGameTabList : PerMiniGameTabList {
    override fun applyPerTabList(minigame: MiniGameInstance) {
        TabListUtilL.targetMiniGame.add(minigame)
    }

    override fun unApplyPerTabList(minigame: MiniGameInstance) {
        TabListUtilL.targetMiniGame.remove(minigame)
    }
}