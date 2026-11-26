# Ollama with the model already inside. One image for two jobs:
#   - compose.yaml builds and runs it as the "llm" service
#   - Micronaut test-resources starts it for mn:run and tests. The ollama test resource looks for the
#     image langchain4j/ollama-<model-name>:latest, and there is none for Gemma 3 on Docker Hub, so we
#     build it under exactly that name (compose.yaml does it, or: docker build -t langchain4j/ollama-gemma3 ollama).
FROM ollama/ollama:latest
RUN ollama serve & pid=$! \
    && until ollama list >/dev/null 2>&1; do sleep 1; done \
    && ollama pull gemma3 \
    && kill $pid && wait $pid || true
