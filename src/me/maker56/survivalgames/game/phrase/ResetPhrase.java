package me.maker56.survivalgames.game.phrase;

import me.maker56.survivalgames.SurvivalGames;
import me.maker56.survivalgames.game.Game;
import me.maker56.survivalgames.game.GameState;
import me.maker56.survivalgames.reset.Reset;

import org.bukkit.World;
import org.bukkit.entity.Animals;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Monster;

public class ResetPhrase {
	
	private Game game;
	
	public ResetPhrase(Game game) throws CloneNotSupportedException {
		this.game = game;
		start();
	}
	
	private void start() throws CloneNotSupportedException {
		game.kickall();
		game.setState(GameState.RESET);
		World w = game.getCurrentArena().getMinimumLocation().getWorld();
		
		
		for(Entity e : w.getEntities()) {
			if(e instanceof Item || e instanceof Animals || e instanceof Monster || e instanceof Arrow) {
				if(game.getCurrentArena().containsBlock(e.getLocation()))
					e.remove();
			}
		}
		
		if(game.isResetEnabled()) {
			new Reset(w, game.getName(), game.getCurrentArena().getName(), game.getChunksToReset()).start();
		} else {
			String name = game.getName();
			SurvivalGames.gameManager.unload(game);
			SurvivalGames.gameManager.load(name);
			SurvivalGames.signManager.updateSigns();
		}
	}

}
