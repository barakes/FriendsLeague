package com.friendsleague.web;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

// Registers the @CurrentAccount resolver with Spring MVC.
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final CurrentAccountResolver currentAccountResolver;

    public WebConfig(CurrentAccountResolver currentAccountResolver) {
        this.currentAccountResolver = currentAccountResolver;
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(currentAccountResolver);
    }
}
