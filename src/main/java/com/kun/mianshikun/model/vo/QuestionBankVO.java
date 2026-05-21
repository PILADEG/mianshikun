package com.kun.mianshikun.model.vo;

import com.kun.mianshikun.model.entity.QuestionBank;
import java.io.Serializable;
import java.util.Date;
import java.util.List;
import lombok.Data;
import org.springframework.beans.BeanUtils;

@Data
public class QuestionBankVO implements Serializable {

    private Long id;

    private String title;

    private String description;

    private String picture;

    private Long userId;

    private Date createTime;

    private Date updateTime;

    private UserVO user;

    private List<QuestionVO> questionList;

    private static final long serialVersionUID = 1L;

    public static QuestionBankVO objToVo(QuestionBank questionBank) {
        if (questionBank == null) {
            return null;
        }
        QuestionBankVO questionBankVO = new QuestionBankVO();
        BeanUtils.copyProperties(questionBank, questionBankVO);
        return questionBankVO;
    }
}
