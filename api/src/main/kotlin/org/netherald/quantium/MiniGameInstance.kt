package org.netherald.quantium

import org.bukkit.Bukkit
import org.bukkit.GameMode
import org.bukkit.Sound
import org.bukkit.World
import org.bukkit.entity.Player
import org.bukkit.event.Event
import org.bukkit.event.EventPriority
import org.bukkit.event.HandlerList
import org.bukkit.event.Listener
import org.bukkit.event.player.*
import org.bukkit.scheduler.BukkitTask
import org.netherald.quantium.data.*
import org.netherald.quantium.event.InstanceDeletedEvent
import org.netherald.quantium.exception.OutOfMaxPlayerSizeException
import org.netherald.quantium.setting.IsolationSetting
import org.netherald.quantium.setting.TeamSetting
import org.netherald.quantium.setting.WorldSetting
import org.netherald.quantium.util.MiniGameBuilderUtil
import org.netherald.quantium.util.SpectatorUtil
import org.netherald.quantium.world.PortalLinker

@QuantiumMarker
class MiniGameInstance(
    val miniGame: MiniGame,
) {

    val unSafe = UnSafe()

    inner class UnSafe {

        var world : World? = null
        var worldNether : World? = null
        var worldEnder : World? = null
        val otherWorlds = HashSet<World>()

        fun start() {

            finished = false

            if (!worldSetting.enableOtherWorldTeleport) {
                listener(PlayerTeleportEvent::class.java) {
                    if (worlds.contains(event.from.world) xor worlds.contains(event.to?.world)) {
                        if (event.cause == PlayerTeleportEvent.TeleportCause.SPECTATE) {
                            event.isCancelled = true
                        }
                    }
                }
            }

            if (teamSetting.enable) {
                team = teamSetting.teamMatcher.match(players)
            }

            if (worldSetting.linkPortal) {
                val linker : PortalLinker = worldSetting.portalLinker
                world?.let { world ->
                    worldNether?.let { linker.linkNether(world, it) }
                    worldEnder?.let { linker.linkEnder(world, it) }
                }
            }

            if (isolationSetting.perChat) {
                isolationSetting.perMiniGameChatUtil.applyPerChat(this@MiniGameInstance)
            }

            if (isolationSetting.perPlayerList) {
                isolationSetting.perMiniGameTabListUtil.applyPerTabList(this@MiniGameInstance)
            }

            registerListeners()

            players.forEach { player ->
                PlayerData.UnSafe.addAllMiniGameData(player, this@MiniGameInstance)
                worldSetting.spawn?.let { player.teleport(it) }
            }

            started = true

            callStartListener()

            println("""
                ${miniGame.name} instance started
                    world : ${world?.name}
                    nether : ${worldNether?.name}
                    ender : ${worldEnder?.name}
            """.trimIndent())

        }

        fun callPlayerAdded(player: Player) {
            addedPlayerListener.forEach {
                it(MiniGameBuilderUtil(this@MiniGameInstance), player)
            }
        }

        fun callPlayerRemoved(player: Player) {
            removedPlayerListener.forEach {
                it(MiniGameBuilderUtil(this@MiniGameInstance), player)
            }
        }

        fun callMiniGameCreatedListener() {
            miniGameCreatedListener.forEach {
                it(MiniGameBuilderUtil(this@MiniGameInstance))
            }
        }

        fun callInstanceCreatedListener() {
            instanceCreatedListener.forEach {
                it(MiniGameBuilderUtil(this@MiniGameInstance))
            }
        }

        fun callStartListener() {
            startListener.forEach {
                it(MiniGameBuilderUtil(this@MiniGameInstance))
            }
        }

        fun callStopListener() {
            stopListener.forEach {
                it(MiniGameBuilderUtil(this@MiniGameInstance))
            }
        }

        fun callDeleteListener() {
            deleteListener.forEach {
                it(MiniGameBuilderUtil(this@MiniGameInstance))
            }
        }

        fun deleteAllWorld() {
            worlds.forEach { worldSetting.worldEditor.deleteWorld(it) }
            worlds.forEach { miniGame.worldInstanceMap -= it }
            unSafe.world = null
            unSafe.worldNether = null
            unSafe.worldEnder = null
            unSafe.otherWorlds.clear()
        }

        fun clearPlayersData() {
            players.forEach { player ->
                PlayerData.UnSafe.clearData(player)
                player.unApplySpectator()
            }
        }
    }

    val world : World? get() = unSafe.world
    val worldNether : World? get() = unSafe.worldNether
    val worldEnder : World? get() = unSafe.worldEnder
    val otherWorlds : Collection<World> get() = unSafe.otherWorlds

    val worlds : Collection<World>
        get() {
            val out = HashSet<World>(unSafe.otherWorlds)
            unSafe.world?.let { out.add(it) }
            unSafe.worldNether?.let { out.add(it) }
            unSafe.worldEnder?.let { out.add(it) }
            unSafe.otherWorlds.forEach { out.add(it) }
            return out
        }

    var autoDelete : Boolean = true
    var autoStart : Boolean = true

    val isStarted : Boolean get() = started
    val isFinished : Boolean get() = finished

    private var started = false
    private var finished = false

    var spectatorUtil: SpectatorUtil = SpectatorUtil.default

    fun Player.applySpectator() = spectatorUtil.applySpectator(this)
    fun Player.unApplySpectator() = spectatorUtil.unApplySpectator(this)

    var teamSetting : TeamSetting = TeamSetting()
    var worldSetting: WorldSetting = WorldSetting()
    var isolationSetting: IsolationSetting = IsolationSetting()

    val reJoinData : MutableCollection<Player> = HashSet()

    fun teamSetting(init : TeamSetting.() -> Unit): TeamSetting {
        teamSetting.init()
        return teamSetting
    }

    fun worldSetting(init : WorldSetting.() -> Unit) : WorldSetting {
        worldSetting.init()
        return worldSetting
    }

    fun isolatedSetting(init : IsolationSetting.() -> Unit) : IsolationSetting {
        isolationSetting.init()
        return isolationSetting
    }

    val listeners = ArrayList<Listener>()
    val tasks = ArrayList<QuantiumTaskData>()


    val players = HashSet<Player>()

    // it works when this minigame is team game
    var team : List<List<Player>> = ArrayList()

    var enableRejoin : Boolean = false
    var defaultGameMode : GameMode = GameMode.ADVENTURE

    private var startTask : BukkitTask? = null

    fun runStartTask() {
        startTask ?: run {

            val sound = fun Player.() = playSound(location, Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 5.0f)
            val soundBroadCast = fun () = players.forEach { it.sound() }

            startTask = MiniGameBuilderUtil(this).loopTask(15 downTo 0, 20, 20) { i ->
                if (0 < i) {
                    if (i == 15 || i <= 5) {
                        broadcast("???????????? ${i}??? ???")
                        soundBroadCast()
                    }
                } else {
                    cancelStartTask()
                    unSafe.start()
                }
            }
        }
    }

    fun cancelStartTask() {
        startTask?.cancel()
        startTask = null
    }

    fun addPlayer(player: Player) {

        if (miniGame.maxPlayerSize < players.size+1) throw OutOfMaxPlayerSizeException()

        PlayerData.UnSafe.addAllMiniGameData(player, this)
        unSafe.callPlayerAdded(player)
        if (autoStart && miniGame.minPlayerSize <= players.size) {
            runStartTask()
        }
    }

    /*
        it just for before start
     */
    fun removePlayer(player: Player) {

        PlayerData.UnSafe.clearData(player)
        unSafe.callPlayerRemoved(player)

        if (players.size < miniGame.minPlayerSize) {
            cancelStartTask()
        }
    }

    fun addWorld(value : World, addWorldType: AddWorldType) {
        when (addWorldType) {
            AddWorldType.NORMAL -> {
                world?.let { removeWorld(it) }
                unSafe.world = value
            }
            AddWorldType.NETHER -> {
                worldNether?.let { removeWorld(it) }
                unSafe.worldNether = value
            }
            AddWorldType.ENDER -> {
                worldEnder?.let { removeWorld(it) }
                unSafe.worldEnder = value
            }
            AddWorldType.OTHER -> {
                unSafe.otherWorlds += value
            }
        }
        miniGame.worldInstanceMap[value] = this
    }

    fun removeWorld(value : World) {
        if (unSafe.world == value) unSafe.world = null
        if (unSafe.worldNether == value) unSafe.worldNether = null
        if (unSafe.worldEnder == value) unSafe.worldEnder = null
        unSafe.otherWorlds -= value
        miniGame.worldInstanceMap -= value
    }

    enum class AddWorldType {
        NORMAL, NETHER, ENDER, OTHER
    }






    fun broadcast(message : String) {
        println("[${miniGame.name}] broadcast : $message")
        players.forEach { it.sendMessage(message) }
    }

    fun broadcast(code : (Player) -> Unit) = players.forEach { code(it) }








    fun stopGame() {
        unregisterListeners()
        unregisterTasks()
        players.forEach { spectatorUtil.unApplySpectator(it) }
        unSafe.callStopListener()
        if (autoDelete) delete()
        finished = true
        println("${miniGame.name} instance is stopped")
    }

    fun clearReJoinData() {
        reJoinData.forEach { it.clearReJoinData() }
    }

    fun unregisterListeners() {
        listeners.forEach { HandlerList.unregisterAll(it) }
        listeners.clear()
    }

    fun unregisterTasks() {
        tasks.forEach { it.cancel() }
        tasks.clear()
    }

    fun delete() {

        miniGame.instances as MutableList
        miniGame.instances.remove(this@MiniGameInstance)

        unSafe.deleteAllWorld()
        unSafe.clearPlayersData()
        unSafe.callDeleteListener()

        Bukkit.getPluginManager().callEvent(InstanceDeletedEvent(this))

        if (miniGame.instances.size < miniGame.defaultInstanceSize) {
            miniGame.createInstance()
        }

    }





    private val addedPlayerListener = ArrayList<MiniGameBuilderUtil.(player : Player) -> Unit>()
    fun onPlayerAdded(listener : MiniGameBuilderUtil.(player : Player) -> Unit) = addedPlayerListener.add(listener)

    private val removedPlayerListener = ArrayList<MiniGameBuilderUtil.(player : Player) -> Unit>()
    fun onPlayerRemoved(listener : MiniGameBuilderUtil.(player : Player) -> Unit) = removedPlayerListener.add(listener)

    private val miniGameCreatedListener = ArrayList<MiniGameBuilderUtil.() -> Unit>()
    fun onMiniGameCreated(listener : MiniGameBuilderUtil.() -> Unit) = miniGameCreatedListener.add(listener)

    private val instanceCreatedListener = ArrayList<MiniGameBuilderUtil.() -> Unit>()
    fun onInstanceCreated(listener : MiniGameBuilderUtil.() -> Unit) = instanceCreatedListener.add(listener)

    private val startListener = ArrayList<MiniGameBuilderUtil.() -> Unit>()
    fun onStart(listener : MiniGameBuilderUtil.() -> Unit) = startListener.add(listener)

    private val stopListener = ArrayList<MiniGameBuilderUtil.() -> Unit>()
    fun onStop(listener : MiniGameBuilderUtil.() -> Unit) = stopListener.add(listener)

    private val deleteListener = ArrayList<MiniGameBuilderUtil.() -> Unit>()
    fun onDelete(listener : MiniGameBuilderUtil.() -> Unit) = stopListener.add(listener)






    // why don't check if player is playing this minigame?
    // because allServerEvent option is false

    fun onPlayerKicked(listener : QuantiumEvent<out PlayerKickEvent>.() -> Unit) : Listener =
        listener(PlayerKickEvent::class.java, EventPriority.NORMAL,
            ignoreCancelled = true,
            allServerEvent = false,
            listener = listener
        )

    fun onPlayerDisconnected(listener : QuantiumEvent<out PlayerQuitEvent>.() -> Unit) : Listener =
        listener(
            PlayerQuitEvent::class.java, EventPriority.NORMAL,
            ignoreCancelled = true,
            allServerEvent = false,
            listener = listener
        )

    fun onPlayerRejoin(listener : QuantiumEvent<out PlayerJoinEvent>.() -> Unit) : Listener? {
        return if (enableRejoin) {
            listener(PlayerJoinEvent::class.java, EventPriority.NORMAL,
                ignoreCancelled = true,
                allServerEvent = false,
                listener = listener
            )
        } else {
            null
        }
    }











    private val listeners1 = HashMap<Listener, Class<out Event>>()
    private val eventPriorityData = HashMap<Listener, EventPriority>()
    private val ignoreCancelledData = HashMap<Listener, Boolean>()

    fun <T : Event> listener(
        clazz: Class<out T>,
        eventPriority : EventPriority = EventPriority.NORMAL,
        ignoreCancelled : Boolean = false,
        allServerEvent: Boolean = false,
        listener: QuantiumEvent<out T>.() -> Unit
    ) : Listener {
        val out = MiniGameBuilderUtil(this).listener(
            clazz, allServerEvent, eventPriority, ignoreCancelled, false, listener
        )
        listeners1[out] = clazz
        ignoreCancelledData[out] = ignoreCancelled
        eventPriorityData[out] = eventPriority
        return out
    }

    private val listeners2 = ArrayList<Listener>()
    fun registerEvents(listener: Listener) {
        listeners2.add(listener)
    }

    private fun registerListeners() {

        listeners1.forEach { (listener, _) -> registerListener(listener) }
        listeners1.clear()
        eventPriorityData.clear()
        ignoreCancelledData.clear()

        listeners2.forEach { listener -> MiniGameBuilderUtil(this).registerEvents(listener) }

    }

    private fun registerListener(listener: Listener) {
        listeners1[listener]?.let {
            MiniGameBuilderUtil(this).registerEvent(
                listeners1[listener]!!, listener, eventPriorityData[listener]!!, ignoreCancelledData[listener]!!
            )
        }
    }
}