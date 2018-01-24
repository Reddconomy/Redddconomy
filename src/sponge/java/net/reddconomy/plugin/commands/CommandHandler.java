/*
 * Copyright (c) 2018, Simone Cervino.
 * 
 * This file is part of Reddconomy-sponge.

    Reddconomy-sponge is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    Reddconomy-sponge is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with Reddconomy-sponge.  If not, see <http://www.gnu.org/licenses/>.
 */
package net.reddconomy.plugin.commands;

import java.util.Arrays;
import org.spongepowered.api.command.CommandException;
import org.spongepowered.api.command.CommandResult;
import org.spongepowered.api.command.CommandSource;
import org.spongepowered.api.command.args.CommandContext;
import org.spongepowered.api.command.spec.CommandExecutor;

import net.reddconomy.plugin.commands.CommandListener;

public class CommandHandler  implements CommandExecutor {


	private final CommandListener _LISTENER;
	public CommandHandler(CommandListener listener){
		_LISTENER=listener;
	}

	@Override
	public CommandResult execute(CommandSource src, CommandContext fargs) throws CommandException {
		
		
		String[] args= fargs.getOne("args").orElse("").toString().split(" ");
		
		String cmd="info";
		if(args.length>0){
			
			cmd= args[0];
			String argss[] = new String[args.length-1];
			System.arraycopy(args, 1, argss, 0, argss.length);
			args = argss;
		}
    	System.out.println("Execute command "+cmd+" "+Arrays.deepToString(args));
		boolean ret = _LISTENER.onCommand(src, cmd, args);
		return ret ? CommandResult.success() : CommandResult.empty();
	}
}