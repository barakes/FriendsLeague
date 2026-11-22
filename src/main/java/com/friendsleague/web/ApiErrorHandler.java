package com.friendsleague.web;

import com.friendsleague.league.LeagueException;
import com.friendsleague.squad.LineupException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

// Turns expected problems from any controller into an answer with a message for the player.
// Unexpected errors are not handled here: Spring answers them with 500 and logs them.
@RestControllerAdvice
public class ApiErrorHandler {

    public record ErrorResponse(String message) {
    }

    @ExceptionHandler(UnauthorizedException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public ErrorResponse unauthorized(UnauthorizedException e) {
        return new ErrorResponse(e.getMessage());
    }

    @ExceptionHandler({LeagueException.class, LineupException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse badRequest(RuntimeException e) {
        return new ErrorResponse(e.getMessage());
    }
}
