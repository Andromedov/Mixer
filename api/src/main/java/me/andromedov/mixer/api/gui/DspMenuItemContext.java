package me.andromedov.mixer.api.gui;

import java.util.Objects;

/** Per-item render data for the DSP editor. */
public record DspMenuItemContext(DspMenuContext menu, DspMenuElement element, int slot) {
    public DspMenuItemContext {
        menu = Objects.requireNonNull(menu, "menu");
        element = Objects.requireNonNull(element, "element");
        if (slot < 0) throw new IllegalArgumentException("slot must not be negative");
    }
}
