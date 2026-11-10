import chevronRight from "../assets/chevron-right.svg";
import type { ReviewPerson } from "../model/personReview";
import {
  Card,
  Chevron,
  EmptiedAction,
  EmptiedButton,
  EmptiedTitle,
  More,
  PhotoRow,
  Thumb,
  Thumbs,
} from "./PersonReview.styles";

const VISIBLE_PHOTOS = 4;

interface EmptiedCardProps {
  person: ReviewPerson;
  onOpenPhotos: () => void;
}

export const EmptiedCard = ({ person, onOpenPhotos }: EmptiedCardProps) => {
  const visible = person.photos.slice(0, VISIBLE_PHOTOS);
  const rest = person.photos.length - VISIBLE_PHOTOS;

  return (
    <Card $picked={false}>
      <EmptiedButton type="button" onClick={onOpenPhotos}>
        <PhotoRow as="span">
          <Thumbs $dimmed>
            {visible.map((photo, index) => (
              <Thumb key={photo.mediaId}>
                <img src={photo.thumbnailUrl} alt="" loading="lazy" />
                {index === VISIBLE_PHOTOS - 1 && rest > 0 && <More>+{rest}</More>}
              </Thumb>
            ))}
          </Thumbs>
          <Chevron aria-hidden="true">
            <img src={chevronRight} alt="" width={18} height={18} />
          </Chevron>
        </PhotoRow>
        <EmptiedTitle>사진을 모두 뺐어요</EmptiedTitle>
        <EmptiedAction>눌러서 다시 넣기</EmptiedAction>
      </EmptiedButton>
    </Card>
  );
};
