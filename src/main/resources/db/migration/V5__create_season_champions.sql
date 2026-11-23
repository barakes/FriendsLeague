-- The champion of every finished season, for the league's "past champions" list.
-- The team's name is copied, so the list stays right even if a friend later takes over and renames the team.

create table season_champions (
    id         bigint generated always as identity primary key,
    league_id  bigint      not null references leagues (id) on delete cascade,
    season     integer     not null,
    team_name  varchar(30) not null,
    points     integer     not null,
    created_at timestamptz not null,
    unique (league_id, season)
);
