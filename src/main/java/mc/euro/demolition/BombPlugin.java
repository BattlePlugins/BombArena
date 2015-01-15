package mc.euro.demolition;

import java.io.File;
import java.util.Collection;
import java.util.Map;
import mc.alk.arena.BattleArena;
import mc.alk.arena.controllers.BattleArenaController;
import mc.alk.arena.objects.arenas.Arena;
import mc.alk.arena.objects.spawns.ItemSpawn;
import mc.alk.arena.objects.spawns.TimedSpawn;
import mc.alk.arena.objects.victoryconditions.NoTeamsLeft;
import mc.alk.arena.objects.victoryconditions.VictoryType;
import mc.alk.arena.serializers.ArenaSerializer;
import mc.euro.demolition.appljuze.ConfigManager;
import mc.euro.demolition.appljuze.CustomConfig;
import mc.euro.demolition.arenas.BombArena;
import mc.euro.demolition.arenas.SndArena;
import mc.euro.demolition.commands.EodExecutor;
import mc.euro.demolition.debug.*;
import mc.euro.demolition.arenas.factories.BombArenaFactory;
import mc.euro.demolition.arenas.factories.SndArenaFactory;
import mc.euro.demolition.holograms.HologramInterface;
import mc.euro.demolition.holograms.HologramsOff;
import mc.euro.demolition.holograms.HolographicAPI;
import mc.euro.demolition.holograms.HolographicDisplay;
import mc.euro.demolition.tracker.PlayerStats;
import mc.euro.demolition.util.BaseType;
import mc.euro.version.Version;
import mc.euro.version.VersionFactory;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Bukkit plugin that adds the Demolition game types: Sabotage & Search N Destroy. <br/><br/>
 * 
 * @author Nikolai
 */
public class BombPlugin extends JavaPlugin {
    
    /**
     * Adds Bombs Planted and Bombs Defused to the database. <br/>
     * WLT.WIN = Bomb Planted Successfully (opponents base was destroyed). <br/>
     * WLT.LOSS = Plant Failure caused by enemy defusal of the bomb. <br/>
     * WLT.TIE = Bomb Defused by the player. <br/>
     * Notice that in the database, Ties = Losses.
     */
    public PlayerStats ti;
    public ConfigManager manager;
    public CustomConfig basesYml;
    
    BombArenaFactory bombArenaFactory;
    SndArenaFactory sndArenaFactory;
    /**
     * debug = new DebugOn(); <br/>
     * debug = new DebugOff(); <br/>
     * debug.log("x = " + x); <br/>
     * debug.messagePlayer(p, "debug msg"); <br/>
     * debug.msgArenaPlayers(match.getPlayers(), "info"); <br/><br/>
     * 
     */
    public DebugInterface debug;
    private HologramInterface holograms; // HolographicDisplays + HoloAPI
    /**
     * Configuration variables:.
     */
    private int PlantTime;
    private int DetonationTime;
    private int DefuseTime;
    private Sound TimerSound;
    private Material BombBlock;
    private Material BaseBlock;
    private InventoryType Baseinv;
    private int BaseRadius;
    private String FakeName;
    private String ChangeFakeName;
    private int MaxDamage;
    private int DeltaDamage;
    private int DamageRadius;
    private int StartupDisplay;
    private String DatabaseTable;
    
    @Override  
    public void onEnable() {
        
        saveDefaultConfig();
        
        debug = new DebugOn(this);
        loadDefaultConfig();
        
        Version<Plugin> ba = VersionFactory.getPluginVersion("BattleArena");
        debug.log("BattleArena version = " + ba.toString());
        debug.log("BattleTracker version = " + VersionFactory.getPluginVersion("BattleTracker").toString());
        debug.log("Enjin version = " + VersionFactory.getPluginVersion("EnjinMinecraftPlugin").toString());
        // requires 3.9.9.12 or newer
        if (!ba.isCompatible("3.9.9.12")) {
            getLogger().severe("BombArena requires BattleArena v3.9.9.12 or newer.");
            getLogger().info("Disabling BombArena");
            getLogger().info("Please install BattleArena.");
            getLogger().info("http://dev.bukkit.org/bukkit-plugins/battlearena2/");
            Bukkit.getPluginManager().disablePlugin(this); 
            return;
        }
        
        // Database Tables: bt_Demolition_*
        setTracker(this.DatabaseTable);
        
        bombArenaFactory = new BombArenaFactory(this);
        sndArenaFactory = new SndArenaFactory(this);
        BattleArena.registerCompetition(this, "SndArena", "snd", sndArenaFactory, new EodExecutor(this));
        BattleArena.registerCompetition(this, "BombArena", "bomb", bombArenaFactory, new EodExecutor(this));
        
        if (StartupDisplay > 0) {
            getServer().dispatchCommand(Bukkit.getConsoleSender(), "bomb stats top " + StartupDisplay);
        }

        manager = new ConfigManager(this);
        basesYml = manager.getNewConfig("bases.yml");
        
        updateArenasYml(this.BombBlock);
        updateBombArenaConfigYml();
        updateBasesYml();
        if (debug instanceof DebugOn) {
            ArenaSerializer.saveAllArenas(true); // Verbose
        } else {
            ArenaSerializer.saveAllArenas(false); // Silent
        }
    }
    
    public void loadDefaultConfig() {
        
        boolean b = getConfig().getBoolean("Debug", false);
        if (b) {
            debug = new DebugOn(this);
            getLogger().info("Debugging mode is ON");
        } else {
            debug = new DebugOff(this);
            getLogger().info("Debugging mode is OFF.");
        }

        getLogger().info("Loading config.yml");
        PlantTime = getConfig().getInt("PlantTime", 8);
        DetonationTime = getConfig().getInt("DetonationTime", 35);
        DefuseTime = getConfig().getInt("DefuseTime", 1);
        String s = getConfig().getString("TimerSound", "LEVEL_UP");
        try {
            TimerSound = Sound.valueOf(s.toUpperCase());
        } catch (IllegalArgumentException ex) {
            this.TimerSound = Sound.ORB_PICKUP;
        }
        BombBlock = Material.getMaterial(
                getConfig().getString("BombBlock", "TNT").toUpperCase());
        BaseBlock = Material.valueOf(
                getConfig().getString("BaseBlock", "BREWING_STAND").toUpperCase());
        try {
            this.Baseinv = BaseType.convert(BaseBlock);
        } catch (IllegalArgumentException ex) {
            getLogger().warning("loadDefaultConfig() has thrown an IllegalArgumentException");
            getLogger().warning("InventoryType has been set to default, BREWING");
            this.Baseinv = InventoryType.BREWING;
            this.setBaseBlock(Material.BREWING_STAND);
        }
        FakeName = getConfig().getString("FakeName", "Bombs Planted Defused");
        ChangeFakeName = getConfig().getString("ChangeFakeName");
        MaxDamage = getConfig().getInt("MaxDamage", 50);
        DeltaDamage = getConfig().getInt("DeltaDamage", 5);
        DamageRadius = getConfig().getInt("DamageRadius", 9);
        StartupDisplay = getConfig().getInt("StartupDisplay", 5);
        DatabaseTable = getConfig().getString("DatabaseTable", "bombarena");

        debug.log("PlantTime = " + PlantTime + " seconds");
        debug.log("DetonationTime = " + DetonationTime + " seconds");
        debug.log("DefuseTime = " + DefuseTime + " seconds");
        debug.log("BombBlock = " + BombBlock.toString());
        debug.log("BaseBlock = " + BaseBlock.toString());
        debug.log("Baseinv = " + Baseinv.toString());
        
        if (!getConfig().contains("BaseRadius")) {
            getConfig().addDefault("BaseRadius", 3);
        }
        this.BaseRadius = getConfig().getInt("BaseRadius", 3);
        
        if (!getConfig().contains("ShowHolograms")) {
            getConfig().addDefault("ShowHolograms", true);
        }
        boolean ShowHolograms = getConfig().getBoolean("ShowHolograms", true);
        Version HD = VersionFactory.getPluginVersion("HolographicDisplays");
        Version Holoapi = VersionFactory.getPluginVersion("HoloAPI");
        debug.log("HolographicDisplays version = " + HD.toString());
        debug.log("HoloAPI version = " + Holoapi.toString());
        if (ShowHolograms && HD.isCompatible("1.8.5")) {
            this.holograms = new HolographicDisplay(this);
            debug.log("HolographicDisplays support is enabled.");
        } else if (ShowHolograms && Holoapi.isEnabled()) {
            this.holograms = new HolographicAPI(this);
            debug.log("HoloAPI support is enabled.");
        } else {
            this.holograms = new HologramsOff();
            debug.log("Hologram support is disabled.");
            debug.log("Please download HoloAPI or HolographicDisplays to enable Hologram support.");
        }
    }
    
    /**
     * Used by assignBases() and setbase command. <br/><br/>
     * 
     * Uses the players location to find the exact location of a nearby BaseBlock. <br/><br/>
     * 
     * @param loc This is the location of their own base. (NOT the enemy base).
     */
    public Location getExactLocation(Location loc) {
        int length = 5;
        Location base_loc = null;
        this.debug.log("Location loc = " + loc.toString());

        int x1 = loc.getBlockX() - length;
        int y1 = loc.getBlockY() - length;
        int z1 = loc.getBlockZ() - length;

        int x2 = loc.getBlockX() + length;
        int y2 = loc.getBlockY() + length;
        int z2 = loc.getBlockZ() + length;

        World world = loc.getWorld();
        this.debug.log("World world = " + world.getName());

        // Loop over the cube in the x dimension.
        for (int xPoint = x1; xPoint <= x2; xPoint++) {
            // Loop over the cube in the y dimension.
            for (int yPoint = y1; yPoint <= y2; yPoint++) {
                // Loop over the cube in the z dimension.
                for (int zPoint = z1; zPoint <= z2; zPoint++) {
                    // Get the block that we are currently looping over.
                    Block currentBlock = world.getBlockAt(xPoint, yPoint, zPoint);
                    // Set the block to type 57 (Diamond block!)
                    if (currentBlock.getType() == this.BaseBlock) {
                        base_loc = new Location(world, xPoint, yPoint, zPoint);
                        this.debug.log("base_loc = " + base_loc.toString());
                        return base_loc;
                    }
                }
            }
        }
        return loc;
    } // END OF getExactLocation()
    
    public HologramInterface holograms() {
        return this.holograms;
    }

    public int getPlantTime() {
        return PlantTime;
    }

    public void setPlantTime(int PlantTime) {
        this.PlantTime = PlantTime;
    }

    public int getDetonationTime() {
        return DetonationTime;
    }

    public void setDetonationTime(int DetonationTime) {
        this.DetonationTime = DetonationTime;
    }

    public int getDefuseTime() {
        return DefuseTime;
    }

    public void setDefuseTime(int DefuseTime) {
        this.DefuseTime = DefuseTime;
    }

    public Material getBombBlock() {
        return this.BombBlock;
    }

    public void setBombBlock(Material type) {
        this.BombBlock = type;
    }

    public Material getBaseBlock() {
        return BaseBlock;
    }

    public void setBaseBlock(Material type) {
        this.BaseBlock = type;
    }

    public InventoryType getBaseinv() {
        return Baseinv;
    }

    public void setBaseinv(InventoryType type) {
        this.Baseinv = type;
    }

    public String getFakeName() {
        return FakeName;
    }

    public void setFakeName(String fakeName) {
        this.FakeName = fakeName;
    }

    public String getChangeFakeName() {
        return ChangeFakeName;
    }

    public void setChangeFakeName(String fakeName) {
        this.ChangeFakeName = fakeName;
    }

    public int getMaxDamage() {
        return MaxDamage;
    }

    public void setMaxDamage(int max) {
        this.MaxDamage = max;
    }

    public int getDeltaDamage() {
        return DeltaDamage;
    }

    public void setDeltaDamage(int delta) {
        this.DeltaDamage = delta;
    }

    public int getDamageRadius() {
        return DamageRadius;
    }

    public void setDamageRadius(int radius) {
        this.DamageRadius = radius;
    }

    public int getStartupDisplay() {
        return StartupDisplay;
    }

    public void setStartupDisplay(int num) {
        this.StartupDisplay = num;
    }

    public String getDatabaseTable() {
        return DatabaseTable;
    }

    public void setDatabaseTable(String table) {
        this.DatabaseTable = table;
    }
    
    public void setTracker(String x) {
        ti = new PlayerStats(x);
    }
    
    public PlayerStats getTracker() {
        return ti;
    }
    
    public CustomConfig getConfig(String x) {
        return this.manager.getNewConfig(x);
    }

    public double getBaseRadius() {
        return this.BaseRadius;
    }
    
    public Sound getSound() {
        return TimerSound;
    }
    
    /**
     * ****************************************************************************
     * onDisable(): cancelTimers(), updateConfig().saveConfig(), updateArenasYml().
     * ****************************************************************************
     */
    @Override
    public void onDisable() {
        cancelAndClearTimers();
        updateConfig().saveConfig();
        updateArenasYml(this.BombBlock);
        BattleArena.saveArenas(this);
    }
    
    private void cancelAndClearTimers() {
        for (BombArena arena : bombArenaFactory.getArenas()) {
            arena.cancelAndClearTimers();
        }
        for (SndArena arena : sndArenaFactory.getArenas()) {
            arena.cancelAndClearTimers();
        }
    }
    
    /**
     * Only updates non-Integer fields.
     * All Integer fields are updated by EodExecutor.
     * @return an instance of BombPlugin: this.
     */
    private BombPlugin updateConfig() {
        getConfig().set("BaseBlock", this.BaseBlock.name());
        getConfig().set("BombBlock", this.BombBlock.name());
        getConfig().set("DatabaseTable", this.DatabaseTable);
        getConfig().set("FakeName", this.FakeName);
        return this;
    }
    
    /**
     * Use-case scenario:. <br/><br/>
     * 
     * Admin creates arenas: They get created with the current bomb block. <br/>
     * Admin then changes the bomb block inside config.yml. <br/>
     * Then all the previously created arenas in arenas.yml will need to be updated. <br/>
     * 
     * <b>arenas.yml:</b> PATH = "arenas.{arenaName}.spawns.{index}.spawn" <br/><br/>
     * <pre>
     * arenas:
     *   arenaName:
     *     type: BombArena
     *     spawns:
     *       '1':
     *         time: 1 500 500
     *         spawn: BOMB_BLOCK 1
     *         loc: world,-429.0,4.0,-1220.0,1.3,3.8
     * </pre>
     * 
     * @param x The new bomb block material type.
     */
    private void updateArenasYml(Material x) {
        this.debug.log("updating arenas.yml with " + x.name());
        BattleArenaController bc = BattleArena.getBAController();
        Map<String, Arena> amap = bc.getArenas();
        if (amap.isEmpty()) return;
        for (Arena arena : amap.values()) {
            if (arena.getTimedSpawns() == null) continue;
            if ((arena.getArenaType().getName().equalsIgnoreCase("BombArena") 
                    || arena.getArenaType().getName().equalsIgnoreCase("SndArena")) 
                    && arena.getTimedSpawns().containsKey(1L)) {
                Map<Long, TimedSpawn> tmap = arena.getTimedSpawns();
                Location loc = tmap.get(1L).getSpawn().getLocation();
                
                long fs = 1L;
                long rs = arena.getParams().getMatchTime();
                long ds = rs;
                ItemSpawn item = new ItemSpawn (new ItemStack(this.BombBlock, 1));
                item.setLocation(loc);
                TimedSpawn timedSpawn = new TimedSpawn(fs, rs, ds, item);
                tmap.put(1L, timedSpawn);
                bc.updateArena(arena);
            }
        }
        // ArenaSerializer.saveAllArenas(true); // moved to onEnabe()
    }
    
    private void updateBombArenaConfigYml() {
        this.debug.log("updating BombArenaConfig.yml");
        // This needs to be tested.
        BattleArenaController bc = BattleArena.getBAController();
        Map<String, Arena> amap = bc.getArenas();
        for (Arena arena : amap.values()) {
            if ((arena.getArenaType().getName().equalsIgnoreCase("BombArena")
                    || arena.getArenaType().getName().equalsIgnoreCase("SndArena"))) {
                String name = arena.getParams().getVictoryType().getName();
                if (!name.equals("NoTeamsLeft")) {
                    VictoryType type = VictoryType.getType(NoTeamsLeft.class);
                    arena.getParams().setVictoryCondition(type);
                    bc.updateArena(arena);
                    debug.log("The VictoryCondition for BombArena " + arena.getName() + " has been updated to NoTeamsLeft");
                }
            }
        }
        // ArenaSerializer.saveAllArenas(true); // moved to onEnable()
        getConfig("bases.yml");
        /*
         CustomConfig bombarena = getConfig("BombArenaConfig.yml");
         bombarena.set("BombArena.victoryCondition", "NoTeamsLeft");
         bombarena.saveConfig();
         */
    }
    
    /**
     * Move information from bases.yml to arenas.yml then delete? bases.yml.
     */
    private boolean updateBasesYml() {
        debug.log("Transferring bases.yml to arenas.yml");
        File file = new File(getDataFolder(), "bases.yml");
        if (!file.exists()) {
            debug.log("Transfer aborted: bases.yml does NOT exist.");
            debug.log("File = " + file.toString());
            return false;
        }
        BattleArenaController bc = BattleArena.getBAController();
        Map<String, Arena> amap = bc.getArenas();
        if (amap.isEmpty()) {
            debug.log("Transfer aborted: No arenas found.");
            return false;
        }
        for (Arena arena : amap.values()) {
            String name = arena.getName();
            if (!basesYml.contains(name)) {
                debug.log("basesYml does NOT contain: " + name);
                continue;
            }
            String type = arena.getArenaType().getName();
            String msg = "" + type + " " + name;
            boolean isBombArena = type.equalsIgnoreCase("BombArena") && (arena instanceof BombArena);
            boolean isSndArena = type.equalsIgnoreCase("SndArena") && (arena instanceof SndArena);
            if (isBombArena) {
                debug.log(msg + " is of type BombArena");
                BombArena bomb = (BombArena) arena;
                if (!bomb.getCopyOfSavedBases().isEmpty()) {
                    debug.log("skipping " + name + " because it already has persistable data for savedBases.");
                    continue;
                }
                Map<Integer, Location> locations = bomb.getOldBases(name);
                for (Integer index : locations.keySet()) {
                    Location loc = locations.get(index);
                    bomb.addSavedBase(loc);
                }
            } else if (isSndArena) {
                debug.log(msg + " is of type SndArena");
                SndArena snd = (SndArena) arena;
                if (!snd.getCopyOfSavedBases().isEmpty()) {
                    debug.log("skipping " + name + " because it already has persistable data for savedBases.");
                    continue;
                }
                Collection<Location> locations = snd.getOldBases(name);
                for (Location loc : locations) {
                    snd.addSavedBase(loc);
                }
            }
            // BattleArena.saveArenas(this); // moved to onEnable()
        }
        // Should we keep the file ? Or delete it ?
        // file.delete();
        return true;
    }
}
