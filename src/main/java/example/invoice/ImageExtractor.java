package example.invoice;

import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ResponseFormat;
import io.micronaut.serde.ObjectMapper;
import jakarta.inject.Singleton;

import java.io.IOException;
import java.util.Base64;

/**
 * Image path: a photo or scan of a receipt goes to the vision model as an image.
 * Needs a llama-server started with a multimodal model (the compose file does that).
 */
@Singleton
public class ImageExtractor {

    private static final String SYSTEM = """
            You read receipts and invoices from photos and answer with one JSON object only.
            Copy values exactly as printed. Use null for anything that is not visible. Do not guess.
            """;

    private static final String PROMPT = """
            Extract the data from this document image. Answer with JSON in exactly this shape:
            {"vendor": string, "invoiceNumber": string or null, "date": "yyyy-MM-dd",
             "currency": "EUR" | "USD" | "GBP" | other ISO code,
             "items": [{"description": string, "quantity": number, "unitPrice": number, "total": number}],
             "subtotal": number or null, "vat": number or null, "total": number}
            Numbers use a dot as decimal separator and have no currency symbol.
            "vat" is the tax amount, not the percentage.
            """;

    private final ChatModel chatModel;
    private final ObjectMapper mapper;

    public ImageExtractor(ChatModel chatModel, ObjectMapper mapper) {
        this.chatModel = chatModel;
        this.mapper = mapper;
    }

    public Invoice extract(byte[] image, String mimeType) throws IOException {
        String base64 = Base64.getEncoder().encodeToString(image);
        ChatRequest request = ChatRequest.builder()
                .messages(
                        SystemMessage.from(SYSTEM),
                        UserMessage.from(TextContent.from(PROMPT), ImageContent.from(base64, mimeType)))
                .responseFormat(ResponseFormat.JSON)
                .build();
        String json = chatModel.chat(request).aiMessage().text();
        return mapper.readValue(json, Invoice.class);
    }
}
