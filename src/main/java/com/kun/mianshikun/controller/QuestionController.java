package com.kun.mianshikun.controller;

import com.kun.mianshikun.annotation.AuthCheck;
import com.kun.mianshikun.common.BaseResponse;
import com.kun.mianshikun.common.DeleteRequest;
import com.kun.mianshikun.constant.UserConstant;
import com.kun.mianshikun.model.dto.question.QuestionAddRequest;
import com.kun.mianshikun.model.dto.question.QuestionEditRequest;
import com.kun.mianshikun.model.dto.question.QuestionQueryRequest;
import com.kun.mianshikun.model.dto.question.QuestionUpdateRequest;
import com.kun.mianshikun.model.vo.QuestionVO;
import com.kun.mianshikun.service.QuestionService;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.List;

@RestController
@RequestMapping("/question")
@Slf4j
public class QuestionController {

    @Resource
    private QuestionService questionService;

    @PostMapping("/add")
    public BaseResponse<Long> addQuestion(@RequestBody QuestionAddRequest questionAddRequest) {
        // todo
        return null;
    }

    @PostMapping("/delete")
    public BaseResponse<Boolean> deleteQuestion(@RequestBody DeleteRequest deleteRequest) {
        // todo
        return null;
    }

    @PostMapping("/update")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> updateQuestion(@RequestBody QuestionUpdateRequest questionUpdateRequest) {
        // todo
        return null;
    }
    @PostMapping("/edit")
    public BaseResponse<Boolean> editQuestion(@RequestBody QuestionEditRequest questionEditRequest) {
        // todo
        return null;
    }
    @GetMapping("/get/vo")
    public BaseResponse<QuestionVO> getQuestionVOById(Long id) {
        // todo
        return null;
    }

    @PostMapping("/list/page")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<?> listQuestionByPage(@RequestBody QuestionQueryRequest questionQueryRequest) {
        // todo
        return null;
    }

    @PostMapping("/list/page/vo")
    public BaseResponse<?> listQuestionVOByPage(@RequestBody QuestionQueryRequest questionQueryRequest) {
        // todo
        return null;
    }

    @PostMapping("/my/list/page/vo")
    public BaseResponse<?> listMyQuestionVOByPage(@RequestBody QuestionQueryRequest questionQueryRequest) {
        // todo
        return null;
    }

    @PostMapping("/search/page/vo")
    public BaseResponse<?> searchQuestionVOByPage(@RequestBody QuestionQueryRequest questionQueryRequest) {
        // todo
        return null;
    }

    @PostMapping("/delete/batch")
    public BaseResponse<Boolean> batchDeleteQuestions(@RequestBody List<Long> questionIdList) {
        // todo
        return null;
    }

    @PostMapping("/ai/generate/question")
    public BaseResponse<Boolean> aiGenerateQuestions(@RequestBody QuestionAiGenerateRequest aiGenerateRequest) {
        // todo
        return null;
    }

    @Data
    public static class QuestionAiGenerateRequest {
        private String questionType;
        private Integer number;
    }
}
