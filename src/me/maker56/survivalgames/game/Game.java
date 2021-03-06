package me.maker56.survivalgames.game;

import java.util.ArrayList;
import java.util.List;

import me.maker56.survivalgames.Util;
import me.maker56.survivalgames.SurvivalGames;
import me.maker56.survivalgames.arena.Arena;
import me.maker56.survivalgames.arena.chest.Chest;
import me.maker56.survivalgames.commands.messages.MessageHandler;
import me.maker56.survivalgames.game.phrase.CooldownPhrase;
import me.maker56.survivalgames.game.phrase.DeathmatchPhrase;
import me.maker56.survivalgames.game.phrase.IngamePhrase;
import me.maker56.survivalgames.game.phrase.ResetPhrase;
import me.maker56.survivalgames.game.phrase.VotingPhrase;
import me.maker56.survivalgames.scoreboard.CustomScore;
import me.maker56.survivalgames.scoreboard.ScoreboardPhase;
import me.maker56.survivalgames.user.SpectatorUser;
import me.maker56.survivalgames.user.User;
import me.maker56.survivalgames.user.UserState;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public class Game {
	
	// STATIC VARIABLES
	private static ItemStack leaveItem;
	
	public static ItemStack getLeaveItem() {
		return leaveItem;
	}
	
	public static void reinitializeDatabase() {
		leaveItem = Util.parseItemStack(SurvivalGames.instance.getConfig().getString("Leave-Item"));
		playerNavigator = Util.parseItemStack(SurvivalGames.instance.getConfig().getString("Spectating.Player-Navigator.Item"));
		String s = SurvivalGames.instance.getConfig().getString("Spectating.Player-Navigator.Inventory-Title");
		if(s.length() > 32)
			s = s.substring(0, 32);
		inventoryTitle = ChatColor.translateAlternateColorCodes('&', s);
	}
	
	private String name;
	private Location lobby;
	private boolean voting, reset;
	private int maxVotingArenas;
	private List<Arena> arenas;
	private int reqplayers, maxplayers;
	private GameState state;
	private int lobbytime, cooldown = 30;
	private int death = 0;
	
	private VotingPhrase votingPhrase;
	private CooldownPhrase cooldownPhrase;
	private IngamePhrase ingamePhrase;
	private DeathmatchPhrase deathmatchPhrase;
	
	private Arena arena;
	private List<User> users = new ArrayList<>();
	private List<Chest> chests = new ArrayList<>();
	private List<String> rChunks = new ArrayList<>();
	public ArrayList<String> voted = new ArrayList<>();
	
	public Game(String name, Location lobby, boolean voting, int lobbytime, int maxVotingArenas, int reqplayers, List<Arena> arenas, boolean reset) throws CloneNotSupportedException {
		this.name = name;
		this.lobby = lobby;
		this.voting = voting;
		this.lobbytime = lobbytime;
		this.maxVotingArenas = maxVotingArenas;
		this.arenas = arenas;
		this.reset = reset;

		if(reqplayers < 2) {
			reqplayers = 2;
		}
		
		this.reqplayers = reqplayers;
		this.maxplayers = getFewestArena().getSpawns().size();
		
		setScoreboardPhase(SurvivalGames.getScoreboardManager().getNewScoreboardPhase(GameState.WAITING));
		
		setState(GameState.WAITING);
	}
	
	public List<String> getVotedUsers() {
		return voted;
	}
	
	// START SPECTATOR
	private Inventory playerNavigatorInventory;
	private List<SpectatorUser> spectators = new ArrayList<>();
	
	private static ItemStack playerNavigator;
	private static String inventoryTitle;
	
	public static ItemStack getPlayerNavigatorItem() {
		return playerNavigator;
	}
	
	public static String getPlayerNavigatorInventoryTitle() {
		return inventoryTitle;
	}
	
	public Inventory getPlayerNavigatorInventory() {
		return playerNavigatorInventory;
	}
	
	public void redefinePlayerNavigatorInventory() {
		if(playerNavigator != null) {
			int amount = getPlayingUsers();
			int inv = 54;
			if(amount <= 9) {
				inv = 9;
			} else if(amount <= 18) {
				inv = 18;
			} else if(amount <= 27) {
				inv = 27;
			} else if(amount <= 36) {
				inv = 36;
			} else if(amount <= 45) {
				inv = 45;
			} else if(amount <= 54) {
				inv = 54;
			}
			
			playerNavigatorInventory = Bukkit.createInventory(null, inv, inventoryTitle);
			
			ItemStack head = new ItemStack(Material.SKULL_ITEM, 0);
			head.setDurability((short) 3);
			ItemMeta im = head.getItemMeta();
			List<String> lore = new ArrayList<>();
			lore.add("§7Click to spectate!");
			for(int i = 0; i < users.size(); i++) {
				if(i >= inv)
					break;
				User u = users.get(i);
				im.setDisplayName("§e" + u.getName());
				head.setItemMeta(im);
				playerNavigatorInventory.setItem(i, head);
			}
		}
	}
	
	@SuppressWarnings("deprecation")
	public void joinSpectator(final SpectatorUser user) {
		spectators.add(user);
		Arena a = getCurrentArena();
		if(getState() == GameState.DEATHMATCH) {
			user.getPlayer().teleport(a.getDeathmatchSpawns().get(0));
		} else {
			user.getPlayer().teleport(a.getSpawns().get(0));
		}
		
		for(User u : users) {
			u.getPlayer().hidePlayer(user.getPlayer());
		}
		user.clear();
		user.getPlayer().setAllowFlight(true);
		user.getPlayer().setFlying(true);
		user.getPlayer().addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, 1000000, 1));
		
		Bukkit.getScheduler().scheduleSyncDelayedTask(SurvivalGames.instance, new Runnable() {
			public void run() {
				user.getPlayer().getInventory().setItem(8, leaveItem);
				user.getPlayer().getInventory().setItem(7, playerNavigator);
				user.getPlayer().updateInventory();
			}
		}, 2L);
		
		sendSpectators(MessageHandler.getMessage("spectator-join").replace("%0%", user.getName()));
		updateScoreboard();
	}
	
	public void leaveSpectator(SpectatorUser user) {
		spectators.remove(user);
		updateScoreboard();
	}
	
	
	public void sendSpectators(String msg) {
		for(SpectatorUser su : spectators) {
			su.sendMessage(msg);
		}
	}
	
	public List<SpectatorUser> getSpecators() {
		return spectators;
	}
	
	// END SPECTATOR
	
	public User getUser(String name) {
		for(User u : users) {
			if(u.getName().equals(name))
				return u;
		}
		return null;
	}
	
	public int getDeathAmount() {
		return death;
	}
	
	public void setDeathAmount(int death) {
		this.death = death;
	}
	
	@SuppressWarnings("deprecation")
	public void join(User user) throws CloneNotSupportedException {
		users.add(user);
		Player p = user.getPlayer();
		if(arenas.size() == 1) {
			Arena arena = arenas.get(0);
			for(int i = 0; i < arena.getSpawns().size(); i++) {
				if(!hasUserIndex(i)) {
					p.teleport(arena.getSpawns().get(i));
					user.setSpawnIndex(i);
					break;
				}
			}
		} else if(getState() == GameState.COOLDOWN) {
			for(int i = 0; i < getCurrentArena().getSpawns().size(); i++) {
				if(!hasUserIndex(i)) {
					p.teleport(getCurrentArena().getSpawns().get(i));
					user.setSpawnIndex(i);
					break;
				}
			}
		} else {
			p.teleport(lobby);
		}
		user.clear();
		p.getInventory().setItem(8, leaveItem);
		p.updateInventory();
		
		if(getState() == GameState.VOTING) {
			getVotingPhrase().equipPlayer(user);
		}
		
		sendMessage(MessageHandler.getMessage("join-success").replace("%0%", p.getName()).replace("%1%", Integer.valueOf(users.size()).toString()).replace("%2%", Integer.valueOf(maxplayers).toString()));
		SurvivalGames.signManager.updateSigns();
		updateScoreboard();
		checkForStart();
	}
	
	public void forceStart(Player p) throws CloneNotSupportedException {
		if(users.size() < 2) {
			p.sendMessage(MessageHandler.getMessage("prefix") + "§cAt least 2 players are required to start the game!");
			return;
		}
		
		if((getVotingPhrase() != null && getVotingPhrase().isRunning()) || (getCooldownPhrase() != null && getCooldownPhrase().isRunning()) ) {
			p.sendMessage(MessageHandler.getMessage("prefix") + "§cThe game is already starting!");
			return;
		}
		
		forcedStart = true;
		checkForStart();
		p.sendMessage(MessageHandler.getMessage("prefix") + "You've started the game in lobby " + getName() + " successfully!");
	}
	
	public void leave(User user) {
		users.remove(user);
		if(getState() == GameState.INGAME || getState() == GameState.DEATHMATCH)
			redefinePlayerNavigatorInventory();
		checkForCancelStart();
		updateScoreboard();
		SurvivalGames.signManager.updateSigns();
	}
	
	public void kickall() {
		if(users.size() != 0) {
			int size = users.size();
			for(int i = 0; i < size; i++) {
				try {
					SurvivalGames.userManger.leaveGame(users.get(0).getPlayer());
				} catch(IndexOutOfBoundsException e) {
					break;
				}
			}
		}
		if(spectators.size() != 0) {
			int size = spectators.size();
			for(int i = 0; i < size; i++) {
				try {
					SurvivalGames.userManger.leaveGame(spectators.get(0));
				} catch(IndexOutOfBoundsException e) {
					break;
				}
			}
		}
	}
	
	public void end() throws CloneNotSupportedException {
		new ResetPhrase(this);
	}
	
	public DeathmatchPhrase getDeathmatch() {
		return deathmatchPhrase;
	}
	
	public void startDeathmatch() throws CloneNotSupportedException {
		deathmatchPhrase = new DeathmatchPhrase(this);
		deathmatchPhrase.load();
	}
	
	public void startIngame() throws CloneNotSupportedException {
		ingamePhrase = new IngamePhrase(this);
		ingamePhrase.load();
	}
	
	public void startCooldown(Arena arena) throws CloneNotSupportedException {
		cooldownPhrase = new CooldownPhrase(this, arena);
		cooldownPhrase.load();
	}
	
	public boolean isResetEnabled() {
		return reset;
	}
	
	private boolean forcedStart = false;
	public void checkForStart() throws CloneNotSupportedException {
		if(users.size() == reqplayers || forcedStart) {
			if(cooldownPhrase != null && cooldownPhrase.isRunning())
				return;
			if(votingPhrase != null && votingPhrase.isRunning())
				return;
			
			if(getArenas().size() == 1) {
				startCooldown(getArenas().get(0));
			} else {
				if(cooldownPhrase != null) {
					startCooldown(getArenas().get(0));
				} else {
					ingamePhrase = new IngamePhrase(this);
					ingamePhrase.load();
				}
			}
		}
	}
	
	public void checkForCancelStart() {
		if(state != GameState.VOTING && state != GameState.COOLDOWN)
			return;
		
		if(forcedStart) {
			if(users.size() == 1) {
				if(getState() == GameState.COOLDOWN) {
					cooldownPhrase.cancelTask();
					sendMessage(MessageHandler.getMessage("game-start-canceled"));
				} else if(getState() == GameState.VOTING){
					votingPhrase.cancelTask();
					sendMessage(MessageHandler.getMessage("game-start-canceled"));
				}
				forcedStart = false;
			}
			
		} else {
			if(users.size() == reqplayers - 1) {
				if(getState() == GameState.COOLDOWN) {
					cooldownPhrase.cancelTask();
					sendMessage(MessageHandler.getMessage("game-start-canceled"));
				} else if(getState() == GameState.VOTING){
					votingPhrase.cancelTask();
					sendMessage(MessageHandler.getMessage("game-start-canceled"));
				}
			}
		}
	}
	
	public IngamePhrase getIngamePhrase() {
		return ingamePhrase;
	}
	
	public VotingPhrase getVotingPhrase() {
		return votingPhrase;
	}
	
	public CooldownPhrase getCooldownPhrase() {
		return cooldownPhrase;
	}
	
	public Arena getFewestArena() {
		int slot = Integer.MAX_VALUE;
		Arena arena = null;
		
		for(Arena a : arenas) {
			if(a.getSpawns().size() < slot) {
				slot = a.getSpawns().size();
				arena = a;
			}
		}
		
		return arena;
	}
	
	public Arena getArena(String name) {
		for(Arena arena : arenas) {
			if(arena.getName().equals(name)) {
				return arena;
			}
		}
		return null;
	}
	
	public void setCurrentArena(Arena arena) {
		this.arena = arena;
	}
	
	public Arena getCurrentArena() {
		return arena;
	}
	
	public void setState(GameState state) {
		this.state = state;
		if(SurvivalGames.signManager != null)
			SurvivalGames.signManager.updateSigns();
	}
	
	public List<User> getUsers() {
		return users;
	}
	
	public int getPlayingUsers() {
		return users.size();
	}
	
	public String getName() {
		return name;
	}
	
	public Location getLobby() {
		return lobby;
	}
	
	public int getLobbyTime() {
		return lobbytime;
	}
	
	public int getRequiredPlayers() {
		return reqplayers;
	}
	
	public int getMaximumPlayers() {
		return maxplayers;
	}
	
	public boolean isVotingEnabled() {
		return voting;
	}
	
	public int getMaxVotingArenas() {
		return maxVotingArenas;
	}
	
	public List<Arena> getArenas() {
		return arenas;
	}
	
	public GameState getState() {
		return state;
	}
	
	public int getCooldownTime() {
		return cooldown;
	}
	
	public void sendMessage(String message) {
		for(User user : users) {
			user.sendMessage(message);
		}
		for(SpectatorUser su : spectators) {
			su.sendMessage(message);
		}
	}
	
	public boolean hasUserIndex(int index) {
		for(User user : users) {
			if(user.getSpawnIndex() == index) {
				return true;
			}
		}
		return false;
	}
	
	public void registerChest(Chest chest) {
		chests.add(chest);
	}
	
	public List<Chest> getRegisteredChests() {
		return chests;
	}
	
	public Chest getChest(Location loc) {
		for(Chest chest : chests) {
			if(chest.getLocation().equals(loc))
				return chest;
		}
		return null;
	}
	
	public boolean isChestRegistered(Location loc) {
		for(Chest chest : chests) {
			if(chest.getLocation().equals(loc))
				return true;
		}
		return false;
	}
	
	public List<String> getChunksToReset() {
		return rChunks;
	}
	
	public String getAlivePlayers() {
		String s = new String();
		List<User> users = getUsers();
		for(int i = 0; i < users.size(); i++) {
			s += "§e" + users.get(i).getName();
			if(i != users.size() - 1)
				s += "§7, ";
		}
		return s;
	}
	
	// SCOREBOARD
	
	private ScoreboardPhase sp;
	
	public void setScoreboardPhase(ScoreboardPhase sp) {
		if(this.sp != null && sp == null) {
			for(User user : users) {
				user.getPlayer().setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
			}
			
			for(SpectatorUser user : spectators) {
				user.getPlayer().setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
			}
		}
		
		this.sp = sp;
		
		if(sp != null) {
			sp.initScoreboard(this);
			updateScoreboard();
		}

	}
	
	public ScoreboardPhase getScoreboardPhase() {
		return sp;
	}
	
	public void updateScoreboard() {
		if(sp != null) {
			for(CustomScore cs : sp.getScores()) {
				cs.update(this);
			}
			
			for(User user : users) {
				updateScoreboard(user);
			}
			
			for(SpectatorUser su : spectators) {
				updateScoreboard(su);
			}
		}
	}
	
	public void updateScoreboard(UserState user) {
		user.getPlayer().setScoreboard(sp.getScoreboard());
	}

}
