package com.sssok.infrastructure.image;

import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;

enum ExifOrientation {

    NORMAL(1, false) {
        @Override
        AffineTransform transform(int width, int height) {
            return new AffineTransform();
        }
    },
    FLIP_HORIZONTAL(2, false) {
        @Override
        AffineTransform transform(int width, int height) {
            return new AffineTransform(-1, 0, 0, 1, width, 0);
        }
    },
    ROTATE_180(3, false) {
        @Override
        AffineTransform transform(int width, int height) {
            return new AffineTransform(-1, 0, 0, -1, width, height);
        }
    },
    FLIP_VERTICAL(4, false) {
        @Override
        AffineTransform transform(int width, int height) {
            return new AffineTransform(1, 0, 0, -1, 0, height);
        }
    },
    TRANSPOSE(5, true) {
        @Override
        AffineTransform transform(int width, int height) {
            return new AffineTransform(0, 1, 1, 0, 0, 0);
        }
    },
    ROTATE_90_CLOCKWISE(6, true) {
        @Override
        AffineTransform transform(int width, int height) {
            return new AffineTransform(0, 1, -1, 0, height, 0);
        }
    },
    TRANSVERSE(7, true) {
        @Override
        AffineTransform transform(int width, int height) {
            return new AffineTransform(0, -1, -1, 0, height, width);
        }
    },
    ROTATE_90_COUNTER_CLOCKWISE(8, true) {
        @Override
        AffineTransform transform(int width, int height) {
            return new AffineTransform(0, -1, 1, 0, 0, width);
        }
    };

    private final int value;
    private final boolean swapsDimensions;

    ExifOrientation(int value, boolean swapsDimensions) {
        this.value = value;
        this.swapsDimensions = swapsDimensions;
    }

    static ExifOrientation from(Integer value) {
        if (value == null) {
            return NORMAL;
        }
        for (ExifOrientation orientation : values()) {
            if (orientation.value == value) {
                return orientation;
            }
        }
        return NORMAL;
    }

    BufferedImage applyTo(BufferedImage source) {
        if (this == NORMAL) {
            return source;
        }

        int width = source.getWidth();
        int height = source.getHeight();
        BufferedImage oriented = new BufferedImage(
            swapsDimensions ? height : width,
            swapsDimensions ? width : height,
            source.getColorModel().hasAlpha()
                ? BufferedImage.TYPE_INT_ARGB
                : BufferedImage.TYPE_INT_RGB);

        Graphics2D graphics = oriented.createGraphics();
        try {
            graphics.drawImage(source, transform(width, height), null);
        } finally {
            graphics.dispose();
        }
        return oriented;
    }

    abstract AffineTransform transform(int width, int height);
}
