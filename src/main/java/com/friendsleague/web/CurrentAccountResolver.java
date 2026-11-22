package com.friendsleague.web;

import com.friendsleague.account.Account;
import com.friendsleague.account.AccountService;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

// Spring asks this class for every controller parameter marked @CurrentAccount.
// It reads the token from the header and returns its account, so controllers never deal with tokens themselves.
@Component
public class CurrentAccountResolver implements HandlerMethodArgumentResolver {

    private static final String BEARER = "Bearer ";

    private final AccountService accountService;

    public CurrentAccountResolver(AccountService accountService) {
        this.accountService = accountService;
    }

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(CurrentAccount.class)
                && Account.class.isAssignableFrom(parameter.getParameterType());
    }

    @Override
    public Account resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                   NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
        String header = webRequest.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith(BEARER)) {
            throw new UnauthorizedException();
        }
        return accountService.findByToken(header.substring(BEARER.length()).strip())
                .orElseThrow(UnauthorizedException::new);
    }
}
