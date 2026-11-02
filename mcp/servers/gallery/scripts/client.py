"""Small real MCP client for backend integration checks; never prints photo URLs or tokens."""
import argparse
import asyncio
import json
import os
from pathlib import Path
import sys
import time
from uuid import uuid4

from mcp import ClientSession
from mcp.client.streamable_http import streamablehttp_client


async def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--endpoint',default='http://127.0.0.1:8818/mcp')
    parser.add_argument('--room-id',type=int,default=1)
    parser.add_argument('--photos',type=Path,help='JSON array of mediaId, source, extension objects')
    parser.add_argument('--request-id',default=str(uuid4()))
    parser.add_argument('--job-id',default=str(uuid4()),help='Spring business job UUID')
    parser.add_argument('--execution-id',help='Resume local execution polling')
    parser.add_argument('--attempt',type=int,default=1)
    parser.add_argument('--output',type=Path)
    parser.add_argument('--wait-seconds',type=int,default=600)
    args = parser.parse_args()
    token_file = os.environ.get('GALLERY_TOKEN_FILE')
    token = Path(token_file).read_text().strip() if token_file else os.environ.get('GALLERY_TOKEN','')
    if not token:
        parser.error('Set GALLERY_TOKEN_FILE or GALLERY_TOKEN')
    if not args.execution_id and not args.photos:
        parser.error('--photos or --execution-id is required')
    async with streamablehttp_client(args.endpoint,headers={'Authorization':f'Bearer {token}'}) as (read,write,_):
        async with ClientSession(read,write) as session:
            await session.initialize()
            print('MCP tools:',', '.join(t.name for t in (await session.list_tools()).tools))

            async def call(name,arguments):
                response = await session.call_tool(name,arguments)
                if response.isError:
                    raise RuntimeError('MCP tool rejected the request; check scope, input and service logs')
                if response.structuredContent is not None:
                    return response.structuredContent
                return json.loads(next(c.text for c in response.content if c.type == 'text'))

            if args.execution_id:
                job = {'executionId':args.execution_id}
            else:
                job = await call('group_photos_by_person',{'room_id':args.room_id,'job_id':args.job_id,'attempt':args.attempt,'request_id':args.request_id,
                                                          'photos':json.loads(args.photos.read_text())})
            print('Execution:',job['executionId'])
            deadline = time.monotonic()+args.wait_seconds;prior=None
            while time.monotonic()<deadline:
                job = await call('get_gallery_job',{'room_id':args.room_id,'execution_id':job['executionId']})
                state = (job['state'],job['progress'].get('stage'))
                if state != prior:
                    print('State:',*state,flush=True);prior=state
                if job['state'] in ('COMPLETED','FAILED'):
                    if args.output:
                        args.output.write_text(json.dumps(job,ensure_ascii=False,indent=2)+'\n')
                    if job['state']=='FAILED':
                        raise RuntimeError('Analysis failed: '+job['errorCode'])
                    print('Result:',json.dumps(job['result']['counts'],ensure_ascii=False))
                    return
                await asyncio.sleep(1)
            raise TimeoutError('Client wait expired; the server job continues. Resume with --execution-id.')


if __name__ == '__main__':
    try:asyncio.run(main())
    except (RuntimeError,TimeoutError,ExceptionGroup) as exc:
        message = str(exc)
        while isinstance(exc, BaseExceptionGroup) and exc.exceptions:
            exc = exc.exceptions[0]; message = str(exc)
        print(message,file=sys.stderr);raise SystemExit(1)
