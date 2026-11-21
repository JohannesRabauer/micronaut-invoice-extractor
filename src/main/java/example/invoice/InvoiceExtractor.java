package example.invoice;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import io.micronaut.langchain4j.annotation.AiService;

/**
 * Text path: the PDF text goes in, a typed {@link Invoice} comes out.
 * LangChain4j builds the JSON schema from the record and parses the answer.
 */
@AiService
public interface InvoiceExtractor {

    @SystemMessage("""
            You extract structured data from invoices and receipts.
            Rules:
            - Copy values exactly as printed. Do not guess. Use null for anything that is not in the document.
            - date: ISO format yyyy-MM-dd.
            - currency: ISO code such as EUR, USD, GBP.
            - Numbers are plain decimals with a dot, no currency symbol, no thousands separator.
            - items: one entry per line item, in the order of the document.
            - vat: the total VAT or tax amount, not the percentage.
            """)
    @UserMessage("Extract the data from this document:\n\n{{text}}")
    Invoice extract(@V("text") String text);
}
