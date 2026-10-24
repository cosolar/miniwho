package cn.minims.who.service;

import cn.minims.who.dto.Dtos.FeedbackRequest;
import cn.minims.who.entity.Feedback;
import cn.minims.who.mapper.FeedbackMapper;
import org.springframework.stereotype.Service;

@Service
public class FeedbackService {

    private final FeedbackMapper feedbackMapper;

    public FeedbackService(FeedbackMapper feedbackMapper) {
        this.feedbackMapper = feedbackMapper;
    }

    public void submit(long userId, FeedbackRequest request) {
        Feedback feedback = new Feedback();
        feedback.setUserId(userId);
        feedback.setContent(request.content().trim());
        feedback.setContact(request.contact() == null ? "" : request.contact().trim());
        feedback.setStatus(0);
        feedbackMapper.insert(feedback);
    }
}
