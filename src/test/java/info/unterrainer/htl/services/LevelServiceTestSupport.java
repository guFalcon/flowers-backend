package info.unterrainer.htl.services;

/**
 * Gives tests outside this package access to the time-taking overloads of {@link LevelService}.
 */
public final class LevelServiceTestSupport {

    // Longer than any flight across the play area (5 s per unit, diagonal ≈ 1.41)
    private static final long LONG_AGO_MILLIS = 10_000;

    private LevelServiceTestSupport() {
    }

    /**
     * Sends the bee to (x, y) on a flight that started long enough ago to be over by now.
     */
    public static void placeArrived(LevelService service, String playerId, double x, double y) {
        service.setTarget(playerId, x, y, System.currentTimeMillis() - LONG_AGO_MILLIS);
    }
}
