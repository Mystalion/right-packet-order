package net.mystalion.rightpacketorder;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.reflect.StructureModifier;

public class RightPacketOrder extends JavaPlugin {

	private static final Random RANDOM = new Random();
	private final Map<Player, Set<String>> sendChunks = new HashMap<Player, Set<String>>();
	private final Map<Player, Map<String, List<PacketContainer>>> packetQueue = new HashMap<Player, Map<String, List<PacketContainer>>>();

	@Override
	public void onEnable() {
		Bukkit.getPluginManager().registerEvents(new Listener() {

			@EventHandler
			public void onPlayerDisconnect(PlayerQuitEvent event) {
				sendChunks.remove(event.getPlayer());
				packetQueue.remove(event.getPlayer());
			}
		}, this);
		getCommand("test-right-order").setExecutor(new CommandExecutor() {

			public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
				if (sender.hasPermission("rightpacketorder.test")) {
					Location x = new Location(Bukkit.getWorlds().get(0), RANDOM.nextInt(10000) - 5000, 70, RANDOM.nextInt(10000) - 5000);
					for (Player player : Bukkit.getOnlinePlayers()) {
						player.teleport(x);
					}
				} else {
					sender.sendMessage("Sorry, but you dont have permissions for this command.");
				}
				return true;
			}
		});
		ProtocolLibrary.getProtocolManager().addPacketListener(new PacketAdapter(this, 
//@formatter:off
				// World
				PacketType.Play.Server.MAP_CHUNK,
				PacketType.Play.Server.MAP_CHUNK_BULK,
				PacketType.Play.Server.RESPAWN,

				// Entities
		//		PacketType.Play.Server.SPAWN_ENTITY,
		//		PacketType.Play.Server.SPAWN_ENTITY_LIVING,
		//		PacketType.Play.Server.SPAWN_ENTITY_PAINTING,
				PacketType.Play.Server.NAMED_ENTITY_SPAWN
//@formatter:on
		) {

			@Override
			public void onPacketSending(PacketEvent event) {
				Player player = event.getPlayer();
				PacketContainer packet = event.getPacket();
				if (event.getPacketType() == PacketType.Play.Server.RESPAWN) {
					sendChunks.remove(player);
				} else if (event.getPacketType() == PacketType.Play.Server.MAP_CHUNK) {
					handleChunkSending(player, packet.getIntegers().read(0), packet.getIntegers().read(1));
				} else if (event.getPacketType() == PacketType.Play.Server.MAP_CHUNK_BULK) {
					processChunkBulk(event, packet);
				} else {
					StructureModifier<Integer> ints = event.getPacket().getIntegers();
					int x = ints.read(1);
					//	int y = ints.read(2);
					int z = ints.read(3);
					int cx = x / 32 >> 4;
					int cz = z / 32 >> 4;
					if (sendChunks.containsKey(player)) {
						if (!sendChunks.get(player).contains(toKey(cx, cz))) {
							player.sendMessage("Delaying packet: " + packet);
							player.sendMessage(sendChunks.get(player).stream().collect(Collectors.joining("\n")));
							player.sendMessage("real:" + toKey(cx, cz));
							getPacketQueue(player, cx, cz).add(packet);
							event.setCancelled(true);
						}
					}
				}
			}
		});
	}

	private List<PacketContainer> getPacketQueue(Player player, int x, int z) {
		if (!packetQueue.containsKey(player)) {
			packetQueue.put(player, new HashMap<String, List<PacketContainer>>());
		}
		String key = toKey(x, z);
		Map<String, List<PacketContainer>> map = packetQueue.get(player);
		if (!map.containsKey(key)) {
			map.put(key, new ArrayList<PacketContainer>());
		}
		return packetQueue.get(player).get(key);
	}

	private void processChunkBulk(PacketEvent event, PacketContainer packet) {
		// This is the trick - we actually move the chunk millions of miles away ...
		int[] chunkX = packet.getIntegerArrays().read(0);
		int[] chunkZ = packet.getIntegerArrays().read(1);
		Player player = event.getPlayer();
		for (int i = 0; i < chunkZ.length; i++) {
			handleChunkSending(player, chunkX[i], chunkZ[i]);
		}
	}

	private void handleChunkSending(Player player, int x, int z) {
		if (!sendChunks.containsKey(player)) {
			sendChunks.put(player, new HashSet<String>());
		}
		sendChunks.get(player).add(toKey(x, z));
		List<PacketContainer> p = getPacketQueue(player, x, z);
		for (PacketContainer container : p) {
			try {
				ProtocolLibrary.getProtocolManager().sendServerPacket(player, container.deepClone());
			} catch (InvocationTargetException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}

	private static String toKey(int x, int z) {
		return x + ";" + z;
	}
}
