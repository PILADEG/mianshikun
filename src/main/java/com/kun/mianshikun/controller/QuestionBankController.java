package com.kun.mianshikun.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.kun.mianshikun.annotation.AuthCheck;
import com.kun.mianshikun.common.BaseResponse;
import com.kun.mianshikun.common.DeleteRequest;
import com.kun.mianshikun.common.ErrorCode;
import com.kun.mianshikun.common.ResultUtils;
import com.kun.mianshikun.constant.UserConstant;
import com.kun.mianshikun.exception.BusinessException;
import com.kun.mianshikun.exception.ThrowUtils;
import com.kun.mianshikun.model.dto.questionBank.QuestionBankAddRequest;
import com.kun.mianshikun.model.dto.questionBank.QuestionBankEditRequest;
import com.kun.mianshikun.model.dto.questionBank.QuestionBankQueryRequest;
import com.kun.mianshikun.model.dto.questionBank.QuestionBankUpdateRequest;
import com.kun.mianshikun.model.entity.Question;
import com.kun.mianshikun.model.entity.QuestionBank;
import com.kun.mianshikun.model.entity.QuestionBankQuestion;
import com.kun.mianshikun.model.entity.User;
import com.kun.mianshikun.model.vo.QuestionBankVO;
import com.kun.mianshikun.model.vo.QuestionVO;
import com.kun.mianshikun.service.QuestionBankQuestionService;
import com.kun.mianshikun.service.QuestionBankService;
import com.kun.mianshikun.service.QuestionService;
import com.kun.mianshikun.service.UserService;
import com.kun.mianshikun.util.UserContext;
import javax.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/questionBank")
@Slf4j
public class QuestionBankController {
    @Resource
    private QuestionService questionService;
    @Resource
    private QuestionBankService questionBankService;
    @Resource
    private QuestionBankQuestionService questionBankQuestionService;
    @Resource
    private FileController fileController;
    @Resource
    private UserService userService;
    @PostMapping("/add")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    @Transactional(rollbackFor = Exception.class)
    public BaseResponse<Long> addQuestionBank(@RequestBody QuestionBankAddRequest questionBankAddRequest) {
        ThrowUtils.throwIf(questionBankAddRequest == null, ErrorCode.PARAMS_ERROR);
        QuestionBank questionBank = new QuestionBank();
        BeanUtils.copyProperties(questionBankAddRequest, questionBank);
        questionBankService.validQuestionBank(questionBank, true);
        questionBank.setUserId(UserContext.getUserId());
        try{
            boolean result = questionBankService.save(questionBank);
            ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
            return ResultUtils.success(questionBank.getId());
        } catch (Exception e) {
            if (questionBankAddRequest.getPicture() != null
            && !questionBankAddRequest.getPicture().isEmpty()){
                fileController.deleteFile(questionBank.getPicture());
            }
            if (e instanceof BusinessException){
                throw e;
            }
            throw new BusinessException(ErrorCode.SYSTEM_ERROR);
        }
    }
    @PostMapping("/delete")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    @Transactional(rollbackFor = Exception.class)
    public BaseResponse<Boolean> deleteQuestionBank(@RequestBody DeleteRequest deleteRequest) {
        try{
            ThrowUtils.throwIf(deleteRequest == null || deleteRequest.getId() <= 0, ErrorCode.PARAMS_ERROR);
            long id = deleteRequest.getId();
            QuestionBank oldQuestionBank = questionBankService.getById(id);
            log.info("oldQuestonBank:{}",oldQuestionBank);
            User oldUser = userService.getById(oldQuestionBank.getUserId());
            ThrowUtils.throwIf(oldQuestionBank == null, ErrorCode.NOT_FOUND_ERROR);
            if (!UserConstant.ADMIN_ROLE.equals(UserContext.getUserRole())) {
                throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
            }
            if (!oldUser.getId().equals(UserContext.getUserId())){
                ThrowUtils.throwIf( oldUser.getUserRole().equals(UserConstant.ADMIN_ROLE),ErrorCode.NO_AUTH_ERROR);
            }
            if (oldQuestionBank.getPicture() != null
                    && !oldQuestionBank.getPicture().isEmpty()){
                fileController.deleteFile(oldQuestionBank.getPicture());
            }
            boolean result = questionBankService.removeById(id);
            return ResultUtils.success(result);
        } catch (Exception e) {
            if (e instanceof BusinessException){
                throw e;
            }
            throw new BusinessException(ErrorCode.SYSTEM_ERROR);
        }
    }
    @Transactional(rollbackFor = Exception.class)
    @PostMapping("/edit")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> editQuestionBank(@RequestBody QuestionBankEditRequest questionBankEditRequest) {
        ThrowUtils.throwIf(questionBankEditRequest == null || questionBankEditRequest.getId() == null,
                ErrorCode.PARAMS_ERROR);
        QuestionBank questionBank = new QuestionBank();
        BeanUtils.copyProperties(questionBankEditRequest, questionBank);
        questionBankService.validQuestionBank(questionBank, false);
        long id = questionBankEditRequest.getId();
        QuestionBank oldQuestionBank = questionBankService.getById(id);
        ThrowUtils.throwIf(oldQuestionBank == null, ErrorCode.NOT_FOUND_ERROR);
        if (!oldQuestionBank.getUserId().equals(UserContext.getUserId()) && !UserConstant.ADMIN_ROLE.equals(UserContext.getUserRole())) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
        boolean result = questionBankService.updateById(questionBank);
        return ResultUtils.success(result);
    }
    @Transactional(rollbackFor = Exception.class)
    @PostMapping("/update")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> updateQuestionBank(@RequestBody QuestionBankUpdateRequest questionBankUpdateRequest) {
        try{
            ThrowUtils.throwIf(questionBankUpdateRequest == null || questionBankUpdateRequest.getId() == null,
                    ErrorCode.PARAMS_ERROR);
            QuestionBank questionBank = new QuestionBank();
            BeanUtils.copyProperties(questionBankUpdateRequest, questionBank);
            questionBankService.validQuestionBank(questionBank, false);
            long id = questionBankUpdateRequest.getId();
            QuestionBank oldQuestionBank = questionBankService.getById(id);
            ThrowUtils.throwIf(oldQuestionBank == null, ErrorCode.NOT_FOUND_ERROR);
            User oldUser = userService.getById(oldQuestionBank.getUserId());
            if (!oldUser.getId().equals(UserContext.getUserId())){
                ThrowUtils.throwIf(oldUser.getUserRole().equals(UserConstant.ADMIN_ROLE),ErrorCode.NO_AUTH_ERROR);
            }
            boolean result = questionBankService.updateById(questionBank);
            return ResultUtils.success(result);
        } catch (Exception e) {
            if (questionBankUpdateRequest.getPicture() != null
                    && !questionBankUpdateRequest.getPicture().isEmpty()){
                fileController.deleteFile(questionBankUpdateRequest.getPicture());
            }
            if (e instanceof BusinessException) {
                throw e;
            }

            throw new BusinessException(ErrorCode.SYSTEM_ERROR);
        }
    }

    @GetMapping("/get/vo")
    public BaseResponse<QuestionBankVO> getQuestionBankVOById(Long id, Boolean needQueryQuestionList,
                                                              @RequestParam(defaultValue = "1",required = false) Integer current,
                                                              @RequestParam(defaultValue = "10",required = false) Integer pageSize) {
        ThrowUtils.throwIf(id == null || id <= 0, ErrorCode.PARAMS_ERROR);
        QuestionBank questionBank = questionBankService.getById(id);
        ThrowUtils.throwIf(questionBank == null, ErrorCode.NOT_FOUND_ERROR);
        QuestionBankVO questionBankVO = questionBankService.getQuestionBankVO(questionBank);
        log.info("{},{}",current, pageSize);
        if (needQueryQuestionList){
            List<QuestionBankQuestion> qbq_list = questionBankQuestionService
                    .list(Wrappers.lambdaQuery(QuestionBankQuestion.class)
                            .eq(QuestionBankQuestion::getQuestionBankId, id));
            List<Long> questionId_list = qbq_list.stream()
                    .map(QuestionBankQuestion::getQuestionId).collect(Collectors.toList());
            log.info("questionId_list:{}",questionId_list);
            if (questionId_list != null && questionId_list.size() > 0){
                Page<Question> questionPage = questionService.page(new Page<>(current, pageSize),
                        Wrappers.lambdaQuery(Question.class).in(Question::getId, questionId_list));
                questionBankVO.setQuestionList(questionService.getQuestionVOPage(questionPage));
            }else{
                questionBankVO.setQuestionList(new Page<QuestionVO>());
            }
        }
        return ResultUtils.success(questionBankVO);
    }

    @PostMapping("/list/page")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Page<QuestionBank>> listQuestionBankByPage(
            @RequestBody QuestionBankQueryRequest questionBankQueryRequest) {
        long current = questionBankQueryRequest.getCurrent();
        long size = questionBankQueryRequest.getPageSize();
        Page<QuestionBank> questionBankPage = questionBankService.page(new Page<>(current, size),
                questionBankService.getQueryWrapper(questionBankQueryRequest));
        return ResultUtils.success(questionBankPage);
    }

    @PostMapping("/list/page/vo")
    public BaseResponse<Page<QuestionBankVO>> listQuestionBankVOByPage(
            @RequestBody QuestionBankQueryRequest questionBankQueryRequest) {
        long current = questionBankQueryRequest.getCurrent();
        long size = questionBankQueryRequest.getPageSize();
        ThrowUtils.throwIf(size > 200, ErrorCode.PARAMS_ERROR);
        Page<QuestionBank> questionBankPage = questionBankService.page(new Page<>(current, size),
                questionBankService.getQueryWrapper(questionBankQueryRequest));
        return ResultUtils.success(questionBankService.getQuestionBankVOPage(questionBankPage));
    }

    @PostMapping("/my/list/page/vo")
    public BaseResponse<Page<QuestionBankVO>> listMyQuestionBankVOByPage(
            @RequestBody QuestionBankQueryRequest questionBankQueryRequest) {
        ThrowUtils.throwIf(questionBankQueryRequest == null, ErrorCode.PARAMS_ERROR);
        Long userId = UserContext.getUserId();
        ThrowUtils.throwIf(userId == null, ErrorCode.NOT_LOGIN_ERROR);
        questionBankQueryRequest.setUserId(UserContext.getUserId());
        long current = questionBankQueryRequest.getCurrent();
        long size = questionBankQueryRequest.getPageSize();
        ThrowUtils.throwIf(size > 20, ErrorCode.PARAMS_ERROR);
        Page<QuestionBank> questionBankPage = questionBankService.page(new Page<>(current, size),
                questionBankService.getQueryWrapper(questionBankQueryRequest));
        return ResultUtils.success(questionBankService.getQuestionBankVOPage(questionBankPage));
    }
}
