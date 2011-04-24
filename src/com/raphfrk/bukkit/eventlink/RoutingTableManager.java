package com.raphfrk.bukkit.eventlink;

import java.io.File;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

public class RoutingTableManager {
	
	private final Thread t;
	
	private final String password;
	
	private boolean updatePending = false;
	private final Object updateSync = new Object();
	
	EventLink p;
	
	private Object endSync = new Object();
	private boolean end = false;
	
	HashMap<String,RoutingTable> routingTables = new HashMap<String,RoutingTable>();
	
	RoutingTableManager(EventLink p, String password) {
		
		this.p = p;
		
		this.password = password;
		
		RoutingTableUpdater routingTableUpdater = new RoutingTableUpdater();
		
		t = new Thread(routingTableUpdater);
		
		t.start();
		
	}
	
	public void stop() {
		
		synchronized(endSync) {
			end = true;
		}
		
		synchronized(updateSync) {
			updatePending = true;
			updateSync.notify();
		}
		
		try {
			t.join();
		} catch (InterruptedException e) {
		}
		
	}

	public synchronized Map<String,RoutingTableEntry> getEntries(String table) {
		
		if(!routingTables.containsKey(table)) {
			return null;
		}
		return routingTables.get(table).getEntries();
		
	}
	
	public synchronized String getNextHop(String table, String name) {
		RoutingTable routingTable = routingTables.get(table);
		if(routingTable == null) {
			return null;
		}
		Map<String, RoutingTableEntry> entries = routingTable.getEntries();
		
		if(entries == null) {
			return null;
		}
		
		RoutingTableEntry entry = entries.get(name);
		if(entry == null) {
			return null;
		}
		
		return entry.getNextServer();
	}
	
	public synchronized String getLocation(String table, String name) {
		RoutingTable routingTable = routingTables.get(table);
		if(routingTable == null) {
			return null;
		}
		Map<String, RoutingTableEntry> entries = routingTable.getEntries();
		
		if(entries == null) {
			return null;
		}
		
		RoutingTableEntry entry = entries.get(name);
		if(entry == null) {
			return null;
		}
		
		return entry.getLocation();
	}
	
	public synchronized Set<String> copyKeySet(String table) {
		RoutingTable routingTable = routingTables.get(table);
		if(routingTable == null) {
			return null;
		}
		Map<String, RoutingTableEntry> entries = routingTable.getEntries();
		
		if(entries == null) {
			return null;
		}
		
		LinkedHashSet<String> newSet = new LinkedHashSet<String>();
		
		for(String key : entries.keySet()) {
			newSet.add(key);
		}
		
		return newSet;
	}
	
	public synchronized boolean addEntry(String table, String name) {
		
		if(!routingTables.containsKey(table)) {
			RoutingTable routingTable = new RoutingTable(table);
			routingTables.put(table, routingTable);
		}
		
		RoutingTable routingTable = routingTables.get(table);
		
		routingTable.addEntry(name, p.serverName);
		
		synchronized(updateSync) {
			updatePending = true;
			updateSync.notify();
		}
		
		return true;
	}
	
	public synchronized boolean deleteTable(String table) {
		if(!routingTables.containsKey(table)) {
			return false;
		}
		
		routingTables.remove(table);
		return true;
	}
	
	public synchronized boolean deleteEntry(String table, String name) {
		
		if(!routingTables.containsKey(table)) {
			return false;
		}
		
		RoutingTable routingTable = routingTables.get(table);
		
		if(!routingTable.deleteEntry(name)) {
			return false;
		}
		
		synchronized(updateSync) {
			updatePending = true;
			updateSync.notify();
		}
		
		return true;
	}
	
	public synchronized boolean combineTable(String source, RoutingTable routingTable) {
		
		if(routingTable == null) {
			return false;
		}
		String table = routingTable.getTableName();
		
		if(!routingTables.containsKey(table)) {
			RoutingTable rt = new RoutingTable(table);
			routingTables.put(table, rt);
		}
		
		RoutingTable rt = routingTables.get(table);
		
		boolean ret = rt.combineTable(source, routingTable);
		
		sendUpdatedTables();
		
		return ret;
		
	}
	
	public synchronized void listTablesToLog() {
		
		for(String key:routingTables.keySet()) {
			RoutingTable table = routingTables.get(key);
			table.listToLog(p);
		}
		
	}
	
	private synchronized void sendUpdatedTables() {
		
		for(String key:routingTables.keySet()) {
			
			RoutingTable table = routingTables.get(key);
			if(table.getChanged()) {
				table.clearChanged();
				sendTableToAll(table);
			}
			
		}
		
	}
	
	private synchronized void sendTableToAll(RoutingTable table) {
		
		Enumeration<String> aliases = SSLUtils.getAliases(new File(p.pluginDirectory + EventLink.slash + p.clientKeys), password);
		
		while(aliases.hasMoreElements()) {
			
			String current = aliases.nextElement();
			String currentServerName = (current.split(";"))[0];
			
			if(current.contains(";") && p.connectionManager != null && p.connectionManager.isConnected(currentServerName)) {
				sendTable(currentServerName, table);
			}	
		}
	}
	
	private synchronized void sendTable(String target, RoutingTable table) {
		
		p.connectionManager.resetConnection(target);
		p.connectionManager.sendObject(target, table);
		
	}
	
	public synchronized void sendAllTablesTo(String target) {
		for(String key : routingTables.keySet()) {
			sendTable(target, routingTables.get(key));
		}
	}
	
	private class RoutingTableUpdater implements Runnable {
		
		public void run() {
			
			boolean localEnd = false;
			
			while(!localEnd) {
				boolean resend = false;
				synchronized(updateSync) {
					if(updatePending) {
						resend = true;
						updatePending = false;
					}
				}
				if(resend) {
					sendUpdatedTables();
				} 
				synchronized(updateSync) {
					try {
						updateSync.wait(1000);
					} catch (InterruptedException e) {
					}
				}

				synchronized(endSync) {
					localEnd = end;
				}
			}
			
		}
		
		
	}
	

}
