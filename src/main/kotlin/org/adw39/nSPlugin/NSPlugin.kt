package org.adw39.nSPlugin

import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents
import org.bukkit.plugin.java.JavaPlugin
import org.spongepowered.configurate.CommentedConfigurationNode
import org.spongepowered.configurate.kotlin.extensions.get
import org.spongepowered.configurate.objectmapping.ConfigSerializable
import org.spongepowered.configurate.yaml.YamlConfigurationLoader
import java.util.*
import kotlin.collections.mutableMapOf
import kotlin.collections.set

enum class Role {
    Admin,
    User;

    companion object {
        fun fromString(str: String): Result<Role> {
            return runCatching {
                val lowerStr = str.lowercase()
                when (lowerStr) {
                    "admin" -> Admin
                    "user" -> User
                    else -> throw IllegalArgumentException("Unknown role: $str")
                }
            }
        }
        fun toString(role: Role): String {
            return when (role) {
                Admin -> "Admin"
                User -> "User"
            }
        }
    }
}

@ConfigSerializable
class Namespace(
    var name: String?,
    var owner: UUID?,
    val member: MutableMap<UUID, Role>,
) {
    constructor(name: String, owner: UUID): this(name, owner, mutableMapOf(
        owner to Role.Admin,
    ))
    constructor(): this(null, null, mutableMapOf())

    fun existsMember(p: UUID): Boolean {
        return p in member
    }
    fun isOwner(p: UUID): Boolean {
        return owner == p
    }
    fun isAdmin(p: UUID): Boolean {
        return member[p] == Role.Admin
    }
    fun addMember(p: UUID, role: Role) {
        if (this.existsMember(p)) {
            throw IllegalStateException("member is already exists")
        } else {
            this.member.putIfAbsent(p, role)
        }
    }
    fun delMember(p: UUID) {
        if (!existsMember(p)) {
            throw IllegalArgumentException("Member does not exist")
        }
        if (isOwner(p)) {
            throw IllegalArgumentException("this member is owner")
        }
        member.remove(p)
    }
    fun lsMember(): Map<UUID, Role> {
        return member
    }
    fun modMember(p: UUID, role: Role) {
        if (!existsMember(p)) {
            throw IllegalArgumentException("Member does not exist")
        }
        if (isOwner(p)) {
            throw IllegalArgumentException("this member is owner")
        }
        member[p] = role
    }
}

@ConfigSerializable
class NameSpaceManager(val namespaces: MutableMap<String, Namespace>) {
    constructor(): this(mutableMapOf())

    fun createNamespace(name: String, owner: UUID): Result<String> {
        if (existsNs(name)){
            return Result.failure(IllegalStateException("Namespace already exists"))
        }
        namespaces[name] = Namespace(name, owner)
        return Result.success(name)
    }

    fun createUniqueNamespace(name: String, owner: UUID): String {
        return if (existsNs(name)) {
            createUniqueNamespace("${name}_", owner) //underbarをつける
        } else {
            namespaces[name] = Namespace(name, owner)
            name
        }
    }

    fun existsNs(name: String): Boolean {
        return name in namespaces.keys
    }

    fun getNamespace(name: String): Namespace {
        return namespaces[name] ?: throw IllegalArgumentException("Namespace not found")
    }

    fun addMember(name: String, sender: UUID, member: UUID, role: Role) {
        val ns = getNamespace(name)
        if (!ns.isAdmin(sender)) {
            throw IllegalArgumentException("sender is not a Admin")
        }
        ns.addMember(member, role)
    }

    fun delMember(name: String, sender: UUID, member: UUID) {
        val ns = getNamespace(name)
        if (!ns.isAdmin(sender)) {
            throw IllegalArgumentException("sender is not a Admin")
        }
        ns.delMember(member)
    }

    fun lsMember(name: String): Map<UUID, Role> {
        val ns = getNamespace(name)
        return ns.lsMember()
    }

    fun allNs(): Set<String> {
        return namespaces.keys
    }

    fun memberJoinedNs(member: UUID): Set<String> {
        return namespaces.filterValues { it.existsMember(member) }.keys
    }
}

class NSPlugin : JavaPlugin() {
    val config = YamlConfigurationLoader.builder().path(
        dataPath.resolve("namespace.yaml"),
    ).build()
    lateinit var configRoot: CommentedConfigurationNode

    lateinit var nsm: NameSpaceManager

    override fun onEnable() {
        configRoot = runCatching { config?.load() }.getOrElse {
            logger.finest(it.localizedMessage)
            null
        } ?: run {
            logger.info("config loader is missing")
            server.pluginManager.disablePlugin(this)
            return
        }

        logger.info("loaded config")
        nsm = configRoot.get() ?: NameSpaceManager()

        logger.info("setup plugin")
        server.pluginManager.registerEvents(Listener(), this)
        this.lifecycleManager.registerEventHandler(LifecycleEvents.COMMANDS) {
            it.registrar().register(nsp!!)
        }
        logger.info("nsp enabled")
    }

    override fun onDisable() {
        logger.info("save config")
        configRoot.set(nsm)
        runCatching { config!!.save(configRoot) }.getOrElse {
            logger.finest(it.localizedMessage)
        }
    }
}
