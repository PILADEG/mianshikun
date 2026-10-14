package com.kun.mianshikun.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.kun.mianshikun.model.entity.Question;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.Date;
import java.util.List;

public interface QuestionMapper extends BaseMapper<Question> {

    @Select("select * from question "
            + "where (updateTime > #{lastTime} or (updateTime = #{lastTime} and id > #{lastId})) "
            + "and updateTime <= #{endTime} "
            + "order by updateTime, id limit #{limit}")
    List<Question> findListByCursor(@Param("lastTime") Date lastTime,
                                    @Param("lastId") Long lastId,
                                    @Param("endTime") Date endTime,
                                    @Param("limit") int limit);

    @Delete("delete from question where updateTime <= #{updateTime} and isDelete = 1 limit #{limit}")
    Integer clearDeletedData(LocalDateTime updateTime, Integer limit);
}
