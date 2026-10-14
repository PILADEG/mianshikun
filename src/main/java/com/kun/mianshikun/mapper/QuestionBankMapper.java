package com.kun.mianshikun.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.kun.mianshikun.model.entity.QuestionBank;
import org.apache.ibatis.annotations.Delete;

import java.time.LocalDateTime;
import java.util.Date;

public interface QuestionBankMapper extends BaseMapper<QuestionBank> {
    @Delete("delete from question_bank where updateTime <= #{updateTime} and isDelete = 1 limit #{limit}")
    Integer clearDeletedData(LocalDateTime updateTime, Integer limit);
}
