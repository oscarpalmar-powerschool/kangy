from fastapi import FastAPI

app = FastAPI(title="Agents API")


@app.get("/health")
def health():
    return {"status": "ok"}
