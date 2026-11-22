package com.friendsleague.web;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

// Put this on a controller method parameter of type Account to get the logged-in player.
// The token in the "Authorization: Bearer ..." header is checked before the method runs;
// without a valid token the request is answered with 401 and the method is never called.
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface CurrentAccount {
}
