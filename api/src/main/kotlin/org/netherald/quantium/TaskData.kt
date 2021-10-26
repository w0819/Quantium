package org.netherald.quantium

import org.bukkit.scheduler.BukkitTask

class TaskData(private val instance: MiniGameInstance) {

    lateinit var task: BukkitTask

    fun cancel() {
        task.cancel()
        instance.tasks.remove(task)
    }

    val taskId : Int get() = task.taskId
    val isCancelled : Boolean get() = task.isCancelled
    val isSync : Boolean get() = task.isSync

}