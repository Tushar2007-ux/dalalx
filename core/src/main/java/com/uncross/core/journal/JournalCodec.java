package com.uncross.core.journal;

import com.uncross.core.exchange.ListedStock;
import com.uncross.core.journal.JournalEntry.AccountOpened;
import com.uncross.core.journal.JournalEntry.Amended;
import com.uncross.core.journal.JournalEntry.Cancelled;
import com.uncross.core.journal.JournalEntry.CancelledAll;
import com.uncross.core.journal.JournalEntry.Listed;
import com.uncross.core.journal.JournalEntry.Submitted;
import com.uncross.core.model.OrderRequest;
import com.uncross.core.model.OrderType;
import com.uncross.core.model.Side;
import java.time.Instant;
import java.util.Optional;

/**
 * Turns a {@link JournalEntry} into one line of text and back.
 *
 * <p>The format is deliberately plain, so a student can open the file and
 * read it: fields separated by {@code |}, the time first, then a word saying
 * what happened.
 * <pre>
 * 2026-11-17T14:30:01.204Z|SUBMIT|1042|TCS|YOU|BUY|LIMIT|409000|100|0
 * 2026-11-17T14:30:03.917Z|CANCEL|1042
 * </pre>
 * A {@code |}, {@code %} or line break inside a name is written as
 * {@code %7C}, {@code %25} or {@code %0A}, so it can never split a line.
 *
 * <p>A line that cannot be read (a crash half-way through writing it, say)
 * decodes to empty and is skipped; one damaged line must not stop the rest of
 * the day being recovered.
 */
public final class JournalCodec {

    private static final String SEP = "|";

    private JournalCodec() {
    }

    public static String encode(JournalEntry entry) {
        String body = switch (entry) {
            case Submitted s -> join("SUBMIT", s.orderId(), s.request().symbol(), s.request().traderId(),
                    s.request().side(), s.request().type(), s.request().price(), s.request().qty(),
                    s.request().displayQty());
            case Cancelled c -> join("CANCEL", c.orderId());
            case Amended a -> join("AMEND", a.orderId(), a.price(), a.qty(), a.newOrderId());
            case CancelledAll c -> join("CANCEL_ALL", c.traderId());
            case Listed l -> join("LIST", l.stock().symbol(), l.stock().name(), l.stock().referencePrice());
            case AccountOpened o -> join("ACCOUNT", o.name(), o.pinHash());
        };
        return entry.at() + SEP + body;
    }

    public static Optional<JournalEntry> decode(String line) {
        try {
            String[] f = line.split("\\|", -1);
            Instant at = Instant.parse(f[0]);
            return Optional.of(switch (f[1]) {
                case "SUBMIT" -> new Submitted(at, Long.parseLong(f[2]), new OrderRequest(text(f[3]), text(f[4]),
                        Side.valueOf(f[5]), OrderType.valueOf(f[6]), Long.parseLong(f[7]), Long.parseLong(f[8]),
                        Long.parseLong(f[9])));
                case "CANCEL" -> new Cancelled(at, Long.parseLong(f[2]));
                case "AMEND" -> new Amended(at, Long.parseLong(f[2]), Long.parseLong(f[3]), Long.parseLong(f[4]),
                        Long.parseLong(f[5]));
                case "CANCEL_ALL" -> new CancelledAll(at, text(f[2]));
                case "LIST" -> new Listed(at, new ListedStock(text(f[2]), text(f[3]), Long.parseLong(f[4])));
                case "ACCOUNT" -> new AccountOpened(at, text(f[2]), text(f[3]));
                default -> throw new IllegalArgumentException("unknown entry " + f[1]);
            });
        } catch (RuntimeException damaged) {
            return Optional.empty();
        }
    }

    private static String join(Object... fields) {
        StringBuilder out = new StringBuilder();
        for (Object field : fields) {
            if (!out.isEmpty()) {
                out.append(SEP);
            }
            out.append(field instanceof String s ? escape(s) : field);
        }
        return out.toString();
    }

    static String escape(String s) {
        return s.replace("%", "%25").replace("|", "%7C").replace("\n", "%0A").replace("\r", "%0D");
    }

    static String text(String s) {
        return s.replace("%0D", "\r").replace("%0A", "\n").replace("%7C", "|").replace("%25", "%");
    }
}
