import numpy as np
from typing import List, Any, Optional
from langchain_text_splitters import RecursiveCharacterTextSplitter
from sentence_transformers import SentenceTransformer

from src.data_loader import load_all_documents
from src.manifest import needs_rebuild, save_manifest

class EmbeddingPipeline:
    def __init__(
        self,
        data_dir: str = "data",
        persist_dir: str = "faiss_store",
        model_name: str = "all-MiniLM-L6-v2",
        chunk_size: int = 1000,
        chunk_overlap: int = 200,
    ):
        self.data_dir = data_dir
        self.persist_dir = persist_dir
        self.model_name = model_name
        self.chunk_size = chunk_size
        self.chunk_overlap = chunk_overlap
        
        # 1. Check if files have changed since the last run
        self.is_rebuild_needed = needs_rebuild(self.persist_dir, self.data_dir)
        
        if not self.is_rebuild_needed:
            print("✅ [CACHE HIT] No files changed. Skipping document loading and embedding!")
            self.documents = []
        else:
            print("🔄 [CACHE MISS] Changes detected. Loading and processing documents...")
            self.documents = load_all_documents(self.data_dir)
            print(f"[DEBUG] Loaded {len(self.documents)} documents. model: {model_name}")
            
        # Initialize the model regardless (it's fast to load)
        self.model = SentenceTransformer(model_name)

    def chunk_documents(self, documents: Optional[List[Any]] = None) -> List[Any]:
        # If we skipped loading, documents will be empty
        if documents is None:
            documents = self.documents
            
        if not documents:
            print("[DEBUG] No documents to chunk (Cache hit or empty folder).")
            return []
            
        splitter = RecursiveCharacterTextSplitter(
            chunk_size=self.chunk_size,
            chunk_overlap=self.chunk_overlap,
            length_function=len,
            separators=["\n\n", "\n", ".", " "],
        )
        chunks = splitter.split_documents(documents)
        print(f"[DEBUG] Split {len(chunks)} chunks from {len(documents)} documents")
        return chunks

    def embed_chunks(self, chunks: List[Any]) -> np.ndarray:
        if not chunks:
            print("[INFO] No chunks to embed.")
            return np.array([])
            
        text = [chunk.page_content for chunk in chunks]
        print(f"[INFO] Generating embeddings for {len(text)} chunks")
        embeddings = self.model.encode(text, show_progress_bar=True)
        if not isinstance(embeddings, np.ndarray):
            embeddings = np.array(embeddings)
        print(f"[DEBUG] Embeddings shape: {embeddings.shape}")
        return embeddings

    def save_manifest_if_updated(self):
        """
        IMPORTANT: Call this method in your vectorstore.py AFTER you successfully 
        save the new FAISS index to disk.
        """
        if self.is_rebuild_needed:
            save_manifest(self.persist_dir, self.data_dir)
            print("✅ [CACHE UPDATE] New manifest saved successfully.")

if __name__ == "__main__":
    emb_pipe = EmbeddingPipeline()
    chunks = emb_pipe.chunk_documents()
    embeddings = emb_pipe.embed_chunks(chunks)
    
    # If you are testing this file directly, save the manifest at the end
    emb_pipe.save_manifest_if_updated()
    
    print(f"[DEBUG] Final Embeddings shape: {embeddings.shape}")