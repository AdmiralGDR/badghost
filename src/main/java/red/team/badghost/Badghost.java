// Copyright (C) 2026 RedTeam. Licensed under GNU AGPLv3.
package red.team.badghost;

import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.common.NeoForge;
import red.team.badghost.automation.AutomationEngine;
import red.team.badghost.command.BadghostCommands;
import red.team.badghost.config.BadghostConfig;
import red.team.badghost.core.FeatureAudit;
import red.team.badghost.visuals.CameraService;
import red.team.badghost.visuals.EspRenderer;
import red.team.badghost.visuals.KeyBindings;
import red.team.badghost.visuals.MinerHUD;
import red.team.badghost.visuals.VisualService;

@Mod(value = Badghost.MODID, dist = Dist.CLIENT)
public final class Badghost {
    public static final String MODID = "badghost";

    public Badghost(IEventBus modEventBus, ModContainer modContainer) {
        modContainer.registerConfig(ModConfig.Type.CLIENT, BadghostConfig.SPEC);
        // Every option becomes an in-game widget with its range and comment, generated from the
        // spec itself — so the settings can no longer drift from what the config actually holds.
        modContainer.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);

        modEventBus.addListener(KeyBindings::onRegisterKeyMappings);
        modEventBus.addListener(Badghost::registerGuiLayers);

        // Client commands: answered locally and never forwarded, see BadghostCommands.
        NeoForge.EVENT_BUS.addListener(BadghostCommands::onRegisterClientCommands);
        // Warns on world join when a setting is switched on but its hook did not apply, so a
        // silently dead feature cannot be mistaken for a working one.
        NeoForge.EVENT_BUS.register(FeatureAudit.class);
        NeoForge.EVENT_BUS.register(VisualService.class);
        NeoForge.EVENT_BUS.register(AutomationEngine.class);
        NeoForge.EVENT_BUS.register(EspRenderer.class);
        NeoForge.EVENT_BUS.register(CameraService.class);

        // Development harness; the class is stripped from the released jar, and the reference
        // below is only resolved when the property is set, so the jar stays loadable without it.
        if (Boolean.getBoolean(red.team.badghost.dev.SelfTest.PROPERTY)) {
            NeoForge.EVENT_BUS.register(red.team.badghost.dev.SelfTest.class);
        }
    }

    private static void registerGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAbove(
                VanillaGuiLayers.HOTBAR,
                ResourceLocation.fromNamespaceAndPath(MODID, "miner_hud"),
                new MinerHUD());
    }
}
