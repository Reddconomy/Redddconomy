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
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.spongepowered.api.Game;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.block.BlockState;
import org.spongepowered.api.block.BlockType;
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
import org.spongepowered.api.text.format.TextStyles;
import org.spongepowered.api.util.Direction;
import org.spongepowered.api.world.LocatableBlock;

import com.google.inject.Inject;

import it.reddconomy.Utils;
import it.reddconomy.common.ApiResponse;
import it.reddconomy.common.data.OffchainWallet;
import it.reddconomy.common.data.Withdraw;
import it.reddconomy.plugin.commands.CommandHandler;
import it.reddconomy.plugin.commands.CommandListener;
import it.reddconomy.plugin.contracts.ContractSign;
import it.reddconomy.plugin.contracts.SignInitialization;
import it.reddconomy.plugin.utils.FrontendUtils;

@Plugin(id="reddconomy-sponge", name="Reddconomy-sponge", version="0.2.1")
public class ReddconomyFrontend implements CommandListener{
	@Inject	private Game GAME;
	@Inject	private Logger LOGGER;
	@Inject @ConfigDir(sharedRoot=true) private File CONFIG_DIR;

	private boolean CAN_RUN=true;
	private final ConcurrentLinkedQueue<String> _PENDING_DEPOSITS=new ConcurrentLinkedQueue<String>();
	private final Map<Player,String> _PENDING_WITHDRAW=new WeakHashMap<Player,String>();

	
	// Backend's info
	public static void showInfos(Player player) throws Exception {
		player.sendMessage(Text.of(TextColors.BLUE,"[REDDCONOMY INFO]"
							+ "\n",TextColors.GOLD,"On testnet? ",TextColors.WHITE,ReddconomyApi.getInfo().testnet
							+ "\n",TextColors.GOLD,"Coin: ",TextColors.WHITE,ReddconomyApi.getInfo().coin
							+ "\n",TextColors.GOLD,"Deposit fee: ",TextColors.WHITE,ReddconomyApi.getInfo().fees.getDepositFee().toString()
							+ "\n",TextColors.GOLD,"Withdraw fee: ",TextColors.WHITE,ReddconomyApi.getInfo().fees.getWithdrawFee().toString()
							+ "\n",TextColors.GOLD,"Transaction fee: ",TextColors.WHITE,ReddconomyApi.getInfo().fees.getTransactionFee().toString()));
	}

	// Help message of Reddconomy
	public static void sendHelpText(Player player) throws MalformedURLException {
		Text license =Text.builder("Read the license by clicking here").style(TextStyles.BOLD).color(TextColors.GOLD).onHover(TextActions.showText(Text.of("Copyright (c) 2018, Riccardo Balbo, Simone Cervino. This plugin and all its components are released under GNU GPL v3 and BSD-2-Clause license."))).onClick(TextActions.openUrl(new URL("https://github.com/Reddconomy/Reddconomy/blob/master/LICENSE.md"))).build();
		Text moreinfo=Text.builder("Click here for more info about Reddconomy!").style(TextStyles.BOLD).color(TextColors.GOLD).onClick(TextActions.openUrl(new URL("https://github.com/Reddconomy"))).build();
		player.sendMessage(Text.of(TextColors.BLUE,"[REDDCONOMY HELP]"
							+ "\n",TextColors.BLUE,"=====[COMMANDS]====="
							+ "\n",TextColors.GOLD,"/$",TextColors.WHITE,": Shows info"
							+ "\n",TextColors.GOLD,"/$ help",TextColors.WHITE,": Shows this"
							+ "\n",TextColors.GOLD,"/$ deposit <amount>",TextColors.WHITE,": Get the deposit address."
							+ "\n",TextColors.GOLD,"/$ balance",TextColors.WHITE,": Shows your balance."
							+ "\n",TextColors.GOLD,"/$ withdraw <amount> <addr>",TextColors.WHITE,": Withdraw money."
							+ "\n",TextColors.GOLD,"/$ tip <amount> <user>",TextColors.WHITE,": Tip an user."
							+ "\n",TextColors.GOLD,"/$ tipsrv <amount>",TextColors.WHITE,": Tip the server."
							+ "\n",TextColors.GOLD,"/$ info",TextColors.WHITE,": Get info from backend."
							+ "\n",TextColors.BLUE,"===[CONTRACT SIGNS]==="
							+ "\n",TextColors.WHITE,"In order to make Contract Signs, you have to write in a sign:"
							+ "\n",TextColors.GOLD,"FIRST LINE:"," [CONTRACT] | ",TextColors.GOLD,"SECOND LINE: ","<amount>"
							+ "\n",TextColors.WHITE,"Third line is for description, it's not necessary."
							+ "\n",TextColors.BLUE,"========[INFOs]========"
							+ "\n",TextColors.WHITE,"Copyright \u00A9 2018, Riccardo Balbo, Simone Cervino. "
							+ "\n",license,
							  "\n",moreinfo));
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
			switch(command.toLowerCase()){
				// deposit
				case "deposit":{
					if(!((boolean)Config.getValue("deposit"))) {
						player.sendMessage(Text.of(TextColors.DARK_RED,"Deposits are disabled."));
						break;
					}
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
					if(!((boolean)Config.getValue("withdraw"))) {
						player.sendMessage(Text.of(TextColors.DARK_RED,"Withdrawals are disabled."));
						break;
					}
					if(args.length<2){
						if(args.length==1){
							if(args[0].equals("confirm")){
								String id=_PENDING_WITHDRAW.get(player);
								ApiResponse resp=ReddconomyApi.withdraw_confirm(id);
								if(resp.statusCode()==200){
									Withdraw wt=resp.data();
									player.sendMessage(Text.of(TextColors.GREEN,"Confirmed!\nTransaction ID:"));
									String trid_url=Config.getValue("trid_viewer").toString().replace("{TRID}",wt.id);
									player.sendMessage(Text.builder(wt.id).color(TextColors.GOLD)
											.onClick(TextActions.openUrl(new URL(trid_url))).build());
								}else{
									player.sendMessage(Text.of(TextColors.RED,"Error: "+resp.status()));
								}
								break;
							}
						}else{
							invalid=true;
							break;
						}
					}
					double damount=Double.parseDouble(args[0]);
					String addr=args[1];
					long amount=Utils.convertToInternal(damount);
					ApiResponse resp=ReddconomyApi.withdraw(amount,addr,pUUID,false);
					if(resp.statusCode()==200){
						Withdraw wt=resp.data();
						StringBuilder tx=new StringBuilder();
						tx.append("You asked to withdraw ");
						tx.append(damount).append(FrontendUtils.getCoinString()).append(" to your address ").append(addr);
						tx.append("\nWithdraw fee: ").append(ReddconomyApi.getInfo().fees.getWithdrawFee().toString());
						tx.append("\nBlockchain fee: ").append(ReddconomyApi.getInfo().fees.getBlockchainFee().toString());
						tx.append("\nYou will receive: ").append(Utils.convertToUserFriendly(wt.amount_net)).append(FrontendUtils.getCoinString());
						player.sendMessage(Text.of(TextColors.BLUE,tx.toString()));
						if(!wt.confirmed){
							player.sendMessage(Text.of(TextColors.GRAY,"Use /$ withdraw confirm to confirm"));

							_PENDING_WITHDRAW.put(player,wt.id);
						}else{
							player.sendMessage(Text.of(TextColors.GREEN,"Transaction ID:"));
							String trid_url=Config.getValue("trid_viewer").toString().replace("{TRID}",wt.id);
							player.sendMessage(Text.builder(wt.id).color(TextColors.GOLD)
									.onClick(TextActions.openUrl(new URL(trid_url))).build());

						}
					}else player.sendMessage(Text.of(TextColors.DARK_RED,"Error: "+resp.status()));
					break;
				}
				// tip
				case "tipsrv":
				case "tip":{
					if(!((boolean)Config.getValue("tips"))) {
						player.sendMessage(Text.of(TextColors.DARK_RED,"Tips are disabled."));
						break;
					}
					boolean server=command.equals("tipsrv");
					if(args.length<2&&(!server||args.length<1)){
						invalid=true;
						break;
					}
					double damount=Double.parseDouble(args[0]);
					if (damount<0) {
						invalid=true;
						break;
					}
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
					int status=ReddconomyApi.acceptContract(cId,to);
					if(status==200){
						player.sendMessage(Text.of(TextColors.GOLD,damount+" "+(ReddconomyApi.getInfo().testnet?"TEST ":" ")+ReddconomyApi.getInfo().coin_short+" sent to the "+(server?"server":"user "+args[1])));
						if(receiver!=null) receiver.sendMessage(Text.of(TextColors.GOLD,player.getName()+" sent you a tip worth "+damount+" "+(ReddconomyApi.getInfo().testnet?"TEST ":" ")+ReddconomyApi.getInfo().coin_short+"!"));
					}else player.sendMessage(Text.of(TextColors.DARK_RED,"Cannot send tip, "+"check your balance or contact an admin."));
					break;
				}
				// Confirm and decline sign
				case "confirmsign":{
					try {
						SignInitialization cdata = ContractSign.csign.remove(player);
						// Check if player wallet != owner wallet
						if(cdata.player_wallet.short_id!=cdata.owner_wallet.short_id||((boolean)Config.getValue("debug"))){
							int status=ReddconomyApi.acceptContract(cdata.contract.id,cdata.player_wallet.id);
							// This activates the redstone only if the contract replied with 200
							if(status==200){
								player.sendMessage(Text.of("Contract ID: "+cdata.contract.id
										+"\nContract accepted."
										+"\nYou have now: "+Utils.convertToUserFriendly(ReddconomyApi.getWallet(cdata.player_wallet.id).balance)+" "+ReddconomyApi.getInfo().coin_short));

								ContractSign._ACTIVATED_SIGNS.add(cdata.sign_location.getBlockPosition());
													
								// TODO: Check if the sign can be converted to a torch, if can't, it tries to put a wall torch and if still can't, it'll put a redstone block.
								if (ContractSign._BLOCK_BLACKLIST.contains(cdata.sign_location.getRelative(Direction.DOWN).getBlockType()))
										if (ContractSign.canHostWallTorch(cdata.sign_location,Direction.EAST)) {
											cdata.sign_location.setBlock(ContractSign.doWallTorch(cdata.sign_location,Direction.EAST));
										} else if (ContractSign.canHostWallTorch(cdata.sign_location,Direction.WEST)) {
											cdata.sign_location.setBlock(ContractSign.doWallTorch(cdata.sign_location,Direction.WEST));
										} else if (ContractSign.canHostWallTorch(cdata.sign_location,Direction.NORTH)) {
											cdata.sign_location.setBlock(ContractSign.doWallTorch(cdata.sign_location,Direction.NORTH));
										} else if (ContractSign.canHostWallTorch(cdata.sign_location,Direction.SOUTH)) {
											cdata.sign_location.setBlock(ContractSign.doWallTorch(cdata.sign_location,Direction.SOUTH));
										} else cdata.sign_location.setBlock(BlockTypes.REDSTONE_BLOCK.getDefaultState());
								else cdata.sign_location.setBlock(BlockTypes.REDSTONE_TORCH.getDefaultState());
									
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

					}catch(Exception e){
						player.sendMessage(Text.of(TextColors.DARK_RED,"Cannot create/accept contract. Maybe already accepted?"));
						e.printStackTrace();

					}
					break;
				}
				case "declinesign":{
					ContractSign.csign.remove(player);
					player.sendMessage(Text.of("Contract declined."));
					break;
				}
				// Help message
				default:
				case "help":{
					sendHelpText(player);
					break;
				}
			}
			// If the command is invalid, show this.
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