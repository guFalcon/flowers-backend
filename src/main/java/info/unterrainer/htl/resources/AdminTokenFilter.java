package info.unterrainer.htl.resources;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Optional;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.resteasy.reactive.server.ServerRequestFilter;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Response;

/**
 * Refuses every request to {@code /api/admin/*} with {@code 403} unless its {@code X-Admin-Token}
 * header equals the configured admin token ({@code FLOWERS_ADMIN_TOKEN}). Without a configured token
 * every admin request is refused.
 */
public class AdminTokenFilter {

    static final String HEADER = "X-Admin-Token";
    private static final String ADMIN_PATH_PREFIX = "/api/admin/";

    @ConfigProperty(name = "flowers.admin-token")
    Optional<String> adminToken;

    @ServerRequestFilter
    public Optional<Response> checkAdminToken(ContainerRequestContext request) {
        if (!request.getUriInfo().getPath().startsWith(ADMIN_PATH_PREFIX))
            return Optional.empty();
        if (accepts(request.getHeaderString(HEADER)))
            return Optional.empty();
        return Optional.of(Response.status(Response.Status.FORBIDDEN).build());
    }

    boolean accepts(String providedToken) {
        if (adminToken.isEmpty() || adminToken.get().isBlank() || providedToken == null)
            return false;
        // Constant-time comparison, so the token cannot be guessed from response times
        return MessageDigest.isEqual(adminToken.get().getBytes(StandardCharsets.UTF_8),
                providedToken.getBytes(StandardCharsets.UTF_8));
    }
}
