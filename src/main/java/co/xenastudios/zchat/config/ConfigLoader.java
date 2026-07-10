package co.xenastudios.zchat.config;

import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Loads, validates and migrates {@code config.yml} into an immutable {@link Settings}
 * snapshot.
 *
 * <p>Contract: {@link #load(Plugin)} <b>never throws</b>. Every value is read
 * defensively; a bad value logs a warning and falls back to its documented default
 * (the {@code def} arguments below are the single source of truth, mirrored by the
 * shipped {@code config.yml}). A catastrophic failure falls back to a fully-default
 * snapshot so the plugin can always come up — stability first.
 */
public final class ConfigLoader {

    /** Current config schema version. Bump when adding/renaming keys. */
    public static final int CONFIG_VERSION = 1;

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final FileConfiguration cfg;
    private final Logger log;

    private ConfigLoader(FileConfiguration cfg, Logger log) {
        this.cfg = cfg;
        this.log = log;
    }

    /**
     * Reload the config from disk, migrate it forward, and build a snapshot. Always
     * returns a usable {@link Settings} — never throws.
     */
    public static Settings load(Plugin plugin) {
        Logger log = plugin.getLogger();
        try {
            plugin.saveDefaultConfig();
            plugin.reloadConfig();
            FileConfiguration cfg = plugin.getConfig();

            YamlConfiguration bundled = loadBundledDefaults(plugin);
            if (bundled != null) {
                cfg.setDefaults(bundled);
                cfg.options().copyDefaults(true);
                migrate(plugin, cfg, bundled, log);
            }

            return new ConfigLoader(cfg, log).build();
        } catch (Throwable t) {
            log.log(Level.SEVERE, "Failed to load config; falling back to built-in defaults.", t);
            return defaults(plugin);
        }
    }

    /**
     * Build a fully-default snapshot from the code-level defaults (last resort). Never
     * throws: an empty config yields every documented default, and a hard failure falls
     * back to a hardcoded minimum so {@link #load(Plugin)} can always return a snapshot.
     */
    public static Settings defaults(Plugin plugin) {
        try {
            return new ConfigLoader(new YamlConfiguration(), plugin.getLogger()).build();
        } catch (Throwable t) {
            plugin.getLogger().log(Level.SEVERE, "Default config build failed; using hardcoded minimum.", t);
            return minimal();
        }
    }

    /** Build a {@link Settings} snapshot from an arbitrary config (used by unit tests). */
    static Settings buildFrom(FileConfiguration cfg, Logger log) {
        return new ConfigLoader(cfg, log).build();
    }

    /** A hardcoded, dependency-free snapshot — the absolute floor if everything else fails. */
    private static Settings minimal() {
        Settings.CommandSpec off = new Settings.CommandSpec(false, List.of(), "");
        return new Settings(
                new Settings.Errors(Msg.of("<red>Only players can use this command.</red>"),
                        Msg.of("<red>You don't have permission to do that.</red>")),
                new Settings.Formatting(true, "", "<gray><player>:</gray> <white><message></white>", List.of()),
                new Settings.Filter(false, Settings.Filter.Mode.CENSOR, "*", List.of(), Msg.of("")),
                new Settings.Cooldown(false, 0, Msg.of("")),
                new Settings.ClearChat(off, 100, Msg.of("")),
                new Settings.MuteChat(off, Msg.of(""), Msg.of(""), Msg.of("")),
                new Settings.ToggleChat(off, Msg.of(""), Msg.of("")));
    }

    private static YamlConfiguration loadBundledDefaults(Plugin plugin) {
        try (InputStream in = plugin.getResource("config.yml")) {
            if (in == null) return null;
            return YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Could not read bundled config defaults.", e);
            return null;
        }
    }

    private static void migrate(Plugin plugin, FileConfiguration cfg, YamlConfiguration bundled, Logger log) {
        try {
            int current = cfg.getInt("config-version", 0);
            int target = bundled.getInt("config-version", CONFIG_VERSION);
            if (current < target) {
                cfg.set("config-version", target);
                plugin.saveConfig();
                log.info("Migrated config.yml from version " + current + " to " + target + ".");
            }
        } catch (Exception e) {
            log.log(Level.WARNING, "Config migration step failed (continuing with in-memory values).", e);
        }
    }

    // ---- build -------------------------------------------------------------

    private Settings build() {
        Settings.Errors errors = new Settings.Errors(
                msg("messages.error.player-only", "<red>Only players can use this command.</red>"),
                msg("messages.error.no-permission", "<red>You don't have permission to do that.</red>")
        );

        return new Settings(errors, formatting(), filter(), cooldown(),
                clearChat(), muteChat(), toggleChat());
    }

    // ---- formatting --------------------------------------------------------

    private Settings.Formatting formatting() {
        boolean enabled = bool("formatting.enabled", true);
        String colorPerm = permission("formatting.color-permission", "zchat.chat.color");
        String defaultFormat = format("formatting.default-format",
                "<gray><player>:</gray> <white><message></white>");

        List<Settings.Group> groups = new ArrayList<>();
        ConfigurationSection section = cfg.getConfigurationSection("formatting.groups");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                String base = "formatting.groups." + key;
                String name = key.toLowerCase(Locale.ROOT);
                int weight = cfg.getInt(base + ".weight", 0);
                String perm = permission(base + ".permission", "");
                String fmt = format(base + ".format", defaultFormat);
                groups.add(new Settings.Group(name, weight, perm, fmt));
            }
        }
        // Highest weight first so the listener returns the first permission match.
        groups.sort((a, b) -> Integer.compare(b.weight(), a.weight()));
        return new Settings.Formatting(enabled, colorPerm, defaultFormat, List.copyOf(groups));
    }

    // ---- filter ------------------------------------------------------------

    private Settings.Filter filter() {
        boolean enabled = bool("filter.enabled", true);
        Settings.Filter.Mode mode = enumValue("filter.mode",
                Settings.Filter.Mode.CENSOR, Settings.Filter.Mode.class);
        String censorChar = str("filter.censor-char", "*");
        if (censorChar.isEmpty()) {
            censorChar = "*";
        }

        List<Pattern> patterns = new ArrayList<>();
        for (String raw : cfg.getStringList("filter.patterns")) {
            if (raw == null || raw.isBlank()) {
                continue;
            }
            try {
                patterns.add(Pattern.compile(raw, Pattern.CASE_INSENSITIVE));
            } catch (Exception e) {
                log.warning("Filter pattern '" + raw + "' is not valid regex; skipped.");
            }
        }

        Msg blocked = msg("filter.message-blocked",
                "<red>Your message was blocked by the chat filter.</red>");
        return new Settings.Filter(enabled, mode, censorChar, List.copyOf(patterns), blocked);
    }

    // ---- cooldown ----------------------------------------------------------

    private Settings.Cooldown cooldown() {
        boolean enabled = bool("cooldown.enabled", true);
        double seconds = Math.max(0, cfg.getDouble("cooldown.seconds", 3));
        Msg message = msg("cooldown.message",
                "<red>Please wait <yellow><seconds></yellow>s before chatting again.</red>");
        return new Settings.Cooldown(enabled, Math.round(seconds * 1000L), message);
    }

    // ---- commands ----------------------------------------------------------

    private Settings.ClearChat clearChat() {
        Settings.CommandSpec spec = spec("clearchat", "zchat.command.clearchat", List.of("cc"));
        int lines = clampInt("clearchat.lines", 100, 1, 300);
        return new Settings.ClearChat(spec, lines,
                msg("clearchat.message-cleared", "<gray>Chat was cleared by <yellow><player></yellow>.</gray>"));
    }

    private Settings.MuteChat muteChat() {
        Settings.CommandSpec spec = spec("mutechat", "zchat.command.mutechat", List.of("mc"));
        return new Settings.MuteChat(spec,
                msg("mutechat.message-muted", "<gray>Chat has been <red>muted</red> by <yellow><player></yellow>.</gray>"),
                msg("mutechat.message-unmuted", "<gray>Chat has been <green>unmuted</green> by <yellow><player></yellow>.</gray>"),
                msg("mutechat.message-blocked", "<red>Chat is currently muted.</red>"));
    }

    private Settings.ToggleChat toggleChat() {
        Settings.CommandSpec spec = spec("togglechat", "zchat.command.togglechat", List.of("tc"));
        return new Settings.ToggleChat(spec,
                msg("togglechat.message-on", "<gray>Chat is now <green>shown</green>.</gray>"),
                msg("togglechat.message-off", "<gray>Chat is now <red>hidden</red>.</gray>"));
    }

    private Settings.CommandSpec spec(String base, String defPerm, List<String> defAliases) {
        boolean enabled = bool(base + ".enabled", true);
        List<String> aliases = aliasList(base + ".aliases", defAliases);
        String permission = permission(base + ".permission", defPerm);
        return new Settings.CommandSpec(enabled, aliases, permission);
    }

    // ---- validated readers -------------------------------------------------

    private boolean bool(String path, boolean def) {
        return cfg.getBoolean(path, def);
    }

    private int clampInt(String path, int def, int min, int max) {
        int v = cfg.getInt(path, def);
        if (v < min || v > max) {
            int clamped = Math.max(min, Math.min(max, v));
            warn(path, v, clamped, "out of range [" + min + ", " + max + "]");
            return clamped;
        }
        return v;
    }

    private String str(String path, String def) {
        String v = cfg.getString(path, def);
        return v == null ? def : v;
    }

    private <E extends Enum<E>> E enumValue(String path, E def, Class<E> type) {
        String v = cfg.getString(path, def.name());
        if (v == null) {
            return def;
        }
        try {
            return Enum.valueOf(type, v.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            warn(path, v, def.name().toLowerCase(Locale.ROOT), "not a recognised option");
            return def;
        }
    }

    /**
     * Read a permission node. An absent key yields {@code def}; an explicitly blank
     * value yields {@code ""}, which means "no restriction".
     */
    private String permission(String path, String def) {
        String v = cfg.getString(path, def);
        return v == null ? def : v.trim();
    }

    private List<String> aliasList(String path, List<String> def) {
        List<String> raw = cfg.getStringList(path);
        if (raw.isEmpty() && !cfg.isList(path)) {
            raw = def;
        }
        List<String> out = new ArrayList<>(raw.size());
        for (String s : raw) {
            if (s == null) continue;
            String a = s.trim().toLowerCase(Locale.ROOT);
            if (!a.isBlank() && !out.contains(a)) {
                out.add(a);
            }
        }
        return List.copyOf(out);
    }

    /** A raw MiniMessage template (kept as a string; parsed per use). Validated only. */
    private String format(String path, String def) {
        String raw = cfg.getString(path, def);
        if (raw == null || raw.isBlank()) {
            raw = def;
        }
        try {
            MM.deserialize(raw);
        } catch (Exception e) {
            warn(path, raw, def, "invalid MiniMessage");
            raw = def;
        }
        return raw;
    }

    /** Required MiniMessage message; blank/absent falls back to {@code def}. */
    private Msg msg(String path, String def) {
        String raw = cfg.getString(path, def);
        if (raw == null || raw.isBlank()) {
            raw = def;
        }
        try {
            MM.deserialize(raw);
        } catch (Exception e) {
            warn(path, raw, "(plain text)", "invalid MiniMessage");
        }
        return Msg.of(raw);
    }

    private void warn(String path, Object bad, Object used, String why) {
        log.warning("Config '" + path + "' = '" + bad + "' is invalid (" + why
                + "); using '" + used + "' instead.");
    }
}
