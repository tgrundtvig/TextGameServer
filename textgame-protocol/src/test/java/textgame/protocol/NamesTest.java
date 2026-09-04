package textgame.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * The naming rules, and — more importantly — what somebody is told when they break one.
 * A person who typed "Alices Game" wants to hear about the space, not to be read the rule.
 */
class NamesTest {

    @Test
    void ordinaryNamesAreFine() {
        assertTrue(Names.isTableName("speedrun"));
        assertTrue(Names.isTableName("grudge-match"));
        assertTrue(Names.isTableName("test1"));
        assertTrue(Names.isTableName("a"));
        assertTrue(Names.isPlayerName("alice"));
        assertTrue(Names.isPlayerName("alice_2"));
    }

    @Test
    void spacesAreOutInBothKinds() {
        assertFalse(Names.isTableName("Alices Game"));
        assertFalse(Names.isPlayerName("Ada Lovelace"));
    }

    @Test
    void lengthLimitsDiffer() {
        assertTrue(Names.isTableName("12345678901234567890"));
        assertFalse(Names.isTableName("123456789012345678901"));
        assertTrue(Names.isPlayerName("1234567890123456"));
        assertFalse(Names.isPlayerName("12345678901234567"));
    }

    @Test
    void emptyAndNullAreOut() {
        assertFalse(Names.isTableName(""));
        assertFalse(Names.isTableName(null));
        assertFalse(Names.isPlayerName(""));
    }

    @Test
    void theSpaceIsWhatGetsMentioned() {
        String said = Names.whyNotTableName("Alices Game");
        assertTrue(said.contains("cannot contain spaces"), said);
        assertFalse(said.contains("20"), "reciting the length rule here is noise: " + said);
    }

    @Test
    void aRepairedNameIsOffered() {
        assertTrue(Names.whyNotTableName("Alices Game").contains("Try Alices-Game."),
                Names.whyNotTableName("Alices Game"));
        assertTrue(Names.whyNotPlayerName("Ada Lovelace").contains("Try Ada-Lovelace."),
                Names.whyNotPlayerName("Ada Lovelace"));
    }

    @Test
    void tooLongSaysHowLongItActuallyIs() {
        String said = Names.whyNotTableName("way-too-long-a-name-for-a-table");
        assertTrue(said.contains("31 characters"), said);
        assertTrue(said.contains("the most is 20"), said);
    }

    @Test
    void oddCharactersAreNamedAsTheProblem() {
        String said = Names.whyNotTableName("tab!e");
        assertTrue(said.contains("letters, digits, - and _"), said);
        assertTrue(said.contains("Try tabe."), said);
    }

    @Test
    void suggestionsAreThemselvesValid() {
        for (String bad : new String[] {"Alices Game", "  lots   of   spaces  ", "tab!e",
                                        "way-too-long-a-name-for-a-table", "a---b",
                                        "Ada Lovelace's Table", "üñíçø∂é name"}) {
            String suggestion = Names.suggestTableName(bad);
            assertTrue(suggestion.isEmpty() || Names.isTableName(suggestion),
                    "suggestion for \"" + bad + "\" was itself invalid: \"" + suggestion + "\"");
        }
    }

    @Test
    void aSuggestionDoesNotEndInADash() {
        assertEquals("Alices-Game", Names.suggestTableName("Alices Game "));
        assertEquals("a-b", Names.suggestTableName("a   b   "));
        // Truncation must not leave a trailing dash either.
        assertEquals("abcdefghijklmnopqrs", Names.suggestTableName("abcdefghijklmnopqrs tuv"));
    }

    @Test
    void aNameWithNothingUsableGetsNoSuggestion() {
        assertEquals("", Names.suggestTableName("!!! ???"));
        String said = Names.whyNotTableName("!!! ???");
        assertFalse(said.contains("Try"), "nothing to suggest, so do not offer one: " + said);
    }

    @Test
    void theDefaultTableNameComesFromThePlayer() {
        assertEquals("alice-table", Names.defaultTableNameFor("alice"));
        assertEquals("alice-table", Names.defaultTableNameFor("Alice"));
        assertEquals("table", Names.defaultTableNameFor("").substring(0, 5));
        assertTrue(Names.isTableName(Names.defaultTableNameFor("1234567890123456")));
    }
}
