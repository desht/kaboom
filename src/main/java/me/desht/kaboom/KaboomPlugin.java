package me.desht.kaboom;

/*
    This file is part of Kaboom

    PortableHole is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    Kaboom is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with Kaboom.  If not, see <http://www.gnu.org/licenses/>.
 */

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import me.desht.dhutils.LogUtils;
import me.desht.dhutils.MiscUtil;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

public class KaboomPlugin extends JavaPlugin implements Listener {

	private static KaboomPlugin instance = null;

	@Override
	public void onEnable() { 

		LogUtils.init(this);
		
		PluginManager pm = this.getServer().getPluginManager();

		pm.registerEvents(this, this);

		this.getConfig().options().copyDefaults(true);
		this.getConfig().options().header("See http://dev.bukkit.org/server-mods/kaboom/pages/configuration");
		this.saveConfig();
		
		instance = this;
	}

	@Override
	public void onDisable() {
		instance = null;
	}
	
	public KaboomPlugin getInstance() {
		return instance;
	}
	
	@Override 
	public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args){
		if (cmd.getName().equals("kaboom")) {
			if (sender instanceof Player) {
				float power = 4.0f;
				if (args.length > 0) {
					power = Float.parseFloat(args[0]);
				}
				makeKaboom((Player) sender, power);
			} else {
				MiscUtil.errorMessage(sender, "not from the console!");
			}
			return true;
		}
		return false;
	}
	
	private void makeKaboom(Player player, float power) {
		Block b = player.getTargetBlock(null, 140);
		player.getWorld().createExplosion(b.getLocation(), power);
	}

	@EventHandler(ignoreCancelled = true)
	public void onEntityExplode(EntityExplodeEvent event) {
		event.setYield(0f);
		final Location centre = event.getLocation();
		
		double distMax0 = 0.0;
		for (Block b : event.blockList()) {
			distMax0 = Math.max(distMax0, b.getLocation().distanceSquared(centre));
		}
		if (distMax0 == 0.0)
			return;
		
		final double distMax = Math.sqrt(distMax0);
		final double forceMult = getConfig().getDouble("force_mult", 1.0);
		final double yOffset = getConfig().getDouble("y_offset", 1.0);
		final double prob = getConfig().getDouble("block_probability", 0.5);
		
		int nSlots = Math.max(10, event.blockList().size() / 20);
		@SuppressWarnings("unchecked")
		final List<BlockDetails>[] taskList = (ArrayList<BlockDetails>[]) new ArrayList[nSlots];
		
		Random rand = new Random();
		
		for (Block b : event.blockList()) {
			if (rand.nextDouble() > prob) continue;
			int slot = rand.nextInt(taskList.length);
			if (taskList[slot] == null)
				taskList[slot] = new ArrayList<BlockDetails>();
			taskList[slot].add(new BlockDetails(b));
		}
		
		for (int i = 0; i < taskList.length; i++) {
			if (taskList[i] == null)
				continue;	
			final int i2 = i;
			Bukkit.getScheduler().scheduleSyncDelayedTask(this, new Runnable() {
				@Override
				public void run() {
					for (BlockDetails b : taskList[i2]) {
						double xOff = b.loc.getX() - centre.getBlockX();
						double yOff = b.loc.getY() - centre.getBlockY();
						double zOff = b.loc.getZ() - centre.getBlockZ();
						double dist = Math.sqrt(xOff * xOff + yOff * yOff + zOff * zOff);
						yOff += yOffset;
						double power = Math.abs((double)distMax - (double)dist);
						if (power < 0.1) power = 0.1;
						FallingBlock fb = centre.getWorld().spawnFallingBlock(b.loc, b.material, b.data);
						fb.setVelocity(new Vector(xOff, yOff, zOff).normalize().multiply(forceMult * power));
//						System.out.println("slot " + i2 + ": dist=" + dist + " power=" + power + " v=" + fb.getVelocity().length());
					}
				}
			}, (long)i);
		}
	}
	
	private class BlockDetails {
		final int material;
		final byte data;
		final Location loc;
		
		public BlockDetails(Block b) {
			material = b.getTypeId();
			data = b.getData();
			loc = b.getLocation();
		}
	}
}
