import sqlite3
import os
import logging
from typing import Optional, Dict, Any
import json
import contextlib

logger = logging.getLogger("WorkerDeduplicationStore")

class WorkerDeduplicationStore:
    """
    Durable deduplication store using SQLite for worker command idempotency.
    
    Location: Defaults to `WORKER_DB_PATH` or `~/.soa_optimizer_worker/dedup.db`.
    Lifecycle: Persists across worker restarts.
    Concurrency: Uses SQLite WAL (Write-Ahead Logging) mode with busy timeout for concurrency safety.
    
    States:
      - PROCESSING: Command picked up and currently executing.
      - PUBLISHED: Result dispatched to Kafka topic, pending offset commit.
      - COMPLETED: Result dispatched and offset committed.
    """
    def __init__(self, db_path: Optional[str] = None):
        if not db_path:
            db_path = os.environ.get("WORKER_DB_PATH")
        if not db_path:
            base_dir = os.path.join(os.path.expanduser("~"), ".soa_optimizer_worker")
            os.makedirs(base_dir, exist_ok=True)
            db_path = os.path.join(base_dir, "worker_dedup.db")
        else:
            parent = os.path.dirname(os.path.abspath(db_path))
            if parent:
                os.makedirs(parent, exist_ok=True)

        self.db_path = db_path
        self._init_db()

    @contextlib.contextmanager
    def _connection(self):
        conn = sqlite3.connect(self.db_path, timeout=10.0)
        conn.execute("PRAGMA journal_mode = WAL")
        conn.execute("PRAGMA synchronous = NORMAL")
        conn.row_factory = sqlite3.Row
        try:
            yield conn
        finally:
            conn.close()

    def _init_db(self):
        with self._connection() as conn:
            conn.execute("""
                CREATE TABLE IF NOT EXISTS processed_commands (
                    command_id TEXT PRIMARY KEY,
                    job_id TEXT NOT NULL,
                    attempt_id TEXT NOT NULL,
                    status TEXT NOT NULL,
                    result_id TEXT,
                    result_json TEXT,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
            """)
            conn.commit()

    def get_command_status(self, command_id: str) -> Optional[Dict[str, Any]]:
        with self._connection() as conn:
            cursor = conn.execute(
                "SELECT command_id, job_id, attempt_id, status, result_id, result_json FROM processed_commands WHERE command_id = ?",
                (command_id,)
            )
            row = cursor.fetchone()
            if row:
                return {
                    "command_id": row["command_id"],
                    "job_id": row["job_id"],
                    "attempt_id": row["attempt_id"],
                    "status": row["status"],
                    "result_id": row["result_id"],
                    "result_json": json.loads(row["result_json"]) if row["result_json"] else None
                }
            return None

    def is_already_completed(self, command_id: str) -> bool:
        status_info = self.get_command_status(command_id)
        return status_info is not None and status_info["status"] in ("COMPLETED", "PUBLISHED")

    def record_published(self, command_id: str, job_id: str, attempt_id: str, result_id: str, result: Dict[str, Any]):
        result_json = json.dumps(result)
        with self._connection() as conn:
            conn.execute("""
                INSERT INTO processed_commands (command_id, job_id, attempt_id, status, result_id, result_json, updated_at)
                VALUES (?, ?, ?, 'PUBLISHED', ?, ?, CURRENT_TIMESTAMP)
                ON CONFLICT(command_id) DO UPDATE SET
                    status = 'PUBLISHED',
                    result_id = excluded.result_id,
                    result_json = excluded.result_json,
                    updated_at = CURRENT_TIMESTAMP
            """, (command_id, job_id, attempt_id, result_id, result_json))
            conn.commit()

    def record_completed(self, command_id: str):
        with self._connection() as conn:
            conn.execute("""
                UPDATE processed_commands 
                SET status = 'COMPLETED', updated_at = CURRENT_TIMESTAMP
                WHERE command_id = ?
            """, (command_id,))
            conn.commit()

    def close(self):
        pass

