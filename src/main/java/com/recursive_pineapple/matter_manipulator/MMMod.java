package com.recursive_pineapple.matter_manipulator;

import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.Mod.EventHandler;
import cpw.mods.fml.common.SidedProxy;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLMissingMappingsEvent;
import cpw.mods.fml.common.event.FMLMissingMappingsEvent.MissingMapping;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartingEvent;
import cpw.mods.fml.common.registry.GameRegistry.Type;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

// Schematic-import fork: reports the stock version it is built on, because Forge requires the same version on both
// sides and stock Matter Manipulator accepts no other. Import traffic uses its own channel (SchematicChannel), so the
// fork interoperates with stock clients and servers. Tags.VERSION still names the actual build in the log.
@Mod(modid = MMMod.MODID, version = MMMod.PROTOCOL_VERSION, name = "Matter Manipulator", acceptedMinecraftVersions = "[1.7.10]")
public class MMMod {

    public static final String MODID = "matter-manipulator";
    /** The stock release this fork is built on and interoperates with. */
    public static final String PROTOCOL_VERSION = "0.1.55-GTNH";
    public static final Logger LOG = LogManager.getLogger(MODID);

    @SidedProxy(
        clientSide = "com.recursive_pineapple.matter_manipulator.ClientProxy",
        serverSide = "com.recursive_pineapple.matter_manipulator.CommonProxy"
    )
    public static CommonProxy proxy;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        proxy.preInit(event);
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        proxy.init(event);
    }

    @Mod.EventHandler
    public void postInit(FMLPostInitializationEvent event) {
        proxy.postInit(event);
    }

    @Mod.EventHandler
    public void serverStarting(FMLServerStartingEvent event) {
        proxy.serverStarting(event);
    }

    @EventHandler
    public void missingMapping(FMLMissingMappingsEvent event) {
        for (MissingMapping mapping : event.getAll()) {
            if (mapping.type != Type.ITEM) continue;
            if (mapping.name.equals("gregtech:gt.metaitem.04")) mapping.ignore();
            if (mapping.name.equals("gregtech:itemMatterManipulator0")) mapping.ignore();
            if (mapping.name.equals("gregtech:itemMatterManipulator1")) mapping.ignore();
            if (mapping.name.equals("gregtech:itemMatterManipulator2")) mapping.ignore();
            if (mapping.name.equals("gregtech:itemMatterManipulator3")) mapping.ignore();
        }
    }
}
