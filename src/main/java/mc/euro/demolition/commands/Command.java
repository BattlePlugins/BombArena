package mc.euro.demolition.commands;

import mc.euro.version.Version;
import mc.euro.version.VersionFactory;

/**
 * This class handles backwards compatibility for different 
 * versions of BattleArena commands.
 * 
 * <pre>
 * 
 * Version - Syntax
 * 
 * +3.9.6.2   - /aa addspawn {block} fs=1 rs=500 ds=500 index=1
 * -3.9.5.8.5 - /aa addspawn {block} fs=1 rs=500 ds=500 1
 * 
 * </pre>
 * 
 * @author Nikolai
 */
public abstract class Command {

    public static String addspawn(String bomb, int time) {
        // /aa addspawn BOMB_BLOCK fs=1 rs=500 ds=500 index=1
        // /aa addspawn BOMB_BLOCK fs=1 rs=500 ds=500 1
        String newCmd = "aa addspawn " + bomb
                + " fs=1"
                + " rs=300"
                + " ds=" + time
                + " index=1";
        String oldCmd = "aa addspawn " + bomb
                + " fs=1"
                + " rs=300"
                + " ds=" + time
                + " 1";
        Version v = VersionFactory.getPluginVersion("BattleArena");
        String cmd = v.isCompatible("3.9.6.2") ? newCmd : oldCmd;
        return cmd;
    }

}
