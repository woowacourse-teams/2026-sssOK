package com.sssok.application.port.out;

import java.util.List;

public interface MediaSearchQueryRepository {
    List<Match> search(Long roomId, String vector, String model, int dimensions, double threshold);
    record Match(Long mediaId, double similarity) {}
}
