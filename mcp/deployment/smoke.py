"""Read-only MCP connection check; no analysis job is created and no token is printed."""
import asyncio
import logging
from pathlib import Path

import httpx
from mcp import ClientSession
from mcp.client.streamable_http import streamablehttp_client


async def main():
    endpoint = 'http://127.0.0.1:8818'
    token = Path('/run/secrets/gallery_token').read_text().strip()
    async with httpx.AsyncClient(timeout=10, trust_env=False) as client:
        for path in ('healthz', 'readyz'):
            response = await client.get(f'{endpoint}/{path}')
            response.raise_for_status()
        response = await client.post(f'{endpoint}/mcp', json={})
        if response.status_code != 401:
            raise RuntimeError('Unauthenticated MCP requests must be rejected')
    async with streamablehttp_client(f'{endpoint}/mcp', headers={'Authorization': f'Bearer {token}'}) as (read, write, _):
        async with ClientSession(read, write) as session:
            await session.initialize()
            names = {tool.name for tool in (await session.list_tools()).tools}
            if not {'group_photos_by_person', 'get_gallery_job'} <= names:
                raise RuntimeError('Required gallery tools are missing')
    print('PASS: health, readiness, authentication, MCP initialize and tools/list')


if __name__ == '__main__':
    logging.basicConfig(level=logging.WARNING)
    asyncio.run(asyncio.wait_for(main(), timeout=30))
