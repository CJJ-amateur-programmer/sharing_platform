import os
import sqlite3
import shutil
from datetime import datetime

# --- Configuration ---
DB_NAME = "./db/platform.db"
RAW_FILES_DIR = "./raw_files"
FILES_DIR = "./files"
CHUNK_SIZE = 65536  # 64KB, for reading files in chunks for hashing

def restore_files_from_db(dest_dir, src_dir, db_path):
    """
    从数据库中读取文件信息，并将文件从按哈希命名的目录
    移动回原始文件名的目录
    """
    # 确保目标目录存在
    if not os.path.exists(dest_dir):
        os.makedirs(dest_dir)
        print(f"Created destination directory '{dest_dir}'.")

    print(f"Restoring files from '{src_dir}' to '{dest_dir}'...")

    with sqlite3.connect(db_path) as conn:
        cursor = conn.cursor()
        
        # 获取数据库中的所有文件记录
        try: 
            cursor.execute("SELECT FileHash, FileName, FileType FROM FILE")
            files = cursor.fetchall()
            
            for file_hash, file_name, file_type in files:
                # 构建源文件路径（按哈希值命名）
                src_file_path = os.path.join(src_dir, file_hash)
                # 构建目标文件路径（使用原始文件名）
                dest_file_path = os.path.join(dest_dir, f"{file_name}.{file_type}")
                
                try:
                    # 检查源文件是否存在
                    if os.path.exists(src_file_path):
                        # 如果目标文件已存在，先删除
                        if os.path.exists(dest_file_path):
                            os.remove(dest_file_path)
                        
                        # 移动文件
                        os.rename(src_file_path, dest_file_path)
                        print(f"  - Restored '{file_name}.{file_type}'")
                    else:
                        print(f"  - Warning: Source file '{file_hash}' not found in '{src_dir}'")
                except Exception as e:
                    print(f"Error restoring file {file_hash}: {e}")
        except Exception as e:
            print(e)
        
        cursor.execute("""
            DROP TABLE IF EXISTS FILE
        """)
        cursor.execute("""
            CREATE TABLE FILE (
                FileHash TEXT PRIMARY KEY,
                FileName TEXT UNIQUE NOT NULL,
                FileType TEXT NOT NULL,
                FileSize INTEGER NOT NULL,
                Time INTEGER NOT NULL,
                Content TEXT
            )
        """)
        conn.commit()

    print("File restoration complete.")


if __name__ == "__main__":
    # 确认是否恢复文件
    if input("Input 'y' to confirm file restoration: ").lower() == "y":
        restore_files_from_db(RAW_FILES_DIR, FILES_DIR, DB_NAME)
