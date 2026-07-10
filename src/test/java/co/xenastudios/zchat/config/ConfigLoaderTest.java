package co.xenastudios.zchat.config;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Server-free coverage of the validated config readers via the {@code buildFrom} test
 * seam. Uses a bare {@link YamlConfiguration} (SnakeYAML-backed, no running server).
 */
class ConfigLoaderTest {

    private static final Logger LOG = Logger.getLogger("zchat-test");

    private static Settings build(YamlConfiguration cfg) {
        return ConfigLoader.buildFrom(cfg, LOG);
    }

    @Test
    void emptyConfigYieldsDocumentedDefaults() {
        Settings s = build(new YamlConfiguration());
        assertTrue(s.formatting().enabled());
        assertEquals(Settings.Filter.Mode.CENSOR, s.filter().mode());
        assertEquals(3000, s.cooldown().millis());          // 3s default
        assertEquals(100, s.clearChat().lines());
        assertEquals(List.of("cc"), s.clearChat().spec().aliases());
    }

    @Test
    void cooldownSecondsConvertToMillis() {
        YamlConfiguration cfg = new YamlConfiguration();
        cfg.set("cooldown.seconds", 2.5);
        assertEquals(2500, build(cfg).cooldown().millis());
    }

    @Test
    void filterModeParsesAndFallsBack() {
        YamlConfiguration block = new YamlConfiguration();
        block.set("filter.mode", "block");
        assertEquals(Settings.Filter.Mode.BLOCK, build(block).filter().mode());

        YamlConfiguration bad = new YamlConfiguration();
        bad.set("filter.mode", "nonsense");
        assertEquals(Settings.Filter.Mode.CENSOR, build(bad).filter().mode()); // fallback
    }

    @Test
    void clearchatLinesAreClamped() {
        YamlConfiguration cfg = new YamlConfiguration();
        cfg.set("clearchat.lines", 9999);
        assertEquals(300, build(cfg).clearChat().lines());   // clamped to max
    }

    @Test
    void invalidRegexPatternIsSkipped() {
        YamlConfiguration cfg = new YamlConfiguration();
        cfg.set("filter.patterns", List.of("valid", "[unclosed"));
        assertEquals(1, build(cfg).filter().patterns().size()); // the bad one is dropped
    }

    @Test
    void groupsAreSortedHighestWeightFirst() {
        YamlConfiguration cfg = new YamlConfiguration();
        cfg.set("formatting.groups.low.weight", 1);
        cfg.set("formatting.groups.low.permission", "zchat.group.low");
        cfg.set("formatting.groups.low.format", "<message>");
        cfg.set("formatting.groups.high.weight", 50);
        cfg.set("formatting.groups.high.permission", "zchat.group.high");
        cfg.set("formatting.groups.high.format", "<message>");

        List<Settings.Group> groups = build(cfg).formatting().groups();
        assertEquals("high", groups.get(0).name());
        assertEquals("low", groups.get(1).name());
    }

    @Test
    void featuresCanBeDisabled() {
        YamlConfiguration cfg = new YamlConfiguration();
        cfg.set("mutechat.enabled", false);
        cfg.set("formatting.enabled", false);
        Settings s = build(cfg);
        assertFalse(s.muteChat().spec().enabled());
        assertFalse(s.formatting().enabled());
    }
}
