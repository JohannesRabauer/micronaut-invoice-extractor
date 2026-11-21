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

`python generator/generate_samples.py` writes 6 fake invoices (PDF: German, English, French, US,
two VAT rates, a discount line) and 6 fake receipts (PNG: rotated, noisy, on a table) to `samples/`.
The documents are generated, so `samples/ground-truth.json` holds the exact correct values.

```powershell
python scripts/evaluate.py          # sends all samples, scores vendor/date/currency/total/vat/item count
python scripts/evaluate.py receipt  # only the receipts
```

For realistic receipt photos see [PROMPT-receipt-photo.md](PROMPT-receipt-photo.md) (ChatGPT image prompt).

## What is where

| File | Job |
|---|---|
| `InvoiceExtractor` | `@AiService` interface, text in, `Invoice` record out |
| `ImageExtractor` | builds a vision request by hand (image + JSON mode) |
| `Verifier` | no model: checks that items, subtotal, VAT and total agree |
| `application.properties` | points the OpenAI module at llama-server |

## First test run (2026-11-21, RTX 3060 Laptop 6 GB, Gemma 3 4B Q4, llama.cpp server-cuda)

| | Result |
|---|---|
| 6 PDF invoices (text path) | 42 of 42 fields right, 13 to 15 s each once warm (the first two took about 2 minutes) |
| 6 receipt images (vision path) | 23 of 36 fields right, 7 to 25 s each |
| Sum check (`Verifier`) | flagged 6 of the 6 receipts that had wrong numbers, and none of the invoices |

On the receipts the model mixes up quantity lines, reads the total from the wrong row or misses
whole line items. The sum check catches exactly these, which is the useful part to show:
the model is good enough to do the typing, a plain Java check decides what a human has to look at.
The fields scored are vendor, date, currency, total, VAT, item count (and invoice number for invoices).
Only one run per document, no repeats, so treat the numbers as a first impression.
