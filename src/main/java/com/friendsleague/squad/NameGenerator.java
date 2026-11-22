package com.friendsleague.squad;

import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

// Makes up Hebrew footballer names. One generator per league, so names don't repeat inside the same league.
public class NameGenerator {

    private static final List<String> FIRST_NAMES = List.of(
            "יוסי", "אבי", "דני", "משה", "רון", "עומר", "איתי", "נועם", "יונתן", "אורי",
            "גיא", "אלון", "עידו", "תומר", "שי", "ניר", "עמית", "יובל", "אסף", "רועי",
            "אדם", "בן", "דור", "אליה", "מאור", "ליאור", "עדי", "שחר", "טל", "אריאל",
            "נתנאל", "אביב", "ירין", "אופיר", "יהונתן", "מתן", "סהר", "עוז", "זיו", "חן");

    private static final List<String> LAST_NAMES = List.of(
            "כהן", "לוי", "מזרחי", "פרץ", "ביטון", "אברהם", "פרידמן", "אזולאי", "דהן", "אוחיון",
            "גבאי", "חדד", "עמר", "יוסף", "שפירא", "גולן", "בן דוד", "אלמוג", "ברק", "שגיא",
            "טל", "רוזן", "קפלן", "אשכנזי", "סלים", "נחום", "חזן", "מלכה", "וקנין", "אלון",
            "שלום", "בירנבאום", "זכאי", "רביבו", "טויטו", "שושן", "אדרי", "בוזגלו", "חביב", "נגר");

    private static final int MAX_ATTEMPTS = 200;

    private final Random random;
    private final Set<String> used = new HashSet<>();

    public NameGenerator(Random random) {
        this.random = random;
    }

    // Tries to find a name not used yet in this league. 40 x 40 = 1,600 combinations, so this almost always works.
    // After many seasons the names can run out; then a name repeats, just like in real life, instead of failing.
    public String next() {
        String name = randomName();
        for (int attempt = 0; attempt < MAX_ATTEMPTS && !used.add(name); attempt++) {
            name = randomName();
        }
        return name;
    }

    private String randomName() {
        return pick(FIRST_NAMES) + " " + pick(LAST_NAMES);
    }

    private String pick(List<String> names) {
        return names.get(random.nextInt(names.size()));
    }
}