package com.kun.mianshikun.job.cycle;

import com.kun.mianshikun.exception.BusinessException;
import com.kun.mianshikun.mapper.QuestionBankMapper;
import com.kun.mianshikun.mapper.QuestionBankQuestionMapper;
import com.kun.mianshikun.mapper.QuestionMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.Date;

@Component
@Slf4j
public class IncClearDeletedData {
    @Resource
    private QuestionBankQuestionMapper questionBankQuestionMapper;
    @Resource
    private QuestionBankMapper questionBankMapper;
    @Resource
    private QuestionMapper questionMapper;
    @Scheduled(cron = "0 30 3 * * ?")
    public void run() {
        try {
            clearQuestionBank();

        }  catch (Exception e) {
            log.error("定时任务出错:", e);
        }
        try {
            clearQuestion();
        } catch (Exception e) {
            log.error("定时任务出错:", e);
        }
    }
    private void clearQuestionBank() throws InterruptedException {
        while (true){
            LocalDateTime date = LocalDateTime.now().minusDays(30);
            Integer result = questionBankMapper.clearDeletedData(date, 1000);
            if (result == 0)break;
            log.info("清空{}条数据", result);
            Thread.sleep(200);
        }
    }
    private void clearQuestion() throws InterruptedException {
        while (true){
            LocalDateTime date = LocalDateTime.now().minusDays(30);
            Integer result = questionMapper.clearDeletedData(date, 1000);
            if (result == 0)break;
            log.info("清空{}条数据", result);
            Thread.sleep(200);
        }
    }
}
