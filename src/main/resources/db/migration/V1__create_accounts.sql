-- Registered players (the friends who manage teams). Same design as in FourInARow, without the rating columns.
create table accounts (
                          id            bigint generated always as identity primary key,
                          username      varchar(20)  not null,
    -- Never the password itself: only its BCrypt hash.
                          password_hash varchar(100) not null,
                          created_at    timestamptz  not null default now()
);

-- "Dana" and "dana" are the same username.
create unique index accounts_username_unique on accounts (lower(username));

-- "Login tickets": after a successful login the browser gets a random token and sends it with every request.
-- Only a SHA-256 hash of the token is stored, so a leaked database can't be used to log in.
create table login_tokens (
                              token_hash varchar(64) primary key,
                              account_id bigint      not null references accounts (id) on delete cascade,
                              created_at timestamptz not null default now(),
                              expires_at timestamptz not null
);

create index login_tokens_account on login_tokens (account_id);
create index login_tokens_expires on login_tokens (expires_at);