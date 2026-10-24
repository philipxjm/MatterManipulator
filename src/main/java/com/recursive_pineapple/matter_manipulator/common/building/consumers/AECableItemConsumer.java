package com.recursive_pineapple.matter_manipulator.common.building.consumers;

import static com.recursive_pineapple.matter_manipulator.common.building.IPseudoInventory.*;

import java.util.Collections;
import java.util.List;

import net.minecraft.item.ItemStack;

import appeng.api.AEApi;
import appeng.api.util.AEColor;
import appeng.api.util.AEColoredItemDefinition;

import com.recursive_pineapple.matter_manipulator.common.building.IPseudoInventory;
import com.recursive_pineapple.matter_manipulator.common.utils.BigItemStack;

/**
 * A consumer that can consume AE cables with any color
 */
public class AECableItemConsumer implements IItemConsumer {

    public static final AEColoredItemDefinition AE_GLASS_CABLE = AEApi.instance().definitions().parts().cableGlass();
    public static final AEColoredItemDefinition AE_COVERED_CABLE = AEApi.instance().definitions().parts().cableCovered();
    public static final AEColoredItemDefinition AE_SMART_CABLE = AEApi.instance().definitions().parts().cableSmart();
    public static final AEColoredItemDefinition AE_DENSE_SMART_CABLE = AEApi.instance().definitions().parts().cableDense();
    public static final AEColoredItemDefinition AE_DENSE_COVERED_CABLE = AEApi.instance().definitions().parts().cableDenseCovered();

    @Override
    public void consume(IPseudoInventory inv, BigItemStack in, BigItemStack out, int flags) {
        if (in.getItem() != AE_GLASS_CABLE.item(AEColor.Transparent)) return;

        // Every AE part (anchors, terminals, P2P tunnels...) shares the cable item, so only step in for a real cable;
        // anything else would be paid for with cables.
        AEColoredItemDefinition definition = getCableDefinition(in.getItemDamage());
        if (definition == null) return;

        boolean isPlanning = (flags & CONSUME_SIMULATED) == 1 && (flags & CONSUME_IGNORE_CREATIVE) == 1;

        // Start searching from fluix cables
        for (int color = AEColor.Transparent.ordinal(); color >= 0; color--) {
            // We're in planning, since colored cables are crafted using fluix, we skip them
            if (isPlanning && color == AEColor.Transparent.ordinal()) continue;

            ItemStack cableStack = definition.stack(AEColor.fromOrdinal(color), 1);
            BigItemStack cableBigStack = BigItemStack.create(cableStack).setStackSize(in.getStackSize());

            List<BigItemStack> extractedStacks = inv.tryConsumeItems(Collections.singletonList(cableBigStack), CONSUME_PARTIAL | flags).right();
            if (!extractedStacks.isEmpty()) {
                BigItemStack extracted = extractedStacks.get(0);

                in.decStackSize(extracted.getStackSize());
                out.incStackSize(extracted.getStackSize());
            }

            if (in.getStackSize() <= 0) return;
        }
    }

    private static final AEColoredItemDefinition[] CABLES = { AE_GLASS_CABLE, AE_COVERED_CABLE, AE_SMART_CABLE,
        AE_DENSE_SMART_CABLE, AE_DENSE_COVERED_CABLE };

    /** The cable type with this damage value in any colour, or null if it is another part. */
    private static AEColoredItemDefinition getCableDefinition(int damage) {
        for (AEColoredItemDefinition cable : CABLES) {
            for (AEColor color : AEColor.values()) {
                ItemStack stack = cable.stack(color, 1);
                if (stack != null && stack.getItemDamage() == damage) return cable;
            }
        }
        return null;
    }
}
