import os
from typing import Dict, List, Tuple
from src.vectorstore import FaissVectorStore

class Retriever:
    def __init__(self, persist_dir: str = "faiss_store", embedding_model: str = "all-MiniLM-L6-v2"):
        self.vectorstore = FaissVectorStore(persist_dir, embedding_model)
        
        faiss_path = os.path.join(persist_dir, "faiss.index")
        meta_path = os.path.join(persist_dir, "metadata.pkl")
        
        if not (os.path.exists(faiss_path) and os.path.exists(meta_path)):
            from src.data_loader import load_all_documents
            docs = load_all_documents("data")
            if not self.vectorstore.build_from_documents(docs):
                print("[WARN] Retriever initialized without a vector index because no documents were available.")
        else:
            self.vectorstore.load()

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
