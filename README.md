# micronaut-invoice-extractor

Demo for a live-coding session on 2026-11-26: a Micronaut service that reads invoices and receipts
with a local model (Ollama) through LangChain4j and returns typed JSON.

This branch is the scaffold: build, Docker Compose stack, configuration, sample documents and the
upload page are there, the Java code is not. The finished proof of concept is on the `POC` branch.

## What is here

| | |
|---|---|
| `pom.xml` | Micronaut 5.2.7, Java 25, Netty, `micronaut-langchain4j-ollama` 2.4.0, PDFBox |
| `compose.yaml`, `compose.gpu.yaml` | Ollama (Gemma 3 4B, text + vision) and the app; GPU overlay |
| `Dockerfile` | builds the jar, runs it |
| `ollama/Dockerfile` | Ollama with Gemma 3 baked in; used by compose and by test-resources |
| `application.properties` | points the Ollama module at the model, serves the upload page |
| `src/main/resources/static/index.html` | upload page, posts a file to `POST /extract` |
| `samples/` | 6 invoice PDFs, 6 receipt images, 1 receipt photo, `ground-truth.json` with the correct values |
| `Application.java` | the only Java class |

## To build in the session

- `Invoice` record (vendor, date, currency, items, VAT, total)
- `InvoiceExtractor`: LangChain4j `@AiService`, text in, `Invoice` out (PDF path, text from PDFBox)
- `ImageExtractor`: vision request with image and JSON mode (receipt path)
- `ExtractionController`: `POST /extract`, multipart file, picks the path by file type
- `Verifier`: items, subtotal, VAT and total must agree

## Run

Needs Docker. The first start downloads the model (about 3 GB) into a Docker volume.

```powershell
# with an NVIDIA GPU
docker compose -f compose.yaml -f compose.gpu.yaml up --build

# CPU only (slow)
docker compose up --build
```

Ollama is on http://localhost:11434, the service on http://localhost:8080.

To develop with a local Maven instead of the `app` container: `mvn mn:run` starts an Ollama container
by itself through Micronaut test-resources (`micronaut-langchain4j-ollama-testresource`), no URL to
configure. The test resource looks for the image `langchain4j/ollama-<model-name>`; none exists for
Gemma 3, so build it once under that name (Compose does that too):

```powershell
docker build -t langchain4j/ollama-gemma3 ollama
mvn mn:run
```

Sample documents: see `samples/ground-truth.json`; [PROMPT-receipt-photo.md](PROMPT-receipt-photo.md)
is a ChatGPT image prompt for more realistic receipt photos.
