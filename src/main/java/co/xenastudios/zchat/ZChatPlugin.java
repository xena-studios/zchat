package co.xenastudios.zchat;

import co.xenastudios.zchat.chat.ChatListener;
import co.xenastudios.zchat.chat.ChatState;
import co.xenastudios.zchat.command.CommandRegistrar;
import co.xenastudios.zchat.config.ConfigLoader;
import co.xenastudios.zchat.config.Settings;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Level;

/**
 * zChat — lightweight, group-based chat formatting and moderation for Paper (and Folia).
 *
 * <p>Design contract, in priority order: <b>never take the server down</b> (stability),
 * keep the async chat path allocation-light (performance), be fully configurable, and
 * stay clean and modular. {@link #onEnable()} and {@link #reload()} are wrapped so they
 * can never throw out; a failing feature is logged and skipped rather than disabling the
 * plugin.
 *
 * <p>Configuration lives in an immutable {@link Settings} snapshot held in a
 * {@code volatile} field and swapped atomically on reload, so the chat listener never
 * reads {@code getConfig()} or re-parses a static message. Mutable runtime state (the
 * global mute flag, per-player "chat hidden" set and cooldown timestamps) lives in a
 * thread-safe {@link ChatState} that survives reloads. Commands are registered once via
 * the stable Paper Brigadier API ({@link LifecycleEvents#COMMANDS}); the single listener
 * is the chat pipeline itself.
 */
public final class ZChatPlugin extends JavaPlugin {

    private volatile Settings settings;
    private final ChatState chatState = new ChatState();
    private Listener chatListener;

    @Override
    public void onEnable() {
        try {
            this.settings = ConfigLoader.load(this);

            // Register commands via the stable Brigadier lifecycle. The handler reads the
            // current settings snapshot, so only enabled commands are ever built.
            getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS,
                    event -> CommandRegistrar.registerAll(event.registrar(), this));

            this.chatListener = new ChatListener(this);
            getServer().getPluginManager().registerEvents(chatListener, this);

            getLogger().info("zChat enabled (" + getPluginMeta().getVersion() + ").");
        } catch (Throwable t) {
            // Absolute backstop: even total failure must not crash the server.
            getLogger().log(Level.SEVERE, "zChat failed to fully enable; running in degraded mode.", t);
        }
    }

    @Override
    public void onDisable() {
        try {
            if (chatListener != null) {
                HandlerList.unregisterAll(chatListener);
            }
            chatState.clear();
        } catch (Throwable t) {
            getLogger().log(Level.WARNING, "Error during disable (ignored).", t);
        }
    }

    /**
     * Re-read + re-validate config and swap in the fresh snapshot atomically. All
     * message/behaviour values apply live; structural values (which commands are
     * registered, their aliases) are restart-only. Runtime state (mute/toggle/cooldowns)
     * is preserved. Never throws.
     *
     * @return true if the reload completed cleanly.
     */
    public boolean reload() {
        try {
            Settings previous = this.settings;
            Settings fresh = ConfigLoader.load(this);
            warnStructural(previous, fresh);
            this.settings = fresh; // atomic swap — readers see old or new, never partial
            return true;
        } catch (Throwable t) {
            getLogger().log(Level.SEVERE, "Reload failed; keeping previous state.", t);
            return false;
        }
    }

    /**
     * Command {@code enabled}/{@code aliases} are consumed once at registration, so a
     * change to them on reload has no effect until restart. Log a warning rather than
     * letting it silently no-op.
     */
    private void warnStructural(Settings previous, Settings fresh) {
        if (previous == null) {
            return;
        }
        warnIfChanged("clearchat", previous.clearChat().spec(), fresh.clearChat().spec());
        warnIfChanged("mutechat", previous.muteChat().spec(), fresh.muteChat().spec());
        warnIfChanged("togglechat", previous.toggleChat().spec(), fresh.toggleChat().spec());
    }

    private void warnIfChanged(String name, Settings.CommandSpec before, Settings.CommandSpec after) {
        if (before.enabled() != after.enabled() || !before.aliases().equals(after.aliases())) {
            getLogger().warning("Config '" + name + ".enabled/aliases' changed; this is "
                    + "restart-only (command registration is fixed at startup).");
        }
    }

    // ---- accessors ---------------------------------------------------------

    /** Current immutable settings snapshot (safe to read from any thread). */
    public Settings settings() {
        return settings;
    }

    /** Thread-safe mutable runtime chat state. */
    public ChatState chatState() {
        return chatState;
    }
}
