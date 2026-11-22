-- Money: the sponsor deal, debt, and a history of every payment.

alter table teams
    add column sponsor varchar(10) not null default 'SAFE',
    add column rounds_in_debt integer not null default 0 check (rounds_in_debt >= 0),
    add column points_deducted integer not null default 0 check (points_deducted >= 0);

-- A game's money is paid only after its live broadcast ends, so the money never gives away a result.
alter table games
    add column settled boolean not null default false;

create index games_to_settle on games (kickoff_at) where home_goals is not null and not settled;

create table money_entries (
    id         bigint generated always as identity primary key,
    team_id    bigint      not null references teams (id) on delete cascade,
    season     integer     not null,
    round      integer,
    kind       varchar(20) not null,
    amount     bigint      not null,
    note       varchar(60),
    created_at timestamptz not null
);

create index money_entries_by_team on money_entries (team_id, id);
