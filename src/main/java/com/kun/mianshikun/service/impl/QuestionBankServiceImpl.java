package com.kun.mianshikun.service.impl;

import cn.hutool.core.collection.CollUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.kun.mianshikun.annotation.HotKeyCache;
import com.kun.mianshikun.common.ErrorCode;
import com.kun.mianshikun.constant.CommonConstant;
import com.kun.mianshikun.constant.HotKeyConstant;
import com.kun.mianshikun.constant.RedisConstant;
import com.kun.mianshikun.exception.BusinessException;
import com.kun.mianshikun.exception.ThrowUtils;
import com.kun.mianshikun.mapper.QuestionBankMapper;
import com.kun.mianshikun.model.dto.questionBank.QuestionBankQueryRequest;
import com.kun.mianshikun.model.entity.Question;
import com.kun.mianshikun.model.entity.QuestionBank;
import com.kun.mianshikun.model.entity.QuestionBankQuestion;
import com.kun.mianshikun.model.entity.User;
import com.kun.mianshikun.model.vo.QuestionBankVO;
import com.kun.mianshikun.model.vo.QuestionVO;
import com.kun.mianshikun.model.vo.UserVO;
import com.kun.mianshikun.service.QuestionBankQuestionService;
import com.kun.mianshikun.service.QuestionBankService;
import com.kun.mianshikun.service.QuestionService;
import com.kun.mianshikun.service.UserService;
import com.kun.mianshikun.utils.SqlUtils;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.Resource;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(rollbackFor = Exception.class)
@Slf4j
public class QuestionBankServiceImpl extends ServiceImpl<QuestionBankMapper, QuestionBank> implements QuestionBankService {

    @Resource
    private UserService userService;

    @Resource
    private QuestionBankQuestionService questionBankQuestionService;

    @Resource
    private QuestionService questionService;

    @Override
    public void validQuestionBank(QuestionBank questionBank, boolean add) {
        if (questionBank == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        String title = questionBank.getTitle();
        String description = questionBank.getDescription();
        if (add) {
            ThrowUtils.throwIf(StringUtils.isBlank(title), ErrorCode.PARAMS_ERROR);
        }
        if (StringUtils.isNotBlank(title) && title.length() > 80) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "标题过长");
        }
        if (StringUtils.isNotBlank(description) && description.length() > 2048) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "描述过长");
        }
    }

    @Override
    public QueryWrapper<QuestionBank> getQueryWrapper(QuestionBankQueryRequest questionBankQueryRequest) {
        QueryWrapper<QuestionBank> queryWrapper = new QueryWrapper<>();
        if (questionBankQueryRequest == null) {
            return queryWrapper;
        }
        String searchText = questionBankQueryRequest.getSearchText();
        String sortField = questionBankQueryRequest.getSortField();
        String sortOrder = questionBankQueryRequest.getSortOrder();
        Long id = questionBankQueryRequest.getId();
        Long notId = questionBankQueryRequest.getNotId();
        String title = questionBankQueryRequest.getTitle();
        String description = questionBankQueryRequest.getDescription();
        Long userId = questionBankQueryRequest.getUserId();

        if (StringUtils.isNotBlank(searchText)) {
            queryWrapper.and(qw -> qw.like("title", searchText).or().like("description", searchText));
        }
        queryWrapper.like(StringUtils.isNotBlank(title), "title", title);
        queryWrapper.like(StringUtils.isNotBlank(description), "description", description);
        queryWrapper.ne(ObjectUtils.isNotEmpty(notId), "id", notId);
        queryWrapper.eq(ObjectUtils.isNotEmpty(id), "id", id);
        queryWrapper.eq(ObjectUtils.isNotEmpty(userId), "userId", userId);
        queryWrapper.orderBy(SqlUtils.validSortField(sortField), CommonConstant.SORT_ORDER_ASC.equals(sortOrder),
                sortField);
        return queryWrapper;
    }

    @Override
    public QuestionBankVO getQuestionBankVO(QuestionBank questionBank) {
        if (questionBank == null) {
            return null;
        }
        QuestionBankVO questionBankVO = QuestionBankVO.objToVo(questionBank);
        Long userId = questionBank.getUserId();
        if (userId != null && userId > 0) {
            User user = userService.getById(userId);
            UserVO userVO = userService.getUserVO(user);
            questionBankVO.setUser(userVO);
        }
        return questionBankVO;
    }

    @HotKeyCache(key = HotKeyConstant.QUESTION_BANK_DETAIL_KEY + "#id",prefix = RedisConstant.QUESTIONBANK_HOTKEY_KEY)
    @Override
    public QuestionBankVO getQuestionBankVOById(Long id, Boolean needQueryQuestionList, Integer current, Integer pageSize) {
        ThrowUtils.throwIf(id == null || id <= 0, ErrorCode.PARAMS_ERROR);
        QuestionBank questionBank = getById(id);
        ThrowUtils.throwIf(questionBank == null, ErrorCode.NOT_FOUND_ERROR);
        QuestionBankVO questionBankVO = getQuestionBankVO(questionBank);
        log.info("{},{}", current, pageSize);
        if (Boolean.TRUE.equals(needQueryQuestionList)) {
            List<QuestionBankQuestion> qbqList = questionBankQuestionService
                    .list(Wrappers.lambdaQuery(QuestionBankQuestion.class)
                            .eq(QuestionBankQuestion::getQuestionBankId, id));
            List<Long> questionIdList = qbqList.stream()
                    .map(QuestionBankQuestion::getQuestionId).collect(Collectors.toList());
            log.info("questionId_list:{}", questionIdList);
            if (!questionIdList.isEmpty()) {
                Page<Question> questionPage = questionService.page(new Page<>(current, pageSize),
                        Wrappers.lambdaQuery(Question.class).in(Question::getId, questionIdList));
                questionBankVO.setQuestionList(questionService.getQuestionVOPage(questionPage));
            } else {
                questionBankVO.setQuestionList(new Page<>());
            }
        }
        return questionBankVO;
    }

    @Override
    public Page<QuestionBankVO> getQuestionBankVOPage(Page<QuestionBank> questionBankPage) {
        List<QuestionBank> questionBankList = questionBankPage.getRecords();
        Page<QuestionBankVO> questionBankVOPage = new Page<>(questionBankPage.getCurrent(), questionBankPage.getSize(),
                questionBankPage.getTotal());
        if (CollUtil.isEmpty(questionBankList)) {
            return questionBankVOPage;
        }
        Set<Long> userIdSet = questionBankList.stream().map(QuestionBank::getUserId).collect(Collectors.toSet());
        Map<Long, List<User>> userIdUserListMap = userService.listByIds(userIdSet).stream()
                .collect(Collectors.groupingBy(User::getId));
        List<QuestionBankVO> questionBankVOList = questionBankList.stream().map(questionBank -> {
            QuestionBankVO questionBankVO = QuestionBankVO.objToVo(questionBank);
            Long userId = questionBank.getUserId();
            if (userIdUserListMap.containsKey(userId)) {
                User user = userIdUserListMap.get(userId).get(0);
                questionBankVO.setUser(userService.getUserVO(user));
            }
            return questionBankVO;
        }).collect(Collectors.toList());
        questionBankVOPage.setRecords(questionBankVOList);
        return questionBankVOPage;
    }
}
