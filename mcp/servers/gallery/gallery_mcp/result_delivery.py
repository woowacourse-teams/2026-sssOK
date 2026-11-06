"""At-least-once delivery to the persisted environment’s configured Spring endpoint.

No callback URL is accepted from an MCP caller. Terminal receipts remain on disk
until Spring confirms the execution ID and state after committing its transaction.

Running progress is coalesced: the analysis only records its latest progress, and this loop
decides when to send it (changed -> progress interval, stage change -> next loop,
unchanged -> heartbeat). A heartbeat proves this process is alive, not that analysis advanced.
"""
import asyncio
from dataclasses import dataclass
import hashlib
import json
import logging
import time

import httpx


@dataclass
class _Sent:
    state: str            # state of the latest attempt, successful or not
    progress: tuple | None = None   # progress fields of the latest successful send
    sent_at: float = 0    # clock time of the latest successful send
    retry_at: float = 0   # no new attempt before this time while failures > 0
    failures: int = 0


def _progress_key(payload):
    progress = payload.get('progress') or {}
    return tuple(progress.get(name) for name in ('stage', 'processed', 'total', 'unit'))


class Delivery:
    # Short enough that a due send is picked up quickly; the intervals below decide how often one is due.
    TICK_SECONDS = 0.5

    def __init__(self, service, clock=time.monotonic):
        self.service = service
        self.settings = service.settings
        self.clock = clock
        self.schedule = {}

    def _due(self, sent, state, key, now):
        if sent is None or sent.state != state:
            return True  # first delivery, or a state change such as COMPLETED/FAILED
        if sent.failures:
            return now >= sent.retry_at  # backoff also holds back stage changes
        if key == sent.progress:
            return now - sent.sent_at >= self.settings.heartbeat_interval_seconds
        if key[0] != sent.progress[0]:
            return True  # stage changed: do not wait for the progress interval
        return now - sent.sent_at >= self.settings.progress_interval_seconds

    async def tick(self, client):
        rows = self.service.store.rows()
        live = {row['id'] for row in rows}
        self.schedule = {key: value for key, value in self.schedule.items() if key in live}
        for row in rows:
            if row['state'] == 'QUEUED' or row['acknowledged_at']:
                continue
            execution_id = row['id']
            payload = self.service.view(row)
            key = _progress_key(payload)
            now = self.clock()
            sent = self.schedule.get(execution_id)
            if not self._due(sent, row['state'], key, now):
                continue
            payload['eventId'] = hashlib.sha256(json.dumps(payload, sort_keys=True).encode()).hexdigest()
            sent = _Sent(row['state'], sent.progress if sent else None, sent.sent_at if sent else 0,
                         0, sent.failures if sent else 0)
            try:
                url, token = self.settings.callback(row['environment'])
                if not url:
                    raise ValueError('Callback missing for environment')
                response = await client.post(url, json=payload,
                    headers={'Authorization': 'Bearer ' + token})
                response.raise_for_status()
                ack = response.json()
                if (ack.get('accepted') is not True or ack.get('executionId') != execution_id
                        or ack.get('state') != row['state']):
                    raise ValueError('Backend did not acknowledge this execution and state')
                self.service.store.acknowledge(execution_id, row['state'])
                sent.failures, sent.progress, sent.sent_at = 0, key, now
            except (httpx.HTTPError, ValueError, AttributeError, KeyError):
                # KeyError: no callback route is configured for this execution's environment.
                sent.failures += 1
                sent.retry_at = now + min(60, 2 ** min(sent.failures, 6))
                # No URL, token, response body or signed source is logged.
                logging.getLogger(__name__).warning('Backend delivery failed for execution %s; retrying', execution_id)
            self.schedule[execution_id] = sent
        self.service.store.prune(self.settings.receipt_retention_seconds)

    async def run(self):
        async with httpx.AsyncClient(timeout=10, follow_redirects=False, trust_env=False) as client:
            while True:
                await self.tick(client)
                await asyncio.sleep(self.TICK_SECONDS)
