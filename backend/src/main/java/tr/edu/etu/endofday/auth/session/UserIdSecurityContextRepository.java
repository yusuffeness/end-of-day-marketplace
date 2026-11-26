package tr.edu.etu.endofday.auth.session;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.util.Optional;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.DeferredSecurityContext;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.web.context.HttpRequestResponseHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import tr.edu.etu.endofday.auth.AuthenticatedUser;
import tr.edu.etu.endofday.user.repository.UserAccess;
import tr.edu.etu.endofday.user.repository.UserRepository;

/**
 * Keeps only the user id in the HTTP session and rebuilds the security context from the database on every request.
 *
 * <p>Spring Security's default repository stores the whole authentication, including the authorities, in the session,
 * so a deactivated user or a revoked role would stay effective until the session ends. This repository instead loads
 * the active flag and the roles with one query (see {@link UserRepository#findAccess(long)}) the first time a request
 * needs its authentication. When the user is inactive or no longer exists, the session is invalidated and the request
 * continues unauthenticated, so protected endpoints answer 401.
 */
public class UserIdSecurityContextRepository implements SecurityContextRepository {

    static final String USER_ID_ATTRIBUTE = UserIdSecurityContextRepository.class.getName() + ".USER_ID";

    private static final Logger log = LoggerFactory.getLogger(UserIdSecurityContextRepository.class);

    private final UserRepository userRepository;
    private final SecurityContextHolderStrategy contextHolderStrategy =
            SecurityContextHolder.getContextHolderStrategy();

    public UserIdSecurityContextRepository(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /** @deprecated superseded by {@link #loadDeferredContext(HttpServletRequest)}, kept for the interface contract */
    @Override
    @Deprecated
    public SecurityContext loadContext(HttpRequestResponseHolder requestResponseHolder) {
        return loadDeferredContext(requestResponseHolder.getRequest()).get();
    }

    @Override
    public DeferredSecurityContext loadDeferredContext(HttpServletRequest request) {
        return new LazySecurityContext(() -> load(request).orElse(null), contextHolderStrategy);
    }

    @Override
    public void saveContext(SecurityContext context, HttpServletRequest request, HttpServletResponse response) {
        Authentication authentication = context.getAuthentication();
        if (authentication != null
                && authentication.isAuthenticated()
                && authentication.getPrincipal() instanceof AuthenticatedUser user) {
            request.getSession(true).setAttribute(USER_ID_ATTRIBUTE, user.id());
            return;
        }
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.removeAttribute(USER_ID_ATTRIBUTE);
        }
    }

    @Override
    public boolean containsContext(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        return session != null && session.getAttribute(USER_ID_ATTRIBUTE) != null;
    }

    private Optional<SecurityContext> load(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null || !(session.getAttribute(USER_ID_ATTRIBUTE) instanceof Long userId)) {
            return Optional.empty();
        }
        Optional<UserAccess> access = userRepository.findAccess(userId);
        if (access.isEmpty() || !access.get().active()) {
            log.info("Invalidating the session of user {}: the account is inactive or no longer exists", userId);
            session.invalidate();
            return Optional.empty();
        }
        SecurityContext context = contextHolderStrategy.createEmptyContext();
        context.setAuthentication(new AuthenticatedUser(userId, access.get().roles()).toAuthentication());
        return Optional.of(context);
    }

    /** Loads the context once, on first use; an empty context counts as generated. */
    private static final class LazySecurityContext implements DeferredSecurityContext {

        private final Supplier<SecurityContext> loader;
        private final SecurityContextHolderStrategy contextHolderStrategy;
        private SecurityContext context;
        private boolean generated;

        LazySecurityContext(Supplier<SecurityContext> loader, SecurityContextHolderStrategy contextHolderStrategy) {
            this.loader = loader;
            this.contextHolderStrategy = contextHolderStrategy;
        }

        @Override
        public SecurityContext get() {
            if (context == null) {
                SecurityContext loaded = loader.get();
                generated = loaded == null;
                context = generated ? contextHolderStrategy.createEmptyContext() : loaded;
            }
            return context;
        }

        @Override
        public boolean isGenerated() {
            get();
            return generated;
        }
    }
}
