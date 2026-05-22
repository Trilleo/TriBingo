package net.trilleo.mc.plugins.tribingo.managers

import net.trilleo.mc.plugins.tribingo.utils.TeamUtil

object TeamManager {
    fun initializeTeam() {
        TeamUtil.createTeam("player", "<bold><green>Player")
        TeamUtil.createTeam("Spectator", "<bold><gray>Spectator")
    }
}