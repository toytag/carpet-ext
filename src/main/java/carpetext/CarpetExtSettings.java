package carpetext;

import carpet.settings.Rule;

import static carpet.settings.RuleCategory.FEATURE;

/**
 * Here is your example Settings class you can plug to use carpetmod /carpet settings command
 */
public class CarpetExtSettings
{
    public static final String EXT = "extensions";

    @Rule(
        desc = "Allow horizontally moving Ender Pearls to load chunks as entity ticking",
        category = {FEATURE, EXT}
    )
    public static boolean enderPearlChunkLoading = false;
}
