package com.friendsleague.team;

import com.friendsleague.squad.FootballerProfile;
import com.friendsleague.squad.Formation;
import com.friendsleague.squad.Lineup;
import com.friendsleague.squad.Position;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

// Saves the strongest lineup a team can field in a formation.
// Used by the "pick my lineup" button, and after a sale when the sold footballer was in the lineup.
@Component
public class LineupPicker {

    private final FootballerRepository footballers;

    public LineupPicker(FootballerRepository footballers) {
        this.footballers = footballers;
    }

    // Must be called inside a transaction: the lineup is saved when it ends.
    public void pickBest(Team team, Formation formation) {
        Map<FootballerProfile, Footballer> byProfile = new IdentityHashMap<>();
        List<FootballerProfile> profiles = new ArrayList<>();
        for (Footballer footballer : footballers.findByTeamIdOrderById(team.getId())) {
            FootballerProfile profile = footballer.toProfile();
            byProfile.put(profile, footballer);
            profiles.add(profile);
        }

        Lineup best = Lineup.best(formation, profiles);
        List<LineupSlot> slots = new ArrayList<>();
        for (Position line : Position.values()) {
            for (FootballerProfile profile : best.playersIn(line)) {
                slots.add(new LineupSlot(byProfile.get(profile).getId(), line));
            }
        }

        team.changeTactics(formation, team.getTactic());
        team.saveLineup(slots);
    }
}
