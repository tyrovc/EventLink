package com.raphfrk.bukkit.eventlink;

import java.io.File;
import java.io.IOException;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.Event.Priority;
import org.bukkit.event.Event.Type;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;


public class EventLink extends JavaPlugin {

	String serverName = "";
	int portnum = 25365;
	Integer defaultTimeToLive = 10;
	String clientKeys = "keysclient";
	String serverKeys = "keysserver";
	private String password = "key_password";
	String algorithm = "RSA";
	String certAlgorithm = "DSA";
	int keySize;
	HashSet<String> admins = new HashSet<String>();

	File pluginDirectory;

	static MiscUtils.LogInstance logger = MiscUtils.getLogger("[EventLink]");

	EventLinkServer eventLinkServer = null;

	boolean nameUpdated = false;

	static final String slash = System.getProperty("file.separator");

	Server server;

	ConnectionManager connectionManager;
	RoutingTableManager routingTableManager;

	PluginManager pm;

	EventLinkCustomListener customListener = new EventLinkCustomListener(this);
	EventLinkPlayerListener playerListener = new EventLinkPlayerListener(this);
	EventLinkWorldListener worldListener = new EventLinkWorldListener(this);

	public void onEnable() {

		String name = "EventLink";

		pm = getServer().getPluginManager();
		server = getServer();

		logger.setLogPrefix(getServer().getLogger(), "[EventLink]");

		log(name + " initialized");

		pluginDirectory = this.getDataFolder();

		if( !readConfig() ) {
			log("Unable to read configs or create dirs, Eventlink failed to start");
			return;
		}

		if(!this.serverName.trim().equals("")) {
			nameUpdated = true;

			createCertFiles();

			eventLinkServer = new EventLinkServer(
					this, 
					this.serverName,
					new File(pluginDirectory + slash + serverKeys), 
					new File(pluginDirectory + slash + clientKeys),
					this.password,
					this.portnum
			);

			connectionManager = new ConnectionManager(this, serverName, password);
			routingTableManager = new RoutingTableManager(this, password);
			addServer();
			addOnlinePlayers();
			addWorlds();

		} else {
			log("Unable to start server, server name not set");
		}

		pm.registerEvent(Type.CUSTOM_EVENT, customListener, Priority.Normal, this);
		pm.registerEvent(Type.PLAYER_JOIN, playerListener, Priority.Normal, this);
		pm.registerEvent(Type.PLAYER_QUIT, playerListener, Priority.Normal, this);
		pm.registerEvent(Type.WORLD_LOAD, worldListener, Priority.Normal, this);

		if(!this.serverName.trim().equals("")) {
			addServer();
			addOnlinePlayers();
			addWorlds();
		}

	}

	public void onDisable() {

		delServer();
		delOnlinePlayers();
		delWorlds();

		if(eventLinkServer!=null) {
			eventLinkServer.stop();
			eventLinkServer = null;
		}
		if(connectionManager!=null) {
			connectionManager.stop();
			connectionManager = null;
		}
		if(routingTableManager!=null) {
			routingTableManager.stop();
			routingTableManager = null;
		}

	}

	private void addOnlinePlayers() {
		Player[] players = getServer().getOnlinePlayers();
		for(Player player : players) {
			routingTableManager.addEntry("players", player.getName());
		}
	}

	private void addWorlds() {
		List<World> worlds = getServer().getWorlds();
		for(World world : worlds) {
			routingTableManager.addEntry("worlds", world.getName());
		}
	}

	private void delOnlinePlayers() {
		Player[] players = getServer().getOnlinePlayers();
		for(Player player : players) {
			routingTableManager.deleteEntry("players", player.getName());
		}
		routingTableManager.deleteTable("players");
	}

	private void delWorlds() {
		List<World> worlds = getServer().getWorlds();
		for(World world : worlds) {
			routingTableManager.deleteEntry("worlds", world.getName());
		}
		routingTableManager.deleteTable("worlds");
	}

	private void addServer() {
		if(!this.serverName.trim().equals("")) {
			routingTableManager.addEntry("servers", this.serverName);
		}
	}

	private void delServer() {
		if(!this.serverName.trim().equals("")) {
			routingTableManager.deleteEntry("servers", this.serverName);
		}
		routingTableManager.deleteTable("servers");
	}

	private boolean readConfig() {

		if( !pluginDirectory.exists() ) {
			pluginDirectory.mkdirs();
		}

		MyPropertiesFile pf;
		try {
			pf = new MyPropertiesFile(new File( pluginDirectory , "eventlink.txt").getCanonicalPath());
		} catch (IOException e) {
			return false;
		}

		pf.load();

		this.serverName = pf.getString("server_name" , "");
		this.portnum = pf.getInt("portnum" , 25365);
		this.defaultTimeToLive = pf.getInt("initial_time_to_live" , 10);
		this.clientKeys = pf.getString("client_keys" , "keysclient");
		this.serverKeys = pf.getString("server_keys" , "keysserver");
		this.password = pf.getString("password" , "key_password");
		this.algorithm = pf.getString("algorithm" , "RSA");
		this.certAlgorithm = pf.getString("cert_algorithm" , "SHA512WITHRSA");
		this.keySize = pf.getInt("key_size" , 512);

		String adminString = pf.getString("admin_list", "");
		for( String current : adminString.split(",")) {
			admins.add(current.toLowerCase());
		}

		pf.save();

		return true;

	}

	@Override
	public boolean onCommand(CommandSender commandSender, Command command, String commandLabel, String[] args) {

		if(args.length == 0) {
			return false;
		}

		if(!commandSender.isOp()) {
			commandSender.sendMessage("You do not have sufficient user level for this command");
			return true;
		}

		if(command.getName().equals("eventlink")) {

			if(commandSender instanceof Player && args[0].equalsIgnoreCase("itemgen") && args.length > 2) {
				Player player = (Player)commandSender;
				try {
					int typeId = Integer.parseInt(args[1]);
					int amount = Integer.parseInt(args[2]);
					player.getInventory().addItem(new ItemStack(typeId, amount));
				} catch (NumberFormatException nfe) {
					player.sendMessage("Unable to parse " + args[1] + " and " + args[2] + " as integers");
				}
			}
					
					if(args[0].equalsIgnoreCase("add")) {
				if( args.length > 1 ) {
					new EventLinkClient(
							this,
							commandSender,
							serverName,
							new File(pluginDirectory + slash + serverKeys), 
							new File(pluginDirectory + slash + clientKeys),
							password,
							SSLUtils.getHostname(args[1]),
							SSLUtils.getPortnum(args[1]),
							true
					);
					return true;
				}
			} else if(args[0].equals("list")) {
				Enumeration<String> aliases = SSLUtils.getAliases(new File(pluginDirectory + slash + clientKeys), password);
				if(!aliases.hasMoreElements()) {
					commandSender.sendMessage("No trusted servers added");
					return true;
				} else {
					commandSender.sendMessage("Trusted servers: (*=connected)");
					while(aliases.hasMoreElements()) {
						String current = aliases.nextElement();
						String currentServerName = (current.split(";"))[0];
						if(current.contains(";")) {
							current = "<" + current.replaceFirst(";", "> ");
							if(connectionManager != null && connectionManager.isConnected(currentServerName)) {
								current += " *";
							}
						}
						commandSender.sendMessage(current);
					}
					return true;
				}
			} else if(args.length>1 && (args[0].equals("delete") || args[0].equals("del"))) {
				commandSender.sendMessage(SSLUtils.removeCertificate(new File(pluginDirectory + slash + clientKeys), this.password, args[1]));
				commandSender.sendMessage(connectionManager.deleteConnection(args[1]));
				eventLinkServer.reload();
				return true;
			} else if(args[0].equals("routes")) {
				routingTableManager.listTablesToLog();
				return true;
			} else if(args[0].equals("refresh")) {
				if(connectionManager != null) {
					commandSender.sendMessage("Refreshing connections");
					connectionManager.checkTrusted(password);
				}
				return true;
			} else if(args[0].equals("ping") && args.length > 1 && commandSender instanceof Player) {
				String[] targets = new String[args.length-1];
				for(int cnt=0;cnt<args.length-1;cnt++) {
					targets[cnt] = args[cnt+1];
				}
				if(!connectionManager.sendObject(targets, new Ping(((Player)commandSender).getName()))) {
					if(args.length==2) {
						commandSender.sendMessage(args[1] + " is not a valid ping target");
					} else {
						commandSender.sendMessage("unknown target list");
					}
				}
				return true;
			} else if(args[0].equals("who")) {
				Map<String,RoutingTableEntry> playerMap = routingTableManager.getEntries("players");
				commandSender.sendMessage("Players online:");
				if(playerMap != null) {
					for(String key : playerMap.keySet()) {
						commandSender.sendMessage(key + " (" + playerMap.get(key).getLocation() + ")");
					}
				}
				return true;
			}

		}

		return false;

	}

	private void createCertFiles() {
		File serverFile = new File(pluginDirectory + slash + serverKeys);
		if(!serverFile.exists()) {
			log("No server key/cert file deteted, attempting creation");

			SSLUtils.generateCertificateFile(
					serverFile, 
					this.keySize, 
					this.password, 
					this.algorithm, 
					this.certAlgorithm, 
					"CN=" + serverName, 
					false);

			if(!serverFile.exists()) {
				log("Creation failed");
				return;
			}

		}


		File clientFile = new File(pluginDirectory + slash + clientKeys);
		if(!clientFile.exists()) {

			//Certificate cert = SSLUtils.getCertificate(serverFile, this.password);
			//PrivateKey privateKey = SSLUtils.getPrivateKey(serverFile, this.password);

			log("No client trust/cert file deteted, attempting creation");
			SSLUtils.createKeyFile(
					clientFile, 
					this.password, 
					null, //new KeyPair(cert.getPublicKey(), privateKey), 
					null, //cert, 
					false);

			if(!clientFile.exists()) {
				log("Creation failed");
				return;
			}
		}

	}

	public void log(String message) {
		logger.log(message);
	}


	/**
	 * Tests if a player is listed on the admin_list for the eventlink.txt file
	 *
	 * @param player Player to test
	 * @return true if the player is on the admin list or is an Op
	 */

	public boolean isAdmin(Player player) {
		return player.isOp() || admins.contains(player.getName().toLowerCase());
	}

	/**
	 * Sends an event to a single server servers.  
	 * 
	 * NOTE: The packet may be lost en-route even if the function returns true
	 *
	 * @param target destination servers.
	 * @return true if a route exists to the target
	 */

	public boolean sendEvent(String target, Event event) {
		if(connectionManager==null) {
			return false;
		}
		return connectionManager.sendObject(target, event);
	}

	/**
	 * Sends an event to multiple servers.  This will only send the  
	 * packet once to each directly connected server.  This has the 
	 * potential to improve broadcast bandwidth usage.
	 *
	 * NOTE: The packet may be lost en-route even if the function returns true
	 *
	 * @param target Array of destination servers.
	 * @return true if a route exists to the target
	 */

	public boolean sendEvent(String[] target, Event event) {
		if(connectionManager==null) {
			return false;
		}
		return connectionManager.sendObject(target, event);
	}

	/**
	 * Route entries are markers that can be seen (and routed to) by
	 * all other servers.
	 * 
	 * There are 3 reserved table
	 * 
	 * "servers":  The servers that are online
	 * "players":  The players that are online
	 * "worlds":   The worlds for all connected servers
	 * 
	 * NOTE: If there is a collision, then the closest entry will be routed to.
	 * 
	 * This means that unique names should be used for servers and worlds.
	 * 
	 * This tables are auto-updated and shouldn't be modified by other plugins
	 *
	 * NOTE: The packet may be lost en-route even if the function returns true
	 *
	 *
	 */

	/**
	 * Adds a route entry for the local server.
	 * 
	 * This will be visible by all the other servers in the cluster
	 * 
	 * @param table This is the table name/type of route entry
	 * @param name This is the specific name of the entry
	 * @return true if the entry was added locally
	 */

	public boolean addRouteEntry(String table, String name) {
		return routingTableManager.addEntry(table, name);
	}

	/**
	 * Deletes a route entry for the local server.
	 * 
	 * This will be deleted for all the other servers in the cluster
	 * 
	 * @param table This is the table name/type of route entry
	 * @param name This is the specific name of the entry
	 * @return true if the entry was deleted
	 */

	public boolean deleteRouteEntry(String table, String name) {
		return routingTableManager.deleteEntry(table, name);
	}

	/**
	 * Gets the current location of a routing entry
	 * 
	 * If there is more than one match, the closest entry will be returned
	 * 
	 * @param table This is the table name/type of route entry
	 * @param name This is the specific name of the entry
	 * @return the name of the server where the entry is located
	 */

	public String getEntryLocation(String table, String name) {
		return routingTableManager.getLocation(table, name);

	}

	/**
	 * Copies the names of all entries in a particular table
	 * 
	 * The name only appears once, even if there are multiple entries with
	 * the same name
	 * 
	 * @param table This is the table name/type of route entry
	 * @return A set containing all the entry names for the table
	 */

	public Set<String> copyEntries(String table) {
		return routingTableManager.copyKeySet(table);
	}

	/**
	 * This gives the name of the current server
	 * 
	 * @return The name of the server
	 */

	public String getServerName() {
		return serverName;
	}

}

