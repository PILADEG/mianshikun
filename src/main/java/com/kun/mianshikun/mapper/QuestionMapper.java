package com.kun.mianshikun.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.kun.mianshikun.model.entity.Question;
import org.apache.ibatis.annotations.Select;

import java.util.Date;
import java.util.List;

public interface QuestionMapper extends BaseMapper<Question> {
    @Select("select * from question where updateTime >= #{updateTime}")
    List<Question> findListWithDelete(Date updateTime);
}
