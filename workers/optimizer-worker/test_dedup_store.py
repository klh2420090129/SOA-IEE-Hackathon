import os
import tempfile
import pytest
from dedup_store import WorkerDeduplicationStore

def test_dedup_store_lifecycle_and_persistence():
    with tempfile.TemporaryDirectory() as tmpdir:
        db_path = os.path.join(tmpdir, "test_dedup.db")
        
        # Instance 1: Store command and result
        store1 = WorkerDeduplicationStore(db_path=db_path)
        assert not store1.is_already_completed("cmd-1")
        
        result_payload = {"resultId": "res-1", "solverStatus": "FEASIBLE", "placements": []}
        store1.record_published("cmd-1", "job-1", "att-1", "res-1", result_payload)
        
        status_info = store1.get_command_status("cmd-1")
        assert status_info is not None
        assert status_info["status"] == "PUBLISHED"
        assert status_info["result_id"] == "res-1"
        assert status_info["result_json"] == result_payload
        
        # Instance 2 (Simulating worker restart with same db_path)
        store2 = WorkerDeduplicationStore(db_path=db_path)
        status_info2 = store2.get_command_status("cmd-1")
        assert status_info2 is not None
        assert status_info2["status"] == "PUBLISHED"
        assert status_info2["result_json"]["solverStatus"] == "FEASIBLE"
        
        # Mark completed
        store2.record_completed("cmd-1")
        assert store2.is_already_completed("cmd-1")
        
        # Instance 3 (Post-completion restart)
        store3 = WorkerDeduplicationStore(db_path=db_path)
        assert store3.is_already_completed("cmd-1")
        assert store3.get_command_status("cmd-1")["status"] == "COMPLETED"

def test_dedup_store_crash_recovery_state():
    with tempfile.TemporaryDirectory() as tmpdir:
        db_path = os.path.join(tmpdir, "test_crash.db")
        store = WorkerDeduplicationStore(db_path=db_path)
        
        # Unseen command
        assert store.get_command_status("unknown") is None
        
        # Command published before crash
        store.record_published("cmd-crash", "job-c", "att-1", "res-c", {"solverStatus": "UNKNOWN"})
        
        # Verify status is PUBLISHED
        retrieved = store.get_command_status("cmd-crash")
        assert retrieved["status"] == "PUBLISHED"
        assert store.is_already_completed("cmd-crash")

def test_duplicate_delivery_and_worker_recovery():
    with tempfile.TemporaryDirectory() as tmpdir:
        db_path = os.path.join(tmpdir, "test_recovery.db")
        store = WorkerDeduplicationStore(db_path=db_path)

        cmd_id = "cmd-100"
        job_id = "job-100"
        att_id = "att-1"
        res_payload = {"resultId": "res-100", "solverStatus": "FEASIBLE"}

        # Simulate first execution: solve and publish
        store.record_published(cmd_id, job_id, att_id, "res-100", res_payload)
        
        # Simulate worker crash before store.record_completed(cmd_id) and consumer.commit()
        # Upon worker reboot:
        store_reboot = WorkerDeduplicationStore(db_path=db_path)
        existing = store_reboot.get_command_status(cmd_id)
        assert existing is not None
        assert existing["status"] == "PUBLISHED"
        assert existing["result_id"] == "res-100"
        
        # Worker detects PUBLISHED state, re-emits cached result (idempotent at broker/consumer level),
        # marks COMPLETED and commits offset:
        store_reboot.record_completed(cmd_id)
        assert store_reboot.is_already_completed(cmd_id)
        assert store_reboot.get_command_status(cmd_id)["status"] == "COMPLETED"

        # Subsequent redelivery from Kafka is dropped as duplicate:
        status_on_redelivery = store_reboot.get_command_status(cmd_id)
        assert status_on_redelivery["status"] == "COMPLETED"
