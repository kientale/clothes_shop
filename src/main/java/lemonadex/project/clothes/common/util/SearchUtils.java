package lemonadex.project.clothes.common.util;

import java.util.Locale;

public final class SearchUtils {
    private SearchUtils() {}

    public static String contains(String value) {
        return "%" + value.strip().toLowerCase(Locale.ROOT)
                .replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
    }
}
