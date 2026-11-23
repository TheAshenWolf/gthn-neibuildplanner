package neibuildplanner.client.planner;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.item.ItemStack;

import cpw.mods.fml.common.Loader;
import gregtech.api.GregTechAPI;
import gregtech.api.enums.HatchElement;
import gregtech.api.interfaces.IHatchElement;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.metatileentity.implementations.MTEHatch;
import gregtech.common.blocks.ItemMachines;
import neibuildplanner.NEIBuildPlanner;
import tectech.thing.metaTileEntity.multi.base.TTMultiblockBase;

/**
 * Groups GregTech hatches into families of interchangeable kinds, for the planner's hatch picker.
 * <p>
 * A family is one or more of GregTech's hatch elements (the "accepts input hatches" parts of structure definitions).
 * Energy and dynamo span several elements, because a multiblock that takes plain energy hatches often takes multi-amp
 * or laser hatches through a second element. Families are per hatch kind, not per multiblock: a candidate can still
 * be one that a particular machine rejects.
 */
public final class HatchCatalog {

    private static final boolean GREGTECH_LOADED = Loader.isModLoaded("gregtech");

    private HatchCatalog() {}

    /** @return the family key of a GregTech hatch item, or null if it isn't one the picker knows. */
    public static String familyOf(ItemStack stack) {
        if (!GREGTECH_LOADED || stack == null) {
            return null;
        }

        try {
            return Lookup.familyOf(stack);
        } catch (Throwable error) {
            NEIBuildPlanner.LOG.error("Could not classify hatch " + stack, error);
            return null;
        }
    }

    /** @return every hatch item of the family, lowest tier first; empty if the family is unknown. */
    public static List<ItemStack> candidates(String family) {
        if (!GREGTECH_LOADED || family == null) {
            return Collections.emptyList();
        }

        try {
            return Lookup.candidates(family);
        } catch (Throwable error) {
            NEIBuildPlanner.LOG.error("Could not list hatches for " + family, error);
            return Collections.emptyList();
        }
    }

    /** Everything that touches GregTech classes, so nothing loads them when GregTech is missing. */
    private static final class Lookup {

        private static final class Family {

            private final String key;
            private final List<IHatchElement<?>> elements = new ArrayList<IHatchElement<?>>();

            private Family(String key) {
                this.key = key;
            }
        }

        private static final class Candidate {

            private final int tier;
            private final int id;
            private final ItemStack stack;

            private Candidate(int tier, int id, ItemStack stack) {
                this.tier = tier;
                this.id = id;
                this.stack = stack;
            }
        }

        private static List<Family> families;
        private static final Map<String, List<ItemStack>> CANDIDATES = new HashMap<String, List<ItemStack>>();

        private static String familyOf(ItemStack stack) {
            final IMetaTileEntity metaTileEntity = metaTileEntityOf(stack);

            if (!(metaTileEntity instanceof MTEHatch)) {
                return null;
            }

            final Family family = familyOf(metaTileEntity);
            return family == null ? null : family.key;
        }

        private static List<ItemStack> candidates(String key) {
            final List<ItemStack> cached = CANDIDATES.get(key);

            if (cached != null) {
                return cached;
            }

            Family family = null;
            final List<Family> all = families();

            for (int i = 0, count = all.size(); i < count; i++) {
                if (all.get(i).key.equals(key)) {
                    family = all.get(i);
                    break;
                }
            }

            final List<Candidate> found = new ArrayList<Candidate>();

            if (family != null) {
                final IMetaTileEntity[] metaTileEntities = GregTechAPI.METATILEENTITIES;

                for (int id = 0, count = metaTileEntities.length; id < count; id++) {
                    final IMetaTileEntity metaTileEntity = metaTileEntities[id];

                    // Each hatch belongs to its first matching family only, so the lists never overlap.
                    if (metaTileEntity instanceof MTEHatch && familyOf(metaTileEntity) == family) {
                        final ItemStack stack = metaTileEntity.getStackForm(1);

                        if (stack != null) {
                            found.add(new Candidate(((MTEHatch) metaTileEntity).mTier, id, stack));
                        }
                    }
                }
            }

            found.sort(
                Comparator.comparingInt((Candidate candidate) -> candidate.tier)
                    .thenComparingInt(candidate -> candidate.id));
            final List<ItemStack> stacks = new ArrayList<ItemStack>();

            for (int i = 0, count = found.size(); i < count; i++) {
                stacks.add(found.get(i).stack);
            }

            CANDIDATES.put(key, stacks);
            return stacks;
        }

        private static IMetaTileEntity metaTileEntityOf(ItemStack stack) {
            if (!(stack.getItem() instanceof ItemMachines)) {
                return null;
            }

            final int id = stack.getItemDamage();
            final IMetaTileEntity[] metaTileEntities = GregTechAPI.METATILEENTITIES;
            return id >= 0 && id < metaTileEntities.length ? metaTileEntities[id] : null;
        }

        private static Family familyOf(IMetaTileEntity metaTileEntity) {
            final List<Family> all = families();

            for (int i = 0, familyCount = all.size(); i < familyCount; i++) {
                final Family family = all.get(i);

                for (int j = 0, elementCount = family.elements.size(); j < elementCount; j++) {
                    if (matches(family.elements.get(j), metaTileEntity)) {
                        return family;
                    }
                }
            }

            return null;
        }

        private static boolean matches(IHatchElement<?> element, IMetaTileEntity metaTileEntity) {
            try {
                return element.matchesHatch(metaTileEntity);
            } catch (RuntimeException exception) {
                return false;
            }
        }

        private static List<Family> families() {
            if (families != null) {
                return families;
            }

            final List<Family> built = new ArrayList<Family>();
            final Family energy = new Family("energy");
            final Family dynamo = new Family("dynamo");
            built.add(energy);
            built.add(dynamo);

            for (HatchElement element : HatchElement.values()) {
                switch (element.name()) {
                    case "Energy":
                    case "MultiAmpEnergy":
                    case "ExoticEnergy":
                        energy.elements.add(element);
                        break;
                    case "Dynamo":
                    case "ExoticDynamo":
                    case "LaserSource":
                        dynamo.elements.add(element);
                        break;
                    default:
                        built.add(single(element));
                        break;
                }
            }

            try {
                TecTechElements.addTo(built, energy, dynamo);
            } catch (Throwable error) {
                NEIBuildPlanner.LOG.error("TecTech hatch elements unavailable; laser hatches won't be offered", error);
            }

            families = built;
            return families;
        }

        private static Family single(IHatchElement<?> element) {
            final Family family = new Family(element.name());
            family.elements.add(element);
            return family;
        }

        /** TecTech's own elements: multi-amp and laser hatches, data and uncertainty hatches. */
        private static final class TecTechElements {

            private static void addTo(List<Family> built, Family energy, Family dynamo) {
                for (TTMultiblockBase.HatchElement element : TTMultiblockBase.HatchElement.values()) {
                    if (element == TTMultiblockBase.HatchElement.EnergyMulti) {
                        energy.elements.add(element);
                    } else if (element == TTMultiblockBase.HatchElement.DynamoMulti) {
                        dynamo.elements.add(element);
                    } else {
                        built.add(single(element));
                    }
                }
            }
        }
    }
}
