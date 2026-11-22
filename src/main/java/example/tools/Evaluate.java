package example.tools;

import example.invoice.ExtractionController.Result;
import example.invoice.Invoice;
import io.micronaut.core.type.Argument;
import io.micronaut.serde.ObjectMapper;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Sends every sample to the service and scores the answer against samples/ground-truth.json.
 *
 * <pre>
 * docker compose --profile tools run --rm evaluate            # all documents
 * docker compose --profile tools run --rm evaluate receipt-04 # files whose name contains the argument
 * </pre>
 */
public class Evaluate {

    public static void main(String[] args) throws Exception {
        Path dir = Path.of(args.length > 0 ? args[0] : "samples");
        String only = args.length > 1 ? args[1] : "";
        String url = System.getenv().getOrDefault("APP_URL", "http://localhost:8080/extract");

        ObjectMapper mapper = ObjectMapper.getDefault();
        List<Truth> truths = mapper.readValue(Files.readAllBytes(dir.resolve("ground-truth.json")), Argument.listOf(Truth.class))
                .stream().filter(t -> t.file().contains(only)).toList();
        HttpClient client = HttpClient.newHttpClient();

        int okFields = 0, allFields = 0, flagged = 0;
        for (Truth truth : truths) {
            long start = System.currentTimeMillis();
            Result got;
            try {
                got = post(client, mapper, url, dir.resolve(truth.file()));
            } catch (Exception e) {
                System.out.printf("%-40s FAILED: %s%n", truth.file(), e);
                allFields += 6;
                continue;
            }
            List<String> wrong = new ArrayList<>();
            int checks = score(truth, got.invoice(), wrong);
            okFields += checks - wrong.size();
            allFields += checks;
            List<String> warnings = got.warnings() == null ? List.of() : got.warnings();
            if (!warnings.isEmpty()) {
                flagged++;
            }
            System.out.printf("%-40s %d/%d fields  %5.1fs  wrong: %s  |  flagged by verifier: %s%n", truth.file(),
                    checks - wrong.size(), checks, (System.currentTimeMillis() - start) / 1000.0,
                    wrong.isEmpty() ? "-" : String.join(", ", wrong),
                    warnings.isEmpty() ? "-" : String.join("; ", warnings));
        }
        System.out.printf("%nfield accuracy: %d/%d (%.0f%%), documents flagged by the sum check: %d/%d%n",
                okFields, allFields, 100.0 * okFields / Math.max(allFields, 1), flagged, truths.size());
    }

    private static Result post(HttpClient client, ObjectMapper mapper, String url, Path file) throws Exception {
        String boundary = UUID.randomUUID().toString().replace("-", "");
        String name = file.getFileName().toString();
        String type = name.endsWith(".pdf") ? "application/pdf" : name.endsWith(".png") ? "image/png" : "image/jpeg";
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        body.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"" + name + "\"\r\n"
                + "Content-Type: " + type + "\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        body.write(Files.readAllBytes(file));
        body.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .timeout(Duration.ofMinutes(15))
                .POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray())).build();
        HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() != 200) {
            throw new IllegalStateException("HTTP " + response.statusCode() + " " + new String(response.body(), StandardCharsets.UTF_8));
        }
        return mapper.readValue(response.body(), Argument.of(Result.class));
    }

    private static String norm(String s) {
        StringBuilder sb = new StringBuilder();
        if (s != null) {
            for (char c : s.toLowerCase().toCharArray()) {
                if (Character.isLetterOrDigit(c)) {
                    sb.append(c);
                }
            }
        }
        return sb.toString();
    }

    private static boolean close(Double a, Double b) {
        return a != null && b != null && Math.abs(a - b) < 0.011;
    }

    /** Returns the number of checked fields and adds the names of the wrong ones to {@code wrong}. */
    private static int score(Truth truth, Invoice inv, List<String> wrong) {
        int checks = 0;
        checks++;
        String vendor = norm(inv.vendor());
        if (vendor.isEmpty() || !(norm(truth.vendor()).contains(vendor) || vendor.contains(norm(truth.vendor())))) {
            wrong.add("vendor");
        }
        checks++;
        if (!truth.date().equals(inv.date())) {
            wrong.add("date");
        }
        checks++;
        if (inv.currency() == null || !inv.currency().equalsIgnoreCase(truth.currency())) {
            wrong.add("currency");
        }
        checks++;
        if (!close(inv.total(), truth.total())) {
            wrong.add("total");
        }
        checks++;
        if (!close(inv.vat(), truth.vat())) {
            wrong.add("vat");
        }
        checks++;
        if ((inv.items() == null ? 0 : inv.items().size()) != truth.items()) {
            wrong.add("items");
        }
        if (truth.invoiceNumber() != null) {
            checks++;
            if (!norm(truth.invoiceNumber()).equals(norm(inv.invoiceNumber()))) {
                wrong.add("number");
            }
        }
        return checks;
    }
}
