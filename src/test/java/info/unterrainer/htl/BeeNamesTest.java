package info.unterrainer.htl;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;

import org.junit.jupiter.api.Test;

class BeeNamesTest {

    private final Random random = new Random(42);

    @Test
    void listHasAtLeastFortyDistinctNames() {
        assertThat(new HashSet<>(BeeNames.NAMES)).hasSizeGreaterThanOrEqualTo(40).hasSameSizeAs(BeeNames.NAMES);
        assertThat(BeeNames.NAMES).allSatisfy(n -> assertThat(n).isNotBlank());
    }

    @Test
    void picksAnUnusedListName() {
        Set<String> used = new HashSet<>(BeeNames.NAMES);
        used.remove("Flip");

        assertThat(BeeNames.pick(used, random)).isEqualTo("Flip");
    }

    @Test
    void picksNamesUntilTheListIsUsedUp() {
        Set<String> used = new HashSet<>();
        for (int i = 0; i < BeeNames.NAMES.size(); i++)
            assertThat(used.add(BeeNames.pick(used, random))).isTrue();

        assertThat(used).containsExactlyInAnyOrderElementsOf(BeeNames.NAMES);
    }

    @Test
    void appendsSuffixWhenAllNamesAreUsed() {
        Set<String> used = new HashSet<>(BeeNames.NAMES);

        String name = BeeNames.pick(used, random);

        assertThat(name).matches(".+ 2");
        assertThat(BeeNames.NAMES).contains(name.substring(0, name.length() - 2));
    }

    @Test
    void suffixSkipsTakenNumbers() {
        Set<String> used = new HashSet<>(BeeNames.NAMES);
        for (String n : BeeNames.NAMES) {
            used.add(n + " 2");
            used.add(n + " 3");
        }

        String name = BeeNames.pick(used, random);

        assertThat(name).matches(".+ 4");
        assertThat(used).doesNotContain(name);
    }
}
