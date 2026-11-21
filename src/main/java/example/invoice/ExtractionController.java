package example.invoice;

import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.exceptions.HttpStatusException;
import io.micronaut.http.multipart.CompletedFileUpload;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import io.micronaut.serde.annotation.Serdeable;

import java.io.IOException;
import java.util.List;

@Controller
@ExecuteOn(TaskExecutors.BLOCKING)
public class ExtractionController {

    private final InvoiceExtractor textExtractor;
    private final ImageExtractor imageExtractor;
    private final PdfText pdfText;
    private final Verifier verifier;

    public ExtractionController(InvoiceExtractor textExtractor, ImageExtractor imageExtractor,
                                PdfText pdfText, Verifier verifier) {
        this.textExtractor = textExtractor;
        this.imageExtractor = imageExtractor;
        this.pdfText = pdfText;
        this.verifier = verifier;
    }

    @Serdeable
    public record Result(String source, Invoice invoice, List<String> warnings, long millis) {
    }

    @Get("/health")
    public String health() {
        return "ok";
    }

    @Post(value = "/extract", consumes = MediaType.MULTIPART_FORM_DATA)
    public Result extract(CompletedFileUpload file) throws IOException {
        long start = System.currentTimeMillis();
        byte[] bytes = file.getBytes();
        String name = file.getFilename() == null ? "" : file.getFilename().toLowerCase();
        Invoice invoice;
        String source;
        if (name.endsWith(".pdf")) {
            source = "pdf-text";
            invoice = textExtractor.extract(pdfText.extract(bytes));
        } else if (name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg")) {
            source = "image";
            invoice = imageExtractor.extract(bytes, name.endsWith(".png") ? "image/png" : "image/jpeg");
        } else {
            throw new HttpStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Send a .pdf, .png or .jpg file");
        }
        return new Result(source, invoice, verifier.check(invoice), System.currentTimeMillis() - start);
    }
}
