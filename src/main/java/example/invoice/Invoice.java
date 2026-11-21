package example.invoice;

import io.micronaut.serde.annotation.Serdeable;

import java.util.List;

/**
 * What the model has to fill in. Every field is nullable: a receipt has no invoice number,
 * and the model should say "unknown" (null) instead of inventing a value.
 */
@Serdeable
public record Invoice(
        String vendor,
        String invoiceNumber,
        String date,
        String currency,
        List<LineItem> items,
        Double subtotal,
        Double vat,
        Double total) {

    @Serdeable
    public record LineItem(String description, Double quantity, Double unitPrice, Double total) {
    }
}
