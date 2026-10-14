package com.sssok.application.port.out;

import com.sssok.domain.feedback.Feedback;

// 새 의견을 팀에 알리는 출력 포트. 어느 채널로 어떻게 보내는지는 응용 계층이 알 필요가 없다.
//
// 알림은 부가 기능이라 실패가 의견 등록에 번지면 안 된다. 구현체는 전송 실패를 예외로 올리지 않고
// 로그로 남긴 뒤 넘어간다.
public interface FeedbackNotifierPort {

    void notifyCreated(Feedback feedback);
}
