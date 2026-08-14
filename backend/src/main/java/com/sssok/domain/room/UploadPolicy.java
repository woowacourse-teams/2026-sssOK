package com.sssok.domain.room;

// 방에서 누가 파일을 올릴 수 있는지 나타내는 권한.
public enum UploadPolicy {

    ANYONE {
        @Override
        public boolean allows(boolean requesterIsHost) {
            return true;
        }
    },
    HOST_ONLY {
        @Override
        public boolean allows(boolean requesterIsHost) {
            return requesterIsHost;
        }
    };

    public abstract boolean allows(boolean requesterIsHost);
}
