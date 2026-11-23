package com.friendsleague.league;

import com.friendsleague.account.Account;
import com.friendsleague.account.AccountRepository;
import com.friendsleague.game.Game;
import com.friendsleague.game.GameRepository;
import com.friendsleague.market.MarketGenerator;
import com.friendsleague.squad.FootballerProfile;
import com.friendsleague.squad.Formation;
import com.friendsleague.squad.Lineup;
import com.friendsleague.squad.NameGenerator;
import com.friendsleague.squad.Position;
import com.friendsleague.squad.SquadGenerator;
import com.friendsleague.team.Footballer;
import com.friendsleague.team.FootballerRepository;
import com.friendsleague.team.LineupSlot;
import com.friendsleague.team.Team;
import com.friendsleague.team.TeamRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.regex.Pattern;

// Creating a league, joining it, and starting a season.
// The first season also adds computer teams and opens the transfer market.
@Service
public class LeagueService {

    public static final Set<Integer> SIZES = Set.of(6, 8, 10, 12);
    public static final long STARTING_MONEY = 10_000_000;
    public static final int STARTING_AVERAGE_RATING = 60;

    // No 0/O or 1/I/L, so a code can be read out to a friend without mix-ups (as in FourInARow).
    private static final String CODE_ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";
    private static final int CODE_LENGTH = 6;

    // Letters in any language, digits, spaces and a few signs that appear in team names (like "בית\"ר" or "F.C.").
    private static final Pattern NAME = Pattern.compile("[\\p{L}\\p{N} '\"\\-.]+");

    private static final List<String> COMPUTER_TEAM_NAMES = List.of(
            "מכבי הכפר", "הפועל העמק", "בית\"ר המושבה", "עירוני הגבעה", "הכוח הצפוני", "מכבי חוף הים",
            "הפועל הדרום", "אחי הנחל", "עירוני העמקים", "בני השרון", "מכבי ההרים", "הפועל הנמל");

    private final LeagueRepository leagues;
    private final TeamRepository teams;
    private final FootballerRepository footballers;
    private final GameRepository games;
    private final AccountRepository accounts;
    private final Clock clock;
    private final Random random = new SecureRandom();

    public LeagueService(LeagueRepository leagues, TeamRepository teams, FootballerRepository footballers,
                         GameRepository games, AccountRepository accounts, Clock clock) {
        this.leagues = leagues;
        this.teams = teams;
        this.footballers = footballers;
        this.games = games;
        this.accounts = accounts;
        this.clock = clock;
    }

    // A friend creates a league, and gets the first team in it.
    @Transactional
    public League createLeague(Long accountId, String leagueName, int size, String kickoffTimes, String teamName) {
        String name = checkName(leagueName, 40, "League name");
        if (!SIZES.contains(size)) {
            throw new LeagueException("A league has 6, 8, 10 or 12 teams");
        }
        String times = KickoffSchedule.normalize(kickoffTimes);
        Account owner = accounts.getReferenceById(accountId);

        League league = leagues.save(new League(name, newJoinCode(), size, owner, times));
        addTeam(league, owner, checkName(teamName, 30, "Team name"));
        return league;
    }

    // A friend joins with the league's code.
    // Before the first season he gets a new team with a fresh squad.
    // Between seasons he takes over a computer team, with its squad and money, under his own team name.
    @Transactional
    public Team joinLeague(Long accountId, String joinCode, String teamName) {
        String code = joinCode == null ? "" : joinCode.strip().toUpperCase(Locale.ROOT);
        League league = leagues.findByJoinCode(code)
                .orElseThrow(() -> new LeagueException("There is no league with this code"));

        if (league.getStatus() == LeagueStatus.RUNNING) {
            throw new LeagueException("This league has already started; you can join next season");
        }
        if (teams.findByLeagueIdAndAccountId(league.getId(), accountId).isPresent()) {
            throw new LeagueException("You are already in this league");
        }
        String name = checkName(teamName, 30, "Team name");

        if (league.getStatus() == LeagueStatus.BETWEEN_SEASONS) {
            return takeOverComputerTeam(league, accountId, name);
        }
        if (teams.countByLeagueId(league.getId()) >= league.getTeamCount()) {
            throw new LeagueException("This league is full");
        }
        return addTeam(league, accounts.getReferenceById(accountId), name);
    }

    private Team takeOverComputerTeam(League league, Long accountId, String name) {
        Team team = teams.findByLeagueIdOrderById(league.getId()).stream()
                .filter(Team::isComputer)
                .findFirst()
                .orElseThrow(() -> new LeagueException("This league is full"));
        // The computer team's own name may be reused, even with different capital letters.
        if (!team.getName().equalsIgnoreCase(name) && teams.existsByLeagueIdAndNameIgnoreCase(league.getId(), name)) {
            throw new LeagueException("This team name is taken in this league");
        }
        team.assignTo(accounts.getReferenceById(accountId));
        team.rename(name);
        return team;
    }

    // The creator starts a season and the whole schedule is created.
    // The first time, computer teams fill the empty places and the transfer market opens.
    // Between seasons, the same teams play a new season (the market is already open).
    @Transactional
    public void startSeason(Long accountId, Long leagueId) {
        League league = leagues.findById(leagueId).orElseThrow(() -> new LeagueException("There is no such league"));
        if (!league.getOwner().getId().equals(accountId)) {
            throw new LeagueException("Only the league's creator can start the season");
        }
        if (league.getStatus() == LeagueStatus.RUNNING) {
            throw new LeagueException("The season has already started");
        }

        if (league.getStatus() == LeagueStatus.BETWEEN_SEASONS) {
            league.startNextSeason();
            List<Team> leagueTeams = new ArrayList<>(teams.findByLeagueIdOrderById(league.getId()));
            // A new table: points lost for debt last season are forgotten.
            leagueTeams.forEach(Team::startNewSeason);
            Collections.shuffle(leagueTeams, random);
            createSchedule(league, leagueTeams);
            return;
        }

        addComputerTeams(league);

        // A random order, so it isn't always the creator who plays the first game at home.
        List<Team> leagueTeams = new ArrayList<>(teams.findByLeagueIdOrderById(league.getId()));
        Collections.shuffle(leagueTeams, random);
        createSchedule(league, leagueTeams);
        fillMarket(league);

        league.start();
    }

    private void addComputerTeams(League league) {
        long missing = league.getTeamCount() - teams.countByLeagueId(league.getId());
        for (String name : COMPUTER_TEAM_NAMES) {
            if (missing == 0) {
                return;
            }
            if (!teams.existsByLeagueIdAndNameIgnoreCase(league.getId(), name)) {
                addTeam(league, null, name);
                missing--;
            }
        }
        // 12 names and at most 12 teams: we only get here if friends took computer names themselves.
        for (int number = 1; missing > 0; number++) {
            String name = "קבוצת מחשב " + number;
            if (!teams.existsByLeagueIdAndNameIgnoreCase(league.getId(), name)) {
                addTeam(league, null, name);
                missing--;
            }
        }
    }

    // The transfer market opens with the season: free agents without a team.
    private void fillMarket(League league) {
        NameGenerator names = new NameGenerator(random, footballers.findNamesByLeagueId(league.getId()));
        List<Footballer> market = new ArrayList<>();
        for (FootballerProfile profile : new MarketGenerator(random, names).generate(league.getTeamCount())) {
            market.add(new Footballer(league, null, profile));
        }
        footballers.saveAll(market);
    }

    private void createSchedule(League league, List<Team> order) {
        List<Fixture> fixtures = ScheduleGenerator.doubleRoundRobin(order.size());
        List<Instant> kickoffs = KickoffSchedule.next(league.getKickoffTimes(), clock.instant(),
                ScheduleGenerator.rounds(order.size()));

        List<Game> season = new ArrayList<>(fixtures.size());
        for (Fixture fixture : fixtures) {
            season.add(new Game(league, league.getSeason(), fixture.round(),
                    order.get(fixture.home()), order.get(fixture.away()), kickoffs.get(fixture.round() - 1)));
        }
        games.saveAll(season);
    }

    // A new team with 18 generated footballers and the best 4-4-2 lineup already saved.
    private Team addTeam(League league, Account account, String teamName) {
        if (teams.existsByLeagueIdAndNameIgnoreCase(league.getId(), teamName)) {
            throw new LeagueException("This team name is taken in this league");
        }
        Team team = teams.save(new Team(league, account, teamName, STARTING_MONEY));

        NameGenerator names = new NameGenerator(random, footballers.findNamesByLeagueId(league.getId()));
        List<FootballerProfile> profiles = new SquadGenerator(random, names).generate(STARTING_AVERAGE_RATING);

        // Remember which saved footballer each generated profile became, by the object itself.
        Map<FootballerProfile, Footballer> saved = new IdentityHashMap<>();
        for (FootballerProfile profile : profiles) {
            saved.put(profile, footballers.save(new Footballer(league, team, profile)));
        }

        Lineup lineup = Lineup.best(Formation.F_4_4_2, profiles);
        List<LineupSlot> slots = new ArrayList<>();
        for (Position line : Position.values()) {
            for (FootballerProfile profile : lineup.playersIn(line)) {
                slots.add(new LineupSlot(saved.get(profile).getId(), line));
            }
        }
        team.saveLineup(slots);
        return team;
    }

    private String newJoinCode() {
        String code;
        do {
            StringBuilder builder = new StringBuilder(CODE_LENGTH);
            for (int i = 0; i < CODE_LENGTH; i++) {
                builder.append(CODE_ALPHABET.charAt(random.nextInt(CODE_ALPHABET.length())));
            }
            code = builder.toString();
        } while (leagues.existsByJoinCode(code));
        return code;
    }

    private static String checkName(String value, int maxLength, String what) {
        String name = value == null ? "" : value.strip();
        if (name.length() < 2 || name.length() > maxLength || !NAME.matcher(name).matches()) {
            throw new LeagueException(what + " must be 2-" + maxLength + " letters, digits or spaces");
        }
        return name;
    }
}
