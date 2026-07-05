import os
from typing import Dict, List, Tuple
from src.vectorstore import FaissVectorStore

class Retriever:
    def __init__(self, persist_dir: str = "faiss_store", data_dir: str = "data", embedding_model: str = "all-MiniLM-L6-v2"):
        # 1. FIX: Use explicit keyword arguments so 'data_dir' doesn't get confused with the model name
        self.vectorstore = FaissVectorStore(
            persist_dir=persist_dir, 
            data_dir=data_dir, 
            embedding_model=embedding_model
        )
        
        # 2. FIX: Removed the manual os.path.exists checks and load/build logic.
        # The api.py startup event now calls `retriever.vectorstore.initialize()`, 
        # which automatically handles checking the manifest, loading from disk, or rebuilding!

    def retrieve_context_and_sources(self, query: str, top_k: int = 5) -> Tuple[str, List[Dict]]:
        if self.vectorstore.index is None:
            return "", []
            
        results = self.vectorstore.query(query, top_k=top_k)
        texts = [r["metadata"].get("text", "") for r in results if r["metadata"]]
        context = "\n\n".join(texts)
        
        if not context:
            return "", []

        sources = []
        seen = set()
        for r in results:
            meta = r.get("metadata", {})
            src = meta.get("source", {})
            if isinstance(src, dict):
                doc = src.get("source", "Unknown")
                page = src.get("page_label", src.get("page", "N/A"))
            else:
                doc = str(src) if src else "Unknown"
                page = "N/A"
            key = (doc, str(page))
            if key not in seen:
                seen.add(key)
                sources.append({"document": doc, "page": page})
                
        return context, sources