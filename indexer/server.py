import logging
import argparse
from typing import List, Dict, Any, Union
import json
from flask import Flask, request, jsonify

# Configure logging
logging.basicConfig(level=logging.INFO, format='%(asctime)s - %(levelname)s - %(message)s')
logger = logging.getLogger(__name__)

app = Flask(__name__)
model = None

def load_model(model_name_or_path: str):
    global model
    try:
        from sentence_transformers import SentenceTransformer
        logger.info(f"Loading SentenceTransformer model: {model_name_or_path}...")
        model = SentenceTransformer(model_name_or_path)
        logger.info("Model loaded successfully.")
    except ImportError:
        logger.error("sentence-transformers not installed. Please run: pip install sentence-transformers")
        sys.exit(1)
    except Exception as e:
        logger.error(f"Failed to load model: {e}")
        sys.exit(1)

@app.route('/v1/embeddings', methods=['POST'])
def embeddings():
    if not model:
        return jsonify({"error": "Model not loaded"}), 500

    data = request.json
    if not data or 'input' not in data:
        return jsonify({"error": "Missing 'input' field"}), 400

    text_input = data['input']
    if isinstance(text_input, str):
        text_input = [text_input]

    try:
        # Generate embeddings
        # convert_to_numpy=False returns valid python lists if using older versions, 
        # but modern sentence-transformers returns tensors or ndarrays.
        # tolist() ensures JSON serializability.
        embeddings = model.encode(text_input, convert_to_numpy=True).tolist()
        
        # Format response to match OpenAI API
        data_response = []
        for i, emb in enumerate(embeddings):
            data_response.append({
                "object": "embedding",
                "index": i,
                "embedding": emb
            })
            
        return jsonify({
            "object": "list",
            "data": data_response,
            "model": "local-sentence-transformer",
            "usage": {
                "prompt_tokens": 0,
                "total_tokens": 0
            }
        })
    except Exception as e:
        logger.error(f"Error generating embedding: {e}")
        return jsonify({"error": str(e)}), 500

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Local Embedding Server")
    parser.add_argument("--port", type=int, default=5001, help="Port to run server on")
    parser.add_argument("--model", type=str, default="all-MiniLM-L6-v2", help="SentenceTransformer model name")
    args = parser.parse_args()
    
    import sys
    load_model(args.model)
    
    logger.info(f"Starting server on port {args.port}...")
    app.run(host="0.0.0.0", port=args.port)
