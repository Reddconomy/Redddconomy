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
package it.reddconomy.plugin.contracts;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import org.spongepowered.api.Sponge;
import org.spongepowered.api.block.BlockSnapshot;
import org.spongepowered.api.block.BlockState;
import org.spongepowered.api.block.BlockType;
import org.spongepowered.api.block.BlockTypes;
import org.spongepowered.api.block.tileentity.Sign;
import org.spongepowered.api.block.tileentity.TileEntity;
import org.spongepowered.api.block.tileentity.carrier.Dispenser;
import org.spongepowered.api.block.tileentity.carrier.Dropper;
import org.spongepowered.api.data.Transaction;
import org.spongepowered.api.data.key.Keys;
import org.spongepowered.api.data.manipulator.mutable.tileentity.SignData;
import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.api.event.Event;
import org.spongepowered.api.event.Listener;
import org.spongepowered.api.event.Order;
import org.spongepowered.api.event.block.ChangeBlockEvent;
import org.spongepowered.api.event.block.CollideBlockEvent;
import org.spongepowered.api.event.block.InteractBlockEvent;
import org.spongepowered.api.event.block.tileentity.ChangeSignEvent;
import org.spongepowered.api.event.data.ChangeDataHolderEvent;
import org.spongepowered.api.event.entity.CollideEntityEvent;
import org.spongepowered.api.event.entity.DamageEntityEvent;
import org.spongepowered.api.event.entity.MoveEntityEvent;
import org.spongepowered.api.event.entity.living.humanoid.AnimateHandEvent;
import org.spongepowered.api.event.item.inventory.InteractItemEvent;
import org.spongepowered.api.event.statistic.ChangeStatisticEvent;
import org.spongepowered.api.event.world.ExplosionEvent;
import org.spongepowered.api.event.world.chunk.TargetChunkEvent;
import org.spongepowered.api.scheduler.Task;
import org.spongepowered.api.text.Text;
import org.spongepowered.api.text.action.TextActions;
import org.spongepowered.api.text.format.TextColors;
import org.spongepowered.api.text.format.TextStyles;
import org.spongepowered.api.util.Direction;
import org.spongepowered.api.world.LocatableBlock;
import org.spongepowered.api.world.Location;
import org.spongepowered.api.world.World;

import com.flowpowered.math.vector.Vector3d;
import com.flowpowered.math.vector.Vector3i;

import it.reddconomy.Utils;
import it.reddconomy.common.data.OffchainWallet;
import it.reddconomy.plugin.Config;
import it.reddconomy.plugin.ReddconomyApi;
import it.reddconomy.plugin.utils.FrontendUtils;

public class ContractSign{

	// If the contract sign is placed above one of these blocks, it will be replaced with a redstone block
	// instead of a redstone torch
	public static final Collection<BlockType> _BLOCK_BLACKLIST=Arrays.asList(new BlockType[]{
		BlockTypes.SEA_LANTERN,
		BlockTypes.PISTON,
		BlockTypes.STICKY_PISTON,
		BlockTypes.PISTON_HEAD,
		BlockTypes.PISTON_EXTENSION,
		BlockTypes.CACTUS,
		BlockTypes.TNT,
		BlockTypes.AIR,
		BlockTypes.GLASS,
		BlockTypes.GLASS_PANE,
		BlockTypes.GLOWSTONE,
		BlockTypes.STAINED_GLASS,
		BlockTypes.STAINED_GLASS_PANE
	});
	
	public static HashMap<Player, SignInitialization> csign = new HashMap<Player, SignInitialization>();
	public static final Collection<Vector3i> _ACTIVATED_SIGNS=new LinkedList<Vector3i>();
	private static Object PLUGIN;
	public static void init(Object plugin){
		if(PLUGIN!=null)return;
		PLUGIN=plugin;		
		Sponge.getEventManager().registerListeners(plugin, new ContractSign());
	}
	
	
	public static String getLine(TileEntity sign, int line)	{
		Optional<SignData> data = sign.getOrCreate(SignData.class);
		return (data.get().lines().get(line)).toPlainSingle();
	}
	
	public static void setLine(TileEntity sign, int line, String text) {
		Optional<SignData> signdata=sign.getOrCreate(SignData.class);
		signdata.get().set(signdata.get().lines().set(line,Text.of(text)));
		sign.offer(signdata.get());
	}
    public static Collection<TileEntity> getNearTileEntities(Location<World> location) {
//		ArrayList<Sign> out = new ArrayList<Sign>();
		TileEntity sr_entities[] = new TileEntity[] { 
				location.getRelative(Direction.DOWN).getTileEntity().orElse(null),
				location.getRelative(Direction.UP).getTileEntity().orElse(null),
				location.getRelative(Direction.EAST).getTileEntity().orElse(null),
				location.getRelative(Direction.WEST).getTileEntity().orElse(null),
				location.getRelative(Direction.NORTH).getTileEntity().orElse(null),
				location.getRelative(Direction.SOUTH).getTileEntity().orElse(null) 
		};
//		for (TileEntity te : sr_entities) {
//			if (te!=null&&te instanceof Sign) {
//				out.add((Sign) te);
//			}
//		}
		return Arrays.asList(sr_entities);
		
	}
    public static Collection<LocatableBlock> getNearBlocks(Location<World> location) {
    	LocatableBlock sr_entities[] = new LocatableBlock[] { 
				location.getRelative(Direction.DOWN).getLocatableBlock().orElse(null),
				location.getRelative(Direction.UP).getLocatableBlock().orElse(null),
				location.getRelative(Direction.EAST).getLocatableBlock().orElse(null),
				location.getRelative(Direction.WEST).getLocatableBlock().orElse(null),
				location.getRelative(Direction.NORTH).getLocatableBlock().orElse(null),
				location.getRelative(Direction.SOUTH).getLocatableBlock().orElse(null)
		};
		return Arrays.asList(sr_entities);
		
	}
    
    public static boolean canHostWallTorch(Location<World> loc, Direction dir)
    {
		    	if (!(loc.getRelative(dir).getBlockType().equals(BlockTypes.AIR))&&!(ContractSign._BLOCK_BLACKLIST.contains(loc.getRelative(dir).getBlockType())))
		    		return true;
		    	else return false;
    }
    
    public static BlockState doWallTorch(Location<World> loc, Direction dir)
    {
		    	return BlockTypes.REDSTONE_TORCH.getDefaultState().with(Keys.DIRECTION, dir.getOpposite()).get();
    }

    public static void createContractSignFromSign(TileEntity sign) throws Exception{
    	String contract_owner=getLine(sign,3);
    	setLine(sign,3,FrontendUtils.getWalletIdFromPrefixedString(contract_owner));
    }
    

    // Create contract signs from placed signs
	@Listener
	public void onSignPlace(ChangeSignEvent event) {
		if(!((boolean)Config.getValue("csigns"))||!(event.getSource() instanceof Player)) return;

		Task.builder().execute(() -> {
			TileEntity tile=event.getTargetTile();
			Player player=(Player)event.getSource();
			if(getLine(tile,0).equals("[CONTRACT]")){
				if(FrontendUtils.isOp(player)&&!getLine(tile,3).isEmpty()){
					player.sendMessage(Text.of("You've just placed a contract sign as Admin."));
				}else{
					player.sendMessage(Text.of("You've just placed a contract sign."));
					setLine(tile,3,player.getName());
				}
				try{
					createContractSignFromSign(tile);
				}catch(Exception e){
					player.sendMessage(Text.of("Unexpected error"));
					setLine(tile,0,"[Err~CONTRACT]");
					e.printStackTrace();
				}
			}
		}).delay(5,TimeUnit.MILLISECONDS).name("Forcing player name in the contract sign").submit(PLUGIN);
	}

	@Listener(order=Order.PRE)
	public void onSignInteract(InteractItemEvent.Secondary event) throws Exception {
		if(!((boolean)Config.getValue("contracts"))||!(event.getSource() instanceof Player)) return;

		Optional<Vector3d> opoint=event.getInteractionPoint();
		if(!opoint.isPresent()) return;
		Vector3d point=opoint.get();

		Player player=(Player)event.getSource();
		SignInitialization cdata = new SignInitialization();
		csign.put(player, cdata);
		cdata.sign_location=player.getWorld().getLocation(point);
		System.out.println(cdata.sign_location);
		if(cdata.sign_location.getTileEntity().isPresent()){
			TileEntity tile = cdata.sign_location.getTileEntity().get();
			if(!(tile instanceof Sign)) return;
			cdata.sign_lines[0]=getLine(tile,0);
			if(cdata.sign_lines[0].equals("[CONTRACT]")){
				event.setCancelled(true);

				cdata.sign_lines[1]=getLine(tile,1);
				cdata.sign_lines[2]=getLine(tile,2);
				cdata.sign_lines[3]=getLine(tile,3);

				// Getting the original position of the block
				cdata.sign_direction=cdata.sign_location.get(Keys.DIRECTION).get();
				cdata.sign=cdata.sign_location.getBlockType();

				cdata.player_wallet=ReddconomyApi.getWallet(player.getUniqueId());
				cdata.owner_wallet=ReddconomyApi.getWallet(cdata.sign_lines[3]);

				String[] values=cdata.sign_lines[1].split(",");
				if(values.length<1) return;

				long amount=Utils.convertToInternal(Double.parseDouble(values[0].trim()));
				cdata.delay=100;
				if(values.length>1){
					int parsed_delay=Integer.parseInt(values[1].trim());
					if(parsed_delay>=0) cdata.delay=parsed_delay;
				}
				cdata.contract=ReddconomyApi.createContract(amount,cdata.owner_wallet.id);
				// Confirm Contract
				Text confirmcontract = Text.builder("[REDDCONOMY] CLICK HERE to Confirm Contract.")
						 .color(TextColors.GREEN)
						 .style(TextStyles.BOLD)
						 .onHover(TextActions.showText(Text.of("Contract ID: "+cdata.contract.id
															  +"\nAmount: "+Utils.convertToUserFriendly(cdata.contract.amount)
															  +"\n"+cdata.sign_lines[3])))
						 .onClick(TextActions.runCommand("/$ confirmsign"))
						 .build();
				// Decline Contract
				Text declinecontract = Text.builder("[REDDCONOMY] CLICK HERE to Decline Contract.")
						 .color(TextColors.DARK_RED)
						 .style(TextStyles.BOLD)
						 .onHover(TextActions.showText(Text.of("Contract ID: "+cdata.contract.id
															  +"\nAmount: "+Utils.convertToUserFriendly(cdata.contract.amount)
															  +"\n"+cdata.sign_lines[3])))
						 .onClick(TextActions.runCommand("/$ declinesign"))
						 .build();
				player.sendMessage(Text.of(confirmcontract,"\n",declinecontract));
			}

		}
	}

	// Protect near dispenser/dropper
	@Listener(order=Order.FIRST)
	public void onContainerAdjacentInteract(InteractBlockEvent.Secondary event) throws Exception {
		Player player=(Player)event.getSource();
		if(event.getTargetBlock().getLocation().isPresent()){
			Location<World> location=event.getTargetBlock().getLocation().get();
			if(location.getTileEntity().isPresent()){
				TileEntity tile=location.getTileEntity().get();
				if(tile instanceof Dispenser||tile instanceof Dropper){
					Collection<TileEntity> near_blocks=getNearTileEntities(location);
					OffchainWallet player_wallet=null;
					for(TileEntity block:near_blocks){
						if(block==null||!(block instanceof Sign))continue;
						Sign s=(Sign)block;
						if(player_wallet==null) player_wallet=ReddconomyApi.getWallet(player.getUniqueId());
						OffchainWallet sign_owner=ReddconomyApi.getWallet(getLine(s,3));
						if(player_wallet.short_id!=sign_owner.short_id){
							if(!FrontendUtils.isOp(player)){
								player.sendMessage(Text.of(TextColors.DARK_RED,"[CONTRACT] Only the owner can open this container."));
								event.setCancelled(true);
							}else{
								player.sendMessage(Text.of(TextColors.DARK_RED,"[CONTRACT] You are bypassing container protection as OP."));
							}
							break;
						}
					}			
				}
			}
		}
	}
	
	
	
	@Listener(order=Order.FIRST)
	public void onExplosion(ExplosionEvent.Detonate event) {		
		for(Location<World> l:event.getAffectedLocations()){
			Optional<LocatableBlock> lb=l.getLocatableBlock();
			if(!lb.isPresent())continue;
			LocatableBlock b=lb.get();
			if(b.getBlockState().getType()==BlockTypes.REDSTONE_TORCH||b.getBlockState().getType()==BlockTypes.REDSTONE_BLOCK){
				boolean is_zone_protected=_ACTIVATED_SIGNS.contains(b.getLocation().getBlockPosition());
				if(is_zone_protected){
					event.setCancelled(true);
					break;
				}
			}
		}	
	}
	
	@Listener(order=Order.FIRST)
	public void onChangeBlockEvent(ChangeBlockEvent.Pre event) {		
		if(event.isCancelled()) return;
		for(Location<World> l:event.getLocations()){
			if(l.getBlockType()==BlockTypes.SLIME){				
				Collection<LocatableBlock> near=getNearBlocks(l);
				for(LocatableBlock b:near){
					if(b.getBlockState().getType()==BlockTypes.REDSTONE_TORCH||b.getBlockState().getType()==BlockTypes.REDSTONE_BLOCK){
						boolean is_zone_protected=_ACTIVATED_SIGNS.contains(b.getLocation().getBlockPosition());
						if(is_zone_protected){
							event.setCancelled(true);
							break;
						}
					}
				}
			}else if((l.getBlockType()==BlockTypes.REDSTONE_TORCH||l.getBlockType()==BlockTypes.REDSTONE_BLOCK)&&_ACTIVATED_SIGNS.contains(l.getBlockPosition())){
				event.setCancelled(true);
				break;
			}
		}		
	}
	
	
	@Listener(order=Order.FIRST)
	public void onChangeBlockEvent(ChangeBlockEvent event) {
		if(event.isCancelled()) return;
		
		for(Transaction<BlockSnapshot> trans:event.getTransactions()){			
			BlockSnapshot oblock=trans.getOriginal();
			BlockSnapshot fblock=trans.getFinal();

			
			if(oblock.getState().getType()!=BlockTypes.REDSTONE_TORCH||oblock.getState().getType()!=BlockTypes.REDSTONE_BLOCK)continue;
				
			Optional<Location<World>> lo=trans.getOriginal().getLocation();
			if(!lo.isPresent()) continue;
			Location<World> l=lo.get();
			

			boolean is_zone_protected=_ACTIVATED_SIGNS.contains(l.getBlockPosition());

			if(is_zone_protected){
				if(	
					fblock.getState().getType()!=(BlockTypes.STANDING_SIGN)
					&&fblock.getState().getType()!=(BlockTypes.WALL_SIGN)
				){
					event.setCancelled(true);
					System.out.println("Can't replace "+l.getBlock()+" with "+fblock);
					if(event.getSource() instanceof Player) ((Player)event.getSource()).sendMessage(Text.of(TextColors.DARK_RED,"[CONTRACT] Block protected."));
					break;
				}
			}
			
		}
	}

	final boolean _DEBUG_PRINT_EVENTS=false;
	@Listener(order=Order.PRE)
	public void debugPrintEvents(Event event) throws Exception {
		if(!_DEBUG_PRINT_EVENTS) return;
		if(event instanceof TargetChunkEvent||
				event instanceof CollideEntityEvent||
				event instanceof MoveEntityEvent||
				event instanceof ChangeStatisticEvent||
				event instanceof CollideBlockEvent||
				event instanceof AnimateHandEvent||
				event instanceof DamageEntityEvent||
				event instanceof ChangeDataHolderEvent) return;
		System.out.println("EV "+event.getClass()+" "+event);

	}
}
