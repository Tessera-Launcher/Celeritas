//? if >=1.8 {
package org.taumc.celeritas.impl.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screen.Screen;
import org.embeddedt.embeddium.impl.gui.CeleritasVideoOptionsController;
import org.embeddedt.embeddium.impl.gui.framework.InteractionContext.EditAction;
import org.embeddedt.embeddium.impl.gui.options.CommonOptionPages;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.taumc.celeritas.api.options.structure.OptionFlag;
import org.taumc.celeritas.impl.Celeritas;

import java.io.IOException;
import java.util.Arrays;
import java.util.Set;

public class CeleritasVideoOptionsScreen extends Screen {
    private final Screen parent;
    private final CeleritasVideoOptionsController controller;

    private int lastMouseX, lastMouseY;

    public CeleritasVideoOptionsScreen(Screen parent) {
        this.parent = parent;
        this.controller = new CeleritasVideoOptionsController(
                () -> Minecraft.getInstance().openScreen(this.parent),
                Arrays.asList(
                        SodiumGameOptionPages.general(),
                        SodiumGameOptionPages.quality(),
                        CommonOptionPages.performance(Celeritas.options()),
                        SodiumGameOptionPages.advanced()),
                new VintageDrawContext()) {
            @Override
            protected void applyFlagSideEffects(Set<OptionFlag> flags) {
                super.applyFlagSideEffects(flags);
                Minecraft client = Minecraft.getInstance();
                if (flags.contains(OptionFlag.REQUIRES_RENDERER_RELOAD) && client.world != null) {
                    client.worldRenderer.reload();
                }
                if (flags.contains(OptionFlag.REQUIRES_ASSET_RELOAD)) {
                    client.reloadResources();
                }
            }
        };
        resetDrag();
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) {
        this.controller.getFrame().mouseClicked(VintageInteractionContext.INSTANCE, mouseX, mouseY, mouseButton);
        lastMouseX = mouseX;
        lastMouseY = mouseY;
    }

    @Override
    protected void mouseReleased(int mouseX, int mouseY, int mouseButton) {
        this.controller.getFrame().mouseReleased(VintageInteractionContext.INSTANCE, mouseX, mouseY, mouseButton);
        resetDrag();
    }

    private void resetDrag() {
        lastMouseX = -1;
        lastMouseY = -1;
    }

    @Override
    protected void mouseDragged(int mouseX, int mouseY, int clickedMouseButton, long timeSinceLastClick) {
        super.mouseDragged(mouseX, mouseY, clickedMouseButton, timeSinceLastClick);
        if (lastMouseY != -1 && lastMouseX != -1) {
            this.controller.getFrame().mouseDragged(
                    VintageInteractionContext.INSTANCE,
                    mouseX, mouseY, clickedMouseButton,
                    mouseX - lastMouseX, mouseY - lastMouseY);
        }
        lastMouseX = mouseX;
        lastMouseY = mouseY;
    }

    @Override
    public void handleMouse() {
        super.handleMouse();
        int wheel = Mouse.getEventDWheel();
        if (wheel != 0) {
            Minecraft mc = Minecraft.getInstance();
            int mouseX = Mouse.getEventX() * this.width / mc.width;
            int mouseY = this.height - Mouse.getEventY() * this.height / mc.height - 1;
            double delta = wheel > 0 ? 1 : -1;
            this.controller.getFrame().mouseScrolled(VintageInteractionContext.INSTANCE, mouseX, mouseY, 0, delta);
        }
    }

    @Override
    protected void keyPressed(char typedChar, int keyCode) {
        EditAction editAction = toEditAction(keyCode);
        if (editAction != null ? this.controller.keyPressed(VintageInteractionContext.INSTANCE, editAction)
                : this.controller.charTyped(typedChar)) {
            return;
        }
        super.keyPressed(typedChar, keyCode);
    }

    private static @Nullable EditAction toEditAction(int keyCode) {
        if (Screen.isSelectAll(keyCode)) {
            return EditAction.SELECT_ALL;
        } else if (Screen.isCopy(keyCode)) {
            return EditAction.COPY;
        } else if (Screen.isPaste(keyCode)) {
            return EditAction.PASTE;
        } else if (Screen.isCut(keyCode)) {
            return EditAction.CUT;
        }
        if (keyCode == Keyboard.KEY_BACK) {
            return EditAction.BACKSPACE;
        } else if (keyCode == Keyboard.KEY_DELETE) {
            return EditAction.DELETE;
        } else if (keyCode == Keyboard.KEY_LEFT) {
            return EditAction.LEFT;
        } else if (keyCode == Keyboard.KEY_RIGHT) {
            return EditAction.RIGHT;
        } else if (keyCode == Keyboard.KEY_HOME) {
            return EditAction.HOME;
        } else if (keyCode == Keyboard.KEY_END) {
            return EditAction.END;
        }
        return null;
    }

    @Override
    public void render(int mouseX, int mouseY, float partialTicks) {
        this.renderBackground();
        this.controller.render(new VintageDrawContext(), mouseX, mouseY, partialTicks);
    }

    @Override
    public void init() {
        this.controller.init(this.width, this.height);
    }
}
//?}
