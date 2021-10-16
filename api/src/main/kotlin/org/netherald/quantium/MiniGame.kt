package org.netherald.quantium

import org.bukkit.World
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import java.util.*
import kotlin.collections.ArrayList

class MiniGame(
    val owner : JavaPlugin,
    val name : String,
    val minPlayerSize : Int,
    val maxPlayerSize : Int,
    var maxInstanceSize : Int,
    var defaultInstanceSize : Int,
    val instanceSettingValue : MiniGameInstance.() -> Unit,
    val instances : List<MiniGameInstance> = ArrayList(),
    val worldInstanceMap: HashMap<World, MiniGameInstance> = HashMap<World, MiniGameInstance>(),
    val players : List<Player> = ArrayList()
) {

    init {

        MiniGameInstance(this, ArrayList()).apply(instanceSettingValue).UnSafe().callMiniGameCreatedListener()

        for (i in 0 until defaultInstanceSize) { createInstance() }

    }

    val worlds : Collection<World>
        get() = worldInstanceMap.keys

    val recommendMatchingInstance : MiniGameInstance?
        get() {
            for (instance in instances) {
                if (!instance.isStarted && !instance.isFinished) {
                    return instance
                }
            }
            return null
        }

    val matchingInstances : List<MiniGameInstance>
        get() {
            val out = ArrayList<MiniGameInstance>()
            for (instance in instances) {
                if (!instance.isStarted && !instance.isFinished) {
                    out.add(instance)
                }
            }
            return out
        }

    fun createInstance() {

        val instance = MiniGameInstance(
            this,
        ).apply(instanceSettingValue)

        val worldNameId = UUID.randomUUID().toString()
        val worlds = ArrayList<World>()

        val clone = fun (world : World, name : String) = instance.worldSetting.worldEditor.cloneWorld(world, name)

        instance.worldSetting.baseWorld?.let { worlds.add(clone(it, worldNameId+it)) }
        instance.worldSetting.baseWorldNether?.let { worlds.add(clone(it, worldNameId+it)) }
        instance.worldSetting.baseWorldTheNether?.let { worlds.add(clone(it, worldNameId+it)) }
        instance.worldSetting.otherBaseWorlds.forEach { worlds.add(clone(it, worldNameId+it)) }

        addInstance(instance)

        worlds.forEach { worldInstanceMap[it] = instance }

        instance.UnSafe().callInstanceCreatedListener()

    }

    fun stopAll() {
        defaultInstanceSize = 0
        instances.forEach { instance ->
            instance.stopGame()
        }
    }

    private fun addInstance(instance: MiniGameInstance) {
        instances as ArrayList
        instances.add(instance)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as MiniGame

        if (name != other.name) return false

        return true
    }

    override fun toString(): String { return name }

    override fun hashCode(): Int { return name.hashCode() }
}
