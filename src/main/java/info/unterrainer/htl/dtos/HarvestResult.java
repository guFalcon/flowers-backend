package info.unterrainer.htl.dtos;

/**
 * Answer to a harvest request: the flower under the bee (or {@code null}), the honey gained by this
 * request and the bee's honey afterwards.
 */
public record HarvestResult(String flowerId, long gained, long total) {
}
