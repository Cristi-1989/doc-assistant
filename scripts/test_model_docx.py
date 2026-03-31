import os
from google import genai
from docx import Document

api_key = os.environ.get("GEMINI_API_KEY")
if not api_key:
    raise ValueError("GEMINI_API_KEY environment variable is not set")

client = genai.Client(api_key=api_key)

# Extract raw text from docx
doc = Document("model_contract.docx")
text = "\n".join([paragraph.text for paragraph in doc.paragraphs])

# model = genai.GenerativeModel("gemini-flash-lite-latest")

prompt = f"""
Extract all fields from this Romanian document.
Return fields as list of key: value.

Document content:
{text}
"""

response = client.models.generate_content(
    model="gemini-flash-lite-latest",
    contents=prompt
)

print(response.text)
