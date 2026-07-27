package com.kun.mianshikun.service.impl;

import cn.hutool.core.collection.CollUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.kun.mianshikun.common.ErrorCode;
import com.kun.mianshikun.constant.CommonConstant;
import com.kun.mianshikun.exception.BusinessException;
import com.kun.mianshikun.exception.ThrowUtils;
import com.kun.mianshikun.mapper.QuestionBankMapper;
import com.kun.mianshikun.mapper.QuestionBankQuestionMapper;
import com.kun.mianshikun.model.dto.questionBankQuestion.QuestionBankQuestionBatchRequest;
import com.kun.mianshikun.model.dto.questionBankQuestion.QuestionBankQuestionQueryRequest;
import com.kun.mianshikun.model.entity.QuestionBank;
import com.kun.mianshikun.model.entity.QuestionBankQuestion;
import com.kun.mianshikun.model.entity.User;
import com.kun.mianshikun.model.vo.QuestionBankQuestionVO;
import com.kun.mianshikun.model.vo.UserVO;
import com.kun.mianshikun.service.QuestionBankQuestionService;
import com.kun.mianshikun.service.UserService;
import com.kun.mianshikun.utils.SqlUtils;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.aop.framework.AopContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(rollbackFor = Exception.class)
@Slf4j
public class QuestionBankQuestionServiceImpl extends ServiceImpl<QuestionBankQuestionMapper, QuestionBankQuestion>
        implements QuestionBankQuestionService {

    @Resource
    private UserService userService;
    @Resource
    private QuestionBankMapper questionBankMapper;
    @Resource
    private QuestionBankQuestionMapper questionBankQuestionMapper;
    @Override
    public void validQuestionBankQuestion(QuestionBankQuestion questionBankQuestion, boolean add) {
        if (questionBankQuestion == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        if (add) {
            ThrowUtils.throwIf(questionBankQuestion.getQuestionBankId() == null, ErrorCode.PARAMS_ERROR);
            ThrowUtils.throwIf(questionBankQuestion.getQuestionId() == null, ErrorCode.PARAMS_ERROR);
        }
    }

    @Override
    public QueryWrapper<QuestionBankQuestion> getQueryWrapper(QuestionBankQuestionQueryRequest queryRequest) {
        QueryWrapper<QuestionBankQuestion> queryWrapper = new QueryWrapper<>();
        if (queryRequest == null) {
            return queryWrapper;
        }
        String sortField = queryRequest.getSortField();
        String sortOrder = queryRequest.getSortOrder();
        Long id = queryRequest.getId();
        Long notId = queryRequest.getNotId();
        Long questionBankId = queryRequest.getQuestionBankId();
        Long questionId = queryRequest.getQuestionId();
        Long userId = queryRequest.getUserId();

        queryWrapper.ne(ObjectUtils.isNotEmpty(notId), "id", notId);
        queryWrapper.eq(ObjectUtils.isNotEmpty(id), "id", id);
        queryWrapper.eq(ObjectUtils.isNotEmpty(questionBankId), "questionBankId", questionBankId);
        queryWrapper.eq(ObjectUtils.isNotEmpty(questionId), "questionId", questionId);
        queryWrapper.eq(ObjectUtils.isNotEmpty(userId), "userId", userId);
        queryWrapper.orderBy(SqlUtils.validSortField(sortField), CommonConstant.SORT_ORDER_ASC.equals(sortOrder),
                sortField);
        return queryWrapper;
    }

    @Override
    public QuestionBankQuestionVO getQuestionBankQuestionVO(QuestionBankQuestion questionBankQuestion) {
        if (questionBankQuestion == null) {
            return null;
        }
        QuestionBankQuestionVO vo = QuestionBankQuestionVO.objToVo(questionBankQuestion);
        Long userId = questionBankQuestion.getUserId();
        if (userId != null && userId > 0) {
            User user = userService.getById(userId);
            UserVO userVO = userService.getUserVO(user);
            vo.setUser(userVO);
        }
        return vo;
    }

    @Override
    public Page<QuestionBankQuestionVO> getQuestionBankQuestionVOPage(Page<QuestionBankQuestion> page) {
        List<QuestionBankQuestion> list = page.getRecords();
        Page<QuestionBankQuestionVO> voPage = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        if (CollUtil.isEmpty(list)) {
            return voPage;
        }
        Set<Long> userIdSet = list.stream().map(QuestionBankQuestion::getUserId).collect(Collectors.toSet());
        Map<Long, List<User>> userIdUserListMap = userService.listByIds(userIdSet).stream()
                .collect(Collectors.groupingBy(User::getId));
        List<QuestionBankQuestionVO> voList = list.stream().map(item -> {
            QuestionBankQuestionVO vo = QuestionBankQuestionVO.objToVo(item);
            Long userId = item.getUserId();
            if (userIdUserListMap.containsKey(userId)) {
                User user = userIdUserListMap.get(userId).get(0);
                vo.setUser(userService.getUserVO(user));
            }
            return vo;
        }).collect(Collectors.toList());
        voPage.setRecords(voList);
        return voPage;
    }

    @Override
    public Boolean batchAddQuestionBankQuestion(QuestionBankQuestionBatchRequest batchRequest, User  user) {
        ThrowUtils.throwIf(batchRequest.questionIdList.isEmpty(),
                ErrorCode.PARAMS_ERROR, "题目列表为空");
        ThrowUtils.throwIf(batchRequest.questionBankId == null
                        || batchRequest.questionBankId <= 0,
                ErrorCode.PARAMS_ERROR, "题库id为空");
        QuestionBank questionBank = questionBankMapper.selectById(batchRequest.questionBankId);
        ThrowUtils.throwIf(questionBank == null,ErrorCode.NOT_FOUND_ERROR,
                "题库不存在");
        List<QuestionBankQuestion> fi_list = questionBankQuestionMapper.selectList(new QueryWrapper<QuestionBankQuestion>()
                .in("questionId",batchRequest.questionIdList)
                .eq("questionBankId",batchRequest.questionBankId));
        List<Long> fi_questionIdList = fi_list.stream()
                .map(QuestionBankQuestion::getQuestionId).collect(Collectors.toList());
        List<Long> add_questionIdList = batchRequest.questionIdList.stream()
                .filter(questionId -> !fi_questionIdList.contains(questionId))
                .collect(Collectors.toList());
        log.info("need add:{}",add_questionIdList);
        if (add_questionIdList.size() < 1 ) {
            return true;
        }
        List<QuestionBankQuestion> questionBankQuestionList = add_questionIdList.stream().map(questionId -> {
            QuestionBankQuestion questionBankQuestion = new QuestionBankQuestion();
            questionBankQuestion.setQuestionId(questionId);
            questionBankQuestion.setQuestionBankId(batchRequest.questionBankId);
            questionBankQuestion.setUserId(user.getId());
            return questionBankQuestion;
        }).collect(Collectors.toList());
        int bathSize = 1000;
        int total = questionBankQuestionList.size();
        for (int i = 0; i < total; i += bathSize) {
            int end = Math.min(i + bathSize, total);
            List<QuestionBankQuestion> qbq =questionBankQuestionList.subList(i, end);
            QuestionBankQuestionService qbq_service = (QuestionBankQuestionService) AopContext.currentProxy();
            Boolean b =qbq_service.batchAddQuestionBankQuestionToInner(qbq);
            if (!b) {
                return false;
            }
        }
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean batchAddQuestionBankQuestionToInner(List<QuestionBankQuestion> questionBankQuestions) {
        try {
            saveBatch(questionBankQuestions);
            return true;
        } catch (Exception e) {
            log.error("批量添加题库题目失败",e);
            throw new BusinessException(ErrorCode.OPERATION_ERROR,"批量添加题库题目失败");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean batchRemoveQuestionBankQuestion(QuestionBankQuestionBatchRequest batchRequest, User user) {
        ThrowUtils.throwIf(batchRequest.questionIdList.isEmpty(),
                ErrorCode.PARAMS_ERROR, "题目列表为空");
        ThrowUtils.throwIf(batchRequest.questionBankId == null
                        || batchRequest.questionBankId <= 0,
                ErrorCode.PARAMS_ERROR, "题库id为空");
        QuestionBank questionBank = questionBankMapper.selectById(batchRequest.questionBankId);
        ThrowUtils.throwIf(questionBank == null,ErrorCode.NOT_FOUND_ERROR,
                "题库不存在");
        try {
            Boolean result = remove(new QueryWrapper<QuestionBankQuestion>()
                    .in("questionId",batchRequest.questionIdList)
                    .eq("questionBankId",batchRequest.questionBankId));
            return result;
        }catch (Exception e){
            log.error("批量删除题库题目失败",e);
            throw new BusinessException(ErrorCode.OPERATION_ERROR,"批量删除题库题目失败");
        }
    }
}
