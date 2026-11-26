"""Cooperative scheduling policy. Only the selected album spends its execution budget."""
from dataclasses import dataclass

RESIDENT_LIMIT = 2
HIGH_QUEUE_SECONDS = 2
BOOST_AFTER_SECONDS = 30
BATCH_PHOTOS = 10
FEATURE_STAGES = {
    'detecting_faces', 'detecting_bodies', 'extracting_scene_features', 'extracting_center_features',
}


@dataclass
class Album:
    id: str
    room_id: int
    queued_at: float
    elapsed: float = 0
    high: bool = True
    high_elapsed: float = 0

    def account(self, seconds):
        self.elapsed += seconds
        self.high_elapsed += seconds
        if self.high_elapsed >= HIGH_QUEUE_SECONDS:
            self.high = False


def select_album(ready, now):
    for album in ready:
        if not album.high and now - album.queued_at >= BOOST_AFTER_SECONDS:
            album.high = True
            album.high_elapsed = 0
    return next((album for album in ready if album.high), ready[0])
