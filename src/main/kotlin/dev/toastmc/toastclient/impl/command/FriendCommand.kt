package dev.toastmc.toastclient.impl.command

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.builder.RequiredArgumentBuilder
import com.mojang.brigadier.exceptions.CommandSyntaxException
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType
import dev.toastmc.toastclient.api.managers.FriendManager
import dev.toastmc.toastclient.api.managers.command.Command
import dev.toastmc.toastclient.api.util.*
import dev.toastmc.toastclient.mixin.client.IEntitySelector
import net.minecraft.client.network.PlayerListEntry
import net.minecraft.command.CommandSource
import net.minecraft.command.EntitySelector
import net.minecraft.command.argument.EntityArgumentType
import net.minecraft.text.*
import net.minecraft.util.Formatting
import java.util.*
import java.util.function.BiFunction
import java.util.function.Function

/**
 * @author 086
 */
object FriendCommand : Command("friend") {
    override fun register(dispatcher: CommandDispatcher<CommandSource>) {
        dispatcher register rootLiteral(label) {
            literal("list") {
                does {
                    if(FriendManager.friends.isEmpty()) {
                        message(lit("$prefix You have no friends").formatted(Formatting.GRAY))
                    } else {
                        var text: MutableText? = null
                        FriendManager.friends.forEach {
                            if(text == null) {
                                text = lit("${Formatting.GREEN}${it.name}")
                                    .setStyle(Style.EMPTY
                                        .withHoverEvent(HoverEvent(HoverEvent.Action.SHOW_TEXT, lit(
                                            "${Formatting.DARK_AQUA}Name: ${Formatting.GREEN}${it.name}"+
                                                    "\n${Formatting.DARK_AQUA}UUID: ${Formatting.GREEN}${it.uuid}"
                                            )
                                        )
                                    )
                                )
                            }
                            else {
                                val friend: Text = lit("${Formatting.DARK_GRAY}, ${Formatting.GREEN}${it.name}")
                                    .setStyle(Style.EMPTY
                                    .withHoverEvent(HoverEvent(HoverEvent.Action.SHOW_TEXT, lit(
                                        "${Formatting.DARK_AQUA}Name: ${Formatting.GREEN}${it.name}"+
                                                "\n${Formatting.DARK_AQUA}UUID: ${Formatting.GREEN}${it.uuid}"
                                            )
                                        )
                                    )
                                )
                                text?.append(friend)
                            }
                        }
                        message(lit("$prefix You are friends with: ").append(text).formatted(Formatting.GRAY))
                    }
                    0
                }
            }
            literal("add") {
                then(
                    createFriendArgument(
                        Function { entry: PlayerListEntry ->
                            if (FriendManager.isFriend(entry.profile.name)) {
                                return@Function FAILED_EXCEPTION.create(
                                    "That player is already your friend!"
                                )
                            }
                            null
                        },
                        { entry: PlayerListEntry, _: CommandSource ->
                            FriendManager.addFriend(entry.profile)
                            message(lit("$prefix ${entry.profile.name} has been ${Formatting.GREEN}added${Formatting.GRAY} as friend").formatted(Formatting.GRAY))
                            0
                        }
                    )
                )
            }
            literal("remove") {
                then(
                    createFriendArgument(
                        Function { entry: PlayerListEntry ->
                            if (!FriendManager.isFriend(entry.profile.name)) {
                                return@Function FAILED_EXCEPTION.create(
                                    "That player isn't your friend!"
                                )
                            }
                            null
                        },
                        { entry: PlayerListEntry, _: CommandSource ->
                            FriendManager.removeFriend(entry.profile)
                            message(lit("$prefix ${entry.profile.name} has been ${Formatting.RED}removed${Formatting.GRAY} as friend").formatted(Formatting.GRAY))
                            0
                        }
                    )
                )
            }
        }
    }

    private val FAILED_EXCEPTION = DynamicCommandExceptionType { o: Any -> lit(o.toString()) }

    private fun createFriendArgument(
        fail: Function<PlayerListEntry, CommandSyntaxException?>,
        function: BiFunction<PlayerListEntry, CommandSource, Int>,
    ): RequiredArgumentBuilder<CommandSource, EntitySelector> {
        return RequiredArgumentBuilder.argument<CommandSource, EntitySelector>(
            "friend",
            EntityArgumentType.player()
        )
            .executes { ctx ->
                val selector: EntitySelector = "friend" from ctx
                val optionalPlayer =
                    mc.networkHandler!!.playerList.stream()
                        .filter { playerListEntry: PlayerListEntry ->
                            val playerName = (selector as IEntitySelector).playerName
                            if (playerName != null) {
                                return@filter playerListEntry.profile.name.equals(playerName, ignoreCase = true)
                            } else {
                                return@filter playerListEntry.profile.id == (selector as IEntitySelector).uuid
                            }
                        }.findAny()
                if (optionalPlayer.isPresent) {
                    val entry = optionalPlayer.get()
                    val e = fail.apply(entry)
                    if (e != null) {
                        throw e
                    }
                    return@executes function.apply(entry, ctx.source as CommandSource)
                } else { // TODO: Process offline players
                    throw FAILED_EXCEPTION.create("Couldn't find that player.")
                }
            }
    }
}