import os
import base64
from google import genai

api_key = os.environ.get("GEMINI_API_KEY")
if not api_key:
    raise ValueError("GEMINI_API_KEY environment variable is not set")

client = genai.Client(api_key=api_key)

image_path = "model_contract.jpg"
with open(image_path, "rb") as f:
    image_data = base64.b64encode(f.read()).decode("utf-8")

prompt = """
Extract most relevant fields from this document, in the form of key: value list.
"""

response = client.models.generate_content(
    model="gemini-flash-lite-latest",
    contents=[
        {
            "parts": [
                {"inline_data": {"mime_type": "image/jpeg", "data": image_data}},
                {"text": prompt},
            ]
        }
    ],
)

print(response.text)