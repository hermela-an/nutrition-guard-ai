from dotenv import load_dotenv
from langchain_groq import ChatGroq

load_dotenv()

class LLMService:
    def __init__(self, llm_model: str = "llama-3.3-70b-versatile"):
        self.llm = ChatGroq(model=llm_model)
        print(f"[INFO] Groq LLM initialized: {llm_model}")

    def summarize(self, query: str, context: str) -> str:
        if not context:
            return "No relevant documents found."
            
        prompt = f"""You are a precise and highly accurate assistant.
Instead of guessing, you must rely strictly on the provided Context to answer the Query.
Write a perfectly formatted, highly accurate summary based ONLY on the private context.
If the context does not contain the answer, say "I don't have enough information to answer that."

Query: '{query}'

Context:
{context}

Summary:"""
        response = self.llm.invoke([prompt])
        
        content = response.content
        if isinstance(content, list):
            summary = " ".join(str(c) for c in content)
        else:
            summary = str(content)
            
        return summary
