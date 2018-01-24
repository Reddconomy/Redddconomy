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
package net.reddconomy.plugin;
import net.reddconomy.plugin.api.PendingDepositData;
import net.reddconomy.plugin.api.ReddconomyApi;
import net.reddconomy.plugin.commands.CommandHandler;
import net.reddconomy.plugin.commands.CommandListener;
import net.reddconomy.plugin.utils.FrontendUtils;
import net.reddconomy.plugin.utils.Help;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.inject.Inject;

import org.spongepowered.api.data.Transaction;
import org.spongepowered.api.data.key.Keys;
import org.spongepowered.api.plugin.Plugin;
import org.spongepowered.api.scheduler.Task;
import org.spongepowered.api.text.Text;
import org.spongepowered.api.text.action.TextActions;
import org.spongepowered.api.text.format.TextColors;
import org.spongepowered.api.util.Direction;
import org.spongepowered.api.world.Location;
import org.spongepowered.api.world.World;
import org.spongepowered.api.Game;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.block.BlockSnapshot;
import org.spongepowered.api.block.BlockState;
import org.spongepowered.api.block.BlockType;
import org.spongepowered.api.block.BlockTypes;
import org.spongepowered.api.block.tileentity.Sign;
import org.spongepowered.api.block.tileentity.TileEntity;
import org.spongepowered.api.block.tileentity.carrier.Dispenser;
import org.spongepowered.api.block.tileentity.carrier.Dropper;
import org.spongepowered.api.command.CommandSource;
import org.spongepowered.api.command.args.GenericArguments;
import org.spongepowered.api.command.spec.CommandSpec;
import org.spongepowered.api.config.ConfigDir;
import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.api.event.Listener;
import org.spongepowered.api.event.Order;
import org.spongepowered.api.event.block.ChangeBlockEvent;
import org.spongepowered.api.event.block.InteractBlockEvent;
import org.spongepowered.api.event.block.tileentity.ChangeSignEvent;
import org.spongepowered.api.event.game.state.GameInitializationEvent;
import org.spongepowered.api.event.game.state.GamePreInitializationEvent;
import org.spongepowered.api.event.game.state.GameStartedServerEvent;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.net.URL;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

@Plugin(id="reddconomy-sponge", name="Reddconomy-sponge", version="0.1.1")

public class ReddconomyFrontend implements CommandListener{

	// Declaring fundamental functions
	private String API_QR;
	private boolean PLUGIN_CONTRACT_SIGNS;
	private boolean CAN_RUN=true;
	private boolean DEBUG;
	private final Map<Location<World>,Boolean> _ACTIVATED_SIGNS=new WeakHashMap<Location<World>,Boolean>();
	private final ConcurrentLinkedQueue<String> _PENDING_DEPOSITS=new ConcurrentLinkedQueue<String>();
	
	// CONFIG BLOCK
	private static final Gson _JSON=new GsonBuilder().setPrettyPrinting().create();

	@Inject
	Game game;

	@Inject
	Logger logger;

	@Inject
	@ConfigDir(sharedRoot=true)
	private File CONFIG_DIR;
	
	
	// Declaring default configuration and loading configuration's settings.
	@SuppressWarnings("unchecked")
	@Listener
	public void onPreInit(GamePreInitializationEvent event) throws Exception {
		
		File config_file=new File(CONFIG_DIR,"reddconomy-sponge.json");
		Map<String,Object> config=new HashMap<String,Object>();
		
		// Load config file if exists
		if(config_file.exists()){
			BufferedReader reader=new BufferedReader(new FileReader(config_file));
			config=_JSON.fromJson(reader,Map.class);
			reader.close();
		}		
		
		// Add missing config parameters
		config.putIfAbsent("ConfigVersion",8);
		config.putIfAbsent("url","http://127.0.0.1:8099");
		config.putIfAbsent("secretkey","changeme");
		config.putIfAbsent("qr","true");
		config.putIfAbsent("csigns",true);
		config.putIfAbsent("debug", false);
		
		// Write config
		BufferedWriter config_writer=new BufferedWriter(new FileWriter(config_file));
		_JSON.toJson(config,config_writer);
		config_writer.close();
		
		// Reddconomy-sponge can't start if the config is still the default one.
		if(config.get("secretkey").toString().equals("changeme")){
			logger.log(Level.SEVERE,"Reddconomy-sponge will not start until you modify the config.");
			CAN_RUN=false;
		}else{
			CAN_RUN=true;
		}
		
		// Initializing Reddconomy APIs
		ReddconomyApi.init(config.get("url").toString(),config.get("secretkey").toString());
		Task.builder().execute(ReddconomyApi::updateInfo)
		.async()
		.interval(60,TimeUnit.SECONDS)
		.name("Update backend info")
		.submit(this);
		
		// Loading configuration's settings.
		API_QR=config.get("qr").toString();
		PLUGIN_CONTRACT_SIGNS=(boolean)config.get("csigns");
		DEBUG=(boolean)config.get("debug");
		int version=((Number)config.get("ConfigVersion")).intValue();
		logger.log(Level.INFO,"Configfile version is "+version+".");
		
		
	}

	// Registering the Reddconomy functionalities
	@Listener
	public void onInit(GameInitializationEvent event) {
		if(CAN_RUN){
			logger.log(Level.INFO,"Reddconomy-sponge is now activated.");
			CommandSpec cmds=CommandSpec.builder().description(Text.of("Command")).executor(new CommandHandler(this)).arguments(GenericArguments.optional(GenericArguments.remainingJoinedStrings(Text.of("args")))).build();
			game.getCommandManager().register(this,cmds,"$","reddconomy","rdd");
		}else logger.log(Level.SEVERE,"Reddconomy-sponge is deactivated, an admin should modify the config.");
	}

	// Checking if the deposit has successfully happened.
	@Listener
	public void hasStarted(GameStartedServerEvent event) {
		Task.builder().execute(() -> processPendingDeposits())
		.async()
		.delay(1,TimeUnit.SECONDS)
		.interval(10,TimeUnit.SECONDS)
		.name("Fetch deposit status")
		.submit(this);
	}

	// Checking if a deposit has been completed.
	private void processPendingDeposits() {
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
						}))
						.delay(0,TimeUnit.MILLISECONDS)
						.name("Fetch deposit status")
						.submit(this);
					

				}
			}catch(Exception e){
				e.printStackTrace();
			}
		}
	}

	// Forcing player name in order to avoid robbing
	@Listener
	public void onSignPlace(ChangeSignEvent event) {
		Task.builder().execute(() -> {
			TileEntity tile=event.getTargetTile();
			Player player=(Player)event.getSource();
			if(FrontendUtils.getLine(tile,0).equals("[CONTRACT]")){
				if(!FrontendUtils.isOp(player)) FrontendUtils.setLine(tile,3,Text.of(player.getName()));
			}
		})
		.delay(5,TimeUnit.MILLISECONDS)
		.name("Forcing player name in the contract sign")
		.submit(this);
	}

	// Disabling dispenser/dropper access to other players than the owner.
	@Listener(order=Order.FIRST)
	public void onContainerAdjacentInteract(InteractBlockEvent.Secondary event) {
		Player player=(Player)event.getSource();
		if(event.getTargetBlock().getLocation().isPresent()){
			Location<World> location=event.getTargetBlock().getLocation().get();
			if(location.getTileEntity().isPresent()){
				TileEntity tile=location.getTileEntity().get();
				if(tile instanceof Dispenser||tile instanceof Dropper){
					if(!FrontendUtils.canPlayerOpen(location,player.getName())&&!FrontendUtils.isOp(player)){
						player.sendMessage(Text.of(TextColors.DARK_RED,"[CONTRACT] Only the owner can open this container."));
						event.setCancelled(true);
					}
				}
			}
		}
	}

	// CONTRACT SIGNS BLOCK
	@Listener
	public void onSignInteract(InteractBlockEvent.Secondary event) {
		if(event.getTargetBlock().getLocation().isPresent()){
			Location<World> location=event.getTargetBlock().getLocation().get();
			if(location.getTileEntity().isPresent()){
				TileEntity tile=location.getTileEntity().get();
				if(tile instanceof Sign){
					// Getting the original position of the block
					Direction origdirection=location.get(Keys.DIRECTION).get();
					Player player=(Player)event.getSource();
					if(PLUGIN_CONTRACT_SIGNS){
						UUID pUUID=player.getUniqueId();
						String line0=FrontendUtils.getLine(tile,0);
						String line1=FrontendUtils.getLine(tile,1);
						String line2=FrontendUtils.getLine(tile,2);
						String line3=FrontendUtils.getLine(tile,3);
						BlockType origsign=location.getBlockType();
						if(line0.equals("[CONTRACT]")){
							Player seller=Sponge.getServer().getPlayer(line3).get();
							UUID sellerUUID=seller.getUniqueId();
							String[] values = line1.split(",");
							long amount=(long)(Double.parseDouble(values[0])*100000000L);
							int delay=100;
							if (values.length>1) {
								int parsed_delay=Integer.parseInt(values[1]);
								if (parsed_delay>=0) delay=parsed_delay;
							}
							try{
								if(player!=seller||DEBUG){
									String cID=ReddconomyApi.createContract(amount,sellerUUID);
									int status=ReddconomyApi.acceptContract(cID,pUUID);
									// This activates the redstone only if the contract replied with 200
									if(status==200){
										player.sendMessage(Text.of("Contract ID: "+cID));
										player.sendMessage(Text.of("Contract accepted."));
										_ACTIVATED_SIGNS.put(location,true);
										location.setBlockType(BlockTypes.REDSTONE_TORCH);
										Task.builder().execute(() -> {
											BlockState state=origsign.getDefaultState();
											BlockState newstate=state.with(Keys.DIRECTION,origdirection).get();
											location.setBlock(newstate);
											TileEntity tile2=location.getTileEntity().get();
											FrontendUtils.setLine(tile2,0,Text.of(line0));
											FrontendUtils.setLine(tile2,1,Text.of(line1));
											FrontendUtils.setLine(tile2,2,Text.of(line2));
											FrontendUtils.setLine(tile2,3,Text.of(line3));
											_ACTIVATED_SIGNS.remove(location);
										})
										.delay(delay,TimeUnit.MILLISECONDS)
										.name("Powering off Redstone.")
										.submit(this);
									}else{
										player.sendMessage(Text.of(TextColors.DARK_RED,"Check your balance. Cannot accept contract"));
									}
								}else{
									player.sendMessage(Text.of(TextColors.DARK_RED,"You can't accept your own contract."));
								}

							}catch(Exception e){
								player.sendMessage(Text.of(TextColors.DARK_RED,"Cannot create/accept contract. Call an admin for more info."));
								e.printStackTrace();
							}
						}
					}
				}
			}
		}
	}
	
	@Listener (order=Order.FIRST)
	public void onRedstoneBreak (ChangeBlockEvent.Break event)
	{
		if(event.isCancelled())return;
		for (Transaction<BlockSnapshot> trans : event.getTransactions()) {
			if(!trans.getOriginal().getState().getType().equals(BlockTypes.REDSTONE_TORCH))continue;
				Optional<Location<World>> loc = trans.getOriginal().getLocation();
			if (loc.isPresent() && _ACTIVATED_SIGNS.containsKey(loc.get()))
				event.setCancelled(true);
		}
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
					long amount=(long)(damount*100000000L);
					String addr=ReddconomyApi.getAddrDeposit(amount,pUUID);
					if(addr!=null){
						if(API_QR.equalsIgnoreCase("true")) player.sendMessage(Text.of(FrontendUtils.createQR(addr,ReddconomyApi.getInfo().coin,amount)));
						else if(!API_QR.equalsIgnoreCase("false")){
							URL qrlink = new URL(FrontendUtils.createQRLink(API_QR, addr,ReddconomyApi.getInfo().coin,amount));
							Text qrlink_text=Text.builder("Click me to get the QR code").color(TextColors.GOLD).onClick(TextActions.openUrl(qrlink)).build();
							player.sendMessage(Text.of(qrlink_text));
						}
						player.sendMessage(Text.of("Deposit "+damount+" "+FrontendUtils.isTestnet()+ReddconomyApi.getInfo().coin_short+" to this address: "+addr));
						_PENDING_DEPOSITS.add(addr);
					}else player.sendMessage(Text.of(TextColors.DARK_RED,"Cannot create deposit address right now. Contact an admin."));
					break;
				}
				// balance
				case "balance":{
					double balance=ReddconomyApi.getBalance(pUUID);
					if(balance!=-1) player.sendMessage(Text.of("You have: "+balance+" "+FrontendUtils.isTestnet()+" "+ReddconomyApi.getInfo().coin_short));
					else player.sendMessage(Text.of(TextColors.DARK_RED,"Cannot request balance right now. Contact an admin."));
					break;
				}
				case "info":{
					Help.showInfos(player);
					break;
				}
				// commands for OPs
				case "admin":{
					if(FrontendUtils.isOp(player)){
						String action=args[0];
						if(args.length<1){
							invalid=true;
							break;
						}
						switch(action){
							
							case "info":{
								Help.showAdminInfos(player);
								break;
							}
							case "send":{
								if(args.length<3){
									invalid=true;
									break;
								}
								double text=Double.parseDouble(args[1]);
								String addr=args[2];
								long amount=(long)(text*100000000L);
								int status=ReddconomyApi.sendCoins(addr,amount);
								if(status==200) player.sendMessage(Text.of("Sending "+text+" to the address: "+addr));
								else player.sendMessage(Text.of(TextColors.DARK_RED,"Cannot request coins right now. Check the error in console"));
								break;
							}
							case "deposit":{
								if(args.length<3){
									invalid=true;
									break;
								}
								String wallid=args[2];
								double damount=Double.parseDouble(args[1]);
								long amount=(long)(damount*100000000L);
								String addr=ReddconomyApi.getAddrDeposit(amount,wallid);
								if(addr!=null){
									if(API_QR.equalsIgnoreCase("true")) player.sendMessage(Text.of(FrontendUtils.createQR(addr,ReddconomyApi.getInfo().coin,amount)));
									else if(!API_QR.equalsIgnoreCase("false")){
										URL qrlink = new URL(FrontendUtils.createQRLink(API_QR, addr,ReddconomyApi.getInfo().coin,amount));
										Text qrlink_text=Text.builder("Click me to get the QR code").color(TextColors.GOLD).onClick(TextActions.openUrl(qrlink)).build();
										player.sendMessage(Text.of(qrlink_text));
									}
									player.sendMessage(Text.of("Deposit "+damount+" "+FrontendUtils.isTestnet()+" "+ReddconomyApi.getInfo().coin_short+" to this address: "+addr));
								}else player.sendMessage(Text.of(TextColors.DARK_RED,"Cannot create deposit address right now. Check the server console."));
								break;
							}
							case "balance":{
								if(args.length<2){
									invalid=true;
									break;
								}
								String wallid=args[1];
								double balance=ReddconomyApi.getBalance(wallid);
								if(balance!=-1) player.sendMessage(Text.of("You have: "+balance+" "+FrontendUtils.isTestnet()+" "+ReddconomyApi.getInfo().coin_short));
								else player.sendMessage(Text.of(TextColors.DARK_RED,"Cannot request balance right now. Contact an admin."));
								break;
							}
							case "withdraw":{
								if(args.length<4){
									invalid=true;
									break;
								}
								String wallid=args[2];
								String addr=args[3];
								double damount=Double.parseDouble(args[1]);
								long amount=(long)(damount*100000000L);
								int status=ReddconomyApi.withdraw(amount,addr,wallid);
								if(status==200)	player.sendMessage(Text.of(TextColors.BLUE,"Withdrawing.. Wait at least 10 minutes"));
								else player.sendMessage(Text.of(TextColors.DARK_RED,"Cannot request a withdraw right now, check the Reddconomy Service error."));
								break;
							}
							default:
							case "help":{
								Help.sendAdminHelpText(player);
								break;
							}
						}
					}else player.sendMessage(Text.of(TextColors.DARK_RED,"Forbidden for non-op"));
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
					long amount=(long)(damount*100000000L);
					int status=ReddconomyApi.withdraw(amount,addr,pUUID);
					if(status==200){
						player.sendMessage(Text.of(TextColors.BLUE,"Withdrawing "+damount+" "+FrontendUtils.isTestnet()+" "+ReddconomyApi.getInfo().coin_short+".. Wait at least 10 minutes"));
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
					long amount=(long)(damount*100000000L);
					String to;
					Player receiver=null;
					if(server){
						to=ReddconomyApi.getInfo().generic_wallid;						
					}else{
						receiver=(Player)Sponge.getServer().getPlayer(args[1]).get();
						to=receiver.getUniqueId().toString();
					}
					String cId=ReddconomyApi.createContract(-amount,pUUID);
					if(cId!=null){
						int status=ReddconomyApi.acceptContract(cId,to);
						if(status==200){
							player.sendMessage(Text.of(TextColors.GOLD,
									damount+" "+FrontendUtils.isTestnet()+" "+ReddconomyApi.getInfo().coin_short
									+" sent to the "+(server?"server":"user "+args[1])));
							if(receiver!=null)	receiver.sendMessage(Text.of(TextColors.GOLD,player.getName()
									+" sent you a tip worth "+damount+" "+FrontendUtils.isTestnet()+" "
									+ReddconomyApi.getInfo().coin_short+"!"));
						}else player.sendMessage(Text.of(TextColors.DARK_RED,"Cannot send tip, "
								+ "check your balance or contact an admin."));
					}else player.sendMessage(Text.of(TextColors.DARK_RED,"Something went wrong, contact an admin."));
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
						long amount=(long)(Double.parseDouble(text)*100000000L);
						String cId=ReddconomyApi.createContract(amount,pUUID);
						if(cId!=null) player.sendMessage(Text.of("Share this Contract ID: "+ReddconomyApi.createContract(amount,pUUID)));
						else player.sendMessage(Text.of(TextColors.DARK_RED,"Can't create contract right now. Contact an admin."));
					}else if(method.equals("accept")){
						String contractId=text;
						int status=ReddconomyApi.acceptContract(contractId,pUUID);
						if(status==200){
							player.sendMessage(Text.of(TextColors.GOLD,"Contract accepted."));
							player.sendMessage(Text.of("You now have: "+ReddconomyApi.getBalance(pUUID)+" RDD"));
						}else{
							player.sendMessage(Text.of(TextColors.DARK_RED,"Cannot accept contract. Are you sure that you haven't already accepted?"));
							player.sendMessage(Text.of(TextColors.GOLD,"Otherwise, call and admin for more info."));
						}

					}
					break;
				}
				
				default:
					// help/info or no args
				case "help":{
					Help.sendHelpText(player);
					break;
				}
			}

			if(invalid){
				player.sendMessage(Text.of(TextColors.DARK_RED,"Invalid Command"));
				Help.sendHelpText(player);
			}
		}catch(Exception e){
			e.printStackTrace();
			player.sendMessage(Text.of(TextColors.DARK_RED,"Unexpected error"));
		}
		return true;
	}
}