//? if >=1.8 {
package org.taumc.celeritas.impl.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.options.GameOptions;
import net.minecraft.client.resource.language.I18n;
import org.embeddedt.embeddium.impl.gui.SodiumGameOptions;
import org.embeddedt.embeddium.impl.gui.framework.TextComponent;
import org.lwjgl.opengl.Display;
import org.taumc.celeritas.api.options.control.ControlValueFormatter;
import org.taumc.celeritas.api.options.control.CyclingControl;
import org.taumc.celeritas.api.options.control.SliderControl;
import org.taumc.celeritas.api.options.control.TickBoxControl;
import org.taumc.celeritas.api.options.structure.OptionFlag;
import org.taumc.celeritas.api.options.structure.OptionGroup;
import org.taumc.celeritas.api.options.structure.OptionImpact;
import org.taumc.celeritas.api.options.structure.OptionImpl;
import org.taumc.celeritas.api.options.structure.OptionPage;
import org.taumc.celeritas.api.options.structure.OptionStorage;
import org.taumc.celeritas.api.options.structure.StandardOptions;
import org.taumc.celeritas.impl.Celeritas;

import java.util.ArrayList;
import java.util.List;

public class SodiumGameOptionPages {
    private static final SodiumGameOptions sodiumOpts = Celeritas.options();
    private static final MinecraftOptionsStorage vanillaOpts = new MinecraftOptionsStorage();

    public static OptionPage general() {
        List<OptionGroup> groups = new ArrayList<>();

        groups.add(OptionGroup.createBuilder()
                .setId(StandardOptions.Group.RENDERING)
                .add(OptionImpl.createBuilder(int.class, vanillaOpts)
                        .setId(StandardOptions.Option.RENDER_DISTANCE.cast())
                        .setName(TextComponent.literal(I18n.translate("options.renderDistance")))
                        .setTooltip(TextComponent.translatable("sodium.options.view_distance.tooltip"))
                        .setControl(option -> new SliderControl(option, 2, 32, 1, (v) -> TextComponent.literal(v + " chunks")))
                        .setBinding((options, value) -> options.viewDistance = value, options -> options.viewDistance)
                        .setImpact(OptionImpact.HIGH)
                        .setFlags(OptionFlag.REQUIRES_RENDERER_RELOAD)
                        .build())
                .add(OptionImpl.createBuilder(int.class, vanillaOpts)
                        .setId(StandardOptions.Option.BRIGHTNESS.cast())
                        .setName(TextComponent.translatable("options.gamma"))
                        .setTooltip(TextComponent.translatable("sodium.options.brightness.tooltip"))
                        .setControl(opt -> new SliderControl(opt, 0, 100, 1, ControlValueFormatter.brightness()))
                        .setBinding((opts, value) -> opts.gamma = (float) (value * 0.01D), opts -> (int) (opts.gamma / 0.01D))
                        .build())
                .build());

        groups.add(OptionGroup.createBuilder()
                .setId(StandardOptions.Group.WINDOW)
                .add(OptionImpl.createBuilder(int.class, vanillaOpts)
                        .setId(StandardOptions.Option.GUI_SCALE.cast())
                        .setName(TextComponent.translatable("options.guiScale"))
                        .setTooltip(TextComponent.translatable("sodium.options.gui_scale.tooltip"))
                        .setControl(option -> new SliderControl(option, 0, 3, 1, ControlValueFormatter.guiScale()))
                        .setBinding((opts, value) -> opts.guiScale = value, opts -> opts.guiScale)
                        .build())
                .add(OptionImpl.createBuilder(boolean.class, vanillaOpts)
                        .setId(StandardOptions.Option.FULLSCREEN.cast())
                        .setName(TextComponent.translatable("options.fullscreen"))
                        .setTooltip(TextComponent.translatable("sodium.options.fullscreen.tooltip"))
                        .setControl(TickBoxControl::new)
                        .setBinding((opts, value) -> {
                            Minecraft client = Minecraft.getInstance();
                            if (opts.fullscreen != value) {
                                client.toggleFullscreen();
                            }
                            opts.fullscreen = value;
                        }, opts -> opts.fullscreen)
                        .build())
                .add(OptionImpl.createBuilder(boolean.class, vanillaOpts)
                        .setId(StandardOptions.Option.VSYNC.cast())
                        .setName(TextComponent.translatable("options.vsync"))
                        .setTooltip(TextComponent.translatable("sodium.options.v_sync.tooltip"))
                        .setControl(TickBoxControl::new)
                        .setBinding((opts, value) -> {
                            opts.vsync = value;
                            Display.setVSyncEnabled(value);
                        }, opts -> opts.vsync)
                        .setImpact(OptionImpact.VARIES)
                        .build())
                .add(OptionImpl.createBuilder(int.class, vanillaOpts)
                        .setId(StandardOptions.Option.MAX_FRAMERATE.cast())
                        .setName(TextComponent.translatable("options.framerateLimit"))
                        .setTooltip(TextComponent.translatable("sodium.options.fps_limit.tooltip"))
                        .setControl(option -> new SliderControl(option, 10, 260, 10, (v) -> v == 260 ? TextComponent.translatable("options.framerateLimit.max") : TextComponent.literal(v + " fps")))
                        .setBinding((opts, value) -> opts.fpsLimit = value, opts -> opts.fpsLimit)
                        .build())
                .build());

        groups.add(OptionGroup.createBuilder()
                .setId(StandardOptions.Group.INDICATORS)
                .add(OptionImpl.createBuilder(boolean.class, vanillaOpts)
                        .setId(StandardOptions.Option.VIEW_BOBBING.cast())
                        .setName(TextComponent.translatable("options.viewBobbing"))
                        .setTooltip(TextComponent.translatable("sodium.options.view_bobbing.tooltip"))
                        .setControl(TickBoxControl::new)
                        .setBinding((opts, value) -> opts.viewBobbing = value, opts -> opts.viewBobbing)
                        .build())
                .build());

        return new OptionPage(StandardOptions.Pages.GENERAL, TextComponent.translatable("stat.generalButton"), List.copyOf(groups));
    }

    public static OptionPage quality() {
        List<OptionGroup> groups = new ArrayList<>();

        groups.add(OptionGroup.createBuilder()
                .setId(StandardOptions.Group.GRAPHICS)
                .add(OptionImpl.createBuilder(boolean.class, vanillaOpts)
                        .setId(StandardOptions.Option.GRAPHICS_MODE.cast())
                        .setName(TextComponent.translatable("options.graphics"))
                        .setTooltip(TextComponent.translatable("sodium.options.graphics_quality.tooltip"))
                        .setControl(TickBoxControl::new)
                        .setBinding((opts, value) -> opts.fancyGraphics = value, opts -> opts.fancyGraphics)
                        .setImpact(OptionImpact.HIGH)
                        .setFlags(OptionFlag.REQUIRES_RENDERER_RELOAD)
                        .build())
                .build());

        groups.add(OptionGroup.createBuilder()
                .setId(StandardOptions.Group.DETAILS)
                .add(OptionImpl.createBuilder(int.class, vanillaOpts)
                        .setId(StandardOptions.Option.CLOUDS.cast())
                        .setName(TextComponent.translatable("options.renderClouds"))
                        .setTooltip(TextComponent.translatable("sodium.options.clouds_quality.tooltip"))
                        .setControl(option -> new CyclingControl<>(option, new Integer[]{0, 1, 2}, new TextComponent[]{
                                TextComponent.translatable("options.off"),
                                TextComponent.translatable("options.clouds.fast"),
                                TextComponent.translatable("options.clouds.fancy")}))
                        .setBinding((opts, value) -> {
                            opts.renderClouds = value != 0;
                            opts.cloudRenderMode = value;
                        }, opts -> opts.cloudRenderMode)
                        .setImpact(OptionImpact.LOW)
                        .setFlags(OptionFlag.REQUIRES_RENDERER_RELOAD)
                        .build())
                .add(OptionImpl.createBuilder(SodiumGameOptions.GraphicsQuality.class, sodiumOpts)
                        .setId(StandardOptions.Option.WEATHER.cast())
                        .setName(TextComponent.translatable("soundCategory.weather"))
                        .setTooltip(TextComponent.translatable("sodium.options.weather_quality.tooltip"))
                        .setControl(option -> new CyclingControl<>(option, SodiumGameOptions.GraphicsQuality.class))
                        .setBinding((opts, value) -> opts.quality.weatherQuality = value, opts -> opts.quality.weatherQuality)
                        .setImpact(OptionImpact.MEDIUM)
                        .build())
                .add(OptionImpl.createBuilder(SodiumGameOptions.GraphicsQuality.class, sodiumOpts)
                        .setId(StandardOptions.Option.LEAVES.cast())
                        .setName(TextComponent.translatable("sodium.options.leaves_quality.name"))
                        .setTooltip(TextComponent.translatable("sodium.options.leaves_quality.tooltip"))
                        .setControl(option -> new CyclingControl<>(option, SodiumGameOptions.GraphicsQuality.class))
                        .setBinding((opts, value) -> opts.quality.leavesQuality = value, opts -> opts.quality.leavesQuality)
                        .setImpact(OptionImpact.MEDIUM)
                        .setFlags(OptionFlag.REQUIRES_RENDERER_RELOAD)
                        .build())
                .add(OptionImpl.createBuilder(int.class, vanillaOpts)
                        .setId(StandardOptions.Option.PARTICLES.cast())
                        .setName(TextComponent.translatable("options.particles"))
                        .setTooltip(TextComponent.translatable("sodium.options.particle_quality.tooltip"))
                        .setControl(option -> new CyclingControl<>(option, new Integer[]{0, 1, 2}, new TextComponent[]{
                                TextComponent.translatable("options.particles.all"),
                                TextComponent.translatable("options.particles.decreased"),
                                TextComponent.translatable("options.particles.minimal")}))
                        .setBinding((opts, value) -> opts.particles = value, opts -> opts.particles)
                        .setImpact(OptionImpact.MEDIUM)
                        .build())
                .add(OptionImpl.createBuilder(int.class, vanillaOpts)
                        .setId(StandardOptions.Option.SMOOTH_LIGHT.cast())
                        .setName(TextComponent.translatable("options.ao"))
                        .setTooltip(TextComponent.translatable("sodium.options.smooth_lighting.tooltip"))
                        .setControl(option -> new CyclingControl<>(option, new Integer[]{0, 1, 2}, new TextComponent[]{
                                TextComponent.translatable("options.ao.off"),
                                TextComponent.translatable("options.ao.min"),
                                TextComponent.translatable("options.ao.max")}))
                        .setBinding((opts, value) -> opts.ambientOcclusion = value, opts -> opts.ambientOcclusion)
                        .setImpact(OptionImpact.LOW)
                        .setFlags(OptionFlag.REQUIRES_RENDERER_RELOAD)
                        .build())
                .add(OptionImpl.createBuilder(int.class, sodiumOpts)
                        .setId(StandardOptions.Option.BIOME_BLEND.cast())
                        .setName(TextComponent.translatable("sodium.options.biomeBlendRadius"))
                        .setTooltip(TextComponent.translatable("sodium.options.biome_blend.tooltip"))
                        .setControl(option -> new SliderControl(option, 0, 14, 1, ControlValueFormatter.biomeBlend()))
                        .setBinding((opts, value) -> opts.quality.legacyBiomeBlendRadius = value, opts -> opts.quality.legacyBiomeBlendRadius)
                        .setImpact(OptionImpact.LOW)
                        .setFlags(OptionFlag.REQUIRES_RENDERER_RELOAD)
                        .build())
                .add(OptionImpl.createBuilder(int.class, sodiumOpts)
                        .setId(StandardOptions.Option.CHUNK_FADE_IN_DURATION.cast())
                        .setName(TextComponent.translatable("celeritas.options.chunk_fade_in_duration.name"))
                        .setTooltip(TextComponent.translatable("celeritas.options.chunk_fade_in_duration.tooltip"))
                        .setControl(o -> new SliderControl(o, 0, 2000, 100, ControlValueFormatter.translateVariable("celeritas.options.chunk_fade_in_duration.value")))
                        .setImpact(OptionImpact.LOW)
                        .setBinding((opts, value) -> opts.quality.chunkFadeInDuration = value, opts -> opts.quality.chunkFadeInDuration)
                        .setFlags(OptionFlag.REQUIRES_RENDERER_RELOAD)
                        .build())
                .add(OptionImpl.createBuilder(boolean.class, sodiumOpts)
                        .setId(StandardOptions.Option.VIGNETTE.cast())
                        .setName(TextComponent.translatable("sodium.options.vignette.name"))
                        .setTooltip(TextComponent.translatable("sodium.options.vignette.tooltip"))
                        .setControl(TickBoxControl::new)
                        .setBinding((opts, value) -> opts.quality.enableVignette = value, opts -> opts.quality.enableVignette)
                        .setImpact(OptionImpact.LOW)
                        .build())
                .build());

        groups.add(OptionGroup.createBuilder()
                .setId(StandardOptions.Group.MIPMAPS)
                .add(OptionImpl.createBuilder(int.class, vanillaOpts)
                        .setId(StandardOptions.Option.MIPMAP_LEVEL.cast())
                        .setName(TextComponent.translatable("options.mipmapLevels"))
                        .setTooltip(TextComponent.translatable("sodium.options.mipmap_levels.tooltip"))
                        .setControl(option -> new SliderControl(option, 0, 4, 1, ControlValueFormatter.multiplier()))
                        .setBinding((opts, value) -> opts.mipmapLevels = value, opts -> opts.mipmapLevels)
                        .setImpact(OptionImpact.MEDIUM)
                        .setFlags(OptionFlag.REQUIRES_ASSET_RELOAD)
                        .build())
                .build());

        groups.add(OptionGroup.createBuilder()
                .setId(StandardOptions.Group.SORTING)
                .add(OptionImpl.createBuilder(boolean.class, sodiumOpts)
                        .setId(StandardOptions.Option.TRANSLUCENT_FACE_SORTING.cast())
                        .setName(TextComponent.translatable("sodium.options.translucent_face_sorting.name"))
                        .setTooltip(TextComponent.translatable("sodium.options.translucent_face_sorting.tooltip"))
                        .setControl(TickBoxControl::new)
                        .setImpact(OptionImpact.VARIES)
                        .setBinding((opts, value) -> opts.performance.useTranslucentFaceSorting = value, opts -> opts.performance.useTranslucentFaceSorting)
                        .setFlags(OptionFlag.REQUIRES_RENDERER_RELOAD)
                        .build())
                .build());

        return new OptionPage(StandardOptions.Pages.QUALITY, TextComponent.translatable("sodium.options.pages.quality"), List.copyOf(groups));
    }

    public static OptionPage advanced() {
        List<OptionGroup> groups = new ArrayList<>();

        groups.add(OptionGroup.createBuilder()
                .setId(StandardOptions.Group.CPU_SAVING)
                .add(OptionImpl.createBuilder(int.class, sodiumOpts)
                        .setId(StandardOptions.Option.CPU_FRAMES_AHEAD.cast())
                        .setName(TextComponent.translatable("sodium.options.cpu_render_ahead_limit.name"))
                        .setTooltip(TextComponent.translatable("sodium.options.cpu_render_ahead_limit.tooltip"))
                        .setControl(opt -> new SliderControl(opt, 0, 9, 1, ControlValueFormatter.translateVariable("sodium.options.cpu_render_ahead_limit.value")))
                        .setBinding((opts, value) -> opts.advanced.cpuRenderAheadLimit = value, opts -> opts.advanced.cpuRenderAheadLimit)
                        .build())
                .build());

        return new OptionPage(StandardOptions.Pages.ADVANCED, TextComponent.translatable("sodium.options.pages.advanced"), List.copyOf(groups));
    }

    public static OptionStorage<GameOptions> getVanillaOpts() {
        return vanillaOpts;
    }

    public static OptionStorage<SodiumGameOptions> getSodiumOpts() {
        return sodiumOpts;
    }
}
//?}
