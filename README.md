# Invoice extractor: Micronaut + LangChain4j + llama.cpp

A small Micronaut service that reads invoices (PDF) and receipts (photos) with a **local** model
and returns typed JSON. Nothing leaves the machine, which is the point: invoices are private data.

```
                     POST /extract (multipart file)
 curl / script  ───────────────────────────────────►  Micronaut app (8080)
                                                         │  .pdf  → PDFBox text → LangChain4j @AiService → Invoice record
                                                         │  .png/.jpg → image → ChatModel (vision, JSON mode) → Invoice record
                                                         │  Verifier: line items must add up to the total
                                                         ▼
                                             llama-server (llama.cpp, OpenAI-compatible API)
                                             Gemma 3 4B, text + vision
```

## Run it

Needs Docker. The first start downloads the model (about 3 GB) into a Docker volume.

```powershell
# with an NVIDIA GPU
docker compose -f compose.yaml -f compose.gpu.yaml up --build

# CPU only (works, but slow)
docker compose up --build
```

llama-server is on http://localhost:18081 (it has a small web UI), the service on http://localhost:8080.

```powershell
curl.exe -F "file=@samples/invoices/invoice-01-nordlicht.pdf" http://localhost:8080/extract
```

## Sample documents

`GenerateSamples` (Java, PDFBox and Java2D) writes 6 fake invoices (PDF: German, English, French, US,
two VAT rates, a discount line) and 6 fake receipts (PNG: rotated, noisy, on a table) to `samples/`.
The documents are generated, so `samples/ground-truth.json` holds the exact correct values.
`Evaluate` sends every sample to the running service and scores vendor, date, currency, total, VAT
and item count against it. Both run from the same jar, through the `tools` profile:

```powershell
docker compose --profile tools run --rm generate            # regenerate the samples
docker compose --profile tools run --rm evaluate            # needs the stack to be up
docker compose --profile tools run --rm evaluate receipt    # only files whose name contains "receipt"
```

For realistic receipt photos see [PROMPT-receipt-photo.md](PROMPT-receipt-photo.md) (ChatGPT image prompt).

## What is where

| File | Job |
|---|---|
| `InvoiceExtractor` | `@AiService` interface, text in, `Invoice` record out |
| `ImageExtractor` | builds a vision request by hand (image + JSON mode) |
| `Verifier` | no model: checks that items, subtotal, VAT and total agree |
| `application.properties` | points the OpenAI module at llama-server |
| `example.tools.GenerateSamples`, `Evaluate` | sample documents with ground truth, and the scoring run |

## Test run (2026-11-22, RTX 3060 Laptop 6 GB, Gemma 3 4B Q4, llama.cpp server-cuda)

Run with `docker compose --profile tools run --rm evaluate` on the Java-generated samples plus one
ChatGPT-made receipt photo (`samples/photos`). One run per document, so a first impression only.

| | Result |
|---|---|
| 6 PDF invoices (text path) | 42 of 42 fields right, about 5 s each |
| 6 generated receipt images + 1 photo (vision path) | 29 of 42 fields right, 3 to 6 s each |
| Sum check (`Verifier`) | flagged 3 of the 6 receipts with wrong values, missed 3 |

What goes wrong, from looking at the answers:

- **VAT-inclusive prices.** On receipts the printed prices already contain the VAT. The model reads
  the items correctly, then adds the VAT on top: the bakery photo came back with total 15.52 instead of
  12.80 (items 12.80, VAT 2.72). The `Verifier` accepts it, because subtotal + VAT = total. The prompt
  never says that receipt prices include VAT, and the check has no rule against it.
- **Dates.** One receipt came back as `02.11.2026` instead of ISO, one as `2026-12-12` instead of
  `2026-11-12`. A date format check in the `Verifier` would catch the first kind, not the second.
- **Names and numbers.** "Café Nordstrom" for "Café Nordstern", "Lauenbrezel" for "Laugenbrezel".
- **Messy receipts.** Missing totals, wrong quantity lines, an item sum of 95.97 on a 22.41 receipt:
  these the sum check does catch.

Ideas that follow from it (not done): tell the prompt that receipt prices include VAT and that the
total is the printed total line; check dates and VAT rates in the `Verifier`; on a flagged document
send the model its answer plus the complaint and let it try again, then re-run the evaluation.
