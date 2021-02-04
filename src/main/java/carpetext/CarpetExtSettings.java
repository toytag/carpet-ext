package carpetext;

import carpet.settings.Rule;

import static carpet.settings.RuleCategory.COMMAND;
import static carpet.settings.RuleCategory.FEATURE;

/**
 * Here is your example Settings class you can plug to use carpetmod /carpet settings command
 */
public class CarpetExtSettings {
    public static final String EXT = "extensions";

    @Rule(
        desc = "Command `/fly` for players (perticularly in survival) to enable/disable fly ability",
        category = {COMMAND, EXT}
    )
    public static boolean commandFly = true;
    
    @Rule(
        desc = "Allow moving Ender Pearls to load chunks and skip empty chunks as entity ticking",
        category = {FEATURE, EXT}
    )
    public static boolean enderPearlChunkLoadingAndSkipping = false;
}
