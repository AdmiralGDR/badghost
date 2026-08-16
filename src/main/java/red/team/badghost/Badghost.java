// Copyright (C) 2026 RedTeam. Licensed under GNU AGPLv3.
package red.team.badghost;

import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.common.NeoForge;
import red.team.badghost.automation.AutomationEngine;
import red.team.badghost.config.BadghostConfig;
import red.team.badghost.visuals.EspRenderer;
import red.team.badghost.visuals.KeyBindings;
import red.team.badghost.visuals.MinerHUD;
import red.team.badghost.visuals.VisualService;

@Mod(value = Badghost.MODID, dist = Dist.CLIENT)
public final class Badghost {
    public static final String MODID = "badghost";

    public Badghost(IEventBus modEventBus, ModContainer modContainer) {
        modContainer.registerConfig(ModConfig.Type.CLIENT, BadghostConfig.SPEC);

        modEventBus.addListener(KeyBindings::onRegisterKeyMappings);
        modEventBus.addListener(Badghost::registerGuiLayers);

        NeoForge.EVENT_BUS.register(VisualService.class);
        NeoForge.EVENT_BUS.register(AutomationEngine.class);
        NeoForge.EVENT_BUS.register(EspRenderer.class);

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
