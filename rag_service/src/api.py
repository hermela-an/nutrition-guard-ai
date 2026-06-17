from fastapi import FastAPI
from pydantic import BaseModel
from typing import Optional, List, Dict
import uvicorn

from src.retriever import Retriever
from src.llm_service import LLMService

app = FastAPI(title="NutritionGuard RAG API")

retriever = Retriever()
llm_service = LLMService()

class QueryRequest(BaseModel):
    query: str

class QueryResponse(BaseModel):
    context: str
    sources: List[Dict]

@app.post("/query", response_model=QueryResponse)
def query_endpoint(req: QueryRequest):
    # 1. Retrieve context and sources from FAISS
    context, sources = retriever.retrieve_context_and_sources(req.query, top_k=3)
    
    # 2. Summarize context with LLM
    if context:
        summary = llm_service.summarize(req.query, context)
    else:
        summary = "No vector index is available or no relevant documents found."
        
    # The Java backend expects a 'context' field which acts as the summary answer
    return {"context": summary, "sources": sources}

if __name__ == "__main__":
    uvicorn.run("src.api:app", host="0.0.0.0", port=5000, reload=True)
