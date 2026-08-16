package com.webappfoundation.security;

import com.webappfoundation.model.User;
import org.springframework.core.MethodParameter;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * Resolves {@link CurrentUser}-annotated {@code Integer} parameters to the authenticated user's id
 * ({@code User.id}), or {@code null} for a guest / anonymous request. The id comes for free from the
 * principal — {@code JwtAuthFilter} already loads the full {@link User} into the security context — so
 * there is no extra query. Registered in {@link com.webappfoundation.config.WebConfig}. Domain
 * controllers use {@code @CurrentUser Integer userId} instead of touching the principal or inventing
 * an id; every user-owned record links by {@code Integer userId}. (Email/phone are lookup keys on
 * {@link com.webappfoundation.repository.UserRepository}, never stored as the domain reference.)
 */
@Component
public class CurrentUserArgumentResolver implements HandlerMethodArgumentResolver {

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(CurrentUser.class)
                && Integer.class.isAssignableFrom(parameter.getParameterType());
    }

    @Override
    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                  NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
            return null; // guest / unauthenticated
        }
        Object principal = auth.getPrincipal();
        return principal instanceof User user ? user.getId() : null;
    }
}
