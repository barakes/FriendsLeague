-- Every game of every season. A game is "played" once its score is saved.
create table games (
                       id           bigint generated always as identity primary key,
                       league_id    bigint      not null references leagues (id) on delete cascade,
                       season       integer     not null,
                       round        integer     not null,
                       home_team_id bigint      not null references teams (id) on delete cascade,
                       away_team_id bigint      not null references teams (id) on delete cascade,
                       kickoff_at   timestamptz not null,
    -- Null until the game is played.
                       home_goals   integer,
                       away_goals   integer,
                       created_at   timestamptz not null default now()
);

create index games_league_round on games (league_id, season, round);
-- Finding games whose kick-off time has passed but were not played yet (for example after the server slept).
create index games_waiting on games (kickoff_at) where home_goals is null;

-- The goals of a game, in time order ("seq" keeps the order when two goals share a minute).
create table game_goals (
                            game_id bigint      not null references games (id) on delete cascade,
                            seq     integer     not null,
                            minute  integer     not null check (minute between 1 and 90),
    side    varchar(4)  not null,
    scorer  varchar(40) not null,
    primary key (game_id, seq)
);