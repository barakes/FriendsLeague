package com.friendsleague.money;

// The sponsor deal a manager chooses for the season.
// SAFE pays the same every round. RISKY pays little, plus a bonus for every win: good for strong teams.
public enum Sponsor {
    SAFE(80_000, 0),
    RISKY(30_000, 60_000);

    private final long perRound;
    private final long perWin;

    Sponsor(long perRound, long perWin) {
        this.perRound = perRound;
        this.perWin = perWin;
    }

    public long payment(boolean won) {
        return perRound + (won ? perWin : 0);
    }
}
