from aiohttp.helpers import DEBUG
from pathlib import Path
from typing import List, Any
from langchain_community.document_loaders import TextLoader, CSVLoader
from langchain_core.documents import Document
import pymupdf4llm
import fitz 
from langchain_community.document_loaders import Docx2txtLoader
from langchain_community.document_loaders.excel import UnstructuredExcelLoader
from langchain_community.document_loaders import JSONLoader
from langchain_community.document_loaders import UnstructuredHTMLLoader
from src.ocr_loader import _extract_text_from_image


def load_pdf_semantic(pdf_path: str) -> List[Document]:
    """
    Extracts text, tables, and images semantically from a PDF.
    Returns one LangChain Document per page (not one giant document for the whole PDF).
    """
    documents = []
    print(f"[DEBUG] Starting semantic extraction for: {pdf_path}")
    
    # Open the PDF once with fitz to be used by both libraries
    doc = fitz.open(pdf_path)
    total_pages = len(doc)
    
    # 1. Extract Text and Tables using pymupdf4llm (page by page)
    try:
        # page_chunks=True returns a list of dictionaries, one for each page
        page_chunks = pymupdf4llm.to_markdown(doc, page_chunks=True)
    except Exception as e:
        print(f"[ERROR] pymupdf4llm failed for {pdf_path}: {e}")
        doc.close()
        return []

    # 2. Iterate through pages to extract and OCR images
    for i, chunk in enumerate(page_chunks):
        # Get the page number from metadata (fallback to loop index 'i' just in case)
        page_num = chunk["metadata"].get("page", i)
        page = doc[page_num]
        
        # A. Get the markdown content (includes standard text and structured tables)
        markdown_content = chunk.get("text", "")
        
        # B. Extract and OCR embedded images from this specific page
        image_texts = []
        image_list = page.get_images(full=True)
        
        for img_index, img in enumerate(image_list):
            xref = img[0]
            try:
                # Extract raw image bytes from the PDF structure
                base_image = doc.extract_image(xref)
                image_bytes = base_image["image"]

                # ADD THIS PRINT STATEMENT TO SEE PROGRESS:
                print(f"[DEBUG] Running OCR on image {img_index + 1} of page {page_num + 1}...")
                
                # Run OCR on the raw image bytes using your existing EasyOCR function
                ocr_text = _extract_text_from_image(image_bytes)
                if ocr_text.strip():
                    image_texts.append(f"[Image {img_index + 1} Content]: {ocr_text.strip()}")
            except Exception as e:
                print(f"[WARN] Failed to process image xref {xref} on page {page_num + 1}: {e}")
                
        # C. Combine markdown (text+tables) and image OCR text
        full_content = markdown_content
        if image_texts:
            full_content += "\n\n---\n**Extracted Image Contents:**\n" + "\n".join(image_texts)
            
        # D. Create LangChain Document for THIS SPECIFIC PAGE
        documents.append(Document(
            page_content=full_content,
            metadata={
                "source": pdf_path,
                "file_name": Path(pdf_path).name,
                "file_type": "pdf",
                "page": page_num + 1,
                "total_pages": total_pages
            }
        ))
        
    doc.close()
    print(f"[DEBUG] Semantic extraction complete. Loaded {len(documents)} pages from {pdf_path}")
    return documents


def load_all_documents(data_dir: str) -> List[Any]:
    data_path = Path(data_dir).resolve()
    print(f"[DEBUG]: Data directory: {data_path}")
    documents = []

    # 1. PDF Files (Semantic Extraction: Text + Tables + Images per page)
    pdf_files = list(data_path.glob("**/*.pdf"))
    print(f"[DEBUG] Found {len(pdf_files)} PDF files: {[str(f) for f in pdf_files]}")

    for pdf_file in pdf_files:
        print(f"[DEBUG] Loading PDF: {pdf_file}")
        try:
            # Use the new semantic loader instead of the basic one
            loaded = load_pdf_semantic(str(pdf_file))
            documents.extend(loaded)
        except Exception as e:
            print(f"[ERROR] Failed to load PDF {pdf_file}: {e}")

    # 2. TXT Files
    text_files = list(data_path.glob("**/*.txt"))
    print(f"[DEBUG] TXT files found: {len(text_files)}")
    
    for txt_file in text_files:
        print(f"[DEBUG] LOADING TXT {txt_file} ")
        try:
            loader = TextLoader(str(txt_file))
            loaded = loader.load()
            documents.extend(loaded)
        except Exception as e:
            print(f"[ERROR]: Failed to load TXT {txt_file}: {e}")

    # 3. EXCEL Files
    xlsx_files = list(data_path.glob("**/*.xlsx"))
    print(f"[DEBUG] EXCEL files found: {len(xlsx_files)}")
    
    for xlsx_file in xlsx_files:
        print(f"[DEBUG] LOADING EXCEL {xlsx_file}")
        try:
            loader = UnstructuredExcelLoader(str(xlsx_file))
            loaded = loader.load()
            documents.extend(loaded)
        except Exception as e:
            print(f"[ERROR]: Failed to load EXCEL {xlsx_file}: {e}")

    # 4. CSV Files
    csv_files = list(data_path.glob("**/*.csv"))
    print(f"[DEBUG] Found {len(csv_files)} CSV files")
    
    for csv_file in csv_files:
        print(f"[DEBUG] Loading CSV: {csv_file}")
        try:
            loader = CSVLoader(str(csv_file))
            loaded = loader.load()
            documents.extend(loaded)
        except Exception as e:
            print(f"[ERROR] Failed to load CSV {csv_file}: {e}")
            
    # 5. WORD Files
    docx_files = list(data_path.glob("**/*.docx"))
    print(f"[DEBUG] Found {len(docx_files)} Word files")
    
    for docx_file in docx_files:
        print(f"[DEBUG] Loading Word: {docx_file}")
        try:
            loader = Docx2txtLoader(str(docx_file))
            loaded = loader.load()
            documents.extend(loaded)
        except Exception as e:
            print(f"[ERROR] Failed to load Word {docx_file}: {e}")

    # 6. JSON Files
    json_files = list(data_path.glob("**/*.json"))
    print(f"[DEBUG] Found {len(json_files)} JSON files")
    
    for json_file in json_files:
        print(f"[DEBUG] Loading JSON: {json_file}")
        try:
            loader = JSONLoader(str(json_file), jq_schema=".")
            loaded = loader.load()
            documents.extend(loaded)
        except Exception as e:
            print(f"[ERROR] Failed to load JSON {json_file}: {e}")

    print(f"[DEBUG] Total loaded documents: {len(documents)}")
    return documents

if __name__ == "__main__":
    docs = load_all_documents("data")
    print(f"Loaded {len(docs)} documents.")
    print("Example document:", docs[0] if docs else None)