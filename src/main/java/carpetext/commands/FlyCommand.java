package carpetext.commands;

import carpetext.CarpetExtSettings;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.LiteralText;
import net.minecraft.util.Util;

import static net.minecraft.server.command.CommandManager.literal;

public class FlyCommand {
    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        LiteralArgumentBuilder<ServerCommandSource> command = literal("fly")
                .requires((player) -> CarpetExtSettings.commandFly).executes(c -> {
                    ServerPlayerEntity playerEntity = c.getSource().getPlayer();
                    if (!playerEntity.abilities.allowFlying) {
                        playerEntity.abilities.allowFlying = true;
                        playerEntity.sendSystemMessage(new LiteralText("Fly on"), Util.NIL_UUID);
                    } else {
                        playerEntity.abilities.allowFlying = false;
                        playerEntity.abilities.flying = false;
                        playerEntity.sendSystemMessage(new LiteralText("Fly off"), Util.NIL_UUID);
                    }
                    playerEntity.sendAbilitiesUpdate();
                    return 1;
                });
        dispatcher.register(command);
    }
}
