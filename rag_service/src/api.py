from fastapi import FastAPI, BackgroundTasks
from pydantic import BaseModel
from typing import List, Dict
import uvicorn

from src.retriever import Retriever
from src.llm_service import LLMService

app = FastAPI(title="NutritionGuard RAG API")

# Initialize services
retriever = Retriever()
llm_service = LLMService()

# --- Pydantic Models ---
class QueryRequest(BaseModel):
    query: str

class QueryResponse(BaseModel):
    context: str
    sources: List[Dict]

# --- Startup Event ---
@app.on_event("startup")
def startup_event():
    """
    Runs exactly once when the server starts.
    Checks manifest.py and either loads the existing FAISS index 
    or rebuilds it if files have changed.
    """
    print("🚀 Starting NutritionGuard AI RAG Service...")
    print("🔍 Checking manifest for file changes...")
    
    # Call the new initialize method which handles the manifest logic
    retriever.vectorstore.initialize()
    
    print("✅ Vector Store initialized and ready to accept queries!")

# --- API Endpoints ---

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

# --- Background Reindexing ---

def background_reindex():
    """
    Background task to rebuild the index. 
    It uses the new initialize() method which checks the manifest.
    """
    print(" Background reindexing triggered...")
    # The initialize() method will automatically check if files changed 
    # and rebuild the index + update the manifest if needed.
    retriever.vectorstore.initialize()
    print("✅ Background reindexing complete.")

@app.post("/reindex")
def reindex_endpoint(background_tasks: BackgroundTasks):
    background_tasks.add_task(background_reindex)
    return {"status": "success", "message": "Indexing process started in background."}

if __name__ == "__main__":
    uvicorn.run("src.api:app", host="0.0.0.0", port=5000, reload=True)