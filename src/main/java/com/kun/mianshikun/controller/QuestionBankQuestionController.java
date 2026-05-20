package com.kun.mianshikun.controller;

import com.kun.mianshikun.common.BaseResponse;
import com.kun.mianshikun.common.DeleteRequest;
import com.kun.mianshikun.model.dto.questionBankQuestion.QuestionBankQuestionAddRequest;
import com.kun.mianshikun.model.dto.questionBankQuestion.QuestionBankQuestionEditRequest;
import com.kun.mianshikun.model.dto.questionBankQuestion.QuestionBankQuestionQueryRequest;
import com.kun.mianshikun.model.dto.questionBankQuestion.QuestionBankQuestionUpdateRequest;
import com.kun.mianshikun.model.vo.QuestionBankQuestionVO;
import com.kun.mianshikun.service.QuestionBankQuestionService;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.List;

@RestController
@RequestMapping("/questionBankQuestion")
@Slf4j
public class QuestionBankQuestionController {

    @Resource
    private QuestionBankQuestionService questionBankQuestionService;

    @PostMapping("/add")
    public BaseResponse<Long> addQuestionBankQuestion(@RequestBody QuestionBankQuestionAddRequest addRequest) {
        // todo
        return null;
    }

    @PostMapping("/delete")
    public BaseResponse<Boolean> deleteQuestionBankQuestion(@RequestBody DeleteRequest deleteRequest) {
        // todo
        return null;
    }

    @PostMapping("/update")
    public BaseResponse<Boolean> updateQuestionBankQuestion(@RequestBody QuestionBankQuestionUpdateRequest updateRequest) {
        // todo
        return null;
    }

    @GetMapping("/get/vo")
    public BaseResponse<QuestionBankQuestionVO> getQuestionBankQuestionVOById(Long id) {
        // todo
        return null;
    }

    @PostMapping("/list/page")
    public BaseResponse<?> listQuestionBankQuestionByPage(@RequestBody QuestionBankQuestionQueryRequest queryRequest) {
        // todo
        return null;
    }

    @PostMapping("/list/page/vo")
    public BaseResponse<?> listQuestionBankQuestionVOByPage(@RequestBody QuestionBankQuestionQueryRequest queryRequest) {
        // todo
        return null;
    }

    @PostMapping("/my/list/page/vo")
    public BaseResponse<?> listMyQuestionBankQuestionVOByPage(@RequestBody QuestionBankQuestionQueryRequest queryRequest) {
        // todo
        return null;
    }

    @PostMapping("/edit")
    public BaseResponse<Boolean> editQuestionBankQuestion(@RequestBody QuestionBankQuestionEditRequest editRequest) {
        // todo
        return null;
    }

    @PostMapping("/remove")
    public BaseResponse<Boolean> removeQuestionBankQuestion(@RequestBody QuestionBankQuestionRemoveRequest removeRequest) {
        // todo
        return null;
    }

    @PostMapping("/add/batch")
    public BaseResponse<Boolean> batchAddQuestionsToBank(@RequestBody QuestionBankQuestionBatchRequest batchRequest) {
        // todo
        return null;
    }

    @PostMapping("/remove/batch")
    public BaseResponse<Boolean> batchRemoveQuestionsFromBank(@RequestBody QuestionBankQuestionBatchRequest batchRequest) {
        // todo
        return null;
    }

    @Data
    public static class QuestionBankQuestionRemoveRequest {
        private Long questionBankId;
        private Long questionId;
    }

    @Data
    public static class QuestionBankQuestionBatchRequest {
        private Long questionBankId;
        private List<Long> questionIdList;
    }
}
