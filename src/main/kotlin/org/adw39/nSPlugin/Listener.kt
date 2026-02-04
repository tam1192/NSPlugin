package org.adw39.nSPlugin

import org.bukkit.Bukkit
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent

class Listener: Listener {
    val plugin = Bukkit.getPluginManager().getPlugin("NSPlugin") as NSPlugin?

    @EventHandler
    fun onPlayerJoin(event: PlayerJoinEvent) {
        if (plugin == null) return
        if (plugin.nsm.memberJoinedNs(event.player.uniqueId).isEmpty()) {
            plugin.nsm.createUniqueNamespace(event.player.name, event.player.uniqueId)
        }
    }
}