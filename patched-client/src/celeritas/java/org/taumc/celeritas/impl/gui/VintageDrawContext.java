//? if >=1.8 {
package org.taumc.celeritas.impl.gui;

import com.mojang.blaze3d.platform.GlStateManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiElement;
import net.minecraft.client.render.TextRenderer;
import net.minecraft.client.render.Window;
import net.minecraft.client.render.texture.DynamicTexture;
import net.minecraft.resource.Identifier;
import net.minecraft.text.Formatting;
import net.minecraft.text.LiteralText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableText;
import org.embeddedt.embeddium.impl.gui.framework.DrawContext;
import org.embeddedt.embeddium.impl.gui.framework.TextComponent;
import org.embeddedt.embeddium.impl.gui.framework.TextFormattingStyle;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.opengl.GL11;
import org.taumc.celeritas.impl.Celeritas;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 1.8.9 implementation of the shared GUI drawing surface.
 */
public class VintageDrawContext implements DrawContext {
    private final TextRenderer font = Minecraft.getInstance().textRenderer;
    private final Map<TextComponent, Text> componentCache = new HashMap<>();

    /**
     * The GUI icon is loaded straight from the class path instead of through the resource manager: a patched
     * client (no mod loader) only exposes the {@code minecraft} and {@code realms} resource domains, so an
     * {@code assets/celeritas/...} texture can never be resolved by {@code TextureManager}. Registering a
     * {@link DynamicTexture} under its own {@link Identifier} sidesteps that entirely.
     */
    private static final String CELERITAS_LOGO = "/assets/celeritas/textures/gui/logo.png";
    private static boolean celeritasLogoRegistered;
    @Nullable
    private static String celeritasLogoId;

    private Text convert(TextComponent component) {
        if (component instanceof TextComponent.Literal literal) {
            return new LiteralText(literal.text());
        } else if (component instanceof TextComponent.Translatable translatable) {
            return new TranslatableText(translatable.keys().get(0), translatable.args().stream()
                    .map(arg -> arg instanceof TextComponent c ? convert(c) : arg)
                    .toArray());
        } else if (component instanceof TextComponent.Styled styled) {
            Text text = convert(styled.inner()).copy();
            Style style = text.getStyle();
            for (TextFormattingStyle formatting : styled.styles()) {
                if (formatting.isColor()) {
                    style = style.setColor(Formatting.byId(formatting.ordinal()));
                } else {
                    style = switch (formatting) {
                        case STRIKETHROUGH -> style.setStrikethrough(true);
                        case UNDERLINE -> style.setUnderlined(true);
                        case ITALIC -> style.setItalic(true);
                        default -> throw new IllegalArgumentException("Unknown style: " + formatting);
                    };
                }
            }
            text.setStyle(style);
            return text;
        }
        throw new IllegalArgumentException("Unexpected text component: " + component.getClass());
    }

    private Text compile(TextComponent component) {
        return this.componentCache.computeIfAbsent(component, this::convert);
    }

    @Override
    public void fill(int x1, int y1, int x2, int y2, int color) {
        GuiElement.fill(x1, y1, x2, y2, color);
    }

    @Override
    public int drawString(TextComponent str, int x, int y, int color, boolean shadow) {
        String text = compile(str).getFormattedString();
        if (shadow) {
            return font.drawWithShadow(text, x, y, color);
        }
        return font.draw(text, x, y, color);
    }

    @Override
    public void blitWholeImage(String icon, int x, int y, int width, int height) {
        Minecraft.getInstance().getTextureManager().bind(new Identifier(icon));
        GuiElement.drawTexture(x, y, 0.0f, 0.0f, width, height, width, height);
    }

    @Override
    public void pushMatrix() {
        GlStateManager.pushMatrix();
    }

    @Override
    public void translate(double x, double y, double z) {
        GlStateManager.translated(x, y, z);
    }

    @Override
    public void popMatrix() {
        GlStateManager.popMatrix();
    }

    @Override
    public void enableScissor(int x1, int y1, int x2, int y2) {
        int width = x2 - x1 + 1;
        int height = y2 - y1 + 1;
        Minecraft mc = Minecraft.getInstance();
        int scale = new Window(mc).getScale();
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor(x1 * scale, mc.height - (y1 + height) * scale, width * scale, height * scale);
    }

    @Override
    public void disableScissor() {
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
    }

    @Override
    public int getStringWidth(TextComponent component) {
        return font.getWidth(compile(component).getString());
    }

    @Override
    public String substrByWidth(String str, int maxWidth, boolean reverse) {
        return font.trim(str, maxWidth, reverse);
    }

    @Override
    public @Nullable String getModLogoPath(String modId) {
        if (!"celeritas".equals(modId)) {
            return null;
        }
        if (!celeritasLogoRegistered) {
            celeritasLogoRegistered = true;
            celeritasLogoId = registerCeleritasLogo();
        }
        return celeritasLogoId;
    }

    @Override
    public TextComponent getFriendlyModName(String modId) {
        return "celeritas".equals(modId) ? TextComponent.literal("Celeritas") : TextComponent.literal(modId);
    }

    @Nullable
    private static String registerCeleritasLogo() {
        try (InputStream in = VintageDrawContext.class.getResourceAsStream(CELERITAS_LOGO)) {
            if (in == null) {
                Celeritas.logger().warn("Missing Celeritas logo at {}", CELERITAS_LOGO);
                return null;
            }
            BufferedImage image = ImageIO.read(in);
            if (image == null) {
                Celeritas.logger().warn("Could not decode the Celeritas logo at {}", CELERITAS_LOGO);
                return null;
            }
            Identifier id = new Identifier("celeritas", "textures/gui/logo.png");
            Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(image));
            return id.toString();
        } catch (IOException e) {
            Celeritas.logger().warn("Could not load the Celeritas logo", e);
            return null;
        }
    }

    @Override
    public List<TextComponent> split(TextComponent component, int maxWidth) {
        return font.split(compile(component).getFormattedString(), maxWidth).stream()
                .map(TextComponent::literal)
                .collect(Collectors.toList());
    }

    @Override
    public String extractString(TextComponent component) {
        return compile(component).getString();
    }

    @Override
    public int lineHeight() {
        return font.fontHeight;
    }
}
//?}
