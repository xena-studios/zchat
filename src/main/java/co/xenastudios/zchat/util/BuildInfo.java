package co.xenastudios.zchat.util;

import org.bukkit.plugin.Plugin;

import java.io.InputStream;
import java.util.Properties;

/**
 * Reads the git-derived build metadata injected at build time into
 * {@code build-info.properties}. Fully defensive — missing values read as
 * {@code "unknown"} rather than throwing.
 */
public final class BuildInfo {

    private final String version;
    private final String commit;
    private final String shortCommit;
    private final String buildTimestamp;

    private BuildInfo(Properties props) {
        this.version = props.getProperty("version", "unknown");
        this.commit = props.getProperty("commit", "unknown");
        this.shortCommit = props.getProperty("shortCommit", "unknown");
        this.buildTimestamp = props.getProperty("buildTimestamp", "unknown");
    }

    public static BuildInfo load(Plugin plugin) {
        Properties props = new Properties();
        try (InputStream in = plugin.getResource("build-info.properties")) {
            if (in != null) {
                props.load(in);
            }
        } catch (Exception ignored) {
            // Fall through to defaults.
        }
        return new BuildInfo(props);
    }

    public String version() {
        return version;
    }

    public String commit() {
        return commit;
    }

    public String shortCommit() {
        return shortCommit;
    }

    public String buildTimestamp() {
        return buildTimestamp;
    }
}
