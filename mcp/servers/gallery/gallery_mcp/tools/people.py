from typing import Any

from ..inputs import Photo
from ..rejection import RetryableRejection


def _start(service, *args, **kwargs):
    # A temporary refusal is returned as a structured error result instead of an exception,
    # so the caller can tell it apart from a permanent one without parsing the message.
    try:
        return service.start(*args, **kwargs)
    except RetryableRejection as rejection:
        return rejection.as_result()


def register(mcp, service):
    @mcp.tool(structured_output=True)
    def group_photos_by_person(room_id: int, job_id: str, request_id: str,
                               attempt: int, photos: list[Photo]) -> dict[str, Any]:
        """Group photos by the people appearing in them, including group photos.
        Analyze Spring's complete room snapshot. job_id is the Spring job UUID;
        request_id is a unique execution UUID, reused only for the same attempt.
        Never applies folders. Spring owns authorization and durable job state.
        """
        return _start(service, room_id, request_id, photos, job_id=job_id, attempt=attempt)

    @mcp.tool(structured_output=True)
    def get_gallery_job(room_id: int, execution_id: str) -> dict[str, Any]:
        """Inspect a temporary local execution receipt. User-facing history lives in Spring."""
        return service.get(room_id, execution_id)

    @mcp.tool(structured_output=True)
    def group_similar_photos(room_id: int, job_id: str, request_id: str,
                             attempt: int, photos: list[Photo]) -> dict[str, Any]:
        """Group photos that look alike: burst shots (연사) and similar composition or scene
        (비슷한 구도·장면), regardless of who appears in them. Analyze a complete room snapshot.
        Returns groups of at least three, small pairs and unassigned photos.
        Never deletes photos or creates folders. IDs/authorization belong to Spring.
        """
        return _start(service, room_id, request_id, photos, job_id=job_id, attempt=attempt, kind='SIMILAR')
