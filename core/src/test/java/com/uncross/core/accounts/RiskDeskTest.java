package com.uncross.core.accounts;

import static org.assertj.core.api.Assertions.assertThat;

import com.uncross.core.events.EventBus;
import com.uncross.core.exchange.Exchange;
import com.uncross.core.exchange.ListedStock;
import com.uncross.core.matching.MatchResult;
import com.uncross.core.matching.OrderGate;
import com.uncross.core.model.OrderRequest;
import com.uncross.core.model.OrderStatus;
import com.uncross.core.model.OrderType;
import com.uncross.core.model.Side;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** No buying with money you do not have; no selling shares you do not own. */
class RiskDeskTest {

    private static final long PRICE = 1_000_00; // ₹1,000.00

    private final Accounts accounts = new Accounts(Clock.systemUTC());
    private final EventBus bus = new EventBus();
    private final Exchange exchange = new Exchange(List.of(new ListedStock("TCS", "TCS", PRICE)), bus, Clock.systemUTC());
    private final RiskDesk desk = new RiskDesk(accounts, symbol -> PRICE);
    private final String me;

    RiskDeskTest() {
        bus.register(desk);
        exchange.guardWith(desk);
        exchange.start();
        accounts.open("Priya", "4321");
        me = Accounts.traderIdFor("Priya");
    }

    @AfterEach
    void stop() throws InterruptedException {
        exchange.shutdown(2, TimeUnit.SECONDS);
    }

    private MatchResult send(String trader, Side side, OrderType type, long price, long qty) {
        return exchange.submit(new OrderRequest("TCS", trader, side, type, price, qty)).join();
    }

    private Account.Statement mine() {
        return accounts.byTrader(me).orElseThrow().statement();
    }

    @Test
    void aBuyBeyondYourCashIsRefused() {
        MatchResult result = send(me, Side.BUY, OrderType.LIMIT, PRICE, 1_001); // ₹10,01,000 > ₹10,00,000

        assertThat(result.status()).isEqualTo(OrderStatus.REJECTED);
        assertThat(result.reason()).contains("not enough cash");
        assertThat(mine().cashSetAside()).isZero();
    }

    @Test
    void aWaitingBuySetsMoneyAsideSoItCannotBeSpentTwice() {
        send(me, Side.BUY, OrderType.LIMIT, PRICE, 600);

        assertThat(mine().availableCash()).isEqualTo(Accounts.STARTING_CASH - 600 * PRICE);
        assertThat(send(me, Side.BUY, OrderType.LIMIT, PRICE, 600).status()).isEqualTo(OrderStatus.REJECTED);
    }

    @Test
    void aFillPaysForTheSharesAtTheTradePrice() {
        send("SELLER", Side.SELL, OrderType.LIMIT, PRICE - 500, 100); // cheaper than my limit
        send(me, Side.BUY, OrderType.LIMIT, PRICE, 100);

        Account.Statement after = mine();
        assertThat(after.cash()).isEqualTo(Accounts.STARTING_CASH - 100 * (PRICE - 500));
        assertThat(after.cashSetAside()).isZero();
        assertThat(after.holdings()).singleElement().satisfies(h -> assertThat(h.shares()).isEqualTo(100));
    }

    @Test
    void youCannotSellWhatYouDoNotOwn() {
        MatchResult result = send(me, Side.SELL, OrderType.LIMIT, PRICE, 10);

        assertThat(result.status()).isEqualTo(OrderStatus.REJECTED);
        assertThat(result.reason()).contains("no short selling");
    }

    @Test
    void sharesYouBoughtCanBeSoldButOnlyOnce() {
        send("SELLER", Side.SELL, OrderType.LIMIT, PRICE, 100);
        send(me, Side.BUY, OrderType.MARKET, 0, 100);

        assertThat(send(me, Side.SELL, OrderType.LIMIT, PRICE + 1_000, 100).status()).isEqualTo(OrderStatus.NEW);
        assertThat(send(me, Side.SELL, OrderType.LIMIT, PRICE + 1_000, 1).status()).isEqualTo(OrderStatus.REJECTED);
    }

    @Test
    void cancellingOrShrinkingAnOrderGivesTheMoneyBack() {
        long id = send(me, Side.BUY, OrderType.LIMIT, PRICE, 500).incoming().id();

        exchange.amend(id, PRICE, 200).join();
        assertThat(mine().cashSetAside()).isEqualTo(200 * PRICE);

        exchange.cancel(id).join();
        assertThat(mine().cashSetAside()).isZero();
    }

    @Test
    void aRefusalFurtherDownTheLineReleasesTheReservation() {
        exchange.guardWith(OrderGate.allOf(desk, request -> Optional.of("stock halted")));

        send(me, Side.BUY, OrderType.LIMIT, PRICE, 100);

        assertThat(mine().cashSetAside()).isZero();
    }

    @Test
    void botsAreNotAccountsAndPassStraightThrough() {
        assertThat(send("MUNIM", Side.SELL, OrderType.LIMIT, PRICE, 5_000_000 / 10).status()).isEqualTo(OrderStatus.NEW);
    }
}
