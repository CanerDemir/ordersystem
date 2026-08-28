package com.example.ordersystem.resolvers;

import com.example.ordersystem.annotations.AuthenticatedUser;
import com.example.ordersystem.auth.CustomUserDetails;
import com.example.ordersystem.exception.UnauthorizedException;
import org.springframework.core.MethodParameter;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

@Component
public class CurrentUserArgumentResolver implements HandlerMethodArgumentResolver {

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(AuthenticatedUser.class)
                && parameter.getParameterType().equals(com.example.ordersystem.auth.CurrentUser.class);
    }

    @Override
    public Object resolveArgument(
            MethodParameter parameter,
            ModelAndViewContainer mavContainer,
            NativeWebRequest webRequest,
            WebDataBinderFactory binderFactory) {

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        if (auth == null || !auth.isAuthenticated() || !(auth.getPrincipal() instanceof CustomUserDetails)) {
            throw new UnauthorizedException("Kullanıcı oturumu bulunamadı.");
        }

        // CustomUserDetails nesnenizden kendi domain/context DTO'nuza dönüşüm yapabilirsiniz
        CustomUserDetails principal = (CustomUserDetails) auth.getPrincipal();
        return new com.example.ordersystem.auth.CurrentUser(principal.getCustomerId());
    }
}