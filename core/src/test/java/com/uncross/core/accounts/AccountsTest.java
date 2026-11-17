package com.uncross.core.accounts;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import com.uncross.core.journal.JournalEntry;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Opening accounts and signing in. */
class AccountsTest {

    private final List<JournalEntry> journal = new ArrayList<>();
    private final Accounts accounts = new Accounts(Clock.systemUTC());

    AccountsTest() {
        accounts.journalTo(journal::add);
    }

    @Test
    void anAccountStartsWithTenLakhRupeesAndNoShares() {
        String token = accounts.open("Priya S", "4321");

        Account.Statement statement = accounts.bySession(token).orElseThrow().statement();
        assertThat(statement.traderId()).isEqualTo("A-PRIYAS");
        assertThat(statement.cash()).isEqualTo(Accounts.STARTING_CASH);
        assertThat(statement.holdings()).isEmpty();
    }

    @Test
    void signingInNeedsTheRightPin() {
        accounts.open("Priya", "4321");

        assertThat(accounts.bySession(accounts.signIn("priya", "4321"))).isPresent();
        assertThatIllegalArgumentException().isThrownBy(() -> accounts.signIn("Priya", "1111"));
        assertThatIllegalArgumentException().isThrownBy(() -> accounts.signIn("Nobody", "4321"));
    }

    @Test
    void signingOutEndsTheSession() {
        String token = accounts.open("Priya", "4321");
        accounts.signOut(token);
        assertThat(accounts.bySession(token)).isEmpty();
    }

    @Test
    void namesAndPinsAreChecked() {
        accounts.open("Priya", "4321");
        assertThatIllegalArgumentException().isThrownBy(() -> accounts.open("PRIYA", "9999"))
                .withMessageContaining("already");
        assertThatIllegalArgumentException().isThrownBy(() -> accounts.open("P", "9999"));
        assertThatIllegalArgumentException().isThrownBy(() -> accounts.open("Robert'); DROP", "9999"));
        assertThatIllegalArgumentException().isThrownBy(() -> accounts.open("Ravi", "12"));
        assertThatIllegalArgumentException().isThrownBy(() -> accounts.open("Ravi", "abcd"));
    }

    @Test
    void thePinIsNeverStoredOrJournalledAsItIs() {
        accounts.open("Priya", "4321");

        JournalEntry.AccountOpened opened = (JournalEntry.AccountOpened) journal.get(0);
        assertThat(opened.pinHash()).doesNotContain("4321").contains(":");

        Accounts restored = new Accounts(Clock.systemUTC());
        restored.restore(opened);
        assertThat(restored.bySession(restored.signIn("Priya", "4321"))).isPresent();
    }
}
