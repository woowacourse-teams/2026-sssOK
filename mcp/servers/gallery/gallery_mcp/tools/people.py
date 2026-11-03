from typing import Any

from ..inputs import Photo


def register(mcp, service):
    @mcp.tool(structured_output=True)
    def group_photos_by_person(room_id: int, job_id: str, request_id: str,
                               attempt: int, photos: list[Photo]) -> dict[str, Any]:
        """Analyze Spring's complete room snapshot. job_id is the Spring job UUID;
        request_id is a unique execution UUID, reused only for the same attempt.
        Never applies folders. Spring owns authorization and durable job state.
        """
        return service.start(room_id, request_id, photos, job_id=job_id, attempt=attempt)

    @mcp.tool(structured_output=True)
    def get_gallery_job(room_id: int, execution_id: str) -> dict[str, Any]:
        """Inspect a temporary local execution receipt. User-facing history lives in Spring."""
        return service.get(room_id, execution_id)

    @mcp.tool(structured_output=True)
    def group_similar_photos(room_id: int, job_id: str, request_id: str,
                             attempt: int, photos: list[Photo]) -> dict[str, Any]:
        """Group a complete room snapshot by similar composition/burst, not identity.
        Returns groups of at least three, small pairs and unassigned photos.
        Never deletes photos or creates folders. IDs/authorization belong to Spring.
        """
        return service.start(room_id, request_id, photos, job_id=job_id, attempt=attempt, kind='SIMILAR')
