package rw.gov.ptc.common;

/** Request bodies carry enum values as text so callers get our own error codes for unknown values. */
public final class Enums {

    private Enums() {
    }

    /** The constant with this exact name, or null when the value is null or unknown. */
    public static <E extends Enum<E>> E parse(Class<E> type, String name) {
        if (name == null) {
            return null;
        }
        for (E e : type.getEnumConstants()) {
            if (e.name().equals(name)) {
                return e;
            }
        }
        return null;
    }
}
