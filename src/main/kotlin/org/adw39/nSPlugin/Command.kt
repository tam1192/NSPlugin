package org.adw39.nSPlugin

import com.mojang.brigadier.Command
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.builder.RequiredArgumentBuilder
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.tree.LiteralCommandNode
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.Commands
import io.papermc.paper.command.brigadier.argument.ArgumentTypes
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver
import org.bukkit.Bukkit
import org.bukkit.OfflinePlayer
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

val plugin = Bukkit.getPluginManager().getPlugin("NSPlugin") as NSPlugin

class NSPCommandContext(val cmd: CommandContext<CommandSourceStack>) {
    val sender: CommandSender
        get() = cmd.source.sender

    fun sendMessage(message: String) {
        sender.sendMessage(message)
    }

    fun senderIsPlayer(): Boolean {
        return sender is Player
    }

    fun getPlayerSender(): Player? {
        return if (senderIsPlayer()) {
            sender as Player
        } else {
            sendMessage("this command can only be run by players")
            null
        }
    }

    fun getStringArgument(arg: String): String? {
        return runCatching { cmd.getArgument(arg, String::class.java) }.getOrElse {
            sendMessage(it.message ?: "unknown error")
            null
        }
    }

    fun tryGetStringArgument(arg: String): Result<String> {
        return runCatching { cmd.getArgument(arg, String::class.java) }
    }

    fun getPlayerArgument(arg: String): Player? {
        return runCatching {
            cmd.getArgument(arg, PlayerSelectorArgumentResolver::class.java)
                .resolve(cmd.source).first()
        }.getOrElse {
            sendMessage("target Player is not found")
            return null
        }
    }
}

class NSUserCommand(
    val sender: Player,
    val namespace: String,
    val target: Player,
    val roleResult: Result<Role>,
) {
    fun addUser(): Result<Boolean> {
        val role = roleResult.getOrElse { return Result.failure(it)}
        return runCatching {
            plugin.nsm.addMember(namespace, sender.uniqueId, target.uniqueId, role)
            true
        }
    }

    fun delUser(): Result<Boolean> {
        return runCatching {
            plugin.nsm.delMember(namespace, sender.uniqueId, target.uniqueId)
            true
        }
    }

    companion object {
        fun fromCommandContext(context: NSPCommandContext): NSUserCommand? {
            val sender = context.getPlayerSender() ?: return null
            val namespace = context.getStringArgument("nameSpace") ?: return null
            val target = context.getPlayerArgument("target") ?: return null
            val role = context.tryGetStringArgument("role").let {
                val arg = it.getOrElse { return@let Result.failure(it) }
                Role.fromString(arg)
            }

            return NSUserCommand(sender, namespace, target, role)
        }
    }

}

val userAdd: LiteralArgumentBuilder<CommandSourceStack?>? = Commands.literal("add").then(
    Commands.argument("target", ArgumentTypes.player())
        .then(Commands.argument("role", StringArgumentType.string())
        .executes {
            val context = NSPCommandContext(it)
            val userCmd = NSUserCommand.fromCommandContext(context)

            userCmd?.addUser()?.onFailure {
                context.sendMessage(it.message ?: "unknown error")
            }

            context.sendMessage("ok!")

            return@executes Command.SINGLE_SUCCESS
        }
    )
)
val userDel: LiteralArgumentBuilder<CommandSourceStack?>? = Commands.literal("del").then(
    Commands.argument("target", ArgumentTypes.player())
        .executes {
            val context = NSPCommandContext(it)
            val userCmd = NSUserCommand.fromCommandContext(context)

            userCmd?.delUser()?.onFailure {
                context.sendMessage(it.message ?: "unknown error")
            }

            context.sendMessage("ok!")

            return@executes Command.SINGLE_SUCCESS
        }
    )

val userList: LiteralArgumentBuilder<CommandSourceStack?>? = Commands.literal("list").executes {
    val context = NSPCommandContext(it)
    val ns = context.getStringArgument("nameSpace") ?: return@executes Command.SINGLE_SUCCESS

    val members = runCatching {
        plugin.nsm.lsMember(ns)
    }.map{ it ->
        it.entries.fold("namespace ($ns) members") {acc, (uuid, role) ->
            val p = Bukkit.getOfflinePlayer(uuid).name ?: "unknown player"
            "$acc\n- $p: $role"
        }
    }.getOrElse {
        context.sendMessage(it.message ?: "unknown error")
        return@executes Command.SINGLE_SUCCESS
    }

    context.sendMessage(members)

    Command.SINGLE_SUCCESS
}

val nsList = Commands.literal("ls").executes {
    val context = NSPCommandContext(it)
    val msg = if (context.senderIsPlayer()) {
        val player = context.sender as Player
        plugin.nsm.memberJoinedNs(player.uniqueId).fold("${player.name} namespaces:") { acc, str ->
            "$acc\n- $str"
        }
    } else {
        plugin.nsm.allNs().fold("all namespaces:") { acc, str ->
            "$acc\n- $str"
        }
    }
    context.sendMessage(msg)
    Command.SINGLE_SUCCESS
}


val nspCreate: LiteralArgumentBuilder<CommandSourceStack?>? = Commands.literal("create").then(
    Commands.argument("name", StringArgumentType.string())
        .executes {
            val context = NSPCommandContext(it)
            val player = context.getPlayerSender() ?: return@executes Command.SINGLE_SUCCESS
            val nameArg = context.getStringArgument("name") ?: return@executes Command.SINGLE_SUCCESS
            try {
                plugin.nsm.createNamespace(nameArg, player.uniqueId)
            } catch (ex: Exception) {
                context.sendMessage(ex.message ?: "unknown error")
            }

            context.sendMessage("ok")
            Command.SINGLE_SUCCESS
        }
)

val nspManagement: RequiredArgumentBuilder<CommandSourceStack?, String?>? =
    Commands.argument("nameSpace", StringArgumentType.string())
    .then(userAdd)
    .then(userDel)
        .then(userList)


val nsp: LiteralCommandNode<CommandSourceStack?>? = Commands.literal("nsp").then(nspCreate).then(nspManagement).then(nsList)
    .build()