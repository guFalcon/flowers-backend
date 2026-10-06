package info.unterrainer.htl;

import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * Fixed list of child-friendly German bee names (in the spirit of "Die Biene Maja") and the picker
 * that gives a new bee a name no current bee has.
 */
public final class BeeNames {

    public static final List<String> NAMES = List.of(
            "Maja", "Willi", "Flip", "Kassandra", "Puck", "Kurt", "Max", "Thekla",
            "Summsi", "Brummel", "Honigtau", "Blümchen", "Pollenpaul", "Wabenwilma", "Nektarina", "Flitzi",
            "Sonnenschein", "Butterblume", "Klee", "Löwenzahn", "Gänseblümchen", "Hummelchen", "Brummbär",
            "Sausewind", "Wirbelwind", "Pünktchen", "Lotte", "Paula", "Emil", "Fridolin", "Krümel",
            "Bommel", "Flocke", "Mimi", "Lilli", "Rosalie", "Kunigunde", "Hugo", "Otto", "Tilda",
            "Frieda", "Anton", "Kasimir", "Wuschel", "Zitronella", "Honigbär", "Summselinchen", "Pippa");

    private BeeNames() {
    }

    /**
     * A random list name not in {@code usedNames}; if every list name is used, a random list name
     * followed by a space and the smallest number from 2 that makes it unused (e.g. {@code Maja 2}).
     */
    public static String pick(Set<String> usedNames, Random random) {
        List<String> free = NAMES.stream().filter(n -> !usedNames.contains(n)).toList();
        if (!free.isEmpty())
            return free.get(random.nextInt(free.size()));

        String base = NAMES.get(random.nextInt(NAMES.size()));
        int suffix = 2;
        while (usedNames.contains(base + " " + suffix))
            suffix++;
        return base + " " + suffix;
    }
}
