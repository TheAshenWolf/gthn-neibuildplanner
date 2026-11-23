package neibuildplanner.client.planner;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

import com.gtnewhorizon.structurelib.structure.IStructureElement;
import com.gtnewhorizon.structurelib.structure.StructureUtility;

import neibuildplanner.NEIBuildPlanner;

/**
 * Looks inside StructureLib's {@code withChannel} wrapper. The wrapper is an anonymous class, so its class and the
 * field holding the wrapped element are found once from a probe wrapper instead of by name: a StructureLib update that
 * renames them keeps working, and one that changes the shape only turns this off (logged once).
 */
public final class ChannelWrappers {

    private static boolean initialised;
    private static Class<?> wrapperClass;
    private static Field backingField;

    private ChannelWrappers() {}

    /** @return the element inside a channel wrapper, or null when {@code element} isn't one. */
    @SuppressWarnings("unchecked")
    public static IStructureElement<Object> unwrap(IStructureElement<?> element) {
        if (element == null) {
            return null;
        }

        if (!initialised) {
            initialise();
        }

        if (backingField == null || element.getClass() != wrapperClass) {
            return null;
        }

        try {
            return (IStructureElement<Object>) backingField.get(element);
        } catch (Exception exception) {
            NEIBuildPlanner.LOG.error("Could not read the element inside a structure channel wrapper", exception);
            backingField = null;
            return null;
        }
    }

    private static void initialise() {
        initialised = true;

        try {
            final IStructureElement<Object> probe = StructureUtility.error();
            final IStructureElement<Object> wrapper = StructureUtility.withChannel("neibuildplanner_probe", probe);
            final Field[] fields = wrapper.getClass()
                .getDeclaredFields();

            for (int i = 0, count = fields.length; i < count; i++) {
                final Field field = fields[i];

                if (Modifier.isStatic(field.getModifiers())
                    || !IStructureElement.class.isAssignableFrom(field.getType())) {
                    continue;
                }

                field.setAccessible(true);

                if (field.get(wrapper) == probe) {
                    wrapperClass = wrapper.getClass();
                    backingField = field;
                    return;
                }
            }

            NEIBuildPlanner.LOG.error(
                "StructureLib's channel wrapper changed; hatch dots inside channel-wrapped elements stay hidden in previews");
        } catch (Throwable throwable) {
            NEIBuildPlanner.LOG.error("Could not inspect StructureLib's channel wrapper", throwable);
        }
    }
}
