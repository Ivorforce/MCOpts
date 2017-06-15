/*
 *  Copyright (c) 2014, Lukas Tenbrink.
 *  * http://ivorius.net
 */

package ivorius.mcopts.commands;

import ivorius.mcopts.commands.parameters.Parameters;
import ivorius.mcopts.commands.parameters.expect.Expect;
import net.minecraft.command.ICommandSender;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;

import javax.annotation.Nullable;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Created by lukas on 01.06.17.
 */
public abstract class SimpleCommand extends CommandExpecting
{
    public String name;

    public String usage;
    public Consumer<Expect> expector;

    public int permissionLevel = 4;

    public SimpleCommand(String name)
    {
        this.name = name;
    }

    public SimpleCommand(String name, Consumer<Expect> expector)
    {
        this.name = name;
        this.usage = Parameters.expect().then(expector).usage();
        this.expector = expector;
    }

    public SimpleCommand(String name, String usage, Consumer<Expect> expector)
    {
        this.name = name;
        this.usage = usage;
        this.expector = expector;
    }

    @Override
    public int getRequiredPermissionLevel()
    {
        return permissionLevel;
    }

    public SimpleCommand permitFor(int level)
    {
        this.permissionLevel = level;
        return this;
    }

    @Override
    public String getName()
    {
        return name;
    }

    @Override
    public void expect(Expect expect)
    {
        if (expector != null)
            expector.accept(expect);
    }

    @Override
    public String getUsage(ICommandSender sender)
    {
        return String.format("%s %s", name, usage != null ? usage : expect().usage());
    }

    @Override
    public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args, @Nullable BlockPos targetPos)
    {
        return expect().get(server, sender, args, targetPos);
    }
}
