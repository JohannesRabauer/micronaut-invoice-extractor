package example.invoice;

import jakarta.inject.Singleton;

import java.util.ArrayList;
import java.util.List;

/**
 * A small model makes arithmetic and copying mistakes. These checks do not need a model:
 * the numbers on an invoice have to add up, otherwise a human should look at it.
 */
@Singleton
public class Verifier {

    private static final double TOLERANCE = 0.02;

    public List<String> check(Invoice invoice) {
        List<String> warnings = new ArrayList<>();
        if (invoice.total() == null) {
            warnings.add("total is missing");
        }
        if (invoice.items() == null || invoice.items().isEmpty()) {
            warnings.add("no line items found");
            return warnings;
        }
        double itemSum = 0;
        for (Invoice.LineItem item : invoice.items()) {
            if (item.total() == null) {
                warnings.add("line item without total: " + item.description());
                continue;
            }
            itemSum += item.total();
            if (item.quantity() != null && item.unitPrice() != null
                    && Math.abs(item.quantity() * item.unitPrice() - item.total()) > TOLERANCE) {
                warnings.add("quantity x unit price does not match total for: " + item.description());
            }
        }
        if (invoice.subtotal() != null && Math.abs(itemSum - invoice.subtotal()) > TOLERANCE) {
            warnings.add("line items sum to %.2f but subtotal is %.2f".formatted(itemSum, invoice.subtotal()));
        }
        if (invoice.total() != null) {
            double expectedFromParts = invoice.subtotal() != null && invoice.vat() != null
                    ? invoice.subtotal() + invoice.vat() : Double.NaN;
            boolean totalMatchesItems = Math.abs(itemSum - invoice.total()) <= TOLERANCE;
            boolean totalMatchesParts = !Double.isNaN(expectedFromParts)
                    && Math.abs(expectedFromParts - invoice.total()) <= TOLERANCE;
            if (!totalMatchesItems && !totalMatchesParts) {
                warnings.add("total %.2f matches neither the item sum %.2f nor subtotal + vat"
                        .formatted(invoice.total(), itemSum));
            }
        }
        return warnings;
    }
}
