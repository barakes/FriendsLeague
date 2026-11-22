package com.friendsleague.match;

import com.friendsleague.squad.Lineup;

// What the simulation needs to know about one side: its name, lineup and tactic.
public record TeamSheet(String name, Lineup lineup, Tactic tactic) {
}