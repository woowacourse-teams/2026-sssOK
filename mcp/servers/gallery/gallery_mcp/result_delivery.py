"""At-least-once delivery to the persisted environment’s configured Spring endpoint.

No callback URL is accepted from an MCP caller. Terminal receipts remain on disk
until Spring confirms the execution ID and state after committing its transaction.
"""
import asyncio
import hashlib
import json
import logging
import time

import httpx


class Delivery:
    def __init__(self, service):
        self.service = service
        self.settings = service.settings
        self.schedule = {}

    async def tick(self, client):
        rows = self.service.store.rows()
        live = {row['id'] for row in rows}
        self.schedule = {key: value for key, value in self.schedule.items() if key in live}
        for row in rows:
            if row['state'] == 'QUEUED' or row['acknowledged_at']:
                continue
            execution_id = row['id']
            previous_state, next_at, failures = self.schedule.get(execution_id, (None, 0, 0))
            if previous_state == row['state'] and time.monotonic() < next_at:
                continue
            payload = self.service.view(row)
            payload['eventId'] = hashlib.sha256(json.dumps(payload, sort_keys=True).encode()).hexdigest()
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
                failures = 0
                delay = 15  # Running heartbeat; terminal states are persisted as acknowledged.
            except (httpx.HTTPError, ValueError, AttributeError, KeyError):
                failures += 1
                delay = min(60, 2 ** min(failures, 6))
                # No URL, token, response body or signed source is logged.
                logging.getLogger(__name__).warning('Backend delivery failed for execution %s; retrying', execution_id)
            self.schedule[execution_id] = (row['state'], time.monotonic() + delay, failures)
        self.service.store.prune(self.settings.receipt_retention_seconds)

    async def run(self):
        async with httpx.AsyncClient(timeout=10, follow_redirects=False, trust_env=False) as client:
            while True:
                await self.tick(client)
                await asyncio.sleep(1)
