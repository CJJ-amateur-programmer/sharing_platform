import os
import sqlite3
import hashlib
from datetime import datetime

# --- Configuration ---
DB_NAME = "./db/platform.db"
FILES_DIR = "./files"
RAW_FILES_DIR = "./raw_files"
CHUNK_SIZE = 65536  # 64KB, for reading files in chunks for hashing

def calculate_sha256(file_path):
    """
    Calculates the SHA256 hash of a file efficiently by reading it in chunks.
    Returns the hex digest of the hash.
    """
    sha256_hash = hashlib.sha256()
    with open(file_path, "rb") as f:
        # Read and update hash in chunks to handle large files
        while chunk := f.read(CHUNK_SIZE):
            sha256_hash.update(chunk)
    return sha256_hash.hexdigest()

def process_files_and_update_db(raw_dir, dest_dir, db_path):
    """
    Scans a raw_dir for files, calculates their hashes and metadata,
    and inserts or replaces the data in the database.
    """
    # Ensure the target raw_dir exists before trying to scan it.
    if not os.path.isdir(raw_dir):
        print(f"Error: raw_dir '{raw_dir}' not found. Please create it and add files.")
        # Create the raw_dir so the script can be run again after adding files.
        os.makedirs(raw_dir)
        print(f"Created raw_dir '{raw_dir}'.")
        return

    print(f"Scanning files in '{raw_dir}'...")
    
    with sqlite3.connect(db_path) as conn:
        cursor = conn.cursor()
        
        # os.scandir is more efficient than os.listdir for getting file metadata.
        for entry in os.scandir(raw_dir):
            # Process only files, not subdirectories.
            if entry.is_file():
                file_path = entry.path
                try:
                    # 1. Get file metadata
                    file_stat = entry.stat()
                    file_size = file_stat.st_size
                    # Convert timestamp to a standard ISO 8601 string format
                    last_modified = int(file_stat.st_mtime)
                    file_name = os.path.splitext(os.path.basename(file_path))[0]
                    # Get file extension, including the dot (e.g., '.txt')
                    file_type = os.path.splitext(file_path)[1][1:]

                    # 2. Calculate SHA256 hash
                    file_hash = calculate_sha256(file_path)
                    
                    print(f"  - Processing '{file_name}' (Hash: {file_hash[:10]}...)")

                    # 3. Update the database
                    # "INSERT OR REPLACE" will insert a new row, or if a row with the
                    # same primary key (FileHash) already exists, it will be replaced.
                    cursor.execute("""
                        INSERT OR REPLACE INTO FILE 
                        (FileHash, FileName, FileType, FileSize, Time)
                        VALUES (?, ?, ?, ?, ?)
                    """, (file_hash, file_name, file_type, file_size, last_modified))
                    
                    os.rename(file_path, os.path.join(dest_dir, file_hash))


                except IOError as e:
                    print(f"Error processing file {file_path}: {e}")
                except Exception as e:
                    print(f"An unexpected error occurred with file {file_path}: {e}")
        
        conn.commit()
    print("Database update complete.")


if __name__ == "__main__":
    
    # 1-3. Read files, calculate hashes, and update the database
    process_files_and_update_db(RAW_FILES_DIR, FILES_DIR, DB_NAME)

