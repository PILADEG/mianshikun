package com.kun.mianshikun.job.cycle;

import cn.hutool.core.collection.CollUtil;
import com.kun.mianshikun.esdao.QuestionEsDao;
import com.kun.mianshikun.mapper.QuestionMapper;
import com.kun.mianshikun.model.dto.question.QuestionEsDTO;
import com.kun.mianshikun.model.entity.Question;
import com.kun.mianshikun.service.QuestionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import javax.annotation.Resource;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

// todo 取消注释开启任务
@Component
@Slf4j
public class IncSyncQuestionToEs {

    @Resource
    private QuestionService questionService;
    @Resource
    private QuestionMapper questionMapper;

    @Resource
    private QuestionEsDao questionEsDao;

    /**
     * 每分钟执行一次
     */
    @Scheduled(fixedRate = 60 * 1000)
    public void run() {
        try {
            // 查询近 5 分钟内的数据
            Date fiveMinutesAgoDate = new Date(new Date().getTime() - 5 * 60 * 1000L);
            List<Question> questionList =questionMapper.findListWithDelete(fiveMinutesAgoDate);
            if (CollUtil.isEmpty(questionList)) {
                log.info("no inc question");
                return;
            }
            List<QuestionEsDTO> questionEsDTOList = questionList.stream()
                    .map(QuestionEsDTO::objToDto)
                    .collect(Collectors.toList());
            final int pageSize = 500;
            int total = questionEsDTOList.size();
            log.info("IncSyncQuestionToEs start, total {}", total);
            for (int i = 0; i < total; i += pageSize) {
                int end = Math.min(i + pageSize, total);
                log.info("sync from {} to {}", i, end);
                questionEsDao.saveAll(questionEsDTOList.subList(i, end));
            }
            log.info("IncSyncQuestionToEs end, total {}", total);
        } catch (Exception e) {
            log.error("IncSyncQuestionToEs error", e);
        }
    }
}
