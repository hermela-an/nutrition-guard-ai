import os
import pickle
import numpy as np
import faiss
from typing import List, Any, Optional
from src.embedding import EmbeddingPipeline

class FaissVectorStore:
    def __init__(self, persist_dir: str, data_dir: str = "data", embedding_model: str = "all-MiniLM-L6-v2"):
        self.persist_dir = persist_dir
        self.data_dir = data_dir
        
        # Pass both directories to the pipeline so it can check the manifest
        self.embedding_pipeline = EmbeddingPipeline(
            data_dir=data_dir,
            persist_dir=persist_dir,
            model_name=embedding_model
        )
        
        self.index: Optional[faiss.Index] = None
        self.metadata: List[dict] = []

    def initialize(self):
        """
        Smart initialization: Checks the manifest. 
        If files haven't changed, it loads from disk. 
        If files changed, it rebuilds and saves.
        """
        if self.embedding_pipeline.is_rebuild_needed:
            print("🔄 [REBUILD] Changes detected. Building new FAISS index...")
            # The pipeline already loaded the documents during __init__
            success = self.build_from_documents(self.embedding_pipeline.documents)
            if success:
                # CRITICAL: Only save the manifest AFTER the index is successfully saved to disk
                self.embedding_pipeline.save_manifest_if_updated()
            else:
                print("⚠️ [WARNING] Failed to build index. Loading old index if available.")
                self.load()
        else:
            print("✅ [LOAD] No changes detected. Loading existing FAISS index from disk...")
            self.load()

    def build_from_documents(self, documents: List[Any]) -> bool:
        os.makedirs(self.persist_dir, exist_ok=True)
        
        # Use the pipeline to chunk and embed
        chunks = self.embedding_pipeline.chunk_documents(documents)
        if not chunks:
            return False
            
        embeddings = self.embedding_pipeline.embed_chunks(chunks)
        dimension = embeddings.shape[1]
        
        # Build FAISS index
        self.index = faiss.IndexFlatL2(dimension)
        self.index.add(embeddings.astype(np.float32))
        
        # Save metadata
        self.metadata = [
            {"text": chunk.page_content, "source": getattr(chunk, "metadata", {})}
            for chunk in chunks
        ]
        
        self._save()
        return True

    def _save(self):
        os.makedirs(self.persist_dir, exist_ok=True)
        if self.index is None:
            raise RuntimeError("Index not built yet")
            
        # Save FAISS index
        faiss.write_index(self.index, os.path.join(self.persist_dir, "faiss.index"))
        
        # Save metadata
        with open(os.path.join(self.persist_dir, "metadata.pkl"), "wb") as f:
            pickle.dump(self.metadata, f)

    def load(self):
        faiss_path = os.path.join(self.persist_dir, "faiss.index")
        meta_path = os.path.join(self.persist_dir, "metadata.pkl")
        
        if os.path.exists(faiss_path) and os.path.exists(meta_path):
            self.index = faiss.read_index(faiss_path)
            with open(meta_path, "rb") as f:
                self.metadata = pickle.load(f)
            print(f"✅ Successfully loaded index with {len(self.metadata)} chunks.")
        else:
            print("⚠️ No existing index found on disk. A rebuild is required.")

    def query(self, query: str, top_k: int = 5) -> List[dict]:
        if self.index is None:
            return []
            
        # Create a dummy object to pass through the embedder (keeping your original logic)
        dummy_chunk = type("Chunk", (), {"page_content": query, "metadata": {}})()
        
        query_emb = self.embedding_pipeline.embed_chunks([dummy_chunk])
        distances, indices = self.index.search(query_emb.astype(np.float32), top_k)
        
        results = []
        for i, idx in enumerate(indices[0]):
            if idx < len(self.metadata):
                results.append(
                    {
                        "metadata": self.metadata[idx],
                        "distance": float(distances[0][i]),
                    }
                )
        return results