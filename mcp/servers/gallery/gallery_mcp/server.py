import asyncio
from contextlib import asynccontextmanager
import hmac
import os
import logging

from mcp.server.fastmcp import FastMCP
from mcp.server.transport_security import TransportSecuritySettings
from starlette.responses import JSONResponse
from starlette.routing import Route
import uvicorn

from .config import Settings
from .result_delivery import Delivery
from .person_grouping_service import PersonGroupingService
from .tools.people import register as register_people_tools


class InternalAuth:
    """Private service authentication, not an end-user OAuth authorization server."""
    def __init__(self, app, token):
        self.app,self.expected = app,('Bearer '+token).encode()

    async def __call__(self, scope, receive, send):
        if scope['type'] == 'http' and scope['path'].startswith('/mcp'):
            values = [v for k,v in scope['headers'] if k.lower() == b'authorization']
            if len(values) != 1 or not hmac.compare_digest(values[0],self.expected):
                await JSONResponse({'error':'Service authentication required'},401)(scope,receive,send)
                return
        await self.app(scope,receive,send)


def create_app(settings=None, service=None):
    settings = settings or Settings.environment()
    service = service or PersonGroupingService(settings)
    mcp = FastMCP('sssOK Gallery',json_response=True,stateless_http=True,
                  max_request_body_size=128*1024,
                  transport_security=TransportSecuritySettings(
                      enable_dns_rebinding_protection=True,allowed_hosts=list(settings.allowed_hosts),allowed_origins=[]))

    register_people_tools(mcp,service)

    app = mcp.streamable_http_app()
    original_lifespan = app.router.lifespan_context

    @asynccontextmanager
    async def lifespan(app):
        with service.boot():
            async with original_lifespan(app):
                stopping = False
                tasks = [asyncio.create_task(service.dispatch(), name='analysis-dispatch')]
                if settings.backend_url:
                    tasks.append(asyncio.create_task(Delivery(service).run(), name="result-delivery"))
                def task_finished(task):
                    # Retrieve exceptions even during normal shutdown.
                    error = None if task.cancelled() else task.exception()
                    if stopping:
                        return
                    service.unavailable = True
                    # Never include exception text: it may contain signed URLs.
                    logging.getLogger(__name__).error(
                        'Essential task %s stopped (%s); new analysis requests blocked',
                        task.get_name(), type(error).__name__ if error else 'unexpected exit')

                for task in tasks:
                    task.add_done_callback(task_finished)
                try:
                    yield
                finally:
                    stopping = True
                    service.unavailable = True
                    for task in tasks:
                        task.cancel()
                    # One failed task must not skip cancellation/cleanup of the other.
                    await asyncio.gather(*tasks, return_exceptions=True)

    app.router.lifespan_context = lifespan

    async def health(request):
        return JSONResponse({'status': 'unavailable' if service.unavailable else 'ok'},
                            status_code=503 if service.unavailable else 200)

    async def ready(request):
        files = ['yunet.onnx','sface.onnx','ssdlite.pth','osnet.pth']
        available = not service.unavailable and all((settings.assets/name).is_file() for name in files)
        return JSONResponse({'ready':available},status_code=200 if available else 503)

    app.routes.append(Route('/healthz',health))
    app.routes.append(Route('/readyz',ready))
    return InternalAuth(app,settings.token)


if __name__ == '__main__':
    uvicorn.run(create_app(),host=os.environ.get('GALLERY_BIND','127.0.0.1'),port=8818,workers=1,access_log=False)
