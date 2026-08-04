package me.andromedov.mixer.api.addon;

public interface MixerAddon {
    String id();

    default String name() {
        return id();
    }

    default String version() {
        return "unknown";
    }

    default void onEnable(MixerAddonContext context) throws Exception { }

    default void onDisable() throws Exception { }
}
