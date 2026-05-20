package com.kun.mianshikun.controller;

import com.kun.mianshikun.annotation.AuthCheck;
import com.kun.mianshikun.common.BaseResponse;
import com.kun.mianshikun.common.DeleteRequest;
import com.kun.mianshikun.constant.UserConstant;
import com.kun.mianshikun.model.dto.questionBank.QuestionBankAddRequest;
import com.kun.mianshikun.model.dto.questionBank.QuestionBankEditRequest;
import com.kun.mianshikun.model.dto.questionBank.QuestionBankQueryRequest;
import com.kun.mianshikun.model.dto.questionBank.QuestionBankUpdateRequest;
import com.kun.mianshikun.model.vo.QuestionBankVO;
import com.kun.mianshikun.service.QuestionBankService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

@RestController
@RequestMapping("/questionBank")
@Slf4j
public class QuestionBankController {

    @Resource
    private QuestionBankService questionBankService;

    @PostMapping("/add")
    public BaseResponse<Long> addQuestionBank(@RequestBody QuestionBankAddRequest questionBankAddRequest) {
        // todo
        return null;
    }

    @PostMapping("/delete")
    public BaseResponse<Boolean> deleteQuestionBank(@RequestBody DeleteRequest deleteRequest) {
        // todo
        return null;
    }
    @PostMapping("/edit")
    public BaseResponse<Boolean> editQuestionBank(@RequestBody QuestionBankEditRequest questionBankEditRequest) {
        // todo
        return null;
    }
    @PostMapping("/update")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> updateQuestionBank(@RequestBody QuestionBankUpdateRequest questionBankUpdateRequest) {
        // todo
        return null;
    }

    @GetMapping("/get/vo")
    public BaseResponse<QuestionBankVO> getQuestionBankVOById(Long id, Boolean needQueryQuestionList) {
        // todo
        return null;
    }

    @PostMapping("/list/page")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<?> listQuestionBankByPage(@RequestBody QuestionBankQueryRequest questionBankQueryRequest) {
        // todo
        return null;
    }

    @PostMapping("/list/page/vo")
    public BaseResponse<?> listQuestionBankVOByPage(@RequestBody QuestionBankQueryRequest questionBankQueryRequest) {
        // todo
        return null;
    }

    @PostMapping("/my/list/page/vo")
    public BaseResponse<?> listMyQuestionBankVOByPage(@RequestBody QuestionBankQueryRequest questionBankQueryRequest) {
        // todo
        return null;
    }

}
