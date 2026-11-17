package com.uncross.server.api;

import com.uncross.core.accounts.Account;
import com.uncross.core.accounts.Accounts;
import com.uncross.server.api.Dtos.AmendOrder;
import com.uncross.server.api.Dtos.AmendResponse;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import com.uncross.core.exchange.Exchange;
import com.uncross.core.model.OrderRequest;
import com.uncross.server.api.Dtos.CancelResponse;
import com.uncross.server.api.Dtos.OrderResult;
import com.uncross.server.api.Dtos.PlaceOrder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST endpoints for placing and cancelling orders.
 *
 * <p>An <em>adapter</em>: it converts JSON to a core {@link OrderRequest},
 * hands it to the {@link Exchange}, waits for the worker's answer and converts
 * that back to JSON. All validation and matching happens in core; there's no
 * business logic here.
 */
@RestController
@RequestMapping("/api/orders")
public class OrderController {

    /** The trader id of the person at the screen (orders without a traderId are theirs). */
    public static final String HUMAN = "YOU";

    private final Exchange exchange;

    /** The header a signed-in browser sends; its orders then trade from that account. */
    public static final String SESSION_HEADER = "X-Uncross-Session";

    private final Accounts accounts;

    public OrderController(Exchange exchange, Accounts accounts) {
        this.exchange = exchange;
        this.accounts = accounts;
    }

    /** {@code POST /api/orders}: place an order; answers with its fills. */
    @PostMapping
    public OrderResult place(@RequestBody PlaceOrder body,
                             @RequestHeader(value = SESSION_HEADER, required = false) String session) {
        String trader = accounts.bySession(session).map(Account::traderId)
                .orElse(body.traderId() == null || body.traderId().isBlank() ? HUMAN : body.traderId());
        // The OrderRequest constructor validates (qty > 0, price for LIMIT/IOC, ...).
        OrderRequest request = new OrderRequest(body.symbol(), trader, body.side(), body.type(), body.price(), body.qty(),
                body.displayQty() == null ? 0 : body.displayQty());
        return OrderResult.of(Futures.await(exchange.submit(request)));
    }

    /** {@code DELETE /api/orders/{id}}: cancel a resting order. */
    /** Changes a waiting order's price or size (a smaller size at the same price keeps its place in line). */
    @PatchMapping("/{id}")
    public AmendResponse amend(@PathVariable long id, @RequestBody AmendOrder body) {
        return AmendResponse.of(Futures.await(exchange.amend(id, body.price(), body.qty())));
    }

    @DeleteMapping("/{id}")
    public CancelResponse cancel(@PathVariable long id) {
        return CancelResponse.of(Futures.await(exchange.cancel(id)));
    }
}
