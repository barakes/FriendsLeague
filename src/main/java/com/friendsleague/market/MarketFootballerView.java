package com.friendsleague.market;

import com.friendsleague.squad.Position;

// A free agent as the market page shows him.
public record MarketFootballerView(Long id, String name, Position position, int rating, int age, long price, long wage) {
}
