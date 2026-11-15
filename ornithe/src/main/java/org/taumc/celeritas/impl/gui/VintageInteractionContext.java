//? if >=1.8 {
package org.taumc.celeritas.impl.gui;

import net.minecraft.client.gui.screen.Screen;
import org.embeddedt.embeddium.impl.gui.framework.InteractionContext;

public enum VintageInteractionContext implements InteractionContext {
    INSTANCE;

    @Override
    public boolean isSpecialKeyDown(SpecialKey key) {
        return switch (key) {
            case SHIFT -> Screen.isShiftDown();
            case CTRL -> Screen.isControlDown();
            case ALT -> Screen.isAltDown();
        };
    }

    @Override
    public String getClipboard() {
        return Screen.getClipboard();
    }

    @Override
    public void setClipboard(String text) {
        Screen.setClipboard(text);
    }
}
//?}
