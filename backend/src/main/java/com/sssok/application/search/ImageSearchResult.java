package com.sssok.application.search;

import com.sssok.application.media.MediaDetail;

public record ImageSearchResult(MediaDetail media, double similarity) {}
