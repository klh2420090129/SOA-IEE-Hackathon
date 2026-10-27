import json
import logging
import os
import signal
import sys
import time
from typing import Dict, Any

from kafka import KafkaConsumer, KafkaProducer
from heuristic_solver import solve_3d_packing
from cpsat_solver import solve_cpsat_cargo
from dedup_store import WorkerDeduplicationStore

logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s [%(levelname)s] [%(name)s] %(message)s'
)
logger = logging.getLogger("OptimizerWorker")

BOOTSTRAP_SERVERS = os.environ.get("KAFKA_BOOTSTRAP_SERVERS", "localhost:9092")
COMMAND_TOPIC = os.environ.get("OPTIMIZATION_COMMAND_TOPIC", "soa.s06.t08.optimization.commands")
RESULT_TOPIC = os.environ.get("OPTIMIZATION_RESULT_TOPIC", "soa.s06.t08.optimization.results")
CONSUMER_GROUP = os.environ.get("WORKER_CONSUMER_GROUP", "optimizer-worker-python-group")
DB_PATH = os.environ.get("WORKER_DB_PATH")

running = True

def signal_handler(sig, frame):
    global running
    logger.info("Termination signal received. Shutting down worker...")
    running = False

signal.signal(signal.SIGINT, signal_handler)
signal.signal(signal.SIGTERM, signal_handler)

def run_worker():
    global running
    logger.info("Initializing Python Optimizer Worker connecting to %s", BOOTSTRAP_SERVERS)
    logger.info("Command Topic: %s | Result Topic: %s", COMMAND_TOPIC, RESULT_TOPIC)

    store = WorkerDeduplicationStore(db_path=DB_PATH)
    logger.info("Worker deduplication store initialized at %s", store.db_path)

    producer = None
    consumer = None

    try:
        producer = KafkaProducer(
            bootstrap_servers=BOOTSTRAP_SERVERS.split(","),
            value_serializer=lambda v: json.dumps(v).encode("utf-8"),
            key_serializer=lambda k: k.encode("utf-8") if k else None,
            acks="all",
            retries=5
        )

        consumer = KafkaConsumer(
            COMMAND_TOPIC,
            bootstrap_servers=BOOTSTRAP_SERVERS.split(","),
            group_id=CONSUMER_GROUP,
            auto_offset_reset="earliest",
            enable_auto_commit=False,
            value_deserializer=lambda m: json.loads(m.decode("utf-8")),
            consumer_timeout_ms=1000
        )
        logger.info("Worker consumer successfully subscribed to topic %s", COMMAND_TOPIC)
    except Exception as e:
        logger.error("Failed to connect to Kafka: %s. Worker running in polling retry loop.", e)

    while running:
        if not consumer or not producer:
            time.sleep(2)
            try:
                producer = KafkaProducer(
                    bootstrap_servers=BOOTSTRAP_SERVERS.split(","),
                    value_serializer=lambda v: json.dumps(v).encode("utf-8"),
                    key_serializer=lambda k: k.encode("utf-8") if k else None,
                    acks="all",
                    retries=5
                )
                consumer = KafkaConsumer(
                    COMMAND_TOPIC,
                    bootstrap_servers=BOOTSTRAP_SERVERS.split(","),
                    group_id=CONSUMER_GROUP,
                    auto_offset_reset="earliest",
                    enable_auto_commit=False,
                    value_deserializer=lambda m: json.loads(m.decode("utf-8")),
                    consumer_timeout_ms=1000
                )
                logger.info("Reconnection to Kafka successful.")
            except Exception:
                continue

        try:
            records = consumer.poll(timeout_ms=1000)
            for topic_partition, messages in records.items():
                for msg in messages:
                    command = msg.value
                    command_id = command.get("commandId")
                    job_id = command.get("jobId")
                    attempt_id = command.get("attemptId")

                    logger.info("Processing optimization command [%s] for jobId [%s] attempt [%s]",
                                command_id, job_id, attempt_id)

                    if not command_id:
                        logger.warning("Rejecting command missing commandId. Committing offset.")
                        consumer.commit()
                        continue

                    # Check persistent deduplication store
                    existing = store.get_command_status(command_id)
                    if existing:
                        status = existing["status"]
                        if status == "COMPLETED":
                            logger.info("Command [%s] already fully processed and completed. Committing offset.", command_id)
                            consumer.commit()
                            continue
                        elif status == "PUBLISHED":
                            # Crash recovery: Result was published but offset was not committed
                            logger.info("Command [%s] was published previously. Republishing cached result to guarantee delivery.", command_id)
                            if existing.get("result_json"):
                                producer.send(RESULT_TOPIC, key=job_id, value=existing["result_json"])
                                producer.flush()
                            store.record_completed(command_id)
                            consumer.commit()
                            continue

                    # Execute bounded optimization pipeline (CP-SAT with heuristic baseline & fallback)
                    result = solve_cpsat_cargo(command)
                    result_id = result.get("resultId")

                    # Step 1: Dispatch result to RESULT_TOPIC keyed by jobId with durable acknowledgment
                    producer.send(RESULT_TOPIC, key=job_id, value=result)
                    producer.flush()

                    # Step 2: Record PUBLISHED in durable SQLite store BEFORE committing offset
                    store.record_published(command_id, job_id, attempt_id, result_id, result)

                    # Step 3: Record COMPLETED and commit Kafka offset
                    store.record_completed(command_id)
                    consumer.commit()

                    logger.info("Successfully completed command [%s], dispatched result [%s] (solverStatus=%s)",
                                command_id, result_id, result.get("solverStatus"))

        except Exception as e:
            if running:
                logger.error("Error during worker execution cycle: %s", e)
                time.sleep(1)

    if consumer:
        consumer.close()
    if producer:
        producer.close()
    logger.info("Worker gracefully terminated.")

if __name__ == "__main__":
    run_worker()

