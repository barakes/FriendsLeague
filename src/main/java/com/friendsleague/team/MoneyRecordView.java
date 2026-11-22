package com.friendsleague.team;

import com.friendsleague.money.MoneyKind;

import java.time.Instant;

// One line of the account history page. "round" is null for payments outside a round, such as a purchase.
public record MoneyRecordView(int season, Integer round, MoneyKind kind, long amount, String note, Instant createdAt) {
}
