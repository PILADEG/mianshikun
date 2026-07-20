package com.kun.mianshikun.controller;

import cn.hutool.json.JSONUtil;
import com.alibaba.csp.sentinel.Entry;
import com.alibaba.csp.sentinel.EntryType;
import com.alibaba.csp.sentinel.SphU;
import com.alibaba.csp.sentinel.Tracer;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeException;
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
import com.kun.mianshikun.model.dto.question.QuestionAddRequest;
import com.kun.mianshikun.model.dto.question.QuestionEditRequest;
import com.kun.mianshikun.model.dto.question.QuestionQueryRequest;
import com.kun.mianshikun.model.dto.question.QuestionUpdateRequest;
import com.kun.mianshikun.model.entity.Question;
import com.kun.mianshikun.model.entity.QuestionBankQuestion;
import com.kun.mianshikun.model.entity.User;
import com.kun.mianshikun.model.vo.QuestionVO;
import com.kun.mianshikun.sentinel.SentinelConstant;
import com.kun.mianshikun.service.QuestionBankQuestionService;
import com.kun.mianshikun.service.QuestionService;
import com.kun.mianshikun.service.UserService;
import com.kun.mianshikun.util.UserContext;
import java.util.List;
import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/question")
@Slf4j
public class QuestionController {

    @Resource
    private QuestionService questionService;
    @Resource
    private UserService userService;
    @Resource
    private QuestionBankQuestionService questionBankQuestionService;
    @Transactional(rollbackFor = Exception.class)
    @PostMapping("/add")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Long> addQuestion(@RequestBody QuestionAddRequest questionAddRequest) {
        ThrowUtils.throwIf(questionAddRequest == null, ErrorCode.PARAMS_ERROR);
        Question question = new Question();
        BeanUtils.copyProperties(questionAddRequest, question);
        List<String> tags = questionAddRequest.getTags();
        if (tags != null) {
            question.setTags(JSONUtil.toJsonStr(tags));
        }
        questionService.validQuestion(question, true);
        question.setUserId(UserContext.getUserId());
        boolean result = questionService.save(question);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        return ResultUtils.success(question.getId());
    }
    @Transactional(rollbackFor = Exception.class)
    @PostMapping("/delete")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> deleteQuestion(@RequestBody DeleteRequest deleteRequest) {
        ThrowUtils.throwIf(deleteRequest == null || deleteRequest.getId() <= 0, ErrorCode.PARAMS_ERROR);
        long id = deleteRequest.getId();
        Question oldQuestion = questionService.getById(id);
        ThrowUtils.throwIf(oldQuestion == null, ErrorCode.NOT_FOUND_ERROR);
        User oldUser = userService.getById(oldQuestion.getUserId());
        if (!oldUser.getId().equals(UserContext.getUserId())){
            ThrowUtils.throwIf(oldUser.getUserRole().equals(UserConstant.ADMIN_ROLE),ErrorCode.NO_AUTH_ERROR);
        }
        boolean re = questionBankQuestionService.remove(new QueryWrapper<QuestionBankQuestion>().eq("questionId", id));
        boolean result = questionService.removeById(id);
        return ResultUtils.success(result);
    }
    @Transactional(rollbackFor = Exception.class)
    @PostMapping("/update")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> updateQuestion(@RequestBody QuestionUpdateRequest questionUpdateRequest) {
        ThrowUtils.throwIf(questionUpdateRequest == null || questionUpdateRequest.getId() == null,
                ErrorCode.PARAMS_ERROR);
        Question question = new Question();
        BeanUtils.copyProperties(questionUpdateRequest, question);
        List<String> tags = questionUpdateRequest.getTags();
        if (tags != null) {
            question.setTags(JSONUtil.toJsonStr(tags));
        }
        questionService.validQuestion(question, false);
        long id = questionUpdateRequest.getId();
        Question oldQuestion = questionService.getById(id);
        ThrowUtils.throwIf(oldQuestion == null, ErrorCode.NOT_FOUND_ERROR);
        User oldUser = userService.getById(oldQuestion.getUserId());
        if (!oldUser.getId().equals(UserContext.getUserId())){
            ThrowUtils.throwIf(oldUser.getUserRole().equals(UserConstant.ADMIN_ROLE),ErrorCode.NO_AUTH_ERROR);
        }
        boolean result = questionService.updateById(question);
        return ResultUtils.success(result);
    }
    @Transactional(rollbackFor = Exception.class)
    @PostMapping("/edit")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> editQuestion(@RequestBody QuestionEditRequest questionEditRequest) {
        ThrowUtils.throwIf(questionEditRequest == null || questionEditRequest.getId() == null,
                ErrorCode.PARAMS_ERROR);
        Question question = new Question();
        BeanUtils.copyProperties(questionEditRequest, question);
        List<String> tags = questionEditRequest.getTags();
        if (tags != null) {
            question.setTags(JSONUtil.toJsonStr(tags));
        }
        questionService.validQuestion(question, false);
        long id = questionEditRequest.getId();
        Question oldQuestion = questionService.getById(id);
        ThrowUtils.throwIf(oldQuestion == null, ErrorCode.NOT_FOUND_ERROR);
        if (!oldQuestion.getUserId().equals(UserContext.getUserId()) && !UserConstant.ADMIN_ROLE.equals(UserContext.getUserRole())) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
        boolean result = questionService.updateById(question);
        return ResultUtils.success(result);
    }

    @GetMapping("/get/vo")
    public BaseResponse<QuestionVO> getQuestionVOById(Long id) {
        ThrowUtils.throwIf(id == null || id <= 0, ErrorCode.PARAMS_ERROR);
        Question question = questionService.getById(id);
        ThrowUtils.throwIf(question == null, ErrorCode.NOT_FOUND_ERROR);
        return ResultUtils.success(questionService.getQuestionVO(question));
    }

    @PostMapping("/list/page")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Page<Question>> listQuestionByPage(@RequestBody QuestionQueryRequest questionQueryRequest) {
        long current = questionQueryRequest.getCurrent();
        long size = questionQueryRequest.getPageSize();
        Page<Question> questionPage = questionService.page(new Page<>(current, size),
                questionService.getQueryWrapper(questionQueryRequest));
        List<Question> new_questions = questionService.getQuestionBanks(questionPage.getRecords());
        questionPage.setRecords(new_questions);
        return ResultUtils.success(questionPage);
    }

    @PostMapping("/list/page/vo")
    public BaseResponse<Page<QuestionVO>> listQuestionVOByPage(@RequestBody QuestionQueryRequest questionQueryRequest) {
        long current = questionQueryRequest.getCurrent();
        long size = questionQueryRequest.getPageSize();
        ThrowUtils.throwIf(size > 200, ErrorCode.PARAMS_ERROR);
        log.info("listQuestionVOByPage: {}", questionQueryRequest);
        Page<Question> questionPage = questionService.page(new Page<>(current, size),
                questionService.getQueryWrapper(questionQueryRequest));
        return ResultUtils.success(questionService.getQuestionVOPage(questionPage));
    }
    @PostMapping("/list/page/es")
    public BaseResponse<Page<QuestionVO>> listQuestionVOByPageEs(
            @RequestBody QuestionQueryRequest questionQueryRequest) {
        long current = questionQueryRequest.getCurrent();
        long size = questionQueryRequest.getPageSize();
        ThrowUtils.throwIf(size > 200, ErrorCode.PARAMS_ERROR);
        log.info("listQuestionVOByPage: {}", questionQueryRequest);
        Page<Question> questionPage = questionService.searchFromES(questionQueryRequest);
        return ResultUtils.success(questionService.getQuestionVOPage(questionPage));
    }
    @PostMapping("/list/page/vo/sentinel")
    public BaseResponse<Page<QuestionVO>> listQuestionVOByPageSentinel(
            @RequestBody QuestionQueryRequest questionQueryRequest
            , HttpServletRequest  request){
        String address = request.getRemoteAddr();
        Entry entry = null;
        try {
            entry = SphU.entry(SentinelConstant.QUESTION_PAGE_NAME
                    ,EntryType.IN , 1, address);
            long current = questionQueryRequest.getCurrent();
            long size = questionQueryRequest.getPageSize();
            ThrowUtils.throwIf(size > 20, ErrorCode.PARAMS_ERROR);
            Page<Question> questionPage = questionService.page(new Page<>(current, size),
                    questionService.getQueryWrapper(questionQueryRequest));
            return ResultUtils.success(questionService.getQuestionVOPage(questionPage));
        } catch (Throwable e) {
            if (!BlockException.isBlockException(e)){
                Tracer.trace(e);
                return ResultUtils.error(ErrorCode.SYSTEM_ERROR,e.getMessage());
            }
            if (e instanceof DegradeException){
                return ResultUtils.success(null);
            }
            return ResultUtils.error(ErrorCode.SYSTEM_ERROR,
                    "当前访问人数过多，请稍后再试");
        }
        finally {
            if (entry != null){
                entry.exit(1,address);
            }
        }
    }

    @PostMapping("/my/list/page/vo")
    public BaseResponse<Page<QuestionVO>> listMyQuestionVOByPage(@RequestBody QuestionQueryRequest questionQueryRequest) {
        ThrowUtils.throwIf(questionQueryRequest == null, ErrorCode.PARAMS_ERROR);
        Long  userId = UserContext.getUserId();
        ThrowUtils.throwIf(userId == null, ErrorCode.NOT_LOGIN_ERROR);
        questionQueryRequest.setUserId(UserContext.getUserId());
        long current = questionQueryRequest.getCurrent();
        long size = questionQueryRequest.getPageSize();
        ThrowUtils.throwIf(size > 20, ErrorCode.PARAMS_ERROR);
        Page<Question> questionPage = questionService.page(new Page<>(current, size),
                questionService.getQueryWrapper(questionQueryRequest));
        return ResultUtils.success(questionService.getQuestionVOPage(questionPage));
    }

    @PostMapping("/search/page/vo")
    public BaseResponse<Page<QuestionVO>> searchQuestionVOByPage(@RequestBody QuestionQueryRequest questionQueryRequest) {
        long current = questionQueryRequest.getCurrent();
        long size = questionQueryRequest.getPageSize();
        ThrowUtils.throwIf(size > 20, ErrorCode.PARAMS_ERROR);
        Page<Question> questionPage = questionService.page(new Page<>(current, size),
                questionService.getQueryWrapper(questionQueryRequest));
        return ResultUtils.success(questionService.getQuestionVOPage(questionPage));
    }

    @PostMapping("/delete/batch")
    public BaseResponse<Boolean> batchDeleteQuestions(@RequestBody List<Long> questionIdList) {
        ThrowUtils.throwIf(questionIdList == null || questionIdList.isEmpty(), ErrorCode.PARAMS_ERROR);
        Long currentUserId = UserContext.getUserId();
        String currentUserRole = UserContext.getUserRole();
        questionIdList.forEach(id -> {
            Question oldQuestion = questionService.getById(id);
            if (oldQuestion != null) {
                if (!oldQuestion.getUserId().equals(currentUserId) && !UserConstant.ADMIN_ROLE.equals(currentUserRole)) {
                    throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权限删除题目: " + id);
                }
                questionService.removeById(id);
            }
        });
        return ResultUtils.success(true);
    }

    @PostMapping("/ai/generate/question")
    public BaseResponse<Boolean> aiGenerateQuestions(@RequestBody QuestionAiGenerateRequest aiGenerateRequest) {
        // AI 生成题目功能待接入 AI 服务后实现
        return ResultUtils.success(true);
    }

    @Data
    public static class QuestionAiGenerateRequest {
        private String questionType;
        private Integer number;
    }
}
