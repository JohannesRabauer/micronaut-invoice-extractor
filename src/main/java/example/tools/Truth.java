package example.tools;

import io.micronaut.serde.annotation.Serdeable;

/** The correct values for one generated document (one entry of samples/ground-truth.json). */
@Serdeable
public record Truth(
        String file,
        String vendor,
        String invoiceNumber,
        String date,
        String currency,
        int items,
        Double subtotal,
        Double vat,
        Double total) {
}
