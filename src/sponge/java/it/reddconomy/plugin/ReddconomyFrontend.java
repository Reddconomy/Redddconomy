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
package it.reddconomy.plugin;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Iterator;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.spongepowered.api.Game;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.block.BlockState;
import org.spongepowered.api.block.BlockTypes;
import org.spongepowered.api.block.tileentity.TileEntity;
import org.spongepowered.api.command.CommandSource;
import org.spongepowered.api.command.args.GenericArguments;
import org.spongepowered.api.command.spec.CommandSpec;
import org.spongepowered.api.config.ConfigDir;
import org.spongepowered.api.data.key.Keys;
import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.api.event.Listener;
import org.spongepowered.api.event.game.state.GameInitializationEvent;
import org.spongepowered.api.event.game.state.GamePreInitializationEvent;
import org.spongepowered.api.event.game.state.GameStartedServerEvent;
import org.spongepowered.api.plugin.Plugin;
import org.spongepowered.api.scheduler.Task;
import org.spongepowered.api.text.Text;
import org.spongepowered.api.text.action.TextActions;
import org.spongepowered.api.text.format.TextColors;
import org.spongepowered.api.util.Direction;

import com.google.inject.Inject;

import it.reddconomy.Utils;
import it.reddconomy.common.data.OffchainWallet;
import it.reddconomy.plugin.commands.CommandHandler;
import it.reddconomy.plugin.commands.CommandListener;
import it.reddconomy.plugin.contracts.ContractInitialization;
//import it.reddconomy.plugin.contracts.ContractGUI;
import it.reddconomy.plugin.contracts.ContractSign;
import it.reddconomy.plugin.utils.FrontendUtils;

@Plugin(id="reddconomy-sponge", name="Reddconomy-sponge", version="0.2.0")
public class ReddconomyFrontend implements CommandListener{
	@Inject	private Game GAME;
	@Inject	private Logger LOGGER;
	@Inject @ConfigDir(sharedRoot=true) private File CONFIG_DIR;

	private boolean CAN_RUN=true;
	private final ConcurrentLinkedQueue<String> _PENDING_DEPOSITS=new ConcurrentLinkedQueue<String>();

	
	// Backend's info
	public static void showInfos(Player player) throws Exception {
		player.sendMessage(Text.of(TextColors.BLUE,"[REDDCONOMY INFO]"));
		player.sendMessage(Text.of(TextColors.GOLD,"On testnet? ",TextColors.WHITE,ReddconomyApi.getInfo().testnet));
		player.sendMessage(Text.of(TextColors.GOLD,"Coin: ",TextColors.WHITE,ReddconomyApi.getInfo().coin));
		player.sendMessage(Text.of(TextColors.GOLD,"Deposit fee: ",TextColors.WHITE,ReddconomyApi.getInfo().fees.getDepositFee().toString()));
		player.sendMessage(Text.of(TextColors.GOLD,"Withdraw fee: ",TextColors.WHITE,ReddconomyApi.getInfo().fees.getWithdrawFee().toString()));
		player.sendMessage(Text.of(TextColors.GOLD,"Transaction fee: ",TextColors.WHITE,ReddconomyApi.getInfo().fees.getTransactionFee().toString()));
	}



	// Help message of Reddconomy
	public static void sendHelpText(Player player) throws MalformedURLException {

		URL github=new URL("https://github.com/Reddconomy");
		Text moreinfo=Text.builder("Click here for more info!").color(TextColors.GOLD).onClick(TextActions.openUrl(github)).build();
		player.sendMessage(Text.of(TextColors.BLUE,"[REDDCONOMY HELP]"));
		player.sendMessage(Text.of(TextColors.BLUE,"=====[COMMANDS]====="));
		player.sendMessage(Text.of(TextColors.GOLD,"/$",TextColors.WHITE,": Shows info"));
		player.sendMessage(Text.of(TextColors.GOLD,"/$ help",TextColors.WHITE,": Shows this"));
		player.sendMessage(Text.of(TextColors.GOLD,"/$ deposit <amount>",TextColors.WHITE,": Get the deposit address."));
		player.sendMessage(Text.of(TextColors.GOLD,"/$ balance",TextColors.WHITE,": Shows your balance."));
		player.sendMessage(Text.of(TextColors.GOLD,"/$ withdraw <amount> <addr>",TextColors.WHITE,": Withdraw money."));
		player.sendMessage(Text.of(TextColors.GOLD,"/$ contract new <amount>",TextColors.WHITE,": Create contract. (- sign for giving, no sign for requesting)"));
		player.sendMessage(Text.of(TextColors.GOLD,"/$ contract accept <contractid>",TextColors.WHITE,": Accept a contract."));
		player.sendMessage(Text.of(TextColors.GOLD,"/$ tip <amount> <user>",TextColors.WHITE,": Tip an user."));
		player.sendMessage(Text.of(TextColors.GOLD,"/$ tipsrv <amount>",TextColors.WHITE,": Tip the server."));
		player.sendMessage(Text.of(TextColors.GOLD,"/$ info",TextColors.WHITE,": Get info from backend."));
		player.sendMessage(Text.of(TextColors.BLUE,"===[CONTRACT SIGNS]==="));
		player.sendMessage(Text.of("In order to make Contract Signs, you have to write in a sign:"));
		player.sendMessage(Text.of(TextColors.GOLD,"FIRST LINE:"," [CONTRACT] | ",TextColors.GOLD,"SECOND LINE: ","<amount>"));
		player.sendMessage(Text.of("Third line is for description, it's not necessary."));
		player.sendMessage(Text.of(TextColors.BLUE,"========[INFOs]========"));
		player.sendMessage(Text.of("Copyright (c) 2018, Riccardo Balbo, Simone Cervino. This plugin and all its components are released under GNU GPL v3 and BSD-2-Clause license."));
		player.sendMessage(moreinfo);
	}
	
	// Declaring default configuration and loading configuration's settings.
	@Listener
	public void onPreInit(GamePreInitializationEvent event) throws Exception {

		Config.init(new File(CONFIG_DIR,"reddconomy-sponge.json"));

		// Reddconomy-sponge can't start if the config is still the default one.
		if(Config.getValue("secretkey").toString().equals("changeme")){
			LOGGER.log(Level.SEVERE,"Reddconomy-sponge will not start until you modify the config.");
			CAN_RUN=false;
		}else{
			CAN_RUN=true;
		}

		// Initializing Reddconomy APIs
		ReddconomyApi.init(Config.getValue("url").toString(),Config.getValue("secretkey").toString());
		Task.builder().execute(ReddconomyApi::updateInfo).async().interval(60,TimeUnit.SECONDS).name("Update backend info").submit(this);

		//ContractGUI.init(this);
		ContractSign.init(this);
		AdminCommands.init(this);
		
		
	}

	@Listener
	public void onInit(GameInitializationEvent event) {
		if(CAN_RUN){
			LOGGER.log(Level.INFO,"Reddconomy-sponge is now activated.");
			CommandSpec cmds=CommandSpec.builder().description(Text.of("Command")).executor(new CommandHandler(this)).arguments(GenericArguments.optional(GenericArguments.remainingJoinedStrings(Text.of("args")))).build();
			GAME.getCommandManager().register(this,cmds,"$","reddconomy","rdd");
		}else LOGGER.log(Level.SEVERE,"Reddconomy-sponge is deactivated, an admin should modify the config.");
	}

	@Listener
	public void hasStarted(GameStartedServerEvent event) {
		// Start task that processes pending deposits
		Task.builder().execute(() -> {
			final Iterator<String> it=_PENDING_DEPOSITS.iterator();
			while(it.hasNext()){
				String addr=it.next();
				try{
					final PendingDepositData deposit_data=ReddconomyApi.getDepositStatus(addr);
					if(deposit_data.status!=1){
						it.remove();

						Task.builder().execute((new Runnable(){
							public void run() {
								final UUID pUUID=UUID.fromString(deposit_data.addr);
								(Sponge.getServer().getPlayer(pUUID)).get().sendMessage(Text.of(deposit_data.status==0?"Deposit completed. Check your balance!":"Deposit expired! Request another one."));
							}
						})).delay(0,TimeUnit.MILLISECONDS).name("Fetch deposit status").submit(this);

					}
				}catch(Exception e){
					e.printStackTrace();
				}
			}
		}).async().delay(1,TimeUnit.SECONDS).interval(10,TimeUnit.SECONDS).name("Process pending deposits").submit(this);
	}

	// All the commands of Reddconomy
	@Override
	public boolean onCommand(CommandSource src, String command, String[] args) {
		if(!(src instanceof Player)) return true;
		Player player=(Player)src;
		UUID pUUID=player.getUniqueId();
		try{
			boolean invalid=false;
			switch(command){
				// deposit
				case "deposit":{
					if(args.length<1){
						invalid=true;
						break;
					}
					double damount=Double.parseDouble(args[0]);
					long amount=Utils.convertToInternal(damount);
					String addr=ReddconomyApi.getAddrDeposit(amount,pUUID);
					if(addr!=null){
						FrontendUtils.sendQrToPlayer(addr,amount,player);
						player.sendMessage(Text.of("Deposit "+damount+" "+(ReddconomyApi.getInfo().testnet?"TEST ":" ")+ReddconomyApi.getInfo().coin_short+" to this address: "+addr));
						_PENDING_DEPOSITS.add(addr);
					}else player.sendMessage(Text.of(TextColors.DARK_RED,"Cannot create deposit address right now. Contact an admin."));
					break;
				}
				// balance
				case "balance":{
					double balance=Utils.convertToUserFriendly(ReddconomyApi.getWallet(pUUID).balance);
					if(balance!=-1) player.sendMessage(Text.of("You have: "+balance+" "+(ReddconomyApi.getInfo().testnet?"TEST ":" ")+ReddconomyApi.getInfo().coin_short));
					else player.sendMessage(Text.of(TextColors.DARK_RED,"Cannot request balance right now. Contact an admin."));
					break;
				}
				case "info":{
					showInfos(player);
					break;
				}
				// commands for OPs
				case "admin":{
					invalid=!AdminCommands.onCommand(player,command,args);
					break;
				}
				// withdraw
				case "withdraw":{
					if(args.length<2){
						invalid=true;
						break;
					}
					double damount=Double.parseDouble(args[0]);
					String addr=args[1];
					long amount=Utils.convertToInternal(damount);
					int status=ReddconomyApi.withdraw(amount,addr,pUUID);
					if(status==200){
						player.sendMessage(Text.of(TextColors.BLUE,"Withdrawing "+damount+" "+(ReddconomyApi.getInfo().testnet?"TEST ":" ")+ReddconomyApi.getInfo().coin_short+".. Wait at least 10 minutes"));
					}else player.sendMessage(Text.of(TextColors.DARK_RED,"Cannot request a withdraw right now, contact an admin."));
					break;
				}
				// tip
				case "tipsrv":
				case "tip":{
					boolean server=command.equals("tipsrv");
					if(args.length<2&&(!server||args.length<1)){
						invalid=true;
						break;
					}

					double damount=Double.parseDouble(args[0]);
					long amount=Utils.convertToInternal(damount);
					String to;
					Player receiver=null;
					if(server){
						to=ReddconomyApi.getInfo().generic_wallid;
					}else{
						to=FrontendUtils.getWalletIdFromPrefixedString(args[1]);
						OffchainWallet receiver_wallet=ReddconomyApi.getWallet(to);
						receiver=(Player)Sponge.getServer().getPlayer(UUID.fromString(receiver_wallet.id)).orElse(null);
					}
					long cId=ReddconomyApi.createContract(-amount,pUUID).id;
					//					if(cId!=null){
					int status=ReddconomyApi.acceptContract(cId,to);
					if(status==200){
						player.sendMessage(Text.of(TextColors.GOLD,damount+" "+(ReddconomyApi.getInfo().testnet?"TEST ":" ")+ReddconomyApi.getInfo().coin_short+" sent to the "+(server?"server":"user "+args[1])));
						if(receiver!=null) receiver.sendMessage(Text.of(TextColors.GOLD,player.getName()+" sent you a tip worth "+damount+" "+(ReddconomyApi.getInfo().testnet?"TEST ":" ")+ReddconomyApi.getInfo().coin_short+"!"));
					}else player.sendMessage(Text.of(TextColors.DARK_RED,"Cannot send tip, "+"check your balance or contact an admin."));
					//					}else player.sendMessage(Text.of(TextColors.DARK_RED,"Something went wrong, contact an admin."));
					break;
				}
				// contract
				case "contract":{
					if(args.length<2){
						invalid=true;
						break;
					}
					String method=args[0];
					String text=args[1];
					if(method.equals("new")){
						long amount=Utils.convertToInternal(Double.parseDouble(text));
						long cId=ReddconomyApi.createContract(amount,pUUID).id;
						player.sendMessage(Text.of("Share this Contract ID: "+cId));
						//						else player.sendMessage(Text.of(TextColors.DARK_RED,"Can't create contract right now. Contact an admin."));
					}else if(method.equals("accept")){
						long contractId=Long.parseLong(text);
						int status=ReddconomyApi.acceptContract(contractId,pUUID);
						if(status==200){
							player.sendMessage(Text.of(TextColors.GOLD,"Contract accepted."));
							player.sendMessage(Text.of("You have now: "+Utils.convertToUserFriendly(ReddconomyApi.getWallet(pUUID).balance)+" "+ReddconomyApi.getInfo().coin_short));
						}else{
							player.sendMessage(Text.of(TextColors.DARK_RED,"Cannot accept contract. Are you sure that you haven't already accepted?"));
							player.sendMessage(Text.of(TextColors.GOLD,"Otherwise, call and admin for more info."));
						}

					}
					break;
				}
				case "confirm":{
					try {
						ContractInitialization cdata = ContractSign.contract.remove(player);
						// Check if player wallet != owner wallet
						if(cdata.sign_location.getTileEntity().isPresent())
						{
							if(cdata.player_wallet.short_id!=cdata.owner_wallet.short_id||((boolean)Config.getValue("debug"))){
								int status=ReddconomyApi.acceptContract(cdata.contract.id,cdata.player_wallet.id);
								// This activates the redstone only if the contract replied with 200
								if(status==200){
									player.sendMessage(Text.of("Contract ID: "+cdata.contract.id));
									player.sendMessage(Text.of("Contract accepted."));
									player.sendMessage(Text.of("You have now: "+Utils.convertToUserFriendly(ReddconomyApi.getWallet(cdata.player_wallet.id).balance)+" "+ReddconomyApi.getInfo().coin_short));
	
									ContractSign._ACTIVATED_SIGNS.add(cdata.sign_location.getBlockPosition());
							
									cdata.sign_location.setBlockType(
											ContractSign._BLOCK_BLACKLIST.contains(cdata.sign_location.getRelative(Direction.DOWN).getBlockType())
											?BlockTypes.REDSTONE_BLOCK:BlockTypes.REDSTONE_TORCH);
									Task.builder().execute(() -> {
										if(cdata.sign_location.getBlock().getType()!=BlockTypes.REDSTONE_TORCH&&cdata.sign_location.getBlock().getType()!=BlockTypes.REDSTONE_BLOCK)return;
	
										BlockState state=cdata.sign.getDefaultState();
										BlockState newstate=state.with(Keys.DIRECTION,cdata.sign_direction).get();
										cdata.sign_location.setBlock(newstate);
										TileEntity tile2=cdata.sign_location.getTileEntity().get();
										ContractSign.setLine(tile2,0,cdata.sign_lines[0]);
										ContractSign.setLine(tile2,1,cdata.sign_lines[1]);
										ContractSign.setLine(tile2,2,cdata.sign_lines[2]);
										ContractSign.setLine(tile2,3,cdata.sign_lines[3]);
										ContractSign._ACTIVATED_SIGNS.remove(cdata.sign_location.getBlockPosition());
									}).delay(cdata.delay,TimeUnit.MILLISECONDS).name("Powering off Redstone.").submit(this);
								}else{
									player.sendMessage(Text.of(TextColors.DARK_RED,"Check your balance. Cannot accept contract"));
								}
							}else{
								player.sendMessage(Text.of(TextColors.DARK_RED,"You can't accept your own contract."));
							}
						}

					}catch(Exception e){
						player.sendMessage(Text.of(TextColors.DARK_RED,"Cannot create/accept contract. Maybe already accepted?"));
						e.printStackTrace();

					}
					break;
				}
				case "decline":{
					ContractSign.contract.remove(player);
					player.sendMessage(Text.of("Contract declined."));
					break;
				}
				default:
				case "help":{
					sendHelpText(player);
					break;
				}
			}

			if(invalid){
				player.sendMessage(Text.of(TextColors.DARK_RED,"Invalid Command"));
				sendHelpText(player);
			}
		}catch(Exception e){
			e.printStackTrace();
			player.sendMessage(Text.of(TextColors.DARK_RED,"Unexpected error"));
		}
		return true;
	}
}