package com.friendsleague.team;

import com.friendsleague.match.Tactic;
import com.friendsleague.money.Sponsor;
import com.friendsleague.squad.Formation;
import com.friendsleague.squad.Position;

import java.util.List;

// A team as the browser sees it. "mine" tells the page whether to show the editing controls.
// wagesPerRound: what the whole squad earns in one round.
public record TeamView(Long id, Long leagueId, String name, boolean computer, boolean mine, long money,
                       Sponsor sponsor, int roundsInDebt, long wagesPerRound,
                       Formation formation, Tactic tactic, List<FootballerView> squad) {

    // "line" is where the footballer plays in the saved lineup, or null if he is on the bench.
    // "sellPrice" is what the team gets if it sells him to the market now.
    public record FootballerView(Long id, String name, Position position, int rating, int age, Position line,
                                 long wage, long sellPrice) {
    }
}
