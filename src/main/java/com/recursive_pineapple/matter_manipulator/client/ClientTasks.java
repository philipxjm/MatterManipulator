package com.recursive_pineapple.matter_manipulator.client;

import java.util.ArrayDeque;
import java.util.Queue;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Runs client work on the next client tick, e.g. opening a screen from a radial menu option, which closes the menu
 * right after its click handler returns.
 */
@SideOnly(Side.CLIENT)
public final class ClientTasks {

    private static final Queue<Runnable> TASKS = new ArrayDeque<>();

    private ClientTasks() {}

    public static void init() {
        FMLCommonHandler.instance()
            .bus()
            .register(new ClientTasks());
    }

    public static void nextTick(Runnable task) {
        TASKS.add(task);
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Runnable task;
        while ((task = TASKS.poll()) != null) {
            task.run();
        }
    }
}
