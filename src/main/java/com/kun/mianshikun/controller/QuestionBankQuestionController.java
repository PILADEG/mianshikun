package com.kun.mianshikun.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.kun.mianshikun.annotation.AuthCheck;
import com.kun.mianshikun.common.BaseResponse;
import com.kun.mianshikun.common.DeleteRequest;
import com.kun.mianshikun.common.ErrorCode;
import com.kun.mianshikun.common.ResultUtils;
import com.kun.mianshikun.constant.UserConstant;
import com.kun.mianshikun.exception.BusinessException;
import com.kun.mianshikun.exception.ThrowUtils;
import com.kun.mianshikun.model.dto.questionBankQuestion.*;
import com.kun.mianshikun.model.entity.Question;
import com.kun.mianshikun.model.entity.QuestionBank;
import com.kun.mianshikun.model.entity.QuestionBankQuestion;
import com.kun.mianshikun.model.entity.User;
import com.kun.mianshikun.model.vo.LoginUserVO;
import com.kun.mianshikun.model.vo.QuestionBankQuestionVO;
import com.kun.mianshikun.service.QuestionBankQuestionService;
import com.kun.mianshikun.service.QuestionBankService;
import com.kun.mianshikun.service.QuestionService;
import com.kun.mianshikun.service.UserService;
import com.kun.mianshikun.util.UserContext;
import java.util.List;
import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/questionBankQuestion")
@Slf4j
public class QuestionBankQuestionController {
    @Resource
    private QuestionService questionService;
    @Resource
    private QuestionBankService questionBankService;
    @Resource
    private QuestionBankQuestionService questionBankQuestionService;
    @Resource
    private UserService userService;
    @PostMapping("/add")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Long> addQuestionBankQuestion(@RequestBody QuestionBankQuestionAddRequest addRequest) {
        ThrowUtils.throwIf(addRequest == null, ErrorCode.PARAMS_ERROR);
        ThrowUtils.throwIf(addRequest.getQuestionBankId() == null || addRequest.getQuestionBankId() <= 0,ErrorCode.PARAMS_ERROR);
        QuestionBankQuestion questionBankQuestion = new QuestionBankQuestion();
        BeanUtils.copyProperties(addRequest, questionBankQuestion);
        questionBankQuestionService.validQuestionBankQuestion(questionBankQuestion, true);
        Long questionCount=questionService.count(new QueryWrapper<Question>()
                .eq("id", questionBankQuestion.getQuestionId()));
        ThrowUtils.throwIf(questionCount<=0,ErrorCode.NOT_FOUND_ERROR);
        Long questionBankCount=questionBankService.count(new QueryWrapper<QuestionBank>()
                .eq("id", questionBankQuestion.getQuestionBankId()));
        ThrowUtils.throwIf(questionBankCount<=0,ErrorCode.NOT_FOUND_ERROR);
        Question question = questionService.getById(questionBankQuestion.getQuestionId());
        User oldUser = userService.getById(question.getUserId());
        if (!oldUser.getId().equals(UserContext.getUserId())){
            ThrowUtils.throwIf(oldUser.getUserRole().equals(UserConstant.ADMIN_ROLE),ErrorCode.NO_AUTH_ERROR);
        }
        questionBankQuestion.setUserId(UserContext.getUserId());
        boolean result = questionBankQuestionService.save(questionBankQuestion);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        return ResultUtils.success(questionBankQuestion.getId());
    }

    @PostMapping("/delete")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> deleteQuestionBankQuestion(@RequestBody DeleteRequest deleteRequest) {
        ThrowUtils.throwIf(deleteRequest == null || deleteRequest.getId() <= 0, ErrorCode.PARAMS_ERROR);
        long id = deleteRequest.getId();
        QuestionBankQuestion old = questionBankQuestionService.getById(id);
        ThrowUtils.throwIf(old == null, ErrorCode.NOT_FOUND_ERROR);
        Question oldQuestion = questionService.getById(old.getQuestionId());
        ThrowUtils.throwIf(oldQuestion == null, ErrorCode.NOT_FOUND_ERROR);
        User oldUser = userService.getById(oldQuestion.getUserId());
        if (!old.getUserId().equals(UserContext.getUserId()) && !UserConstant.ADMIN_ROLE.equals(UserContext.getUserRole())) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
        if (!old.getUserId().equals(UserContext.getUserId())){
            ThrowUtils.throwIf(oldUser.getUserRole().equals(UserConstant.ADMIN_ROLE),ErrorCode.NO_AUTH_ERROR);
        }
        boolean result = questionBankQuestionService.removeById(id);
        return ResultUtils.success(result);
    }

    @PostMapping("/update")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> updateQuestionBankQuestion(
            @RequestBody QuestionBankQuestionUpdateRequest updateRequest) {
        ThrowUtils.throwIf(updateRequest == null || updateRequest.getId() == null, ErrorCode.PARAMS_ERROR);
        QuestionBankQuestion questionBankQuestion = new QuestionBankQuestion();
        BeanUtils.copyProperties(updateRequest, questionBankQuestion);
        questionBankQuestionService.validQuestionBankQuestion(questionBankQuestion, false);
        long id = updateRequest.getId();
        QuestionBankQuestion old = questionBankQuestionService.getById(id);
        ThrowUtils.throwIf(old == null, ErrorCode.NOT_FOUND_ERROR);
        boolean result = questionBankQuestionService.updateById(questionBankQuestion);
        return ResultUtils.success(result);
    }

    @GetMapping("/get/vo")
    public BaseResponse<QuestionBankQuestionVO> getQuestionBankQuestionVOById(Long id) {
        ThrowUtils.throwIf(id == null || id <= 0, ErrorCode.PARAMS_ERROR);
        QuestionBankQuestion questionBankQuestion = questionBankQuestionService.getById(id);
        ThrowUtils.throwIf(questionBankQuestion == null, ErrorCode.NOT_FOUND_ERROR);
        return ResultUtils.success(questionBankQuestionService.getQuestionBankQuestionVO(questionBankQuestion));
    }

    @PostMapping("/list/page")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Page<QuestionBankQuestion>> listQuestionBankQuestionByPage(
            @RequestBody QuestionBankQuestionQueryRequest queryRequest) {
        long current = queryRequest.getCurrent();
        long size = queryRequest.getPageSize();
        Page<QuestionBankQuestion> page = questionBankQuestionService.page(new Page<>(current, size),
                questionBankQuestionService.getQueryWrapper(queryRequest));
        return ResultUtils.success(page);
    }

    @PostMapping("/list/page/vo")
    public BaseResponse<Page<QuestionBankQuestionVO>> listQuestionBankQuestionVOByPage(
            @RequestBody QuestionBankQuestionQueryRequest queryRequest) {
        long current = queryRequest.getCurrent();
        long size = queryRequest.getPageSize();
        ThrowUtils.throwIf(size > 20, ErrorCode.PARAMS_ERROR);
        Page<QuestionBankQuestion> page = questionBankQuestionService.page(new Page<>(current, size),
                questionBankQuestionService.getQueryWrapper(queryRequest));
        return ResultUtils.success(questionBankQuestionService.getQuestionBankQuestionVOPage(page));
    }

    @PostMapping("/my/list/page/vo")
    public BaseResponse<Page<QuestionBankQuestionVO>> listMyQuestionBankQuestionVOByPage(
            @RequestBody QuestionBankQuestionQueryRequest queryRequest) {
        ThrowUtils.throwIf(queryRequest == null, ErrorCode.PARAMS_ERROR);
        Long userId = UserContext.getUserId();
        ThrowUtils.throwIf(userId == null, ErrorCode.NOT_LOGIN_ERROR);
        queryRequest.setUserId(UserContext.getUserId());
        long current = queryRequest.getCurrent();
        long size = queryRequest.getPageSize();
        ThrowUtils.throwIf(size > 20, ErrorCode.PARAMS_ERROR);
        Page<QuestionBankQuestion> page = questionBankQuestionService.page(new Page<>(current, size),
                questionBankQuestionService.getQueryWrapper(queryRequest));
        return ResultUtils.success(questionBankQuestionService.getQuestionBankQuestionVOPage(page));
    }

    @PostMapping("/edit")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> editQuestionBankQuestion(@RequestBody QuestionBankQuestionEditRequest editRequest) {
        ThrowUtils.throwIf(editRequest == null || editRequest.getId() == null, ErrorCode.PARAMS_ERROR);
        QuestionBankQuestion questionBankQuestion = new QuestionBankQuestion();
        BeanUtils.copyProperties(editRequest, questionBankQuestion);
        questionBankQuestionService.validQuestionBankQuestion(questionBankQuestion, false);
        long id = editRequest.getId();
        QuestionBankQuestion old = questionBankQuestionService.getById(id);
        ThrowUtils.throwIf(old == null, ErrorCode.NOT_FOUND_ERROR);
        if (!old.getUserId().equals(UserContext.getUserId()) && !UserConstant.ADMIN_ROLE.equals(UserContext.getUserRole())) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
        boolean result = questionBankQuestionService.updateById(questionBankQuestion);
        return ResultUtils.success(result);
    }

    @PostMapping("/remove")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> removeQuestionBankQuestion(
            @RequestBody QuestionBankQuestionRemoveRequest removeRequest) {
        ThrowUtils.throwIf(removeRequest == null || removeRequest.getQuestionBankId() == null
                || removeRequest.getQuestionId() == null, ErrorCode.PARAMS_ERROR);
        QueryWrapper<QuestionBankQuestion> queryWrapper =
                new QueryWrapper<>();
        queryWrapper.eq("questionBankId", removeRequest.getQuestionBankId());
        queryWrapper.eq("questionId", removeRequest.getQuestionId());
        QuestionBankQuestion questionBankQuestion = questionBankQuestionService.getOne(queryWrapper);
        ThrowUtils.throwIf(questionBankQuestion == null, ErrorCode.NOT_FOUND_ERROR);
        Question question = questionService.getById(questionBankQuestion.getQuestionId());
        ThrowUtils.throwIf(question == null, ErrorCode.NOT_FOUND_ERROR);
        User oldUser = userService.getById(question.getUserId());
        if (!oldUser.getId().equals(UserContext.getUserId())){
            ThrowUtils.throwIf(oldUser.getUserRole().equals(UserConstant.ADMIN_ROLE),ErrorCode.NO_AUTH_ERROR);
        }
        boolean result = questionBankQuestionService.remove(queryWrapper);
        return ResultUtils.success(result);
    }

    @PostMapping("/add/batch")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> batchAddQuestionsToBank(@RequestBody QuestionBankQuestionBatchRequest batchRequest,
                                                         HttpServletRequest  request) {
        ThrowUtils.throwIf(batchRequest == null || batchRequest.getQuestionBankId() == null
                || batchRequest.getQuestionIdList() == null || batchRequest.getQuestionIdList().isEmpty(),
                ErrorCode.PARAMS_ERROR);
        LoginUserVO loginUser = UserContext.getLoginUser();
        User user = new User();
        BeanUtils.copyProperties(loginUser, user, User.class);
        Boolean result =questionBankQuestionService.batchAddQuestionBankQuestion(batchRequest,user);
        return ResultUtils.success(result);
    }

    @PostMapping("/remove/batch")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> batchRemoveQuestionsFromBank(
            @RequestBody QuestionBankQuestionBatchRequest batchRequest) {
        ThrowUtils.throwIf(batchRequest == null || batchRequest.getQuestionBankId() == null
                || batchRequest.getQuestionIdList() == null || batchRequest.getQuestionIdList().isEmpty(),
                ErrorCode.PARAMS_ERROR);
        LoginUserVO loginUser = UserContext.getLoginUser();
        User user = new User();
        BeanUtils.copyProperties(loginUser, user, User.class);
        Boolean result =questionBankQuestionService.batchRemoveQuestionBankQuestion(batchRequest,user);
        return ResultUtils.success(result);
    }
}
