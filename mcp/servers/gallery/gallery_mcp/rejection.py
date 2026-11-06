"""Temporary admission refusals that a caller can recognise without parsing English text."""
from mcp.types import CallToolResult, TextContent


class RetryableRejection(ValueError):
    """Raised before any execution exists, so the same execution ID may be sent again later."""

    def __init__(self, code, message):
        super().__init__(message)
        self.code = code

    def as_result(self):
        return CallToolResult(isError=True, content=[TextContent(type='text', text=str(self))],
                              structuredContent={'errorCode': self.code, 'retryable': True})
