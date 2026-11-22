-- A private league of friends.
create table leagues (
                         id               bigint generated always as identity primary key,
                         name             varchar(40) not null,
    -- Friends join with this code, like a room code in FourInARow.
                         join_code        varchar(8)  not null,
    -- How many teams the league has; computer teams fill the empty places when the season starts.
                         size             integer     not null check (size in (6, 8, 10, 12)),
    -- The friend who created the league: only he can start the season and change the schedule.
    owner_account_id bigint      not null references accounts (id),
    season           integer     not null default 1,
    -- WAITING (friends are joining), RUNNING (rounds are being played), BETWEEN_SEASONS
    status           varchar(20) not null,
    -- Kick-off times of the rounds, Israel time, for example '21:00' or '13:00,21:00'.
    kickoff_times    varchar(60) not null,
    created_at       timestamptz not null default now()
);

create unique index leagues_join_code_unique on leagues (join_code);

-- A team in a league. A computer team has no account.
create table teams (
                       id          bigint generated always as identity primary key,
                       league_id   bigint      not null references leagues (id) on delete cascade,
                       account_id  bigint      references accounts (id),
                       name        varchar(30) not null,
    -- Money in shekels (whole numbers, no fractions).
                       money       bigint      not null,
                       formation   varchar(10) not null,
                       tactic      varchar(10) not null,
                       created_at  timestamptz not null default now()
);

-- One team per friend in each league, and no two teams with the same name in a league.
create unique index teams_account_per_league on teams (league_id, account_id) where account_id is not null;
create unique index teams_name_per_league on teams (league_id, lower(name));
create index teams_league on teams (league_id);

-- A made-up footballer. A footballer with no team is on the transfer market of his league.
create table footballers (
                             id         bigint generated always as identity primary key,
                             league_id  bigint      not null references leagues (id) on delete cascade,
                             team_id    bigint      references teams (id) on delete set null,
                             name       varchar(40) not null,
                             position   varchar(12) not null,
                             rating     integer     not null check (rating between 1 and 99),
                             age        integer     not null check (age between 15 and 45),
                             created_at timestamptz not null default now()
);

create index footballers_team on footballers (team_id);
create index footballers_market on footballers (league_id) where team_id is null;

-- The lineup a manager saved: which footballer plays in which line.
create table lineup_slots (
                              team_id       bigint      not null references teams (id) on delete cascade,
                              footballer_id bigint      not null references footballers (id) on delete cascade,
                              line          varchar(12) not null,
                              primary key (team_id, footballer_id)
);