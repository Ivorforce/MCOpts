/*
 *  Copyright (c) 2014, Lukas Tenbrink.
 *  * http://ivorius.net
 */

package ivorius.mcopts.commands.parameters;

import ivorius.mcopts.MCOpts;
import ivorius.mcopts.accessor.AccessorBiomeDictionary;
import net.minecraft.block.Block;
import net.minecraft.command.*;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.Item;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.Rotation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraft.world.biome.Biome;
import net.minecraftforge.common.BiomeDictionary;

import java.util.List;
import java.util.function.Function;

/**
 * Created by lukas on 31.05.17.
 */
public class MCP
{
    // Since CommandBase's version requires a sender
    public static BlockPos parseBlockPos(BlockPos blockpos, String[] args, int startIndex, boolean centerBlock) throws NumberInvalidException
    {
        return new BlockPos(CommandBase.parseDouble((double) blockpos.getX(), args[startIndex], -30000000, 30000000, centerBlock), CommandBase.parseDouble((double) blockpos.getY(), args[startIndex + 1], 0, 256, false), CommandBase.parseDouble((double) blockpos.getZ(), args[startIndex + 2], -30000000, 30000000, centerBlock));
    }

    public static Function<Parameter<String>, Parameter<BlockPos>> pos(Parameter<String> yp, Parameter<String> zp, BlockPos ref, boolean centerBlock)
    {
        return xp -> xp.orElse("~").flatMap(x ->
                yp.orElse("~").flatMap(y ->
                        zp.orElse("~").map(z ->
                                parseBlockPos(ref, new String[]{x, y, z}, 0, centerBlock)
                        )));
    }

    public static Function<Parameter<String>, Parameter<BlockPos>> pos(BlockPos ref, boolean centerBlock)
    {
        return p -> pos(p.move(1), p.move(2), ref, centerBlock).apply(p);
    }

    public static Function<Parameters, Parameter<BlockPos>> pos(String x, String y, String z, BlockPos ref, boolean centerBlock)
    {
        return p -> p.get(x).to(pos(p.get(y), p.get(z), ref, centerBlock));
    }

    public static Parameter<Biome> biome(Parameter<String> p)
    {
        return p.map(ResourceLocation::new)
                .map(Biome.REGISTRY::getObject, t -> MCOpts.translations.commandException("commands.parameters.biome.invalid"));
    }

    public static Parameter<BiomeDictionary.Type> biomeDictionaryType(Parameter<String> p)
    {
        return p.map(AccessorBiomeDictionary::getTypeWeak, s -> MCOpts.translations.commandException("commands.parameters.biometype.invalid", s));
    }

    public static Parameter<WorldServer> dimension(Parameter<String> p, MinecraftServer server)
    {
        return p.filter(d -> !d.equals("~"), null)
                .map(CommandBase::parseInt).map(server::getWorld, t -> MCOpts.translations.commandException("commands.parameters.dimension.invalid"));
    }

    public static Function<Parameter<String>, Parameter<WorldServer>> dimension(MinecraftServer server, ICommandSender sender)
    {
        return p -> dimension(p, server).orElse((WorldServer) sender.getEntityWorld());
    }

    public static Parameter<Block> block(Parameter<String> p, ICommandSender commandSender)
    {
        return p.map(s -> CommandBase.getBlockByText(commandSender, s));
    }

    public static Parameter<Item> item(Parameter<String> p, ICommandSender commandSender)
    {
        return p.map(s -> CommandBase.getItemByText(commandSender, s));
    }

    public static Parameter<ICommand> command(Parameter<String> p, MinecraftServer server)
    {
        return p.map(server.getCommandManager().getCommands()::get, s -> new CommandNotFoundException());
    }

    public static Function<Parameter<String>, Parameter<Entity>> entity(MinecraftServer server, ICommandSender sender)
    {
        return p -> p.map(s -> CommandBase.getEntity(server, sender, s));
    }

    public static <T extends Entity> Function<Parameter<String>, Parameter<Entity>> entity(MinecraftServer server, ICommandSender sender, Class<T> targetClass)
    {
        return p -> p.map(s -> CommandBase.getEntity(server, sender, s, targetClass));
    }

    public static Function<Parameter<String>, Parameter<List<Entity>>> entities(MinecraftServer server, ICommandSender sender)
    {
        return p -> p.map(s -> CommandBase.getEntityList(server, sender, s));
    }

    public static Function<Parameter<String>, Parameter<EntityPlayerMP>> player(MinecraftServer server, ICommandSender sender)
    {
        return p -> p.map(s -> CommandBase.getPlayer(server, sender, s));
    }

    public static Function<Parameter<String>, Parameter<List<EntityPlayerMP>>> players(MinecraftServer server, ICommandSender sender)
    {
        return p -> p.map(s -> CommandBase.getPlayers(server, sender, s));
    }

    public static Function<Parameter<String>, Parameter<String>> playerName(MinecraftServer server, ICommandSender sender)
    {
        return p -> p.map(s -> CommandBase.getPlayerName(server, sender, s));
    }

    public static Function<Parameter<String>, Parameter<String>> entityName(MinecraftServer server, ICommandSender sender)
    {
        return p -> p.map(s -> CommandBase.getEntityName(server, sender, s));
    }

    public static Parameter<Rotation> rotation(Parameter<String> p)
    {
        return p.map(CommandBase::parseInt)
                .map(i -> i > 40 ? i / 90 : i)
                .map(MCP::rotationFromInt);
    }

    public static Rotation rotationFromInt(int rotation)
    {
        switch (((rotation % 4) + 4) % 4)
        {
            case 0:
                return Rotation.NONE;
            case 1:
                return Rotation.CLOCKWISE_90;
            case 2:
                return Rotation.CLOCKWISE_180;
            case 3:
                return Rotation.COUNTERCLOCKWISE_90;
            default:
                throw new IllegalStateException();
        }
    }
}
