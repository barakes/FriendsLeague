package com.friendsleague.market;

import com.friendsleague.money.MoneyKind;
import com.friendsleague.money.Prices;
import com.friendsleague.squad.FootballerProfile;
import com.friendsleague.team.Footballer;
import com.friendsleague.team.FootballerRepository;
import com.friendsleague.team.MoneyRecord;
import com.friendsleague.team.MoneyRecordRepository;
import com.friendsleague.team.Team;
import com.friendsleague.team.TeamRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

// Lets a computer team use the market after each of its games. The decision itself is in ComputerManager.
// The same locks as a friend's purchase (team first, then footballer), so a friend and the computer
// can never buy the same footballer.
@Service
public class ComputerTransfers {

    private final TeamRepository teams;
    private final FootballerRepository footballers;
    private final MoneyRecordRepository moneyRecords;

    public ComputerTransfers(TeamRepository teams, FootballerRepository footballers, MoneyRecordRepository moneyRecords) {
        this.teams = teams;
        this.footballers = footballers;
        this.moneyRecords = moneyRecords;
    }

    // Does nothing for a friend's team. Returns true if the computer bought or sold someone.
    @Transactional
    public boolean consider(Long teamId) {
        Team team = teams.findByIdForUpdate(teamId).orElseThrow();
        if (!team.isComputer()) {
            return false;
        }

        Map<FootballerProfile, Footballer> byProfile = new IdentityHashMap<>();
        List<FootballerProfile> squad = profiles(footballers.findByTeamIdOrderById(teamId), byProfile);
        List<FootballerProfile> market = profiles(
                footballers.findByLeagueIdAndTeamIsNullOrderByRatingDesc(team.getLeague().getId()), byProfile);

        ComputerManager.Decision decision = ComputerManager.decide(squad, team.getMoney(), market);
        boolean changed = false;
        if (decision.sell() != null) {
            sell(team, byProfile.get(decision.sell()));
            changed = true;
        }
        if (decision.buy() != null) {
            changed |= buy(team, byProfile.get(decision.buy()));
        }
        return changed;
    }

    private void sell(Team team, Footballer footballer) {
        footballers.findByIdForUpdate(footballer.getId());
        long price = Prices.sellPrice(footballer.getRating(), footballer.getAge());
        footballer.moveTo(null);
        team.addMoney(price);
        moneyRecords.save(new MoneyRecord(team, team.getLeague().getSeason(), null, MoneyKind.SALE, price,
                footballer.getName()));
    }

    // False if a friend bought him a moment earlier.
    private boolean buy(Team team, Footballer footballer) {
        Footballer locked = footballers.findByIdForUpdate(footballer.getId()).orElseThrow();
        long price = Prices.price(locked.getRating(), locked.getAge());
        if (!locked.isOnMarket() || team.getMoney() < price) {
            return false;
        }
        locked.moveTo(team);
        team.addMoney(-price);
        moneyRecords.save(new MoneyRecord(team, team.getLeague().getSeason(), null, MoneyKind.PURCHASE, -price,
                locked.getName()));
        return true;
    }

    private static List<FootballerProfile> profiles(List<Footballer> list, Map<FootballerProfile, Footballer> byProfile) {
        List<FootballerProfile> profiles = new ArrayList<>(list.size());
        for (Footballer footballer : list) {
            FootballerProfile profile = footballer.toProfile();
            byProfile.put(profile, footballer);
            profiles.add(profile);
        }
        return profiles;
    }
}
