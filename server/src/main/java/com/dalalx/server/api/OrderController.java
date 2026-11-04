package com.dalalx.server.api;

import com.dalalx.core.exchange.Exchange;
import com.dalalx.core.model.OrderRequest;
import com.dalalx.server.api.Dtos.CancelResponse;
import com.dalalx.server.api.Dtos.OrderResult;
import com.dalalx.server.api.Dtos.PlaceOrder;
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

    public OrderController(Exchange exchange) {
        this.exchange = exchange;
    }

    /** {@code POST /api/orders}: place an order; answers with its fills. */
    @PostMapping
    public OrderResult place(@RequestBody PlaceOrder body) {
        String trader = body.traderId() == null || body.traderId().isBlank() ? HUMAN : body.traderId();
        // The OrderRequest constructor validates (qty > 0, price for LIMIT/IOC, ...).
        OrderRequest request = new OrderRequest(body.symbol(), trader, body.side(), body.type(), body.price(), body.qty());
        return OrderResult.of(Futures.await(exchange.submit(request)));
    }

    /** {@code DELETE /api/orders/{id}}: cancel a resting order. */
    @DeleteMapping("/{id}")
    public CancelResponse cancel(@PathVariable long id) {
        return CancelResponse.of(Futures.await(exchange.cancel(id)));
    }
}
